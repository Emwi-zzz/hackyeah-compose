package features.indoor.presentation

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import features.indoor.domain.IndoorRoute
import features.rendering.domain.MapLayer
import features.rendering.domain.RenderContext
import sklepsearch.*
import kotlin.math.abs

class IndoorBuildingLayer(
    override val id: String = "indoor_building_layer",
    override val name: String = "Indoor Building & Floor Plan",
    override val description: String = "Multi-level indoor building floor plan with stores, elevators, and escalators",
    override var isVisible: Boolean = true,
    override var opacity: Float = 1.0f,
    override val zIndex: Int = 10,
    var malls: List<Mall> = emptyList(),
    var focusedMall: Mall? = null,
    var selectedFloorNumber: Int = 0,
    var selectedStore: Store? = null,
    var activeRoute: IndoorRoute? = null,
    var textMeasurer: TextMeasurer? = null
) : MapLayer {

    companion object {
        const val DETAIL_ZOOM_THRESHOLD = 15.5
    }

    override fun render(context: RenderContext) {
        val measurer = textMeasurer
        val zoom = context.viewport.zoom
        val isDetailedView = zoom >= DETAIL_ZOOM_THRESHOLD

        for (curMall in malls) {
            val mallBounds = curMall.getBoundingBox()
            if (!context.visibleBounds.intersects(mallBounds)) continue

            val isFocused = (focusedMall != null && curMall.id == focusedMall?.id)

            fun mapToScreen(p: Point): Offset {
                val geo = curMall.pointToGeo(p)
                return context.geoToScreen(geo)
            }

            fun pathToScreen(path2D: Path2D): Path {
                val p = Path()
                var hasStarted = false
                for (seg in path2D.segments) {
                    when (seg) {
                        is PathSegment.MoveTo -> {
                            val sp = mapToScreen(Point(seg.x, seg.y))
                            p.moveTo(sp.x, sp.y)
                            hasStarted = true
                        }
                        is PathSegment.LineTo -> {
                            val sp = mapToScreen(Point(seg.x, seg.y))
                            if (!hasStarted) {
                                p.moveTo(sp.x, sp.y)
                                hasStarted = true
                            } else {
                                p.lineTo(sp.x, sp.y)
                            }
                        }
                        is PathSegment.QuadTo -> {
                            val sp1 = mapToScreen(Point(seg.x1, seg.y1))
                            val sp2 = mapToScreen(Point(seg.x2, seg.y2))
                            if (!hasStarted) {
                                p.moveTo(sp1.x, sp1.y)
                                hasStarted = true
                            }
                            p.quadraticTo(sp1.x, sp1.y, sp2.x, sp2.y)
                        }
                        is PathSegment.CubicTo -> {
                            val sp1 = mapToScreen(Point(seg.x1, seg.y1))
                            val sp2 = mapToScreen(Point(seg.x2, seg.y2))
                            val sp3 = mapToScreen(Point(seg.x3, seg.y3))
                            if (!hasStarted) {
                                p.moveTo(sp1.x, sp1.y)
                                hasStarted = true
                            }
                            p.cubicTo(sp1.x, sp1.y, sp2.x, sp2.y, sp3.x, sp3.y)
                        }
                        is PathSegment.Close -> p.close()
                    }
                }
                return p
            }

            val floor = if (isFocused) {
                curMall.getFloor(selectedFloorNumber) ?: curMall.floors.firstOrNull() ?: continue
            } else {
                curMall.floors.firstOrNull() ?: continue
            }

            // Outer footprint / building box
            val floorBoxPath = pathToScreen(floor.box)

            // When scale is large (zoom < 15.5) OR if the building is not focused:
            // ONLY the outline should be rendered!
            if (!isDetailedView || !isFocused) {
                val footprintPath = curMall.outline?.let { pathToScreen(it) } ?: floorBoxPath
                // Drop shadow under outline
                context.drawScope.drawPath(
                    path = footprintPath,
                    color = Color.Black.copy(alpha = 0.15f * opacity),
                    style = Stroke(width = if (isFocused) 5f else 3f)
                )

                // Building footprint fill
                context.drawScope.drawPath(
                    path = footprintPath,
                    color = (if (isFocused) Color(0xFFEFF6FF) else Color(0xFFF1F5F9)).copy(alpha = opacity),
                    style = Fill
                )

                // Building outline stroke
                context.drawScope.drawPath(
                    path = footprintPath,
                    color = (if (isFocused) Color(0xFF2563EB) else Color(0xFF64748B)).copy(alpha = opacity),
                    style = Stroke(width = if (isFocused) 3.5f else 2.2f)
                )

                // Outer entry points on outline
                for (entry in curMall.entryPoints) {
                    val sp = mapToScreen(entry)
                    context.drawScope.drawCircle(
                        color = Color(0xFF2563EB).copy(alpha = opacity),
                        radius = 4.5f,
                        center = sp
                    )
                    context.drawScope.drawCircle(
                        color = Color.White.copy(alpha = opacity),
                        radius = 2.5f,
                        center = sp
                    )
                }

                // Building status badge pill
                if (measurer != null && zoom >= 13.0) {
                    val centerGeo = mallBounds.center
                    val centerScreen = context.geoToScreen(centerGeo)
                    val labelText = if (isFocused) "🏢 ${curMall.name} (Active)" else "🏢 ${curMall.name} (Click to focus)"
                    val labelLayout = measurer.measure(
                        text = labelText,
                        style = TextStyle(
                            color = if (isFocused) Color(0xFF1E3A8A) else Color(0xFF334155),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    val pillW = labelLayout.size.width + 16f
                    val pillH = labelLayout.size.height + 6f
                    val tl = Offset(centerScreen.x - pillW / 2f, centerScreen.y - pillH / 2f)

                    context.drawScope.drawRoundRect(
                        color = Color.White.copy(alpha = 0.95f * opacity),
                        topLeft = tl,
                        size = Size(pillW, pillH),
                        cornerRadius = CornerRadius(6f, 6f)
                    )
                    context.drawScope.drawRoundRect(
                        color = (if (isFocused) Color(0xFF2563EB) else Color(0xFF94A3B8)).copy(alpha = opacity),
                        topLeft = tl,
                        size = Size(pillW, pillH),
                        cornerRadius = CornerRadius(6f, 6f),
                        style = Stroke(width = 1.2f)
                    )
                    context.drawScope.drawText(
                        textLayoutResult = labelLayout,
                        topLeft = Offset(tl.x + 8f, tl.y + 3f)
                    )
                }
                continue
            }

            // --- DETAILED INDOOR VIEW FOR FOCUSED BUILDING (zoom >= 15.5) ---
            // 1. Draw Building Outer Footprint
            context.drawScope.drawPath(
                path = floorBoxPath,
                color = Color.Black.copy(alpha = 0.25f * opacity),
                style = Stroke(width = 6f)
            )
            context.drawScope.drawPath(
                path = floorBoxPath,
                color = Color(0xFFF8FAFC).copy(alpha = opacity),
                style = Fill
            )
            context.drawScope.drawPath(
                path = floorBoxPath,
                color = Color(0xFF0F172A).copy(alpha = opacity),
                style = Stroke(width = 3.5f)
            )

            // 2. Blocked areas: atria / escalator wells open to the floor below, edged by a railing
            for (void in floor.voids) {
                val voidPath = pathToScreen(void)
                context.drawScope.drawPath(
                    path = voidPath,
                    color = Color(0xFFDBEAFE).copy(alpha = opacity),
                    style = Fill
                )
                context.drawScope.drawPath(
                    path = voidPath,
                    color = Color(0xFF64748B).copy(alpha = opacity),
                    style = Stroke(width = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)))
                )
            }

            // 3. Draw Stores (Colors generated deterministically based on Shopid!)
            for (store in floor.stores) {
                val storePath = pathToScreen(store.area)
                val isSelected = selectedStore?.Instanceid == store.Instanceid

                val fillColor = ShopColorGenerator.colorForShopId(store.Shopid, isSelected)
                val wallColor = ShopColorGenerator.wallColorForShopId(store.Shopid, isSelected)

                context.drawScope.drawPath(
                    path = storePath,
                    color = fillColor.copy(alpha = opacity),
                    style = Fill
                )
                val wallWidth = if (isSelected) 3f else 1.5f
                context.drawScope.drawPath(
                    path = storePath,
                    color = wallColor.copy(alpha = opacity),
                    style = Stroke(width = wallWidth)
                )

                // Entry points
                for (entry in store.entryPoints) {
                    val sp = mapToScreen(entry)
                    context.drawScope.drawCircle(
                        color = Color(0xFF10B981).copy(alpha = opacity),
                        radius = 3.5f,
                        center = sp
                    )
                    context.drawScope.drawCircle(
                        color = Color.White.copy(alpha = opacity),
                        radius = 1.5f,
                        center = sp
                    )
                }

                // Store name label
                if (measurer != null) {
                    val bounds = store.area.getBounds()
                    val centerPt = Point(bounds.centerX, bounds.centerY)
                    val centerScreen = mapToScreen(centerPt)

                    val labelFontSize = if (zoom >= 16.5) 11.sp else 9.sp
                    val textLayout = measurer.measure(
                        text = store.name,
                        style = TextStyle(
                            color = Color(0xFF0F172A),
                            fontSize = labelFontSize,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                        )
                    )

                    val textW = textLayout.size.width
                    val textH = textLayout.size.height
                    val corner1 = mapToScreen(Point(bounds.minX, bounds.minY))
                    val corner2 = mapToScreen(Point(bounds.maxX, bounds.maxY))
                    val fits = textW <= abs(corner2.x - corner1.x) && textH <= abs(corner2.y - corner1.y)
                    if (!fits && !isSelected) continue

                    val textTopLeft = Offset(centerScreen.x - textW / 2f, centerScreen.y - textH / 2f)

                    context.drawScope.drawRoundRect(
                        color = Color.White.copy(alpha = 0.85f * opacity),
                        topLeft = Offset(textTopLeft.x - 4f, textTopLeft.y - 2f),
                        size = Size(textW + 8f, textH + 4f),
                        cornerRadius = CornerRadius(4f, 4f)
                    )
                    context.drawScope.drawText(
                        textLayoutResult = textLayout,
                        topLeft = textTopLeft
                    )
                }
            }

            // 4. Elevators
            for (elevator in floor.elevators) {
                val sp = mapToScreen(elevator.coordinates)
                val elevatorSize = 22f

                context.drawScope.drawRoundRect(
                    color = Color(0xFF334155).copy(alpha = opacity),
                    topLeft = Offset(sp.x - elevatorSize / 2f, sp.y - elevatorSize / 2f),
                    size = Size(elevatorSize, elevatorSize),
                    cornerRadius = CornerRadius(4f, 4f)
                )
                context.drawScope.drawRoundRect(
                    color = Color.White.copy(alpha = opacity),
                    topLeft = Offset(sp.x - elevatorSize / 2f, sp.y - elevatorSize / 2f),
                    size = Size(elevatorSize, elevatorSize),
                    cornerRadius = CornerRadius(4f, 4f),
                    style = Stroke(width = 1.5f)
                )

                if (measurer != null) {
                    val layout = measurer.measure(
                        text = "🛗",
                        style = TextStyle(fontSize = 11.sp)
                    )
                    context.drawScope.drawText(
                        textLayoutResult = layout,
                        topLeft = Offset(sp.x - layout.size.width / 2f, sp.y - layout.size.height / 2f)
                    )
                }
            }

            // 5. Escalators (Directed UP or DOWN)
            for (escalator in floor.escalators) {
                val sp = mapToScreen(escalator.coordinates)
                val width = 28f
                val height = 18f
                val isUp = escalator.direction == EscalatorDirection.UP
                val bg = if (isUp) Color(0xFF059669) else Color(0xFFD97706)

                context.drawScope.drawRoundRect(
                    color = bg.copy(alpha = 0.95f * opacity),
                    topLeft = Offset(sp.x - width / 2f, sp.y - height / 2f),
                    size = Size(width, height),
                    cornerRadius = CornerRadius(4f, 4f)
                )
                context.drawScope.drawRoundRect(
                    color = Color.White.copy(alpha = opacity),
                    topLeft = Offset(sp.x - width / 2f, sp.y - height / 2f),
                    size = Size(width, height),
                    cornerRadius = CornerRadius(4f, 4f),
                    style = Stroke(width = 1.5f)
                )

                if (measurer != null) {
                    val dirText = if (isUp) "▲ UP" else "▼ DN"
                    val layout = measurer.measure(
                        text = dirText,
                        style = TextStyle(
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    context.drawScope.drawText(
                        textLayoutResult = layout,
                        topLeft = Offset(sp.x - layout.size.width / 2f, sp.y - layout.size.height / 2f)
                    )
                }
            }

            // 6. Mall Outer Entry Points
            for (entry in curMall.entryPoints) {
                val sp = mapToScreen(entry)
                context.drawScope.drawCircle(
                    color = Color(0xFF2563EB).copy(alpha = opacity),
                    radius = 7f,
                    center = sp
                )
                context.drawScope.drawCircle(
                    color = Color.White.copy(alpha = opacity),
                    radius = 4f,
                    center = sp
                )
            }

            // 7. Building Header Indicator Banner
            if (measurer != null) {
                val topCenter = mapToScreen(Point(curMall.size.x / 2.0, 40.0))
                val headerText = "🏢 ${curMall.name} • Floor ${if (floor.number >= 0) "+${floor.number}" else floor.number}"
                val layout = measurer.measure(
                    text = headerText,
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                val pillW = layout.size.width + 16f
                val pillH = layout.size.height + 6f
                val topLeft = Offset(topCenter.x - pillW / 2f, topCenter.y - pillH - 6f)

                context.drawScope.drawRoundRect(
                    color = Color(0xEE0F172A),
                    topLeft = topLeft,
                    size = Size(pillW, pillH),
                    cornerRadius = CornerRadius(6f, 6f)
                )
                context.drawScope.drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(topLeft.x + 8f, topLeft.y + 3f)
                )
            }

            // 8. Active Indoor Bézier Navigation Route on this Floor Level
            val route = activeRoute
            if (route != null && route.mallId == curMall.id) {
                val levelRoute = route.levels.find { it.floorNumber == selectedFloorNumber }
                if (levelRoute != null) {
                    val routePath = pathToScreen(levelRoute.bezierPath)

                    // 8a. Outer glowing halo / aura
                    context.drawScope.drawPath(
                        path = routePath,
                        color = Color(0x553B82F6).copy(alpha = opacity),
                        style = Stroke(width = 14f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )

                    // 8b. Core vibrant navigation curve
                    context.drawScope.drawPath(
                        path = routePath,
                        color = Color(0xFF2563EB).copy(alpha = opacity),
                        style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )

                    // 8c. Animated flowing beam dash effect
                    val phase = ((context.frameTimeNanos / 20_000_000L) % 36).toFloat()
                    context.drawScope.drawPath(
                        path = routePath,
                        color = Color(0xFF67E8F9).copy(alpha = opacity),
                        style = Stroke(
                            width = 3f,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 18f), -phase)
                        )
                    )

                    // 8d. Start Pin Marker (if start location is on this floor)
                    if (route.startLocation.floorNumber == selectedFloorNumber) {
                        val sp = mapToScreen(route.startLocation.coordinates)
                        val pulse = (kotlin.math.sin(context.frameTimeNanos / 200_000_000.0) * 3f + 12f).toFloat()
                        context.drawScope.drawCircle(
                            color = Color(0x4410B981).copy(alpha = opacity),
                            radius = pulse,
                            center = sp
                        )
                        context.drawScope.drawCircle(
                            color = Color(0xFF10B981).copy(alpha = opacity),
                            radius = 8f,
                            center = sp
                        )
                        context.drawScope.drawCircle(
                            color = Color.White.copy(alpha = opacity),
                            radius = 3.5f,
                            center = sp
                        )

                        if (measurer != null) {
                            val layout = measurer.measure(
                                text = "🟢 START: ${route.startLocation.name}",
                                style = TextStyle(
                                    color = Color(0xFF065F46),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            val pillW = layout.size.width + 12f
                            val pillH = layout.size.height + 6f
                            val tl = Offset(sp.x - pillW / 2f, sp.y - pillH - 12f)
                            context.drawScope.drawRoundRect(
                                color = Color(0xF0ECFDF5).copy(alpha = opacity),
                                topLeft = tl,
                                size = Size(pillW, pillH),
                                cornerRadius = CornerRadius(4f, 4f)
                            )
                            context.drawScope.drawRoundRect(
                                color = Color(0xFF10B981).copy(alpha = opacity),
                                topLeft = tl,
                                size = Size(pillW, pillH),
                                cornerRadius = CornerRadius(4f, 4f),
                                style = Stroke(width = 1f)
                            )
                            context.drawScope.drawText(
                                textLayoutResult = layout,
                                topLeft = Offset(tl.x + 6f, tl.y + 3f)
                            )
                        }
                    }

                    // 8e. Destination Pin Marker (if destination is on this floor)
                    if (route.endLocation.floorNumber == selectedFloorNumber) {
                        val sp = mapToScreen(route.endLocation.coordinates)
                        val pulse = (kotlin.math.cos(context.frameTimeNanos / 200_000_000.0) * 3f + 12f).toFloat()
                        context.drawScope.drawCircle(
                            color = Color(0x44EF4444).copy(alpha = opacity),
                            radius = pulse,
                            center = sp
                        )
                        context.drawScope.drawCircle(
                            color = Color(0xFFEF4444).copy(alpha = opacity),
                            radius = 8f,
                            center = sp
                        )
                        context.drawScope.drawCircle(
                            color = Color.White.copy(alpha = opacity),
                            radius = 3.5f,
                            center = sp
                        )

                        if (measurer != null) {
                            val layout = measurer.measure(
                                text = "🏁 END: ${route.endLocation.name}",
                                style = TextStyle(
                                    color = Color(0xFF991B1B),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            val pillW = layout.size.width + 12f
                            val pillH = layout.size.height + 6f
                            val tl = Offset(sp.x - pillW / 2f, sp.y - pillH - 12f)
                            context.drawScope.drawRoundRect(
                                color = Color(0xF0FEF2F2).copy(alpha = opacity),
                                topLeft = tl,
                                size = Size(pillW, pillH),
                                cornerRadius = CornerRadius(4f, 4f)
                            )
                            context.drawScope.drawRoundRect(
                                color = Color(0xFFEF4444).copy(alpha = opacity),
                                topLeft = tl,
                                size = Size(pillW, pillH),
                                cornerRadius = CornerRadius(4f, 4f),
                                style = Stroke(width = 1f)
                            )
                            context.drawScope.drawText(
                                textLayoutResult = layout,
                                topLeft = Offset(tl.x + 6f, tl.y + 3f)
                            )
                        }
                    }

                    // 8f. Escalator/Elevator Transition Callout
                    if (measurer != null && route.endLocation.floorNumber != selectedFloorNumber) {
                        val transitPt = levelRoute.waypoints.lastOrNull()
                        if (transitPt != null) {
                            val sp = mapToScreen(transitPt)
                            val instruction = levelRoute.instructions.lastOrNull() ?: "Transfer floors"
                            val layout = measurer.measure(
                                text = "⚡ $instruction",
                                style = TextStyle(
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            val pillW = layout.size.width + 16f
                            val pillH = layout.size.height + 6f
                            val tl = Offset(sp.x - pillW / 2f, sp.y + 14f)
                            context.drawScope.drawRoundRect(
                                color = Color(0xEE1E40AF).copy(alpha = opacity),
                                topLeft = tl,
                                size = Size(pillW, pillH),
                                cornerRadius = CornerRadius(6f, 6f)
                            )
                            context.drawScope.drawText(
                                textLayoutResult = layout,
                                topLeft = Offset(tl.x + 8f, tl.y + 3f)
                            )
                        }
                    }
                }
            }
        }
    }
}

