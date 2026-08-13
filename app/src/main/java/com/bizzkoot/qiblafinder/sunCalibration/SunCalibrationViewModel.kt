package com.bizzkoot.qiblafinder.sunCalibration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bizzkoot.qiblafinder.model.CalibrationRepository
import com.bizzkoot.qiblafinder.model.CalibrationResult
import com.bizzkoot.qiblafinder.model.LocationRepository
import com.bizzkoot.qiblafinder.model.SensorRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * ViewModel for the Sun Calibration feature.
 * Handles the calibration process by comparing the sun's true azimuth
 * with the device's measured heading.
 */
class SunCalibrationViewModel(
    private val locationRepository: LocationRepository,
    private val sensorRepository: SensorRepository,
    private val calibrationRepository: CalibrationRepository,
    sunPositionViewModel: SunPositionViewModel
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<SunCalibrationUiState>(SunCalibrationUiState.Loading)
    val uiState: StateFlow<SunCalibrationUiState> = _uiState
    
    // Single source of truth lives in CalibrationRepository (persisted + reactive).
    // It is seeded from SharedPreferences at construction, so a previously stored
    // calibration is reflected here immediately.
    val calibrationResult: StateFlow<CalibrationResult?> = calibrationRepository.calibrationResult
    
    init {
        observeSunPosition(sunPositionViewModel)
    }
    
    /**
     * Observes the sun position data and updates the UI state accordingly.
     */
    private fun observeSunPosition(sunPositionViewModel: SunPositionViewModel) {
        viewModelScope.launch {
            sunPositionViewModel.uiState.collect { sunPositionUiState ->
                when (sunPositionUiState) {
                    is SunPositionUiState.Loading -> {
                        _uiState.value = SunCalibrationUiState.Loading
                    }
                    is SunPositionUiState.Available -> {
                        _uiState.value = SunCalibrationUiState.Ready(
                            sunAzimuth = sunPositionUiState.azimuth,
                            sunElevation = sunPositionUiState.elevation,
                            isSunVisible = sunPositionUiState.isVisible
                        )
                    }
                    is SunPositionUiState.Error -> {
                        _uiState.value = SunCalibrationUiState.Error(sunPositionUiState.message)
                    }
                }
            }
        }
    }
    
    /**
     * Performs the calibration by comparing the sun's true azimuth with
     * the device's current heading.
     */
    fun performCalibration() {
        viewModelScope.launch {
            try {
                // Get the current sun position
                val sunState = uiState.value
                if (sunState !is SunCalibrationUiState.Ready) {
                    _uiState.value = SunCalibrationUiState.Error("Sun position data not available")
                    return@launch
                }
                
                // Check if sun is visible
                if (!sunState.isSunVisible) {
                    _uiState.value = SunCalibrationUiState.Error("Sun is not visible. Please try again during daylight hours.")
                    return@launch
                }
                
                // Get a LIVE device heading. The compass gates its own sensor
                // collection off as soon as this route covers it, so the shared
                // orientationState StateFlow would be frozen at the pre-navigation
                // reading (C1); collect a fresh orientation flow instead. The timeout
                // bounds the wait for the sensor collection mutex (SensorRepository)
                // in case a stale holder is still releasing it.
                val measuredHeading = withTimeout(5_000L) {
                    sensorRepository.getOrientationFlow()
                        .filterIsInstance<com.bizzkoot.qiblafinder.model.OrientationState.Available>()
                        .first()
                        .trueHeading.toDouble()
                }
                val sunAzimuth = sunState.sunAzimuth
                
                // The emitted heading already has the current calibration offset baked
                // in (SensorRepository applies it to every reading). Computing the
                // error against that calibrated heading would double-apply the previous
                // offset on a re-calibration, so correct it back to the un-offset
                // heading first (C2).
                val currentOffset = calibrationRepository.getCurrentOffset()
                val unOffsetHeading = (measuredHeading - currentOffset + 360.0) % 360.0
                
                // Calculate the error offset
                // Error = True Sun Azimuth - Un-offset Measured Heading
                // We need to normalize the result to be between -180 and 180 degrees
                var error = sunAzimuth - unOffsetHeading
                while (error > 180) error -= 360
                while (error < -180) error += 360
                
                // Create calibration result and store it via the repository
                // (persists to SharedPreferences and publishes to calibrationResult)
                val result = CalibrationResult(
                    errorOffset = error,
                    timestamp = System.currentTimeMillis()
                )
                calibrationRepository.store(result)
                _uiState.value = SunCalibrationUiState.Calibrated(result)
            } catch (e: Exception) {
                _uiState.value = SunCalibrationUiState.Error("Calibration failed: ${e.message}")
            }
        }
    }
}

/**
 * Sealed interface representing the UI state for sun calibration.
 */
sealed interface SunCalibrationUiState {
    /**
     * Initial state when data is being loaded.
     */
    object Loading : SunCalibrationUiState
    
    /**
     * State when the calibration UI is ready and sun position data is available.
     */
    data class Ready(
        val sunAzimuth: Double,
        val sunElevation: Double,
        val isSunVisible: Boolean
    ) : SunCalibrationUiState
    
    /**
     * State when calibration has been successfully performed.
     */
    data class Calibrated(val result: CalibrationResult) : SunCalibrationUiState
    
    /**
     * State when there is an error.
     */
    data class Error(val message: String) : SunCalibrationUiState
}
