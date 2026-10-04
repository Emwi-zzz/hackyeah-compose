package features.indoor.presentation

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
import sklepsearch.Floor
import sklepsearch.Mall
import sklepsearch.Store

@Composable
fun StoreDetailSheet(
    store: Store,
    floor: Floor,
    mall: Mall,
    onClose: () -> Unit,
    onNavigateToStore: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .width(320.dp)
            .clip(AppShapes.Card),
        shape = AppShapes.Card,
        color = AppColors.Surface,
        shadowElevation = 4.dp,
        border = AppBorder
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Surface(
                        shape = AppShapes.Pill,
                        color = AppColors.AccentSoft
                    ) {
                        Text(
                            text = store.category.uppercase(),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppColors.Accent,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = store.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.TextPrimary
                    )
                    Text(
                        text = "${mall.name} • Floor ${if (floor.number >= 0) "+${floor.number}" else floor.number}",
                        fontSize = 11.sp,
                        color = AppColors.TextSecondary
                    )
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(24.dp)
                ) {
                    Text("✕", fontSize = 12.sp, color = AppColors.TextMuted)
                }
            }

            HorizontalDivider(color = AppColors.Border)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Store ID", fontSize = 9.sp, color = AppColors.TextMuted)
                    Text("#${store.Shopid}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                }
                Column {
                    Text("Entrances", fontSize = 9.sp, color = AppColors.TextMuted)
                    Text("${store.entryPoints.size} Doorway(s)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Success)
                }
                Column {
                    Text("Level", fontSize = 9.sp, color = AppColors.TextMuted)
                    Text("Level ${floor.number}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Accent)
                }
            }

            if (onNavigateToStore != null) {
                Button(
                    onClick = onNavigateToStore,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.Accent,
                        contentColor = AppColors.OnAccent
                    ),
                    shape = AppShapes.Control,
                    modifier = Modifier.fillMaxWidth().height(36.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text("🧭 Directions to ${store.name}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
