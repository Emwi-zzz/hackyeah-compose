package backend

import api.NavLocationDto
import api.PointDto
import backend.data.MallCatalog
import backend.db.PostgresMallRepository
import backend.routing.IndoorRoutingService
import sklepsearch.*
import kotlin.math.hypot
import kotlin.test.*

class IndoorRoutingServiceTest {
    private val catalog = MallCatalog.load(PostgresMallRepository(TestDb.freshDataSource()))
    private val service = IndoorRoutingService()
    private val krakowska = catalog.find(1L)!!

    @Test
    fun navLocationsContainExitsAndStores() {
        val locations = service.navLocations(krakowska)
        assertTrue(locations.count { it.type == "EXIT" } >= 3)
        assertTrue(locations.count { it.type == "STORE" } >= 8)
        assertEquals(locations.size, locations.map { it.id }.distinct().size, "ids must be unique")
    }

    @Test
    fun exitNamesComeFromDatabase() {
        val exits = service.navLocations(krakowska).filter { it.type == "EXIT" }.map { it.name }
        assertEquals("North entrance (Station Square)", exits.first())
        val mall = catalog.find(1L)!!
        assertEquals(mall.entryPointNames, exits)
    }

    @Test
    fun singleFloorRoute() {
        val stores = service.navLocations(krakowska).filter { it.type == "STORE" && it.floorNumber == 0 }
        val route = service.calculateRoute(krakowska, stores[0], stores[1]).getOrThrow()
        assertEquals(1, route.levels.size)
        assertTrue(route.levels[0].waypoints.size >= 2)
        assertTrue(route.totalDistanceMeters > 0.0)
        assertTrue(route.estimatedTimeSeconds >= 30)
        assertTrue(route.levels[0].instructions.isNotEmpty())
    }

    @Test
    fun multiFloorRouteVisitsEveryFloor() {
        val locations = service.navLocations(krakowska)
        val from = locations.first { it.floorNumber == -1 && it.type == "STORE" }
        val to = locations.first { it.floorNumber == 1 && it.type == "STORE" }
        val route = service.calculateRoute(krakowska, from, to).getOrThrow()
        assertEquals(listOf(-1, 0, 1), route.levels.map { it.floorNumber })
        route.levels.forEach { assertTrue(it.waypoints.isNotEmpty()) }
    }

    @Test
    fun accessibleRouteUsesAccessibleVerticalConnections() {
        val locations = service.navLocations(krakowska)
        val from = locations.first { it.floorNumber == -1 && it.type == "STORE" }
        val to = locations.first { it.floorNumber == 1 && it.type == "STORE" }
        val route = service.calculateRoute(krakowska, from, to, accessibleOnly = true).getOrThrow()

        assertEquals(listOf(-1, 0, 1), route.levels.map { it.floorNumber })
    }

