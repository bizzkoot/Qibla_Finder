# Satellite Map Implementation Plan

## Overview

This document outlines the implementation of satellite map support as an additional option to the existing OpenStreetMap functionality in the QiblaFinder application. The implementation will be designed as a **good-to-have** feature that integrates seamlessly with the current architecture.

## Current Architecture Analysis

### Existing Components
- **`OpenStreetMapTileManager.kt`**: Handles tile downloading, caching, and coordinate conversion
- **`OpenStreetMapView.kt`**: Renders map tiles and handles user interactions
- **`ManualLocationScreen.kt`**: Orchestrates the map UI and user experience
- **`ManualLocationViewModel.kt`**: Manages state and location data

### Current Strengths
- ✅ Robust tile caching system (100MB limit with LRU eviction)
- ✅ Smart coordinate conversion (`latLngToTileXY`, `tileXYToLatLng`)
- ✅ Batch downloading capabilities
- ✅ Memory pressure handling
- ✅ Network-aware buffering
- ✅ Clean separation of concerns

## Implementation Strategy

### Phase 1: Core Infrastructure (Foundation)

#### 1.1 Map Type Enum and Data Structures
```kotlin
// File: app/src/main/java/com/bizzkoot/qiblafinder/ui/location/MapType.kt
package com.bizzkoot.qiblafinder.ui.location

enum class MapType(val displayName: String, val description: String) {
    STREET("Street", "Standard OpenStreetMap with roads and labels"),
    SATELLITE("Satellite", "High-resolution satellite imagery")
}
```

#### 1.2 Enhanced Tile Coordinate System
```kotlin
// Update: app/src/main/java/com/bizzkoot/qiblafinder/ui/location/OpenStreetMapTileManager.kt
data class TileCoordinate(
    val x: Int,
    val y: Int,
    val zoom: Int,
    val mapType: MapType = MapType.STREET
) {
    fun toFileName(): String = "tile_${mapType.name.lowercase()}_${zoom}_${x}_${y}.png"
    
    fun toCacheKey(): String = "${mapType.name.lowercase()}_${zoom}_${x}_${y}"
}
```

#### 1.3 Tile URL Provider Pattern
```kotlin
// File: app/src/main/java/com/bizzkoot/qiblafinder/ui/location/TileUrlProvider.kt
package com.bizzkoot.qiblafinder.ui.location

interface TileUrlProvider {
    fun getTileUrl(tile: TileCoordinate): String
    fun getMaxZoom(): Int
    fun getMinZoom(): Int
    fun requiresApiKey(): Boolean = false
    fun getAttribution(): String
}

class OpenStreetMapUrlProvider : TileUrlProvider {
    override fun getTileUrl(tile: TileCoordinate): String = 
        "https://tile.openstreetmap.org/${tile.zoom}/${tile.x}/${tile.y}.png"
    
    override fun getMaxZoom(): Int = 19
    override fun getMinZoom(): Int = 0
    override fun getAttribution(): String = "© OpenStreetMap contributors"
}

class EsriSatelliteUrlProvider : TileUrlProvider {
    override fun getTileUrl(tile: TileCoordinate): String = 
        "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/${tile.zoom}/${tile.y}/${tile.x}"
    
    override fun getMaxZoom(): Int = 19
    override fun getMinZoom(): Int = 0
    override fun getAttribution(): String = "© Esri, Maxar, Earthstar Geographics"
}

class BingSatelliteUrlProvider : TileUrlProvider {
    override fun getTileUrl(tile: TileCoordinate): String = 
        "https://ecn.t3.tiles.virtualearth.net/tiles/a${getQuadKey(tile)}.jpeg?g=1"
    
    override fun getMaxZoom(): Int = 19
    override fun getMinZoom(): Int = 1
    override fun getAttribution(): String = "© Microsoft Corporation"
    
    private fun getQuadKey(tile: TileCoordinate): String {
        // Implementation for Bing's quadkey system
        // This is a simplified version - full implementation needed
        return ""
    }
}
```

