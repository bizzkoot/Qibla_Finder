package com.bizzkoot.qiblafinder.model

import com.bizzkoot.qiblafinder.ui.location.MapLocation
import org.junit.Test
import org.junit.Assert.*
import kotlin.math.*
import kotlin.random.Random

/**
 * Comprehensive performance benchmark tests for geodesy calculations
 * These tests measure performance characteristics and ensure calculations
 * meet performance requirements under various conditions.
 */
class GeodesyPerformanceBenchmarkTest {
    
    companion object {
        private const val PERFORMANCE_ITERATIONS = 1000
        private const val STRESS_ITERATIONS = 10000
        private const val MEMORY_TEST_ITERATIONS = 100
        
        // Performance thresholds (in milliseconds)
        private const val BEARING_CALCULATION_THRESHOLD = 1.0
        private const val DISTANCE_CALCULATION_THRESHOLD = 1.0
        private const val PATH_GENERATION_THRESHOLD = 50.0
        private const val LARGE_PATH_GENERATION_THRESHOLD = 200.0
        
        // Test locations for consistent benchmarking
        private const val NEW_YORK_LAT = 40.7128
        private const val NEW_YORK_LON = -74.0060
        private const val LONDON_LAT = 51.5074
        private const val LONDON_LON = -0.1278
        private const val SYDNEY_LAT = -33.8688
        private const val SYDNEY_LON = 151.2093
        private const val TOKYO_LAT = 35.6762
        private const val TOKYO_LON = 139.6503
    }
    
    // --- Bearing Calculation Performance ---
    
    @Test
    fun benchmarkBearingCalculation_singleLocation() {
        val iterations = PERFORMANCE_ITERATIONS
        val startTime = System.currentTimeMillis()
        
        repeat(iterations) {
            GeodesyUtils.calculateQiblaBearing(NEW_YORK_LAT, NEW_YORK_LON)
        }
        
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        val avgTime = duration.toDouble() / iterations
        
        assertTrue("Bearing calculation should be fast: ${avgTime}ms per calculation", 
            avgTime < BEARING_CALCULATION_THRESHOLD)
        
        println("Bearing calculation benchmark: ${avgTime}ms average over $iterations iterations")
    }
    
    @Test
    fun benchmarkBearingCalculation_multipleLocations() {
        val locations = listOf(
            Pair(NEW_YORK_LAT, NEW_YORK_LON),
            Pair(LONDON_LAT, LONDON_LON),
            Pair(SYDNEY_LAT, SYDNEY_LON),
            Pair(TOKYO_LAT, TOKYO_LON)
        )
        
        val iterations = PERFORMANCE_ITERATIONS / locations.size
        val startTime = System.currentTimeMillis()
        
        repeat(iterations) {
            locations.forEach { (lat, lon) ->
                GeodesyUtils.calculateQiblaBearing(lat, lon)
            }
        }
        
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        val totalCalculations = iterations * locations.size
        val avgTime = duration.toDouble() / totalCalculations
        
        assertTrue("Multi-location bearing calculation should be fast: ${avgTime}ms per calculation", 
            avgTime < BEARING_CALCULATION_THRESHOLD)
        
        println("Multi-location bearing benchmark: ${avgTime}ms average over $totalCalculations calculations")
    }
    
    @Test
    fun benchmarkBearingCalculation_randomLocations() {
        val random = Random(42) // Fixed seed for reproducible results
        val iterations = PERFORMANCE_ITERATIONS
        val startTime = System.currentTimeMillis()
        
        repeat(iterations) {
            val lat = random.nextDouble(-90.0, 90.0)
            val lon = random.nextDouble(-180.0, 180.0)
            GeodesyUtils.calculateQiblaBearing(lat, lon)
        }
        
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        val avgTime = duration.toDouble() / iterations
        
        assertTrue("Random location bearing calculation should be fast: ${avgTime}ms per calculation", 
            avgTime < BEARING_CALCULATION_THRESHOLD)
        
        println("Random location bearing benchmark: ${avgTime}ms average over $iterations calculations")
    }
    
    // --- Distance Calculation Performance ---
    
