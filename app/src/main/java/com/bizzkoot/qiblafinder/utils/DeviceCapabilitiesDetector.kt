package com.bizzkoot.qiblafinder.utils

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import timber.log.Timber

/**
 * Utility class to detect device capabilities and adjust app behavior accordingly.
 */
object DeviceCapabilitiesDetector {
    private var memoryClass: Int = -1
    private var isInitialized = false
    private lateinit var deviceCapabilities: DeviceCapabilities

    /**
     * Initialize the detector with application context.
     * Should be called once from Application class.
     */
    fun initialize(context: Context) {
        if (isInitialized) return

        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        memoryClass = activityManager.memoryClass
        
        val isHighEnd = memoryClass >= 256 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val isMidRange = memoryClass >= 128 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        
        deviceCapabilities = DeviceCapabilities(
            memoryClass = memoryClass,
            sdkVersion = Build.VERSION.SDK_INT,
            isHighEndDevice = isHighEnd,
            isMidRangeDevice = isMidRange,
            isLowEndDevice = !isHighEnd && !isMidRange
        )
        
        isInitialized = true
        
        Timber.d("📱 Device capabilities: Memory Class: ${memoryClass}MB, SDK: ${Build.VERSION.SDK_INT}")
        Timber.d("📱 Device tier: High-end: $isHighEnd, Mid-range: $isMidRange, Low-end: ${!isHighEnd && !isMidRange}")
        Timber.d("📱 Max digital zoom factor: ${if (isHighEnd) "5.0f" else if (isMidRange) "3.0f" else "2.5f"}")
    }

    /**
     * Get device capabilities. Must be called after initialization.
     */
    fun getDeviceCapabilities(context: Context): DeviceCapabilities {
        if (!isInitialized) {
            initialize(context)
        }
        return deviceCapabilities
    }

    /**
     * Check if the device is considered high-end based on memory and SDK version
     */
    fun isHighEndDevice(): Boolean {
        checkInitialization()
        return deviceCapabilities.isHighEndDevice
    }

    /**
     * Check if the device is considered mid-range based on memory and SDK version
     */
    fun isMidRangeDevice(): Boolean {
        checkInitialization()
        return deviceCapabilities.isMidRangeDevice
    }

    /**
     * Check if the device is considered low-end based on memory and SDK version
     */
    fun isLowEndDevice(): Boolean {
        checkInitialization()
        return deviceCapabilities.isLowEndDevice
    }

    private fun checkInitialization() {
        if (!isInitialized) {
            Timber.e("DeviceCapabilitiesDetector not initialized! Call initialize() first.")
            throw IllegalStateException("DeviceCapabilitiesDetector not initialized")
        }
    }

    /**
     * Gets device tier for performance optimization
     */
    fun getDeviceTier(): com.bizzkoot.qiblafinder.ui.location.QiblaPerformanceMonitor.DeviceTier {
        checkInitialization()
        return when {
            deviceCapabilities.isHighEndDevice -> com.bizzkoot.qiblafinder.ui.location.QiblaPerformanceMonitor.DeviceTier.HIGH_END
            deviceCapabilities.isMidRangeDevice -> com.bizzkoot.qiblafinder.ui.location.QiblaPerformanceMonitor.DeviceTier.MID_RANGE
            else -> com.bizzkoot.qiblafinder.ui.location.QiblaPerformanceMonitor.DeviceTier.LOW_END
        }
    }

    /**
     * Gets maximum digital zoom factor based on device capabilities
     */
    fun getMaxDigitalZoomFactor(): Float {
        checkInitialization()
        return when {
            deviceCapabilities.isHighEndDevice -> 5.0f
            deviceCapabilities.isMidRangeDevice -> 3.0f
            else -> 2.5f
        }
    }

    /**
     * Gets optimal update frequency for the device
     */
    fun getOptimalUpdateFrequency(digitalZoom: Float): Long {
        checkInitialization()
        val baseFrequency = when {
            deviceCapabilities.isHighEndDevice -> 16L // 60fps
            deviceCapabilities.isMidRangeDevice -> 33L // 30fps
            else -> 50L // 20fps
        }
        
        // Adjust based on zoom level
        val zoomMultiplier = if (digitalZoom > 2f) 1.5f else 1.0f
        return (baseFrequency * zoomMultiplier).toLong().coerceAtMost(100L)
    }
}

