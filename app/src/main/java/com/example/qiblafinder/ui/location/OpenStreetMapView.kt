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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val tileManager = remember { OpenStreetMapTileManager(context) }

    // --- State Management in Tile Space ---
    var zoom by remember { mutableStateOf(18) }
    var tileX by remember { mutableStateOf(0.0) }
    var tileY by remember { mutableStateOf(0.0) }

    // --- Tile Cache & Loading State ---
    var tileCache by remember { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    // --- Initialization ---
    LaunchedEffect(currentLocation) {
        val (initialX, initialY) = tileManager.latLngToTileXY(currentLocation.latitude, currentLocation.longitude, zoom)
        tileX = initialX
        tileY = initialY
    }

    // --- Tile Loading and Accuracy Calculation ---
    LaunchedEffect(tileX, tileY, zoom) {
        // Update accuracy first
        val accuracyMeters = getAccuracyForZoom(zoom)
        onAccuracyChanged(accuracyMeters)

        // Load tiles
        if (!isActive) return@LaunchedEffect
        isLoading = true
        error = null
        try {
            val newTiles = tileManager.getTilesForView(tileX, tileY, zoom, 800, 800)
            val newCache = newTiles.associate {
                it.toFileName() to (tileCache[it.toFileName()] ?: tileManager.getTileBitmap(it))
            }.filterValues { it != null }.mapValues { it.value!! }

            if (isActive) {
                tileCache = newCache
                isLoading = false
            }
        } catch (e: Exception) {
            if (isActive) {
                error = "Failed to load map: ${e.message}"
                isLoading = false
            }
        }
    }

    Box(
        modifier = modifier
            .background(Color(0xFFE8F5E8))
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val tileSize = 256f
                    tileX -= dragAmount.x / tileSize
                    tileY -= dragAmount.y / tileSize
                    val (newLat, newLng) = tileManager.tileXYToLatLng(tileX, tileY, zoom)
                    onLocationSelected(MapLocation(newLat, newLng))
                }
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

            tileCache.forEach { (fileName, bitmap) ->
                val tile = parseTileFileName(fileName)
                if (tile != null) {
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
                    val newZoom = min(zoom + 1, 18)
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
                    val newZoom = max(zoom - 1, 10)
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

        Card(modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text("Tiles: ${tileCache.size}", style = MaterialTheme.typography.bodySmall)
                Text("Cache: ${String.format("%.1f", tileManager.getCacheSizeMB())}MB", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun parseTileFileName(fileName: String): TileCoordinate? {
    return try {
        val parts = fileName.removePrefix("tile_").removeSuffix(".png").split("_")
        if (parts.size == 3) {
            TileCoordinate(zoom = parts[0].toInt(), x = parts[1].toInt(), y = parts[2].toInt())
        } else null
    } catch (e: Exception) {
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