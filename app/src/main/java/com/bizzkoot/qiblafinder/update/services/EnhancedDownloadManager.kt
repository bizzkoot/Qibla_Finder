package com.bizzkoot.qiblafinder.update.services

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File

class EnhancedDownloadManager(private val context: Context) {
    
    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    
    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()
    
    // Coroutine scope for background operations
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private var currentDownloadId: Long = -1
    private var downloadReceiver: BroadcastReceiver? = null
    
    fun startDownload(
        downloadUrl: String,
        fileName: String,
        versionName: String
    ): Long {
        try {
            // Clean up any existing download receiver
            cleanup()
            
            val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
                setTitle("Qibla Finder Update")
                setDescription("Downloading version $versionName...")
                // Use VISIBILITY_VISIBLE instead of VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                // This shows progress but doesn't persist completion notification
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                setAllowedNetworkTypes(
                    DownloadManager.Request.NETWORK_WIFI or 
                    DownloadManager.Request.NETWORK_MOBILE
                )
                setAllowedOverRoaming(false)
                setAllowedOverMetered(true)
            }
            
            currentDownloadId = downloadManager.enqueue(request)
            _downloadState.value = DownloadState.Downloading(0, 0)
            
            setupDownloadReceiver()
            startProgressMonitoring()
            
            Timber.i("Download started with ID: $currentDownloadId")
            return currentDownloadId
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to start download")
            _downloadState.value = DownloadState.Error("Failed to start download: ${e.message}")
            return -1
        }
    }
    
    private fun setupDownloadReceiver() {
        downloadReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    DownloadManager.ACTION_DOWNLOAD_COMPLETE -> {
                        val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                        if (downloadId == currentDownloadId) {
                            // Although updateProgress can now handle completion,
                            // the receiver is faster and remains the primary mechanism.
                            handleDownloadComplete(downloadId)
                        }
                    }
                    DownloadManager.ACTION_NOTIFICATION_CLICKED -> {
                        // Handle notification click if needed
                        Timber.d("Download notification clicked")
                    }
                }
            }
        }
        
        val filter = IntentFilter().apply {
            addAction(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            addAction(DownloadManager.ACTION_NOTIFICATION_CLICKED)
        }
        
        // Android 14+ (API 34+) requires explicit export specification for dynamic receivers
        ContextCompat.registerReceiver(
            context, 
            downloadReceiver, 
            filter, 
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }
    
    private fun handleDownloadComplete(downloadId: Long) {
        val query = DownloadManager.Query().setFilterById(downloadId)
        downloadManager.query(query)?.use { cursor -> // Use .use for automatic closing
            if (cursor.moveToFirst()) {
                val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                if (statusIndex < 0) {
                    _downloadState.value = DownloadState.Error("Download completed but status column not found")
                    Timber.e("Download completed but status column not found")
                    return
                }

                val status = cursor.getInt(statusIndex)
                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        val uriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                        if (uriIndex >= 0) {
                            val localUri = cursor.getString(uriIndex)
                            _downloadState.value = DownloadState.Completed(localUri)
                            Timber.i("Download completed successfully: $localUri")
                        } else {
                            _downloadState.value = DownloadState.Error("Download completed but URI not found")
                            Timber.e("Download completed but URI column not found")
                        }
                    }
                    DownloadManager.STATUS_FAILED -> {
                        val reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                        val reason = if (reasonIndex >= 0) cursor.getInt(reasonIndex) else -1
                        val errorMessage = getDownloadErrorMessage(reason)
                        _downloadState.value = DownloadState.Error(errorMessage)
                        Timber.e("Download failed: $errorMessage")
                    }
                }
            }
        }
    }
    
    private fun startProgressMonitoring() {
        coroutineScope.launch {
            // Loop as long as the state is Downloading. It will exit when the state
            // becomes Completed, Error, or Cancelled.
            while (_downloadState.value is DownloadState.Downloading) {
                try {
                    updateProgress()
                    delay(1000) // Update every second
                } catch (e: Exception) {
                    Timber.w(e, "Error in progress monitoring loop")
                }
            }
        }
    }
    
    /**
     * [UPDATED] This function now checks for all relevant download statuses.
     * This provides a reliable fallback to the BroadcastReceiver, ensuring the
     * UI state is always updated correctly upon download completion or failure.
     */
    private fun updateProgress() {
        val query = DownloadManager.Query().setFilterById(currentDownloadId)
        downloadManager.query(query)?.use { cursor -> // Use .use for automatic closing
            if (cursor.moveToFirst()) {
                val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                if (statusIndex < 0) return // Cannot get status, exit

                val status = cursor.getInt(statusIndex)
                when (status) {
                    DownloadManager.STATUS_RUNNING -> {
                        val bytesDownloadedIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                        val bytesTotalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)

                        if (bytesDownloadedIndex >= 0 && bytesTotalIndex >= 0) {
                            val bytesDownloaded = cursor.getLong(bytesDownloadedIndex)
                            val bytesTotal = cursor.getLong(bytesTotalIndex)
                            if (bytesTotal > 0) {
                                _downloadState.value = DownloadState.Downloading(bytesDownloaded, bytesTotal)
                            }
                        }
                    }
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        // The download is complete. Update progress to 100% and set state to Completed.
                        // This acts as a fallback if the BroadcastReceiver is missed.
                        val bytesTotalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                        val totalBytes = if(bytesTotalIndex >= 0) cursor.getLong(bytesTotalIndex) else 0L
                        _downloadState.value = DownloadState.Downloading(totalBytes, totalBytes)

                        // Now, call the main completion handler to finalize the state.
                        handleDownloadComplete(currentDownloadId)
                    }
                    DownloadManager.STATUS_FAILED -> {
                        // The download failed. Call the main completion handler to set the error state.
                        // This also acts as a fallback.
                        handleDownloadComplete(currentDownloadId)
                    }
                }
            }
        }
    }
    
    fun cancelDownload() {
        if (currentDownloadId != -1L) {
            downloadManager.remove(currentDownloadId)
            _downloadState.value = DownloadState.Cancelled
            cleanup()
        }
    }
    
    fun installApk(fileUri: String) {
        try {
            Timber.d("installApk() called with fileUri: $fileUri")
            val file = File(Uri.parse(fileUri).path ?: run {
                Timber.e("FileUri path is null: $fileUri")
                _downloadState.value = DownloadState.Error("Invalid file URI")
                return
            })
            
            Timber.d("File path resolved to: ${file.absolutePath}")
            Timber.d("File exists: ${file.exists()}")
            if (!file.exists()) {
                Timber.e("APK file not found at: ${file.absolutePath}")
                _downloadState.value = DownloadState.Error("APK file not found at ${file.absolutePath}")
                return
            }
            
            Timber.d("File size: ${file.length()} bytes")
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            Timber.d("FileProvider URI created: $apkUri")
            
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            
            Timber.d("Starting install intent with URI: $apkUri")
            context.startActivity(installIntent)
            Timber.i("Installation intent started successfully")
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to install APK: ${e.message}")
            Timber.e(e, "Full stack trace:", e)
            _downloadState.value = DownloadState.Error("Failed to install: ${e.message}")
        }
    }
    
    private fun cleanup() {
        downloadReceiver?.let { receiver ->
            try {
                context.unregisterReceiver(receiver)
            } catch (e: IllegalArgumentException) {
                Timber.w(e, "Receiver not registered, skipping unregister.")
            }
        }
        downloadReceiver = null
    }
    
    private fun getDownloadErrorMessage(reason: Int): String {
        return when (reason) {
            DownloadManager.ERROR_CANNOT_RESUME -> "Cannot resume download"
            DownloadManager.ERROR_DEVICE_NOT_FOUND -> "Storage device not found"
            DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "File already exists"
            DownloadManager.ERROR_FILE_ERROR -> "File error occurred"
            DownloadManager.ERROR_HTTP_DATA_ERROR -> "HTTP data error"
            DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Insufficient storage space"
            DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "Too many redirects"
            DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "Unhandled HTTP response code"
            DownloadManager.ERROR_UNKNOWN -> "Unknown error"
            else -> "Download failed with code: $reason"
        }
    }
}

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val bytesDownloaded: Long, val totalBytes: Long) : DownloadState()
    data class Completed(val fileUri: String) : DownloadState()
    data class Error(val message: String) : DownloadState()
    object Cancelled : DownloadState()
}