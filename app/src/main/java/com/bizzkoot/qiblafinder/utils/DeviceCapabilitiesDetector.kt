package com.bizzkoot.qiblafinder.utils

import android.content.Context
import timber.log.Timber

data class DeviceCapabilities(
    val isHighEndDevice: Boolean,
    val isMidRangeDevice: Boolean,
    val isLowEndDevice: Boolean,
    val hasWebpLossySupport: Boolean,
    val availableMemory: Long,
    val isHuaweiDevice: Boolean
)

object DeviceCapabilitiesDetector {
    private var deviceCapabilities: DeviceCapabilities? = null

    fun getDeviceCapabilities(context: Context): DeviceCapabilities {
        if (deviceCapabilities != null) {
            return deviceCapabilities!!
        }

        val runtime = Runtime.getRuntime()
        val availableMemory = runtime.maxMemory()
        val isHuaweiDevice = isHuaweiDevice()
        val hasWebpLossySupport = CompressionFormatDetector.isWebpLossySupported()

        val deviceTier = when {
            availableMemory >= 4L * 1024 * 1024 * 1024 -> "high" // 4GB+
            availableMemory >= 2L * 1024 * 1024 * 1024 -> "mid"  // 2GB+
            else -> "low"
        }

        deviceCapabilities = DeviceCapabilities(
            isHighEndDevice = deviceTier == "high",
            isMidRangeDevice = deviceTier == "mid",
            isLowEndDevice = deviceTier == "low",
            hasWebpLossySupport = hasWebpLossySupport,
            availableMemory = availableMemory,
            isHuaweiDevice = isHuaweiDevice
        )

        Timber.d("Device capabilities detected: $deviceCapabilities")
        return deviceCapabilities!!
    }

    private fun isHuaweiDevice(): Boolean {
        return try {
            val manufacturer = android.os.Build.MANUFACTURER.lowercase()
            val brand = android.os.Build.BRAND.lowercase()
            manufacturer.contains("huawei") || brand.contains("huawei")
        } catch (e: Exception) {
            Timber.w(e, "Error detecting Huawei device")
            false
        }
    }
}
