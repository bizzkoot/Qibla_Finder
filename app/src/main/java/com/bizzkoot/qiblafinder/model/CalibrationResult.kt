package com.bizzkoot.qiblafinder.model

/**
 * Data class representing the result of a sun calibration.
 *
 * [errorOffset] is the correction (in degrees) that must be added to the
 * raw compass heading to align it with the true azimuth, normalized to
 * the range [-180, 180).
 */
data class CalibrationResult(
    val errorOffset: Double,
    val timestamp: Long
)
