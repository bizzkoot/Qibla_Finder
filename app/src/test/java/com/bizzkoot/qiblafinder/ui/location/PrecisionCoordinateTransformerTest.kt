package com.bizzkoot.qiblafinder.ui.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PRD M10: high-precision tile <-> lat/lng transforms, Web-Mercator round-trips,
 * great-circle distance, bearing normalization, and input validation.
 */
class PrecisionCoordinateTransformerTest {

    private val meccaLat = 21.4225
    private val meccaLng = 39.8262

    @Test
    fun `latLng round-trips through high precision tiles at multiple zooms`() {
        for (zoom in listOf(2, 10, 18)) {
            val (tileX, tileY) =
                PrecisionCoordinateTransformer.latLngToHighPrecisionTile(meccaLat, meccaLng, zoom)
            val (lat, lng) =
                PrecisionCoordinateTransformer.highPrecisionTileToLatLng(tileX, tileY, zoom)
            assertEquals("lat round-trip at zoom $zoom", meccaLat, lat, 1e-6)
            assertEquals("lng round-trip at zoom $zoom", meccaLng, lng, 1e-6)
        }
    }

    @Test
    fun `web mercator round-trips back to the original coordinates`() {
        val (x, y) = PrecisionCoordinateTransformer.latLngToWebMercator(meccaLat, meccaLng)
        val (lat, lng) = PrecisionCoordinateTransformer.webMercatorToLatLng(x, y)
        assertEquals(meccaLat, lat, 1e-6)
        assertEquals(meccaLng, lng, 1e-6)
    }

    @Test
    fun `great circle distance between Mecca and the Kaaba is only a few meters`() {
        val meters = PrecisionCoordinateTransformer.calculateGreatCircleDistance(
            meccaLat, meccaLng, 21.422487, 39.826206
        )
        assertTrue("expected at most a few hundred meters, got $meters", meters < 500.0)
    }

    @Test
    fun `great circle distance New York to London is about 5 point 5 million meters`() {
        val meters = PrecisionCoordinateTransformer.calculateGreatCircleDistance(
            40.7128, -74.0060, 51.5074, -0.1278
        )
        assertTrue("expected ~5.5e6 m, got $meters", meters in 5.3e6..5.8e6)
    }

    @Test
    fun `bearing is normalized into the 0 to 360 range`() {
        val bearings = listOf(
            PrecisionCoordinateTransformer.calculateBearing(0.0, 0.0, 1.0, 0.0),  // north
            PrecisionCoordinateTransformer.calculateBearing(0.0, 0.0, -1.0, 0.0), // south -> 180
            PrecisionCoordinateTransformer.calculateBearing(0.0, 0.0, 0.0, -1.0)  // west  -> 270
        )
        bearings.forEach { bearing ->
            assertTrue("bearing $bearing must be in [0,360)", bearing >= 0.0 && bearing < 360.0)
        }
        // Sanity: south is 180, west is 270.
        assertEquals(180.0, bearings[1], 1e-9)
        assertEquals(270.0, bearings[2], 1e-9)
    }

    @Test
    fun `out-of-range inputs are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            PrecisionCoordinateTransformer.latLngToHighPrecisionTile(90.0, 0.0, 10)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PrecisionCoordinateTransformer.latLngToHighPrecisionTile(0.0, 181.0, 10)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PrecisionCoordinateTransformer.latLngToHighPrecisionTile(0.0, 0.0, 21)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PrecisionCoordinateTransformer.highPrecisionTileToLatLng(-1.0, 0.0, 10)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PrecisionCoordinateTransformer.highPrecisionTileToLatLng(0.0, 0.0, 25)
        }
    }
}
