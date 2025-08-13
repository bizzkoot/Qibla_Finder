package com.bizzkoot.qiblafinder.ui.location

import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.*
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*

/**
 * Performance optimization utilities for Qibla direction line rendering.
 * Provides caching, throttling, and level-of-detail management.
 */
class QiblaPerformanceOptimizer {
    
    companion object {
        private const val MAX_CACHE_SIZE = 50
        private const val THROTTLE_DELAY_MS = 100L
        private const val SIGNIFICANT_LOCATION_CHANGE_THRESHOLD = 0.001 // ~100m
        private const val CACHE_CLEANUP_THRESHOLD = 40
        
        // Level-of-detail thresholds
        private const val HIGH_DETAIL_ZOOM = 14
        private const val MEDIUM_DETAIL_ZOOM = 10
        private const val LOW_DETAIL_ZOOM = 6
    }
    
    // LRU Cache for path calculations
    private val pathCache = LRUCache<String, CachedPathData>(MAX_CACHE_SIZE)
    
    // Throttling state
    private var throttleJob: Job? = null
    private var lastCalculationTime = 0L
    private var lastSignificantLocation: MapLocation? = null
    
    /**
     * Data class for cached path information
     */
    data class CachedPathData(
        val pathPoints: List<MapLocation>,
        val bearing: Double,
        val distance: Double,
        val segments: Int,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    /**
     * Calculates optimal number of segments based on zoom level and distance
     * with enhanced level-of-detail reduction
     */
    fun calculateOptimalSegments(
        zoomLevel: Int, 
        digitalZoom: Float, 
        distance: Double,
        isHighPerformanceMode: Boolean = false
    ): Int {
        val baseSegments = when {
            zoomLevel >= HIGH_DETAIL_ZOOM -> 50
            zoomLevel >= MEDIUM_DETAIL_ZOOM -> 30
            zoomLevel >= LOW_DETAIL_ZOOM -> 20
            else -> 10
        }
        
        // Apply digital zoom factor
        val zoomAdjustedSegments = (baseSegments * digitalZoom.coerceAtMost(2f)).toInt()
        
        // Distance-based adjustment
        val distanceMultiplier = when {
            distance > 15000 -> 1.5 // Very long distances need more segments
            distance > 8000 -> 1.2  // Long distances
            distance > 3000 -> 1.0  // Medium distances
            else -> 0.8             // Short distances can use fewer segments
        }
        
        // Performance mode adjustment
        val performanceMultiplier = if (isHighPerformanceMode) 0.7 else 1.0
        
        val finalSegments = (zoomAdjustedSegments * distanceMultiplier * performanceMultiplier).toInt()
        return finalSegments.coerceIn(8, 80) // Reasonable bounds
    }
    
    /**
     * Gets cached path data or calculates new path with caching and error handling
     */
    fun getCachedOrCalculatePathSafe(
        userLocation: MapLocation,
        kaabaLat: Double,
        kaabaLon: Double,
        segments: Int,
        calculator: (MapLocation, Double, Double, Int) -> com.bizzkoot.qiblafinder.model.GeodesyResult<List<MapLocation>>
    ): com.bizzkoot.qiblafinder.model.GeodesyResult<CachedPathData> {
        val cacheKey = generateCacheKey(userLocation, segments)
        
        // Check cache first
        pathCache.get(cacheKey)?.let { cachedData ->
            QiblaPerformanceMonitor.recordCacheHit()
            Timber.d("📍 Using cached path data for location: ${userLocation.latitude}, ${userLocation.longitude}")
            return com.bizzkoot.qiblafinder.model.GeodesyResult.Success(cachedData)
        }
        
        // Cache miss - calculate new path
        QiblaPerformanceMonitor.recordCacheMiss()
        
        return QiblaPerformanceMonitor.measureCalculation("Great Circle Path Calculation") {
            try {
                when (val pathResult = calculator(userLocation, kaabaLat, kaabaLon, segments)) {
                    is com.bizzkoot.qiblafinder.model.GeodesyResult.Success -> {
                        val pathPoints = pathResult.data
                        
                        if (pathPoints.isNotEmpty()) {
                            // Calculate bearing and distance
                            val bearing = calculateBearing(
                                userLocation.latitude, userLocation.longitude,
                                kaabaLat, kaabaLon
                            )
                            val distance = calculateDistance(
                                userLocation.latitude, userLocation.longitude,
                                kaabaLat, kaabaLon
                            )
                            
                            val cachedData = CachedPathData(
                                pathPoints = pathPoints,
                                bearing = bearing,
                                distance = distance,
                                segments = segments
                            )
                            
                            // Store in cache
                            pathCache.put(cacheKey, cachedData)
                            
                            Timber.d("📍 Calculated and cached new path for location: ${userLocation.latitude}, ${userLocation.longitude}")
                            com.bizzkoot.qiblafinder.model.GeodesyResult.Success(cachedData)
                        } else {
                            com.bizzkoot.qiblafinder.model.GeodesyResult.Error("Empty path points returned")
                        }
                    }
                    is com.bizzkoot.qiblafinder.model.GeodesyResult.Error -> pathResult
                }
            } catch (e: Exception) {
                Timber.e(e, "📍 Error calculating path for caching")
                com.bizzkoot.qiblafinder.model.GeodesyResult.Error("Path calculation failed", e)
            }
        }
    }
    
    /**
     * Legacy method for backward compatibility
     */
    fun getCachedOrCalculatePath(
        userLocation: MapLocation,
        kaabaLat: Double,
        kaabaLon: Double,
        segments: Int,
        calculator: (MapLocation, Double, Double, Int) -> List<MapLocation>
    ): CachedPathData? {
        val cacheKey = generateCacheKey(userLocation, segments)
        
        // Check cache first
        pathCache.get(cacheKey)?.let { cachedData ->
            QiblaPerformanceMonitor.recordCacheHit()
            Timber.d("📍 Using cached path data for location: ${userLocation.latitude}, ${userLocation.longitude}")
            return cachedData
        }
        
        // Cache miss - calculate new path
        QiblaPerformanceMonitor.recordCacheMiss()
        
        return QiblaPerformanceMonitor.measureCalculation("Great Circle Path Calculation") {
            try {
                val pathPoints = calculator(userLocation, kaabaLat, kaabaLon, segments)
                
                if (pathPoints.isNotEmpty()) {
                    // Calculate bearing and distance
                    val bearing = calculateBearing(
                        userLocation.latitude, userLocation.longitude,
                        kaabaLat, kaabaLon
                    )
                    val distance = calculateDistance(
                        userLocation.latitude, userLocation.longitude,
                        kaabaLat, kaabaLon
                    )
                    
                    val cachedData = CachedPathData(
                        pathPoints = pathPoints,
                        bearing = bearing,
                        distance = distance,
                        segments = segments
                    )
                    
                    // Store in cache
                    pathCache.put(cacheKey, cachedData)
                    
                    Timber.d("📍 Calculated and cached new path for location: ${userLocation.latitude}, ${userLocation.longitude}")
                    cachedData
                } else {
                    null
                }
            } catch (e: Exception) {
                Timber.e(e, "📍 Error calculating path for caching")
                null
            }
        }
    }
    
    /**
     * Throttles calculation calls to prevent excessive computation during drag operations
     */
    fun throttleCalculation(
        scope: CoroutineScope,
        calculation: suspend () -> Unit
    ) {
        // Cancel previous throttled calculation
        throttleJob?.cancel()
        
        val currentTime = System.currentTimeMillis()
        val timeSinceLastCalculation = currentTime - lastCalculationTime
        
        if (timeSinceLastCalculation >= THROTTLE_DELAY_MS) {
            // Execute immediately if enough time has passed
            throttleJob = scope.launch {
                calculation()
                lastCalculationTime = System.currentTimeMillis()
            }
        } else {
            // Throttle the calculation
            throttleJob = scope.launch {
                delay(THROTTLE_DELAY_MS - timeSinceLastCalculation)
                calculation()
                lastCalculationTime = System.currentTimeMillis()
            }
        }
    }
    
    /**
     * Enhanced viewport culling that considers path curvature
     */
    fun cullPathToViewport(
        pathPoints: List<MapLocation>,
        viewportBounds: ViewportBounds,
        includeBuffer: Boolean = true
    ): List<MapLocation> {
        if (pathPoints.isEmpty()) return emptyList()
        
        val effectiveBounds = if (includeBuffer) {
            // Add buffer to viewport to include slightly off-screen segments
            val latBuffer = (viewportBounds.northLat - viewportBounds.southLat) * 0.1
            val lonBuffer = abs(viewportBounds.eastLon - viewportBounds.westLon) * 0.1
            
            ViewportBounds(
                northLat = viewportBounds.northLat + latBuffer,
                southLat = viewportBounds.southLat - latBuffer,
                eastLon = viewportBounds.eastLon + lonBuffer,
                westLon = viewportBounds.westLon - lonBuffer,
                centerLat = viewportBounds.centerLat,
                centerLon = viewportBounds.centerLon
            )
        } else {
            viewportBounds
        }
        
        // Simple filtering - just include points that are within bounds
        // and add intersection points for segments that cross boundaries
        val visiblePoints = mutableListOf<MapLocation>()
        var lastPointVisible = false
        
        for (i in pathPoints.indices) {
            val point = pathPoints[i]
            val pointVisible = effectiveBounds.contains(point)
            
            when {
                pointVisible -> {
                    // Add intersection point if transitioning from invisible to visible
                    if (!lastPointVisible && i > 0) {
                        findViewportIntersection(pathPoints[i - 1], point, effectiveBounds)?.let {
                            visiblePoints.add(it)
                        }
                    }
                    visiblePoints.add(point)
                }
                lastPointVisible -> {
                    // Add intersection point when transitioning from visible to invisible
                    findViewportIntersection(pathPoints[i - 1], point, effectiveBounds)?.let {
                        visiblePoints.add(it)
                    }
                }
            }
            
            lastPointVisible = pointVisible
        }
        
        return visiblePoints
    }
    
    /**
     * Checks if location has changed significantly enough to invalidate cache
     */
    private fun isLocationSignificantlyDifferent(
        newLocation: MapLocation,
        @Suppress("UNUSED_PARAMETER") cachedData: CachedPathData
    ): Boolean {
        lastSignificantLocation?.let { lastLoc ->
            val latDiff = abs(newLocation.latitude - lastLoc.latitude)
            val lonDiff = abs(newLocation.longitude - lastLoc.longitude)
            
            if (latDiff > SIGNIFICANT_LOCATION_CHANGE_THRESHOLD || 
                lonDiff > SIGNIFICANT_LOCATION_CHANGE_THRESHOLD) {
                lastSignificantLocation = newLocation
                return true
            }
            return false
        }
        
        lastSignificantLocation = newLocation
        return true
    }
    
    /**
     * Generates cache key for path data
     */
    private fun generateCacheKey(location: MapLocation, segments: Int): String {
        // Round to reasonable precision to improve cache hit rate
        val roundedLat = (location.latitude * 1000).roundToInt() / 1000.0
        val roundedLon = (location.longitude * 1000).roundToInt() / 1000.0
        return "${roundedLat}_${roundedLon}_$segments"
    }
    
    /**
     * Finds intersection between line segment and viewport bounds
     */
    private fun findViewportIntersection(
        inside: MapLocation,
        outside: MapLocation,
        bounds: ViewportBounds
    ): MapLocation? {
        val latDiff = outside.latitude - inside.latitude
        val lonDiff = outside.longitude - inside.longitude
        
        val intersections = mutableListOf<MapLocation>()
        
        // Check each boundary
        listOf(
            // North boundary
            if (latDiff != 0.0) {
                val t = (bounds.northLat - inside.latitude) / latDiff
                if (t in 0.0..1.0) {
                    val lon = inside.longitude + t * lonDiff
                    if (isLongitudeInBounds(lon, bounds)) {
                        MapLocation(bounds.northLat, lon)
                    } else null
                } else null
            } else null,
            
            // South boundary
            if (latDiff != 0.0) {
                val t = (bounds.southLat - inside.latitude) / latDiff
                if (t in 0.0..1.0) {
                    val lon = inside.longitude + t * lonDiff
                    if (isLongitudeInBounds(lon, bounds)) {
                        MapLocation(bounds.southLat, lon)
                    } else null
                } else null
            } else null,
            
            // East boundary
            if (lonDiff != 0.0) {
                val t = (bounds.eastLon - inside.longitude) / lonDiff
                if (t in 0.0..1.0) {
                    val lat = inside.latitude + t * latDiff
                    if (lat in bounds.southLat..bounds.northLat) {
                        MapLocation(lat, bounds.eastLon)
                    } else null
                } else null
            } else null,
            
            // West boundary
            if (lonDiff != 0.0) {
                val t = (bounds.westLon - inside.longitude) / lonDiff
                if (t in 0.0..1.0) {
                    val lat = inside.latitude + t * latDiff
                    if (lat in bounds.southLat..bounds.northLat) {
                        MapLocation(lat, bounds.westLon)
                    } else null
                } else null
            } else null
        ).filterNotNull().let { intersections.addAll(it) }
        
        // Return closest intersection to inside point
        return intersections.minByOrNull { intersection ->
            val latDist = intersection.latitude - inside.latitude
            val lonDist = intersection.longitude - inside.longitude
            latDist * latDist + lonDist * lonDist
        }
    }
    
    /**
     * Checks if longitude is within bounds, handling date line crossing
     */
    private fun isLongitudeInBounds(longitude: Double, bounds: ViewportBounds): Boolean {
        return if (bounds.eastLon >= bounds.westLon) {
            longitude in bounds.westLon..bounds.eastLon
        } else {
            longitude >= bounds.westLon || longitude <= bounds.eastLon
        }
    }
    
    /**
     * Calculates bearing between two points
     */
    private fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val deltaLonRad = Math.toRadians(lon2 - lon1)
        
        val y = sin(deltaLonRad) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(deltaLonRad)
        
        val bearingRad = atan2(y, x)
        return (Math.toDegrees(bearingRad) + 360) % 360
    }
    
