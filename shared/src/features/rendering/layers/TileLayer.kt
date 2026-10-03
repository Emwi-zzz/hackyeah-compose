package features.rendering.layers

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import core.geometry.WebMercatorProjection
import features.rendering.domain.MapLayer
import features.rendering.domain.RenderContext
import kotlin.math.pow
import kotlin.math.roundToInt

class TileLayer(
    override val id: String = "base_tile_layer",
    override val name: String = "Base Map Tiles",
    override val description: String = "Dynamic OpenStreetMap / Carto raster basemap",
    override var isVisible: Boolean = true,
    override var opacity: Float = 1.0f,
    override val zIndex: Int = 0
) : MapLayer {

    override fun render(context: RenderContext) {
        val viewport = context.viewport
        val z = viewport.integerZoom
        val tiles = viewport.visibleTileCoordinates(z)
        val numTiles = 1 shl z
        val scale = WebMercatorProjection.worldPixelSize(viewport.zoom)

        val centerWorldX = WebMercatorProjection.toWorldX(viewport.center.longitude)
        val centerWorldY = WebMercatorProjection.toWorldY(viewport.center.latitude)

        for (tile in tiles) {
            val tileMinWorldX = tile.x.toDouble() / numTiles
            val tileMinWorldY = tile.y.toDouble() / numTiles
            val tileSizeWorld = 1.0 / numTiles

            val tileScreenX = (viewport.screenWidth / 2f) + ((tileMinWorldX - centerWorldX) * scale).toFloat()
            val tileScreenY = (viewport.screenHeight / 2f) + ((tileMinWorldY - centerWorldY) * scale).toFloat()
            val tileScreenSize = (tileSizeWorld * scale).toFloat()

            val dstOffset = IntOffset(tileScreenX.roundToInt(), tileScreenY.roundToInt())
            // Add 1px overlap to prevent subpixel rounding gaps
            val dstSize = IntSize((tileScreenSize + 1f).roundToInt(), (tileScreenSize + 1f).roundToInt())

            val cachedBitmap = context.tileRepository.getTileFromCache(tile, context.activeTileSource)

            if (cachedBitmap != null) {
                context.drawScope.drawImage(
                    image = cachedBitmap,
                    dstOffset = dstOffset,
                    dstSize = dstSize,
                    alpha = opacity,
                    filterQuality = FilterQuality.Low
                )
            } else {
                // Request tile loading in background
                context.tileRepository.prefetchTiles(listOf(tile), context.activeTileSource)

                // Progressive fallback: draw parent tile from zoom - 1 if available
                val parent = tile.parent
                if (parent != null) {
                    val parentBitmap = context.tileRepository.getTileFromCache(parent, context.activeTileSource)
                    if (parentBitmap != null) {
                        val subX = (tile.x % 2) * (parentBitmap.width / 2)
                        val subY = (tile.y % 2) * (parentBitmap.height / 2)
                        val subW = parentBitmap.width / 2
                        val subH = parentBitmap.height / 2

                        context.drawScope.drawImage(
                            image = parentBitmap,
                            srcOffset = IntOffset(subX, subY),
                            srcSize = IntSize(subW, subH),
                            dstOffset = dstOffset,
                            dstSize = dstSize,
                            alpha = opacity * 0.85f,
                            filterQuality = FilterQuality.Low
                        )
                    }
                }
            }
        }
    }
}
