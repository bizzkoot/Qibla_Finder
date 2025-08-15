package com.bizzkoot.qiblafinder.ui.location

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.delay
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before
import org.junit.runner.RunWith
import kotlin.system.measureTimeMillis

/**
 * Performance benchmark tests for measuring memory usage impact, CPU performance,
 * and frame rate maintenance during high-frequency Qibla arrow updates
 */
@RunWith(AndroidJUnit4::class)
class QiblaPerformanceBenchmarkTest {

    @Before
    fun setUp() {
        QiblaPerformanceMonitor.reset()
        // Force garbage collection before tests
        System.gc()
        Thread.sleep(100)
    }

    @Test
    fun `benchmark update frequency accuracy across different device tiers`() = runTest {
        // Given: Different device tier configurations
        val deviceTiers = listOf(
            QiblaPerformanceMonitor.DeviceTier.HIGH_END,
            QiblaPerformanceMonitor.DeviceTier.MID_RANGE,
            QiblaPerformanceMonitor.DeviceTier.LOW_END
        )
        
        val digitalZoomLevels = listOf(2f, 3f, 4f)
        val benchmarkResults = mutableMapOf<String, List<Long>>()
        
        // When: Benchmarking each configuration
        deviceTiers.forEach { deviceTier ->
            digitalZoomLevels.forEach { digitalZoom ->
                val testKey = "${deviceTier}_${digitalZoom}x"
                val measuredIntervals = mutableListOf<Long>()
                
                // Measure actual update intervals
                val targetFrequency = QiblaPerformanceMonitor.calculateAdaptiveUpdateFrequency(digitalZoom, deviceTier)
                val testIterations = 20
                
                repeat(testIterations) { iteration ->
                    val actualInterval = measureTimeMillis {
                        delay(targetFrequency)
                        QiblaPerformanceMonitor.recordHighFrequencyUpdate()
                    }
                    measuredIntervals.add(actualInterval)
                }
                
                benchmarkResults[testKey] = measuredIntervals
            }
        }
        
        // Then: Analyze benchmark results
        benchmarkResults.forEach { (testKey, intervals) ->
            val averageInterval = intervals.average()
            val intervalStdDev = calculateStandardDeviation(intervals)
            val minInterval = intervals.minOrNull() ?: 0L
            val maxInterval = intervals.maxOrNull() ?: 0L
            
            println("📊 Benchmark Results for $testKey:")
            println("   Average interval: ${averageInterval}ms")
            println("   Std deviation: ${intervalStdDev}ms")
            println("   Min interval: ${minInterval}ms")
            println("   Max interval: ${maxInterval}ms")
            
            // Assertions for performance consistency
            assertTrue("Interval standard deviation should be low for $testKey", intervalStdDev < 10.0)
            assertTrue("All intervals should be reasonable for $testKey", minInterval > 0L && maxInterval < 200L)
        }
        
        // Verify device tier performance ordering
        val highEndAvg = benchmarkResults.filter { it.key.contains("HIGH_END") }.values.flatten().average()
        val lowEndAvg = benchmarkResults.filter { it.key.contains("LOW_END") }.values.flatten().average()
        
        assertTrue("High-end devices should have better (lower) intervals than low-end", highEndAvg <= lowEndAvg)
    }

    @Test
    fun `measure memory usage impact of high-frequency updates`() = runTest {
        // Given: Initial memory state
        val initialMemory = QiblaPerformanceMonitor.monitorMemoryUsage()
        val memoryMeasurements = mutableListOf<QiblaPerformanceMonitor.MemoryUsageInfo>()
        
        // Record baseline
        memoryMeasurements.add(initialMemory)
        
        // When: Performing intensive high-frequency updates
        val updateCount = 1000
        val startTime = System.currentTimeMillis()
        
        repeat(updateCount) { iteration ->
            QiblaPerformanceMonitor.recordHighFrequencyUpdate()
            
            // Simulate calculation work with varying complexity
            QiblaPerformanceMonitor.measureCalculation("Memory Benchmark $iteration") {
                // Simulate memory-intensive operations
                val tempData = Array(100) { Math.random() }
                tempData.sum() // Use the data to prevent optimization
            }
            
            // Measure memory every 100 iterations
            if (iteration % 100 == 0) {
                val currentMemory = QiblaPerformanceMonitor.monitorMemoryUsage()
                memoryMeasurements.add(currentMemory)
            }
            
            delay(8) // Ultra-high frequency updates
        }
        
        val endTime = System.currentTimeMillis()
        val totalDuration = endTime - startTime
        val finalMemory = QiblaPerformanceMonitor.monitorMemoryUsage()
        
        // Then: Analyze memory impact
        val initialUsageMB = initialMemory.usedMemoryMB
        val finalUsageMB = finalMemory.usedMemoryMB
        val memoryIncrease = finalUsageMB - initialUsageMB
        val maxMemoryUsage = memoryMeasurements.maxByOrNull { it.usedMemoryMB }?.usedMemoryMB ?: 0L
        
        println("📊 Memory Usage Impact Benchmark:")
        println("   Updates performed: $updateCount")
        println("   Total duration: ${totalDuration}ms")
        println("   Initial memory: ${initialUsageMB}MB")
        println("   Final memory: ${finalUsageMB}MB")
        println("   Memory increase: ${memoryIncrease}MB")
        println("   Peak memory: ${maxMemoryUsage}MB")
        println("   Memory efficiency: ${updateCount.toFloat() / memoryIncrease} updates/MB")
        
        // Memory usage should be reasonable
        assertTrue("Memory increase should be manageable (< 20MB)", memoryIncrease < 20L)
        assertTrue("Peak memory should not exceed 80% of max", maxMemoryUsage < finalMemory.maxMemoryMB * 0.8)
        
        val memoryPressureEvents = memoryMeasurements.count { it.isPressure }
        assertTrue("Memory pressure events should be minimal", memoryPressureEvents < memoryMeasurements.size * 0.2)
    }

