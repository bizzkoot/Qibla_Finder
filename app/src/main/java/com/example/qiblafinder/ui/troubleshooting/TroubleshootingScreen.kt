package com.bizzkoot.qiblafinder.ui.troubleshooting

import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class TroubleshootingItem(
    val title: String,
    val symptoms: List<String>,
    val solutions: List<String>,
    val icon: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TroubleshootingScreen(
    onBackPressed: () -> Unit
) {
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
            title = { Text("Troubleshooting Guide") },
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
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Need Help?",
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
            
            items(troubleshootingItems) { item ->
                TroubleshootingItemCard(item = item)
            }
        }
    }
}

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