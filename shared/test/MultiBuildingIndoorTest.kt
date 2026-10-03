package test

import core.geometry.GeoPoint
import features.indoor.presentation.IndoorBuildingLayer
import features.map.domain.MapViewport
import features.map.presentation.MapState
import sklepsearch.*
import kotlin.test.*

class MultiBuildingIndoorTest {

    private fun createMapState(): MapState = createTestMapState()

    @Test
    fun testFocusBasedOnPositionAndScale() {
        val mapState = createMapState()
        assertEquals(2, mapState.malls.size, "Should have 2 malls (Krakowska & Kazimierz)")

        // 1. Center map around Galeria Krakowska
        val krakowskaCenter = TestIndoorData.krakowska.getBoundingBox().center
        mapState.viewport = MapViewport(
            center = krakowskaCenter,
            zoom = 16.5,
            screenWidth = 1000f,
            screenHeight = 800f
        )
        mapState.updateFocusBasedOnScaleAndPosition()
        assertEquals("Galeria Krakowska", mapState.focusedMall?.name)
        assertTrue(mapState.isMallOnScreen(), "Galeria Krakowska should be on screen and detailed")

        // 2. Pan map to Galeria Kazimierz
        val kazimierzCenter = TestIndoorData.kazimierz.getBoundingBox().center
        mapState.viewport = MapViewport(
            center = kazimierzCenter,
            zoom = 16.5,
            screenWidth = 1000f,
            screenHeight = 800f
        )
        mapState.userLockedMallId = null
        mapState.updateFocusBasedOnScaleAndPosition()
        assertEquals("Galeria Kazimierz", mapState.focusedMall?.name)
        assertTrue(mapState.isMallOnScreen(), "Galeria Kazimierz should be on screen and detailed")

        // 3. Zoom out to large scale (zoom 14.0)
        mapState.viewport = mapState.viewport.withZoom(14.0)
        assertFalse(
            mapState.isMallOnScreen(),
            "When scale is large (zoom < 15.5), isMallOnScreen() must be false to hide floor chooser"
        )
    }

    @Test
    fun testRefocusByDirectClick() {
        val mapState = createMapState()
        mapState.viewport = MapViewport(
            center = GeoPoint.KRAKOW_CENTER,
            zoom = 14.0,
            screenWidth = 1000f,
            screenHeight = 800f
        )

        // Initial focus can be Krakowska
        val krakowska = TestIndoorData.krakowska
        val kazimierz = TestIndoorData.kazimierz

        mapState.refocusOnMall(krakowska, animate = false)
        assertEquals(krakowska.id, mapState.focusedMall?.id)

        // Refocus on Kazimierz
        mapState.refocusOnMall(kazimierz, animate = false)
        assertEquals(kazimierz.id, mapState.focusedMall?.id)
        assertEquals(0, mapState.currentFloorNumber)
        assertNull(mapState.selectedStore)
        assertTrue(mapState.viewport.zoom >= IndoorBuildingLayer.DETAIL_ZOOM_THRESHOLD)

        // Test refocusing via handleMapClick on Krakowska
        val krakowskaScreen = core.geometry.WebMercatorProjection.geoToScreen(
            geo = krakowska.getBoundingBox().center,
            center = mapState.viewport.center,
            zoom = mapState.viewport.zoom,
            screenWidth = mapState.viewport.screenWidth,
            screenHeight = mapState.viewport.screenHeight
        )
        mapState.handleMapClick(krakowskaScreen)
        assertEquals(krakowska.id, mapState.focusedMall?.id, "Clicking Krakowska on map must refocus to it")
    }
}
