package features.tools.domain

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import core.geometry.GeoPoint

class ToolController {
    var activeMode by mutableStateOf(ToolMode.PAN)
    val draftPoints = mutableStateListOf<GeoPoint>()
    val geometries = mutableStateListOf<UserGeometry>()
    var inspectedPoint by mutableStateOf<GeoPoint?>(null)

    private var pinCounter = 1
    private var pathCounter = 1
    private var polygonCounter = 1

    fun selectMode(mode: ToolMode) {
        if (activeMode != mode) {
            cancelDraft()
            activeMode = mode
        }
    }

    fun onMapClick(point: GeoPoint) {
        when (activeMode) {
            ToolMode.PAN -> {
                inspectedPoint = null
            }
            ToolMode.INSPECT -> {
                inspectedPoint = point
            }
            ToolMode.ADD_PIN -> {
                val newPin = UserGeometry.Pin(
                    id = "pin_${pinCounter}",
                    name = "Custom Marker #${pinCounter++}",
                    coordinate = point
                )
                geometries.add(newPin)
                inspectedPoint = point
            }
            ToolMode.MEASURE, ToolMode.DRAW_PATH, ToolMode.DRAW_POLYGON -> {
                draftPoints.add(point)
            }
        }
    }

    fun undoLastPoint() {
        if (draftPoints.isNotEmpty()) {
            draftPoints.removeAt(draftPoints.size - 1)
        }
    }

    fun cancelDraft() {
        draftPoints.clear()
        if (activeMode == ToolMode.INSPECT) {
            inspectedPoint = null
        }
    }

    fun finishCurrentShape() {
        if (draftPoints.isEmpty()) return

        when (activeMode) {
            ToolMode.MEASURE -> {
                if (draftPoints.size >= 2) {
                    val path = UserGeometry.Path(
                        id = "measure_${pathCounter}",
                        name = "Measurement #${pathCounter++}",
                        points = draftPoints.toList()
                    )
                    geometries.add(path)
                }
                draftPoints.clear()
            }
            ToolMode.DRAW_PATH -> {
                if (draftPoints.size >= 2) {
                    val path = UserGeometry.Path(
                        id = "path_${pathCounter}",
                        name = "Route #${pathCounter++}",
                        points = draftPoints.toList()
                    )
                    geometries.add(path)
                }
                draftPoints.clear()
            }
            ToolMode.DRAW_POLYGON -> {
                if (draftPoints.size >= 3) {
                    val poly = UserGeometry.Polygon(
                        id = "poly_${polygonCounter}",
                        name = "Zone #${polygonCounter++}",
                        points = draftPoints.toList()
                    )
                    geometries.add(poly)
                }
                draftPoints.clear()
            }
            else -> {
                draftPoints.clear()
            }
        }
    }

    fun deleteGeometry(id: String) {
        geometries.removeAll { it.id == id }
    }

    fun clearAll() {
        draftPoints.clear()
        geometries.clear()
        inspectedPoint = null
    }
}
