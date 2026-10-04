package features.tools.domain

import androidx.compose.ui.graphics.Color
import core.geometry.GeoMath
import core.geometry.GeoPoint

sealed interface UserGeometry {
    val id: String
    val name: String

    data class Pin(
        override val id: String,
        override val name: String,
        val coordinate: GeoPoint,
        val color: Color = Color(0xFFEF4444)
    ) : UserGeometry

    data class Path(
        override val id: String,
        override val name: String,
        val points: List<GeoPoint>,
        val color: Color = Color(0xFF3B82F6),
        val strokeWidthDp: Float = 4f
    ) : UserGeometry {
        val totalDistanceMeters: Double
            get() = GeoMath.totalPathDistance(points)

        val formattedDistance: String
            get() = GeoMath.formatDistance(totalDistanceMeters)
    }

    data class Polygon(
        override val id: String,
        override val name: String,
        val points: List<GeoPoint>,
        val fillColor: Color = Color(0x3334D399),
        val strokeColor: Color = Color(0xFF34D399),
        val strokeWidthDp: Float = 2.5f
    ) : UserGeometry {
        val totalAreaSquareMeters: Double
            get() = GeoMath.polygonAreaSquareMeters(points)

        val formattedArea: String
            get() = GeoMath.formatArea(totalAreaSquareMeters)
    }
}
