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
import features.rendering.domain.RenderContext
import features.rendering.layers.PoiMarkerLayer
import features.tools.layers.InteractiveToolLayer

@Composable
fun MapCanvas(
    mapState: MapState,
    modifier: Modifier = Modifier,
    onHover: (Offset) -> Unit = {}
) {
    val textMeasurer = rememberTextMeasurer()

    // Smooth continuous animation frame clock for dynamic render layers (beacons, pulse, etc.)
    val frameTimeNanos by produceState(0L) {
        while (true) {
            withFrameNanos { value = it }
        }
    }

    // Keep text measurer and selection state synchronized with layers
    val poiLayer = mapState.layerRegistry.getLayer("poi_marker_layer") as? PoiMarkerLayer
    poiLayer?.textMeasurer = textMeasurer
    poiLayer?.selectedPlaceId = mapState.selectedPlace?.id

    val toolLayer = mapState.layerRegistry.getLayer("interactive_tool_layer") as? InteractiveToolLayer
    toolLayer?.textMeasurer = textMeasurer

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
