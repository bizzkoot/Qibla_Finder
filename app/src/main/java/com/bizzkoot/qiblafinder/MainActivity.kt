package com.bizzkoot.qiblafinder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.bizzkoot.qiblafinder.navigation.QiblaNavHost
import com.bizzkoot.qiblafinder.navigation.rememberQiblaAppState
import com.bizzkoot.qiblafinder.permissions.PermissionManager
import com.bizzkoot.qiblafinder.ui.permissions.PermissionScreen
import com.bizzkoot.qiblafinder.model.LocationRepository
import com.bizzkoot.qiblafinder.model.SensorRepository
import timber.log.Timber

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Timber.d("🎯 MainActivity - onCreate() called")
        setContent {
            QiblaFinderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    QiblaApp()
                }
            }
        }
    }
}

@Composable
fun QiblaApp() {
    val context = LocalContext.current
    val permissionManager = remember { PermissionManager(context) }
    val permissionState by permissionManager.permissionState.collectAsState()
    
    var permissionsGranted by remember { mutableStateOf(false) }
    
    LaunchedEffect(permissionState) {
        permissionsGranted = permissionManager.hasRequiredPermissions()
        Timber.d("🎯 QiblaApp - Permissions granted: $permissionsGranted")
    }
    
    if (!permissionsGranted) {
        Timber.d("🎯 QiblaApp - Showing PermissionScreen")
        PermissionScreen(
            permissionManager = permissionManager,
            onPermissionsGranted = {
                permissionsGranted = true
                Timber.d("🎯 QiblaApp - Permissions granted, switching to main app")
            }
        )
    } else {
        // NORMAL NAVIGATION MODE - Use shared repositories
        Timber.d("🎯 QiblaApp - Starting normal navigation mode")
        
        val sharedLocationRepository = remember { 
            Timber.d("🎯 QiblaApp - Creating sharedLocationRepository")
            LocationRepository(context) 
        }
        val sharedSensorRepository = remember { 
            Timber.d("🎯 QiblaApp - Creating sharedSensorRepository")
            SensorRepository(context, sharedLocationRepository) 
        }
        
        Timber.d("🎯 QiblaApp - Repositories created: Location=$sharedLocationRepository, Sensor=$sharedSensorRepository")
        
        val appState = rememberQiblaAppState()
        
        QiblaNavHost(
            navController = appState.navController,
            sharedLocationRepository = sharedLocationRepository,
            sharedSensorRepository = sharedSensorRepository
        )
    }
}

@Composable
fun QiblaFinderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme,
        typography = MaterialTheme.typography,
        content = content
    )
}