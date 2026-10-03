package features.map.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import features.map.data.OpenTileSources
import features.map.presentation.MapState

@Composable
fun TileSourceSelector(
    mapState: MapState,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .width(320.dp)
            .clip(RoundedCornerShape(16.dp)),
        color = Color.White.copy(alpha = 0.98f),
        shadowElevation = 8.dp,
        tonalElevation = 3.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Open Base Map Style",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color(0xFF0F172A)
                )
                IconButton(
                    onClick = { mapState.isTileSelectorOpen = false },
                    modifier = Modifier.size(24.dp)
                ) {
                    Text("✕", fontSize = 14.sp, color = Color.Gray)
                }
            }

            Text(
                text = "Live dynamic tiles streamed from open APIs without keys",
                fontSize = 11.sp,
                color = Color(0xFF64748B)
            )

            HorizontalDivider(color = Color(0xFFE2E8F0))

            for (source in OpenTileSources.ALL) {
                val isSelected = mapState.activeTileSource.id == source.id

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) Color(0xFFEFF6FF) else Color(0xFFF8FAFC),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) Color(0xFF3B82F6) else Color(0xFFE2E8F0),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable {
                            mapState.activeTileSource = source
                        }
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { mapState.activeTileSource = source },
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = source.name,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = if (isSelected) Color(0xFF1D4ED8) else Color(0xFF1E293B)
                            )
                        }
                        Text(
                            text = source.description,
                            fontSize = 11.sp,
                            color = Color(0xFF64748B),
                            modifier = Modifier.padding(start = 28.dp, top = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
