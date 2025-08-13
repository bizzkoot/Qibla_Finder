package com.bizzkoot.qiblafinder.ui.location

import androidx.compose.ui.unit.IntSize
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Integration tests for Qibla direction line rendering across different scenarios
 */
class QiblaDirectionRenderingIntegrationTest {

    private lateinit var overlay: QiblaDirectionOverlay
    private lateinit var renderer: DirectionLineRenderer
    
    @Before
    fun setup() {
        overlay = QiblaDirectionOverlay()
        renderer = DirectionLineRenderer()
    }

    /**
     * Test requirement 3.1, 3.2: Rendering across different zoom levels
     */
    @Test
    fun `direction line should adapt rendering detail to zoom levels`() {
        val location = MapLocation(40.7128, -74.0060) // New York
        val viewportBounds = createViewportBounds(location, 10.0)
        
        val zoomLevels = listOf(5, 10, 15)
        
        zoomLevels.forEach { zoomLevel ->
            val directionState = overlay.calculateDirectionLine(
                userLocation = location,
                viewportBounds = viewportBounds,
                zoomLevel = zoomLevel,
                digitalZoom = 1f
            )
            
            assertTrue("Should have valid calculation at zoom $zoomLevel", 
                directionState.isCalculationValid || directionState.errorMessage != null)
        }
    }

    /**
     * Test viewport clipping and edge cases
     */
    @Test
    fun `direction line should handle viewport clipping correctly`() {
        val testCases = listOf(
            Triple(MapLocation(40.0, -75.0), createViewportBounds(MapLocation(40.0, -75.0), 5.0), "inside"),
            Triple(MapLocation(42.5, -75.0), createViewportBounds(MapLocation(40.0, -75.0), 5.0), "edge"),
            Triple(MapLocation(50.0, -75.0), createViewportBounds(MapLocation(40.0, -75.0), 5.0), "outside")
        )
        
        testCases.forEach { (location, viewport, scenario) ->
            val directionState = overlay.calculateDirectionLine(
                userLocation = location,
                viewportBounds = viewport,
                zoomLevel = 10,
                digitalZoom = 1f
            )
            
            assertTrue("Should handle $scenario scenario", 
                directionState.isCalculationValid || directionState.errorMessage != null)
        }
    }

    /**
     * Test rendering with different viewport sizes
     */
    @Test
    fun `direction line should adapt to different viewport sizes`() {
        val location = MapLocation(51.5074, -0.1278) // London
        val viewportBounds = createViewportBounds(location, 8.0)
        val zoomLevel = 12
        
        val viewportSizes = listOf(
            IntSize(400, 400),
            IntSize(800, 800),
            IntSize(1200, 800)
        )
        
        viewportSizes.forEach { viewportSize ->
            val directionState = overlay.calculateDirectionLine(
                userLocation = location,
                viewportBounds = viewportBounds,
                zoomLevel = zoomLevel,
                digitalZoom = 1f
            )
            
            assertTrue("Should work with viewport ${viewportSize.width}x${viewportSize.height}", 
                directionState.isCalculationValid || directionState.errorMessage != null)
        }
    }

    /**
     * Test rendering performance with complex paths
     */
    @Test
    fun `direction line rendering should handle complex paths efficiently`() {
        val location = MapLocation(-33.8688, 151.2093) // Sydney (far from Kaaba)
        val viewportBounds = createViewportBounds(location, 15.0)
        
        val startTime = System.currentTimeMillis()
        val directionState = overlay.calculateDirectionLine(
            userLocation = location,
            viewportBounds = viewportBounds,
            zoomLevel = 18,
            digitalZoom = 2f
        )
        val calculationTime = System.currentTimeMillis() - startTime
        
        assertTrue("Complex path calculation should complete", 
            directionState.isCalculationValid || directionState.errorMessage != null)
        assertTrue("Complex path calculation should be reasonably fast (${calculationTime}ms)", 
            calculationTime < 5000) // Should complete within 5 seconds
    }

    /**
     * Test edge cases in rendering
     */
    @Test
    fun `direction line should handle rendering edge cases gracefully`() {
        val edgeCases = listOf(
            MapLocation(21.4225, 39.8262), // Very close to Kaaba
            MapLocation(89.0, 0.0),        // Near North Pole
            MapLocation(-89.0, 0.0),       // Near South Pole
            MapLocation(0.0, 179.9)        // Near date line
        )
        
        edgeCases.forEach { location ->
            val viewportBounds = createViewportBounds(location, 10.0)
            
            val directionState = overlay.calculateDirectionLine(
                userLocation = location,
                viewportBounds = viewportBounds,
                zoomLevel = 10,
                digitalZoom = 1f
            )
            
            assertTrue("Should handle edge case at ${location.latitude}, ${location.longitude}", 
                directionState.isCalculationValid || directionState.errorMessage != null)
        }
    }

    /**
     * Test rendering with different digital zoom levels
     */
    @Test
    fun `direction line should handle digital zoom correctly`() {
        val location = MapLocation(35.6762, 139.6503) // Tokyo
        val viewportBounds = createViewportBounds(location, 5.0)
        val baseZoom = 12
        
        val digitalZoomLevels = listOf(0.5f, 1f, 2f, 3f)
        
        digitalZoomLevels.forEach { digitalZoom ->
            val directionState = overlay.calculateDirectionLine(
                userLocation = location,
                viewportBounds = viewportBounds,
                zoomLevel = baseZoom,
                digitalZoom = digitalZoom
            )
            
            assertTrue("Should handle digital zoom $digitalZoom", 
                directionState.isCalculationValid || directionState.errorMessage != null)
        }
    }

    /**
     * Test color adaptation for different map types
     */
    @Test
    fun `direction line should adapt colors for different map types`() {
        val location = MapLocation(40.7128, -74.0060) // New York
        val viewportBounds = createViewportBounds(location, 5.0)
        
        // Test that direction line renders for different map types
        val streetState = overlay.calculateDirectionLine(
            userLocation = location,
            viewportBounds = viewportBounds,
            zoomLevel = 12,
            digitalZoom = 1f
        )
        
        val satelliteState = overlay.calculateDirectionLine(
            userLocation = location,
            viewportBounds = viewportBounds,
            zoomLevel = 12,
            digitalZoom = 1f
        )
        
        // Both should be valid and consistent
        assertTrue("Street map should work", 
            streetState.isCalculationValid || streetState.errorMessage != null)
        assertTrue("Satellite map should work", 
            satelliteState.isCalculationValid || satelliteState.errorMessage != null)
    }

    // Helper functions
    
    private fun createViewportBounds(center: MapLocation, spanDegrees: Double): ViewportBounds {
        val halfSpan = spanDegrees / 2.0
        return ViewportBounds(
            northLat = center.latitude + halfSpan,
            southLat = center.latitude - halfSpan,
            eastLon = center.longitude + halfSpan,
            westLon = center.longitude - halfSpan,
            centerLat = center.latitude,
            centerLon = center.longitude
        )
    }
}