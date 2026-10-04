package backend.outdoor

import api.*
import backend.routing.IndoorRoutingService
import sklepsearch.Mall
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Walking directions from anywhere in Kraków to a mall: picks the entrance that minimises
 * outdoor walk + indoor walk to the destination, then returns both parts.
 */
class OutdoorRoutingService(
    private val graph: WalkGraph,
    private val indoor: IndoorRoutingService,
) {
    class RoutingException(val code: String, message: String) : RuntimeException(message)

    fun approach(
        mall: Mall,
        latitude: Double,
        longitude: Double,
        destination: NavLocationDto?,
        accessibleOnly: Boolean,
    ): ApproachRouteDto {
        val start = graph.snap(latitude, longitude, accessibleOnly)
            ?: throw RoutingException("outside_network", "Your location is too far from the Kraków walking network")

        val locations = indoor.navLocations(mall)
        val candidates = locations.filter { it.type == "EXIT" }.mapNotNull { exit ->
            val indoorRoute = when {
                destination == null || destination.id == exit.id -> null
                else -> indoor.calculateRoute(mall, exit, destination, accessibleOnly).getOrNull() ?: return@mapNotNull null
            }
            val geo = mall.toGeo(exit.position)
            val snap = graph.snap(geo.latitude, geo.longitude, accessibleOnly, maxMeters = 300.0) ?: return@mapNotNull null
            Triple(exit, indoorRoute, WalkGraph.Target(geo.latitude, geo.longitude, indoorRoute?.totalDistanceMeters ?: 0.0) to snap)
        }
        if (candidates.isEmpty()) {
            throw RoutingException("no_entrance", "No reachable entrance of ${mall.name}" + if (accessibleOnly) " without steps" else "")
        }

        val path = graph.route(start, candidates.map { it.third }, accessibleOnly)
            ?: throw RoutingException("no_path", "No walking path to ${mall.name}")
        val (entrance, indoorRoute) = candidates[path.targetIndex]

        val speed = if (accessibleOnly) WHEELCHAIR_SPEED else WALKING_SPEED
        val outdoor = OutdoorRouteDto(
            points = listOf(GeoPointDto(latitude, longitude)) + path.points.map { (lat, lon) -> GeoPointDto(lat, lon) },
            distanceMeters = path.walkMeters + WalkGraph.distance(latitude, longitude, start.lat, start.lon),
            durationSeconds = 0,
            instructions = instructions(path, entrance.name),
        ).let { it.copy(durationSeconds = (it.distanceMeters / speed).roundToInt()) }

        return ApproachRouteDto(
            mallId = mall.id,
            entrance = entrance,
            outdoor = outdoor,
            indoor = indoorRoute,
            totalDistanceMeters = outdoor.distanceMeters + (indoorRoute?.totalDistanceMeters ?: 0.0),
            estimatedTimeSeconds = outdoor.durationSeconds + (indoorRoute?.estimatedTimeSeconds ?: 0),
        )
    }

    /**
     * Turn-by-turn steps. Sidewalks in OSM are mostly unnamed, so legs are split at real turns
     * (measured over ~10 m either side of a vertex to ignore kinks) as well as at street name changes.
     */
    private fun instructions(path: WalkGraph.Path, entranceName: String): List<String> {
        val points = path.points
        val lengths = points.zipWithNext().map { (a, b) -> WalkGraph.distance(a.first, a.second, b.first, b.second) }

        fun bearingBetween(from: Int, to: Int): Double {
            val (a, b) = points[from] to points[to]
            val dx = (b.second - a.second) * cos(Math.toRadians(a.first))
            val dy = b.first - a.first
            return Math.toDegrees(atan2(dx, dy))
        }
        fun pointBack(vertex: Int): Int {
            var i = vertex
            var walked = 0.0
            while (i > 0 && walked < TURN_WINDOW_METERS) walked += lengths[--i]
            return i
        }
        fun pointAhead(vertex: Int): Int {
            var i = vertex
            var walked = 0.0
            while (i < points.lastIndex && walked < TURN_WINDOW_METERS) walked += lengths[i++]
            return i
        }

        class Leg(var meters: Double, val names: MutableMap<String, Double> = mutableMapOf(), var turn: Double = 0.0) {
            val name get() = names.maxByOrNull { it.value }?.key
        }
        val legs = mutableListOf(Leg(0.0))
        for (i in lengths.indices) {
            if (i > 0) {
                val turn = normalize(bearingBetween(i, pointAhead(i)) - bearingBetween(pointBack(i), i))
                val name = path.segmentNames[i]
                val current = legs.last()
                val renamed = name != null && current.name != null && name != current.name
                val bendOnSameStreet = name != null && name == current.name && abs(turn) < 60
                if (current.meters >= MIN_LEG_METERS && ((abs(turn) >= TURN_DEGREES && !bendOnSameStreet) || renamed)) {
                    legs += Leg(0.0, turn = turn)
                }
            }
            legs.last().meters += lengths[i]
            path.segmentNames[i]?.let { legs.last().names.merge(it, lengths[i], Double::plus) }
        }

        fun rounded(meters: Double) = max(5, (meters / 5).roundToInt() * 5)
        return legs.mapIndexed { index, leg ->
            val street = leg.name
            when {
                index == 0 -> "Head ${compass(bearingBetween(0, pointAhead(0)))}" +
                    (street?.let { " along $it" } ?: "") + " for ${rounded(leg.meters)} m"
                abs(leg.turn) < TURN_DEGREES -> "Continue" + (street?.let { " onto $it" } ?: "") + " for ${rounded(leg.meters)} m"
                else -> "Turn ${turnWord(leg.turn)}" + (street?.let { " onto $it" } ?: "") + ", walk ${rounded(leg.meters)} m"
            }
        } + "Enter through $entranceName"
    }

    private fun normalize(degrees: Double): Double {
        var d = degrees % 360.0
        if (d > 180) d -= 360.0
        if (d < -180) d += 360.0
        return d
    }

    private fun turnWord(turn: Double) = when {
        turn > 135 || turn < -135 -> "around"
        turn > 0 && turn < 60 -> "slightly right"
        turn < 0 && turn > -60 -> "slightly left"
        turn > 0 -> "right"
        else -> "left"
    }

    private fun compass(bearing: Double): String {
        val names = listOf("north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west")
        return names[(((bearing % 360) + 360 + 22.5) / 45).toInt() % 8]
    }

    private fun Mall.toGeo(point: PointDto): GeoPointDto {
        val u = (point.x / size.x).coerceIn(0.0, 1.0)
        val v = (point.y / size.y).coerceIn(0.0, 1.0)
        return GeoPointDto(
            upperLeft.latitude + v * (downRight.latitude - upperLeft.latitude),
            upperLeft.longitude + u * (downRight.longitude - upperLeft.longitude),
        )
    }

    private companion object {
        const val WALKING_SPEED = 1.3
        const val WHEELCHAIR_SPEED = 1.0
        const val TURN_WINDOW_METERS = 10.0
        const val TURN_DEGREES = 40.0
        const val MIN_LEG_METERS = 25.0
    }
}
