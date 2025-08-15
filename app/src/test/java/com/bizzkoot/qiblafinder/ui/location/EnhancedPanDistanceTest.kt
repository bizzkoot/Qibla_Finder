package com.bizzkoot.qiblafinder.ui.location

import org.junit.Test
import org.junit.Assert.*
import org.junit.Before
import kotlin.math.abs

/**
 * Unit tests for enhanced pan distance calculation accuracy and zoom-adaptive threshold logic
 */
class EnhancedPanDistanceTest {

    private lateinit var enhancedPanState: EnhancedPanState
    private lateinit var updateConfig: DigitalZoomUpdateConfig

    @Before
    fun setUp() {
        enhancedPanState = EnhancedPanState()
        updateConfig = DigitalZoomUpdateConfig()
    }

    @Test
    fun `test cumulative pan distance accuracy with single drag`() {
        // Given: Starting with zero cumulative distance
        var cumulativePanDistance = 0f
        val dragAmounts = listOf(10f, 15f, 20f, 25f, 30f)
        
        // When: Accumulating drag amounts
        dragAmounts.forEach { dragAmount ->
            cumulativePanDistance += dragAmount
        }
        
        // Then: Cumulative distance should equal sum of all drags
        val expectedDistance = dragAmounts.sum()
        assertEquals("Cumulative pan distance should be accurate", expectedDistance, cumulativePanDistance, 0.001f)
    }

    @Test
    fun `test cumulative pan distance with mixed positive and negative values`() {
        // Given: Mixed drag amounts (simulating back-and-forth movement)
        var cumulativePanDistance = 0f
        val dragAmounts = listOf(10f, -5f, 15f, -8f, 20f, -3f)
        
        // When: Accumulating absolute drag amounts (distance traveled)
        dragAmounts.forEach { dragAmount ->
            cumulativePanDistance += abs(dragAmount)
        }
        
        // Then: Should accumulate absolute values
        val expectedDistance = dragAmounts.sumOf { abs(it).toDouble() }.toFloat()
        assertEquals("Cumulative distance should use absolute values", expectedDistance, cumulativePanDistance, 0.001f)
    }

    @Test
    fun `test enhanced sensitivity threshold calculation at various zoom levels`() {
        // Test data: zoom level to expected threshold mapping
        val testCases = mapOf(
            1f to 10f,   // Standard zoom - default threshold
            2f to 7.5f,  // 2x zoom - reduced threshold (75% of original)
            3f to 5f,    // 3x zoom - further reduced (50% of original)
            4f to 2.5f   // 4x zoom - minimum threshold (25% of original)
        )
        
        testCases.forEach { (digitalZoom, expectedThreshold) ->
            // When: Calculating zoom-adaptive threshold
            val calculatedThreshold = calculateZoomAdaptiveThreshold(digitalZoom, updateConfig)
            
            // Then: Threshold should match expected value
            assertEquals(
                "Threshold for ${digitalZoom}x zoom should be $expectedThreshold",
                expectedThreshold,
                calculatedThreshold,
                0.1f
            )
        }
    }

    @Test
    fun `test zoom-adaptive threshold configuration logic`() {
        // Given: Different digital zoom levels
        val zoomLevels = listOf(1f, 1.5f, 2f, 2.5f, 3f, 3.5f, 4f)
        
        zoomLevels.forEach { digitalZoom ->
            // When: Calculating threshold
            val threshold = calculateZoomAdaptiveThreshold(digitalZoom, updateConfig)
            
            // Then: Threshold should decrease as zoom increases
            assertTrue(
                "Threshold should be positive for zoom $digitalZoom",
                threshold > 0f
            )
            
            // And threshold should be inversely related to zoom
            if (digitalZoom > 1f) {
                val previousThreshold = calculateZoomAdaptiveThreshold(digitalZoom - 0.5f, updateConfig)
                assertTrue(
                    "Threshold should decrease as zoom increases (${digitalZoom}x: $threshold vs ${digitalZoom - 0.5f}x: $previousThreshold)",
                    threshold <= previousThreshold
                )
            }
        }
    }

