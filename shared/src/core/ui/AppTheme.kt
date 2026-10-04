package core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Dark palette shared by every screen. Use these instead of raw colour literals. */
object AppColors {
    val Background = Color(0xFF0B0E14)
    /** Floating panels, bars and cards (slightly translucent over the map). */
    val Surface = Color(0xF2141922)
    /** Inputs, list rows and nested cards inside a [Surface]. */
    val SurfaceRaised = Color(0xFF1C2230)
    val SurfaceHover = Color(0xFF262E3F)
    val Border = Color(0x1FFFFFFF)
    val BorderStrong = Color(0x33FFFFFF)

    val TextPrimary = Color(0xFFE7EAF0)
    val TextSecondary = Color(0xFFA3ACBD)
    val TextMuted = Color(0xFF6B7487)

    val Accent = Color(0xFF5B8CFF)
    val AccentStrong = Color(0xFF3D6FF2)
    val AccentSoft = Color(0x295B8CFF)
    val OnAccent = Color.White

    val Success = Color(0xFF34D399)
    val SuccessSoft = Color(0x2434D399)
    val Warning = Color(0xFFFBBF24)
    val WarningSoft = Color(0x24FBBF24)
    val Danger = Color(0xFFF87171)
    val DangerSoft = Color(0x24F87171)
}

object AppShapes {
    val Pill = RoundedCornerShape(50)
    val Card = RoundedCornerShape(16.dp)
    val Control = RoundedCornerShape(10.dp)
}

/** Hairline outline that replaces drop shadows on dark surfaces. */
val AppBorder = BorderStroke(1.dp, AppColors.Border)

private val DarkScheme = darkColorScheme(
    primary = AppColors.Accent,
    onPrimary = AppColors.OnAccent,
    primaryContainer = AppColors.AccentSoft,
    onPrimaryContainer = AppColors.TextPrimary,
    secondary = AppColors.Success,
    onSecondary = AppColors.Background,
    background = AppColors.Background,
    onBackground = AppColors.TextPrimary,
    surface = AppColors.Surface,
    onSurface = AppColors.TextPrimary,
    surfaceVariant = AppColors.SurfaceRaised,
    onSurfaceVariant = AppColors.TextSecondary,
    surfaceContainer = AppColors.SurfaceRaised,
    surfaceContainerHigh = AppColors.SurfaceHover,
    surfaceContainerHighest = AppColors.SurfaceHover,
    outline = AppColors.BorderStrong,
    outlineVariant = AppColors.Border,
    error = AppColors.Danger,
    onError = AppColors.Background,
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkScheme) {
        CompositionLocalProvider(LocalContentColor provides AppColors.TextPrimary, content = content)
    }
}
