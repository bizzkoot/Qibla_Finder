package com.bizzkoot.qiblafinder.ui.location

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
        try {
            // Check cache first
            val cachedBitmap = getCachedTile(tile)
            if (cachedBitmap != null) {
                Timber.d("📍 Tile loaded from cache: ${tile.toFileName()}")
                return@withContext cachedBitmap
            }
            
            // Download tile
            val downloadedBitmap = downloadTile(tile)
            if (downloadedBitmap != null) {
                // Cache the downloaded tile
                cacheTile(tile, downloadedBitmap)
                Timber.d("📍 Tile downloaded and cached: ${tile.toFileName()}")
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
     * Download tile from OpenStreetMap
     */
    private suspend fun downloadTile(tile: TileCoordinate): Bitmap? = withContext(Dispatchers.IO) {
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
                return@withContext bitmap
            } else {
                Timber.e("📍 HTTP ${connection.responseCode} for tile: ${tile.toFileName()}")
                connection.disconnect()
                return@withContext null
            }
        } catch (e: Exception) {
            Timber.e(e, "📍 Error downloading tile: ${tile.toFileName()}")
            return@withContext null
        }
    }
    
    /**
     * Cache tile to local storage
     */
    private fun cacheTile(tile: TileCoordinate, bitmap: Bitmap) {
        try {
            val file = File(cacheDir, tile.toFileName())
            FileOutputStream(file).use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
            
            // Manage cache size
            manageCacheSize()
            
            Timber.d("📍 Tile cached: ${tile.toFileName()}")
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
        } catch (e: Exception) {
            Timber.e(e, "📍 Error managing cache size")
        }
    }
    
    /**
     * Get tiles needed for a map view
     */
    fun getTilesForView(
        centerTileX: Double,
        centerTileY: Double,
        zoom: Int,
        mapWidth: Int,
        mapHeight: Int
    ): List<TileCoordinate> {
        val tiles = mutableListOf<TileCoordinate>()
        val tileSize = 256
        val numTilesX = ceil(mapWidth.toDouble() / tileSize).toInt() + 2
        val numTilesY = ceil(mapHeight.toDouble() / tileSize).toInt() + 2

        val startX = floor(centerTileX - numTilesX / 2.0).toInt()
        val startY = floor(centerTileY - numTilesY / 2.0).toInt()
        val endX = startX + numTilesX
        val endY = startY + numTilesY

        for (x in startX..endX) {
            for (y in startY..endY) {
                tiles.add(TileCoordinate(x, y, zoom))
            }
        }
        return tiles
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
} 