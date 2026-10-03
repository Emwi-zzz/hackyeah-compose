package features.rendering.layers

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import core.geometry.GeoPoint
import features.rendering.domain.MapLayer
import features.rendering.domain.RenderContext

data class HeatPoint(
    val point: GeoPoint,
    val intensity: Float, // 0.0 .. 1.0
    val radiusMeters: Double = 400.0
)

class HeatmapLayer(
    override val id: String = "heatmap_layer",
    override val name: String = "Activity Density Heatmap",
    override val description: String = "Smooth continuous field / density rendering",
    override var isVisible: Boolean = false, // Disabled by default, can be toggled on
    override var opacity: Float = 0.55f,
    override val zIndex: Int = 15,
    val points: List<HeatPoint> = listOf(
        HeatPoint(GeoPoint.KRAKOW_RYNEK, 1.0f, 600.0),
        HeatPoint(GeoPoint.SUKIENNICE, 0.9f, 450.0),
        HeatPoint(GeoPoint.KOSCIOL_MARIACKI, 0.95f, 500.0),
        HeatPoint(GeoPoint.WAWEL_CASTLE, 0.95f, 650.0),
        HeatPoint(GeoPoint.KAZIMIERZ_PLAC_NOWY, 0.85f, 500.0),
        HeatPoint(GeoPoint.KRAKOW_GLOWNY, 0.8f, 550.0),
        HeatPoint(GeoPoint.KOPIEC_KOSCIUSZKI, 0.6f, 400.0),
        HeatPoint(GeoPoint.BLONIA_KRAKOWSKIE, 0.7f, 700.0),
        HeatPoint(GeoPoint.NOWA_HUTA_PLAC_CENTRALNY, 0.65f, 600.0)
    )
) : MapLayer {

    override fun render(context: RenderContext) {
        for (hp in points) {
            val center = context.geoToScreen(hp.point)
            val radiusPx = context.metersToPixels(hp.radiusMeters, hp.point.latitude)

            if (radiusPx > 2f) {
                val brush = Brush.radialGradient(
                    0.0f to Color(0xFFFF2200).copy(alpha = 0.7f * hp.intensity * opacity),
                    0.3f to Color(0xFFFF8800).copy(alpha = 0.5f * hp.intensity * opacity),
                    0.6f to Color(0xFFFFEE00).copy(alpha = 0.3f * hp.intensity * opacity),
                    1.0f to Color.Transparent,
                    center = center,
                    radius = radiusPx
                )

                context.drawScope.drawCircle(
                    brush = brush,
                    radius = radiusPx,
                    center = center
                )
            }
        }
    }
}
