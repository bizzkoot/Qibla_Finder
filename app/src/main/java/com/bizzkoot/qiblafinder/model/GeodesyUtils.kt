package com.bizzkoot.qiblafinder.model

import com.bizzkoot.qiblafinder.ui.location.MapLocation
import timber.log.Timber
import kotlin.math.*

/**
 * Result wrapper for geodesy calculations that can fail
 */
sealed class GeodesyResult<out T> {
    data class Success<T>(val data: T) : GeodesyResult<T>()
    data class Error(val message: String, val cause: Throwable? = null) : GeodesyResult<Nothing>()
}

/**
 * Exception types for geodesy calculations
 */
sealed class GeodesyException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class InvalidCoordinatesException(message: String) : GeodesyException(message)
    class NumericalPrecisionException(message: String, cause: Throwable? = null) : GeodesyException(message, cause)
    class CalculationFailedException(message: String, cause: Throwable? = null) : GeodesyException(message, cause)
    class MemoryPressureException(message: String) : GeodesyException(message)
}

object GeodesyUtils {

    private const val KAABA_LATITUDE = 21.4225
    private const val KAABA_LONGITUDE = 39.8262
    
    // Numerical precision constants
    private const val EPSILON = 1e-10
    private const val MAX_LATITUDE = 90.0
    private const val MIN_LATITUDE = -90.0
    private const val MAX_LONGITUDE = 180.0
    private const val MIN_LONGITUDE = -180.0
    
    // Memory management constants
    private const val MAX_SEGMENTS_LIMIT = 1000
    private const val MEMORY_PRESSURE_SEGMENT_LIMIT = 50
    private const val MAX_PATH_POINTS_IN_MEMORY = 5000

    /**
     * Calculates the initial bearing (Qibla direction) from a given location to the Kaaba.
     * Enhanced with comprehensive error handling and validation.
     *
     * @param latitude The latitude of the user's location.
     * @param longitude The longitude of the user's location.
     * @return GeodesyResult containing the Qibla bearing in degrees from True North or error.
     */
    fun calculateQiblaBearingSafe(latitude: Double, longitude: Double): GeodesyResult<Double> {
        return try {
            // Validate input coordinates
            validateCoordinates(latitude, longitude).let { validationResult ->
                if (validationResult is GeodesyResult.Error) {
                    return validationResult
                }
            }
            
            // Check for numerical edge cases
            if (abs(latitude - KAABA_LATITUDE) < EPSILON && abs(longitude - KAABA_LONGITUDE) < EPSILON) {
                return GeodesyResult.Success(0.0) // At Kaaba, any direction is valid
            }
            
            val lat1 = Math.toRadians(latitude)
            val lon1 = Math.toRadians(longitude)
            val lat2 = Math.toRadians(KAABA_LATITUDE)
            val lon2 = Math.toRadians(KAABA_LONGITUDE)

            val deltaLon = lon2 - lon1

            val y = sin(deltaLon) * cos(lat2)
            val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(deltaLon)

            // Check for numerical precision issues
            if (abs(x) < EPSILON && abs(y) < EPSILON) {
                return GeodesyResult.Error("Cannot determine bearing: points may be antipodal or identical")
            }

            val initialBearing = Math.toDegrees(atan2(y, x))
            val normalizedBearing = (initialBearing + 360) % 360 // Normalize to 0-360
            
            // Validate result
            if (normalizedBearing.isNaN() || normalizedBearing.isInfinite()) {
                return GeodesyResult.Error("Bearing calculation resulted in invalid value")
            }
            
            GeodesyResult.Success(normalizedBearing)
            
        } catch (e: Exception) {
            Timber.e(e, "Error calculating Qibla bearing for coordinates: $latitude, $longitude")
            GeodesyResult.Error("Failed to calculate Qibla bearing", e)
        }
    }
    
    /**
     * Legacy method for backward compatibility - returns 0.0 on error
     */
    fun calculateQiblaBearing(latitude: Double, longitude: Double): Double {
        return when (val result = calculateQiblaBearingSafe(latitude, longitude)) {
            is GeodesyResult.Success -> result.data
            is GeodesyResult.Error -> {
                Timber.w("Using fallback bearing (0.0) due to calculation error: ${result.message}")
                0.0
            }
        }
    }
    
