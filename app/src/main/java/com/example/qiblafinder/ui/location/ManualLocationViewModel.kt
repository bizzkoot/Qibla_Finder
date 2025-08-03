package com.bizzkoot.qiblafinder.ui.location

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bizzkoot.qiblafinder.model.LocationRepository
import com.bizzkoot.qiblafinder.model.LocationState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

data class ManualLocationUiState(
    val selectedLocation: MapLocation? = null,
    val currentLocation: MapLocation? = null,
    val accuracyInMeters: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null
)

class ManualLocationViewModel(
    private val locationRepository: LocationRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ManualLocationUiState())
    val uiState: StateFlow<ManualLocationUiState> = _uiState.asStateFlow()
    
    init {
        Timber.d("📍 ManualLocationViewModel - Initializing ViewModel")
        loadInitialLocation()
    }
    
    private fun loadInitialLocation() {
        viewModelScope.launch {
            Timber.d("📍 ManualLocationViewModel - Starting loadInitialLocation")
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            // Fetch location only ONCE with a timeout
            val locationState = withTimeoutOrNull(5000) { // 5-second timeout
                locationRepository.getLocation().first { it is LocationState.Available }
            }
            
            if (locationState is LocationState.Available) {
                val location = MapLocation(
                    latitude = locationState.location.latitude,
                    longitude = locationState.location.longitude
                )
                _uiState.value = _uiState.value.copy(
                    currentLocation = location,
                    selectedLocation = location,
                    isLoading = false,
                    error = null
                )
                Timber.d("📍 Initial location loaded successfully: $location")
            } else {
                // Use fallback location on timeout or error
                val fallbackLocation = MapLocation(3.1390, 101.6869) // Kuala Lumpur
                _uiState.value = _uiState.value.copy(
                    currentLocation = fallbackLocation,
                    selectedLocation = fallbackLocation,
                    isLoading = false,
                    error = "Could not get GPS location. Using fallback (Kuala Lumpur)."
                )
                Timber.w("📍 Could not get GPS location within 5s, using fallback.")
            }
        }
    }
    
    fun updateSelectedLocation(location: MapLocation) {
        _uiState.value = _uiState.value.copy(
            selectedLocation = location,
            error = null
        )
        Timber.d("📍 Location updated: $location")
    }

    fun updateAccuracy(accuracy: Int) {
        _uiState.value = _uiState.value.copy(accuracyInMeters = accuracy)
    }
    
    fun confirmLocation(): MapLocation? {
        return _uiState.value.selectedLocation?.also { mapLocation ->
            val location = android.location.Location("manual").apply {
                latitude = mapLocation.latitude
                longitude = mapLocation.longitude
            }
            locationRepository.setManualLocation(location)
            Timber.d("📍 Location confirmed: $mapLocation")
        }
    }
    
    fun refreshLocation() {
        Timber.d("📍 ManualLocationViewModel - Refreshing location")
        loadInitialLocation()
    }
} 