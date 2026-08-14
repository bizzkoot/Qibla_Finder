package com.bizzkoot.qiblafinder.ui.location

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * PRD M10: tile-manager math — Web-Mercator conversions, meters-to-pixels, lower-res
 * tiles, and viewport/buffer partitioning. Robolectric is used only for the Context
 * needed to construct the manager; no network or disk I/O is exercised.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class OpenStreetMapTileManagerTest {

    private lateinit var tileManager: OpenStreetMapTileManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        tileManager = OpenStreetMapTileManager(context, 100)
    }

    @Test
    fun `latLngToTile maps the origin to the center tile at zoom 1`() {
        assertEquals(
            TileCoordinate(1, 1, 1, MapType.STREET),
            tileManager.latLngToTile(0.0, 0.0, 1, MapType.STREET)
        )
    }

    @Test
    fun `latLngToTile stays within the tile grid`() {
        val tile = tileManager.latLngToTile(21.422487, 39.826206, 18, MapType.STREET)
        val gridSize = 1 shl 18
        assertTrue("x=${tile.x} out of range", tile.x in 0 until gridSize)
        assertTrue("y=${tile.y} out of range", tile.y in 0 until gridSize)
        assertEquals(18, tile.zoom)
    }

    @Test
    fun `tileToLatLngBounds keeps west below east and north above south`() {
        val bounds = tileManager.tileToLatLngBounds(TileCoordinate(1, 1, 1, MapType.STREET))
        val north = bounds.first.first
        val south = bounds.first.second
        val west = bounds.second.first
        val east = bounds.second.second

        assertTrue(west < east)
        assertTrue(north > south)
        // Zoom-1 center tile: x=1 -> lng [0, 180), y=1 -> lat [0, ~-85.05).
        assertEquals(0.0, west, 1e-9)
        assertEquals(180.0, east, 1e-9)
        assertEquals(0.0, north, 1e-9)
        assertEquals(-85.05112877980659, south, 1e-9)
    }

    @Test
    fun `latLngToTileXY and tileXYToLatLng round-trip`() {
        val (x, y) = tileManager.latLngToTileXY(21.4225, 39.8262, 10)
        val (lat, lng) = tileManager.tileXYToLatLng(x, y, 10)
        assertEquals(21.4225, lat, 1e-6)
        assertEquals(39.8262, lng, 1e-6)
    }

    @Test
    fun `metersToPixels grows with zoom and with distance`() {
        assertTrue(tileManager.metersToPixels(100f, 11) > tileManager.metersToPixels(100f, 10))
        assertTrue(tileManager.metersToPixels(1000f, 10) > tileManager.metersToPixels(100f, 10))
    }

    @Test
    fun `getLowerResolutionTile drops zoom by the offset`() {
        assertEquals(
            TileCoordinate(25, 25, 8, MapType.STREET),
            tileManager.getLowerResolutionTile(TileCoordinate(100, 100, 10, MapType.STREET))
        )
        assertEquals(
            TileCoordinate(25, 50, 8, MapType.SATELLITE),
            tileManager.getLowerResolutionTile(TileCoordinate(100, 200, 10, MapType.SATELLITE))
        )
    }

    @Test
    fun `getLowerResolutionTile returns null below the minimum zoom`() {
        assertNull(tileManager.getLowerResolutionTile(TileCoordinate(0, 0, 2, MapType.STREET)))
        assertNull(tileManager.getLowerResolutionTile(TileCoordinate(0, 0, 3, MapType.STREET)))
    }

    @Test
    fun `getTilesForViewWithPriority partitions visible and buffer tiles`() {
        val (visible, buffer) =
            tileManager.getTilesForViewWithPriority(10.5, 10.5, 10, 800, 800, MapType.STREET, 0.4)

        // The two lists must not overlap.
        assertTrue("visible and buffer must not overlap", visible.intersect(buffer).isEmpty())

        // All tiles share the requested zoom and map type.
        assertTrue(visible.all { it.zoom == 10 && it.mapType == MapType.STREET })
        assertTrue(buffer.all { it.zoom == 10 && it.mapType == MapType.STREET })

        // 800px at 256px/tile -> ceil(3.125) + 2 = 6 tile columns per axis; the visible
        // range spans 7 tile positions per axis (49 total) and the buffered range spans
        // 9 (81 total), leaving 32 buffer tiles.
        assertEquals(49, visible.size)
        assertEquals(32, buffer.size)

        // The center tile is part of the visible set.
        assertTrue(visible.contains(TileCoordinate(10, 10, 10, MapType.STREET)))
    }

    @Test
    fun `getTilesForViewWithPriority with zero buffer returns only visible tiles`() {
        val (visible, buffer) =
            tileManager.getTilesForViewWithPriority(10.5, 10.5, 10, 800, 800, MapType.STREET, 0.0)

        assertEquals(49, visible.size)
        assertTrue(buffer.isEmpty())
    }
}
