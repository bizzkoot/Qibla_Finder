package com.bizzkoot.qiblafinder.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController

/**
 * Class that manages the overall application state including navigation
 */
class QiblaAppState(
    val navController: NavHostController
) {
    companion object {
        const val COMPASS_ROUTE = "compass"
        const val SUN_CALIBRATION_ROUTE = "sun_calibration"
        const val AR_VIEW_ROUTE = "ar_view"
        const val MANUAL_LOCATION_ROUTE = "manual_location"
        const val TROUBLESHOOTING_ROUTE = "troubleshooting"
    }
}

/**
 * Composable function that remembers and provides the QiblaAppState
 */
@Composable
fun rememberQiblaAppState(
    navController: NavHostController = androidx.navigation.compose.rememberNavController()
): QiblaAppState = remember(navController) {
    QiblaAppState(navController)
}
