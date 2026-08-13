package com.bizzkoot.qiblafinder.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.bizzkoot.qiblafinder.model.CalibrationRepository
import com.bizzkoot.qiblafinder.model.LocationRepository
import com.bizzkoot.qiblafinder.model.SensorRepository
import com.bizzkoot.qiblafinder.sunCalibration.SunCalibrationViewModel
import com.bizzkoot.qiblafinder.sunCalibration.SunPositionViewModel
import com.bizzkoot.qiblafinder.ui.compass.CompassScreen
import com.bizzkoot.qiblafinder.ui.compass.CompassViewModel
import com.bizzkoot.qiblafinder.ui.compass.CompassPreferences
import com.bizzkoot.qiblafinder.ui.compass.KeepScreenOn
import androidx.compose.runtime.collectAsState
import com.bizzkoot.qiblafinder.model.MapLocation
import com.bizzkoot.qiblafinder.ui.sunCalibration.SunCalibrationScreen
import com.bizzkoot.qiblafinder.ui.ar.ARScreen
import com.bizzkoot.qiblafinder.ui.ar.ARViewModel
import com.bizzkoot.qiblafinder.ui.location.ManualLocationScreen
import com.bizzkoot.qiblafinder.ui.location.ManualLocationViewModel

import timber.log.Timber

/**
 * Composable that hosts the navigation graph for the Qibla Finder app
 */
