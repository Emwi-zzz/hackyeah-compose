package backend.outdoor

import crosby.binary.BinaryParser
import crosby.binary.Osmformat
import crosby.binary.file.BlockInputStream
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.util.zip.GZIPOutputStream
import kotlin.system.exitProcess

/**
 * Converts an OpenStreetMap PBF extract into the compact walking graph loaded by [WalkGraph].
 *
 * Usage: `./gradlew :backend:buildWalkGraph -Ppbf=/path/malopolskie-latest.osm.pbf`
 */
fun main(args: Array<String>) {
    if (args.size < 2) {
        System.err.println("Usage: WalkGraphBuilder <input.osm.pbf> <output.graph.gz> [south west north east]")
        exitProcess(1)
    }
    val bbox = if (args.size >= 6) args.drop(2).take(4).map { it.toDouble() } else WalkGraphBuilder.KRAKOW_BBOX
    WalkGraphBuilder(File(args[0]), bbox).build(File(args[1]))
}

class WalkGraphBuilder(private val pbf: File, private val bbox: List<Double>) {

    private class LongList {
        var data = LongArray(1 shl 16)
        var size = 0
        fun add(value: Long) {
            if (size == data.size) data = data.copyOf(size * 2)
            data[size++] = value
        }
    }

    private class IntList {
        var data = IntArray(1 shl 16)
        var size = 0
        fun add(value: Int) {
            if (size == data.size) data = data.copyOf(size * 2)
            data[size++] = value
        }
    }

    private val wayRefs = LongList()
    private val wayStarts = IntList()
    private val wayFlags = IntList()
    private val wayNames = IntList()
    private val names = mutableListOf<String>()
    private val nameIndex = HashMap<String, Int>()

    fun build(output: File) {
        val started = System.currentTimeMillis()
        readWays()
        wayStarts.add(wayRefs.size)
        println("Walkable ways: ${wayStarts.size - 1}, node references: ${wayRefs.size}")

        val needed = wayRefs.data.copyOf(wayRefs.size).also { it.sort() }.distinctSorted()
        val lat = IntArray(needed.size) { Int.MIN_VALUE }
        val lon = IntArray(needed.size)
        readNodes(needed, lat, lon)

        val (south, west, north, east) = bbox
        fun usable(i: Int) = lat[i] != Int.MIN_VALUE &&
            lat[i] / 1e7 in south..north && lon[i] / 1e7 in west..east

        val from = IntList()
        val to = IntList()
        val edgeName = IntList()
        val edgeFlags = IntList()
        for (w in 0 until wayStarts.size - 1) {
            var previous = -1
            for (r in wayStarts.data[w] until wayStarts.data[w + 1]) {
                val node = needed.binarySearch(wayRefs.data[r]).takeIf { it >= 0 && usable(it) } ?: -1
                if (previous >= 0 && node >= 0 && previous != node) {
                    from.add(previous)
                    to.add(node)
                    edgeName.add(wayNames.data[w])
                    edgeFlags.add(wayFlags.data[w])
                }
                previous = node
            }
        }

        // Keep only the largest connected component so that every snapped point can reach every other one
        val parent = IntArray(needed.size) { it }
        fun find(x: Int): Int {
            var root = x
            while (parent[root] != root) root = parent[root]
            var cursor = x
            while (parent[cursor] != root) {
                val next = parent[cursor]
                parent[cursor] = root
                cursor = next
            }
            return root
        }
        for (e in 0 until from.size) {
            val a = find(from.data[e])
            val b = find(to.data[e])
            if (a != b) parent[a] = b
        }
        val componentSize = IntArray(needed.size)
        for (e in 0 until from.size) componentSize[find(from.data[e])]++
        val mainRoot = componentSize.indices.maxBy { componentSize[it] }

        val remap = IntArray(needed.size) { -1 }
        var nodeCount = 0
        for (e in 0 until from.size) {
            if (find(from.data[e]) != mainRoot) continue
            for (n in intArrayOf(from.data[e], to.data[e])) if (remap[n] < 0) remap[n] = nodeCount++
        }
        val keptEdges = (0 until from.size).filter { remap[from.data[it]] >= 0 }

        output.parentFile?.mkdirs()
        DataOutputStream(BufferedOutputStream(GZIPOutputStream(output.outputStream()), 1 shl 16)).use { out ->
            out.writeInt(WalkGraph.MAGIC)
            out.writeInt(nodeCount)
            val nodeLat = IntArray(nodeCount)
            val nodeLon = IntArray(nodeCount)
            for (i in needed.indices) if (remap[i] >= 0) {
                nodeLat[remap[i]] = lat[i]
                nodeLon[remap[i]] = lon[i]
            }
            nodeLat.forEach { out.writeInt(it) }
            nodeLon.forEach { out.writeInt(it) }
            out.writeInt(names.size)
            names.forEach { out.writeUTF(it) }
            out.writeInt(keptEdges.size)
            keptEdges.forEach { out.writeInt(remap[from.data[it]]) }
            keptEdges.forEach { out.writeInt(remap[to.data[it]]) }
            keptEdges.forEach { out.writeInt(edgeName.data[it]) }
            keptEdges.forEach { out.writeByte(edgeFlags.data[it]) }
        }
        println(
            "Graph: $nodeCount nodes, ${keptEdges.size} edges, ${names.size} street names -> " +
                "${output.path} (${output.length() / 1024} KiB) in ${(System.currentTimeMillis() - started) / 1000} s"
        )
    }

