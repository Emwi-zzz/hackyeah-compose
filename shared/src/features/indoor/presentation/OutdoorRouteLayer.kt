package features.indoor.presentation

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import core.geometry.GeoPoint
import core.geometry.PolylineMath
import core.ui.AppColors
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
                    toPath(walked), AppColors.TextMuted,
                    style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
                )
            }
            if (remaining.size >= 2) {
                val path = toPath(remaining)
                scope.drawPath(path, AppColors.Background.copy(alpha = 0.85f), style = round(11f))
                scope.drawPath(path, AppColors.Success, style = round(6f))
            }
            route.outdoor.points.lastOrNull()?.let { entrance ->
                val s = context.geoToScreen(entrance)
                scope.drawCircle(AppColors.Background, radius = 10f, center = s)
                scope.drawCircle(AppColors.Success, radius = 7f, center = s)
            }
        }
        userLocation()?.let { geo ->
            val s = context.geoToScreen(geo)
            accuracyMeters()?.let { meters ->
                val radius = context.metersToPixels(meters, geo.latitude)
                scope.drawCircle(AppColors.Accent.copy(alpha = 0.12f), radius = radius, center = s)
                scope.drawCircle(AppColors.Accent.copy(alpha = 0.4f), radius = radius, center = s, style = Stroke(width = 1.5f))
            }
            scope.drawCircle(AppColors.Accent.copy(alpha = 0.22f), radius = 22f, center = s)
            scope.drawCircle(Color.White, radius = 10f, center = s)
            scope.drawCircle(AppColors.Accent, radius = 7f, center = s)
        }
    }

    companion object {
        const val ID = "outdoor_route_layer"
    }
}
