package com.bizzkoot.qiblafinder.ui.location

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class QiblaPerformanceOptimizerTest {
    
    private lateinit var optimizer: QiblaPerformanceOptimizer
    
    @Before
    fun setUp() {
        optimizer = QiblaPerformanceOptimizer()
    }
    
    @Test
    fun `calculateOptimalSegments returns appropriate values for different zoom levels`() {
        // High zoom level should return more segments
        val highZoomSegments = optimizer.calculateOptimalSegments(
            zoomLevel = 16,
            digitalZoom = 1f,
            distance = 5000.0,
            isHighPerformanceMode = false
        )
        
        // Low zoom level should return fewer segments
        val lowZoomSegments = optimizer.calculateOptimalSegments(
            zoomLevel = 6,
            digitalZoom = 1f,
            distance = 5000.0,
            isHighPerformanceMode = false
        )
        
        assertTrue("High zoom should have more segments than low zoom", 
                  highZoomSegments > lowZoomSegments)
        
        // Segments should be within reasonable bounds
        assertTrue("Segments should be at least 8", highZoomSegments >= 8)
        assertTrue("Segments should be at most 80", highZoomSegments <= 80)
    }
    
    @Test
    fun `calculateOptimalSegments adjusts for digital zoom`() {
        val baseSegments = optimizer.calculateOptimalSegments(
            zoomLevel = 14,
            digitalZoom = 1f,
            distance = 5000.0,
            isHighPerformanceMode = false
        )
        
        val digitalZoomSegments = optimizer.calculateOptimalSegments(
            zoomLevel = 14,
            digitalZoom = 2f,
            distance = 5000.0,
            isHighPerformanceMode = false
        )
        
        assertTrue("Digital zoom should increase segments", 
                  digitalZoomSegments > baseSegments)
    }
    
    @Test
    fun `calculateOptimalSegments reduces segments in high performance mode`() {
        val normalModeSegments = optimizer.calculateOptimalSegments(
            zoomLevel = 14,
            digitalZoom = 1f,
            distance = 5000.0,
            isHighPerformanceMode = false
        )
        
        val highPerfModeSegments = optimizer.calculateOptimalSegments(
            zoomLevel = 14,
            digitalZoom = 1f,
            distance = 5000.0,
            isHighPerformanceMode = true
        )
        
        assertTrue("High performance mode should reduce segments", 
                  highPerfModeSegments < normalModeSegments)
    }
    
    @Test
    fun `calculateOptimalSegments adjusts for distance`() {
        val shortDistanceSegments = optimizer.calculateOptimalSegments(
            zoomLevel = 14,
            digitalZoom = 1f,
            distance = 1000.0,
            isHighPerformanceMode = false
        )
        
        val longDistanceSegments = optimizer.calculateOptimalSegments(
            zoomLevel = 14,
            digitalZoom = 1f,
            distance = 15000.0,
            isHighPerformanceMode = false
        )
        
        assertTrue("Long distances should have more segments", 
                  longDistanceSegments > shortDistanceSegments)
    }
    
    @Test
    fun `getCachedOrCalculatePath caches results correctly`() {
        val location = MapLocation(40.7128, -74.0060) // New York
        val kaabaLat = 21.4225
        val kaabaLon = 39.8262
        val segments = 30
        
        var calculationCount = 0
        val calculator: (MapLocation, Double, Double, Int) -> List<MapLocation> = { _, _, _, _ ->
            calculationCount++
            listOf(location, MapLocation(kaabaLat, kaabaLon))
        }
        
        // First call should trigger calculation
        val result1 = optimizer.getCachedOrCalculatePath(location, kaabaLat, kaabaLon, segments, calculator)
        assertEquals("First call should trigger calculation", 1, calculationCount)
        assertNotNull("Result should not be null", result1)
        
        // Second call with same parameters should use cache
        val result2 = optimizer.getCachedOrCalculatePath(location, kaabaLat, kaabaLon, segments, calculator)
        assertEquals("Second call should use cache", 1, calculationCount)
        assertNotNull("Cached result should not be null", result2)
        
        // Results should be equal
        assertEquals("Cached result should match original", result1?.pathPoints, result2?.pathPoints)
    }
    
    @Test
    fun `getCachedOrCalculatePath invalidates cache for significantly different locations`() {
        val location1 = MapLocation(40.7128, -74.0060) // New York
        val location2 = MapLocation(41.7128, -73.0060) // Significantly different location
        val kaabaLat = 21.4225
        val kaabaLon = 39.8262
        val segments = 30
        
        var calculationCount = 0
        val calculator: (MapLocation, Double, Double, Int) -> List<MapLocation> = { loc, _, _, _ ->
            calculationCount++
            listOf(loc, MapLocation(kaabaLat, kaabaLon))
        }
        
        // First call
        optimizer.getCachedOrCalculatePath(location1, kaabaLat, kaabaLon, segments, calculator)
        assertEquals("First call should trigger calculation", 1, calculationCount)
        
        // Second call with significantly different location should trigger new calculation
        optimizer.getCachedOrCalculatePath(location2, kaabaLat, kaabaLon, segments, calculator)
        assertEquals("Different location should trigger new calculation", 2, calculationCount)
    }
    
    @Test
    fun `throttleCalculation executes calculation`() {
        var executionCount = 0
        val calculation: suspend () -> Unit = {
            executionCount++
        }
        
        val testScope = CoroutineScope(Dispatchers.Unconfined)
        
        // Make a single call
        optimizer.throttleCalculation(testScope) { calculation() }
        
        // Wait a bit for execution
        Thread.sleep(50)
        
        // Should execute at least once
        assertTrue("Throttling should allow execution", executionCount >= 1)
    }
    
    @Test
    fun `cullPathToViewport filters points correctly`() {
        val viewportBounds = ViewportBounds(
            northLat = 45.0,
            southLat = 40.0,
            eastLon = -70.0,
            westLon = -75.0,
            centerLat = 42.5,
            centerLon = -72.5
        )
        
        val pathPoints = listOf(
            MapLocation(42.0, -72.0), // Inside
            MapLocation(43.0, -72.0), // Inside
            MapLocation(44.0, -72.0)  // Inside
        )
        
        val culledPoints = optimizer.cullPathToViewport(pathPoints, viewportBounds, includeBuffer = false)
        
        // Should include all inside points
        assertTrue("Should have some visible points", culledPoints.isNotEmpty())
        assertEquals("Should include all inside points", 3, culledPoints.size)
        
        // All returned points should be the same as input (all were inside)
        assertEquals("Should return all inside points", pathPoints, culledPoints)
    }
    
    @Test
    fun `clearCache resets cache state`() {
        val location = MapLocation(40.7128, -74.0060)
        val kaabaLat = 21.4225
        val kaabaLon = 39.8262
        val segments = 30
        
        var calculationCount = 0
        val calculator: (MapLocation, Double, Double, Int) -> List<MapLocation> = { _, _, _, _ ->
            calculationCount++
            listOf(location, MapLocation(kaabaLat, kaabaLon))
        }
        
        // Add something to cache
        optimizer.getCachedOrCalculatePath(location, kaabaLat, kaabaLon, segments, calculator)
        assertEquals("Should have calculated once", 1, calculationCount)
        
        // Clear cache
        optimizer.clearCache()
        
        // Next call should trigger calculation again
        optimizer.getCachedOrCalculatePath(location, kaabaLat, kaabaLon, segments, calculator)
        assertEquals("Should calculate again after cache clear", 2, calculationCount)
    }
    
    @Test
    fun `getCacheStats returns valid statistics`() {
        val stats = optimizer.getCacheStats()
        
        assertTrue("Cache size should be non-negative", stats.size >= 0)
        assertTrue("Max size should be positive", stats.maxSize > 0)
        assertTrue("Hit rate should be between 0 and 1", stats.hitRate >= 0.0 && stats.hitRate <= 1.0)
    }
}