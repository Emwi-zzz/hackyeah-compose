package features.map.presentation

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.ln

fun Modifier.mapInteractionGestures(
    mapState: MapState,
    onHover: (Offset) -> Unit = {}
): Modifier {
    return this
        // Multiplatform pointer event listener for scroll wheel and hover movement
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull() ?: continue

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
