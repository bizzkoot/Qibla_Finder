package com.bizzkoot.qiblafinder.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PermissionState(
    val locationGranted: Boolean = false,
    val cameraGranted: Boolean = false,
    val locationAccuracy: LocationAccuracy = LocationAccuracy.UNKNOWN
)

enum class LocationAccuracy {
    UNKNOWN,
    HIGH_ACCURACY,    // ACCESS_FINE_LOCATION granted
    APPROXIMATE,      // Only ACCESS_COARSE_LOCATION granted
    DENIED            // No location permission
}

class PermissionManager(private val context: Context) {
    
    private val _permissionState = MutableStateFlow(PermissionState())
    val permissionState: StateFlow<PermissionState> = _permissionState.asStateFlow()
    
    init {
        updatePermissionState()
    }
    
    fun updatePermissionState() {
        val locationFine = ContextCompat.checkSelfPermission(
            context, 
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        val locationCoarse = ContextCompat.checkSelfPermission(
            context, 
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        val camera = ContextCompat.checkSelfPermission(
            context, 
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        
        val locationAccuracy = when {
            locationFine -> LocationAccuracy.HIGH_ACCURACY
            locationCoarse -> LocationAccuracy.APPROXIMATE
            else -> LocationAccuracy.DENIED
        }
        
        _permissionState.value = PermissionState(
            locationGranted = locationFine || locationCoarse,
            cameraGranted = camera,
            locationAccuracy = locationAccuracy
        )
    }
    
    fun hasRequiredPermissions(): Boolean {
        val state = _permissionState.value
        return state.locationGranted && state.cameraGranted
    }
    
    fun getRequiredPermissions(): Array<String> {
        return arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA
        )
    }
} 