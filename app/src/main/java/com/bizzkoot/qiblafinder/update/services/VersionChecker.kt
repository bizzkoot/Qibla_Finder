package com.bizzkoot.qiblafinder.update.services

import android.content.Context
import android.content.pm.PackageInfo
import com.bizzkoot.qiblafinder.update.api.GitHubApiClient
import com.bizzkoot.qiblafinder.update.models.ReleaseInfo
import com.bizzkoot.qiblafinder.update.models.UpdateInfo
import timber.log.Timber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VersionChecker(
    private val context: Context,
    private val gitHubApiClient: GitHubApiClient
) {
    suspend fun checkForUpdates(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val currentVersion = getCurrentVersion()
            val latestRelease = gitHubApiClient.getLatestRelease() ?: return@withContext null

            if (isNewerVersion(currentVersion, latestRelease)) {
                UpdateInfo(
                    currentVersion = currentVersion.versionName,
                    newVersion = latestRelease.versionName,
                    downloadUrl = latestRelease.downloadUrl,
                    releaseNotes = latestRelease.releaseNotes,
                    tagName = latestRelease.tagName
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "Error checking for updates")
            null
        }
    }

    private fun getCurrentVersion(): PackageInfo {
        return context.packageManager.getPackageInfo(context.packageName, 0)
    }

    private fun isNewerVersion(currentVersion: PackageInfo, latestRelease: ReleaseInfo): Boolean {
        @Suppress("DEPRECATION")
        val currentVersionCode = currentVersion.versionCode
        return latestRelease.versionCode > currentVersionCode
    }
}
