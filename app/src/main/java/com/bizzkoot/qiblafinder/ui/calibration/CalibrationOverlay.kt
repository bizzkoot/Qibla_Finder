package com.bizzkoot.qiblafinder.ui.calibration

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bizzkoot.qiblafinder.R
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun CalibrationOverlay(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val announcementText = stringResource(R.string.calibration_overlay_accessibility_hint)
    val overlayTitle = stringResource(R.string.calibration_overlay_title)
    val overlayInstructions = stringResource(R.string.calibration_overlay_instructions)
    val dismissLabel = stringResource(R.string.calibration_overlay_dismiss)

    LaunchedEffect(isVisible) {
        if (isVisible) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            delay(150)
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(durationMillis = 250)) +
            scaleIn(initialScale = 0.95f, animationSpec = tween(durationMillis = 250)),
        exit = fadeOut(animationSpec = tween(durationMillis = 200)) +
            scaleOut(targetScale = 0.95f, animationSpec = tween(durationMillis = 200))
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f))
                .semantics { contentDescription = announcementText },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                tonalElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = overlayTitle,
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.size(12.dp))

                    Text(
                        text = overlayInstructions,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.size(24.dp))

                    FigureEightAnimation(modifier = Modifier.size(160.dp))

                    Spacer(modifier = Modifier.size(24.dp))

                    Button(onClick = onDismiss) {
                        Text(text = dismissLabel)
                    }
                }
            }
        }
    }
}

@Composable
private fun FigureEightAnimation(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "figure8")
    val phase = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "figure8phase"
    )

    val animationDescription = stringResource(R.string.calibration_overlay_animation_description)
    val trailColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val dotColor = MaterialTheme.colorScheme.primary

    Canvas(
        modifier = modifier.semantics {
            contentDescription = animationDescription
        }
    ) {
        val width = size.width
        val height = size.height
        val centerX = width / 2f
        val centerY = height / 2f
        val radius = size.minDimension / 3f

        val path = Path()
        var initialized = false
        for (degrees in 0..360 step 2) {
            val angle = Math.toRadians(degrees.toDouble())
            val sinValue = sin(angle)
            val cosValue = cos(angle)
            val x = centerX + (radius * sinValue * cosValue).toFloat()
            val y = centerY + (radius * sinValue * cosValue * cosValue).toFloat()
            if (!initialized) {
                path.moveTo(x, y)
                initialized = true
            } else {
                path.lineTo(x, y)
            }
        }

        drawPath(path = path, color = trailColor, style = Stroke(width = 6f))

        val animatedAngle = 2 * Math.PI * phase.value
        val dotX = centerX + (radius * sin(animatedAngle) * cos(animatedAngle)).toFloat()
        val dotY = centerY + (radius * sin(animatedAngle) * cos(animatedAngle) * cos(animatedAngle)).toFloat()

        drawCircle(color = dotColor, radius = 10f, center = Offset(dotX, dotY))
    }
}
