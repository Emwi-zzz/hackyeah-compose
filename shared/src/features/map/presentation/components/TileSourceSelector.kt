package features.map.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import features.map.data.OpenTileSources
import features.map.presentation.MapState

@Composable
fun TileSourceSelector(
    mapState: MapState,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.width(320.dp),
        shape = AppShapes.Card,
        color = AppColors.Surface,
        border = AppBorder,
        shadowElevation = 8.dp
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
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = AppColors.TextPrimary
                )
                IconButton(
                    onClick = { mapState.isTileSelectorOpen = false },
                    modifier = Modifier.size(24.dp)
                ) {
                    Text("✕", fontSize = 14.sp, color = AppColors.TextMuted)
                }
            }

            Text(
                text = "Live dynamic tiles streamed from open APIs without keys",
                fontSize = 11.sp,
                color = AppColors.TextMuted
            )

            HorizontalDivider(color = AppColors.Border)

            for (source in OpenTileSources.ALL) {
                val isSelected = mapState.activeTileSource.id == source.id

                Surface(
                    shape = AppShapes.Control,
                    color = if (isSelected) AppColors.AccentSoft else AppColors.SurfaceRaised,
                    border = if (isSelected) BorderStroke(1.5.dp, AppColors.Accent) else AppBorder,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(AppShapes.Control)
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
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = AppColors.Accent,
                                    unselectedColor = AppColors.TextMuted
                                ),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = source.name,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = if (isSelected) AppColors.Accent else AppColors.TextPrimary
                            )
                        }
                        Text(
                            text = source.description,
                            fontSize = 11.sp,
                            color = AppColors.TextMuted,
                            modifier = Modifier.padding(start = 28.dp, top = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
