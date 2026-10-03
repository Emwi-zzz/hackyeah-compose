package backend.api

import api.*
import api.DtoMapper.toDto
import api.DtoMapper.toSummaryDto
import backend.data.MallCatalog
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

fun Route.indoorApi(catalog: MallCatalog, routing: IndoorRoutingService) {
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
            val route = routing.calculateRoute(mall, start, end).getOrElse {
                throw ApiException(HttpStatusCode.UnprocessableEntity, "route_failed", it.message ?: "Route failed")
            }
            call.respond(route)
        }
    }
}

private fun ApplicationCall.mall(catalog: MallCatalog) =
    parameters["mallId"]?.toLongOrNull()?.let { catalog.find(it) }
        ?: throw ApiException(HttpStatusCode.NotFound, "mall_not_found", "Mall '${parameters["mallId"]}' not found")
