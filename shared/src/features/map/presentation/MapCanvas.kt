package features.map.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.rememberTextMeasurer
import features.indoor.presentation.IndoorBuildingLayer
import features.rendering.domain.RenderContext

@Composable
fun MapCanvas(
    mapState: MapState,
    modifier: Modifier = Modifier,
    onHover: (Offset) -> Unit = {}
) {
    val textMeasurer = rememberTextMeasurer()

    val frameTimeNanos by produceState(0L) {
        while (true) {
            withFrameNanos { value = it }
        }
    }

    // Synchronize text measurer, malls, focused mall, active floor, selected store, and active route with indoor building layer
    val indoorLayer = mapState.layerRegistry.getLayer("indoor_building_layer") as? IndoorBuildingLayer
    indoorLayer?.textMeasurer = textMeasurer
    indoorLayer?.malls = mapState.malls
    indoorLayer?.focusedMall = mapState.focusedMall
    indoorLayer?.selectedFloorNumber = mapState.currentFloorNumber
    indoorLayer?.selectedStore = mapState.selectedStore
    indoorLayer?.activeRoute = mapState.activeIndoorRoute

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                mapState.updateScreenSize(size.width.toFloat(), size.height.toFloat())
            }
            .mapInteractionGestures(mapState, onHover)
    ) {
        val renderContext = RenderContext(
            drawScope = this,
            viewport = mapState.viewport,
            tileRepository = mapState.tileRepository,
            activeTileSource = mapState.activeTileSource,
            frameTimeNanos = frameTimeNanos
        )

        mapState.renderPipeline.render(renderContext)
    }
}
