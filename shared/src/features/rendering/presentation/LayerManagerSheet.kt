package features.rendering.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import core.ui.AppBorder
import core.ui.AppColors
import core.ui.AppShapes
import features.map.presentation.MapState
import kotlin.math.roundToInt

@Composable
fun LayerManagerSheet(
    mapState: MapState,
    modifier: Modifier = Modifier
) {
    // Subscribes this sheet to visibility/opacity changes of layers
    mapState.layerRegistry.revision

    Surface(
        modifier = modifier
            .width(360.dp)
            .heightIn(max = 520.dp)
            .clip(AppShapes.Card),
        shape = AppShapes.Card,
        color = AppColors.Surface,
        contentColor = AppColors.TextPrimary,
        border = AppBorder,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Layer & Render Pipeline",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = AppColors.TextPrimary
                    )
                    Text(
                        text = "Extensible Clean Architecture render stack",
                        fontSize = 11.sp,
                        color = AppColors.TextMuted
                    )
                }
                IconButton(
                    onClick = { mapState.isLayerManagerOpen = false },
                    modifier = Modifier.size(24.dp)
                ) {
                    Text("✕", fontSize = 14.sp, color = AppColors.TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = AppColors.Border)
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                // Read inside the lazy scope so item content recomposes on revision change
                val revision = mapState.layerRegistry.revision
                items(
                    items = mapState.layerRegistry.layers.sortedByDescending { it.zIndex },
                    key = { it.id }
                ) { layer ->
                    check(revision >= 0)
                    Surface(
                        shape = AppShapes.Control,
                        color = if (layer.isVisible) AppColors.SurfaceRaised else AppColors.SurfaceRaised.copy(alpha = 0.5f),
                        border = AppBorder,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = AppColors.AccentSoft
                                    ) {
                                        Text(
                                            text = "z:${layer.zIndex}",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = AppColors.Accent,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                    Text(
                                        text = layer.name,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = if (layer.isVisible) AppColors.TextPrimary else AppColors.TextMuted
                                    )
                                }

                                Switch(
                                    checked = layer.isVisible,
                                    onCheckedChange = { mapState.layerRegistry.toggleLayer(layer.id) },
                                    modifier = Modifier.height(24.dp)
                                )
                            }

                            Text(
                                text = layer.description,
                                fontSize = 10.sp,
                                color = AppColors.TextMuted,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )

                            if (layer.isVisible) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Opacity:",
                                        fontSize = 10.sp,
                                        color = AppColors.TextSecondary
                                    )
                                    Slider(
                                        value = layer.opacity,
                                        onValueChange = { newOpacity ->
                                            mapState.layerRegistry.setLayerOpacity(layer.id, newOpacity)
                                        },
                                        valueRange = 0.1f..1.0f,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "${(layer.opacity * 100).roundToInt()}%",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = AppColors.TextSecondary,
                                        modifier = Modifier.width(32.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                shape = AppShapes.Control,
                color = AppColors.SuccessSoft,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "💡 Tip: Plug any custom MapLayer into layerRegistry.registerLayer()",
                    fontSize = 10.sp,
                    color = AppColors.Success,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}
