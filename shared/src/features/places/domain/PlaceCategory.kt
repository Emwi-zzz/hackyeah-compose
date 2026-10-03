package features.places.domain

import androidx.compose.ui.graphics.Color

enum class PlaceCategory(
    val title: String,
    val color: Color
) {
    CASTLE("Castle & Royal", Color(0xFF7C3AED)),      // Purple
    MONUMENT("Historic Landmark", Color(0xFFD97706)),// Amber
    CHURCH("Historic Church", Color(0xFFDC2626)),     // Red
    CULTURE("Museum & Culture", Color(0xFF2563EB)),   // Blue
    SQUARE("Public Square", Color(0xFF0D9488)),       // Teal
    PARK("Park & Nature", Color(0xFF16A34A)),         // Green
    MOUND("Historic Mound", Color(0xFF059669)),       // Emerald
    TRANSPORT("Transport Hub", Color(0xFF475569))     // Slate
}
