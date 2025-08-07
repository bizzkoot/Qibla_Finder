package com.bizzkoot.qiblafinder.utils

import android.graphics.Bitmap
import timber.log.Timber
import java.io.OutputStream

object CompressionUtils {
    private const val MIN_QUALITY = 60
    private const val MAX_QUALITY = 95
    private const val DEFAULT_QUALITY = 85

    /**
     * Compress bitmap with optimal format and quality
     */
    fun compressBitmap(
        bitmap: Bitmap,
        quality: Int = DEFAULT_QUALITY,
        outputStream: OutputStream
    ): Boolean {
        val adjustedQuality = quality.coerceIn(MIN_QUALITY, MAX_QUALITY)
        val compressionFormat = CompressionFormatDetector.getOptimalCompressionFormat(adjustedQuality)

        return try {
            val success = bitmap.compress(compressionFormat, adjustedQuality, outputStream)
            if (success) {
                Timber.d("Bitmap compressed successfully with format: $compressionFormat, quality: $adjustedQuality")
            } else {
                Timber.w("Bitmap compression failed with format: $compressionFormat")
            }
            success
        } catch (e: Exception) {
            Timber.e(e, "Error compressing bitmap with format: $compressionFormat")
            // Fallback to PNG if compression fails
            fallbackToPng(bitmap, outputStream)
        }
    }

    /**
     * Fallback compression method using PNG
     */
    private fun fallbackToPng(bitmap: Bitmap, outputStream: OutputStream): Boolean {
        return try {
            val success = bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            if (success) {
                Timber.d("Bitmap compressed successfully with PNG fallback")
            } else {
                Timber.w("PNG fallback compression failed")
            }
            success
        } catch (e: Exception) {
            Timber.e(e, "PNG fallback compression failed")
            false
        }
    }

    /**
     * Get compression quality based on zoom level and device capabilities
     */
    fun getCompressionQuality(zoom: Int, deviceCapabilities: DeviceCapabilities): Int {
        val baseQuality = when {
            zoom >= 18 -> 95  // High detail, high quality
            zoom >= 16 -> 90  // Medium detail, good quality
            zoom >= 14 -> 85  // Lower detail, acceptable quality
            else -> 80         // Low detail, compressed
        }

        // Adjust quality based on device capabilities
        return when {
            deviceCapabilities.isHighEndDevice -> baseQuality
            deviceCapabilities.isMidRangeDevice -> (baseQuality * 0.9).toInt()
            else -> (baseQuality * 0.8).toInt() // Low-end devices get more compression
        }
    }
}
