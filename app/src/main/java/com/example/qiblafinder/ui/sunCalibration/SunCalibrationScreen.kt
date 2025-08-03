package com.bizzkoot.qiblafinder.ui.sunCalibration

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.bizzkoot.qiblafinder.sunCalibration.SunCalibrationUiState
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SunCalibrationScreen(
    uiState: SunCalibrationUiState,
    onCalibrate: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalContext.current as? LifecycleOwner
    
    Box(modifier = Modifier.fillMaxSize()) {
        // Camera preview background
        if (uiState is SunCalibrationUiState.Ready && uiState.isSunVisible) {
            CameraPreview(
                modifier = Modifier.fillMaxSize(),
                lifecycleOwner = lifecycleOwner
            )
        } else {
            // Fallback background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            )
        }
        
        // Overlay content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                            CircleShape
                        )
                        .size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                    )
                ) {
                    Text(
                        text = "Sun Calibration",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(48.dp))
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Sun position indicator
            if (uiState is SunCalibrationUiState.Ready) {
                SunPositionIndicator(
                    sunAzimuth = uiState.sunAzimuth,
                    sunElevation = uiState.sunElevation,
                    isSunVisible = uiState.isSunVisible,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Status and controls
            CalibrationControls(
                uiState = uiState,
                onCalibrate = onCalibrate,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    lifecycleOwner: LifecycleOwner?
) {
    val context = LocalContext.current
    
    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                this.scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        },
        modifier = modifier,
        update = { previewView ->
            lifecycleOwner?.let { owner ->
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    
                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                    
                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            owner,
                            cameraSelector,
                            preview
                        )
                    } catch (e: Exception) {
                        // Handle camera binding error
                    }
                }, ContextCompat.getMainExecutor(context))
            }
        }
    )
}

@Composable
fun SunPositionIndicator(
    sunAzimuth: Double,
    sunElevation: Double,
    isSunVisible: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isSunVisible) "☀️ Sun Position" else "🌙 Sun Not Visible",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (isSunVisible) {
                // Sun position visualization
                val primaryContainerColor = MaterialTheme.colorScheme.primaryContainer
                val onSurfaceColor = MaterialTheme.colorScheme.onSurface
                val primaryColor = MaterialTheme.colorScheme.primary
                
                Canvas(
                    modifier = Modifier
                        .size(120.dp)
                        .background(
                            primaryContainerColor.copy(alpha = 0.3f),
                            CircleShape
                        )
                ) {
                    val centerX = size.width / 2
                    val centerY = size.height / 2
                    val radius = size.minDimension / 2 * 0.8f
                    
                    // Draw horizon circle
                    drawCircle(
                        color = onSurfaceColor.copy(alpha = 0.3f),
                        radius = radius,
                        center = Offset(centerX, centerY),
                        style = Stroke(width = 2f)
                    )
                    
                    // Draw sun position
                    val sunAngle = Math.toRadians(sunAzimuth)
                    val sunDistance = radius * (1f - (sunElevation / 90f))
                    val sunX = centerX + (sunDistance * cos(sunAngle)).toFloat()
                    val sunY = centerY - (sunDistance * sin(sunAngle)).toFloat()
                    
                    drawCircle(
                        color = Color.Yellow,
                        radius = 12f,
                        center = Offset(sunX, sunY)
                    )
                    
                    // Draw direction indicator
                    drawLine(
                        color = primaryColor,
                        start = Offset(centerX, centerY),
                        end = Offset(sunX, sunY),
                        strokeWidth = 3f,
                        cap = StrokeCap.Round
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Azimuth: ${sunAzimuth.toInt()}°",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Text(
                    text = "Elevation: ${sunElevation.toInt()}°",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Text(
                    text = "Sun is below the horizon",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun CalibrationControls(
    uiState: SunCalibrationUiState,
    onCalibrate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            when (uiState) {
                is SunCalibrationUiState.Loading -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Calculating sun position...")
                    }
                }
                is SunCalibrationUiState.Ready -> {
                    if (uiState.isSunVisible) {
                        Text(
                            text = "Point your phone towards the sun and tap calibrate",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Button(
                            onClick = onCalibrate,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = uiState.isSunVisible
                        ) {
                            Text("Calibrate Compass")
                        }
                    } else {
                        Text(
                            text = "Sun is not visible. Try during daylight hours.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                is SunCalibrationUiState.Error -> {
                    Text(
                        text = "Error: ${uiState.message}",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                is SunCalibrationUiState.Calibrated -> {
                    Text(
                        text = "Calibration completed successfully!",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}