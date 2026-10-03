package features.rendering.layers

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import core.geometry.GeoPoint
import features.rendering.domain.MapLayer
import features.rendering.domain.RenderContext

sealed interface VectorFeature {
    data class Polyline(
        val id: String,
        val name: String,
        val points: List<GeoPoint>,
        val color: Color,
        val strokeWidthDp: Float = 3f,
        val isDashed: Boolean = false
    ) : VectorFeature

    data class Polygon(
        val id: String,
        val name: String,
        val points: List<GeoPoint>,
        val fillColor: Color,
        val strokeColor: Color = Color.Transparent,
        val strokeWidthDp: Float = 2f
    ) : VectorFeature
}

class VectorLayer(
    override val id: String = "vector_layer",
    override val name: String = "Vector Features",
    override val description: String = "Polygons and paths (Wisła River, Planty, Krakow Boundary)",
    override var isVisible: Boolean = true,
    override var opacity: Float = 0.85f,
    override val zIndex: Int = 10,
    val features: MutableList<VectorFeature> = mutableListOf()
) : MapLayer {

    override fun render(context: RenderContext) {
        val strokeMultiplier = (context.viewport.zoom / 14.0).toFloat().coerceIn(0.6f, 3.0f)

        for (feature in features) {
            when (feature) {
                is VectorFeature.Polyline -> renderPolyline(context, feature, strokeMultiplier)
                is VectorFeature.Polygon -> renderPolygon(context, feature, strokeMultiplier)
            }
        }
    }

    private fun renderPolyline(context: RenderContext, polyline: VectorFeature.Polyline, multiplier: Float) {
        if (polyline.points.size < 2) return

        val path = Path()
        var hasStarted = false

        for (geo in polyline.points) {
            val screenPos = context.geoToScreen(geo)
            if (!hasStarted) {
                path.moveTo(screenPos.x, screenPos.y)
                hasStarted = true
            } else {
                path.lineTo(screenPos.x, screenPos.y)
            }
        }

        val style = Stroke(
            width = polyline.strokeWidthDp * multiplier,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
            pathEffect = if (polyline.isDashed) PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f) else null
        )

        context.drawScope.drawPath(
            path = path,
            color = polyline.color.copy(alpha = polyline.color.alpha * opacity),
            style = style
        )
    }

    private fun renderPolygon(context: RenderContext, polygon: VectorFeature.Polygon, multiplier: Float) {
        if (polygon.points.size < 3) return

        val path = Path()
        var hasStarted = false

        for (geo in polygon.points) {
            val screenPos = context.geoToScreen(geo)
            if (!hasStarted) {
                path.moveTo(screenPos.x, screenPos.y)
                hasStarted = true
            } else {
                path.lineTo(screenPos.x, screenPos.y)
            }
        }
        path.close()

        if (polygon.fillColor != Color.Transparent) {
            context.drawScope.drawPath(
                path = path,
                color = polygon.fillColor.copy(alpha = polygon.fillColor.alpha * opacity),
                style = Fill
            )
        }

        if (polygon.strokeColor != Color.Transparent && polygon.strokeWidthDp > 0f) {
            context.drawScope.drawPath(
                path = path,
                color = polygon.strokeColor.copy(alpha = polygon.strokeColor.alpha * opacity),
                style = Stroke(
                    width = polygon.strokeWidthDp * multiplier,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}
