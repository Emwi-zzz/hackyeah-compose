package features.indoor.domain

import sklepsearch.Path2D
import sklepsearch.Point

/**
 * Converts a sequence of navigation points into a continuous C¹-smooth cubic Bézier curve
 * using Catmull-Rom spline interpolation.
 */
object CatmullRomBezierSpline {

    /**
     * Constructs a smooth cubic Bézier Path2D passing through all provided [points].
     * @param points list of waypoints along the route
     * @param tension spline tension parameter (default 0.5 for centripetal-like smoothness)
     */
    fun createSpline(points: List<Point>, tension: Double = 0.5): Path2D {
        val path = Path2D()
        if (points.isEmpty()) return path

        if (points.size == 1) {
            path.moveTo(points[0].x, points[0].y)
            return path
        }

        if (points.size == 2) {
            val p0 = points[0]
            val p1 = points[1]
            path.moveTo(p0.x, p0.y)
            val c1 = Point(p0.x + (p1.x - p0.x) / 3.0, p0.y + (p1.y - p0.y) / 3.0)
            val c2 = Point(p1.x - (p1.x - p0.x) / 3.0, p1.y - (p1.y - p0.y) / 3.0)
            path.curveTo(c1, c2, p1)
            return path
        }

        path.moveTo(points[0].x, points[0].y)
        val n = points.size
        val factor = tension / 3.0 // 1/6 for standard Catmull-Rom

        for (i in 0 until n - 1) {
            val p0 = if (i == 0) {
                Point(2.0 * points[0].x - points[1].x, 2.0 * points[0].y - points[1].y)
            } else {
                points[i - 1]
            }

            val p1 = points[i]
            val p2 = points[i + 1]

            val p3 = if (i + 2 < n) {
                points[i + 2]
            } else {
                Point(2.0 * points[n - 1].x - points[n - 2].x, 2.0 * points[n - 1].y - points[n - 2].y)
            }

            val c1x = p1.x + (p2.x - p0.x) * factor
            val c1y = p1.y + (p2.y - p0.y) * factor

            val c2x = p2.x - (p3.x - p1.x) * factor
            val c2y = p2.y - (p3.y - p1.y) * factor

            path.curveTo(c1x, c1y, c2x, c2y, p2.x, p2.y)
        }

        return path
    }
}