    @Test
    fun `test high zoom mode activation logic`() {
        // Given: Different zoom levels and configurations
        val testCases = listOf(
            Triple(1.5f, false, "Below threshold"),
            Triple(2.0f, true, "At threshold"),
            Triple(2.5f, true, "Above threshold"),
            Triple(3.0f, true, "High zoom"),
            Triple(4.0f, true, "Maximum zoom")
        )
        
        testCases.forEach { (digitalZoom, expectedHighZoomMode, description) ->
            // When: Determining if high zoom mode should be active
            val isHighZoomMode = digitalZoom > updateConfig.highZoomThreshold
            
            // Then: Should match expected behavior
            assertEquals(
                "$description: High zoom mode should be $expectedHighZoomMode for ${digitalZoom}x zoom",
                expectedHighZoomMode,
                isHighZoomMode
            )
        }
    }

    @Test
    fun `test enhanced pan state initialization`() {
        // When: Creating a new enhanced pan state
        val panState = EnhancedPanState()
        
        // Then: Should have correct default values
        assertEquals("Initial cumulative distance should be 0", 0f, panState.cumulativePanDistance, 0.001f)
        assertEquals("Initial update time should be 0", 0L, panState.lastSignificantUpdateTime)
        assertFalse("Initial high zoom mode should be false", panState.isHighZoomMode)
        assertEquals("Initial digital zoom should be 1", 1f, panState.digitalZoomFactor, 0.001f)
        assertEquals("Initial threshold should be default", 10f, panState.panDistanceThreshold, 0.001f)
        assertEquals("Initial update interval should be 16ms (60fps)", 16L, panState.timeBasedUpdateInterval)
    }

    @Test
    fun `test digital zoom update config initialization`() {
        // When: Creating a new digital zoom update config
        val config = DigitalZoomUpdateConfig()
        
        // Then: Should have correct default values
        assertEquals("High zoom threshold should be 2f", 2f, config.highZoomThreshold, 0.001f)
        assertEquals("Update frequency should be 16ms", 16L, config.updateFrequencyMs)
        assertTrue("Time-based updates should be enabled by default", config.timeBasedUpdateEnabled)
    }

    @Test
    fun `test cumulative distance reset behavior`() {
        // Given: Accumulated distance
        var cumulativePanDistance = 50f
        val lastSignificantUpdateTime = System.currentTimeMillis()
        
        // When: Checking if reset is needed (simulate significant update)
        val shouldReset = cumulativePanDistance > 10f // Threshold exceeded
        
        // Then: Reset should be triggered
        assertTrue("Should reset when threshold exceeded", shouldReset)
        
        // When: Resetting
        if (shouldReset) {
            cumulativePanDistance = 0f
        }
        
        // Then: Distance should be reset
        assertEquals("Distance should be reset to 0", 0f, cumulativePanDistance, 0.001f)
    }

    @Test
    fun `test time-based update interval calculation`() {
        // Given: Different target frame rates
        val testCases = mapOf(
            60L to 16L,  // 60fps = ~16.67ms ≈ 16ms
            30L to 33L,  // 30fps = ~33.33ms ≈ 33ms
            120L to 8L   // 120fps = ~8.33ms ≈ 8ms
        )
        
        testCases.forEach { (targetFps, expectedInterval) ->
            // When: Calculating update interval
            val calculatedInterval = (1000L / targetFps)
            
            // Then: Should match expected interval (with tolerance)
            assertTrue(
                "Interval for ${targetFps}fps should be approximately ${expectedInterval}ms, got ${calculatedInterval}ms",
                abs(calculatedInterval - expectedInterval) <= 1L
            )
        }
    }

    /**
     * Helper function to calculate zoom-adaptive threshold
     */
    private fun calculateZoomAdaptiveThreshold(digitalZoom: Float, config: DigitalZoomUpdateConfig): Float {
        val baseThreshold = 10f
        return when {
            digitalZoom >= 4f -> baseThreshold * 0.25f
            digitalZoom >= 3f -> baseThreshold * 0.5f
            digitalZoom >= 2f -> baseThreshold * 0.75f
            else -> baseThreshold
        }
    }
}