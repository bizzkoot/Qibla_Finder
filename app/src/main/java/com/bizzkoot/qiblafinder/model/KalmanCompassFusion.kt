package com.bizzkoot.qiblafinder.model

import kotlin.math.abs

/**
 * Lightweight 1D Kalman-style filter that fuses gyroscope integration with
 * magnetometer/accelerometer heading measurements. This intentionally keeps the
 * state minimal (heading + error covariance) to avoid allocations while still
 * providing drift correction when fresh magnetic readings arrive.
 */
class KalmanCompassFusion(
    private val config: KalmanFusionConfig = KalmanFusionConfig()
) {

    private var headingDegrees: Float? = null
    private var errorVariance: Float = config.initialError

    fun hasEstimate(): Boolean = headingDegrees != null

    fun reset(initialHeading: Float? = null) {
        headingDegrees = initialHeading?.let { normalize(it) }
        errorVariance = config.initialError
    }

    /**
     * Predict step driven by gyroscope Z rate (degrees per second) and delta time.
     */
    fun predict(gyroRateDegPerSec: Float, deltaSeconds: Float) {
        val current = headingDegrees ?: return
        val predicted = normalize(current + gyroRateDegPerSec * deltaSeconds)
        headingDegrees = predicted
        errorVariance += config.processNoise + config.gyroNoise * abs(gyroRateDegPerSec) * deltaSeconds
    }

    /**
     * Correct step with a tilt-compensated magnetic heading measurement.
     */
    fun correct(measuredHeading: Float) {
        val measurement = normalize(measuredHeading)
        val current = headingDegrees
        if (current == null) {
            headingDegrees = measurement
            errorVariance = config.initialError
            return
        }

        val innovation = smallestAngleDifference(measurement, current)
        val kalmanGain = errorVariance / (errorVariance + config.measurementNoise)
        val updatedHeading = normalize(current + kalmanGain * innovation)
        headingDegrees = updatedHeading
        errorVariance = (1f - kalmanGain) * errorVariance + config.processNoiseFloor
    }

    fun getHeading(): Float? = headingDegrees

    private fun normalize(heading: Float): Float {
        var value = heading % 360f
        if (value < 0f) value += 360f
        return value
    }

    private fun smallestAngleDifference(target: Float, source: Float): Float {
        var diff = (target - source + 540f) % 360f - 180f
        if (diff > 180f) diff -= 360f
        if (diff < -180f) diff += 360f
        return diff
    }
}

data class KalmanFusionConfig(
    val processNoise: Float = 0.05f,
    val gyroNoise: Float = 0.005f,
    val measurementNoise: Float = 2.5f,
    val initialError: Float = 1.5f,
    val processNoiseFloor: Float = 0.01f
)

