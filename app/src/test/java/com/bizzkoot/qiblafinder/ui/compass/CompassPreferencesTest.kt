package com.bizzkoot.qiblafinder.ui.compass

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
// Use a plain Application: the real QiblaFinderApplication.onCreate schedules WorkManager,
// which is not initialized in unit-test environments.
@Config(application = Application::class)
class CompassPreferencesTest {

    private fun prefs(): CompassPreferences {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Start from a clean slate for each test
        context.getSharedPreferences("compass_prefs", Context.MODE_PRIVATE)
            .edit().clear().commit()
        return CompassPreferences(context)
    }

    @Test
    fun `default keep screen on is true`() {
        assertTrue(prefs().getKeepScreenOn())
    }

    @Test
    fun `setKeepScreenOn false persists`() {
        val p = prefs()
        p.setKeepScreenOn(false)
        assertFalse(p.getKeepScreenOn())
    }

    @Test
    fun `setKeepScreenOn round trips back to true`() {
        val p = prefs()
        p.setKeepScreenOn(false)
        p.setKeepScreenOn(true)
        assertTrue(p.getKeepScreenOn())
    }

    @Test
    fun `preference survives instance recreation`() {
        val p1 = prefs()
        p1.setKeepScreenOn(false)
        val p2 = CompassPreferences(ApplicationProvider.getApplicationContext<Context>())
        assertEquals(false, p2.getKeepScreenOn())
    }
}
