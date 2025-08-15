package com.bizzkoot.qiblafinder.ui.location

import androidx.compose.ui.geometry.Size
import kotlin.math.pow

/**
 * High-precision viewport state for tracking exact map position and zoom
 * Maintains double precision throughout viewport calculations
 */
data class PreciseViewportState(
    val centerTileX: Double,
    val centerTileY: Double,
    val zoomLevel: Int,
    val digitalZoom: Double,
    val canvasSize: Size,
    val visibleTileRange: PreciseTileRange? = null
) {
    companion object {
        private const val TILE_SIZE = 256.0
        
        /**
         * Creates viewport state from geographic center and zoom parameters
         */
        fun fromCenter(
            centerLatitude: Double,
            centerLongitude: Double,
            zoomLevel: Int,
            digitalZoom: Double,
            canvasSize: Size
        ): PreciseViewportState {
            val (centerTileX, centerTileY) = PrecisionCoordinateTransformer
                .latLngToHighPrecisionTile(centerLatitude, centerLongitude, zoomLevel)
            
            return PreciseViewportState(
                centerTileX = centerTileX,
                centerTileY = centerTileY,
                zoomLevel = zoomLevel,
                digitalZoom = digitalZoom,
                canvasSize = canvasSize
            ).withCalculatedTileRange()
        }
        
        /**
         * Creates viewport state from tile coordinates
         */
        fun fromTileCoordinates(
            centerTileX: Double,
            centerTileY: Double,
            zoomLevel: Int,
            digitalZoom: Double,
            canvasSize: Size
        ): PreciseViewportState {
            return PreciseViewportState(
                centerTileX = centerTileX,
                centerTileY = centerTileY,
                zoomLevel = zoomLevel,
                digitalZoom = digitalZoom,
                canvasSize = canvasSize
            ).withCalculatedTileRange()
        }
    }
    
    /**
     * Calculates the visible tile range for efficient rendering
     */
    fun withCalculatedTileRange(): PreciseViewportState {
        val tilesHorizontal = canvasSize.width / (TILE_SIZE * digitalZoom)
        val tilesVertical = canvasSize.height / (TILE_SIZE * digitalZoom)
        
        val minTileX = centerTileX - tilesHorizontal / 2.0
        val maxTileX = centerTileX + tilesHorizontal / 2.0
        val minTileY = centerTileY - tilesVertical / 2.0
        val maxTileY = centerTileY + tilesVertical / 2.0
        
        val range = PreciseTileRange(
            minTileX = minTileX,
            maxTileX = maxTileX,
            minTileY = minTileY,
            maxTileY = maxTileY
        )
        
        return copy(visibleTileRange = range)
    }
    
    /**
     * Converts screen coordinates to high-precision tile coordinates
     */
    fun screenToTileCoordinate(screenX: Float, screenY: Float): Pair<Double, Double> {
        val centerScreenX = canvasSize.width / 2.0
        val centerScreenY = canvasSize.height / 2.0
        
        val deltaScreenX = screenX - centerScreenX
        val deltaScreenY = screenY - centerScreenY
        
        val deltaTileX = deltaScreenX / (TILE_SIZE * digitalZoom)
        val deltaTileY = deltaScreenY / (TILE_SIZE * digitalZoom)
        
        val tileX = centerTileX + deltaTileX
        val tileY = centerTileY + deltaTileY
        
        return Pair(tileX, tileY)
    }
    
    /**
     * Converts high-precision tile coordinates to screen coordinates
     */
    fun tileToScreenCoordinate(tileX: Double, tileY: Double): Pair<Float, Float> {
        val deltaTileX = tileX - centerTileX
        val deltaTileY = tileY - centerTileY
        
        val deltaScreenX = deltaTileX * TILE_SIZE * digitalZoom
        val deltaScreenY = deltaTileY * TILE_SIZE * digitalZoom
        
        val screenX = canvasSize.width / 2.0 + deltaScreenX
        val screenY = canvasSize.height / 2.0 + deltaScreenY
        
        return Pair(screenX.toFloat(), screenY.toFloat())
    }
    
    /**
     * Updates viewport with new center coordinates while maintaining precision
     */
    fun withNewCenter(newCenterTileX: Double, newCenterTileY: Double): PreciseViewportState {
        return copy(
            centerTileX = newCenterTileX,
            centerTileY = newCenterTileY
        ).withCalculatedTileRange()
    }
    
    /**
     * Updates viewport with new digital zoom while maintaining center precision
     */
    fun withNewDigitalZoom(newDigitalZoom: Double): PreciseViewportState {
        return copy(digitalZoom = newDigitalZoom).withCalculatedTileRange()
    }
    
    /**
     * Updates viewport with new canvas size
     */
    fun withNewCanvasSize(newCanvasSize: Size): PreciseViewportState {
        return copy(canvasSize = newCanvasSize).withCalculatedTileRange()
    }
    
    /**
     * Returns the geographic center coordinates
     */
    fun getCenterLatLng(): Pair<Double, Double> {
        return PrecisionCoordinateTransformer.highPrecisionTileToLatLng(
            centerTileX, centerTileY, zoomLevel
        )
    }
    
    /**
     * Calculates viewport bounds in geographic coordinates
     */
    fun getGeographicBounds(): GeographicBounds? {
        visibleTileRange?.let { range ->
            val (minLat, minLng) = PrecisionCoordinateTransformer
                .highPrecisionTileToLatLng(range.minTileX, range.maxTileY, zoomLevel)
            val (maxLat, maxLng) = PrecisionCoordinateTransformer
                .highPrecisionTileToLatLng(range.maxTileX, range.minTileY, zoomLevel)
            
            return GeographicBounds(
                minLatitude = minLat,
                maxLatitude = maxLat,
                minLongitude = minLng,
                maxLongitude = maxLng
            )
        }
        return null
    }
    
    /**
     * Validates that the viewport state is within reasonable bounds
     */
    fun isValid(): Boolean {
        val maxTileCoord = 2.0.pow(zoomLevel.toDouble())
        val validTileX = centerTileX >= 0.0 && centerTileX <= maxTileCoord.toDouble()
        val validTileY = centerTileY >= 0.0 && centerTileY <= maxTileCoord.toDouble()
        val validDigitalZoom = digitalZoom >= 0.1 && digitalZoom <= 10.0
        val validZoomLevel = zoomLevel >= 0 && zoomLevel <= 20
        
        return validTileX && validTileY && validDigitalZoom && validZoomLevel
    }
}