### Phase 2: Enhanced Tile Manager

#### 2.1 Updated OpenStreetMapTileManager
```kotlin
// Update: app/src/main/java/com/bizzkoot/qiblafinder/ui/location/OpenStreetMapTileManager.kt
class OpenStreetMapTileManager(private val context: Context) {
    
    private val cacheDir = File(context.cacheDir, "map_tiles")
    private val maxCacheSize = 100 * 1024 * 1024 // 100MB max cache
    
    // Map type specific cache directories
    private val streetCacheDir = File(cacheDir, "street")
    private val satelliteCacheDir = File(cacheDir, "satellite")
    
    // Tile providers
    private val tileProviders = mapOf(
        MapType.STREET to OpenStreetMapUrlProvider(),
        MapType.SATELLITE to EsriSatelliteUrlProvider()
    )
    
    init {
        if (!cacheDir.exists()) cacheDir.mkdirs()
        if (!streetCacheDir.exists()) streetCacheDir.mkdirs()
        if (!satelliteCacheDir.exists()) satelliteCacheDir.mkdirs()
    }
    
    /**
     * Get cache directory for specific map type
     */
    private fun getCacheDir(mapType: MapType): File {
        return when (mapType) {
            MapType.STREET -> streetCacheDir
            MapType.SATELLITE -> satelliteCacheDir
        }
    }
    
    /**
     * Download tile with map type support
     */
    private suspend fun downloadTileWithRetry(tile: TileCoordinate, maxRetries: Int = 3): Bitmap? {
        val provider = tileProviders[tile.mapType] ?: return null
        
        for (attempt in 0 until maxRetries) {
            try {
                val url = URL(provider.getTileUrl(tile))
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                connection.setRequestProperty("User-Agent", "QiblaFinder/1.0")
                
                if (connection.responseCode == 200) {
                    val inputStream = connection.inputStream
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream.close()
                    connection.disconnect()
                    return bitmap
                } else {
                    Timber.e("📍 HTTP ${connection.responseCode} for tile: ${tile.toFileName()}")
                    connection.disconnect()
                }
            } catch (e: Exception) {
                Timber.e(e, "📍 Error downloading tile (attempt ${attempt + 1}): ${tile.toFileName()}")
                if (attempt == maxRetries - 1) {
                    return null
                }
                kotlinx.coroutines.delay(1000L * (1 shl attempt))
            }
        }
        return null
    }
    
    /**
     * Clear cache for specific map type
     */
    fun clearCache(mapType: MapType? = null) {
        try {
            when (mapType) {
                null -> {
                    // Clear all caches
                    cacheDir.listFiles()?.forEach { it.delete() }
                    Timber.d("📍 All map caches cleared")
                }
                else -> {
                    // Clear specific map type cache
                    val typeCacheDir = getCacheDir(mapType)
                    typeCacheDir.listFiles()?.forEach { it.delete() }
                    Timber.d("📍 ${mapType.displayName} cache cleared")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "📍 Error clearing cache")
        }
    }
    
    /**
     * Get cache size for specific map type
     */
    fun getCacheSizeMB(mapType: MapType? = null): Double {
        return try {
            val targetDir = when (mapType) {
                null -> cacheDir
                else -> getCacheDir(mapType)
            }
            val files = targetDir.listFiles() ?: return 0.0
            files.sumOf { it.length() } / (1024.0 * 1024.0)
        } catch (e: Exception) {
            Timber.e(e, "📍 Error getting cache size")
            0.0
        }
    }
}
```

### Phase 3: UI Enhancements