    @Test
    fun `test CPU usage during maximum digital zoom with continuous panning`() = runTest {
        // Given: Maximum zoom with continuous panning simulation
        val maxDigitalZoom = 4f
        val panDuration = 3000L // 3 seconds of continuous panning
        val cpuMeasurements = mutableListOf<QiblaPerformanceMonitor.CpuUsageInfo>()
        val calculationTimes = mutableListOf<Double>()
        
        val startTime = System.currentTimeMillis()
        var panIterations = 0
        
        // When: Simulating continuous panning at max zoom
        while ((System.currentTimeMillis() - startTime) < panDuration) {
            panIterations++
            
            // Simulate pan gesture calculation
            val calculationTime = QiblaPerformanceMonitor.measureCalculation("Max Zoom Pan $panIterations") {
                // Simulate complex calculation at high zoom
                repeat(1000) {
                    Math.sin(Math.random() * Math.PI)
                }
                
                // Simulate Qibla direction calculation
                val bearing = Math.atan2(39.8262 - 55.2708, 21.4225 - 25.2048) * 180 / Math.PI
                bearing // Return to prevent optimization
            }
            
            QiblaPerformanceMonitor.recordHighFrequencyUpdate()
            
            // Measure CPU usage periodically
            if (panIterations % 50 == 0) {
                val cpuInfo = QiblaPerformanceMonitor.estimateCpuUsage()
                cpuMeasurements.add(cpuInfo)
                calculationTimes.add(QiblaPerformanceMonitor.getAverageCalculationTime())
            }
            
            delay(8) // Ultra-high frequency (125fps target)
        }
        
        val endTime = System.currentTimeMillis()
        val totalDuration = endTime - startTime
        
        // Then: Analyze CPU performance
        val averageCpuUsage = cpuMeasurements.map { it.currentUsage }.average()
        val maxCpuUsage = cpuMeasurements.maxByOrNull { it.currentUsage }?.currentUsage ?: 0.0
        val averageCalculationTime = calculationTimes.average()
        val highCpuEvents = cpuMeasurements.count { it.isHighUsage }
        
        println("📊 CPU Usage Benchmark (Max Zoom + Continuous Panning):")
        println("   Pan iterations: $panIterations")
        println("   Total duration: ${totalDuration}ms")
        println("   Average CPU usage: ${averageCpuUsage}%")
        println("   Peak CPU usage: ${maxCpuUsage}%")
        println("   Average calculation time: ${averageCalculationTime}ms")
        println("   High CPU events: $highCpuEvents/${cpuMeasurements.size}")
        println("   Effective frame rate: ${(panIterations * 1000) / totalDuration}fps")
        
        // CPU usage should be reasonable
        assertTrue("Average CPU usage should be manageable (< 80%)", averageCpuUsage < 80.0)
        assertTrue("Peak CPU usage should not exceed 95%", maxCpuUsage < 95.0)
        assertTrue("Average calculation time should be fast (< 50ms)", averageCalculationTime < 50.0)
        assertTrue("High CPU events should be limited", highCpuEvents < cpuMeasurements.size * 0.3)
        
        // Should maintain reasonable frame rate
        val effectiveFrameRate = (panIterations * 1000) / totalDuration
        assertTrue("Should maintain > 60fps effective rate", effectiveFrameRate > 60)
    }

