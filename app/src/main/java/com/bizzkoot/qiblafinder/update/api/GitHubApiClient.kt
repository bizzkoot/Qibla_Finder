package com.bizzkoot.qiblafinder.update.api

import com.bizzkoot.qiblafinder.update.models.GitHubRelease
import com.bizzkoot.qiblafinder.update.models.ReleaseInfo
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import timber.log.Timber

class GitHubApiClient {
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.github.com/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val api = retrofit.create(GitHubApi::class.java)

    suspend fun getLatestRelease(): ReleaseInfo? {
        return try {
            val response = api.getLatestRelease("bizzkoot", "Qibla_Finder")
            response.toReleaseInfo()
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch latest release")
            null
        }
    }
}

private fun GitHubRelease.toReleaseInfo(): ReleaseInfo? {
    val apkAsset = assets.find { it.name.endsWith(".apk") }
    if (apkAsset == null) {
        Timber.w("No APK asset found in release")
        return null
    }

    // Extract version from tag_name (e.g., "v1.4.0" -> "1.4.0")
    val versionName = tagName.removePrefix("v")
    
    // Extract version code from version name (e.g., "1.4.0" -> 10400)
    val versionCode = extractVersionCode(versionName)

    return ReleaseInfo(
        versionName = versionName,
        versionCode = versionCode,
        downloadUrl = apkAsset.downloadUrl,
        releaseNotes = body,
        publishedAt = publishedAt,
        tagName = tagName
    )
}

private fun extractVersionCode(versionName: String): Int {
    // Parse semantic version and convert to versionCode
    // Format: major * 10000 + minor * 100 + patch
    val parts = versionName.split(".")
    return when {
        parts.size >= 3 -> {
            val major = parts[0].toIntOrNull() ?: 0
            val minor = parts[1].toIntOrNull() ?: 0
            val patch = parts[2].toIntOrNull() ?: 0
            major * 10000 + minor * 100 + patch
        }
        parts.size == 2 -> {
            val major = parts[0].toIntOrNull() ?: 0
            val minor = parts[1].toIntOrNull() ?: 0
            major * 10000 + minor * 100
        }
        else -> {
            val major = parts[0].toIntOrNull() ?: 0
            major * 10000
        }
    }
}
