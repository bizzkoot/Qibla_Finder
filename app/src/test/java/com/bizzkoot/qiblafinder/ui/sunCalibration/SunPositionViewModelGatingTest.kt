package com.bizzkoot.qiblafinder.ui.sunCalibration

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.bizzkoot.qiblafinder.MainDispatcherRule
import com.bizzkoot.qiblafinder.model.LocationRepository
import com.bizzkoot.qiblafinder.sunCalibration.SunPositionViewModel
import com.google.android.gms.location.FusedLocationProviderClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * PRD H4-style lifecycle-gating tests for the Sun Calibration route. [SunPositionViewModel]
 * must gate its GPS-backed getSunPosition() collection behind onScreenVisible(): while
 * hidden (another route covering it, or the app backgrounded) the collection is cancelled
 * and the LocationRepository refcount releases the shared GPS callback; returning to
 * visible re-subscribes and restarts GPS.
 *
 * Built against the REAL [SunPositionViewModel] with a spied [LocationRepository] (real
 * refcount behavior, verifiable invocations) so the assertions exercise the actual gating
 * wiring rather than a re-implementation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SunPositionViewModelGatingTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var locationRepository: LocationRepository

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        shadowOf(context as Application)
            .grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)

        locationRepository = spy(
            LocationRepository(
                context,
                mock(FusedLocationProviderClient::class.java)
            )
        )
    }

    private fun getLocationCallCount(): Int =
        Mockito.mockingDetails(locationRepository).invocations.count { it.method.name == "getLocation" }

    // stopLocationUpdates is the repository-level release invoked when the refcount hits
    // zero; the underlying removeLocationUpdates lands on the mocked FusedLocationProviderClient.
    private fun stopLocationUpdatesCallCount(): Int =
        Mockito.mockingDetails(locationRepository).invocations.count { it.method.name == "stopLocationUpdates" }

    @Test
    fun `onScreenVisible false releases GPS and true resumes the collection`() = runBlocking {
        val viewModel = SunPositionViewModel(locationRepository)

        // init collects getSunPosition() while visible -> getLocation() subscribed once.
        waitUntil("getLocation subscribed on start") { getLocationCallCount() == 1 }

        // Hidden: the gated flow switches to emptyFlow -> collection cancelled ->
        // LocationRepository refcount releases the shared GPS callback.
        viewModel.onScreenVisible(false)
        waitUntil("GPS released while hidden") { stopLocationUpdatesCallCount() == 1 }

        // No re-subscription while hidden.
        viewModel.onScreenVisible(false)
        delay(50)
        assertEquals(1, getLocationCallCount())

        // Visible again: a fresh collection re-subscribes to getLocation().
        viewModel.onScreenVisible(true)
        waitUntil("getLocation re-subscribed on return") { getLocationCallCount() == 2 }

        viewModel.viewModelScope.cancel()
    }

    private suspend fun waitUntil(
        message: String,
        timeoutMs: Long = 2_000,
        condition: () -> Boolean
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) fail("$message: Condition not met within ${timeoutMs}ms")
            delay(5)
        }
    }
}
