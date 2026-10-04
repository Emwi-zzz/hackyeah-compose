package features.admin.layers

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import core.ui.AppColors
import features.admin.domain.AdminShapeFactory
import core.geometry.GeoPoint
import features.rendering.domain.MapLayer
import features.rendering.domain.RenderContext
import sklepsearch.Point

private val CurveControlColor = Color(0xFFC084FC)

/** Draws the polygon the admin is currently tracing on the map. */
class AdminDraftLayer(
    private val pointsProvider: () -> List<GeoPoint>,
    private val isPolygon: () -> Boolean,
    private val isBezier: () -> Boolean,
    private val curvedEdgesProvider: () -> List<Boolean>,
    private val previewPointProvider: () -> GeoPoint?,
    private val vertexHandlesProvider: () -> List<GeoPoint>,
    private val curveControlHandlesProvider: () -> List<GeoPoint>,
    private val pendingEscalatorProvider: () -> Pair<GeoPoint, Boolean>? = { null },
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
        val pendingEscalator = pendingEscalatorProvider()
        if (pts.isEmpty() && vertexHandles.isEmpty() && curveControlHandles.isEmpty() && pendingEscalator == null) return
        val screen = pts.map { context.geoToScreen(it) }
        val color = AppColors.Accent
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
            scope.drawCircle(AppColors.AccentStrong, radius = 8f, center = handle, style = Stroke(width = 2.5f))
            scope.drawCircle(AppColors.AccentStrong, radius = 2.5f, center = handle)
        }
        curveControlHandles.forEach { point ->
            val handle = context.geoToScreen(point)
            scope.drawCircle(Color.White, radius = 7f, center = handle)
            scope.drawCircle(CurveControlColor, radius = 7f, center = handle, style = Stroke(width = 2.5f))
            scope.drawCircle(CurveControlColor, radius = 2f, center = handle)
        }
        pendingEscalator?.let { (geo, isUp) ->
            val center = context.geoToScreen(geo)
            val bg = if (isUp) AppColors.Success else AppColors.Warning
            val topLeft = Offset(center.x - 14f, center.y - 9f)
            val size = androidx.compose.ui.geometry.Size(28f, 18f)
            val corner = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
            scope.drawRoundRect(bg, topLeft, size, corner)
            scope.drawRoundRect(Color.White, topLeft, size, corner, style = Stroke(width = 2f))
            val arrow = if (isUp) -1f else 1f
            val tip = Offset(center.x, center.y + 4f * arrow)
            scope.drawLine(AppColors.Background, tip, Offset(center.x - 4f, center.y - 2f * arrow), strokeWidth = 2f)
            scope.drawLine(AppColors.Background, tip, Offset(center.x + 4f, center.y - 2f * arrow), strokeWidth = 2f)
        }
    }

    companion object {
        const val ID = "admin_draft_layer"
    }
}
