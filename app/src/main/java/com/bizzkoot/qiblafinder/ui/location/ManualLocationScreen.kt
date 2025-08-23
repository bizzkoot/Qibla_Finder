package com.bizzkoot.qiblafinder.ui.location

import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Satellite
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import timber.log.Timber
import javax.inject.Inject

/**
 * Composable function that measures the height of its content and reports it back
 * via the onHeightMeasured callback. This is used to dynamically position zoom buttons
 * based on the actual height of the "Adjust Your Location" panel.
 */
@Composable
fun MeasuredTopPanel(
    content: @Composable () -> Unit,
    onHeightMeasured: (Int) -> Unit
) {
    Box(
        modifier = Modifier.onGloballyPositioned { coordinates ->
            onHeightMeasured(coordinates.size.height)
        }
    ) {
        content()
    }
}

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
        Timber.d("📍 ManualLocationScreen - UI State updated: isLoading=${uiState.isLoading}, error=${uiState.error}, currentLocation=${uiState.currentLocation}, mapType=${uiState.selectedMapType}")
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
                    MapTypeToggle(
                        selectedMapType = uiState.selectedMapType,
                        availableMapTypes = uiState.availableMapTypes,
                        onMapTypeChanged = { viewModel.updateMapType(it) }
                    )
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
            if (uiState.isLoading && uiState.currentLocation == null) { // Show loading only if location is not yet available
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
                    mapType = uiState.selectedMapType,
                    showQiblaDirection = uiState.showQiblaDirection,
                    onQiblaLineNeedsRedraw = { viewModel.markQiblaLineNeedsRedraw() },
                    onPanStop = { viewModel.onPanStop() },
                    panelHeight = uiState.panelHeight,
                    isMapTypeChanging = uiState.isMapTypeChanging,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Top Instructions Card wrapped with MeasuredTopPanel for dynamic positioning
            MeasuredTopPanel(
                content = {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f))
                ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Adjust Your Location", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        
                        // Qibla Direction Toggle Button
                        IconButton(
                            onClick = { viewModel.toggleQiblaDirection() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (uiState.showQiblaDirection) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (uiState.showQiblaDirection) "Hide Qibla direction" else "Show Qibla direction",
                                tint = if (uiState.showQiblaDirection) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Drag the pin to your exact location. The green line shows the direction to Kaaba.", fontSize = 14.sp)
                    
                    uiState.selectedLocation?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Selected: ${String.format("%.4f", it.latitude)}, ${String.format("%.4f", it.longitude)}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    // Qibla Information Display with Error Handling
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Show error indicator if there's a calculation error
                    if (uiState.error != null && uiState.currentLocation != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Calculation Error",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = uiState.error!!,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    
                    // Show Qibla information or fallback indicators
                    if (uiState.qiblaBearing != 0.0 || uiState.distanceToKaaba != 0.0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Qibla: ${String.format("%.1f", uiState.qiblaBearing)}°",
                                fontSize = 12.sp,
                                color = if (uiState.error != null) 
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                else 
                                    MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "Distance: ${String.format("%.0f", uiState.distanceToKaaba)} km",
                                fontSize = 12.sp,
                                color = if (uiState.error != null) 
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                else 
                                    MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else if (uiState.selectedLocation != null && uiState.error == null) {
                        // Show calculating indicator when location is selected but no Qibla info yet
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 1.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Calculating Qibla direction...",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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
        },
                onHeightMeasured = { height -> viewModel.updatePanelHeight(height) }
            )

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

            // Redraw Qibla Button (only shows when Qibla line is missing)
            if (uiState.showQiblaDirection && uiState.needsQiblaRedraw && !uiState.isLoading) {
                FloatingActionButton(
                    onClick = { viewModel.redrawQiblaLine() },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 16.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Redraw Qibla Direction",
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Qibla",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (uiState.isLoading && uiState.currentLocation != null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
fun MapTypeToggle(
    selectedMapType: MapType,
    availableMapTypes: List<MapType>,
    onMapTypeChanged: (MapType) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = when (selectedMapType) {
                    MapType.STREET -> Icons.Default.Map
                    MapType.SATELLITE -> Icons.Default.Satellite
                },
                contentDescription = "Map Type: ${selectedMapType.displayName}"
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            availableMapTypes.forEach { mapType ->
                DropdownMenuItem(
                    text = { Text(mapType.displayName) },
                    onClick = {
                        onMapTypeChanged(mapType)
                        expanded = false
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = when (mapType) {
                                MapType.STREET -> Icons.Default.Map
                                MapType.SATELLITE -> Icons.Default.Satellite
                            },
                            contentDescription = null
                        )
                    }
                )
            }
        }
    }
}

 