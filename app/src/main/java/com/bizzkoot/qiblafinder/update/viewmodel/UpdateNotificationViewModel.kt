package com.bizzkoot.qiblafinder.update.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bizzkoot.qiblafinder.update.models.UpdateInfo
import com.bizzkoot.qiblafinder.update.repositories.UpdateNotificationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

class UpdateNotificationViewModel(
    private val updateRepository: UpdateNotificationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(UpdateNotificationUiState())
    val uiState: StateFlow<UpdateNotificationUiState> = _uiState.asStateFlow()

    init {
        checkForUpdates()
    }

    fun checkForUpdates(forceCheck: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            try {
                val updateInfo = updateRepository.checkForUpdates(forceCheck)
                
                if (updateInfo != null && !updateRepository.isUpdateDismissed(updateInfo.newVersion)) {
                    _uiState.value = _uiState.value.copy(
                        updateInfo = updateInfo,
                        isLoading = false,
                        showNotification = true
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        showNotification = false
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Error checking for updates")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    fun dismissUpdate() {
        updateRepository.dismissUpdate()
        _uiState.value = _uiState.value.copy(
            showNotification = false,
            updateInfo = null
        )
    }

    fun downloadUpdate() {
        _uiState.value.updateInfo?.let { updateInfo ->
            // This will be handled by the UI to open the download URL
            _uiState.value = _uiState.value.copy(
                shouldDownload = true,
                downloadUrl = updateInfo.downloadUrl
            )
        }
    }

    fun onDownloadInitiated() {
        _uiState.value = _uiState.value.copy(shouldDownload = false)
    }
}

data class UpdateNotificationUiState(
    val isLoading: Boolean = false,
    val updateInfo: UpdateInfo? = null,
    val showNotification: Boolean = false,
    val shouldDownload: Boolean = false,
    val downloadUrl: String? = null,
    val error: String? = null
)
