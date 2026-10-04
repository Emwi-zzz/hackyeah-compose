package backend

import backend.api.apiStatusPages
import backend.api.indoorApi
import backend.auth.*
import backend.db.MallRepository
import backend.data.MallCatalog
import backend.db.Database
import backend.db.DbConfig
import backend.db.PostgresMallRepository
import backend.outdoor.OutdoorRoutingService
import backend.outdoor.WalkGraph
import backend.routing.IndoorRoutingService
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.routing.*
import java.io.File

fun Application.module(
    catalog: MallCatalog,
    malls: MallRepository,
    users: UserRepository,
    jwt: JwtService,
    routingService: IndoorRoutingService = IndoorRoutingService(),
    walkGraph: WalkGraph? = null,
) {
    val outdoorService = walkGraph?.let { OutdoorRoutingService(it, routingService) }
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
        indoorApi(catalog, routingService, outdoorService)
        authApi(users, jwt)
        adminApi(catalog, malls, users)
    }
}

fun main() {
    val ds = Database.connect(DbConfig.fromEnv())
    val repository = PostgresMallRepository(ds)
    val catalog = MallCatalog.load(repository)
    val users = PostgresUserRepository(ds)
    bootstrapAdmin(users, System.getenv("ADMIN_USERNAME"), System.getenv("ADMIN_PASSWORD"))
    val jwt = JwtService.fromEnv()
    val walkGraph = loadWalkGraph(File(System.getenv("WALK_GRAPH") ?: "data/krakow-walk.graph.gz"))

    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    val host = System.getenv("HOST") ?: "0.0.0.0"
    embeddedServer(CIO, port = port, host = host) {
        module(catalog, repository, users, jwt, walkGraph = walkGraph)
    }.start(wait = true)
}

private fun loadWalkGraph(file: File): WalkGraph? {
    if (!file.exists()) {
        println("Walk graph ${file.absolutePath} not found: outdoor routing disabled")
        return null
    }
    val started = System.currentTimeMillis()
    return WalkGraph.load(file).also {
        println("Walk graph loaded: ${it.nodeCount} nodes, ${it.edgeCount} edges in ${System.currentTimeMillis() - started} ms")
    }
}
