# Progressive Rendering Implementation Guide

## 1. Objective

To improve the perceived performance of the map screen by implementing progressive rendering. Instead of showing a loading indicator while high-resolution map tiles are downloaded, the app will immediately display a low-resolution, blurry version of the map. This low-resolution placeholder will then be replaced by the sharp, high-resolution tiles as they become available.

This enhancement will make the map feel significantly faster and more responsive to the user, especially on slower network connections.

## 2. High-Level Strategy

The implementation is self-contained within the manual location feature and will not affect any other part of the application. The strategy involves two main areas of change:

1.  **Data Layer (`OpenStreetMapTileManager.kt`):** The tile manager will be updated to orchestrate the loading of both low-resolution and high-resolution tiles. It will be responsible for fetching the low-resolution tile first, then the high-resolution tile, and communicating the state of this process to the UI layer.

2.  **UI Layer (`OpenStreetMapView.kt`):** The map view will be updated to consume the new tile loading states. It will need to manage a more complex cache and update its drawing logic to first draw low-resolution tiles (scaled up) and then redraw itself when the high-resolution tiles arrive.

## 3. Detailed Implementation Steps

### Part 1: Modifying `OpenStreetMapTileManager.kt`

The goal here is to create a new public function that handles the entire progressive loading sequence and communicates its state via a Kotlin `Flow`.

#### Step 1.1: Create a Parent Tile Calculation Function

First, we need a helper function to find the corresponding low-resolution tile for a given high-resolution tile.

```kotlin
// In OpenStreetMapTileManager.kt

/**
 * Calculates the parent tile at a lower zoom level.
 * @param tile The original TileCoordinate.
 * @param zoomOffset How many zoom levels to go up (e.g., 2 means zoom-2).
 * @return The parent TileCoordinate, or null if the resulting zoom is invalid.
 */
fun getLowerResolutionTile(tile: TileCoordinate, zoomOffset: Int = 2): TileCoordinate? {
    val lowerZoom = tile.zoom - zoomOffset
    if (lowerZoom < MIN_ZOOM_LEVEL) return null // Or your minimum supported zoom
    
    val scale = 2.0.pow(zoomOffset)
    val lowerX = floor(tile.x / scale).toInt()
    val lowerY = floor(tile.y / scale).toInt()
    
    return TileCoordinate(lowerX, lowerY, lowerZoom, tile.mapType)
}
```

#### Step 1.2: Define a State Holder for Tile Loading

To communicate the loading status, we'll use a sealed class.

```kotlin
// Can be defined in OpenStreetMapTileManager.kt or a separate file
sealed class TileLoadState {
    object Loading : TileLoadState()
    data class LowRes(val bitmap: Bitmap) : TileLoadState()
    data class HighRes(val bitmap: Bitmap) : TileLoadState()
    data class Failed(val error: String) : TileLoadState()
}
```

#### Step 1.3: Implement the Progressive Loading Flow

This is the core logic change. We will create a new public function that returns a `Flow` of `TileLoadState`.

```kotlin
// In OpenStreetMapTileManager.kt

/**
 * Loads a tile progressively, emitting low-resolution and then high-resolution bitmaps.
 * @param tile The target high-resolution tile to load.
 * @return A Flow that emits the various states of the tile loading process.
 */
fun loadTileProgressively(tile: TileCoordinate): Flow<TileLoadState> = flow {
    emit(TileLoadState.Loading)

    // Coroutine scope for concurrent downloads
    coroutineScope {
        // Launch high-resolution download in the background
        val highResDeferred = async {
            getTileBitmap(tile) // Your existing function to get a tile from cache or download
        }

        // Attempt to load low-resolution tile first
        val lowerResTile = getLowerResolutionTile(tile)
        if (lowerResTile != null) {
            val lowResBitmap = getTileBitmap(lowerResTile)
            if (lowResBitmap != null) {
                emit(TileLoadState.LowRes(lowResBitmap))
            }
        }

        // Await the high-resolution result
        val highResBitmap = highResDeferred.await()
        if (highResBitmap != null) {
            emit(TileLoadState.HighRes(highResBitmap))
        } else {
            // Only emit Failed if we haven't already provided a LowRes version
            // This prevents the UI from showing an error if a placeholder is already visible
            val currentState = this@flow.lastOrNull()
            if (currentState !is TileLoadState.LowRes) {
                emit(TileLoadState.Failed("Failed to load high-res tile."))
            }
        }
    }
}.catch { e ->
    emit(TileLoadState.Failed(e.message ?: "Unknown error"))
}.flowOn(Dispatchers.IO)
```

