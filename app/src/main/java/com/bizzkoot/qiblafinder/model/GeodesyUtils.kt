package com.bizzkoot.qiblafinder.model

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object GeodesyUtils {

    private const val KAABA_LATITUDE = 21.4225
    private const val KAABA_LONGITUDE = 39.8262

    /**
     * Calculates the initial bearing (Qibla direction) from a given location to the Kaaba.
     *
     * @param latitude The latitude of the user's location.
     * @param longitude The longitude of the user's location.
     * @return The Qibla bearing in degrees from True North.
     */
    fun calculateQiblaBearing(latitude: Double, longitude: Double): Double {
        val lat1 = Math.toRadians(latitude)
        val lon1 = Math.toRadians(longitude)
        val lat2 = Math.toRadians(KAABA_LATITUDE)
        val lon2 = Math.toRadians(KAABA_LONGITUDE)

        val deltaLon = lon2 - lon1

        val y = sin(deltaLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(deltaLon)

        val initialBearing = Math.toDegrees(atan2(y, x))
        return (initialBearing + 360) % 360 // Normalize to 0-360
    }
    
    /**
     * Calculates the distance between two points on Earth using the Haversine formula.
     *
     * @param lat1 Latitude of the first point
     * @param lon1 Longitude of the first point
     * @param lat2 Latitude of the second point
     * @param lon2 Longitude of the second point
     * @return Distance in kilometers
     */
    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371.0 // Earth's radius in kilometers
        
        val lat1Rad = Math.toRadians(lat1)
        val lon1Rad = Math.toRadians(lon1)
        val lat2Rad = Math.toRadians(lat2)
        val lon2Rad = Math.toRadians(lon2)
        
        val deltaLat = lat2Rad - lat1Rad
        val deltaLon = lon2Rad - lon1Rad
        
        val a = sin(deltaLat / 2) * sin(deltaLat / 2) +
                cos(lat1Rad) * cos(lat2Rad) *
                sin(deltaLon / 2) * sin(deltaLon / 2)
        
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        
        return earthRadius * c
    }
}