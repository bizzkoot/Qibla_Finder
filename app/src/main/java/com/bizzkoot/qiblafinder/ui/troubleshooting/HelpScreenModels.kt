package com.bizzkoot.qiblafinder.ui.troubleshooting

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

data class AppInfoItem(
    val label: String,
    val value: String,
    val icon: ImageVector
)

data class GitHubLinkItem(
    val title: String,
    val description: String,
    val url: String,
    val icon: ImageVector
)

data class UpdateCheckState(
    val isChecking: Boolean = false,
    val lastChecked: String? = null,
    val hasUpdate: Boolean = false,
    val currentVersion: String = "",
    val newVersion: String? = null,
    val error: String? = null
)

sealed class UpdateCheckResult {
    object Checking : UpdateCheckResult()
    object UpToDate : UpdateCheckResult()
    data class UpdateAvailable(val newVersion: String) : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
}