    @Test
    fun benchmarkDistanceCalculation_singlePair() {
        val iterations = PERFORMANCE_ITERATIONS
        val startTime = System.currentTimeMillis()
        
        repeat(iterations) {
            GeodesyUtils.calculateDistance(NEW_YORK_LAT, NEW_YORK_LON, LONDON_LAT, LONDON_LON)
        }
        
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        val avgTime = duration.toDouble() / iterations
        
        assertTrue("Distance calculation should be fast: ${avgTime}ms per calculation", 
            avgTime < DISTANCE_CALCULATION_THRESHOLD)
        
        println("Distance calculation benchmark: ${avgTime}ms average over $iterations iterations")
    }
    
    @Test
    fun benchmarkDistanceCalculation_multiplePairs() {
        val locationPairs = listOf(
            Pair(Pair(NEW_YORK_LAT, NEW_YORK_LON), Pair(LONDON_LAT, LONDON_LON)),
            Pair(Pair(LONDON_LAT, LONDON_LON), Pair(SYDNEY_LAT, SYDNEY_LON)),
            Pair(Pair(SYDNEY_LAT, SYDNEY_LON), Pair(TOKYO_LAT, TOKYO_LON)),
            Pair(Pair(TOKYO_LAT, TOKYO_LON), Pair(NEW_YORK_LAT, NEW_YORK_LON))
        )
        
        val iterations = PERFORMANCE_ITERATIONS / locationPairs.size
        val startTime = System.currentTimeMillis()
        
        repeat(iterations) {
            locationPairs.forEach { (start, end) ->
                GeodesyUtils.calculateDistance(start.first, start.second, end.first, end.second)
            }
        }
        
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        val totalCalculations = iterations * locationPairs.size
        val avgTime = duration.toDouble() / totalCalculations
        
        assertTrue("Multi-pair distance calculation should be fast: ${avgTime}ms per calculation", 
            avgTime < DISTANCE_CALCULATION_THRESHOLD)
        
        println("Multi-pair distance benchmark: ${avgTime}ms average over $totalCalculations calculations")
    }
    
    @Test
    fun benchmarkDistanceCalculation_randomPairs() {
        val random = Random(42)
        val iterations = PERFORMANCE_ITERATIONS
        val startTime = System.currentTimeMillis()
        
        repeat(iterations) {
            val lat1 = random.nextDouble(-90.0, 90.0)
            val lon1 = random.nextDouble(-180.0, 180.0)
            val lat2 = random.nextDouble(-90.0, 90.0)
            val lon2 = random.nextDouble(-180.0, 180.0)
            GeodesyUtils.calculateDistance(lat1, lon1, lat2, lon2)
        }
        
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        val avgTime = duration.toDouble() / iterations
        
        assertTrue("Random pair distance calculation should be fast: ${avgTime}ms per calculation", 
            avgTime < DISTANCE_CALCULATION_THRESHOLD)
        
        println("Random pair distance benchmark: ${avgTime}ms average over $iterations calculations")
    }
    
    // --- Path Generation Performance ---
    
    @Test
    fun benchmarkPathGeneration_smallPaths() {
        val segments = 25
        val iterations = PERFORMANCE_ITERATIONS / 10 // Fewer iterations for path generation
        val startTime = System.currentTimeMillis()
        
        repeat(iterations) {
            GeodesyUtils.calculateGreatCirclePath(
                NEW_YORK_LAT, NEW_YORK_LON,
                LONDON_LAT, LONDON_LON,
                segments
            )
        }
        
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        val avgTime = duration.toDouble() / iterations
        
        assertTrue("Small path generation should be fast: ${avgTime}ms per path", 
            avgTime < PATH_GENERATION_THRESHOLD)
        
        println("Small path generation benchmark: ${avgTime}ms average for $segments segments over $iterations iterations")
    }
    
    @Test
    fun benchmarkPathGeneration_mediumPaths() {
        val segments = 100
        val iterations = PERFORMANCE_ITERATIONS / 20
        val startTime = System.currentTimeMillis()
        
        repeat(iterations) {
            GeodesyUtils.calculateGreatCirclePath(
                NEW_YORK_LAT, NEW_YORK_LON,
                SYDNEY_LAT, SYDNEY_LON,
                segments
            )
        }
        
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        val avgTime = duration.toDouble() / iterations
        
        assertTrue("Medium path generation should be reasonable: ${avgTime}ms per path", 
            avgTime < PATH_GENERATION_THRESHOLD * 2)
        
        println("Medium path generation benchmark: ${avgTime}ms average for $segments segments over $iterations iterations")
    }
    
