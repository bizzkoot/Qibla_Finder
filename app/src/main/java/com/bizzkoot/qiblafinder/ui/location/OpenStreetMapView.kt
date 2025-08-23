package com.bizzkoot.qiblafinder.ui.location

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.bizzkoot.qiblafinder.utils.DeviceCapabilitiesDetector
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.math.*

const val MAX_ZOOM_LEVEL = 19
const val MAX_TILE_ZOOM_LEVEL = 18  // Maximum zoom level for tile downloads
const val MIN_ZOOM_LEVEL = 2
const val MAX_DIGITAL_ZOOM_FACTOR = 4.0
const val DIGITAL_ZOOM_STEP = 1.2
const val TILE_SIZE = 256.0

// Enhanced pan state for high digital zoom arrow positioning
data class EnhancedPanState(
    val cumulativePanDistance: Float = 0f,
    val lastSignificantUpdateTime: Long = 0L,
    val isHighZoomMode: Boolean = false,
    val digitalZoomFactor: Double = 1.0,
    val panDistanceThreshold: Double = 10.0,
    val timeBasedUpdateInterval: Long = 16L // 60fps target
)

// Configuration for digital zoom updates
data class DigitalZoomUpdateConfig(
    val highZoomThreshold: Double = 2.0,
    val updateFrequencyMs: Long = 16L, // 60fps
    val panSensitivityMultiplier: Double = 1.5,
    val forcedUpdateThreshold: Double = 20.0,
    val timeBasedUpdateEnabled: Boolean = true
)

// Device-specific zoom limits
private fun getMaxDigitalZoomForDevice(): Double {
    return when {
        DeviceCapabilitiesDetector.isHighEndDevice() -> 4.0
        DeviceCapabilitiesDetector.isMidRangeDevice() -> 2.5
        else -> 2.0
    }
}

// Get maximum zoom level for the current map type provider
private fun getMaxZoomForMapType(mapType: MapType): Int {
    return when (mapType) {
        MapType.STREET -> 18  // OpenStreetMap supports up to 18 reliably
        MapType.SATELLITE -> 18  // Esri satellite supports up to 18 reliably
    }
}

// Calculate zoom-adaptive threshold for pan distance detection
private fun calculateZoomAdaptiveThreshold(digitalZoomFactor: Double, config: DigitalZoomUpdateConfig): Double {
    return when {
        digitalZoomFactor > config.highZoomThreshold -> {
            // More sensitive at higher zoom levels
            config.forcedUpdateThreshold / (digitalZoomFactor * config.panSensitivityMultiplier)
        }
        else -> config.forcedUpdateThreshold
    }.coerceAtLeast(5.0) // Minimum threshold
}

