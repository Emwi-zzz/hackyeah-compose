package features.indoor.domain

import core.geometry.GeoPoint
import sklepsearch.Mall
import sklepsearch.Path2D
import sklepsearch.Point

enum class NavLocationType {
    STORE,
    EXIT,
    /** The user's own position outside the mall; routes from it go through the street network first. */
    USER_LOCATION
}

data class NavLocation(
    val id: String,
    val name: String,
    val type: NavLocationType,
    val floorNumber: Int,
    val coordinates: Point,
    val category: String? = null
)

enum class WaypointType {
    START,
    CORRIDOR,
    ESCALATOR,
    ELEVATOR,
    END
}

data class RouteWaypoint(
    val point: Point,
    val floorNumber: Int,
    val type: WaypointType = WaypointType.CORRIDOR,
    val description: String? = null
)

data class FloorLevelRoute(
    val floorNumber: Int,
    val waypoints: List<Point>,
    val bezierPath: Path2D,
    val instructions: List<String>,
    val distanceMeters: Double
)

data class IndoorRoute(
    val mallId: Long,
    val startLocation: NavLocation,
    val endLocation: NavLocation,
    val levels: List<FloorLevelRoute>,
    val totalDistanceMeters: Double,
    val estimatedTimeSeconds: Int
)

data class OutdoorRoute(
    val points: List<GeoPoint>,
    val distanceMeters: Double,
    val durationSeconds: Int,
    val instructions: List<String>
)

/** Walk from the street to the best entrance, then (optionally) the indoor route from that entrance. */
data class ApproachRoute(
    val mallId: Long,
    val entrance: NavLocation,
    val outdoor: OutdoorRoute,
    val indoor: IndoorRoute?,
    val totalDistanceMeters: Double,
    val estimatedTimeSeconds: Int
)

interface IndoorRoutingRepository {
    suspend fun getMalls(): Result<List<Mall>>

    suspend fun getNavLocations(mallId: Long): Result<List<NavLocation>>

    suspend fun calculateRoute(
        mallId: Long,
        start: NavLocation,
        end: NavLocation,
        accessibleOnly: Boolean = false
    ): Result<IndoorRoute>

    suspend fun calculateApproach(
        mallId: Long,
        from: GeoPoint,
        end: NavLocation?,
        accessibleOnly: Boolean = false
    ): Result<ApproachRoute> = Result.failure(UnsupportedOperationException("Outdoor routing is not supported"))
}