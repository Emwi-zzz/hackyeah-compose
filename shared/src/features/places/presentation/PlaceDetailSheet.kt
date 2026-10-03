package features.places.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
            .clip(RoundedCornerShape(16.dp)),
        color = Color.White.copy(alpha = 0.98f),
        shadowElevation = 8.dp,
        tonalElevation = 3.dp
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
                        shape = RoundedCornerShape(6.dp),
                        color = place.categoryColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = place.category.title.uppercase(),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = place.categoryColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = place.name,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    if (place.polishName != place.name) {
                        Text(
                            text = place.polishName,
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(24.dp)
                ) {
                    Text("✕", fontSize = 14.sp, color = Color.Gray)
                }
            }

            // Description
            Text(
                text = place.description,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = Color(0xFF334155)
            )

            // Year & Coordinates
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (place.yearEstablished != null) {
                    Column {
                        Text("Established", fontSize = 10.sp, color = Color(0xFF64748B))
                        Text(place.yearEstablished, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1E293B))
                    }
                }

                Column {
                    Text("Coordinates", fontSize = 10.sp, color = Color(0xFF64748B))
                    val latStr = ((place.coordinate.latitude * 10000.0).roundToInt() / 10000.0).toString()
                    val lonStr = ((place.coordinate.longitude * 10000.0).roundToInt() / 10000.0).toString()
                    Text("$latStr, $lonStr", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1E293B))
                }
            }

            // Highlights
            if (place.highlights.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Highlights", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF64748B))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (highlight in place.highlights.take(3)) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFF1F5F9),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                            ) {
                                Text(
                                    text = highlight,
                                    fontSize = 9.sp,
                                    color = Color(0xFF475569),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = Color(0xFFE2E8F0))

            // Actions
            Button(
                onClick = onCenter,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Center on Map", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
