package features.map.presentation

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import kotlin.math.ln

fun Modifier.mapInteractionGestures(
    mapState: MapState,
    onHover: (Offset) -> Unit = {},
    onModifiersChanged: (Offset, Boolean, Boolean) -> Unit = { _, _, _ -> },
    onDragStart: (Offset) -> Boolean = { false },
    onDrag: (Offset, Boolean, Boolean) -> Unit = { _, _, _ -> },
    onDragEnd: () -> Unit = {}
): Modifier {
    var adminVertexDragging = false
    return this
        // Multiplatform pointer event listener for scroll wheel and hover movement
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull() ?: continue
                    val shift = event.keyboardModifiers.isShiftPressed
                    val ctrl = event.keyboardModifiers.isCtrlPressed
                    onModifiersChanged(change.position, shift, ctrl)

                    when (event.type) {
                        PointerEventType.Scroll -> {
                            val scrollDeltaY = change.scrollDelta.y
                            if (scrollDeltaY != 0f) {
                                val zoomDelta = -scrollDeltaY.toDouble() * 0.25
                                mapState.zoomBy(zoomDelta, change.position)
                                change.consume()
                            }
                        }
                        PointerEventType.Move -> {
                            onHover(change.position)
                            if (adminVertexDragging && change.pressed) {
                                onDrag(change.position, shift, ctrl)
                                change.consume()
                            }
                        }
                        PointerEventType.Press -> {
                            adminVertexDragging = onDragStart(change.position)
                            if (adminVertexDragging) change.consume()
                        }
                        PointerEventType.Release -> {
                            if (adminVertexDragging) {
                                onDragEnd()
                                adminVertexDragging = false
                                change.consume()
                            }
                        }
                    }
                }
            }
        }
        // Pan and multi-touch pinch-to-zoom gesture
        .pointerInput(Unit) {
            detectTransformGestures(panZoomLock = false) { centroid, pan, zoomFactor, _ ->
                if (pan.x != 0f || pan.y != 0f) {
                    mapState.panBy(pan.x, pan.y)
                }
                if (zoomFactor != 1.0f && zoomFactor > 0f) {
                    val zoomDelta = ln(zoomFactor.toDouble()) / ln(2.0)
                    mapState.zoomBy(zoomDelta, centroid)
                }
            }
        }
        // Tap and double-tap gestures
        .pointerInput(Unit) {
            detectTapGestures(
                onTap = { offset ->
                    mapState.handleMapClick(offset)
                },
                onDoubleTap = { offset ->
                    mapState.zoomBy(1.0, offset)
                }
            )
        }
}
