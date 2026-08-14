package com.bizzkoot.qiblafinder.model

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
 * Regression tests for the GPS lifecycle gating fix: the shared location flow is
 * reference-counted in [LocationRepository], so the GPS callback is released when the
 * LAST collector ends (covered screens / app backgrounded) but stays registered while
 * any collector is still active.
 *
 * These tests pin the register-on-call semantics too: calling getLocation() without
 * collecting (as the Dedupe/ManualLocation tests do) must never trigger a removal.
 */
@RunWith(RobolectricTestRunner::class)
// Use a plain Application: the real QiblaFinderApplication.onCreate schedules WorkManager,
// which is not initialized in unit-test environments.
@Config(application = Application::class)
class LocationRepositoryGatingTest {

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
    fun `cancelling the only collector stops GPS updates`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val job = launch {
            repository.getLocation()
                .onStart { started.complete(Unit) }
                .collect { }
        }
        started.await()

        // Register-on-call still holds: collecting subscribes a single callback.
        verifyRequestLocationUpdates(1)
        verifyRemoveLocationUpdates(0)

        job.cancelAndJoin()

        // The last (only) collector ended -> the shared GPS callback is released.
        verifyRemoveLocationUpdates(1)
    }

    @Test
    fun `cancelling one of two collectors keeps GPS registered`() = runBlocking {
        val firstStarted = CompletableDeferred<Unit>()
        val secondStarted = CompletableDeferred<Unit>()
        val firstJob = launch {
            repository.getLocation()
                .onStart { firstStarted.complete(Unit) }
                .collect { }
        }
        firstStarted.await()
        val secondJob = launch {
            repository.getLocation()
                .onStart { secondStarted.complete(Unit) }
                .collect { }
        }
        secondStarted.await()

        // The dedupe guard keeps a single LocationCallback for both collectors.
        verifyRequestLocationUpdates(1)
        verifyRemoveLocationUpdates(0)

        firstJob.cancelAndJoin()

        // The second collector is still active, so GPS stays registered.
        verifyRemoveLocationUpdates(0)

        secondJob.cancelAndJoin()

        // Last collector gone -> shared callback released exactly once.
        verifyRemoveLocationUpdates(1)
    }

    @Test
    fun `getLocation without collecting never triggers a removal`() = runBlocking {
        repository.getLocation()
        repository.getLocation()

        // Register-on-call preserved, but with no collectors the refcount never
        // reaches zero, so no removal may fire.
        verifyRequestLocationUpdates(1)
        verifyRemoveLocationUpdates(0)
    }
}
