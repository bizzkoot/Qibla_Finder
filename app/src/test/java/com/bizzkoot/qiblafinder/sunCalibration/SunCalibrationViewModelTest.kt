package com.bizzkoot.qiblafinder.sunCalibration

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.bizzkoot.qiblafinder.MainDispatcherRule
import com.bizzkoot.qiblafinder.model.CalibrationRepository
import com.bizzkoot.qiblafinder.model.CalibrationResult
import com.bizzkoot.qiblafinder.model.CompassStatus
import com.bizzkoot.qiblafinder.model.LocationRepository
import com.bizzkoot.qiblafinder.model.OrientationState
import com.bizzkoot.qiblafinder.model.SensorRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression tests for the re-calibration offset math in [SunCalibrationViewModel.performCalibration]
 * (the C2 fix): the measured heading has the previous calibration offset baked in by
 * SensorRepository, so re-calibration must un-offset it before computing the error,
 * otherwise the previous offset is double-applied.
 *
 * Design notes (validated):
 *  - [SunPositionViewModel] is MOCKED with a mutable uiState flow: the real one calls the
 *    real SunPositionCalculator with Date(), making azimuth/visibility time-of-day
 *    dependent and CI-flaky. Stubbing keeps the sun azimuth deterministic (180.0 unless
 *    a case says otherwise).
 *  - [SensorRepository] is mocked: getOrientationFlow() stubs the live heading;
 *    orientationState stubs the (deliberately stale) cached state to prove the live
 *    flow wins.
 *  - [CalibrationRepository] is REAL with cleared prefs so the "replaces old offset"
 *    assertions exercise real persistence + StateFlow.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
// Use a plain Application: the real QiblaFinderApplication.onCreate schedules WorkManager,
// which is not initialized in unit-test environments.
@Config(application = Application::class)
class SunCalibrationViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var context: Context
    private lateinit var sensorRepository: SensorRepository
    private lateinit var calibrationRepository: CalibrationRepository
    private lateinit var sunPositionViewModel: SunPositionViewModel
    private lateinit var sunPositionUiState: MutableStateFlow<SunPositionUiState>

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Application>()
        // Start from a clean slate for each test.
        context.getSharedPreferences("sun_calibration", Context.MODE_PRIVATE)
            .edit().clear().commit()

        sensorRepository = mock(SensorRepository::class.java)
        calibrationRepository = CalibrationRepository(context)
        sunPositionViewModel = mock(SunPositionViewModel::class.java)
        sunPositionUiState = MutableStateFlow(
            SunPositionUiState.Available(
                azimuth = 180.0,
                elevation = 30.0,
                isVisible = true
            )
        )
        `when`(sunPositionViewModel.uiState).thenReturn(sunPositionUiState)
    }

    private fun buildViewModel(): SunCalibrationViewModel {
        return SunCalibrationViewModel(
            locationRepository = mock(LocationRepository::class.java),
            sensorRepository = sensorRepository,
            calibrationRepository = calibrationRepository,
            sunPositionViewModel = sunPositionViewModel
        )
    }

    private fun setSunAzimuth(azimuth: Double) {
        sunPositionUiState.value = SunPositionUiState.Available(
            azimuth = azimuth,
            elevation = 30.0,
            isVisible = true
        )
    }

    private fun stubHeading(heading: Double, cachedHeading: Double? = null) {
        `when`(sensorRepository.getOrientationFlow()).thenReturn(
            flowOf(
                OrientationState.Available(
                    trueHeading = heading.toFloat(),
                    compassStatus = CompassStatus.OK
                )
            )
        )
        if (cachedHeading != null) {
            `when`(sensorRepository.orientationState).thenReturn(
                flowOf(
                    OrientationState.Available(
                        trueHeading = cachedHeading.toFloat(),
                        compassStatus = CompassStatus.OK
                    )
                )
            )
        }
    }

    private fun assertStoredOffset(expected: Double) {
        val result = calibrationRepository.calibrationResult.value
        assertNotNull("calibration result should be stored", result)
        assertEquals(expected, result!!.errorOffset, 1e-6)
    }

    @Test
    fun `first calibration computes the error from the live heading`() = runBlocking {
        // Sun at 180, device pointing at the sun but measuring 190 -> error -10.
        stubHeading(heading = 190.0)
        val viewModel = buildViewModel()

        viewModel.performCalibration()

        assertStoredOffset(-10.0)
        assertTrue(viewModel.uiState.value is SunCalibrationUiState.Calibrated)
    }

    @Test
    fun `re-calibration un-offsets the baked-in offset without double-applying it`() = runBlocking {
        // Previous calibration stored -10. The sensor bakes it into every reading, so
        // a physical 180 arrives as 170. The error must be 180 - (170 - (-10)) = 0.
        // A buggy double-apply would store +10.
        calibrationRepository.store(CalibrationResult(errorOffset = -10.0, timestamp = 1L))
        stubHeading(heading = 170.0)
        val viewModel = buildViewModel()

        viewModel.performCalibration()

        assertStoredOffset(0.0)
        assertTrue(viewModel.uiState.value is SunCalibrationUiState.Calibrated)
    }

    @Test
    fun `second calibration replaces the old offset without accumulation`() = runBlocking {
        // Seed -10. Physical 205 arrives as 195. Error must be 180 - 205 = -25,
        // i.e. the new offset replaces the old one (no -35 accumulation).
        calibrationRepository.store(CalibrationResult(errorOffset = -10.0, timestamp = 1L))
        stubHeading(heading = 195.0)
        val viewModel = buildViewModel()

        viewModel.performCalibration()

        assertStoredOffset(-25.0)
        assertTrue(viewModel.uiState.value is SunCalibrationUiState.Calibrated)
    }

    @Test
    fun `error wraps above 180 degrees`() = runBlocking {
        // Sun at 350, heading 10 -> raw error 340 -> normalized -20.
        setSunAzimuth(350.0)
        stubHeading(heading = 10.0)
        val viewModel = buildViewModel()

        viewModel.performCalibration()

        assertStoredOffset(-20.0)
        assertTrue(viewModel.uiState.value is SunCalibrationUiState.Calibrated)
    }

    @Test
    fun `error wraps below -180 degrees`() = runBlocking {
        // Sun at 10, heading 350 -> raw error -340 -> normalized +20.
        setSunAzimuth(10.0)
        stubHeading(heading = 350.0)
        val viewModel = buildViewModel()

        viewModel.performCalibration()

        assertStoredOffset(20.0)
        assertTrue(viewModel.uiState.value is SunCalibrationUiState.Calibrated)
    }

    @Test
    fun `live orientation flow wins over the cached orientation state`() = runBlocking {
        // The live flow reports 190 (-> error -10); the cached orientationState is
        // deliberately stale at 300 (which would produce -120 if the VM read it).
        stubHeading(heading = 190.0, cachedHeading = 300.0)
        val viewModel = buildViewModel()

        viewModel.performCalibration()

        assertStoredOffset(-10.0)
        assertTrue(viewModel.uiState.value is SunCalibrationUiState.Calibrated)
    }
}
