package features.admin.domain

import sklepsearch.Path2D
import sklepsearch.PathSegment
import sklepsearch.Point

object AdminShapeFactory {
    data class EditablePoint(
        val point: Point,
        val anchorIndex: Int? = null,
        val segmentIndex: Int? = null,
        val controlIndex: Int? = null
    )

    fun vertices(path: Path2D): List<Point> {
        return editablePoints(path).mapNotNull { point ->
            point.anchorIndex?.let { point.point }
        }
    }

    fun editablePoints(path: Path2D): List<EditablePoint> {
        val points = mutableListOf<EditablePoint>()
        var anchorIndex = 0
        for ((segmentIndex, segment) in path.segments.withIndex()) {
            when (segment) {
                is PathSegment.MoveTo -> points.add(
                    EditablePoint(Point(segment.x, segment.y), anchorIndex = anchorIndex++)
                )
                is PathSegment.LineTo -> points.add(
                    EditablePoint(Point(segment.x, segment.y), anchorIndex = anchorIndex++)
                )
                is PathSegment.QuadTo -> {
                    points.add(
                        EditablePoint(Point(segment.x1, segment.y1), segmentIndex = segmentIndex, controlIndex = 1)
                    )
                    points.add(
                        EditablePoint(Point(segment.x2, segment.y2), anchorIndex = anchorIndex++)
                    )
                }
                is PathSegment.CubicTo -> {
                    points.add(
                        EditablePoint(Point(segment.x1, segment.y1), segmentIndex = segmentIndex, controlIndex = 1)
                    )
                    points.add(
                        EditablePoint(Point(segment.x2, segment.y2), segmentIndex = segmentIndex, controlIndex = 2)
                    )
                    points.add(
                        EditablePoint(Point(segment.x3, segment.y3), anchorIndex = anchorIndex++)
                    )
                }
                PathSegment.Close -> Unit
            }
        }
        val first = points.firstOrNull { it.anchorIndex == 0 }?.point
        if (first != null) {
            val duplicateClosingAnchor = points.lastOrNull()?.takeIf {
                it.anchorIndex != null && it.point == first
            }
            if (duplicateClosingAnchor != null) points.remove(duplicateClosingAnchor)
        }
        return points
    }

    fun moveEditablePoint(path: Path2D, editablePoint: EditablePoint, point: Point): Path2D {
        val anchorIndex = editablePoint.anchorIndex
        if (anchorIndex != null) {
            val originalVertices = vertices(path)
            require(anchorIndex in originalVertices.indices)
            val originalFirst = originalVertices.first()
            val segments = path.segments.toMutableList()
            var currentAnchorIndex = 0
            var targetSegmentIndex: Int? = null
            for (index in segments.indices) {
                when (segments[index]) {
                    is PathSegment.MoveTo -> {
                        if (currentAnchorIndex == anchorIndex) targetSegmentIndex = index
                    }
                    is PathSegment.LineTo, is PathSegment.QuadTo, is PathSegment.CubicTo -> {
                        currentAnchorIndex++
                        if (currentAnchorIndex == anchorIndex) targetSegmentIndex = index
                    }
                    PathSegment.Close -> Unit
                }
            }
            val targetIndex = targetSegmentIndex ?: error("Could not locate anchor point")
            segments[targetIndex] = segments[targetIndex].withEndpoint(point)
            if (anchorIndex == 0) {
                val lastDrawableIndex = segments.indexOfLast {
                    it is PathSegment.LineTo || it is PathSegment.QuadTo || it is PathSegment.CubicTo
                }
                if (lastDrawableIndex >= 0 && lastDrawableIndex != targetIndex &&
                    segments[lastDrawableIndex].endpoint() == originalFirst
                ) {
                    segments[lastDrawableIndex] = segments[lastDrawableIndex].withEndpoint(point)
                }
            }
            return Path2D(segments)
        }

        val segmentIndex = editablePoint.segmentIndex ?: error("Editable point has no path location")
        val controlIndex = editablePoint.controlIndex ?: error("Editable point has no control point")
        val segments = path.segments.toMutableList()
        require(segmentIndex in segments.indices)
        segments[segmentIndex] = segments[segmentIndex].withControl(controlIndex, point)
        return Path2D(segments)
    }

