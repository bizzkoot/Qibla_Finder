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
import com.bizzkoot.qiblafinder.model.MapLocation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
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

@OptIn(ExperimentalCoroutinesApi::class)
class CompassViewModel(
    private val locationRepository: LocationRepository,
    private val sensorRepository: SensorRepository,
    private val calibrationRepository: CalibrationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CompassUiState())
    val uiState: StateFlow<CompassUiState> = _uiState

    private val isManualCalibrationInProgress = MutableStateFlow(false)

    // Lifecycle gate for the sensor stream: CompassScreen sets this to false
    // whenever this screen is not RESUMED (AR / Sun Calibration / Manual Location /
    // Help pushed on top, or the app backgrounded), so getOrientationFlow() is
    // cancelled and the accelerometer + magnetometer + gyroscope stop running.
    // Defaults to true because the ViewModel is created while the compass screen
    // is the active destination.
    private val screenVisible = MutableStateFlow(true)

    // Must be initialized before 'init' block uses it
    private val _showCalibration = MutableStateFlow(false)
    val showCalibration: StateFlow<Boolean> = _showCalibration

    init {
        // Apply any existing calibration offset immediately
        sensorRepository.setCalibrationOffset(calibrationRepository.getCurrentOffset())
        
        // Location-derived data (bearing + distance) recomputes only when the location
        // changes — never on the high-frequency orientation stream.
        // LocationRepository is the single source of truth for both the location state
        // (it already emits LocationState.Available for a manual location) and the
        // manual-mode flag (PRD M4).
        val locationDerived = combine(
            locationRepository.getLocation(),
            locationRepository.isManualLocation
        ) { locationState, isManual ->
            LocationDerivedState(
                locationState = locationState,
                qiblaBearing = calculateQiblaBearing(locationState),
                distanceToKaaba = calculateDistanceToKaaba(locationState),
                isManualLocation = isManual
            )
        }

        // Gate the high-frequency orientation stream behind screen visibility:
        // while the compass is not RESUMED, flatMapLatest switches to an empty
        // flow, which cancels the sensor collection (SensorRepository unregisters
        // the listeners in awaitClose). combine caches the last value per source,
        // so the UI keeps showing the last heading while hidden and immediately
        // resumes with live readings when the compass becomes visible again —
        // no Initializing flash on return.
        val gatedOrientationFlow = screenVisible.flatMapLatest { visible ->
            if (visible) sensorRepository.getOrientationFlow() else emptyFlow()
        }

        viewModelScope.launch {
            combine(
                locationDerived,
                gatedOrientationFlow
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
        locationRepository.setManualLocation(location)
        Timber.d("📍 Manual location set: $location")
    }

    fun revertToGps() {
        locationRepository.revertToGps()
        Timber.d("📍 Reverted to GPS location")
    }

    /**
     * Called by CompassScreen when its lifecycle transitions to/from RESUMED.
     * While false, the orientation sensor stream is gated off.
     */
    fun onScreenVisible(visible: Boolean) {
        screenVisible.value = visible
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
