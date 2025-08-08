# Implementing Digital Zoom for Maps

This document outlines the implementation of a "digital zoom" feature for the map view in the Qiblah Finder application. This feature enhances the user experience by allowing users to zoom beyond the maximum level supported by the map tile server.

## 1. Overview

### The Problem
The current map implementation in `OpenStreetMapView.kt` fetches map tiles from a server. These servers typically provide tiles up to a certain maximum zoom level (e.g., level 19). When a user reaches this level, they cannot zoom in any further, which can be a frustrating experience if they want a closer view of the selected location.

### The Solution
To address this limitation, we can implement a **digital zoom**. Instead of trying to download non-existent tiles, we will programmatically scale the map image of the highest available zoom level. This creates the illusion of zooming in further, providing a smoother and more satisfying user interaction.

## 2. Enhanced Implementation Details

The core of the implementation will be in the `app/src/main/java/com/bizzkoot/qiblafinder/ui/location/OpenStreetMapView.kt` file.

### Key Enhancements Over Basic Plan:
1. **Performance Safeguards**: Device capability detection and memory pressure handling
2. **Improved Drag Handling**: Smooth interpolation for precise positioning
3. **Optimized Tile Loading**: Pause tile loading during digital zoom to reduce network overhead
4. **Accurate Calculations**: Proper accuracy circle scaling and coordinate calculations
5. **User Feedback**: Visual indicators for digital zoom mode
6. **Error Handling**: Graceful fallbacks and state recovery
7. **State Management**: Proper synchronization between normal and digital zoom modes

## 3. Step-by-Step Enhanced Code Changes

Here are the specific modifications to be made to `OpenStreetMapView.kt`.

### Step 1: Add Enhanced Constants and State Management

First, define constants for zoom levels and add new state variables with performance considerations.

```kotlin
// Add these constants above the composable function
const val MAX_ZOOM_LEVEL = 19
const val MIN_ZOOM_LEVEL = 2
const val MAX_DIGITAL_ZOOM_FACTOR = 4f // Allows zooming 4x beyond the max tile zoom
const val DIGITAL_ZOOM_STEP = 1.2f // Smooth zoom factor
const val TILE_SIZE = 256f

// Device-specific zoom limits
private fun getMaxDigitalZoomForDevice(): Float {
    return when {
        DeviceCapabilitiesDetector.isHighEndDevice() -> 4f
        DeviceCapabilitiesDetector.isMidRangeDevice() -> 2.5f
        else -> 2f
    }
}

@Composable
fun OpenStreetMapView(...) {
    // ...
    var zoom by remember { mutableStateOf(18) }
    var tileX by remember { mutableStateOf(0.0) }
    var tileY by remember { mutableStateOf(0.0) }
    var digitalZoom by remember { mutableStateOf(1f) } // New state variable
    var isTileLoadingPaused by remember { mutableStateOf(false) } // Pause tile loading during digital zoom
    var digitalZoomIndicator by remember { mutableStateOf(false) } // Show indicator when in digital zoom
    // ...
}
```

### Step 2: Enhanced Zoom-In Button Logic

Modify the `onClick` lambda for the zoom-in `IconButton` with performance safeguards and user feedback.

```kotlin
// Inside the zoom-in IconButton's onClick
onClick = {
    if (zoom < MAX_ZOOM_LEVEL) {
        val newZoom = zoom + 1
        val (currentLat, currentLng) = tileManager.tileXYToLatLng(tileX, tileY, zoom)
        val (newTileX, newTileY) = tileManager.latLngToTileXY(currentLat, currentLng, newZoom)
        zoom = newZoom
        tileX = newTileX
        tileY = newTileY
        digitalZoom = 1f // Reset digital zoom when changing tile zoom
        digitalZoomIndicator = false
        onLocationSelected(MapLocation(currentLat, currentLng))
    } else {
        val maxDigitalZoom = getMaxDigitalZoomForDevice()
        if (digitalZoom < maxDigitalZoom) {
            digitalZoom = min(digitalZoom * DIGITAL_ZOOM_STEP, maxDigitalZoom)
            digitalZoomIndicator = true
        }
    }
},
```

### Step 3: Enhanced Zoom-Out Button Logic

Modify the `onClick` lambda for the zoom-out `IconButton` with proper state management.