    /**
     * Calculates the distance between two points on Earth using the Haversine formula.
     * Enhanced with comprehensive error handling and validation.
     *
     * @param lat1 Latitude of the first point
     * @param lon1 Longitude of the first point
     * @param lat2 Latitude of the second point
     * @param lon2 Longitude of the second point
     * @return GeodesyResult containing distance in kilometers or error
     */
    fun calculateDistanceSafe(lat1: Double, lon1: Double, lat2: Double, lon2: Double): GeodesyResult<Double> {
        return try {
            // Validate input coordinates
            validateCoordinates(lat1, lon1).let { result ->
                if (result is GeodesyResult.Error) return result
            }
            validateCoordinates(lat2, lon2).let { result ->
                if (result is GeodesyResult.Error) return result
            }
            
            // Handle identical points
            if (abs(lat1 - lat2) < EPSILON && abs(lon1 - lon2) < EPSILON) {
                return GeodesyResult.Success(0.0)
            }
            
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
            
            // Validate intermediate calculation
            if (a < 0 || a > 1) {
                return GeodesyResult.Error("Invalid intermediate calculation in Haversine formula")
            }
            
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            val distance = earthRadius * c
            
            // Validate result
            if (distance.isNaN() || distance.isInfinite() || distance < 0) {
                return GeodesyResult.Error("Distance calculation resulted in invalid value")
            }
            
            GeodesyResult.Success(distance)
            
        } catch (e: Exception) {
            Timber.e(e, "Error calculating distance between ($lat1, $lon1) and ($lat2, $lon2)")
            GeodesyResult.Error("Failed to calculate distance", e)
        }
    }
    
