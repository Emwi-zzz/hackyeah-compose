package features.indoor.data

import api.*
import api.DtoMapper.toDomain
import core.network.BackendConfig
import core.network.PlatformHttp
import features.indoor.domain.*
import kotlinx.serialization.json.Json
import sklepsearch.Mall
import sklepsearch.Point

/** Fetches malls, navigation targets and routes from the backend REST API. */
class RemoteIndoorRoutingRepository(
    private val baseUrl: () -> String = { BackendConfig.baseUrl }
) : IndoorRoutingRepository {

    private val json = Json { ignoreUnknownKeys = true }

    private suspend inline fun <reified T> get(path: String): Result<T> {
        val text = PlatformHttp.getText(baseUrl().trimEnd('/') + path)
            ?: return Result.failure(IllegalStateException("Backend request failed: $path"))
        return runCatching { json.decodeFromString<T>(text) }
    }

    override suspend fun getMalls(): Result<List<Mall>> = runCatching {
        val summaries = get<List<MallSummaryDto>>(ApiPaths.MALLS).getOrThrow()
        summaries.map { get<MallDto>(ApiPaths.mall(it.id)).getOrThrow().toDomain() }
    }

    override suspend fun getNavLocations(mallId: Long): Result<List<NavLocation>> =
        get<List<NavLocationDto>>(ApiPaths.locations(mallId)).map { list -> list.map { it.toNav() } }

    override suspend fun calculateRoute(
        mallId: Long,
        start: NavLocation,
        end: NavLocation,
        accessibleOnly: Boolean
    ): Result<IndoorRoute> =
        get<RouteDto>(ApiPaths.route(mallId, start.id, end.id, accessibleOnly)).map { it.toRoute() }

    private fun NavLocationDto.toNav() = NavLocation(
        id = id,
        name = name,
        type = NavLocationType.valueOf(type),
        floorNumber = floorNumber,
        coordinates = Point(position.x, position.y),
        category = category
    )

    // The backend sends plain waypoints; the smooth Bézier curve is built here
    private fun RouteDto.toRoute() = IndoorRoute(
        mallId = mallId,
        startLocation = start.toNav(),
        endLocation = end.toNav(),
        levels = levels.map { l ->
            val points = l.waypoints.map { Point(it.x, it.y) }
            FloorLevelRoute(
                floorNumber = l.floorNumber,
                waypoints = points,
                bezierPath = CatmullRomBezierSpline.createSpline(points),
                instructions = l.instructions,
                distanceMeters = l.distanceMeters
            )
        },
        totalDistanceMeters = totalDistanceMeters,
        estimatedTimeSeconds = estimatedTimeSeconds
    )
}
