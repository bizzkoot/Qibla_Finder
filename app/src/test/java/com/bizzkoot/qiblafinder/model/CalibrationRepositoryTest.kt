package com.bizzkoot.qiblafinder.model

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
// Use a plain Application: the real QiblaFinderApplication.onCreate schedules WorkManager,
// which is not initialized in unit-test environments.
@Config(application = Application::class)
class CalibrationRepositoryTest {

    private fun repo(): CalibrationRepository {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Start from a clean slate for each test
        context.getSharedPreferences("sun_calibration", Context.MODE_PRIVATE)
            .edit().clear().commit()
        return CalibrationRepository(context)
    }

    @Test
    fun `default calibration result is null`() {
        val r = repo()
        assertNull(r.calibrationResult.value)
        assertEquals(0.0, r.getCurrentOffset(), 0.0)
    }

    @Test
    fun `store then read publishes and persists the result`() {
        val r = repo()
        val result = CalibrationResult(errorOffset = 12.5, timestamp = 123456789L)
        r.store(result)
        assertEquals(result, r.calibrationResult.value)
        assertEquals(12.5, r.getCurrentOffset(), 0.001)
    }

    @Test
    fun `store a second calibration replaces the first`() {
        val r = repo()
        r.store(CalibrationResult(errorOffset = 12.5, timestamp = 111L))
        r.store(CalibrationResult(errorOffset = -3.25, timestamp = 222L))
        assertEquals(-3.25, r.getCurrentOffset(), 0.001)
        assertEquals(CalibrationResult(errorOffset = -3.25, timestamp = 222L), r.calibrationResult.value)
    }

    @Test
    fun `clear removes the calibration`() {
        val r = repo()
        r.store(CalibrationResult(errorOffset = -7.25, timestamp = 987654321L))
        r.clear()
        assertNull(r.calibrationResult.value)
        assertEquals(0.0, r.getCurrentOffset(), 0.0)
    }

    @Test
    fun `offset round trips through shared preferences`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("sun_calibration", Context.MODE_PRIVATE)
            .edit().clear().commit()

        val repo1 = CalibrationRepository(context)
        repo1.store(CalibrationResult(errorOffset = 42.5, timestamp = 333L))

        // A new instance reads the persisted value back from the same prefs file
        // (same keys the legacy feature used, so existing calibrations survive).
        val repo2 = CalibrationRepository(context)
        assertEquals(42.5, repo2.getCurrentOffset(), 0.001)
        assertEquals(CalibrationResult(errorOffset = 42.5, timestamp = 333L), repo2.calibrationResult.value)
    }

    @Test
    fun `stored offset without a timestamp is treated as no calibration`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("sun_calibration", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        // Offset present but no timestamp: legacy semantics treat this as no calibration
        prefs.edit().putFloat("calibration_offset", 10f).commit()
        assertNull(CalibrationRepository(context).calibrationResult.value)
        assertEquals(0.0, CalibrationRepository(context).getCurrentOffset(), 0.0)
    }
}