    @Test
    fun benchmarkPathGeneration_largePaths() {
        val segments = 500
        val iterations = PERFORMANCE_ITERATIONS / 100
        val startTime = System.currentTimeMillis()
        
        repeat(iterations) {
            GeodesyUtils.calculateGreatCirclePath(
                NEW_YORK_LAT, NEW_YORK_LON,
                SYDNEY_LAT, SYDNEY_LON,
                segments
            )
        }
        
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        val avgTime = duration.toDouble() / iterations
        
        assertTrue("Large path generation should complete reasonably: ${avgTime}ms per path", 
            avgTime < LARGE_PATH_GENERATION_THRESHOLD)
        
        println("Large path generation benchmark: ${avgTime}ms average for $segments segments over $iterations iterations")
    }
    
    @Test
    fun benchmarkPathGeneration_varyingSegments() {
        val segmentCounts = listOf(10, 25, 50, 100, 200)
        val iterations = 20
        
        segmentCounts.forEach { segments ->
            val startTime = System.currentTimeMillis()
            
            repeat(iterations) {
                GeodesyUtils.calculateGreatCirclePath(
                    NEW_YORK_LAT, NEW_YORK_LON,
                    LONDON_LAT, LONDON_LON,
                    segments
                )
            }
            
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            val avgTime = duration.toDouble() / iterations
            
            val expectedThreshold = PATH_GENERATION_THRESHOLD * (segments / 50.0)
            assertTrue("Path generation with $segments segments should be reasonable: ${avgTime}ms per path", 
                avgTime < expectedThreshold)
            
            println("Path generation benchmark ($segments segments): ${avgTime}ms average over $iterations iterations")
        }
    }
    
    // --- Memory Performance Tests ---
    
    @Test
    fun benchmarkMemoryUsage_bearingCalculations() {
        val runtime = Runtime.getRuntime()
        val initialMemory = runtime.totalMemory() - runtime.freeMemory()
        
        val results = mutableListOf<Double>()
        repeat(MEMORY_TEST_ITERATIONS) { i ->
            val lat = -90.0 + (i % 180)
            val lon = -180.0 + (i % 360)
            val bearing = GeodesyUtils.calculateQiblaBearing(lat, lon)
            results.add(bearing)
        }
        
        val finalMemory = runtime.totalMemory() - runtime.freeMemory()
        val memoryIncrease = finalMemory - initialMemory
        val memoryPerCalculation = memoryIncrease / results.size
        
        assertTrue("Memory usage for bearing calculations should be minimal: ${memoryPerCalculation} bytes per calculation", 
            memoryPerCalculation < 1024) // Less than 1KB per calculation
        
        println("Bearing calculation memory usage: ${memoryPerCalculation} bytes per calculation")
        
        // Clear references
        results.clear()
    }
    
    @Test
    fun benchmarkMemoryUsage_pathGeneration() {
        val runtime = Runtime.getRuntime()
        val initialMemory = runtime.totalMemory() - runtime.freeMemory()
        
        val paths = mutableListOf<List<MapLocation>>()
        repeat(20) { i ->
            val path = GeodesyUtils.calculateGreatCirclePath(
                NEW_YORK_LAT + i * 0.1, NEW_YORK_LON + i * 0.1,
                LONDON_LAT + i * 0.1, LONDON_LON + i * 0.1,
                100
            )
            paths.add(path)
        }
        
        val finalMemory = runtime.totalMemory() - runtime.freeMemory()
        val memoryIncrease = finalMemory - initialMemory
        val memoryPerPath = memoryIncrease / paths.size
        
        assertTrue("Memory usage for path generation should be reasonable: ${memoryPerPath} bytes per path", 
            memoryPerPath < 1024 * 100) // Less than 100KB per path
        
        println("Path generation memory usage: ${memoryPerPath} bytes per path with 100 segments")
        
        // Clear references
        paths.clear()
    }
    
