package sklepsearch

import kotlin.math.max
import kotlin.math.min

sealed interface PathSegment {
    data class MoveTo(val x: Double, val y: Double) : PathSegment
    data class LineTo(val x: Double, val y: Double) : PathSegment
    data class QuadTo(
        val x1: Double,
        val y1: Double,
        val x2: Double,
        val y2: Double
    ) : PathSegment
    data class CubicTo(
        val x1: Double,
        val y1: Double,
        val x2: Double,
        val y2: Double,
        val x3: Double,
        val y3: Double
    ) : PathSegment
    object Close : PathSegment
}

typealias QuadraticTo = PathSegment.QuadTo
typealias CurveTo = PathSegment.CubicTo

data class Rect2D(
    val minX: Double,
    val minY: Double,
    val maxX: Double,
    val maxY: Double
) {
    val width: Double get() = maxX - minX
    val height: Double get() = maxY - minY
    val centerX: Double get() = (minX + maxX) / 2.0
    val centerY: Double get() = (minY + maxY) / 2.0
}

/**
 * Pure Kotlin multiplatform replacement for java.awt.geom.Path2D supporting lines,
 * quadratic Bézier curves (quadTo/quadraticTo), and cubic Bézier curves (curveTo/cubicTo).
 */
class Path2D(
    initialSegments: List<PathSegment> = emptyList()
) {
    private val _segments = initialSegments.toMutableList()
    val segments: List<PathSegment> get() = _segments

    fun moveTo(x: Double, y: Double): Path2D {
        _segments.add(PathSegment.MoveTo(x, y))
        return this
    }

    fun moveTo(point: Point): Path2D = moveTo(point.x, point.y)

    fun lineTo(x: Double, y: Double): Path2D {
        _segments.add(PathSegment.LineTo(x, y))
        return this
    }

    fun lineTo(point: Point): Path2D = lineTo(point.x, point.y)

    /**
     * Appends a quadratic Bézier curve segment with one control point (x1, y1)
     * and destination point (x2, y2).
     */
    fun quadTo(x1: Double, y1: Double, x2: Double, y2: Double): Path2D {
        _segments.add(PathSegment.QuadTo(x1, y1, x2, y2))
        return this
    }

    fun quadTo(control: Point, end: Point): Path2D = quadTo(control.x, control.y, end.x, end.y)

    fun quadraticTo(x1: Double, y1: Double, x2: Double, y2: Double): Path2D = quadTo(x1, y1, x2, y2)

    fun quadraticTo(control: Point, end: Point): Path2D = quadTo(control, end)

    /**
     * Appends a cubic Bézier curve segment with two control points (x1, y1), (x2, y2)
     * and destination point (x3, y3). Standard java.awt.geom.Path2D method.
     */
    fun curveTo(x1: Double, y1: Double, x2: Double, y2: Double, x3: Double, y3: Double): Path2D {
        _segments.add(PathSegment.CubicTo(x1, y1, x2, y2, x3, y3))
        return this
    }

    fun curveTo(control1: Point, control2: Point, end: Point): Path2D =
        curveTo(control1.x, control1.y, control2.x, control2.y, end.x, end.y)

    fun cubicTo(x1: Double, y1: Double, x2: Double, y2: Double, x3: Double, y3: Double): Path2D =
        curveTo(x1, y1, x2, y2, x3, y3)

    fun cubicTo(control1: Point, control2: Point, end: Point): Path2D =
        curveTo(control1, control2, end)

    fun closePath(): Path2D {
        _segments.add(PathSegment.Close)
        return this
    }

    /**
     * Discretizes all path segments into points along the path.
     * Quadratic and cubic Bézier curves are sampled into [stepsPerCurve] linear subdivisions.
     */
    fun getVertices(stepsPerCurve: Int = 16): List<Point> {
        val result = mutableListOf<Point>()
        var currentX = 0.0
        var currentY = 0.0
        var startX = 0.0
        var startY = 0.0

        for (seg in _segments) {
            when (seg) {
                is PathSegment.MoveTo -> {
                    result.add(Point(seg.x, seg.y))
                    currentX = seg.x
                    currentY = seg.y
                    startX = seg.x
                    startY = seg.y
                }
                is PathSegment.LineTo -> {
                    result.add(Point(seg.x, seg.y))
                    currentX = seg.x
                    currentY = seg.y
                }
                is PathSegment.QuadTo -> {
                    val steps = max(1, stepsPerCurve)
                    for (i in 1..steps) {
                        val t = i.toDouble() / steps
                        val invT = 1.0 - t
                        val x = invT * invT * currentX + 2.0 * invT * t * seg.x1 + t * t * seg.x2
                        val y = invT * invT * currentY + 2.0 * invT * t * seg.y1 + t * t * seg.y2
                        result.add(Point(x, y))
                    }
                    currentX = seg.x2
                    currentY = seg.y2
                }
                is PathSegment.CubicTo -> {
                    val steps = max(1, stepsPerCurve)
                    for (i in 1..steps) {
                        val t = i.toDouble() / steps
                        val invT = 1.0 - t
                        val x = invT * invT * invT * currentX +
                                3.0 * invT * invT * t * seg.x1 +
                                3.0 * invT * t * t * seg.x2 +
                                t * t * t * seg.x3
                        val y = invT * invT * invT * currentY +
                                3.0 * invT * invT * t * seg.y1 +
                                3.0 * invT * t * t * seg.y2 +
                                t * t * t * seg.y3
                        result.add(Point(x, y))
                    }
                    currentX = seg.x3
                    currentY = seg.y3
                }
                is PathSegment.Close -> {
                    currentX = startX
                    currentY = startY
                }
            }
        }
        return result
    }

    /**
     * Calculates the bounding box of this Path2D, taking into account analytical
     * extrema of quadratic and cubic Bézier curve segments.
     */
    fun getBounds(): Rect2D {
        val pts = getVertices(stepsPerCurve = 16)
        if (pts.isEmpty()) return Rect2D(0.0, 0.0, 0.0, 0.0)

        var minX = pts[0].x
        var minY = pts[0].y
        var maxX = pts[0].x
        var maxY = pts[0].y

        for (i in 1 until pts.size) {
            val p = pts[i]
            minX = min(minX, p.x)
            minY = min(minY, p.y)
            maxX = max(maxX, p.x)
            maxY = max(maxY, p.y)
        }

        // Add analytical extrema for quadratic and cubic segments for exact bounds
        var currentX = 0.0
        var currentY = 0.0
        for (seg in _segments) {
            when (seg) {
                is PathSegment.MoveTo -> {
                    currentX = seg.x
                    currentY = seg.y
                }
                is PathSegment.LineTo -> {
                    currentX = seg.x
                    currentY = seg.y
                }
                is PathSegment.QuadTo -> {
                    // Quadratic extrema: d/dt = 0
                    val denomX = currentX - 2.0 * seg.x1 + seg.x2
                    if (denomX != 0.0) {
                        val tx = (currentX - seg.x1) / denomX
                        if (tx > 0.0 && tx < 1.0) {
                            val invTx = 1.0 - tx
                            val exX = invTx * invTx * currentX + 2.0 * invTx * tx * seg.x1 + tx * tx * seg.x2
                            minX = min(minX, exX)
                            maxX = max(maxX, exX)
                        }
                    }
                    val denomY = currentY - 2.0 * seg.y1 + seg.y2
                    if (denomY != 0.0) {
                        val ty = (currentY - seg.y1) / denomY
                        if (ty > 0.0 && ty < 1.0) {
                            val invTy = 1.0 - ty
                            val exY = invTy * invTy * currentY + 2.0 * invTy * ty * seg.y1 + ty * ty * seg.y2
                            minY = min(minY, exY)
                            maxY = max(maxY, exY)
                        }
                    }
                    currentX = seg.x2
                    currentY = seg.y2
                }
                is PathSegment.CubicTo -> {
                    // Cubic extrema: d/dt = 3*(d0*(1-t)^2 + 2*d1*(1-t)*t + d2*t^2) = 0
                    findCubicRoots(currentX, seg.x1, seg.x2, seg.x3) { t ->
                        val invT = 1.0 - t
                        val exX = invT * invT * invT * currentX + 3.0 * invT * invT * t * seg.x1 + 3.0 * invT * t * t * seg.x2 + t * t * t * seg.x3
                        minX = min(minX, exX)
                        maxX = max(maxX, exX)
                    }
                    findCubicRoots(currentY, seg.y1, seg.y2, seg.y3) { t ->
                        val invT = 1.0 - t
                        val exY = invT * invT * invT * currentY + 3.0 * invT * invT * t * seg.y1 + 3.0 * invT * t * t * seg.y2 + t * t * t * seg.y3
                        minY = min(minY, exY)
                        maxY = max(maxY, exY)
                    }
                    currentX = seg.x3
                    currentY = seg.y3
                }
                is PathSegment.Close -> Unit
            }
        }

        return Rect2D(minX, minY, maxX, maxY)
    }

    /**
     * Point-in-polygon test using ray-casting algorithm (even-odd rule)
     * evaluated over the discretized path segments.
     */
    fun contains(point: Point, curvePrecision: Int = 24): Boolean {
        val vertices = getVertices(stepsPerCurve = curvePrecision)
        if (vertices.size < 3) return false

        var inside = false
        val px = point.x
        val py = point.y
        val n = vertices.size
        var j = n - 1

        for (i in 0 until n) {
            val xi = vertices[i].x
            val yi = vertices[i].y
            val xj = vertices[j].x
            val yj = vertices[j].y

            val intersect = ((yi > py) != (yj > py)) &&
                    (px < (xj - xi) * (py - yi) / (yj - yi) + xi)
            if (intersect) {
                inside = !inside
            }
            j = i
        }
        return inside
    }

    /**
     * Converts this Path2D into an SVG path data string (e.g., "M 10 20 L 30 40 Q 50 60 70 80 Z")
     * for sharing with backend APIs, database persistence, and cross-platform transport.
     */
    fun toSvgPath(): String = buildString {
        for (seg in _segments) {
            when (seg) {
                is PathSegment.MoveTo -> append("M ${seg.x} ${seg.y} ")
                is PathSegment.LineTo -> append("L ${seg.x} ${seg.y} ")
                is PathSegment.QuadTo -> append("Q ${seg.x1} ${seg.y1} ${seg.x2} ${seg.y2} ")
                is PathSegment.CubicTo -> append("C ${seg.x1} ${seg.y1} ${seg.x2} ${seg.y2} ${seg.x3} ${seg.y3} ")
                is PathSegment.Close -> append("Z ")
            }
        }
    }.trim()

    companion object {
        fun of(vararg points: Point): Path2D {
            val path = Path2D()
            if (points.isNotEmpty()) {
                path.moveTo(points[0].x, points[0].y)
                for (i in 1 until points.size) {
                    path.lineTo(points[i].x, points[i].y)
                }
                path.closePath()
            }
            return path
        }

        fun rectangle(x: Double, y: Double, width: Double, height: Double): Path2D {
            return Path2D().apply {
                moveTo(x, y)
                lineTo(x + width, y)
                lineTo(x + width, y + height)
                lineTo(x, y + height)
                closePath()
            }
        }

        /**
         * Parses an SVG path data string (e.g., "M 0 0 L 100 0 C 100 50 0 50 0 0 Z") into a Path2D.
         */
        fun fromSvgPath(svg: String): Path2D {
            val path = Path2D()
            val clean = svg.replace(Regex("([a-zA-Z])"), " $1 ").replace(',', ' ').trim()
            if (clean.isEmpty()) return path

            val tokens = clean.split(Regex("\\s+")).filter { it.isNotEmpty() }
            var idx = 0
            while (idx < tokens.size) {
                val token = tokens[idx++]
                when (token.uppercase()) {
                    "M" -> {
                        val x = tokens[idx++].toDouble()
                        val y = tokens[idx++].toDouble()
                        path.moveTo(x, y)
                    }
                    "L" -> {
                        val x = tokens[idx++].toDouble()
                        val y = tokens[idx++].toDouble()
                        path.lineTo(x, y)
                    }
                    "Q" -> {
                        val x1 = tokens[idx++].toDouble()
                        val y1 = tokens[idx++].toDouble()
                        val x2 = tokens[idx++].toDouble()
                        val y2 = tokens[idx++].toDouble()
                        path.quadTo(x1, y1, x2, y2)
                    }
                    "C" -> {
                        val x1 = tokens[idx++].toDouble()
                        val y1 = tokens[idx++].toDouble()
                        val x2 = tokens[idx++].toDouble()
                        val y2 = tokens[idx++].toDouble()
                        val x3 = tokens[idx++].toDouble()
                        val y3 = tokens[idx++].toDouble()
                        path.curveTo(x1, y1, x2, y2, x3, y3)
                    }
                    "Z" -> {
                        path.closePath()
                    }
                }
            }
            return path
        }

        /**
         * Evaluates a quadratic Bézier curve at parameter t in [0.0, 1.0].
         */
        fun evaluateQuad(p0: Point, p1: Point, p2: Point, t: Double): Point {
            val invT = 1.0 - t
            val x = invT * invT * p0.x + 2.0 * invT * t * p1.x + t * t * p2.x
            val y = invT * invT * p0.y + 2.0 * invT * t * p1.y + t * t * p2.y
            return Point(x, y)
        }

        /**
         * Evaluates a cubic Bézier curve at parameter t in [0.0, 1.0].
         */
        fun evaluateCubic(p0: Point, p1: Point, p2: Point, p3: Point, t: Double): Point {
            val invT = 1.0 - t
            val x = invT * invT * invT * p0.x +
                    3.0 * invT * invT * t * p1.x +
                    3.0 * invT * t * t * p2.x +
                    t * t * t * p3.x
            val y = invT * invT * invT * p0.y +
                    3.0 * invT * invT * t * p1.y +
                    3.0 * invT * t * t * p2.y +
                    t * t * t * p3.y
            return Point(x, y)
        }

        private inline fun findCubicRoots(
            p0: Double, p1: Double, p2: Double, p3: Double,
            onRoot: (Double) -> Unit
        ) {
            val d0 = p1 - p0
            val d1 = p2 - p1
            val d2 = p3 - p2
            val a = d0 - 2.0 * d1 + d2
            val b = 2.0 * (d1 - d0)
            val c = d0

            if (kotlin.math.abs(a) < 1e-12) {
                if (kotlin.math.abs(b) > 1e-12) {
                    val t = -c / b
                    if (t > 0.0 && t < 1.0) onRoot(t)
                }
            } else {
                val disc = b * b - 4.0 * a * c
                if (disc >= 0.0) {
                    val sqrtD = kotlin.math.sqrt(disc)
                    val t1 = (-b - sqrtD) / (2.0 * a)
                    val t2 = (-b + sqrtD) / (2.0 * a)
                    if (t1 > 0.0 && t1 < 1.0) onRoot(t1)
                    if (t2 > 0.0 && t2 < 1.0) onRoot(t2)
                }
            }
        }
    }
}

