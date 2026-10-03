package test

import features.indoor.presentation.IndoorBuildingLayer
import sklepsearch.ShopColorGenerator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ShopColorGeneratorTest {

    @Test
    fun testDeterministicColorGeneration() {
        val shopId1 = 101L
        val shopId2 = 205L

        val color1a = ShopColorGenerator.colorForShopId(shopId1)
        val color1b = ShopColorGenerator.colorForShopId(shopId1)
        val color2 = ShopColorGenerator.colorForShopId(shopId2)

        // Same shopId must produce identical color
        assertEquals(color1a, color1b, "Colors for same Shopid must be identical")

        // Different shopIds should produce different colors
        assertNotEquals(color1a, color2, "Different Shopids should produce different colors")
    }

    @Test
    fun testWallColorDeterministic() {
        val shopId = 303L
        val wallColor1 = ShopColorGenerator.wallColorForShopId(shopId)
        val wallColor2 = ShopColorGenerator.wallColorForShopId(shopId)

        assertEquals(wallColor1, wallColor2)
        assertTrue(wallColor1.alpha > 0.99f)
    }

    @Test
    fun testDetailZoomThreshold() {
        assertEquals(15.5, IndoorBuildingLayer.DETAIL_ZOOM_THRESHOLD)
    }
}