    /**
     * Legacy method for backward compatibility - returns 0.0 on error
     */
    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        return when (val result = calculateDistanceSafe(lat1, lon1, lat2, lon2)) {
            is GeodesyResult.Success -> result.data
            is GeodesyResult.Error -> {
                Timber.w("Using fallback distance (0.0) due to calculation error: ${result.message}")
                0.0
            }
        }
    }
    
    /**
     * Calculates intermediate points along the great circle path between two locations.
     * Uses spherical interpolation (slerp) algorithm with comprehensive error handling.
     *
     * @param startLat Starting latitude in degrees
     * @param startLon Starting longitude in degrees
     * @param endLat Ending latitude in degrees
     * @param endLon Ending longitude in degrees
     * @param segments Number of intermediate points to generate (default: 50)
     * @return GeodesyResult containing List of MapLocation points or error
     */
    fun calculateGreatCirclePathSafe(
        startLat: Double,
        startLon: Double,
        endLat: Double,
        endLon: Double,
        segments: Int = 50
    ): GeodesyResult<List<MapLocation>> {
        return try {
            // Validate input parameters
            if (segments <= 0) {
                return GeodesyResult.Error("Segments must be positive, got: $segments")
            }
            
            if (segments > MAX_SEGMENTS_LIMIT) {
                return GeodesyResult.Error("Segments exceed maximum limit of $MAX_SEGMENTS_LIMIT")
            }
            
            // Check for memory pressure and reduce segments if necessary
            val adjustedSegments = if (isMemoryPressure()) {
                minOf(segments, MEMORY_PRESSURE_SEGMENT_LIMIT).also {
                    Timber.w("Memory pressure detected, reducing segments from $segments to $it")
                }
            } else {
                segments
            }
            
            // Validate coordinates
            validateCoordinates(startLat, startLon).let { result ->
                if (result is GeodesyResult.Error) return result
            }
            validateCoordinates(endLat, endLon).let { result ->
                if (result is GeodesyResult.Error) return result
            }
            
            // Convert to radians with error checking
            val lat1 = Math.toRadians(startLat)
            val lon1 = Math.toRadians(startLon)
            val lat2 = Math.toRadians(endLat)
            val lon2 = Math.toRadians(endLon)
            
            if (lat1.isNaN() || lon1.isNaN() || lat2.isNaN() || lon2.isNaN()) {
                return GeodesyResult.Error("Invalid radians conversion")
            }
            
            // Handle edge case where start and end points are the same
            if (abs(startLat - endLat) < EPSILON && abs(startLon - endLon) < EPSILON) {
                return GeodesyResult.Success(listOf(MapLocation(startLat, startLon)))
            }
            
            // Convert to Cartesian coordinates on unit sphere
            val x1 = cos(lat1) * cos(lon1)
            val y1 = cos(lat1) * sin(lon1)
            val z1 = sin(lat1)
            
            val x2 = cos(lat2) * cos(lon2)
            val y2 = cos(lat2) * sin(lon2)
            val z2 = sin(lat2)
            
            // Validate Cartesian coordinates
            if (listOf(x1, y1, z1, x2, y2, z2).any { it.isNaN() || it.isInfinite() }) {
                return GeodesyResult.Error("Invalid Cartesian coordinate conversion")
            }
            
            // Calculate angular distance between points
            val dot = (x1 * x2 + y1 * y2 + z1 * z2).coerceIn(-1.0, 1.0)
            val angle = acos(dot)
            
            if (angle.isNaN() || angle.isInfinite()) {
                return GeodesyResult.Error("Invalid angular distance calculation")
            }
            
            // Handle edge case where points are antipodal (opposite sides of Earth)
            if (abs(angle - PI) < EPSILON) {
                return generateAntipodalSafe(startLat, startLon, endLat, endLon, adjustedSegments)
            }
            
            // Handle edge case where angle is very small (points are very close)
            if (angle < EPSILON) {
                return GeodesyResult.Success(listOf(MapLocation(startLat, startLon), MapLocation(endLat, endLon)))
            }
            
            // Generate intermediate points using spherical interpolation (slerp)
            val points = mutableListOf<MapLocation>()
            val sinAngle = sin(angle)
            
            if (abs(sinAngle) < EPSILON) {
                return GeodesyResult.Error("Cannot perform spherical interpolation: sin(angle) too small")
            }
            
            for (i in 0..adjustedSegments) {
                try {
                    val f = i.toDouble() / adjustedSegments
                    val a = sin((1 - f) * angle) / sinAngle
                    val b = sin(f * angle) / sinAngle
                    
                    if (a.isNaN() || b.isNaN() || a.isInfinite() || b.isInfinite()) {
                        Timber.w("Skipping invalid interpolation coefficients at segment $i")
                        continue
                    }
                    
                    val x = a * x1 + b * x2
                    val y = a * y1 + b * y2
                    val z = a * z1 + b * z2
                    
                    if (listOf(x, y, z).any { it.isNaN() || it.isInfinite() }) {
                        Timber.w("Skipping invalid Cartesian coordinates at segment $i")
                        continue
                    }
                    
                    // Convert back to lat/lon
                    val lat = asin(z.coerceIn(-1.0, 1.0))
                    val lon = atan2(y, x)
                    
                    if (lat.isNaN() || lon.isNaN() || lat.isInfinite() || lon.isInfinite()) {
                        Timber.w("Skipping invalid lat/lon conversion at segment $i")
                        continue
                    }
                    
                    val latDegrees = Math.toDegrees(lat)
                    val lonDegrees = Math.toDegrees(lon)
                    
                    // Validate final coordinates
                    if (latDegrees in MIN_LATITUDE..MAX_LATITUDE && 
                        lonDegrees in MIN_LONGITUDE..MAX_LONGITUDE) {
                        points.add(MapLocation(latDegrees, lonDegrees))
                    } else {
                        Timber.w("Skipping out-of-bounds coordinates at segment $i: ($latDegrees, $lonDegrees)")
                    }
                    
                } catch (e: Exception) {
                    Timber.w(e, "Error calculating segment $i, skipping")
                    continue
                }
            }
            
            if (points.isEmpty()) {
                return GeodesyResult.Error("No valid points generated in great circle path")
            }
            
            if (points.size > MAX_PATH_POINTS_IN_MEMORY) {
                return GeodesyResult.Error("Generated path exceeds memory limits")
            }
            
            GeodesyResult.Success(points)
            
        } catch (e: OutOfMemoryError) {
            Timber.e(e, "Out of memory calculating great circle path")
            GeodesyResult.Error("Insufficient memory for path calculation", e)
        } catch (e: Exception) {
            Timber.e(e, "Error calculating great circle path")
            GeodesyResult.Error("Failed to calculate great circle path", e)
        }
    }
    
    /**
     * Legacy method for backward compatibility - returns empty list on error
     */
    fun calculateGreatCirclePath(
        startLat: Double,
        startLon: Double,
        endLat: Double,
        endLon: Double,
        segments: Int = 50
    ): List<MapLocation> {
        return when (val result = calculateGreatCirclePathSafe(startLat, startLon, endLat, endLon, segments)) {
            is GeodesyResult.Success -> result.data
            is GeodesyResult.Error -> {
                Timber.w("Using fallback empty path due to calculation error: ${result.message}")
                emptyList()
            }
        }
    }
    
    /**
     * Calculates the distance from a given location to the Kaaba in Mecca.
     * Enhanced with error handling.
     *
     * @param latitude User's latitude in degrees
     * @param longitude User's longitude in degrees
     * @return GeodesyResult containing distance to Kaaba in kilometers or error
     */
    fun calculateDistanceToKaabaSafe(latitude: Double, longitude: Double): GeodesyResult<Double> {
        return calculateDistanceSafe(latitude, longitude, KAABA_LATITUDE, KAABA_LONGITUDE)
    }
    
    /**
     * Legacy method for backward compatibility - returns 0.0 on error
     */
    fun calculateDistanceToKaaba(latitude: Double, longitude: Double): Double {
        return calculateDistance(latitude, longitude, KAABA_LATITUDE, KAABA_LONGITUDE)
    }
    
    /**
     * Generates a great circle path for antipodal points with error handling.
     * Uses a path through the North Pole as the default route.
     */
    private fun generateAntipodalSafe(
        startLat: Double,
        startLon: Double,
        endLat: Double,
        endLon: Double,
        segments: Int
    ): GeodesyResult<List<MapLocation>> {
        return try {
            val points = mutableListOf<MapLocation>()
            
            // First half: from start to North Pole
            val halfSegments = segments / 2
            for (i in 0..halfSegments) {
                val f = i.toDouble() / halfSegments
                val lat = startLat + f * (90.0 - startLat)
                val lon = startLon
                
                if (lat in MIN_LATITUDE..MAX_LATITUDE && lon in MIN_LONGITUDE..MAX_LONGITUDE) {
                    points.add(MapLocation(lat, lon))
                }
            }
            
            // Second half: from North Pole to end
            for (i in 1..segments - halfSegments) {
                val f = i.toDouble() / (segments - halfSegments)
                val lat = 90.0 + f * (endLat - 90.0)
                val lon = endLon
                
                if (lat in MIN_LATITUDE..MAX_LATITUDE && lon in MIN_LONGITUDE..MAX_LONGITUDE) {
                    points.add(MapLocation(lat, lon))
                }
            }
            
            if (points.isEmpty()) {
                GeodesyResult.Error("No valid points generated for antipodal path")
            } else {
                GeodesyResult.Success(points)
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Error generating antipodal path")
            GeodesyResult.Error("Failed to generate antipodal path", e)
        }
    }
    
    /**
     * Legacy method for backward compatibility
     */
    private fun generateAntipodal(
        startLat: Double,
        startLon: Double,
        endLat: Double,
        endLon: Double,
        segments: Int
    ): List<MapLocation> {
        return when (val result = generateAntipodalSafe(startLat, startLon, endLat, endLon, segments)) {
            is GeodesyResult.Success -> result.data
            is GeodesyResult.Error -> {
                Timber.w("Using fallback empty path for antipodal generation: ${result.message}")
                emptyList()
            }
        }
    }
    
    /**
     * Validates coordinate values for latitude and longitude
     */
    private fun validateCoordinates(latitude: Double, longitude: Double): GeodesyResult<Unit> {
        return when {
            latitude.isNaN() || latitude.isInfinite() -> 
                GeodesyResult.Error("Invalid latitude: $latitude")
            longitude.isNaN() || longitude.isInfinite() -> 
                GeodesyResult.Error("Invalid longitude: $longitude")
            latitude !in MIN_LATITUDE..MAX_LATITUDE -> 
                GeodesyResult.Error("Latitude out of range: $latitude (must be between $MIN_LATITUDE and $MAX_LATITUDE)")
            longitude !in MIN_LONGITUDE..MAX_LONGITUDE -> 
                GeodesyResult.Error("Longitude out of range: $longitude (must be between $MIN_LONGITUDE and $MAX_LONGITUDE)")
            else -> GeodesyResult.Success(Unit)
        }
    }
    
    /**
     * Checks if the system is under memory pressure
     */
    private fun isMemoryPressure(): Boolean {
        return try {
            val runtime = Runtime.getRuntime()
            val maxMemory = runtime.maxMemory()
            val totalMemory = runtime.totalMemory()
            val freeMemory = runtime.freeMemory()
            val usedMemory = totalMemory - freeMemory
            val memoryUsageRatio = usedMemory.toDouble() / maxMemory.toDouble()
            
            // Consider memory pressure if using more than 80% of available memory
            memoryUsageRatio > 0.8
        } catch (e: Exception) {
            Timber.w(e, "Error checking memory pressure, assuming no pressure")
            false
        }
    }
}