    private fun readWays() = parse(object : Parser() {
        override fun parseWays(ways: List<Osmformat.Way>) {
            for (way in ways) {
                val tags = HashMap<String, String>(way.keysCount * 2)
                for (i in 0 until way.keysCount) tags[getStringById(way.getKeys(i))] = getStringById(way.getVals(i))
                val flags = walkFlags(tags) ?: continue
                wayStarts.add(wayRefs.size)
                wayFlags.add(flags)
                wayNames.add(tags["name"]?.let { name -> nameIndex.getOrPut(name) { names.add(name); names.lastIndex } } ?: -1)
                var ref = 0L
                for (i in 0 until way.refsCount) {
                    ref += way.getRefs(i)
                    wayRefs.add(ref)
                }
            }
        }
    })

    private fun readNodes(needed: LongArray, lat: IntArray, lon: IntArray) = parse(object : Parser() {
        fun store(id: Long, latitude: Double, longitude: Double) {
            val index = needed.binarySearch(id)
            if (index < 0) return
            lat[index] = Math.round(latitude * 1e7).toInt()
            lon[index] = Math.round(longitude * 1e7).toInt()
        }

        override fun parseDense(nodes: Osmformat.DenseNodes) {
            var id = 0L
            var rawLat = 0L
            var rawLon = 0L
            for (i in 0 until nodes.idCount) {
                id += nodes.getId(i)
                rawLat += nodes.getLat(i)
                rawLon += nodes.getLon(i)
                store(id, parseLat(rawLat), parseLon(rawLon))
            }
        }

        override fun parseNodes(nodes: List<Osmformat.Node>) {
            for (node in nodes) store(node.id, parseLat(node.lat), parseLon(node.lon))
        }
    })

    private fun parse(parser: Parser) = FileInputStream(pbf).use { BlockInputStream(it, parser).process() }

    private abstract class Parser : BinaryParser() {
        override fun parseRelations(relations: List<Osmformat.Relation>) = Unit
        override fun parseDense(nodes: Osmformat.DenseNodes) = Unit
        override fun parseNodes(nodes: List<Osmformat.Node>) = Unit
        override fun parseWays(ways: List<Osmformat.Way>) = Unit
        override fun parse(header: Osmformat.HeaderBlock) = Unit
        override fun complete() = Unit
    }

    companion object {
        /** Kraków city limits with a small margin: south, west, north, east. */
        val KRAKOW_BBOX = listOf(49.95, 19.78, 50.14, 20.23)

        private val WALKABLE = setOf(
            "footway", "pedestrian", "path", "steps", "living_street", "residential", "service",
            "unclassified", "tertiary", "tertiary_link", "secondary", "secondary_link", "primary", "primary_link",
            "track", "corridor", "platform", "road",
        )
        private val FOOT_ALLOWED = setOf("yes", "designated", "permissive")

        /** Returns the edge flags for a walkable way, or null when pedestrians cannot use it. */
        fun walkFlags(tags: Map<String, String>): Int? {
            val highway = tags["highway"] ?: return null
            val foot = tags["foot"]
            val explicitlyAllowed = foot in FOOT_ALLOWED
            if (foot == "no" || foot == "private") return null
            if (!explicitlyAllowed) {
                if (highway !in WALKABLE) return null
                if (tags["access"] == "no" || tags["access"] == "private") return null
            }
            if (tags["area"] == "yes" && highway != "pedestrian" && highway != "footway") return null
            var flags = 0
            if (highway == "steps" || tags["wheelchair"] == "no") flags = flags or WalkGraph.FLAG_NOT_ACCESSIBLE
            return flags
        }

        private fun LongArray.distinctSorted(): LongArray {
            if (isEmpty()) return this
            var count = 1
            for (i in 1 until size) if (this[i] != this[count - 1]) this[count++] = this[i]
            return copyOf(count)
        }
    }
}
