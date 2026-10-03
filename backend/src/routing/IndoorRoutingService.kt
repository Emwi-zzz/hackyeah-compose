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

    fun calculateRoute(
        mall: Mall,
        start: NavLocationDto,
        end: NavLocationDto,
        accessibleOnly: Boolean = false
    ): Result<RouteDto> {
        val widthMeters = abs(mall.downRight.longitude - mall.upperLeft.longitude) *
            111_320.0 * cos(Math.toRadians((mall.upperLeft.latitude + mall.downRight.latitude) / 2.0))
        val metersPerUnit = widthMeters / mall.size.x
        val path = findPath(
            mall,
            RouteNode(start.floorNumber, start.position.toDomain()),
            RouteNode(end.floorNumber, end.position.toDomain()),
            accessibleOnly
        ) ?: return Result.failure(
            IllegalStateException(
                if (accessibleOnly) "No accessible path from ${start.name} to ${end.name}"
                else "No walkable path from ${start.name} to ${end.name}"
            )
        )

        val legs = mutableListOf<MutableList<RouteNode>>()
        for (node in path) {
            if (legs.isEmpty() || legs.last().first().floor != node.floor) legs += mutableListOf(node)
            else legs.last() += node
        }

        val levels = legs.mapIndexed { index, leg ->
            val floorNumber = leg.first().floor
            val points = leg.map { it.point }
            val distanceMeters = polylineLength(points) * metersPerUnit
            val nextFloor = legs.getOrNull(index + 1)?.first()?.floor
            val instructions = buildList {
                add(
                    if (index == 0) "Depart from ${start.name} on Floor ${floorLabel(floorNumber)}"
                    else "Arrive on Floor ${floorLabel(floorNumber)}"
                )
                if (nextFloor == null) {
                    add("Arrive at ${end.name}")
                } else {
                    add("Walk to the ${transportName(mall, floorNumber, points.last())}")
                    add("Take it to Floor ${floorLabel(nextFloor)}")
                }
            }
            RouteLevelDto(
                floorNumber = floorNumber,
                waypoints = points.map { it.toDto() },
                instructions = instructions,
                distanceMeters = distanceMeters
            )
        }
        val totalDistance = levels.sumOf { it.distanceMeters }
        val floorChanges = levels.size - 1
        val estimatedTime = max(30, (totalDistance / 1.2).toInt() + 25 * floorChanges)
        return Result.success(RouteDto(mall.id, start, end, levels, totalDistance, estimatedTime))
    }

    private data class RouteNode(val floor: Int, val point: Point)

    private class Polygon(val vertices: List<Point>) {
        val edges = vertices.indices.mapNotNull { i ->
            val a = vertices[i]
            val b = vertices[(i + 1) % vertices.size]
            if (distance(a, b) < EPSILON) null else a to b
        }
        val bounds = Bounds.of(vertices)

        fun contains(point: Point): Boolean {
            if (!bounds.contains(point)) return false
            var inside = false
            var j = vertices.lastIndex
            for (i in vertices.indices) {
                val a = vertices[i]
                val b = vertices[j]
                if ((a.y > point.y) != (b.y > point.y) &&
                    point.x < (b.x - a.x) * (point.y - a.y) / (b.y - a.y) + a.x
                ) inside = !inside
                j = i
            }
            return inside
        }

        fun onBoundary(point: Point) = edges.any { (a, b) -> distanceToSegment(point, a, b) < EPSILON }
        fun strictlyContains(point: Point) = contains(point) && !onBoundary(point)

        fun nearestBoundaryPoint(point: Point) =
            edges.map { (a, b) -> nearestOnSegment(point, a, b) }.minBy { distance(it, point) }
    }

    private data class Bounds(val minX: Double, val minY: Double, val maxX: Double, val maxY: Double) {
        fun contains(point: Point) =
            point.x >= minX - EPSILON && point.x <= maxX + EPSILON &&
                point.y >= minY - EPSILON && point.y <= maxY + EPSILON

        fun overlaps(other: Bounds) =
            other.maxX >= minX - EPSILON && other.minX <= maxX + EPSILON &&
                other.maxY >= minY - EPSILON && other.minY <= maxY + EPSILON

        companion object {
            fun of(points: List<Point>) = Bounds(
                points.minOf { it.x }, points.minOf { it.y }, points.maxOf { it.x }, points.maxOf { it.y }
            )
        }
    }

    private data class Wall(val start: Point, val end: Point) {
        val bounds = Bounds.of(listOf(start, end))
    }

    private data class ShopArea(val polygon: Polygon, val doors: List<Point>)

    private data class FloorPlan(
        val number: Int,
        val outline: Polygon,
        val shops: List<ShopArea>,
        val voids: List<Polygon>
    ) {
        val walls = (outline.edges + shops.flatMap { it.polygon.edges } + voids.flatMap { it.edges })
            .map { (a, b) -> Wall(a, b) }
    }

    private fun findPath(mall: Mall, start: RouteNode, end: RouteNode, accessibleOnly: Boolean): List<RouteNode>? {
        val plans = mall.floors.associate { floor ->
            val shops = floor.stores.map { store ->
                val polygon = Polygon(store.area.getVertices(CURVE_STEPS))
                val doors = store.entryPoints.map { point ->
                    if (polygon.onBoundary(point)) point else polygon.nearestBoundaryPoint(point)
                }
                ShopArea(polygon, doors)
            }
            floor.number to FloorPlan(
                floor.number,
                Polygon(floor.box.getVertices(CURVE_STEPS)),
                shops,
                floor.voids.map { Polygon(it.getVertices(CURVE_STEPS)) }
            )
        }

        fun onFloor(node: RouteNode) = plans[node.floor]?.let {
            it.outline.contains(node.point) || it.outline.onBoundary(node.point)
        } ?: false
        if (!onFloor(start) || !onFloor(end)) return null

        val nodes = mutableListOf(start, end)
        fun addNode(node: RouteNode) {
            if (nodes.none { it.floor == node.floor && distance(it.point, node.point) < EPSILON }) nodes += node
        }
        for (floor in mall.floors) {
            val plan = plans.getValue(floor.number)
            plan.outline.vertices.forEach { addNode(RouteNode(floor.number, it)) }
            plan.voids.forEach { polygon -> polygon.vertices.forEach { addNode(RouteNode(floor.number, it)) } }
            for (shop in plan.shops) {
                shop.polygon.vertices.forEach { addNode(RouteNode(floor.number, it)) }
                shop.doors.forEach { addNode(RouteNode(floor.number, it)) }
            }
            floor.elevators.forEach { addNode(RouteNode(floor.number, it.coordinates)) }
            floor.escalators.forEach { addNode(RouteNode(floor.number, it.coordinates)) }
        }

        val rides = buildRides(mall, nodes, accessibleOnly)
        val best = DoubleArray(nodes.size) { Double.POSITIVE_INFINITY }
        val previous = IntArray(nodes.size) { -1 }
        val settled = BooleanArray(nodes.size)
        val visibleCache = HashMap<Triple<Int, Point, Point>, Boolean>()
        best[0] = 0.0

        while (true) {
            var current = -1
            for (i in nodes.indices) {
                if (!settled[i] && best[i] < Double.POSITIVE_INFINITY &&
                    (current == -1 || best[i] < best[current])
                ) current = i
            }
            if (current == -1) return null
            if (current == 1) break
            settled[current] = true

            val plan = plans.getValue(nodes[current].floor)
            for (next in nodes.indices) {
                if (settled[next] || nodes[next].floor != nodes[current].floor) continue
                val cost = distance(nodes[current].point, nodes[next].point)
                if (best[current] + cost >= best[next]) continue
                val a = nodes[current].point
                val b = nodes[next].point
                val cacheKey = if (a.x < b.x || (a.x == b.x && a.y <= b.y)) {
                    Triple(plan.number, a, b)
                } else {
                    Triple(plan.number, b, a)
                }
                val isEndpoint = current < 2 || next < 2
                val visible = if (isEndpoint) visible(plan, a, b) else
                    visibleCache.getOrPut(cacheKey) { visible(plan, a, b) }
                if (visible) relax(current, next, cost, best, previous)
            }
            for ((next, cost) in rides[current].orEmpty()) {
                if (!settled[next]) relax(current, next, cost, best, previous)
            }
        }

        val path = mutableListOf<RouteNode>()
        var cursor = 1
        while (cursor != -1) {
            path += nodes[cursor]
            cursor = previous[cursor]
        }
        return path.asReversed()
    }

    private fun buildRides(
        mall: Mall,
        nodes: List<RouteNode>,
        accessibleOnly: Boolean
    ): Map<Int, List<Pair<Int, Double>>> {
        val rides = mutableMapOf<Int, MutableList<Pair<Int, Double>>>()
        fun nodeIndex(floor: Int, point: Point) =
            nodes.indexOfFirst { it.floor == floor && distance(it.point, point) < EPSILON }
        fun link(from: Int, to: Int, cost: Double) {
            if (from >= 0 && to >= 0) rides.getOrPut(from) { mutableListOf() } += to to cost
        }

        for (floor in mall.floors) {
            val upper = mall.getFloor(floor.number + 1)
            val lower = mall.getFloor(floor.number - 1)
            if (upper != null) {
                for (elevator in floor.elevators) {
                    if (accessibleOnly && !elevator.isAccessible) continue
                    val paired = upper.elevators.firstOrNull {
                        distance(it.coordinates, elevator.coordinates) < 1.0 &&
                            (!accessibleOnly || it.isAccessible)
                    } ?: continue
                    val from = nodeIndex(floor.number, elevator.coordinates)
                    val to = nodeIndex(upper.number, paired.coordinates)
                    link(from, to, ELEVATOR_COST)
                    link(to, from, ELEVATOR_COST)
                }
            }
            for (escalator in floor.escalators) {
                if (accessibleOnly && !escalator.isAccessible) continue
                val target = when (escalator.direction) {
                    EscalatorDirection.UP -> upper
                    EscalatorDirection.DOWN -> lower
                } ?: continue
                val landing = target.escalators.minByOrNull { distance(it.coordinates, escalator.coordinates) }
                    ?.takeIf { !accessibleOnly || it.isAccessible } ?: continue
                link(
                    nodeIndex(floor.number, escalator.coordinates),
                    nodeIndex(target.number, landing.coordinates),
                    ESCALATOR_COST + distance(escalator.coordinates, landing.coordinates)
                )
            }
        }
        return rides
    }

    private fun visible(plan: FloorPlan, a: Point, b: Point): Boolean {
        if (distance(a, b) < EPSILON) return true
        val segmentBounds = Bounds.of(listOf(a, b))
        val cuts = mutableListOf(0.0, 1.0)
        for (wall in plan.walls) {
            if (!wall.bounds.overlaps(segmentBounds)) continue
            if (crossesProperly(a, b, wall.start, wall.end)) return false
            if (distanceToSegment(wall.start, a, b) < EPSILON) cuts += projection(wall.start, a, b)
            if (distanceToSegment(wall.end, a, b) < EPSILON) cuts += projection(wall.end, a, b)
        }
        cuts.sort()
        for (i in 0 until cuts.lastIndex) {
            if (cuts[i + 1] - cuts[i] < 1e-9) continue
            val t = (cuts[i] + cuts[i + 1]) / 2.0
            val midpoint = Point(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
            if (!walkable(plan, midpoint, a, b)) return false
        }
        return true
    }

    private fun walkable(plan: FloorPlan, point: Point, a: Point, b: Point): Boolean {
        if (!plan.outline.contains(point) && !plan.outline.onBoundary(point)) return false
        if (plan.voids.any { it.strictlyContains(point) }) return false
        val shop = plan.shops.firstOrNull { it.polygon.strictlyContains(point) } ?: return true
        return insideOrDoor(shop, a) && insideOrDoor(shop, b)
    }

    private fun insideOrDoor(shop: ShopArea, point: Point) =
        shop.polygon.strictlyContains(point) || shop.doors.any { distance(it, point) < EPSILON }

    private fun transportName(mall: Mall, floorNumber: Int, point: Point): String {
        val floor = mall.getFloor(floorNumber) ?: return "stairs"
        return when {
            floor.elevators.any { distance(it.coordinates, point) < EPSILON } -> "elevator"
            floor.escalators.any { distance(it.coordinates, point) < EPSILON } -> "escalator"
            else -> "stairs"
        }
    }

    private fun relax(from: Int, to: Int, cost: Double, best: DoubleArray, previous: IntArray) {
        val candidate = best[from] + cost
        if (candidate < best[to]) {
            best[to] = candidate
            previous[to] = from
        }
    }

    private fun floorLabel(number: Int) = if (number >= 0) "+$number" else "$number"

    private companion object {
        const val EPSILON = 1e-6
        const val CURVE_STEPS = 8
        const val ELEVATOR_COST = 60.0
        const val ESCALATOR_COST = 40.0

        fun distance(a: Point, b: Point) = hypot(b.x - a.x, b.y - a.y)

        fun projection(point: Point, a: Point, b: Point): Double {
            val dx = b.x - a.x
            val dy = b.y - a.y
            val lengthSquared = dx * dx + dy * dy
            if (lengthSquared == 0.0) return 0.0
            return (((point.x - a.x) * dx + (point.y - a.y) * dy) / lengthSquared).coerceIn(0.0, 1.0)
        }

        fun nearestOnSegment(point: Point, a: Point, b: Point): Point {
            val t = projection(point, a, b)
            return Point(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
        }

        fun distanceToSegment(point: Point, a: Point, b: Point) =
            distance(point, nearestOnSegment(point, a, b))

        fun side(point: Point, a: Point, b: Point) =
            ((b.x - a.x) * (point.y - a.y) - (b.y - a.y) * (point.x - a.x)) / distance(a, b)

        fun crossesProperly(a: Point, b: Point, p: Point, q: Point): Boolean {
            val s1 = side(a, p, q)
            val s2 = side(b, p, q)
            val s3 = side(p, a, b)
            val s4 = side(q, a, b)
            return ((s1 > EPSILON && s2 < -EPSILON) || (s1 < -EPSILON && s2 > EPSILON)) &&
                ((s3 > EPSILON && s4 < -EPSILON) || (s3 < -EPSILON && s4 > EPSILON))
        }

        fun polylineLength(points: List<Point>) =
            points.zipWithNext().sumOf { (a, b) -> distance(a, b) }
    }
}
