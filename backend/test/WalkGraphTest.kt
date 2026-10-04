import backend.outdoor.WalkGraph
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WalkGraphTest {

    // 0 --- 1 --- 2      top street (long way round)
    // |           |
    // 3 ==steps== 4      short cut over steps
    private val step = 0.001
    private val graph = WalkGraph(
        lat = doubleArrayOf(50.0 + step, 50.0 + step, 50.0 + step, 50.0, 50.0),
        lon = doubleArrayOf(20.0, 20.0 + step, 20.0 + 2 * step, 20.0, 20.0 + 2 * step),
        names = listOf("Top", "Side", "Stairs"),
        edgeFrom = intArrayOf(0, 1, 0, 2, 3),
        edgeTo = intArrayOf(1, 2, 3, 4, 4),
        edgeName = intArrayOf(0, 0, 1, 1, 2),
        edgeFlags = byteArrayOf(0, 0, 0, 0, WalkGraph.FLAG_NOT_ACCESSIBLE.toByte()),
    )

    private fun routeAcross(accessibleOnly: Boolean): WalkGraph.Path {
        val start = assertNotNull(graph.snap(50.0, 20.0, accessibleOnly))
        val target = WalkGraph.Target(50.0, 20.0 + 2 * step)
        val targetSnap = assertNotNull(graph.snap(target.lat, target.lon, accessibleOnly))
        return assertNotNull(graph.route(start, listOf(target to targetSnap), accessibleOnly))
    }

    @Test
    fun takesStepsWhenAllowed() {
        val path = routeAcross(accessibleOnly = false)
        assertTrue(path.walkMeters < 150, "expected the ~143 m short cut, got ${path.walkMeters}")
        assertTrue("Stairs" in path.segmentNames)
    }

    @Test
    fun accessibleRouteAvoidsSteps() {
        val path = routeAcross(accessibleOnly = true)
        assertTrue(path.walkMeters > 350, "expected the long way round, got ${path.walkMeters}")
        assertTrue("Stairs" !in path.segmentNames)
    }

    @Test
    fun picksTargetWithLowestTotalCost() {
        val start = assertNotNull(graph.snap(50.0, 20.0, false))
        val near = WalkGraph.Target(50.0 + step, 20.0, extraCost = 500.0)
        val far = WalkGraph.Target(50.0 + step, 20.0 + 2 * step, extraCost = 0.0)
        val targets = listOf(near, far).map { it to assertNotNull(graph.snap(it.lat, it.lon, false)) }
        assertEquals(1, assertNotNull(graph.route(start, targets, false)).targetIndex)
    }

    @Test
    fun routesAcrossKrakowWhenGraphIsBuilt() {
        val file = File("data/krakow-walk.graph.gz")
        if (!file.exists()) return
        val krakow = WalkGraph.load(file)
        // Main Market Square -> Galeria Krakowska
        val start = assertNotNull(krakow.snap(50.0617, 19.9373, false))
        val target = WalkGraph.Target(50.0672, 19.9455)
        val targetSnap = assertNotNull(krakow.snap(target.lat, target.lon, false))
        val started = System.nanoTime()
        val path = assertNotNull(krakow.route(start, listOf(target to targetSnap), false))
        val millis = (System.nanoTime() - started) / 1_000_000
        println("Rynek -> Galeria Krakowska: ${path.walkMeters.toInt()} m, ${path.points.size} points, $millis ms")
        assertTrue(path.walkMeters in 800.0..1500.0)
    }
}
