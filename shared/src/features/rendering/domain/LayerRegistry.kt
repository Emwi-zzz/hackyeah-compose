package features.rendering.domain

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList

class LayerRegistry(
    initialLayers: List<MapLayer> = emptyList()
) {
    val layers: SnapshotStateList<MapLayer> = mutableStateListOf(*initialLayers.toTypedArray())

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
        // Force mutation trigger in Compose state list
        val index = layers.indexOfFirst { it.id == layerId }
        if (index >= 0) {
            layers[index] = layer
        }
    }

    fun setLayerOpacity(layerId: String, opacity: Float) {
        val layer = getLayer(layerId) ?: return
        layer.opacity = opacity.coerceIn(0f, 1f)
        val index = layers.indexOfFirst { it.id == layerId }
        if (index >= 0) {
            layers[index] = layer
        }
    }

    fun clear() {
        layers.clear()
    }
}
