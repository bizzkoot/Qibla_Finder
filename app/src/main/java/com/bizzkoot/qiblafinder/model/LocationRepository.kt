package com.bizzkoot.qiblafinder.model

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger

sealed interface LocationState {
    object Loading : LocationState
    data class Available(
        val location: Location,
        val accuracy: Float,
        val accuracyLevel: LocationAccuracy
    ) : LocationState
    data class Error(val message: String) : LocationState
    object PermissionDenied : LocationState
}

enum class LocationAccuracy {
    HIGH_ACCURACY,    // GPS, accuracy < 10m
    MEDIUM_ACCURACY,  // Network, accuracy 10-100m
    LOW_ACCURACY,     // Network, accuracy > 100m
    UNKNOWN
}

class LocationRepository(private val context: Context) {

    // PRD M14: GPS fix timeout. When a fresh acquisition registers the GPS callback, a
    // timeout is armed; if no LocationState.Available fix arrives within the window the
    // radio is stopped and a LocationState.Error is emitted so the UI can offer Retry
    // instead of acquiring forever (which kept the GPS radio on indefinitely).
    private var fixTimeoutMs: Long = DEFAULT_FIX_TIMEOUT_MS

    // Repo-owned scope for the fix-timeout job. Defaults to a non-main dispatcher so the
    // timeout never blocks the UI; tests inject a TestScope for deterministic timing.
    private var scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Test seam: when set, overrides the lazily-created default client so tests can
    // inject a fake/mock FusedLocationProviderClient. The primary path never sets it.
    private var fusedLocationClientOverride: FusedLocationProviderClient? = null

    private val defaultFusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    private val fusedLocationClient: FusedLocationProviderClient
        get() = fusedLocationClientOverride ?: defaultFusedLocationClient

    internal constructor(
        context: Context,
        fusedLocationClient: FusedLocationProviderClient
    ) : this(context) {
        fusedLocationClientOverride = fusedLocationClient
    }

    // Test seam (PRD M14): injects the coroutine scope + timeout duration for
    // deterministic fix-timeout tests. The primary path uses the defaults above.
    internal constructor(
        context: Context,
        fusedLocationClient: FusedLocationProviderClient,
        scope: CoroutineScope,
        fixTimeoutMs: Long = DEFAULT_FIX_TIMEOUT_MS
    ) : this(context) {
        fusedLocationClientOverride = fusedLocationClient
        this.scope = scope
        this.fixTimeoutMs = fixTimeoutMs
    }
    
    private val _locationState = MutableStateFlow<LocationState>(LocationState.Loading)
    val locationState: Flow<LocationState> = _locationState.asStateFlow()
    
    // Audit hardening: locationCallback and fixTimeoutJob are written on the main thread
    // but read by the fix-timeout coroutine (Dispatchers.Default) after delay(), with no
    // happens-before edge. @Volatile guarantees cross-thread visibility so a stale read
    // can never let an old session's timeout fire against a newer acquisition.
    @Volatile
    private var locationCallback: LocationCallback? = null

    // PRD M14: the armed fix-timeout job for the current acquisition session. Cancelled
    // on a fix, on stopLocationUpdates(), or when manual mode is entered.
    @Volatile
    private var fixTimeoutJob: Job? = null

    // Tracks how many collectors are subscribed to the shared location flow so the
    // GPS callback is released when the LAST collector ends (covered screens / app
    // backgrounded). Register-on-call semantics are preserved: startLocationUpdates()
    // still runs when getLocation() is called, and stopLocationUpdates() fires only
    // when the last collector is cancelled/completes.
    private val activeCollectors = AtomicInteger(0)

    // Single source of truth for manual-location mode (PRD M4): the repository owns
    // the manual-location state so consumers (CompassViewModel, ManualLocationViewModel)
    // cannot desync. The flag is reactive so UI state can observe it directly.
    private val _isManualLocation = MutableStateFlow(false)
    val isManualLocation: StateFlow<Boolean> = _isManualLocation.asStateFlow()

    fun setManualLocation(location: Location) {
        _isManualLocation.value = true
        _locationState.value = LocationState.Available(
            location = location,
            accuracy = 5f, // Manual location has a fixed high accuracy
            accuracyLevel = LocationAccuracy.HIGH_ACCURACY
        )
        stopLocationUpdates() // Stop GPS updates when in manual mode
    }

