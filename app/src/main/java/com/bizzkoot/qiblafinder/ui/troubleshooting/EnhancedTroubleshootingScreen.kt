package com.bizzkoot.qiblafinder.ui.troubleshooting

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bizzkoot.qiblafinder.ui.icons.GitHub
import com.bizzkoot.qiblafinder.ui.theme.QiblaTypography
import com.bizzkoot.qiblafinder.utils.GitHubUtils
import com.bizzkoot.qiblafinder.update.services.DownloadState

data class TroubleshootingItem(
    val title: String,
    val symptoms: List<String>,
    val solutions: List<String>,
    val icon: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedTroubleshootingScreen(
    onBackPressed: () -> Unit,
    viewModel: EnhancedHelpViewModel
) {
    val context = LocalContext.current
    val updateCheckState by viewModel.updateCheckState.collectAsState()
    val downloadState by viewModel.downloadState.collectAsState()
    val typography = QiblaTypography.current
    
    val troubleshootingItems = listOf(
        TroubleshootingItem(
            title = "Compass is Inaccurate or Not Moving",
            symptoms = listOf(
                "The compass needle jumps around erratically",
                "The needle points in a direction you know is wrong",
                "The needle doesn't move when you turn the phone"
            ),
            solutions = listOf(
                "Move your phone in a figure-8 pattern several times",
                "Move away from metal objects and electronics",
                "Remove magnetic phone cases or mounts",
                "Restart the app completely"
            ),
            icon = "🧭"
        ),
        TroubleshootingItem(
            title = "Interference Detected Warning Persists",
            symptoms = listOf(
                "⚠️ Interference warning does not go away",
                "Compass readings remain unstable"
            ),
            solutions = listOf(
                "Perform figure-8 calibration for 15-20 seconds",
                "Check your phone case for magnets",
                "Restart your phone completely",
                "Move to an open area away from buildings"
            ),
            icon = "⚠️"
        ),
        TroubleshootingItem(
            title = "Location is Inaccurate or Loading",
            symptoms = listOf(
                "Location shows 'Loading...' for a long time",
                "Very large accuracy radius (e.g., ±100m)",
                "GPS signal is weak"
            ),
            solutions = listOf(
                "Go outdoors for better GPS signal",
                "Enable High Accuracy Location in settings",
                "Turn on Wi-Fi and Bluetooth",
                "Check location permissions"
            ),
            icon = "📍"
        ),
        TroubleshootingItem(
            title = "AR View is Not Working",
            symptoms = listOf(
                "AR button is grayed out or disabled",
                "Camera feed appears but no AR object",
                "AR object doesn't appear"
            ),
            solutions = listOf(
                "Check if your device supports ARCore",
                "Install/Update Google Play Services for AR",
                "Scan textured surfaces slowly",
                "Avoid blank white walls or reflective surfaces"
            ),
            icon = "📱"
        ),
        TroubleshootingItem(
            title = "Sun Calibration Not Working",
            symptoms = listOf(
                "Sun Calibration button is disabled",
                "Cannot see the sun in camera view",
                "Alignment doesn't work"
            ),
            solutions = listOf(
                "Wait for a clear day with visible sun",
                "Check camera permissions",
                "Point camera directly at the sun",
                "Avoid overcast or nighttime use"
            ),
            icon = "☀️"
        )
    )
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top App Bar
        TopAppBar(
            title = { Text("Help & Support", style = typography.titleTertiary) },
            windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top),
            navigationIcon = {
                IconButton(onClick = onBackPressed) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            }
        )
        
        // Content
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // App Information Section
            item {
                AppInformationCard(
                    updateCheckState = updateCheckState,
                    downloadState = downloadState,
                    onCheckForUpdates = { viewModel.checkForUpdates() },
                    onDownload = { viewModel.downloadUpdate() },
                    onCancel = { viewModel.cancelDownload() },
                    onInstall = { viewModel.installUpdate() }
                )
            }
            
            // GitHub Repository Section
            item {
                GitHubRepositoryCard()
            }
            
            // Need Help Header
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "❓ Need Help?",
                            style = typography.titleSecondary,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Find solutions to common issues below. Tap on any section to expand it.",
                            style = typography.bodySecondary,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            
            // Troubleshooting Items
            items(troubleshootingItems) { item ->
                TroubleshootingItemCard(item = item)
            }
        }
    }
}

