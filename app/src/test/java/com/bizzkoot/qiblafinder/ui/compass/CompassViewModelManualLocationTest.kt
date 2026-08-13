package com.bizzkoot.qiblafinder.ui.compass

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.location.Location
import androidx.lifecycle.viewModelScope
import com.bizzkoot.qiblafinder.MainDispatcherRule
import com.bizzkoot.qiblafinder.model.CalibrationRepository
import com.bizzkoot.qiblafinder.model.LocationRepository
import com.bizzkoot.qiblafinder.model.LocationState
import com.bizzkoot.qiblafinder.model.MapLocation
import com.bizzkoot.qiblafinder.model.OrientationState
import com.bizzkoot.qiblafinder.model.SensorRepository
import com.google.android.gms.location.FusedLocationProviderClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSensor
import org.robolectric.shadows.ShadowSensorManager

/**
 * PRD M4 delegation tests: [CompassViewModel] must route manual-location mutations ONLY
 * through [LocationRepository.setManualLocation]/[revertToGps] (the single source of truth),
 * and [CompassUiState.isManualLocation] must reflect the repository's reactive flag rather
 * than a ViewModel-local copy. Built on the real repos (SensorRepository via ShadowSensorManager,
 * a spied LocationRepository for verifiability), so the uiState assertion exercises the real
 * gated location combine in CompassViewModel.init.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class CompassViewModelManualLocationTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var shadowSensorManager: ShadowSensorManager
    private lateinit var sensorRepository: SensorRepository
    private lateinit var locationRepository: LocationRepository
    private lateinit var calibrationRepository: CalibrationRepository
    private lateinit var accelerometer: Sensor
    private lateinit var magnetometer: Sensor
    private var timestampNs = 1_000_000_000L

    @Before
    fun setUp() {
        val context: Context = Robolectric.buildActivity(Activity::class.java).setup().get()
        shadowOf(context.applicationContext as Application)
            .grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)

        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        shadowSensorManager = shadowOf(sensorManager)
        accelerometer = ShadowSensor.newInstance(Sensor.TYPE_ACCELEROMETER)
        magnetometer = ShadowSensor.newInstance(Sensor.TYPE_MAGNETIC_FIELD)
        shadowSensorManager.addSensor(accelerometer)
        shadowSensorManager.addSensor(magnetometer)
        shadowSensorManager.addSensor(ShadowSensor.newInstance(Sensor.TYPE_GYROSCOPE))

        locationRepository = spy(
            LocationRepository(
                context,
                mock(FusedLocationProviderClient::class.java)
            )
        )
        calibrationRepository = CalibrationRepository(context)
        sensorRepository = SensorRepository(context, locationRepository)
    }

    private fun buildViewModel(): CompassViewModel {
        return CompassViewModel(locationRepository, sensorRepository, calibrationRepository)
    }

    private fun fireFlatPose() {
        fireEvent(accelerometer, 0f, 0f, 9.81f)
        fireEvent(magnetometer, 0f, 25f, 0f)
    }

    private fun fireEvent(sensor: Sensor, vararg values: Float) {
        val event = ShadowSensorManager.createSensorEvent(values.size, sensor.type)
        event.sensor = sensor
        for (i in values.indices) {
            event.values[i] = values[i]
        }
        event.timestamp = timestampNs
        timestampNs += 33_333_333L
        shadowSensorManager.sendSensorEventToListeners(event)
    }

    @Test
    fun `setManualLocation delegates to the repository and uiState reflects the manual flag`() = runBlocking {
        val viewModel = buildViewModel()
        waitUntil("sensor listener registered on start") { shadowSensorManager.getListeners().size == 1 }

        // The uiState combine emits only once every source (location, orientation,
        // calibration) has emitted; deliver one orientation reading first.
        fireFlatPose()
        waitUntil("uiState combine emitted") {
            viewModel.uiState.value.orientationState is OrientationState.Available
        }

        viewModel.setManualLocation(MapLocation(latitude = 24.467, longitude = 39.611))

        // The ViewModel must not own the mutation: it delegates to LocationRepository.
        // `any()` registers a Mockito matcher but returns null, which would trip Kotlin's
        // non-null parameter check on the Kotlin-declared setManualLocation(Location); the
        // elvis supplies a non-null dummy while the matcher still matches any argument.
        verify(locationRepository).setManualLocation(any() ?: Location("manual"))

        // uiState.isManualLocation reacts to the repository's StateFlow, not a local copy.
        waitUntil("uiState reflects the manual flag") { viewModel.uiState.value.isManualLocation }
        val state = viewModel.uiState.value.locationState
        assertTrue("manual location must flow into uiState", state is LocationState.Available)
        state as LocationState.Available
        assertEquals(24.467, state.location.latitude, 1e-9)
        assertEquals(39.611, state.location.longitude, 1e-9)

        viewModel.viewModelScope.cancel()
        waitUntil("sensor listener unregistered after scope cancel") { shadowSensorManager.getListeners().isEmpty() }
    }

    @Test
    fun `revertToGps delegates to the repository and clears the manual flag in uiState`() = runBlocking {
        val viewModel = buildViewModel()
        waitUntil("sensor listener registered on start") { shadowSensorManager.getListeners().size == 1 }

        fireFlatPose()
        waitUntil("uiState combine emitted") {
            viewModel.uiState.value.orientationState is OrientationState.Available
        }

        viewModel.setManualLocation(MapLocation(latitude = 24.467, longitude = 39.611))
        waitUntil("uiState manual flag set") { viewModel.uiState.value.isManualLocation }

        viewModel.revertToGps()
        verify(locationRepository).revertToGps()

        waitUntil("uiState manual flag cleared") { !viewModel.uiState.value.isManualLocation }

        viewModel.viewModelScope.cancel()
        waitUntil("sensor listener unregistered after scope cancel") { shadowSensorManager.getListeners().isEmpty() }
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
