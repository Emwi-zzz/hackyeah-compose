package test

import core.geometry.GeoMath
import core.geometry.GeoPoint
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeoMathTest {

    @Test
    fun testHaversineDistanceBetweenRynekAndWawel() {
        val rynek = GeoPoint.KRAKOW_RYNEK
        val wawel = GeoPoint.WAWEL_CASTLE

        val distMeters = GeoMath.haversineDistanceMeters(rynek, wawel)

        // Actual straight-line distance is ~880-920 meters
        assertTrue(distMeters in 800.0..1050.0, "Distance between Rynek and Wawel should be ~900m, got: $distMeters")
    }

    @Test
    fun testDistanceFormatting() {
        assertEquals("450 m", GeoMath.formatDistance(450.0))
        assertEquals("1.85 km", GeoMath.formatDistance(1850.0))
    }

    @Test
    fun testPolygonArea() {
        // Approximate square around Rynek Główny (~200m x 200m = ~40,000 m²)
        val square = listOf(
            GeoPoint(50.0628, 19.9360),
            GeoPoint(50.0628, 19.9386),
            GeoPoint(50.0610, 19.9386),
            GeoPoint(50.0610, 19.9360)
        )

        val area = GeoMath.polygonAreaSquareMeters(square)
        assertTrue(area in 30000.0..50000.0, "Rynek square area should be ~40,000 m², got: $area")
    }
}
