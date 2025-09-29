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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import timber.log.Timber

data class ManualLocationUiState(
    val selectedLocation: MapLocation? = null,
    val currentLocation: MapLocation? = null,
    val initialLocation: MapLocation? = null,
    val recenterTo: MapLocation? = null,
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
    val isMapTypeChanging: Boolean = false,
    val cacheLimitMb: Int = 60,
    // Search state
    val searchAvailable: Boolean = true,
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val searchResults: List<GeocodingResult> = emptyList(),
    val searchError: String? = null
)

class ManualLocationViewModel(
    private val locationRepository: LocationRepository,
    private val geocodingService: GeocodingService? = null,
    private val preferences: ManualLocationPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(ManualLocationUiState())
    val uiState: StateFlow<ManualLocationUiState> = _uiState.asStateFlow()
    
    // Performance optimizer for throttling calculations during drag operations
    private val performanceOptimizer = QiblaPerformanceOptimizer()
    
    // Progressive delay state for auto-refresh functionality
    private var panDebounceJob: Job? = null
    private var consecutivePans = 0
    private var lastPanTime = 0L
    
    // Progressive delay configuration constants
    private val baseDelay = 400L          // Initial delay (400ms)
    private val maxDelay = 800L           // Maximum delay cap (800ms)
    private val panResetThreshold = 2000L // Counter reset time (2 seconds)
    private val delayIncrement = 100L     // Delay increase per pan (100ms)

    /**
     * Calculates the progressive delay based on consecutive pan count.
     * Delay progression: 400ms → 500ms → 600ms → 700ms → 800ms (max)
     */
    private fun calculateProgressiveDelay(): Long {
        val cappedPans = consecutivePans.coerceAtMost(4)
        val increment = cappedPans * delayIncrement
        val calculatedDelay = baseDelay + increment
        val finalDelay = calculatedDelay.coerceAtMost(maxDelay)
        
        Timber.d("📍 Progressive delay calculation - Consecutive pans: $consecutivePans, Capped: $cappedPans, Increment: ${increment}ms, Final delay: ${finalDelay}ms")
        
        return finalDelay
    }

    init {
        Timber.d("📍 ManualLocationViewModel - Initializing ViewModel")
        val preferredMapType = preferences.getLastMapType()
        val cacheConfig = preferences.getCacheConfig()
        _uiState.value = _uiState.value.copy(
            selectedMapType = preferredMapType,
            cacheLimitMb = cacheConfig.limitMb
        )
        loadInitialLocation()
        // Initialize search availability
        val available = try {
            geocodingService?.isAvailable() ?: false
        } catch (_: Exception) { false }
        _uiState.value = _uiState.value.copy(searchAvailable = available)
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
                    initialLocation = _uiState.value.initialLocation ?: location,
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
                    initialLocation = _uiState.value.initialLocation ?: fallbackLocation,
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

    fun centerToInitialLocation() {
        val init = _uiState.value.initialLocation
        if (init != null) {
            _uiState.value = _uiState.value.copy(
                recenterTo = init,
                error = null
            )
            Timber.d("📍 Recenter request issued to: $init")
        } else {
            Timber.w("📍 No initial location snapshot available; attempting to reload initial location")
            refreshLocation()
        }
    }

    fun consumeRecenter() {
        _uiState.value = _uiState.value.copy(recenterTo = null)
    }

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun performSearch() {
        val service = geocodingService
        if (service == null || !service.isAvailable()) {
            _uiState.value = _uiState.value.copy(
                searchError = "Search unavailable on this device",
                isSearching = false,
                searchResults = emptyList()
            )
            return
        }

        val query = _uiState.value.searchQuery.trim()
        if (query.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                searchError = "Enter a place or address",
                searchResults = emptyList(),
                isSearching = false
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSearching = true, searchError = null, searchResults = emptyList())
            try {
                val center = _uiState.value.selectedLocation ?: _uiState.value.currentLocation
                val result = service.search(query = query, center = center, limit = 5)
                val list = result.getOrElse { emptyList() }
                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    searchResults = list,
                    searchError = if (list.isEmpty()) "No results found" else null
                )
            } catch (e: Exception) {
                Timber.e(e, "📍 Search failed")
                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    searchResults = emptyList(),
                    searchError = e.message ?: "Search failed"
                )
            }
        }
    }

    fun chooseSearchResult(result: GeocodingResult) {
        val loc = result.location
        _uiState.value = _uiState.value.copy(
            selectedLocation = loc,
            currentLocation = loc,
            searchResults = emptyList(),
            searchQuery = "",
            searchError = null
        )
        updateQiblaInfo(loc)
        Timber.d("📍 Search result chosen: ${loc}")
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
        // Increased delay for better state coordination during map type transitions.
        viewModelScope.launch {
            preferences.setLastMapType(mapType)
            kotlinx.coroutines.delay(400) // Allow cache reset and tile loading
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
        val newShowQiblaDirection = !currentState.showQiblaDirection
        
        _uiState.value = currentState.copy(
            showQiblaDirection = newShowQiblaDirection
        )
        
        // Cancel pending auto-refresh when hiding Qibla direction
        if (!newShowQiblaDirection) {
            panDebounceJob?.cancel()
            Timber.d("📍 Auto-refresh cancelled - Qibla direction hidden")
        }
        
        Timber.d("📍 Qibla direction visibility toggled to: $newShowQiblaDirection")
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
            // Cancel pending auto-refresh and reset progressive state on manual redraw
            panDebounceJob?.cancel()
            consecutivePans = 0
            
            updateQiblaInfo(location)
            _uiState.value = _uiState.value.copy(
                qiblaLineVisible = true,
                needsQiblaRedraw = false,
                error = null
            )
            
            Timber.d("📍 Qibla line manually redrawn - Progressive delay counter reset")
        }
    }
    
    /**
     * Performs automatic Qibla line redraw triggered by progressive auto-refresh.
     * Reuses existing redraw logic with specific logging for auto-triggered redraws.
     */
    private fun autoRedrawQiblaLine() {
        _uiState.value.selectedLocation?.let { location ->
            updateQiblaInfo(location)
            _uiState.value = _uiState.value.copy(
                qiblaLineVisible = true,
                needsQiblaRedraw = false,
                error = null
            )
            Timber.d("📍 Qibla line automatically redrawn")
        }
    }
    
    /**
     * Handles pan update events during high digital zoom scenarios.
     * Provides optimized update handling for enhanced arrow positioning.
     *
     * @param cumulativeDistance The cumulative pan distance since drag start
     */
    fun onPanUpdateAtHighZoom(cumulativeDistance: Float) {
        // Use optimized calculation for high zoom scenarios
        _uiState.value.selectedLocation?.let { location ->
            performanceOptimizer.throttleCalculation(viewModelScope, minDelayMs = 8L) { // 120fps cap
                updateQiblaInfo(location)
            }
        }
        
        Timber.d("📍 High zoom pan update - Cumulative distance: ${cumulativeDistance}px")
    }
    
    /**
     * Configures the ViewModel for high zoom mode operations.
     * Optimizes performance settings based on digital zoom factor.
     *
     * @param digitalZoomFactor The current digital zoom level
     */
    fun configureHighZoomMode(digitalZoomFactor: Float) {
        // Enable high-performance mode for digital zoom > 2x
        if (digitalZoomFactor > 2f) {
            // Reduce throttling for more responsive updates during high zoom
            performanceOptimizer.configureForHighZoom()
        } else {
            // Reset to normal performance mode
            performanceOptimizer.configureForNormalZoom()
        }
        
        Timber.d("📍 High zoom mode configured - Digital zoom: ${digitalZoomFactor}x")
    }
    
    /**
     * Resets accumulated pan tracking state.
     * Called when starting new drag sessions or when significant updates occur.
     */
    fun resetPanAccumulation() {
        // Reset performance optimizer state
        performanceOptimizer.reset()
        
        // Cancel any pending delayed operations
        panDebounceJob?.cancel()
        panDebounceJob = null
        
        Timber.d("📍 Pan accumulation state reset")
    }

    /**
     * Handles pan stop events for progressive auto-refresh functionality.
     * Implements adaptive delay based on consecutive pan frequency.
     * Enhanced to handle enhanced pan state cleanup.
     */
    fun onPanStop() {
        try {
            val currentTime = System.currentTimeMillis()
            
            // Validate current time to prevent issues with system clock changes
            if (currentTime < lastPanTime) {
                Timber.w("📍 System clock moved backwards - Resetting pan timing state")
                consecutivePans = 0
                lastPanTime = currentTime
                return
            }
            
            // Reset counter if user hasn't panned recently (beyond threshold)
            val timeSinceLastPan = currentTime - lastPanTime
            if (timeSinceLastPan > panResetThreshold) {
                val previousPans = consecutivePans
                consecutivePans = 0
                Timber.d("📍 Progressive delay counter reset after inactivity - Time since last pan: ${timeSinceLastPan}ms, Previous count: $previousPans")
            }
        
        consecutivePans++
        lastPanTime = currentTime
        
        // Enhanced pan state cleanup
        resetPanAccumulation()
        
        // Calculate progressive delay based on consecutive pans
        val progressiveDelay = calculateProgressiveDelay()
        
        Timber.d("📍 Pan stop detected - Consecutive pans: $consecutivePans, Delay: ${progressiveDelay}ms")
        
        // Cancel previous auto-refresh job and schedule new one
        val hadPreviousJob = panDebounceJob != null
        panDebounceJob?.cancel()
        if (hadPreviousJob) {
            Timber.d("📍 Previous auto-refresh job cancelled for new pan activity")
        }
        
        panDebounceJob = viewModelScope.launch {
            try {
                delay(progressiveDelay)
                
                // Check if coroutine is still active and Qibla direction should be shown
                if (isActive && uiState.value.showQiblaDirection) {
                    autoRedrawQiblaLine()
                    Timber.d("📍 Auto-refresh executed after ${progressiveDelay}ms delay")
                } else {
                    val reason = when {
                        !isActive -> "coroutine inactive"
                        !uiState.value.showQiblaDirection -> "Qibla direction hidden"
                        else -> "unknown condition"
                    }
                    Timber.d("📍 Auto-refresh skipped - Reason: $reason")
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                Timber.d("📍 Auto-refresh cancelled due to new pan activity")
                // Normal cancellation - no error handling needed
            } catch (e: Exception) {
                Timber.e(e, "📍 Error in progressive auto-refresh - Exception: ${e.javaClass.simpleName}, Message: ${e.message}")
                // Reset state on unexpected errors to prevent stuck state
                try {
                    consecutivePans = 0
                    lastPanTime = 0L
                    panDebounceJob = null
                    Timber.d("📍 Progressive delay state reset due to error recovery")
                } catch (resetError: Exception) {
                    Timber.e(resetError, "📍 Critical error during state reset")
                }
            }
        }
        } catch (e: Exception) {
            Timber.e(e, "📍 Critical error in onPanStop - Resetting progressive delay state")
            // Emergency state reset
            try {
                consecutivePans = 0
                lastPanTime = 0L
                panDebounceJob?.cancel()
                panDebounceJob = null
            } catch (resetError: Exception) {
                Timber.e(resetError, "📍 Failed to reset state during error recovery")
            }
        }
    }
    
    /**
     * Clean up resources when the ViewModel is destroyed
     */
    override fun onCleared() {
        super.onCleared()
        try {
            // Cancel any pending auto-refresh jobs to prevent memory leaks
            panDebounceJob?.cancel()
            panDebounceJob = null
            Timber.d("📍 ManualLocationViewModel - Cleanup completed, auto-refresh job cancelled")
        } catch (e: Exception) {
            Timber.e(e, "📍 Error during ViewModel cleanup")
        }
    }
} 
