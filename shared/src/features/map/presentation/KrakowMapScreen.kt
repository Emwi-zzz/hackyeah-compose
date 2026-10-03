package features.map.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import features.admin.domain.AdminState
import features.admin.layers.AdminDraftLayer
import features.admin.presentation.AdminPanel
import features.admin.presentation.AdminToggleButton
import features.indoor.presentation.FloorSelectorControl
import features.indoor.presentation.IndoorBuildingLayer
import features.indoor.presentation.IndoorRouteSearchBar
import features.indoor.presentation.StoreDetailSheet
import features.map.data.TileRepositoryImpl
import features.map.presentation.components.CoordinateHUD
import features.map.presentation.components.MapControls
import features.map.presentation.components.PresetLocationsBar
import features.map.presentation.components.TileSourceSelector
import features.places.data.NominatimPlacesRepository
import features.places.presentation.PlaceDetailSheet
import features.places.presentation.SearchBarOverlay
import features.rendering.domain.LayerRegistry
import features.rendering.layers.TileLayer
import features.rendering.presentation.LayerManagerSheet
import features.rendering.presentation.RenderDebugOverlay
import sklepsearch.getFloor

@Composable
fun KrakowMapScreen() {
    val coroutineScope = rememberCoroutineScope()
    var hoveredOffset by remember { mutableStateOf<Offset?>(null) }

    // 1. Core Repositories (Clean Architecture)
    val tileRepository = remember { TileRepositoryImpl() }
    val placesRepository = remember { NominatimPlacesRepository() }

    // 2. Clear old on-top draws; register base map tile layer and indoor building layer
    val indoorBuildingLayer = remember {
        IndoorBuildingLayer(
            selectedFloorNumber = 0
        )
    }

    val initialLayers = remember {
        listOf(
            TileLayer(),
            indoorBuildingLayer
        )
    }

    val layerRegistry = remember {
        LayerRegistry(initialLayers)
    }

    // 3. Map State
    val mapState = remember {
        MapState(
            tileRepository = tileRepository,
            layerRegistry = layerRegistry,
            scope = coroutineScope
        )
    }

    val adminState = remember {
        AdminState(mapState, coroutineScope).also { admin ->
            layerRegistry.registerLayer(AdminDraftLayer({ admin.points.toList() }, { admin.isDrawingPolygon }))
        }
    }

    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFE2E8F0))
        ) {
            // Base Layer: Map Canvas & Gesture Engine
            MapCanvas(
                mapState = mapState,
                onHover = { hoveredOffset = it },
                modifier = Modifier.fillMaxSize()
            )

            // Top Area: Search Bar and Quick Navigation Presets
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Search Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.Top
                ) {
                    SearchBarOverlay(
                        mapState = mapState,
                        placesRepository = placesRepository
                    )
                }

                // Krakow Presets Bar (with Galeria Krakowska Indoor leading)
                PresetLocationsBar(
                    mapState = mapState
                )

                // Indoor Multi-floor Route Search Bar / Launcher Button
                if (mapState.focusedMall != null && (mapState.isMallOnScreen() || mapState.activeIndoorRoute != null)) {
                    if (mapState.isIndoorNavigationOpen || mapState.activeIndoorRoute != null) {
                        IndoorRouteSearchBar(
                            mapState = mapState,
                            modifier = Modifier
                                .widthIn(max = 480.dp)
                                .padding(top = 4.dp)
                        )
                    } else {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFF2563EB),
                            shadowElevation = 4.dp,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .clickable {
                                    mapState.isIndoorNavigationOpen = true
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("🧭", fontSize = 13.sp)
                                Text(
                                    "Indoor Directions & Route",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Floor Choosing Side Control: Appears on the side when building is on screen
            mapState.focusedMall?.let { mall ->
                FloorSelectorControl(
                    mall = mall,
                    currentFloorNumber = mapState.currentFloorNumber,
                    onSelectFloor = { floorNumber ->
                        mapState.selectFloor(floorNumber)
                    },
                    routeFloors = mapState.activeIndoorRoute?.levels?.map { it.floorNumber }?.toSet() ?: emptySet(),
                    visible = mapState.isMallOnScreen(),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 16.dp)
                )
            }

            // Bottom-Right: Navigation Controls
            MapControls(
                mapState = mapState,
                modifier = Modifier.align(Alignment.BottomEnd)
            )

            // Bottom-Left: Real-time Coordinate & Tile HUD
            CoordinateHUD(
                mapState = mapState,
                hoveredScreenOffset = hoveredOffset,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            )

            // Overlays: Selected Place Detail Card
            mapState.selectedPlace?.let { place ->
                PlaceDetailSheet(
                    place = place,
                    onClose = { mapState.selectedPlace = null },
                    onCenter = { mapState.flyTo(place.coordinate, 16.5) },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, bottom = 64.dp)
                )
            }

            // Overlays: Selected Indoor Store Detail Sheet
            mapState.selectedStore?.let { store ->
                val mall = mapState.focusedMall
                val floor = mall?.getFloor(mapState.currentFloorNumber)
                if (mall != null && floor != null) {
                    StoreDetailSheet(
                        store = store,
                        floor = floor,
                        mall = mall,
                        onClose = { mapState.selectedStore = null },
                        onNavigateToStore = {
                            mapState.startRouteToStore(store)
                        },
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 16.dp, bottom = 64.dp)
                    )
                }
            }

            // Overlays: Layer Manager Drawer / Card
            if (mapState.isLayerManagerOpen) {
                LayerManagerSheet(
                    mapState = mapState,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 70.dp, end = 16.dp)
                )
            }

            // Overlays: Tile Source Selector
            if (mapState.isTileSelectorOpen) {
                TileSourceSelector(
                    mapState = mapState,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 70.dp, end = 16.dp)
                )
            }

            // Overlays: Admin panel
            AdminToggleButton(
                admin = adminState,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 16.dp)
            )
            if (adminState.isOpen) {
                AdminPanel(
                    admin = adminState,
                    mapState = mapState,
                    modifier = Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = 70.dp)
                )
            }

            // Overlays: Engine Stats
            if (mapState.isDebugStatsOpen) {
                RenderDebugOverlay(
                    mapState = mapState,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 70.dp, end = 70.dp)
                )
            }
        }
    }
}