#### 3.1 Updated ManualLocationViewModel
```kotlin
// Update: app/src/main/java/com/bizzkoot/qiblafinder/ui/location/ManualLocationViewModel.kt
data class ManualLocationUiState(
    val selectedLocation: MapLocation? = null,
    val currentLocation: MapLocation? = null,
    val accuracyInMeters: Int = 0,
    val tileCount: Int = 0,
    val cacheSizeMB: Double = 0.0,
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedMapType: MapType = MapType.STREET, // New field
    val availableMapTypes: List<MapType> = listOf(MapType.STREET, MapType.SATELLITE)
)

class ManualLocationViewModel(
    private val locationRepository: LocationRepository
) : ViewModel() {
    
    // ... existing code ...
    
    fun updateMapType(mapType: MapType) {
        _uiState.value = _uiState.value.copy(
            selectedMapType = mapType,
            isLoading = true,
            error = null
        )
        
        // Clear cache for the new map type to ensure fresh tiles
        viewModelScope.launch {
            // This would require access to tile manager
            // Implementation depends on how we pass tile manager to ViewModel
            Timber.d("📍 Map type changed to: ${mapType.displayName}")
        }
    }
}
```

#### 3.2 Enhanced ManualLocationScreen
```kotlin
// Update: app/src/main/java/com/bizzkoot/qiblafinder/ui/location/ManualLocationScreen.kt
@Composable
fun ManualLocationScreen(
    viewModel: ManualLocationViewModel,
    onLocationConfirmed: (MapLocation) -> Unit,
    onBackPressed: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manual Location Adjustment") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Map Type Toggle
                    MapTypeToggle(
                        selectedMapType = uiState.selectedMapType,
                        availableMapTypes = uiState.availableMapTypes,
                        onMapTypeChanged = { viewModel.updateMapType(it) }
                    )
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Map View with map type support
            if (uiState.currentLocation != null) {
                OpenStreetMapView(
                    currentLocation = uiState.currentLocation!!,
                    onLocationSelected = { mapLocation ->
                        viewModel.updateSelectedLocation(mapLocation)
                    },
                    onAccuracyChanged = { accuracy ->
                        viewModel.updateAccuracy(accuracy)
                    },
                    onTileInfoChanged = { tileCount, cacheSizeMB ->
                        viewModel.updateTileInfo(tileCount, cacheSizeMB)
                    },
                    mapType = uiState.selectedMapType, // New parameter
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            // ... existing UI elements ...
        }
    }
}

@Composable
fun MapTypeToggle(
    selectedMapType: MapType,
    availableMapTypes: List<MapType>,
    onMapTypeChanged: (MapType) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = when (selectedMapType) {
                    MapType.STREET -> Icons.Default.Map
                    MapType.SATELLITE -> Icons.Default.Satellite
                },
                contentDescription = "Map Type: ${selectedMapType.displayName}"
            )
        }
        
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            availableMapTypes.forEach { mapType ->
                DropdownMenuItem(
                    text = { Text(mapType.displayName) },
                    onClick = {
                        onMapTypeChanged(mapType)
                        expanded = false
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = when (mapType) {
                                MapType.STREET -> Icons.Default.Map
                                MapType.SATELLITE -> Icons.Default.Satellite
                            },
                            contentDescription = null
                        )
                    }
                )
            }
        }
    }
}
```

