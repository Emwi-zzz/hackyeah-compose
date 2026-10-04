package features.indoor.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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

@Composable
fun FloorSelectorControl(
    mall: Mall,
    currentFloorNumber: Int,
    onSelectFloor: (Int) -> Unit,
    routeFloors: Set<Int> = emptySet(),
    modifier: Modifier = Modifier,
    visible: Boolean = true
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInHorizontally(initialOffsetX = { it }),
        exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it }),
        modifier = modifier
    ) {
        Surface(
            shape = AppShapes.Card,
            color = AppColors.Surface,
            shadowElevation = 4.dp,
            border = AppBorder
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Building Icon & Title
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🏢", fontSize = 16.sp)
                    Text(
                        text = mall.name,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.TextPrimary
                    )
                    Text(
                        text = "FLOORS",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.TextMuted
                    )
                }

                Box(
                    modifier = Modifier
                        .width(44.dp)
                        .height(1.dp)
                        .background(AppColors.Border)
                )

                // Floor Buttons (sorted descending: e.g. +2, +1, 0, -1)
                val sortedFloors = mall.floors.sortedByDescending { it.number }

                for (floor in sortedFloors) {
                    val isSelected = floor.number == currentFloorNumber
                    val floorLabel = when {
                        floor.number > 0 -> "+${floor.number}"
                        floor.number == 0 -> "0"
                        else -> "${floor.number}"
                    }

                    val subtitle = when (floor.number) {
                        2 -> "Food"
                        1 -> "Media"
                        0 -> "Main"
                        -1 -> "Train"
                        else -> "Level"
                    }

                    val hasRoute = floor.number in routeFloors
                    Surface(
                        shape = AppShapes.Control,
                        color = if (isSelected) AppColors.Accent else if (hasRoute) AppColors.AccentSoft else AppColors.SurfaceRaised,
                        modifier = Modifier
                            .width(52.dp)
                            .clip(AppShapes.Control)
                            .clickable { onSelectFloor(floor.number) }
                            .border(
                                width = 1.dp,
                                color = if (isSelected) AppColors.Accent else if (hasRoute) AppColors.Accent else AppColors.Border,
                                shape = AppShapes.Control
                            )
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = floorLabel,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSelected) AppColors.OnAccent else AppColors.TextPrimary
                                )
                                if (hasRoute) {
                                    Spacer(Modifier.width(3.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(if (isSelected) AppColors.OnAccent else AppColors.Accent, CircleShape)
                                    )
                                }
                            }
                            Text(
                                text = if (hasRoute && !isSelected) "Route" else subtitle,
                                fontSize = 8.sp,
                                color = if (isSelected) AppColors.OnAccent.copy(alpha = 0.8f) else if (hasRoute) AppColors.Accent else AppColors.TextMuted
                            )
                        }
                    }
                }
            }
        }
    }
}
