package com.bizzkoot.qiblafinder.ui.compass

import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.offset
import com.bizzkoot.qiblafinder.model.CompassStatus
import timber.log.Timber
import com.bizzkoot.qiblafinder.model.LocationAccuracy
import com.bizzkoot.qiblafinder.model.LocationState
import com.bizzkoot.qiblafinder.model.NOT_FLAT_TILT_MAX_DEGREES
import com.bizzkoot.qiblafinder.model.NOT_FLAT_TILT_MIN_DEGREES
import com.bizzkoot.qiblafinder.model.OrientationState
import com.bizzkoot.qiblafinder.ui.calibration.CalibrationOverlay
import com.bizzkoot.qiblafinder.ui.theme.QiblaTypography
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlin.math.abs

@Composable
fun CompassScreen(
    viewModel: CompassViewModel,
    onNavigateToSunCalibration: (() -> Unit)? = null,
    onNavigateToAR: (() -> Unit)? = null,
    onNavigateToManualLocation: (() -> Unit)? = null,
    onNavigateToTroubleshooting: (() -> Unit)? = null,
    keepScreenOn: Boolean = false,
    onToggleKeepScreenOn: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val locationState = uiState.locationState
    val orientationState = uiState.orientationState
    val qiblaBearing = uiState.qiblaBearing
    val distanceToKaaba = uiState.distanceToKaaba
    val isSunCalibrated = uiState.isSunCalibrated
    val isManualLocation = uiState.isManualLocation
    val showCalibration by viewModel.showCalibration.collectAsState()
    val typography = QiblaTypography.current

    // Lifecycle gate: pause the compass sensor stream whenever this screen is no
    // longer RESUMED (AR / Sun Calibration / Manual Location / Help pushed on top,
    // or the app backgrounded). Inside a NavHost, LocalLifecycleOwner is the
    // NavBackStackEntry lifecycle, which drops to STARTED (ON_PAUSE) as soon as
    // another destination covers this one, and returns to RESUMED (ON_RESUME)
    // when the user comes back.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.onScreenVisible(true)
                Lifecycle.Event.ON_PAUSE -> viewModel.onScreenVisible(false)
                // ON_STOP = the whole app is backgrounded (a covering route only
                // pauses this entry). Release the shared GPS callback there so the
                // device stops fixing location while nothing is on screen.
                Lifecycle.Event.ON_STOP -> viewModel.onScreenStopped()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        // Seed screenVisible from the CURRENT lifecycle state: the observer only
        // fires on transitions after registration, so a compass entry restored onto
        // the back stack (process-death) would otherwise stay "visible=true" with
        // its sensors running while covered by another route (W5).
        viewModel.onScreenVisible(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    

    
    // Debug logging for UI state changes
    LaunchedEffect(orientationState) {
        Timber.d("📱 CompassScreen - Orientation state changed: $orientationState")
    }
    


    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Status bar
            StatusBar(
                locationState = locationState,
                orientationState = orientationState,
                isSunCalibrated = isSunCalibrated,
                isManualLocation = isManualLocation,
                keepScreenOn = keepScreenOn,
                onToggleKeepScreenOn = onToggleKeepScreenOn
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
                        is OrientationState.Error -> 0f
                        is OrientationState.Available -> oState.trueHeading
                    }
                    val difference = angleDiff(deviceRotation, qibla)
                    val isPhoneUpright = when (val oState = orientationState) {
                        is OrientationState.Initializing -> false
                        is OrientationState.Error -> false
                        is OrientationState.Available -> oState.isPhoneUpright
                    }
                    // The arrow only counts as "aligned" while the phone is actually
                    // FLAT (isPhoneUpright == true means the phone is held upright).
                    (difference <= 5f) && !isPhoneUpright
                } ?: false
                
                CompassGraphic(
                    orientationState = orientationState,
                    qiblaBearing = qiblaBearing,
                    isAligned = isAligned
                )

                // Compact bubble level below the compass center: it remains visible
                // without covering the Qibla arrow and gives an immediate flatness
                // cue while the phone is being placed on the ground.
                WaterLevelIndicator(
                    levelX = (orientationState as? OrientationState.Available)?.levelX ?: 0f,
                    levelY = (orientationState as? OrientationState.Available)?.levelY ?: 0f,
                    modifier = Modifier.align(Alignment.Center).offset(y = 168.dp)
                )
                
                // Show red alert when the phone is NOT flat (held upright/vertical).
                // Driven directly from phoneTiltAngle using the shared 65..115° band
                // (NOT_FLAT_TILT_MIN/MAX_DEGREES in SensorRepository) so the tilt
                // band is self-documenting and cannot drift from the detector.
                when (val oState = orientationState) {
                    is OrientationState.Available -> {
                        if (isPhoneUpright(oState.phoneTiltAngle)) {
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
                                            style = typography.titleSecondary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Please lay your phone FLAT to ensure accurate Qibla reading",
                                            color = Color.White,
                                            style = typography.bodyPrimary,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Current tilt: ${oState.phoneTiltAngle.toInt()}°",
                                            color = Color.White.copy(alpha = 0.8f),
                                            style = typography.bodySecondary
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
                                style = typography.titleTertiary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(16.dp)
                            )
                            if (isAligned) {
                                Text(
                                    text = "✅ Qibla Found! Face this direction to pray",
                                    style = typography.bodyPrimary,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Green,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            } else {
                                Text(
                                    text = "Align blue arrow with red arrow",
                                    style = typography.bodySecondary,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                                Text(
                                    text = "Then face 12 o'clock position",
                                    style = typography.bodySecondary,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }
                    }
                    is OrientationState.Error -> {
                        // PRD M8: the sensor stream failed (rotation-vector sensor
                        // absent or registration failure). Offer a Retry instead of
                        // leaving the user staring at "Initializing..." forever.
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "⚠️ Compass sensor unavailable",
                                style = typography.bodyPrimary,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "The orientation sensor could not be started. Retry to attempt reconnecting.",
                                style = typography.bodySecondary,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(onClick = { viewModel.retrySensors() }) {
                                Text("Retry Sensors")
                            }
                        }
                    }
                    else -> {
                        Text(
                            text = "Initializing compass...",
                            style = typography.bodyPrimary,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            // Location and distance info
            LocationInfo(
                locationState = locationState,
                distanceToKaaba = distanceToKaaba,
                onRetryLocation = viewModel::retryLocation
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

private fun angleDiff(a: Float, b: Float): Float {
    val d = ((a - b + 540f) % 360f) - 180f
    return kotlin.math.abs(d)
}

/**
 * True when the tilt angle (degrees from flat) puts the phone in the "upright /
 * NOT flat" band (65..115°). Mirrors SensorRepository.checkPhoneOrientation so the
 * red alert and the detector agree; see NOT_FLAT_TILT_MIN/MAX_DEGREES.
 */
private fun isPhoneUpright(tiltAngle: Float): Boolean =
    tiltAngle >= NOT_FLAT_TILT_MIN_DEGREES && tiltAngle <= NOT_FLAT_TILT_MAX_DEGREES

@Composable
fun StatusBar(
    locationState: LocationState,
    orientationState: OrientationState,
    isSunCalibrated: Boolean = false,
    isManualLocation: Boolean = false,
    keepScreenOn: Boolean = false,
    onToggleKeepScreenOn: (() -> Unit)? = null
) {
    val typography = QiblaTypography.current
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
        Text(text = locationText, style = typography.bodySecondary)

        // Compass status
        val compassText = when (orientationState) {
            is OrientationState.Initializing -> "🔄 Initializing..."
            is OrientationState.Error -> "⚠️ Sensor Unavailable"
            is OrientationState.Available -> {
                when (orientationState.compassStatus) {
                    CompassStatus.OK -> if (isSunCalibrated) "✅ Sun Calibrated" else "✅ Calibrated"
                    CompassStatus.NEEDS_CALIBRATION -> "⚠️ Needs Calibration"
                    CompassStatus.INTERFERENCE -> "⚠️ Interference"
                }
            }
        }
        Text(text = compassText, style = typography.bodySecondary)

        // Keep-screen-on toggle (screen stays awake while the compass is open)
        if (onToggleKeepScreenOn != null) {
            IconButton(
                onClick = onToggleKeepScreenOn,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = if (keepScreenOn) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                    contentDescription = if (keepScreenOn) "Keep screen on" else "Screen timeout active",
                    tint = if (keepScreenOn) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

@Composable
fun CompassGraphic(
    orientationState: OrientationState,
    qiblaBearing: Float?,
    isAligned: Boolean
) {
    // Theme-aware canvas text color: adapts to light/dark (MaterialTheme colorScheme)
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface

    var targetRotation by remember { mutableStateOf(0f) }
    var lastDelta by remember { mutableStateOf(0f) }
    val lastLogElapsed = remember { mutableStateOf(0L) }

    LaunchedEffect(orientationState) {
        if (orientationState is OrientationState.Available) {
            val currentAngle = orientationState.trueHeading
            val previous = targetRotation

            val laps = (previous / 360f).toInt()
            var candidate = laps * 360f + currentAngle
            val diff = candidate - previous
            if (diff > 180f) {
                candidate -= 360f
            } else if (diff < -180f) {
                candidate += 360f
            }

            lastDelta = abs(candidate - previous)
            targetRotation = candidate
        }
    }

    val animationSpec: AnimationSpec<Float> = when {
        lastDelta < 1f -> snap()
        lastDelta >= 90f -> snap()
        else -> spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioNoBouncy)
    }

    val animatedRotation by animateFloatAsState(
        targetValue = targetRotation,
        animationSpec = animationSpec,
        label = "CompassRotation"
    )

    Box(
        modifier = Modifier.size(300.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier.size(300.dp)
        ) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastLogElapsed.value >= 1000L) {
                Timber.d("🎨 Canvas drawing - Device rotation: %.1f°", animatedRotation)
                lastLogElapsed.value = now
            }
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
                val screenAngle = directionAngles[index] - animatedRotation - 90f
                val angleRad = Math.toRadians(screenAngle.toDouble())
                val textX = centerX + (radius * 0.7f * Math.cos(angleRad)).toFloat()
                val textY = centerY + (radius * 0.7f * Math.sin(angleRad)).toFloat()
                
                drawContext.canvas.nativeCanvas.drawText(
                    direction,
                    textX,
                    textY,
                    android.graphics.Paint().apply {
                        textSize = 32f
                        color = onSurfaceColor.hashCode()
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
            val screenAngle = animatedRotation - 90f
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
                val kaabaRadius = 50f // 2x larger than before (25f * 2)
                // Clamp so the marker is never clipped off-canvas: the previous
                // centerY - radius*1.3f put it above the top edge at every density.
                val kaabaY = (centerY - radius * 1.3f).coerceAtLeast(kaabaRadius)
                
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
    distanceToKaaba: String,
    onRetryLocation: () -> Unit = {}
) {
    val typography = QiblaTypography.current
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
                    Text("Searching for GPS signal...", style = typography.bodyPrimary)
                }
            }
            is LocationState.Available -> {
                Text(
                    text = "Your Location:",
                    fontWeight = FontWeight.Bold,
                    style = typography.titleTertiary
                )
                Text(
                    text = "Lat: ${"%.4f".format(locationState.location.latitude)}",
                    style = typography.bodyPrimary
                )
                Text(
                    text = "Lng: ${"%.4f".format(locationState.location.longitude)}",
                    style = typography.bodyPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "GPS Accuracy:",
                        fontWeight = FontWeight.Bold,
                        style = typography.bodyPrimary
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
                    style = typography.bodySecondary,
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
                    style = typography.titleTertiary
                )
                Text(
                    text = distanceToKaaba,
                    style = typography.bodyPrimary
                )
            }
            is LocationState.Error -> {
                Text("Location Error: ${locationState.message}")
                Spacer(modifier = Modifier.height(8.dp))
                // PRD M8: GPS can fail transiently (no fix / timeout); a Retry
                // re-requests updates instead of leaving a dead-end error state.
                Button(onClick = onRetryLocation) {
                    Text("Retry Location")
                }
            }
            is LocationState.PermissionDenied -> {
                Text("Location permission denied. Please grant location permission in settings.")
            }
        }
    }
}
