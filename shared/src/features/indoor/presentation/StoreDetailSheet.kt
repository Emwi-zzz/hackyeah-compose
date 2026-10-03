package features.indoor.presentation

import androidx.compose.foundation.background
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
            .clip(RoundedCornerShape(16.dp)),
        color = Color.White.copy(alpha = 0.98f),
        shadowElevation = 8.dp,
        tonalElevation = 3.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
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
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF2563EB).copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = store.category.uppercase(),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1D4ED8),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = store.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = "${mall.name} • Floor ${if (floor.number >= 0) "+${floor.number}" else floor.number}",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(24.dp)
                ) {
                    Text("✕", fontSize = 12.sp, color = Color.Gray)
                }
            }

            HorizontalDivider(color = Color(0xFFE2E8F0))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Store ID", fontSize = 9.sp, color = Color(0xFF64748B))
                    Text("#${store.Shopid}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1E293B))
                }
                Column {
                    Text("Entrances", fontSize = 9.sp, color = Color(0xFF64748B))
                    Text("${store.entryPoints.size} Doorway(s)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF059669))
                }
                Column {
                    Text("Level", fontSize = 9.sp, color = Color(0xFF64748B))
                    Text("Level ${floor.number}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF2563EB))
                }
            }

            if (onNavigateToStore != null) {
                Button(
                    onClick = onNavigateToStore,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().height(36.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text("🧭 Directions to ${store.name}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
