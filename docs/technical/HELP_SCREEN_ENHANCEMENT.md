# Help Screen Enhancement: Manual Update Check & GitHub Integration

## 🎯 Overview

This guide provides a comprehensive implementation for enhancing the existing TroubleshootingScreen (Help screen) with manual update checking functionality and GitHub repository links. This enhancement gives users control over update checking while providing easy access to project resources.

## 📋 Table of Contents

1. [Current Implementation Analysis](#current-implementation-analysis)
2. [Enhancement Features](#enhancement-features)
3. [UI/UX Design](#uiux-design)
4. [Implementation Steps](#implementation-steps)
5. [Code Implementation](#code-implementation)
6. [GitHub Icons Integration](#github-icons-integration)
7. [Testing Guide](#testing-guide)
8. [Best Practices](#best-practices)

## 🔍 Current Implementation Analysis

### Existing Structure
- **Location**: `app/src/main/java/com/bizzkoot/qiblafinder/ui/troubleshooting/TroubleshootingScreen.kt`
- **Navigation**: Accessed via "Help" button in CompassScreen
- **Content**: 5 troubleshooting items with expandable cards
- **Design**: Clean Material 3 design with TopAppBar

### Current Flow
```
CompassScreen → Help Button → TroubleshootingScreen
```

## 🚀 Enhancement Features

### New Features to Add:
1. **Manual Update Check Section**
   - Current version display
   - "Check for Updates" button
   - Update status indicators
   - Progress feedback

2. **GitHub Repository Links**
   - Repository URL with GitHub icon
   - Issues/Bug reports link
   - Releases page link
   - Contributing guidelines link

3. **App Information Section**
   - Version information
   - Build details
   - Developer information

## 🎨 UI/UX Design

### Enhanced Screen Layout
```
┌─────────────────────────────────┐
│ ← Troubleshooting Guide         │ ← TopAppBar
├─────────────────────────────────┤
│ 📱 App Information             │ ← New Section
│ ├─ Version: 2.0.4              │
│ ├─ [Check for Updates]         │
│ └─ Last Checked: 2 hours ago    │
├─────────────────────────────────┤
│ 🔗 GitHub Repository           │ ← New Section
│ ├─ [📁 View Source Code]       │
│ ├─ [🐛 Report an Issue]        │
│ └─ [📋 View Releases]          │
├─────────────────────────────────┤
│ ❓ Need Help?                  │ ← Existing Section
│ Common issues and solutions...  │
├─────────────────────────────────┤
│ 🧭 Compass Issues              │ ← Existing Items
│ ⚠️ Interference Issues         │
│ 📍 Location Issues             │
│ 📱 AR Issues                   │
│ ☀️ Sun Calibration Issues      │
└─────────────────────────────────┘
```

## 🛠️ Implementation Steps

### Step 1: Create Enhanced Data Models

Create `app/src/main/java/com/bizzkoot/qiblafinder/ui/troubleshooting/HelpScreenModels.kt`:

```kotlin
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
```

### Step 2: Create GitHub Link Utilities

Create `app/src/main/java/com/bizzkoot/qiblafinder/utils/GitHubUtils.kt`:

```kotlin
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
```

### Step 3: Create GitHub Vector Icons

Create `app/src/main/java/com/bizzkoot/qiblafinder/ui/icons/GitHubIcon.kt`:

```kotlin
package com.bizzkoot.qiblafinder.ui.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

val Icons.Filled.GitHub: ImageVector
    get() {
        if (_gitHub != null) {
            return _gitHub!!
        }
        _gitHub = materialIcon(name = "Filled.GitHub") {
            materialPath {
                // GitHub icon path data
                moveTo(12f, 2f)
                curveTo(6.477f, 2f, 2f, 6.484f, 2f, 12.017f)
                curveTo(2f, 16.624f, 4.865f, 20.539f, 8.839f, 21.777f)
                curveTo(9.339f, 21.869f, 9.521f, 21.564f, 9.521f, 21.302f)
                curveTo(9.521f, 21.067f, 9.512f, 20.295f, 9.507f, 19.496f)
                curveTo(6.726f, 20.096f, 6.139f, 18.156f, 6.139f, 18.156f)
                curveTo(5.695f, 17.064f, 5.029f, 16.76f, 5.029f, 16.76f)
                curveTo(4.121f, 16.14f, 5.098f, 16.153f, 5.098f, 16.153f)
                curveTo(6.101f, 16.223f, 6.629f, 17.178f, 6.629f, 17.178f)
                curveTo(7.521f, 18.681f, 8.97f, 18.239f, 9.539f, 17.985f)
                curveTo(9.631f, 17.327f, 9.889f, 16.885f, 10.175f, 16.639f)
                curveTo(7.905f, 16.389f, 5.509f, 15.525f, 5.509f, 11.446f)
                curveTo(5.509f, 10.432f, 5.891f, 9.603f, 6.649f, 8.955f)
                curveTo(6.544f, 8.705f, 6.198f, 7.688f, 6.749f, 6.328f)
                curveTo(6.749f, 6.328f, 7.589f, 6.062f, 9.499f, 7.417f)
                curveTo(10.299f, 7.199f, 11.149f, 7.09f, 11.999f, 7.086f)
                curveTo(12.849f, 7.09f, 13.699f, 7.199f, 14.499f, 7.417f)
                curveTo(16.409f, 6.062f, 17.249f, 6.328f, 17.249f, 6.328f)
                curveTo(17.801f, 7.688f, 17.455f, 8.705f, 17.349f, 8.955f)
                curveTo(18.109f, 9.603f, 18.489f, 10.432f, 18.489f, 11.446f)
                curveTo(18.489f, 15.535f, 16.089f, 16.385f, 13.811f, 16.629f)
                curveTo(14.171f, 16.937f, 14.489f, 17.547f, 14.489f, 18.481f)
                curveTo(14.489f, 19.828f, 14.479f, 20.914f, 14.479f, 21.302f)
                curveTo(14.479f, 21.566f, 14.659f, 21.873f, 15.169f, 21.775f)
                curveTo(19.139f, 20.534f, 22f, 16.622f, 22f, 12.017f)
                curveTo(22f, 6.484f, 17.523f, 2f, 12f, 2f)
                close()
            }
        }
        return _gitHub!!
    }

private var _gitHub: ImageVector? = null
```

### Step 4: Create Enhanced Help ViewModel

Create `app/src/main/java/com/bizzkoot/qiblafinder/ui/troubleshooting/EnhancedHelpViewModel.kt`:

```kotlin
package com.bizzkoot.qiblafinder.ui.troubleshooting

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bizzkoot.qiblafinder.BuildConfig
import com.bizzkoot.qiblafinder.update.repositories.UpdateNotificationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*

class EnhancedHelpViewModel(
    private val updateRepository: UpdateNotificationRepository
) : ViewModel() {
    
    private val _updateCheckState = MutableStateFlow(UpdateCheckState())
    val updateCheckState: StateFlow<UpdateCheckState> = _updateCheckState.asStateFlow()
    
    init {
        initializeState()
    }
    
    private fun initializeState() {
        _updateCheckState.value = _updateCheckState.value.copy(
            currentVersion = BuildConfig.VERSION_NAME,
            lastChecked = getLastCheckTime()
        )
    }
    
    fun checkForUpdates(forceCheck: Boolean = true) {
        viewModelScope.launch {
            _updateCheckState.value = _updateCheckState.value.copy(
                isChecking = true,
                error = null
            )
            
            try {
                val updateInfo = updateRepository.checkForUpdates(forceCheck)
                val currentTime = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date())
                
                if (updateInfo != null) {
                    _updateCheckState.value = _updateCheckState.value.copy(
                        isChecking = false,
                        hasUpdate = true,
                        newVersion = updateInfo.newVersion,
                        lastChecked = currentTime
                    )
                } else {
                    _updateCheckState.value = _updateCheckState.value.copy(
                        isChecking = false,
                        hasUpdate = false,
                        newVersion = null,
                        lastChecked = currentTime
                    )
                }
                
                saveLastCheckTime(currentTime)
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to check for updates")
                _updateCheckState.value = _updateCheckState.value.copy(
                    isChecking = false,
                    error = "Failed to check for updates: ${e.message}"
                )
            }
        }
    }
    
    private fun getLastCheckTime(): String? {
        // You can implement SharedPreferences storage here
        // For now, return null to indicate never checked
        return null
    }
    
    private fun saveLastCheckTime(time: String) {
        // Implement SharedPreferences storage here
        // For now, just log
        Timber.d("Last check time saved: $time")
    }
}
```

### Step 5: Enhanced Troubleshooting Screen

Create the enhanced version `app/src/main/java/com/bizzkoot/qiblafinder/ui/troubleshooting/EnhancedTroubleshootingScreen.kt`:

```kotlin
package com.bizzkoot.qiblafinder.ui.troubleshooting

import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.sp
import com.bizzkoot.qiblafinder.BuildConfig
import com.bizzkoot.qiblafinder.ui.icons.GitHub
import com.bizzkoot.qiblafinder.utils.GitHubUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedTroubleshootingScreen(
    onBackPressed: () -> Unit,
    viewModel: EnhancedHelpViewModel
) {
    val context = LocalContext.current
    val updateCheckState by viewModel.updateCheckState.collectAsState()
    
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
        modifier = Modifier.fillMaxSize()
    ) {
        // Top App Bar
        TopAppBar(
            title = { Text("Help & Support") },
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
                    onCheckForUpdates = { viewModel.checkForUpdates() }
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
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Find solutions to common issues below. Tap on any section to expand it.",
                            fontSize = 14.sp,
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
    onCheckForUpdates: () -> Unit
) {
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
                    fontSize = 18.sp,
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
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "v${updateCheckState.currentVersion}",
                        fontSize = 16.sp,
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
                                fontSize = 12.sp,
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
                                fontSize = 12.sp,
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
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
            
            if (updateCheckState.error != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = updateCheckState.error,
                    fontSize = 12.sp,
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
                        fontSize = 12.sp,
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
        }
    }
}

@Composable
fun GitHubRepositoryCard() {
    val context = LocalContext.current
    
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
                    fontSize = 18.sp,
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
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    fontSize = 12.sp,
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
                    fontSize = 24.sp,
                    modifier = Modifier.padding(end = 12.dp)
                )
                Text(
                    text = item.title,
                    fontSize = 16.sp,
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
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(8.dp))
                item.symptoms.forEach { symptom ->
                    Text(
                        text = "• $symptom",
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Solutions
                Text(
                    text = "Solutions:",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                item.solutions.forEachIndexed { index, solution ->
                    Text(
                        text = "${index + 1}. $solution",
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                    )
                }
            }
        }
    }
}
```

### Step 6: Update Navigation and Integration

Update the `QiblaNavHost.kt` to use the enhanced screen:

```kotlin
// In QiblaNavHost.kt, replace the troubleshooting composable:
composable(QiblaAppState.TROUBLESHOOTING_ROUTE) {
    Timber.d("🎯 QiblaNavHost - Enhanced Troubleshooting screen composable called")
    val enhancedHelpViewModel: EnhancedHelpViewModel = viewModel()
    EnhancedTroubleshootingScreen(
        onBackPressed = { navController.popBackStack() },
        viewModel = enhancedHelpViewModel
    )
}
```

### Step 7: Update Dependency Injection

If using Koin, add the new ViewModel:

```kotlin
val troubleshootingModule = module {
    viewModel { EnhancedHelpViewModel(get()) }
}
```

## 🧪 Testing Guide

### Manual Testing Checklist

#### App Information Section
- [ ] Current version displays correctly
- [ ] "Check for Updates" button works
- [ ] Loading state shows during check
- [ ] Update available state displays correctly
- [ ] Error states are handled gracefully
- [ ] Last checked time is saved and displayed

#### GitHub Repository Section
- [ ] All GitHub links open correctly in browser
- [ ] GitHub icon displays properly
- [ ] Link descriptions are clear and helpful
- [ ] External link icon appears

#### Integration Testing
- [ ] Navigation from Help button works
- [ ] Back navigation works correctly
- [ ] Screen adapts to different screen sizes
- [ ] Dark/light theme support works

#### Error Scenarios
- [ ] Network unavailable during update check
- [ ] GitHub links work without internet (show error)
- [ ] Malformed update responses handled

## 🎨 Design Considerations

### Material 3 Design System
- Uses proper Material 3 color roles
- Consistent typography scale
- Appropriate spacing and elevation
- Proper contrast ratios

### Accessibility
- Proper content descriptions for icons
- Sufficient touch target sizes (44dp minimum)
- Clear visual hierarchy
- Screen reader friendly

### User Experience
- Non-blocking update checks
- Clear status feedback
- Graceful error handling
- Consistent interaction patterns

## 📱 Platform Considerations

### Android Version Support
- Minimum API 24 (Android 7.0)
- Uses compatible Material 3 components
- Graceful degradation for older versions

### Performance
- Efficient state management
- Minimal memory usage
- Fast UI rendering
- Background update checks

## 🔧 Best Practices

### State Management
- Use StateFlow for reactive updates
- Handle loading and error states
- Persist user preferences
- Clean up resources properly

### Security
- Validate all external URLs
- Use HTTPS for all GitHub links
- Handle malicious URL scenarios
- Secure API communications

### User Privacy
- No tracking in GitHub links
- Minimal data collection
- Transparent about external navigation
- User control over update checks

## 🚀 Future Enhancements

### Potential Improvements
1. **Update Scheduling**: Allow users to set automatic check frequency
2. **Release Notes**: Show changelog in the app
3. **Download Progress**: Integrate with Enhanced DownloadManager
4. **Feedback Integration**: Direct feedback submission to GitHub
5. **Community Features**: Link to discussions, wiki, etc.

### Analytics Considerations
- Track update check frequency
- Monitor GitHub link usage
- Measure user engagement
- A/B test different layouts

## 📝 Migration Guide

### Step-by-Step Migration

1. **Phase 1: Add New Components**
   - Create new files and utilities
   - Test in isolation
   - Keep existing screen as fallback

2. **Phase 2: Update Navigation**
   - Replace troubleshooting screen reference
   - Update dependency injection
   - Test navigation flow

3. **Phase 3: Rollout**
   - Deploy to internal testing
   - Gather user feedback
   - Monitor error rates

### Rollback Plan
- Keep original TroubleshootingScreen.kt as backup
- Simple navigation change to revert
- Monitor crash rates and user feedback

---

## 🎯 Conclusion

This enhancement transforms the basic troubleshooting screen into a comprehensive help and support center that:

- ✅ Gives users control over update checking
- ✅ Provides easy access to GitHub repository
- ✅ Maintains the existing help functionality
- ✅ Follows Material 3 design guidelines
- ✅ Ensures excellent user experience

The implementation is modular, testable, and scalable, making it easy to add more features in the future while maintaining code quality and user experience.

### Key Benefits
1. **User Empowerment**: Manual update control
2. **Community Engagement**: Easy access to GitHub
3. **Better Support**: Comprehensive help in one place
4. **Professional Appearance**: Modern, polished UI
5. **Developer Friendly**: Clean, maintainable code

For questions or additional features, refer to the troubleshooting section or consult the development team.