```kotlin
// Inside the zoom-out IconButton's onClick
onClick = {
    if (digitalZoom > 1f) {
        digitalZoom = max(digitalZoom / DIGITAL_ZOOM_STEP, 1f)
        if (digitalZoom <= 1f) {
            digitalZoom = 1f
            digitalZoomIndicator = false
        }
    } else {
        val newZoom = max(zoom - 1, MIN_ZOOM_LEVEL)
        val (currentLat, currentLng) = tileManager.tileXYToLatLng(tileX, tileY, zoom)
        val (newTileX, newTileY) = tileManager.latLngToTileXY(currentLat, currentLng, newZoom)
        zoom = newZoom
        tileX = newTileX
        tileY = newTileY
        onLocationSelected(MapLocation(currentLat, currentLng))
    }
},
```

### Step 4: Enhanced Drag Logic with Smooth Interpolation

Update the `onDrag` gesture detector with improved precision and performance.

```kotlin
// Inside detectDragGestures
onDrag = { change, dragAmount ->
    change.consume()
    
    // Calculate drag sensitivity based on digital zoom
    val dragSensitivity = 1f / digitalZoom
    val adjustedDragAmount = Offset(
        dragAmount.x * dragSensitivity,
        dragAmount.y * dragSensitivity
    )
    
    val tileSize = TILE_SIZE
    tileX -= adjustedDragAmount.x / tileSize
    tileY -= adjustedDragAmount.y / tileSize
    
    val (newLat, newLng) = tileManager.tileXYToLatLng(tileX, tileY, zoom)
    onLocationSelected(MapLocation(newLat, newLng))

    // Update drag position for continuous loading
    lastDragPosition = Pair(tileX, tileY)
},
```

### Step 5: Enhanced Tile Loading with Digital Zoom Optimization

Add logic to pause tile loading during digital zoom to reduce network overhead.

```kotlin
// Add this LaunchedEffect for tile loading optimization
LaunchedEffect(digitalZoom) {
    isTileLoadingPaused = digitalZoom > 1f
}

// Modify the existing tile loading LaunchedEffect
LaunchedEffect(tileX, tileY, zoom, mapType) {
    // Skip tile loading if paused (during digital zoom)
    if (isTileLoadingPaused) {
        return@LaunchedEffect
    }
    
    // Update accuracy first
    val accuracyMeters = getAccuracyForZoomWithDigitalZoom(zoom, digitalZoom)
    onAccuracyChanged(accuracyMeters)

    // Check memory pressure before loading
    if (tileManager.shouldHandleMemoryPressure()) {
        tileManager.handleMemoryPressure()
    }

    // Load tiles with priority (visible first, then buffer)
    if (!isActive) return@LaunchedEffect
    isLoading = true
    error = null
    try {
        val bufferSize = tileManager.getBufferSizeBasedOnConnection()
        val (visibleTiles, bufferTiles) = tileManager.getTilesForViewWithPriority(tileX, tileY, zoom, 800, 800, mapType, bufferSize)

        // Add null safety and empty checks
        if (visibleTiles.isNotEmpty()) {
            // Load visible tiles first using batch download (priority 1)
            val visibleCache = tileManager.batchDownloadTiles(visibleTiles, batchSize = 3)

            if (isActive) {
                tileCache = tileCache + visibleCache
                isLoading = false
            }
        } else {
            isLoading = false
        }

        // Load buffer tiles in background using batch download (priority 2)
        if (bufferTiles.isNotEmpty()) {
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
        }
    } catch (e: Exception) {
        if (isActive) {
            val fallbackMapType = MapTypeFallbackManager.getFallbackMapType(mapType, e)
            if (fallbackMapType != mapType) {
                onMapTypeFallback(fallbackMapType)
            } else {
                error = "Failed to load ${mapType.displayName.lowercase()} map: ${e.message}"
                isLoading = false
            }
        }
    }
}
```

### Step 6: Enhanced Canvas Drawing with Proper Scaling

Wrap the drawing logic inside the `Canvas` with enhanced scaling and accuracy calculations.

