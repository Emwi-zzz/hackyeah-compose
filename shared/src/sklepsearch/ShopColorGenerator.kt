package sklepsearch

import androidx.compose.ui.graphics.Color
import kotlin.math.abs

object ShopColorGenerator {

    /**
     * Deterministically generates a soft, distinct architectural color based on Shopid.
     */
    fun colorForShopId(shopId: Long, isSelected: Boolean = false): Color {
        if (isSelected) {
            return Color(0xFF93C5FD) // Highlighted soft bright blue
        }
        var h = shopId xor (shopId ushr 16)
        h = h * 0x45d9f3bL
        h = h xor (h ushr 16)
        val hash = abs(h).toInt()

        val hue = (hash % 360).toFloat()
        val saturation = 0.40f + ((hash ushr 8) % 22) / 100f
        val lightness = 0.84f + ((hash ushr 16) % 8) / 100f

        return hslToColor(hue, saturation, lightness)
    }

    /**
     * Generates a matching partition wall stroke color based on Shopid.
     */
    fun wallColorForShopId(shopId: Long, isSelected: Boolean = false): Color {
        if (isSelected) {
            return Color(0xFF1D4ED8) // Deep blue for selected store
        }
        var h = shopId xor (shopId ushr 16)
        h = h * 0x45d9f3bL
        h = h xor (h ushr 16)
        val hash = abs(h).toInt()

        val hue = (hash % 360).toFloat()
        return hslToColor(hue, 0.35f, 0.50f)
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
        return Color(r + m, g + m, b + m, alpha)
    }
}
