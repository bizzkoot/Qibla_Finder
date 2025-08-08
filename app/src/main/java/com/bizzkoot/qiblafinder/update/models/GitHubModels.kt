package com.bizzkoot.qiblafinder.update.models

import com.google.gson.annotations.SerializedName

data class GitHubRelease(
    @SerializedName("id") val id: Long,
    @SerializedName("tag_name") val tagName: String,
    @SerializedName("name") val name: String,
    @SerializedName("body") val body: String,
    @SerializedName("published_at") val publishedAt: String,
    @SerializedName("assets") val assets: List<GitHubAsset>
)

data class GitHubAsset(
    @SerializedName("id") val id: Long,
    @SerializedName("name") val name: String,
    @SerializedName("browser_download_url") val downloadUrl: String,
    @SerializedName("content_type") val contentType: String
)

data class ReleaseInfo(
    val versionName: String,
    val versionCode: Int,
    val downloadUrl: String,
    val releaseNotes: String,
    val publishedAt: String,
    val tagName: String
)

data class UpdateInfo(
    val currentVersion: String,
    val newVersion: String,
    val downloadUrl: String,
    val releaseNotes: String,
    val tagName: String
)
