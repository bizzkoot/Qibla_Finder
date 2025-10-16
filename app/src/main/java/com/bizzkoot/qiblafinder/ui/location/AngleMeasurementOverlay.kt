package com.bizzkoot.qiblafinder.ui.location

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt

@Composable
fun AngleMeasurementOverlay(
    snapshot: AngleMeasurementSnapshot,
    lastMeasuredAngle: Double?,
    onAngleMeasured: (Double) -> Unit,
    onClearMeasurement: () -> Unit,
    onDismiss: () -> Unit
) {
    val bitmap = snapshot.bitmap
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    val qiblaStart = remember(snapshot) { Offset(snapshot.qiblaLineStart.x, snapshot.qiblaLineStart.y) }
    val qiblaEnd = remember(snapshot) { Offset(snapshot.qiblaLineEnd.x, snapshot.qiblaLineEnd.y) }

    var touchPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var calculatedAngle by remember { mutableStateOf(lastMeasuredAngle) }

    LaunchedEffect(lastMeasuredAngle) {
        calculatedAngle = lastMeasuredAngle
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
    ) {
        val density = LocalDensity.current
        val containerWidth = with(density) { maxWidth.toPx() }
        val containerHeight = with(density) { maxHeight.toPx() }
        val imageWidth = bitmap.width.coerceAtLeast(1).toFloat()
        val imageHeight = bitmap.height.coerceAtLeast(1).toFloat()

        val imageAspect = imageWidth / imageHeight
        val containerAspect = if (containerHeight == 0f) 1f else containerWidth / containerHeight

        val scale = if (containerAspect > imageAspect) {
            containerHeight / imageHeight
        } else {
            containerWidth / imageWidth
        }

        val drawWidth = imageWidth * scale
        val drawHeight = imageHeight * scale
        val offsetX = (containerWidth - drawWidth) / 2f
        val offsetY = (containerHeight - drawHeight) / 2f

        val qiblaStartScaled = Offset(offsetX + qiblaStart.x * scale, offsetY + qiblaStart.y * scale)
        val qiblaEndScaled = Offset(offsetX + qiblaEnd.x * scale, offsetY + qiblaEnd.y * scale)

        val projectedTouches = touchPoints.map { Offset(offsetX + it.x * scale, offsetY + it.y * scale) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(imageWidth, imageHeight, offsetX, offsetY, scale) {
                    detectTapGestures { pointerPosition ->
                        val imageX = (pointerPosition.x - offsetX) / scale
                        val imageY = (pointerPosition.y - offsetY) / scale

                        if (imageX in 0f..imageWidth && imageY in 0f..imageHeight) {
                            val newPoint = Offset(imageX, imageY)
                            touchPoints = when (touchPoints.size) {
                                0 -> listOf(newPoint)
                                1 -> listOf(touchPoints.first(), newPoint)
                                else -> listOf(newPoint)
                            }

                            if (touchPoints.size == 2) {
                                val angle = calculateAngleDifference(
                                    touchPoints[0],
                                    touchPoints[1],
                                    qiblaStart,
                                    qiblaEnd
                                )
                                calculatedAngle = angle
                                onAngleMeasured(angle)
                            }
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawImage(
                    image = imageBitmap,
                    topLeft = Offset(offsetX, offsetY),
                    size = androidx.compose.ui.geometry.Size(drawWidth, drawHeight)
                )

                drawLine(
                    color = Color(0xFF81C784),
                    start = qiblaStartScaled,
                    end = qiblaEndScaled,
                    strokeWidth = 4.dp.toPx()
                )

                if (projectedTouches.size == 2) {
                    drawLine(
                        color = Color.Yellow,
                        start = projectedTouches[0],
                        end = projectedTouches[1],
                        strokeWidth = 3.dp.toPx()
                    )
                }

                projectedTouches.forEach { point ->
                    drawCircle(
                        color = Color.Yellow,
                        radius = 6.dp.toPx(),
                        center = point
                    )
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = buildString {
                        append("Angle to Qibla")
                        calculatedAngle?.let { append(": ${it.roundToInt()}°") }
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Qibla bearing ${snapshot.qiblaBearing.roundToInt()}°",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = if (touchPoints.size < 2) "Tap two points to draw your reference line" else "Tap again to start a new measurement",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center
                )
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                TextButton(
                    onClick = {
                        touchPoints = emptyList()
                        calculatedAngle = null
                        onClearMeasurement()
                    },
                    enabled = touchPoints.isNotEmpty() || calculatedAngle != null
                ) {
                    Text("Clear", color = Color.White)
                }
                Button(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    }
}

private fun calculateAngleDifference(
    userStart: Offset,
    userEnd: Offset,
    qiblaStart: Offset,
    qiblaEnd: Offset
): Double {
    val userVector = userEnd - userStart
    val qiblaVector = qiblaEnd - qiblaStart

    val userMagnitude = userVector.x * userVector.x + userVector.y * userVector.y
    val qiblaMagnitude = qiblaVector.x * qiblaVector.x + qiblaVector.y * qiblaVector.y

    if (userMagnitude == 0f || qiblaMagnitude == 0f) {
        return 0.0
    }

    val userAngle = Math.toDegrees(atan2(userVector.y.toDouble(), userVector.x.toDouble()))
    val qiblaAngle = Math.toDegrees(atan2(qiblaVector.y.toDouble(), qiblaVector.x.toDouble()))

    var difference = qiblaAngle - userAngle
    difference = ((difference + 540) % 360) - 180

    return abs(difference)
}
