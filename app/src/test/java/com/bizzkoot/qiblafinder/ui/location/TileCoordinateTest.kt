package com.bizzkoot.qiblafinder.ui.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * PRD M10: tile cache-key/name formats and parse round-trips.
 */
class TileCoordinateTest {

    @Test
    fun `toCacheKey uses maptype_zoom_x_y`() {
        assertEquals("street_3_1_2", TileCoordinate(1, 2, 3, MapType.STREET).toCacheKey())
        assertEquals("satellite_10_5_6", TileCoordinate(5, 6, 10, MapType.SATELLITE).toCacheKey())
    }

    @Test
    fun `toFileName uses tile_maptype_zoom_x_y png`() {
        assertEquals("tile_street_3_1_2.png", TileCoordinate(1, 2, 3, MapType.STREET).toFileName())
        assertEquals("tile_satellite_10_5_6.png", TileCoordinate(5, 6, 10, MapType.SATELLITE).toFileName())
    }

    @Test
    fun `parseTileCacheKey round-trips 4-part keys`() {
        assertEquals(
            TileCoordinate(1, 2, 3, MapType.STREET),
            parseTileCacheKey("street_3_1_2")
        )
        assertEquals(
            TileCoordinate(5, 6, 10, MapType.SATELLITE),
            parseTileCacheKey("satellite_10_5_6")
        )
    }

    @Test
    fun `parseTileCacheKey round-trips 3-part keys with the default map type`() {
        assertEquals(
            TileCoordinate(1, 2, 3, MapType.STREET),
            parseTileCacheKey("3_1_2")
        )
    }

    @Test
    fun `parseTileCacheKey returns null for malformed keys`() {
        assertNull(parseTileCacheKey(""))
        assertNull(parseTileCacheKey("street"))
        assertNull(parseTileCacheKey("street_3_1"))
        assertNull(parseTileCacheKey("street_3_1_2_extra"))
        assertNull(parseTileCacheKey("street_x_1_2"))
        assertNull(parseTileCacheKey("unknown_3_1_2"))
    }
}
