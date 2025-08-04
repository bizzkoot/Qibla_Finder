package com.bizzkoot.qiblafinder.ui.compass

import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bizzkoot.qiblafinder.model.CompassStatus
import timber.log.Timber
import com.bizzkoot.qiblafinder.model.LocationAccuracy
import com.bizzkoot.qiblafinder.model.LocationState
import com.bizzkoot.qiblafinder.model.OrientationState
import com.bizzkoot.qiblafinder.ui.calibration.CalibrationOverlay

@Composable
fun CompassScreen(
    viewModel: CompassViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onNavigateToSunCalibration: (() -> Unit)? = null,
    onNavigateToAR: (() -> Unit)? = null,
    onNavigateToManualLocation: (() -> Unit)? = null,
    onNavigateToTroubleshooting: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val locationState = uiState.locationState
    val orientationState = uiState.orientationState
    val qiblaBearing = uiState.qiblaBearing
    val distanceToKaaba = uiState.distanceToKaaba
    val isSunCalibrated = uiState.isSunCalibrated
    val isManualLocation = uiState.isManualLocation
    val showCalibration by viewModel.showCalibration.collectAsState()
    

    
    // Debug logging for UI state changes
    LaunchedEffect(orientationState) {
        Timber.d("📱 CompassScreen - Orientation state changed: $orientationState")
    }
    


    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Status bar
            StatusBar(
                locationState = locationState,
                orientationState = orientationState,
                isSunCalibrated = isSunCalibrated,
                isManualLocation = isManualLocation
            )

            // Compass graphic
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                // Check if arrows are aligned (within 5 degrees tolerance) and phone is NOT flat (reversed logic)
                val isAligned = qiblaBearing?.let { qibla ->
                    val deviceRotation = when (val oState = orientationState) {
                        is OrientationState.Initializing -> 0f
                        is OrientationState.Available -> oState.trueHeading
                    }
                    val difference = kotlin.math.abs(deviceRotation - qibla)
                    val isPhoneFlat = when (val oState = orientationState) {
                        is OrientationState.Initializing -> false
                        is OrientationState.Available -> oState.isPhoneFlat
                    }
                    (difference <= 5f || difference >= 355f) && !isPhoneFlat
                } ?: false
                
                CompassGraphic(
                    orientationState = orientationState,
                    qiblaBearing = qiblaBearing,
                    isAligned = isAligned
                )
                
                // Show red alert when phone IS flat (reversed logic due to axis setup)
                when (val oState = orientationState) {
                    is OrientationState.Available -> {
                        if (oState.isPhoneFlat) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                // Semi-transparent red background
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Red.copy(alpha = 0.1f))
                                )
                                
                                // Alert message
                                Card(
                                    modifier = Modifier
                                        .padding(32.dp)
                                        .background(Color.Red.copy(alpha = 0.9f), RoundedCornerShape(16.dp)),
                                    colors = CardDefaults.cardColors(containerColor = Color.Red)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = "Phone not flat warning",
                                            tint = Color.White,
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            text = "⚠️ RED ALERT",
                                            color = Color.White,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Please lay your phone FLAT to ensure accurate Qibla reading",
                                            color = Color.White,
                                            fontSize = 16.sp,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Current tilt: ${oState.phoneTiltAngle.toInt()}°",
                                            color = Color.White.copy(alpha = 0.8f),
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                    else -> { /* No alert for initializing state */ }
                }
                
                // Display current heading and instructions
                when (val oState = orientationState) {
                    is OrientationState.Available -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Heading: ${oState.trueHeading.toInt()}°",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(16.dp)
                            )
                            if (isAligned) {
                                Text(
                                    text = "✅ Qibla Found! Face this direction to pray",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Green,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            } else {
                                Text(
                                    text = "Align blue arrow with red arrow",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                                Text(
                                    text = "Then face 12 o'clock position",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }
                    }
                    else -> {
                        Text(
                            text = "Initializing compass...",
                            fontSize = 16.sp,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            // Location and distance info
            LocationInfo(
                locationState = locationState,
                distanceToKaaba = distanceToKaaba
            )

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        viewModel.startCalibration()
                    },
                    modifier = Modifier.weight(1f),
                    enabled = orientationState is OrientationState.Available
                ) {
                    Text("Calibrate")
                }
                
                Button(
                    onClick = { onNavigateToSunCalibration?.invoke() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Sun Calibration")
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { onNavigateToAR?.invoke() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("AR View")
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Additional action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                
                Button(
                    onClick = { 
                        Timber.d("🎯 CompassScreen - Manual Location button clicked - NAVIGATION APPROACH")
                        onNavigateToManualLocation?.invoke()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Manual Location")
                }
                
                Button(
                    onClick = { onNavigateToTroubleshooting?.invoke() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Help")
                }
            }

            if (isManualLocation) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.revertToGps() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Revert to GPS")
                }
            }
            

            

        }
        
        // Calibration overlay
        CalibrationOverlay(
            isVisible = showCalibration,
            onDismiss = { viewModel.stopCalibration() }
        )
    }
}

@Composable
fun StatusBar(
    locationState: LocationState,
    orientationState: OrientationState,
    isSunCalibrated: Boolean = false,
    isManualLocation: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Location status
        val locationText = if (isManualLocation) {
            "📍 Manual Location"
        } else {
            when (locationState) {
                is LocationState.Loading -> "📍 Searching GPS..."
                is LocationState.Available -> {
                    when (locationState.accuracyLevel) {
                        LocationAccuracy.HIGH_ACCURACY -> "📍 GPS (±${locationState.accuracy.toInt()}m)"
                        LocationAccuracy.MEDIUM_ACCURACY -> "📍 Network (±${locationState.accuracy.toInt()}m)"
                        LocationAccuracy.LOW_ACCURACY -> "📍 Approximate (±${locationState.accuracy.toInt()}m)"
                        LocationAccuracy.UNKNOWN -> "📍 Unknown accuracy"
                    }
                }
                is LocationState.Error -> "📍 Location Error"
                is LocationState.PermissionDenied -> "📍 Permission Denied"
            }
        }
        Text(text = locationText, fontSize = 14.sp)

        // Compass status
        val compassText = when (orientationState) {
            is OrientationState.Initializing -> "🔄 Initializing..."
            is OrientationState.Available -> {
                when (orientationState.compassStatus) {
                    CompassStatus.OK -> if (isSunCalibrated) "✅ Sun Calibrated" else "✅ Calibrated"
                    CompassStatus.NEEDS_CALIBRATION -> "⚠️ Needs Calibration"
                    CompassStatus.INTERFERENCE -> "⚠️ Interference"
                }
            }
        }
        Text(text = compassText, fontSize = 14.sp)
    }
}

@Composable
fun CompassGraphic(
    orientationState: OrientationState,
    qiblaBearing: Float?,
    isAligned: Boolean
) {
    val animatableRotation = remember { Animatable(0f) }

    val previousAngle = remember { mutableStateOf(0f) }

    LaunchedEffect(orientationState) {
        if (orientationState is OrientationState.Available) {
            val currentAngle = orientationState.trueHeading
            val previous = previousAngle.value
            
            // Determine the number of full rotations (laps)
            val laps = (previous / 360).toInt()
            var targetRotation = laps * 360 + currentAngle

            // Find the shortest path by checking the adjacent laps
            val diff = targetRotation - previous
            if (diff > 180) {
                targetRotation -= 360
            } else if (diff < -180) {
                targetRotation += 360
            }

            previousAngle.value = targetRotation
            animatableRotation.animateTo(
                targetValue = targetRotation,
                animationSpec = tween(durationMillis = 300, easing = androidx.compose.animation.core.LinearEasing)
            )
        }
    }
    
    // Additional debug logging for orientation state

    Box(
        modifier = Modifier.size(300.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier.size(300.dp)
        ) {
            Timber.d("🎨 Canvas drawing - Device rotation: ${animatableRotation.value}°")
            val centerX = size.width / 2
            val centerY = size.height / 2
            val radius = size.minDimension / 2 * 0.8f
            


            // Draw compass circle (green when aligned, gray otherwise)
            drawCircle(
                color = if (isAligned) Color.Green else Color.LightGray,
                radius = radius,
                center = Offset(centerX, centerY),
                style = Stroke(width = if (isAligned) 6f else 4f)
            )

            // Draw cardinal directions
            val directions = listOf("N", "E", "S", "W")
            val directionAngles = listOf(0f, 90f, 180f, 270f)
            
            directions.forEachIndexed { index, direction ->
                // Convert compass coordinates (0° = North) to screen coordinates (0° = right, 90° = down)
                // We need to subtract 90° to align North with the top of the screen
                val screenAngle = directionAngles[index] - animatableRotation.value - 90f
                val angleRad = Math.toRadians(screenAngle.toDouble())
                val textX = centerX + (radius * 0.7f * Math.cos(angleRad)).toFloat()
                val textY = centerY + (radius * 0.7f * Math.sin(angleRad)).toFloat()
                
                drawContext.canvas.nativeCanvas.drawText(
                    direction,
                    textX,
                    textY,
                    android.graphics.Paint().apply {
                        textSize = 32f
                        color = Color.Black.hashCode()
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                )
            }

            // Draw Qibla direction if available
            qiblaBearing?.let { qibla ->
                // Qibla bearing shows the heading you need to face
                // When blue arrow matches red arrow, 12 o'clock points to Mecca
                val screenAngle = qibla - 90f
                val qiblaAngleRad = Math.toRadians(screenAngle.toDouble())
                val qiblaX = centerX + (radius * 0.6f * Math.cos(qiblaAngleRad)).toFloat()
                val qiblaY = centerY + (radius * 0.6f * Math.sin(qiblaAngleRad)).toFloat()
                
                // Draw line to Qibla
                drawLine(
                    color = Color.Red,
                    start = Offset(centerX, centerY),
                    end = Offset(qiblaX, qiblaY),
                    strokeWidth = 8f
                )
                
                // Draw Qibla marker
                drawCircle(
                    color = Color.Red,
                    radius = 12f,
                    center = Offset(qiblaX, qiblaY)
                )
                
                // Only show text when aligned (remove confusing "Face This Direction")
                if (isAligned) {
                    drawContext.canvas.nativeCanvas.drawText(
                        "Qibla Found!",
                        qiblaX,
                        qiblaY - 20,
                        android.graphics.Paint().apply {
                            textSize = 20f
                            color = Color.Green.hashCode()
                            textAlign = android.graphics.Paint.Align.CENTER
                        }
                    )
                }
            }

            // Draw compass needle (device direction indicator)
            // The needle should point to the direction the device is facing
            val needleLength = radius * 0.9f
            // Convert compass coordinates to screen coordinates
            val screenAngle = animatableRotation.value - 90f
            val needleAngleRad = Math.toRadians(screenAngle.toDouble())
            
            // Draw main needle line
            drawLine(
                color = Color.Blue,
                start = Offset(centerX, centerY),
                end = Offset(
                    centerX + (needleLength * Math.cos(needleAngleRad)).toFloat(),
                    centerY + (needleLength * Math.sin(needleAngleRad)).toFloat()
                ),
                strokeWidth = 8f
            )
            
            // Draw needle arrowhead
            val arrowheadLength = 20f
            val arrowheadAngle1 = needleAngleRad + Math.toRadians(150.0)
            val arrowheadAngle2 = needleAngleRad - Math.toRadians(150.0)
            
            val needleEndX = centerX + (needleLength * Math.cos(needleAngleRad)).toFloat()
            val needleEndY = centerY + (needleLength * Math.sin(needleAngleRad)).toFloat()
            
            // Draw arrowhead lines
            drawLine(
                color = Color.Blue,
                start = Offset(needleEndX, needleEndY),
                end = Offset(
                    needleEndX + (arrowheadLength * Math.cos(arrowheadAngle1)).toFloat(),
                    needleEndY + (arrowheadLength * Math.sin(arrowheadAngle1)).toFloat()
                ),
                strokeWidth = 6f
            )
            
            drawLine(
                color = Color.Blue,
                start = Offset(needleEndX, needleEndY),
                end = Offset(
                    needleEndX + (arrowheadLength * Math.cos(arrowheadAngle2)).toFloat(),
                    needleEndY + (arrowheadLength * Math.sin(arrowheadAngle2)).toFloat()
                ),
                strokeWidth = 6f
            )
            
            // Draw center dot
            drawCircle(
                color = Color.Blue,
                radius = 8f,
                center = Offset(centerX, centerY)
            )
            
            // Draw green arrow at 12 o'clock when aligned (shows direction to face)
            if (isAligned) {
                val arrowY = centerY - radius * 0.9f
                val arrowLength = 40f
                val arrowAngleRad = Math.toRadians(-90.0) // Pointing up (12 o'clock)
                
                // Draw main arrow line
                drawLine(
                    color = Color.Green,
                    start = Offset(centerX, arrowY + arrowLength),
                    end = Offset(centerX, arrowY),
                    strokeWidth = 12f
                )
                
                // Draw arrowhead
                val greenArrowheadLength = 25f
                val greenArrowheadAngle1 = arrowAngleRad + Math.toRadians(150.0)
                val greenArrowheadAngle2 = arrowAngleRad - Math.toRadians(150.0)
                
                // Draw arrowhead lines
                drawLine(
                    color = Color.Green,
                    start = Offset(centerX, arrowY),
                    end = Offset(
                        centerX + (greenArrowheadLength * Math.cos(greenArrowheadAngle1)).toFloat(),
                        arrowY + (greenArrowheadLength * Math.sin(greenArrowheadAngle1)).toFloat()
                    ),
                    strokeWidth = 10f
                )
                
                drawLine(
                    color = Color.Green,
                    start = Offset(centerX, arrowY),
                    end = Offset(
                        centerX + (greenArrowheadLength * Math.cos(greenArrowheadAngle2)).toFloat(),
                        arrowY + (greenArrowheadLength * Math.sin(greenArrowheadAngle2)).toFloat()
                    ),
                    strokeWidth = 10f
                )
            }
            
            // Draw Kaaba logo outside compass circle when aligned (2x larger)
            if (isAligned) {
                val kaabaY = centerY - radius * 1.3f // Further outside the circle
                val kaabaRadius = 50f // 2x larger than before (25f * 2)
                
                // Draw background circle
                drawCircle(
                    color = Color.Green,
                    radius = kaabaRadius,
                    center = Offset(centerX, kaabaY)
                )
                
                // Draw Kaaba logo
                drawContext.canvas.nativeCanvas.drawText(
                    "🕋",
                    centerX,
                    kaabaY + 20, // Adjusted for larger circle
                    android.graphics.Paint().apply {
                        textSize = 72f // 2x larger than before (36f * 2)
                        color = Color.White.hashCode()
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                )
            }
        }
    }
}

@Composable
fun LocationInfo(
    locationState: LocationState,
    distanceToKaaba: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (locationState) {
            is LocationState.Loading -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Searching for GPS signal...", fontSize = 16.sp)
                }
            }
            is LocationState.Available -> {
                Text(
                    text = "Your Location:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text(
                    text = "Lat: ${"%.4f".format(locationState.location.latitude)}",
                    fontSize = 16.sp
                )
                Text(
                    text = "Lng: ${"%.4f".format(locationState.location.longitude)}",
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "GPS Accuracy:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    // Show green checkmark if accuracy is sufficient for prayer (≤10m)
                    if (locationState.accuracy <= 10f) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "GPS accuracy sufficient for prayer",
                            tint = Color.Green,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Text(
                    text = when (locationState.accuracyLevel) {
                        LocationAccuracy.HIGH_ACCURACY -> "High (±${locationState.accuracy.toInt()}m) ✅ Sufficient for prayer"
                        LocationAccuracy.MEDIUM_ACCURACY -> "Medium (±${locationState.accuracy.toInt()}m) ⚠️ Consider moving to open area"
                        LocationAccuracy.LOW_ACCURACY -> "Low (±${locationState.accuracy.toInt()}m) ❌ Move to open area"
                        LocationAccuracy.UNKNOWN -> "Unknown"
                    },
                    fontSize = 14.sp,
                    color = when (locationState.accuracyLevel) {
                        LocationAccuracy.HIGH_ACCURACY -> Color.Green
                        LocationAccuracy.MEDIUM_ACCURACY -> Color(0xFFFF8C00) // Orange
                        LocationAccuracy.LOW_ACCURACY -> Color.Red
                        LocationAccuracy.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Distance to Kaaba:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text(
                    text = distanceToKaaba,
                    fontSize = 16.sp
                )
            }
            is LocationState.Error -> {
                Text("Location Error: ${locationState.message}")
            }
            is LocationState.PermissionDenied -> {
                Text("Location permission denied. Please grant location permission in settings.")
            }
        }
    }
}