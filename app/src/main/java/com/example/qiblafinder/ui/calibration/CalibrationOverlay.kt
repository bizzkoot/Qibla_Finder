package com.bizzkoot.qiblafinder.ui.calibration

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun CalibrationOverlay(
    isVisible: Boolean,
    calibrationProgress: Float,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(
            initialOffsetY = { -it },
            animationSpec = tween(300, easing = EaseOutCubic)
        ) + fadeIn(animationSpec = tween(300)),
        exit = slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = tween(300, easing = EaseInCubic)
        ) + fadeOut(animationSpec = tween(300))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.8f))
        ) {
            // Calibration card
            Card(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header
                    Text(
                        text = "Calibrate Compass",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Instructions
                    Text(
                        text = "Move your phone in a figure-8 pattern to calibrate the compass sensor",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Animated figure-8 pattern
                    CalibrationAnimation(
                        progress = calibrationProgress,
                        modifier = Modifier.size(120.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Progress indicator
                    LinearProgressIndicator(
                        progress = calibrationProgress,
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "${(calibrationProgress * 100).toInt()}% Complete",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Skip")
                        }
                        
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        Button(
                            onClick = onDismiss,
                            enabled = calibrationProgress >= 1f,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Done")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CalibrationAnimation(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "calibration")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    
    Canvas(modifier = modifier) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        val radius = size.minDimension / 3
        
        // Draw figure-8 pattern
        val path = androidx.compose.ui.graphics.Path()
        var firstPoint = true
        
        for (i in 0..360 step 5) {
            val angle = Math.toRadians(i.toDouble())
            val x = centerX + (radius * sin(angle) * cos(angle)).toFloat()
            val y = centerY + (radius * sin(angle) * cos(angle) * cos(angle)).toFloat()
            
            if (firstPoint) {
                path.moveTo(x, y)
                firstPoint = false
            } else {
                path.lineTo(x, y)
            }
        }
        
        // Draw the figure-8 path
        drawPath(
            path = path,
            color = primaryColor.copy(alpha = 0.3f),
            style = Stroke(width = 4f, cap = StrokeCap.Round)
        )
        
        // Draw animated dot following the path
        val progressAngle = progress * 720f // Two full rotations for figure-8
        val dotAngle = Math.toRadians(progressAngle.toDouble())
        val dotX = centerX + (radius * sin(dotAngle) * cos(dotAngle)).toFloat()
        val dotY = centerY + (radius * sin(dotAngle) * cos(dotAngle) * cos(dotAngle)).toFloat()
        
        drawCircle(
            color = primaryColor,
            radius = 8f,
            center = Offset(dotX, dotY)
        )
        
        // Draw phone icon with rotation
        val phoneSize = 20f
        val phoneX = centerX + (radius * 0.8f * cos(Math.toRadians(rotation.toDouble()))).toFloat()
        val phoneY = centerY + (radius * 0.8f * sin(Math.toRadians(rotation.toDouble()))).toFloat()
        
        drawRect(
            color = secondaryColor,
            topLeft = Offset(phoneX - phoneSize/2, phoneY - phoneSize/2),
            size = androidx.compose.ui.geometry.Size(phoneSize, phoneSize * 1.8f)
        )
    }
} 