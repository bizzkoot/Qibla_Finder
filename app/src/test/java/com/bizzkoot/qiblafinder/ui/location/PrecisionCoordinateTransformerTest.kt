package com.bizzkoot.qiblafinder.ui.location

import org.junit.Test
import org.junit.Assert.*
import kotlin.math.abs
import kotlin.math.pow

/**
 * Unit tests for PrecisionCoordinateTransformer
 * Validates mathematical accuracy and precision of coordinate transformations
 */
class PrecisionCoordinateTransformerTest {
    
    companion object {
        private const val PRECISION_TOLERANCE = 1e-10
        private const val GEOGRAPHIC_TOLERANCE = 1e-8 // ~1cm accuracy at equator
        
        // Test coordinates for various scenarios
        private val TEST_COORDINATES = listOf(
            // Standard locations
            37.4223 to -122.0770, // Googleplex
            40.7589 to -73.9851,  // Times Square
            51.5074 to -0.1278,   // London
            35.6762 to 139.6503,  // Tokyo
            
            // Edge cases
            0.0 to 0.0,           // Null Island
            85.05 to 180.0,       // Near maximum latitude/longitude
            -85.05 to -180.0,     // Near minimum latitude/longitude
            45.0 to 0.0,          // Prime meridian
            0.0 to 90.0,          // Equator + 90 degrees
            
            // High precision coordinates
            37.422396123456789 to -122.077012345678901,
            40.758895123456789 to -73.985123456789012
        )
        
        private val TEST_ZOOM_LEVELS = listOf(0, 1, 5, 10, 15, 18, 20)
    }
    
    @Test
    fun testLatLngToTileConversion() {
        TEST_COORDINATES.forEach { (lat, lng) ->
            TEST_ZOOM_LEVELS.forEach { zoom ->
                val (tileX, tileY) = PrecisionCoordinateTransformer.latLngToHighPrecisionTile(lat, lng, zoom)
                
                // Validate tile coordinates are within bounds
                val maxTileCoord = 2.0.pow(zoom.toDouble())
                assertTrue("TileX $tileX out of bounds for zoom $zoom", tileX >= 0.0 && tileX <= maxTileCoord)
                assertTrue("TileY $tileY out of bounds for zoom $zoom", tileY >= 0.0 && tileY <= maxTileCoord)
                
                // Validate precision is maintained
                assertTrue("TileX precision lost", tileX.isFinite())
                assertTrue("TileY precision lost", tileY.isFinite())
            }
        }
    }
    
    @Test
    fun testTileToLatLngConversion() {
        TEST_ZOOM_LEVELS.forEach { zoom ->
            val maxTileCoord = 2.0.pow(zoom.toDouble())
            
            // Test various tile coordinates
            val testTileCoords = listOf(
                0.0 to 0.0,
                maxTileCoord / 2.0 to maxTileCoord / 2.0,
                maxTileCoord to maxTileCoord,
                123.456789 to 234.567890 // High precision tile coords
            ).filter { (x, y) -> x <= maxTileCoord && y <= maxTileCoord }
            
            testTileCoords.forEach { (tileX, tileY) ->
                val (lat, lng) = PrecisionCoordinateTransformer.highPrecisionTileToLatLng(tileX, tileY, zoom)
                
                // Validate geographic bounds
                assertTrue("Latitude $lat out of bounds", lat >= -85.05112877980659 && lat <= 85.05112877980659)
                assertTrue("Longitude $lng out of bounds", lng >= -180.0 && lng <= 180.0)
                
                // Validate precision is maintained
                assertTrue("Latitude precision lost", lat.isFinite())
                assertTrue("Longitude precision lost", lng.isFinite())
            }
        }
    }
    