    fun previewEdge(previous: Point?, start: Point, end: Point, curved: Boolean): List<Point> {
        if (!curved) return listOf(start, end)
        val prior = previous ?: start
        val factor = 0.5 / 3.0
        val control1 = Point(
            start.x + (end.x - prior.x) * factor,
            start.y + (end.y - prior.y) * factor
        )
        val control2 = Point(
            end.x - (end.x - start.x) * factor,
            end.y - (end.y - start.y) * factor
        )
        return Path2D().moveTo(start).curveTo(control1, control2, end).getVertices(stepsPerCurve = 24)
    }

    fun moveVertex(path: Path2D, index: Int, point: Point): Path2D {
        val vertices = editablePoints(path).filter { it.anchorIndex != null }
        require(index in vertices.indices)
        return moveEditablePoint(path, vertices[index], point)
    }

    fun polygon(points: List<Point>, smoothBezier: Boolean): Path2D {
        return polygon(
            points = points,
            curvedEdges = List((points.size - 1).coerceAtLeast(0)) { smoothBezier },
            closingBezier = smoothBezier
        )
    }

    fun polygon(
        points: List<Point>,
        curvedEdges: List<Boolean>,
        closingBezier: Boolean
    ): Path2D {
        require(points.size >= 3) { "A shape needs at least 3 points" }
        require(curvedEdges.size == points.size - 1) { "Expected one edge style between each pair of points" }

        val path = Path2D().moveTo(points.first())
        val factor = 0.5 / 3.0
        fun appendCurve(start: Point, end: Point, previous: Point, next: Point) {
            val control1 = Point(
                start.x + (end.x - previous.x) * factor,
                start.y + (end.y - previous.y) * factor
            )
            val control2 = Point(
                end.x - (next.x - start.x) * factor,
                end.y - (next.y - start.y) * factor
            )
            path.curveTo(control1, control2, end)
        }
        for (i in 0 until points.lastIndex) {
            val start = points[i]
            val end = points[i + 1]
            if (curvedEdges[i]) {
                val previous = points[(i - 1 + points.size) % points.size]
                val next = points[(i + 2) % points.size]
                appendCurve(start, end, previous, next)
            } else {
                path.lineTo(end)
            }
        }
        if (closingBezier) {
            val start = points.last()
            val end = points.first()
            val previous = points[points.lastIndex - 1]
            val next = points[1]
            appendCurve(start, end, previous, next)
        }
        return path.closePath()
    }

    private fun PathSegment.endpoint(): Point? = when (this) {
        is PathSegment.LineTo -> Point(x, y)
        is PathSegment.QuadTo -> Point(x2, y2)
        is PathSegment.CubicTo -> Point(x3, y3)
        else -> null
    }

    private fun PathSegment.withEndpoint(point: Point): PathSegment = when (this) {
        is PathSegment.MoveTo -> copy(x = point.x, y = point.y)
        is PathSegment.LineTo -> copy(x = point.x, y = point.y)
        is PathSegment.QuadTo -> copy(x2 = point.x, y2 = point.y)
        is PathSegment.CubicTo -> copy(x3 = point.x, y3 = point.y)
        PathSegment.Close -> this
    }

    private fun PathSegment.withControl(controlIndex: Int, point: Point): PathSegment = when (this) {
        is PathSegment.QuadTo -> {
            require(controlIndex == 1)
            copy(x1 = point.x, y1 = point.y)
        }
        is PathSegment.CubicTo -> when (controlIndex) {
            1 -> copy(x1 = point.x, y1 = point.y)
            2 -> copy(x2 = point.x, y2 = point.y)
            else -> error("Invalid cubic control point index")
        }
        else -> error("Path segment does not have control points")
    }
}
