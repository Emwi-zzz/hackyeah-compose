package features.map.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import core.geometry.GeoPoint
import core.geometry.WebMercatorProjection
import core.ui.AppBorder
import core.ui.AppColors
import core.ui.AppShapes
import features.map.presentation.MapState
import kotlin.math.roundToInt

@Composable
fun CoordinateHUD(
    mapState: MapState,
    hoveredScreenOffset: Offset?,
    modifier: Modifier = Modifier,
    compact: Boolean = false
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
        shape = AppShapes.Control,
        color = AppColors.Surface,
        border = AppBorder,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Zoom indicator
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Zoom:", fontSize = 10.sp, color = AppColors.TextMuted)
                val zoomStr = ((zoom * 10.0).roundToInt() / 10.0).toString()
                Text(zoomStr, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
            }

            // Tile coordinate indicator (omitted in compact mobile view unless debug)
            if (!compact || mapState.isDebugStatsOpen) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Tile:", fontSize = 10.sp, color = AppColors.TextMuted)
                    Text("$zInt/$centerTileX/$centerTileY", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = AppColors.TextPrimary)
                }
            }

            // Coordinates (hovered or center)
            val displayGeo = hoveredGeo ?: center
            val latStr = ((displayGeo.latitude * 10000.0).roundToInt() / 10000.0).toString()
            val lonStr = ((displayGeo.longitude * 10000.0).roundToInt() / 10000.0).toString()

            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(if (hoveredGeo != null) "Cursor:" else "Center:", fontSize = 10.sp, color = AppColors.TextMuted)
                Text("$latStr°, $lonStr°", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Accent)
            }
        }
    }
}

@Composable
fun MapAttribution(
    mapState: MapState,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = AppShapes.Pill,
        color = AppColors.Surface.copy(alpha = 0.85f),
        border = AppBorder,
        modifier = modifier
    ) {
        Text(
            text = mapState.activeTileSource.attribution,
            fontSize = 9.sp,
            color = AppColors.TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}
