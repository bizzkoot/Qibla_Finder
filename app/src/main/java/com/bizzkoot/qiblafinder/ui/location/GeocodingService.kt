package com.bizzkoot.qiblafinder.ui.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

data class GeocodingResult(
    val title: String,
    val subtitle: String,
    val location: MapLocation
)

interface GeocodingService {
    suspend fun search(
        query: String,
        center: MapLocation?,
        limit: Int = 5
    ): Result<List<GeocodingResult>>

    fun isAvailable(): Boolean
}

class AndroidGeocodingService(
    private val context: Context,
    private val locale: Locale = Locale.getDefault()
) : GeocodingService {

    override fun isAvailable(): Boolean {
        return Geocoder.isPresent()
    }

    override suspend fun search(query: String, center: MapLocation?, limit: Int): Result<List<GeocodingResult>> {
        return withContext(Dispatchers.IO) {
            try {
                if (!isAvailable()) {
                    return@withContext Result.success(emptyList())
                }

                // 1) Direct lat,lng parsing support (e.g., "3.1390, 101.6869")
                parseLatLng(query)?.let { ll ->
                    return@withContext Result.success(
                        listOf(
                            GeocodingResult(
                                title = "Coordinates",
                                subtitle = "${ll.latitude}, ${ll.longitude}",
                                location = ll
                            )
                        )
                    )
                }

                val geocoder = Geocoder(context, locale)

                // 2) Try small viewport-biased search first
                val results: List<Address> = if (center != null) {
                    val small = createBoundingBox(center, deltaDegrees = 0.5)
                    val r1 = withTimeoutOrNull(4000) {
                        geocoder.getFromLocationName(query, limit, small.llLat, small.llLng, small.urLat, small.urLng)
                    } ?: emptyList()
                    if (r1.isNotEmpty()) r1 else {
                        // 3) Expand to a larger region around center if empty
                        val large = createBoundingBox(center, deltaDegrees = 2.0)
                        val r2 = withTimeoutOrNull(4000) {
                            geocoder.getFromLocationName(query, limit, large.llLat, large.llLng, large.urLat, large.urLng)
                        } ?: emptyList()
                        if (r2.isNotEmpty()) r2 else {
                            // 4) Fallback: unbounded search
                            withTimeoutOrNull(4000) {
                                geocoder.getFromLocationName(query, limit)
                            } ?: emptyList()
                        }
                    }
                } else {
                    withTimeoutOrNull(4000) {
                        geocoder.getFromLocationName(query, limit)
                    } ?: emptyList()
                }

                val mapped = results.mapNotNull { addr ->
                    if (!addr.hasLatitude() || !addr.hasLongitude()) return@mapNotNull null
                    val lat = addr.latitude
                    val lng = addr.longitude

                    val title = listOfNotNull(
                        addr.featureName,
                        addr.subLocality,
                        addr.locality
                    ).distinct().joinToString(", ").ifBlank { addr.getAddressLine(0) ?: "Unnamed location" }

                    val subtitle = listOfNotNull(
                        addr.thoroughfare,
                        addr.subAdminArea,
                        addr.adminArea,
                        addr.postalCode,
                        addr.countryName
                    ).distinct().joinToString(", ")

                    GeocodingResult(
                        title = title,
                        subtitle = subtitle,
                        location = MapLocation(lat, lng)
                    )
                }

                Result.success(mapped)
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }
    }

    private fun createBoundingBox(center: MapLocation, deltaDegrees: Double = 0.5): Quadruple {
        val lat = center.latitude
        val lng = center.longitude
        val latDelta = deltaDegrees
        val lngDelta = deltaDegrees / kotlin.math.max(0.1, kotlin.math.cos(Math.toRadians(lat)))

        val llLat = (lat - latDelta).coerceIn(-90.0, 90.0)
        val urLat = (lat + latDelta).coerceIn(-90.0, 90.0)
        val llLng = (lng - lngDelta).let { wrapLongitude(it) }
        val urLng = (lng + lngDelta).let { wrapLongitude(it) }

        return Quadruple(llLat, llLng, urLat, urLng)
    }

    private fun wrapLongitude(value: Double): Double {
        var v = value
        while (v < -180.0) v += 360.0
        while (v > 180.0) v -= 360.0
        return v
    }

    private data class Quadruple(val llLat: Double, val llLng: Double, val urLat: Double, val urLng: Double)

    private fun parseLatLng(text: String): MapLocation? {
        val trimmed = text.trim()
        val regex = Regex("^\\s*(-?\\d+(?:\\.\\d+)?)\\s*,\\s*(-?\\d+(?:\\.\\d+)?)\\s*$")
        val match = regex.find(trimmed) ?: return null
        val (latStr, lngStr) = match.destructured
        return try {
            val lat = latStr.toDouble()
            val lng = lngStr.toDouble()
            if (lat in -90.0..90.0 && lng >= -180.0 && lng <= 180.0) MapLocation(lat, lng) else null
        } catch (_: Exception) {
            null
        }
    }
}
