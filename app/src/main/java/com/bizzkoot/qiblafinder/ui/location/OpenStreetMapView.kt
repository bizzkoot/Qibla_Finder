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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

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

    // --- State Management in Tile Space ---
    var zoom by remember { mutableStateOf(18) }
    var tileX by remember { mutableStateOf(0.0) }
    var tileY by remember { mutableStateOf(0.0) }

    // --- Tile Cache & Loading State ---
    var tileCache by remember(mapType) { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    // Add cleanup when mapType changes
    LaunchedEffect(mapType) {
        // Clear old cache when switching map types
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

    // --- Tile Loading and Accuracy Calculation ---
    LaunchedEffect(tileX, tileY, zoom, mapType) { // Added mapType dependency
        // Update accuracy first
        val accuracyMeters = getAccuracyForZoom(zoom)
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

    // Update tile info when cache changes
    LaunchedEffect(tileCache) {
        onTileInfoChanged(tileCache.size, tileManager.getCacheSizeMB(mapType))
    }

    // Continuous tile loading during drag
    LaunchedEffect(isDragging, lastDragPosition) {
        if (isDragging) {
            try {
                val (dragTileX, dragTileY) = lastDragPosition
                val bufferSize = tileManager.getBufferSizeBasedOnConnection()
                val (visibleTiles, _) = tileManager.getTilesForViewWithPriority(dragTileX, dragTileY, zoom, 800, 800, mapType, bufferSize)

                // Add null safety for drag loading
                if (visibleTiles.isNotEmpty()) {
                    // Load visible tiles first during drag using batch download
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
                        val tileSize = 256f
                        tileX -= dragAmount.x / tileSize
                        tileY -= dragAmount.y / tileSize
                        val (newLat, newLng) = tileManager.tileXYToLatLng(tileX, tileY, zoom)
                        onLocationSelected(MapLocation(newLat, newLng))

                        // Update drag position for continuous loading
                        lastDragPosition = Pair(tileX, tileY)
                    },
                    onDragEnd = {
                        isDragging = false
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val mapWidth = size.width
            val mapHeight = size.height

            val fractionalTileX = tileX - floor(tileX)
            val fractionalTileY = tileY - floor(tileY)

            val tileOffsetX = (fractionalTileX * 256).toFloat()
            val tileOffsetY = (fractionalTileY * 256).toFloat()

            val centerX = mapWidth / 2f
            val centerY = mapHeight / 2f

            tileCache.forEach { (cacheKey, bitmap) ->
                val tile = parseTileCacheKey(cacheKey)
                if (tile != null && tile.mapType == mapType) {
                    val drawX = (tile.x - floor(tileX)).toFloat() * 256f - tileOffsetX + centerX
                    val drawY = (tile.y - floor(tileY)).toFloat() * 256f - tileOffsetY + centerY
                    drawImage(bitmap.asImageBitmap(), topLeft = Offset(drawX, drawY))
                }
            }

            // --- Draw Location Pin ---
            val pinColor = Color.Red
            drawCircle(color = pinColor, radius = 15f, center = Offset(centerX, centerY))
            drawCircle(color = Color.White, radius = 3f, center = Offset(centerX, centerY))

            // --- Draw Accuracy Circle ---
            val accuracyInMeters = getAccuracyForZoom(zoom)
            val accuracyInPixels = tileManager.metersToPixels(accuracyInMeters.toFloat(), zoom)
            drawCircle(color = Color.Red.copy(alpha = 0.1f), radius = accuracyInPixels, center = Offset(centerX, centerY))
            drawCircle(color = Color.Red, radius = accuracyInPixels, center = Offset(centerX, centerY), style = Stroke(width = 2f))
        }

        if (isLoading && tileCache.isEmpty()) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        // --- UI Elements (Zoom, Info) ---
        Column(modifier = Modifier.align(Alignment.TopEnd).padding(top = 130.dp, end = 16.dp)) {
            IconButton(
                onClick = {
                    val newZoom = min(zoom + 1, 19) // Max zoom 19
                    val (currentLat, currentLng) = tileManager.tileXYToLatLng(tileX, tileY, zoom)
                    val (newTileX, newTileY) = tileManager.latLngToTileXY(currentLat, currentLng, newZoom)
                    zoom = newZoom
                    tileX = newTileX
                    tileY = newTileY
                    onLocationSelected(MapLocation(currentLat, currentLng))
                },
                modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.primary, CircleShape)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Zoom In", tint = Color.White)
            }
            Spacer(modifier = Modifier.height(8.dp))
            IconButton(
                onClick = {
                    val newZoom = max(zoom - 1, 2) // Min zoom 2
                    val (currentLat, currentLng) = tileManager.tileXYToLatLng(tileX, tileY, zoom)
                    val (newTileX, newTileY) = tileManager.latLngToTileXY(currentLat, currentLng, newZoom)
                    zoom = newZoom
                    tileX = newTileX
                    tileY = newTileY
                    onLocationSelected(MapLocation(currentLat, currentLng))
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
                // Legacy format: zoom_x_y (for backward compatibility)
                val zoom = parts[0].toInt()
                val x = parts[1].toInt()
                val y = parts[2].toInt()
                TileCoordinate(x, y, zoom, MapType.STREET) // Default to street
            }
            4 -> {
                // New format: mapType_zoom_x_y
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