    @Test
    fun benchmarkMemoryUsage_largePathGeneration() {
        val runtime = Runtime.getRuntime()
        val initialMemory = runtime.totalMemory() - runtime.freeMemory()
        
        val largePaths = mutableListOf<List<MapLocation>>()
        repeat(5) { i ->
            val path = GeodesyUtils.calculateGreatCirclePath(
                NEW_YORK_LAT + i, NEW_YORK_LON + i,
                SYDNEY_LAT + i, SYDNEY_LON + i,
                500
            )
            largePaths.add(path)
        }
        
        val finalMemory = runtime.totalMemory() - runtime.freeMemory()
        val memoryIncrease = finalMemory - initialMemory
        val memoryPerPath = memoryIncrease / largePaths.size
        
        assertTrue("Memory usage for large path generation should be manageable: ${memoryPerPath} bytes per path", 
            memoryPerPath < 1024 * 500) // Less than 500KB per large path
        
        println("Large path generation memory usage: ${memoryPerPath} bytes per path with 500 segments")
        
        // Clear references
        largePaths.clear()
    }
    
    // --- Stress Tests ---
    
    @Test
    fun stressTestBearingCalculation() {
        val iterations = STRESS_ITERATIONS
        val random = Random(42)
        val startTime = System.currentTimeMillis()
        
        var successCount = 0
        var errorCount = 0
        
        repeat(iterations) {
            try {
                val lat = random.nextDouble(-90.0, 90.0)
                val lon = random.nextDouble(-180.0, 180.0)
                val bearing = GeodesyUtils.calculateQiblaBearing(lat, lon)
                
                if (bearing >= 0.0 && bearing <= 360.0) {
                    successCount++
                } else {
                    errorCount++
                }
            } catch (e: Exception) {
                errorCount++
            }
        }
        
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        val avgTime = duration.toDouble() / iterations
        val successRate = successCount.toDouble() / iterations
        
        assertTrue("Stress test should have high success rate: ${successRate * 100}%", successRate > 0.99)
        assertTrue("Stress test should maintain performance: ${avgTime}ms per calculation", 
            avgTime < BEARING_CALCULATION_THRESHOLD * 2)
        
        println("Bearing calculation stress test: ${successRate * 100}% success rate, ${avgTime}ms average over $iterations iterations")
    }
    
    @Test
    fun stressTestPathGeneration() {
        val iterations = STRESS_ITERATIONS / 100 // Fewer iterations for path generation
        val random = Random(42)
        val startTime = System.currentTimeMillis()
        
        var successCount = 0
        var errorCount = 0
        var totalPoints = 0
        
        repeat(iterations) {
            try {
                val lat1 = random.nextDouble(-89.0, 89.0) // Avoid exact poles
                val lon1 = random.nextDouble(-179.0, 179.0)
                val lat2 = random.nextDouble(-89.0, 89.0)
                val lon2 = random.nextDouble(-179.0, 179.0)
                val segments = random.nextInt(10, 101) // 10 to 100 segments
                
                val path = GeodesyUtils.calculateGreatCirclePath(lat1, lon1, lat2, lon2, segments)
                
                if (path.isNotEmpty()) {
                    successCount++
                    totalPoints += path.size
                } else {
                    errorCount++
                }
            } catch (e: Exception) {
                errorCount++
            }
        }
        
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        val avgTime = duration.toDouble() / iterations
        val successRate = successCount.toDouble() / iterations
        val avgPointsPerPath = if (successCount > 0) totalPoints.toDouble() / successCount else 0.0
        
        assertTrue("Path generation stress test should have high success rate: ${successRate * 100}%", 
            successRate > 0.95)
        assertTrue("Path generation stress test should maintain performance: ${avgTime}ms per path", 
            avgTime < PATH_GENERATION_THRESHOLD * 3)
        
        println("Path generation stress test: ${successRate * 100}% success rate, ${avgTime}ms average, ${avgPointsPerPath} points per path over $iterations iterations")
    }
    
