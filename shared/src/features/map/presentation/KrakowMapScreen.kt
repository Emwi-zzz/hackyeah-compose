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
import features.indoor.presentation.OutdoorRouteLayer
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
        ).also { state ->
            layerRegistry.registerLayer(
                OutdoorRouteLayer(
                    approach = { state.activeApproach },
                    walkedMeters = { state.outdoorProgress?.alongMeters ?: 0.0 },
                    userLocation = { state.userLocation },
                    accuracyMeters = { state.gpsAccuracyMeters }
                )
            )
        }
    }

    val adminState = remember {
        AdminState(mapState, coroutineScope).also { admin ->
            layerRegistry.registerLayer(
                AdminDraftLayer(
                    { admin.points.toList() },
                    { admin.isDrawingPolygon },
                    { admin.isBezierDrawing },
                    { admin.curvedEdges.toList() },
                    { admin.previewPoint },
                    { admin.editableVertexHandles },
                    { admin.editableControlHandles },
                    { admin.pendingEscalatorMarker }
                )
            )
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
                onModifiersChanged = { offset, shift, ctrl ->
                    adminState.updatePointer(mapState.geoAtScreen(offset), shift, ctrl)
                },
                onDragStart = adminState::beginVertexDrag,
                onDrag = adminState::dragVertex,
                onDragEnd = adminState::endVertexDrag,
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
                val hasRoute = mapState.activeIndoorRoute != null || mapState.activeApproach != null
                if (mapState.focusedMall != null && (mapState.isMallOnScreen() || hasRoute)) {
                    if (mapState.isIndoorNavigationOpen || hasRoute) {
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

            // Left, below the presets bar: step-free routing toggle and the user's street position
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 160.dp, start = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val accessible = mapState.isAccessibleRouting
                MapPill(
                    text = if (accessible) "♿ Accessible: ON" else "♿ Accessible: OFF",
                    active = accessible,
                    onClick = { mapState.toggleAccessibleRouting() }
                )
                MapPill(
                    text = when {
                        !mapState.isGpsOn -> "🛰 Use my GPS"
                        mapState.gpsAccuracyMeters == null -> "🛰 Locating…"
                        else -> "🛰 GPS ±${mapState.gpsAccuracyMeters!!.toInt()} m"
                    },
                    active = mapState.isGpsOn,
                    onClick = { mapState.toggleGps() }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MapPill(
                        text = when {
                            mapState.isPickingUserLocation -> "📍 Click on the map…"
                            mapState.userLocation != null -> "📍 Move my location"
                            else -> "📍 Set my location"
                        },
                        active = mapState.isPickingUserLocation,
                        onClick = {
                            if (mapState.isGpsOn) mapState.toggleGps() // a placed position would be overwritten by the next fix
                            mapState.isPickingUserLocation = !mapState.isPickingUserLocation
                        }
                    )
                    if (mapState.userLocation != null) {
                        MapPill(text = "✕", active = false, onClick = { mapState.clearUserLocation() })
                    }
                }
                AdminToggleButton(admin = adminState)
                mapState.locationError?.let { error ->
                    Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFFEF2F2), shadowElevation = 2.dp) {
                        Text(
                            error,
                            color = Color(0xFFDC2626),
                            fontSize = 11.sp,
                            modifier = Modifier.widthIn(max = 260.dp).padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Overlays: Admin panel (its toggle sits in the left column above)
            if (adminState.isOpen) {
                AdminPanel(
                    admin = adminState,
                    mapState = mapState,
                    modifier = Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = 365.dp)
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

@Composable
private fun MapPill(text: String, active: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (active) Color(0xFF2563EB) else Color.White,
        shadowElevation = 4.dp,
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = text,
            color = if (active) Color.White else Color(0xFF0F172A),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}
