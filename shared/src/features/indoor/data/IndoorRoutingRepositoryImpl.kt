package features.indoor.data

import features.indoor.domain.*
import sklepsearch.*
import kotlin.math.hypot
import kotlin.math.max

class IndoorRoutingRepositoryImpl(
    private val remoteApiUrl: String? = null,
    private val mallsSupplier: () -> List<Mall> = {
        listOf(MockGaleriaKrakowska.INSTANCE, MockGaleriaKazimierz.INSTANCE)
    }
) : IndoorRoutingRepository {

    fun getAllNavLocations(mallId: Long): List<NavLocation> {
        val mall = mallsSupplier().find { it.id == mallId } ?: return emptyList()
        val result = mutableListOf<NavLocation>()

        // 1. Mall Exits & Street Entrances (located on ground / min floor)
        val groundFloorNum = maxOf(0, mall.minFloor)
        mall.entryPoints.forEachIndexed { index, pt ->
            val exitName = when (mall.id) {
                1L -> when (index) {
                    0 -> "Main Entrance (Kraków Główny / Pawia)"
                    1 -> "East Exit (Dworzec Autobusowy MDA)"
                    2 -> "South Exit (Plac Jana Nowaka-Jeziorańskiego)"
                    else -> "Exit #${index + 1}"
                }
                2L -> when (index) {
                    0 -> "Podgórska Street Entrance (Wisła River)"
                    1 -> "Rzeźnicza Street Entrance"
                    2 -> "Daszyńskiego Entrance"
                    else -> "Exit #${index + 1}"
                }
                else -> "Entrance / Exit #${index + 1}"
            }
            result.add(
                NavLocation(
                    id = "exit_${mall.id}_$index",
                    name = exitName,
                    type = NavLocationType.EXIT,
                    floorNumber = groundFloorNum,
                    coordinates = pt,
                    category = "Building Entrance / Exit"
                )
            )
        }

        // 2. Stores on each floor
        for (floor in mall.floors) {
            val floorLabel = if (floor.number >= 0) "Floor +${floor.number}" else "Floor ${floor.number}"
            for (store in floor.stores) {
                val entry = store.entryPoints.firstOrNull()
                    ?: Point(store.area.getBounds().centerX, store.area.getBounds().centerY)
                result.add(
                    NavLocation(
                        id = "store_${store.Instanceid}",
                        name = store.name,
                        type = NavLocationType.STORE,
                        floorNumber = floor.number,
                        coordinates = entry,
                        category = "$floorLabel • ${store.category}"
                    )
                )
            }
        }

        return result
    }

    override suspend fun calculateRoute(
        mallId: Long,
        start: NavLocation,
        end: NavLocation
    ): Result<IndoorRoute> = calculateRouteSync(mallId, start, end)

    fun calculateRouteSync(
        mallId: Long,
        start: NavLocation,
        end: NavLocation
    ): Result<IndoorRoute> {
        val mall = mallsSupplier().find { it.id == mallId }
            ?: return Result.failure(IllegalArgumentException("Mall with id $mallId not found"))

        // Scale factor: local units to approximate meters
        val metersPerUnit = when (mall.id) {
            1L -> 0.25 // 1000 units ~ 250m long
            2L -> 0.22
            else -> 0.25
        }

        // Spine X for corridor routing
        val spineX = when (mall.id) {
            1L -> 500.0
            2L -> 500.0
            else -> mall.size.x / 2.0
        }

        if (start.floorNumber == end.floorNumber) {
            // --- SINGLE FLOOR ROUTE ---
            val floorNum = start.floorNumber
            val waypoints = buildCorridorWaypoints(start.coordinates, end.coordinates, spineX)
            val spline = CatmullRomBezierSpline.createSpline(waypoints)
            val dist = calculatePathDistance(waypoints) * metersPerUnit

            val floorLabel = if (floorNum >= 0) "+$floorNum" else "$floorNum"
            val instructions = listOf(
                "Depart from ${start.name} on Floor $floorLabel",
                "Follow the central concourse towards ${end.name}",
                "Arrive at ${end.name}"
            )

            val levelRoute = FloorLevelRoute(
                floorNumber = floorNum,
                waypoints = waypoints,
                bezierPath = spline,
                instructions = instructions,
                distanceMeters = dist
            )

            val totalTime = max(30, (dist / 1.2).toInt())
            return Result.success(
                IndoorRoute(
                    mallId = mallId,
                    startLocation = start,
                    endLocation = end,
                    levels = listOf(levelRoute),
                    totalDistanceMeters = dist,
                    estimatedTimeSeconds = totalTime
                )
            )
        } else {
            // --- MULTI-FLOOR ROUTE ---
            val isAscending = end.floorNumber > start.floorNumber
            val desiredEscalatorDir = if (isAscending) EscalatorDirection.UP else EscalatorDirection.DOWN
            val startFloor = mall.getFloor(start.floorNumber)
                ?: return Result.failure(IllegalStateException("Floor ${start.floorNumber} not found"))

            // Find optimal vertical transport on start floor:
            // Prefer directed escalator if available, otherwise elevator
            val matchingEscalator = startFloor.escalators
                .filter { it.direction == desiredEscalatorDir }
                .minByOrNull { distance(start.coordinates, it.coordinates) }

            val nearestElevator = startFloor.elevators
                .minByOrNull { distance(start.coordinates, it.coordinates) }

            val transportPoint: Point
            val transportName: String
            if (matchingEscalator != null) {
                transportPoint = matchingEscalator.coordinates
                transportName = "Escalator (Going ${if (isAscending) "UP" else "DOWN"})"
            } else if (nearestElevator != null) {
                transportPoint = nearestElevator.coordinates
                transportName = "Elevator"
            } else {
                // Fallback to corridor center if no transport explicitly defined
                transportPoint = Point(spineX, 500.0)
                transportName = "Stairs / Concourse"
            }

            val levels = mutableListOf<FloorLevelRoute>()
            var totalDist = 0.0

            // 1. Start floor: Start -> Transport facility
            val startWaypoints = buildCorridorWaypoints(start.coordinates, transportPoint, spineX)
            val startSpline = CatmullRomBezierSpline.createSpline(startWaypoints)
            val startDist = calculatePathDistance(startWaypoints) * metersPerUnit
            totalDist += startDist

            val startFloorLabel = if (start.floorNumber >= 0) "+${start.floorNumber}" else "${start.floorNumber}"
            val endFloorLabel = if (end.floorNumber >= 0) "+${end.floorNumber}" else "${end.floorNumber}"

            levels.add(
                FloorLevelRoute(
                    floorNumber = start.floorNumber,
                    waypoints = startWaypoints,
                    bezierPath = startSpline,
                    instructions = listOf(
                        "Depart from ${start.name} on Floor $startFloorLabel",
                        "Walk to the $transportName",
                        "Take $transportName to Floor $endFloorLabel"
                    ),
                    distanceMeters = startDist
                )
            )

            // 2. Intermediate floors (if any)
            val step = if (isAscending) 1 else -1
            var curFloor = start.floorNumber + step
            while (curFloor != end.floorNumber) {
                val interWaypoints = listOf(
                    Point(transportPoint.x, transportPoint.y - 15.0),
                    Point(spineX, transportPoint.y),
                    Point(transportPoint.x, transportPoint.y + 15.0)
                )
                val interSpline = CatmullRomBezierSpline.createSpline(interWaypoints)
                val interDist = 15.0 * metersPerUnit
                totalDist += interDist

                val interLabel = if (curFloor >= 0) "+$curFloor" else "$curFloor"
                levels.add(
                    FloorLevelRoute(
                        floorNumber = curFloor,
                        waypoints = interWaypoints,
                        bezierPath = interSpline,
                        instructions = listOf(
                            "Pass through Floor $interLabel",
                            "Continue on $transportName to Floor $endFloorLabel"
                        ),
                        distanceMeters = interDist
                    )
                )
                curFloor += step
            }

            // 3. Destination floor: Transport facility -> End destination
            val endWaypoints = buildCorridorWaypoints(transportPoint, end.coordinates, spineX)
            val endSpline = CatmullRomBezierSpline.createSpline(endWaypoints)
            val endDist = calculatePathDistance(endWaypoints) * metersPerUnit
            totalDist += endDist

            levels.add(
                FloorLevelRoute(
                    floorNumber = end.floorNumber,
                    waypoints = endWaypoints,
                    bezierPath = endSpline,
                    instructions = listOf(
                        "Exit $transportName on Floor $endFloorLabel",
                        "Follow concourse towards ${end.name}",
                        "Arrive at ${end.name}"
                    ),
                    distanceMeters = endDist
                )
            )

            val floorChangePenalty = 25 * kotlin.math.abs(end.floorNumber - start.floorNumber)
            val totalTime = max(45, (totalDist / 1.2).toInt() + floorChangePenalty)

            return Result.success(
                IndoorRoute(
                    mallId = mallId,
                    startLocation = start,
                    endLocation = end,
                    levels = levels,
                    totalDistanceMeters = totalDist,
                    estimatedTimeSeconds = totalTime
                )
            )
        }
    }

    private fun buildCorridorWaypoints(from: Point, to: Point, spineX: Double): List<Point> {
        val pts = mutableListOf<Point>()
        pts.add(from)

        // If 'from' is significantly off the spine (e.g. store entrance), step into the spine
        if (kotlin.math.abs(from.x - spineX) > 15.0) {
            pts.add(Point(spineX, from.y))
        }

        // If distance along spine is substantial, add an intermediate guiding point
        val yDiff = to.y - from.y
        if (kotlin.math.abs(yDiff) > 120.0) {
            pts.add(Point(spineX, from.y + yDiff * 0.5))
        }

        // If 'to' is off the spine, align opposite to 'to'
        if (kotlin.math.abs(to.x - spineX) > 15.0) {
            pts.add(Point(spineX, to.y))
        }

        pts.add(to)

        // Deduplicate consecutive points that are too close (< 2.0 units)
        val deduplicated = mutableListOf<Point>()
        for (p in pts) {
            if (deduplicated.isEmpty() || distance(deduplicated.last(), p) > 2.0) {
                deduplicated.add(p)
            }
        }

        return deduplicated
    }

    private fun distance(p1: Point, p2: Point): Double {
        return hypot(p2.x - p1.x, p2.y - p1.y)
    }

    private fun calculatePathDistance(points: List<Point>): Double {
        var d = 0.0
        for (i in 0 until points.size - 1) {
            d += distance(points[i], points[i + 1])
        }
        return d
    }
}
