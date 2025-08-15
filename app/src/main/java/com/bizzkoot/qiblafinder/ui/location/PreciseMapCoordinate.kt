package com.bizzkoot.qiblafinder.ui.location

import androidx.compose.ui.geometry.Offset
import kotlin.math.pow

/**
 * High-precision coordinate container that maintains accuracy
 * through the entire transformation pipeline
 */
data class PreciseMapCoordinate(
    val latitude: Double,
    val longitude: Double,
    val tileX: Double,      // High-precision tile coordinates
    val tileY: Double,
    val pixelX: Double,     // Sub-pixel screen coordinates  
    val pixelY: Double,
    val zoomLevel: Int,
    val digitalZoom: Double // Double precision for zoom factor
) {
    companion object {
        private const val TILE_SIZE = 256.0 // Double precision tile size
        
        /**
         * Creates a PreciseMapCoordinate from geographic coordinates
         */
        fun fromLatLng(
            latitude: Double, 
            longitude: Double, 
            zoomLevel: Int, 
            digitalZoom: Double = 1.0
        ): PreciseMapCoordinate {
            val (tileX, tileY) = PrecisionCoordinateTransformer.latLngToHighPrecisionTile(
                latitude, longitude, zoomLevel
            )
            
            return PreciseMapCoordinate(
                latitude = latitude,
                longitude = longitude,
                tileX = tileX,
                tileY = tileY,
                pixelX = tileX * TILE_SIZE,
                pixelY = tileY * TILE_SIZE,
                zoomLevel = zoomLevel,
                digitalZoom = digitalZoom
            )
        }
    }
    
    /**
     * Calculates exact screen position with sub-pixel accuracy
     */
    fun toExactScreenPosition(
        canvasWidth: Float, 
        canvasHeight: Float, 
        viewportTileX: Double, 
        viewportTileY: Double
    ): Offset {
        val preciseX = (tileX - viewportTileX) * TILE_SIZE * digitalZoom + canvasWidth / 2.0
        val preciseY = (tileY - viewportTileY) * TILE_SIZE * digitalZoom + canvasHeight / 2.0
        return Offset(preciseX.toFloat(), preciseY.toFloat())
    }
    
    /**
     * Returns a new coordinate with updated digital zoom factor
     */
    fun withDigitalZoom(newDigitalZoom: Double): PreciseMapCoordinate {
        return copy(digitalZoom = newDigitalZoom)
    }
    
    /**
     * Calculates the distance to another coordinate in tile units
     */
    fun distanceTo(other: PreciseMapCoordinate): Double {
        val deltaX = tileX - other.tileX
        val deltaY = tileY - other.tileY
        return kotlin.math.sqrt(deltaX * deltaX + deltaY * deltaY)
    }
    
    /**
     * Returns the geographic coordinate as a pair
     */
    fun toLatLng(): Pair<Double, Double> = Pair(latitude, longitude)
    
    /**
     * Returns the tile coordinate as a pair
     */
    fun toTileCoordinate(): Pair<Double, Double> = Pair(tileX, tileY)
    
    /**
     * Validates coordinate precision and returns true if within acceptable bounds
     */
    fun isValidPrecision(): Boolean {
        // Check that coordinates are within valid geographic bounds
        val validLatitude = latitude >= -90.0 && latitude <= 90.0
        val validLongitude = longitude >= -180.0 && longitude <= 180.0
        
        // Check that tile coordinates are reasonable for zoom level
        val maxTileCoord = 2.0.pow(zoomLevel.toDouble())
        val validTileX = tileX >= 0.0 && tileX <= maxTileCoord
        val validTileY = tileY >= 0.0 && tileY <= maxTileCoord
        
        // Check that digital zoom is within reasonable bounds
        val validDigitalZoom = digitalZoom >= 0.1 && digitalZoom <= 10.0
        
        return validLatitude && validLongitude && validTileX && validTileY && validDigitalZoom
    }
}