package com.bizzkoot.qiblafinder.ui.location

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.*

data class MapLocation(
    val latitude: Double,
    val longitude: Double
)

@Composable
fun SimpleMapView(
    currentLocation: MapLocation,
    selectedLocation: MapLocation?,
    onLocationSelected: (MapLocation) -> Unit,
    modifier: Modifier = Modifier
) {
    var mapCenter by remember { mutableStateOf(currentLocation) }
    var zoomLevel by remember { mutableStateOf(15f) }
    var isDragging by remember { mutableStateOf(false) }
    var dragStart by remember { mutableStateOf(Offset.Zero) }
    
    val mapSize = 300.dp
    val pinSize = 20.dp
    
    Box(
        modifier = modifier
            .size(mapSize)
            .background(Color(0xFFE8F5E8)) // Light green background
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        dragStart = offset
                        val pinCenter = Offset(size.width / 2f, size.height / 2f)
                        val distance = sqrt((offset.x - pinCenter.x).pow(2f) + (offset.y - pinCenter.y).pow(2f))
                        if (distance <= pinSize.value * 3) {
                            // Drag started on pin area
                        }
                    },
                    onDrag = { change, _ ->
                        if (isDragging) {
                            // Convert screen coordinates to lat/lng changes
                            val latChange = -change.position.y / (1000000.0 * zoomLevel)
                            val lngChange = change.position.x / (1000000.0 * zoomLevel)
                            
                            val newLat = mapCenter.latitude + latChange
                            val newLng = mapCenter.longitude + lngChange
                            
                            val newLocation = MapLocation(newLat, newLng)
                            mapCenter = newLocation
                            onLocationSelected(newLocation)
                        }
                    },
                    onDragEnd = {
                        isDragging = false
                    }
                )
            }
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val centerX = size.width / 2f
            val centerY = size.height / 2f
            
            // Draw map background with terrain-like pattern
            val terrainColor = Color(0xFF8BC34A) // Light green for land
            drawRect(
                color = terrainColor,
                topLeft = Offset(0f, 0f),
                size = androidx.compose.ui.geometry.Size(size.width, size.height)
            )
            
            // Draw subtle terrain variations
            val grassColor = Color(0xFF7CB342) // Darker green for grass
            for (i in 0..8) {
                val x = (i * 40f) % size.width
                val y = (i * 35f) % size.height
                drawCircle(
                    color = grassColor,
                    radius = 15f,
                    center = Offset(x, y)
                )
            }
            
            // Draw roads/streets pattern - make them more subtle
            val roadColor = Color(0xFFE8E8E8) // Lighter gray for roads
            val roadWidth = 6f
            
            // Horizontal roads - fewer and more spaced out
            for (y in 0..size.height.toInt() step 100) {
                drawRect(
                    color = roadColor,
                    topLeft = Offset(0f, y.toFloat()),
                    size = androidx.compose.ui.geometry.Size(size.width, roadWidth)
                )
            }
            
            // Vertical roads - fewer and more spaced out
            for (x in 0..size.width.toInt() step 120) {
                drawRect(
                    color = roadColor,
                    topLeft = Offset(x.toFloat(), 0f),
                    size = androidx.compose.ui.geometry.Size(roadWidth, size.height)
                )
            }
            
            // Draw buildings/structures as more natural shapes
            val buildingColor = Color(0xFF9E9E9E) // Gray for buildings
            for (i in 0..3) { // Reduced from 5 to 3 buildings
                val x = (i * 80f + 30f) % (size.width - 60f)
                val y = (i * 70f + 40f) % (size.height - 80f)
                
                // Draw building with rounded corners effect
                drawRoundRect(
                    color = buildingColor,
                    topLeft = Offset(x, y),
                    size = androidx.compose.ui.geometry.Size(40f, 35f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f)
                )
                
                // Add building details (windows)
                drawCircle(
                    color = Color.White,
                    radius = 3f,
                    center = Offset(x + 10f, y + 8f)
                )
                drawCircle(
                    color = Color.White,
                    radius = 3f,
                    center = Offset(x + 30f, y + 8f)
                )
                drawCircle(
                    color = Color.White,
                    radius = 3f,
                    center = Offset(x + 10f, y + 25f)
                )
                drawCircle(
                    color = Color.White,
                    radius = 3f,
                    center = Offset(x + 30f, y + 25f)
                )
            }
            
            // Draw accuracy circle
            val accuracyRadius = when {
                zoomLevel >= 18 -> 15f  // Very precise: 15px
                zoomLevel >= 16 -> 30f  // Precise: 30px
                zoomLevel >= 14 -> 45f  // Medium: 45px
                zoomLevel >= 12 -> 60f  // Approximate: 60px
                else -> 75f             // Rough: 75px
            }
            
            // Draw accuracy circle background
            drawCircle(
                color = Color.Red.copy(alpha = 0.1f),
                radius = accuracyRadius,
                center = Offset(centerX, centerY)
            )
            
            // Draw accuracy circle border
            drawCircle(
                color = Color.Red,
                radius = accuracyRadius,
                center = Offset(centerX, centerY),
                style = Stroke(width = 2f)
            )
            
            // Draw pin shadow
            drawCircle(
                color = Color.Black.copy(alpha = 0.3f),
                radius = pinSize.value,
                center = Offset(centerX + 2f, centerY + 2f)
            )
            
            // Draw pin (location marker)
            val pinColor = if (isDragging) Color.Blue else Color.Red
            drawCircle(
                color = pinColor,
                radius = pinSize.value,
                center = Offset(centerX, centerY)
            )
            
            // Draw pin center dot
            drawCircle(
                color = Color.White,
                radius = 3f,
                center = Offset(centerX, centerY)
            )
            
            // Draw direction indicator (arrow pointing to Qibla)
            val arrowLength = 40f
            val arrowWidth = 8f
            val qiblaAngle = 45f // This would be calculated based on actual Qibla direction
            
            val arrowPath = Path().apply {
                moveTo(centerX, centerY - arrowLength)
                lineTo(centerX - arrowWidth, centerY - arrowLength + 15f)
                lineTo(centerX + arrowWidth, centerY - arrowLength + 15f)
                close()
            }
            
            drawPath(
                path = arrowPath,
                color = Color(0xFF4CAF50) // Green arrow pointing to Qibla
            )
            
            // Draw compass rose
            val compassRadius = 25f
            drawCircle(
                color = Color.White.copy(alpha = 0.8f),
                radius = compassRadius,
                center = Offset(compassRadius + 10f, compassRadius + 10f)
            )
            
            // Draw N, S, E, W labels
            drawCircle(
                color = Color.Red,
                radius = 3f,
                center = Offset(compassRadius + 10f, 10f) // North
            )
        }
        
        // Zoom controls
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
        ) {
            Button(
                onClick = { zoomLevel = minOf(zoomLevel + 1, 20f) },
                modifier = Modifier.size(40.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("+", color = Color.White)
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Button(
                onClick = { zoomLevel = maxOf(zoomLevel - 1, 10f) },
                modifier = Modifier.size(40.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("-", color = Color.White)
            }
        }
        
        // Location info card
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
            )
        ) {
            Column(
                modifier = Modifier.padding(8.dp)
            ) {
                Text(
                    text = "📍 Selected Location",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
                Text(
                    text = "Lat: ${String.format("%.6f", selectedLocation?.latitude ?: currentLocation.latitude)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Lng: ${String.format("%.6f", selectedLocation?.longitude ?: currentLocation.longitude)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Zoom: ${zoomLevel.toInt()}x",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (isDragging) {
                    Text(
                        text = "🔄 Dragging...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
} 