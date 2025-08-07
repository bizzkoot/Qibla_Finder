package com.bizzkoot.qiblafinder.ui.location

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.bizzkoot.qiblafinder.utils.CompressionUtils
import com.bizzkoot.qiblafinder.utils.DeviceCapabilitiesDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URL
import java.net.HttpURLConnection
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withTimeoutOrNull

data class TileCoordinate(
    val x: Int,
    val y: Int,
    val zoom: Int
) {
    fun toFileName(): String = "tile_${zoom}_${x}_${y}.png"
}

class OpenStreetMapTileManager(private val context: Context) {
    
    private val cacheDir = File(context.cacheDir, "map_tiles")
    private val maxCacheSize = 100 * 1024 * 1024 // 100MB max cache
    
    // Performance monitoring
    private var cacheHits = 0
    private var cacheMisses = 0
    private val tileLoadTimes = mutableMapOf<String, Long>()
    
    init {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        Timber.d("📍 OpenStreetMapTileManager - Initialized with cache dir: ${cacheDir.absolutePath}")
    }
    
    /**
     * Convert latitude/longitude to tile coordinates
     */
    fun latLngToTile(lat: Double, lng: Double, zoom: Int): TileCoordinate {
        val n = 2.0.pow(zoom)
        val xtile = floor((lng + 180) / 360 * n)
        val latRad = Math.toRadians(lat)
        val tanLat = Math.tan(latRad)
        val cosLat = Math.cos(latRad)
        val logTerm = Math.log(tanLat + 1.0 / cosLat)
        val ytile = floor((1.0 - logTerm / Math.PI) * n / 2.0)
        return TileCoordinate(xtile.toInt(), ytile.toInt(), zoom)
    }
    
    /**
     * Convert tile coordinates to latitude/longitude bounds
     */
    fun tileToLatLngBounds(tile: TileCoordinate): Pair<Pair<Double, Double>, Pair<Double, Double>> {
        val n = 2.0.pow(tile.zoom)
        val west = tile.x / n * 360.0 - 180.0
        val east = (tile.x + 1) / n * 360.0 - 180.0
        val north = Math.toDegrees(Math.atan(Math.sinh(Math.PI * (1 - 2 * tile.y / n))))
        val south = Math.toDegrees(Math.atan(Math.sinh(Math.PI * (1 - 2 * (tile.y + 1) / n))))
        return Pair(Pair(north, south), Pair(west, east))
    }

    /**
     * Convert latitude/longitude to precise, floating-point tile coordinates
     */
    fun latLngToTileXY(lat: Double, lng: Double, zoom: Int): Pair<Double, Double> {
        val n = 2.0.pow(zoom)
        val xtile = (lng + 180) / 360 * n
        val latRad = Math.toRadians(lat)
        val ytile = (1.0 - Math.log(Math.tan(latRad) + 1 / Math.cos(latRad)) / Math.PI) / 2.0 * n
        return Pair(xtile, ytile)
    }

    /**
     * Convert precise, floating-point tile coordinates to latitude/longitude
     */
    fun tileXYToLatLng(tileX: Double, tileY: Double, zoom: Int): Pair<Double, Double> {
        val n = 2.0.pow(zoom)
        val lngDeg = tileX / n * 360.0 - 180.0
        val latRad = Math.atan(Math.sinh(Math.PI * (1 - 2 * tileY / n)))
        val latDeg = Math.toDegrees(latRad)
        return Pair(latDeg, lngDeg)
    }

    /**
     * Convert meters to pixels at a certain latitude and zoom level.
     */
    fun metersToPixels(meters: Float, zoom: Int): Float {
        val earthCircumference = 40075017.0 // in meters
        val metersPerPixel = earthCircumference * Math.cos(Math.toRadians(0.0)) / (256 * 2.0.pow(zoom))
        return (meters / metersPerPixel).toFloat()
    }
    
