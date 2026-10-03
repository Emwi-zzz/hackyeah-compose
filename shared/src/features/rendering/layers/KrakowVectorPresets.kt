package features.rendering.layers

import androidx.compose.ui.graphics.Color
import core.geometry.GeoPoint

object KrakowVectorPresets {

    /**
     * Vistula River (Wisła) path winding through Krakow from west to east.
     */
    val VISTULA_RIVER = VectorFeature.Polyline(
        id = "wisla_river",
        name = "Wisła (Vistula River)",
        points = listOf(
            GeoPoint(50.0350, 19.8300), // Near Tyniec
            GeoPoint(50.0420, 19.8650), // Bielany / Przegorzały
            GeoPoint(50.0480, 19.8950), // Salwator
            GeoPoint(50.0515, 19.9250), // Most Dębnicki
            GeoPoint(50.0520, 19.9320), // Under Wawel Hill bend
            GeoPoint(50.0470, 19.9400), // Most Grunwaldzki
            GeoPoint(50.0450, 19.9490), // Most Piłsudskiego (Kazimierz / Podgórze)
            GeoPoint(50.0475, 19.9570), // Kładka Bernatka
            GeoPoint(50.0520, 19.9650), // Most Kotlarski
            GeoPoint(50.0560, 19.9850), // Stopień Wodny Dąbie
            GeoPoint(50.0550, 20.0200), // Łęg / Nowa Huta
            GeoPoint(50.0480, 20.0600)  // East Krakow bend
        ),
        color = Color(0xFF2563EB), // Rich blue
        strokeWidthDp = 7f,
        isDashed = false
    )

    /**
     * Planty Park belt encircling the Old Town of Krakow.
     */
    val PLANTY_BELT = VectorFeature.Polyline(
        id = "planty_krakow",
        name = "Planty Park Green Belt",
        points = listOf(
            GeoPoint(50.0570, 19.9330), // Under Wawel
            GeoPoint(50.0590, 19.9315), // Straszewskiego / Filharmonia
            GeoPoint(50.0625, 19.9325), // Teatr Bagatela
            GeoPoint(50.0655, 19.9360), // Brama Floriańska / Barbakan
            GeoPoint(50.0645, 19.9420), // Teatr Słowackiego
            GeoPoint(50.0610, 19.9435), // Poczta Główna / Westerplatte
            GeoPoint(50.0575, 19.9410), // Dominikańska / Gertrudy
            GeoPoint(50.0555, 19.9380), // Podzamcze
            GeoPoint(50.0570, 19.9330)  // Closing loop
        ),
        color = Color(0xFF16A34A), // Rich park green
        strokeWidthDp = 5f,
        isDashed = false
    )

    /**
     * Rynek Główny (Main Market Square) perimeter.
     */
    val RYNEK_OUTLINE = VectorFeature.Polygon(
        id = "rynek_glowny_polygon",
        name = "Rynek Główny Perimeter",
        points = listOf(
            GeoPoint(50.0628, 19.9360), // NW corner (Szczepańska / Sławkowska)
            GeoPoint(50.0628, 19.9386), // NE corner (Floriańska)
            GeoPoint(50.0610, 19.9386), // SE corner (Grodzka / Sienna)
            GeoPoint(50.0610, 19.9360)  // SW corner (Wiślna / Bracka)
        ),
        fillColor = Color(0x33F59E0B), // Warm amber translucent
        strokeColor = Color(0xFFD97706),
        strokeWidthDp = 2f
    )

    /**
     * Approximate Krakow municipal administrative border.
     */
    val KRAKOW_BORDER = VectorFeature.Polyline(
        id = "krakow_border",
        name = "Kraków Boundary Outline",
        points = listOf(
            GeoPoint(50.1250, 19.8600), // North West (Bronowice Wielkie / Tonie)
            GeoPoint(50.1280, 19.9450), // North (Prądnik Biały)
            GeoPoint(50.1150, 20.0400), // North East (Mistrzejowice)
            GeoPoint(50.0900, 20.1400), // Far East (Nowa Huta / Ruszcza)
            GeoPoint(50.0400, 20.1500), // South East (Przylasek Rusiecki)
            GeoPoint(50.0050, 20.0300), // South (Bieżanów / Prokocim)
            GeoPoint(49.9850, 19.9300), // South (Swoszowice / Kurdwanów)
            GeoPoint(50.0050, 19.8500), // South West (Tyniec / Skotniki)
            GeoPoint(50.0500, 19.8100), // West (Las Wolski / Bielany)
            GeoPoint(50.0900, 19.8200), // West (Mydlniki)
            GeoPoint(50.1250, 19.8600)  // Close loop
        ),
        color = Color(0xFFDC2626), // Red dashed
        strokeWidthDp = 2f,
        isDashed = true
    )

    val DEFAULT_FEATURES = listOf(
        VISTULA_RIVER,
        PLANTY_BELT,
        RYNEK_OUTLINE,
        KRAKOW_BORDER
    )
}
