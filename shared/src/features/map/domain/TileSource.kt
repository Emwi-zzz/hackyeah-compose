package features.map.domain

data class TileSource(
    val id: String,
    val name: String,
    val description: String,
    val attribution: String,
    val urlTemplate: String,
    val minZoom: Int = 0,
    val maxZoom: Int = 19
) {
    fun getTileUrl(coord: TileCoordinate): String {
        return urlTemplate
            .replace("{z}", coord.zoom.toString())
            .replace("{x}", coord.x.toString())
            .replace("{y}", coord.y.toString())
    }
}
