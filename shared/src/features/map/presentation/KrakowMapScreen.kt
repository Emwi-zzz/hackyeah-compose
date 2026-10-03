package features.map.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import features.map.data.TileRepositoryImpl
import features.map.presentation.components.CoordinateHUD
import features.map.presentation.components.MapControls
import features.map.presentation.components.PresetLocationsBar
import features.map.presentation.components.TileSourceSelector
import features.places.data.KrakowCuratedPlaces
import features.places.data.NominatimPlacesRepository
import features.places.presentation.PlaceDetailSheet
import features.places.presentation.SearchBarOverlay
import features.rendering.domain.LayerRegistry
import features.rendering.layers.BeaconPulseLayer
import features.rendering.layers.HeatmapLayer
import features.rendering.layers.KrakowVectorPresets
import features.rendering.layers.PoiMarkerLayer
import features.rendering.layers.TileLayer
import features.rendering.layers.VectorLayer
import features.rendering.presentation.LayerManagerSheet
import features.rendering.presentation.RenderDebugOverlay
import features.tools.domain.ToolController
import features.tools.layers.InteractiveToolLayer
import features.tools.presentation.ToolPaletteBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Composable
fun KrakowMapScreen() {
    val coroutineScope = rememberCoroutineScope()
    var hoveredOffset by remember { mutableStateOf<Offset?>(null) }

    // 1. Core Repositories & Controllers (Clean Architecture)
    val tileRepository = remember {
        TileRepositoryImpl()
    }
    val placesRepository = remember {
        NominatimPlacesRepository()
    }
    val toolController = remember {
        ToolController()
    }

    // 2. Default Map Layers
    val initialLayers = remember {
        listOf(
            TileLayer(),
            VectorLayer(features = KrakowVectorPresets.DEFAULT_FEATURES.toMutableList()),
            HeatmapLayer(),
            BeaconPulseLayer(),
            PoiMarkerLayer(places = KrakowCuratedPlaces.ALL),
            InteractiveToolLayer(toolController = toolController)
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
            toolController = toolController,
            scope = coroutineScope
        )
    }

    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFE2E8F0))
        ) {
            // Base Layer: Real-time 60fps Map Canvas & Gesture Engine
            MapCanvas(
                mapState = mapState,
                onHover = { hoveredOffset = it },
                modifier = Modifier.fillMaxSize()
            )

            // Top Area: Search Bar, Tools, and Landmark Presets
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Action Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    SearchBarOverlay(
                        mapState = mapState,
                        placesRepository = placesRepository
                    )

                    ToolPaletteBar(
                        toolController = toolController
                    )
                }

                // Krakow Landmark Presets Bar
                PresetLocationsBar(
                    mapState = mapState
                )
            }

            // Bottom-Right: Navigation & Engine Controls
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

            // Overlays: Engine Stats & Custom Layer Injector
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
