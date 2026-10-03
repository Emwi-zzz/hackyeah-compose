package backend

import api.*
import backend.data.MallCatalog
import backend.db.PostgresMallRepository
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientNegotiation
import kotlin.test.*

class ApiTest {
    // Built eagerly so container startup is not counted against the test timeout
    private val ds = TestDb.freshDataSource()
    private val repo = PostgresMallRepository(ds)
    private val catalog = MallCatalog.load(repo)

    private fun ApplicationTestBuilder.jsonClient() = createClient { install(ClientNegotiation) { json() } }

    @Test
    fun healthMallsAndLocations() = testApplication {
        application { module(catalog, repo, backend.auth.PostgresUserRepository(ds), backend.auth.JwtService("x".repeat(40))) }
        val client = jsonClient()
        assertEquals(HttpStatusCode.OK, client.get("/health").status)

        val malls: List<MallSummaryDto> = client.get(ApiPaths.MALLS).body()
        assertEquals(setOf(1L, 2L), malls.map { it.id }.toSet())

        val mall: MallDto = client.get(ApiPaths.mall(2)).body()
        assertTrue(mall.floors.isNotEmpty())
        assertTrue(mall.floors.first().outline.size >= 3)
        assertTrue(mall.floors.first().stores.all { it.outline.size >= 3 })

        val locations: List<NavLocationDto> = client.get(ApiPaths.locations(1)).body()
        assertTrue(locations.isNotEmpty())
    }

    @Test
    fun routeEndpoint() = testApplication {
        application { module(catalog, repo, backend.auth.PostgresUserRepository(ds), backend.auth.JwtService("x".repeat(40))) }
        val client = jsonClient()
        val locations: List<NavLocationDto> = client.get(ApiPaths.locations(1)).body()
        val from = locations.first { it.type == "EXIT" }
        val to = locations.first { it.type == "STORE" && it.floorNumber == 1 }

        val route: RouteDto = client.get(ApiPaths.route(1, from.id, to.id)).body()
        assertEquals(from.id, route.start.id)
        assertEquals(to.floorNumber, route.levels.last().floorNumber)
    }

    @Test
    fun errors() = testApplication {
        application { module(catalog, repo, backend.auth.PostgresUserRepository(ds), backend.auth.JwtService("x".repeat(40))) }
        val client = jsonClient()
        assertEquals(HttpStatusCode.NotFound, client.get(ApiPaths.mall(999)).status)
        assertEquals(HttpStatusCode.BadRequest, client.get("${ApiPaths.mall(1)}/route?from=x").status)
        val unknown = client.get(ApiPaths.route(1, "nope", "nada"))
        assertEquals(HttpStatusCode.NotFound, unknown.status)
        assertEquals("location_not_found", unknown.body<ErrorDto>().error)
    }
}
