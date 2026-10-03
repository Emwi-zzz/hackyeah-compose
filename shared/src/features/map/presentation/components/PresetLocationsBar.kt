package features.map.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import core.geometry.GeoPoint
import features.map.presentation.MapState

data class PresetLocation(
    val emoji: String,
    val name: String,
    val point: GeoPoint,
    val zoom: Double = 15.0
)

val KRAKOW_PRESETS = listOf(
    PresetLocation("🏢", "Galeria Krakowska (Indoor)", GeoPoint(50.0664, 19.9482), 16.5),
    PresetLocation("🛍️", "Galeria Kazimierz (Indoor)", GeoPoint(50.0528, 19.9580), 16.5),
    PresetLocation("🏰", "Wawel Castle", GeoPoint.WAWEL_CASTLE, 15.5),
    PresetLocation("🏛️", "Rynek Główny", GeoPoint.KRAKOW_RYNEK, 16.0),
    PresetLocation("⛪", "Kościół Mariacki", GeoPoint.KOSCIOL_MARIACKI, 16.5),
    PresetLocation("🕍", "Kazimierz", GeoPoint.KAZIMIERZ_PLAC_NOWY, 15.5),
    PresetLocation("🌄", "Kopiec Kościuszki", GeoPoint.KOPIEC_KOSCIUSZKI, 14.5),
    PresetLocation("🌳", "Błonia", GeoPoint.BLONIA_KRAKOWSKIE, 14.5),
    PresetLocation("🏗️", "Nowa Huta", GeoPoint.NOWA_HUTA_PLAC_CENTRALNY, 14.5),
    PresetLocation("🚂", "Kraków Główny", GeoPoint.KRAKOW_GLOWNY, 15.5)
)

@Composable
fun PresetLocationsBar(
    mapState: MapState,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (preset in KRAKOW_PRESETS) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.White.copy(alpha = 0.95f),
                shadowElevation = 3.dp,
                tonalElevation = 1.dp,
                modifier = Modifier.clickable {
                    val matchingMall = mapState.malls.find {
                        it.name.contains(preset.name.substringBefore(" (Indoor)"), ignoreCase = true)
                    }
                    if (matchingMall != null) {
                        mapState.refocusOnMall(matchingMall)
                    } else {
                        mapState.flyTo(preset.point, preset.zoom)
                    }
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(preset.emoji, fontSize = 14.sp)
                    Text(
                        preset.name,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1E293B)
                    )
                }
            }
        }
    }
}
