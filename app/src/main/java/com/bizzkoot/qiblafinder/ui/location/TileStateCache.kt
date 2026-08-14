package com.bizzkoot.qiblafinder.ui.location

import kotlin.math.ceil
import kotlin.math.floor

/**
 * Bounded in-memory tile-state cache helpers (PRD M5).
 *
 * OpenStreetMapView previously kept an unbounded `Map<String, TileLoadState>` that only
 * reset on map-type change and was iterated in full on every frame. These helpers keep
 * the in-memory tile-state set bounded (hard cap + viewport pruning) so memory stays at
 * roughly one viewport and the per-frame draw pass only touches on-screen tiles.
 *
 * Design note: the helpers operate on immutable `Map` snapshots rather than a mutable
 * class because OpenStreetMapView holds the cache in a Compose `mutableStateOf<Map<...>>`
 * — replacing the map on every write keeps recomposition and the keyed `LaunchedEffect`s
 * working exactly as before, with no state-tracking redesign.
 *
 * The on-disk LRU tile cache in OpenStreetMapTileManager is unaffected: an in-memory
 * entry that gets evicted simply re-loads from disk when the tile is revisited.
 */

/** Hard cap for the in-memory tile-state cache (a viewport is ~12-15 tiles + buffer). */
const val TILE_STATE_CACHE_MAX_SIZE = 60

/**
 * Returns a NEW map with [key] -> [state] inserted (or refreshed), bounded to [maxSize]
 * entries. Insertion order tracks recency: putting an existing key moves it to the most
 * recent position. When over the cap, entries that are NOT in [visibleKeys] are evicted
 * first (oldest first) so tiles intersecting the current viewport survive eviction; if
 * the visible set itself exceeds the cap, the oldest entries are dropped.
 */
fun putTileState(
    cache: Map<String, TileLoadState>,
    key: String,
    state: TileLoadState,
    visibleKeys: Set<String> = emptySet(),
    maxSize: Int = TILE_STATE_CACHE_MAX_SIZE
): Map<String, TileLoadState> {
    val updated = LinkedHashMap<String, TileLoadState>(cache.size + 1)
    for ((existingKey, existingState) in cache) {
        if (existingKey != key) updated[existingKey] = existingState
    }
    updated[key] = state

    if (updated.size <= maxSize) return updated

    // Prefer keeping tiles that intersect the current viewport: evict oldest
    // non-visible entries before touching visible ones.
    val nonVisibleKeys = updated.keys.filter { it !in visibleKeys }
    for (keyToEvict in nonVisibleKeys) {
        if (updated.size <= maxSize) break
        updated.remove(keyToEvict)
    }
    while (updated.size > maxSize) {
        updated.remove(updated.keys.first())
    }
    return updated
}

/**
 * Tile cache keys (same zoom + mapType as the viewport center) whose tiles lie within a
 * viewport of [viewportWidth] x [viewportHeight] pixels centered on the fractional tile
 * coordinates [centerTileX]/[centerTileY]. [margin] adds extra whole-tile rings so tiles
 * that only partially intersect the canvas (and a small pan-ahead ring) are retained.
 */
fun visibleTileKeys(
    centerTileX: Double,
    centerTileY: Double,
    zoom: Int,
    viewportWidth: Int,
    viewportHeight: Int,
    mapType: MapType,
    margin: Int = 1
): Set<String> {
    val tilesAcross = ceil(viewportWidth / TILE_SIZE).toInt() + margin
    val tilesDown = ceil(viewportHeight / TILE_SIZE).toInt() + margin

    val startX = floor(centerTileX - tilesAcross / 2.0).toInt()
    val endX = startX + tilesAcross
    val startY = floor(centerTileY - tilesDown / 2.0).toInt()
    val endY = startY + tilesDown

    val keys = HashSet<String>(tilesAcross * tilesDown)
    for (x in startX..endX) {
        for (y in startY..endY) {
            keys.add(TileCoordinate(x, y, zoom, mapType).toCacheKey())
        }
    }
    return keys
}

/**
 * Returns a NEW map keeping only entries whose tiles intersect the current viewport
 * (see [visibleTileKeys]). Cheap no-op for an empty cache.
 */
fun pruneToViewport(
    cache: Map<String, TileLoadState>,
    centerTileX: Double,
    centerTileY: Double,
    zoom: Int,
    viewportWidth: Int,
    viewportHeight: Int,
    mapType: MapType,
    margin: Int = 1
): Map<String, TileLoadState> {
    if (cache.isEmpty()) return cache
    val visible = visibleTileKeys(
        centerTileX, centerTileY, zoom, viewportWidth, viewportHeight, mapType, margin
    )
    return cache.filterKeys { it in visible }
}
