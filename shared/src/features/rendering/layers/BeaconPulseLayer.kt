package features.rendering.layers

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import core.geometry.GeoPoint
import features.rendering.domain.MapLayer
import features.rendering.domain.RenderContext
import kotlin.math.cos
import kotlin.math.sin

data class PulseBeacon(
    val point: GeoPoint,
    val color: Color,
    val maxRadiusMeters: Double = 600.0,
    val label: String
)

class BeaconPulseLayer(
    override val id: String = "beacon_pulse_layer",
    override val name: String = "Animated Radar Beacons",
    override val description: String = "Demonstrates animated real-time custom graphics rendering",
    override var isVisible: Boolean = true,
    override var opacity: Float = 0.75f,
    override val zIndex: Int = 20,
    val beacons: List<PulseBeacon> = listOf(
        PulseBeacon(GeoPoint.WAWEL_CASTLE, Color(0xFF8B5CF6), 750.0, "Wawel Royal Hill"),
        PulseBeacon(GeoPoint.KRAKOW_RYNEK, Color(0xFFF59E0B), 500.0, "Rynek Główny"),
        PulseBeacon(GeoPoint.KAZIMIERZ_PLAC_NOWY, Color(0xFF06B6D4), 450.0, "Kazimierz Quarter")
    )
) : MapLayer {

    override fun render(context: RenderContext) {
        val timeMs = context.frameTimeNanos / 1_000_000
        val cycleMs = 2400L

        for (beacon in beacons) {
            val center = context.geoToScreen(beacon.point)

            // Render 3 staggered expanding wave rings
            for (ring in 0..2) {
                val offsetPhase = (timeMs + ring * (cycleMs / 3)) % cycleMs
                val progress = offsetPhase / cycleMs.toFloat()

                val radiusMeters = beacon.maxRadiusMeters * progress
                val pixelRadius = context.metersToPixels(radiusMeters, beacon.point.latitude)

                if (pixelRadius > 0f) {
                    val ringAlpha = (1f - progress) * opacity * 0.7f

                    context.drawScope.drawCircle(
                        color = beacon.color.copy(alpha = ringAlpha),
                        radius = pixelRadius,
                        center = center,
                        style = Stroke(width = 2.5f)
                    )
                }
            }

            // Central beacon core
            val corePulse = 0.8f + 0.2f * sin(timeMs / 200.0).toFloat()
            context.drawScope.drawCircle(
                color = beacon.color.copy(alpha = opacity * 0.9f),
                radius = 6f * corePulse,
                center = center
            )
        }
    }
}
