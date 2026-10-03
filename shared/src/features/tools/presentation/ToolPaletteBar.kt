package features.tools.presentation

import androidx.compose.foundation.background
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
import features.tools.domain.ToolController
import features.tools.domain.ToolMode

@Composable
fun ToolPaletteBar(
    toolController: ToolController,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.95f),
        shadowElevation = 6.dp,
        tonalElevation = 2.dp,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (mode in ToolMode.entries) {
                    val isSelected = toolController.activeMode == mode

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) Color(0xFF2563EB) else Color.Transparent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { toolController.selectMode(mode) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val icon = when (mode) {
                                ToolMode.PAN -> "✋"
                                ToolMode.MEASURE -> "📏"
                                ToolMode.ADD_PIN -> "📍"
                                ToolMode.DRAW_PATH -> "〰️"
                                ToolMode.DRAW_POLYGON -> "⬡"
                                ToolMode.INSPECT -> "🔍"
                            }
                            Text(icon, fontSize = 13.sp)
                            Text(
                                text = mode.title,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else Color(0xFF334155)
                            )
                        }
                    }
                }
            }

            // Draft actions bar (shown when drafting points or geometries exist)
            if (toolController.draftPoints.isNotEmpty() || toolController.geometries.isNotEmpty()) {
                HorizontalDivider(color = Color(0xFFE2E8F0))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (toolController.draftPoints.isNotEmpty()) {
                        Text(
                            text = "Points: ${toolController.draftPoints.size}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF0F172A)
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            // Undo button
                            Button(
                                onClick = { toolController.undoLastPoint() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF64748B)),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("Undo", fontSize = 10.sp)
                            }

                            // Finish shape button
                            Button(
                                onClick = { toolController.finishCurrentShape() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("Finish Shape", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }

                            // Cancel button
                            Button(
                                onClick = { toolController.cancelDraft() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("Cancel", fontSize = 10.sp)
                            }
                        }
                    } else if (toolController.geometries.isNotEmpty()) {
                        Text(
                            text = "Rendered: ${toolController.geometries.size} custom shape(s)",
                            fontSize = 11.sp,
                            color = Color(0xFF475569)
                        )

                        Button(
                            onClick = { toolController.clearAll() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF94A3B8)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Text("Clear All", fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}
