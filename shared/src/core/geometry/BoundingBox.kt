package core.geometry

data class BoundingBox(
    val north: Double,
    val south: Double,
    val east: Double,
    val west: Double
) {
    fun contains(point: GeoPoint): Boolean {
        return point.latitude in south..north && point.longitude in west..east
    }

    val center: GeoPoint
        get() = GeoPoint((north + south) / 2.0, (east + west) / 2.0)

    val latSpan: Double
        get() = north - south

    val lonSpan: Double
        get() = east - west

    companion object {
        // Krakow metropolitan area bounding box
        val KRAKOW_AREA = BoundingBox(
            north = 50.1500,
            south = 49.9500,
            east = 20.2200,
            west = 19.7500
        )
    }
}
