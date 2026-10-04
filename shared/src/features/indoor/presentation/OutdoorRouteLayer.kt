package features.indoor.presentation

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import core.geometry.GeoPoint
import core.geometry.PolylineMath
import features.indoor.domain.ApproachRoute
import features.rendering.domain.MapLayer
import features.rendering.domain.RenderContext

/** Street part of an approach route (walked part greyed out), the target entrance and the user's position. */
class OutdoorRouteLayer(
    private val approach: () -> ApproachRoute?,
    private val walkedMeters: () -> Double,
    private val userLocation: () -> GeoPoint?,
    private val accuracyMeters: () -> Double? = { null },
    override val id: String = ID,
    override val name: String = "Walking route",
    override val description: String = "Street route from your position to the mall entrance",
    override var isVisible: Boolean = true,
    override var opacity: Float = 1.0f,
    override val zIndex: Int = 20
) : MapLayer {

    override fun render(context: RenderContext) {
        val scope = context.drawScope
        approach()?.let { route ->
            val (walked, remaining) = PolylineMath.splitAt(route.outdoor.points, walkedMeters())
            fun toPath(points: List<GeoPoint>) = Path().apply {
                points.forEachIndexed { i, p ->
                    val s = context.geoToScreen(p)
                    if (i == 0) moveTo(s.x, s.y) else lineTo(s.x, s.y)
                }
            }
            val round = { width: Float -> Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round) }
            if (walked.size >= 2) {
                scope.drawPath(
                    toPath(walked), Color(0xFF94A3B8),
                    style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
                )
            }
            if (remaining.size >= 2) {
                val path = toPath(remaining)
                scope.drawPath(path, Color.White, style = round(11f))
                scope.drawPath(path, Color(0xFF16A34A), style = round(6f))
            }
            route.outdoor.points.lastOrNull()?.let { entrance ->
                val s = context.geoToScreen(entrance)
                scope.drawCircle(Color.White, radius = 10f, center = s)
                scope.drawCircle(Color(0xFF16A34A), radius = 7f, center = s)
            }
        }
        userLocation()?.let { geo ->
            val s = context.geoToScreen(geo)
            accuracyMeters()?.let { meters ->
                val radius = context.metersToPixels(meters, geo.latitude)
                scope.drawCircle(Color(0x1A2563EB), radius = radius, center = s)
                scope.drawCircle(Color(0x552563EB), radius = radius, center = s, style = Stroke(width = 1.5f))
            }
            scope.drawCircle(Color(0x332563EB), radius = 22f, center = s)
            scope.drawCircle(Color.White, radius = 10f, center = s)
            scope.drawCircle(Color(0xFF2563EB), radius = 7f, center = s)
        }
    }

    companion object {
        const val ID = "outdoor_route_layer"
    }
}
