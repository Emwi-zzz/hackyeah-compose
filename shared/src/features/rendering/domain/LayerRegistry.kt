package features.rendering.domain

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList

class LayerRegistry(
    initialLayers: List<MapLayer> = emptyList()
) {
    val layers: SnapshotStateList<MapLayer> = mutableStateListOf(*initialLayers.toTypedArray())

    // Layer properties (isVisible, opacity) are plain vars, so replacing a layer with itself in the
    // list is not a state change. UI that displays them reads this counter to get recomposed.
    var revision by mutableIntStateOf(0)
        private set

    fun registerLayer(layer: MapLayer) {
        val existingIndex = layers.indexOfFirst { it.id == layer.id }
        if (existingIndex >= 0) {
            layers[existingIndex] = layer
        } else {
            layers.add(layer)
        }
    }

    fun unregisterLayer(layerId: String) {
        layers.removeAll { it.id == layerId }
    }

    fun getLayer(layerId: String): MapLayer? {
        return layers.find { it.id == layerId }
    }

    fun toggleLayer(layerId: String) {
        val layer = getLayer(layerId) ?: return
        layer.isVisible = !layer.isVisible
        revision++
    }

    fun setLayerOpacity(layerId: String, opacity: Float) {
        val layer = getLayer(layerId) ?: return
        layer.opacity = opacity.coerceIn(0f, 1f)
        revision++
    }

    fun clear() {
        layers.clear()
    }
}
