package features.rendering.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import core.ui.AppBorder
import core.ui.AppColors
import core.ui.AppShapes
import features.map.presentation.MapState
import features.rendering.domain.MapLayer
import features.rendering.domain.RenderContext
import kotlin.math.roundToInt

@Composable
fun RenderDebugOverlay(
    mapState: MapState,
    modifier: Modifier = Modifier
) {
    val bounds = mapState.viewport.visibleBounds()
    val tiles = mapState.viewport.visibleTileCoordinates()

    Surface(
        modifier = modifier
            .width(320.dp)
            .clip(AppShapes.Card),
        shape = AppShapes.Card,
        color = AppColors.Surface,
        contentColor = AppColors.TextPrimary,
        border = AppBorder,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Engine & Render Tools",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = AppColors.TextPrimary
                )
                IconButton(
                    onClick = { mapState.isDebugStatsOpen = false },
                    modifier = Modifier.size(20.dp)
                ) {
                    Text("✕", fontSize = 12.sp, color = AppColors.TextSecondary)
                }
            }

            HorizontalDivider(color = AppColors.Border)

            // Stats grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Viewport Size", fontSize = 9.sp, color = AppColors.TextMuted)
                    Text(
                        "${mapState.viewport.screenWidth.roundToInt()} × ${mapState.viewport.screenHeight.roundToInt()} px",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.TextPrimary
                    )
                }
                Column {
                    Text("Visible Tiles", fontSize = 9.sp, color = AppColors.TextMuted)
                    Text("${tiles.size} tiles", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Accent)
                }
                Column {
                    Text("Active Layers", fontSize = 9.sp, color = AppColors.TextMuted)
                    val activeCount = mapState.layerRegistry.layers.count { it.isVisible }
                    Text("$activeCount / ${mapState.layerRegistry.layers.size}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Success)
                }
            }

            // Visible Geo Bounding Box
            Surface(
                shape = AppShapes.Control,
                color = AppColors.SurfaceRaised,
                border = AppBorder,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text("Visible Geographic Bounds (Krakow)", fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextSecondary)
                    Text("N: ${bounds.north.format5()}°, S: ${bounds.south.format5()}°", fontSize = 9.sp, color = AppColors.TextMuted)
                    Text("W: ${bounds.west.format5()}°, E: ${bounds.east.format5()}°", fontSize = 9.sp, color = AppColors.TextMuted)
                }
            }

            // Custom Render Layer Injector
            Text("Custom Render Tools:", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextSecondary)

            var isDemoGridInjected by remember { mutableStateOf(false) }

            Button(
                onClick = {
                    if (isDemoGridInjected) {
                        mapState.layerRegistry.unregisterLayer("demo_grid_layer")
                        isDemoGridInjected = false
                    } else {
                        // Dynamically inject a custom procedural grid render layer
                        val customLayer = object : MapLayer {
                            override val id = "demo_grid_layer"
                            override val name = "Procedural Geo-Grid"
                            override val description = "Injected custom renderer with lat/lon meridian lines"
                            override var isVisible = true
                            override var opacity = 0.5f
                            override val zIndex = 25

                            override fun render(context: RenderContext) {
                                val b = context.visibleBounds
                                val step = 0.01 // Every ~1km
                                var lat = (b.south / step).toInt() * step
                                while (lat <= b.north) {
                                    val start = context.geoToScreen(core.geometry.GeoPoint(lat, b.west))
                                    val end = context.geoToScreen(core.geometry.GeoPoint(lat, b.east))
                                    context.drawScope.drawLine(
                                        color = Color(0xFF22D3EE).copy(alpha = opacity),
                                        start = start,
                                        end = end,
                                        strokeWidth = 1f
                                    )
                                    lat += step
                                }
                            }
                        }
                        mapState.layerRegistry.registerLayer(customLayer)
                        isDemoGridInjected = true
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDemoGridInjected) AppColors.DangerSoft else AppColors.Accent,
                    contentColor = if (isDemoGridInjected) AppColors.Danger else AppColors.OnAccent
                ),
                shape = AppShapes.Control,
                modifier = Modifier.fillMaxWidth().height(32.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(
                    text = if (isDemoGridInjected) "Remove Injected Layer" else "⚡ Inject Custom Render Layer",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Cache flush
            OutlinedButton(
                onClick = { mapState.tileRepository.clearCache() },
                shape = AppShapes.Control,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.TextSecondary),
                border = AppBorder,
                modifier = Modifier.fillMaxWidth().height(30.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("Flush Tile Memory Cache", fontSize = 10.sp)
            }
        }
    }
}

private fun Double.format5(): String {
    return ((this * 100000.0).roundToInt() / 100000.0).toString()
}
