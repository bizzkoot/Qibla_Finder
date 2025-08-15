package com.bizzkoot.qiblafinder.ui.location

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import timber.log.Timber
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.*

/**
 * Performance monitoring utility for Qibla direction calculations
 * Tracks timing, cache hit rates, and memory usage
 */
object QiblaPerformanceMonitor {
    
    internal var totalCalculations = 0
    internal var totalCalculationTime = 0L
    internal var cacheHits = 0
    internal var cacheMisses = 0
    
    // Memory monitoring
    private var peakMemoryUsage = 0L
    private var memoryPressureEvents = 0
    private var context: Context? = null
    
    // CPU and performance monitoring
    private var highFrequencyUpdates = 0
    private var throttleEvents = 0
    private var updateFrequencyMs = 16L // Default 60fps
    private var isAdaptiveMode = false
    
    // CPU usage monitoring
    private var cpuSampleCount = 0
    private var totalCpuUsage = 0.0
    private var lastCpuSampleTime = 0L
    private var highCpuEvents = 0
    
    // Performance timing for CPU load calculation
    private var calculationStartTimes = mutableMapOf<String, Long>()
    private var consecutiveSlowCalculations = 0
    
    // Calculation failure tracking
    private var calculationFailures = 0
    private var consecutiveFailures = 0
    private var lastFailureTime = 0L
    private var recoveryAttempts = 0
    
    // Performance thresholds
    private const val MAX_MEMORY_USAGE_MB = 50 // Maximum acceptable memory usage
    private const val MAX_AVG_CALCULATION_TIME_MS = 100.0
    private const val MIN_CACHE_HIT_RATE = 50.0
    private const val MEMORY_PRESSURE_THRESHOLD = 0.8f // 80% memory usage threshold
    private const val CPU_USAGE_SAMPLE_INTERVAL_MS = 1000L // Sample CPU usage every second
    private const val HIGH_CPU_THRESHOLD = 80.0 // 80% CPU usage threshold
    private const val SLOW_CALCULATION_THRESHOLD = 3 // Number of consecutive slow calculations before throttling
    private const val MAX_CONSECUTIVE_FAILURES = 5 // Maximum consecutive failures before emergency recovery
    private const val FAILURE_RECOVERY_COOLDOWN_MS = 5000L // 5 seconds between recovery attempts
    
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
        
        // Track consecutive slow calculations for CPU throttling
        if (executionTime > MAX_AVG_CALCULATION_TIME_MS) {
            consecutiveSlowCalculations++
            Timber.w("📍 Slow Qibla calculation: $operationName took ${executionTime}ms (${consecutiveSlowCalculations} consecutive)")
        } else {
            consecutiveSlowCalculations = 0
            Timber.d("📍 Qibla calculation: $operationName took ${executionTime}ms")
        }
        
