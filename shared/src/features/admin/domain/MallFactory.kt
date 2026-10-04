package features.admin.domain

import core.geometry.GeoPoint
import sklepsearch.*
import kotlin.math.cos
import kotlin.math.roundToInt

object MallFactory {
    /** Id of a mall that is not saved yet; the server assigns the real one. */
    const val UNSAVED_ID = -1L

    /**
     * Creates a new mall whose coordinate system fits the drawn [outline] (with a small margin).
     * Local space is 1000 units wide; the height keeps the real-world aspect ratio.
     * The mall gets a single floor 0 whose shape equals the outline.
     */
    fun fromOutline(
        name: String,
        outline: List<GeoPoint>,
        smoothBezier: Boolean = false,
        curvedEdges: List<Boolean>? = null,
        closingBezier: Boolean = smoothBezier
    ): Mall {
        require(outline.size >= 3) { "Outline needs at least 3 points" }
        val minLat = outline.minOf { it.latitude }
        val maxLat = outline.maxOf { it.latitude }
        val minLon = outline.minOf { it.longitude }
        val maxLon = outline.maxOf { it.longitude }
        val padLat = ((maxLat - minLat) * 0.05).coerceAtLeast(1e-6)
        val padLon = ((maxLon - minLon) * 0.05).coerceAtLeast(1e-6)

        val upperLeft = sklepsearch.GeoPoint(longitude = minLon - padLon, latitude = maxLat + padLat)
        val downRight = sklepsearch.GeoPoint(longitude = maxLon + padLon, latitude = minLat - padLat)

        val midLat = (upperLeft.latitude + downRight.latitude) / 2.0
        val widthM = ((downRight.longitude - upperLeft.longitude) * 111_320.0 * cos(midLat * PI / 180.0)).coerceAtLeast(1.0)
        val heightM = ((upperLeft.latitude - downRight.latitude) * 110_540.0).coerceAtLeast(1.0)
        val sizeX = 1000
        val sizeY = (sizeX * heightM / widthM).roundToInt().coerceIn(100, 5000)

        val frame = Mall(
            id = UNSAVED_ID, name = name.trim(), size = Size(sizeX, sizeY),
            upperLeft = upperLeft, downRight = downRight, minFloor = 0,
            entryPoints = emptyList(), floors = emptyList()
        )
        val local = outline.map { frame.geoToPoint(it) }
        val edgeModes = curvedEdges ?: List(local.size - 1) { smoothBezier }
        return frame.copy(
            outline = AdminShapeFactory.polygon(local, edgeModes, closingBezier),
            floors = listOf(
                Floor(0, AdminShapeFactory.polygon(local, edgeModes, closingBezier), emptyList(), emptyList(), emptyList())
            )
        )
    }

    private const val PI = kotlin.math.PI
}
