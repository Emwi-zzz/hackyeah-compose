package api

import kotlinx.serialization.Serializable

/**
 * Wire format of the Indoor backend REST API (v1).
 *
 * Geometry is transferred as plain points in the mall's local coordinate system (0..size.x, 0..size.y).
 * Polygons (floor outlines, store shapes) are implicitly closed; routes are open polylines.
 * Bezier curves are NOT transferred - the client draws them through the points.
 */

@Serializable
data class PointDto(val x: Double, val y: Double)

@Serializable
data class GeoPointDto(val latitude: Double, val longitude: Double)

@Serializable
data class MallSummaryDto(
    val id: Long,
    val name: String,
    val sizeX: Int,
    val sizeY: Int,
    val upperLeft: GeoPointDto,
    val downRight: GeoPointDto,
    val minFloor: Int,
    val floorNumbers: List<Int>,
)

@Serializable
data class MallDto(
    val id: Long,
    val name: String,
    val sizeX: Int,
    val sizeY: Int,
    val upperLeft: GeoPointDto,
    val downRight: GeoPointDto,
    val minFloor: Int,
    val entryPoints: List<PointDto>,
    val entryPointNames: List<String> = emptyList(),
    val floors: List<FloorDto>,
    /** Building footprint (polygon). The exact path with curves is in [outlineSvg]. */
    val outline: List<PointDto> = emptyList(),
    val outlineSvg: String? = null,
)

@Serializable
data class FloorDto(
    val number: Int,
    val outline: List<PointDto>,
    /** Exact outline incl. curves as SVG path. Optional: on PUT it wins over [outline] when present. */
    val outlineSvg: String? = null,
    val stores: List<StoreDto>,
    val elevators: List<ElevatorDto>,
    val escalators: List<EscalatorDto>,
    /** SVG paths for floor areas that cannot be walked through, such as atria. */
    val voids: List<String> = emptyList(),
)

@Serializable
data class StoreDto(
    val instanceId: Long,
    val shopId: Long,
    val name: String,
    val category: String,
    val description: String? = null,
    val outline: List<PointDto>,
    val outlineSvg: String? = null,
    val entryPoints: List<PointDto>,
)

@Serializable
data class ElevatorDto(val id: Long, val position: PointDto, val isAccessible: Boolean = true)

@Serializable
data class EscalatorDto(
    val id: Long,
    val position: PointDto,
    val direction: String,
    val isAccessible: Boolean = false
) // "UP" | "DOWN"

@Serializable
data class NavLocationDto(
    val id: String,
    val name: String,
    val type: String, // "STORE" | "EXIT"
    val floorNumber: Int,
    val position: PointDto,
    val category: String? = null,
)

@Serializable
data class RouteLevelDto(
    val floorNumber: Int,
    val waypoints: List<PointDto>,
    val instructions: List<String>,
    val distanceMeters: Double,
)

@Serializable
data class RouteDto(
    val mallId: Long,
    val start: NavLocationDto,
    val end: NavLocationDto,
    val levels: List<RouteLevelDto>,
    val totalDistanceMeters: Double,
    val estimatedTimeSeconds: Int,
)

@Serializable
data class ErrorDto(val error: String, val message: String)

@Serializable
data class CredentialsRequest(val username: String, val password: String)

@Serializable
data class TokenResponse(val token: String, val expiresInSeconds: Long, val user: UserDto)

@Serializable
data class UserDto(val id: Long, val username: String, val role: String) // role: "USER" | "ADMIN"

object ApiPaths {
    const val PREFIX = "/api/v1"
    const val MALLS = "$PREFIX/malls"
    const val REGISTER = "$PREFIX/auth/register"
    const val LOGIN = "$PREFIX/auth/login"
    const val ME = "$PREFIX/auth/me"
    const val ADMIN_MALLS = "$PREFIX/admin/malls" // POST creates (server assigns ids), PUT/DELETE on /{id}
    fun adminMall(id: Long) = "$ADMIN_MALLS/$id"
    fun mall(id: Long) = "$MALLS/$id"
    fun locations(id: Long) = "${mall(id)}/locations"
    fun route(id: Long, fromId: String, toId: String, accessibleOnly: Boolean = false) =
        "${mall(id)}/route?from=$fromId&to=$toId" + if (accessibleOnly) "&accessible=true" else ""
}
