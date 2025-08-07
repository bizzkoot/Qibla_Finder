package com.bizzkoot.qiblafinder.sunCalibration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bizzkoot.qiblafinder.model.LocationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.util.*

/**
 * ViewModel for preparing sun position data for the UI.
 * Handles the state of sun position calculations and provides data to the UI.
 */
class SunPositionViewModel(
    locationRepository: LocationRepository
) : ViewModel() {
    
    private val sunPositionRepository = SunPositionRepository(locationRepository)
    
    private val _uiState = MutableStateFlow<SunPositionUiState>(SunPositionUiState.Loading)
    val uiState: StateFlow<SunPositionUiState> = _uiState
    
    init {
        observeSunPosition()
    }
    
    /**
     * Observes the sun position data and updates the UI state accordingly.
     */
    private fun observeSunPosition() {
        viewModelScope.launch {
            sunPositionRepository.getSunPosition()
                .catch { exception ->
                    _uiState.value = SunPositionUiState.Error("Failed to calculate sun position: ${exception.message}")
                }
                .collect { sunPositionData ->
                    if (sunPositionData != null) {
                        _uiState.value = SunPositionUiState.Available(
                            azimuth = sunPositionData.azimuth,
                            elevation = sunPositionData.elevation,
                            isVisible = sunPositionData.isVisible
                        )
                    } else {
                        _uiState.value = SunPositionUiState.Loading
                    }
                }
        }
    }
    
    /**
     * Forces a refresh of the sun position data.
     */
    fun refresh() {
        observeSunPosition()
    }
}

/**
 * Sealed interface representing the UI state for sun position data.
 */
sealed interface SunPositionUiState {
    /**
     * Initial state when data is being loaded.
     */
    object Loading : SunPositionUiState
    
    /**
     * State when sun position data is available.
     */
    data class Available(
        val azimuth: Double,
        val elevation: Double,
        val isVisible: Boolean
    ) : SunPositionUiState
    
    /**
     * State when there is an error in calculating sun position.
     */
    data class Error(val message: String) : SunPositionUiState
}