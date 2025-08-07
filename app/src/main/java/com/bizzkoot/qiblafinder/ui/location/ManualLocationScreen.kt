package com.bizzkoot.qiblafinder.ui.location

import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import timber.log.Timber
import javax.inject.Inject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualLocationScreen(
    viewModel: ManualLocationViewModel,
    onLocationConfirmed: (MapLocation) -> Unit,
    onBackPressed: () -> Unit
) {
    timber.log.Timber.d("🎯 ManualLocationScreen - ENTERING ManualLocationScreen composable")
    timber.log.Timber.d("🎯 ManualLocationScreen - ViewModel: $viewModel")
    val uiState by viewModel.uiState.collectAsState()
    
    // Debug logging
    LaunchedEffect(Unit) {
        Timber.d("📍 ManualLocationScreen - Screen initialized with viewModel: $viewModel")
    }
    
    LaunchedEffect(uiState) {
        Timber.d("📍 ManualLocationScreen - UI State updated: isLoading=${uiState.isLoading}, error=${uiState.error}, currentLocation=${uiState.currentLocation}")
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manual Location Adjustment") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refreshLocation() },
                        enabled = !uiState.isLoading
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = "Refresh location")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Map View as the background
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Loading map...",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Getting your location",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (uiState.error != null && uiState.currentLocation == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Error loading map",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = uiState.error!!,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.refreshLocation() }
                        ) {
                            Text("Retry")
                        }
                    }
                }
            } else {
                val currentLocation = uiState.currentLocation ?: MapLocation(3.1390, 101.6869) // Fallback
                OpenStreetMapView(
                    currentLocation = currentLocation,
                    onLocationSelected = { mapLocation ->
                        viewModel.updateSelectedLocation(mapLocation)
                    },
                    onAccuracyChanged = { accuracy ->
                        viewModel.updateAccuracy(accuracy)
                    },
                    onTileInfoChanged = { tileCount, cacheSizeMB ->
                        viewModel.updateTileInfo(tileCount, cacheSizeMB)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Top Instructions Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Adjust Your Location", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Drag the pin to your exact location.", fontSize = 14.sp)
                    uiState.selectedLocation?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Selected: ${it.latitude}, ${it.longitude}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Tiles: ${uiState.tileCount}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Cache: ${String.format("%.1f", uiState.cacheSizeMB)}MB", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Bottom Action Bar
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Accuracy: ±${uiState.accuracyInMeters}m", fontSize = 14.sp)
                    Button(
                        onClick = {
                            viewModel.confirmLocation()?.let { onLocationConfirmed(it) }
                        },
                        enabled = uiState.selectedLocation != null && !uiState.isLoading
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Confirm")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Confirm")
                    }
                }
            }
        }
    }
}

 