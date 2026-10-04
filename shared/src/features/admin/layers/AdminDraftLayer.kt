package features.admin.layers

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import features.admin.domain.AdminShapeFactory
import core.geometry.GeoPoint
import features.rendering.domain.MapLayer
import features.rendering.domain.RenderContext
import sklepsearch.Point

/** Draws the polygon the admin is currently tracing on the map. */
class AdminDraftLayer(
    private val pointsProvider: () -> List<GeoPoint>,
    private val isPolygon: () -> Boolean,
    private val isBezier: () -> Boolean,
    private val curvedEdgesProvider: () -> List<Boolean>,
    private val previewPointProvider: () -> GeoPoint?,
    private val vertexHandlesProvider: () -> List<GeoPoint>,
    private val curveControlHandlesProvider: () -> List<GeoPoint>,
    override val id: String = ID,
    override val name: String = "Admin drawing",
    override val description: String = "Shape being drawn in the admin panel",
    override var isVisible: Boolean = true,
    override var opacity: Float = 1.0f,
    override val zIndex: Int = 100
) : MapLayer {

    override fun render(context: RenderContext) {
        val pts = pointsProvider()
        val vertexHandles = vertexHandlesProvider()
        val curveControlHandles = curveControlHandlesProvider()
        if (pts.isEmpty() && vertexHandles.isEmpty() && curveControlHandles.isEmpty()) return
        val screen = pts.map { context.geoToScreen(it) }
        val color = Color(0xFFF59E0B)
        val scope = context.drawScope
        val previewPoint = previewPointProvider()?.let(context::geoToScreen)

        val polygonPreview = isPolygon() && screen.size >= 3
        val pathPoints = if (polygonPreview) {
            AdminShapeFactory.polygon(
                points = screen.map { Point(it.x.toDouble(), it.y.toDouble()) },
                curvedEdges = curvedEdgesProvider().take(screen.size - 1),
                closingBezier = isBezier()
            ).getVertices(stepsPerCurve = 16).map { Offset(it.x.toFloat(), it.y.toFloat()) }
        } else {
            screen
        }
        for (i in 0 until pathPoints.size - 1) {
            scope.drawLine(color, pathPoints[i], pathPoints[i + 1], strokeWidth = 3f)
        }
        if (previewPoint != null && screen.isNotEmpty()) {
            val previewEdge = AdminShapeFactory.previewEdge(
                previous = screen.getOrNull(screen.lastIndex - 1)?.let { Point(it.x.toDouble(), it.y.toDouble()) },
                start = Point(screen.last().x.toDouble(), screen.last().y.toDouble()),
                end = Point(previewPoint.x.toDouble(), previewPoint.y.toDouble()),
                curved = isBezier()
            ).map { Offset(it.x.toFloat(), it.y.toFloat()) }
            for (i in 0 until previewEdge.lastIndex) {
                scope.drawLine(color.copy(alpha = 0.8f), previewEdge[i], previewEdge[i + 1], strokeWidth = 2.5f)
            }
            scope.drawCircle(Color.White, radius = 5f, center = previewPoint)
            scope.drawCircle(color, radius = 5f, center = previewPoint, style = Stroke(width = 2f))
        }
        if (polygonPreview && !isBezier()) {
            scope.drawLine(color.copy(alpha = 0.6f), pathPoints.last(), pathPoints.first(), strokeWidth = 2f)
        }
        screen.forEachIndexed { i, p: Offset ->
            scope.drawCircle(Color.White, radius = if (i == 0) 8f else 6f, center = p)
            scope.drawCircle(color, radius = if (i == 0) 8f else 6f, center = p, style = Stroke(width = 3f))
        }
        vertexHandles.forEach { point ->
            val handle = context.geoToScreen(point)
            scope.drawCircle(Color.White, radius = 8f, center = handle)
            scope.drawCircle(Color(0xFF2563EB), radius = 8f, center = handle, style = Stroke(width = 2.5f))
            scope.drawCircle(Color(0xFF2563EB), radius = 2.5f, center = handle)
        }
        curveControlHandles.forEach { point ->
            val handle = context.geoToScreen(point)
            scope.drawCircle(Color.White, radius = 7f, center = handle)
            scope.drawCircle(Color(0xFF9333EA), radius = 7f, center = handle, style = Stroke(width = 2.5f))
            scope.drawCircle(Color(0xFF9333EA), radius = 2f, center = handle)
        }
    }

    companion object {
        const val ID = "admin_draft_layer"
    }
}