    fun revertToGps() {
        _isManualLocation.value = false
        // Restart location updates
        startLocationUpdates()
    }

    private fun startLocationUpdates() {
        // This function is extracted from getLocation to be reusable
        if (locationCallback != null) {
            // Already running — avoid registering duplicate LocationCallbacks.
            // (Multiple subscribers previously created one callback each; only the
            // last was retained, leaking the earlier registrations.)
            return
        }
        if (!hasLocationPermission()) {
            _locationState.value = LocationState.PermissionDenied
            return
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
            .setWaitForAccurateLocation(false)
            .setMinUpdateIntervalMillis(5000)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                if (isManualLocation.value) return // Don't update if in manual mode

                locationResult.lastLocation?.let { location ->
                    // A fix arrived — the acquisition succeeded, so the fix timeout is
                    // no longer relevant (PRD M14).
                    cancelFixTimeout()
                    val accuracyLevel = when {
                        location.accuracy <= 10 -> LocationAccuracy.HIGH_ACCURACY
                        location.accuracy <= 100 -> LocationAccuracy.MEDIUM_ACCURACY
                        else -> LocationAccuracy.LOW_ACCURACY
                    }

                    val state = LocationState.Available(
                        location = location,
                        accuracy = location.accuracy,
                        accuracyLevel = accuracyLevel
                    )
                    _locationState.value = state
                    Timber.d("Location updated: ${location.latitude}, ${location.longitude}, accuracy: ${location.accuracy}m")
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                ContextCompat.getMainExecutor(context),
                locationCallback!!
            )
            // Fresh acquisition: surface the acquiring state ONLY when the previous state
            // was an error (so Retry shows "acquiring" again) — otherwise keep the
            // last-known-good fix instead of clobbering it on every re-registration
            // (foreground, return to compass, revert-to-GPS).
            if (_locationState.value is LocationState.Error ||
                _locationState.value is LocationState.PermissionDenied
            ) {
                _locationState.value = LocationState.Loading
            }
            armFixTimeout(locationCallback!!)
        } catch (e: SecurityException) {
            // Clear the callback so a later retry can re-attempt registration; leaving it
            // set would poison every future acquisition (the dedupe guard early-returns).
            locationCallback = null
            _locationState.value = LocationState.Error("Location permission denied")
        } catch (e: Exception) {
            // Same recovery for any other synchronous registration failure (e.g. location
            // services disconnected): clear the callback so retry is not a placebo.
            locationCallback = null
            _locationState.value = LocationState.Error("Failed to start location updates")
        }
    }

    /**
     * Arms the PRD M14 fix timeout for the given acquisition session. The timeout is
     * cancelled when a fix arrives, when [stopLocationUpdates] runs, or when manual mode
     * is entered (setManualLocation -> stopLocationUpdates). The identity check ensures a
     * stale timeout can never kill a newer acquisition session.
     */
    private fun armFixTimeout(callback: LocationCallback) {
        cancelFixTimeout()
        fixTimeoutJob = scope.launch {
            delay(fixTimeoutMs)
            // Only fire if this exact acquisition session is still live.
            if (locationCallback === callback) {
                Timber.w("GPS fix timed out after ${fixTimeoutMs}ms - stopping updates")
                _locationState.value =
                    LocationState.Error("GPS fix timed out. Check GPS signal and retry.")
                stopLocationUpdates()
            }
        }
    }

    private fun cancelFixTimeout() {
        fixTimeoutJob?.cancel()
        fixTimeoutJob = null
    }

    fun getLocation(): Flow<LocationState> {
        if (!isManualLocation.value) {
            startLocationUpdates()
        }
        return locationState
            .onStart { activeCollectors.incrementAndGet() }
            .onCompletion {
                // Release the shared GPS callback when the last collector ends so the
                // device stops fixing location while nothing is consuming it.
                if (activeCollectors.decrementAndGet() == 0) {
                    stopLocationUpdates()
                }
            }
    }
    
    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    fun stopLocationUpdates() {
        cancelFixTimeout()
        locationCallback?.let { callback ->
            fusedLocationClient.removeLocationUpdates(callback)
            locationCallback = null
        }
    }

    private companion object {
        const val DEFAULT_FIX_TIMEOUT_MS = 45_000L
    }
}