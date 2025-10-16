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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import android.graphics.Paint as AndroidPaint
import android.graphics.Color as AndroidColor
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

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

    var touchPoints by remember(snapshot) { mutableStateOf<List<Offset>>(emptyList()) }
    var calculatedAngle by remember(snapshot) { mutableStateOf(lastMeasuredAngle) }
    var overlayScale by remember(snapshot) { mutableStateOf(1f) }

    LaunchedEffect(lastMeasuredAngle) {
        calculatedAngle = lastMeasuredAngle
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
    ) {
        val density = LocalDensity.current
        val containerWidth = with(density) { maxWidth.toPx() }
        val containerHeight = with(density) { maxHeight.toPx() }
        val imageWidth = bitmap.width.coerceAtLeast(1).toFloat()
        val imageHeight = bitmap.height.coerceAtLeast(1).toFloat()

        val imageAspect = imageWidth / imageHeight
        val containerAspect = if (containerHeight == 0f) 1f else containerWidth / containerHeight

        val baseScale = if (containerAspect > imageAspect) {
            containerHeight / imageHeight
        } else {
            containerWidth / imageWidth
        }

        val minOverlayScale = 1f
        val maxOverlayScale = 12f
        val scaleStep = 1.3f

        val totalScale = baseScale * overlayScale
        val scaledWidth = imageWidth * totalScale
        val scaledHeight = imageHeight * totalScale
        val offsetX = (containerWidth - scaledWidth) / 2f
        val offsetY = (containerHeight - scaledHeight) / 2f

        val qiblaStartScaled = Offset(offsetX + qiblaStart.x * totalScale, offsetY + qiblaStart.y * totalScale)
        val qiblaEndScaled = Offset(offsetX + qiblaEnd.x * totalScale, offsetY + qiblaEnd.y * totalScale)
        val projectedTouches = touchPoints.map { Offset(offsetX + it.x * totalScale, offsetY + it.y * totalScale) }

        val labelPaint = remember(density) {
            AndroidPaint().apply {
                color = AndroidColor.WHITE
                isAntiAlias = true
                textAlign = AndroidPaint.Align.LEFT
            }
        }
        labelPaint.textSize = with(density) { 12.sp.toPx() }

        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(imageWidth, imageHeight, offsetX, offsetY, totalScale) {
                        detectTapGestures { pointerPosition ->
                            val imageX = (pointerPosition.x - offsetX) / totalScale
                            val imageY = (pointerPosition.y - offsetY) / totalScale

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
                val dstOffset = IntOffset(offsetX.roundToInt(), offsetY.roundToInt())
                val dstSize = IntSize(scaledWidth.roundToInt().coerceAtLeast(1), scaledHeight.roundToInt().coerceAtLeast(1))

                drawImage(
                    image = imageBitmap,
                    dstOffset = dstOffset,
                    dstSize = dstSize
                )

                drawLine(
                    color = Color(0xFF81C784),
                    start = qiblaStartScaled,
                    end = qiblaEndScaled,
                    strokeWidth = 4.dp.toPx()
                )

                if (projectedTouches.size == 2) {
                    drawLine(
                        color = Color.Black.copy(alpha = 0.45f),
                        start = projectedTouches[0],
                        end = projectedTouches[1],
                        strokeWidth = 5.dp.toPx()
                    )
                    drawLine(
                        color = Color.Yellow,
                        start = projectedTouches[0],
                        end = projectedTouches[1],
                        strokeWidth = 3.dp.toPx()
                    )

                    calculatedAngle?.let { angle ->
                        val pivot = projectedTouches[0]
                        val userVector = projectedTouches[1] - projectedTouches[0]
                        val qiblaVector = qiblaEndScaled - qiblaStartScaled
                        val userLength = sqrt((userVector.x * userVector.x + userVector.y * userVector.y).toDouble()).toFloat()
                        val qiblaLength = sqrt((qiblaVector.x * qiblaVector.x + qiblaVector.y * qiblaVector.y).toDouble()).toFloat()

                        if (userLength > 1f && qiblaLength > 1f) {
                            val userAngleDeg = Math.toDegrees(atan2(userVector.y.toDouble(), userVector.x.toDouble())).toFloat()
                            val arcRadius = min(userLength, 140.dp.toPx())
                            val arcTopLeft = Offset(pivot.x - arcRadius, pivot.y - arcRadius)
                            val arcSize = Size(arcRadius * 2, arcRadius * 2)

                            drawArc(
                                color = Color.Yellow.copy(alpha = 0.7f),
                                startAngle = userAngleDeg,
                                sweepAngle = angle.toFloat(),
                                useCenter = false,
                                topLeft = arcTopLeft,
                                size = arcSize,
                                style = Stroke(width = 2.dp.toPx())
                            )

                            val normalizedQibla = Offset(
                                (qiblaVector.x / qiblaLength).toFloat(),
                                (qiblaVector.y / qiblaLength).toFloat()
                            )
                            val qiblaReferenceEnd = Offset(
                                pivot.x + normalizedQibla.x * arcRadius,
                                pivot.y + normalizedQibla.y * arcRadius
                            )
                            drawLine(
                                color = Color(0xFF81C784),
                                start = pivot,
                                end = qiblaReferenceEnd,
                                strokeWidth = 2.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f))
                            )
                        }
                    }
                }

                val labelOffset = 8.dp.toPx()
                projectedTouches.forEachIndexed { index, point ->
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.5f),
                        radius = 8.dp.toPx(),
                        center = point
                    )
                    drawCircle(
                        color = Color.Yellow,
                        radius = 6.dp.toPx(),
                        center = point
                    )

                    drawContext.canvas.nativeCanvas.drawText(
                        "P${index + 1}",
                        point.x + labelOffset,
                        point.y - labelOffset,
                        labelPaint
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                color = Color.Black.copy(alpha = 0.55f),
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                        .widthIn(max = 360.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = buildString {
                            append("Angle to Qibla")
                            calculatedAngle?.let {
                                val formatted = if (it >= 0) "+${it.roundToInt()}" else "${it.roundToInt()}"
                                append(": $formatted°")
                            }
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Divider(color = Color.White.copy(alpha = 0.25f))
                    Text(
                        text = "Qibla bearing ${snapshot.qiblaBearing.roundToInt()}°",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    calculatedAngle?.let {
                        Text(
                            text = when {
                                it > 0 -> "Rotate clockwise from Point 1 → Point 2 to align with the Qibla line."
                                it < 0 -> "Rotate counter-clockwise from Point 1 → Point 2 to align with the Qibla line."
                                else -> "Your reference line is aligned with the Qibla direction."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.95f),
                            textAlign = TextAlign.Center
                        )
                    }
                    Text(
                        text = "Point 1 sets the arc pivot. Point 2 extends your reference line.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = if (touchPoints.size < 2) "Tap two points to draw your reference line" else "Tap again to start a new measurement",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.9f),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp)
                    .zIndex(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                FloatingActionButton(
                    onClick = {
                        overlayScale = (overlayScale * scaleStep).coerceAtMost(maxOverlayScale)
                    },
                    shape = CircleShape,
                    containerColor = Color.Black.copy(alpha = 0.55f),
                    contentColor = Color.White,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Zoom in")
                }

                FloatingActionButton(
                    onClick = {
                        overlayScale = (overlayScale / scaleStep).coerceAtLeast(minOverlayScale)
                    },
                    shape = CircleShape,
                    containerColor = Color.Black.copy(alpha = 0.55f),
                    contentColor = Color.White,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Filled.Remove, contentDescription = "Zoom out")
                }

                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    contentColor = Color.White,
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = String.format("%.1fx", overlayScale),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 24.dp, end = 96.dp, bottom = 24.dp),
                color = Color.Black.copy(alpha = 0.5f),
                shape = RoundedCornerShape(24.dp)
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
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

    return difference
}
