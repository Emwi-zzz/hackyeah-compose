package features.places.domain

import androidx.compose.ui.graphics.Color
import core.geometry.GeoPoint

data class Place(
    val id: String,
    val name: String,
    val polishName: String = name,
    val description: String,
    val category: PlaceCategory,
    val coordinate: GeoPoint,
    val yearEstablished: String? = null,
    val highlights: List<String> = emptyList()
) {
    val categoryColor: Color
        get() = category.color
}
