package com.bizzkoot.qiblafinder.ui.location

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.sqrt

/**
 * Synchronized renderer that ensures drop point and arrow base use identical 
 * position calculations to achieve perfect alignment.
 */
class SynchronizedLocationRenderer {
    
    companion object {
        // Drop point rendering constants
        private const val DROP_POINT_RADIUS = 15f
        private const val DROP_POINT_CENTER_RADIUS = 3f
        private val DROP_POINT_COLOR = Color.Red
        private val DROP_POINT_CENTER_COLOR = Color.White
        
        // Accuracy circle rendering constants
        private const val ACCURACY_CIRCLE_ALPHA = 0.1f
        private const val ACCURACY_CIRCLE_STROKE_WIDTH = 2f
        
        // Position validation constants
        private const val POSITION_TOLERANCE_PIXELS = 0.5f // Sub-pixel tolerance for alignment
    }
    
    private val arrowBaseCalculator = PreciseArrowBaseCalculator()
    
    /**
     * Renders the drop point using high-precision coordinate calculations.
     * This method must be used instead of the original drop point rendering
     * to ensure perfect alignment with the arrow base.
     * 
     * @param drawScope The Canvas DrawScope for rendering
     * @param userLocation The user location as a PreciseMapCoordinate
     * @param canvasWidth The canvas width in pixels
     * @param canvasHeight The canvas height in pixels
     * @param viewportTileX The viewport's precise tile X coordinate
     * @param viewportTileY The viewport's precise tile Y coordinate
     * @param accuracyInPixels The accuracy circle radius in pixels
     * @param tileManager The tile manager for coordinate conversions (legacy support)
     * @param zoom The current zoom level
     * @param digitalZoom The current digital zoom factor
     */
    fun renderSynchronizedDropPoint(
        drawScope: DrawScope,
        userLocation: PreciseMapCoordinate,
        canvasWidth: Float,
        canvasHeight: Float,
        viewportTileX: Double,
        viewportTileY: Double,
        accuracyInPixels: Float,
        tileManager: OpenStreetMapTileManager? = null,
        zoom: Int = 18,
        digitalZoom: Float = 1f
    ) = with(drawScope) {
        
        // Calculate the precise drop point position using the same method as arrow base
        val dropPointPosition = calculateSynchronizedDropPointPosition(
            userLocation, canvasWidth, canvasHeight, viewportTileX, viewportTileY
        )
        
        // Validate the calculated position
        if (!isValidScreenPosition(dropPointPosition, canvasWidth, canvasHeight)) {
            // Fall back to center screen if calculation fails
            val centerPosition = Offset(canvasWidth / 2f, canvasHeight / 2f)
            renderDropPointAtPosition(centerPosition, accuracyInPixels)
            return@with
        }
        
        // Render the drop point at the calculated position
        renderDropPointAtPosition(dropPointPosition, accuracyInPixels)
    }
    
    /**
     * Calculates the synchronized drop point position using identical mathematical
     * pipeline as the arrow base calculation.
     * 
     * @param userLocation The user location as a PreciseMapCoordinate
     * @param canvasWidth The canvas width in pixels
     * @param canvasHeight The canvas height in pixels
     * @param viewportTileX The viewport's tile X coordinate
     * @param viewportTileY The viewport's tile Y coordinate
     * @return The exact screen position for the drop point
     */
    fun calculateSynchronizedDropPointPosition(
        userLocation: PreciseMapCoordinate,
        canvasWidth: Float,
        canvasHeight: Float,
        viewportTileX: Double,
        viewportTileY: Double
    ): Offset {
        
        // Use the same transformation as PreciseMapCoordinate.toExactScreenPosition()
        // This ensures perfect synchronization with arrow base calculations
        return userLocation.toExactScreenPosition(
            canvasWidth, canvasHeight, viewportTileX, viewportTileY
        )
    }
    
    /**
     * Calculates both the drop point and arrow base positions using the unified
     * mathematical pipeline to guarantee perfect alignment.
     * 
     * @param userLocation The user location as a PreciseMapCoordinate
     * @param qiblaLocation The Qibla location as a PreciseMapCoordinate
     * @param canvasWidth The canvas width in pixels
     * @param canvasHeight The canvas height in pixels
     * @param viewportTileX The viewport's tile X coordinate
     * @param viewportTileY The viewport's tile Y coordinate
     * @param arrowLength The desired arrow length in pixels
     * @return Triple of (drop point position, arrow base position, arrow tip position)
     */
    fun calculateSynchronizedPositions(
        userLocation: PreciseMapCoordinate,
        qiblaLocation: PreciseMapCoordinate,
        canvasWidth: Float,
        canvasHeight: Float,
        viewportTileX: Double,
        viewportTileY: Double,
        arrowLength: Double = 50.0
    ): Triple<Offset, Offset, Offset> {
        
        // Calculate drop point position
        val dropPointPosition = calculateSynchronizedDropPointPosition(
            userLocation, canvasWidth, canvasHeight, viewportTileX, viewportTileY
        )
        
        // Calculate arrow positions using the same base coordinate
        val (arrowBase, arrowTip) = arrowBaseCalculator.calculatePreciseArrowBase(
            userLocation, qiblaLocation, canvasWidth, canvasHeight,
            viewportTileX, viewportTileY, arrowLength
        )
        
        return Triple(dropPointPosition, arrowBase, arrowTip)
    }
    