    @Test
    fun `validate frame rate maintenance during intensive arrow updates`() = runTest {
        // Given: Frame rate measurement setup
        val targetFps = 60L
        val targetInterval = 1000L / targetFps // ~16.67ms
        val testDuration = 2000L // 2 seconds
        val frameTimestamps = mutableListOf<Long>()
        
        val startTime = System.currentTimeMillis()
        var frameCount = 0
        
        // When: Performing intensive arrow updates
        while ((System.currentTimeMillis() - startTime) < testDuration) {
            val frameStartTime = System.currentTimeMillis()
            
            // Simulate arrow update work
            QiblaPerformanceMonitor.measureCalculation("Frame Update $frameCount") {
                // Simulate arrow calculation and rendering work
                repeat(500) {
                    Math.cos(Math.random() * Math.PI * 2)
                }
            }
            
            QiblaPerformanceMonitor.recordHighFrequencyUpdate()
            frameTimestamps.add(frameStartTime)
            frameCount++
            
            delay(targetInterval)
        }
        
        val endTime = System.currentTimeMillis()
        val actualDuration = endTime - startTime
        
        // Then: Analyze frame rate performance
        val actualFps = (frameCount * 1000L) / actualDuration
        val frameIntervals = frameTimestamps.zipWithNext { a, b -> b - a }
        val averageInterval = frameIntervals.average()
        val intervalVariance = frameIntervals.map { kotlin.math.abs(it - averageInterval) }.average()
        val droppedFrames = frameIntervals.count { it > targetInterval * 1.5 } // Frames taking >50% longer
        
        println("📊 Frame Rate Maintenance Benchmark:")
        println("   Target FPS: ${targetFps}")
        println("   Actual FPS: ${actualFps}")
        println("   Total frames: $frameCount")
        println("   Average interval: ${averageInterval}ms")
        println("   Interval variance: ${intervalVariance}ms")
        println("   Dropped frames: $droppedFrames")
        println("   Frame consistency: ${((frameIntervals.size - droppedFrames) * 100) / frameIntervals.size}%")
        
        // Frame rate should be maintained
        assertTrue("Should achieve at least 90% of target FPS", actualFps >= targetFps * 0.9)
        assertTrue("Average interval should be close to target", kotlin.math.abs(averageInterval - targetInterval) < 5.0)
        assertTrue("Interval variance should be low (< 8ms)", intervalVariance < 8.0)
        assertTrue("Dropped frames should be minimal (< 10%)", droppedFrames < frameIntervals.size * 0.1)
        
        val stats = QiblaPerformanceMonitor.getPerformanceStats()
        assertEquals("Should track all frame updates", frameCount, stats.highFrequencyUpdates)
    }

    @Test
    fun `profile performance impact on overall app responsiveness`() = runTest {
        // Given: App responsiveness simulation with background Qibla updates
        val responsivenessMeasurements = mutableListOf<Long>()
        val backgroundUpdateCount = 500
        
        // Simulate typical app operations during background Qibla updates
        val appOperations = listOf(
            { Thread.sleep(10) }, // UI update simulation
            { repeat(1000) { Math.random() } }, // Light computation
            { Thread.sleep(5) }, // Quick I/O simulation
            { repeat(500) { Math.sin(Math.random()) } } // Math operations
        )
        
        // When: Running app operations with concurrent Qibla updates
        repeat(backgroundUpdateCount) { iteration ->
            // Start background Qibla update
            val qiblaUpdateTime = measureTimeMillis {
                QiblaPerformanceMonitor.measureCalculation("Background Update $iteration") {
                    // Simulate Qibla calculation
                    repeat(200) { Math.atan2(Math.random(), Math.random()) }
                }
                QiblaPerformanceMonitor.recordHighFrequencyUpdate()
            }
            
            // Measure app operation responsiveness
            val operationIndex = iteration % appOperations.size
            val appOperationTime = measureTimeMillis {
                appOperations[operationIndex]()
            }
            
            responsivenessMeasurements.add(appOperationTime)
            
            delay(20) // Moderate update frequency
        }
        
        // Then: Analyze overall app responsiveness
        val averageResponseTime = responsivenessMeasurements.average()
        val maxResponseTime = responsivenessMeasurements.maxOrNull() ?: 0L
        val p95ResponseTime = responsivenessMeasurements.sorted().let { sorted ->
            sorted[(sorted.size * 0.95).toInt()]
        }
        
        val stats = QiblaPerformanceMonitor.getPerformanceStats()
        
        println("📊 App Responsiveness Impact Analysis:")
        println("   Background updates: $backgroundUpdateCount")
        println("   Average response time: ${averageResponseTime}ms")
        println("   Max response time: ${maxResponseTime}ms")
        println("   P95 response time: ${p95ResponseTime}ms")
        println("   Average Qibla calc time: ${stats.averageCalculationTime}ms")
        println("   Memory pressure events: ${stats.memoryPressureEvents}")
        println("   Throttle events: ${stats.throttleEvents}")
        
        // App should remain responsive
        assertTrue("Average response time should be fast (< 25ms)", averageResponseTime < 25.0)
        assertTrue("Max response time should be reasonable (< 50ms)", maxResponseTime < 50L)
        assertTrue("P95 response time should be good (< 35ms)", p95ResponseTime < 35L)
        assertTrue("Qibla calculations should not be too heavy", stats.averageCalculationTime < 30.0)
        
        // System should not be under excessive pressure
        assertTrue("Memory pressure should be minimal", stats.memoryPressureEvents < 5)
        assertTrue("Throttling should be minimal", stats.throttleEvents < backgroundUpdateCount * 0.1)
    }

    /**
     * Helper function to calculate standard deviation
     */
    private fun calculateStandardDeviation(values: List<Long>): Double {
        val mean = values.average()
        val variance = values.map { (it - mean) * (it - mean) }.average()
        return kotlin.math.sqrt(variance)
    }
}