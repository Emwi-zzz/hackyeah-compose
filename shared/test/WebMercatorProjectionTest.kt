package test

import androidx.compose.ui.geometry.Offset
import core.geometry.GeoPoint
import core.geometry.WebMercatorProjection
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class WebMercatorProjectionTest {

    @Test
    fun testWorldCoordinatesRoundTrip() {
        val testPoints = listOf(
            GeoPoint.KRAKOW_RYNEK,
            GeoPoint.WAWEL_CASTLE,
            GeoPoint.KAZIMIERZ_PLAC_NOWY,
            GeoPoint(0.0, 0.0),
            GeoPoint(45.0, 45.0),
            GeoPoint(-45.0, -45.0)
        )

        for (pt in testPoints) {
            val wx = WebMercatorProjection.toWorldX(pt.longitude)
            val wy = WebMercatorProjection.toWorldY(pt.latitude)

            assertTrue(wx in 0.0..1.0, "WorldX should be in [0, 1]")
            assertTrue(wy in 0.0..1.0, "WorldY should be in [0, 1]")

            val backLon = WebMercatorProjection.toGeoLongitude(wx)
            val backLat = WebMercatorProjection.toGeoLatitude(wy)

            assertTrue(abs(backLon - pt.longitude) < 1e-6, "Longitude roundtrip failed for $pt")
            assertTrue(abs(backLat - pt.latitude) < 1e-6, "Latitude roundtrip failed for $pt")
        }
    }

    @Test
    fun testScreenCoordinatesRoundTrip() {
        val center = GeoPoint.KRAKOW_CENTER
        val zoom = 14.5
        val width = 1200f
        val height = 800f

        val target = GeoPoint.WAWEL_CASTLE
        val screenPos = WebMercatorProjection.geoToScreen(target, center, zoom, width, height)

        val reconstructedGeo = WebMercatorProjection.screenToGeo(screenPos, center, zoom, width, height)

        assertTrue(abs(reconstructedGeo.latitude - target.latitude) < 1e-6, "Latitude screen roundtrip failed")
        assertTrue(abs(reconstructedGeo.longitude - target.longitude) < 1e-6, "Longitude screen roundtrip failed")
    }

    @Test
    fun testCenterIsAtScreenCenter() {
        val center = GeoPoint.KRAKOW_RYNEK
        val zoom = 15.0
        val width = 1000f
        val height = 600f

        val screenPos = WebMercatorProjection.geoToScreen(center, center, zoom, width, height)

        assertTrue(abs(screenPos.x - 500f) < 0.01f, "Center screen X should be 500")
        assertTrue(abs(screenPos.y - 300f) < 0.01f, "Center screen Y should be 300")
    }
}
