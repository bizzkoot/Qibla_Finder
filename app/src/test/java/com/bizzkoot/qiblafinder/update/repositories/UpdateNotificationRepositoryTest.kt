package com.bizzkoot.qiblafinder.update.repositories

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.bizzkoot.qiblafinder.update.models.UpdateInfo
import com.bizzkoot.qiblafinder.update.services.VersionChecker
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * PRD M10 + M7: pins the update-check rate limit in [UpdateNotificationRepository].
 * The 24h window must engage even when the checker finds no update (last_check_time is
 * saved unconditionally — PRD M7), forceCheck must bypass the window, and
 * dismiss/clear must round-trip through SharedPreferences.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class UpdateNotificationRepositoryTest {

    private lateinit var context: Context
    private lateinit var versionChecker: VersionChecker

    private val updateInfo = UpdateInfo(
        currentVersion = "2.10.3",
        newVersion = "2.10.4",
        downloadUrl = "https://example.com/app.apk",
        releaseNotes = "fixes",
        tagName = "v2.10.4"
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Application>()
        // Start from a clean slate for each test
        context.getSharedPreferences("update_notifications", Context.MODE_PRIVATE)
            .edit().clear().commit()
        versionChecker = mock(VersionChecker::class.java)
    }

    private fun repository() = UpdateNotificationRepository(context, versionChecker)

    private fun lastCheckTime(): Long =
        context.getSharedPreferences("update_notifications", Context.MODE_PRIVATE)
            .getLong("last_check_time", 0L)

    @Test
    fun `first check calls the version checker`() {
        runBlocking {
            `when`(versionChecker.checkForUpdates()).thenReturn(updateInfo)

            val result = repository().checkForUpdates()

            assertEquals(updateInfo, result)
            verify(versionChecker, times(1)).checkForUpdates()
        }
    }

    @Test
    fun `second check within 24h is skipped by the rate limit`() {
        runBlocking {
            val repo = repository()
            `when`(versionChecker.checkForUpdates()).thenReturn(updateInfo)

            repo.checkForUpdates()
            repo.checkForUpdates()

            // The second check must NOT reach the version checker (24h window).
            verify(versionChecker, times(1)).checkForUpdates()
        }
    }

    @Test
    fun `forceCheck bypasses the rate limit`() {
        runBlocking {
            val repo = repository()
            `when`(versionChecker.checkForUpdates()).thenReturn(updateInfo)

            repo.checkForUpdates()
            repo.checkForUpdates(forceCheck = true)

            verify(versionChecker, times(2)).checkForUpdates()
        }
    }

    @Test
    fun `last check time is saved even when no update exists (M7)`() {
        runBlocking {
            `when`(versionChecker.checkForUpdates()).thenReturn(null)

            val result = repository().checkForUpdates()

            assertNull(result)
            assertTrue("last_check_time must be persisted after a no-update check", lastCheckTime() > 0)
        }
    }

    @Test
    fun `dismissed version stays dismissed until cleared`() {
        runBlocking {
            val repo = repository()

            // dismissUpdate with nothing surfaced is a no-op.
            repo.dismissUpdate()
            assertFalse(repo.isUpdateDismissed("2.10.4"))

            `when`(versionChecker.checkForUpdates()).thenReturn(updateInfo)
            repo.checkForUpdates(forceCheck = true)
            repo.dismissUpdate()

            assertTrue(repo.isUpdateDismissed("2.10.4"))

            repo.clearDismissedVersion()
            assertFalse(repo.isUpdateDismissed("2.10.4"))
        }
    }
}
