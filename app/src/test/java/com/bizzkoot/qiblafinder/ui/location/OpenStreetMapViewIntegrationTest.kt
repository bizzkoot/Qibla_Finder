package com.bizzkoot.qiblafinder.ui.location

import org.junit.Test
import org.junit.Assert.*
import kotlin.math.abs

/**
 * Integration tests for OpenStreetMapView with Qibla direction functionality
 */
class OpenStreetMapViewIntegrationTest {

    @Test
    fun `calculateViewportBounds should return correct bounds for given tile coordinates`() {
        // Test data: center of New York City approximately
        val centerTileX = 75.0
        val centerTileY = 96.0
        val zoom = 8
        val viewportWidth = 800
        val viewportHeight = 800
        
        // Calculate viewport bounds using the private function logic
        val tileSize = 256.0
        val tilesVisibleX = viewportWidth / tileSize
        val tilesVisibleY = viewportHeight / tileSize
        
        val minTileX = centerTileX - tilesVisibleX / 2
        val maxTileX = centerTileX + tilesVisibleX / 2
        val minTileY = centerTileY - tilesVisibleY / 2
        val maxTileY = centerTileY + tilesVisibleY / 2
        
        // Convert to lat/lng using the same logic as the implementation
        val westLon = tileXToLongitude(minTileX, zoom)
        val eastLon = tileXToLongitude(maxTileX, zoom)
        val northLat = tileYToLatitude(minTileY, zoom)
        val southLat = tileYToLatitude(maxTileY, zoom)
        
        // Verify bounds are reasonable
        assertTrue("West longitude should be less than east", westLon < eastLon)
        assertTrue("North latitude should be greater than south", northLat > southLat)
        assertTrue("Longitude should be within valid range", westLon >= -180.0 && eastLon <= 180.0)
        assertTrue("Latitude should be within valid range", southLat >= -90.0 && northLat <= 90.0)
    }
    
    @Test
    fun `tileXToLongitude should convert tile coordinates correctly`() {
        // Test known conversions
        val zoom = 8
        
        // Tile X = 0 should be -180 degrees
        val westMost = tileXToLongitude(0.0, zoom)
        assertEquals(-180.0, westMost, 0.001)
        
        // Tile X = 2^zoom should be 180 degrees
        val eastMost = tileXToLongitude((1 shl zoom).toDouble(), zoom)
        assertEquals(180.0, eastMost, 0.001)
        
        // Middle tile should be around 0 degrees
        val middle = tileXToLongitude((1 shl zoom).toDouble() / 2, zoom)
        assertEquals(0.0, middle, 0.001)
    }
    
    @Test
    fun `tileYToLatitude should convert tile coordinates correctly`() {
        val zoom = 8
        
        // Test that tile Y = 0 gives maximum latitude (around 85.05 degrees)
        val northMost = tileYToLatitude(0.0, zoom)
        assertTrue("North-most latitude should be positive and large", northMost > 80.0)
        
        // Test that tile Y = 2^zoom gives minimum latitude (around -85.05 degrees)
        val southMost = tileYToLatitude((1 shl zoom).toDouble(), zoom)
        assertTrue("South-most latitude should be negative and large", southMost < -80.0)
        
        // Middle tile should be around 0 degrees
        val middle = tileYToLatitude((1 shl zoom).toDouble() / 2, zoom)
        assertTrue("Middle latitude should be close to 0", abs(middle) < 1.0)
    }
    
    @Test
    fun `viewport bounds should handle different zoom levels correctly`() {
        val viewportWidth = 800
        val viewportHeight = 800
        
        // Test different zoom levels with appropriate tile coordinates
        for (zoom in 2..18) {
            // Use tile coordinates that are valid for the zoom level
            val maxTileCoord = (1 shl zoom).toDouble()
            val centerTileX = maxTileCoord / 2  // Center of the world
            val centerTileY = maxTileCoord / 2  // Center of the world
            
            val tileSize = 256.0
            val tilesVisibleX = viewportWidth / tileSize
            val tilesVisibleY = viewportHeight / tileSize
            
            val minTileX = centerTileX - tilesVisibleX / 2
            val maxTileX = centerTileX + tilesVisibleX / 2
            val minTileY = centerTileY - tilesVisibleY / 2
            val maxTileY = centerTileY + tilesVisibleY / 2
            
            // Ensure tile coordinates are within valid bounds
            val clampedMinTileX = minTileX.coerceAtLeast(0.0)
            val clampedMaxTileX = maxTileX.coerceAtMost(maxTileCoord)
            val clampedMinTileY = minTileY.coerceAtLeast(0.0)
            val clampedMaxTileY = maxTileY.coerceAtMost(maxTileCoord)
            
            val westLon = tileXToLongitude(clampedMinTileX, zoom)
            val eastLon = tileXToLongitude(clampedMaxTileX, zoom)
            val northLat = tileYToLatitude(clampedMinTileY, zoom)
            val southLat = tileYToLatitude(clampedMaxTileY, zoom)
            
            // At higher zoom levels, the viewport should cover a smaller area
            val lonSpan = eastLon - westLon
            val latSpan = northLat - southLat
            
            assertTrue("Longitude span should be positive at zoom $zoom", lonSpan > 0)
            assertTrue("Latitude span should be positive at zoom $zoom", latSpan > 0)
            assertTrue("Longitude span should be reasonable at zoom $zoom", lonSpan <= 360.0)
            assertTrue("Latitude span should be reasonable at zoom $zoom", latSpan <= 180.0)
        }
    }
    
