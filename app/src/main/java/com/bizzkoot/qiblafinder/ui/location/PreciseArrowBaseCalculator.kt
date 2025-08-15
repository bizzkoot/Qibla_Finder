package com.bizzkoot.qiblafinder.ui.location

import androidx.compose.ui.geometry.Offset
import kotlin.math.*

/**
 * Calculates precise arrow base positions using high-precision coordinate arithmetic.
 * Ensures perfect alignment between arrow base and drop point through unified 
 * mathematical pipeline.
 */
class PreciseArrowBaseCalculator {
    
    companion object {
        // Constants for sub-pixel precision calculations
        private const val PRECISION_FACTOR = 1000000.0 // Micro-pixel precision
        private const val MIN_ARROW_LENGTH_PIXELS = 50.0 // Minimum arrow length for visibility
        private const val MAX_ARROW_LENGTH_PIXELS = 200.0 // Maximum arrow length to prevent clipping
    }
    
    /**
     * Calculates the precise arrow base position with sub-pixel accuracy.
     * Uses the same coordinate transformation pipeline as the drop point to ensure
     * perfect alignment.
     * 
     * @param userLocation The user's current location as a PreciseMapCoordinate
     * @param qiblaLocation The Qibla location as a PreciseMapCoordinate  
     * @param canvasWidth The canvas width in pixels
     * @param canvasHeight The canvas height in pixels
     * @param viewportTileX The viewport's tile X coordinate
     * @param viewportTileY The viewport's tile Y coordinate
     * @param arrowLength The desired arrow length in pixels
     * @return Pair of (arrow base position, arrow tip position) with sub-pixel precision
     */
    fun calculatePreciseArrowBase(
        userLocation: PreciseMapCoordinate,
        qiblaLocation: PreciseMapCoordinate,
        canvasWidth: Float,
        canvasHeight: Float,
        viewportTileX: Double,
        viewportTileY: Double,
        arrowLength: Double = MIN_ARROW_LENGTH_PIXELS
    ): Pair<Offset, Offset> {
        
        // Calculate the exact screen position of the user location (drop point)
        // This must use identical calculation as the drop point rendering
        val dropPointPosition = userLocation.toExactScreenPosition(
            canvasWidth, canvasHeight, viewportTileX, viewportTileY
        )
        
        // Calculate the bearing from user to Qibla with high precision
        val bearing = PrecisionCoordinateTransformer.calculateBearing(
            userLocation.latitude, userLocation.longitude,
            qiblaLocation.latitude, qiblaLocation.longitude
        )
        
        // Convert bearing to radians for trigonometric calculations
        val bearingRad = Math.toRadians(bearing)
        
        // Validate and constrain arrow length
        val constrainedArrowLength = arrowLength.coerceIn(
            MIN_ARROW_LENGTH_PIXELS, MAX_ARROW_LENGTH_PIXELS
        )
        
        // Calculate arrow tip position with sub-pixel precision
        val arrowTipX = dropPointPosition.x + (constrainedArrowLength * sin(bearingRad))
        val arrowTipY = dropPointPosition.y - (constrainedArrowLength * cos(bearingRad))
        
        val arrowTipPosition = Offset(arrowTipX.toFloat(), arrowTipY.toFloat())
        
        return Pair(dropPointPosition, arrowTipPosition)
    }
    
    /**
     * Calculates viewport-relative position with double precision arithmetic.
     * This method maintains coordinate precision throughout the transformation
     * to prevent cumulative floating-point errors.
     * 
     * @param coordinate The precise map coordinate to transform
     * @param canvasWidth The canvas width in pixels
     * @param canvasHeight The canvas height in pixels
     * @param viewportTileX The viewport's precise tile X coordinate
     * @param viewportTileY The viewport's precise tile Y coordinate
     * @return The viewport-relative position with sub-pixel precision
     */
    fun calculateViewportRelativePosition(
        coordinate: PreciseMapCoordinate,
        canvasWidth: Float,
        canvasHeight: Float,
        viewportTileX: Double,
        viewportTileY: Double
    ): Offset {
        
        // Use high-precision arithmetic for viewport transformation
        val tileSize = 256.0 // Double precision tile size
        
        // Calculate relative tile position with double precision
        val relativeTileX = coordinate.tileX - viewportTileX
        val relativeTileY = coordinate.tileY - viewportTileY
        
        // Transform to screen coordinates with digital zoom factor
        val screenX = relativeTileX * tileSize * coordinate.digitalZoom + canvasWidth / 2.0
        val screenY = relativeTileY * tileSize * coordinate.digitalZoom + canvasHeight / 2.0
        
        return Offset(screenX.toFloat(), screenY.toFloat())
    }
    
