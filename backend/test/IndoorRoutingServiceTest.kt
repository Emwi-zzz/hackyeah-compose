package backend

import backend.data.MallCatalog
import backend.db.PostgresMallRepository
import backend.routing.IndoorRoutingService
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
        assertEquals("Main Entrance (Kraków Główny / Pawia)", exits.first())
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
}
