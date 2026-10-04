package features.places.domain

import androidx.compose.ui.graphics.Color

enum class PlaceCategory(
    val title: String,
    val color: Color
) {
    CASTLE("Castle & Royal", Color(0xFF8B5CF6)),      // Violet
    MONUMENT("Historic Landmark", Color(0xFFF59E0B)),// Amber
    CHURCH("Historic Church", Color(0xFFEF4444)),     // Red
    CULTURE("Museum & Culture", Color(0xFF3B82F6)),   // Blue
    SQUARE("Public Square", Color(0xFF14B8A6)),       // Teal
    PARK("Park & Nature", Color(0xFF22C55E)),         // Green
    MOUND("Historic Mound", Color(0xFF10B981)),       // Emerald
    TRANSPORT("Transport Hub", Color(0xFF94A3B8))     // Slate
}
