package backend

import api.*
import backend.auth.*
import backend.data.MallCatalog
import backend.db.PostgresMallRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientNegotiation
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlin.test.*

class AuthApiTest {
    private val ds = TestDb.freshDataSource()
    private val malls = PostgresMallRepository(ds)
    private val users = PostgresUserRepository(ds)
    private val catalog = MallCatalog.load(malls)
    private val jwt = JwtService("s".repeat(40))

    init {
        bootstrapAdmin(users, "boss", "super-secret-1")
    }

    private fun ApplicationTestBuilder.client(): HttpClient {
        application { module(catalog, malls, users, jwt) }
        return createClient { install(ClientNegotiation) { json() } }
    }

    private suspend fun HttpClient.token(user: String, pass: String): String =
        post(ApiPaths.LOGIN) { contentType(ContentType.Application.Json); setBody(CredentialsRequest(user, pass)) }
            .body<TokenResponse>().token

    @Test
    fun passwordHasherVerifiesOnlyCorrectPassword() {
        val h = PasswordHasher.hash("correct horse")
        assertTrue(PasswordHasher.verify("correct horse", h))
        assertFalse(PasswordHasher.verify("wrong", h))
        assertFalse(PasswordHasher.verify("x", "garbage"))
        assertNotEquals(h, PasswordHasher.hash("correct horse"), "salt must differ")
    }

    @Test
    fun registerLoginAndMe() = testApplication {
        val c = client()
        val reg = c.post(ApiPaths.REGISTER) { contentType(ContentType.Application.Json); setBody(CredentialsRequest("Alice", "password123")) }
        assertEquals(HttpStatusCode.Created, reg.status)
        assertEquals("USER", reg.body<TokenResponse>().user.role)

        val token = c.token("alice", "password123") // case-insensitive username
        val me = c.get(ApiPaths.ME) { bearerAuth(token) }
        assertEquals(HttpStatusCode.OK, me.status)
        assertEquals("Alice", me.body<UserDto>().username)

        val dup = c.post(ApiPaths.REGISTER) { contentType(ContentType.Application.Json); setBody(CredentialsRequest("ALICE", "password123")) }
        assertEquals(HttpStatusCode.Conflict, dup.status)
    }

    @Test
    fun badCredentialsAndValidation() = testApplication {
        val c = client()
        val bad = c.post(ApiPaths.LOGIN) { contentType(ContentType.Application.Json); setBody(CredentialsRequest("boss", "nope")) }
        assertEquals(HttpStatusCode.Unauthorized, bad.status)
        val unknown = c.post(ApiPaths.LOGIN) { contentType(ContentType.Application.Json); setBody(CredentialsRequest("ghost", "whatever123")) }
        assertEquals(HttpStatusCode.Unauthorized, unknown.status)
        val shortPw = c.post(ApiPaths.REGISTER) { contentType(ContentType.Application.Json); setBody(CredentialsRequest("bob", "short")) }
        assertEquals(HttpStatusCode.BadRequest, shortPw.status)
        assertEquals(HttpStatusCode.Unauthorized, c.get(ApiPaths.ME).status)
        assertEquals(HttpStatusCode.Unauthorized, c.get(ApiPaths.ME) { bearerAuth("not.a.jwt") }.status)
    }

    @Test
    fun readApiStaysPublic() = testApplication {
        assertEquals(HttpStatusCode.OK, client().get(ApiPaths.MALLS).status)
    }

    @Test
    fun putPreservesCurvesWhenSvgIsSent() = testApplication {
        val c = client()
        val admin = c.token("boss", "super-secret-1")
        val original = c.get(ApiPaths.mall(2)).body<MallDto>()
        assertNotNull(original.floors[0].outlineSvg)

        // Round trip with svg: curves survive. Without svg (polygon only): they are flattened.
        c.put(ApiPaths.adminMall(2)) { bearerAuth(admin); contentType(ContentType.Application.Json); setBody(original) }
        assertEquals(original.floors[0].outlineSvg, c.get(ApiPaths.mall(2)).body<MallDto>().floors[0].outlineSvg)
        assertTrue(original.floors[0].outlineSvg!!.contains(" C "))

        val flat = original.copy(floors = original.floors.map { it.copy(outlineSvg = null) })
        c.put(ApiPaths.adminMall(2)) { bearerAuth(admin); contentType(ContentType.Application.Json); setBody(flat) }
        assertFalse(c.get(ApiPaths.mall(2)).body<MallDto>().floors[0].outlineSvg!!.contains(" C "))

        val broken = original.copy(floors = original.floors.map { it.copy(outlineSvg = "M 1 2 L x y") })
        val r = c.put(ApiPaths.adminMall(2)) { bearerAuth(admin); contentType(ContentType.Application.Json); setBody(broken) }
        assertEquals(HttpStatusCode.BadRequest, r.status)
    }

