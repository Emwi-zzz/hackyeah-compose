package features.indoor.presentation

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import core.ui.AppBorder
import core.ui.AppColors
import core.ui.AppShapes
import features.indoor.domain.NavLocation
import features.indoor.domain.NavLocationType
import features.map.presentation.MapState

@Composable
fun IndoorRouteSearchBar(
    mapState: MapState,
    modifier: Modifier = Modifier
) {
    val mall = mapState.focusedMall ?: return
    val availableLocations = remember(mall) { mapState.getAvailableNavLocations() }

    var isSelectingStart by remember { mutableStateOf(false) }
    var isSelectingEnd by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isExpanded by remember { mutableStateOf(false) }

    val activeRoute = mapState.activeIndoorRoute
    val approach = mapState.activeApproach
    val hasRoute = activeRoute != null || approach != null

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = AppShapes.Card,
        color = AppColors.Surface,
        shadowElevation = 4.dp,
        border = AppBorder
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Top Bar: Navigation Header & Expand/Collapse Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(AppColors.AccentSoft, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🧭", fontSize = 14.sp)
                    }
                    Column {
                        Text(
                            text = "Indoor Navigation • ${mall.name}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppColors.TextPrimary
                        )
                        if (approach != null) {
                            val inside = approach.indoor?.totalDistanceMeters?.toInt()
                            Text(
                                text = "🚶 ${approach.outdoor.distanceMeters.toInt()} m to ${approach.entrance.name}" +
                                    (if (inside != null) " + $inside m inside" else "") +
                                    " • ~${approach.estimatedTimeSeconds / 60 + 1} min" +
                                    if (mapState.isAccessibleRouting) " • ♿" else "",
                                fontSize = 11.sp,
                                color = AppColors.Success,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else if (activeRoute != null) {
                            Text(
                                text = "${activeRoute.totalDistanceMeters.toInt()} m • ~${activeRoute.estimatedTimeSeconds / 60 + 1} min • ${activeRoute.levels.size} floor${if (activeRoute.levels.size > 1) "s" else ""}" +
                                    if (mapState.isAccessibleRouting) " • ♿" else "",
                                fontSize = 11.sp,
                                color = AppColors.Success,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else if (mapState.indoorRouteError != null) {
                            Text(
                                text = mapState.indoorRouteError!!,
                                fontSize = 10.sp,
                                color = AppColors.Danger,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else {
                            Text(
                                text = "Select start (exit/shop) and destination",
                                fontSize = 10.sp,
                                color = AppColors.TextMuted
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            mapState.clearIndoorRoute()
                            mapState.isIndoorNavigationOpen = false
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text("✕", fontSize = 14.sp, color = AppColors.TextMuted)
                    }
                    if (hasRoute) {
                        IconButton(
                            onClick = { isExpanded = !isExpanded },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Text(if (isExpanded) "▲" else "▼", fontSize = 12.sp, color = AppColors.TextSecondary)
                        }
                    }
                }
            }

            // Expanded Route Selection Panel
            AnimatedVisibility(
                visible = isExpanded || !hasRoute,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Start Point Selector Field
                    LocationPickerField(
                        label = "Starting point (Exit or Shop)",
                        prefixIcon = "🟢",
                        selectedLocation = mapState.indoorRouteStartLocation,
                        onClick = {
                            isSelectingStart = true
                            isSelectingEnd = false
                            searchQuery = ""
                        },
                        onClear = { mapState.indoorRouteStartLocation = null }
                    )

                    // Destination Point Selector Field
                    LocationPickerField(
                        label = "Destination (Exit or Shop)",
                        prefixIcon = "🏁",
                        selectedLocation = mapState.indoorRouteEndLocation,
                        onClick = {
                            isSelectingEnd = true
                            isSelectingStart = false
                            searchQuery = ""
                        },
                        onClear = { mapState.indoorRouteEndLocation = null }
                    )

                    // Controls Row: Swap Endpoints + Find Route Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { mapState.swapIndoorRouteEndpoints() },
                            shape = AppShapes.Control,
                            border = AppBorder,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.TextPrimary),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(38.dp)
                        ) {
                            Text("⇅ Swap", fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                val s = mapState.indoorRouteStartLocation
                                val e = mapState.indoorRouteEndLocation
                                if (s != null && e != null) {
                                    mapState.requestIndoorRoute(s, e)
                                    isExpanded = false
                                }
                            },
                            enabled = mapState.indoorRouteStartLocation != null &&
                                    mapState.indoorRouteEndLocation != null &&
                                    !mapState.isIndoorRouteLoading,
                            shape = AppShapes.Control,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AppColors.Accent,
                                contentColor = AppColors.OnAccent
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                        ) {
                            if (mapState.isIndoorRouteLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = AppColors.OnAccent,
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Calculating...", fontSize = 12.sp)
                            } else {
                                Text("Find Bézier Route ➔", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    // Location Search & Selection Modal / Dropdown List
                    if (isSelectingStart || isSelectingEnd) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp),
                            colors = CardDefaults.cardColors(containerColor = AppColors.SurfaceRaised),
                            shape = AppShapes.Control,
                            border = AppBorder
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = {
                                        Text(
                                            if (isSelectingStart) "Search starting exit or shop..." else "Search destination exit or shop...",
                                            fontSize = 11.sp
                                        )
                                    },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    trailingIcon = {
                                        IconButton(onClick = {
                                            isSelectingStart = false
                                            isSelectingEnd = false
                                        }) {
                                            Text("✕", fontSize = 12.sp)
                                        }
                                    }
                                )

                                val userLocation = mapState.userLocationNav.takeIf { isSelectingStart }
                                val choices = listOfNotNull(userLocation) + availableLocations
                                val filtered = remember(searchQuery, choices) {
                                    if (searchQuery.isBlank()) choices
                                    else choices.filter {
                                        it.name.contains(searchQuery, ignoreCase = true) ||
                                                (it.category?.contains(searchQuery, ignoreCase = true) == true)
                                    }
                                }

                                LazyColumn(
                                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items(filtered) { loc ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(AppShapes.Control)
                                                .clickable {
                                                    if (isSelectingStart) {
                                                        mapState.indoorRouteStartLocation = loc
                                                        isSelectingStart = false
                                                    } else {
                                                        mapState.indoorRouteEndLocation = loc
                                                        isSelectingEnd = false
                                                    }
                                                    // Auto-trigger if both are set
                                                    val s = mapState.indoorRouteStartLocation
                                                    val e = mapState.indoorRouteEndLocation
                                                    if (s != null && e != null) {
                                                        mapState.requestIndoorRoute(s, e)
                                                        isExpanded = false
                                                    }
                                                }
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text(
                                                    when (loc.type) {
                                                        NavLocationType.EXIT -> "🚪"
                                                        NavLocationType.USER_LOCATION -> "📍"
                                                        NavLocationType.STORE -> "🛍️"
                                                    },
                                                    fontSize = 14.sp
                                                )
                                                Column {
                                                    Text(
                                                        loc.name,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    loc.category?.let {
                                                        Text(it, fontSize = 10.sp, color = AppColors.TextMuted)
                                                    }
                                                }
                                            }

                                            Surface(
                                                color = AppColors.SurfaceHover,
                                                shape = AppShapes.Pill
                                            ) {
                                                Text(
                                                    floorText(loc),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = AppColors.TextSecondary,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Street part: remaining distance and the walking steps, until the user reaches the entrance
            val progress = mapState.outdoorProgress
            if (approach != null && !isExpanded) {
                val remaining = (approach.outdoor.distanceMeters - (progress?.alongMeters ?: 0.0)).coerceAtLeast(0.0)
                Surface(
                    color = AppColors.SuccessSoft,
                    shape = AppShapes.Control,
                    border = BorderStroke(1.dp, AppColors.Success.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                        Text(
                            if (remaining < 15) "At the entrance: ${approach.entrance.name}"
                            else "Outdoor: ${remaining.toInt()} m left" +
                                if (mapState.rerouteCount > 0) " • re-routed ${mapState.rerouteCount}×" else "",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppColors.Success
                        )
                        approach.outdoor.instructions.take(4).forEach {
                            Text(it, fontSize = 11.sp, color = AppColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (approach.outdoor.instructions.size > 4) {
                            Text("…", fontSize = 11.sp, color = AppColors.TextMuted)
                        }
                        // Test tools: fake GPS movement along the route, and a jump off it to force a re-route
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                            OutlinedButton(
                                onClick = { mapState.toggleWalkSimulation() },
                                shape = AppShapes.Pill,
                                border = BorderStroke(1.dp, AppColors.Success.copy(alpha = 0.4f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.Success),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text(if (mapState.isSimulatingWalk) "⏸ Stop walk" else "▶ Simulate walk", fontSize = 10.sp)
                            }
                            OutlinedButton(
                                onClick = { mapState.simulateWrongTurn() },
                                enabled = remaining >= 15,
                                shape = AppShapes.Pill,
                                border = BorderStroke(1.dp, AppColors.Success.copy(alpha = 0.4f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.Success),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("↯ Wrong turn", fontSize = 10.sp)
                            }
                        }
                    }
                }
            }

            // Current Active Step Banner (when route is calculated)
            if (activeRoute != null && !isExpanded) {
                val curFloorRoute = activeRoute.levels.find { it.floorNumber == mapState.currentFloorNumber }
                if (curFloorRoute != null) {
                    Surface(
                        color = AppColors.AccentSoft,
                        shape = AppShapes.Control,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Level ${if (curFloorRoute.floorNumber >= 0) "+${curFloorRoute.floorNumber}" else "${curFloorRoute.floorNumber}"} Route:",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AppColors.Accent
                                )
                                Text(
                                    curFloorRoute.instructions.firstOrNull() ?: "Follow blue Bézier path",
                                    fontSize = 11.sp,
                                    color = AppColors.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // If multi-floor, quick jump to next route floor
                            if (activeRoute.levels.size > 1) {
                                val currentIdx = activeRoute.levels.indexOfFirst { it.floorNumber == mapState.currentFloorNumber }
                                if (currentIdx < activeRoute.levels.size - 1) {
                                    val nextLevel = activeRoute.levels[currentIdx + 1]
                                    TextButton(
                                        onClick = { mapState.selectFloor(nextLevel.floorNumber) },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text("Next Floor ➔", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Accent)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun floorText(location: NavLocation) = when {
    location.type == NavLocationType.USER_LOCATION -> "Outdoor"
    location.floorNumber >= 0 -> "Floor +${location.floorNumber}"
    else -> "Floor ${location.floorNumber}"
}

@Composable
private fun LocationPickerField(
    label: String,
    prefixIcon: String,
    selectedLocation: NavLocation?,
    onClick: () -> Unit,
    onClear: () -> Unit
) {
    Surface(
        shape = AppShapes.Control,
        color = AppColors.SurfaceRaised,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, AppColors.Border, AppShapes.Control)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(prefixIcon, fontSize = 12.sp)
                if (selectedLocation != null) {
                    Column {
                        Text(
                            text = selectedLocation.name,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = floorText(selectedLocation),
                            fontSize = 10.sp,
                            color = AppColors.TextMuted
                        )
                    }
                } else {
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        color = AppColors.TextMuted
                    )
                }
            }

            if (selectedLocation != null) {
                IconButton(
                    onClick = onClear,
                    modifier = Modifier.size(20.dp)
                ) {
                    Text("✕", fontSize = 10.sp, color = AppColors.TextMuted)
                }
            }
        }
    }
}
