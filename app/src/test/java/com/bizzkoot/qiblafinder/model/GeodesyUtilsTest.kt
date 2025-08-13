package com.bizzkoot.qiblafinder.model

import com.bizzkoot.qiblafinder.ui.location.MapLocation
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class GeodesyUtilsTest {

    companion object {
        private const val DELTA = 1e-6 // Precision for double comparisons
        private const val DISTANCE_DELTA = 0.1 // 100m precision for distance tests
        private const val BEARING_DELTA = 0.1 // 0.1 degree precision for bearing tests
        
        // Known test locations
        private const val NEW_YORK_LAT = 40.7128
        private const val NEW_YORK_LON = -74.0060
        private const val LONDON_LAT = 51.5074
        private const val LONDON_LON = -0.1278
        private const val SYDNEY_LAT = -33.8688
        private const val SYDNEY_LON = 151.2093
        private const val KAABA_LAT = 21.4225
        private const val KAABA_LONGITUDE = 39.8262
    }

    @Test
    fun testCalculateQiblaBearing_fromNewYork() {
        val bearing = GeodesyUtils.calculateQiblaBearing(NEW_YORK_LAT, NEW_YORK_LON)
        // Expected bearing from New York to Kaaba is approximately 58.48 degrees
        assertEquals(58.48, bearing, BEARING_DELTA)
    }

    @Test
    fun testCalculateQiblaBearing_fromLondon() {
        val bearing = GeodesyUtils.calculateQiblaBearing(LONDON_LAT, LONDON_LON)
        // Expected bearing from London to Kaaba is approximately 118.99 degrees
        assertEquals(118.99, bearing, BEARING_DELTA)
    }

    @Test
    fun testCalculateDistance_knownDistances() {
        // Distance from New York to London should be reasonable (between 5500-5600 km)
        val distance = GeodesyUtils.calculateDistance(NEW_YORK_LAT, NEW_YORK_LON, LONDON_LAT, LONDON_LON)
        assertTrue("Distance should be between 5500-5600 km, got $distance", distance in 5500.0..5600.0)
    }

    @Test
    fun testCalculateDistanceToKaaba_fromNewYork() {
        val distance = GeodesyUtils.calculateDistanceToKaaba(NEW_YORK_LAT, NEW_YORK_LON)
        // Expected distance from New York to Kaaba should be reasonable (between 8000-12000 km)
        assertTrue("Distance should be between 8000-12000 km, got $distance", distance in 8000.0..12000.0)
    }

    @Test
    fun testCalculateDistanceToKaaba_fromKaaba() {
        val distance = GeodesyUtils.calculateDistanceToKaaba(KAABA_LAT, KAABA_LONGITUDE)
        // Distance from Kaaba to itself should be 0
        assertEquals(0.0, distance, DISTANCE_DELTA)
    }

    @Test
    fun testCalculateGreatCirclePath_basicPath() {
        val path = GeodesyUtils.calculateGreatCirclePath(
            NEW_YORK_LAT, NEW_YORK_LON,
            LONDON_LAT, LONDON_LON,
            10
        )
        
        // Should have 11 points (0 to 10 inclusive)
        assertEquals(11, path.size)
        
        // First point should be start location
        assertEquals(NEW_YORK_LAT, path.first().latitude, DELTA)
        assertEquals(NEW_YORK_LON, path.first().longitude, DELTA)
        
        // Last point should be end location
        assertEquals(LONDON_LAT, path.last().latitude, DELTA)
        assertEquals(LONDON_LON, path.last().longitude, DELTA)
    }

    @Test
    fun testCalculateGreatCirclePath_sameLocation() {
        val path = GeodesyUtils.calculateGreatCirclePath(
            NEW_YORK_LAT, NEW_YORK_LON,
            NEW_YORK_LAT, NEW_YORK_LON,
            10
        )
        
        // Should return single point when start and end are the same
        assertEquals(1, path.size)
        assertEquals(NEW_YORK_LAT, path.first().latitude, DELTA)
        assertEquals(NEW_YORK_LON, path.first().longitude, DELTA)
    }

    @Test
    fun testCalculateGreatCirclePath_antipodal() {
        // Test with nearly antipodal points (opposite sides of Earth)
        val path = GeodesyUtils.calculateGreatCirclePath(
            0.0, 0.0,  // Equator, Prime Meridian
            0.0, 179.9, // Nearly opposite longitude
            20
        )
        
        // Should generate a valid path
        assertTrue(path.size > 1)
        assertEquals(0.0, path.first().latitude, DELTA)
        assertEquals(0.0, path.first().longitude, DELTA)
    }

    @Test
    fun testCalculateGreatCirclePath_crossingDateLine() {
        // Test path crossing the International Date Line
        val path = GeodesyUtils.calculateGreatCirclePath(
            35.6762, 139.6503, // Tokyo
            21.3099, -157.8581, // Honolulu
            10
        )
        
        assertEquals(11, path.size)
        
        // Verify start and end points
        assertEquals(35.6762, path.first().latitude, DELTA)
        assertEquals(139.6503, path.first().longitude, DELTA)
        assertEquals(21.3099, path.last().latitude, DELTA)
        assertEquals(-157.8581, path.last().longitude, DELTA)
    }

    @Test
    fun testCalculateGreatCirclePath_polarRegions() {
        // Test path involving polar regions
        val path = GeodesyUtils.calculateGreatCirclePath(
            80.0, 0.0,   // Near North Pole
            -80.0, 180.0, // Near South Pole
            15
        )
        
        assertEquals(16, path.size)
        
        // Verify start and end points
        assertEquals(80.0, path.first().latitude, DELTA)
        assertEquals(0.0, path.first().longitude, DELTA)
        assertEquals(-80.0, path.last().latitude, DELTA)
        assertEquals(180.0, path.last().longitude, DELTA)
    }

    @Test
    fun testCalculateGreatCirclePath_pathContinuity() {
        val path = GeodesyUtils.calculateGreatCirclePath(
            NEW_YORK_LAT, NEW_YORK_LON,
            LONDON_LAT, LONDON_LON,
            50
        )
        
        // Verify path continuity - each point should be reasonably close to the next
        for (i in 0 until path.size - 1) {
            val distance = GeodesyUtils.calculateDistance(
                path[i].latitude, path[i].longitude,
                path[i + 1].latitude, path[i + 1].longitude
            )
            // Each segment should be less than 200km for 50 segments
            assertTrue("Segment $i distance $distance is too large", distance < 200.0)
        }
    }

    @Test
    fun testCalculateGreatCirclePath_pathAccuracy() {
        // Test that intermediate points lie on the great circle
        val path = GeodesyUtils.calculateGreatCirclePath(
            NEW_YORK_LAT, NEW_YORK_LON,
            LONDON_LAT, LONDON_LON,
            20
        )
        
        // Calculate total distance along path
        var totalPathDistance = 0.0
        for (i in 0 until path.size - 1) {
            totalPathDistance += GeodesyUtils.calculateDistance(
                path[i].latitude, path[i].longitude,
                path[i + 1].latitude, path[i + 1].longitude
            )
        }
        
        // Direct great circle distance
        val directDistance = GeodesyUtils.calculateDistance(
            NEW_YORK_LAT, NEW_YORK_LON,
            LONDON_LAT, LONDON_LON
        )
        
        // Path distance should be very close to direct distance
        assertEquals(directDistance, totalPathDistance, 10.0) // 10km tolerance
    }

    @Test
    fun testCalculateGreatCirclePath_invalidLatitude() {
        // Legacy method should return empty list for invalid input
        val result = GeodesyUtils.calculateGreatCirclePath(
            91.0, 0.0, // Invalid latitude > 90
            0.0, 0.0,
            10
        )
        assertTrue("Should return empty list for invalid latitude", result.isEmpty())
    }

    @Test
    fun testCalculateGreatCirclePath_invalidLongitude() {
        // Legacy method should return empty list for invalid input
        val result = GeodesyUtils.calculateGreatCirclePath(
            0.0, 181.0, // Invalid longitude > 180
            0.0, 0.0,
            10
        )
        assertTrue("Should return empty list for invalid longitude", result.isEmpty())
    }

    @Test
    fun testCalculateGreatCirclePath_negativeSegments() {
        // Legacy method should return empty list for invalid input
        val result = GeodesyUtils.calculateGreatCirclePath(
            0.0, 0.0,
            10.0, 10.0,
            -1 // Invalid negative segments
        )
        assertTrue("Should return empty list for negative segments", result.isEmpty())
    }

    @Test
    fun testCalculateGreatCirclePath_singleSegment() {
        val path = GeodesyUtils.calculateGreatCirclePath(
            NEW_YORK_LAT, NEW_YORK_LON,
            LONDON_LAT, LONDON_LON,
            1
        )
        
        // Should have 2 points for 1 segment
        assertEquals(2, path.size)
        assertEquals(NEW_YORK_LAT, path.first().latitude, DELTA)
        assertEquals(NEW_YORK_LON, path.first().longitude, DELTA)
        assertEquals(LONDON_LAT, path.last().latitude, DELTA)
        assertEquals(LONDON_LON, path.last().longitude, DELTA)
    }

    @Test
    fun testCalculateGreatCirclePath_manySegments() {
        val path = GeodesyUtils.calculateGreatCirclePath(
            NEW_YORK_LAT, NEW_YORK_LON,
            LONDON_LAT, LONDON_LON,
            1000
        )
        
        // Should have 1001 points for 1000 segments
        assertEquals(1001, path.size)
        
        // Verify start and end points are still accurate
        assertEquals(NEW_YORK_LAT, path.first().latitude, DELTA)
        assertEquals(NEW_YORK_LON, path.first().longitude, DELTA)
        assertEquals(LONDON_LAT, path.last().latitude, DELTA)
        assertEquals(LONDON_LON, path.last().longitude, DELTA)
    }

    @Test
    fun testCalculateGreatCirclePath_equatorCrossing() {
        // Test path crossing the equator
        val path = GeodesyUtils.calculateGreatCirclePath(
            10.0, 0.0,  // North of equator
            -10.0, 0.0, // South of equator
            20
        )
        
        assertEquals(21, path.size)
        
        // Should cross equator (latitude = 0) somewhere in the middle
        val hasEquatorCrossing = path.any { abs(it.latitude) < 0.1 }
        assertTrue("Path should cross near the equator", hasEquatorCrossing)
    }

    @Test
    fun testCalculateGreatCirclePath_primeMeridianCrossing() {
        // Test path crossing the Prime Meridian
        val path = GeodesyUtils.calculateGreatCirclePath(
            50.0, -10.0, // West of Prime Meridian
            50.0, 10.0,  // East of Prime Meridian
            20
        )
        
        assertEquals(21, path.size)
        
        // Should cross Prime Meridian (longitude = 0) somewhere in the middle
        val hasPrimeMeridianCrossing = path.any { abs(it.longitude) < 0.1 }
        assertTrue("Path should cross near the Prime Meridian", hasPrimeMeridianCrossing)
    }

    @Test
    fun testCalculateGreatCirclePath_performanceBenchmark() {
        val startTime = System.currentTimeMillis()
        
        // Generate a complex path with many segments
        val path = GeodesyUtils.calculateGreatCirclePath(
            NEW_YORK_LAT, NEW_YORK_LON,
            SYDNEY_LAT, SYDNEY_LON,
            1000
        )
        
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        
        // Should complete within reasonable time (less than 100ms)
        assertTrue("Calculation took too long: ${duration}ms", duration < 100)
        assertEquals(1001, path.size)
    }

    // --- Enhanced Error Handling Tests ---
    
    @Test
    fun testErrorHandling_invalidCoordinates() {
        // Test invalid latitude (out of range)
        val invalidLatResult = GeodesyUtils.calculateQiblaBearingSafe(91.0, 0.0)
        assertTrue(invalidLatResult is GeodesyResult.Error)
        assertTrue((invalidLatResult as GeodesyResult.Error).message.contains("Latitude out of range"))
        
        // Test invalid longitude (out of range)
        val invalidLonResult = GeodesyUtils.calculateQiblaBearingSafe(0.0, 181.0)
        assertTrue(invalidLonResult is GeodesyResult.Error)
        assertTrue((invalidLonResult as GeodesyResult.Error).message.contains("Longitude out of range"))
        
        // Test NaN coordinates
        val nanLatResult = GeodesyUtils.calculateQiblaBearingSafe(Double.NaN, 0.0)
        assertTrue(nanLatResult is GeodesyResult.Error)
        assertTrue((nanLatResult as GeodesyResult.Error).message.contains("Invalid latitude"))
        
        // Test infinite coordinates
        val infiniteLonResult = GeodesyUtils.calculateQiblaBearingSafe(0.0, Double.POSITIVE_INFINITY)
        assertTrue(infiniteLonResult is GeodesyResult.Error)
        assertTrue((infiniteLonResult as GeodesyResult.Error).message.contains("Invalid longitude"))
    }
    
    @Test
    fun testErrorHandling_gracefulFallbacks() {
        // Test legacy methods provide fallback values on error
        val fallbackBearing = GeodesyUtils.calculateQiblaBearing(Double.NaN, 0.0)
        assertEquals(0.0, fallbackBearing, 0.001)
        
        val fallbackDistance = GeodesyUtils.calculateDistance(Double.NaN, 0.0, 0.0, 0.0)
        assertEquals(0.0, fallbackDistance, 0.001)
        
        val fallbackPath = GeodesyUtils.calculateGreatCirclePath(Double.NaN, 0.0, 0.0, 0.0, 10)
        assertTrue(fallbackPath.isEmpty())
    }
    
    @Test
    fun testErrorHandling_memoryPressureHandling() {
        // Test excessive segments are rejected
        val excessiveSegmentsResult = GeodesyUtils.calculateGreatCirclePathSafe(0.0, 0.0, 1.0, 1.0, 2000)
        assertTrue(excessiveSegmentsResult is GeodesyResult.Error)
        assertTrue((excessiveSegmentsResult as GeodesyResult.Error).message.contains("exceed maximum limit"))
        
        // Test negative segments are rejected
        val negativeSegmentsResult = GeodesyUtils.calculateGreatCirclePathSafe(0.0, 0.0, 1.0, 1.0, -5)
        assertTrue(negativeSegmentsResult is GeodesyResult.Error)
        assertTrue((negativeSegmentsResult as GeodesyResult.Error).message.contains("must be positive"))
        
        // Test zero segments are rejected
        val zeroSegmentsResult = GeodesyUtils.calculateGreatCirclePathSafe(0.0, 0.0, 1.0, 1.0, 0)
        assertTrue(zeroSegmentsResult is GeodesyResult.Error)
        assertTrue((zeroSegmentsResult as GeodesyResult.Error).message.contains("must be positive"))
    }
    
    @Test
    fun testErrorHandling_numericalPrecisionEdgeCases() {
        // Test calculation at Kaaba location (should return 0 bearing)
        val kaabaResult = GeodesyUtils.calculateQiblaBearingSafe(KAABA_LAT, KAABA_LONGITUDE)
        assertTrue(kaabaResult is GeodesyResult.Success)
        assertEquals(0.0, (kaabaResult as GeodesyResult.Success).data, 0.001)
        
        // Test very close points (should handle numerical precision)
        val closePointsResult = GeodesyUtils.calculateDistanceSafe(0.0, 0.0, 0.0000001, 0.0000001)
        assertTrue(closePointsResult is GeodesyResult.Success)
        assertTrue((closePointsResult as GeodesyResult.Success).data >= 0.0)
        
        // Test points that might cause numerical issues
        val precisionResult = GeodesyUtils.calculateQiblaBearingSafe(89.9999, 179.9999)
        assertTrue(precisionResult is GeodesyResult.Success)
        val bearing = (precisionResult as GeodesyResult.Success).data
        assertTrue(bearing >= 0.0 && bearing <= 360.0)
    }
    
    @Test
    fun testErrorHandling_safeMethodsReturnSuccess() {
        // Test that safe methods return Success for valid inputs
        val validBearingResult = GeodesyUtils.calculateQiblaBearingSafe(NEW_YORK_LAT, NEW_YORK_LON)
        assertTrue(validBearingResult is GeodesyResult.Success)
        val bearing = (validBearingResult as GeodesyResult.Success).data
        assertTrue(bearing >= 0.0 && bearing <= 360.0)
        
        val validDistanceResult = GeodesyUtils.calculateDistanceSafe(NEW_YORK_LAT, NEW_YORK_LON, LONDON_LAT, LONDON_LON)
        assertTrue(validDistanceResult is GeodesyResult.Success)
        val distance = (validDistanceResult as GeodesyResult.Success).data
        assertTrue(distance > 0.0)
        
        val validPathResult = GeodesyUtils.calculateGreatCirclePathSafe(NEW_YORK_LAT, NEW_YORK_LON, LONDON_LAT, LONDON_LON, 10)
        assertTrue(validPathResult is GeodesyResult.Success)
        val path = (validPathResult as GeodesyResult.Success).data
        assertEquals(11, path.size)
    }
    
    @Test
    fun testErrorHandling_pathCalculationEdgeCases() {
        // Test identical start and end points
        val identicalResult = GeodesyUtils.calculateGreatCirclePathSafe(0.0, 0.0, 0.0, 0.0, 10)
        assertTrue(identicalResult is GeodesyResult.Success)
        assertEquals(1, (identicalResult as GeodesyResult.Success).data.size)
        
        // Test antipodal points (opposite sides of Earth)
        val antipodalResult = GeodesyUtils.calculateGreatCirclePathSafe(0.0, 0.0, 0.0, 180.0, 10)
        assertTrue(antipodalResult is GeodesyResult.Success)
        assertTrue((antipodalResult as GeodesyResult.Success).data.isNotEmpty())
        
        // Test path with invalid coordinates in the middle of calculation
        val invalidEndResult = GeodesyUtils.calculateGreatCirclePathSafe(0.0, 0.0, 91.0, 0.0, 10)
        assertTrue(invalidEndResult is GeodesyResult.Error)
    }

    // --- Comprehensive Accuracy Tests Against Known Values ---
    
    @Test
    fun testGreatCircleAccuracy_knownDistances() {
        // Test against known great circle distances with high precision
        
        // New York to London: ~5585 km
        val nyToLondonDistance = GeodesyUtils.calculateDistance(NEW_YORK_LAT, NEW_YORK_LON, LONDON_LAT, LONDON_LON)
        assertEquals("NY to London distance", 5585.0, nyToLondonDistance, 50.0) // 50km tolerance
        
        // Sydney to London: ~17016 km  
        val sydneyToLondonDistance = GeodesyUtils.calculateDistance(SYDNEY_LAT, SYDNEY_LON, LONDON_LAT, LONDON_LON)
        assertEquals("Sydney to London distance", 17016.0, sydneyToLondonDistance, 100.0) // 100km tolerance
        
        // New York to Sydney: ~15993 km
        val nyToSydneyDistance = GeodesyUtils.calculateDistance(NEW_YORK_LAT, NEW_YORK_LON, SYDNEY_LAT, SYDNEY_LON)
        assertEquals("NY to Sydney distance", 15993.0, nyToSydneyDistance, 100.0) // 100km tolerance
    }
    
    @Test
    fun testQiblaBearingAccuracy_knownValues() {
        // Test against known Qibla bearings with high precision
        
        // From New York: ~58.48°
        val nyBearing = GeodesyUtils.calculateQiblaBearing(NEW_YORK_LAT, NEW_YORK_LON)
        assertEquals("NY Qibla bearing", 58.48, nyBearing, 0.5)
        
        // From London: ~118.99°
        val londonBearing = GeodesyUtils.calculateQiblaBearing(LONDON_LAT, LONDON_LON)
        assertEquals("London Qibla bearing", 118.99, londonBearing, 0.5)
        
        // From Sydney: ~277.50°
        val sydneyBearing = GeodesyUtils.calculateQiblaBearing(SYDNEY_LAT, SYDNEY_LON)
        assertEquals("Sydney Qibla bearing", 277.50, sydneyBearing, 2.0)
        
        // From Cairo (close to Kaaba): ~135.03°
        val cairoBearing = GeodesyUtils.calculateQiblaBearing(30.0444, 31.2357)
        assertEquals("Cairo Qibla bearing", 135.03, cairoBearing, 2.0)
    }
    
    @Test
    fun testDistanceToKaabaAccuracy_knownValues() {
        // Test that distance calculations to Kaaba are reasonable
        
        // From New York: should be a reasonable distance
        val nyDistance = GeodesyUtils.calculateDistanceToKaaba(NEW_YORK_LAT, NEW_YORK_LON)
        assertTrue("NY to Kaaba distance should be positive and reasonable", nyDistance > 0.0 && nyDistance < 20015.0)
        
        // From London: should be a reasonable distance
        val londonDistance = GeodesyUtils.calculateDistanceToKaaba(LONDON_LAT, LONDON_LON)
        assertTrue("London to Kaaba distance should be positive and reasonable", londonDistance > 0.0 && londonDistance < 20015.0)
        
        // From Sydney: should be a reasonable distance
        val sydneyDistance = GeodesyUtils.calculateDistanceToKaaba(SYDNEY_LAT, SYDNEY_LON)
        assertTrue("Sydney to Kaaba distance should be positive and reasonable", sydneyDistance > 0.0 && sydneyDistance < 20015.0)
        
        // From Jakarta: should be a reasonable distance
        val jakartaDistance = GeodesyUtils.calculateDistanceToKaaba(-6.2088, 106.8456)
        assertTrue("Jakarta to Kaaba distance should be positive and reasonable", jakartaDistance > 0.0 && jakartaDistance < 20015.0)
        
        // Verify that closer locations have shorter distances
        assertTrue("London should be closer to Kaaba than Sydney", londonDistance < sydneyDistance)
    }
    
    // --- Coordinate Transformation Tests ---
    
    @Test
    fun testCoordinateTransformation_sphericalToCartesian() {
        // Test the internal spherical to Cartesian conversion accuracy
        // We can't directly test internal methods, but we can verify through path generation
        
        val path = GeodesyUtils.calculateGreatCirclePath(0.0, 0.0, 0.0, 90.0, 4)
        assertEquals("Path should have 5 points", 5, path.size)
        
        // First point should be origin
        assertEquals("Start latitude", 0.0, path[0].latitude, DELTA)
        assertEquals("Start longitude", 0.0, path[0].longitude, DELTA)
        
        // Last point should be destination
        assertEquals("End latitude", 0.0, path[4].latitude, DELTA)
        assertEquals("End longitude", 90.0, path[4].longitude, DELTA)
        
        // Intermediate points should be on the equator
        for (i in 1..3) {
            assertEquals("Intermediate point $i should be on equator", 0.0, path[i].latitude, 0.1)
            assertTrue("Longitude should increase", path[i].longitude > path[i-1].longitude)
        }
    }
    
    @Test
    fun testCoordinateTransformation_cartesianToSpherical() {
        // Test conversion back from Cartesian to spherical coordinates
        // Verify through round-trip accuracy in path generation
        
        val originalLat = 45.0
        val originalLon = -75.0
        val path = GeodesyUtils.calculateGreatCirclePath(
            originalLat, originalLon,
            originalLat, originalLon, // Same point
            1
        )
        
        assertEquals("Should return single point for identical coordinates", 1, path.size)
        assertEquals("Latitude should be preserved", originalLat, path[0].latitude, DELTA)
        assertEquals("Longitude should be preserved", originalLon, path[0].longitude, DELTA)
    }
    
    @Test
    fun testCoordinateTransformation_normalizeAngles() {
        // Test angle normalization in bearing calculations
        
        // Test bearing from point near date line
        val bearing1 = GeodesyUtils.calculateQiblaBearing(0.0, 179.0)
        assertTrue("Bearing should be normalized to 0-360", bearing1 >= 0.0 && bearing1 <= 360.0)
        
        val bearing2 = GeodesyUtils.calculateQiblaBearing(0.0, -179.0)
        assertTrue("Bearing should be normalized to 0-360", bearing2 >= 0.0 && bearing2 <= 360.0)
        
        // Test bearing from polar regions
        val polarBearing = GeodesyUtils.calculateQiblaBearing(85.0, 0.0)
        assertTrue("Polar bearing should be normalized", polarBearing >= 0.0 && polarBearing <= 360.0)
    }
    
    // --- Edge Case Tests ---
    
    @Test
    fun testEdgeCases_polarRegions() {
        // Test calculations near the poles
        
        // North Pole
        val northPoleBearing = GeodesyUtils.calculateQiblaBearing(89.9, 0.0)
        assertTrue("North pole bearing should be valid", northPoleBearing >= 0.0 && northPoleBearing <= 360.0)
        
        val northPoleDistance = GeodesyUtils.calculateDistanceToKaaba(89.9, 0.0)
        assertTrue("North pole distance should be reasonable", northPoleDistance > 7000.0 && northPoleDistance < 12000.0)
        
        // South Pole
        val southPoleBearing = GeodesyUtils.calculateQiblaBearing(-89.9, 0.0)
        assertTrue("South pole bearing should be valid", southPoleBearing >= 0.0 && southPoleBearing <= 360.0)
        
        val southPoleDistance = GeodesyUtils.calculateDistanceToKaaba(-89.9, 0.0)
        assertTrue("South pole distance should be reasonable", southPoleDistance > 10000.0 && southPoleDistance < 25000.0)
    }
    
    @Test
    fun testEdgeCases_internationalDateLine() {
        // Test calculations crossing the International Date Line
        
        // Points on either side of date line
        val eastSideBearing = GeodesyUtils.calculateQiblaBearing(0.0, 179.0)
        val westSideBearing = GeodesyUtils.calculateQiblaBearing(0.0, -179.0)
        
        assertTrue("East side bearing should be valid", eastSideBearing >= 0.0 && eastSideBearing <= 360.0)
        assertTrue("West side bearing should be valid", westSideBearing >= 0.0 && westSideBearing <= 360.0)
        
        // Path crossing date line
        val dateLinePath = GeodesyUtils.calculateGreatCirclePath(0.0, 179.0, 0.0, -179.0, 10)
        assertTrue("Date line path should be generated", dateLinePath.size > 1)
        
        // Verify path continuity across date line
        for (i in 0 until dateLinePath.size - 1) {
            val distance = GeodesyUtils.calculateDistance(
                dateLinePath[i].latitude, dateLinePath[i].longitude,
                dateLinePath[i + 1].latitude, dateLinePath[i + 1].longitude
            )
            assertTrue("Date line path segments should be reasonable: segment $i = ${distance}km", distance < 500.0)
        }
    }
    
    @Test
    fun testEdgeCases_antipodalPoints() {
        // Test calculations with antipodal points (opposite sides of Earth)
        
        val antipodalPath = GeodesyUtils.calculateGreatCirclePath(0.0, 0.0, 0.0, 180.0, 20)
        assertTrue("Antipodal path should be generated", antipodalPath.isNotEmpty())
        
        // Verify start and end points
        assertEquals("Antipodal start lat", 0.0, antipodalPath.first().latitude, DELTA)
        assertEquals("Antipodal start lon", 0.0, antipodalPath.first().longitude, DELTA)
        assertEquals("Antipodal end lat", 0.0, antipodalPath.last().latitude, DELTA)
        assertEquals("Antipodal end lon", 180.0, antipodalPath.last().longitude, DELTA)
        
        // Test nearly antipodal points
        val nearAntipodalPath = GeodesyUtils.calculateGreatCirclePath(0.0, 0.0, 0.0, 179.9, 15)
        assertTrue("Near antipodal path should be generated", nearAntipodalPath.isNotEmpty())
    }
    
    @Test
    fun testEdgeCases_veryClosePoints() {
        // Test calculations with points very close together
        
        val closeDistance = GeodesyUtils.calculateDistance(0.0, 0.0, 0.0001, 0.0001)
        assertTrue("Close points distance should be small but positive", closeDistance > 0.0 && closeDistance < 0.1)
        
        val closeBearing = GeodesyUtils.calculateQiblaBearing(KAABA_LAT + 0.0001, KAABA_LONGITUDE + 0.0001)
        assertTrue("Close to Kaaba bearing should be valid", closeBearing >= 0.0 && closeBearing <= 360.0)
        
        val closePath = GeodesyUtils.calculateGreatCirclePath(0.0, 0.0, 0.0001, 0.0001, 5)
        assertEquals("Close points path should have correct number of points", 6, closePath.size)
    }
    
    // --- Performance Benchmark Tests ---
    
    @Test
    fun testPerformance_bearingCalculation() {
        val iterations = 1000
        val startTime = System.currentTimeMillis()
        
        for (i in 0 until iterations) {
            val lat = -90.0 + (i % 180)
            val lon = -180.0 + (i % 360)
            GeodesyUtils.calculateQiblaBearing(lat, lon)
        }
        
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        val avgTime = duration.toDouble() / iterations
        
        assertTrue("Bearing calculation should be fast: ${avgTime}ms per calculation", avgTime < 1.0)
        println("Bearing calculation performance: ${avgTime}ms average over $iterations iterations")
    }
    
    @Test
    fun testPerformance_distanceCalculation() {
        val iterations = 1000
        val startTime = System.currentTimeMillis()
        
        for (i in 0 until iterations) {
            val lat1 = -90.0 + (i % 180)
            val lon1 = -180.0 + (i % 360)
            val lat2 = -90.0 + ((i + 1) % 180)
            val lon2 = -180.0 + ((i + 1) % 360)
            GeodesyUtils.calculateDistance(lat1, lon1, lat2, lon2)
        }
        
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        val avgTime = duration.toDouble() / iterations
        
        assertTrue("Distance calculation should be fast: ${avgTime}ms per calculation", avgTime < 1.0)
        println("Distance calculation performance: ${avgTime}ms average over $iterations iterations")
    }
    
    @Test
    fun testPerformance_pathGenerationSmall() {
        val iterations = 100
        val segments = 50
        val startTime = System.currentTimeMillis()
        
        for (i in 0 until iterations) {
            val lat1 = -45.0 + (i % 90)
            val lon1 = -90.0 + (i % 180)
            val lat2 = -45.0 + ((i + 10) % 90)
            val lon2 = -90.0 + ((i + 10) % 180)
            GeodesyUtils.calculateGreatCirclePath(lat1, lon1, lat2, lon2, segments)
        }
        
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        val avgTime = duration.toDouble() / iterations
        
        assertTrue("Small path generation should be fast: ${avgTime}ms per path", avgTime < 10.0)
        println("Small path generation performance: ${avgTime}ms average for $segments segments over $iterations iterations")
    }
    
    @Test
    fun testPerformance_pathGenerationLarge() {
        val iterations = 10
        val segments = 500
        val startTime = System.currentTimeMillis()
        
        for (i in 0 until iterations) {
            GeodesyUtils.calculateGreatCirclePath(
                NEW_YORK_LAT, NEW_YORK_LON,
                SYDNEY_LAT, SYDNEY_LON,
                segments
            )
        }
        
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        val avgTime = duration.toDouble() / iterations
        
        assertTrue("Large path generation should complete reasonably: ${avgTime}ms per path", avgTime < 100.0)
        println("Large path generation performance: ${avgTime}ms average for $segments segments over $iterations iterations")
    }
    
    @Test
    fun testPerformance_memoryUsage() {
        // Test memory usage with large path generation
        val runtime = Runtime.getRuntime()
        val initialMemory = runtime.totalMemory() - runtime.freeMemory()
        
        // Generate several large paths
        val paths = mutableListOf<List<MapLocation>>()
        for (i in 0 until 10) {
            val path = GeodesyUtils.calculateGreatCirclePath(
                NEW_YORK_LAT + i, NEW_YORK_LON + i,
                SYDNEY_LAT + i, SYDNEY_LON + i,
                200
            )
            paths.add(path)
        }
        
        val finalMemory = runtime.totalMemory() - runtime.freeMemory()
        val memoryIncrease = finalMemory - initialMemory
        val memoryPerPath = memoryIncrease / paths.size
        
        assertTrue("Memory usage should be reasonable: ${memoryPerPath} bytes per path", memoryPerPath < 1024 * 1024) // Less than 1MB per path
        println("Memory usage: ${memoryPerPath} bytes per path with 200 segments")
        
        // Clear references to allow GC
        paths.clear()
    }
    
    // --- Stress Tests ---
    
    @Test
    fun testStress_extremeCoordinates() {
        // Test with extreme but valid coordinates
        val extremeCoordinates = listOf(
            Pair(90.0, 180.0),    // North Pole, Date Line
            Pair(-90.0, -180.0),  // South Pole, Date Line
            Pair(89.999, 179.999), // Very close to North Pole
            Pair(-89.999, -179.999), // Very close to South Pole
            Pair(0.0, 0.0),       // Origin
            Pair(0.0, 180.0),     // Equator, Date Line
            Pair(0.0, -180.0)     // Equator, Date Line (other side)
        )
        
        for ((lat, lon) in extremeCoordinates) {
            val bearing = GeodesyUtils.calculateQiblaBearing(lat, lon)
            assertTrue("Extreme coordinate bearing should be valid: ($lat, $lon) -> $bearing", 
                bearing >= 0.0 && bearing <= 360.0)
            
            val distance = GeodesyUtils.calculateDistanceToKaaba(lat, lon)
            assertTrue("Extreme coordinate distance should be valid: ($lat, $lon) -> $distance", 
                distance >= 0.0 && distance <= 20015.0) // Max possible distance on Earth
        }
    }
    
    @Test
    fun testStress_rapidCalculations() {
        // Test rapid successive calculations to check for race conditions or state issues
        val results = mutableListOf<Double>()
        
        repeat(1000) {
            val bearing = GeodesyUtils.calculateQiblaBearing(NEW_YORK_LAT, NEW_YORK_LON)
            results.add(bearing)
        }
        
        // All results should be identical (no state corruption)
        val expectedBearing = results.first()
        assertTrue("All rapid calculations should produce identical results", 
            results.all { abs(it - expectedBearing) < 0.001 })
    }
    
    @Test
    fun testStress_concurrentCalculations() {
        // Test concurrent calculations (basic thread safety check)
        val results = mutableListOf<Double>()
        val threads = mutableListOf<Thread>()
        
        repeat(10) { threadIndex ->
            val thread = Thread {
                repeat(100) {
                    val bearing = GeodesyUtils.calculateQiblaBearing(
                        NEW_YORK_LAT + threadIndex * 0.1, 
                        NEW_YORK_LON + threadIndex * 0.1
                    )
                    synchronized(results) {
                        results.add(bearing)
                    }
                }
            }
            threads.add(thread)
            thread.start()
        }
        
        // Wait for all threads to complete
        threads.forEach { it.join() }
        
        assertEquals("All concurrent calculations should complete", 1000, results.size)
        assertTrue("All concurrent results should be valid", 
            results.all { it >= 0.0 && it <= 360.0 })
    }
}