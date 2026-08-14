package com.bizzkoot.qiblafinder.model

import android.Manifest
import android.app.Application
import android.content.Context
import android.location.Location
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.Executor

/**
 * PRD M14 fix-timeout tests against the REAL [LocationRepository]:
 *  - a fresh GPS acquisition arms a fix timeout; if no fix arrives within the window
 *    the repository emits LocationState.Error and stops the radio;
 *  - a fix, stopLocationUpdates(), or manual mode cancels the timeout;
 *  - after a timeout, a new acquisition re-registers a fresh callback;
 *  - a failed registration clears the callback so Retry is not a placebo.
 *
 * Time is virtual: the repository is constructed with a [TestScope] + short timeout via
 * the internal test seam, and the test advances the scheduler deterministically.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
// Use a plain Application: the real QiblaFinderApplication.onCreate schedules WorkManager,
// which is not initialized in unit-test environments.
@Config(application = Application::class)
class LocationRepositoryFixTimeoutTest {

    private val testScope = TestScope(StandardTestDispatcher())

    private lateinit var context: Context
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var repository: LocationRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(context as Application).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        fusedLocationClient = mock(FusedLocationProviderClient::class.java)
        repository = LocationRepository(context, fusedLocationClient, testScope, FIX_TIMEOUT_MS)
    }

    @After
    fun tearDown() {
        testScope.cancel()
    }

    private val state: StateFlow<LocationState>
        get() = repository.locationState as StateFlow<LocationState>

    /** Starts acquisition and returns the LocationCallback the repository registered. */
    private fun startAcquisition(): LocationCallback {
        repository.getLocation()
        val captor = ArgumentCaptor.forClass(LocationCallback::class.java)
        verify(fusedLocationClient).requestLocationUpdates(
            any(LocationRequest::class.java),
            any(Executor::class.java),
            captor.capture()
        )
        return captor.value
    }

    private fun verifyRequestLocationUpdates(times: Int) {
        verify(fusedLocationClient, times(times)).requestLocationUpdates(
            any(LocationRequest::class.java),
            any(Executor::class.java),
            any(LocationCallback::class.java)
        )
    }

    private fun verifyRemoveLocationUpdates(times: Int) {
        verify(fusedLocationClient, times(times)).removeLocationUpdates(
            any(LocationCallback::class.java)
        )
    }

    @Test
    fun `no fix within the window emits Error and stops the radio`() {
        startAcquisition()
        // A fresh acquisition surfaces the acquiring state (not a stale error).
        assertEquals(LocationState.Loading, state.value)

        testScope.advanceTimeBy(FIX_TIMEOUT_MS)
        testScope.runCurrent()

        val current = state.value
        assertTrue("expected Error after timeout, got $current", current is LocationState.Error)
        assertTrue((current as LocationState.Error).message.contains("timed out"))
        // The radio was turned off when the timeout fired.
        verifyRemoveLocationUpdates(1)
    }

    @Test
    fun `a fix arriving before the window cancels the timeout`() {
        val callback = startAcquisition()

        callback.onLocationResult(
            LocationResult.create(
                listOf(
                    Location("gps").apply {
                        latitude = 24.467
                        longitude = 39.611
                        accuracy = 5f
                    }
                )
            )
        )
        assertTrue(state.value is LocationState.Available)

        // The window elapsing after a fix must NOT produce an error or stop updates.
        testScope.advanceTimeBy(FIX_TIMEOUT_MS)
        testScope.runCurrent()

        assertTrue("no Error expected after a fix", state.value is LocationState.Available)
        verifyRemoveLocationUpdates(0)
    }

    @Test
    fun `stopLocationUpdates before the window prevents the timeout error`() {
        startAcquisition()
        repository.stopLocationUpdates()

        testScope.advanceTimeBy(FIX_TIMEOUT_MS)
        testScope.runCurrent()

        assertTrue(
            "no Error expected after explicit stop",
            state.value is LocationState.Loading
        )
        // Only the explicit stop removed the callback — the timeout never fired.
        verifyRemoveLocationUpdates(1)
    }

    @Test
    fun `acquisition can restart with a fresh callback after a timeout`() {
        startAcquisition()
        testScope.advanceTimeBy(FIX_TIMEOUT_MS)
        testScope.runCurrent()
        assertTrue(state.value is LocationState.Error)
        verifyRemoveLocationUpdates(1)

        // A new acquisition re-registers a fresh callback and re-surfaces Loading.
        repository.getLocation()
        verifyRequestLocationUpdates(2)
        assertEquals(LocationState.Loading, state.value)
    }

    @Test
    fun `a stale timeout cannot fire against a newer acquisition session`() {
        startAcquisition() // session A, timeout deadline at t=50
        testScope.advanceTimeBy(FIX_TIMEOUT_MS / 2)
        repository.stopLocationUpdates()
        repository.getLocation() // session B, fresh timeout with a later deadline
        assertEquals(LocationState.Loading, state.value)

        // Advance past session A's deadline (t=50); B (armed at t=25) is still pending.
        testScope.advanceTimeBy(FIX_TIMEOUT_MS / 2 + 1)
        testScope.runCurrent()

        assertTrue(
            "stale timeout must not fire against the newer session",
            state.value is LocationState.Loading
        )
        // Only the explicit stop removed a callback — no timeout fired.
        verifyRemoveLocationUpdates(1)

        // Session B's OWN timeout still fires at its deadline.
        testScope.advanceTimeBy(FIX_TIMEOUT_MS)
        testScope.runCurrent()
        assertTrue("new session timeout expected", state.value is LocationState.Error)
        verifyRemoveLocationUpdates(2)
    }

    @Test
    fun `failed registration clears the callback so retry is not a placebo`() {
        doThrow(SecurityException("permission revoked")).`when`(fusedLocationClient)
            .requestLocationUpdates(
                any(LocationRequest::class.java),
                any(Executor::class.java),
                any(LocationCallback::class.java)
            )

        repository.getLocation()
        assertTrue(state.value is LocationState.Error)

        // The dedupe guard must not block a later re-attempt: the callback was cleared.
        repository.getLocation()
        verifyRequestLocationUpdates(2)
    }

    @Test
    fun `re-registration while the last state is Available keeps the last fix`() {
        val callback = startAcquisition()
        callback.onLocationResult(
            LocationResult.create(
                listOf(
                    Location("gps").apply {
                        latitude = 24.467
                        longitude = 39.611
                        accuracy = 5f
                    }
                )
            )
        )
        assertTrue(state.value is LocationState.Available)

        // Release the radio, then re-acquire: the last-known-good fix must survive the
        // re-registration (no Loading flash on foreground / return-to-compass).
        repository.stopLocationUpdates()
        repository.getLocation()
        verifyRequestLocationUpdates(2)

        assertTrue(
            "re-registration must not clobber an Available fix with Loading, got ${state.value}",
            state.value is LocationState.Available
        )
    }

    @Test
    fun `re-registration after a failed registration emits Loading`() {
        // First registration fails (SecurityException); the retry then succeeds. The mock
        // returns Task<Void>, so the success leg is a doReturn(null) after the doThrow.
        doThrow(SecurityException("permission revoked"))
            .doReturn(null)
            .`when`(fusedLocationClient)
            .requestLocationUpdates(
                any(LocationRequest::class.java),
                any(Executor::class.java),
                any(LocationCallback::class.java)
            )

        repository.getLocation()
        assertTrue(state.value is LocationState.Error)

        // The retry re-registers successfully; because the previous state was an Error,
        // the fresh acquisition surfaces Loading (Retry shows "acquiring" again).
        repository.getLocation()
        verifyRequestLocationUpdates(2)
        assertEquals(LocationState.Loading, state.value)
    }

    @Test
    fun `non-security registration failure clears the callback for a later retry`() {
        doThrow(RuntimeException("location service unavailable")).`when`(fusedLocationClient)
            .requestLocationUpdates(
                any(LocationRequest::class.java),
                any(Executor::class.java),
                any(LocationCallback::class.java)
            )

        repository.getLocation()
        val current = state.value
        assertTrue("expected Error, got $current", current is LocationState.Error)
        assertEquals(
            "Failed to start location updates",
            (current as LocationState.Error).message
        )

        // The dedupe guard must not block a later re-attempt: the callback was cleared.
        repository.getLocation()
        verifyRequestLocationUpdates(2)
    }

    private companion object {
        const val FIX_TIMEOUT_MS = 50L
    }
}
