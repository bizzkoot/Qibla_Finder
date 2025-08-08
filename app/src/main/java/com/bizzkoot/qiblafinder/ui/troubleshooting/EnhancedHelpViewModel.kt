package com.bizzkoot.qiblafinder.ui.troubleshooting

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bizzkoot.qiblafinder.update.repositories.UpdateNotificationRepository
import com.bizzkoot.qiblafinder.update.services.EnhancedDownloadManager
import com.bizzkoot.qiblafinder.update.services.DownloadState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*

class EnhancedHelpViewModel(
    private val context: Context,
    private val updateRepository: UpdateNotificationRepository,
    private val downloadManager: EnhancedDownloadManager,
    private val onUpdateFound: (() -> Unit)? = null
) : ViewModel() {
    
    private val _updateCheckState = MutableStateFlow(UpdateCheckState())
    val updateCheckState: StateFlow<UpdateCheckState> = _updateCheckState.asStateFlow()
    
    // Download state for in-Help screen downloads
    val downloadState: StateFlow<DownloadState> = downloadManager.downloadState
    
    init {
        initializeState()
    }
    
    private fun initializeState() {
        val currentVersion = getCurrentVersion()
        _updateCheckState.value = _updateCheckState.value.copy(
            currentVersion = currentVersion,
            lastChecked = getLastCheckTime()
        )
    }
    
    private fun getCurrentVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "Unknown"
        } catch (e: Exception) {
            Timber.e(e, "Failed to get version name")
            "Unknown"
        }
    }
    
    fun checkForUpdates(forceCheck: Boolean = true) {
        viewModelScope.launch {
            _updateCheckState.value = _updateCheckState.value.copy(
                isChecking = true,
                error = null
            )
            
            try {
                val updateInfo = updateRepository.checkForUpdates(forceCheck)
                val currentTime = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date())
                
                if (updateInfo != null) {
                    _updateCheckState.value = _updateCheckState.value.copy(
                        isChecking = false,
                        hasUpdate = true,
                        newVersion = updateInfo.newVersion,
                        lastChecked = currentTime
                    )
                    // Trigger main screen download banner
                    onUpdateFound?.invoke()
                } else {
                    _updateCheckState.value = _updateCheckState.value.copy(
                        isChecking = false,
                        hasUpdate = false,
                        newVersion = null,
                        lastChecked = currentTime
                    )
                }
                
                saveLastCheckTime(currentTime)
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to check for updates")
                _updateCheckState.value = _updateCheckState.value.copy(
                    isChecking = false,
                    error = "Failed to check for updates: ${e.message}"
                )
            }
        }
    }
    
    private fun getLastCheckTime(): String? {
        // You can implement SharedPreferences storage here
        // For now, return null to indicate never checked
        return null
    }
    
    private fun saveLastCheckTime(time: String) {
        // Implement SharedPreferences storage here
        // For now, just log
        Timber.d("Last check time saved: $time")
    }
    
    // Download functionality for Help screen
    fun downloadUpdate() {
        viewModelScope.launch {
            try {
                val updateInfo = updateRepository.checkForUpdates(forceCheck = false)
                if (updateInfo != null) {
                    downloadManager.startDownload(
                        downloadUrl = updateInfo.downloadUrl,
                        fileName = "qibla-finder-${updateInfo.newVersion}.apk",
                        versionName = updateInfo.newVersion
                    )
                    Timber.d("Download started from Help screen")
                } else {
                    Timber.w("No update info available for download")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to start download from Help screen")
            }
        }
    }
    
    fun cancelDownload() {
        downloadManager.cancelDownload()
        Timber.d("Download cancelled from Help screen")
    }
    
    fun installUpdate() {
        val currentDownloadState = downloadState.value
        if (currentDownloadState is DownloadState.Completed) {
            downloadManager.installApk(currentDownloadState.fileUri)
            Timber.d("Install triggered from Help screen with fileUri: ${currentDownloadState.fileUri}")
        } else {
            Timber.w("Cannot install: download not completed. Current state: $currentDownloadState")
        }
    }
}