/**
 * Common Bézier curve interface for backend / frontend geometric sharing.
 */
sealed interface BezierCurve {
    val start: Point
    val end: Point
    fun evaluate(t: Double): Point
    fun getBounds(): Rect2D
    fun sample(steps: Int = 16): List<Point>
}

/**
 * Data class representing a quadratic Bézier curve with 1 control point.
 */
data class QuadraticBezier(
    override val start: Point,
    val control: Point,
    override val end: Point
) : BezierCurve {
    override fun evaluate(t: Double): Point = Path2D.evaluateQuad(start, control, end, t)

    override fun getBounds(): Rect2D {
        val path = Path2D().moveTo(start).quadTo(control, end)
        return path.getBounds()
    }

    override fun sample(steps: Int): List<Point> {
        val pts = mutableListOf<Point>()
        for (i in 0..steps) {
            pts.add(evaluate(i.toDouble() / steps))
        }
        return pts
    }
}

/**
 * Data class representing a cubic Bézier curve with 2 control points.
 */
data class CubicBezier(
    override val start: Point,
    val control1: Point,
    val control2: Point,
    override val end: Point
) : BezierCurve {
    override fun evaluate(t: Double): Point = Path2D.evaluateCubic(start, control1, control2, end, t)

    override fun getBounds(): Rect2D {
        val path = Path2D().moveTo(start).curveTo(control1, control2, end)
        return path.getBounds()
    }

    override fun sample(steps: Int): List<Point> {
        val pts = mutableListOf<Point>()
        for (i in 0..steps) {
            pts.add(evaluate(i.toDouble() / steps))
        }
        return pts
    }
}

/**
 * Data Transfer Object (DTO) for sharing arbitrary 2D paths and Bézier shapes with backend services.
 */
data class BezierPathDto(
    val segments: List<PathSegment>
) {
    fun toPath2D(): Path2D = Path2D(segments)
    fun toSvgPath(): String = toPath2D().toSvgPath()

    companion object {
        fun fromPath2D(path: Path2D): BezierPathDto = BezierPathDto(path.segments)
        fun fromSvgPath(svg: String): BezierPathDto = BezierPathDto(Path2D.fromSvgPath(svg).segments)
    }
}
