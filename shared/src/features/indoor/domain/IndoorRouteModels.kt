package features.indoor.domain

import sklepsearch.Path2D
import sklepsearch.Point

enum class NavLocationType {
    STORE,
    EXIT
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

interface IndoorRoutingRepository {
    suspend fun calculateRoute(
        mallId: Long,
        start: NavLocation,
        end: NavLocation
    ): Result<IndoorRoute>
}
