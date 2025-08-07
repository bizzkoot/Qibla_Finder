package com.bizzkoot.qiblafinder.sunCalibration

import android.location.Location
import org.shredzone.commons.suncalc.SunPosition
import java.util.*

/**
 * Utility class for calculating the sun's position based on date, time, and location.
 * Uses the SunCalc library to perform astronomical calculations.
 */
object SunPositionCalculator {
    
    /**
     * Calculates the sun's position (azimuth and elevation) for the given location and time.
     *
     * @param location The user's current location (latitude and longitude)
     * @param time The time for which to calculate the sun's position (default: current time)
     * @return SunPosition object containing azimuth and elevation data
     */
    fun calculateSunPosition(location: Location, time: Date = Date()): SunPosition {
        return SunPosition.compute()
            .at(location.latitude, location.longitude)
            .on(time)
            .execute()
    }
    
    /**
     * Gets the sun's true azimuth (direction relative to True North) for the given location and time.
     *
     * @param location The user's current location (latitude and longitude)
     * @param time The time for which to calculate the sun's position (default: current time)
     * @return The sun's azimuth in degrees (0-360)
     */
    fun getSunAzimuth(location: Location, time: Date = Date()): Double {
        val sunPosition = calculateSunPosition(location, time)
        // Convert azimuth from [-180, 180] to [0, 360] range
        var azimuth = sunPosition.azimuth
        if (azimuth < 0) {
            azimuth += 360
        }
        return azimuth
    }
    
    /**
     * Gets the sun's elevation (angle above the horizon) for the given location and time.
     *
     * @param location The user's current location (latitude and longitude)
     * @param time The time for which to calculate the sun's position (default: current time)
     * @return The sun's elevation in degrees
     */
    fun getSunElevation(location: Location, time: Date = Date()): Double {
        val sunPosition = calculateSunPosition(location, time)
        return sunPosition.altitude
    }
    
    /**
     * Checks if the sun is currently visible (above the horizon) at the given location and time.
     *
     * @param location The user's current location (latitude and longitude)
     * @param time The time for which to check sun visibility (default: current time)
     * @return true if the sun is above the horizon, false otherwise
     */
    fun isSunVisible(location: Location, time: Date = Date()): Boolean {
        val elevation = getSunElevation(location, time)
        return elevation > 0
    }
}