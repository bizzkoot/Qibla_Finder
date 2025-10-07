package com.bizzkoot.qiblafinder.ui.compass

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bizzkoot.qiblafinder.model.CompassStatus
import com.bizzkoot.qiblafinder.model.GeodesyUtils
import com.bizzkoot.qiblafinder.model.LocationRepository
import com.bizzkoot.qiblafinder.model.LocationState
import com.bizzkoot.qiblafinder.model.OrientationState
import com.bizzkoot.qiblafinder.model.SensorRepository
import com.bizzkoot.qiblafinder.sunCalibration.SunCalibrationViewModel
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
    private val sunCalibrationViewModel: SunCalibrationViewModel? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(CompassUiState())
    val uiState: StateFlow<CompassUiState> = _uiState

    private val manualLocationOverride = MutableStateFlow<Location?>(null)

    private val isManualCalibrationInProgress = MutableStateFlow(false)

    // Must be initialized before 'init' block uses it
    private val _showCalibration = MutableStateFlow(false)
    val showCalibration: StateFlow<Boolean> = _showCalibration

    init {
        // Apply any existing calibration offset
        sunCalibrationViewModel?.let { vm ->
            val offset = vm.getCurrentCalibrationOffset()
            sensorRepository.setCalibrationOffset(offset)
        }
        
        viewModelScope.launch {
            combine(
                locationRepository.getLocation(),
                sensorRepository.getOrientationFlow(),
                manualLocationOverride
            ) { locationState, orientationState, manualLocation ->
                Timber.d("🎯 CompassViewModel - Location: $locationState, Orientation: $orientationState, Manual: $manualLocation")

                val activeLocationState = if (manualLocation != null) {
                    LocationState.Available(
                        location = manualLocation,
                        accuracy = 0f, // Manual location is precise
                        accuracyLevel = com.bizzkoot.qiblafinder.model.LocationAccuracy.HIGH_ACCURACY
                    )
                } else {
                    locationState
                }
                
                val qiblaBearing = calculateQiblaBearing(activeLocationState)
                val distanceToKaaba = calculateDistanceToKaaba(activeLocationState)
                val isSunCalibrated = sunCalibrationViewModel?.calibrationResult?.value != null
                
                Timber.d("🧭 CompassViewModel - Qibla bearing: $qiblaBearing°, Distance: $distanceToKaaba, Sun calibrated: $isSunCalibrated")
                
                CompassUiState(
                    locationState = activeLocationState,
                    orientationState = orientationState,
                    qiblaBearing = qiblaBearing,
                    distanceToKaaba = distanceToKaaba,
                    isSunCalibrated = isSunCalibrated,
                    isManualLocation = manualLocation != null
                )
            }.collect { state ->
                Timber.d("📱 CompassViewModel - UI State updated: ${state.orientationState}")
                _uiState.value = state
            }
        }
        
        // Observe calibration changes
        sunCalibrationViewModel?.let { vm ->
            viewModelScope.launch {
                vm.calibrationResult.collect { result ->
                    val offset = result?.errorOffset ?: 0.0
                    sensorRepository.setCalibrationOffset(offset)
                }
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
                // Calculate actual distance to Kaaba
                val kaabaLat = 21.4225
                val kaabaLng = 39.8262
                val distance = GeodesyUtils.calculateDistance(
                    locationState.location.latitude,
                    locationState.location.longitude,
                    kaabaLat,
                    kaabaLng
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
}
