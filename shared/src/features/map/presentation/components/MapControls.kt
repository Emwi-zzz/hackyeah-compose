package features.map.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import features.map.presentation.MapState

@Composable
fun MapControls(
    mapState: MapState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.End
    ) {
        // Zoom controls block
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color.White.copy(alpha = 0.95f),
            shadowElevation = 4.dp,
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier.width(44.dp)
            ) {
                // Zoom In
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clickable { mapState.zoomIn() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("+", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                }

                HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 1.dp)

                // Zoom Out
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clickable { mapState.zoomOut() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("-", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                }
            }
        }

        // Recenter on Krakow Center
        Surface(
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.95f),
            shadowElevation = 4.dp,
            modifier = Modifier.size(44.dp)
        ) {
            IconButton(
                onClick = { mapState.resetToKrakow() },
                modifier = Modifier.fillMaxSize()
            ) {
                Text("🎯", fontSize = 18.sp)
            }
        }

        // Layer Manager toggle
        Surface(
            shape = CircleShape,
            color = if (mapState.isLayerManagerOpen) Color(0xFF2563EB) else Color.White.copy(alpha = 0.95f),
            shadowElevation = 4.dp,
            modifier = Modifier.size(44.dp)
        ) {
            IconButton(
                onClick = { mapState.isLayerManagerOpen = !mapState.isLayerManagerOpen },
                modifier = Modifier.fillMaxSize()
            ) {
                Text("🥞", fontSize = 18.sp)
            }
        }

        // Tile Source selector toggle
        Surface(
            shape = CircleShape,
            color = if (mapState.isTileSelectorOpen) Color(0xFF2563EB) else Color.White.copy(alpha = 0.95f),
            shadowElevation = 4.dp,
            modifier = Modifier.size(44.dp)
        ) {
            IconButton(
                onClick = { mapState.isTileSelectorOpen = !mapState.isTileSelectorOpen },
                modifier = Modifier.fillMaxSize()
            ) {
                Text("🗺️", fontSize = 18.sp)
            }
        }

        // Debug & Render inspector toggle
        Surface(
            shape = CircleShape,
            color = if (mapState.isDebugStatsOpen) Color(0xFF10B981) else Color.White.copy(alpha = 0.95f),
            shadowElevation = 4.dp,
            modifier = Modifier.size(44.dp)
        ) {
            IconButton(
                onClick = { mapState.isDebugStatsOpen = !mapState.isDebugStatsOpen },
                modifier = Modifier.fillMaxSize()
            ) {
                Text("⚙️", fontSize = 18.sp)
            }
        }
    }
}
