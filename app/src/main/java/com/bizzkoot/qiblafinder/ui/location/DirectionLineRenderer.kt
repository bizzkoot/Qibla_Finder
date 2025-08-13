package com.bizzkoot.qiblafinder.ui.location

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import timber.log.Timber
import kotlin.math.*

/**
 * Renders the Qibla direction line with dual-layer rendering (outline + core)
 * and adaptive styling based on map type.
 */
class DirectionLineRenderer {
    
    companion object {
        private const val CORE_LINE_WIDTH_DP = 3f
        private const val OUTLINE_WIDTH_DP = 5f
        private const val ARROW_SIZE_DP = 12f
        private const val ARROW_ANGLE = 45.0 // degrees
        private const val CORE_ALPHA = 0.9f
        private const val OUTLINE_ALPHA = 0.7f
    }
    
    /**
     * Renders the complete direction line with outline and core layers
     * Enhanced with error handling and graceful fallbacks
     */
    fun renderDirectionLine(
        drawScope: DrawScope,
        screenPoints: List<Offset>,
        mapType: MapType,
        showArrow: Boolean = true
    ) {
        try {
            if (screenPoints.size < 2) {
                Timber.d("📍 Insufficient points for direction line rendering: ${screenPoints.size}")
                return
            }
            
            // Validate screen points for invalid values
            val validPoints = screenPoints.filter { point ->
                point.x.isFinite() && point.y.isFinite() && 
                !point.x.isNaN() && !point.y.isNaN()
            }
            
            if (validPoints.size < 2) {
                Timber.w("📍 Insufficient valid points after filtering: ${validPoints.size}")
                return
            }
            
            val colors = getAdaptiveColors(mapType)
            val path = createSmoothPath(validPoints)
            
            // Render outline layer first (wider)
            renderOutlineLayer(drawScope, path, colors.outline)
            
            // Render core layer on top (narrower)
            renderCoreLayer(drawScope, path, colors.core)
            
            // Render arrow if requested and we have enough points
            if (showArrow && validPoints.size >= 2) {
                renderArrow(drawScope, validPoints, colors)
            }
            
        } catch (e: Exception) {
            Timber.e(e, "📍 Error rendering direction line, attempting fallback")
            
            // Fallback: render simple line if smooth path fails
            try {
                renderFallbackLine(drawScope, screenPoints, mapType)
            } catch (fallbackError: Exception) {
                Timber.e(fallbackError, "📍 Fallback rendering also failed")
            }
        }
    }
    
    /**
     * Creates a smooth curved path using quadratic Bézier curves with error handling
     */
    private fun createSmoothPath(points: List<Offset>): Path {
        val path = Path()
        
        try {
            if (points.isEmpty()) return path
            if (points.size == 1) {
                path.moveTo(points[0].x, points[0].y)
                return path
            }
            
            // Start at the first point
            path.moveTo(points[0].x, points[0].y)
            
            if (points.size == 2) {
                // Simple line for two points
                path.lineTo(points[1].x, points[1].y)
                return path
            }
            
            // Create smooth curves through multiple points
            for (i in 1 until points.size) {
                try {
                    val current = points[i]
                    val previous = points[i - 1]
                    
                    // Validate points before using them
                    if (!isValidPoint(current) || !isValidPoint(previous)) {
                        Timber.w("📍 Invalid point detected at index $i, using linear interpolation")
                        path.lineTo(current.x, current.y)
                        continue
                    }
                    
                    if (i == points.size - 1) {
                        // Last segment - draw straight to end
                        path.lineTo(current.x, current.y)
                    } else {
                        // Create control point for smooth curve
                        val next = points[i + 1]
                        if (isValidPoint(next)) {
                            val controlPoint = calculateControlPoint(previous, current, next)
                            if (isValidPoint(controlPoint)) {
                                path.quadraticBezierTo(
                                    controlPoint.x, controlPoint.y,
                                    current.x, current.y
                                )
                            } else {
                                // Fallback to linear if control point is invalid
                                path.lineTo(current.x, current.y)
                            }
                        } else {
                            path.lineTo(current.x, current.y)
                        }
                    }
                } catch (e: Exception) {
                    Timber.w(e, "📍 Error processing point $i, using linear fallback")
                    // Fallback to linear connection
                    try {
                        path.lineTo(points[i].x, points[i].y)
                    } catch (fallbackError: Exception) {
                        Timber.w(fallbackError, "📍 Linear fallback also failed for point $i")
                    }
                }
            }
            
        } catch (e: Exception) {
            Timber.e(e, "📍 Error creating smooth path, returning simple path")
            // Return a simple linear path as ultimate fallback
            return createSimplePath(points)
        }
        
        return path
    }
    
