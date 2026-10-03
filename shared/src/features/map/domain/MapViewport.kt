package features.map.domain

import androidx.compose.ui.geometry.Offset
import core.geometry.BoundingBox
import core.geometry.GeoPoint
import core.geometry.WebMercatorProjection
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

data class MapViewport(
    val center: GeoPoint = GeoPoint.KRAKOW_CENTER,
    val zoom: Double = 14.0,
    val screenWidth: Float = 800f,
    val screenHeight: Float = 600f
) {
    val integerZoom: Int
        get() = zoom.toInt().coerceIn(0, 19)

    val zoomFraction: Double
        get() = zoom - integerZoom

    fun visibleBounds(): BoundingBox {
        if (screenWidth <= 0f || screenHeight <= 0f) {
            return BoundingBox.KRAKOW_AREA
        }
        val topLeft = WebMercatorProjection.screenToGeo(Offset(0f, 0f), center, zoom, screenWidth, screenHeight)
        val topRight = WebMercatorProjection.screenToGeo(Offset(screenWidth, 0f), center, zoom, screenWidth, screenHeight)
        val bottomLeft = WebMercatorProjection.screenToGeo(Offset(0f, screenHeight), center, zoom, screenWidth, screenHeight)
        val bottomRight = WebMercatorProjection.screenToGeo(Offset(screenWidth, screenHeight), center, zoom, screenWidth, screenHeight)

        val north = max(max(topLeft.latitude, topRight.latitude), max(bottomLeft.latitude, bottomRight.latitude))
        val south = min(min(topLeft.latitude, topRight.latitude), min(bottomLeft.latitude, bottomRight.latitude))
        val west = min(min(topLeft.longitude, topRight.longitude), min(bottomLeft.longitude, bottomRight.longitude))
        val east = max(max(topLeft.longitude, topRight.longitude), max(bottomLeft.longitude, bottomRight.longitude))

        return BoundingBox(north = north, south = south, east = east, west = west)
    }

    fun visibleTileCoordinates(targetZoom: Int = integerZoom): List<TileCoordinate> {
        if (screenWidth <= 0f || screenHeight <= 0f) return emptyList()

        val z = targetZoom.coerceIn(0, 19)
        val numTiles = 1 shl z
        val scale = WebMercatorProjection.worldPixelSize(zoom)

        val centerWorldX = WebMercatorProjection.toWorldX(center.longitude)
        val centerWorldY = WebMercatorProjection.toWorldY(center.latitude)

        val minWorldX = centerWorldX - (screenWidth / 2f) / scale
        val maxWorldX = centerWorldX + (screenWidth / 2f) / scale
        val minWorldY = centerWorldY - (screenHeight / 2f) / scale
        val maxWorldY = centerWorldY + (screenHeight / 2f) / scale

        val minTileX = floor(minWorldX * numTiles).toInt().coerceIn(0, numTiles - 1)
        val maxTileX = floor(maxWorldX * numTiles).toInt().coerceIn(0, numTiles - 1)
        val minTileY = floor(minWorldY * numTiles).toInt().coerceIn(0, numTiles - 1)
        val maxTileY = floor(maxWorldY * numTiles).toInt().coerceIn(0, numTiles - 1)

        val result = mutableListOf<TileCoordinate>()
        for (y in minTileY..maxTileY) {
            for (x in minTileX..maxTileX) {
                result.add(TileCoordinate(x, y, z))
            }
        }
        return result
    }

    fun withCenter(newCenter: GeoPoint): MapViewport = copy(center = newCenter)

    fun withZoom(newZoom: Double): MapViewport = copy(zoom = newZoom.coerceIn(2.0, 19.0))

    fun withSize(width: Float, height: Float): MapViewport = copy(screenWidth = width, screenHeight = height)
}
