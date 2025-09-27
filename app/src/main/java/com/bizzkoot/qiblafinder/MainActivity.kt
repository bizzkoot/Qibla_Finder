package com.bizzkoot.qiblafinder

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.bizzkoot.qiblafinder.model.LocationRepository
import com.bizzkoot.qiblafinder.model.SensorRepository
import com.bizzkoot.qiblafinder.navigation.QiblaNavHost
import com.bizzkoot.qiblafinder.navigation.rememberQiblaAppState
import com.bizzkoot.qiblafinder.permissions.PermissionManager
import com.bizzkoot.qiblafinder.ui.permissions.PermissionScreen
import com.bizzkoot.qiblafinder.update.ui.UpdateNotificationBanner
import com.bizzkoot.qiblafinder.update.ui.EnhancedUpdateNotificationBanner
import com.bizzkoot.qiblafinder.update.services.EnhancedDownloadManager
import com.bizzkoot.qiblafinder.update.viewmodel.UpdateNotificationViewModel
import com.bizzkoot.qiblafinder.ui.theme.QiblaFinderTheme
import timber.log.Timber

class MainActivity : ComponentActivity() {
    
    private lateinit var enhancedDownloadManager: EnhancedDownloadManager

    private val updateNotificationViewModel: UpdateNotificationViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = application as QiblaFinderApplication
                @Suppress("UNCHECKED_CAST")
                return UpdateNotificationViewModel(app.updateNotificationRepository, enhancedDownloadManager) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Edge-to-edge: keep system bars visible; screens pad via insets
        enableEdgeToEdge()
        
        // Initialize enhanced download manager
        enhancedDownloadManager = EnhancedDownloadManager(this)
        
        Timber.d("🎯 MainActivity - onCreate() called with edge-to-edge")
        setContent {
            QiblaFinderTheme {
                androidx.compose.material3.Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.background
                ) {
                    val updateUiState by updateNotificationViewModel.uiState.collectAsState()
                    
                    // Main content box - let individual screens handle their own insets
                    Box(modifier = Modifier.fillMaxSize()) {
                        QiblaApp(updateNotificationViewModel, enhancedDownloadManager)
                        
                        // Update notification positioned properly with insets
                        if (updateUiState.showNotification) {
                            updateUiState.updateInfo?.let { updateInfo ->
                                EnhancedUpdateNotificationBanner(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter),
                                    updateInfo = updateInfo,
                                    downloadState = updateUiState.downloadState,
                                    onDismiss = { updateNotificationViewModel.dismissUpdate() },
                                    onDownload = { updateNotificationViewModel.downloadUpdate() },
                                    onCancel = { updateNotificationViewModel.cancelDownload() },
                                    onInstall = { updateNotificationViewModel.installUpdate() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    
    // No re-hiding of system UI; Compose screens manage insets

    override fun onDestroy() {
        super.onDestroy()
        // The DownloadManager handles cleanup automatically
    }
}

@Composable
fun QiblaApp(
    updateNotificationViewModel: UpdateNotificationViewModel,
    enhancedDownloadManager: EnhancedDownloadManager
) {
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
            sharedSensorRepository = sharedSensorRepository,
            updateNotificationViewModel = updateNotificationViewModel,
            enhancedDownloadManager = enhancedDownloadManager
        )
    }
}