#### 3.3 Updated OpenStreetMapView
```kotlin
// Update: app/src/main/java/com/bizzkoot/qiblafinder/ui/location/OpenStreetMapView.kt
@Composable
fun OpenStreetMapView(
    currentLocation: MapLocation,
    onLocationSelected: (MapLocation) -> Unit,
    onAccuracyChanged: (Int) -> Unit,
    onTileInfoChanged: (Int, Double) -> Unit = { _, _ -> },
    mapType: MapType = MapType.STREET, // New parameter
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val tileManager = remember { OpenStreetMapTileManager(context) }
    
    // ... existing state management ...
    
    // Update tile loading to use map type
    LaunchedEffect(tileX, tileY, zoom, mapType) { // Added mapType dependency
        val accuracyMeters = getAccuracyForZoom(zoom)
        onAccuracyChanged(accuracyMeters)
        
        if (tileManager.shouldHandleMemoryPressure()) {
            tileManager.handleMemoryPressure()
        }
        
        if (!isActive) return@LaunchedEffect
        isLoading = true
        error = null
        
        try {
            val bufferSize = tileManager.getBufferSizeBasedOnConnection()
            val (visibleTiles, bufferTiles) = tileManager.getTilesForViewWithPriority(
                tileX, tileY, zoom, 800, 800, bufferSize
            )
            
            // Load tiles with map type
            val visibleCache = tileManager.batchDownloadTiles(visibleTiles, batchSize = 3)
            
            if (isActive) {
                tileCache = tileCache + visibleCache
                isLoading = false
            }
            
            // Load buffer tiles in background
            launch {
                try {
                    val bufferCache = tileManager.batchDownloadTiles(bufferTiles, batchSize = 5)
                    if (isActive) {
                        tileCache = tileCache + bufferCache
                    }
                } catch (e: Exception) {
                    Timber.e(e, "📍 Error loading buffer tiles")
                }
            }
        } catch (e: Exception) {
            if (isActive) {
                error = "Failed to load ${mapType.displayName.lowercase()} map: ${e.message}"
                isLoading = false
            }
        }
    }
    
    // ... rest of the implementation remains similar ...
}
```

### Phase 4: Error Handling and Fallbacks

#### 4.1 Graceful Degradation
```kotlin
// File: app/src/main/java/com/bizzkoot/qiblafinder/ui/location/MapTypeFallbackManager.kt
package com.bizzkoot.qiblafinder.ui.location

class MapTypeFallbackManager {
    companion object {
        fun getFallbackMapType(originalMapType: MapType, error: Exception): MapType {
            return when (originalMapType) {
                MapType.SATELLITE -> {
                    Timber.w("📍 Satellite map failed, falling back to street map: ${error.message}")
                    MapType.STREET
                }
                MapType.STREET -> {
                    Timber.e("📍 Street map failed, this is unexpected: ${error.message}")
                    MapType.STREET // Keep as is, let error handling take over
                }
            }
        }
    }
}
```

#### 4.2 Enhanced Error Handling
```kotlin
// Update: OpenStreetMapTileManager.kt
private suspend fun downloadTileWithRetry(tile: TileCoordinate, maxRetries: Int = 3): Bitmap? {
    val provider = tileProviders[tile.mapType] ?: return null
    
    for (attempt in 0 until maxRetries) {
        try {
            val url = URL(provider.getTileUrl(tile))
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.setRequestProperty("User-Agent", "QiblaFinder/1.0")
            
            when (connection.responseCode) {
                200 -> {
                    val inputStream = connection.inputStream
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream.close()
                    connection.disconnect()
                    return bitmap
                }
                403, 429 -> {
                    // Rate limited or forbidden - try fallback
                    Timber.w("📍 Tile provider rate limited: ${tile.toFileName()}")
                    return null
                }
                else -> {
                    Timber.e("📍 HTTP ${connection.responseCode} for tile: ${tile.toFileName()}")
                    connection.disconnect()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "📍 Error downloading tile (attempt ${attempt + 1}): ${tile.toFileName()}")
            if (attempt == maxRetries - 1) {
                return null
            }
            kotlinx.coroutines.delay(1000L * (1 shl attempt))
        }
    }
    return null
}
```

### Phase 5: Performance Optimizations

#### 5.1 Map Type Specific Caching
```kotlin
// Update: OpenStreetMapTileManager.kt
private fun manageCacheSize() {
    try {
        // Manage cache size per map type
        MapType.values().forEach { mapType ->
            val typeCacheDir = getCacheDir(mapType)
            val cacheFiles = typeCacheDir.listFiles() ?: return@forEach
            val totalSize = cacheFiles.sumOf { it.length() }
            val maxTypeCacheSize = maxCacheSize / 2 // Split cache between map types
            
            if (totalSize > maxTypeCacheSize) {
                val sortedFiles = cacheFiles.sortedBy { it.lastModified() }
                var currentSize = totalSize
                
                for (file in sortedFiles) {
                    if (currentSize <= maxTypeCacheSize) break
                    currentSize -= file.length()
                    file.delete()
                    Timber.d("📍 Removed old ${mapType.displayName} cache file: ${file.name}")
                }
            }
        }
        
        evictOldTiles()
    } catch (e: Exception) {
        Timber.e(e, "📍 Error managing cache size")
    }
}
```

