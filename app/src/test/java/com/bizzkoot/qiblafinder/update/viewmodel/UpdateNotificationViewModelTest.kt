package com.bizzkoot.qiblafinder.update.viewmodel

import com.bizzkoot.qiblafinder.MainDispatcherRule
import com.bizzkoot.qiblafinder.update.models.UpdateInfo
import com.bizzkoot.qiblafinder.update.repositories.UpdateNotificationRepository
import com.bizzkoot.qiblafinder.update.services.DownloadState
import com.bizzkoot.qiblafinder.update.services.EnhancedDownloadManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/**
 * PRD M10: ViewModel-level tests for the update banner flow. The repository and the
 * download manager are mocked so the StateFlow state machine (found / dismissed /
 * error / dismiss / download / install) is pinned without network or DownloadManager.
 */
class UpdateNotificationViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val updateInfo = UpdateInfo(
        currentVersion = "2.10.3",
        newVersion = "2.10.4",
        downloadUrl = "https://example.com/app.apk",
        releaseNotes = "fixes",
        tagName = "v2.10.4"
    )

    private fun repository(): UpdateNotificationRepository = mock(UpdateNotificationRepository::class.java)

    private fun downloadManager(state: DownloadState = DownloadState.Idle): EnhancedDownloadManager {
        val manager = mock(EnhancedDownloadManager::class.java)
        `when`(manager.downloadState).thenReturn(MutableStateFlow(state))
        return manager
    }

    @Test
    fun `checkForUpdates surfaces a new non-dismissed update`() {
        runBlocking {
            val repo = repository()
            `when`(repo.checkForUpdates(false)).thenReturn(updateInfo)
            `when`(repo.isUpdateDismissed(updateInfo.newVersion)).thenReturn(false)

            val viewModel = UpdateNotificationViewModel(repo, downloadManager())

            waitUntil("update banner shown") { viewModel.uiState.value.showNotification }
            assertEquals(updateInfo, viewModel.uiState.value.updateInfo)
            assertFalse(viewModel.uiState.value.isLoading)
        }
    }

    @Test
    fun `dismissed update is not surfaced`() {
        runBlocking {
            val repo = repository()
            `when`(repo.checkForUpdates(false)).thenReturn(updateInfo)
            `when`(repo.isUpdateDismissed(updateInfo.newVersion)).thenReturn(true)

            val viewModel = UpdateNotificationViewModel(repo, downloadManager())

            waitUntil("check finished") { !viewModel.uiState.value.isLoading }
            assertFalse(viewModel.uiState.value.showNotification)
            assertNull(viewModel.uiState.value.updateInfo)
        }
    }

    @Test
    fun `repository exception surfaces an error`() {
        runBlocking {
            val repo = repository()
            `when`(repo.checkForUpdates(false)).thenThrow(RuntimeException("check failed"))

            val viewModel = UpdateNotificationViewModel(repo, downloadManager())

            waitUntil("error surfaced") { viewModel.uiState.value.error != null }
            assertFalse(viewModel.uiState.value.isLoading)
            assertFalse(viewModel.uiState.value.showNotification)
            assertEquals("check failed", viewModel.uiState.value.error)
        }
    }

    @Test
    fun `dismissUpdate clears the banner and dismisses via the repository`() {
        runBlocking {
            val repo = repository()
            `when`(repo.checkForUpdates(false)).thenReturn(updateInfo)
            `when`(repo.isUpdateDismissed(updateInfo.newVersion)).thenReturn(false)

            val viewModel = UpdateNotificationViewModel(repo, downloadManager())
            waitUntil("update banner shown") { viewModel.uiState.value.showNotification }

            viewModel.dismissUpdate()

            verify(repo, times(1)).dismissUpdate()
            assertNull(viewModel.uiState.value.updateInfo)
            assertFalse(viewModel.uiState.value.showNotification)
        }
    }

    @Test
    fun `downloadUpdate starts the download with the update fields`() {
        runBlocking {
            val repo = repository()
            `when`(repo.checkForUpdates(false)).thenReturn(updateInfo)
            `when`(repo.isUpdateDismissed(updateInfo.newVersion)).thenReturn(false)
            val manager = downloadManager()

            val viewModel = UpdateNotificationViewModel(repo, manager)
            waitUntil("update banner shown") { viewModel.uiState.value.showNotification }

            viewModel.downloadUpdate()

            verify(manager, times(1)).startDownload(
                "https://example.com/app.apk",
                "qibla_finder_2.10.4.apk",
                "2.10.4"
            )
        }
    }

    @Test
    fun `installUpdate installs only when the download is completed`() {
        runBlocking {
            val repo = repository()
            val manager = downloadManager(state = DownloadState.Completed("content://downloads/app.apk"))

            val viewModel = UpdateNotificationViewModel(repo, manager)
            waitUntil("download state observed") {
                viewModel.uiState.value.downloadState is DownloadState.Completed
            }

            viewModel.installUpdate()

            verify(manager, times(1)).installApk("content://downloads/app.apk")
        }
    }

    @Test
    fun `installUpdate is a no-op when the download is not completed`() {
        runBlocking {
            val repo = repository()
            val manager = downloadManager(state = DownloadState.Idle)

            val viewModel = UpdateNotificationViewModel(repo, manager)
            waitUntil("download state observed") { viewModel.uiState.value.downloadState is DownloadState.Idle }

            viewModel.installUpdate()

            verify(manager, never()).installApk(anyString())
        }
    }

    private suspend fun waitUntil(
        message: String,
        timeoutMs: Long = 2_000,
        condition: () -> Boolean
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) fail("$message: Condition not met within ${timeoutMs}ms")
            delay(5)
        }
    }
}