@Composable
fun OpenStreetMapView(
    currentLocation: MapLocation,
    onLocationSelected: (MapLocation) -> Unit,
    onAccuracyChanged: (Int) -> Unit,
    onTileInfoChanged: (Int, Double) -> Unit = { _, _ -> },
    mapType: MapType = MapType.STREET,
    showQiblaDirection: Boolean = true,
    onQiblaLineNeedsRedraw: () -> Unit = {},
    onPanStop: (() -> Unit)? = null,
    panelHeight: Int = 0,
    selectedLocation: MapLocation? = null,
    isMapTypeChanging: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val tileManager = remember { OpenStreetMapTileManager(context) }
    val qiblaOverlay = remember { QiblaDirectionOverlay() }

    // --- Enhanced State Management ---
    var zoom by remember { mutableStateOf(18) }  // Initial zoom level (will be validated per map type)
    var tileX by remember { mutableStateOf(0.0) }
    var tileY by remember { mutableStateOf(0.0) }
    var digitalZoom by remember { mutableStateOf(1.0) }
    var isTileLoadingPaused by remember { mutableStateOf(false) }
    var digitalZoomIndicator by remember { mutableStateOf(false) }

    // --- Tile Cache & Loading State ---
    var tileStateCache by remember(mapType) { mutableStateOf<Map<String, TileLoadState>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var forceTileReload by remember { mutableStateOf(false) }
    
    // --- Qibla Direction State ---
    var qiblaDirectionState by remember { mutableStateOf(QiblaDirectionState()) }

    // Add cleanup when mapType changes
    LaunchedEffect(mapType) {
        tileStateCache = emptyMap()
        
        // Clear Qibla direction cache when map type changes
        qiblaOverlay.clearCache()
        
        // Reset digital zoom state during map type changes
        digitalZoom = 1.0
        digitalZoomIndicator = false
        Timber.d("📍 Digital zoom reset during map type change to ${mapType.displayName}")
        
        // Validate zoom level for the new map type
        val maxTileZoom = getMaxZoomForMapType(mapType)
        if (zoom > maxTileZoom) {
            zoom = maxTileZoom
            Timber.d("📍 Adjusted zoom level to $maxTileZoom for ${mapType.displayName}")
        }
        
        // Force tile reload after map type changes
        forceTileReload = true
        Timber.d("📍 Force tile reload triggered for map type change to ${mapType.displayName}")
    }

    // --- Drag State for Continuous Loading ---
    var isDragging by remember { mutableStateOf(false) }
    var lastDragPosition by remember { mutableStateOf(Pair(0.0, 0.0)) }
    
    // --- Enhanced Pan State for High Digital Zoom ---
    var enhancedPanState by remember { mutableStateOf(EnhancedPanState()) }
    var updateConfig by remember { mutableStateOf(DigitalZoomUpdateConfig()) }
    var cumulativePanDistance by remember { mutableStateOf(0f) }
    var lastSignificantUpdateTime by remember { mutableStateOf(0L) }

    // --- Initialization ---
    LaunchedEffect(Unit) {
        QiblaPerformanceMonitor.initialize(context.applicationContext)
        QiblaPerformanceMonitor.setAdaptiveMode(true)
    }
    
    LaunchedEffect(currentLocation) {
        // Use high-precision coordinate transformation for better accuracy
        val (initialX, initialY) = PrecisionCoordinateTransformer.latLngToHighPrecisionTile(
            currentLocation.latitude, currentLocation.longitude, zoom
        )
        tileX = initialX
        tileY = initialY
    }

    // --- Digital Zoom Optimization ---
    LaunchedEffect(digitalZoom) {
        isTileLoadingPaused = digitalZoom > 1.0
    }

    // --- Performance Monitoring ---
    LaunchedEffect(digitalZoom) {
        if (digitalZoom > 2.0) {
            // Monitor memory usage using QiblaPerformanceMonitor
            val memoryInfo = QiblaPerformanceMonitor.monitorMemoryUsage()
            val tileMemoryUsage = tileManager.getCacheSizeMB(mapType)
            
            if (memoryInfo.isPressure || tileMemoryUsage > 80) {
                Timber.w("📍 High memory usage during digital zoom: System ${memoryInfo.usedMemoryMB}MB, Tiles ${tileMemoryUsage}MB")
                
                // Handle excessive memory usage
                val recoveryAction = QiblaPerformanceMonitor.handleExcessiveMemoryUsage()
                when (recoveryAction) {
                    QiblaPerformanceMonitor.MemoryRecoveryAction.EMERGENCY_STOP -> {
                        Timber.e("📍 Emergency stop - reducing digital zoom due to critical memory pressure")
                        digitalZoom = 1.5
                        QiblaPerformanceMonitor.executeMemoryRecovery(recoveryAction)
                    }
                    QiblaPerformanceMonitor.MemoryRecoveryAction.AGGRESSIVE_CLEANUP -> {
                        Timber.w("📍 Aggressive cleanup - reducing digital zoom")
                        digitalZoom = kotlin.math.min(digitalZoom * 0.8, 2.5)
                        QiblaPerformanceMonitor.executeMemoryRecovery(recoveryAction)
                    }
                    QiblaPerformanceMonitor.MemoryRecoveryAction.THROTTLE_UPDATES -> {
                        if (digitalZoom > 3.0) {
                            digitalZoom = 3.0
                        }
                        QiblaPerformanceMonitor.executeMemoryRecovery(recoveryAction)
                    }
                    else -> {
                        if (digitalZoom > 3.0) {
                            digitalZoom = 3.0
                        }
                    }
                }
            }
            
            // Log performance stats periodically (debug builds only)
            if (digitalZoom > 3.0) {
                QiblaPerformanceMonitor.logPerformanceStats(debugOnly = true)
                QiblaPerformanceMonitor.logDetailedPerformanceMetrics()
            }
        }
    }

    // --- Error Handling for Extreme Zoom ---
    LaunchedEffect(digitalZoom) {
        val maxZoom = DeviceCapabilitiesDetector.getMaxDigitalZoomFactor().toDouble()
        if (digitalZoom > maxZoom) {
            digitalZoom = maxZoom
            Timber.w("📍 Digital zoom limited to device capabilities: ${maxZoom}x")
        }
    }

    // --- Enhanced Tile Loading and Accuracy Calculation ---
    LaunchedEffect(tileX, tileY, zoom, mapType, isDragging, isMapTypeChanging, forceTileReload) {
        // Skip tile loading if paused (during digital zoom) or during interactions
        // Exception: Allow tile loading during map type changes even if normally paused
        // Exception: Allow tile loading when force reload is triggered
        if ((isTileLoadingPaused && !isMapTypeChanging && !forceTileReload) || isDragging) {
            return@LaunchedEffect
        }
        
        // Validate zoom level for current map type
        val maxTileZoom = getMaxZoomForMapType(mapType)
        if (zoom > maxTileZoom) {
            // Don't try to load tiles beyond the supported zoom level
            Timber.d("📍 Skipping tile loading - zoom level $zoom exceeds max tile zoom $maxTileZoom for ${mapType.displayName}")
            return@LaunchedEffect
        }

        // Update accuracy first
        val accuracyMeters = getAccuracyForZoomWithDigitalZoom(zoom, digitalZoom)
        onAccuracyChanged(accuracyMeters)

        // Check memory pressure before loading
        if (tileManager.shouldHandleMemoryPressure()) {
            tileManager.handleMemoryPressure()
        }

        // Load tiles with priority (visible first, then buffer)
        if (!isActive) return@LaunchedEffect
        isLoading = true
        error = null

        val bufferSize = tileManager.getBufferSizeBasedOnConnection()
        val (visibleTiles, bufferTiles) = tileManager.getTilesForViewWithPriority(tileX, tileY, zoom, 800, 800, mapType, bufferSize)
        val allTiles = visibleTiles + bufferTiles

        allTiles.forEach { tile ->
            val cacheKey = tile.toCacheKey()
            if (tileStateCache[cacheKey] !is TileLoadState.HighRes) {
                launch {
                    tileManager.loadTileProgressively(tile).collect { state ->
                        tileStateCache = tileStateCache + (cacheKey to state)
                    }
                }
            }
        }
        isLoading = false
        
        // Reset force tile reload flag after successful loading
        if (forceTileReload) {
            forceTileReload = false
            Timber.d("📍 Force tile reload completed, flag reset")
        }
    }

    // Update tile info when cache changes
    LaunchedEffect(tileStateCache) {
        onTileInfoChanged(tileStateCache.size, tileManager.getCacheSizeMB(mapType))
    }

    // --- Time-based forced updates for high digital zoom scenarios with adaptive frequency ---
    LaunchedEffect(digitalZoom, isDragging, enhancedPanState.isHighZoomMode) {
        if (enhancedPanState.isHighZoomMode && isDragging && updateConfig.timeBasedUpdateEnabled) {
            // Get device tier and check for fallback configuration
            val deviceTier = DeviceCapabilitiesDetector.getDeviceTier()
            val fallbackConfig = QiblaPerformanceMonitor.createFallbackConfig(deviceTier)
            
            while (isActive && isDragging && digitalZoom > updateConfig.highZoomThreshold) {
                // Validate memory before continuing high-frequency updates
                if (!QiblaPerformanceMonitor.validateMemoryForHighFrequencyUpdates()) {
                    // Handle memory pressure and potentially stop updates
                    val recoveryNeeded = QiblaPerformanceMonitor.checkAndHandleMemoryPressure()
                    if (recoveryNeeded) {
                        Timber.w("📍 Pausing high-frequency updates due to memory pressure")
                        kotlinx.coroutines.delay(1000) // Wait 1 second before retry
                        continue
                    }
                }
                
                // Calculate update frequency with fallback considerations
                val adaptiveFrequency = if (fallbackConfig != null) {
                    // Use fallback frequency and log the activation
                    Timber.w("📍 Fallback mode activated for device tier: $deviceTier")
                    fallbackConfig.maxUpdateFrequencyMs.coerceAtLeast(updateConfig.updateFrequencyMs)
                } else if (QiblaPerformanceMonitor.shouldThrottleForMemoryPressure()) {
                    QiblaPerformanceMonitor.recordThrottleEvent()
                    updateConfig.updateFrequencyMs * 2 // Slow down during memory pressure
                } else {
                    QiblaPerformanceMonitor.calculateAdaptiveUpdateFrequency(digitalZoom.toFloat(), deviceTier)
                }
                
                kotlinx.coroutines.delay(adaptiveFrequency)
                QiblaPerformanceMonitor.recordHighFrequencyUpdate()
                onQiblaLineNeedsRedraw()
                
                // Emergency fallback check with memory recovery
                if (!QiblaPerformanceMonitor.isPerformanceAcceptable()) {
                    val emergencyConfig = QiblaPerformanceMonitor.createEmergencyFallbackConfig()
                    Timber.e("📍 Emergency fallback activated - reducing to ${emergencyConfig.maxUpdateFrequencyMs}ms updates")
                    
                    // Attempt memory recovery
                    QiblaPerformanceMonitor.checkAndHandleMemoryPressure()
                    kotlinx.coroutines.delay(emergencyConfig.maxUpdateFrequencyMs)
                }
            }
        }
    }

    // --- Calculate Qibla Direction State ---
    LaunchedEffect(selectedLocation, tileX, tileY, zoom, digitalZoom, showQiblaDirection, isDragging, cumulativePanDistance) {
        if (showQiblaDirection) {
            // Calculate viewport bounds for efficient path clipping
            val viewportBounds = calculateViewportBounds(tileX, tileY, zoom, 800, 800)
            
            // Check for fallback configuration
            val deviceTier = DeviceCapabilitiesDetector.getDeviceTier()
            val fallbackConfig = QiblaPerformanceMonitor.createFallbackConfig(deviceTier)
            
            // Apply fallback settings if needed
            val isHighPerformanceMode = if (fallbackConfig?.simplifiedCalculations == true) {
                false // Disable high-performance mode to reduce CPU usage
            } else {
                isDragging || digitalZoom > 2.0
            }
            
            val highPrecisionEnabled = if (fallbackConfig?.disableHighPrecisionMode == true) {
                false // Disable high precision mode to reduce CPU usage
            } else {
                digitalZoom > updateConfig.highZoomThreshold
            }
            
            // Calculate Qibla direction state with fallback optimizations
            selectedLocation?.let { location ->
                // Determine update frequency based on current state and fallback config
                val updateFreq = if (fallbackConfig != null) {
                    when (deviceTier) {
                        QiblaPerformanceMonitor.DeviceTier.LOW_END -> UpdateFrequency.THROTTLED
                        QiblaPerformanceMonitor.DeviceTier.MID_RANGE -> UpdateFrequency.STANDARD
                        else -> UpdateFrequency.HIGH_FREQUENCY
                    }
                } else {
                    when {
                        enhancedPanState.isHighZoomMode && isDragging -> UpdateFrequency.HIGH_FREQUENCY
                        digitalZoom > 3f && isDragging -> UpdateFrequency.ULTRA_HIGH_FREQUENCY
                        !isDragging -> UpdateFrequency.STANDARD
                        else -> UpdateFrequency.THROTTLED
                    }
                }
                
                // Use safe calculation with recovery mechanisms
                val calculationResult = QiblaPerformanceMonitor.safeCalculation(
                    operationName = "Qibla Direction Calculation",
                    digitalZoom = digitalZoom.toFloat(),
                    isHighZoom = digitalZoom > 2.0
                ) {
                    qiblaOverlay.calculateDirectionLine(
                        userLocation = location,
                        viewportBounds = viewportBounds,
                        zoomLevel = zoom,
                        digitalZoom = digitalZoom.toFloat(),
                        isHighPerformanceMode = isHighPerformanceMode,
                        highPrecisionMode = highPrecisionEnabled,
                        updateFrequency = updateFreq
                    )
                }
                
                qiblaDirectionState = calculationResult ?: QiblaDirectionState(
                    isVisible = true,
                    isCalculationValid = false,
                    errorMessage = "Calculation failed - using recovery mode",
                    reducedComplexity = true
                )
            }
        } else {
            qiblaDirectionState = QiblaDirectionState(isVisible = false)
        }
    }

    Box(
        modifier = modifier
            .background(Color(0xFFE8F5E8))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { _ ->
                        isDragging = true
                        // Reset cumulative distance on new drag session
                        cumulativePanDistance = 0f
                        lastSignificantUpdateTime = System.currentTimeMillis()
                        
                        // Update enhanced pan state
                        enhancedPanState = enhancedPanState.copy(
                            isHighZoomMode = digitalZoom > updateConfig.highZoomThreshold,
                            digitalZoomFactor = digitalZoom,
                            panDistanceThreshold = calculateZoomAdaptiveThreshold(digitalZoom, updateConfig)
                        )
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        
                        // When digital zoom increases, drag sensitivity should decrease.
                        val dragSensitivity = (1.0 / digitalZoom).toFloat()
                        val adjustedDragAmount = Offset(
                            dragAmount.x * dragSensitivity,
                            dragAmount.y * dragSensitivity
                        )
                        
                        // Track cumulative pan distance for high zoom arrow positioning with double precision
                        val panDistance = kotlin.math.sqrt(
                            (dragAmount.x * dragAmount.x + dragAmount.y * dragAmount.y).toDouble()
                        ).toFloat()
                        cumulativePanDistance += panDistance
                        
                        // Enhanced sensitivity thresholds based on digital zoom level
                        val currentTime = System.currentTimeMillis()
                        val timeSinceLastUpdate = currentTime - lastSignificantUpdateTime
                        val shouldForceUpdate = cumulativePanDistance >= enhancedPanState.panDistanceThreshold ||
                                (enhancedPanState.isHighZoomMode && timeSinceLastUpdate >= updateConfig.updateFrequencyMs)
                        
                        // Use double precision arithmetic for tile coordinate updates
                        tileX -= adjustedDragAmount.x.toDouble() / TILE_SIZE
                        tileY -= adjustedDragAmount.y.toDouble() / TILE_SIZE
                        val (newLat, newLng) = PrecisionCoordinateTransformer.highPrecisionTileToLatLng(tileX, tileY, zoom)
                        onLocationSelected(MapLocation(newLat, newLng))

                        lastDragPosition = Pair(tileX, tileY)
                        
                        // Forced update logic when cumulative distance exceeds threshold
                        if (shouldForceUpdate) {
                            onQiblaLineNeedsRedraw()
                            cumulativePanDistance = 0f // Reset on significant updates
                            lastSignificantUpdateTime = currentTime
                        }
                    },
                    onDragEnd = {
                        isDragging = false
                        // Enhanced pan state cleanup
                        cumulativePanDistance = 0f
                        lastSignificantUpdateTime = 0L
                        enhancedPanState = enhancedPanState.copy(isHighZoomMode = false)
                        
                        // Trigger tile loading after drag ends
                        lastDragPosition = Pair(tileX, tileY)
                        // Trigger progressive auto-refresh
                        onPanStop?.invoke()
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = size.width / 2f
            val centerY = size.height / 2f

            withTransform({
                scale(digitalZoom.toFloat(), digitalZoom.toFloat(), pivot = Offset(centerX, centerY))
            }) {
                val fractionalTileX = tileX - floor(tileX)
                val fractionalTileY = tileY - floor(tileY)

                val tileOffsetX = (fractionalTileX * TILE_SIZE).toFloat()
                val tileOffsetY = (fractionalTileY * TILE_SIZE).toFloat()

                // Center point of the canvas, this is where the pin is
                val canvasCenter = Offset(size.width / 2f, size.height / 2f)

                tileStateCache.forEach { (cacheKey, state) ->
                    val tile = parseTileCacheKey(cacheKey)
                    if (tile != null && tile.mapType == mapType) {
                        val drawX = (tile.x - floor(tileX)).toFloat() * TILE_SIZE.toFloat() - tileOffsetX + canvasCenter.x
                        val drawY = (tile.y - floor(tileY)).toFloat() * TILE_SIZE.toFloat() - tileOffsetY + canvasCenter.y

                        when (state) {
                            is TileLoadState.Loading -> {
                                // Optional: Draw a placeholder rectangle
                                drawRect(
                                    color = Color.LightGray,
                                    topLeft = Offset(drawX, drawY),
                                    size = androidx.compose.ui.geometry.Size(TILE_SIZE.toFloat(), TILE_SIZE.toFloat())
                                )
                            }
                            is TileLoadState.LowRes -> {
                                // Draw the low-res bitmap, it will be scaled up by the canvas transform
                                drawImage(state.bitmap.asImageBitmap(), topLeft = Offset(drawX, drawY))
                            }
                            is TileLoadState.HighRes -> {
                                // Draw the final, sharp high-res bitmap
                                drawImage(state.bitmap.asImageBitmap(), topLeft = Offset(drawX, drawY))
                            }
                            is TileLoadState.Failed -> {
                                // Optional: Draw an error indicator
                                drawRect(
                                    color = Color.Gray,
                                    topLeft = Offset(drawX, drawY),
                                    size = androidx.compose.ui.geometry.Size(TILE_SIZE.toFloat(), TILE_SIZE.toFloat())
                                )
                                // You could also draw an 'X' or other indicator
                            }
                        }
                    }
                }
            }

            // --- Draw Qibla Direction Line (above tiles, below UI controls) ---
            if (showQiblaDirection) {
                if (qiblaDirectionState.isVisible && qiblaDirectionState.isCalculationValid) {
                    qiblaOverlay.renderDirectionLine(
                        drawScope = this,
                        directionState = qiblaDirectionState,
                        centerOffset = Offset(centerX, centerY),
                        tileX = tileX,
                        tileY = tileY,
                        zoom = zoom,
                        digitalZoom = digitalZoom.toFloat(),
                        mapType = mapType,
                        selectiveRenderingMode = enhancedPanState.isHighZoomMode && isDragging
                    )
                } else if (!qiblaDirectionState.isCalculationValid) {
                    // Draw error indicator for failed Qibla calculation
                    val errorColor = Color.Red.copy(alpha = 0.7f)
                    val warningSize = 20f
                    
                    // Draw warning triangle at center
                    drawCircle(
                        color = errorColor,
                        radius = warningSize,
                        center = Offset(centerX, centerY + 40f),
                        style = Stroke(width = 3f)
                    )
                    
                    // Draw exclamation mark
                    drawLine(
                        color = errorColor,
                        start = Offset(centerX, centerY + 30f),
                        end = Offset(centerX, centerY + 45f),
                        strokeWidth = 3f
                    )
                    drawCircle(
                        color = errorColor,
                        radius = 2f,
                        center = Offset(centerX, centerY + 50f)
                    )
                }
            }

            // --- Draw Location Pin (always at the center of the screen) ---
            val pinColor = Color.Red
            drawCircle(color = pinColor, radius = 15f, center = Offset(centerX, centerY))
            drawCircle(color = Color.White, radius = 3f, center = Offset(centerX, centerY))

            // --- Draw Accuracy Circle (around the pin) ---
            val accuracyInMeters = getAccuracyForZoomWithDigitalZoom(zoom, digitalZoom)
            val accuracyInPixels = (tileManager.metersToPixels(accuracyInMeters.toFloat(), zoom) * digitalZoom).toFloat()
            drawCircle(color = Color.Red.copy(alpha = 0.1f), radius = accuracyInPixels, center = Offset(centerX, centerY))
            drawCircle(color = Color.Red, radius = accuracyInPixels, center = Offset(centerX, centerY), style = Stroke(width = 2f))
        }

        if (isLoading && tileStateCache.isEmpty()) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        // --- Digital Zoom Indicator ---
        if (digitalZoomIndicator) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
                )
            ) {
                Text(
                    text = "Digital Zoom ${String.format("%.1fx", digitalZoom)}",
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        
        // --- Memory Pressure and Performance Indicators ---
        if (showQiblaDirection && (qiblaDirectionState.hasMemoryPressure || qiblaDirectionState.reducedComplexity)) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 16.dp, top = if (digitalZoomIndicator) 80.dp else 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (qiblaDirectionState.hasMemoryPressure) 
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f)
                    else 
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f)
                )
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    if (qiblaDirectionState.hasMemoryPressure) {
                        Text(
                            text = "⚠️ Memory Pressure",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    if (qiblaDirectionState.reducedComplexity) {
                        Text(
                            text = "🔧 Simplified Mode",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (qiblaDirectionState.hasMemoryPressure) 
                                MaterialTheme.colorScheme.onErrorContainer
                            else 
                                MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
        
        // --- Qibla Calculation Error Indicator ---
        if (showQiblaDirection && !qiblaDirectionState.isCalculationValid && qiblaDirectionState.errorMessage != null) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
                    .widthIn(max = 250.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.95f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "⚠️",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = qiblaDirectionState.errorMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // --- UI Elements (Zoom, Info) ---
        val density = LocalDensity.current
        val dynamicTopPadding = with(density) { panelHeight.toDp() + 16.dp }
        Column(modifier = Modifier.align(Alignment.TopEnd).padding(top = dynamicTopPadding, end = 16.dp)) {
            IconButton(
                onClick = {
                    val maxTileZoom = getMaxZoomForMapType(mapType)
                    if (zoom < maxTileZoom) {
                        val newZoom = zoom + 1
                        val (currentLat, currentLng) = PrecisionCoordinateTransformer.highPrecisionTileToLatLng(tileX, tileY, zoom)
                        val (newTileX, newTileY) = PrecisionCoordinateTransformer.latLngToHighPrecisionTile(currentLat, currentLng, newZoom)
                        zoom = newZoom
                        tileX = newTileX
                        tileY = newTileY
                        digitalZoom = 1.0
                        digitalZoomIndicator = false
                        onLocationSelected(MapLocation(currentLat, currentLng))
                    } else {
                        // Use digital zoom when tile zoom limit is reached
                        val maxDigitalZoom = getMaxDigitalZoomForDevice()
                        if (digitalZoom < maxDigitalZoom) {
                            digitalZoom = kotlin.math.min(digitalZoom * DIGITAL_ZOOM_STEP, maxDigitalZoom)
                            digitalZoomIndicator = true
                        }
                    }
                },
                modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.primary, CircleShape)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Zoom In", tint = Color.White)
            }
            Spacer(modifier = Modifier.height(8.dp))
            IconButton(
                onClick = {
                    if (digitalZoom > 1.0) {
                        digitalZoom = kotlin.math.max(digitalZoom / DIGITAL_ZOOM_STEP, 1.0)
                        if (digitalZoom <= 1.0) {
                            digitalZoom = 1.0
                            digitalZoomIndicator = false
                        }
                    } else {
                        val newZoom = kotlin.math.max(zoom - 1, MIN_ZOOM_LEVEL)
                        val (currentLat, currentLng) = PrecisionCoordinateTransformer.highPrecisionTileToLatLng(tileX, tileY, zoom)
                        val (newTileX, newTileY) = PrecisionCoordinateTransformer.latLngToHighPrecisionTile(currentLat, currentLng, newZoom)
                        zoom = newZoom
                        tileX = newTileX
                        tileY = newTileY
                        onLocationSelected(MapLocation(currentLat, currentLng))
                    }
                },
                modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.primary, CircleShape)
            ) {
                Icon(Icons.Filled.Remove, contentDescription = "Zoom Out", tint = Color.White)
            }
        }
    }
}

private fun parseTileCacheKey(key: String): TileCoordinate? {
    return try {
        val parts = key.split("_")
        when (parts.size) {
            3 -> {
                val zoom = parts[0].toInt()
                val x = parts[1].toInt()
                val y = parts[2].toInt()
                TileCoordinate(x, y, zoom, MapType.STREET)
            }
            4 -> {
                val mapType = MapType.valueOf(parts[0].uppercase())
                val zoom = parts[1].toInt()
                val x = parts[2].toInt()
                val y = parts[3].toInt()
                TileCoordinate(x, y, zoom, mapType)
            }
            else -> null
        }
    } catch (e: Exception) {
        Timber.e(e, "Failed to parse tile cache key: $key")
        null
    }
}

private fun getAccuracyForZoom(zoom: Int): Int {
    return when {
        zoom >= 18 -> 5
        zoom >= 17 -> 10
        zoom >= 16 -> 20
        zoom >= 15 -> 40
        zoom >= 14 -> 80
        zoom >= 13 -> 150
        zoom >= 12 -> 300
        zoom >= 11 -> 600
        else -> 1000
    }
}

private fun getAccuracyForZoomWithDigitalZoom(zoom: Int, digitalZoom: Double): Int {
    val baseAccuracy = getAccuracyForZoom(zoom)
    return (baseAccuracy / digitalZoom).toInt().coerceAtLeast(1)
}

/**
 * Calculates viewport bounds for the current map view
 */
private fun calculateViewportBounds(
    centerTileX: Double,
    centerTileY: Double,
    zoom: Int,
    viewportWidth: Int,
    viewportHeight: Int
): ViewportBounds {
    val tileSize = 256.0
    
    // Calculate how many tiles are visible in each direction
    val tilesVisibleX = viewportWidth / tileSize
    val tilesVisibleY = viewportHeight / tileSize
    
    // Calculate bounds in tile coordinates
    val minTileX = centerTileX - tilesVisibleX / 2
    val maxTileX = centerTileX + tilesVisibleX / 2
    val minTileY = centerTileY - tilesVisibleY / 2
    val maxTileY = centerTileY + tilesVisibleY / 2
    
    // Convert tile coordinates to lat/lng
    val westLon = tileXToLongitude(minTileX, zoom)
    val eastLon = tileXToLongitude(maxTileX, zoom)
    val northLat = tileYToLatitude(minTileY, zoom)
    val southLat = tileYToLatitude(maxTileY, zoom)
    val centerLat = tileYToLatitude(centerTileY, zoom)
    val centerLon = tileXToLongitude(centerTileX, zoom)
    
    return ViewportBounds(
        northLat = northLat,
        southLat = southLat,
        eastLon = eastLon,
        westLon = westLon,
        centerLat = centerLat,
        centerLon = centerLon
    )
}

/**
 * Converts tile X coordinate to longitude
 */
private fun tileXToLongitude(tileX: Double, zoom: Int): Double {
    return tileX / (1 shl zoom).toDouble() * 360.0 - 180.0
}

/**
 * Converts tile Y coordinate to latitude
 */
private fun tileYToLatitude(tileY: Double, zoom: Int): Double {
    val n = PI - 2.0 * PI * tileY / (1 shl zoom).toDouble()
    return 180.0 / PI * atan(0.5 * (exp(n) - exp(-n)))
}
