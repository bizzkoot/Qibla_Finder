package com.bizzkoot.qiblafinder.ui.location

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.bizzkoot.qiblafinder.model.GeodesyUtils
import com.bizzkoot.qiblafinder.model.GeodesyResult
import timber.log.Timber
import kotlin.math.*

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
        
        // Colors
        private val LINE_COLOR = Color(0xFF4CAF50) // Green
        private val OUTLINE_COLOR_LIGHT = Color.White
        private val OUTLINE_COLOR_DARK = Color(0xFF333333)
    }
    
    /**
     * Calculates the Qibla direction line state with comprehensive error handling
     * Enhanced with performance optimizations and graceful fallbacks
     */
    fun calculateDirectionLine(
        userLocation: MapLocation,
        viewportBounds: ViewportBounds,
        zoomLevel: Int,
        digitalZoom: Float,
        isHighPerformanceMode: Boolean = false
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
            
            // Determine optimal number of segments with memory pressure consideration
            val baseSegments = performanceOptimizer.calculateOptimalSegments(
                zoomLevel, 
                digitalZoom, 
                distance,
                effectiveHighPerformanceMode
            )
            
            val segments = if (memoryPressure) {
                minOf(baseSegments, MEMORY_PRESSURE_SEGMENT_LIMIT).also {
                    Timber.w("Memory pressure detected, reducing segments from $baseSegments to $it")
                }
            } else {
                baseSegments
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
                    
                    // Enhanced viewport culling with error handling
                    val visiblePoints = try {
                        performanceOptimizer.cullPathToViewport(
                            data.pathPoints, 
                            viewportBounds,
                            includeBuffer = true
                        )
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
     * Renders the Qibla direction line on the canvas
     */
    fun renderDirectionLine(
        drawScope: DrawScope,
        directionState: QiblaDirectionState,
        centerOffset: Offset,
        tileX: Double,
        tileY: Double,
        zoom: Int,
        digitalZoom: Float,
        mapType: MapType
    ) {
        if (!directionState.isVisible || !directionState.isCalculationValid || directionState.pathPoints.isEmpty()) {
            return
        }
        
        // Convert geographic coordinates to screen coordinates
        val screenPoints = directionState.pathPoints.mapNotNull { location ->
            geoToScreenCoordinate(
                location,
                centerOffset,
                tileX,
                tileY,
                zoom,
                digitalZoom
            )
        }
        
        if (screenPoints.size < 2) return
        
        // Create path for the direction line
        val path = createSmoothPath(screenPoints)
        
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
}