package com.bizzkoot.qiblafinder

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.bizzkoot.qiblafinder.update.api.GitHubApiClient
import com.bizzkoot.qiblafinder.update.repositories.UpdateNotificationRepository
import com.bizzkoot.qiblafinder.update.services.VersionChecker
import com.bizzkoot.qiblafinder.update.workers.UpdateCheckWorker
import com.bizzkoot.qiblafinder.utils.DeviceCapabilitiesDetector
import timber.log.Timber
import java.util.concurrent.TimeUnit

class QiblaFinderApplication : Application() {

    // Lazy initialization of update-related services
    val gitHubApiClient by lazy { GitHubApiClient() }
    val versionChecker by lazy { VersionChecker(this, gitHubApiClient) }
    val updateNotificationRepository by lazy { UpdateNotificationRepository(this, versionChecker) }

    override fun onCreate() {
        super.onCreate()
        
        // Check if we're in debug mode using application info flags
        val isDebuggable = applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (isDebuggable) {
            Timber.plant(Timber.DebugTree())
        }

        // Initialize device capabilities detector
        DeviceCapabilitiesDetector.initialize(this)

        // Schedule periodic update checks
        scheduleUpdateChecks()
    }

    private fun scheduleUpdateChecks() {
        val updateCheckRequest = PeriodicWorkRequestBuilder<UpdateCheckWorker>(
            1, TimeUnit.DAYS
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "update_check",
            ExistingPeriodicWorkPolicy.KEEP,
            updateCheckRequest
        )
    }
}