    /**
     * Implements exact screen coordinate transformation using the same mathematical
     * pipeline as PreciseMapCoordinate.toExactScreenPosition() to ensure consistency.
     * 
     * @param tileX The precise tile X coordinate
     * @param tileY The precise tile Y coordinate
     * @param digitalZoom The digital zoom factor
     * @param canvasWidth The canvas width in pixels
     * @param canvasHeight The canvas height in pixels
     * @param viewportTileX The viewport's tile X coordinate
     * @param viewportTileY The viewport's tile Y coordinate
     * @return The exact screen coordinate with sub-pixel precision
     */
    fun transformToExactScreenCoordinate(
        tileX: Double,
        tileY: Double,
        digitalZoom: Double,
        canvasWidth: Float,
        canvasHeight: Float,
        viewportTileX: Double,
        viewportTileY: Double
    ): Offset {
        
        val tileSize = 256.0 // Must match PreciseMapCoordinate.TILE_SIZE
        
        // Use identical transformation as PreciseMapCoordinate.toExactScreenPosition
        val preciseX = (tileX - viewportTileX) * tileSize * digitalZoom + canvasWidth / 2.0
        val preciseY = (tileY - viewportTileY) * tileSize * digitalZoom + canvasHeight / 2.0
        
        return Offset(preciseX.toFloat(), preciseY.toFloat())
    }
    
    /**
     * Calculates the optimal arrow length based on zoom level and screen space.
     * Ensures the arrow is visible but doesn't dominate the view.
     * 
     * @param zoomLevel The current map zoom level
     * @param digitalZoom The current digital zoom factor
     * @param canvasWidth The canvas width in pixels
     * @param canvasHeight The canvas height in pixels
     * @return The optimal arrow length in pixels
     */
    fun calculateOptimalArrowLength(
        zoomLevel: Int,
        digitalZoom: Double,
        canvasWidth: Float,
        canvasHeight: Float
    ): Double {
        
        // Base arrow length scales with zoom level
        val baseLength = MIN_ARROW_LENGTH_PIXELS + (zoomLevel * 5.0)
        
        // Adjust for digital zoom - larger at higher zoom levels
        val zoomAdjustedLength = baseLength * sqrt(digitalZoom)
        
        // Constrain based on screen space - don't let arrow become too large
        val screenDiagonal = sqrt(canvasWidth * canvasWidth + canvasHeight * canvasHeight)
        val maxScreenLength = screenDiagonal * 0.2 // Maximum 20% of screen diagonal
        
        return zoomAdjustedLength.coerceIn(MIN_ARROW_LENGTH_PIXELS, 
                                         minOf(MAX_ARROW_LENGTH_PIXELS, maxScreenLength))
    }
    
    /**
     * Validates that the calculated arrow base position is within screen bounds
     * and has acceptable precision.
     * 
     * @param arrowBase The calculated arrow base position
     * @param arrowTip The calculated arrow tip position
     * @param canvasWidth The canvas width in pixels
     * @param canvasHeight The canvas height in pixels
     * @return True if the arrow positions are valid, false otherwise
     */
    fun validateArrowPosition(
        arrowBase: Offset,
        arrowTip: Offset,
        canvasWidth: Float,
        canvasHeight: Float
    ): Boolean {
        
        // Check that positions are finite (not NaN or Infinite)
        if (!arrowBase.x.isFinite() || !arrowBase.y.isFinite()) return false
        if (!arrowTip.x.isFinite() || !arrowTip.y.isFinite()) return false
        
        // Arrow base should be visible on screen (with reasonable margin)
        val margin = 50f
        val baseVisible = arrowBase.x >= -margin && arrowBase.x <= canvasWidth + margin &&
                         arrowBase.y >= -margin && arrowBase.y <= canvasHeight + margin
        
        // Calculate arrow length for validation
        val dx = arrowTip.x - arrowBase.x
        val dy = arrowTip.y - arrowBase.y
        val arrowLength = sqrt(dx * dx + dy * dy)
        
        // Arrow should have reasonable length
        val lengthValid = arrowLength >= MIN_ARROW_LENGTH_PIXELS && 
                         arrowLength <= MAX_ARROW_LENGTH_PIXELS * 2 // Allow some extra for edge cases
        
        return baseVisible && lengthValid
    }
    
    /**
     * Rounds coordinates to prevent excessive precision that could cause
     * floating-point precision issues in rendering.
     * 
     * @param position The position to round
     * @param decimalPlaces The number of decimal places to preserve
     * @return The rounded position
     */
    fun roundToPrecision(position: Offset, decimalPlaces: Int = 3): Offset {
        val factor = 10.0.pow(decimalPlaces.toDouble()).toFloat()
        val roundedX = (position.x * factor).roundToInt() / factor
        val roundedY = (position.y * factor).roundToInt() / factor
        return Offset(roundedX, roundedY)
    }
}