package features.rendering.domain

class RenderPipeline(
    private val layersSupplier: () -> List<MapLayer>
) {
    fun render(context: RenderContext) {
        val sortedLayers = layersSupplier()
            .filter { it.isVisible && it.opacity > 0.001f }
            .sortedBy { it.zIndex }

        for (layer in sortedLayers) {
            runCatching {
                layer.render(context)
            }
        }
    }
}
