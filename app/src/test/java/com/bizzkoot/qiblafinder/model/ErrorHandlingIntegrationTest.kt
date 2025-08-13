package com.bizzkoot.qiblafinder.model

import com.bizzkoot.qiblafinder.ui.location.MapLocation
import org.junit.Assert.*
import org.junit.Test

/**
 * Integration tests to verify comprehensive error handling across all components
 */
class ErrorHandlingIntegrationTest {

    @Test
    fun testComprehensiveErrorHandling_invalidCoordinates() {
        // Test various invalid coordinate scenarios
        val invalidInputs = listOf(
            Pair(Double.NaN, 0.0),
            Pair(0.0, Double.NaN),
            Pair(Double.POSITIVE_INFINITY, 0.0),
            Pair(0.0, Double.NEGATIVE_INFINITY),
            Pair(91.0, 0.0), // Latitude out of range
            Pair(-91.0, 0.0), // Latitude out of range
            Pair(0.0, 181.0), // Longitude out of range
            Pair(0.0, -181.0) // Longitude out of range
        )

        invalidInputs.forEach { (lat, lon) ->
            // Test bearing calculation
            val bearingResult = GeodesyUtils.calculateQiblaBearingSafe(lat, lon)
            assertTrue("Bearing calculation should fail for invalid coordinates ($lat, $lon)", 
                bearingResult is GeodesyResult.Error)

            // Test distance calculation
            val distanceResult = GeodesyUtils.calculateDistanceSafe(lat, lon, 0.0, 0.0)
            assertTrue("Distance calculation should fail for invalid coordinates ($lat, $lon)", 
                distanceResult is GeodesyResult.Error)

            // Test path calculation
            val pathResult = GeodesyUtils.calculateGreatCirclePathSafe(lat, lon, 0.0, 0.0, 10)
            assertTrue("Path calculation should fail for invalid coordinates ($lat, $lon)", 
                pathResult is GeodesyResult.Error)

            // Test legacy methods provide fallbacks
            val fallbackBearing = GeodesyUtils.calculateQiblaBearing(lat, lon)
            assertEquals("Legacy bearing should return 0.0 fallback", 0.0, fallbackBearing, 0.001)

            val fallbackDistance = GeodesyUtils.calculateDistance(lat, lon, 0.0, 0.0)
            assertEquals("Legacy distance should return 0.0 fallback", 0.0, fallbackDistance, 0.001)

            val fallbackPath = GeodesyUtils.calculateGreatCirclePath(lat, lon, 0.0, 0.0, 10)
            assertTrue("Legacy path should return empty list fallback", fallbackPath.isEmpty())
        }
    }

    @Test
    fun testMemoryPressureHandling() {
        // Test that excessive segments are properly rejected
        val excessiveSegments = listOf(1001, 2000, 5000, 10000)
        
        excessiveSegments.forEach { segments ->
            val result = GeodesyUtils.calculateGreatCirclePathSafe(0.0, 0.0, 1.0, 1.0, segments)
            assertTrue("Should reject excessive segments: $segments", result is GeodesyResult.Error)
            
            val errorMessage = (result as GeodesyResult.Error).message
            assertTrue("Error message should mention segment limit", 
                errorMessage.contains("exceed maximum limit") || errorMessage.contains("segments"))
        }
        
        // Test that invalid segment counts are rejected
        val invalidSegments = listOf(-1, 0, -10)
        
        invalidSegments.forEach { segments ->
            val result = GeodesyUtils.calculateGreatCirclePathSafe(0.0, 0.0, 1.0, 1.0, segments)
            assertTrue("Should reject invalid segments: $segments", result is GeodesyResult.Error)
            
            val errorMessage = (result as GeodesyResult.Error).message
            assertTrue("Error message should mention positive requirement", 
                errorMessage.contains("must be positive"))
        }
    }

