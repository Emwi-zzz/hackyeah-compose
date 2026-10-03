package test

import sklepsearch.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Path2DTest {

    @Test
    fun testPath2DConstructionAndBounds() {
        val path = Path2D.rectangle(10.0, 20.0, 100.0, 50.0)
        val bounds = path.getBounds()

        assertEquals(10.0, bounds.minX)
        assertEquals(20.0, bounds.minY)
        assertEquals(110.0, bounds.maxX)
        assertEquals(70.0, bounds.maxY)
        assertEquals(100.0, bounds.width)
        assertEquals(50.0, bounds.height)
        assertEquals(60.0, bounds.centerX)
        assertEquals(45.0, bounds.centerY)
    }

    @Test
    fun testPointInPolygonContains() {
        val square = Path2D.of(
            Point(0.0, 0.0),
            Point(100.0, 0.0),
            Point(100.0, 100.0),
            Point(0.0, 100.0)
        )

        // Point inside
        assertTrue(square.contains(Point(50.0, 50.0)), "Center point should be inside")
        assertTrue(square.contains(Point(10.0, 10.0)), "Near-corner point should be inside")

        // Point outside
        assertFalse(square.contains(Point(-5.0, 50.0)), "Point to the left should be outside")
        assertFalse(square.contains(Point(150.0, 50.0)), "Point to the right should be outside")
        assertFalse(square.contains(Point(50.0, -10.0)), "Point above should be outside")
        assertFalse(square.contains(Point(50.0, 110.0)), "Point below should be outside")
    }

    @Test
    fun testSvgRoundTripWithExponentNumbers() {
        val path = Path2D().moveTo(1.0E-4, 5.0).lineTo(2.5E7, -3.0E-5).quadTo(1.0, 1.0, 2.0, 2.0).closePath()
        val parsed = Path2D.fromSvgPath(path.toSvgPath())
        assertEquals(path.segments, parsed.segments)
    }

    @Test
    fun testQuadraticBezierCurve() {
        // Create an arch with flat bottom (y=0, x: 0..100) and quadratic curved top bulging to y=100 with control (50, 100)
        val arch = Path2D().apply {
            moveTo(0.0, 0.0)
            lineTo(100.0, 0.0)
            lineTo(100.0, 50.0)
            quadTo(50.0, 100.0, 0.0, 50.0)
            closePath()
        }

        // Test segments
        assertEquals(5, arch.segments.size)
        assertTrue(arch.segments[3] is PathSegment.QuadTo)
        val quad = arch.segments[3] as PathSegment.QuadTo
        assertEquals(50.0, quad.x1)
        assertEquals(100.0, quad.y1)
        assertEquals(0.0, quad.x2)
        assertEquals(50.0, quad.y2)

        // Evaluate midpoint of quad curve: t=0.5
        // B(0.5) = 0.25*(100, 50) + 0.5*(50, 100) + 0.25*(0, 50) = (25 + 25 + 0, 12.5 + 50 + 12.5) = (50.0, 75.0)
        val mid = Path2D.evaluateQuad(Point(100.0, 50.0), Point(50.0, 100.0), Point(0.0, 50.0), 0.5)
        assertEquals(50.0, mid.x, 1e-4)
        assertEquals(75.0, mid.y, 1e-4)

        // Test bounds: maxY should reach 75.0 (the apex of the curve)
        val bounds = arch.getBounds()
        assertEquals(0.0, bounds.minX)
        assertEquals(100.0, bounds.maxX)
        assertEquals(0.0, bounds.minY)
        assertEquals(75.0, bounds.maxY, 1e-2)

        // Point inside the bulge (x=50, y=60)
        assertTrue(arch.contains(Point(50.0, 60.0)), "Point inside quadratic bulge must be inside")

        // Point outside the bulge (x=50, y=85)
        assertFalse(arch.contains(Point(50.0, 85.0)), "Point beyond apex must be outside")

        // Point near bottom inside (x=50, y=20)
        assertTrue(arch.contains(Point(50.0, 20.0)), "Point inside lower body must be inside")
    }

    @Test
    fun testCubicBezierCurve() {
        // Semicircular dome: flat base from (0, 0) to (100, 0), cubic curve bulging up to y=66.6
        // Control points at (100, 55.2) and (0, 55.2)
        val dome = Path2D().apply {
            moveTo(0.0, 0.0)
            lineTo(100.0, 0.0)
            curveTo(100.0, 55.2, 0.0, 55.2, 0.0, 0.0)
            closePath()
        }

        assertEquals(4, dome.segments.size)
        assertTrue(dome.segments[2] is PathSegment.CubicTo)
        val cubic = dome.segments[2] as PathSegment.CubicTo
        assertEquals(100.0, cubic.x1)
        assertEquals(55.2, cubic.y1)
        assertEquals(0.0, cubic.x2)
        assertEquals(55.2, cubic.y2)
        assertEquals(0.0, cubic.x3)
        assertEquals(0.0, cubic.y3)

        // Evaluate apex at t=0.5:
        // B(0.5) = (1/8)*100 + (3/8)*100 + (3/8)*0 + (1/8)*0 = 50.0 for x
        // y: (1/8)*0 + (3/8)*55.2 + (3/8)*55.2 + (1/8)*0 = 41.4
        val apex = Path2D.evaluateCubic(Point(100.0, 0.0), Point(100.0, 55.2), Point(0.0, 55.2), Point(0.0, 0.0), 0.5)
        assertEquals(50.0, apex.x, 1e-4)
        assertEquals(41.4, apex.y, 1e-4)

        // Bounds check
        val bounds = dome.getBounds()
        assertEquals(0.0, bounds.minX)
        assertEquals(100.0, bounds.maxX)
        assertEquals(0.0, bounds.minY)
        assertEquals(41.4, bounds.maxY, 1e-2)

        // Containment check
        assertTrue(dome.contains(Point(50.0, 20.0)), "Center under dome must be inside")
        assertTrue(dome.contains(Point(50.0, 35.0)), "High under dome must be inside")
        assertFalse(dome.contains(Point(50.0, 45.0)), "Above apex must be outside")
        assertFalse(dome.contains(Point(50.0, -10.0)), "Below baseline must be outside")
    }

    @Test
    fun testBezierAliasesAndPointOverloads() {
        val path = Path2D()
            .moveTo(Point(10.0, 10.0))
            .lineTo(Point(20.0, 10.0))
            .quadraticTo(Point(25.0, 15.0), Point(30.0, 20.0))
            .cubicTo(Point(35.0, 25.0), Point(40.0, 30.0), Point(45.0, 35.0))
            .closePath()

        assertEquals(5, path.segments.size)
        assertTrue(path.segments[2] is PathSegment.QuadTo)
        assertTrue(path.segments[3] is PathSegment.CubicTo)

        val v = path.getVertices(stepsPerCurve = 8)
        assertTrue(v.size > 10, "Discretization should produce sampled polyline")
    }

    @Test
    fun testSvgPathSerializationAndDeserialization() {
        val original = Path2D()
            .moveTo(10.0, 20.0)
            .lineTo(30.0, 40.0)
            .quadTo(50.0, 60.0, 70.0, 80.0)
            .curveTo(90.0, 100.0, 110.0, 120.0, 130.0, 140.0)
            .closePath()

        val svg = original.toSvgPath()
        assertTrue(svg.startsWith("M 10.0 20.0"))
        assertTrue(svg.contains("Q 50.0 60.0 70.0 80.0"))
        assertTrue(svg.contains("C 90.0 100.0 110.0 120.0 130.0 140.0"))
        assertTrue(svg.endsWith("Z"))

        val parsed = Path2D.fromSvgPath(svg)
        assertEquals(original.segments.size, parsed.segments.size)
        assertEquals(original.segments, parsed.segments)

        val dto = BezierPathDto.fromPath2D(original)
        assertEquals(svg, dto.toSvgPath())
        val reconstructedDto = BezierPathDto.fromSvgPath(svg)
        assertEquals(dto.segments, reconstructedDto.segments)
    }

    @Test
    fun testStandaloneBezierDataClasses() {
        // QuadraticBezier
        val qb = QuadraticBezier(
            start = Point(0.0, 0.0),
            control = Point(50.0, 100.0),
            end = Point(100.0, 0.0)
        )
        val qbMid = qb.evaluate(0.5)
        assertEquals(50.0, qbMid.x, 1e-4)
        assertEquals(50.0, qbMid.y, 1e-4)
        assertEquals(17, qb.sample(16).size)

        // CubicBezier
        val cb = CubicBezier(
            start = Point(0.0, 0.0),
            control1 = Point(0.0, 100.0),
            control2 = Point(100.0, 100.0),
            end = Point(100.0, 0.0)
        )
        val cbMid = cb.evaluate(0.5)
        assertEquals(50.0, cbMid.x, 1e-4)
        assertEquals(75.0, cbMid.y, 1e-4)
        assertEquals(17, cb.sample(16).size)
    }
}
