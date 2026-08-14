package com.bizzkoot.qiblafinder.ui.compass

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.sqrt

/**
 * A compact bubble level for placing the phone flat on the ground. The bubble
 * moves in the direction of the screen-plane gravity vector; it settles in the
 * center when both display axes are level.
 */
@Composable
fun WaterLevelIndicator(
    levelX: Float,
    levelY: Float,
    modifier: Modifier = Modifier
) {
    val x by animateFloatAsState(
        targetValue = levelX,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "levelBubbleX"
    )
    val y by animateFloatAsState(
        targetValue = levelY,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "levelBubbleY"
    )
    val deviation = sqrt(x * x + y * y)
    val isLevel = deviation < LEVEL_TOLERANCE
    val accent = if (isLevel) Color(0xFF2EAA5B) else Color(0xFFFFB020)

    Box(
        modifier = modifier
            .size(86.dp)
            .alpha(0.94f)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(72.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val travel = size.minDimension * 0.30f
            drawCircle(color = accent, center = center, radius = size.minDimension / 2f, style = Stroke(3.dp.toPx()))
            drawLine(accent.copy(alpha = 0.45f), Offset(center.x, 8.dp.toPx()), Offset(center.x, size.height - 8.dp.toPx()), 1.dp.toPx())
            drawLine(accent.copy(alpha = 0.45f), Offset(8.dp.toPx(), center.y), Offset(size.width - 8.dp.toPx(), center.y), 1.dp.toPx())
            drawCircle(
                color = accent,
                center = Offset(center.x + x * travel, center.y + y * travel),
                radius = 9.dp.toPx()
            )
        }
        Text(
            text = if (isLevel) "LEVEL" else "FLAT",
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

private const val LEVEL_TOLERANCE = 0.10f
