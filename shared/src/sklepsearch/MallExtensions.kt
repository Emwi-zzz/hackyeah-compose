package sklepsearch

import core.geometry.BoundingBox

fun Mall.getBoundingBox(): BoundingBox {
    val north = maxOf(upperLeft.latitude, downRight.latitude)
    val south = minOf(upperLeft.latitude, downRight.latitude)
    val east = maxOf(upperLeft.longitude, downRight.longitude)
    val west = minOf(upperLeft.longitude, downRight.longitude)
    return BoundingBox(north = north, south = south, east = east, west = west)
}

fun Mall.pointToGeo(point: Point): core.geometry.GeoPoint {
    val u = (point.x / size.x).coerceIn(0.0, 1.0)
    val v = (point.y / size.y).coerceIn(0.0, 1.0)

    val lat = upperLeft.latitude + v * (downRight.latitude - upperLeft.latitude)
    val lon = upperLeft.longitude + u * (downRight.longitude - upperLeft.longitude)
    return core.geometry.GeoPoint(lat, lon)
}

fun Mall.geoToPoint(geo: core.geometry.GeoPoint): Point {
    val latSpan = downRight.latitude - upperLeft.latitude
    val lonSpan = downRight.longitude - upperLeft.longitude

    val u = if (lonSpan != 0.0) (geo.longitude - upperLeft.longitude) / lonSpan else 0.0
    val v = if (latSpan != 0.0) (geo.latitude - upperLeft.latitude) / latSpan else 0.0

    return Point(u * size.x, v * size.y)
}
