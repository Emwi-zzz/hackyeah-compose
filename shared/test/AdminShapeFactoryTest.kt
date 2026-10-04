import features.admin.domain.AdminShapeFactory
import sklepsearch.Path2D
import sklepsearch.PathSegment
import sklepsearch.Point
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdminShapeFactoryTest {
    private val corners = listOf(
        Point(0.0, 0.0),
        Point(100.0, 0.0),
        Point(100.0, 100.0),
        Point(0.0, 100.0)
    )

    @Test
    fun straightModeKeepsPolygonEdges() {
        val path = AdminShapeFactory.polygon(corners, smoothBezier = false)

        assertEquals(5, path.segments.size)
        assertTrue(path.segments.none { it is PathSegment.CubicTo })
        assertTrue(path.segments.last() is PathSegment.Close)
    }

    @Test
    fun bezierModeCreatesClosedCubicOutlineThroughAnchors() {
        val path = AdminShapeFactory.polygon(corners, smoothBezier = true)
        val curves = path.segments.filterIsInstance<PathSegment.CubicTo>()

        assertEquals(corners.size, curves.size)
        assertEquals(1, path.segments.count { it is PathSegment.Close })
        assertEquals(corners.first().x, curves.last().x3)
        assertEquals(corners.first().y, curves.last().y3)
        assertTrue(path.getVertices().size > corners.size)
    }

    @Test
    fun polygonCanMixStraightAndBezierEdges() {
        val path = AdminShapeFactory.polygon(
            points = corners,
            curvedEdges = listOf(true, false, true),
            closingBezier = false
        )

        assertEquals(
            listOf(
                PathSegment.MoveTo::class,
                PathSegment.CubicTo::class,
                PathSegment.LineTo::class,
                PathSegment.CubicTo::class,
                PathSegment.Close::class
            ),
            path.segments.map { it::class }
        )
    }

    @Test
    fun movingVertexKeepsPolygonAnchorsAndEdgeStyles() {
        val path = AdminShapeFactory.polygon(
            points = corners,
            curvedEdges = listOf(true, false, true),
            closingBezier = false
        )
        val moved = AdminShapeFactory.moveVertex(path, 1, Point(120.0, 10.0))

        assertEquals(
            listOf(corners[0], Point(120.0, 10.0), corners[2], corners[3]),
            AdminShapeFactory.vertices(moved)
        )
        assertEquals(2, moved.segments.count { it is PathSegment.CubicTo })
        assertTrue(moved.segments.last() is PathSegment.Close)
    }

    @Test
    fun movingFirstVertexKeepsCurvedClosingEdge() {
        val moved = AdminShapeFactory.moveVertex(
            AdminShapeFactory.polygon(corners, smoothBezier = true),
            0,
            Point(-10.0, -10.0)
        )

        assertEquals(Point(-10.0, -10.0), AdminShapeFactory.vertices(moved).first())
        assertEquals(corners.size, moved.segments.count { it is PathSegment.CubicTo })
    }

    @Test
    fun bezierControlPointsAreEditableAndPreservedWhenMoved() {
        val path = Path2D()
            .moveTo(0.0, 0.0)
            .curveTo(Point(20.0, 0.0), Point(80.0, 100.0), Point(100.0, 100.0))
            .lineTo(0.0, 100.0)
            .closePath()
        val control = AdminShapeFactory.editablePoints(path).first { it.controlIndex == 2 }

        assertEquals(Point(80.0, 100.0), control.point)
        val moved = AdminShapeFactory.moveEditablePoint(path, control, Point(80.0, 120.0))

        assertEquals(
            PathSegment.CubicTo(20.0, 0.0, 80.0, 120.0, 100.0, 100.0),
            moved.segments[1]
        )
    }

    @Test
    fun seededKazimierzFloorAndStorePathsExposeBezierControls() {
        val floor = Path2D.fromSvgPath(
            "M 80 160 L 620 100 C 760 95 920 180 900 340 L 900 840 " +
                "Q 730 920 560 880 L 560 560 Q 560 520 520 520 L 80 520 Z"
        )
        val store = Path2D.fromSvgPath(
            "M 380 400 L 540 400 L 540 470 C 540 515 380 515 380 470 Z"
        )

        assertEquals(4, AdminShapeFactory.editablePoints(floor).count { it.controlIndex != null })
        assertEquals(2, AdminShapeFactory.editablePoints(store).count { it.controlIndex != null })
    }

    @Test
    fun bezierPreviewSamplesCurvedEdge() {
        val preview = AdminShapeFactory.previewEdge(
            previous = Point(0.0, 100.0),
            start = Point(100.0, 100.0),
            end = Point(200.0, 0.0),
            curved = true
        )

        assertTrue(preview.size > 2)
        assertTrue(kotlin.math.abs(preview[6].y - 75.0) > 1.0)
    }
}
