package com.bizzkoot.qiblafinder.ui.location

import android.graphics.Bitmap
import android.graphics.PointF

/**
 * Captured snapshot of the manual location map used by the angle measurement overlay.
 * The bitmap represents the frozen map image and the Qibla line coordinates are
 * expressed relative to the bitmap's original dimensions.
 */
data class AngleMeasurementSnapshot(
    val bitmap: Bitmap,
    val qiblaLineStart: PointF,
    val qiblaLineEnd: PointF,
    val qiblaBearing: Double
)
