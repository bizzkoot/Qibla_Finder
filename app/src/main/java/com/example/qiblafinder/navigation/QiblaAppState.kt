package com.bizzkoot.qiblafinder.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState

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

    @Composable
    fun getCurrentRoute(): String? {
        return navController.currentBackStackEntryAsState().value?.destination?.route
    }

    fun navigateToCompass() {
        navController.navigate(COMPASS_ROUTE) {
            // Pop up to the start destination of the graph to
            // avoid building up a large stack of destinations
            // on the back stack as users select items
            popUpTo(navController.graph.startDestinationId) {
                saveState = true
            }
            // Avoid multiple copies of the same destination when
            // reselecting the same item
            launchSingleTop = true
            // Restore state when reselecting a previously selected item
            restoreState = true
        }
    }

    fun navigateToSunCalibration() {
        navController.navigate(SUN_CALIBRATION_ROUTE)
    }

    fun navigateToARView() {
        navController.navigate(AR_VIEW_ROUTE)
    }

    fun navigateToManualLocation() {
        navController.navigate(MANUAL_LOCATION_ROUTE)
    }

    fun navigateToTroubleshooting() {
        navController.navigate(TROUBLESHOOTING_ROUTE)
    }

    fun upPress() {
        navController.navigateUp()
    }

    @Composable
    fun bottomBarVisible(): Boolean {
        return getCurrentRoute() == COMPASS_ROUTE
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