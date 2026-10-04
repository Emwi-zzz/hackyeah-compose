package features.map.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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

@Composable
fun MapControls(
    mapState: MapState,
    modifier: Modifier = Modifier,
    showAdmin: Boolean = false,
    isAdminOpen: Boolean = false,
    onToggleAdmin: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.End
    ) {
        // Zoom controls block
        Surface(
            shape = AppShapes.Control,
            color = AppColors.Surface,
            border = AppBorder,
            shadowElevation = 4.dp
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
                    Text("+", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                }

                HorizontalDivider(color = AppColors.Border, thickness = 1.dp)

                // Zoom Out
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clickable { mapState.zoomOut() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("-", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                }
            }
        }

        // GPS / User location toggle & center
        RoundToolButton(
            icon = if (mapState.isGpsOn) "📍" else "🛰️",
            active = mapState.isGpsOn,
            activeColor = AppColors.Accent
        ) {
            if (!mapState.isGpsOn) {
                mapState.toggleGps()
            } else {
                mapState.userLocation?.let { mapState.flyTo(it, 16.5) } ?: mapState.toggleGps()
            }
        }

        // Recenter on Krakow Center
        RoundToolButton(icon = "🎯", active = false) { mapState.resetToKrakow() }

        // Layer Manager toggle
        RoundToolButton(icon = "🥞", active = mapState.isLayerManagerOpen) {
            mapState.isLayerManagerOpen = !mapState.isLayerManagerOpen
        }

        // Tile Source selector toggle
        RoundToolButton(icon = "🗺️", active = mapState.isTileSelectorOpen) {
            mapState.isTileSelectorOpen = !mapState.isTileSelectorOpen
        }

        // Admin toggle (when enabled in mobile/compact view)
        if (showAdmin && onToggleAdmin != null) {
            RoundToolButton(
                icon = "🛠️",
                active = isAdminOpen,
                activeColor = AppColors.Accent
            ) {
                onToggleAdmin()
            }
        }

        // Debug & Render inspector toggle
        RoundToolButton(
            icon = "⚙️",
            active = mapState.isDebugStatsOpen,
            activeColor = AppColors.Success
        ) {
            mapState.isDebugStatsOpen = !mapState.isDebugStatsOpen
        }
    }
}

@Composable
fun RoundToolButton(
    icon: String,
    active: Boolean,
    activeColor: Color = AppColors.Accent,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = if (active) activeColor else AppColors.Surface,
        border = if (active) BorderStroke(1.dp, activeColor) else AppBorder,
        shadowElevation = 4.dp,
        modifier = Modifier.size(44.dp)
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(icon, fontSize = 18.sp)
        }
    }
}
