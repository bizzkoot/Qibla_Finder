package com.bizzkoot.qiblafinder.ui.compass

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bizzkoot.qiblafinder.model.CalibrationRepository
import com.bizzkoot.qiblafinder.model.CompassStatus
import com.bizzkoot.qiblafinder.model.GeodesyUtils
import com.bizzkoot.qiblafinder.model.LocationRepository
import com.bizzkoot.qiblafinder.model.LocationState
import com.bizzkoot.qiblafinder.model.OrientationState
import com.bizzkoot.qiblafinder.model.SensorRepository
import com.bizzkoot.qiblafinder.ui.location.MapLocation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import timber.log.Timber

data class CompassUiState(
    val locationState: LocationState = LocationState.Loading,
    val orientationState: OrientationState = OrientationState.Initializing,
    val qiblaBearing: Float? = null,
    val distanceToKaaba: String = "",
    val isSunCalibrated: Boolean = false,
    val isManualLocation: Boolean = false
)

class CompassViewModel(
    private val locationRepository: LocationRepository,
    private val sensorRepository: SensorRepository,
    private val calibrationRepository: CalibrationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CompassUiState())
    val uiState: StateFlow<CompassUiState> = _uiState

    private val manualLocationOverride = MutableStateFlow<Location?>(null)

    private val isManualCalibrationInProgress = MutableStateFlow(false)

    // Must be initialized before 'init' block uses it
    private val _showCalibration = MutableStateFlow(false)
    val showCalibration: StateFlow<Boolean> = _showCalibration

    init {
        // Apply any existing calibration offset immediately
        sensorRepository.setCalibrationOffset(calibrationRepository.getCurrentOffset())
        
        // Location-derived data (bearing + distance) recomputes only when the location
        // changes — never on the high-frequency orientation stream.
        val locationDerived = combine(
            locationRepository.getLocation(),
            manualLocationOverride
        ) { locationState, manualLocation ->
            val activeLocationState = if (manualLocation != null) {
                LocationState.Available(
                    location = manualLocation,
                    accuracy = 0f, // Manual location is precise
                    accuracyLevel = com.bizzkoot.qiblafinder.model.LocationAccuracy.HIGH_ACCURACY
                )
            } else {
                locationState
            }
            LocationDerivedState(
                locationState = activeLocationState,
                qiblaBearing = calculateQiblaBearing(activeLocationState),
                distanceToKaaba = calculateDistanceToKaaba(activeLocationState),
                isManualLocation = manualLocation != null
            )
        }

        viewModelScope.launch {
            combine(
                locationDerived,
                sensorRepository.getOrientationFlow()
            ) { derived, orientationState ->
                CompassUiState(
                    locationState = derived.locationState,
                    orientationState = orientationState,
                    qiblaBearing = derived.qiblaBearing,
                    distanceToKaaba = derived.distanceToKaaba,
                    isSunCalibrated = calibrationRepository.calibrationResult.value != null,
                    isManualLocation = derived.isManualLocation
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
        
        // Observe calibration changes: calibrating on the Sun Calibration screen
        // applies the new offset to the compass immediately on return.
        viewModelScope.launch {
            calibrationRepository.calibrationResult.collect { result ->
                val offset = result?.errorOffset ?: 0.0
                sensorRepository.setCalibrationOffset(offset)
            }
        }

        // Observe orientation state for automatic calibration
        viewModelScope.launch {
            combine(
                sensorRepository.orientationState,
                isManualCalibrationInProgress
            ) { orientation, manual ->
                val auto = (orientation as? OrientationState.Available)?.shouldShowCalibration ?: false
                auto || manual
            }.collect { shouldShow ->
                _showCalibration.value = shouldShow
            }
        }
    }

    fun setManualLocation(mapLocation: MapLocation) {
        val location = Location("manual").apply {
            latitude = mapLocation.latitude
            longitude = mapLocation.longitude
        }
        manualLocationOverride.value = location
        Timber.d("📍 Manual location override set: $location")
    }

    fun revertToGps() {
        manualLocationOverride.value = null
        locationRepository.revertToGps()
        Timber.d("📍 Reverted to GPS location")
    }

    private fun calculateQiblaBearing(locationState: LocationState): Float? {
        return when (locationState) {
            is LocationState.Available -> {
                GeodesyUtils.calculateQiblaBearing(
                    locationState.location.latitude,
                    locationState.location.longitude
                ).toFloat()
            }
            else -> null
        }
    }

    private fun calculateDistanceToKaaba(locationState: LocationState): String {
        return when (locationState) {
            is LocationState.Available -> {
                // Single source of truth: Kaaba coordinates live in GeodesyUtils
                val distance = GeodesyUtils.calculateDistanceToKaaba(
                    locationState.location.latitude,
                    locationState.location.longitude
                )
                "${distance.toInt()} km"
            }
            else -> ""
        }
    }
    
    fun startCalibration() {
        isManualCalibrationInProgress.value = true
        sensorRepository.onManualCalibrationRequested()
    }
    
    fun stopCalibration() {
        isManualCalibrationInProgress.value = false
        sensorRepository.onCalibrationDismissed()
    }

    override fun onCleared() {
        super.onCleared()
        // Stop GPS updates when the compass (the primary screen) is destroyed.
        // LocationRepository guards against duplicate registration, so the shared
        // repo stays consistent even if other screens are still alive.
        locationRepository.stopLocationUpdates()
    }
}

private data class LocationDerivedState(
    val locationState: LocationState,
    val qiblaBearing: Float?,
    val distanceToKaaba: String,
    val isManualLocation: Boolean
)
