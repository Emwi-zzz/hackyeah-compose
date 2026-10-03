package features.indoor.presentation

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.98f),
        shadowElevation = 8.dp,
        tonalElevation = 2.dp
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
                            .background(Color(0xFF2563EB), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🧭", fontSize = 14.sp)
                    }
                    Column {
                        Text(
                            text = "Indoor Navigation • ${mall.name}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        if (activeRoute != null) {
                            Text(
                                text = "${activeRoute.totalDistanceMeters.toInt()} m • ~${activeRoute.estimatedTimeSeconds / 60 + 1} min • ${activeRoute.levels.size} floor${if (activeRoute.levels.size > 1) "s" else ""}",
                                fontSize = 11.sp,
                                color = Color(0xFF059669),
                                fontWeight = FontWeight.SemiBold
                            )
                        } else {
                            Text(
                                text = "Select start (exit/shop) and destination",
                                fontSize = 10.sp,
                                color = Color(0xFF64748B)
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
                        Text("✕", fontSize = 14.sp, color = Color(0xFF64748B))
                    }
                    if (activeRoute != null) {
                        IconButton(
                            onClick = { isExpanded = !isExpanded },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Text(if (isExpanded) "▲" else "▼", fontSize = 12.sp, color = Color(0xFF334155))
                        }
                    }
                }
            }

            // Expanded Route Selection Panel
            AnimatedVisibility(
                visible = isExpanded || activeRoute == null,
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
                            shape = RoundedCornerShape(8.dp),
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
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                        ) {
                            if (mapState.isIndoorRouteLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Calculating...", fontSize = 12.sp)
                            } else {
                                Text("Find Bézier Route ➔", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Location Search & Selection Modal / Dropdown List
                    if (isSelectingStart || isSelectingEnd) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                            shape = RoundedCornerShape(12.dp)
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

                                val filtered = remember(searchQuery, availableLocations) {
                                    if (searchQuery.isBlank()) availableLocations
                                    else availableLocations.filter {
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
                                                .clip(RoundedCornerShape(6.dp))
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
                                                    if (loc.type == NavLocationType.EXIT) "🚪" else "🛍️",
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
                                                        Text(it, fontSize = 10.sp, color = Color(0xFF64748B))
                                                    }
                                                }
                                            }

                                            Surface(
                                                color = Color(0xFFE2E8F0),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    "Floor ${if (loc.floorNumber >= 0) "+${loc.floorNumber}" else "${loc.floorNumber}"}",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
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

            // Current Active Step Banner (when route is calculated)
            if (activeRoute != null && !isExpanded) {
                val curFloorRoute = activeRoute.levels.find { it.floorNumber == mapState.currentFloorNumber }
                if (curFloorRoute != null) {
                    Surface(
                        color = Color(0xFFEFF6FF),
                        shape = RoundedCornerShape(8.dp),
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
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1D4ED8)
                                )
                                Text(
                                    curFloorRoute.instructions.firstOrNull() ?: "Follow blue Bézier path",
                                    fontSize = 11.sp,
                                    color = Color(0xFF1E293B),
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
                                        Text("Next Floor ➔", fontSize = 10.sp, fontWeight = FontWeight.Bold)
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

@Composable
private fun LocationPickerField(
    label: String,
    prefixIcon: String,
    selectedLocation: NavLocation?,
    onClick: () -> Unit,
    onClear: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFF8FAFC),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(8.dp))
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
                            color = Color(0xFF0F172A),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Floor ${if (selectedLocation.floorNumber >= 0) "+${selectedLocation.floorNumber}" else "${selectedLocation.floorNumber}"}",
                            fontSize = 10.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                } else {
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }

            if (selectedLocation != null) {
                IconButton(
                    onClick = onClear,
                    modifier = Modifier.size(20.dp)
                ) {
                    Text("✕", fontSize = 10.sp, color = Color(0xFF64748B))
                }
            }
        }
    }
}
