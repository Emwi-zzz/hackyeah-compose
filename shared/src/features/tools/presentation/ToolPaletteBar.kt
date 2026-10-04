package features.tools.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
import features.tools.domain.ToolController
import features.tools.domain.ToolMode

@Composable
fun ToolPaletteBar(
    toolController: ToolController,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = AppShapes.Card,
        color = AppColors.Surface,
        border = AppBorder,
        shadowElevation = 6.dp,
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
                        shape = AppShapes.Control,
                        color = if (isSelected) AppColors.Accent else Color.Transparent,
                        modifier = Modifier
                            .clip(AppShapes.Control)
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
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                color = if (isSelected) AppColors.OnAccent else AppColors.TextSecondary
                            )
                        }
                    }
                }
            }

            // Draft actions bar (shown when drafting points or geometries exist)
            if (toolController.draftPoints.isNotEmpty() || toolController.geometries.isNotEmpty()) {
                HorizontalDivider(color = AppColors.Border)

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
                            color = AppColors.TextPrimary
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            // Undo button
                            Button(
                                onClick = { toolController.undoLastPoint() },
                                shape = AppShapes.Control,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AppColors.SurfaceRaised,
                                    contentColor = AppColors.TextPrimary
                                ),
                                border = AppBorder,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("Undo", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            }

                            // Finish shape button
                            Button(
                                onClick = { toolController.finishCurrentShape() },
                                shape = AppShapes.Control,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AppColors.SuccessSoft,
                                    contentColor = AppColors.Success
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("Finish Shape", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }

                            // Cancel button
                            Button(
                                onClick = { toolController.cancelDraft() },
                                shape = AppShapes.Control,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AppColors.DangerSoft,
                                    contentColor = AppColors.Danger
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("Cancel", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    } else if (toolController.geometries.isNotEmpty()) {
                        Text(
                            text = "Rendered: ${toolController.geometries.size} custom shape(s)",
                            fontSize = 11.sp,
                            color = AppColors.TextSecondary
                        )

                        Button(
                            onClick = { toolController.clearAll() },
                            shape = AppShapes.Control,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AppColors.SurfaceRaised,
                                contentColor = AppColors.TextPrimary
                            ),
                            border = AppBorder,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Text("Clear All", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
