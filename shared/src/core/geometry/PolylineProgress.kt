package core.geometry

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot

/** Where a position lies relative to a route: how far from the line and how far along it. */
data class PolylineProgress(val offRouteMeters: Double, val alongMeters: Double, val totalMeters: Double)

object PolylineMath {
    private const val METERS_PER_DEGREE = 111_320.0

    /** Projects [point] onto the nearest segment of [line] (local planar approximation, fine at city scale). */
    fun locate(line: List<GeoPoint>, point: GeoPoint): PolylineProgress {
        if (line.size < 2) return PolylineProgress(Double.POSITIVE_INFINITY, 0.0, 0.0)
        val lonScale = cos(point.latitude * PI / 180.0) * METERS_PER_DEGREE
        fun x(p: GeoPoint) = (p.longitude - point.longitude) * lonScale
        fun y(p: GeoPoint) = (p.latitude - point.latitude) * METERS_PER_DEGREE

        var bestOffset = Double.POSITIVE_INFINITY
        var bestAlong = 0.0
        var walked = 0.0
        for (i in 0 until line.lastIndex) {
            val ax = x(line[i]); val ay = y(line[i])
            val bx = x(line[i + 1]); val by = y(line[i + 1])
            val dx = bx - ax
            val dy = by - ay
            val length = hypot(dx, dy)
            val t = if (length == 0.0) 0.0 else ((-ax * dx - ay * dy) / (length * length)).coerceIn(0.0, 1.0)
            val offset = hypot(ax + dx * t, ay + dy * t)
            if (offset < bestOffset) {
                bestOffset = offset
                bestAlong = walked + length * t
            }
            walked += length
        }
        return PolylineProgress(bestOffset, bestAlong, walked)
    }

    /** Point [meters] from the start of [line] (clamped to its ends). */
    fun pointAt(line: List<GeoPoint>, meters: Double): GeoPoint = splitAt(line, meters).first.last()

    /** [point] moved [meters] perpendicular to [heading] (degrees clockwise from north); positive = to the right. */
    fun offsetSideways(point: GeoPoint, heading: Double, meters: Double): GeoPoint {
        val side = (heading + 90.0) * PI / 180.0
        val dLat = meters * cos(side) / METERS_PER_DEGREE
        val dLon = meters * kotlin.math.sin(side) / (METERS_PER_DEGREE * cos(point.latitude * PI / 180.0))
        return GeoPoint(point.latitude + dLat, point.longitude + dLon)
    }

    /** Heading in degrees clockwise from north from [a] to [b]. */
    fun heading(a: GeoPoint, b: GeoPoint): Double {
        val dx = (b.longitude - a.longitude) * cos(a.latitude * PI / 180.0)
        val dy = b.latitude - a.latitude
        return kotlin.math.atan2(dx, dy) * 180.0 / PI
    }

    /** Splits [line] at [meters] from its start into the walked part and the remaining part (sharing the split point). */
    fun splitAt(line: List<GeoPoint>, meters: Double): Pair<List<GeoPoint>, List<GeoPoint>> {
        if (line.size < 2 || meters <= 0.0) return listOf(line.firstOrNull()).filterNotNull() to line
        var walked = 0.0
        for (i in 0 until line.lastIndex) {
            val a = line[i]
            val b = line[i + 1]
            val length = GeoMath.haversineDistanceMeters(a, b)
            if (walked + length >= meters) {
                val t = if (length == 0.0) 0.0 else (meters - walked) / length
                val split = GeoPoint(a.latitude + (b.latitude - a.latitude) * t, a.longitude + (b.longitude - a.longitude) * t)
                return (line.subList(0, i + 1) + split) to (listOf(split) + line.subList(i + 1, line.size))
            }
            walked += length
        }
        return line to listOf(line.last())
    }
}
