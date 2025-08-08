package com.bizzkoot.qiblafinder.update.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bizzkoot.qiblafinder.QiblaFinderApplication
import timber.log.Timber

class UpdateCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val app = applicationContext as QiblaFinderApplication
            val updateRepository = app.updateNotificationRepository
            
            val updateInfo = updateRepository.checkForUpdates()
            
            if (updateInfo != null) {
                Timber.d("Update available: ${updateInfo.newVersion}")
                Result.success()
            } else {
                Timber.d("No update available")
                Result.success()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error in update check worker")
            Result.retry()
        }
    }
}
