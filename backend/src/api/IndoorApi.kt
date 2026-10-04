package backend.api

import api.*
import api.DtoMapper.toDto
import api.DtoMapper.toSummaryDto
import backend.data.MallCatalog
import backend.outdoor.OutdoorRoutingService
import backend.routing.IndoorRoutingService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

class ApiException(val status: HttpStatusCode, val code: String, message: String) : RuntimeException(message)

fun Application.apiStatusPages() {
    install(StatusPages) {
        exception<ApiException> { call, e ->
            call.respond(e.status, ErrorDto(e.code, e.message ?: e.code))
        }
        exception<io.ktor.server.plugins.BadRequestException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ErrorDto("bad_request", "Malformed request body"))
        }
        exception<Throwable> { call, e ->
            call.respond(HttpStatusCode.InternalServerError, ErrorDto("internal_error", e.message ?: "Internal error"))
        }
    }
}

fun Route.indoorApi(catalog: MallCatalog, routing: IndoorRoutingService, outdoor: OutdoorRoutingService? = null) {
    get("/health") { call.respond(mapOf("status" to "ok")) }

    route(ApiPaths.MALLS) {
        get {
            call.respond(catalog.malls.map { it.toSummaryDto() })
        }
        get("{mallId}") {
            call.respond(call.mall(catalog).toDto())
        }
        get("{mallId}/locations") {
            call.respond(routing.navLocations(call.mall(catalog)))
        }
        get("{mallId}/route") {
            val mall = call.mall(catalog)
            val fromId = call.request.queryParameters["from"]
                ?: throw ApiException(HttpStatusCode.BadRequest, "bad_request", "Query parameter 'from' is required")
            val toId = call.request.queryParameters["to"]
                ?: throw ApiException(HttpStatusCode.BadRequest, "bad_request", "Query parameter 'to' is required")
            val locations = routing.navLocations(mall).associateBy { it.id }
            val start = locations[fromId]
                ?: throw ApiException(HttpStatusCode.NotFound, "location_not_found", "Location '$fromId' not found in mall ${mall.id}")
            val end = locations[toId]
                ?: throw ApiException(HttpStatusCode.NotFound, "location_not_found", "Location '$toId' not found in mall ${mall.id}")
            val accessibleOnly = call.request.queryParameters["accessible"]?.toBooleanStrictOrNull() ?: false
            val route = routing.calculateRoute(mall, start, end, accessibleOnly).getOrElse {
                throw ApiException(HttpStatusCode.UnprocessableEntity, "route_failed", it.message ?: "Route failed")
            }
            call.respond(route)
        }
        get("{mallId}/approach") {
            val mall = call.mall(catalog)
            val service = outdoor ?: throw ApiException(
                HttpStatusCode.ServiceUnavailable, "outdoor_unavailable",
                "Outdoor routing is not configured: build the walk graph (see docs-backend.md)"
            )
            val params = call.request.queryParameters
            val lat = params["lat"]?.toDoubleOrNull()
                ?: throw ApiException(HttpStatusCode.BadRequest, "bad_request", "Query parameter 'lat' is required")
            val lon = params["lon"]?.toDoubleOrNull()
                ?: throw ApiException(HttpStatusCode.BadRequest, "bad_request", "Query parameter 'lon' is required")
            val destination = params["to"]?.let { toId ->
                routing.navLocations(mall).find { it.id == toId }
                    ?: throw ApiException(HttpStatusCode.NotFound, "location_not_found", "Location '$toId' not found in mall ${mall.id}")
            }
            val accessibleOnly = params["accessible"]?.toBooleanStrictOrNull() ?: false
            val route = try {
                service.approach(mall, lat, lon, destination, accessibleOnly)
            } catch (e: OutdoorRoutingService.RoutingException) {
                throw ApiException(HttpStatusCode.UnprocessableEntity, e.code, e.message ?: e.code)
            }
            call.respond(route)
        }
    }
}

private fun ApplicationCall.mall(catalog: MallCatalog) =
    parameters["mallId"]?.toLongOrNull()?.let { catalog.find(it) }
        ?: throw ApiException(HttpStatusCode.NotFound, "mall_not_found", "Mall '${parameters["mallId"]}' not found")
