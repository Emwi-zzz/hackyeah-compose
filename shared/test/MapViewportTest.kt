package test

import core.geometry.GeoPoint
import features.map.domain.MapViewport
import kotlin.test.Test
import kotlin.test.assertTrue

class MapViewportTest {

    @Test
    fun testVisibleTilesGeneration() {
        val viewport = MapViewport(
            center = GeoPoint.KRAKOW_CENTER,
            zoom = 14.0,
            screenWidth = 1000f,
            screenHeight = 800f
        )

        val tiles = viewport.visibleTileCoordinates()

        assertTrue(tiles.isNotEmpty(), "Visible tiles should not be empty")
        for (tile in tiles) {
            assertEquals(14, tile.zoom, "All tiles should have zoom 14")
            assertTrue(tile.x >= 0, "Tile x should be non-negative")
            assertTrue(tile.y >= 0, "Tile y should be non-negative")
        }
    }

    private fun assertEquals(expected: Int, actual: Int, message: String) {
        kotlin.test.assertEquals(expected, actual, message)
    }
}