    @Test
    fun testNumericalPrecisionEdgeCases() {
        // Test calculation at Kaaba (should return 0 bearing)
        val kaabaResult = GeodesyUtils.calculateQiblaBearingSafe(21.4225, 39.8262)
        assertTrue("Kaaba calculation should succeed", kaabaResult is GeodesyResult.Success)
        assertEquals("Bearing at Kaaba should be 0", 0.0, (kaabaResult as GeodesyResult.Success).data, 0.001)

        // Test very close points
        val closeResult = GeodesyUtils.calculateDistanceSafe(0.0, 0.0, 0.0000001, 0.0000001)
        assertTrue("Close points calculation should succeed", closeResult is GeodesyResult.Success)
        assertTrue("Distance should be non-negative", (closeResult as GeodesyResult.Success).data >= 0.0)

        // Test identical points
        val identicalResult = GeodesyUtils.calculateDistanceSafe(45.0, 90.0, 45.0, 90.0)
        assertTrue("Identical points calculation should succeed", identicalResult is GeodesyResult.Success)
        assertEquals("Distance between identical points should be 0", 0.0, (identicalResult as GeodesyResult.Success).data, 0.001)

        // Test extreme coordinates (near poles)
        val poleResult = GeodesyUtils.calculateQiblaBearingSafe(89.9999, 179.9999)
        assertTrue("Pole calculation should succeed", poleResult is GeodesyResult.Success)
        val bearing = (poleResult as GeodesyResult.Success).data
        assertTrue("Bearing should be valid", bearing >= 0.0 && bearing <= 360.0)
    }

    @Test
    fun testErrorMessageQuality() {
        // Test that error messages are informative and helpful
        val invalidLatResult = GeodesyUtils.calculateQiblaBearingSafe(91.0, 0.0)
        assertTrue("Should be error result", invalidLatResult is GeodesyResult.Error)
        val latErrorMessage = (invalidLatResult as GeodesyResult.Error).message
        assertTrue("Error message should mention latitude", latErrorMessage.contains("latitude", ignoreCase = true))
        assertTrue("Error message should mention range", latErrorMessage.contains("range", ignoreCase = true))

        val invalidLonResult = GeodesyUtils.calculateQiblaBearingSafe(0.0, 181.0)
        assertTrue("Should be error result", invalidLonResult is GeodesyResult.Error)
        val lonErrorMessage = (invalidLonResult as GeodesyResult.Error).message
        assertTrue("Error message should mention longitude", lonErrorMessage.contains("longitude", ignoreCase = true))
        assertTrue("Error message should mention range", lonErrorMessage.contains("range", ignoreCase = true))

        val nanResult = GeodesyUtils.calculateQiblaBearingSafe(Double.NaN, 0.0)
        assertTrue("Should be error result", nanResult is GeodesyResult.Error)
        val nanErrorMessage = (nanResult as GeodesyResult.Error).message
        assertTrue("Error message should mention invalid", nanErrorMessage.contains("Invalid", ignoreCase = true))
    }

    @Test
    fun testGracefulFallbackBehavior() {
        // Test that the system continues to function even with errors
        val validLocation = MapLocation(40.7128, -74.0060) // New York
        
        // Valid calculation should work
        val validResult = GeodesyUtils.calculateQiblaBearingSafe(validLocation.latitude, validLocation.longitude)
        assertTrue("Valid calculation should succeed", validResult is GeodesyResult.Success)
        
        // Invalid calculation should fail gracefully
        val invalidResult = GeodesyUtils.calculateQiblaBearingSafe(Double.NaN, validLocation.longitude)
        assertTrue("Invalid calculation should fail gracefully", invalidResult is GeodesyResult.Error)
        
        // Legacy method should provide fallback
        val fallback = GeodesyUtils.calculateQiblaBearing(Double.NaN, validLocation.longitude)
        assertEquals("Legacy method should provide fallback", 0.0, fallback, 0.001)
        
        // System should still work for subsequent valid calculations
        val subsequentResult = GeodesyUtils.calculateQiblaBearingSafe(validLocation.latitude, validLocation.longitude)
        assertTrue("Subsequent calculation should still work", subsequentResult is GeodesyResult.Success)
    }
}