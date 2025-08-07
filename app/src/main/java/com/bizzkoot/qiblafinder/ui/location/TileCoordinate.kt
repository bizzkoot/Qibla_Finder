package com.bizzkoot.qiblafinder.ui.location

data class TileCoordinate(
    val x: Int,
    val y: Int,
    val zoom: Int,
    val mapType: MapType = MapType.STREET
) {
    fun toFileName(): String = "tile_${mapType.name.lowercase()}_${zoom}_${x}_${y}.png"

    fun toCacheKey(): String = "${mapType.name.lowercase()}_${zoom}_${x}_${y}"
}
