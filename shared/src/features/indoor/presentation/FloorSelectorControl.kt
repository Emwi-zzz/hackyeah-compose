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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
            shape = RoundedCornerShape(16.dp),
            color = Color(0xF20F172A),
            shadowElevation = 8.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
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
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "FLOORS",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF94A3B8)
                    )
                }

                Box(
                    modifier = Modifier
                        .width(44.dp)
                        .height(1.dp)
                        .background(Color(0xFF334155))
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
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) Color(0xFF2563EB) else if (hasRoute) Color(0xFF1E3A8A) else Color(0xFF1E293B),
                        modifier = Modifier
                            .width(52.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelectFloor(floor.number) }
                            .border(
                                width = if (isSelected) 1.5.dp else if (hasRoute) 1.dp else 0.5.dp,
                                color = if (isSelected) Color(0xFF60A5FA) else if (hasRoute) Color(0xFF38BDF8) else Color(0xFF475569),
                                shape = RoundedCornerShape(10.dp)
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
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                if (hasRoute) {
                                    Spacer(Modifier.width(3.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(Color(0xFF38BDF8), androidx.compose.foundation.shape.CircleShape)
                                    )
                                }
                            }
                            Text(
                                text = if (hasRoute && !isSelected) "Route" else subtitle,
                                fontSize = 8.sp,
                                color = if (isSelected) Color(0xFFDBEAFE) else if (hasRoute) Color(0xFF7DD3FC) else Color(0xFF94A3B8)
                            )
                        }
                    }
                }
            }
        }
    }
}
