package com.bizzkoot.qiblafinder.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import timber.log.Timber

object GitHubUtils {
    private const val REPO_OWNER = "bizzkoot"
    private const val REPO_NAME = "Qibla_Finder"
    private const val BASE_URL = "https://github.com/$REPO_OWNER/$REPO_NAME"
    
    const val REPOSITORY_URL = BASE_URL
    const val ISSUES_URL = "$BASE_URL/issues"
    const val RELEASES_URL = "$BASE_URL/releases"
    const val NEW_ISSUE_URL = "$BASE_URL/issues/new"
    const val CONTRIBUTING_URL = "$BASE_URL/blob/main/CONTRIBUTING.md"
    
    fun openUrl(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
            Timber.i("Opened URL: $url")
        } catch (e: Exception) {
            Timber.e(e, "Failed to open URL: $url")
        }
    }
    
    fun openRepository(context: Context) = openUrl(context, REPOSITORY_URL)
    fun openIssues(context: Context) = openUrl(context, ISSUES_URL)
    fun openReleases(context: Context) = openUrl(context, RELEASES_URL)
    fun openNewIssue(context: Context) = openUrl(context, NEW_ISSUE_URL)
}
