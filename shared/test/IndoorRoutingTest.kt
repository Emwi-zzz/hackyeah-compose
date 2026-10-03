package test

import features.indoor.domain.CatmullRomBezierSpline
import features.indoor.domain.NavLocation
import features.indoor.domain.NavLocationType
import features.map.presentation.MapState
import sklepsearch.*
import kotlin.test.*

class IndoorRoutingTest {

    private fun createMapState(): MapState = createTestMapState()

    @Test
    fun testCatmullRomSplineEmptyAndSingle() {
        val emptyPath = CatmullRomBezierSpline.createSpline(emptyList())
        assertTrue(emptyPath.segments.isEmpty())

        val singlePoint = listOf(Point(100.0, 200.0))
        val singlePath = CatmullRomBezierSpline.createSpline(singlePoint)
        assertEquals(1, singlePath.segments.size)
        val moveSeg = singlePath.segments[0] as PathSegment.MoveTo
        assertEquals(100.0, moveSeg.x)
        assertEquals(200.0, moveSeg.y)
    }

    @Test
    fun testCatmullRomSplineTwoPoints() {
        val points = listOf(Point(0.0, 0.0), Point(60.0, 90.0))
        val path = CatmullRomBezierSpline.createSpline(points)
        assertEquals(2, path.segments.size)
        assertTrue(path.segments[0] is PathSegment.MoveTo)
        val cubic = path.segments[1] as PathSegment.CubicTo
        assertEquals(60.0, cubic.x3)
        assertEquals(90.0, cubic.y3)
    }

    @Test
    fun testCatmullRomSplineMultipleWaypoints() {
        val points = listOf(
            Point(100.0, 100.0),
            Point(200.0, 150.0),
            Point(300.0, 120.0),
            Point(400.0, 250.0)
        )
        val spline = CatmullRomBezierSpline.createSpline(points)
        // 1 MoveTo + (points.size - 1) CubicTo segments = 4 segments
        assertEquals(4, spline.segments.size)
        assertTrue(spline.segments[0] is PathSegment.MoveTo)
        for (i in 1 until spline.segments.size) {
            assertTrue(spline.segments[i] is PathSegment.CubicTo, "Segment $i should be CubicTo")
        }

        // Final segment should end at the last point
        val lastCubic = spline.segments.last() as PathSegment.CubicTo
        assertEquals(400.0, lastCubic.x3)
        assertEquals(250.0, lastCubic.y3)
    }

    @Test
    fun testMapStateIndoorNavigationWorkflow() {
        val mapState = createMapState()
        mapState.refocusOnMall(TestIndoorData.krakowska, animate = false)

        val locations = mapState.getAvailableNavLocations()
        assertTrue(locations.isNotEmpty())

        val start = locations.first { it.type == NavLocationType.EXIT }
        val end = locations.first { it.type == NavLocationType.STORE }

        // 1. Request route
        mapState.requestIndoorRoute(start, end)
        assertNotNull(mapState.activeIndoorRoute, "Active route should be set")
        assertEquals(start, mapState.indoorRouteStartLocation)
        assertEquals(end, mapState.indoorRouteEndLocation)
        assertEquals(start.floorNumber, mapState.currentFloorNumber)

        // 2. Swap endpoints
        mapState.swapIndoorRouteEndpoints()
        assertEquals(end, mapState.indoorRouteStartLocation)
        assertEquals(start, mapState.indoorRouteEndLocation)
        assertNotNull(mapState.activeIndoorRoute)

        // 3. Clear route
        mapState.clearIndoorRoute()
        assertNull(mapState.activeIndoorRoute)
        assertNull(mapState.indoorRouteStartLocation)
        assertNull(mapState.indoorRouteEndLocation)
    }
}
