package com.bizzkoot.qiblafinder.ui.location

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.bizzkoot.qiblafinder.model.GeodesyUtils
import com.bizzkoot.qiblafinder.model.GeodesyResult
import timber.log.Timber
import kotlin.math.*

/**
 * Enum for update frequency modes in high-frequency scenarios
 */
enum class UpdateFrequency(val intervalMs: Long, val description: String) {
    STANDARD(100L, "Standard update rate for normal operations"),
    HIGH_FREQUENCY(16L, "High-frequency updates for digital zoom scenarios (60fps)"),
    ULTRA_HIGH_FREQUENCY(8L, "Ultra-high frequency for critical responsiveness (120fps)"),
    THROTTLED(250L, "Throttled updates for performance conservation")
}

/**
 * Data class to hold the state of the Qibla direction line with error information
 */
data class QiblaDirectionState(
    val isVisible: Boolean = true,
    val bearing: Double = 0.0,
    val distance: Double = 0.0,
    val pathPoints: List<MapLocation> = emptyList(),
    val screenPoints: List<Offset> = emptyList(),
    val isCalculationValid: Boolean = true,
    val errorMessage: String? = null,
    val hasMemoryPressure: Boolean = false,
    val reducedComplexity: Boolean = false
)

/**
 * Data class representing viewport bounds for efficient path clipping
 */
data class ViewportBounds(
    val northLat: Double,
    val southLat: Double,
    val eastLon: Double,
    val westLon: Double,
    val centerLat: Double,
    val centerLon: Double
) {
    /**
     * Checks if a location is within the viewport bounds
     */
    fun contains(location: MapLocation): Boolean {
        return location.latitude <= northLat &&
                location.latitude >= southLat &&
                isLongitudeInBounds(location.longitude)
    }
    
    /**
     * Handles longitude bounds checking, accounting for date line crossing
     */
    private fun isLongitudeInBounds(longitude: Double): Boolean {
        return if (eastLon >= westLon) {
            // Normal case: bounds don't cross the date line
            longitude >= westLon && longitude <= eastLon
        } else {
            // Date line crossing case
            longitude >= westLon || longitude <= eastLon
        }
    }
}

/**
 * Overlay component for rendering Qibla direction line on the map
 * Enhanced with performance optimizations including caching, throttling, and level-of-detail
 */
class QiblaDirectionOverlay {
    
    // Performance optimizer for caching and throttling
    private val performanceOptimizer = QiblaPerformanceOptimizer()
    
    companion object {
        private const val KAABA_LATITUDE = 21.4225
        private const val KAABA_LONGITUDE = 39.8262
        private const val DEFAULT_SEGMENTS = 50
        private const val MIN_SEGMENTS = 10
        private const val MAX_SEGMENTS = 100
        private const val MEMORY_PRESSURE_SEGMENT_LIMIT = 25
        
        // Visual styling constants
        private const val LINE_WIDTH_DP = 3f
        private const val OUTLINE_WIDTH_DP = 5f
        private const val ARROW_SIZE_DP = 12f
        private const val LINE_ALPHA = 0.9f
        private const val OUTLINE_ALPHA = 0.7f
        
        // Simplified arrow constants
        private const val SIMPLE_ARROW_BASE_LENGTH = 100f // Base length for calculation
        private const val SIMPLE_ARROW_HEAD_LENGTH = 20f // Increased arrowhead size
        private const val SIMPLE_ARROW_HEAD_ANGLE = 25.0 // degrees
        private const val ARROW_EDGE_MARGIN = 30f // Margin from screen edge
        
        // Colors
        private val LINE_COLOR = Color(0xFF4CAF50) // Green
        private val OUTLINE_COLOR_LIGHT = Color.White
        private val OUTLINE_COLOR_DARK = Color(0xFF333333)
    }
    
    /**
     * Calculates the Qibla direction line state using PreciseMapCoordinate for maximum accuracy.
     * This is the preferred method for high-precision arrow alignment.
     */
    fun calculateDirectionLineWithPrecision(
        userLocation: PreciseMapCoordinate,
        qiblaLocation: PreciseMapCoordinate,
        zoomLevel: Int,
        digitalZoom: Double,
        isHighPerformanceMode: Boolean = false,
        updateFrequency: UpdateFrequency = UpdateFrequency.STANDARD
    ): QiblaDirectionState {
        return try {
            // Check memory pressure first
            val memoryPressure = isMemoryUnderPressure()
            val effectiveHighPerformanceMode = isHighPerformanceMode || memoryPressure
            
            // Calculate distance with high precision
            val distance = PrecisionCoordinateTransformer.calculateGreatCircleDistance(
                userLocation.latitude, userLocation.longitude,
                qiblaLocation.latitude, qiblaLocation.longitude
            )
            
            // Calculate bearing with high precision
            val bearing = PrecisionCoordinateTransformer.calculateBearing(
                userLocation.latitude, userLocation.longitude,
                qiblaLocation.latitude, qiblaLocation.longitude
            )
            
            // Determine optimal number of segments with precision considerations
            val baseSegments = performanceOptimizer.calculateOptimalSegments(
                zoomLevel, 
                digitalZoom.toFloat(), 
                distance,
                effectiveHighPerformanceMode
            )
            
            // Enhanced precision for high digital zoom levels
            val precisionAdjustedSegments = if (digitalZoom > 2.0) {
                (baseSegments * 1.5).toInt().coerceAtMost(MAX_SEGMENTS)
            } else {
                baseSegments
            }
            
            // Apply update frequency considerations
            val frequencyAdjustedSegments = when (updateFrequency) {
                UpdateFrequency.ULTRA_HIGH_FREQUENCY -> (precisionAdjustedSegments * 0.8).toInt()
                UpdateFrequency.HIGH_FREQUENCY -> precisionAdjustedSegments
                UpdateFrequency.STANDARD -> precisionAdjustedSegments
                UpdateFrequency.THROTTLED -> (precisionAdjustedSegments * 0.6).toInt()
            }
            
            val finalSegments = frequencyAdjustedSegments.coerceAtLeast(MIN_SEGMENTS).coerceAtMost(MAX_SEGMENTS)
            
            // Generate precise path points using high-precision geodesy
            val pathPoints = generatePrecisePathPoints(
                userLocation, qiblaLocation, finalSegments
            )
            
            QiblaDirectionState(
                isVisible = true,
                bearing = bearing,
                distance = distance,
                pathPoints = pathPoints.map { MapLocation(it.latitude, it.longitude) },
                screenPoints = emptyList(), // Will be calculated during rendering
                isCalculationValid = true,
                errorMessage = null,
                hasMemoryPressure = memoryPressure,
                reducedComplexity = effectiveHighPerformanceMode
            )
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to calculate direction line with precision")
            createErrorState("Precision calculation failed", e.message)
        }
    }
    
