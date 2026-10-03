package test

import core.geometry.GeoPoint
import features.indoor.data.IndoorRoutingRepositoryImpl
import features.indoor.domain.CatmullRomBezierSpline
import features.indoor.domain.NavLocation
import features.indoor.domain.NavLocationType
import features.map.data.TileRepositoryImpl
import features.map.presentation.MapState
import features.rendering.domain.LayerRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import sklepsearch.*
import kotlin.test.*

class IndoorRoutingTest {

    private val repository = IndoorRoutingRepositoryImpl()

    private fun createMapState(): MapState {
        val tileRepo = TileRepositoryImpl()
        val layerRegistry = LayerRegistry(emptyList())
        val scope = CoroutineScope(Dispatchers.Unconfined)
        return MapState(tileRepo, layerRegistry, scope)
    }

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
    fun testGetAllNavLocationsForMalls() {
        // Galeria Krakowska (id = 1)
        val krakowskaLocations = repository.getAllNavLocations(1L)
        assertTrue(krakowskaLocations.isNotEmpty(), "Krakowska should have navigation locations")

        val exits = krakowskaLocations.filter { it.type == NavLocationType.EXIT }
        assertTrue(exits.size >= 3, "Krakowska should have at least 3 exits")

        val stores = krakowskaLocations.filter { it.type == NavLocationType.STORE }
        assertTrue(stores.size >= 8, "Krakowska should have stores across its 3 floors")

        // Exits must have non-empty names and valid coordinates
        for (exit in exits) {
            assertTrue(exit.name.isNotBlank())
            assertTrue(exit.coordinates.x >= 0.0)
            assertTrue(exit.coordinates.y >= 0.0)
        }

        // Galeria Kazimierz (id = 2)
        val kazimierzLocations = repository.getAllNavLocations(2L)
        assertTrue(kazimierzLocations.isNotEmpty(), "Kazimierz should have navigation locations")
        assertTrue(kazimierzLocations.any { it.type == NavLocationType.EXIT })
        assertTrue(kazimierzLocations.any { it.type == NavLocationType.STORE })
    }

    @Test
    fun testSingleFloorRoutingBetweenStores() {
        val locations = repository.getAllNavLocations(1L)
        val floor0Stores = locations.filter { it.type == NavLocationType.STORE && it.floorNumber == 0 }
        assertTrue(floor0Stores.size >= 2, "Floor 0 must have at least 2 stores")

        val start = floor0Stores[0]
        val end = floor0Stores[1]

        val result = repository.calculateRouteSync(1L, start, end)
        assertTrue(result.isSuccess, "Single floor routing should succeed")

        val route = result.getOrThrow()
        assertEquals(1L, route.mallId)
        assertEquals(start, route.startLocation)
        assertEquals(end, route.endLocation)
        assertEquals(1, route.levels.size, "Single floor route must have exactly 1 level")

        val level0 = route.levels[0]
        assertEquals(0, level0.floorNumber)
        assertTrue(level0.waypoints.size >= 2, "Level must have waypoints connecting start and end")
        assertTrue(level0.distanceMeters > 0.0, "Distance must be positive")
        assertTrue(route.estimatedTimeSeconds > 0, "Time must be positive")
        assertTrue(level0.bezierPath.segments.isNotEmpty(), "Bézier curve must have segments")
        assertTrue(level0.instructions.isNotEmpty(), "Level must have directions")
    }

    @Test
    fun testMultiFloorRoutingWithVerticalTransport() {
        val locations = repository.getAllNavLocations(1L)
        val floorMinus1Store = locations.firstOrNull { it.floorNumber == -1 && it.type == NavLocationType.STORE }
        val floorPlus1Store = locations.firstOrNull { it.floorNumber == 1 && it.type == NavLocationType.STORE }

        assertNotNull(floorMinus1Store, "Floor -1 store should exist")
        assertNotNull(floorPlus1Store, "Floor 1 store should exist")

        val result = repository.calculateRouteSync(1L, floorMinus1Store, floorPlus1Store)
        assertTrue(result.isSuccess, "Multi-floor routing should succeed")

        val route = result.getOrThrow()
        assertEquals(3, route.levels.size, "Route from -1 to 1 should have 3 levels (-1, 0, 1)")

        // Check levels order
        assertEquals(-1, route.levels[0].floorNumber)
        assertEquals(0, route.levels[1].floorNumber)
        assertEquals(1, route.levels[2].floorNumber)

        // Each level must have its own Bézier curve
        for (lvl in route.levels) {
            assertTrue(lvl.bezierPath.segments.isNotEmpty(), "Level ${lvl.floorNumber} must have Bézier path")
            assertTrue(lvl.waypoints.isNotEmpty(), "Level ${lvl.floorNumber} must have waypoints")
            assertTrue(lvl.instructions.isNotEmpty(), "Level ${lvl.floorNumber} must have instructions")
        }

        assertTrue(route.totalDistanceMeters > 0.0)
        assertTrue(route.estimatedTimeSeconds >= 30)
    }

    @Test
    fun testExitToStoreRouting() {
        val locations = repository.getAllNavLocations(1L)
        val exit = locations.first { it.type == NavLocationType.EXIT }
        val store = locations.first { it.type == NavLocationType.STORE }

        val result = repository.calculateRouteSync(1L, exit, store)
        assertTrue(result.isSuccess)

        val route = result.getOrThrow()
        assertEquals(exit, route.startLocation)
        assertEquals(store, route.endLocation)
        assertTrue(route.levels.isNotEmpty())
    }

    @Test
    fun testStoreToExitRouting() {
        val locations = repository.getAllNavLocations(1L)
        val exit = locations.first { it.type == NavLocationType.EXIT }
        val store = locations.first { it.type == NavLocationType.STORE }

        val result = repository.calculateRouteSync(1L, store, exit)
        assertTrue(result.isSuccess)

        val route = result.getOrThrow()
        assertEquals(store, route.startLocation)
        assertEquals(exit, route.endLocation)
        assertTrue(route.levels.isNotEmpty())
    }

    @Test
    fun testMapStateIndoorNavigationWorkflow() {
        val mapState = createMapState()
        mapState.refocusOnMall(MockGaleriaKrakowska.INSTANCE, animate = false)

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

    @Test
    fun testInvalidMallIdReturnsFailure() {
        val start = NavLocation("dummy_1", "Start", NavLocationType.EXIT, 0, Point(0.0, 0.0))
        val end = NavLocation("dummy_2", "End", NavLocationType.STORE, 0, Point(10.0, 10.0))
        val result = repository.calculateRouteSync(99999L, start, end)
        assertTrue(result.isFailure)
    }
}
