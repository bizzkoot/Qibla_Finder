package com.bizzkoot.qiblafinder.ui.location

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * PRD M10: pure JVM tests for the map math helpers extracted from OpenStreetMapView —
 * accuracy-for-zoom mapping, digital-zoom scaling, and tile <-> lat/lng conversions.
 */
class OpenStreetMapViewMathTest {

    @Test
    fun `getAccuracyForZoom maps zoom levels to meters`() {
        assertEquals(5, getAccuracyForZoom(18))
        assertEquals(5, getAccuracyForZoom(19)) // anything >= 18 clamps to best accuracy
        assertEquals(10, getAccuracyForZoom(17))
        assertEquals(20, getAccuracyForZoom(16))
        assertEquals(40, getAccuracyForZoom(15))
        assertEquals(80, getAccuracyForZoom(14))
        assertEquals(150, getAccuracyForZoom(13))
        assertEquals(300, getAccuracyForZoom(12))
        assertEquals(600, getAccuracyForZoom(11))
        assertEquals(1000, getAccuracyForZoom(10))
        assertEquals(1000, getAccuracyForZoom(2)) // anything <= 11 floors at 1000
    }

    @Test
    fun `getAccuracyForZoomWithDigitalZoom divides by the digital zoom factor`() {
        assertEquals(5, getAccuracyForZoomWithDigitalZoom(18, 1.0))
        assertEquals(2, getAccuracyForZoomWithDigitalZoom(18, 2.0))  // 5 / 2 = 2 (int)
        assertEquals(20, getAccuracyForZoomWithDigitalZoom(14, 4.0)) // 80 / 4 = 20
        assertEquals(300, getAccuracyForZoomWithDigitalZoom(11, 2.0))
    }

    @Test
    fun `getAccuracyForZoomWithDigitalZoom never drops below 1 meter`() {
        assertEquals(1, getAccuracyForZoomWithDigitalZoom(18, 10.0)) // 5 / 10 = 0 -> 1
        assertEquals(1, getAccuracyForZoomWithDigitalZoom(18, 100.0))
    }

    @Test
    fun `tileXToLongitude maps tile X to longitude`() {
        assertEquals(-180.0, tileXToLongitude(0.0, 0), 1e-9)
        assertEquals(180.0, tileXToLongitude(1.0, 0), 1e-9)
        assertEquals(-180.0, tileXToLongitude(0.0, 1), 1e-9)
        assertEquals(0.0, tileXToLongitude(1.0, 1), 1e-9)
        assertEquals(180.0, tileXToLongitude(2.0, 1), 1e-9)
        assertEquals(0.0, tileXToLongitude(512.0, 10), 1e-9)
    }

    @Test
    fun `tileYToLatitude maps tile Y to latitude`() {
        // Web-Mercator max/min latitudes at the top/bottom rows.
        assertEquals(85.05112877980659, tileYToLatitude(0.0, 0), 1e-9)
        assertEquals(-85.05112877980659, tileYToLatitude(1.0, 0), 1e-9)
        assertEquals(85.05112877980659, tileYToLatitude(0.0, 1), 1e-9)
        assertEquals(0.0, tileYToLatitude(1.0, 1), 1e-9)
        assertEquals(-85.05112877980659, tileYToLatitude(2.0, 1), 1e-9)
    }

    @Test
    fun `tileYToLatitude is symmetric around the equator`() {
        val north = tileYToLatitude(0.25, 2)
        val south = tileYToLatitude(3.75, 2) // mirrored across y = 2 (equator at zoom 2)
        assertEquals(north, -south, 1e-9)
    }
}