@Composable
fun QiblaNavHost(
    navController: NavHostController,
    sharedLocationRepository: LocationRepository,
    sharedSensorRepository: SensorRepository,
    updateNotificationViewModel: com.bizzkoot.qiblafinder.update.viewmodel.UpdateNotificationViewModel,
    enhancedDownloadManager: com.bizzkoot.qiblafinder.update.services.EnhancedDownloadManager,
    modifier: Modifier = Modifier
) {
    Timber.d("🎯 QiblaNavHost - Using shared repositories: LocationRepository=$sharedLocationRepository, SensorRepository=$sharedSensorRepository")

    // Single shared CalibrationRepository: one in-memory source of truth for the
    // sun calibration offset, backed by the "sun_calibration" SharedPreferences.
    // The SAME instance is passed to both the compass and the sun calibration
    // routes so that calibrating on the sun screen emits into the flow the
    // compass collects, applying the offset immediately on return.
    val navContext = LocalContext.current
    val calibrationRepository = remember { CalibrationRepository(navContext.applicationContext) }

    NavHost(
        navController = navController,
        startDestination = QiblaAppState.COMPASS_ROUTE,
        modifier = modifier
    ) {
        composable(QiblaAppState.COMPASS_ROUTE) { backStackEntry ->
            Timber.d("🎯 QiblaNavHost - Compass screen composable called")

            // Keep-screen-on: persisted toggle + lifecycle-safe window flag
            val compassPreferences = remember { CompassPreferences(navContext.applicationContext) }
            var keepScreenOn by remember { mutableStateOf(compassPreferences.getKeepScreenOn()) }
            KeepScreenOn(enabled = keepScreenOn)
            
            val viewModel: CompassViewModel = viewModel(
                factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        Timber.d("🎯 QiblaNavHost - Creating CompassViewModel with shared repositories")
                        return CompassViewModel(
                            sharedLocationRepository,
                            sharedSensorRepository,
                            calibrationRepository
                        ) as T
                    }
                }
            )

            // Check for result from ManualLocationScreen
            val manualLocationLat = backStackEntry.savedStateHandle.get<Float>("manual_lat")
            val manualLocationLng = backStackEntry.savedStateHandle.get<Float>("manual_lng")

            if (manualLocationLat != null && manualLocationLng != null) {
                LaunchedEffect(manualLocationLat, manualLocationLng) {
                    val manualLocation = MapLocation(manualLocationLat.toDouble(), manualLocationLng.toDouble())
                    viewModel.setManualLocation(manualLocation)
                    // Clear the result from the savedStateHandle so it's not used again
                    backStackEntry.savedStateHandle.remove<Float>("manual_lat")
                    backStackEntry.savedStateHandle.remove<Float>("manual_lng")
                }
            }

            CompassScreen(
                viewModel = viewModel,
                onNavigateToSunCalibration = { navController.navigate(QiblaAppState.SUN_CALIBRATION_ROUTE) },
                onNavigateToAR = { navController.navigate(QiblaAppState.AR_VIEW_ROUTE) },
                onNavigateToManualLocation = { 
                    Timber.d("🎯 QiblaNavHost - Navigating to Manual Location - START")
                    Timber.d("🎯 QiblaNavHost - Current route: ${navController.currentDestination?.route}")
                    try {
                        navController.navigate(QiblaAppState.MANUAL_LOCATION_ROUTE) 
                        Timber.d("🎯 QiblaNavHost - Navigating to Manual Location - SUCCESS")
                    } catch (e: Exception) {
                        Timber.e("🎯 QiblaNavHost - Navigating to Manual Location - ERROR: ${e.message}")
                    }
                },
                onNavigateToTroubleshooting = { navController.navigate(QiblaAppState.TROUBLESHOOTING_ROUTE) },
                keepScreenOn = keepScreenOn,
                onToggleKeepScreenOn = {
                    keepScreenOn = !keepScreenOn
                    compassPreferences.setKeepScreenOn(keepScreenOn)
                }
            )
        }

        composable(QiblaAppState.SUN_CALIBRATION_ROUTE) {
            Timber.d("🎯 QiblaNavHost - Sun Calibration screen composable called")
            
            val sunPositionViewModel = viewModel<SunPositionViewModel>(
                factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return SunPositionViewModel(sharedLocationRepository) as T
                    }
                }
            )
            val sunCalibrationViewModel = viewModel<SunCalibrationViewModel>(
                factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return SunCalibrationViewModel(
                            sharedLocationRepository,
                            sharedSensorRepository,
                            calibrationRepository,
                            sunPositionViewModel
                        ) as T
                    }
                }
            )

            SunCalibrationScreen(
                uiState = sunCalibrationViewModel.uiState.collectAsState().value,
                onCalibrate = { sunCalibrationViewModel.performCalibration() },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(QiblaAppState.AR_VIEW_ROUTE) {
            Timber.d("🎯 QiblaNavHost - AR screen composable called")
            val context = LocalContext.current
            val arViewModel: ARViewModel = viewModel(
                factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        if (modelClass.isAssignableFrom(ARViewModel::class.java)) {
                            @Suppress("UNCHECKED_CAST")
                            return ARViewModel(
                                application = context.applicationContext as android.app.Application,
                                locationRepository = sharedLocationRepository,
                                sensorRepository = sharedSensorRepository
                            ) as T
                        }
                        throw IllegalArgumentException("Unknown ViewModel class")
                    }
                }
            )
            ARScreen(
                viewModel = arViewModel,
                onNavigateBack = { navController.popBackStack() },
                onCalibrateClicked = { navController.navigate(QiblaAppState.SUN_CALIBRATION_ROUTE) }
            )
        }

        composable(QiblaAppState.MANUAL_LOCATION_ROUTE) {
            Timber.d("🎯 QiblaNavHost - Manual Location composable called")
            
            // Create ManualLocationViewModel directly with shared repository
            val context = LocalContext.current
            val viewModel = remember {
                ManualLocationViewModel(
                    sharedLocationRepository,
                    com.bizzkoot.qiblafinder.ui.location.AndroidGeocodingService(context.applicationContext),
                    com.bizzkoot.qiblafinder.ui.location.ManualLocationPreferences(context.applicationContext)
                )
            }
            
            Timber.d("🎯 QiblaNavHost - ManualLocationViewModel created directly: $viewModel")

            ManualLocationScreen(
                viewModel = viewModel,
                onLocationConfirmed = { mapLocation ->
                    Timber.d("🎯 QiblaNavHost - Location confirmed: $mapLocation, passing back to Compass.")
                    // Set the result for the previous back stack entry
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("manual_lat", mapLocation.latitude.toFloat())
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("manual_lng", mapLocation.longitude.toFloat())
                    navController.popBackStack()
                },
                onBackPressed = { 
                    Timber.d("🎯 QiblaNavHost - Back pressed from Manual Location")
                    navController.popBackStack() 
                }
            )
        }
        


        composable(QiblaAppState.TROUBLESHOOTING_ROUTE) {
            Timber.d("🎯 QiblaNavHost - Enhanced Troubleshooting screen composable called")
            val context = LocalContext.current
            val app = context.applicationContext as com.bizzkoot.qiblafinder.QiblaFinderApplication
            val enhancedHelpViewModel: com.bizzkoot.qiblafinder.ui.troubleshooting.EnhancedHelpViewModel = viewModel(
                factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return com.bizzkoot.qiblafinder.ui.troubleshooting.EnhancedHelpViewModel(
                            context = context, 
                            updateRepository = app.updateNotificationRepository,
                            downloadManager = enhancedDownloadManager,
                            onUpdateFound = { updateNotificationViewModel.checkForUpdates(forceCheck = true) }
                        ) as T
                    }
                }
            )
            com.bizzkoot.qiblafinder.ui.troubleshooting.EnhancedTroubleshootingScreen(
                onBackPressed = { navController.popBackStack() },
                viewModel = enhancedHelpViewModel
            )
        }
    }
}
