import core.geometry.GeoPoint
import features.admin.domain.MallFactory
import sklepsearch.geoToPoint
import kotlin.test.*

class MallFactoryTest {
    @Test
    fun outlineFitsInsideMallFrame() {
        val geo = listOf(GeoPoint(50.060, 19.940), GeoPoint(50.060, 19.945), GeoPoint(50.063, 19.945), GeoPoint(50.063, 19.940))
        val mall = MallFactory.fromOutline("  Test  ", geo)
        assertEquals("Test", mall.name)
        assertEquals(1, mall.floors.size)
        val b = mall.outline!!.getBounds()
        assertTrue(b.minX > 0 && b.minY > 0 && b.maxX < mall.size.x && b.maxY < mall.size.y)
        assertEquals(geo.size, mall.outline!!.getVertices().distinctBy { it }.size)
        assertEquals(mall.outline!!.toSvgPath(), mall.floors[0].box.toSvgPath())
    }

    @Test
    fun needsThreePoints() {
        assertFailsWith<IllegalArgumentException> { MallFactory.fromOutline("x", listOf(GeoPoint(1.0, 1.0))) }
    }
}
