package features.rendering.layers

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import core.ui.AppColors
import features.places.domain.Place
import features.rendering.domain.MapLayer
import features.rendering.domain.RenderContext

class PoiMarkerLayer(
    override val id: String = "poi_marker_layer",
    override val name: String = "Krakow Landmarks",
    override val description: String = "Interactive landmark markers and POIs",
    override var isVisible: Boolean = true,
    override var opacity: Float = 1.0f,
    override val zIndex: Int = 30,
    var places: List<Place> = emptyList(),
    var selectedPlaceId: String? = null,
    var textMeasurer: TextMeasurer? = null
) : MapLayer {

    override fun render(context: RenderContext) {
        val selectedId = selectedPlaceId
        val measurer = textMeasurer

        for (place in places) {
            val screenPos = context.geoToScreen(place.coordinate)

            // Skip off-screen markers (with 60px padding)
            if (screenPos.x < -60f || screenPos.x > context.viewport.screenWidth + 60f ||
                screenPos.y < -60f || screenPos.y > context.viewport.screenHeight + 60f
            ) {
                continue
            }

            val isSelected = place.id == selectedId
            val markerColor = place.categoryColor

            // Animated pulsing beacon if selected
            if (isSelected) {
                val pulseProgress = (context.frameTimeNanos / 1_000_000 % 1200) / 1200f
                val pulseRadius = 14f + pulseProgress * 24f
                val pulseAlpha = (1f - pulseProgress) * 0.6f * opacity

                context.drawScope.drawCircle(
                    color = markerColor.copy(alpha = pulseAlpha),
                    radius = pulseRadius,
                    center = screenPos
                )
            }

            // Pin drop shadow
            context.drawScope.drawOval(
                color = Color.Black.copy(alpha = 0.5f * opacity),
                topLeft = Offset(screenPos.x - 10f, screenPos.y - 2f),
                size = Size(20f, 8f)
            )

            // Pin Shape
            val pinHeight = if (isSelected) 36f else 28f
            val pinRadius = if (isSelected) 14f else 11f

            val pinPath = Path().apply {
                moveTo(screenPos.x, screenPos.y)
                lineTo(screenPos.x - pinRadius * 0.85f, screenPos.y - pinRadius)
                arcTo(
                    rect = androidx.compose.ui.geometry.Rect(
                        screenPos.x - pinRadius,
                        screenPos.y - pinHeight,
                        screenPos.x + pinRadius,
                        screenPos.y - pinHeight + (pinRadius * 2f)
                    ),
                    startAngleDegrees = 150f,
                    sweepAngleDegrees = 240f,
                    forceMoveTo = false
                )
                close()
            }

            // Draw pin body
            context.drawScope.drawPath(
                path = pinPath,
                color = markerColor.copy(alpha = opacity),
                style = Fill
            )
            // Pin border
            context.drawScope.drawPath(
                path = pinPath,
                color = Color.White.copy(alpha = opacity),
                style = Stroke(width = 2f)
            )

            // Pin inner white dot
            val dotCenterY = screenPos.y - pinHeight + pinRadius
            context.drawScope.drawCircle(
                color = Color.White.copy(alpha = opacity),
                radius = pinRadius * 0.45f,
                center = Offset(screenPos.x, dotCenterY)
            )

            // Render label if zoomed in sufficiently or if selected
            if (measurer != null && (isSelected || context.viewport.zoom >= 13.5)) {
                val labelText = place.name
                val fontSize = if (isSelected) 11.sp else 10.sp
                val textLayout = measurer.measure(
                    text = labelText,
                    style = TextStyle(
                        color = AppColors.TextPrimary,
                        fontSize = fontSize,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                )

                val pillWidth = textLayout.size.width + 16f
                val pillHeight = textLayout.size.height + 6f
                val pillTopLeft = Offset(
                    screenPos.x - (pillWidth / 2f),
                    screenPos.y - pinHeight - pillHeight - 4f
                )

                // Label pill background
                context.drawScope.drawRoundRect(
                    color = AppColors.Surface.copy(alpha = 0.95f * opacity),
                    topLeft = pillTopLeft,
                    size = Size(pillWidth, pillHeight),
                    cornerRadius = CornerRadius(6f, 6f)
                )
                context.drawScope.drawRoundRect(
                    color = if (isSelected) markerColor.copy(alpha = opacity) else AppColors.BorderStrong.copy(alpha = AppColors.BorderStrong.alpha * opacity),
                    topLeft = pillTopLeft,
                    size = Size(pillWidth, pillHeight),
                    cornerRadius = CornerRadius(6f, 6f),
                    style = Stroke(width = if (isSelected) 2f else 1f)
                )

                // Draw text inside pill
                context.drawScope.drawText(
                    textLayoutResult = textLayout,
                    topLeft = Offset(pillTopLeft.x + 8f, pillTopLeft.y + 3f)
                )
            }
        }
    }
}
