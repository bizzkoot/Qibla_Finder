package com.bizzkoot.qiblafinder.sunCalibration

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bizzkoot.qiblafinder.model.LocationRepository
import com.bizzkoot.qiblafinder.model.SensorRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * ViewModel for the Sun Calibration feature.
 * Handles the calibration process by comparing the sun's true azimuth
 * with the device's measured heading.
 */
class SunCalibrationViewModel(
    private val context: Context,
    private val locationRepository: LocationRepository,
    private val sensorRepository: SensorRepository,
    sunPositionViewModel: SunPositionViewModel
) : ViewModel() {
    
    private val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences("sun_calibration", Context.MODE_PRIVATE)
    }
    
    private val _uiState = MutableStateFlow<SunCalibrationUiState>(SunCalibrationUiState.Loading)
    val uiState: StateFlow<SunCalibrationUiState> = _uiState
    
    private val _calibrationResult = MutableStateFlow<CalibrationResult?>(getStoredCalibrationResult())
    val calibrationResult: StateFlow<CalibrationResult?> = _calibrationResult
    
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
                
                // Get the current device heading
                val orientationState = sensorRepository.orientationState.first()
                                    if (orientationState !is com.bizzkoot.qiblafinder.model.OrientationState.Available) {
                    _uiState.value = SunCalibrationUiState.Error("Device orientation not available")
                    return@launch
                }
                
                val measuredHeading = orientationState.trueHeading.toDouble()
                val sunAzimuth = sunState.sunAzimuth
                
                // Calculate the error offset
                // Error = True Sun Azimuth - Measured Heading
                // We need to normalize the result to be between -180 and 180 degrees
                var error = sunAzimuth - measuredHeading
                while (error > 180) error -= 360
                while (error < -180) error += 360
                
                // Create calibration result
                val result = CalibrationResult(
                    errorOffset = error,
                    timestamp = System.currentTimeMillis()
                )
                
                // Store the calibration result
                storeCalibrationResult(result)
                _calibrationResult.value = result
                _uiState.value = SunCalibrationUiState.Calibrated(result)
            } catch (e: Exception) {
                _uiState.value = SunCalibrationUiState.Error("Calibration failed: ${e.message}")
            }
        }
    }
    
    /**
     * Resets the calibration state
     */
    fun resetCalibration() {
        clearStoredCalibrationResult()
        _calibrationResult.value = null
        // Re-observe sun position to update UI state
        // This will be handled by the ongoing collection in init
    }
    
    /**
     * Gets the current calibration offset that should be applied to compass readings
     */
    fun getCurrentCalibrationOffset(): Double {
        return _calibrationResult.value?.errorOffset ?: 0.0
    }
    
    /**
     * Stores the calibration result in SharedPreferences
     */
    private fun storeCalibrationResult(result: CalibrationResult) {
        with(sharedPreferences.edit()) {
            putFloat("calibration_offset", result.errorOffset.toFloat())
            putLong("calibration_timestamp", result.timestamp)
            apply()
        }
    }
    
    /**
     * Retrieves the stored calibration result from SharedPreferences
     */
    private fun getStoredCalibrationResult(): CalibrationResult? {
        val offset = sharedPreferences.getFloat("calibration_offset", 0f)
        val timestamp = sharedPreferences.getLong("calibration_timestamp", 0L)
        
        return if (timestamp > 0) {
            CalibrationResult(
                errorOffset = offset.toDouble(),
                timestamp = timestamp
            )
        } else {
            null
        }
    }
    
    /**
     * Clears the stored calibration result
     */
    private fun clearStoredCalibrationResult() {
        with(sharedPreferences.edit()) {
            remove("calibration_offset")
            remove("calibration_timestamp")
            apply()
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

/**
 * Data class representing the result of a calibration.
 */
data class CalibrationResult(
    val errorOffset: Double,
    val timestamp: Long
)