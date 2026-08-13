package com.bizzkoot.qiblafinder.model

import android.Manifest
import android.app.Application
import android.content.Context
import android.location.Location
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
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
 * Regression tests for PRD H2: LocationRepository must never register more than one
 * LocationCallback. The dedupe guard is `if (locationCallback != null) return` at the top
 * of startLocationUpdates(); these tests pin that behavior so a silent removal of the
 * guard (which previously caused 3-4 concurrent GPS streams) fails CI.
 *
 * The FusedLocationProviderClient is a final class from play-services-location, so it is
 * mocked via the injected internal test seam using mockito-inline's final-class support.
 */
@RunWith(RobolectricTestRunner::class)
// Use a plain Application: the real QiblaFinderApplication.onCreate schedules WorkManager,
// which is not initialized in unit-test environments.
@Config(application = Application::class)
class LocationRepositoryDedupeTest {

    private lateinit var context: Context
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var repository: LocationRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Application>()
        // Grant fine location so startLocationUpdates() proceeds past the permission gate.
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
    fun `getLocation called twice registers requestLocationUpdates only once`() {
        repository.getLocation()
        repository.getLocation()

        // The dedupe guard must prevent a second LocationCallback registration.
        verifyRequestLocationUpdates(1)
        verifyRemoveLocationUpdates(0)
    }

    @Test
    fun `stopLocationUpdates removes callback and allows re-registration`() {
        repository.getLocation()
        repository.stopLocationUpdates()
        repository.getLocation()

        // stopLocationUpdates clears the guard, so the later getLocation() re-registers.
        verifyRequestLocationUpdates(2)
        verifyRemoveLocationUpdates(1)
    }

    @Test
    fun `setManualLocation stops location updates`() {
        repository.getLocation()
        repository.setManualLocation(Location("gps"))

        assertTrue(repository.isManualLocation)
        verifyRequestLocationUpdates(1)
        verifyRemoveLocationUpdates(1)

        // While manual, getLocation() must NOT re-register updates.
        repository.getLocation()
        verifyRequestLocationUpdates(1)
    }

    @Test
    fun `revertToGps restarts location updates`() {
        repository.getLocation()
        repository.setManualLocation(Location("gps"))
        repository.revertToGps()

        assertTrue(!repository.isManualLocation)
        // Initial registration + the restart from revertToGps().
        verifyRequestLocationUpdates(2)
        verifyRemoveLocationUpdates(1)
    }
}