### Part 2: Updating `OpenStreetMapView.kt`

The UI needs to be updated to use the new flow-based loading mechanism and render the different states.

#### Step 2.1: Modify the Tile Cache

The cache will now store the state of each tile, not just the final bitmap.

```kotlin
// In OpenStreetMapView.kt

// The cache will hold the latest state for each tile's cache key
var tileStateCache by remember(mapType) { 
    mutableStateOf<Map<String, TileLoadState>>(emptyMap()) 
}
```

#### Step 2.2: Update the Tile Loading Logic

The `LaunchedEffect` that loads tiles will be updated to call `loadTileProgressively` and collect the `Flow` for each required tile.

```kotlin
// In OpenStreetMapView.kt, inside the main LaunchedEffect for tile loading

// ... inside LaunchedEffect(tileX, tileY, zoom, mapType) ...

// This logic replaces the previous call to batchDownloadTiles
val requiredTiles = tileManager.getTilesForViewWithPriority(...) // Get the list of tiles

requiredTiles.forEach { tile ->
    // Only launch a new loading flow if the tile isn't already loaded or loading
    val cacheKey = tile.toCacheKey()
    if (tileStateCache[cacheKey] !is TileLoadState.HighRes) {
        launch {
            tileManager.loadTileProgressively(tile).collect { state ->
                // Update the cache with the latest state for the tile
                tileStateCache = tileStateCache + (cacheKey to state)
            }
        }
    }
}
```

#### Step 2.3: Adjust the Canvas Drawing Logic

The `Canvas` `onDraw` block must be updated to handle the `TileLoadState`.

```kotlin
// In OpenStreetMapView.kt, inside the Canvas onDraw scope

tileStateCache.forEach { (cacheKey, state) ->
    val tile = parseTileCacheKey(cacheKey)
    if (tile != null && tile.mapType == mapType) {
        val drawX = ... // Calculate draw position as before
        val drawY = ... // Calculate draw position as before

        when (state) {
            is TileLoadState.Loading -> {
                // Optional: Draw a placeholder rectangle
                drawRect(
                    color = Color.LightGray,
                    topLeft = Offset(drawX, drawY),
                    size = Size(TILE_SIZE, TILE_SIZE)
                )
            }
            is TileLoadState.LowRes -> {
                // Draw the low-res bitmap, it will be scaled up by the canvas transform
                drawImage(state.bitmap.asImageBitmap(), topLeft = Offset(drawX, drawY))
            }
            is TileLoadState.HighRes -> {
                // Draw the final, sharp high-res bitmap
                drawImage(state.bitmap.asImageBitmap(), topLeft = Offset(drawX, drawY))
            }
            is TileLoadState.Failed -> {
                // Optional: Draw an error indicator
                drawRect(
                    color = Color.Gray,
                    topLeft = Offset(drawX, drawY),
                    size = Size(TILE_SIZE, TILE_SIZE)
                )
                // You could also draw an 'X' or other indicator
            }
        }
    }
}
```

## 4. Safety Analysis and Verification

### Non-Breaking Changes

This implementation is designed to be safe and isolated:
- **Encapsulation:** All changes are confined to `OpenStreetMapTileManager.kt` and `OpenStreetMapView.kt`. No other modules or features of the app are affected.
- **No API Change:** The external dependencies and contracts of the map feature remain the same. It still uses the same tile provider URLs.
- **Graceful Degradation:** In the event of an error during loading, the implementation can fall back to showing a placeholder or simply not rendering the tile, which is similar to the current behavior of a failed download. The core functionality of selecting a location is not impacted.

### Verification Plan

To ensure the implementation is working correctly and is stable:
1.  **Visual Correctness:**
    -   Manually pan and zoom the map on both fast and slow network conditions (using the Android Emulator's network throttling).
    -   **Check for:** The initial appearance of blurry tiles, followed by them becoming sharp.
    -   **Verify:** There is no flickering or visual tearing during the tile replacement process.
2.  **Performance:**
    -   Measure the time from when the `ManualLocationScreen` is opened to when the map is first rendered. This time should be significantly shorter.
    -   Monitor memory usage to ensure the new caching strategy does not introduce memory leaks.
3.  **Edge Cases:**
    -   Test with rapid panning and zooming to ensure the tile loading and cancellation logic is robust.
    -   Test what happens when a tile fails to load (e.g., by disconnecting the network mid-load). The app should handle this gracefully.
    -   Switch between map types (Street and Satellite) to ensure the caches are managed correctly and the progressive rendering works for both.