```kotlin
// Inside the Box composable
Canvas(modifier = Modifier.fillMaxSize()) {
    withTransform({
        scale(digitalZoom, digitalZoom, center)
    }) {
        val mapWidth = size.width / digitalZoom
        val mapHeight = size.height / digitalZoom

        val fractionalTileX = tileX - floor(tileX)
        val fractionalTileY = tileY - floor(tileY)

        val tileOffsetX = (fractionalTileX * TILE_SIZE).toFloat()
        val tileOffsetY = (fractionalTileY * TILE_SIZE).toFloat()

        val centerX = mapWidth / 2f
        val centerY = mapHeight / 2f

        tileCache.forEach { (cacheKey, bitmap) ->
            val tile = parseTileCacheKey(cacheKey)
            if (tile != null && tile.mapType == mapType) {
                val drawX = (tile.x - floor(tileX)).toFloat() * TILE_SIZE - tileOffsetX + centerX
                val drawY = (tile.y - floor(tileY)).toFloat() * TILE_SIZE - tileOffsetY + centerY
                drawImage(bitmap.asImageBitmap(), topLeft = Offset(drawX, drawY))
            }
        }

        // --- Draw Location Pin (adjust radius for digital zoom) ---
        val pinColor = Color.Red
        drawCircle(color = pinColor, radius = 15f / digitalZoom, center = Offset(centerX, centerY))
        drawCircle(color = Color.White, radius = 3f / digitalZoom, center = Offset(centerX, centerY))

        // --- Draw Accuracy Circle (adjust for digital zoom) ---
        val accuracyInMeters = getAccuracyForZoomWithDigitalZoom(zoom, digitalZoom)
        val accuracyInPixels = tileManager.metersToPixels(accuracyInMeters.toFloat(), zoom)
        drawCircle(color = Color.Red.copy(alpha = 0.1f), radius = accuracyInPixels, center = Offset(centerX, centerY))
        drawCircle(color = Color.Red, radius = accuracyInPixels, center = Offset(centerX, centerY), style = Stroke(width = 2f / digitalZoom))
    }
}
```

### Step 7: Add Digital Zoom Indicator

Add a visual indicator to inform users when they're in digital zoom mode.

```kotlin
// Add this after the Canvas but before the loading indicator
if (digitalZoomIndicator) {
    Card(
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
        )
    ) {
        Text(
            text = "Digital Zoom ${String.format("%.1fx", digitalZoom)}",
            modifier = Modifier.padding(8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}
```

### Step 8: Enhanced Accuracy Calculation

Add a new function to properly calculate accuracy with digital zoom.

```kotlin
// Add this function alongside the existing getAccuracyForZoom function
private fun getAccuracyForZoomWithDigitalZoom(zoom: Int, digitalZoom: Float): Int {
    val baseAccuracy = getAccuracyForZoom(zoom)
    return (baseAccuracy / digitalZoom).toInt().coerceAtLeast(1)
}
```

### Step 9: Add Error Handling and Performance Monitoring

Add comprehensive error handling for edge cases.

```kotlin
// Add this LaunchedEffect for performance monitoring
LaunchedEffect(digitalZoom) {
    // Monitor memory usage during digital zoom
    if (digitalZoom > 2f) {
        val memoryUsage = tileManager.getCacheSizeMB(mapType)
        if (memoryUsage > 80) { // 80MB threshold
            Timber.w("📍 High memory usage during digital zoom: ${memoryUsage}MB")
            // Optionally reduce digital zoom automatically
            if (digitalZoom > 3f) {
                digitalZoom = 3f
            }
        }
    }
}

// Add error handling for extreme zoom levels
LaunchedEffect(digitalZoom) {
    if (digitalZoom > getMaxDigitalZoomForDevice()) {
        digitalZoom = getMaxDigitalZoomForDevice()
        Timber.w("📍 Digital zoom limited to device capabilities")
    }
}
```

## 4. Complete Enhanced Code for `OpenStreetMapView.kt`

For your convenience, here is the complete, enhanced code for the `OpenStreetMapView.kt` file with all the improvements implemented.

