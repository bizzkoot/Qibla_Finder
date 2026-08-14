package com.bizzkoot.qiblafinder.update.api

import com.bizzkoot.qiblafinder.update.models.GitHubRelease
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * PRD M10: pins the GitHub release JSON mapping (Gson @SerializedName annotations) and
 * the vX.Y.Z -> versionCode derivation used by the in-app updater. Both are pure logic,
 * so these tests run as plain JVM tests (no Android runtime needed).
 */
class GitHubApiClientTest {

    private val gson = Gson()

    private fun sampleJson(assets: String): String = """
        {
          "id": 12345,
          "tag_name": "v2.10.4",
          "name": "Release v2.10.4",
          "body": "Bug fixes",
          "published_at": "2025-11-01T12:00:00Z",
          "assets": $assets
        }
    """.trimIndent()

    private val apkAssetJson =
        """[{"id": 1, "name": "app-release.apk", "browser_download_url": "https://example.com/app-release.apk", "content_type": "application/vnd.android.package-archive"}]"""

    @Test
    fun `gson parses the latest release payload`() {
        val release = gson.fromJson(sampleJson(apkAssetJson), GitHubRelease::class.java)

        assertEquals("v2.10.4", release.tagName)
        assertEquals("Release v2.10.4", release.name)
        assertEquals("Bug fixes", release.body)
        assertEquals("2025-11-01T12:00:00Z", release.publishedAt)
        assertEquals(1, release.assets.size)
        assertEquals("app-release.apk", release.assets[0].name)
        assertEquals("https://example.com/app-release.apk", release.assets[0].downloadUrl)
    }

    @Test
    fun `toReleaseInfo maps fields and derives versionCode from the tag`() {
        val release = gson.fromJson(sampleJson(apkAssetJson), GitHubRelease::class.java)

        val info = release.toReleaseInfo()

        assertEquals("2.10.4", info!!.versionName)
        assertEquals(21004, info.versionCode)
        assertEquals("https://example.com/app-release.apk", info.downloadUrl)
        assertEquals("Bug fixes", info.releaseNotes)
        assertEquals("v2.10.4", info.tagName)
    }

    @Test
    fun `toReleaseInfo returns null when no apk asset exists`() {
        val release = gson.fromJson(
            sampleJson(
                """[{"id": 1, "name": "readme.txt", "browser_download_url": "https://example.com/readme.txt", "content_type": "text/plain"}]"""
            ),
            GitHubRelease::class.java
        )

        assertNull(release.toReleaseInfo())
    }

    @Test
    fun `extractVersionCode converts semantic versions`() {
        assertEquals(21003, extractVersionCode("2.10.3"))
        assertEquals(10400, extractVersionCode("1.4"))
        assertEquals(30000, extractVersionCode("3"))
        assertEquals(10200, extractVersionCode("1.2.x"))
        assertEquals(0, extractVersionCode("garbage"))
    }
}
