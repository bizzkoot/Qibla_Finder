package com.bizzkoot.qiblafinder.ui.ar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bizzkoot.qiblafinder.model.GeodesyUtils
import com.bizzkoot.qiblafinder.model.LocationRepository
import com.bizzkoot.qiblafinder.model.OrientationState
import com.bizzkoot.qiblafinder.model.SensorRepository
import com.google.ar.core.Anchor
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import timber.log.Timber

class ARViewModel(
    application: Application,
    private val locationRepository: LocationRepository,
    private val sensorRepository: SensorRepository
) : AndroidViewModel(application) {
    private val _arCoreAvailability = MutableStateFlow<ArCoreApk.Availability?>(null)
    val arCoreAvailability = _arCoreAvailability.asStateFlow()

    private val _session = MutableStateFlow<Session?>(null)
    val session = _session.asStateFlow()

    private val _anchors = MutableStateFlow<List<Anchor>>(emptyList())
    val anchors = _anchors.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    
    private val _qiblaDirection = MutableStateFlow(0f)
    val qiblaDirection = _qiblaDirection.asStateFlow()
    
    private val _isAligned = MutableStateFlow(false)
    val isAligned = _isAligned.asStateFlow()
    
    private val _phoneTiltAngle = MutableStateFlow(0f)
    val phoneTiltAngle = _phoneTiltAngle.asStateFlow()
    
    private val _isPhoneFlat = MutableStateFlow(true)
    val isPhoneFlat = _isPhoneFlat.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                _arCoreAvailability.value = ArCoreApk.getInstance().checkAvailability(getApplication())
                Timber.d("ARCore availability checked: ${_arCoreAvailability.value}")
            } catch (e: Exception) {
                Timber.e(e, "Error checking ARCore availability")
                _error.value = "Failed to check ARCore availability: ${e.message}"
            }
        }
        
        // Start compass and location monitoring
        startCompassMonitoring()
    }
    
    private fun startCompassMonitoring() {
        viewModelScope.launch {
            combine(
                sensorRepository.getOrientationFlow(),
                locationRepository.getLocation()
            ) { orientationState, locationState ->
                when {
                    orientationState is OrientationState.Available && 
                    locationState is com.bizzkoot.qiblafinder.model.LocationState.Available -> {
                        
                        val trueHeading = orientationState.trueHeading
                        val location = locationState.location
                        
                        // Calculate Qibla direction
                        val qiblaBearing = GeodesyUtils.calculateQiblaBearing(
                            location.latitude,
                            location.longitude
                        )
                        
                        // Calculate the difference between current heading and Qibla direction
                        val directionDifference = (qiblaBearing - trueHeading + 360) % 360
                        
                        // Update state
                        _qiblaDirection.value = directionDifference.toFloat()
                        _phoneTiltAngle.value = orientationState.phoneTiltAngle
                        _isPhoneFlat.value = orientationState.isPhoneFlat
                        
                        // Debug flat detection
                        Timber.d("📱 AR Flat Detection - Tilt: ${orientationState.phoneTiltAngle}°, Flat: ${orientationState.isPhoneFlat}, Vertical: ${orientationState.isPhoneVertical}")
                        
                        // Check if aligned (within 5 degrees)
                        val isAligned = directionDifference <= 5 || directionDifference >= 355
                        _isAligned.value = isAligned
                        
                        Timber.d("🧭 AR Compass - Heading: ${trueHeading}°, Qibla: ${qiblaBearing}°, Difference: ${directionDifference}°, Aligned: $isAligned")
                    }
                    else -> {
                        Timber.d("⚠️ AR Compass - Waiting for orientation and location data")
                    }
                }
            }.collect { }
        }
    }

    fun createSession() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            try {
                // Close existing session if any
                _session.value?.close()
                
                val session = Session(getApplication())
                val config = Config(session).apply {
                    updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                    lightEstimationMode = Config.LightEstimationMode.AMBIENT_INTENSITY
                    planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                    focusMode = Config.FocusMode.AUTO
                }
                session.configure(config)
                _session.value = session
                Timber.d("AR Session created successfully")
            } catch (e: Exception) {
                Timber.e(e, "Failed to create AR session")
                _session.value = null
                _error.value = "Failed to create AR session: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun addAnchor(anchor: Anchor) {
        try {
            _anchors.value = _anchors.value + anchor
            Timber.d("Anchor added, total anchors: ${_anchors.value.size}")
        } catch (e: Exception) {
            Timber.e(e, "Error adding anchor")
        }
    }

    fun removeAnchor(anchor: Anchor) {
        try {
            _anchors.value = _anchors.value - anchor
            anchor.detach()
            Timber.d("Anchor removed, total anchors: ${_anchors.value.size}")
        } catch (e: Exception) {
            Timber.e(e, "Error removing anchor")
        }
    }

    fun clearAllAnchors() {
        try {
            _anchors.value.forEach { anchor ->
                anchor.detach()
            }
            _anchors.value = emptyList()
            Timber.d("All anchors cleared")
        } catch (e: Exception) {
            Timber.e(e, "Error clearing anchors")
        }
    }

    fun clearError() {
        _error.value = null
    }

    override fun onCleared() {
        super.onCleared()
        try {
            _session.value?.close()
            clearAllAnchors()
            Timber.d("ARViewModel cleared")
        } catch (e: Exception) {
            Timber.e(e, "Error clearing ARViewModel")
        }
    }
}