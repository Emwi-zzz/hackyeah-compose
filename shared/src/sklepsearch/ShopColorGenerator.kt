package sklepsearch

import androidx.compose.ui.graphics.Color
import core.ui.AppColors
import kotlin.math.abs

object ShopColorGenerator {

    /**
     * Deterministically generates a muted, dark-map friendly tinted fill based on Shopid.
     * Opaque on purpose: callers override alpha with the layer opacity.
     */
    fun colorForShopId(shopId: Long, isSelected: Boolean = false): Color {
        if (isSelected) {
            return Color(0xFF233A6B) // Deep accent-tinted fill for the selected store
        }
        val hash = hashOf(shopId)

        val hue = (hash % 360).toFloat()
        val saturation = 0.28f + ((hash ushr 8) % 14) / 100f
        val lightness = 0.20f + ((hash ushr 16) % 6) / 100f

        return hslToColor(hue, saturation, lightness)
    }

    /**
     * Generates a matching, brighter partition wall stroke color of the same hue based on Shopid.
     */
    fun wallColorForShopId(shopId: Long, isSelected: Boolean = false): Color {
        if (isSelected) {
            return AppColors.Accent
        }
        val hue = (hashOf(shopId) % 360).toFloat()
        return hslToColor(hue, 0.55f, 0.62f)
    }

    /** Non-negative mixed hash; a negative value would yield a negative hue and out-of-range channels. */
    private fun hashOf(shopId: Long): Int {
        var h = shopId xor (shopId ushr 16)
        h = h * 0x45d9f3bL
        h = h xor (h ushr 16)
        return (h and 0x7FFFFFFFL).toInt()
    }

    private fun hslToColor(hue: Float, saturation: Float, lightness: Float, alpha: Float = 1.0f): Color {
        val c = (1f - abs(2f * lightness - 1f)) * saturation
        val x = c * (1f - abs((hue / 60f) % 2f - 1f))
        val m = lightness - c / 2f
        val (r, g, b) = when ((hue / 60f).toInt() % 6) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return Color(
            (r + m).coerceIn(0f, 1f),
            (g + m).coerceIn(0f, 1f),
            (b + m).coerceIn(0f, 1f),
            alpha
        )
    }
}