        return result
    }
    
    /**
     * Estimates CPU usage based on calculation performance and device capabilities
     */
    fun estimateCpuUsage(): CpuUsageInfo {
        val currentTime = System.currentTimeMillis()
        
        // Calculate CPU load based on calculation performance
        val avgCalculationTime = getAverageCalculationTime()
        val estimatedCpuUsage = when {
            avgCalculationTime > 200.0 -> 95.0 // Very high CPU usage
            avgCalculationTime > 150.0 -> 85.0 // High CPU usage
            avgCalculationTime > 100.0 -> 70.0 // Medium-high CPU usage
            avgCalculationTime > 50.0 -> 50.0  // Medium CPU usage
            else -> 25.0 // Low CPU usage
        }
        
        // Adjust based on consecutive slow calculations
        val adjustedCpuUsage = estimatedCpuUsage + (consecutiveSlowCalculations * 10.0)
        val finalCpuUsage = adjustedCpuUsage.coerceAtMost(100.0)
        
        // Update samples if enough time has passed
        if (currentTime - lastCpuSampleTime > CPU_USAGE_SAMPLE_INTERVAL_MS) {
            cpuSampleCount++
            totalCpuUsage += finalCpuUsage
            lastCpuSampleTime = currentTime
            
            // Check for high CPU usage events
            if (finalCpuUsage > HIGH_CPU_THRESHOLD) {
                highCpuEvents++
                Timber.w("📍 High CPU usage detected: ${"%.1f".format(finalCpuUsage)}%")
            }
        }
        
        val averageCpuUsage = if (cpuSampleCount > 0) totalCpuUsage / cpuSampleCount else finalCpuUsage
        
        return CpuUsageInfo(
            currentUsage = finalCpuUsage,
            averageUsage = averageCpuUsage,
            isHighUsage = finalCpuUsage > HIGH_CPU_THRESHOLD,
            consecutiveSlowCalculations = consecutiveSlowCalculations,
            highCpuEvents = highCpuEvents
        )
    }
    
    /**
     * Checks if CPU throttling should be applied based on current performance
     */
    fun shouldApplyCpuThrottling(): Boolean {
        val cpuInfo = estimateCpuUsage()
        return cpuInfo.isHighUsage || consecutiveSlowCalculations >= SLOW_CALCULATION_THRESHOLD
    }
    
    /**
     * Calculates throttled update frequency when CPU usage is high
     */
    fun calculateThrottledFrequency(baseFrequency: Long): Long {
        val cpuInfo = estimateCpuUsage()
        
        val throttleMultiplier = when {
            cpuInfo.currentUsage > 95.0 -> 4.0 // Severe throttling
            cpuInfo.currentUsage > 85.0 -> 3.0 // Heavy throttling
            cpuInfo.currentUsage > 70.0 -> 2.0 // Moderate throttling
            consecutiveSlowCalculations >= SLOW_CALCULATION_THRESHOLD -> 2.0 // Throttle on slow calculations
            else -> 1.0 // No throttling
        }
        
        val throttledFrequency = (baseFrequency * throttleMultiplier).toLong()
        
        if (throttleMultiplier > 1.0) {
            Timber.d("📍 CPU throttling applied: ${baseFrequency}ms -> ${throttledFrequency}ms (CPU: ${"%.1f".format(cpuInfo.currentUsage)}%)")
        }
        
        return throttledFrequency.coerceAtMost(200L) // Max 5fps during severe throttling
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
     * Initialize performance monitor with application context
     */
    fun initialize(applicationContext: Context) {
        context = applicationContext
        if (com.bizzkoot.qiblafinder.BuildConfig.DEBUG) {
            Timber.d("📍 Performance monitor initialized with debug logging enabled")
        } else {
            Timber.d("📍 Performance monitor initialized")
        }
    }
    
    /**
     * Monitors current memory usage during high-frequency updates
     */
    fun monitorMemoryUsage(): MemoryUsageInfo {
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val maxMemory = runtime.maxMemory()
        val usedMemoryMB = usedMemory / (1024 * 1024)
        val maxMemoryMB = maxMemory / (1024 * 1024)
        val memoryUsagePercent = (usedMemory.toFloat() / maxMemory.toFloat())
        
        // Track peak memory usage
        if (usedMemory > peakMemoryUsage) {
            peakMemoryUsage = usedMemory
        }
        
        // Check for memory pressure
        if (memoryUsagePercent > MEMORY_PRESSURE_THRESHOLD) {
            memoryPressureEvents++
            Timber.w("📍 Memory pressure detected: ${usedMemoryMB}MB/${maxMemoryMB}MB (${(memoryUsagePercent * 100).toInt()}%)")
        }
        
        return MemoryUsageInfo(
            usedMemoryMB = usedMemoryMB,
            maxMemoryMB = maxMemoryMB,
            usagePercent = memoryUsagePercent,
            isPressure = memoryUsagePercent > MEMORY_PRESSURE_THRESHOLD
        )
    }
    
    /**
     * Records a high-frequency update event
     */
    fun recordHighFrequencyUpdate() {
        highFrequencyUpdates++
    }
    
    /**
     * Records a throttle event when performance is degraded
     */
    fun recordThrottleEvent() {
        throttleEvents++
        Timber.d("📍 Throttle event recorded. Total throttles: $throttleEvents")
    }
    
    /**
     * Calculates adaptive update frequency based on current performance metrics
     */
    fun calculateAdaptiveUpdateFrequency(digitalZoom: Float, deviceTier: DeviceTier): Long {
        val memoryInfo = monitorMemoryUsage()
        val cpuInfo = estimateCpuUsage()
        val avgCalculationTime = getAverageCalculationTime()
        
        val baseFrequency = when {
            digitalZoom > 3f -> 16L // 60fps for ultra-high zoom
            digitalZoom > 2f -> 20L // 50fps for high zoom
            else -> 33L // 30fps for normal zoom
        }
        
        // Adjust based on device capabilities
        val deviceMultiplier = when (deviceTier) {
            DeviceTier.HIGH_END -> 1.0
            DeviceTier.MID_RANGE -> 1.5
            DeviceTier.LOW_END -> 2.0
        }
        
        // Adjust based on memory pressure
        val memoryMultiplier = when {
            memoryInfo.isPressure -> 2.0
            memoryInfo.usagePercent > 0.6f -> 1.5
            else -> 1.0
        }
        
        // Adjust based on CPU usage
        val cpuMultiplier = when {
            cpuInfo.isHighUsage -> 2.5
            cpuInfo.currentUsage > 60.0 -> 1.8
            cpuInfo.currentUsage > 40.0 -> 1.3
            else -> 1.0
        }
        
        // Adjust based on calculation performance
        val performanceMultiplier = when {
            avgCalculationTime > 100.0 -> 2.0
            avgCalculationTime > 50.0 -> 1.5
            else -> 1.0
        }
        
        val adaptiveFrequency = (baseFrequency * deviceMultiplier * memoryMultiplier * cpuMultiplier * performanceMultiplier).toLong()
        
        // Apply CPU throttling if necessary
        val finalFrequency = if (shouldApplyCpuThrottling()) {
            calculateThrottledFrequency(adaptiveFrequency)
        } else {
            adaptiveFrequency
        }
        
        updateFrequencyMs = finalFrequency.coerceIn(8L, 200L) // Clamp between 5fps and 125fps
        
        if (isAdaptiveMode) {
            Timber.d("📍 Adaptive frequency: ${updateFrequencyMs}ms (${1000/updateFrequencyMs}fps) - Device: $deviceTier, Memory: ${(memoryInfo.usagePercent*100).toInt()}%, CPU: ${"%.1f".format(cpuInfo.currentUsage)}%, Calc: ${"%.1f".format(avgCalculationTime)}ms")
        }
        
        return updateFrequencyMs
    }
    
    /**
     * Enables or disables adaptive update frequency mode
     */
    fun setAdaptiveMode(enabled: Boolean) {
        isAdaptiveMode = enabled
        if (com.bizzkoot.qiblafinder.BuildConfig.DEBUG) {
            Timber.d("📍 Adaptive mode ${if (enabled) "enabled" else "disabled"} - Debug logging active")
        } else {
            Timber.d("📍 Adaptive mode ${if (enabled) "enabled" else "disabled"}")
        }
    }
    
    /**
     * Logs detailed performance metrics (debug builds only)
     */
    fun logDetailedPerformanceMetrics() {
        if (!com.bizzkoot.qiblafinder.BuildConfig.DEBUG) return
        
        val stats = getPerformanceStats()
        val memoryInfo = monitorMemoryUsage()
        val cpuInfo = estimateCpuUsage()
        
        Timber.d("""
            📍 Detailed Performance Metrics (DEBUG):
            ==========================================
            CALCULATIONS:
            - Total calculations: ${stats.totalCalculations}
            - Average calculation time: ${"%.2f".format(stats.averageCalculationTime)}ms
            - Consecutive slow calculations: ${stats.consecutiveSlowCalculations}
            
            CACHE PERFORMANCE:
            - Cache hit rate: ${"%.1f".format(stats.cacheHitRate)}%
            - Cache hits: ${stats.totalCacheHits}
            - Cache misses: ${stats.totalCacheMisses}
            
            MEMORY USAGE:
            - Current memory usage: ${memoryInfo.usedMemoryMB}MB / ${memoryInfo.maxMemoryMB}MB
            - Memory usage percentage: ${(memoryInfo.usagePercent * 100).toInt()}%
            - Peak memory usage: ${stats.peakMemoryUsageMB}MB
            - Memory pressure events: ${stats.memoryPressureEvents}
            - Memory pressure active: ${memoryInfo.isPressure}
            
            CPU PERFORMANCE:
            - Current CPU usage (estimated): ${"%.1f".format(cpuInfo.currentUsage)}%
            - Average CPU usage: ${"%.1f".format(cpuInfo.averageUsage)}%
            - High CPU events: ${stats.highCpuEvents}
            - High CPU usage active: ${cpuInfo.isHighUsage}
            
            UPDATE FREQUENCY:
            - Current update frequency: ${stats.currentUpdateFrequencyMs}ms (${1000/stats.currentUpdateFrequencyMs}fps)
            - High frequency updates: ${stats.highFrequencyUpdates}
            - Throttle events: ${stats.throttleEvents}
            - Adaptive mode: $isAdaptiveMode
            
            PERFORMANCE STATUS:
            - Performance acceptable: ${isPerformanceAcceptable()}
            - Should throttle for memory: ${shouldThrottleForMemoryPressure()}
            - Should apply CPU throttling: ${shouldApplyCpuThrottling()}
            ==========================================
        """.trimIndent())
    }
    
    /**
     * Logs performance warnings (debug builds only)
     */
    fun logPerformanceWarnings() {
        if (!com.bizzkoot.qiblafinder.BuildConfig.DEBUG) return
        
        val recommendations = getPerformanceRecommendations()
        if (recommendations.size > 1 || recommendations.first() != "Performance is within acceptable parameters") {
            Timber.w("""
                📍 Performance Warnings (DEBUG):
                ${recommendations.joinToString("\n- ", "- ")}
            """.trimIndent())
        }
    }
    
    /**
     * Logs memory allocation events during high-frequency updates (debug builds only)
     */
    fun logMemoryAllocation(operationName: String, beforeMemory: Long, afterMemory: Long) {
        if (!com.bizzkoot.qiblafinder.BuildConfig.DEBUG) return
        
        val memoryDiff = afterMemory - beforeMemory
        if (memoryDiff > 1024 * 1024) { // Log allocations > 1MB
            Timber.d("📍 Memory allocation during $operationName: ${memoryDiff / (1024 * 1024)}MB")
        }
    }
    
    /**
     * Starts performance profiling session (debug builds only)
     */
    fun startProfilingSession(sessionName: String) {
        if (!com.bizzkoot.qiblafinder.BuildConfig.DEBUG) return
        
        calculationStartTimes[sessionName] = System.currentTimeMillis()
        Timber.d("📍 Profiling session started: $sessionName")
    }
    
    /**
     * Ends performance profiling session and logs results (debug builds only)
     */
    fun endProfilingSession(sessionName: String) {
        if (!com.bizzkoot.qiblafinder.BuildConfig.DEBUG) return
        
        val startTime = calculationStartTimes[sessionName]
        if (startTime != null) {
            val duration = System.currentTimeMillis() - startTime
            calculationStartTimes.remove(sessionName)
            Timber.d("📍 Profiling session completed: $sessionName took ${duration}ms")
        }
    }
    
    /**
     * Gets current adaptive update frequency
     */
    fun getAdaptiveUpdateFrequency(): Long = updateFrequencyMs
    
    /**
     * Checks if system is under memory pressure and should throttle updates
     */
    fun shouldThrottleForMemoryPressure(): Boolean {
        val memoryInfo = monitorMemoryUsage()
        return memoryInfo.isPressure
    }
    
    /**
     * Creates a fallback configuration based on device tier and current performance
     */
    fun createFallbackConfig(deviceTier: DeviceTier, forceActivation: Boolean = false): FallbackConfig? {
        val memoryInfo = monitorMemoryUsage()
        val cpuInfo = estimateCpuUsage()
        val shouldActivate = forceActivation || 
                           deviceTier == DeviceTier.LOW_END ||
                           memoryInfo.isPressure ||
                           cpuInfo.isHighUsage ||
                           consecutiveSlowCalculations >= SLOW_CALCULATION_THRESHOLD ||
                           !isPerformanceAcceptable()
        
        if (!shouldActivate) return null
        
        return when (deviceTier) {
            DeviceTier.LOW_END -> FallbackConfig(
                maxUpdateFrequencyMs = 100L, // Max 10fps for low-end
                reducedSegments = true,
                disableHighPrecisionMode = true,
                simplifiedCalculations = true,
                aggressiveCaching = true
            )
            DeviceTier.MID_RANGE -> FallbackConfig(
                maxUpdateFrequencyMs = 50L, // Max 20fps for mid-range under pressure
                reducedSegments = true,
                disableHighPrecisionMode = cpuInfo.isHighUsage,
                simplifiedCalculations = memoryInfo.isPressure,
                aggressiveCaching = true
            )
            DeviceTier.HIGH_END -> FallbackConfig(
                maxUpdateFrequencyMs = 33L, // Max 30fps for high-end under pressure
                reducedSegments = false,
                disableHighPrecisionMode = false,
                simplifiedCalculations = memoryInfo.isPressure && cpuInfo.isHighUsage,
                aggressiveCaching = memoryInfo.isPressure
            )
        }
    }
    
    /**
     * Checks if fallback mode should be activated
     */
    fun shouldActivateFallbackMode(deviceTier: DeviceTier): Boolean {
        return createFallbackConfig(deviceTier) != null
    }
    
    /**
     * Creates emergency fallback config for critical performance issues
     */
    fun createEmergencyFallbackConfig(): FallbackConfig {
        return FallbackConfig(
            maxUpdateFrequencyMs = 200L, // Max 5fps
            reducedSegments = true,
            disableHighPrecisionMode = true,
            simplifiedCalculations = true,
            aggressiveCaching = true
        )
    }
    
    /**
     * Handles excessive memory usage during high-frequency updates
     */
    fun handleExcessiveMemoryUsage(): MemoryRecoveryAction {
        val memoryInfo = monitorMemoryUsage()
        val stats = getPerformanceStats()
        
        return when {
            memoryInfo.usagePercent > 0.95f -> {
                // Critical memory pressure - emergency actions
                Timber.e("📍 Critical memory pressure detected: ${(memoryInfo.usagePercent * 100).toInt()}%")
                MemoryRecoveryAction.EMERGENCY_STOP
            }
            memoryInfo.usagePercent > 0.90f -> {
                // Severe memory pressure - aggressive cleanup
                Timber.w("📍 Severe memory pressure detected: ${(memoryInfo.usagePercent * 100).toInt()}%")
                MemoryRecoveryAction.AGGRESSIVE_CLEANUP
            }
            memoryInfo.usagePercent > MEMORY_PRESSURE_THRESHOLD -> {
                // High memory usage - throttle updates
                Timber.w("📍 High memory usage detected: ${(memoryInfo.usagePercent * 100).toInt()}%")
                MemoryRecoveryAction.THROTTLE_UPDATES
            }
            stats.memoryPressureEvents > 10 -> {
                // Frequent memory pressure - enable aggressive caching
                Timber.w("📍 Frequent memory pressure events: ${stats.memoryPressureEvents}")
                MemoryRecoveryAction.ENABLE_AGGRESSIVE_CACHING
            }
            else -> {
                MemoryRecoveryAction.NO_ACTION
            }
        }
    }
    
    /**
     * Executes memory recovery action
     */
    fun executeMemoryRecovery(action: MemoryRecoveryAction): Boolean {
        return when (action) {
            MemoryRecoveryAction.EMERGENCY_STOP -> {
                Timber.e("📍 Executing emergency stop due to critical memory pressure")
                reset()
                System.gc() // Force garbage collection as emergency measure
                true
            }
            MemoryRecoveryAction.AGGRESSIVE_CLEANUP -> {
                Timber.w("📍 Executing aggressive memory cleanup")
                System.gc()
                Thread.sleep(100) // Give GC time to work
                val memoryAfterGc = monitorMemoryUsage()
                Timber.d("📍 Memory after cleanup: ${memoryAfterGc.usedMemoryMB}MB")
                true
            }
            MemoryRecoveryAction.THROTTLE_UPDATES -> {
                Timber.w("📍 Throttling updates due to memory pressure")
                recordThrottleEvent()
                true
            }
            MemoryRecoveryAction.ENABLE_AGGRESSIVE_CACHING -> {
                Timber.w("📍 Enabling aggressive caching due to frequent memory pressure")
                true
            }
            MemoryRecoveryAction.NO_ACTION -> false
        }
    }
    
    /**
     * Checks if memory recovery is needed and executes it
     */
    fun checkAndHandleMemoryPressure(): Boolean {
        val action = handleExcessiveMemoryUsage()
        return if (action != MemoryRecoveryAction.NO_ACTION) {
            executeMemoryRecovery(action)
        } else {
            false
        }
    }
    
    /**
     * Validates that memory usage is within safe limits before continuing high-frequency updates
     */
    fun validateMemoryForHighFrequencyUpdates(): Boolean {
        val memoryInfo = monitorMemoryUsage()
        
        return when {
            memoryInfo.usagePercent > 0.95f -> {
                Timber.e("📍 Cannot continue high-frequency updates: Critical memory pressure (${(memoryInfo.usagePercent * 100).toInt()}%)")
                false
            }
            memoryInfo.usagePercent > 0.90f -> {
                Timber.w("📍 High-frequency updates restricted: Severe memory pressure (${(memoryInfo.usagePercent * 100).toInt()}%)")
                false
            }
            else -> true
        }
    }
    
    /**
     * Records a calculation failure
     */
    fun recordCalculationFailure(errorMessage: String, exception: Exception? = null) {
        calculationFailures++
        consecutiveFailures++
        lastFailureTime = System.currentTimeMillis()
        
        if (com.bizzkoot.qiblafinder.BuildConfig.DEBUG) {
            Timber.w(exception, "📍 Calculation failure recorded: $errorMessage (consecutive: $consecutiveFailures)")
        } else {
            Timber.w("📍 Calculation failure recorded: $errorMessage (consecutive: $consecutiveFailures)")
        }
    }
    
    /**
     * Records a successful calculation (resets consecutive failure count)
     */
    fun recordCalculationSuccess() {
        consecutiveFailures = 0
    }
    
    /**
     * Checks if calculation recovery is needed
     */
    fun needsCalculationRecovery(): Boolean {
        return consecutiveFailures >= MAX_CONSECUTIVE_FAILURES
    }
    
    /**
     * Determines the appropriate recovery strategy for calculation failures
     */
    fun getCalculationRecoveryStrategy(digitalZoom: Float, isHighZoom: Boolean): CalculationRecoveryStrategy {
        val currentTime = System.currentTimeMillis()
        val timeSinceLastFailure = currentTime - lastFailureTime
        
        return when {
            consecutiveFailures >= MAX_CONSECUTIVE_FAILURES -> {
                Timber.e("📍 Critical calculation failure threshold reached: $consecutiveFailures failures")
                CalculationRecoveryStrategy.EMERGENCY_FALLBACK
            }
            consecutiveFailures >= 3 && isHighZoom -> {
                Timber.w("📍 Multiple calculation failures at high zoom: $consecutiveFailures failures")
                CalculationRecoveryStrategy.REDUCE_PRECISION
            }
            consecutiveFailures >= 2 && digitalZoom > 3f -> {
                Timber.w("📍 Calculation failures at ultra-high zoom: $consecutiveFailures failures")
                CalculationRecoveryStrategy.SIMPLIFY_CALCULATION
            }
            timeSinceLastFailure < FAILURE_RECOVERY_COOLDOWN_MS && consecutiveFailures > 0 -> {
                Timber.d("📍 Recent calculation failure, applying throttling")
                CalculationRecoveryStrategy.THROTTLE_UPDATES
            }
            else -> CalculationRecoveryStrategy.NO_RECOVERY
        }
    }
    
    /**
     * Executes calculation recovery strategy
     */
    fun executeCalculationRecovery(strategy: CalculationRecoveryStrategy): CalculationRecoveryConfig? {
        recoveryAttempts++
        
        return when (strategy) {
            CalculationRecoveryStrategy.EMERGENCY_FALLBACK -> {
                Timber.e("📍 Executing emergency fallback for calculation failures")
                CalculationRecoveryConfig(
                    reduceSegments = true,
                    disableHighPrecision = true,
                    simplifyCalculations = true,
                    useLastKnownGood = true,
                    maxRetries = 1,
                    fallbackToBasicCalculation = true
                )
            }
            CalculationRecoveryStrategy.REDUCE_PRECISION -> {
                Timber.w("📍 Reducing calculation precision due to failures")
                CalculationRecoveryConfig(
                    reduceSegments = true,
                    disableHighPrecision = true,
                    simplifyCalculations = false,
                    useLastKnownGood = false,
                    maxRetries = 2,
                    fallbackToBasicCalculation = false
                )
            }
            CalculationRecoveryStrategy.SIMPLIFY_CALCULATION -> {
                Timber.w("📍 Simplifying calculations due to failures")
                CalculationRecoveryConfig(
                    reduceSegments = true,
                    disableHighPrecision = false,
                    simplifyCalculations = true,
                    useLastKnownGood = false,
                    maxRetries = 3,
                    fallbackToBasicCalculation = false
                )
            }
            CalculationRecoveryStrategy.THROTTLE_UPDATES -> {
                Timber.d("📍 Throttling updates due to recent failures")
                CalculationRecoveryConfig(
                    reduceSegments = false,
                    disableHighPrecision = false,
                    simplifyCalculations = false,
                    useLastKnownGood = true,
                    maxRetries = 5,
                    fallbackToBasicCalculation = false
                )
            }
            CalculationRecoveryStrategy.NO_RECOVERY -> null
        }
    }
    
    /**
     * Performs safe calculation with recovery mechanisms
     */
    internal inline fun <T> safeCalculation(
        operationName: String,
        digitalZoom: Float = 1f,
        isHighZoom: Boolean = false,
        calculation: () -> T
    ): T? {
        return try {
            val result = measureCalculation(operationName, calculation)
            recordCalculationSuccess()
            result
        } catch (exception: Exception) {
            recordCalculationFailure("$operationName failed", exception)
            
            // Check if recovery is needed
            val recoveryStrategy = getCalculationRecoveryStrategy(digitalZoom, isHighZoom)
            if (recoveryStrategy != CalculationRecoveryStrategy.NO_RECOVERY) {
                Timber.w("📍 Applying recovery strategy: $recoveryStrategy for $operationName")
                executeCalculationRecovery(recoveryStrategy)
            }
            
            null
        }
    }
    
    /**
     * Gets calculation failure statistics
     */
    fun getCalculationFailureStats(): CalculationFailureStats {
        return CalculationFailureStats(
            totalFailures = calculationFailures,
            consecutiveFailures = consecutiveFailures,
            lastFailureTime = lastFailureTime,
            recoveryAttempts = recoveryAttempts,
            needsRecovery = needsCalculationRecovery()
        )
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
        val memoryInfo = monitorMemoryUsage()
        val cpuInfo = estimateCpuUsage()
        return PerformanceStats(
            totalCalculations = totalCalculations,
            averageCalculationTime = getAverageCalculationTime(),
            cacheHitRate = getCacheHitRate(),
            totalCacheHits = cacheHits,
            totalCacheMisses = cacheMisses,
            peakMemoryUsageMB = peakMemoryUsage / (1024 * 1024),
            currentMemoryUsageMB = memoryInfo.usedMemoryMB,
            memoryPressureEvents = memoryPressureEvents,
            highFrequencyUpdates = highFrequencyUpdates,
            throttleEvents = throttleEvents,
            currentUpdateFrequencyMs = updateFrequencyMs,
            currentCpuUsage = cpuInfo.currentUsage,
            averageCpuUsage = cpuInfo.averageUsage,
            highCpuEvents = highCpuEvents,
            consecutiveSlowCalculations = consecutiveSlowCalculations,
            calculationFailures = calculationFailures,
            consecutiveFailures = consecutiveFailures,
            recoveryAttempts = recoveryAttempts
        )
    }
    
    /**
     * Logs current performance statistics
     */
    fun logPerformanceStats(debugOnly: Boolean = false) {
        if (debugOnly && !com.bizzkoot.qiblafinder.BuildConfig.DEBUG) return
        
        val stats = getPerformanceStats()
        val logLevel = if (debugOnly) "DEBUG" else "INFO"
        
        Timber.i("""
            📍 Qibla Performance Statistics ${if (debugOnly) "($logLevel)" else ""}:
            - Total calculations: ${stats.totalCalculations}
            - Average calculation time: ${"%.2f".format(stats.averageCalculationTime)}ms
            - Cache hit rate: ${"%.1f".format(stats.cacheHitRate)}%
            - Cache hits: ${stats.totalCacheHits}
            - Cache misses: ${stats.totalCacheMisses}
            - Peak memory usage: ${stats.peakMemoryUsageMB}MB
            - Current memory usage: ${stats.currentMemoryUsageMB}MB
            - Memory pressure events: ${stats.memoryPressureEvents}
            - Current CPU usage: ${"%.1f".format(stats.currentCpuUsage)}%
            - Average CPU usage: ${"%.1f".format(stats.averageCpuUsage)}%
            - High CPU events: ${stats.highCpuEvents}
            - Consecutive slow calculations: ${stats.consecutiveSlowCalculations}
            - High frequency updates: ${stats.highFrequencyUpdates}
            - Throttle events: ${stats.throttleEvents}
            - Current update frequency: ${stats.currentUpdateFrequencyMs}ms (${1000/stats.currentUpdateFrequencyMs}fps)
        """.trimIndent())
        
        // Also log warnings if in debug mode
        if (debugOnly) {
            logPerformanceWarnings()
        }
    }
    
    /**
     * Resets all performance counters
     */
    fun reset() {
        totalCalculations = 0
        totalCalculationTime = 0L
        cacheHits = 0
        cacheMisses = 0
        peakMemoryUsage = 0L
        memoryPressureEvents = 0
        highFrequencyUpdates = 0
        throttleEvents = 0
        updateFrequencyMs = 16L
        isAdaptiveMode = false
        
        // Reset CPU monitoring
        cpuSampleCount = 0
        totalCpuUsage = 0.0
        lastCpuSampleTime = 0L
        highCpuEvents = 0
        calculationStartTimes.clear()
        consecutiveSlowCalculations = 0
        
        // Reset calculation failure tracking
        calculationFailures = 0
        consecutiveFailures = 0
        lastFailureTime = 0L
        recoveryAttempts = 0
        
        Timber.d("📍 Performance monitor reset")
    }
    
    /**
     * Checks if performance is within acceptable thresholds
     */
    fun isPerformanceAcceptable(): Boolean {
        val avgTime = getAverageCalculationTime()
        val hitRate = getCacheHitRate()
        val memoryInfo = monitorMemoryUsage()
        val cpuInfo = estimateCpuUsage()
        
        return avgTime <= MAX_AVG_CALCULATION_TIME_MS && // Average calculation should be under 100ms
               (totalCalculations < 10 || hitRate >= MIN_CACHE_HIT_RATE) && // Cache hit rate should be at least 50% after 10 calculations
               !memoryInfo.isPressure && // No memory pressure
               memoryInfo.usedMemoryMB <= MAX_MEMORY_USAGE_MB && // Memory usage within limits
               !cpuInfo.isHighUsage && // No high CPU usage
               consecutiveSlowCalculations < SLOW_CALCULATION_THRESHOLD // No consecutive slow calculations
    }
    
    /**
     * Gets performance recommendations based on current statistics
     */
    fun getPerformanceRecommendations(): List<String> {
        val recommendations = mutableListOf<String>()
        val stats = getPerformanceStats()
        val memoryInfo = monitorMemoryUsage()
        val cpuInfo = estimateCpuUsage()
        
        if (stats.averageCalculationTime > MAX_AVG_CALCULATION_TIME_MS) {
            recommendations.add("Consider reducing path segments or enabling high-performance mode")
        }
        
        if (stats.cacheHitRate < MIN_CACHE_HIT_RATE && stats.totalCalculations > 10) {
            recommendations.add("Cache hit rate is low - check if locations are changing too frequently")
        }
        
        if (stats.totalCalculations > 100 && stats.averageCalculationTime > 50.0) {
            recommendations.add("High calculation frequency with slow performance - consider throttling")
        }
        
        if (memoryInfo.isPressure || stats.memoryPressureEvents > 5) {
            recommendations.add("Memory pressure detected - consider reducing update frequency or enabling low-end fallback")
        }
        
        if (stats.currentMemoryUsageMB > MAX_MEMORY_USAGE_MB) {
            recommendations.add("High memory usage - enable aggressive caching cleanup")
        }
        
        if (cpuInfo.isHighUsage || stats.highCpuEvents > 5) {
            recommendations.add("High CPU usage detected - enable CPU throttling or reduce update frequency")
        }
        
        if (stats.consecutiveSlowCalculations >= SLOW_CALCULATION_THRESHOLD) {
            recommendations.add("Consecutive slow calculations detected - consider reducing calculation complexity")
        }
        
        if (stats.throttleEvents > 10) {
            recommendations.add("Frequent throttling detected - consider adaptive update frequency")
        }
        
        if (stats.highFrequencyUpdates > 1000 && stats.averageCalculationTime > 30.0) {
            recommendations.add("High frequency updates with slow calculations - optimize performance")
        }
        
        if (cpuInfo.currentUsage > 90.0 && memoryInfo.isPressure) {
            recommendations.add("Critical performance issue: both CPU and memory are under pressure")
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
        val totalCacheMisses: Int,
        val peakMemoryUsageMB: Long,
        val currentMemoryUsageMB: Long,
        val memoryPressureEvents: Int,
        val highFrequencyUpdates: Int,
        val throttleEvents: Int,
        val currentUpdateFrequencyMs: Long,
        val currentCpuUsage: Double,
        val averageCpuUsage: Double,
        val highCpuEvents: Int,
        val consecutiveSlowCalculations: Int,
        val calculationFailures: Int,
        val consecutiveFailures: Int,
        val recoveryAttempts: Int
    )
    
    data class MemoryUsageInfo(
        val usedMemoryMB: Long,
        val maxMemoryMB: Long,
        val usagePercent: Float,
        val isPressure: Boolean
    )
    
    data class CpuUsageInfo(
        val currentUsage: Double,
        val averageUsage: Double,
        val isHighUsage: Boolean,
        val consecutiveSlowCalculations: Int,
        val highCpuEvents: Int
    )
    
    data class FallbackConfig(
        val maxUpdateFrequencyMs: Long,
        val reducedSegments: Boolean,
        val disableHighPrecisionMode: Boolean,
        val simplifiedCalculations: Boolean,
        val aggressiveCaching: Boolean
    )
    
    enum class DeviceTier {
        HIGH_END,
        MID_RANGE,
        LOW_END
    }
    
    enum class MemoryRecoveryAction {
        NO_ACTION,
        ENABLE_AGGRESSIVE_CACHING,
        THROTTLE_UPDATES,
        AGGRESSIVE_CLEANUP,
        EMERGENCY_STOP
    }
    
    enum class CalculationRecoveryStrategy {
        NO_RECOVERY,
        THROTTLE_UPDATES,
        SIMPLIFY_CALCULATION,
        REDUCE_PRECISION,
        EMERGENCY_FALLBACK
    }
    
    data class CalculationRecoveryConfig(
        val reduceSegments: Boolean,
        val disableHighPrecision: Boolean,
        val simplifyCalculations: Boolean,
        val useLastKnownGood: Boolean,
        val maxRetries: Int,
        val fallbackToBasicCalculation: Boolean
    )
    
    data class CalculationFailureStats(
        val totalFailures: Int,
        val consecutiveFailures: Int,
        val lastFailureTime: Long,
        val recoveryAttempts: Int,
        val needsRecovery: Boolean
    )
}