@Composable
fun AppInformationCard(
    updateCheckState: UpdateCheckState,
    downloadState: com.bizzkoot.qiblafinder.update.services.DownloadState,
    onCheckForUpdates: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onInstall: () -> Unit
) {
    val typography = QiblaTypography.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "📱 App Information",
                    style = typography.titleTertiary,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Version Information
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Current Version",
                        style = typography.bodySecondary,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "v${updateCheckState.currentVersion}",
                        style = typography.bodyEmphasis,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Update Status
                when {
                    updateCheckState.hasUpdate -> {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                        ) {
                            Text(
                                text = "Update Available",
                                style = typography.badge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    updateCheckState.lastChecked != null && !updateCheckState.hasUpdate -> {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Text(
                                text = "Up to Date",
                                style = typography.badge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
            
            if (updateCheckState.hasUpdate && updateCheckState.newVersion != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "New version v${updateCheckState.newVersion} is available!",
                    style = typography.bodySecondary,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
            
            if (updateCheckState.error != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = updateCheckState.error,
                    style = typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Check for Updates Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                updateCheckState.lastChecked?.let { lastChecked ->
                    Text(
                        text = "Last checked: $lastChecked",
                        style = typography.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                
                Button(
                    onClick = onCheckForUpdates,
                    enabled = !updateCheckState.isChecking,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    if (updateCheckState.isChecking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Checking...")
                    } else {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Check for Updates")
                    }
                }
            }
            
            // Download Section - Only show when update is available
            if (updateCheckState.hasUpdate) {
                Spacer(modifier = Modifier.height(16.dp))
                UpdateDownloadSection(
                    downloadState = downloadState,
                    newVersion = updateCheckState.newVersion,
                    onDownload = onDownload,
                    onCancel = onCancel,
                    onInstall = onInstall
                )
            }
        }
    }
}

@Composable
fun UpdateDownloadSection(
    downloadState: DownloadState,
    newVersion: String?,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onInstall: () -> Unit
) {
    val typography = QiblaTypography.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when (downloadState) {
                        is DownloadState.Downloading -> Icons.Default.CloudDownload
                        is DownloadState.Completed -> Icons.Default.CheckCircle
                        is DownloadState.Error -> Icons.Default.Error
                        else -> Icons.Default.Download
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when (downloadState) {
                        is DownloadState.Downloading -> "Downloading Update..."
                        is DownloadState.Completed -> "Update Ready to Install"
                        is DownloadState.Error -> "Download Failed"
                        else -> "🎉 Ready to Update!"
                    },
                    style = typography.bodyEmphasis,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            
            newVersion?.let { version ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Version $version is available",
                    style = typography.bodySecondary,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                )
            }
            
            // Progress indicator for downloading
            if (downloadState is DownloadState.Downloading) {
                Spacer(modifier = Modifier.height(12.dp))
                
                val progress = if (downloadState.totalBytes > 0) {
                    downloadState.bytesDownloaded.toFloat() / downloadState.totalBytes.toFloat()
                } else 0f
                
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                    )
                    
                    Text(
                        text = "${formatBytes(downloadState.bytesDownloaded)} / ${formatBytes(downloadState.totalBytes)}",
                        style = typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
            
            // Error message
            if (downloadState is DownloadState.Error) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = downloadState.message,
                    style = typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                when (downloadState) {
                    is DownloadState.Idle, is DownloadState.Error -> {
                        Button(
                            onClick = onDownload,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (downloadState is DownloadState.Error) "Retry" else "Download")
                        }
                    }
                    
                    is DownloadState.Downloading -> {
                        Button(
                            onClick = onCancel,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(
                                Icons.Default.Cancel,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Cancel")
                        }
                    }
                    
                    is DownloadState.Completed -> {
                        Button(
                            onClick = onInstall,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                Icons.Default.InstallMobile,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Install")
                        }
                    }
                    
                    is DownloadState.Cancelled -> {
                        Button(
                            onClick = onDownload,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Download")
                        }
                    }
                }
            }
        }
    }
}

// Helper function for formatting bytes
private fun formatBytes(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    
    return when {
        mb >= 1 -> String.format("%.1f MB", mb)
        kb >= 1 -> String.format("%.1f KB", kb)
        else -> "$bytes B"
    }
}

@Composable
fun GitHubRepositoryCard() {
    val context = LocalContext.current
    val typography = QiblaTypography.current
    
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.GitHub,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "🔗 GitHub Repository",
                    style = typography.titleTertiary,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // GitHub Links
            GitHubLinkButton(
                title = "View Source Code",
                description = "Browse the complete source code",
                icon = Icons.Default.Code,
                onClick = { GitHubUtils.openRepository(context) }
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            GitHubLinkButton(
                title = "Report an Issue",
                description = "Found a bug? Let us know!",
                icon = Icons.Default.BugReport,
                onClick = { GitHubUtils.openNewIssue(context) }
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            GitHubLinkButton(
                title = "View Releases",
                description = "Download previous versions",
                icon = Icons.Default.Download,
                onClick = { GitHubUtils.openReleases(context) }
            )
        }
    }
}

@Composable
fun GitHubLinkButton(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    val typography = QiblaTypography.current
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.primary
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = title,
                    style = typography.bodySecondary,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    style = typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                Icons.Default.OpenInNew,
                contentDescription = "Open in browser",
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// Keep the existing TroubleshootingItemCard composable unchanged
@Composable
fun TroubleshootingItemCard(item: TroubleshootingItem) {
    var expanded by remember { mutableStateOf(false) }
    val typography = QiblaTypography.current
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.icon,
                    style = typography.titlePrimary,
                    modifier = Modifier.padding(end = 12.dp)
                )
                Text(
                    text = item.title,
                    style = typography.titleTertiary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { expanded = !expanded }
                ) {
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) "Collapse" else "Expand"
                    )
                }
            }
            
            // Expanded Content
            if (expanded) {
                Spacer(modifier = Modifier.height(16.dp))
                
                // Symptoms
                Text(
                    text = "Symptoms:",
                    style = typography.bodyEmphasis,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(8.dp))
                item.symptoms.forEach { symptom ->
                    Text(
                        text = "• $symptom",
                        style = typography.labelSmall,
                        modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Solutions
                Text(
                    text = "Solutions:",
                    style = typography.bodyEmphasis,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                item.solutions.forEachIndexed { index, solution ->
                    Text(
                        text = "${index + 1}. $solution",
                        style = typography.labelSmall,
                        modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                    )
                }
            }
        }
    }
}
