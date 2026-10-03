package backend.routing

import api.*
import api.DtoMapper.toDomain
import api.DtoMapper.toDto
import sklepsearch.*
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max

/**
 * Indoor navigation: navigation targets of a mall and route calculation.
 * Routes are returned as plain waypoints; the client draws the smoothing Bezier curve.
 */
class IndoorRoutingService {

    fun navLocations(mall: Mall): List<NavLocationDto> {
        val result = mutableListOf<NavLocationDto>()

        val groundFloorNum = maxOf(0, mall.minFloor)
        mall.entryPoints.forEachIndexed { index, pt ->
            val exitName = mall.entryPointNames.getOrNull(index) ?: "Entrance / Exit #${index + 1}"
            result.add(
                NavLocationDto(
                    id = "exit_${mall.id}_$index",
                    name = exitName,
                    type = "EXIT",
                    floorNumber = groundFloorNum,
                    position = pt.toDto(),
                    category = "Building Entrance / Exit"
                )
            )
        }

        for (floor in mall.floors) {
            val floorLabel = if (floor.number >= 0) "Floor +${floor.number}" else "Floor ${floor.number}"
            for (store in floor.stores) {
                val entry = store.entryPoints.firstOrNull()
                    ?: Point(store.area.getBounds().centerX, store.area.getBounds().centerY)
                result.add(
                    NavLocationDto(
                        id = "store_${store.Instanceid}",
                        name = store.name,
                        type = "STORE",
                        floorNumber = floor.number,
                        position = entry.toDto(),
                        category = "$floorLabel • ${store.category}"
                    )
                )
            }
        }
        return result
    }

    fun calculateRoute(mall: Mall, start: NavLocationDto, end: NavLocationDto): Result<RouteDto> {
        // Local units -> meters, derived from the mall's real-world width
        val widthMeters = abs(mall.downRight.longitude - mall.upperLeft.longitude) *
            111_320.0 * cos(Math.toRadians((mall.upperLeft.latitude + mall.downRight.latitude) / 2.0))
        val metersPerUnit = widthMeters / mall.size.x
        val spineX = mall.size.x / 2.0
        val spineY = mall.size.y / 2.0
        val startPos = start.position.toDomain()
        val endPos = end.position.toDomain()

        if (start.floorNumber == end.floorNumber) {
            val waypoints = buildCorridorWaypoints(startPos, endPos, spineX)
            val dist = pathDistance(waypoints) * metersPerUnit
            val level = RouteLevelDto(
                floorNumber = start.floorNumber,
                waypoints = waypoints.map { it.toDto() },
                instructions = listOf(
                    "Depart from ${start.name} on Floor ${floorLabel(start.floorNumber)}",
                    "Follow the central concourse towards ${end.name}",
                    "Arrive at ${end.name}"
                ),
                distanceMeters = dist
            )
            return Result.success(
                RouteDto(mall.id, start, end, listOf(level), dist, max(30, (dist / 1.2).toInt()))
            )
        }

        val isAscending = end.floorNumber > start.floorNumber
        val desiredDir = if (isAscending) EscalatorDirection.UP else EscalatorDirection.DOWN
        val startFloor = mall.getFloor(start.floorNumber)
            ?: return Result.failure(IllegalStateException("Floor ${start.floorNumber} not found"))

        val escalator = startFloor.escalators
            .filter { it.direction == desiredDir }
            .minByOrNull { distance(startPos, it.coordinates) }
        val elevator = startFloor.elevators.minByOrNull { distance(startPos, it.coordinates) }

        val transportPoint: Point
        val transportName: String
        when {
            escalator != null -> {
                transportPoint = escalator.coordinates
                transportName = "Escalator (Going ${if (isAscending) "UP" else "DOWN"})"
            }
            elevator != null -> {
                transportPoint = elevator.coordinates
                transportName = "Elevator"
            }
            else -> {
                transportPoint = Point(spineX, spineY)
                transportName = "Stairs / Concourse"
            }
        }

        val levels = mutableListOf<RouteLevelDto>()
        var totalDist = 0.0
        val endLabel = floorLabel(end.floorNumber)

        val startWaypoints = buildCorridorWaypoints(startPos, transportPoint, spineX)
        val startDist = pathDistance(startWaypoints) * metersPerUnit
        totalDist += startDist
        levels.add(
            RouteLevelDto(
                floorNumber = start.floorNumber,
                waypoints = startWaypoints.map { it.toDto() },
                instructions = listOf(
                    "Depart from ${start.name} on Floor ${floorLabel(start.floorNumber)}",
                    "Walk to the $transportName",
                    "Take $transportName to Floor $endLabel"
                ),
                distanceMeters = startDist
            )
        )

        val step = if (isAscending) 1 else -1
        var cur = start.floorNumber + step
        while (cur != end.floorNumber) {
            val interWaypoints = listOf(
                Point(transportPoint.x, transportPoint.y - 15.0),
                Point(spineX, transportPoint.y),
                Point(transportPoint.x, transportPoint.y + 15.0)
            )
            val interDist = 15.0 * metersPerUnit
            totalDist += interDist
            levels.add(
                RouteLevelDto(
                    floorNumber = cur,
                    waypoints = interWaypoints.map { it.toDto() },
                    instructions = listOf(
                        "Pass through Floor ${floorLabel(cur)}",
                        "Continue on $transportName to Floor $endLabel"
                    ),
                    distanceMeters = interDist
                )
            )
            cur += step
        }

        val endWaypoints = buildCorridorWaypoints(transportPoint, endPos, spineX)
        val endDist = pathDistance(endWaypoints) * metersPerUnit
        totalDist += endDist
        levels.add(
            RouteLevelDto(
                floorNumber = end.floorNumber,
                waypoints = endWaypoints.map { it.toDto() },
                instructions = listOf(
                    "Exit $transportName on Floor $endLabel",
                    "Follow concourse towards ${end.name}",
                    "Arrive at ${end.name}"
                ),
                distanceMeters = endDist
            )
        )

        val floorChangePenalty = 25 * abs(end.floorNumber - start.floorNumber)
        val totalTime = max(45, (totalDist / 1.2).toInt() + floorChangePenalty)
        return Result.success(RouteDto(mall.id, start, end, levels, totalDist, totalTime))
    }

    private fun floorLabel(n: Int) = if (n >= 0) "+$n" else "$n"

    private fun buildCorridorWaypoints(from: Point, to: Point, spineX: Double): List<Point> {
        val pts = mutableListOf(from)
        if (abs(from.x - spineX) > 15.0) pts.add(Point(spineX, from.y))
        val yDiff = to.y - from.y
        if (abs(yDiff) > 120.0) pts.add(Point(spineX, from.y + yDiff * 0.5))
        if (abs(to.x - spineX) > 15.0) pts.add(Point(spineX, to.y))
        pts.add(to)

        val result = mutableListOf<Point>()
        for (p in pts) {
            if (result.isEmpty() || distance(result.last(), p) > 2.0) result.add(p)
        }
        return result
    }

    private fun distance(a: Point, b: Point) = hypot(b.x - a.x, b.y - a.y)

    private fun pathDistance(points: List<Point>): Double =
        points.zipWithNext().sumOf { (a, b) -> distance(a, b) }
}