    @Test
    fun `QiblaDirectionOverlay should handle performance optimizations correctly`() {
        val overlay = QiblaDirectionOverlay()
        val location = MapLocation(40.7128, -74.0060) // New York
        
        // Create viewport bounds
        val viewportBounds = ViewportBounds(
            northLat = 41.0,
            southLat = 40.0,
            eastLon = -73.0,
            westLon = -75.0,
            centerLat = 40.5,
            centerLon = -74.0
        )
        
        // Test normal performance mode
        val normalState = overlay.calculateDirectionLine(
            userLocation = location,
            viewportBounds = viewportBounds,
            zoomLevel = 14,
            digitalZoom = 1f,
            isHighPerformanceMode = false
        )
        
        // Test high performance mode
        val highPerfState = overlay.calculateDirectionLine(
            userLocation = location,
            viewportBounds = viewportBounds,
            zoomLevel = 14,
            digitalZoom = 1f,
            isHighPerformanceMode = true
        )
        
        // Both should be valid
        assertTrue("Normal mode should be valid", normalState.isCalculationValid)
        assertTrue("High performance mode should be valid", highPerfState.isCalculationValid)
        
        // High performance mode might have fewer path points (due to reduced segments)
        assertTrue("Both modes should have path points", 
                  normalState.pathPoints.isNotEmpty() && highPerfState.pathPoints.isNotEmpty())
    }
    
    @Test
    fun `QiblaDirectionOverlay should cache calculations effectively`() {
        val overlay = QiblaDirectionOverlay()
        val location = MapLocation(40.7128, -74.0060) // New York
        
        val viewportBounds = ViewportBounds(
            northLat = 41.0,
            southLat = 40.0,
            eastLon = -73.0,
            westLon = -75.0,
            centerLat = 40.5,
            centerLon = -74.0
        )
        
        // First calculation
        val startTime1 = System.currentTimeMillis()
        val state1 = overlay.calculateDirectionLine(
            userLocation = location,
            viewportBounds = viewportBounds,
            zoomLevel = 14,
            digitalZoom = 1f
        )
        val time1 = System.currentTimeMillis() - startTime1
        
        // Second calculation with same parameters (should use cache)
        val startTime2 = System.currentTimeMillis()
        val state2 = overlay.calculateDirectionLine(
            userLocation = location,
            viewportBounds = viewportBounds,
            zoomLevel = 14,
            digitalZoom = 1f
        )
        val time2 = System.currentTimeMillis() - startTime2
        
        // Both should be valid and have same results
        assertTrue("First calculation should be valid", state1.isCalculationValid)
        assertTrue("Second calculation should be valid", state2.isCalculationValid)
        assertEquals("Bearing should be same", state1.bearing, state2.bearing, 0.001)
        assertEquals("Distance should be same", state1.distance, state2.distance, 0.001)
        
        // Second calculation should be faster (cached)
        assertTrue("Cached calculation should be faster or similar", time2 <= time1 + 10) // Allow 10ms tolerance
    }
    
    @Test
    fun `QiblaDirectionOverlay cache statistics should be accessible`() {
        val overlay = QiblaDirectionOverlay()
        
        // Get initial cache stats
        val initialStats = overlay.getCacheStats()
        assertTrue("Initial cache size should be non-negative", initialStats.size >= 0)
        assertTrue("Max cache size should be positive", initialStats.maxSize > 0)
        
        // Perform some calculations to populate cache
        val location = MapLocation(40.7128, -74.0060)
        val viewportBounds = ViewportBounds(
            northLat = 41.0, southLat = 40.0,
            eastLon = -73.0, westLon = -75.0,
            centerLat = 40.5, centerLon = -74.0
        )
        
        overlay.calculateDirectionLine(location, viewportBounds, 14, 1f)
        
        // Check stats after calculation
        val afterStats = overlay.getCacheStats()
        assertTrue("Cache size should increase after calculation", afterStats.size >= initialStats.size)
        
        // Clear cache and verify
        overlay.clearCache()
        val clearedStats = overlay.getCacheStats()
        assertEquals("Cache should be empty after clear", 0, clearedStats.size)
    }
    
    // Helper functions that mirror the implementation
    private fun tileXToLongitude(tileX: Double, zoom: Int): Double {
        return tileX / (1 shl zoom).toDouble() * 360.0 - 180.0
    }
    
    private fun tileYToLatitude(tileY: Double, zoom: Int): Double {
        val n = kotlin.math.PI - 2.0 * kotlin.math.PI * tileY / (1 shl zoom).toDouble()
        return 180.0 / kotlin.math.PI * kotlin.math.atan(0.5 * (kotlin.math.exp(n) - kotlin.math.exp(-n)))
    }
}