    @Test
    fun adminEndpointsRequireAdminRole() = testApplication {
        val c = client()
        val body = c.get(ApiPaths.mall(1)).body<MallDto>()

        assertEquals(HttpStatusCode.Unauthorized, c.delete(ApiPaths.adminMall(2)).status)

        c.post(ApiPaths.REGISTER) { contentType(ContentType.Application.Json); setBody(CredentialsRequest("plain", "password123")) }
        val userToken = c.token("plain", "password123")
        val forbidden = c.put(ApiPaths.adminMall(1)) { bearerAuth(userToken); contentType(ContentType.Application.Json); setBody(body) }
        assertEquals(HttpStatusCode.Forbidden, forbidden.status)
        assertEquals(HttpStatusCode.Forbidden, c.delete(ApiPaths.adminMall(2)) { bearerAuth(userToken) }.status)

        val admin = c.token("boss", "super-secret-1")
        val renamed = body.copy(name = "Renamed by admin")
        assertEquals(HttpStatusCode.OK, c.put(ApiPaths.adminMall(1)) { bearerAuth(admin); contentType(ContentType.Application.Json); setBody(renamed) }.status)
        assertEquals("Renamed by admin", c.get(ApiPaths.mall(1)).body<MallDto>().name)

        val mismatch = c.put(ApiPaths.adminMall(5)) { bearerAuth(admin); contentType(ContentType.Application.Json); setBody(renamed) }
        assertEquals(HttpStatusCode.BadRequest, mismatch.status)

        assertEquals(HttpStatusCode.NoContent, c.delete(ApiPaths.adminMall(2)) { bearerAuth(admin) }.status)
        assertEquals(HttpStatusCode.NotFound, c.get(ApiPaths.mall(2)).status)
        assertEquals(HttpStatusCode.NotFound, c.delete(ApiPaths.adminMall(2)) { bearerAuth(admin) }.status)
    }

    @Test
    fun adminCreatesMallWithOutlineAndServerAssignedIds() = testApplication {
        val c = client()
        val admin = c.token("boss", "super-secret-1")
        val outline = listOf(PointDto(0.0, 0.0), PointDto(1000.0, 0.0), PointDto(1000.0, 500.0), PointDto(0.0, 500.0))
        val base = c.get(ApiPaths.mall(1)).body<MallDto>()
        val shop = base.floors[0].stores.first().copy(instanceId = -1, shopId = -1, name = "New shop")
        val draft = base.copy(
            id = -1, name = "Drawn mall", outline = outline, outlineSvg = null, entryPoints = emptyList(),
            entryPointNames = emptyList(), floors = listOf(base.floors[0].copy(stores = listOf(shop)))
        )
        assertEquals(HttpStatusCode.Unauthorized, c.post(ApiPaths.ADMIN_MALLS) {
            contentType(ContentType.Application.Json); setBody(draft)
        }.status)

        val created = c.post(ApiPaths.ADMIN_MALLS) {
            bearerAuth(admin); contentType(ContentType.Application.Json); setBody(draft)
        }
        assertEquals(HttpStatusCode.Created, created.status)
        val newId = Regex("\\d+").find(created.bodyAsText())!!.value.toLong()

        val stored = c.get(ApiPaths.mall(newId)).body<MallDto>()
        assertEquals("Drawn mall", stored.name)
        assertEquals(4, stored.outline.size)
        assertTrue(stored.floors[0].stores.single().instanceId > 0)

        val blank = c.post(ApiPaths.ADMIN_MALLS) {
            bearerAuth(admin); contentType(ContentType.Application.Json); setBody(draft.copy(name = " "))
        }
        assertEquals(HttpStatusCode.BadRequest, blank.status)
    }}

