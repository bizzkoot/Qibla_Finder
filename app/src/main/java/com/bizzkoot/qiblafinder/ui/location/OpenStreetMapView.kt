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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.bizzkoot.qiblafinder.utils.DeviceCapabilitiesDetector
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.math.*

const val MAX_ZOOM_LEVEL = 19
const val MAX_TILE_ZOOM_LEVEL = 18  // Maximum zoom level for tile downloads
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

// Get maximum zoom level for the current map type provider
private fun getMaxZoomForMapType(mapType: MapType): Int {
    return when (mapType) {
        MapType.STREET -> 18  // OpenStreetMap supports up to 18 reliably
        MapType.SATELLITE -> 18  // Esri satellite supports up to 18 reliably
    }
}

@Composable
fun OpenStreetMapView(
    currentLocation: MapLocation,
    onLocationSelected: (MapLocation) -> Unit,
    onAccuracyChanged: (Int) -> Unit,
    onTileInfoChanged: (Int, Double) -> Unit = { _, _ -> },
    mapType: MapType = MapType.STREET,
    showQiblaDirection: Boolean = true,
    onQiblaLineNeedsRedraw: () -> Unit = {},
    panelHeight: Int = 0,
    selectedLocation: MapLocation? = null,
    isMapTypeChanging: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val tileManager = remember { OpenStreetMapTileManager(context) }
    val qiblaOverlay = remember { QiblaDirectionOverlay() }

    // --- Enhanced State Management ---
    var zoom by remember { mutableStateOf(18) }  // Initial zoom level (will be validated per map type)
    var tileX by remember { mutableStateOf(0.0) }
    var tileY by remember { mutableStateOf(0.0) }
    var digitalZoom by remember { mutableStateOf(1f) }
    var isTileLoadingPaused by remember { mutableStateOf(false) }
    var digitalZoomIndicator by remember { mutableStateOf(false) }

    // --- Tile Cache & Loading State ---
    var tileStateCache by remember(mapType) { mutableStateOf<Map<String, TileLoadState>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    
    // --- Qibla Direction State ---
    var qiblaDirectionState by remember { mutableStateOf(QiblaDirectionState()) }

    // Add cleanup when mapType changes
    LaunchedEffect(mapType) {
        tileStateCache = emptyMap()
        
        // Clear Qibla direction cache when map type changes
        qiblaOverlay.clearCache()
        
        // Validate zoom level for the new map type
        val maxTileZoom = getMaxZoomForMapType(mapType)
        if (zoom > maxTileZoom) {
            zoom = maxTileZoom
            digitalZoom = 1f
            digitalZoomIndicator = false
            Timber.d("📍 Adjusted zoom level to $maxTileZoom for ${mapType.displayName}")
        }
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
    LaunchedEffect(tileX, tileY, zoom, mapType, isDragging, isMapTypeChanging) {
        // Skip tile loading if paused (during digital zoom) or during interactions
        if (isTileLoadingPaused || isDragging) {
            return@LaunchedEffect
        }
        
        // Reset tile cache when map type changes
        if (isMapTypeChanging) {
            tileStateCache = emptyMap()
        }

        // Validate zoom level for current map type
        val maxTileZoom = getMaxZoomForMapType(mapType)
        if (zoom > maxTileZoom) {
            // Don't try to load tiles beyond the supported zoom level
            Timber.d("📍 Skipping tile loading - zoom level $zoom exceeds max tile zoom $maxTileZoom for ${mapType.displayName}")
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

        val bufferSize = tileManager.getBufferSizeBasedOnConnection()
        val (visibleTiles, bufferTiles) = tileManager.getTilesForViewWithPriority(tileX, tileY, zoom, 800, 800, mapType, bufferSize)
        val allTiles = visibleTiles + bufferTiles

        allTiles.forEach { tile ->
            val cacheKey = tile.toCacheKey()
            if (tileStateCache[cacheKey] !is TileLoadState.HighRes) {
                launch {
                    tileManager.loadTileProgressively(tile).collect { state ->
                        tileStateCache = tileStateCache + (cacheKey to state)
                    }
                }
            }
        }
        isLoading = false
    }

    // Update tile info when cache changes
    LaunchedEffect(tileStateCache) {
        onTileInfoChanged(tileStateCache.size, tileManager.getCacheSizeMB(mapType))
    }

    // --- Calculate Qibla Direction State ---
    LaunchedEffect(selectedLocation, tileX, tileY, zoom, digitalZoom, showQiblaDirection, isDragging) {
        if (showQiblaDirection) {
            // Calculate viewport bounds for efficient path clipping
            val viewportBounds = calculateViewportBounds(tileX, tileY, zoom, 800, 800)
            
            // Enable high-performance mode during dragging or high digital zoom
            val isHighPerformanceMode = isDragging || digitalZoom > 2f
            
            // Calculate Qibla direction state with performance optimizations
            selectedLocation?.let { location ->
                qiblaDirectionState = qiblaOverlay.calculateDirectionLine(
                    userLocation = location,
                    viewportBounds = viewportBounds,
                    zoomLevel = zoom,
                    digitalZoom = digitalZoom,
                    isHighPerformanceMode = isHighPerformanceMode
                )
            }
        } else {
            qiblaDirectionState = QiblaDirectionState(isVisible = false)
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
                        
                        // When digital zoom increases, drag sensitivity should decrease.
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
                        // Trigger tile loading after drag ends
                        lastDragPosition = Pair(tileX, tileY)
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = size.width / 2f
            val centerY = size.height / 2f

            withTransform({
                scale(digitalZoom, digitalZoom, pivot = Offset(centerX, centerY))
            }) {
                val fractionalTileX = tileX - floor(tileX)
                val fractionalTileY = tileY - floor(tileY)

                val tileOffsetX = (fractionalTileX * TILE_SIZE).toFloat()
                val tileOffsetY = (fractionalTileY * TILE_SIZE).toFloat()

                // Center point of the canvas, this is where the pin is
                val canvasCenter = Offset(size.width / 2f, size.height / 2f)

                tileStateCache.forEach { (cacheKey, state) ->
                    val tile = parseTileCacheKey(cacheKey)
                    if (tile != null && tile.mapType == mapType) {
                        val drawX = (tile.x - floor(tileX)).toFloat() * TILE_SIZE - tileOffsetX + canvasCenter.x
                        val drawY = (tile.y - floor(tileY)).toFloat() * TILE_SIZE - tileOffsetY + canvasCenter.y

                        when (state) {
                            is TileLoadState.Loading -> {
                                // Optional: Draw a placeholder rectangle
                                drawRect(
                                    color = Color.LightGray,
                                    topLeft = Offset(drawX, drawY),
                                    size = androidx.compose.ui.geometry.Size(TILE_SIZE, TILE_SIZE)
                                )
                            }
                            is TileLoadState.LowRes -> {
                                // Draw the low-res bitmap, it will be scaled up by the canvas transform
                                drawImage(state.bitmap.asImageBitmap(), topLeft = Offset(drawX, drawY))
                            }
                            is TileLoadState.HighRes -> {
                                // Draw the final, sharp high-res bitmap
                                drawImage(state.bitmap.asImageBitmap(), topLeft = Offset(drawX, drawY))
                            }
                            is TileLoadState.Failed -> {
                                // Optional: Draw an error indicator
                                drawRect(
                                    color = Color.Gray,
                                    topLeft = Offset(drawX, drawY),
                                    size = androidx.compose.ui.geometry.Size(TILE_SIZE, TILE_SIZE)
                                )
                                // You could also draw an 'X' or other indicator
                            }
                        }
                    }
                }
            }

            // --- Draw Qibla Direction Line (above tiles, below UI controls) ---
            if (showQiblaDirection) {
                if (qiblaDirectionState.isVisible && qiblaDirectionState.isCalculationValid) {
                    qiblaOverlay.renderDirectionLine(
                        drawScope = this,
                        directionState = qiblaDirectionState,
                        centerOffset = Offset(centerX, centerY),
                        tileX = tileX,
                        tileY = tileY,
                        zoom = zoom,
                        digitalZoom = digitalZoom,
                        mapType = mapType
                    )
                } else if (!qiblaDirectionState.isCalculationValid) {
                    // Draw error indicator for failed Qibla calculation
                    val errorColor = Color.Red.copy(alpha = 0.7f)
                    val warningSize = 20f
                    
                    // Draw warning triangle at center
                    drawCircle(
                        color = errorColor,
                        radius = warningSize,
                        center = Offset(centerX, centerY + 40f),
                        style = Stroke(width = 3f)
                    )
                    
                    // Draw exclamation mark
                    drawLine(
                        color = errorColor,
                        start = Offset(centerX, centerY + 30f),
                        end = Offset(centerX, centerY + 45f),
                        strokeWidth = 3f
                    )
                    drawCircle(
                        color = errorColor,
                        radius = 2f,
                        center = Offset(centerX, centerY + 50f)
                    )
                }
            }

            // --- Draw Location Pin (always at the center of the screen) ---
            val pinColor = Color.Red
            drawCircle(color = pinColor, radius = 15f, center = Offset(centerX, centerY))
            drawCircle(color = Color.White, radius = 3f, center = Offset(centerX, centerY))

            // --- Draw Accuracy Circle (around the pin) ---
            val accuracyInMeters = getAccuracyForZoomWithDigitalZoom(zoom, digitalZoom)
            val accuracyInPixels = tileManager.metersToPixels(accuracyInMeters.toFloat(), zoom) * digitalZoom
            drawCircle(color = Color.Red.copy(alpha = 0.1f), radius = accuracyInPixels, center = Offset(centerX, centerY))
            drawCircle(color = Color.Red, radius = accuracyInPixels, center = Offset(centerX, centerY), style = Stroke(width = 2f))
        }

        if (isLoading && tileStateCache.isEmpty()) {
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
        
        // --- Memory Pressure and Performance Indicators ---
        if (showQiblaDirection && (qiblaDirectionState.hasMemoryPressure || qiblaDirectionState.reducedComplexity)) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 16.dp, top = if (digitalZoomIndicator) 80.dp else 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (qiblaDirectionState.hasMemoryPressure) 
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f)
                    else 
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f)
                )
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    if (qiblaDirectionState.hasMemoryPressure) {
                        Text(
                            text = "⚠️ Memory Pressure",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    if (qiblaDirectionState.reducedComplexity) {
                        Text(
                            text = "🔧 Simplified Mode",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (qiblaDirectionState.hasMemoryPressure) 
                                MaterialTheme.colorScheme.onErrorContainer
                            else 
                                MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
        
        // --- Qibla Calculation Error Indicator ---
        if (showQiblaDirection && !qiblaDirectionState.isCalculationValid && qiblaDirectionState.errorMessage != null) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
                    .widthIn(max = 250.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.95f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "⚠️",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = qiblaDirectionState.errorMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // --- UI Elements (Zoom, Info) ---
        val density = LocalDensity.current
        val dynamicTopPadding = with(density) { panelHeight.toDp() + 16.dp }
        Column(modifier = Modifier.align(Alignment.TopEnd).padding(top = dynamicTopPadding, end = 16.dp)) {
            IconButton(
                onClick = {
                    val maxTileZoom = getMaxZoomForMapType(mapType)
                    if (zoom < maxTileZoom) {
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
                        // Use digital zoom when tile zoom limit is reached
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

/**
 * Calculates viewport bounds for the current map view
 */
private fun calculateViewportBounds(
    centerTileX: Double,
    centerTileY: Double,
    zoom: Int,
    viewportWidth: Int,
    viewportHeight: Int
): ViewportBounds {
    val tileSize = 256.0
    
    // Calculate how many tiles are visible in each direction
    val tilesVisibleX = viewportWidth / tileSize
    val tilesVisibleY = viewportHeight / tileSize
    
    // Calculate bounds in tile coordinates
    val minTileX = centerTileX - tilesVisibleX / 2
    val maxTileX = centerTileX + tilesVisibleX / 2
    val minTileY = centerTileY - tilesVisibleY / 2
    val maxTileY = centerTileY + tilesVisibleY / 2
    
    // Convert tile coordinates to lat/lng
    val westLon = tileXToLongitude(minTileX, zoom)
    val eastLon = tileXToLongitude(maxTileX, zoom)
    val northLat = tileYToLatitude(minTileY, zoom)
    val southLat = tileYToLatitude(maxTileY, zoom)
    val centerLat = tileYToLatitude(centerTileY, zoom)
    val centerLon = tileXToLongitude(centerTileX, zoom)
    
    return ViewportBounds(
        northLat = northLat,
        southLat = southLat,
        eastLon = eastLon,
        westLon = westLon,
        centerLat = centerLat,
        centerLon = centerLon
    )
}

/**
 * Converts tile X coordinate to longitude
 */
private fun tileXToLongitude(tileX: Double, zoom: Int): Double {
    return tileX / (1 shl zoom).toDouble() * 360.0 - 180.0
}

/**
 * Converts tile Y coordinate to latitude
 */
private fun tileYToLatitude(tileY: Double, zoom: Int): Double {
    val n = PI - 2.0 * PI * tileY / (1 shl zoom).toDouble()
    return 180.0 / PI * atan(0.5 * (exp(n) - exp(-n)))
}
