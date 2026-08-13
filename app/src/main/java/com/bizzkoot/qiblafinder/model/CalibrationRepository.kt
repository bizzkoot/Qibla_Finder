package com.bizzkoot.qiblafinder.model

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Repository for the sun calibration offset.
 *
 * Single source of truth for [CalibrationResult]: the value is persisted to
 * the same SharedPreferences file ("sun_calibration") and keys
 * ("calibration_offset"/"calibration_timestamp") used historically by the sun
 * calibration feature, and exposed as a [StateFlow] so the compass can react
 * to calibration changes immediately (including ones made on the Sun
 * Calibration screen while the compass is still alive on the back stack).
 */
class CalibrationRepository(context: Context) {

    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        "sun_calibration",
        Context.MODE_PRIVATE
    )

    private val _calibrationResult = MutableStateFlow(readStoredCalibrationResult())
    val calibrationResult: StateFlow<CalibrationResult?> = _calibrationResult.asStateFlow()

    /**
     * Persists [result] and publishes it on [calibrationResult].
     */
    fun store(result: CalibrationResult) {
        sharedPreferences.edit {
            putFloat("calibration_offset", result.errorOffset.toFloat())
            putLong("calibration_timestamp", result.timestamp)
        }
        _calibrationResult.value = result
    }

    /**
     * Clears the stored calibration: removes the persisted values and sets
     * [calibrationResult] to null.
     */
    fun clear() {
        sharedPreferences.edit {
            remove("calibration_offset")
            remove("calibration_timestamp")
        }
        _calibrationResult.value = null
    }

    /**
     * Returns the current calibration offset to apply to compass readings,
     * or 0.0 when no calibration has been stored.
     */
    fun getCurrentOffset(): Double = _calibrationResult.value?.errorOffset ?: 0.0

    private fun readStoredCalibrationResult(): CalibrationResult? {
        val offset = sharedPreferences.getFloat("calibration_offset", 0f)
        val timestamp = sharedPreferences.getLong("calibration_timestamp", 0L)
        return if (timestamp > 0) {
            CalibrationResult(
                errorOffset = offset.toDouble(),
                timestamp = timestamp
            )
        } else {
            null
        }
    }
}