    @Test
    fun stressTestConcurrentCalculations() {
        val threadCount = 10
        val calculationsPerThread = STRESS_ITERATIONS / threadCount
        val threads = mutableListOf<Thread>()
        val results = mutableListOf<Double>()
        val errors = mutableListOf<Exception>()
        
        val startTime = System.currentTimeMillis()
        
        repeat(threadCount) { threadIndex ->
            val thread = Thread {
                val random = Random(42 + threadIndex)
                repeat(calculationsPerThread) {
                    try {
                        val lat = random.nextDouble(-90.0, 90.0)
                        val lon = random.nextDouble(-180.0, 180.0)
                        val bearing = GeodesyUtils.calculateQiblaBearing(lat, lon)
                        
                        synchronized(results) {
                            results.add(bearing)
                        }
                    } catch (e: Exception) {
                        synchronized(errors) {
                            errors.add(e)
                        }
                    }
                }
            }
            threads.add(thread)
            thread.start()
        }
        
        // Wait for all threads to complete
        threads.forEach { it.join() }
        
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        val totalCalculations = results.size + errors.size
        val avgTime = duration.toDouble() / totalCalculations
        val successRate = results.size.toDouble() / totalCalculations
        
        assertTrue("Concurrent calculations should have high success rate: ${successRate * 100}%", 
            successRate > 0.99)
        assertTrue("Concurrent calculations should maintain reasonable performance: ${avgTime}ms per calculation", 
            avgTime < BEARING_CALCULATION_THRESHOLD * 5) // Allow more time for concurrent execution
        assertTrue("All concurrent calculations should produce valid results", 
            results.all { it >= 0.0 && it <= 360.0 })
        
        println("Concurrent calculation stress test: ${successRate * 100}% success rate, ${avgTime}ms average over $totalCalculations calculations across $threadCount threads")
    }
    
    // --- Edge Case Performance Tests ---
    
    @Test
    fun benchmarkEdgeCases_polarRegions() {
        val polarLocations = listOf(
            Pair(89.9, 0.0),    // Near North Pole
            Pair(-89.9, 0.0),   // Near South Pole
            Pair(89.0, 180.0),  // North Pole, Date Line
            Pair(-89.0, -180.0) // South Pole, Date Line
        )
        
        val iterations = PERFORMANCE_ITERATIONS / polarLocations.size
        val startTime = System.currentTimeMillis()
        
        repeat(iterations) {
            polarLocations.forEach { (lat, lon) ->
                GeodesyUtils.calculateQiblaBearing(lat, lon)
                GeodesyUtils.calculateDistanceToKaaba(lat, lon)
            }
        }
        
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        val totalCalculations = iterations * polarLocations.size * 2 // bearing + distance
        val avgTime = duration.toDouble() / totalCalculations
        
        assertTrue("Polar region calculations should be reasonable: ${avgTime}ms per calculation", 
            avgTime < BEARING_CALCULATION_THRESHOLD * 2)
        
        println("Polar region benchmark: ${avgTime}ms average over $totalCalculations calculations")
    }
    
    @Test
    fun benchmarkEdgeCases_dateLineCrossing() {
        val dateLineLocations = listOf(
            Pair(0.0, 179.0),   // Near date line, east
            Pair(0.0, -179.0),  // Near date line, west
            Pair(45.0, 180.0),  // On date line
            Pair(-45.0, -180.0) // On date line, other side
        )
        
        val iterations = PERFORMANCE_ITERATIONS / dateLineLocations.size
        val startTime = System.currentTimeMillis()
        
        repeat(iterations) {
            dateLineLocations.forEach { (lat, lon) ->
                GeodesyUtils.calculateQiblaBearing(lat, lon)
                // Test path generation across date line
                GeodesyUtils.calculateGreatCirclePath(lat, lon, lat + 1, lon + 1, 20)
            }
        }
        
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        val totalCalculations = iterations * dateLineLocations.size * 2 // bearing + path
        val avgTime = duration.toDouble() / totalCalculations
        
        assertTrue("Date line calculations should be reasonable: ${avgTime}ms per calculation", 
            avgTime < PATH_GENERATION_THRESHOLD)
        
        println("Date line crossing benchmark: ${avgTime}ms average over $totalCalculations calculations")
    }
}