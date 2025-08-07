package com.bizzkoot.qiblafinder.ui.location

interface TileUrlProvider {
    fun getTileUrl(tile: TileCoordinate): String
    fun getMaxZoom(): Int
    fun getMinZoom(): Int
    fun requiresApiKey(): Boolean = false
    fun getAttribution(): String
}

class OpenStreetMapUrlProvider : TileUrlProvider {
    override fun getTileUrl(tile: TileCoordinate): String =
        "https://tile.openstreetmap.org/${tile.zoom}/${tile.x}/${tile.y}.png"

    override fun getMaxZoom(): Int = 19
    override fun getMinZoom(): Int = 0
    override fun getAttribution(): String = "© OpenStreetMap contributors"
}

class EsriSatelliteUrlProvider : TileUrlProvider {
    override fun getTileUrl(tile: TileCoordinate): String =
        "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/${tile.zoom}/${tile.y}/${tile.x}"

    override fun getMaxZoom(): Int = 19
    override fun getMinZoom(): Int = 0
    override fun getAttribution(): String = "© Esri, Maxar, Earthstar Geographics"
}

class BingSatelliteUrlProvider : TileUrlProvider {
    override fun getTileUrl(tile: TileCoordinate): String =
        "https://ecn.t3.tiles.virtualearth.net/tiles/a${getQuadKey(tile)}.jpeg?g=1"

    override fun getMaxZoom(): Int = 19
    override fun getMinZoom(): Int = 1
    override fun getAttribution(): String = "© Microsoft Corporation"

    private fun getQuadKey(tile: TileCoordinate): String {
        val quadKey = StringBuilder()
        for (i in tile.zoom downTo 1) {
            var digit = 0
            val mask = 1 shl (i - 1)
            if ((tile.x and mask) != 0) {
                digit = digit or 1
            }
            if ((tile.y and mask) != 0) {
                digit = digit or 2
            }
            quadKey.append(digit)
        }
        return quadKey.toString()
    }
}