    /**
     * Renders the drop point at the specified position.
     * 
     * @param position The position to render the drop point
     * @param accuracyInPixels The accuracy circle radius in pixels
     */
    private fun DrawScope.renderDropPointAtPosition(
        position: Offset,
        accuracyInPixels: Float
    ) {
        // Draw accuracy circle (background)
        drawCircle(
            color = DROP_POINT_COLOR.copy(alpha = ACCURACY_CIRCLE_ALPHA),
            radius = accuracyInPixels,
            center = position
        )
        
        // Draw accuracy circle (stroke)
        drawCircle(
            color = DROP_POINT_COLOR,
            radius = accuracyInPixels,
            center = position,
            style = Stroke(width = ACCURACY_CIRCLE_STROKE_WIDTH)
        )
        
        // Draw main drop point
        drawCircle(
            color = DROP_POINT_COLOR,
            radius = DROP_POINT_RADIUS,
            center = position
        )
        
        // Draw center point
        drawCircle(
            color = DROP_POINT_CENTER_COLOR,
            radius = DROP_POINT_CENTER_RADIUS,
            center = position
        )
    }
    
    /**
     * Validates that the calculated screen position is within reasonable bounds.
     * 
     * @param position The position to validate
     * @param canvasWidth The canvas width
     * @param canvasHeight The canvas height
     * @return True if the position is valid, false otherwise
     */
    private fun isValidScreenPosition(
        position: Offset,
        canvasWidth: Float,
        canvasHeight: Float
    ): Boolean {
        // Check for NaN or infinite values
        if (!position.x.isFinite() || !position.y.isFinite()) {
            return false
        }
        
        // Allow some margin outside visible area for smooth panning
        val margin = 100f
        return position.x >= -margin && position.x <= canvasWidth + margin &&
               position.y >= -margin && position.y <= canvasHeight + margin
    }
    
    /**
     * Validates perfect alignment between drop point and arrow base positions.
     * 
     * @param dropPointPosition The calculated drop point position
     * @param arrowBasePosition The calculated arrow base position
     * @return True if positions are aligned within tolerance, false otherwise
     */
    fun validatePerfectAlignment(
        dropPointPosition: Offset,
        arrowBasePosition: Offset
    ): Boolean {
        val deltaX = kotlin.math.abs(dropPointPosition.x - arrowBasePosition.x)
        val deltaY = kotlin.math.abs(dropPointPosition.y - arrowBasePosition.y)
        val distance = sqrt(deltaX * deltaX + deltaY * deltaY)
        
        return distance <= POSITION_TOLERANCE_PIXELS
    }
    
    /**
     * Creates a PreciseMapCoordinate from legacy MapLocation for backward compatibility.
     * 
     * @param location The legacy MapLocation
     * @param zoomLevel The current zoom level
     * @param digitalZoom The current digital zoom factor
     * @return The precise map coordinate
     */
    fun createPreciseCoordinate(
        location: MapLocation,
        zoomLevel: Int,
        digitalZoom: Double = 1.0
    ): PreciseMapCoordinate {
        return PreciseMapCoordinate.fromLatLng(
            location.latitude,
            location.longitude,
            zoomLevel,
            digitalZoom
        )
    }
    
    /**
     * Converts legacy tile coordinates to precise coordinates.
     * 
     * @param tileX The legacy tile X coordinate (Float)
     * @param tileY The legacy tile Y coordinate (Float)  
     * @param zoomLevel The zoom level
     * @param digitalZoom The digital zoom factor
     * @return The corresponding geographic coordinates as PreciseMapCoordinate
     */
    fun convertLegacyTileCoordinates(
        tileX: Double,
        tileY: Double,
        zoomLevel: Int,
        digitalZoom: Double = 1.0
    ): PreciseMapCoordinate {
        // Convert tile coordinates back to lat/lng using high-precision transformer
        val (lat, lng) = PrecisionCoordinateTransformer.highPrecisionTileToLatLng(
            tileX, tileY, zoomLevel
        )
        
        return PreciseMapCoordinate.fromLatLng(lat, lng, zoomLevel, digitalZoom)
    }
    
    /**
     * Debug method to log position alignment for troubleshooting.
     * 
     * @param dropPointPosition The drop point position
     * @param arrowBasePosition The arrow base position
     * @param context Additional context for logging
     */
    fun debugLogAlignment(
        dropPointPosition: Offset,
        arrowBasePosition: Offset,
        context: String = ""
    ) {
        val deltaX = dropPointPosition.x - arrowBasePosition.x
        val deltaY = dropPointPosition.y - arrowBasePosition.y
        val distance = sqrt(deltaX * deltaX + deltaY * deltaY)
        
        timber.log.Timber.d(
            "📍 Position Alignment $context: " +
            "DropPoint(${dropPointPosition.x}, ${dropPointPosition.y}) " +
            "ArrowBase(${arrowBasePosition.x}, ${arrowBasePosition.y}) " +
            "Delta(${deltaX}, ${deltaY}) Distance=${distance}px"
        )
        
        if (distance > POSITION_TOLERANCE_PIXELS) {
            timber.log.Timber.w(
                "⚠️ Position misalignment detected: ${distance}px exceeds tolerance ${POSITION_TOLERANCE_PIXELS}px"
            )
        }
    }
}