    @Test
    fun testRoundTripAccuracy() {
        TEST_COORDINATES.forEach { (originalLat, originalLng) ->
            TEST_ZOOM_LEVELS.forEach { zoom ->
                // Forward transformation
                val (tileX, tileY) = PrecisionCoordinateTransformer.latLngToHighPrecisionTile(
                    originalLat, originalLng, zoom
                )
                
                // Backward transformation
                val (reconstructedLat, reconstructedLng) = PrecisionCoordinateTransformer.highPrecisionTileToLatLng(
                    tileX, tileY, zoom
                )
                
                // Calculate errors
                val latError = abs(originalLat - reconstructedLat)
                val lngError = abs(originalLng - reconstructedLng)
                
                // Define acceptable error based on zoom level
                val acceptableError = when {
                    zoom <= 5 -> 1e-4
                    zoom <= 10 -> 1e-6
                    zoom <= 15 -> 1e-8
                    else -> 1e-10
                }
                
                assertTrue(
                    "Round trip latitude error too large: $latError > $acceptableError at zoom $zoom for ($originalLat, $originalLng)",
                    latError <= acceptableError
                )
                
                assertTrue(
                    "Round trip longitude error too large: $lngError > $acceptableError at zoom $zoom for ($originalLat, $originalLng)",
                    lngError <= acceptableError
                )
            }
        }
    }
    
    @Test
    fun testWebMercatorConversion() {
        TEST_COORDINATES.forEach { (lat, lng) ->
            // Forward conversion
            val (x, y) = PrecisionCoordinateTransformer.latLngToWebMercator(lat, lng)
            
            // Backward conversion
            val (reconstructedLat, reconstructedLng) = PrecisionCoordinateTransformer.webMercatorToLatLng(x, y)
            
            // Validate round trip accuracy
            val latError = abs(lat - reconstructedLat)
            val lngError = abs(lng - reconstructedLng)
            
            assertTrue("Web Mercator latitude error: $latError", latError < GEOGRAPHIC_TOLERANCE)
            assertTrue("Web Mercator longitude error: $lngError", lngError < GEOGRAPHIC_TOLERANCE)
        }
    }
    
    @Test
    fun testGreatCircleDistance() {
        // Test known distances
        val testCases = listOf(
            // Same point
            Triple(37.4223 to -122.0770, 37.4223 to -122.0770, 0.0),
            
            // Approximately 1 degree latitude difference (~111 km)
            Triple(37.0 to -122.0, 38.0 to -122.0, 111000.0),
            
            // Equator to equator, 1 degree longitude difference (~111 km)
            Triple(0.0 to 0.0, 0.0 to 1.0, 111000.0)
        )
        
        testCases.forEach { (point1, point2, expectedDistance) ->
            val (lat1, lng1) = point1
            val (lat2, lng2) = point2
            
            val calculatedDistance = PrecisionCoordinateTransformer.calculateGreatCircleDistance(
                lat1, lng1, lat2, lng2
            )
            
            val tolerance = if (expectedDistance == 0.0) 1e-6 else expectedDistance * 0.1 // 10% tolerance
            val error = abs(calculatedDistance - expectedDistance)
            
            assertTrue(
                "Distance calculation error: expected $expectedDistance, got $calculatedDistance, error $error",
                error <= tolerance
            )
        }
    }
    
    @Test
    fun testBearingCalculation() {
        val testCases = listOf(
            // Due North
            Triple(37.0 to -122.0, 38.0 to -122.0, 0.0),
            
            // Due East  
            Triple(0.0 to 0.0, 0.0 to 1.0, 90.0),
            
            // Due South
            Triple(38.0 to -122.0, 37.0 to -122.0, 180.0),
            
            // Due West
            Triple(0.0 to 1.0, 0.0 to 0.0, 270.0)
        )
        
        testCases.forEach { (point1, point2, expectedBearing) ->
            val (lat1, lng1) = point1
            val (lat2, lng2) = point2
            
            val calculatedBearing = PrecisionCoordinateTransformer.calculateBearing(
                lat1, lng1, lat2, lng2
            )
            
            val bearingError = abs(calculatedBearing - expectedBearing)
            val tolerance = 1.0 // 1 degree tolerance
            
            assertTrue(
                "Bearing calculation error: expected $expectedBearing°, got $calculatedBearing°, error $bearingError°",
                bearingError <= tolerance
            )
        }
    }
    
    @Test
    fun testCoordinateValidation() {
        // Valid coordinates
        assertTrue(PrecisionCoordinateTransformer.validateCoordinatePrecision(37.422396, -122.077012))
        assertTrue(PrecisionCoordinateTransformer.validateCoordinatePrecision(0.0, 0.0))
        assertTrue(PrecisionCoordinateTransformer.validateCoordinatePrecision(85.05, 179.99))
        
        // Invalid coordinates
        assertFalse(PrecisionCoordinateTransformer.validateCoordinatePrecision(Double.NaN, 0.0))
        assertFalse(PrecisionCoordinateTransformer.validateCoordinatePrecision(0.0, Double.POSITIVE_INFINITY))
        assertFalse(PrecisionCoordinateTransformer.validateCoordinatePrecision(91.0, 0.0)) // Invalid latitude
        assertFalse(PrecisionCoordinateTransformer.validateCoordinatePrecision(0.0, 181.0)) // Invalid longitude
    }
    
