package backend.outdoor

import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.File
import java.util.zip.GZIPInputStream
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * Pedestrian street network held in memory (built from OpenStreetMap by [WalkGraphBuilder]).
 *
 * Distances use a local equirectangular projection, which is accurate to well under 1% at city scale and
 * keeps the A* straight-line estimate consistent with the edge weights.
 */
class WalkGraph(
    val lat: DoubleArray,
    val lon: DoubleArray,
    val names: List<String>,
    edgeFrom: IntArray,
    edgeTo: IntArray,
    edgeName: IntArray,
    edgeFlags: ByteArray,
) {
    data class Snap(val edge: Int, val t: Double, val lat: Double, val lon: Double, val distanceMeters: Double)

    /** A place to reach, with an extra cost added once it is reached (e.g. the indoor walk behind an entrance). */
    data class Target(val lat: Double, val lon: Double, val extraCost: Double = 0.0)

    data class Path(
        val targetIndex: Int,
        val points: List<Pair<Double, Double>>,
        /** Street name per polyline segment (null when unnamed), aligned with points.zipWithNext(). */
        val segmentNames: List<String?>,
        val walkMeters: Double,
        val totalCost: Double,
    )

    val nodeCount = lat.size
    val edgeCount = edgeFrom.size
    private val from = edgeFrom
    private val to = edgeTo
    private val edgeNames = edgeName
    private val flags = edgeFlags
    private val length = DoubleArray(edgeCount) { distance(lat[from[it]], lon[from[it]], lat[to[it]], lon[to[it]]) }

    // Adjacency in compressed sparse row form: for node n, slots offsets[n] until offsets[n + 1] hold edge ids
    private val offsets = IntArray(nodeCount + 1)
    private val adjacentEdges = IntArray(edgeCount * 2)

    private val cells = HashMap<Long, IntArray>()

    init {
        for (e in 0 until edgeCount) {
            offsets[from[e] + 1]++
            offsets[to[e] + 1]++
        }
        for (n in 0 until nodeCount) offsets[n + 1] += offsets[n]
        val fill = offsets.copyOf(nodeCount)
        for (e in 0 until edgeCount) {
            adjacentEdges[fill[from[e]]++] = e
            adjacentEdges[fill[to[e]]++] = e
        }

        val buckets = HashMap<Long, MutableList<Int>>()
        for (e in 0 until edgeCount) {
            val (minRow, maxRow) = sorted(row(lat[from[e]]), row(lat[to[e]]))
            val (minCol, maxCol) = sorted(col(lon[from[e]]), col(lon[to[e]]))
            for (r in minRow..maxRow) for (c in minCol..maxCol) buckets.getOrPut(key(r, c)) { mutableListOf() } += e
        }
        buckets.forEach { (k, v) -> cells[k] = v.toIntArray() }
    }

    fun isAccessible(edge: Int) = flags[edge].toInt() and FLAG_NOT_ACCESSIBLE == 0

    /** Nearest point on the network, searching rings of grid cells up to [maxMeters] away. */
    fun snap(latitude: Double, longitude: Double, accessibleOnly: Boolean, maxMeters: Double = 1_000.0): Snap? {
        val r0 = row(latitude)
        val c0 = col(longitude)
        val maxRing = (maxMeters / CELL_METERS).toInt() + 1
        var best: Snap? = null
        for (ring in 0..maxRing) {
            for (r in r0 - ring..r0 + ring) for (c in c0 - ring..c0 + ring) {
                if (maxOf(kotlin.math.abs(r - r0), kotlin.math.abs(c - c0)) != ring) continue
                for (e in cells[key(r, c)] ?: continue) {
                    if (accessibleOnly && !isAccessible(e)) continue
                    val candidate = project(e, latitude, longitude)
                    if (best == null || candidate.distanceMeters < best.distanceMeters) best = candidate
                }
            }
            // Anything in a further ring is at least ring * CELL_METERS away
            if (best != null && best.distanceMeters <= ring * CELL_METERS) break
        }
        return best?.takeIf { it.distanceMeters <= maxMeters }
    }

    /**
     * A* from [start] to whichever target is cheapest to reach (walking distance plus the target's extra cost).
     * Each target is snapped to the network and joined to it by a straight segment.
     */
    fun route(start: Snap, targets: List<Pair<Target, Snap>>, accessibleOnly: Boolean): Path? {
        if (targets.isEmpty()) return null
        val best = DoubleArray(nodeCount) { Double.POSITIVE_INFINITY }
        val previousEdge = IntArray(nodeCount) { -1 }
        val heap = MinHeap()

        // Leaving a node towards a target: node -> list of (target index, remaining cost)
        val exits = HashMap<Int, MutableList<Pair<Int, Double>>>()
        targets.forEachIndexed { i, (target, snap) ->
            val tail = snap.distanceMeters + target.extraCost
            exits.getOrPut(from[snap.edge]) { mutableListOf() } += i to snap.t * length[snap.edge] + tail
            exits.getOrPut(to[snap.edge]) { mutableListOf() } += i to (1 - snap.t) * length[snap.edge] + tail
        }

        fun estimate(n: Int) = targets.minOf { (target, snap) ->
            distance(lat[n], lon[n], snap.lat, snap.lon) + snap.distanceMeters + target.extraCost
        }

        var bestCost = Double.POSITIVE_INFINITY
        var bestTarget = -1
        var bestVia = -1 // network node the best target is reached from; -1 when start and target share the edge
        targets.forEachIndexed { i, (target, snap) ->
            if (snap.edge == start.edge) {
                val direct = kotlin.math.abs(snap.t - start.t) * length[start.edge] + snap.distanceMeters + target.extraCost
                if (direct < bestCost) {
                    bestCost = direct
                    bestTarget = i
                }
            }
        }

        fun push(node: Int, cost: Double, edge: Int) {
            if (cost < best[node]) {
                best[node] = cost
                previousEdge[node] = edge
                heap.push(node, cost + estimate(node))
            }
        }
        push(from[start.edge], start.t * length[start.edge], -1)
        push(to[start.edge], (1 - start.t) * length[start.edge], -1)

        while (heap.isNotEmpty()) {
            val priority = heap.peekPriority()
            val node = heap.pop()
            if (priority >= bestCost) break
            val g = best[node]
            if (priority > g + estimate(node) + 1e-9) continue // stale heap entry
            exits[node]?.forEach { (target, remaining) ->
                if (g + remaining < bestCost) {
                    bestCost = g + remaining
                    bestTarget = target
                    bestVia = node
                }
            }
            for (slot in offsets[node] until offsets[node + 1]) {
                val e = adjacentEdges[slot]
                if (accessibleOnly && !isAccessible(e)) continue
                val next = if (from[e] == node) to[e] else from[e]
                push(next, g + length[e], e)
            }
        }
        if (bestTarget < 0) return null

        val (target, targetSnap) = targets[bestTarget]
        val points = mutableListOf<Pair<Double, Double>>()
        val segmentNames = mutableListOf<String?>()
        fun nameOf(e: Int) = edgeNames[e].takeIf { it >= 0 }?.let { names[it] }

        if (bestVia >= 0) {
            val nodes = mutableListOf<Int>()
            val edges = mutableListOf<Int>()
            var cursor = bestVia
            while (true) {
                nodes += cursor
                val e = previousEdge[cursor]
                if (e < 0) break
                edges += e
                cursor = if (from[e] == cursor) to[e] else from[e]
            }
            nodes.reverse()
            edges.reverse()
            points += start.lat to start.lon
            nodes.forEachIndexed { i, n ->
                segmentNames += if (i == 0) nameOf(start.edge) else nameOf(edges[i - 1])
                points += lat[n] to lon[n]
            }
            segmentNames += nameOf(targetSnap.edge)
            points += targetSnap.lat to targetSnap.lon
        } else {
            points += start.lat to start.lon
            segmentNames += nameOf(start.edge)
            points += targetSnap.lat to targetSnap.lon
        }
        segmentNames += null
        points += target.lat to target.lon

        val walk = points.zipWithNext().sumOf { (a, b) -> distance(a.first, a.second, b.first, b.second) }
        return Path(bestTarget, points, segmentNames, walk, bestCost)
    }

    private fun project(edge: Int, latitude: Double, longitude: Double): Snap {
        val ax = x(lon[from[edge]]); val ay = y(lat[from[edge]])
        val bx = x(lon[to[edge]]); val by = y(lat[to[edge]])
        val px = x(longitude); val py = y(latitude)
        val dx = bx - ax
        val dy = by - ay
        val lengthSquared = dx * dx + dy * dy
        val t = if (lengthSquared == 0.0) 0.0 else (((px - ax) * dx + (py - ay) * dy) / lengthSquared).coerceIn(0.0, 1.0)
        val qLat = lat[from[edge]] + (lat[to[edge]] - lat[from[edge]]) * t
        val qLon = lon[from[edge]] + (lon[to[edge]] - lon[from[edge]]) * t
        return Snap(edge, t, qLat, qLon, hypot(px - (ax + dx * t), py - (ay + dy * t)))
    }

    private class MinHeap {
        private var nodes = IntArray(1024)
        private var priorities = DoubleArray(1024)
        private var size = 0

        fun isNotEmpty() = size > 0
        fun peekPriority() = priorities[0]

        fun push(node: Int, priority: Double) {
            if (size == nodes.size) {
                nodes = nodes.copyOf(size * 2)
                priorities = priorities.copyOf(size * 2)
            }
            var i = size++
            while (i > 0) {
                val parent = (i - 1) / 2
                if (priorities[parent] <= priority) break
                nodes[i] = nodes[parent]
                priorities[i] = priorities[parent]
                i = parent
            }
            nodes[i] = node
            priorities[i] = priority
        }

        fun pop(): Int {
            val top = nodes[0]
            size--
            if (size > 0) {
                val node = nodes[size]
                val priority = priorities[size]
                var i = 0
                while (true) {
                    var child = 2 * i + 1
                    if (child >= size) break
                    if (child + 1 < size && priorities[child + 1] < priorities[child]) child++
                    if (priorities[child] >= priority) break
                    nodes[i] = nodes[child]
                    priorities[i] = priorities[child]
                    i = child
                }
                nodes[i] = node
                priorities[i] = priority
            }
            return top
        }
    }

    companion object {
        const val MAGIC = 0x4B574731 // "KWG1"
        const val FLAG_NOT_ACCESSIBLE = 1

        private const val METERS_PER_DEGREE = 111_320.0
        private const val REFERENCE_LATITUDE = 50.06
        private val LON_SCALE = cos(Math.toRadians(REFERENCE_LATITUDE))
        private const val CELL_METERS = 150.0

        private fun x(longitude: Double) = longitude * LON_SCALE * METERS_PER_DEGREE
        private fun y(latitude: Double) = latitude * METERS_PER_DEGREE

        fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val dx = (lon2 - lon1) * LON_SCALE * METERS_PER_DEGREE
            val dy = (lat2 - lat1) * METERS_PER_DEGREE
            return sqrt(dx * dx + dy * dy)
        }

        private fun row(latitude: Double) = floor(y(latitude) / CELL_METERS).toInt()
        private fun col(longitude: Double) = floor(x(longitude) / CELL_METERS).toInt()
        private fun key(row: Int, col: Int) = (row.toLong() shl 32) or (col.toLong() and 0xFFFFFFFFL)
        private fun sorted(a: Int, b: Int) = if (a <= b) a to b else b to a

        fun load(file: File): WalkGraph =
            DataInputStream(BufferedInputStream(GZIPInputStream(file.inputStream()), 1 shl 16)).use { input ->
                require(input.readInt() == MAGIC) { "${file.path} is not a walk graph file" }
                val nodes = input.readInt()
                val lat = DoubleArray(nodes) { input.readInt() / 1e7 }
                val lon = DoubleArray(nodes) { input.readInt() / 1e7 }
                val names = List(input.readInt()) { input.readUTF() }
                val edges = input.readInt()
                val from = IntArray(edges) { input.readInt() }
                val to = IntArray(edges) { input.readInt() }
                val name = IntArray(edges) { input.readInt() }
                val flags = ByteArray(edges) { input.readByte() }
                WalkGraph(lat, lon, names, from, to, name, flags)
            }
    }
}
