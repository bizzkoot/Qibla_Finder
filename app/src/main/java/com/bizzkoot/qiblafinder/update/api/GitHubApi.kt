package com.bizzkoot.qiblafinder.update.api

import com.bizzkoot.qiblafinder.update.models.GitHubRelease
import retrofit2.http.GET
import retrofit2.http.Path

interface GitHubApi {
    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun getLatestRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): GitHubRelease
}
