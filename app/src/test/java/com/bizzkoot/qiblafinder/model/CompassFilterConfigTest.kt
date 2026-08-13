package com.bizzkoot.qiblafinder.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression tests for PRD H3: CompassFilterConfig must default to a 30 Hz target sampling
 * rate. The throttle was changed from 50 Hz to 30 Hz to halve the ~100+ StateFlow
 * emissions/sec; these tests pin the defaults (and the derived sampling period) so a silent
 * regression back to 50 Hz fails CI.
 */
class CompassFilterConfigTest {

    @Test
    fun `default target sampling rate is 30 Hz`() {
        assertEquals(30, CompassFilterConfig().targetSamplingRateHz)
    }

    @Test
    fun `default min sampling rate is 20 Hz`() {
        assertEquals(20, CompassFilterConfig().minSamplingRateHz)
    }

    @Test
    fun `default config sampling period is 33333 microseconds`() {
        assertEquals(33_333, CompassFilterConfig().samplingPeriodUs())
    }

    @Test
    fun `sampling period is clamped to a 5 ms floor`() {
        assertEquals(5_000, CompassFilterConfig(targetSamplingRateHz = 1_000).samplingPeriodUs())
    }
}
