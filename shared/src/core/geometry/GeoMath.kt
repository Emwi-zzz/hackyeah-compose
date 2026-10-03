package core.geometry

import kotlin.math.*

object GeoMath {
    private const val EARTH_RADIUS_METERS = 6371000.0

    /**
     * Computes the great-circle distance between two points using the Haversine formula.
     */
    fun haversineDistanceMeters(p1: GeoPoint, p2: GeoPoint): Double {
        val lat1Rad = p1.latitude * PI / 180.0
        val lat2Rad = p2.latitude * PI / 180.0
        val deltaLat = (p2.latitude - p1.latitude) * PI / 180.0
        val deltaLon = (p2.longitude - p1.longitude) * PI / 180.0

        val a = sin(deltaLat / 2.0).pow(2.0) +
                cos(lat1Rad) * cos(lat2Rad) * sin(deltaLon / 2.0).pow(2.0)
        val c = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
        return EARTH_RADIUS_METERS * c
    }

    /**
     * Formats a distance in meters into human-readable string (e.g. "350 m" or "1.82 km").
     */
    fun formatDistance(meters: Double): String {
        return if (meters < 1000.0) {
            "${meters.roundToInt()} m"
        } else {
            val km = meters / 1000.0
            val formatted = ((km * 100.0).roundToInt()) / 100.0
            "$formatted km"
        }
    }

    /**
     * Computes total distance of a polyline in meters.
     */
    fun totalPathDistance(points: List<GeoPoint>): Double {
        if (points.size < 2) return 0.0
        var total = 0.0
        for (i in 0 until points.size - 1) {
            total += haversineDistanceMeters(points[i], points[i + 1])
        }
        return total
    }

    /**
     * Computes the approximate surface area of a spherical polygon in square meters.
     */
    fun polygonAreaSquareMeters(points: List<GeoPoint>): Double {
        if (points.size < 3) return 0.0
        var total = 0.0
        val n = points.size

        for (i in 0 until n) {
            val p1 = points[i]
            val p2 = points[(i + 1) % n]
            val lon1Rad = p1.longitude * PI / 180.0
            val lon2Rad = p2.longitude * PI / 180.0
            val lat1Rad = p1.latitude * PI / 180.0
            val lat2Rad = p2.latitude * PI / 180.0

            total += (lon2Rad - lon1Rad) * (2.0 + sin(lat1Rad) + sin(lat2Rad))
        }

        val area = abs(total * EARTH_RADIUS_METERS * EARTH_RADIUS_METERS / 2.0)
        return area
    }

    fun formatArea(sqMeters: Double): String {
        return if (sqMeters < 10000.0) {
            "${sqMeters.roundToInt()} m²"
        } else if (sqMeters < 1_000_000.0) {
            val ha = sqMeters / 10000.0
            val formatted = ((ha * 100.0).roundToInt()) / 100.0
            "$formatted ha"
        } else {
            val sqKm = sqMeters / 1_000_000.0
            val formatted = ((sqKm * 100.0).roundToInt()) / 100.0
            "$formatted km²"
        }
    }
}
