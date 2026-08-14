package com.bizzkoot.qiblafinder.ui.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PRD M5: the in-memory tile-state cache must stay bounded (hard cap) and prefer keeping
 * tiles that intersect the current viewport. Plain JVM tests — the helpers in
 * TileStateCache.kt have no Android/Compose dependencies by design.
 */
class TileStateCacheTest {

    private fun key(x: Int, y: Int, zoom: Int = 10, mapType: MapType = MapType.STREET): String =
        TileCoordinate(x, y, zoom, mapType).toCacheKey()

    @Test
    fun `put enforces the hard cap - 70 inserts keep at most 60 entries`() {
        var cache: Map<String, TileLoadState> = emptyMap()
        repeat(70) { i ->
            cache = putTileState(cache, key(i, i), TileLoadState.Loading)
        }
        assertEquals(TILE_STATE_CACHE_MAX_SIZE, cache.size)
    }

    @Test
    fun `put with a custom maxSize respects it`() {
        var cache: Map<String, TileLoadState> = emptyMap()
        repeat(10) { i ->
            cache = putTileState(cache, key(i, i), TileLoadState.Loading, maxSize = 3)
        }
        assertEquals(3, cache.size)
    }

    @Test
    fun `re-putting an existing key refreshes its recency so it is evicted last`() {
        var cache: Map<String, TileLoadState> = emptyMap()
        cache = putTileState(cache, key(0, 0), TileLoadState.Loading) // oldest
        cache = putTileState(cache, key(1, 1), TileLoadState.Loading)
        cache = putTileState(cache, key(2, 2), TileLoadState.Loading)
        // Refresh the oldest key: it moves to the most-recent slot.
        cache = putTileState(cache, key(0, 0), TileLoadState.Loading)

        // maxSize=3 -> the next insert evicts the oldest remaining entry: (1,1).
        cache = putTileState(cache, key(3, 3), TileLoadState.Loading, maxSize = 3)

        assertEquals(setOf(key(2, 2), key(0, 0), key(3, 3)), cache.keys)
    }

    @Test
    fun `eviction drops oldest non-visible tiles before visible ones`() {
        var cache: Map<String, TileLoadState> = emptyMap()
        // Three old entries, all far from the viewport.
        cache = putTileState(cache, key(100, 100), TileLoadState.Loading)
        cache = putTileState(cache, key(101, 101), TileLoadState.Loading)
        cache = putTileState(cache, key(102, 102), TileLoadState.Loading)

        // Inserting a visible entry over the cap evicts the oldest NON-visible one.
        val visible = setOf(key(10, 10))
        cache = putTileState(cache, key(10, 10), TileLoadState.Loading, visibleKeys = visible, maxSize = 3)

        assertEquals(3, cache.size)
        assertTrue(cache.containsKey(key(10, 10)))
        assertFalse(cache.containsKey(key(100, 100)))
        assertTrue(cache.containsKey(key(101, 101)))
        assertTrue(cache.containsKey(key(102, 102)))
    }

    @Test
    fun `visibleKeys larger than the cap evicts the oldest visible entry too`() {
        var cache: Map<String, TileLoadState> = emptyMap()
        cache = putTileState(cache, key(10, 10), TileLoadState.Loading)
        cache = putTileState(cache, key(11, 11), TileLoadState.Loading)
        cache = putTileState(cache, key(12, 12), TileLoadState.Loading)

        // maxSize=2 with all three keys "visible": oldest (10,10) must go.
        val visible = setOf(key(10, 10), key(11, 11), key(12, 12))
        cache = putTileState(cache, key(12, 12), TileLoadState.Failed("x"), visibleKeys = visible, maxSize = 2)

        assertEquals(setOf(key(11, 11), key(12, 12)), cache.keys)
    }

    @Test
    fun `pruneToViewport keeps visible tiles and drops far-away ones`() {
        val cache = mapOf(
            key(10, 10) to TileLoadState.Loading,    // center
            key(11, 10) to TileLoadState.Loading,    // adjacent (visible at 800x800)
            key(50, 50) to TileLoadState.Failed("x") // far away
        )
        val pruned = pruneToViewport(cache, 10.5, 10.5, 10, 800, 800, MapType.STREET)

        assertTrue(pruned.containsKey(key(10, 10)))
        assertTrue(pruned.containsKey(key(11, 10)))
        assertFalse(pruned.containsKey(key(50, 50)))
    }

    @Test
    fun `pruneToViewport on an empty cache is a no-op`() {
        val pruned = pruneToViewport(emptyMap(), 10.5, 10.5, 10, 800, 800, MapType.STREET)
        assertTrue(pruned.isEmpty())
    }

    @Test
    fun `visibleTileKeys covers the center tile and neighbours at 800x800`() {
        val keys = visibleTileKeys(10.5, 10.5, 10, 800, 800, MapType.STREET)

        assertTrue(keys.contains(key(10, 10)))
        assertTrue(keys.contains(key(11, 11)))
        // 800px / 256px = 3.125 -> ceil 4 + 1 margin = 5 columns, +1 for the inclusive
        // end: 6 tile positions per axis -> 36 keys.
        assertEquals(36, keys.size)
        assertTrue(keys.all { parseTileCacheKey(it)?.zoom == 10 })
        assertTrue(keys.all { parseTileCacheKey(it)?.mapType == MapType.STREET })
    }

    @Test
    fun `visibleTileKeys includes tiles partially visible at the canvas edge`() {
        // Center exactly on an integer tile: the canvas extends half a tile beyond on
        // each side, so the edge tiles must still be retained.
        val keys = visibleTileKeys(10.0, 10.0, 10, 256, 256, MapType.STREET)

        assertTrue(keys.contains(key(9, 9)))
        assertTrue(keys.contains(key(10, 10)))
        assertTrue(keys.contains(key(11, 11)))
        assertEquals(9, keys.size)
    }

    @Test
    fun `visibleTileKeys differentiates map types`() {
        val street = visibleTileKeys(10.5, 10.5, 10, 800, 800, MapType.STREET)
        val satellite = visibleTileKeys(10.5, 10.5, 10, 800, 800, MapType.SATELLITE)

        assertTrue(street.contains(key(10, 10)))
        assertTrue(satellite.contains(key(10, 10, mapType = MapType.SATELLITE)))
        assertFalse(street.contains(key(10, 10, mapType = MapType.SATELLITE)))
        assertFalse(satellite.contains(key(10, 10)))
    }
}
