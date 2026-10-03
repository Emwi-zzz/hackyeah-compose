package features.map.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import core.geometry.GeoPoint
import core.geometry.WebMercatorProjection
import features.map.presentation.MapState
import kotlin.math.roundToInt

@Composable
fun CoordinateHUD(
    mapState: MapState,
    hoveredScreenOffset: Offset?,
    modifier: Modifier = Modifier
) {
    val center = mapState.viewport.center
    val zoom = mapState.viewport.zoom
    val zInt = mapState.viewport.integerZoom
    val numTiles = 1 shl zInt

    val centerTileX = (WebMercatorProjection.toWorldX(center.longitude) * numTiles).toInt()
    val centerTileY = (WebMercatorProjection.toWorldY(center.latitude) * numTiles).toInt()

    val hoveredGeo: GeoPoint? = if (hoveredScreenOffset != null && mapState.viewport.screenWidth > 0f) {
        WebMercatorProjection.screenToGeo(
            screenOffset = hoveredScreenOffset,
            center = center,
            zoom = zoom,
            screenWidth = mapState.viewport.screenWidth,
            screenHeight = mapState.viewport.screenHeight
        )
    } else null

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xCC0F172A),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Zoom indicator
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Zoom:", fontSize = 10.sp, color = Color(0xFF94A3B8))
                val zoomStr = ((zoom * 10.0).roundToInt() / 10.0).toString()
                Text(zoomStr, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }

            // Tile coordinate indicator
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Tile:", fontSize = 10.sp, color = Color(0xFF94A3B8))
                Text("$zInt / $centerTileX / $centerTileY", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = Color.White)
            }

            // Coordinates (hovered or center)
            val displayGeo = hoveredGeo ?: center
            val latStr = ((displayGeo.latitude * 100000.0).roundToInt() / 100000.0).toString()
            val lonStr = ((displayGeo.longitude * 100000.0).roundToInt() / 100000.0).toString()

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (hoveredGeo != null) "Cursor:" else "Center:", fontSize = 10.sp, color = Color(0xFF94A3B8))
                Text("$latStr° N, $lonStr° E", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF38BDF8))
            }

            // Attribution
            Text(
                text = mapState.activeTileSource.attribution,
                fontSize = 9.sp,
                color = Color(0xFFCBD5E1)
            )
        }
    }
}
