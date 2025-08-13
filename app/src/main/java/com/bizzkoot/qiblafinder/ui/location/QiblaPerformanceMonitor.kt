package com.bizzkoot.qiblafinder.ui.location

import timber.log.Timber
import kotlin.system.measureTimeMillis

/**
 * Performance monitoring utility for Qibla direction calculations
 * Tracks timing, cache hit rates, and memory usage
 */
object QiblaPerformanceMonitor {
    
    internal var totalCalculations = 0
    internal var totalCalculationTime = 0L
    internal var cacheHits = 0
    internal var cacheMisses = 0
    
    /**
     * Measures and logs the execution time of a calculation
     */
    internal inline fun <T> measureCalculation(
        operationName: String,
        operation: () -> T
    ): T {
        val result: T
        val executionTime = measureTimeMillis {
            result = operation()
        }
        
        totalCalculations++
        totalCalculationTime += executionTime
        
        if (executionTime > 50) { // Log slow operations
            Timber.w("📍 Slow Qibla calculation: $operationName took ${executionTime}ms")
        } else {
            Timber.d("📍 Qibla calculation: $operationName took ${executionTime}ms")
        }
        
        return result
    }
    
    /**
     * Records a cache hit
     */
    fun recordCacheHit() {
        cacheHits++
        Timber.d("📍 Cache hit recorded. Hit rate: ${getCacheHitRate()}%")
    }
    
    /**
     * Records a cache miss
     */
    fun recordCacheMiss() {
        cacheMisses++
        Timber.d("📍 Cache miss recorded. Hit rate: ${getCacheHitRate()}%")
    }
    
    /**
     * Gets the current cache hit rate as a percentage
     */
    fun getCacheHitRate(): Double {
        val total = cacheHits + cacheMisses
        return if (total > 0) (cacheHits.toDouble() / total) * 100.0 else 0.0
    }
    
    /**
     * Gets the average calculation time in milliseconds
     */
    fun getAverageCalculationTime(): Double {
        return if (totalCalculations > 0) {
            totalCalculationTime.toDouble() / totalCalculations
        } else 0.0
    }
    
    /**
     * Gets performance statistics
     */
    fun getPerformanceStats(): PerformanceStats {
        return PerformanceStats(
            totalCalculations = totalCalculations,
            averageCalculationTime = getAverageCalculationTime(),
            cacheHitRate = getCacheHitRate(),
            totalCacheHits = cacheHits,
            totalCacheMisses = cacheMisses
        )
    }
    
    /**
     * Logs current performance statistics
     */
    fun logPerformanceStats() {
        val stats = getPerformanceStats()
        Timber.i("""
            📍 Qibla Performance Statistics:
            - Total calculations: ${stats.totalCalculations}
            - Average calculation time: ${"%.2f".format(stats.averageCalculationTime)}ms
            - Cache hit rate: ${"%.1f".format(stats.cacheHitRate)}%
            - Cache hits: ${stats.totalCacheHits}
            - Cache misses: ${stats.totalCacheMisses}
        """.trimIndent())
    }
    
    /**
     * Resets all performance counters
     */
    fun reset() {
        totalCalculations = 0
        totalCalculationTime = 0L
        cacheHits = 0
        cacheMisses = 0
        Timber.d("📍 Performance monitor reset")
    }
    
    /**
     * Checks if performance is within acceptable thresholds
     */
    fun isPerformanceAcceptable(): Boolean {
        val avgTime = getAverageCalculationTime()
        val hitRate = getCacheHitRate()
        
        return avgTime <= 100.0 && // Average calculation should be under 100ms
               (totalCalculations < 10 || hitRate >= 50.0) // Cache hit rate should be at least 50% after 10 calculations
    }
    
    /**
     * Gets performance recommendations based on current statistics
     */
    fun getPerformanceRecommendations(): List<String> {
        val recommendations = mutableListOf<String>()
        val stats = getPerformanceStats()
        
        if (stats.averageCalculationTime > 100.0) {
            recommendations.add("Consider reducing path segments or enabling high-performance mode")
        }
        
        if (stats.cacheHitRate < 50.0 && stats.totalCalculations > 10) {
            recommendations.add("Cache hit rate is low - check if locations are changing too frequently")
        }
        
        if (stats.totalCalculations > 100 && stats.averageCalculationTime > 50.0) {
            recommendations.add("High calculation frequency with slow performance - consider throttling")
        }
        
        if (recommendations.isEmpty()) {
            recommendations.add("Performance is within acceptable parameters")
        }
        
        return recommendations
    }
    
    data class PerformanceStats(
        val totalCalculations: Int,
        val averageCalculationTime: Double,
        val cacheHitRate: Double,
        val totalCacheHits: Int,
        val totalCacheMisses: Int
    )
}