    @Test
    fun singleFloorRouteAvoidsShopsAndFloorVoids() {
        val mall = Mall(
            id = 99,
            name = "Test mall",
            size = Size(100, 100),
            upperLeft = GeoPoint(19.0, 50.0),
            downRight = GeoPoint(19.001, 49.999),
            minFloor = 0,
            entryPoints = emptyList(),
            floors = listOf(
                Floor(
                    number = 0,
                    box = Path2D.rectangle(0.0, 0.0, 100.0, 100.0),
                    stores = listOf(
                        Store(
                            Instanceid = 1,
                            Shopid = 1,
                            name = "Obstacle shop",
                            area = Path2D.rectangle(35.0, 25.0, 10.0, 50.0),
                            entryPoints = listOf(Point(35.0, 50.0))
                        )
                    ),
                    elevators = emptyList(),
                    escalators = emptyList(),
                    voids = listOf(Path2D.rectangle(55.0, 25.0, 10.0, 50.0))
                )
            )
        )
        val start = NavLocationDto("start", "Start", "STORE", 0, PointDto(10.0, 50.0))
        val end = NavLocationDto("end", "End", "STORE", 0, PointDto(90.0, 50.0))

        val route = service.calculateRoute(mall, start, end).getOrThrow()
        val blockedAreas = mall.floors.single().stores.map { it.area } + mall.floors.single().voids

        assertTrue(route.levels.single().waypoints.size > 2)
        route.levels.single().waypoints.zipWithNext().forEach { (a, b) ->
            for (step in 1..9) {
                val t = step / 10.0
                val point = Point(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
                assertTrue(blockedAreas.none { strictlyInside(it, point) }, "Route crosses a blocked area at $point")
            }
        }
    }

    @Test
    fun routeDoesNotSqueezeBetweenShopsSharingAWall() {
        fun shop(id: Long, area: Path2D) = Store(
            Instanceid = id, Shopid = id, name = "Shop $id", area = area, entryPoints = emptyList()
        )
        val mall = Mall(
            id = 98,
            name = "Touching shops",
            size = Size(100, 100),
            upperLeft = GeoPoint(19.0, 50.0),
            downRight = GeoPoint(19.001, 49.999),
            minFloor = 0,
            entryPoints = emptyList(),
            floors = listOf(
                Floor(
                    number = 0,
                    box = Path2D.rectangle(0.0, 0.0, 100.0, 100.0),
                    stores = listOf(
                        shop(1, Path2D.rectangle(30.0, 25.0, 20.0, 15.0)),
                        shop(2, Path2D.rectangle(30.0, 40.0, 20.0, 35.0))
                    ),
                    elevators = emptyList(),
                    escalators = emptyList()
                )
            )
        )
        val start = NavLocationDto("start", "Start", "STORE", 0, PointDto(10.0, 40.0))
        val end = NavLocationDto("end", "End", "STORE", 0, PointDto(90.0, 40.0))

        val waypoints = service.calculateRoute(mall, start, end).getOrThrow().levels.single().waypoints

        assertTrue(waypoints.size > 2, "Route walked along the shared wall: $waypoints")
        assertTrue(waypoints.any { it.y <= 25.0 + 1e-6 || it.y >= 75.0 - 1e-6 }, "Route must go around both shops")
    }

    @Test
    fun adjacentKrakowskaFoodStandsAreNotAShortcut() {
        val locations = service.navLocations(krakowska)
        val pierogi = locations.first { it.id == "store_20156" } // Pierogi Republic, shop 505
        val churros = locations.first { it.id == "store_20155" } // Churros Calle, shop 602
        val waypoints = service.calculateRoute(krakowska, pierogi, churros).getOrThrow()
            .levels.single { it.floorNumber == 1 }.waypoints

        waypoints.zipWithNext().forEach { (a, b) ->
            for (step in 1..9) {
                val t = step / 10.0
                val x = a.x + (b.x - a.x) * t
                val y = a.y + (b.y - a.y) * t
                val insideBlock = x > 42.05 && x < 61.95 && y > 196.05 && y < 203.95
                val onSharedWall = kotlin.math.abs(x - 52.0) < 0.05 && y in 196.0..204.0
                assertTrue(!insideBlock && !onSharedWall, "Path cuts through 505/602 at $x,$y: $waypoints")
            }
        }
        assertTrue(
            waypoints.any { it.y > 204.2 || it.y < 195.8 },
            "Path stayed on the shared shop front: $waypoints"
        )
    }

    private fun strictlyInside(path: Path2D, point: Point): Boolean {
        if (!path.contains(point)) return false
        val vertices = path.getVertices()
        return vertices.indices.none { index ->
            val a = vertices[index]
            val b = vertices[(index + 1) % vertices.size]
            val dx = b.x - a.x
            val dy = b.y - a.y
            val lengthSquared = dx * dx + dy * dy
            val t = if (lengthSquared == 0.0) 0.0 else
                (((point.x - a.x) * dx + (point.y - a.y) * dy) / lengthSquared).coerceIn(0.0, 1.0)
            hypot(point.x - (a.x + t * dx), point.y - (a.y + t * dy)) < 1e-4
        }
    }
}