    /**
     * Calculates distance between two points using Haversine formula
     */
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371.0 // km
        
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val deltaLatRad = Math.toRadians(lat2 - lat1)
        val deltaLonRad = Math.toRadians(lon2 - lon1)
        
        val a = sin(deltaLatRad / 2) * sin(deltaLatRad / 2) +
                cos(lat1Rad) * cos(lat2Rad) *
                sin(deltaLonRad / 2) * sin(deltaLonRad / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        
        return earthRadius * c
    }
    
    /**
     * Clears cache and resets throttling state
     */
    fun clearCache() {
        pathCache.clear()
        throttleJob?.cancel()
        throttleJob = null
        lastCalculationTime = 0L
        lastSignificantLocation = null
        Timber.d("📍 Performance optimizer cache cleared")
    }
    
    /**
     * Gets cache statistics for monitoring
     */
    fun getCacheStats(): CacheStats {
        return CacheStats(
            size = pathCache.size(),
            maxSize = MAX_CACHE_SIZE,
            hitRate = pathCache.getHitRate()
        )
    }
    
    data class CacheStats(
        val size: Int,
        val maxSize: Int,
        val hitRate: Double
    )
}

/**
 * Simple LRU Cache implementation
 */
private class LRUCache<K, V>(private val maxSize: Int) {
    private val cache = LinkedHashMap<K, V>(16, 0.75f, true)
    private var hits = 0
    private var misses = 0
    
    @Synchronized
    fun get(key: K): V? {
        val value = cache[key]
        if (value != null) {
            hits++
        } else {
            misses++
        }
        return value
    }
    
    @Synchronized
    fun put(key: K, value: V): V? {
        val previous = cache.put(key, value)
        
        // Remove oldest entries if cache exceeds max size
        while (cache.size > maxSize) {
            val oldest = cache.entries.iterator()
            if (oldest.hasNext()) {
                oldest.next()
                oldest.remove()
            }
        }
        
        return previous
    }
    
    @Synchronized
    fun remove(key: K): V? {
        return cache.remove(key)
    }
    
    @Synchronized
    fun clear() {
        cache.clear()
        hits = 0
        misses = 0
    }
    
    @Synchronized
    fun size(): Int = cache.size
    
    @Synchronized
    fun getHitRate(): Double {
        val total = hits + misses
        return if (total > 0) hits.toDouble() / total else 0.0
    }
}