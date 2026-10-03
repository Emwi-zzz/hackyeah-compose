package features.admin.layers

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import core.geometry.GeoPoint
import features.rendering.domain.MapLayer
import features.rendering.domain.RenderContext

/** Draws the polygon the admin is currently tracing on the map. */
class AdminDraftLayer(
    private val pointsProvider: () -> List<GeoPoint>,
    private val isPolygon: () -> Boolean,
    override val id: String = ID,
    override val name: String = "Admin drawing",
    override val description: String = "Shape being drawn in the admin panel",
    override var isVisible: Boolean = true,
    override var opacity: Float = 1.0f,
    override val zIndex: Int = 100
) : MapLayer {

    override fun render(context: RenderContext) {
        val pts = pointsProvider()
        if (pts.isEmpty()) return
        val screen = pts.map { context.geoToScreen(it) }
        val color = Color(0xFFF59E0B)
        val scope = context.drawScope

        for (i in 0 until screen.size - 1) {
            scope.drawLine(color, screen[i], screen[i + 1], strokeWidth = 3f)
        }
        if (isPolygon() && screen.size >= 3) {
            // Dashed closing edge
            scope.drawLine(color.copy(alpha = 0.6f), screen.last(), screen.first(), strokeWidth = 2f)
        }
        screen.forEachIndexed { i, p: Offset ->
            scope.drawCircle(Color.White, radius = if (i == 0) 8f else 6f, center = p)
            scope.drawCircle(color, radius = if (i == 0) 8f else 6f, center = p, style = Stroke(width = 3f))
        }
    }

    companion object {
        const val ID = "admin_draft_layer"
    }
}
