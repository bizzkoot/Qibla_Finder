package com.bizzkoot.qiblafinder.ui.compass

import android.app.Activity
import android.app.Application
import android.content.Context
import androidx.lifecycle.viewModelScope
import com.bizzkoot.qiblafinder.MainDispatcherRule
import com.bizzkoot.qiblafinder.model.CalibrationRepository
import com.bizzkoot.qiblafinder.model.CompassStatus
import com.bizzkoot.qiblafinder.model.LocationRepository
import com.bizzkoot.qiblafinder.model.LocationState
import com.bizzkoot.qiblafinder.model.OrientationState
import com.bizzkoot.qiblafinder.model.SensorRepository
import com.google.android.gms.location.FusedLocationProviderClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * PRD M8 dead-end-error-state tests against the REAL [CompassViewModel]:
 *  - when the orientation flow fails/errors while the compass is visible, uiState must
 *    surface the failure (sensorError + OrientationState.Error) instead of leaving the
 *    UI stuck on "Initializing..." forever;
 *  - retrySensors() clears the error and re-collects getOrientationFlow();
 *  - retryLocation() re-requests the location flow after a LocationState.Error.
 *
 * SensorRepository is mocked (mockito-inline handles the final class) so the failure
 * path can be driven deterministically; CalibrationRepository is the real one.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class CompassViewModelRetryTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var context: Context
    private lateinit var calibrationRepository: CalibrationRepository

    @Before
    fun setUp() {
        context = Robolectric.buildActivity(Activity::class.java).setup().get()
        calibrationRepository = CalibrationRepository(context)
    }

    private fun availableOrientation() =
        OrientationState.Available(trueHeading = 10f, compassStatus = CompassStatus.OK)

    @Test
    fun `orientation flow failure sets sensorError and shows Error state`() = runBlocking {
        val sensorRepository = mock(SensorRepository::class.java)
        `when`(sensorRepository.orientationState)
            .thenReturn(MutableStateFlow(OrientationState.Initializing))
        `when`(sensorRepository.getOrientationFlow()).thenReturn(
            flow {
                emit(availableOrientation())
                throw RuntimeException("sensor registration failed")
            }
        )
        val locationRepository = spy(
            LocationRepository(context, mock(FusedLocationProviderClient::class.java))
        )

        val viewModel = CompassViewModel(locationRepository, sensorRepository, calibrationRepository)

        waitUntil("sensorError surfaced after flow failure") {
            viewModel.uiState.value.sensorError &&
                viewModel.uiState.value.orientationState is OrientationState.Error
        }

        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `retrySensors clears the error and re-collects the orientation flow`() = runBlocking {
        var getOrientationFlowCalls = 0
        val sensorRepository = mock(SensorRepository::class.java)
        `when`(sensorRepository.orientationState)
            .thenReturn(MutableStateFlow(OrientationState.Initializing))
        `when`(sensorRepository.getOrientationFlow()).thenAnswer {
            getOrientationFlowCalls++
            if (getOrientationFlowCalls == 1) {
                flow<OrientationState> { throw RuntimeException("sensor registration failed") }
            } else {
                flow<OrientationState> { emit(availableOrientation()) }
            }
        }
        val locationRepository = spy(
            LocationRepository(context, mock(FusedLocationProviderClient::class.java))
        )

        val viewModel = CompassViewModel(locationRepository, sensorRepository, calibrationRepository)
        waitUntil("sensorError surfaced after flow failure") { viewModel.uiState.value.sensorError }

        viewModel.retrySensors()

        waitUntil("orientation recovered after retry") {
            viewModel.uiState.value.orientationState is OrientationState.Available &&
                !viewModel.uiState.value.sensorError
        }
        assertEquals(false, viewModel.uiState.value.sensorError)
        assertEquals(2, getOrientationFlowCalls)
        verify(sensorRepository, times(2)).getOrientationFlow()

        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `retryLocation re-requests the location flow after a location error`() = runBlocking {
        var getLocationCalls = 0
        val sensorRepository = mock(SensorRepository::class.java)
        `when`(sensorRepository.orientationState)
            .thenReturn(MutableStateFlow(OrientationState.Initializing))
        `when`(sensorRepository.getOrientationFlow()).thenReturn(flowOf(availableOrientation()))

        val locationRepository = mock(LocationRepository::class.java)
        `when`(locationRepository.getLocation()).thenAnswer {
            getLocationCalls++
            flowOf(LocationState.Error("GPS fix timed out"))
        }
        `when`(locationRepository.isManualLocation).thenReturn(MutableStateFlow(false))

        val viewModel = CompassViewModel(locationRepository, sensorRepository, calibrationRepository)
        waitUntil("location error surfaced") {
            viewModel.uiState.value.locationState is LocationState.Error
        }
        assertEquals(1, getLocationCalls)

        viewModel.retryLocation()

        waitUntil("getLocation re-requested after retry") { getLocationCalls >= 2 }
        assertTrue(viewModel.uiState.value.locationState is LocationState.Error)

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
