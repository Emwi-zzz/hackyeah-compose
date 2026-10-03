package core.geometry

import androidx.compose.ui.geometry.Offset
import kotlin.math.*

object WebMercatorProjection {
    private const val MIN_LAT = -85.05112878
    private const val MAX_LAT = 85.05112878
    const val TILE_SIZE = 256.0
    private const val EARTH_CIRCUMFERENCE_METERS = 40075016.686

    fun toWorldX(longitude: Double): Double {
        return (longitude + 180.0) / 360.0
    }

    fun toWorldY(latitude: Double): Double {
        val clampedLat = latitude.coerceIn(MIN_LAT, MAX_LAT)
        val latRad = clampedLat * PI / 180.0
        val sinLat = sin(latRad)
        return 0.5 - ln((1.0 + sinLat) / (1.0 - sinLat)) / (4.0 * PI)
    }

    fun toGeoLongitude(worldX: Double): Double {
        return worldX * 360.0 - 180.0
    }

    fun toGeoLatitude(worldY: Double): Double {
        val latRad = 2.0 * atan(exp(PI * (1.0 - 2.0 * worldY))) - (PI / 2.0)
        return (latRad * 180.0 / PI).coerceIn(MIN_LAT, MAX_LAT)
    }

    fun worldPixelSize(zoom: Double): Double {
        return TILE_SIZE * 2.0.pow(zoom)
    }

    fun geoToScreen(
        geo: GeoPoint,
        center: GeoPoint,
        zoom: Double,
        screenWidth: Float,
        screenHeight: Float
    ): Offset {
        val scale = worldPixelSize(zoom)
        val centerWorldX = toWorldX(center.longitude)
        val centerWorldY = toWorldY(center.latitude)
        val ptWorldX = toWorldX(geo.longitude)
        val ptWorldY = toWorldY(geo.latitude)

        val screenX = (screenWidth / 2f) + ((ptWorldX - centerWorldX) * scale).toFloat()
        val screenY = (screenHeight / 2f) + ((ptWorldY - centerWorldY) * scale).toFloat()
        return Offset(screenX, screenY)
    }

    fun screenToGeo(
        screenOffset: Offset,
        center: GeoPoint,
        zoom: Double,
        screenWidth: Float,
        screenHeight: Float
    ): GeoPoint {
        val scale = worldPixelSize(zoom)
        val centerWorldX = toWorldX(center.longitude)
        val centerWorldY = toWorldY(center.latitude)

        val ptWorldX = centerWorldX + (screenOffset.x - (screenWidth / 2f)) / scale
        val ptWorldY = centerWorldY + (screenOffset.y - (screenHeight / 2f)) / scale

        return GeoPoint(
            latitude = toGeoLatitude(ptWorldY),
            longitude = toGeoLongitude(ptWorldX)
        )
    }

    fun metersPerPixel(latitude: Double, zoom: Double): Double {
        val latRad = latitude.coerceIn(MIN_LAT, MAX_LAT) * PI / 180.0
        return (EARTH_CIRCUMFERENCE_METERS * cos(latRad)) / worldPixelSize(zoom)
    }

    fun pixelsPerMeter(latitude: Double, zoom: Double): Double {
        val mpp = metersPerPixel(latitude, zoom)
        return if (mpp > 0.0) 1.0 / mpp else 0.0
    }
}