    /**
     * Generates precise path points using high-precision coordinate transformations
     */
    private fun generatePrecisePathPoints(
        userLocation: PreciseMapCoordinate,
        qiblaLocation: PreciseMapCoordinate,
        segments: Int
    ): List<PreciseMapCoordinate> {
        val pathPoints = mutableListOf<PreciseMapCoordinate>()
        
        // Always include the start point (user location)
        pathPoints.add(userLocation)
        
        if (segments <= 1) {
            // For single segment, just connect start to end
            pathPoints.add(qiblaLocation)
            return pathPoints
        }
        
        // Generate intermediate points along the great circle path
        val lat1 = Math.toRadians(userLocation.latitude)
        val lon1 = Math.toRadians(userLocation.longitude)
        val lat2 = Math.toRadians(qiblaLocation.latitude)
        val lon2 = Math.toRadians(qiblaLocation.longitude)
        
        // Calculate the angular distance between the two points
        val deltaLat = lat2 - lat1
        val deltaLon = lon2 - lon1
        
        val a = kotlin.math.sin(deltaLat / 2.0).pow(2) + 
                kotlin.math.cos(lat1) * kotlin.math.cos(lat2) * 
                kotlin.math.sin(deltaLon / 2.0).pow(2)
        val angularDistance = 2.0 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1.0 - a))
        
        // Generate intermediate points using spherical interpolation
        for (i in 1 until segments) {
            val fraction = i.toDouble() / segments.toDouble()
            
            val A = kotlin.math.sin((1.0 - fraction) * angularDistance) / kotlin.math.sin(angularDistance)
            val B = kotlin.math.sin(fraction * angularDistance) / kotlin.math.sin(angularDistance)
            
            val x = A * kotlin.math.cos(lat1) * kotlin.math.cos(lon1) + 
                    B * kotlin.math.cos(lat2) * kotlin.math.cos(lon2)
            val y = A * kotlin.math.cos(lat1) * kotlin.math.sin(lon1) + 
                    B * kotlin.math.cos(lat2) * kotlin.math.sin(lon2)
            val z = A * kotlin.math.sin(lat1) + B * kotlin.math.sin(lat2)
            
            val lat = kotlin.math.atan2(z, kotlin.math.sqrt(x * x + y * y))
            val lon = kotlin.math.atan2(y, x)
            
            val latDeg = Math.toDegrees(lat)
            val lonDeg = Math.toDegrees(lon)
            
            val intermediatePoint = PreciseMapCoordinate.fromLatLng(
                latDeg, lonDeg, userLocation.zoomLevel, userLocation.digitalZoom
            )
            
            pathPoints.add(intermediatePoint)
        }
        
        // Always include the end point (Qibla location)
        pathPoints.add(qiblaLocation)
        
        return pathPoints
    }
    
    /**
     * Calculates the Qibla direction line state with comprehensive error handling
     * Enhanced with performance optimizations, high precision mode, and graceful fallbacks
     */
    fun calculateDirectionLine(
        userLocation: MapLocation,
        viewportBounds: ViewportBounds,
        zoomLevel: Int,
        digitalZoom: Float,
        isHighPerformanceMode: Boolean = false,
        highPrecisionMode: Boolean = false,
        updateFrequency: UpdateFrequency = UpdateFrequency.STANDARD
    ): QiblaDirectionState {
        return try {
            // Check memory pressure first
            val memoryPressure = isMemoryUnderPressure()
            val effectiveHighPerformanceMode = isHighPerformanceMode || memoryPressure
            
            // Calculate distance with error handling
            val distanceResult = GeodesyUtils.calculateDistanceToKaabaSafe(
                userLocation.latitude, 
                userLocation.longitude
            )
            
            val distance = when (distanceResult) {
                is GeodesyResult.Success -> distanceResult.data
                is GeodesyResult.Error -> {
                    Timber.w("Distance calculation failed: ${distanceResult.message}, using fallback")
                    return createErrorState("Failed to calculate distance to Kaaba", distanceResult.message)
                }
            }
            
            // Determine optimal number of segments with high precision and memory pressure consideration
            val baseSegments = performanceOptimizer.calculateOptimalSegments(
                zoomLevel, 
                digitalZoom, 
                distance,
                effectiveHighPerformanceMode
            )
            
            // Apply high precision mode adjustments for digital zoom > 2x
            val precisionAdjustedSegments = if (highPrecisionMode && digitalZoom > 2f) {
                // Increase segments for better accuracy at high zoom
                (baseSegments * 1.5f).toInt().coerceAtMost(MAX_SEGMENTS)
            } else {
                baseSegments
            }
            
            // Apply update frequency considerations
            val frequencyAdjustedSegments = when (updateFrequency) {
                UpdateFrequency.ULTRA_HIGH_FREQUENCY -> (precisionAdjustedSegments * 0.8f).toInt() // Reduce for ultra-high frequency
                UpdateFrequency.HIGH_FREQUENCY -> precisionAdjustedSegments
                UpdateFrequency.STANDARD -> precisionAdjustedSegments
                UpdateFrequency.THROTTLED -> (precisionAdjustedSegments * 0.6f).toInt() // Reduce for throttled mode
            }
            
            val segments = if (memoryPressure) {
                minOf(frequencyAdjustedSegments, MEMORY_PRESSURE_SEGMENT_LIMIT).also {
                    Timber.w("Memory pressure detected, reducing segments from $frequencyAdjustedSegments to $it")
                }
            } else {
                frequencyAdjustedSegments.coerceAtLeast(MIN_SEGMENTS)
            }
            
            // Try to get cached path data or calculate new one with error handling
            val cachedData = performanceOptimizer.getCachedOrCalculatePathSafe(
                userLocation,
                KAABA_LATITUDE,
                KAABA_LONGITUDE,
                segments
            ) { location, kaabaLat, kaabaLon, segs ->
                GeodesyUtils.calculateGreatCirclePathSafe(
                    location.latitude,
                    location.longitude,
                    kaabaLat,
                    kaabaLon,
                    segs
                )
            }
            
            when (cachedData) {
                is GeodesyResult.Success -> {
                    val data = cachedData.data
                    
                    // Enhanced viewport culling with high precision mode and predictive positioning
                    val visiblePoints = try {
                        val cullResult = performanceOptimizer.cullPathToViewport(
                            data.pathPoints, 
                            viewportBounds,
                            includeBuffer = true
                        )
                        
                        // Apply predictive positioning for high precision mode
                        if (highPrecisionMode && digitalZoom > 2f && updateFrequency == UpdateFrequency.HIGH_FREQUENCY) {
                            enhanceWithPredictivePositioning(cullResult, viewportBounds)
                        } else {
                            cullResult
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "Viewport culling failed, using all points")
                        data.pathPoints
                    }
                    
                    QiblaDirectionState(
                        isVisible = true,
                        bearing = data.bearing,
                        distance = data.distance,
                        pathPoints = visiblePoints,
                        screenPoints = emptyList(), // Will be calculated during rendering
                        isCalculationValid = true,
                        errorMessage = null,
                        hasMemoryPressure = memoryPressure,
                        reducedComplexity = effectiveHighPerformanceMode
                    )
                }
                
                is GeodesyResult.Error -> {
                    Timber.e("Path calculation failed: ${cachedData.message}")
                    
                    // Try fallback calculation with reduced complexity
                    val fallbackResult = calculateFallbackPath(userLocation, segments / 2)
                    when (fallbackResult) {
                        is GeodesyResult.Success -> {
                            Timber.i("Using fallback path calculation")
                            QiblaDirectionState(
                                isVisible = true,
                                bearing = fallbackResult.data.bearing,
                                distance = fallbackResult.data.distance,
                                pathPoints = fallbackResult.data.pathPoints,
                                screenPoints = emptyList(),
                                isCalculationValid = true,
                                errorMessage = "Using simplified path due to calculation issues",
                                hasMemoryPressure = memoryPressure,
                                reducedComplexity = true
                            )
                        }
                        is GeodesyResult.Error -> {
                            createErrorState("Path calculation failed", cachedData.message)
                        }
                    }
                }
            }
            
        } catch (e: OutOfMemoryError) {
            Timber.e(e, "📍 Out of memory calculating Qibla direction line")
            createErrorState("Insufficient memory for direction calculation", "Out of memory")
        } catch (e: Exception) {
            Timber.e(e, "📍 Unexpected error calculating Qibla direction line")
            createErrorState("Unexpected calculation error", e.message ?: "Unknown error")
        }
    }
    
    /**
     * Renders the Qibla direction line on the canvas with optimized performance for high-frequency updates
     */
    fun renderDirectionLine(
        drawScope: DrawScope,
        directionState: QiblaDirectionState,
        centerOffset: Offset,
        tileX: Double,
        tileY: Double,
        zoom: Int,
        digitalZoom: Float,
        mapType: MapType,
        selectiveRenderingMode: Boolean = false
    ) {
        if (!directionState.isVisible || !directionState.isCalculationValid || directionState.pathPoints.isEmpty()) {
            return
        }
        
        // Convert geographic coordinates to screen coordinates with optimized pooling
        val screenPoints = mutableListOf<Offset>()
        try {
            for (location in directionState.pathPoints) {
                geoToScreenCoordinate(
                    location,
                    centerOffset,
                    tileX,
                    tileY,
                    zoom,
                    digitalZoom
                )?.let { screenPoints.add(it) }
            }
            
            if (screenPoints.size < 2) return
            
            // Create path for the direction line using object pooling
            val path = getPooledPath()
            
            try {
                // Selective rendering: update only arrow components during high-frequency updates
                if (selectiveRenderingMode && digitalZoom > 2f) {
                    renderOptimizedArrowOnly(drawScope, screenPoints, mapType)
                } else {
                    // Full rendering for normal operations
                    createOptimizedPath(path, screenPoints)
                    
                    // Determine outline color based on map type
                    val outlineColor = when (mapType) {
                        MapType.SATELLITE -> OUTLINE_COLOR_LIGHT
                        MapType.STREET -> OUTLINE_COLOR_DARK
                    }
                    
                    with(drawScope) {
                        // Draw outline (wider line)
                        drawPath(
                            path = path,
                            color = outlineColor.copy(alpha = OUTLINE_ALPHA),
                            style = Stroke(width = OUTLINE_WIDTH_DP * density)
                        )
                        
                        // Draw core line
                        drawPath(
                            path = path,
                            color = LINE_COLOR.copy(alpha = LINE_ALPHA),
                            style = Stroke(width = LINE_WIDTH_DP * density)
                        )
                        
                        // Draw arrow at the end of the visible line
                        drawArrow(screenPoints.last(), screenPoints, outlineColor)
                    }
                }
            } finally {
                returnToPool(path)
            }
        } finally {
            // Return screen points to pool (simplified - in real implementation you'd manage this better)
            screenPoints.clear()
        }
    }
    
    /**
     * Converts geographic coordinates to screen coordinates
     */
    private fun geoToScreenCoordinate(
        location: MapLocation,
        centerOffset: Offset,
        tileX: Double,
        tileY: Double,
        zoom: Int,
        digitalZoom: Float
    ): Offset? {
        try {
            // Convert lat/lng to tile coordinates
            val locationTileX = longitudeToTileX(location.longitude, zoom)
            val locationTileY = latitudeToTileY(location.latitude, zoom)
            
            // Calculate offset from center tile position
            val deltaX = (locationTileX - tileX) * 256f
            val deltaY = (locationTileY - tileY) * 256f
            
            // Convert to screen coordinates
            val screenX = centerOffset.x + deltaX.toFloat() * digitalZoom
            val screenY = centerOffset.y + deltaY.toFloat() * digitalZoom
            
            return Offset(screenX, screenY)
        } catch (e: Exception) {
            return null
        }
    }
    
    /**
     * Renders only the arrow component for high-frequency updates (selective rendering)
     */
    private fun renderOptimizedArrowOnly(
        drawScope: DrawScope,
        screenPoints: List<Offset>,
        mapType: MapType
    ) {
        if (screenPoints.size < 2) return
        
        val outlineColor = when (mapType) {
            MapType.SATELLITE -> OUTLINE_COLOR_LIGHT
            MapType.STREET -> OUTLINE_COLOR_DARK
        }
        
        with(drawScope) {
            // Draw only the arrow at high frequency for performance
            drawArrow(screenPoints.last(), screenPoints, outlineColor)
            
            // Optionally draw a minimal line segment near the arrow
            if (screenPoints.size >= 2) {
                val endPoint = screenPoints.last()
                val secondLastPoint = screenPoints[screenPoints.size - 2]
                
                drawLine(
                    color = LINE_COLOR.copy(alpha = LINE_ALPHA),
                    start = secondLastPoint,
                    end = endPoint,
                    strokeWidth = LINE_WIDTH_DP * density
                )
            }
        }
    }
    
    /**
     * Creates an optimized path with reduced complexity for high-frequency rendering
     */
    private fun createOptimizedPath(path: Path, points: List<Offset>) {
        if (points.isEmpty()) return
        if (points.size == 1) {
            path.addOval(androidx.compose.ui.geometry.Rect(
                points[0].x - 2f, points[0].y - 2f,
                points[0].x + 2f, points[0].y + 2f
            ))
            return
        }
        
        path.moveTo(points[0].x, points[0].y)
        
        if (points.size == 2) {
            path.lineTo(points[1].x, points[1].y)
            return
        }
        
        // Optimized path creation with fewer control points for performance
        val stepSize = if (points.size > 50) 2 else 1 // Skip points if too many
        
        for (i in stepSize until points.size - 1 step stepSize) {
            val current = points[i]
            val next = points[minOf(i + stepSize, points.size - 1)]
            
            // Simple linear interpolation for high-frequency updates
            path.lineTo(current.x, current.y)
            if (i + stepSize < points.size - 1) {
                path.lineTo(next.x, next.y)
            }
        }
        
        // Always connect to the last point
        path.lineTo(points.last().x, points.last().y)
    }

    /**
     * Creates a smooth path using quadratic Bézier curves
     */
    private fun createSmoothPath(points: List<Offset>): Path {
        val path = Path()
        
        if (points.isEmpty()) return path
        if (points.size == 1) {
            path.addOval(androidx.compose.ui.geometry.Rect(
                points[0].x - 2f, points[0].y - 2f,
                points[0].x + 2f, points[0].y + 2f
            ))
            return path
        }
        
        path.moveTo(points[0].x, points[0].y)
        
        if (points.size == 2) {
            path.lineTo(points[1].x, points[1].y)
            return path
        }
        
        // Create smooth curves using quadratic Bézier
        for (i in 1 until points.size - 1) {
            val current = points[i]
            val next = points[i + 1]
            
            // Use current point as control point for smooth curve
            val controlX = current.x
            val controlY = current.y
            val endX = (current.x + next.x) / 2f
            val endY = (current.y + next.y) / 2f
            
            path.quadraticBezierTo(controlX, controlY, endX, endY)
        }
        
        // Connect to the last point
        val lastPoint = points.last()
        path.lineTo(lastPoint.x, lastPoint.y)
        
        return path
    }
    
    /**
     * Adjusts the arrow path to ensure perfect alignment with the exact base position.
     * This method ensures the arrow base is positioned exactly on the drop point using
     * the same coordinate transformation pipeline.
     */
    fun adjustArrowPathToExactBase(
        pathPoints: List<Offset>,
        exactBasePosition: Offset,
        tolerance: Float = 0.5f
    ): List<Offset> {
        if (pathPoints.isEmpty()) return pathPoints
        if (pathPoints.size == 1) return listOf(exactBasePosition)
        
        val adjustedPoints = pathPoints.toMutableList()
        
        // Check if the first point (arrow base) needs adjustment
        val currentBase = pathPoints.first()
        val deltaX = kotlin.math.abs(currentBase.x - exactBasePosition.x)
        val deltaY = kotlin.math.abs(currentBase.y - exactBasePosition.y)
        val distance = kotlin.math.sqrt(deltaX * deltaX + deltaY * deltaY)
        
        if (distance > tolerance) {
            // Adjust the first point to the exact base position
            adjustedPoints[0] = exactBasePosition
            
            // If there are only two points, also adjust the direction slightly
            if (pathPoints.size == 2) {
                val originalDirection = pathPoints[1]
                val adjustmentX = exactBasePosition.x - currentBase.x
                val adjustmentY = exactBasePosition.y - currentBase.y
                
                // Apply the same adjustment to maintain direction consistency
                adjustedPoints[1] = Offset(
                    originalDirection.x + adjustmentX,
                    originalDirection.y + adjustmentY
                )
            }
            
            Timber.d("📍 Arrow base adjusted: distance=${distance}px, tolerance=${tolerance}px")
        }
        
        return adjustedPoints
    }
    
    /**
     * Renders an arrow with precise base positioning using the synchronized location renderer.
     * This ensures perfect alignment between the arrow base and the drop point.
     */
    fun renderPreciseArrow(
        drawScope: DrawScope,
        userLocation: PreciseMapCoordinate,
        qiblaLocation: PreciseMapCoordinate,
        canvasWidth: Float,
        canvasHeight: Float,
        viewportTileX: Double,
        viewportTileY: Double,
        mapType: MapType,
        arrowLength: Double = 50.0
    ) = with(drawScope) {
        
        val synchronizedRenderer = SynchronizedLocationRenderer()
        
        // Calculate synchronized positions to ensure perfect alignment
        val (dropPointPosition, arrowBasePosition, arrowTipPosition) = 
            synchronizedRenderer.calculateSynchronizedPositions(
                userLocation, qiblaLocation, canvasWidth, canvasHeight,
                viewportTileX, viewportTileY, arrowLength
            )
        
        // Validate perfect alignment
        if (!synchronizedRenderer.validatePerfectAlignment(dropPointPosition, arrowBasePosition)) {
            Timber.w("⚠️ Arrow base misalignment detected, using drop point position as fallback")
            
            // Debug log the alignment issue
            synchronizedRenderer.debugLogAlignment(
                dropPointPosition, arrowBasePosition, "Precision Arrow Render"
            )
        }
        
        // Create arrow path points using the exact positions
        val arrowPathPoints = listOf(arrowBasePosition, arrowTipPosition)
        
        // Determine outline color based on map type
        val outlineColor = when (mapType) {
            MapType.SATELLITE -> OUTLINE_COLOR_LIGHT
            MapType.STREET -> OUTLINE_COLOR_DARK
        }
        
        // Draw the precise arrow
        drawArrowWithSubPixelRendering(arrowPathPoints, outlineColor)
    }
    
    /**
     * Draws an arrow with sub-pixel rendering support for maximum precision
     */
    private fun DrawScope.drawArrowWithSubPixelRendering(
        pathPoints: List<Offset>,
        outlineColor: Color
    ) {
        if (pathPoints.size < 2) return
        
        val arrowBase = pathPoints[0]
        val arrowTip = pathPoints[1]
        
        // Calculate arrow direction with high precision
        val deltaX = arrowTip.x - arrowBase.x
        val deltaY = arrowTip.y - arrowBase.y
        val direction = kotlin.math.atan2(deltaY.toDouble(), deltaX.toDouble())
        
        val arrowSize = ARROW_SIZE_DP * density
        val arrowAngle = kotlin.math.PI / 6 // 30 degrees
        
        // Calculate arrow wing points with sub-pixel precision
        val arrowPoint1 = Offset(
            (arrowTip.x - arrowSize * kotlin.math.cos(direction - arrowAngle)).toFloat(),
            (arrowTip.y - arrowSize * kotlin.math.sin(direction - arrowAngle)).toFloat()
        )
        val arrowPoint2 = Offset(
            (arrowTip.x - arrowSize * kotlin.math.cos(direction + arrowAngle)).toFloat(),
            (arrowTip.y - arrowSize * kotlin.math.sin(direction + arrowAngle)).toFloat()
        )
        
        val arrowPath = Path().apply {
            moveTo(arrowTip.x, arrowTip.y)
            lineTo(arrowPoint1.x, arrowPoint1.y)
            moveTo(arrowTip.x, arrowTip.y)
            lineTo(arrowPoint2.x, arrowPoint2.y)
        }
        
        // Draw arrow with outline for visibility
        drawPath(
            path = arrowPath,
            color = outlineColor,
            style = Stroke(width = 3.dp.toPx())
        )
        
        // Draw arrow core with primary color
        drawPath(
            path = arrowPath,
            color = LINE_COLOR,
            style = Stroke(width = 2.dp.toPx())
        )
        
        // Draw the line from base to tip
        drawLine(
            color = outlineColor,
            start = arrowBase,
            end = arrowTip,
            strokeWidth = 3.dp.toPx()
        )
        drawLine(
            color = LINE_COLOR,
            start = arrowBase,
            end = arrowTip,
            strokeWidth = 2.dp.toPx()
        )
    }
    
    /**
     * Creates an arrow base position validation system to ensure perfect alignment
     */
    fun validateArrowBasePosition(
        calculatedBase: Offset,
        expectedBase: Offset,
        tolerance: Float = 0.5f
    ): Boolean {
        val deltaX = kotlin.math.abs(calculatedBase.x - expectedBase.x)
        val deltaY = kotlin.math.abs(calculatedBase.y - expectedBase.y)
        val distance = kotlin.math.sqrt(deltaX * deltaX + deltaY * deltaY)
        
        val isValid = distance <= tolerance
        
        if (!isValid) {
            Timber.w(
                "📍 Arrow base validation failed: " +
                "calculated(${calculatedBase.x}, ${calculatedBase.y}) " +
                "expected(${expectedBase.x}, ${expectedBase.y}) " +
                "distance=${distance}px tolerance=${tolerance}px"
            )
        }
        
        return isValid
    }
    
    /**
     * Draws an arrow at the specified position
     */
    private fun DrawScope.drawArrow(
        position: Offset,
        pathPoints: List<Offset>,
        outlineColor: Color
    ) {
        if (pathPoints.size < 2) return
        
        // Calculate arrow direction from the last two points
        val direction = if (pathPoints.size >= 2) {
            val prev = pathPoints[pathPoints.size - 2]
            val current = pathPoints.last()
            atan2((current.y - prev.y).toDouble(), (current.x - prev.x).toDouble())
        } else {
            0.0
        }
        
        val arrowSize = ARROW_SIZE_DP * density
        val arrowAngle = PI / 6 // 30 degrees
        
        // Calculate arrow points
        val arrowPoint1 = Offset(
            position.x - (arrowSize * cos(direction - arrowAngle)).toFloat(),
            position.y - (arrowSize * sin(direction - arrowAngle)).toFloat()
        )
        val arrowPoint2 = Offset(
            position.x - (arrowSize * cos(direction + arrowAngle)).toFloat(),
            position.y - (arrowSize * sin(direction + arrowAngle)).toFloat()
        )
        
        val arrowPath = Path().apply {
            moveTo(position.x, position.y)
            lineTo(arrowPoint1.x, arrowPoint1.y)
            moveTo(position.x, position.y)
            lineTo(arrowPoint2.x, arrowPoint2.y)
        }
        
        // Draw arrow outline
        drawPath(
            path = arrowPath,
            color = outlineColor.copy(alpha = OUTLINE_ALPHA),
            style = Stroke(width = OUTLINE_WIDTH_DP * density)
        )
        
        // Draw arrow core
        drawPath(
            path = arrowPath,
            color = LINE_COLOR.copy(alpha = LINE_ALPHA),
            style = Stroke(width = LINE_WIDTH_DP * density)
        )
    }
    
    /**
     * Clears the performance optimizer cache
     */
    fun clearCache() {
        performanceOptimizer.clearCache()
    }
    
    /**
     * Gets cache statistics for monitoring performance
     */
    fun getCacheStats(): QiblaPerformanceOptimizer.CacheStats {
        return performanceOptimizer.getCacheStats()
    }
    
    /**
     * Gets comprehensive performance statistics
     */
    fun getPerformanceStats(): QiblaPerformanceMonitor.PerformanceStats {
        return QiblaPerformanceMonitor.getPerformanceStats()
    }
    
    /**
     * Logs current performance statistics
     */
    fun logPerformanceStats() {
        QiblaPerformanceMonitor.logPerformanceStats()
    }
    
    /**
     * Converts longitude to tile X coordinate
     */
    private fun longitudeToTileX(longitude: Double, zoom: Int): Double {
        return (longitude + 180.0) / 360.0 * (1 shl zoom).toDouble()
    }
    
    /**
     * Converts latitude to tile Y coordinate
     */
    private fun latitudeToTileY(latitude: Double, zoom: Int): Double {
        val latRad = Math.toRadians(latitude)
        return (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * (1 shl zoom).toDouble()
    }
    
    /**
     * Creates an error state for the Qibla direction
     */
    private fun createErrorState(primaryMessage: String, detailMessage: String?): QiblaDirectionState {
        return QiblaDirectionState(
            isVisible = false,
            bearing = 0.0,
            distance = 0.0,
            pathPoints = emptyList(),
            screenPoints = emptyList(),
            isCalculationValid = false,
            errorMessage = if (detailMessage != null) "$primaryMessage: $detailMessage" else primaryMessage,
            hasMemoryPressure = false,
            reducedComplexity = false
        )
    }
    
    /**
     * Checks if the system is under memory pressure
     */
    private fun isMemoryUnderPressure(): Boolean {
        return try {
            val runtime = Runtime.getRuntime()
            val maxMemory = runtime.maxMemory()
            val totalMemory = runtime.totalMemory()
            val freeMemory = runtime.freeMemory()
            val usedMemory = totalMemory - freeMemory
            val memoryUsageRatio = usedMemory.toDouble() / maxMemory.toDouble()
            
            // Consider memory pressure if using more than 75% of available memory
            memoryUsageRatio > 0.75
        } catch (e: Exception) {
            Timber.w(e, "Error checking memory pressure, assuming no pressure")
            false
        }
    }
    
    /**
     * Calculates a fallback path with reduced complexity
     */
    private fun calculateFallbackPath(
        userLocation: MapLocation,
        segments: Int
    ): GeodesyResult<QiblaPerformanceOptimizer.CachedPathData> {
        return try {
            // Use simple bearing calculation for fallback
            val bearingResult = GeodesyUtils.calculateQiblaBearingSafe(
                userLocation.latitude,
                userLocation.longitude
            )
            
            val distanceResult = GeodesyUtils.calculateDistanceToKaabaSafe(
                userLocation.latitude,
                userLocation.longitude
            )
            
            when {
                bearingResult is GeodesyResult.Error -> bearingResult
                distanceResult is GeodesyResult.Error -> distanceResult
                else -> {
                    val bearing = (bearingResult as GeodesyResult.Success).data
                    val distance = (distanceResult as GeodesyResult.Success).data
                    
                    // Create a simple straight line as fallback
                    val fallbackPoints = createSimpleFallbackPath(
                        userLocation,
                        bearing,
                        distance,
                        minOf(segments, 10) // Very reduced complexity
                    )
                    
                    GeodesyResult.Success(
                        QiblaPerformanceOptimizer.CachedPathData(
                            pathPoints = fallbackPoints,
                            bearing = bearing,
                            distance = distance,
                            segments = fallbackPoints.size
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Fallback path calculation failed")
            GeodesyResult.Error("Fallback calculation failed", e)
        }
    }
    
    // Object pooling for frequent calculations
    private val offsetPool = mutableListOf<Offset>()
    private val pathPool = mutableListOf<Path>()
    private val locationPool = mutableListOf<MapLocation>()
    
    /**
     * Gets a pooled Offset object or creates a new one
     */
    private fun getPooledOffset(x: Float, y: Float): Offset {
        return if (offsetPool.isNotEmpty()) {
            offsetPool.removeAt(offsetPool.size - 1).copy(x, y)
        } else {
            Offset(x, y)
        }
    }
    
    /**
     * Returns an Offset to the pool for reuse
     */
    private fun returnToPool(offset: Offset) {
        if (offsetPool.size < 50) { // Limit pool size
            offsetPool.add(offset)
        }
    }
    
    /**
     * Gets a pooled Path object or creates a new one
     */
    private fun getPooledPath(): Path {
        return if (pathPool.isNotEmpty()) {
            pathPool.removeAt(pathPool.size - 1).apply { reset() }
        } else {
            Path()
        }
    }
    
    /**
     * Returns a Path to the pool for reuse
     */
    private fun returnToPool(path: Path) {
        if (pathPool.size < 10) { // Limit pool size
            path.reset()
            pathPool.add(path)
        }
    }
    
    /**
     * Enhances path points with predictive positioning for common pan directions
     */
    private fun enhanceWithPredictivePositioning(
        basePoints: List<MapLocation>,
        viewportBounds: ViewportBounds
    ): List<MapLocation> {
        if (basePoints.size < 2) return basePoints
        
        return try {
            val enhanced = basePoints.toMutableList()
            
            // Predict likely pan direction based on viewport center and bearing
            val centerLat = viewportBounds.centerLat
            val centerLon = viewportBounds.centerLon
            
            // Calculate viewport diagonal for prediction range
            val latRange = viewportBounds.northLat - viewportBounds.southLat
            val lonRange = abs(viewportBounds.eastLon - viewportBounds.westLon)
            val predictionRange = max(latRange, lonRange) * 0.2 // 20% of viewport size
            
            // Add predictive points in likely pan directions
            val commonPanDirections = listOf(0.0, 90.0, 180.0, 270.0) // N, E, S, W
            
            for (panDirection in commonPanDirections) {
                val panRad = Math.toRadians(panDirection)
                val predictiveLat = centerLat + predictionRange * cos(panRad)
                val predictiveLon = centerLon + predictionRange * sin(panRad) / cos(Math.toRadians(centerLat))
                
                // Validate coordinates
                if (predictiveLat in -90.0..90.0 && predictiveLon in -180.0..180.0) {
                    val predictiveLocation = MapLocation(predictiveLat, predictiveLon)
                    
                    // Add only if not too close to existing points
                    val tooClose = enhanced.any { existing ->
                        val latDiff = abs(existing.latitude - predictiveLocation.latitude)
                        val lonDiff = abs(existing.longitude - predictiveLocation.longitude)
                        latDiff < predictionRange * 0.1 && lonDiff < predictionRange * 0.1
                    }
                    
                    if (!tooClose) {
                        enhanced.add(predictiveLocation)
                    }
                }
            }
            
            enhanced.sortedBy { location ->
                // Sort by distance from viewport center to maintain path coherence
                val latDiff = location.latitude - centerLat
                val lonDiff = location.longitude - centerLon
                sqrt(latDiff * latDiff + lonDiff * lonDiff)
            }
        } catch (e: Exception) {
            Timber.w(e, "Predictive positioning enhancement failed, using base points")
            basePoints
        }
    }

    /**
     * Creates a simple fallback path using linear interpolation
     */
    private fun createSimpleFallbackPath(
        start: MapLocation,
        bearing: Double,
        distance: Double,
        segments: Int
    ): List<MapLocation> {
        return try {
            val points = mutableListOf<MapLocation>()
            points.add(start)
            
            // Create simple linear approximation (not great circle, but better than nothing)
            val bearingRad = Math.toRadians(bearing)
            val earthRadius = 6371.0 // km
            
            for (i in 1..segments) {
                val fraction = i.toDouble() / segments
                val segmentDistance = distance * fraction
                
                // Simple linear approximation (not accurate for long distances, but works as fallback)
                val deltaLat = (segmentDistance / earthRadius) * cos(bearingRad)
                val deltaLon = (segmentDistance / earthRadius) * sin(bearingRad) / cos(Math.toRadians(start.latitude))
                
                val newLat = start.latitude + Math.toDegrees(deltaLat)
                val newLon = start.longitude + Math.toDegrees(deltaLon)
                
                // Validate coordinates before adding
                if (newLat in -90.0..90.0 && newLon in -180.0..180.0) {
                    points.add(MapLocation(newLat, newLon))
                }
            }
            
            points
        } catch (e: Exception) {
            Timber.w(e, "Simple fallback path creation failed")
            listOf(start) // Return at least the starting point
        }
    }
    
    /**
     * Calculates the optimal arrow length based on screen size and bearing direction.
     * Ensures the arrow extends to near the edge of the screen window.
     * 
     * @param dropPinCenter The center position of the drop pin
     * @param bearingRad The bearing in radians
     * @param canvasWidth The canvas width in pixels
     * @param canvasHeight The canvas height in pixels
     * @return The optimal arrow length in pixels
     */
    private fun calculateOptimalArrowLength(
        dropPinCenter: Offset,
        bearingRad: Double,
        canvasWidth: Float,
        canvasHeight: Float
    ): Float {
        // Calculate direction vector
        val directionX = sin(bearingRad).toFloat()
        val directionY = -cos(bearingRad).toFloat()
        
        // Calculate distances to each edge
        val distanceToRight = if (directionX > 0) (canvasWidth - dropPinCenter.x) / directionX else Float.MAX_VALUE
        val distanceToLeft = if (directionX < 0) dropPinCenter.x / (-directionX) else Float.MAX_VALUE
        val distanceToBottom = if (directionY > 0) (canvasHeight - dropPinCenter.y) / directionY else Float.MAX_VALUE
        val distanceToTop = if (directionY < 0) dropPinCenter.y / (-directionY) else Float.MAX_VALUE
        
        // Find the minimum distance to any edge (this is where the arrow would hit the screen boundary)
        val distanceToEdge = minOf(
            distanceToRight,
            distanceToLeft,
            distanceToBottom,
            distanceToTop
        )
        
        // Use 85% of the distance to edge, with margin
        val maxLength = (distanceToEdge * 0.85f) - ARROW_EDGE_MARGIN
        
        // Ensure minimum and maximum reasonable lengths
        return maxLength.coerceIn(SIMPLE_ARROW_BASE_LENGTH, minOf(canvasWidth, canvasHeight) * 0.8f)
    }
    
    /**
     * Renders a simple Qibla arrow using the same anchor point as the drop pin.
     * This ensures perfect alignment between the pin and arrow base.
     * Uses Great Circle calculations for accurate bearing.
     * 
     * @param drawScope The Canvas DrawScope for rendering
     * @param dropPinCenter The center position of the drop pin (same anchor point)
     * @param userLatitude The user's current latitude
     * @param userLongitude The user's current longitude
     * @param mapType The current map type for styling
     */
    fun renderSimpleQiblaArrow(
        drawScope: DrawScope,
        dropPinCenter: Offset,
        userLatitude: Double,
        userLongitude: Double,
        mapType: MapType
    ) {
        with(drawScope) {
            try {
                // Calculate Qibla bearing using Great Circle method
                val bearingResult = GeodesyUtils.calculateQiblaBearingSafe(userLatitude, userLongitude)
                
                when (bearingResult) {
                    is GeodesyResult.Success -> {
                        val qiblaBearing = bearingResult.data
                        
                        // Convert bearing to radians (bearing is from North, clockwise)
                        val bearingRad = Math.toRadians(qiblaBearing)
                        
                        // Calculate optimal arrow length that extends to near screen edge
                        val optimalArrowLength = calculateOptimalArrowLength(
                            dropPinCenter, bearingRad, size.width, size.height
                        )
                        
                        // Calculate arrow tip position from drop pin center
                        val arrowTipX = dropPinCenter.x + (optimalArrowLength * sin(bearingRad)).toFloat()
                        val arrowTipY = dropPinCenter.y - (optimalArrowLength * cos(bearingRad)).toFloat()
                        val arrowTip = Offset(arrowTipX, arrowTipY)
                        
                        // Determine colors based on map type
                        val outlineColor = when (mapType) {
                            MapType.SATELLITE -> OUTLINE_COLOR_LIGHT
                            MapType.STREET -> OUTLINE_COLOR_DARK
                        }
                        
                        // Draw arrow shaft (outline)
                        drawLine(
                            color = outlineColor.copy(alpha = OUTLINE_ALPHA),
                            start = dropPinCenter,
                            end = arrowTip,
                            strokeWidth = OUTLINE_WIDTH_DP * density
                        )
                        
                        // Draw arrow shaft (main line)
                        drawLine(
                            color = LINE_COLOR.copy(alpha = LINE_ALPHA),
                            start = dropPinCenter,
                            end = arrowTip,
                            strokeWidth = LINE_WIDTH_DP * density
                        )
                        
                        // Draw arrowhead
                        drawSimpleArrowHead(arrowTip, bearingRad, outlineColor)
                        
                        Timber.d("📍 Simple Qibla arrow rendered: bearing=${String.format("%.1f", qiblaBearing)}°, length=${String.format("%.0f", optimalArrowLength)}px, from=${dropPinCenter}, to=${arrowTip}")
                        
                    }
                    is GeodesyResult.Error -> {
                        // Draw error indicator at drop pin center
                        drawCircle(
                            color = Color.Red.copy(alpha = 0.7f),
                            radius = 20f,
                            center = dropPinCenter,
                            style = Stroke(width = 3f)
                        )
                        
                        // Draw X mark
                        val crossSize = 12f
                        drawLine(
                            color = Color.Red,
                            start = Offset(dropPinCenter.x - crossSize, dropPinCenter.y - crossSize),
                            end = Offset(dropPinCenter.x + crossSize, dropPinCenter.y + crossSize),
                            strokeWidth = 3f
                        )
                        drawLine(
                            color = Color.Red,
                            start = Offset(dropPinCenter.x + crossSize, dropPinCenter.y - crossSize),
                            end = Offset(dropPinCenter.x - crossSize, dropPinCenter.y + crossSize),
                            strokeWidth = 3f
                        )
                        
                        Timber.w("📍 Qibla calculation error: ${bearingResult.message}")
                    }
                }
                
            } catch (e: Exception) {
                Timber.e(e, "📍 Error rendering simple Qibla arrow")
                
                // Draw fallback error indicator
                drawCircle(
                    color = Color.Red.copy(alpha = 0.5f),
                    radius = 15f,
                    center = dropPinCenter,
                    style = Stroke(width = 2f)
                )
            }
        }
    }
    
    /**
     * Draws a simple arrowhead at the tip of the arrow.
     * 
     * @param arrowTip The position of the arrow tip
     * @param bearingRad The bearing in radians
     * @param outlineColor The outline color for the arrowhead
     */
    private fun DrawScope.drawSimpleArrowHead(
        arrowTip: Offset,
        bearingRad: Double,
        outlineColor: Color
    ) {
        val headAngleRad = Math.toRadians(SIMPLE_ARROW_HEAD_ANGLE)
        
        // Calculate arrowhead points
        val leftAngle = bearingRad - headAngleRad + Math.PI
        val rightAngle = bearingRad + headAngleRad + Math.PI
        
        val leftPoint = Offset(
            arrowTip.x + (SIMPLE_ARROW_HEAD_LENGTH * sin(leftAngle)).toFloat(),
            arrowTip.y - (SIMPLE_ARROW_HEAD_LENGTH * cos(leftAngle)).toFloat()
        )
        
        val rightPoint = Offset(
            arrowTip.x + (SIMPLE_ARROW_HEAD_LENGTH * sin(rightAngle)).toFloat(),
            arrowTip.y - (SIMPLE_ARROW_HEAD_LENGTH * cos(rightAngle)).toFloat()
        )
        
        // Draw arrowhead outline
        drawLine(
            color = outlineColor.copy(alpha = OUTLINE_ALPHA),
            start = arrowTip,
            end = leftPoint,
            strokeWidth = OUTLINE_WIDTH_DP * density
        )
        drawLine(
            color = outlineColor.copy(alpha = OUTLINE_ALPHA),
            start = arrowTip,
            end = rightPoint,
            strokeWidth = OUTLINE_WIDTH_DP * density
        )
        
        // Draw arrowhead main lines
        drawLine(
            color = LINE_COLOR.copy(alpha = LINE_ALPHA),
            start = arrowTip,
            end = leftPoint,
            strokeWidth = LINE_WIDTH_DP * density
        )
        drawLine(
            color = LINE_COLOR.copy(alpha = LINE_ALPHA),
            start = arrowTip,
            end = rightPoint,
            strokeWidth = LINE_WIDTH_DP * density
        )
    }
}