    /**
     * Get tile bitmap - from cache if available, otherwise download
     */
    suspend fun getTileBitmap(tile: TileCoordinate): Bitmap? = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            // Check cache first
            val cachedBitmap = getCachedTile(tile)
            if (cachedBitmap != null) {
                cacheHits++
                val loadTime = System.currentTimeMillis() - startTime
                tileLoadTimes[tile.toFileName()] = loadTime
                Timber.d("📍 Tile loaded from cache in ${loadTime}ms: ${tile.toFileName()}")
                return@withContext cachedBitmap
            }
            
            cacheMisses++
            // Download tile
            val downloadedBitmap = downloadTile(tile)
            if (downloadedBitmap != null) {
                // Cache the downloaded tile
                cacheTile(tile, downloadedBitmap)
                val loadTime = System.currentTimeMillis() - startTime
                tileLoadTimes[tile.toFileName()] = loadTime
                Timber.d("📍 Tile downloaded and cached in ${loadTime}ms: ${tile.toFileName()}")
                return@withContext downloadedBitmap
            }
            
            Timber.e("📍 Failed to get tile: ${tile.toFileName()}")
            return@withContext null
        } catch (e: Exception) {
            Timber.e(e, "📍 Error getting tile: ${tile.toFileName()}")
            return@withContext null
        }
    }
    
    /**
     * Get cached tile if available
     */
    private fun getCachedTile(tile: TileCoordinate): Bitmap? {
        val file = File(cacheDir, tile.toFileName())
        return if (file.exists()) {
            try {
                FileInputStream(file).use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            } catch (e: Exception) {
                Timber.e(e, "📍 Error reading cached tile: ${tile.toFileName()}")
                null
            }
        } else null
    }
    
    /**
     * Download tile from OpenStreetMap with retry logic
     */
    private suspend fun downloadTile(tile: TileCoordinate): Bitmap? = withContext(Dispatchers.IO) {
        downloadTileWithAdaptiveTimeout(tile)
    }
    
    /**
     * Download tile with retry logic and exponential backoff
     */
    private suspend fun downloadTileWithRetry(tile: TileCoordinate, maxRetries: Int = 3): Bitmap? {
        for (attempt in 0 until maxRetries) {
            try {
                val url = URL("https://tile.openstreetmap.org/${tile.zoom}/${tile.x}/${tile.y}.png")
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                connection.setRequestProperty("User-Agent", "QiblaFinder/1.0")
                
                if (connection.responseCode == 200) {
                    val inputStream = connection.inputStream
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream.close()
                    connection.disconnect()
                    return bitmap
                } else {
                    Timber.e("📍 HTTP ${connection.responseCode} for tile: ${tile.toFileName()}")
                    connection.disconnect()
                }
            } catch (e: Exception) {
                Timber.e(e, "📍 Error downloading tile (attempt ${attempt + 1}): ${tile.toFileName()}")
                if (attempt == maxRetries - 1) {
                    return null
                }
                // Exponential backoff: 1s, 2s, 4s
                kotlinx.coroutines.delay(1000L * (1 shl attempt))
            }
        }
        return null
    }
    
    /**
     * Get compression quality based on zoom level
     */
    private fun getCompressionQuality(zoom: Int): Int {
        return when {
            zoom >= 18 -> 95  // High detail, high quality
            zoom >= 16 -> 90  // Medium detail, good quality
            zoom >= 14 -> 85  // Lower detail, acceptable quality
            else -> 80         // Low detail, compressed
        }
    }

    /**
     * Cache tile to local storage with fallback support
     */
    private fun cacheTile(tile: TileCoordinate, bitmap: Bitmap) {
        try {
            val file = File(cacheDir, tile.toFileName())
            val deviceCapabilities = DeviceCapabilitiesDetector.getDeviceCapabilities(context)
            val compressionQuality = CompressionUtils.getCompressionQuality(tile.zoom, deviceCapabilities)
            
            FileOutputStream(file).use { stream ->
                val success = CompressionUtils.compressBitmap(bitmap, compressionQuality, stream)
                if (success) {
                    // Manage cache size
                    manageCacheSize()
                    Timber.d("📍 Tile cached successfully: ${tile.toFileName()} with quality: $compressionQuality")
                } else {
                    Timber.e("📍 Failed to cache tile: ${tile.toFileName()}")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "📍 Error caching tile: ${tile.toFileName()}")
        }
    }
    
    /**
     * Manage cache size by removing old tiles if needed
     */
    private fun manageCacheSize() {
        try {
            val cacheFiles = cacheDir.listFiles() ?: return
            val totalSize = cacheFiles.sumOf { it.length() }
            
            if (totalSize > maxCacheSize) {
                // Sort by modification time (oldest first)
                val sortedFiles = cacheFiles.sortedBy { it.lastModified() }
                
                // Remove oldest files until we're under the limit
                var currentSize = totalSize
                for (file in sortedFiles) {
                    if (currentSize <= maxCacheSize) break
                    currentSize -= file.length()
                    file.delete()
                    Timber.d("📍 Removed old cache file: ${file.name}")
                }
            }
            
            // Time-based eviction: remove tiles not accessed in last 30 minutes
            evictOldTiles()
        } catch (e: Exception) {
            Timber.e(e, "📍 Error managing cache size")
        }
    }
    
    /**
     * Remove tiles not accessed in the last 30 minutes
     */
    private fun evictOldTiles() {
        try {
            val cacheFiles = cacheDir.listFiles() ?: return
            val cutoffTime = System.currentTimeMillis() - (30 * 60 * 1000) // 30 minutes
            
            cacheFiles.forEach { file ->
                if (file.lastModified() < cutoffTime) {
                    file.delete()
                    Timber.d("📍 Removed old tile (time-based): ${file.name}")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "📍 Error evicting old tiles")
        }
    }
    
    /**
     * Get tiles needed for a map view with priority (visible first, then buffer)
     */
    fun getTilesForViewWithPriority(
        centerTileX: Double,
        centerTileY: Double,
        zoom: Int,
        mapWidth: Int,
        mapHeight: Int,
        bufferPercentage: Double = 0.4
    ): Pair<List<TileCoordinate>, List<TileCoordinate>> {
        val tileSize = 256
        val numTilesX = ceil(mapWidth.toDouble() / tileSize).toInt() + 2
        val numTilesY = ceil(mapHeight.toDouble() / tileSize).toInt() + 2

        // Calculate buffer tiles
        val bufferTilesX = (numTilesX * bufferPercentage).toInt()
        val bufferTilesY = (numTilesY * bufferPercentage).toInt()

        val startX = floor(centerTileX - (numTilesX + bufferTilesX) / 2.0).toInt()
        val startY = floor(centerTileY - (numTilesY + bufferTilesY) / 2.0).toInt()
        val endX = startX + numTilesX + bufferTilesX
        val endY = startY + numTilesY + bufferTilesY

        // Visible tiles (priority 1)
        val visibleTiles = mutableListOf<TileCoordinate>()
        val visibleStartX = floor(centerTileX - numTilesX / 2.0).toInt()
        val visibleStartY = floor(centerTileY - numTilesY / 2.0).toInt()
        val visibleEndX = visibleStartX + numTilesX
        val visibleEndY = visibleStartY + numTilesY

        for (x in visibleStartX..visibleEndX) {
            for (y in visibleStartY..visibleEndY) {
                visibleTiles.add(TileCoordinate(x, y, zoom))
            }
        }

        // Buffer tiles (priority 2)
        val bufferTiles = mutableListOf<TileCoordinate>()
        for (x in startX..endX) {
            for (y in startY..endY) {
                val tile = TileCoordinate(x, y, zoom)
                if (!visibleTiles.contains(tile)) {
                    bufferTiles.add(tile)
                }
            }
        }
        
        Timber.d("📍 Loading ${visibleTiles.size} visible tiles + ${bufferTiles.size} buffer tiles")
        return Pair(visibleTiles, bufferTiles)
    }

    /**
     * Get lower resolution tile for progressive loading
     */
    fun getLowerResolutionTile(tile: TileCoordinate): TileCoordinate? {
        return if (tile.zoom > 10) {
            val lowerZoom = tile.zoom - 1
            val scale = 2.0.pow(tile.zoom - lowerZoom)
            val lowerX = (tile.x / scale).toInt()
            val lowerY = (tile.y / scale).toInt()
            TileCoordinate(lowerX, lowerY, lowerZoom)
        } else null
    }

    /**
     * Get tiles needed for a map view (original method for backward compatibility)
     */
    fun getTilesForView(
        centerTileX: Double,
        centerTileY: Double,
        zoom: Int,
        mapWidth: Int,
        mapHeight: Int
    ): List<TileCoordinate> {
        val (visibleTiles, bufferTiles) = getTilesForViewWithPriority(centerTileX, centerTileY, zoom, mapWidth, mapHeight, 0.0)
        return visibleTiles + bufferTiles
    }
    
    /**
     * Get tiles needed for a map view with buffer zone (for backward compatibility)
     */
    fun getTilesForViewWithBuffer(
        centerTileX: Double,
        centerTileY: Double,
        zoom: Int,
        mapWidth: Int,
        mapHeight: Int,
        bufferPercentage: Double = 0.4
    ): List<TileCoordinate> {
        val (visibleTiles, bufferTiles) = getTilesForViewWithPriority(centerTileX, centerTileY, zoom, mapWidth, mapHeight, bufferPercentage)
        return visibleTiles + bufferTiles
    }
    
    /**
     * Clear all cached tiles
     */
    fun clearCache() {
        try {
            cacheDir.listFiles()?.forEach { it.delete() }
            Timber.d("📍 Map cache cleared")
        } catch (e: Exception) {
            Timber.e(e, "📍 Error clearing cache")
        }
    }
    
    /**
     * Get cache size in MB
     */
    fun getCacheSizeMB(): Double {
        return try {
            val files = cacheDir.listFiles() ?: return 0.0
            files.sumOf { it.length() } / (1024.0 * 1024.0)
        } catch (e: Exception) {
            Timber.e(e, "📍 Error getting cache size")
            0.0
        }
    }
    
    /**
     * Get cache hit rate
     */
    fun getCacheHitRate(): Double {
        val total = cacheHits + cacheMisses
        return if (total > 0) cacheHits.toDouble() / total else 0.0
    }
    
    /**
     * Get average tile load time in milliseconds
     */
    fun getAverageLoadTime(): Long {
        return if (tileLoadTimes.isNotEmpty()) {
            tileLoadTimes.values.average().toLong()
        } else 0L
    }
    
    /**
     * Get performance statistics
     */
    fun getPerformanceStats(): String {
        val hitRate = getCacheHitRate() * 100
        val avgLoadTime = getAverageLoadTime()
        val cacheSize = getCacheSizeMB()
        
        return "Cache: ${String.format("%.1f", cacheSize)}MB, " +
               "Hit Rate: ${String.format("%.1f", hitRate)}%, " +
               "Avg Load: ${avgLoadTime}ms"
    }

    /**
     * Get buffer size based on network connection type
     */
    fun getBufferSizeBasedOnConnection(): Double {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            
            when {
                capabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true -> 0.5  // 50% buffer on WiFi
                capabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) == true -> {
                    when {
                        capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_CONGESTED) -> 0.3  // 30% buffer on 4G
                        else -> 0.2  // 20% buffer on 3G/slow
                    }
                }
                else -> 0.1  // 10% buffer on unknown/slow connection
            }
        } catch (e: Exception) {
            Timber.e(e, "📍 Error detecting network type, using default buffer")
            0.4  // Default 40% buffer
        }
    }

    /**
     * Load tile progressively (low-res first, then high-res)
     */
    suspend fun loadTileProgressive(tile: TileCoordinate): Bitmap? {
        // Try to get the tile directly first
        val directTile = getTileBitmap(tile)
        if (directTile != null) {
            return directTile
        }
        
        // If not available, try lower resolution first
        val lowerResTile = getLowerResolutionTile(tile)
        if (lowerResTile != null) {
            val lowerResBitmap = getTileBitmap(lowerResTile)
            if (lowerResBitmap != null) {
                // Return the lower resolution tile immediately
                // The high-res tile will be loaded in background by the caller
                return lowerResBitmap
            }
        }
        
        // Fallback to direct loading
        return getTileBitmap(tile)
    }

    /**
     * Batch download multiple tiles simultaneously
     */
    suspend fun batchDownloadTiles(tiles: List<TileCoordinate>, batchSize: Int = 5): Map<String, Bitmap> {
        return withContext(Dispatchers.IO) {
            val results = mutableMapOf<String, Bitmap>()
            
            // Split tiles into batches
            val batches = tiles.chunked(batchSize)
            
            for (batch in batches) {
                val batchResults = batch.map { tile ->
                    async {
                        val bitmap = getTileBitmap(tile)
                        tile.toFileName() to bitmap
                    }
                }.awaitAll()
                
                // Add successful downloads to results
                for ((fileName, bitmap) in batchResults) {
                    if (bitmap != null) {
                        results[fileName] = bitmap
                    }
                }
            }
            
            Timber.d("📍 Batch downloaded ${results.size}/${tiles.size} tiles")
            results
        }
    }

    /**
     * Handle memory pressure by reducing cache size
     */
    fun handleMemoryPressure() {
        try {
            val cacheFiles = cacheDir.listFiles() ?: return
            val totalSize = cacheFiles.sumOf { it.length() }
            
            // Reduce cache to 50% when memory pressure is detected
            val targetSize = maxCacheSize / 2
            
            if (totalSize > targetSize) {
                // Sort by modification time (oldest first)
                val sortedFiles = cacheFiles.sortedBy { it.lastModified() }
                
                // Remove oldest files until we're under the target
                var currentSize = totalSize
                for (file in sortedFiles) {
                    if (currentSize <= targetSize) break
                    currentSize -= file.length()
                    file.delete()
                    Timber.d("📍 Removed file due to memory pressure: ${file.name}")
                }
                
                // Force garbage collection
                System.gc()
                Timber.d("📍 Memory pressure handled - cache reduced to ${String.format("%.1f", currentSize / (1024.0 * 1024.0))}MB")
            }
        } catch (e: Exception) {
            Timber.e(e, "📍 Error handling memory pressure")
        }
    }
    
    /**
     * Check if memory pressure should be handled
     */
    fun shouldHandleMemoryPressure(): Boolean {
        return try {
            val runtime = Runtime.getRuntime()
            val usedMemory = runtime.totalMemory() - runtime.freeMemory()
            val maxMemory = runtime.maxMemory()
            val memoryUsage = usedMemory.toDouble() / maxMemory
            
            // Trigger if memory usage is above 80%
            memoryUsage > 0.8
        } catch (e: Exception) {
            Timber.e(e, "📍 Error checking memory pressure")
            false
        }
    }

    /**
     * Get adaptive timeout based on connection type
     */
    private fun getAdaptiveTimeout(): Int {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            
            when {
                capabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true -> 5000  // 5s on WiFi
                capabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) == true -> {
                    when {
                        capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_CONGESTED) -> 8000  // 8s on 4G
                        else -> 12000  // 12s on 3G/slow
                    }
                }
                else -> 15000  // 15s on unknown/slow connection
            }
        } catch (e: Exception) {
            Timber.e(e, "📍 Error detecting network type, using default timeout")
            10000  // Default 10s timeout
        }
    }
    
    /**
     * Download tile with adaptive timeout
     */
    private suspend fun downloadTileWithAdaptiveTimeout(tile: TileCoordinate): Bitmap? {
        val timeout = getAdaptiveTimeout()
        
        return withTimeoutOrNull(timeout.toLong()) {
            downloadTileWithRetry(tile, maxRetries = 3)
        }
    }
} 