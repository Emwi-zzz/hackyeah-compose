package features.map.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import core.network.BackendConfig
import core.ui.AppBorder
import core.ui.AppColors
import core.ui.AppShapes
import core.ui.AppTheme
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
import features.map.presentation.components.MapAttribution
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
    var showBackendConfigDialog by remember { mutableStateOf(false) }
    var backendUrlInput by remember { mutableStateOf(BackendConfig.baseUrl) }

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

    AppTheme {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.Background)
        ) {
            val isCompact = maxWidth < 600.dp

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

            // Top Area: Search Bar and Quick Navigation Presets (with safe status bar padding)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 8.dp),
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

                // Krakow Presets Bar (with Galeria Krakowska Indoor leading & Step-free chip)
                PresetLocationsBar(
                    mapState = mapState
                )

                // Indoor Multi-floor Route Search Bar / Launcher Button / Outdoor Approach Route
                val hasRoute = mapState.activeIndoorRoute != null || mapState.activeApproach != null
                if (mapState.activeApproach != null) {
                    IndoorRouteSearchBar(
                        mapState = mapState,
                        modifier = Modifier
                            .widthIn(max = 480.dp)
                            .padding(top = 4.dp)
                    )
                } else if (mapState.focusedMall != null && (mapState.isMallOnScreen() || hasRoute)) {
                    if (mapState.isIndoorNavigationOpen || hasRoute) {
                        IndoorRouteSearchBar(
                            mapState = mapState,
                            modifier = Modifier
                                .widthIn(max = 480.dp)
                                .padding(top = 4.dp)
                        )
                    } else {
                        Surface(
                            shape = AppShapes.Pill,
                            color = AppColors.Accent,
                            shadowElevation = 6.dp,
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
                                    color = AppColors.OnAccent,
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

            // Bottom-Right: Navigation Controls (respects system navigation bars)
            MapControls(
                mapState = mapState,
                showAdmin = isCompact,
                isAdminOpen = adminState.isOpen,
                onToggleAdmin = { adminState.isOpen = !adminState.isOpen },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
            )

            // Bottom Attribution Credit (unobtrusive, never wraps vertically)
            MapAttribution(
                mapState = mapState,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(start = 12.dp, bottom = 6.dp)
            )

            // Coordinate & Tile HUD (shown on desktop or when debug toggle is active)
            if (!isCompact || mapState.isDebugStatsOpen) {
                CoordinateHUD(
                    mapState = mapState,
                    hoveredScreenOffset = hoveredOffset,
                    compact = isCompact,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .navigationBarsPadding()
                        .padding(start = 12.dp, bottom = 32.dp)
                )
            }

            // Location picking banner (replaces the clunky permanent button when active)
            if (mapState.isPickingUserLocation) {
                Surface(
                    shape = AppShapes.Pill,
                    color = AppColors.Accent,
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 116.dp)
                        .clickable { mapState.isPickingUserLocation = false }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("📍", fontSize = 13.sp)
                        Text(
                            "Tap map to set position • Cancel ✕",
                            color = AppColors.OnAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Desktop Left Column: for wide screens (>= 600dp)
            if (!isCompact) {
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
                                if (mapState.isGpsOn) mapState.toggleGps()
                                mapState.isPickingUserLocation = !mapState.isPickingUserLocation
                            }
                        )
                        if (mapState.userLocation != null) {
                            MapPill(text = "✕", active = false, onClick = { mapState.clearUserLocation() })
                        }
                    }
                    AdminToggleButton(admin = adminState)
                    mapState.locationError?.let { error ->
                        Surface(shape = AppShapes.Control, color = AppColors.Surface, border = AppBorder) {
                            Text(
                                error,
                                color = AppColors.Danger,
                                fontSize = 11.sp,
                                modifier = Modifier.widthIn(max = 260.dp).padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            // Warning banner if backend is unreachable or returned error
            mapState.indoorDataError?.let { error ->
                Surface(
                    shape = AppShapes.Pill,
                    color = Color(0xFF8B2500),
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 20.dp)
                        .clickable { mapState.loadIndoorData() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("⚠️", fontSize = 13.sp)
                        Text(
                            "Indoor backend offline ($error) • Retry",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Surface(
                            shape = AppShapes.Pill,
                            color = Color(0x33FFFFFF),
                            modifier = Modifier.clickable {
                                backendUrlInput = BackendConfig.baseUrl
                                showBackendConfigDialog = true
                            }
                        ) {
                            Text(
                                "⚙️ Host",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Overlays: Selected Place Detail Card
            mapState.selectedPlace?.let { place ->
                PlaceDetailSheet(
                    place = place,
                    onClose = { mapState.selectedPlace = null },
                    onCenter = { mapState.flyTo(place.coordinate, 16.5) },
                    modifier = Modifier
                        .align(if (isCompact) Alignment.BottomCenter else Alignment.BottomStart)
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 16.dp)
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
                            .align(if (isCompact) Alignment.BottomCenter else Alignment.BottomStart)
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 16.dp)
                    )
                }
            }

            // Overlays: Layer Manager Drawer / Card
            if (mapState.isLayerManagerOpen) {
                LayerManagerSheet(
                    mapState = mapState,
                    modifier = Modifier
                        .align(if (isCompact) Alignment.BottomCenter else Alignment.TopEnd)
                        .navigationBarsPadding()
                        .padding(
                            if (isCompact) PaddingValues(start = 16.dp, end = 16.dp, bottom = 48.dp)
                            else PaddingValues(top = 70.dp, end = 16.dp)
                        )
                )
            }

            // Overlays: Tile Source Selector
            if (mapState.isTileSelectorOpen) {
                TileSourceSelector(
                    mapState = mapState,
                    modifier = Modifier
                        .align(if (isCompact) Alignment.BottomCenter else Alignment.TopEnd)
                        .navigationBarsPadding()
                        .padding(
                            if (isCompact) PaddingValues(start = 16.dp, end = 16.dp, bottom = 48.dp)
                            else PaddingValues(top = 70.dp, end = 16.dp)
                        )
                )
            }

            // Overlays: Admin panel
            if (adminState.isOpen) {
                AdminPanel(
                    admin = adminState,
                    mapState = mapState,
                    modifier = Modifier
                        .align(if (isCompact) Alignment.Center else Alignment.TopStart)
                        .padding(
                            if (isCompact) PaddingValues(16.dp)
                            else PaddingValues(start = 16.dp, top = 365.dp)
                        )
                )
            }

            // Overlays: Engine Stats
            if (mapState.isDebugStatsOpen) {
                RenderDebugOverlay(
                    mapState = mapState,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(bottom = 70.dp, end = 70.dp)
                )
            }

            // Dialog: Backend Server URL Configuration
            if (showBackendConfigDialog) {
                AlertDialog(
                    onDismissRequest = { showBackendConfigDialog = false },
                    title = {
                        Text(
                            "Backend API URL",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.TextPrimary
                        )
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                "Enter the backend server address (e.g. your computer's local IP or emulator bridge):",
                                fontSize = 12.sp,
                                color = AppColors.TextSecondary
                            )
                            TextField(
                                value = backendUrlInput,
                                onValueChange = { backendUrlInput = it },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = AppColors.SurfaceRaised,
                                    unfocusedContainerColor = AppColors.SurfaceRaised,
                                    focusedTextColor = AppColors.TextPrimary,
                                    unfocusedTextColor = AppColors.TextPrimary
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = AppShapes.Pill,
                                    color = AppColors.SurfaceRaised,
                                    border = AppBorder,
                                    modifier = Modifier.clickable { backendUrlInput = "http://10.0.2.2:8080" }
                                ) {
                                    Text(
                                        "Emulator 10.0.2.2",
                                        fontSize = 11.sp,
                                        color = AppColors.Accent,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                Surface(
                                    shape = AppShapes.Pill,
                                    color = AppColors.SurfaceRaised,
                                    border = AppBorder,
                                    modifier = Modifier.clickable { backendUrlInput = "http://localhost:8080" }
                                ) {
                                    Text(
                                        "localhost:8080",
                                        fontSize = 11.sp,
                                        color = AppColors.Accent,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                BackendConfig.baseUrl = backendUrlInput.trim().trimEnd('/')
                                showBackendConfigDialog = false
                                mapState.loadIndoorData()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent)
                        ) {
                            Text("Save & Connect", color = AppColors.OnAccent, fontWeight = FontWeight.SemiBold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showBackendConfigDialog = false }) {
                            Text("Cancel", color = AppColors.TextSecondary)
                        }
                    },
                    containerColor = AppColors.Surface,
                    shape = AppShapes.Card
                )
            }
        }
    }
}

@Composable
private fun MapPill(text: String, active: Boolean, onClick: () -> Unit) {
    Surface(
        shape = AppShapes.Pill,
        color = if (active) AppColors.Accent else AppColors.Surface,
        border = if (active) null else AppBorder,
        shadowElevation = 4.dp,
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = text,
            color = if (active) AppColors.OnAccent else AppColors.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}