/**
 * Represents a precise tile range for viewport calculations
 */
data class PreciseTileRange(
    val minTileX: Double,
    val maxTileX: Double,
    val minTileY: Double,
    val maxTileY: Double
) {
    /**
     * Checks if a tile coordinate is within this range
     */
    fun contains(tileX: Double, tileY: Double): Boolean {
        return tileX >= minTileX && tileX <= maxTileX && 
               tileY >= minTileY && tileY <= maxTileY
    }
    
    /**
     * Expands the range by the specified margin
     */
    fun expandBy(margin: Double): PreciseTileRange {
        return PreciseTileRange(
            minTileX = minTileX - margin,
            maxTileX = maxTileX + margin,
            minTileY = minTileY - margin,
            maxTileY = maxTileY + margin
        )
    }
}

/**
 * Represents geographic bounds in latitude/longitude
 */
data class GeographicBounds(
    val minLatitude: Double,
    val maxLatitude: Double,
    val minLongitude: Double,
    val maxLongitude: Double
) {
    /**
     * Checks if a geographic coordinate is within these bounds
     */
    fun contains(latitude: Double, longitude: Double): Boolean {
        return latitude >= minLatitude && latitude <= maxLatitude &&
               longitude >= minLongitude && longitude <= maxLongitude
    }
}