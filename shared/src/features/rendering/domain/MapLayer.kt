package features.rendering.domain

interface MapLayer {
    val id: String
    val name: String
    val description: String
    var isVisible: Boolean
    var opacity: Float
    val zIndex: Int

    fun render(context: RenderContext)
}
