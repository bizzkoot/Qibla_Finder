package com.bizzkoot.qiblafinder.update.services

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.bizzkoot.qiblafinder.update.api.GitHubApiClient
import com.bizzkoot.qiblafinder.update.models.ReleaseInfo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * PRD M10: first unit tests for the update/ package. Pins [VersionChecker]'s update
 * gating: a strictly-newer release (by versionCode) yields an [UpdateInfo] with fields
 * mapped from the [ReleaseInfo]; equal/older yields null; an API failure yields null so
 * a check never crashes the app.
 */
@RunWith(RobolectricTestRunner::class)
// Use a plain Application: the real QiblaFinderApplication.onCreate schedules WorkManager,
// which is not initialized in unit-test environments.
@Config(application = Application::class)
class VersionCheckerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /**
     * The installed app's version as reported by the (Robolectric) PackageManager — the
     * exact source [VersionChecker.getCurrentVersion] reads. Derived at runtime instead of
     * hardcoding so these tests stay green under the release workflow (release-drafter.yml),
     * which bumps versionName/versionCode in app/build.gradle BEFORE running ./gradlew test.
     */
    @Suppress("DEPRECATION")
    private val installedVersionName: String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName

    @Suppress("DEPRECATION")
    private val installedVersionCode: Int =
        context.packageManager.getPackageInfo(context.packageName, 0).versionCode

    private fun releaseInfo(
        versionName: String = "2.10.4",
        versionCode: Int = 21004
    ) = ReleaseInfo(
        versionName = versionName,
        versionCode = versionCode,
        downloadUrl = "https://github.com/bizzkoot/Qibla_Finder/releases/download/v2.10.4/app-release.apk",
        releaseNotes = "Fix GPS timeout",
        publishedAt = "2025-11-01T00:00:00Z",
        tagName = "v$versionName"
    )

    private fun checker(client: GitHubApiClient): VersionChecker = VersionChecker(context, client)

    @Test
    fun `newer release returns UpdateInfo with mapped fields`() = runBlocking {
        val client = mock(GitHubApiClient::class.java)
        `when`(client.getLatestRelease()).thenReturn(releaseInfo(versionCode = installedVersionCode + 1))

        val updateInfo = checker(client).checkForUpdates()

        assertNotNull(updateInfo)
        updateInfo!!
        assertEquals(installedVersionName, updateInfo.currentVersion)
        assertEquals("2.10.4", updateInfo.newVersion)
        assertEquals(
            "https://github.com/bizzkoot/Qibla_Finder/releases/download/v2.10.4/app-release.apk",
            updateInfo.downloadUrl
        )
        assertEquals("Fix GPS timeout", updateInfo.releaseNotes)
        assertEquals("v2.10.4", updateInfo.tagName)
    }

    @Test
    fun `equal version returns null`() = runBlocking {
        val client = mock(GitHubApiClient::class.java)
        `when`(client.getLatestRelease()).thenReturn(releaseInfo(versionCode = installedVersionCode))

        assertNull(checker(client).checkForUpdates())
    }

    @Test
    fun `older version returns null`() = runBlocking {
        val client = mock(GitHubApiClient::class.java)
        `when`(client.getLatestRelease()).thenReturn(releaseInfo(versionCode = installedVersionCode - 1))

        assertNull(checker(client).checkForUpdates())
    }

    @Test
    fun `null latest release returns null`() = runBlocking {
        val client = mock(GitHubApiClient::class.java)
        `when`(client.getLatestRelease()).thenReturn(null)

        assertNull(checker(client).checkForUpdates())
    }

    @Test
    fun `api exception returns null instead of crashing`() = runBlocking {
        val client = mock(GitHubApiClient::class.java)
        `when`(client.getLatestRelease()).thenThrow(RuntimeException("network down"))

        assertNull(checker(client).checkForUpdates())
    }

    @Test
    fun `isNewerVersion compares version codes`() {
        val checker = checker(mock(GitHubApiClient::class.java))
        assertTrue(checker.isNewerVersion(21003, 21004))
        assertFalse(checker.isNewerVersion(21004, 21003))
        assertFalse(checker.isNewerVersion(21003, 21003))
    }
}
