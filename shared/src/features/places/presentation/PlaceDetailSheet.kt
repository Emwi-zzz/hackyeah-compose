package features.places.presentation

import androidx.compose.foundation.BorderStroke
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
import features.places.domain.Place
import kotlin.math.roundToInt

@Composable
fun PlaceDetailSheet(
    place: Place,
    onClose: () -> Unit,
    onCenter: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .width(360.dp)
            .clip(AppShapes.Card),
        shape = AppShapes.Card,
        color = AppColors.Surface,
        shadowElevation = 4.dp,
        border = AppBorder
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = AppShapes.Pill,
                        color = place.categoryColor.copy(alpha = 0.18f)
                    ) {
                        Text(
                            text = place.category.title.uppercase(),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = place.categoryColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = place.name,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.TextPrimary
                    )
                    if (place.polishName != place.name) {
                        Text(
                            text = place.polishName,
                            fontSize = 12.sp,
                            color = AppColors.TextMuted
                        )
                    }
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(24.dp)
                ) {
                    Text("✕", fontSize = 14.sp, color = AppColors.TextMuted)
                }
            }

            // Description
            Text(
                text = place.description,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = AppColors.TextSecondary
            )

            // Year & Coordinates
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (place.yearEstablished != null) {
                    Column {
                        Text("Established", fontSize = 10.sp, color = AppColors.TextMuted)
                        Text(place.yearEstablished, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                    }
                }

                Column {
                    Text("Coordinates", fontSize = 10.sp, color = AppColors.TextMuted)
                    val latStr = ((place.coordinate.latitude * 10000.0).roundToInt() / 10000.0).toString()
                    val lonStr = ((place.coordinate.longitude * 10000.0).roundToInt() / 10000.0).toString()
                    Text("$latStr, $lonStr", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                }
            }

            // Highlights
            if (place.highlights.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Highlights", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextMuted)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (highlight in place.highlights.take(3)) {
                            Surface(
                                shape = AppShapes.Pill,
                                color = AppColors.SurfaceRaised,
                                border = BorderStroke(1.dp, AppColors.Border)
                            ) {
                                Text(
                                    text = highlight,
                                    fontSize = 9.sp,
                                    color = AppColors.TextSecondary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = AppColors.Border)

            // Actions
            Button(
                onClick = onCenter,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.Accent,
                    contentColor = AppColors.OnAccent
                ),
                modifier = Modifier.fillMaxWidth(),
                shape = AppShapes.Control
            ) {
                Text("Center on Map", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
