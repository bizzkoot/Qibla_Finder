package com.bizzkoot.qiblafinder.ui.ar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun ARErrorScreen(
    errorType: ARErrorType,
    onRetry: () -> Unit,
    onNavigateBack: () -> Unit,
    onCalibrateClicked: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Error icon
            Icon(
                imageVector = when (errorType) {
                    ARErrorType.NOT_SUPPORTED -> Icons.Default.Info
                    ARErrorType.PERMISSION_DENIED -> Icons.Default.Info
                    ARErrorType.SESSION_FAILED -> Icons.Default.Settings
                },
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.error
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Error title
            Text(
                text = when (errorType) {
                    ARErrorType.NOT_SUPPORTED -> "AR Not Supported"
                    ARErrorType.PERMISSION_DENIED -> "Camera Permission Required"
                    ARErrorType.SESSION_FAILED -> "AR Session Failed"
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Error description
            Text(
                text = when (errorType) {
                    ARErrorType.NOT_SUPPORTED -> "Your device doesn't support ARCore. You can still use the compass and sun calibration features."
                    ARErrorType.PERMISSION_DENIED -> "Camera permission is required for AR features. Please grant camera permission in settings."
                    ARErrorType.SESSION_FAILED -> "Failed to initialize AR session. This might be due to insufficient lighting or camera issues."
                },
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Action buttons
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (errorType) {
                    ARErrorType.NOT_SUPPORTED -> {
                        Button(
                            onClick = onNavigateBack,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Use Compass Instead")
                        }
                        
                        OutlinedButton(
                            onClick = onCalibrateClicked,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Try Sun Calibration")
                        }
                    }
                    ARErrorType.PERMISSION_DENIED -> {
                        Button(
                            onClick = onRetry,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Grant Permission")
                        }
                        
                        OutlinedButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Go Back")
                        }
                    }
                    ARErrorType.SESSION_FAILED -> {
                        Button(
                            onClick = onRetry,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Try Again")
                        }
                        
                        OutlinedButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Use Compass")
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Helpful tips
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "💡 Tips:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = when (errorType) {
                            ARErrorType.NOT_SUPPORTED -> "• Use the compass for accurate Qibla direction\n• Try sun calibration for outdoor verification\n• Ensure good lighting conditions"
                            ARErrorType.PERMISSION_DENIED -> "• Go to Settings > Apps > Qibla Finder > Permissions\n• Enable Camera permission\n• Restart the app after granting permission"
                            ARErrorType.SESSION_FAILED -> "• Ensure good lighting conditions\n• Move to a well-lit area\n• Clean your camera lens\n• Restart the app"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

enum class ARErrorType {
    NOT_SUPPORTED,
    PERMISSION_DENIED,
    SESSION_FAILED
} 