package features.map.domain

data class TileCoordinate(
    val x: Int,
    val y: Int,
    val zoom: Int
) {
    val key: String get() = "$zoom/$x/$y"

    val parent: TileCoordinate?
        get() = if (zoom > 0) TileCoordinate(x / 2, y / 2, zoom - 1) else null
}