    /**
     * Calculates control point for smooth Bézier curve
     */
    private fun calculateControlPoint(
        previous: Offset,
        current: Offset,
        next: Offset
    ): Offset {
        // Calculate the midpoint between previous and next
        val midX = (previous.x + next.x) / 2f
        val midY = (previous.y + next.y) / 2f
        
        // Use current point as control, but smooth it towards the midpoint
        val smoothingFactor = 0.3f
        return Offset(
            current.x + (midX - current.x) * smoothingFactor,
            current.y + (midY - current.y) * smoothingFactor
        )
    }
    
    /**
     * Renders the outline layer (wider, for contrast)
     */
    private fun renderOutlineLayer(
        drawScope: DrawScope,
        path: Path,
        outlineColor: Color
    ) {
        with(drawScope) {
            drawPath(
                path = path,
                color = outlineColor,
                style = Stroke(
                    width = OUTLINE_WIDTH_DP.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                ),
                alpha = OUTLINE_ALPHA
            )
        }
    }
    
    /**
     * Renders the core layer (narrower, main color)
     */
    private fun renderCoreLayer(
        drawScope: DrawScope,
        path: Path,
        coreColor: Color
    ) {
        with(drawScope) {
            drawPath(
                path = path,
                color = coreColor,
                style = Stroke(
                    width = CORE_LINE_WIDTH_DP.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                ),
                alpha = CORE_ALPHA
            )
        }
    }
    
    /**
     * Renders an arrow at the end of the direction line
     */
    private fun renderArrow(
        drawScope: DrawScope,
        screenPoints: List<Offset>,
        colors: AdaptiveColors
    ) {
        if (screenPoints.size < 2) return
        
        // Get the last two points to determine arrow direction
        val endPoint = screenPoints.last()
        val secondLastPoint = screenPoints[screenPoints.size - 2]
        
        // Calculate arrow direction
        val direction = calculateArrowDirection(secondLastPoint, endPoint)
        val arrowPoints = createArrowPath(endPoint, direction)
        
        // Render arrow outline
        renderArrowOutline(drawScope, arrowPoints, colors.outline)
        
        // Render arrow core
        renderArrowCore(drawScope, arrowPoints, colors.core)
    }
    
    /**
     * Calculates the direction angle for the arrow
     */
    private fun calculateArrowDirection(from: Offset, to: Offset): Float {
        val dx = to.x - from.x
        val dy = to.y - from.y
        return atan2(dy, dx)
    }
    
    /**
     * Creates the arrow path points
     */
    private fun createArrowPath(tip: Offset, direction: Float): List<Offset> {
        val arrowSize = ARROW_SIZE_DP
        val halfAngle = Math.toRadians(ARROW_ANGLE / 2.0)
        
        // Calculate the two base points of the arrow
        val leftAngle = direction + halfAngle + PI
        val rightAngle = direction - halfAngle + PI
        
        val leftBase = Offset(
            tip.x + (arrowSize * cos(leftAngle)).toFloat(),
            tip.y + (arrowSize * sin(leftAngle)).toFloat()
        )
        
        val rightBase = Offset(
            tip.x + (arrowSize * cos(rightAngle)).toFloat(),
            tip.y + (arrowSize * sin(rightAngle)).toFloat()
        )
        
        return listOf(tip, leftBase, rightBase)
    }
    
