package com.bizzkoot.qiblafinder.sunCalibration

import com.bizzkoot.qiblafinder.model.LocationRepository
import com.bizzkoot.qiblafinder.model.LocationState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.*

/**
 * Repository for handling sun position calculations.
 * Combines location data with astronomical calculations to provide real-time sun position data.
 */
class SunPositionRepository(
    private val locationRepository: LocationRepository,
    private val sunPositionCalculator: SunPositionCalculator = SunPositionCalculator
) {
    
    /**
     * Data class representing the sun's position with azimuth, elevation, and visibility status.
     */
    data class SunPositionData(
        val azimuth: Double,
        val elevation: Double,
        val isVisible: Boolean
    )
    
    /**
     * A flow that emits the current sun position data based on the user's location.
     * Combines location data with sun position calculations.
     *
     * @param time The time for which to calculate the sun's position (default: current time)
     * @return Flow of SunPositionData or null if location is not available
     */
    fun getSunPosition(time: Date = Date()): Flow<SunPositionData?> {
        // Log: Using map() instead of combine() for single Flow transformation
        return locationRepository.getLocation().map { locationState ->
            // Log: locationState should be a single LocationState object, not an array
            when (locationState) {
                is LocationState.Available -> {
                    val location = locationState.location
                    // Log: Converting custom Location to Android Location
                    val androidLocation = android.location.Location("").apply {
                        latitude = location.latitude
                        longitude = location.longitude
                    }
                    val azimuth = sunPositionCalculator.getSunAzimuth(androidLocation, time)
                    val elevation = sunPositionCalculator.getSunElevation(androidLocation, time)
                    val isVisible = sunPositionCalculator.isSunVisible(androidLocation, time)
                    
                    SunPositionData(azimuth, elevation, isVisible)
                }
                is LocationState.Loading -> null
                is LocationState.Error -> null
                else -> null
            }
        }
    }
    
    /**
     * Checks if the sun is currently visible at the user's location.
     *
     * @param time The time for which to check sun visibility (default: current time)
     * @return Flow of Boolean indicating if the sun is visible, or null if location is not available
     */
    fun isSunVisible(time: Date = Date()): Flow<Boolean?> {
        // Log: Using map() instead of combine() for single Flow transformation
        return locationRepository.getLocation().map { locationState ->
            // Log: locationState should be a single LocationState object, not an array
            when (locationState) {
                is LocationState.Available -> {
                    val location = locationState.location
                    // Log: Converting custom Location to Android Location
                    val androidLocation = android.location.Location("").apply {
                        latitude = location.latitude
                        longitude = location.longitude
                    }
                    sunPositionCalculator.isSunVisible(androidLocation, time)
                }
                is LocationState.Loading -> null
                is LocationState.Error -> null
                else -> null
            }
        }
    }
}