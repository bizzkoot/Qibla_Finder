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
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import timber.log.Timber

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
    
    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }
    
    private val _locationState = MutableStateFlow<LocationState>(LocationState.Loading)
    val locationState: Flow<LocationState> = _locationState.asStateFlow()
    
    private var locationCallback: LocationCallback? = null
    var isManualLocation = false
        private set
    private var manualLocation: Location? = null

    fun setManualLocation(location: Location) {
        isManualLocation = true
        manualLocation = location
        _locationState.value = LocationState.Available(
            location = location,
            accuracy = 5f, // Manual location has a fixed high accuracy
            accuracyLevel = LocationAccuracy.HIGH_ACCURACY
        )
        stopLocationUpdates() // Stop GPS updates when in manual mode
    }

    fun revertToGps() {
        isManualLocation = false
        manualLocation = null
        // Restart location updates
        startLocationUpdates()
    }

    private fun startLocationUpdates() {
        // This function is extracted from getLocation to be reusable
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
                if (isManualLocation) return // Don't update if in manual mode

                locationResult.lastLocation?.let { location ->
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
        } catch (e: SecurityException) {
            _locationState.value = LocationState.Error("Location permission denied")
        }
    }

    fun getLocation(): Flow<LocationState> {
        if (!isManualLocation) {
            startLocationUpdates()
        }
        return locationState
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
        locationCallback?.let { callback ->
            fusedLocationClient.removeLocationUpdates(callback)
            locationCallback = null
        }
    }
}