    /**
     * Renders the arrow outline
     */
    private fun renderArrowOutline(
        drawScope: DrawScope,
        arrowPoints: List<Offset>,
        outlineColor: Color
    ) {
        if (arrowPoints.size != 3) return
        
        val path = Path().apply {
            moveTo(arrowPoints[0].x, arrowPoints[0].y) // tip
            lineTo(arrowPoints[1].x, arrowPoints[1].y) // left base
            moveTo(arrowPoints[0].x, arrowPoints[0].y) // tip
            lineTo(arrowPoints[2].x, arrowPoints[2].y) // right base
        }
        
        with(drawScope) {
            drawPath(
                path = path,
                color = outlineColor,
                style = Stroke(
                    width = (OUTLINE_WIDTH_DP + 1f).dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                ),
                alpha = OUTLINE_ALPHA
            )
        }
    }
    
    /**
     * Renders the arrow core
     */
    private fun renderArrowCore(
        drawScope: DrawScope,
        arrowPoints: List<Offset>,
        coreColor: Color
    ) {
        if (arrowPoints.size != 3) return
        
        val path = Path().apply {
            moveTo(arrowPoints[0].x, arrowPoints[0].y) // tip
            lineTo(arrowPoints[1].x, arrowPoints[1].y) // left base
            moveTo(arrowPoints[0].x, arrowPoints[0].y) // tip
            lineTo(arrowPoints[2].x, arrowPoints[2].y) // right base
        }
        
        with(drawScope) {
            drawPath(
                path = path,
                color = coreColor,
                style = Stroke(
                    width = CORE_LINE_WIDTH_DP.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                ),
                alpha = CORE_ALPHA
            )
        }
    }
    
    /**
     * Gets adaptive colors based on map type for maximum visibility
     */
    private fun getAdaptiveColors(mapType: MapType): AdaptiveColors {
        return when (mapType) {
            MapType.STREET -> AdaptiveColors(
                core = Color(0xFF4CAF50), // Green
                outline = Color.White
            )
            MapType.SATELLITE -> AdaptiveColors(
                core = Color(0xFF4CAF50), // Green
                outline = Color.Black
            )
        }
    }
    
    /**
     * Data class to hold adaptive color scheme
     */
    private data class AdaptiveColors(
        val core: Color,
        val outline: Color
    )
    
    /**
     * Validates if a point has finite, non-NaN coordinates
     */
    private fun isValidPoint(point: Offset): Boolean {
        return point.x.isFinite() && point.y.isFinite() && 
               !point.x.isNaN() && !point.y.isNaN()
    }
    
    /**
     * Creates a simple linear path as fallback when smooth path creation fails
     */
    private fun createSimplePath(points: List<Offset>): Path {
        val path = Path()
        
        try {
            if (points.isEmpty()) return path
            
            val validPoints = points.filter { isValidPoint(it) }
            if (validPoints.isEmpty()) return path
            
            path.moveTo(validPoints[0].x, validPoints[0].y)
            
            for (i in 1 until validPoints.size) {
                path.lineTo(validPoints[i].x, validPoints[i].y)
            }
            
        } catch (e: Exception) {
            Timber.e(e, "📍 Even simple path creation failed")
        }
        
        return path
    }
    
    /**
     * Renders a fallback simple line when normal rendering fails
     */
    private fun renderFallbackLine(
        drawScope: DrawScope,
        screenPoints: List<Offset>,
        mapType: MapType
    ) {
        try {
            val validPoints = screenPoints.filter { isValidPoint(it) }
            if (validPoints.size < 2) return
            
            val colors = getAdaptiveColors(mapType)
            val simplePath = createSimplePath(validPoints)
            
            with(drawScope) {
                // Draw simple line without fancy effects
                drawPath(
                    path = simplePath,
                    color = colors.core,
                    style = Stroke(
                        width = CORE_LINE_WIDTH_DP.dp.toPx(),
                        cap = StrokeCap.Round
                    ),
                    alpha = CORE_ALPHA
                )
            }
            
            Timber.i("📍 Fallback line rendering successful")
            
        } catch (e: Exception) {
            Timber.e(e, "📍 Fallback line rendering failed")
        }
    }
}