#### 5.2 Progressive Loading for Satellite Tiles
```kotlin
// Update: OpenStreetMapTileManager.kt
suspend fun loadTileProgressive(tile: TileCoordinate): Bitmap? {
    // Try to get the tile directly first
    val directTile = getTileBitmap(tile)
    if (directTile != null) {
        return directTile
    }
    
    // For satellite tiles, try lower resolution first
    if (tile.mapType == MapType.SATELLITE) {
        val lowerResTile = getLowerResolutionTile(tile)
        if (lowerResTile != null) {
            val lowerResBitmap = getTileBitmap(lowerResTile)
            if (lowerResBitmap != null) {
                return lowerResBitmap
            }
        }
    }
    
    // Fallback to direct loading
    return getTileBitmap(tile)
}
```

## Implementation Timeline

### Week 1: Foundation (8-10 hours)
- [ ] Create `MapType.kt` enum
- [ ] Update `TileCoordinate` data class
- [ ] Implement `TileUrlProvider` interface and providers
- [ ] Basic integration testing

### Week 2: Core Integration (6-8 hours)
- [ ] Update `OpenStreetMapTileManager` with map type support
- [ ] Implement map type specific caching
- [ ] Add error handling and fallbacks
- [ ] Update tile downloading logic

### Week 3: UI Implementation (4-6 hours)
- [ ] Update `ManualLocationViewModel` with map type state
- [ ] Create `MapTypeToggle` composable
- [ ] Update `ManualLocationScreen` with toggle UI
- [ ] Update `OpenStreetMapView` with map type parameter

### Week 4: Testing and Polish (4-6 hours)
- [ ] Comprehensive testing on different devices
- [ ] Performance optimization
- [ ] Error handling refinement
- [ ] User experience improvements

## Testing Strategy

### Unit Tests
- Tile URL generation for different providers
- Cache management for different map types
- Coordinate conversion accuracy
- Error handling and fallbacks

### Integration Tests
- Map type switching functionality
- Tile loading performance
- Cache size management
- Memory pressure handling

### User Acceptance Tests
- Smooth map type switching
- Satellite tile loading performance
- Error scenarios and fallbacks
- Overall user experience

## Risk Assessment

### Low Risk
- ✅ Integration with existing architecture
- ✅ Backward compatibility maintained
- ✅ Graceful error handling

### Medium Risk
- ⚠️ Satellite tile provider reliability
- ⚠️ Performance impact on slower devices
- ⚠️ Cache size management complexity

### Mitigation Strategies
- Implement multiple satellite providers as fallbacks
- Progressive loading for satellite tiles
- Separate cache management per map type
- Comprehensive error handling and user feedback

## Success Criteria

1. **Functional Requirements**
   - Users can switch between street and satellite maps
   - Map type preference is maintained during session
   - Smooth transition between map types
   - Proper error handling and fallbacks

2. **Performance Requirements**
   - Satellite tiles load within 3 seconds on 4G
   - Cache size remains under 100MB total
   - Memory usage doesn't increase significantly
   - Smooth scrolling and zooming

3. **User Experience Requirements**
   - Intuitive map type toggle UI
   - Clear visual feedback during loading
   - Consistent behavior across different devices
   - No regression in existing functionality

## Conclusion

This implementation plan provides a comprehensive approach to adding satellite map support to the QiblaFinder application. The design leverages the existing robust architecture while adding new capabilities in a modular and maintainable way.

The implementation is **moderately complex** but highly feasible, with clear phases and success criteria. The feature will enhance the user experience by providing additional map visualization options while maintaining the reliability and performance of the existing system. 