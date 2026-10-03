package features.rendering.presentation

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import features.map.presentation.MapState
import kotlin.math.roundToInt

@Composable
fun LayerManagerSheet(
    mapState: MapState,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .width(360.dp)
            .heightIn(max = 520.dp)
            .clip(RoundedCornerShape(16.dp)),
        color = Color.White.copy(alpha = 0.98f),
        shadowElevation = 8.dp,
        tonalElevation = 3.dp
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
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = "Extensible Clean Architecture render stack",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                }
                IconButton(
                    onClick = { mapState.isLayerManagerOpen = false },
                    modifier = Modifier.size(24.dp)
                ) {
                    Text("✕", fontSize = 14.sp, color = Color.Gray)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = Color(0xFFE2E8F0))
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                items(mapState.layerRegistry.layers.sortedByDescending { it.zIndex }) { layer ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (layer.isVisible) Color(0xFFF8FAFC) else Color(0xFFF1F5F9),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(10.dp))
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
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(0xFF3B82F6).copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = "z:${layer.zIndex}",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF2563EB),
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                    Text(
                                        text = layer.name,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = if (layer.isVisible) Color(0xFF1E293B) else Color(0xFF94A3B8)
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
                                color = Color(0xFF64748B),
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
                                        color = Color(0xFF475569)
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
                                        color = Color(0xFF475569),
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
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFF0FDF4),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "💡 Tip: Plug any custom MapLayer into layerRegistry.registerLayer()",
                    fontSize = 10.sp,
                    color = Color(0xFF166534),
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}