    @Test
    fun testCoordinateRounding() {
        val testCases = listOf(
            Triple(37.123456789012345, -122.987654321098765, 6),
            Triple(0.123456789, 0.987654321, 4),
            Triple(45.0, 90.0, 10)
        )
        
        testCases.forEach { (lat, lng, precision) ->
            val (roundedLat, roundedLng) = PrecisionCoordinateTransformer.roundCoordinatesToPrecision(
                lat, lng, precision
            )
            
            // Verify precision
            val latString = roundedLat.toString()
            val lngString = roundedLng.toString()
            
            val latDecimals = latString.substringAfter(".", "").length
            val lngDecimals = lngString.substringAfter(".", "").length
            
            assertTrue("Latitude precision not correct", latDecimals <= precision)
            assertTrue("Longitude precision not correct", lngDecimals <= precision)
        }
    }
    
    @Test
    fun testTileSizeCalculation() {
        TEST_ZOOM_LEVELS.forEach { zoom ->
            // Test at equator (latitude = 0)
            val tileSizeAtEquator = PrecisionCoordinateTransformer.calculateTileSizeInMeters(0.0, zoom)
            
            // At zoom 0, there's 1 tile covering the entire earth (circumference ~40,075 km)
            val expectedSize = 40075000.0 / (2.0.pow(zoom.toDouble()))
            val tolerance = expectedSize * 0.01 // 1% tolerance
            
            val error = abs(tileSizeAtEquator - expectedSize)
            assertTrue(
                "Tile size calculation error at zoom $zoom: expected $expectedSize, got $tileSizeAtEquator",
                error <= tolerance
            )
        }
    }
    
    @Test
    fun testPixelResolution() {
        val testCases = listOf(
            Triple(0.0, 10, 1.0),   // Equator, zoom 10, no digital zoom
            Triple(45.0, 15, 2.0),  // 45° latitude, zoom 15, 2x digital zoom
            Triple(60.0, 5, 0.5)    // 60° latitude, zoom 5, 0.5x digital zoom
        )
        
        testCases.forEach { (latitude, zoom, digitalZoom) ->
            val resolution = PrecisionCoordinateTransformer.calculatePixelResolution(latitude, zoom, digitalZoom)
            
            // Verify resolution is positive and reasonable
            assertTrue("Pixel resolution should be positive", resolution > 0.0)
            assertTrue("Pixel resolution should be finite", resolution.isFinite())
            
            // Higher zoom should give better (smaller) resolution
            if (zoom < 18) {
                val higherZoomResolution = PrecisionCoordinateTransformer.calculatePixelResolution(latitude, zoom + 1, digitalZoom)
                assertTrue("Higher zoom should give better resolution", higherZoomResolution < resolution)
            }
        }
    }
    
    @Test 
    fun testHighPrecisionMaintenance() {
        // Test that high precision is maintained through complex operations
        val highPrecisionLat = 37.422396123456789012345
        val highPrecisionLng = -122.077012345678901234567
        
        TEST_ZOOM_LEVELS.forEach { zoom ->
            val (tileX, tileY) = PrecisionCoordinateTransformer.latLngToHighPrecisionTile(
                highPrecisionLat, highPrecisionLng, zoom
            )
            
            // Verify that precision is not lost in the tile coordinates
            assertTrue("TileX should maintain high precision", tileX.toString().contains("."))
            assertTrue("TileY should maintain high precision", tileY.toString().contains("."))
            
            // Verify decimal places are reasonable for the zoom level
            val expectedMinDecimals = when {
                zoom <= 5 -> 3
                zoom <= 10 -> 6
                zoom <= 15 -> 8
                else -> 10
            }
            
            val tileXDecimals = tileX.toString().substringAfter(".", "").length
            val tileYDecimals = tileY.toString().substringAfter(".", "").length
            
            assertTrue("TileX should have sufficient precision", tileXDecimals >= expectedMinDecimals)
            assertTrue("TileY should have sufficient precision", tileYDecimals >= expectedMinDecimals)
        }
    }
}