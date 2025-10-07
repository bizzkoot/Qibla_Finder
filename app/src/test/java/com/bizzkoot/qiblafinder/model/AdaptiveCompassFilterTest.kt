package com.bizzkoot.qiblafinder.model

import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AdaptiveCompassFilterTest {

    private lateinit var filter: AdaptiveCompassFilter

    @Before
    fun setUp() {
        filter = AdaptiveCompassFilter(
            config = CompassFilterConfig(),
            analytics = CompassAnalytics.NO_OP
        )
    }

    @Test
    fun `alpha stays within expected bounds`() {
        val alphaValues = mutableListOf<Float>()
        filter.computeAlpha(10_000_000L, 0f)
        repeat(10) { index ->
            val timestamp = 20_000_000L + index * 10_000_000L
            alphaValues += filter.computeAlpha(timestamp, index.toFloat())
        }
        alphaValues.forEach { alpha ->
            assertTrue("Alpha out of bounds: $alpha", alpha in 0.05f..0.6f)
        }
    }

    @Test
    fun `alpha increases when motion is large`() {
        filter.computeAlpha(10_000_000L, 0f)
        var lowMotionAlpha = 0f
        repeat(5) { index ->
            val timestamp = 20_000_000L + index * 10_000_000L
            lowMotionAlpha = filter.computeAlpha(timestamp, index * 0.5f)
        }

        filter.reset()
        filter.computeAlpha(10_000_000L, 0f)
        var highMotionAlpha = 0f
        repeat(5) { index ->
            val timestamp = 20_000_000L + index * 10_000_000L
            highMotionAlpha = filter.computeAlpha(timestamp, index * 30f)
        }

        assertTrue(highMotionAlpha > lowMotionAlpha)
    }

    @Test
    fun `alpha decreases when device is stationary`() {
        filter.computeAlpha(10_000_000L, 45f)
        val firstAlpha = filter.computeAlpha(20_000_000L, 46f)

        var laterAlpha = firstAlpha
        repeat(10) { iteration ->
            val timestamp = 30_000_000L + iteration * 10_000_000L
            laterAlpha = filter.computeAlpha(timestamp, 46f)
        }

        assertTrue(laterAlpha <= firstAlpha)
    }
}
