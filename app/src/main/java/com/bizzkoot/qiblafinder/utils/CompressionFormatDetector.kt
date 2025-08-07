package com.bizzkoot.qiblafinder.utils

import android.graphics.Bitmap
import timber.log.Timber

object CompressionFormatDetector {
    private var isWebpLossySupported: Boolean? = null

    fun isWebpLossySupported(): Boolean {
        if (isWebpLossySupported != null) {
            return isWebpLossySupported!!
        }

        return try {
            // Use reflection to check if WEBP_LOSSY field exists
            val compressFormatClass = Bitmap.CompressFormat::class.java
            compressFormatClass.getField("WEBP_LOSSY")
            isWebpLossySupported = true
            true
        } catch (e: NoSuchFieldException) {
            isWebpLossySupported = false
            false
        } catch (e: Exception) {
            // Log the unexpected error but default to false for safety
            Timber.w(e, "Unexpected error checking WEBP_LOSSY support")
            isWebpLossySupported = false
            false
        }
    }

    fun getOptimalCompressionFormat(quality: Int): Bitmap.CompressFormat {
        return when {
            isWebpLossySupported() -> {
                try {
                    // Use reflection to get the WEBP_LOSSY field
                    val compressFormatClass = Bitmap.CompressFormat::class.java
                    val webpLossyField = compressFormatClass.getField("WEBP_LOSSY")
                    webpLossyField.get(null) as Bitmap.CompressFormat
                } catch (e: Exception) {
                    Timber.w(e, "Failed to get WEBP_LOSSY format, falling back to WEBP")
                    Bitmap.CompressFormat.WEBP
                }
            }
            else -> Bitmap.CompressFormat.WEBP
        }
    }
}
