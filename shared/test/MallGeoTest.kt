package test

import sklepsearch.*
import kotlin.test.*

class MallGeoTest {
    @Test
    fun testMallCoordinateTransformation() {
        val mall = TestIndoorData.krakowska
        val centerPoint = Point(500.0, 500.0)

        val geo = mall.pointToGeo(centerPoint)
        val reconstructed = mall.geoToPoint(geo)

        assertTrue(kotlin.math.abs(centerPoint.x - reconstructed.x) < 1e-4, "X roundtrip should match")
        assertTrue(kotlin.math.abs(centerPoint.y - reconstructed.y) < 1e-4, "Y roundtrip should match")
    }
}
