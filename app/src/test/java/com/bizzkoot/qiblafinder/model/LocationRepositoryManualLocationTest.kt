package com.bizzkoot.qiblafinder.model

import android.Manifest
import android.app.Application
import android.content.Context
import android.location.Location
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.Executor

/**
 * Regression tests for PRD M4: LocationRepository is the single source of truth for
 * manual-location mode. The reactive `isManualLocation` StateFlow and the emitted
 * LocationState must stay in sync with setManualLocation()/revertToGps(), and GPS
 * updates must stop in manual mode and restart on revert.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LocationRepositoryManualLocationTest {

    private lateinit var context: Context
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var repository: LocationRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(context as Application).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        fusedLocationClient = mock(FusedLocationProviderClient::class.java)
        repository = LocationRepository(context, fusedLocationClient)
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
    fun `setManualLocation emits manual Available state, flags isManualLocation, stops GPS`() {
        val manualLocation = Location("manual").apply {
            latitude = 24.467
            longitude = 39.611
        }

        repository.getLocation() // start GPS updates first
        repository.setManualLocation(manualLocation)

        // The reactive flag is the single source of truth.
        assertTrue(repository.isManualLocation.value)

        // The location flow now carries the manual location as Available.
        val state = runBlocking { repository.locationState.first() }
        assertTrue(state is LocationState.Available)
        state as LocationState.Available
        assertEquals(manualLocation.latitude, state.location.latitude, 1e-9)
        assertEquals(manualLocation.longitude, state.location.longitude, 1e-9)
        assertEquals(5f, state.accuracy)
        assertEquals(LocationAccuracy.HIGH_ACCURACY, state.accuracyLevel)

        // GPS updates were stopped when entering manual mode.
        verifyRemoveLocationUpdates(1)

        // getLocation() while manual must not re-register updates.
        repository.getLocation()
        verifyRequestLocationUpdates(1)
    }

    @Test
    fun `revertToGps clears the flag and restarts GPS updates`() {
        repository.getLocation()
        repository.setManualLocation(Location("gps"))
        assertTrue(repository.isManualLocation.value)

        repository.revertToGps()

        assertFalse(repository.isManualLocation.value)
        // Initial registration + the restart from revertToGps().
        verifyRequestLocationUpdates(2)
        verifyRemoveLocationUpdates(1)
    }

    @Test
    fun `setting a second manual location keeps flag true and re-emits the new location`() {
        val first = Location("manual").apply {
            latitude = 1.0
            longitude = 2.0
        }
        val second = Location("manual").apply {
            latitude = 3.0
            longitude = 4.0
        }

        repository.setManualLocation(first)
        repository.setManualLocation(second)

        assertTrue(repository.isManualLocation.value)
        val state = runBlocking { repository.locationState.first() }
        assertTrue(state is LocationState.Available)
        state as LocationState.Available
        assertEquals(second.latitude, state.location.latitude, 1e-9)
        assertEquals(second.longitude, state.location.longitude, 1e-9)
    }
}
