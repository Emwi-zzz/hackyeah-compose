package backend

import backend.api.apiStatusPages
import backend.api.indoorApi
import backend.auth.*
import backend.db.MallRepository
import backend.data.MallCatalog
import backend.db.Database
import backend.db.DbConfig
import backend.db.PostgresMallRepository
import backend.routing.IndoorRoutingService
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.routing.*

fun Application.module(
    catalog: MallCatalog,
    malls: MallRepository,
    users: UserRepository,
    jwt: JwtService,
    routingService: IndoorRoutingService = IndoorRoutingService(),
) {
    install(ContentNegotiation) { json() }
    // Allows the wasm/web frontend served from another origin to call the API
    install(CORS) {
        anyHost()
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
    }
    apiStatusPages()
    installAuth(jwt, users)
    routing {
        indoorApi(catalog, routingService)
        authApi(users, jwt)
        adminApi(catalog, malls)
    }
}

fun main() {
    val ds = Database.connect(DbConfig.fromEnv())
    val repository = PostgresMallRepository(ds)
    val catalog = MallCatalog.load(repository)
    val users = PostgresUserRepository(ds)
    bootstrapAdmin(users, System.getenv("ADMIN_USERNAME"), System.getenv("ADMIN_PASSWORD"))
    val jwt = JwtService.fromEnv()

    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    val host = System.getenv("HOST") ?: "0.0.0.0"
    embeddedServer(CIO, port = port, host = host) { module(catalog, repository, users, jwt) }.start(wait = true)
}
