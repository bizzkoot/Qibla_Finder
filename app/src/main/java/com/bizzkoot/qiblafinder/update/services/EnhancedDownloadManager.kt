package com.bizzkoot.qiblafinder.update.services

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.io.File

class EnhancedDownloadManager(private val context: Context) {
    
    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    
    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()
    
    private var currentDownloadId: Long = -1
    private var downloadReceiver: BroadcastReceiver? = null
    
    fun startDownload(
        downloadUrl: String,
        fileName: String,
        versionName: String
    ): Long {
        try {
            // Clean up any existing download
            cleanup()
            
            val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
                setTitle("Qibla Finder Update")
                setDescription("Downloading version $versionName...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
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
        
        // Android 14+ requires explicit export specification for dynamic receivers
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.registerReceiver(
                context, 
                downloadReceiver, 
                filter, 
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } else {
            context.registerReceiver(downloadReceiver, filter)
        }
    }
    
    private fun handleDownloadComplete(downloadId: Long) {
        try {
            val query = DownloadManager.Query().setFilterById(downloadId)
            val cursor = downloadManager.query(query)
            
            if (cursor.moveToFirst()) {
                val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val status = cursor.getInt(statusIndex)
                
                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        val uriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                        val localUri = cursor.getString(uriIndex)
                        _downloadState.value = DownloadState.Completed(localUri)
                        Timber.i("Download completed successfully: $localUri")
                    }
                    DownloadManager.STATUS_FAILED -> {
                        val reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                        val reason = cursor.getInt(reasonIndex)
                        val errorMessage = getDownloadErrorMessage(reason)
                        _downloadState.value = DownloadState.Error(errorMessage)
                        Timber.e("Download failed: $errorMessage")
                    }
                }
            }
            cursor.close()
        } catch (e: Exception) {
            Timber.e(e, "Error handling download completion")
            _downloadState.value = DownloadState.Error("Error processing download: ${e.message}")
        }
    }
    
    private fun startProgressMonitoring() {
        // Use a simple timer for progress monitoring
        Thread {
            while (_downloadState.value is DownloadState.Downloading) {
                try {
                    Thread.sleep(1000) // Update every second
                    updateProgress()
                } catch (e: InterruptedException) {
                    break
                }
            }
        }.start()
    }
    
    private fun updateProgress() {
        try {
            val query = DownloadManager.Query().setFilterById(currentDownloadId)
            val cursor = downloadManager.query(query)
            
            if (cursor.moveToFirst()) {
                val bytesDownloadedIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val bytesTotalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                
                val bytesDownloaded = cursor.getLong(bytesDownloadedIndex)
                val bytesTotal = cursor.getLong(bytesTotalIndex)
                val status = cursor.getInt(statusIndex)
                
                if (status == DownloadManager.STATUS_RUNNING && bytesTotal > 0) {
                    _downloadState.value = DownloadState.Downloading(bytesDownloaded, bytesTotal)
                }
            }
            cursor.close()
        } catch (e: Exception) {
            Timber.w(e, "Error updating progress")
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
            val file = File(Uri.parse(fileUri).path ?: return)
            if (!file.exists()) {
                _downloadState.value = DownloadState.Error("APK file not found")
                return
            }
            
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            
            context.startActivity(installIntent)
            Timber.i("Installation intent started")
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to install APK")
            _downloadState.value = DownloadState.Error("Failed to install: ${e.message}")
        }
    }
    
    private fun cleanup() {
        downloadReceiver?.let { receiver ->
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {
                Timber.w(e, "Error unregistering receiver")
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