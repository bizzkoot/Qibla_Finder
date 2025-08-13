package com.bizzkoot.qiblafinder.ui.location

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bizzkoot.qiblafinder.model.GeodesyUtils
import com.bizzkoot.qiblafinder.model.LocationRepository
import com.bizzkoot.qiblafinder.model.LocationState
import com.bizzkoot.qiblafinder.ui.location.QiblaPerformanceOptimizer
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
    val tileCount: Int = 0,
    val cacheSizeMB: Double = 0.0,
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedMapType: MapType = MapType.STREET, // New field
    val availableMapTypes: List<MapType> = listOf(MapType.STREET, MapType.SATELLITE),
    val qiblaBearing: Double = 0.0,
    val distanceToKaaba: Double = 0.0,
    val showQiblaDirection: Boolean = true,
    val qiblaLineVisible: Boolean = true,
    val needsQiblaRedraw: Boolean = false,
    val panelHeight: Int = 0, // New field for dynamic positioning
    val lastMapType: MapType? = null,
    val isMapTypeChanging: Boolean = false
)

class ManualLocationViewModel(
    private val locationRepository: LocationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ManualLocationUiState())
    val uiState: StateFlow<ManualLocationUiState> = _uiState.asStateFlow()
    
    // Performance optimizer for throttling calculations during drag operations
    private val performanceOptimizer = QiblaPerformanceOptimizer()

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
                updateQiblaInfo(location)
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
                updateQiblaInfo(fallbackLocation)
                Timber.w("📍 Could not get GPS location within 5s, using fallback.")
            }
        }
    }

    fun updateSelectedLocation(location: MapLocation) {
        _uiState.value = _uiState.value.copy(
            selectedLocation = location,
            error = null
        )
        
        // Use throttled calculation to prevent excessive computation during drag operations
        performanceOptimizer.throttleCalculation(viewModelScope) {
            updateQiblaInfo(location)
        }
        
        Timber.d("📍 Location updated: $location")
    }

    fun updateAccuracy(accuracy: Int) {
        _uiState.value = _uiState.value.copy(accuracyInMeters = accuracy)
    }

    fun updateTileInfo(tileCount: Int, cacheSizeMB: Double) {
        _uiState.value = _uiState.value.copy(tileCount = tileCount, cacheSizeMB = cacheSizeMB)
    }

    fun updatePanelHeight(heightInPx: Int) {
        _uiState.value = _uiState.value.copy(panelHeight = heightInPx)
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

    fun updateMapType(mapType: MapType) {
        val currentState = _uiState.value
        _uiState.value = currentState.copy(
            selectedMapType = mapType,
            isLoading = true, // To show loading indicator while tiles are switching
            error = null,
            lastMapType = currentState.selectedMapType,
            isMapTypeChanging = true
        )

        // The view will react to the state change and reload the tiles.
        // We can add a small delay to allow the UI to update.
        viewModelScope.launch {
            kotlinx.coroutines.delay(100) // Allow cache reset and tile loading
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                isMapTypeChanging = false
            )
            Timber.d("📍 Map type changed to: ${mapType.displayName}")
        }
    }

    /**
     * Toggles the visibility of the Qibla direction line.
     */
    fun toggleQiblaDirection() {
        val currentState = _uiState.value
        _uiState.value = currentState.copy(
            showQiblaDirection = !currentState.showQiblaDirection
        )
        Timber.d("📍 Qibla direction visibility toggled to: ${_uiState.value.showQiblaDirection}")
    }

    /**
     * Updates Qibla bearing and distance information for the given location.
     * Enhanced with comprehensive error handling and user feedback.
     *
     * @param location The location to calculate Qibla information for
     */
    private fun updateQiblaInfo(location: MapLocation) {
        try {
            // Use safe calculation methods with error handling
            val bearingResult = GeodesyUtils.calculateQiblaBearingSafe(location.latitude, location.longitude)
            val distanceResult = GeodesyUtils.calculateDistanceToKaabaSafe(location.latitude, location.longitude)
            
            when {
                bearingResult is com.bizzkoot.qiblafinder.model.GeodesyResult.Success && 
                distanceResult is com.bizzkoot.qiblafinder.model.GeodesyResult.Success -> {
                    // Both calculations successful
                    _uiState.value = _uiState.value.copy(
                        qiblaBearing = bearingResult.data,
                        distanceToKaaba = distanceResult.data,
                        error = null // Clear any previous errors
                    )
                    
                    Timber.d("📍 Qibla info updated - Bearing: %.2f°, Distance: %.2f km", 
                        bearingResult.data, distanceResult.data)
                }
                
                bearingResult is com.bizzkoot.qiblafinder.model.GeodesyResult.Error -> {
                    // Bearing calculation failed
                    Timber.w("📍 Bearing calculation failed: ${bearingResult.message}")
                    _uiState.value = _uiState.value.copy(
                        error = "Unable to calculate Qibla direction: ${bearingResult.message}"
                    )
                }
                
                distanceResult is com.bizzkoot.qiblafinder.model.GeodesyResult.Error -> {
                    // Distance calculation failed
                    Timber.w("📍 Distance calculation failed: ${distanceResult.message}")
                    _uiState.value = _uiState.value.copy(
                        error = "Unable to calculate distance to Kaaba: ${distanceResult.message}"
                    )
                }
            }
            
        } catch (e: Exception) {
            Timber.e(e, "📍 Unexpected error calculating Qibla information")
            _uiState.value = _uiState.value.copy(
                error = "Unexpected error calculating Qibla information: ${e.message}"
            )
        }
    }
    
    /**
     * Marks that the Qibla line needs to be redrawn (e.g., after fast panning)
     */
    fun markQiblaLineNeedsRedraw() {
        _uiState.value = _uiState.value.copy(
            qiblaLineVisible = false,
            needsQiblaRedraw = true
        )
        Timber.d("📍 Qibla line marked for redraw")
    }
    
    /**
     * Triggers a manual redraw of the Qibla direction line
     */
    fun redrawQiblaLine() {
        _uiState.value.selectedLocation?.let { location ->
            updateQiblaInfo(location)
            _uiState.value = _uiState.value.copy(
                qiblaLineVisible = true,
                needsQiblaRedraw = false,
                error = null
            )
            Timber.d("📍 Qibla line manually redrawn")
        }
    }
} 