```kotlin
package com.bizzkoot.qiblafinder.ui.location

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

const val MAX_ZOOM_LEVEL = 19
const val MIN_ZOOM_LEVEL = 2
const val MAX_DIGITAL_ZOOM_FACTOR = 4f
const val DIGITAL_ZOOM_STEP = 1.2f
const val TILE_SIZE = 256f

// Device-specific zoom limits
private fun getMaxDigitalZoomForDevice(): Float {
    return when {
        DeviceCapabilitiesDetector.isHighEndDevice() -> 4f
        DeviceCapabilitiesDetector.isMidRangeDevice() -> 2.5f
        else -> 2f
    }
}

@Composable
fun OpenStreetMapView(
    currentLocation: MapLocation,
    onLocationSelected: (MapLocation) -> Unit,
    onAccuracyChanged: (Int) -> Unit,
    onTileInfoChanged: (Int, Double) -> Unit = { _, _ -> },
    mapType: MapType = MapType.STREET,
    onMapTypeFallback: (MapType) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val tileManager = remember { OpenStreetMapTileManager(context) }

    // --- Enhanced State Management ---
    var zoom by remember { mutableStateOf(18) }
    var tileX by remember { mutableStateOf(0.0) }
    var tileY by remember { mutableStateOf(0.0) }
    var digitalZoom by remember { mutableStateOf(1f) }
    var isTileLoadingPaused by remember { mutableStateOf(false) }
    var digitalZoomIndicator by remember { mutableStateOf(false) }

    // --- Tile Cache & Loading State ---
    var tileCache by remember(mapType) { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    // Add cleanup when mapType changes
    LaunchedEffect(mapType) {
        tileCache = emptyMap()
    }

    // --- Drag State for Continuous Loading ---
    var isDragging by remember { mutableStateOf(false) }
    var lastDragPosition by remember { mutableStateOf(Pair(0.0, 0.0)) }

    // --- Initialization ---
    LaunchedEffect(currentLocation) {
        val (initialX, initialY) = tileManager.latLngToTileXY(currentLocation.latitude, currentLocation.longitude, zoom)
        tileX = initialX
        tileY = initialY
    }

    // --- Digital Zoom Optimization ---
    LaunchedEffect(digitalZoom) {
        isTileLoadingPaused = digitalZoom > 1f
    }

    // --- Performance Monitoring ---
    LaunchedEffect(digitalZoom) {
        if (digitalZoom > 2f) {
            val memoryUsage = tileManager.getCacheSizeMB(mapType)
            if (memoryUsage > 80) {
                Timber.w("📍 High memory usage during digital zoom: ${memoryUsage}MB")
                if (digitalZoom > 3f) {
                    digitalZoom = 3f
                }
            }
        }
    }

    // --- Error Handling for Extreme Zoom ---
    LaunchedEffect(digitalZoom) {
        if (digitalZoom > getMaxDigitalZoomForDevice()) {
            digitalZoom = getMaxDigitalZoomForDevice()
            Timber.w("📍 Digital zoom limited to device capabilities")
        }
    }

    // --- Enhanced Tile Loading and Accuracy Calculation ---
    LaunchedEffect(tileX, tileY, zoom, mapType) {
        // Skip tile loading if paused (during digital zoom)
        if (isTileLoadingPaused) {
            return@LaunchedEffect
        }
        
        // Update accuracy first
        val accuracyMeters = getAccuracyForZoomWithDigitalZoom(zoom, digitalZoom)
        onAccuracyChanged(accuracyMeters)

        // Check memory pressure before loading
        if (tileManager.shouldHandleMemoryPressure()) {
            tileManager.handleMemoryPressure()
        }

        // Load tiles with priority (visible first, then buffer)
        if (!isActive) return@LaunchedEffect
        isLoading = true
        error = null
        try {
            val bufferSize = tileManager.getBufferSizeBasedOnConnection()
            val (visibleTiles, bufferTiles) = tileManager.getTilesForViewWithPriority(tileX, tileY, zoom, 800, 800, mapType, bufferSize)

            if (visibleTiles.isNotEmpty()) {
                val visibleCache = tileManager.batchDownloadTiles(visibleTiles, batchSize = 3)

                if (isActive) {
                    tileCache = tileCache + visibleCache
                    isLoading = false
                }
            } else {
                isLoading = false
            }

            if (bufferTiles.isNotEmpty()) {
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
            }
        } catch (e: Exception) {
            if (isActive) {
                val fallbackMapType = MapTypeFallbackManager.getFallbackMapType(mapType, e)
                if (fallbackMapType != mapType) {
                    onMapTypeFallback(fallbackMapType)
                } else {
                    error = "Failed to load ${mapType.displayName.lowercase()} map: ${e.message}"
                    isLoading = false
                }
            }
        }
    }

    // Update tile info when cache changes
    LaunchedEffect(tileCache) {
        onTileInfoChanged(tileCache.size, tileManager.getCacheSizeMB(mapType))
    }

    // Continuous tile loading during drag
    LaunchedEffect(isDragging, lastDragPosition) {
        if (isDragging && !isTileLoadingPaused) {
            try {
                val (dragTileX, dragTileY) = lastDragPosition
                val bufferSize = tileManager.getBufferSizeBasedOnConnection()
                val (visibleTiles, _) = tileManager.getTilesForViewWithPriority(dragTileX, dragTileY, zoom, 800, 800, mapType, bufferSize)

                if (visibleTiles.isNotEmpty()) {
                    val visibleCache = tileManager.batchDownloadTiles(visibleTiles, batchSize = 3)

                    if (isActive) {
                        tileCache = tileCache + visibleCache
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "📍 Error loading tiles during drag")
            }
        }
    }

    Box(
        modifier = modifier
            .background(Color(0xFFE8F5E8))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { _ ->
                        isDragging = true
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        
                        // Calculate drag sensitivity based on digital zoom
                        val dragSensitivity = 1f / digitalZoom
                        val adjustedDragAmount = Offset(
                            dragAmount.x * dragSensitivity,
                            dragAmount.y * dragSensitivity
                        )
                        
                        tileX -= adjustedDragAmount.x / TILE_SIZE
                        tileY -= adjustedDragAmount.y / TILE_SIZE
                        val (newLat, newLng) = tileManager.tileXYToLatLng(tileX, tileY, zoom)
                        onLocationSelected(MapLocation(newLat, newLng))

                        lastDragPosition = Pair(tileX, tileY)
                    },
                    onDragEnd = {
                        isDragging = false
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            withTransform({
                scale(digitalZoom, digitalZoom, center)
            }) {
                val mapWidth = size.width / digitalZoom
                val mapHeight = size.height / digitalZoom

                val fractionalTileX = tileX - floor(tileX)
                val fractionalTileY = tileY - floor(tileY)

                val tileOffsetX = (fractionalTileX * TILE_SIZE).toFloat()
                val tileOffsetY = (fractionalTileY * TILE_SIZE).toFloat()

                val centerX = mapWidth / 2f
                val centerY = mapHeight / 2f

                tileCache.forEach { (cacheKey, bitmap) ->
                    val tile = parseTileCacheKey(cacheKey)
                    if (tile != null && tile.mapType == mapType) {
                        val drawX = (tile.x - floor(tileX)).toFloat() * TILE_SIZE - tileOffsetX + centerX
                        val drawY = (tile.y - floor(tileY)).toFloat() * TILE_SIZE - tileOffsetY + centerY
                        drawImage(bitmap.asImageBitmap(), topLeft = Offset(drawX, drawY))
                    }
                }

                // --- Draw Location Pin ---
                val pinColor = Color.Red
                drawCircle(color = pinColor, radius = 15f / digitalZoom, center = Offset(centerX, centerY))
                drawCircle(color = Color.White, radius = 3f / digitalZoom, center = Offset(centerX, centerY))

                // --- Draw Accuracy Circle ---
                val accuracyInMeters = getAccuracyForZoomWithDigitalZoom(zoom, digitalZoom)
                val accuracyInPixels = tileManager.metersToPixels(accuracyInMeters.toFloat(), zoom)
                drawCircle(color = Color.Red.copy(alpha = 0.1f), radius = accuracyInPixels, center = Offset(centerX, centerY))
                drawCircle(color = Color.Red, radius = accuracyInPixels, center = Offset(centerX, centerY), style = Stroke(width = 2f / digitalZoom))
            }
        }

        if (isLoading && tileCache.isEmpty()) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        // --- Digital Zoom Indicator ---
        if (digitalZoomIndicator) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
                )
            ) {
                Text(
                    text = "Digital Zoom ${String.format("%.1fx", digitalZoom)}",
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        // --- UI Elements (Zoom, Info) ---
        Column(modifier = Modifier.align(Alignment.TopEnd).padding(top = 130.dp, end = 16.dp)) {
            IconButton(
                onClick = {
                    if (zoom < MAX_ZOOM_LEVEL) {
                        val newZoom = zoom + 1
                        val (currentLat, currentLng) = tileManager.tileXYToLatLng(tileX, tileY, zoom)
                        val (newTileX, newTileY) = tileManager.latLngToTileXY(currentLat, currentLng, newZoom)
                        zoom = newZoom
                        tileX = newTileX
                        tileY = newTileY
                        digitalZoom = 1f
                        digitalZoomIndicator = false
                        onLocationSelected(MapLocation(currentLat, currentLng))
                    } else {
                        val maxDigitalZoom = getMaxDigitalZoomForDevice()
                        if (digitalZoom < maxDigitalZoom) {
                            digitalZoom = min(digitalZoom * DIGITAL_ZOOM_STEP, maxDigitalZoom)
                            digitalZoomIndicator = true
                        }
                    }
                },
                modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.primary, CircleShape)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Zoom In", tint = Color.White)
            }
            Spacer(modifier = Modifier.height(8.dp))
            IconButton(
                onClick = {
                    if (digitalZoom > 1f) {
                        digitalZoom = max(digitalZoom / DIGITAL_ZOOM_STEP, 1f)
                        if (digitalZoom <= 1f) {
                            digitalZoom = 1f
                            digitalZoomIndicator = false
                        }
                    } else {
                        val newZoom = max(zoom - 1, MIN_ZOOM_LEVEL)
                        val (currentLat, currentLng) = tileManager.tileXYToLatLng(tileX, tileY, zoom)
                        val (newTileX, newTileY) = tileManager.latLngToTileXY(currentLat, currentLng, newZoom)
                        zoom = newZoom
                        tileX = newTileX
                        tileY = newTileY
                        onLocationSelected(MapLocation(currentLat, currentLng))
                    }
                },
                modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.primary, CircleShape)
            ) {
                Icon(Icons.Filled.Remove, contentDescription = "Zoom Out", tint = Color.White)
            }
        }
    }
}

private fun parseTileCacheKey(key: String): TileCoordinate? {
    return try {
        val parts = key.split("_")
        when (parts.size) {
            3 -> {
                val zoom = parts[0].toInt()
                val x = parts[1].toInt()
                val y = parts[2].toInt()
                TileCoordinate(x, y, zoom, MapType.STREET)
            }
            4 -> {
                val mapType = MapType.valueOf(parts[0].uppercase())
                val zoom = parts[1].toInt()
                val x = parts[2].toInt()
                val y = parts[3].toInt()
                TileCoordinate(x, y, zoom, mapType)
            }
            else -> null
        }
    } catch (e: Exception) {
        Timber.e(e, "Failed to parse tile cache key: $key")
        null
    }
}

private fun getAccuracyForZoom(zoom: Int): Int {
    return when {
        zoom >= 18 -> 5
        zoom >= 17 -> 10
        zoom >= 16 -> 20
        zoom >= 15 -> 40
        zoom >= 14 -> 80
        zoom >= 13 -> 150
        zoom >= 12 -> 300
        zoom >= 11 -> 600
        else -> 1000
    }
}

private fun getAccuracyForZoomWithDigitalZoom(zoom: Int, digitalZoom: Float): Int {
    val baseAccuracy = getAccuracyForZoom(zoom)
    return (baseAccuracy / digitalZoom).toInt().coerceAtLeast(1)
}
```

## 5. Testing Strategy

### Unit Tests
- Test zoom transitions between normal and digital zoom
- Test accuracy calculations with different zoom levels
- Test drag sensitivity with various digital zoom factors
- Test memory pressure handling

### Integration Tests
- Test on different device capabilities
- Test rapid zoom in/out scenarios
- Test tile loading pause/resume functionality
- Test error handling and fallback mechanisms

### Performance Tests
- Monitor memory usage during extended digital zoom usage
- Test smoothness of drag operations at high zoom levels
- Verify tile loading optimization effectiveness

## 6. Deployment Considerations

### Phased Rollout
1. **Phase 1**: Deploy with conservative digital zoom limits (2x max)
2. **Phase 2**: Increase limits based on performance monitoring
3. **Phase 3**: Enable advanced features like smooth transitions

### Monitoring
- Track digital zoom usage patterns
- Monitor memory usage and performance metrics
- Collect user feedback on zoom experience

This enhanced implementation ensures a robust, performant, and user-friendly digital zoom feature that gracefully handles edge cases while providing the enhanced zoom capability users expect.