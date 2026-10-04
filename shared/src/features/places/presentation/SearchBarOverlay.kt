package features.places.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import core.ui.AppBorder
import core.ui.AppColors
import core.ui.AppShapes
import features.map.presentation.MapState
import features.places.domain.Place
import features.places.domain.PlacesRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SearchBarOverlay(
    mapState: MapState,
    placesRepository: PlacesRepository,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var isExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var searchJob by remember { mutableStateOf<Job?>(null) }

    Surface(
        modifier = modifier.width(360.dp),
        shape = AppShapes.Card,
        color = AppColors.Surface,
        border = AppBorder,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("🔍", fontSize = 16.sp, modifier = Modifier.padding(start = 6.dp))

                TextField(
                    value = query,
                    onValueChange = { newQuery ->
                        query = newQuery
                        isExpanded = true
                        searchJob?.cancel()
                        searchJob = scope.launch {
                            if (newQuery.isNotBlank()) {
                                isSearching = true
                                delay(250) // Debounce
                                results = placesRepository.searchPlaces(newQuery)
                                isSearching = false
                            } else {
                                results = emptyList()
                                isSearching = false
                            }
                        }
                    },
                    placeholder = {
                        Text("Search places in Kraków (e.g. Wawel, Rynek)", fontSize = 13.sp, color = AppColors.TextMuted)
                    },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = AppColors.TextPrimary,
                        unfocusedTextColor = AppColors.TextPrimary,
                        cursorColor = AppColors.Accent
                    ),
                    modifier = Modifier.weight(1f)
                )

                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            query = ""
                            results = emptyList()
                            isExpanded = false
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Text("✕", fontSize = 12.sp, color = AppColors.TextMuted)
                    }
                }
            }

            if (isSearching) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = AppColors.Accent
                )
            }

            // Results list
            if (isExpanded && results.isNotEmpty()) {
                HorizontalDivider(color = AppColors.Border, modifier = Modifier.padding(vertical = 4.dp))

                LazyColumn(
                    modifier = Modifier.heightIn(max = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(results) { place ->
                        Surface(
                            shape = AppShapes.Control,
                            color = AppColors.SurfaceRaised,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    mapState.selectedPlace = place
                                    mapState.flyTo(place.coordinate, 16.0)
                                    isExpanded = false
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = place.categoryColor.copy(alpha = 0.2f),
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("📍", fontSize = 12.sp)
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = place.name,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp,
                                        color = AppColors.TextPrimary
                                    )
                                    Text(
                                        text = place.description,
                                        fontSize = 10.sp,
                                        color = AppColors.TextMuted,
                                        maxLines = 1
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
