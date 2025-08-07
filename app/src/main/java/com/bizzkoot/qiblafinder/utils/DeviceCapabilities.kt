package com.bizzkoot.qiblafinder.utils

/**
 * Data class representing device capabilities
 */
data class DeviceCapabilities(
    val memoryClass: Int,
    val sdkVersion: Int,
    val isHighEndDevice: Boolean,
    val isMidRangeDevice: Boolean,
    val isLowEndDevice: Boolean
)