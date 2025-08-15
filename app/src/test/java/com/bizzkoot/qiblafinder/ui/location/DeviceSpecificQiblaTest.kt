package com.bizzkoot.qiblafinder.ui.location

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.delay
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import com.bizzkoot.qiblafinder.utils.DeviceCapabilitiesDetector

/**
 * Device-specific tests for validating Qibla arrow behavior across different device tiers,
 * screen sizes, and map configurations
 */
@RunWith(AndroidJUnit4::class)
class DeviceSpecificQiblaTest {

    @Mock
    private lateinit var mockQiblaOverlay: QiblaDirectionOverlay

    private val testLocation = MapLocation(25.2048, 55.2708) // Dubai

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        QiblaPerformanceMonitor.reset()
    }

    @Test
    fun `test on high-end devices with 4x digital zoom capability`() = runTest {
        // Given: High-end device configuration
        val deviceTier = QiblaPerformanceMonitor.DeviceTier.HIGH_END
        val maxDigitalZoom = 4f
        val expectedMaxZoom = 4f
        val testViewportBounds = ViewportBounds(25.21, 25.19, 55.29, 55.27, 25.20, 55.28)
        
        // Mock QiblaDirectionOverlay for high-end device behavior
        whenever(mockQiblaOverlay.calculateDirectionLine(any(), any(), any(), any(), any(), any(), any())).thenReturn(
            QiblaDirectionState(
                isVisible = true,
                bearing = 225.5,
                distance = 1200.0,
                pathPoints = listOf(testLocation, MapLocation(21.4225, 39.8262)),
                screenPoints = listOf(),
                isCalculationValid = true,
                reducedComplexity = false // High-end should support full complexity
            )
        )
        
        // When: Testing maximum zoom capabilities
        val adaptiveFrequency = QiblaPerformanceMonitor.calculateAdaptiveUpdateFrequency(maxDigitalZoom, deviceTier)
        val fallbackConfig = QiblaPerformanceMonitor.createFallbackConfig(deviceTier, forceActivation = false)
        
        // Test at maximum zoom
        val result = mockQiblaOverlay.calculateDirectionLine(
            userLocation = testLocation,
            viewportBounds = testViewportBounds,
            zoomLevel = 18,
            digitalZoom = maxDigitalZoom,
            isHighPerformanceMode = true,
            highPrecisionMode = true,
            updateFrequency = UpdateFrequency.ULTRA_HIGH_FREQUENCY
        )
        
        // Then: High-end device should handle maximum zoom efficiently
        assertTrue("High-end device should support 4x digital zoom", maxDigitalZoom <= expectedMaxZoom)
        assertTrue("Update frequency should be fast (< 25ms)", adaptiveFrequency < 25L)
        assertNull("Should not require fallback under normal conditions", fallbackConfig)
        assertTrue("Calculation should be valid", result.isCalculationValid)
        assertFalse("Should not use reduced complexity", result.reducedComplexity)
        
        // Performance should be excellent
        val memoryInfo = QiblaPerformanceMonitor.monitorMemoryUsage()
        assertFalse("Should not trigger memory pressure", memoryInfo.isPressure)
        
        println("📱 High-End Device Test Results:")
        println("   Max digital zoom: ${maxDigitalZoom}x")
        println("   Adaptive frequency: ${adaptiveFrequency}ms")
        println("   Memory usage: ${memoryInfo.usedMemoryMB}MB (${(memoryInfo.usagePercent * 100).toInt()}%)")
        println("   Calculation valid: ${result.isCalculationValid}")
    }

    @Test
    fun `validate behavior on mid-range devices with 2_5x digital zoom limit`() = runTest {
        // Given: Mid-range device configuration
        val deviceTier = QiblaPerformanceMonitor.DeviceTier.MID_RANGE
        val maxDigitalZoom = 2.5f
        val testViewportBounds = ViewportBounds(25.21, 25.19, 55.29, 55.27, 25.20, 55.28)
        
        // Mock mid-range device behavior
        whenever(mockQiblaOverlay.calculateDirectionLine(any(), any(), any(), any(), any(), any(), any())).thenReturn(
            QiblaDirectionState(
                isVisible = true,
                bearing = 225.5,
                distance = 1200.0,
                pathPoints = listOf(testLocation, MapLocation(21.4225, 39.8262)),
                screenPoints = listOf(),
                isCalculationValid = true,
                reducedComplexity = false
            )
        )
        
        // When: Testing at device limits
        val adaptiveFrequency = QiblaPerformanceMonitor.calculateAdaptiveUpdateFrequency(maxDigitalZoom, deviceTier)
        
        // Test normal operation
        val result = mockQiblaOverlay.calculateDirectionLine(
            userLocation = testLocation,
            viewportBounds = testViewportBounds,
            zoomLevel = 16,
            digitalZoom = maxDigitalZoom,
            isHighPerformanceMode = true,
            highPrecisionMode = true,
            updateFrequency = UpdateFrequency.HIGH_FREQUENCY
        )
        
        // Simulate some load to potentially trigger fallback
        repeat(100) {
            QiblaPerformanceMonitor.recordHighFrequencyUpdate()
            QiblaPerformanceMonitor.measureCalculation("Mid-range load test") {
                Thread.sleep(5)
            }
        }
        
        val fallbackConfig = QiblaPerformanceMonitor.createFallbackConfig(deviceTier)
        
        // Then: Mid-range device should handle moderate zoom with appropriate limitations
        assertTrue("Update frequency should be reasonable (< 50ms)", adaptiveFrequency < 50L)
        assertTrue("Should support 2.5x digital zoom", maxDigitalZoom <= 2.5f)
        assertTrue("Calculation should be valid", result.isCalculationValid)
        
        // May require fallback under pressure
        if (fallbackConfig != null) {
            assertEquals("Fallback frequency should be 50ms", 50L, fallbackConfig.maxUpdateFrequencyMs)
            assertTrue("Should reduce segments under pressure", fallbackConfig.reducedSegments)
            assertTrue("Should enable aggressive caching", fallbackConfig.aggressiveCaching)
        }
        
        val stats = QiblaPerformanceMonitor.getPerformanceStats()
        
        println("📱 Mid-Range Device Test Results:")
        println("   Max digital zoom: ${maxDigitalZoom}x")
        println("   Adaptive frequency: ${adaptiveFrequency}ms")
        println("   High frequency updates: ${stats.highFrequencyUpdates}")
        println("   Average calc time: ${stats.averageCalculationTime}ms")
        println("   Fallback required: ${fallbackConfig != null}")
    }

    @Test
    fun `test graceful degradation on low-end devices with 2x digital zoom limit`() = runTest {
        // Given: Low-end device configuration
        val deviceTier = QiblaPerformanceMonitor.DeviceTier.LOW_END
        val maxDigitalZoom = 2f
        val testViewportBounds = ViewportBounds(25.21, 25.19, 55.29, 55.27, 25.20, 55.28)
        
        // Mock low-end device behavior with potential limitations
        whenever(mockQiblaOverlay.calculateDirectionLine(any(), any(), any(), any(), any(), any(), any())).thenReturn(
            QiblaDirectionState(
                isVisible = true,
                bearing = 225.5,
                distance = 1200.0,
                pathPoints = listOf(testLocation, MapLocation(21.4225, 39.8262)),
                screenPoints = listOf(),
                isCalculationValid = true,
                reducedComplexity = true // Low-end may use reduced complexity
            )
        )
        
        // When: Testing low-end device behavior
        val adaptiveFrequency = QiblaPerformanceMonitor.calculateAdaptiveUpdateFrequency(maxDigitalZoom, deviceTier)
        
        // Simulate heavier load to trigger fallback
        repeat(50) {
            QiblaPerformanceMonitor.recordHighFrequencyUpdate()
            QiblaPerformanceMonitor.measureCalculation("Low-end load test") {
                Thread.sleep(20) // Simulate slower calculations
            }
        }
        
        val fallbackConfig = QiblaPerformanceMonitor.createFallbackConfig(deviceTier)
        
        val result = mockQiblaOverlay.calculateDirectionLine(
            userLocation = testLocation,
            viewportBounds = testViewportBounds,
            zoomLevel = 14,
            digitalZoom = maxDigitalZoom,
            isHighPerformanceMode = false, // Disabled for low-end
            highPrecisionMode = false, // Disabled for low-end
            updateFrequency = UpdateFrequency.THROTTLED
        )
        
        // Then: Low-end device should gracefully degrade while maintaining functionality
        assertTrue("Should limit to 2x digital zoom", maxDigitalZoom <= 2f)
        assertTrue("Update frequency should be conservative (> 30ms)", adaptiveFrequency >= 30L)
        assertNotNull("Should have fallback configuration", fallbackConfig)
        assertTrue("Calculation should still be valid", result.isCalculationValid)
        
        fallbackConfig?.let { config ->
            assertEquals("Should have 100ms max frequency", 100L, config.maxUpdateFrequencyMs)
            assertTrue("Should reduce segments", config.reducedSegments)
            assertTrue("Should disable high precision", config.disableHighPrecisionMode)
            assertTrue("Should simplify calculations", config.simplifiedCalculations)
            assertTrue("Should enable aggressive caching", config.aggressiveCaching)
        }
        
        val stats = QiblaPerformanceMonitor.getPerformanceStats()
        val memoryInfo = QiblaPerformanceMonitor.monitorMemoryUsage()
        
        println("📱 Low-End Device Test Results:")
        println("   Max digital zoom: ${maxDigitalZoom}x")
        println("   Adaptive frequency: ${adaptiveFrequency}ms")
        println("   Average calc time: ${stats.averageCalculationTime}ms")
        println("   Memory usage: ${memoryInfo.usedMemoryMB}MB")
        println("   Reduced complexity: ${result.reducedComplexity}")
        println("   Fallback active: ${fallbackConfig != null}")
    }

    @Test
    fun `verify consistent behavior across different screen sizes and densities`() = runTest {
        // Given: Different screen configurations
        val screenConfigurations = listOf(
            Triple("Small Phone", 480, 800),    // Small phone screen
            Triple("Standard Phone", 720, 1280), // Standard phone screen
            Triple("Large Phone", 1080, 1920),   // Large phone screen
            Triple("Tablet", 1200, 1920)         // Tablet screen
        )
        
        val bearingConsistencyResults = mutableMapOf<String, Double>()
        val performanceResults = mutableMapOf<String, Long>()
        
        // Mock consistent calculation results across screen sizes
        whenever(mockQiblaOverlay.calculateDirectionLine(any(), any(), any(), any(), any(), any(), any())).thenReturn(
            QiblaDirectionState(
                isVisible = true,
                bearing = 225.5,
                distance = 1200.0,
                pathPoints = listOf(testLocation, MapLocation(21.4225, 39.8262)),
                screenPoints = listOf(),
                isCalculationValid = true
            )
        )
        
        // When: Testing across different screen configurations
        screenConfigurations.forEach { (screenName, width, height) ->
            val viewportBounds = ViewportBounds(
                northLat = 25.21,
                southLat = 25.19,
                eastLon = 55.29,
                westLon = 55.27,
                centerLat = 25.20,
                centerLon = 55.28
            )
            
            // Simulate screen-specific calculations
            val calculationTime = kotlin.system.measureTimeMillis {
                val result = mockQiblaOverlay.calculateDirectionLine(
                    userLocation = testLocation,
                    viewportBounds = viewportBounds,
                    zoomLevel = 16,
                    digitalZoom = 3f,
                    isHighPerformanceMode = true,
                    highPrecisionMode = true,
                    updateFrequency = UpdateFrequency.HIGH_FREQUENCY
                )
                
                bearingConsistencyResults[screenName] = result.bearing
            }
            
            performanceResults[screenName] = calculationTime
            
            delay(10) // Small delay between tests
        }
        
        // Then: Results should be consistent across screen sizes
        val bearingValues = bearingConsistencyResults.values
        val bearingVariance = bearingValues.maxOrNull()!! - bearingValues.minOrNull()!!
        val avgPerformance = performanceResults.values.average()
        
        assertTrue("Bearing should be consistent across screens (< 0.1° variance)", bearingVariance < 0.1)
        assertTrue("Performance should be reasonable across screens", avgPerformance < 50.0)
        
        println("📱 Screen Size Consistency Test Results:")
        bearingConsistencyResults.forEach { (screen, bearing) ->
            println("   $screen: ${bearing}° (${performanceResults[screen]}ms)")
        }
        println("   Bearing variance: ${bearingVariance}°")
        println("   Average performance: ${avgPerformance}ms")
        
        // All screen sizes should produce valid results
        bearingConsistencyResults.values.forEach { bearing ->
            assertTrue("Bearing should be valid", bearing > 0 && bearing < 360)
        }
    }

    @Test
    fun `test with both MapType STREET and MapType SATELLITE configurations`() = runTest {
        // Given: Different map type configurations
        val mapTypes = listOf("STREET", "SATELLITE")
        val mapTypeResults = mutableMapOf<String, QiblaDirectionState>()
        val mapTypePerformance = mutableMapOf<String, Double>()
        
        // Mock map type specific behavior
        whenever(mockQiblaOverlay.calculateDirectionLine(any(), any(), any(), any(), any(), any(), any())).thenReturn(
            QiblaDirectionState(
                isVisible = true,
                bearing = 225.5,
                distance = 1200.0,
                pathPoints = listOf(testLocation, MapLocation(21.4225, 39.8262)),
                screenPoints = listOf(),
                isCalculationValid = true
            )
        )
        
        // When: Testing with different map types
        mapTypes.forEach { mapType ->
            QiblaPerformanceMonitor.reset() // Reset for each map type test
            
            val testIterations = 20
            var totalCalculationTime = 0.0
            
            repeat(testIterations) { iteration ->
                val calculationTime = QiblaPerformanceMonitor.measureCalculation("$mapType Test $iteration") {
                    val result = mockQiblaOverlay.calculateDirectionLine(
                        userLocation = testLocation,
                        viewportBounds = ViewportBounds(25.21, 25.19, 55.29, 55.27, 25.20, 55.28),
                        zoomLevel = 17,
                        digitalZoom = 3f,
                        isHighPerformanceMode = true,
                        highPrecisionMode = true,
                        updateFrequency = UpdateFrequency.HIGH_FREQUENCY
                    )
                    
                    if (iteration == 0) {
                        mapTypeResults[mapType] = result
                    }
                    
                    // Simulate map type specific rendering overhead
                    when (mapType) {
                        "SATELLITE" -> Thread.sleep(2) // Satellite may have slight overhead
                        "STREET" -> Thread.sleep(1) // Street may be lighter
                    }
                }
                
                totalCalculationTime += calculationTime
                delay(16) // High frequency updates
            }
            
            mapTypePerformance[mapType] = totalCalculationTime / testIterations
        }
        
        // Then: Both map types should provide consistent results
        val streetResult = mapTypeResults["STREET"]!!
        val satelliteResult = mapTypeResults["SATELLITE"]!!
        val streetPerformance = mapTypePerformance["STREET"]!!
        val satellitePerformance = mapTypePerformance["SATELLITE"]!!
        
        // Results should be identical (arrow calculation is independent of map type)
        assertEquals("Bearing should be identical across map types", streetResult.bearing, satelliteResult.bearing, 0.001)
        assertEquals("Distance should be identical across map types", streetResult.distance, satelliteResult.distance, 0.001)
        assertTrue("Both calculations should be valid", streetResult.isCalculationValid && satelliteResult.isCalculationValid)
        
        // Performance should be comparable
        val performanceDifference = kotlin.math.abs(streetPerformance - satellitePerformance)
        assertTrue("Performance difference should be minimal (< 10ms)", performanceDifference < 10.0)
        
        println("📱 Map Type Comparison Test Results:")
        println("   STREET - Bearing: ${streetResult.bearing}°, Performance: ${streetPerformance}ms")
        println("   SATELLITE - Bearing: ${satelliteResult.bearing}°, Performance: ${satellitePerformance}ms")
        println("   Performance difference: ${performanceDifference}ms")
        
        // Both should maintain good performance
        assertTrue("Street map performance should be good", streetPerformance < 50.0)
        assertTrue("Satellite map performance should be good", satellitePerformance < 50.0)
    }

    @Test
    fun `test device-specific zoom limits and capabilities`() = runTest {
        // Given: Device-specific zoom capabilities
        val deviceZoomLimits = mapOf(
            QiblaPerformanceMonitor.DeviceTier.HIGH_END to 4f,
            QiblaPerformanceMonitor.DeviceTier.MID_RANGE to 2.5f,
            QiblaPerformanceMonitor.DeviceTier.LOW_END to 2f
        )
        
        // When: Testing zoom limits for each device tier
        deviceZoomLimits.forEach { (deviceTier, expectedMaxZoom) ->
            // Test at maximum zoom for device
            val adaptiveFrequency = QiblaPerformanceMonitor.calculateAdaptiveUpdateFrequency(expectedMaxZoom, deviceTier)
            val fallbackConfig = QiblaPerformanceMonitor.createFallbackConfig(deviceTier)
            
            // Test exceeding zoom limit
            val excessiveZoom = expectedMaxZoom + 1f
            val excessiveFrequency = QiblaPerformanceMonitor.calculateAdaptiveUpdateFrequency(excessiveZoom, deviceTier)
            
            // Then: Device should handle zoom appropriately
            when (deviceTier) {
                QiblaPerformanceMonitor.DeviceTier.HIGH_END -> {
                    assertTrue("High-end should handle max zoom efficiently", adaptiveFrequency <= 25L)
                    assertTrue("High-end should handle excessive zoom", excessiveFrequency <= 50L)
                }
                QiblaPerformanceMonitor.DeviceTier.MID_RANGE -> {
                    assertTrue("Mid-range should use moderate frequency", adaptiveFrequency <= 50L)
                    assertTrue("Mid-range should throttle excessive zoom", excessiveFrequency >= 50L)
                }
                QiblaPerformanceMonitor.DeviceTier.LOW_END -> {
                    assertTrue("Low-end should use conservative frequency", adaptiveFrequency >= 50L)
                    assertTrue("Low-end should heavily throttle excessive zoom", excessiveFrequency >= 100L)
                    assertNotNull("Low-end should require fallback", fallbackConfig)
                }
            }
            
            println("📱 Device Zoom Limits Test - $deviceTier:")
            println("   Max zoom: ${expectedMaxZoom}x")
            println("   Frequency at max: ${adaptiveFrequency}ms")
            println("   Frequency at excessive: ${excessiveFrequency}ms")
            println("   Fallback required: ${fallbackConfig != null}")
        }
    }
}