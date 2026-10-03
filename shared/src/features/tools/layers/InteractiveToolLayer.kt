package features.tools.layers

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import core.geometry.GeoMath
import core.geometry.GeoPoint
import features.rendering.domain.MapLayer
import features.rendering.domain.RenderContext
import features.tools.domain.ToolController
import features.tools.domain.ToolMode
import features.tools.domain.UserGeometry

class InteractiveToolLayer(
    override val id: String = "interactive_tool_layer",
    override val name: String = "User Drawings & Tools",
    override val description: String = "Renders custom user pins, polylines, polygons, and live drawing previews",
    override var isVisible: Boolean = true,
    override var opacity: Float = 1.0f,
    override val zIndex: Int = 50,
    val toolController: ToolController,
    var textMeasurer: TextMeasurer? = null
) : MapLayer {

    override fun render(context: RenderContext) {
        val measurer = textMeasurer

        // 1. Render finalized user geometries
        for (geom in toolController.geometries) {
            when (geom) {
                is UserGeometry.Pin -> renderPin(context, geom, measurer)
                is UserGeometry.Path -> renderPath(context, geom, measurer)
                is UserGeometry.Polygon -> renderPolygon(context, geom, measurer)
            }
        }

        // 2. Render live draft in-progress points
        val draft = toolController.draftPoints
        if (draft.isNotEmpty()) {
            renderDraft(context, draft, toolController.activeMode, measurer)
        }

        // 3. Render inspected crosshair
        val inspected = toolController.inspectedPoint
        if (inspected != null) {
            renderInspectedCrosshair(context, inspected)
        }
    }

    private fun renderPin(context: RenderContext, pin: UserGeometry.Pin, measurer: TextMeasurer?) {
        val screenPos = context.geoToScreen(pin.coordinate)

        // Drop shadow
        context.drawScope.drawCircle(
            color = Color.Black.copy(alpha = 0.3f * opacity),
            radius = 6f,
            center = Offset(screenPos.x, screenPos.y + 2f)
        )

        // Pin outer & inner circle
        context.drawScope.drawCircle(
            color = pin.color.copy(alpha = opacity),
            radius = 9f,
            center = screenPos
        )
        context.drawScope.drawCircle(
            color = Color.White.copy(alpha = opacity),
            radius = 4f,
            center = screenPos
        )
        context.drawScope.drawCircle(
            color = Color.White.copy(alpha = opacity),
            radius = 9f,
            center = screenPos,
            style = Stroke(width = 2f)
        )

        // Label
        if (measurer != null) {
            val textLayout = measurer.measure(
                text = pin.name,
                style = TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            )
            val pillW = textLayout.size.width + 12f
            val pillH = textLayout.size.height + 4f
            val topLeft = Offset(screenPos.x - pillW / 2f, screenPos.y - 20f - pillH)

            context.drawScope.drawRoundRect(
                color = Color(0xDD1E293B),
                topLeft = topLeft,
                size = Size(pillW, pillH),
                cornerRadius = CornerRadius(4f, 4f)
            )
            context.drawScope.drawText(
                textLayoutResult = textLayout,
                topLeft = Offset(topLeft.x + 6f, topLeft.y + 2f)
            )
        }
    }

    private fun renderPath(context: RenderContext, path: UserGeometry.Path, measurer: TextMeasurer?) {
        if (path.points.size < 2) return

        val p = Path()
        var started = false
        val screenPoints = mutableListOf<Offset>()

        for (pt in path.points) {
            val sp = context.geoToScreen(pt)
            screenPoints.add(sp)
            if (!started) {
                p.moveTo(sp.x, sp.y)
                started = true
            } else {
                p.lineTo(sp.x, sp.y)
            }
        }

        context.drawScope.drawPath(
            path = p,
            color = path.color.copy(alpha = opacity),
            style = Stroke(width = path.strokeWidthDp, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Draw vertex handles
        for (sp in screenPoints) {
            context.drawScope.drawCircle(
                color = path.color.copy(alpha = opacity),
                radius = 5f,
                center = sp
            )
            context.drawScope.drawCircle(
                color = Color.White.copy(alpha = opacity),
                radius = 2.5f,
                center = sp
            )
        }

        // Draw distance badge on middle segment
        if (measurer != null && screenPoints.size >= 2) {
            val midIdx = (screenPoints.size - 1) / 2
            val mid = Offset(
                (screenPoints[midIdx].x + screenPoints[midIdx + 1].x) / 2f,
                (screenPoints[midIdx].y + screenPoints[midIdx + 1].y) / 2f
            )

            val badgeText = "${path.name}: ${path.formattedDistance}"
            val layout = measurer.measure(
                text = badgeText,
                style = TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            )
            val w = layout.size.width + 12f
            val h = layout.size.height + 4f
            val tl = Offset(mid.x - w / 2f, mid.y - h / 2f)

            context.drawScope.drawRoundRect(
                color = Color(0xEE2563EB),
                topLeft = tl,
                size = Size(w, h),
                cornerRadius = CornerRadius(4f, 4f)
            )
            context.drawScope.drawText(textLayoutResult = layout, topLeft = Offset(tl.x + 6f, tl.y + 2f))
        }
    }

    private fun renderPolygon(context: RenderContext, poly: UserGeometry.Polygon, measurer: TextMeasurer?) {
        if (poly.points.size < 3) return

        val p = Path()
        var started = false
        var sumX = 0f
        var sumY = 0f

        for (pt in poly.points) {
            val sp = context.geoToScreen(pt)
            sumX += sp.x
            sumY += sp.y
            if (!started) {
                p.moveTo(sp.x, sp.y)
                started = true
            } else {
                p.lineTo(sp.x, sp.y)
            }
        }
        p.close()

        context.drawScope.drawPath(
            path = p,
            color = poly.fillColor.copy(alpha = poly.fillColor.alpha * opacity),
            style = Fill
        )
        context.drawScope.drawPath(
            path = p,
            color = poly.strokeColor.copy(alpha = opacity),
            style = Stroke(width = poly.strokeWidthDp, join = StrokeJoin.Round)
        )

        // Center badge with area
        if (measurer != null) {
            val centroid = Offset(sumX / poly.points.size, sumY / poly.points.size)
            val text = "${poly.name}: ${poly.formattedArea}"
            val layout = measurer.measure(
                text = text,
                style = TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            )
            val w = layout.size.width + 12f
            val h = layout.size.height + 4f
            val tl = Offset(centroid.x - w / 2f, centroid.y - h / 2f)

            context.drawScope.drawRoundRect(
                color = Color(0xEE059669),
                topLeft = tl,
                size = Size(w, h),
                cornerRadius = CornerRadius(4f, 4f)
            )
            context.drawScope.drawText(textLayoutResult = layout, topLeft = Offset(tl.x + 6f, tl.y + 2f))
        }
    }

    private fun renderDraft(
        context: RenderContext,
        draft: List<GeoPoint>,
        mode: ToolMode,
        measurer: TextMeasurer?
    ) {
        val screenPoints = draft.map { context.geoToScreen(it) }
        val draftColor = when (mode) {
            ToolMode.MEASURE -> Color(0xFFEAB308)     // Yellow
            ToolMode.DRAW_PATH -> Color(0xFF3B82F6)   // Blue
            ToolMode.DRAW_POLYGON -> Color(0xFF10B981)// Green
            else -> Color(0xFFEF4444)
        }

        // Draw dashed connecting path
        if (screenPoints.size >= 2) {
            val p = Path()
            p.moveTo(screenPoints[0].x, screenPoints[0].y)
            for (i in 1 until screenPoints.size) {
                p.lineTo(screenPoints[i].x, screenPoints[i].y)
            }
            if (mode == ToolMode.DRAW_POLYGON && screenPoints.size >= 3) {
                p.close()
                context.drawScope.drawPath(
                    path = p,
                    color = draftColor.copy(alpha = 0.2f),
                    style = Fill
                )
            }
            context.drawScope.drawPath(
                path = p,
                color = draftColor,
                style = Stroke(
                    width = 2.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )
            )
        }

        // Draw vertex handles
        screenPoints.forEachIndexed { index, sp ->
            context.drawScope.drawCircle(
                color = draftColor,
                radius = 6f,
                center = sp
            )
            context.drawScope.drawCircle(
                color = Color.White,
                radius = 3f,
                center = sp
            )

            // Point index badge
            if (measurer != null) {
                val layout = measurer.measure(
                    text = "${index + 1}",
                    style = TextStyle(color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                )
                context.drawScope.drawRoundRect(
                    color = Color.Black.copy(alpha = 0.7f),
                    topLeft = Offset(sp.x + 8f, sp.y - 12f),
                    size = Size(layout.size.width + 6f, layout.size.height + 2f),
                    cornerRadius = CornerRadius(3f, 3f)
                )
                context.drawScope.drawText(textLayoutResult = layout, topLeft = Offset(sp.x + 11f, sp.y - 11f))
            }
        }

        // Live stats badge for draft
        if (measurer != null && screenPoints.isNotEmpty()) {
            val lastPoint = screenPoints.last()
            val statsText = when (mode) {
                ToolMode.MEASURE, ToolMode.DRAW_PATH -> {
                    val dist = GeoMath.totalPathDistance(draft)
                    "Length: ${GeoMath.formatDistance(dist)}"
                }
                ToolMode.DRAW_POLYGON -> {
                    if (draft.size >= 3) {
                        val area = GeoMath.polygonAreaSquareMeters(draft)
                        "Area: ${GeoMath.formatArea(area)}"
                    } else {
                        "Add ${3 - draft.size} more point(s)"
                    }
                }
                else -> ""
            }

            if (statsText.isNotEmpty()) {
                val layout = measurer.measure(
                    text = statsText,
                    style = TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                )
                val w = layout.size.width + 16f
                val h = layout.size.height + 8f
                val tl = Offset(lastPoint.x + 16f, lastPoint.y + 16f)

                context.drawScope.drawRoundRect(
                    color = Color(0xF00F172A),
                    topLeft = tl,
                    size = Size(w, h),
                    cornerRadius = CornerRadius(6f, 6f)
                )
                context.drawScope.drawRoundRect(
                    color = draftColor,
                    topLeft = tl,
                    size = Size(w, h),
                    cornerRadius = CornerRadius(6f, 6f),
                    style = Stroke(width = 1.5f)
                )
                context.drawScope.drawText(textLayoutResult = layout, topLeft = Offset(tl.x + 8f, tl.y + 4f))
            }
        }
    }

    private fun renderInspectedCrosshair(context: RenderContext, pt: GeoPoint) {
        val sp = context.geoToScreen(pt)
        val len = 18f
        val color = Color(0xFFEF4444)

        // Crosshair reticle
        context.drawScope.drawLine(color, Offset(sp.x - len, sp.y), Offset(sp.x + len, sp.y), strokeWidth = 2f)
        context.drawScope.drawLine(color, Offset(sp.x, sp.y - len), Offset(sp.x, sp.y + len), strokeWidth = 2f)
        context.drawScope.drawCircle(color = color, radius = 8f, center = sp, style = Stroke(width = 1.5f))
    }
}
