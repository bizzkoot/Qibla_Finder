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
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import timber.log.Timber

data class CompassUiState(
    val locationState: LocationState = LocationState.Loading,
    val orientationState: OrientationState = OrientationState.Initializing,
    val qiblaBearing: Float? = null,
    val distanceToKaaba: String = "",
    val isSunCalibrated: Boolean = false,
    val isManualLocation: Boolean = false,
    // PRD M8: true when the sensor stream ended/errored while the compass is
    // visible (rotation-vector sensor absent or registration failure), so the UI
    // can offer a Retry instead of showing "Initializing..." forever.
    val sensorError: Boolean = false
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

    // PRD M8 retry plumbing. sensorError is set when the orientation flow ends with
    // an error/exception while the compass is visible; the retry keys force the
    // gated flows to re-subscribe (flatMapLatest re-collects when the key bumps),
    // which re-requests the sensor listeners / GPS updates.
    private val sensorError = MutableStateFlow(false)
    private val sensorRetryKey = MutableStateFlow(0)
    private val locationRetryKey = MutableStateFlow(0)

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
        // W2: the combine is gated behind the same screen-visible signal as the
        // orientation stream, so while the compass is not RESUMED it stops subscribing
        // to LocationRepository (mirroring the H4 orientation pattern).
        val locationDerived = combine(screenVisible, locationRetryKey) { visible, _ -> visible }
            .flatMapLatest { visible ->
                if (visible) {
                    combine(
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
                } else {
                    emptyFlow()
                }
            }

        // Gate the high-frequency orientation stream behind screen visibility:
        // while the compass is not RESUMED, flatMapLatest switches to an empty
        // flow, which cancels the sensor collection (SensorRepository unregisters
        // the listeners in awaitClose). combine caches the last value per source,
        // so the UI keeps showing the last heading while hidden and immediately
        // resumes with live readings when the compass becomes visible again —
        // no Initializing flash on return.
        val gatedOrientationFlow = combine(screenVisible, sensorRetryKey) { visible, _ -> visible }
            .flatMapLatest { visible ->
                if (visible) {
                    // Wrap the sensor stream so it NEVER completes while the compass
                    // is visible: after a failure (registration error / sensor absent)
                    // the wrapper suspends on awaitCancellation, keeping the outer
                    // combine alive so retrySensors() can re-collect. Without this, a
                    // completed inner flow would complete the whole combine and the
                    // UI would freeze on the last state.
                    flow {
                        sensorRepository.getOrientationFlow()
                            .onCompletion { cause ->
                                // The sensor stream ended while the compass is visible and
                                // NOT because we gated it off (that surfaces as a
                                // CancellationException) — a sensor failure (PRD M8).
                                // Only real failures (a thrown exception) set the flag;
                                // a benign normal completion is not treated as an error.
                                if (cause != null && cause !is CancellationException) {
                                    sensorError.value = true
                                }
                            }
                            .catch { emit(OrientationState.Error) }
                            .collect { emit(it) }
                        awaitCancellation()
                    }
                } else {
                    emptyFlow()
                }
            }

        viewModelScope.launch {
            combine(
                locationDerived,
                gatedOrientationFlow,
                // N5: collect the calibration state flow so isSunCalibrated reacts to
                // store/clear instead of being read once from `.value` inside the combine.
                calibrationRepository.calibrationResult,
                sensorError
            ) { derived, orientationState, calibrationResult, sensorErr ->
                CompassUiState(
                    locationState = derived.locationState,
                    orientationState = orientationState,
                    qiblaBearing = derived.qiblaBearing,
                    distanceToKaaba = derived.distanceToKaaba,
                    isSunCalibrated = calibrationResult != null,
                    isManualLocation = derived.isManualLocation,
                    sensorError = sensorErr
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
     * While false, the orientation sensor stream and the location collection are
     * gated off.
     */
    fun onScreenVisible(visible: Boolean) {
        screenVisible.value = visible
    }

    /**
     * Called by CompassScreen when the app is fully backgrounded (ON_STOP). Releases
     * the shared GPS callback so the device stops fixing location while nothing is
     * on screen; returning to the compass re-subscribes getLocation() (gated flow),
     * which restarts updates unless manual mode is active.
     */
    fun onScreenStopped() {
        locationRepository.stopLocationUpdates()
    }

    /**
     * PRD M8: re-request the sensor stream after a failure (sensor absent /
     * registration error). Clears the error flag and re-triggers the gated
     * orientation collection via the retry key.
     */
    fun retrySensors() {
        sensorError.value = false
        sensorRetryKey.value++
    }

    /**
     * PRD M8: re-request the location after a LocationState.Error. Re-collecting
     * getLocation() re-runs startLocationUpdates() (register-on-call semantics).
     */
    fun retryLocation() {
        locationRetryKey.value++
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
