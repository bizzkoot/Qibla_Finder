package com.bizzkoot.qiblafinder.update.repositories

import android.content.Context
import android.content.SharedPreferences
import com.bizzkoot.qiblafinder.update.models.UpdateInfo
import com.bizzkoot.qiblafinder.update.services.VersionChecker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

class UpdateNotificationRepository(
    private val context: Context,
    private val versionChecker: VersionChecker
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "update_notifications", Context.MODE_PRIVATE
    )

    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo: Flow<UpdateInfo?> = _updateInfo.asStateFlow()

    suspend fun checkForUpdates(forceCheck: Boolean = false): UpdateInfo? {
        // Check if we should skip the check (rate limiting)
        if (!forceCheck && shouldSkipCheck()) {
            Timber.d("Skipping update check due to rate limiting")
            return null
        }

        val updateInfo = versionChecker.checkForUpdates()
        
        if (updateInfo != null) {
            _updateInfo.value = updateInfo
            // Save the last check time
            prefs.edit().putLong("last_check_time", System.currentTimeMillis()).apply()
        }

        return updateInfo
    }

    fun dismissUpdate() {
        val dismissedUpdateInfo = _updateInfo.value
        _updateInfo.value = null
        // Mark this version as dismissed
        dismissedUpdateInfo?.let { updateInfo ->
            prefs.edit().putString("dismissed_version", updateInfo.newVersion).apply()
        }
    }

    fun isUpdateDismissed(version: String): Boolean {
        return prefs.getString("dismissed_version", "") == version
    }

    private fun shouldSkipCheck(): Boolean {
        val lastCheckTime = prefs.getLong("last_check_time", 0)
        val currentTime = System.currentTimeMillis()
        val timeSinceLastCheck = currentTime - lastCheckTime
        
        // Check once per day maximum
        return timeSinceLastCheck < 24 * 60 * 60 * 1000
    }

    fun clearDismissedVersion() {
        prefs.edit().remove("dismissed_version").apply()
    }
}
