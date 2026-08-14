package com.bizzkoot.qiblafinder.ui.permissions

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.bizzkoot.qiblafinder.permissions.PermissionManager
import com.bizzkoot.qiblafinder.permissions.PermissionState

@Composable
fun PermissionScreen(
    permissionManager: PermissionManager,
    onPermissionsGranted: () -> Unit
) {
    val context = LocalContext.current
    val permissionState by permissionManager.permissionState.collectAsState()
    // PRD M8: once a required permission is denied twice (no rationale shown
    // anymore), the in-app request can never succeed again — offer the system
    // settings screen as the escape hatch.
    var showOpenSettings by remember { mutableStateOf(false) }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissionManager.updatePermissionState()
        
        // Check if all required permissions are granted
        if (permissionManager.hasRequiredPermissions()) {
            onPermissionsGranted()
        } else {
            // Permanently denied = denied AND the system will no longer show the
            // rationale dialog (shouldShowRequestPermissionRationale == false).
            val activity = context as? Activity
            showOpenSettings = activity != null && permissionManager.getRequiredPermissions().any { permission ->
                ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED &&
                    !androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
            }
        }
    }
    
    LaunchedEffect(permissionState) {
        if (permissionManager.hasRequiredPermissions()) {
            onPermissionsGranted()
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Qibla Finder",
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "Location permission is required for the compass. Camera is optional — only needed for AR view and sun calibration:",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Permission status cards
        PermissionStatusCard(
            title = "Location Permission",
            description = "Required to calculate the Qibla direction from your current location",
            isGranted = permissionState.locationGranted,
            accuracy = permissionState.locationAccuracy
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        PermissionStatusCard(
            title = "Camera Permission", 
            description = "Optional — only needed for AR view and sun calibration",
            isGranted = permissionState.cameraGranted,
            accuracy = null,
            deniedLabel = "✗ Optional"
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = {
                permissionLauncher.launch(permissionManager.getRequiredPermissions())
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Grant Permissions")
        }
        
        if (!permissionState.locationGranted) {
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Some permissions are required for the app to function properly. " +
                       "Please grant all permissions to continue.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.error
            )
        }
        
        if (showOpenSettings) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            "package:${context.packageName}".toUri()
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open Settings")
            }
        }
    }
}

@Composable
private fun PermissionStatusCard(
    title: String,
    description: String,
    isGranted: Boolean,
                accuracy: com.bizzkoot.qiblafinder.permissions.LocationAccuracy?,
                deniedLabel: String = "✗ Required"
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                
                Spacer(modifier = Modifier.weight(1f))
                
                Text(
                    text = if (isGranted) "✓ Granted" else deniedLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isGranted) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.error
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium
            )
            
            accuracy?.let { acc ->
                Spacer(modifier = Modifier.height(8.dp))
                
                val accuracyText = when (acc) {
                    com.bizzkoot.qiblafinder.permissions.LocationAccuracy.HIGH_ACCURACY -> "High Accuracy (GPS)"
                    com.bizzkoot.qiblafinder.permissions.LocationAccuracy.APPROXIMATE -> "Approximate (Network)"
                    com.bizzkoot.qiblafinder.permissions.LocationAccuracy.DENIED -> "Permission Denied"
                    com.bizzkoot.qiblafinder.permissions.LocationAccuracy.UNKNOWN -> "Unknown"
                }
                
                Text(
                    text = "Accuracy: $accuracyText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
} 
