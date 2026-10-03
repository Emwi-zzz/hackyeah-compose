package test

import core.geometry.GeoPoint
import features.indoor.presentation.IndoorBuildingLayer
import features.map.data.TileRepositoryImpl
import features.map.domain.MapViewport
import features.map.presentation.MapState
import features.rendering.domain.LayerRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import sklepsearch.*
import kotlin.test.*

class MultiBuildingIndoorTest {

    private fun createMapState(): MapState {
        val tileRepo = TileRepositoryImpl()
        val layerRegistry = LayerRegistry(emptyList())
        val scope = CoroutineScope(Dispatchers.Unconfined)
        return MapState(tileRepo, layerRegistry, scope)
    }

    @Test
    fun testGaleriaKazimierzComplexShapeAndFloors() {
        val kazimierz = MockGaleriaKazimierz.INSTANCE

        assertEquals(2L, kazimierz.id)
        assertEquals("Galeria Kazimierz", kazimierz.name)
        assertEquals(3, kazimierz.floors.size, "Should have 3 floors (0, 1, 2)")

        val floor0 = kazimierz.getFloor(0)
        assertNotNull(floor0)
        assertTrue(floor0.stores.isNotEmpty(), "Floor 0 should have stores")

        // Check Shopid determinism and distinctness
        val shopIds = floor0.stores.map { it.Shopid }
        assertEquals(shopIds.distinct().size, shopIds.size, "All stores must have distinct Shopids")

        // Check escalators have direction
        for (floor in kazimierz.floors) {
            for (escalator in floor.escalators) {
                assertTrue(
                    escalator.direction == EscalatorDirection.UP || escalator.direction == EscalatorDirection.DOWN,
                    "Escalator must have direction UP or DOWN"
                )
            }
        }
    }

    @Test
    fun testComplexLShapedPolygonContainment() {
        val kazimierz = MockGaleriaKazimierz.INSTANCE
        val floor0 = kazimierz.getFloor(0)
        assertNotNull(floor0)
        val complexBox = floor0.box

        // Point inside the main central wing (x: 400, y: 300)
        assertTrue(complexBox.contains(Point(400.0, 300.0)), "Center of wing should be inside L-shape")

        // Point inside the east wing (x: 750, y: 600)
        assertTrue(complexBox.contains(Point(750.0, 600.0)), "East wing should be inside L-shape")

        // Point in the inner courtyard notch (x: 300, y: 700) -> should be OUTSIDE L-shape
        assertFalse(complexBox.contains(Point(300.0, 700.0)), "Courtyard notch must be outside L-shape")

        // Point completely outside (x: 20, y: 20)
        assertFalse(complexBox.contains(Point(20.0, 20.0)), "Point outside bounds must be outside")

        // Point inside the sweeping cubic Bézier northeast rotunda facade
        assertTrue(complexBox.contains(Point(840.0, 200.0)), "Point inside northeast curved glass rotunda must be inside")

        // Point inside the curved southern riverfront facade (quadratic Bézier curve bulges to y=890 at x=730)
        // Straight baseline was at y=860, so (730, 875) is inside strictly due to the Bézier curve!
        assertTrue(complexBox.contains(Point(730.0, 875.0)), "Point inside curved southern riverfront facade must be inside")

        // Point beyond the southern curve apex (x: 730, y: 910) -> should be OUTSIDE
        assertFalse(complexBox.contains(Point(730.0, 910.0)), "Point beyond southern curve apex must be outside")
    }

    @Test
    fun testFocusBasedOnPositionAndScale() {
        val mapState = createMapState()
        assertEquals(2, mapState.malls.size, "Should have 2 malls (Krakowska & Kazimierz)")

        // 1. Center map around Galeria Krakowska
        val krakowskaCenter = MockGaleriaKrakowska.INSTANCE.getBoundingBox().center
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
        val kazimierzCenter = MockGaleriaKazimierz.INSTANCE.getBoundingBox().center
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
        val krakowska = MockGaleriaKrakowska.INSTANCE
        val kazimierz = MockGaleriaKazimierz.INSTANCE

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
