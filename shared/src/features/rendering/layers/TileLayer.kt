package features.rendering.layers

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import core.geometry.WebMercatorProjection
import features.map.domain.TileCoordinate
import features.map.domain.TileSource
import features.rendering.domain.MapLayer
import features.rendering.domain.RenderContext
import kotlin.math.pow
import kotlin.math.roundToInt

class TileLayer(
    override val id: String = "base_tile_layer",
    override val name: String = "Base Map Tiles",
    override val description: String = "Dynamic OpenStreetMap / Stadia raster basemap",
    override var isVisible: Boolean = true,
    override var opacity: Float = 1.0f,
    override val zIndex: Int = 0
) : MapLayer {

    override fun render(context: RenderContext) {
        val viewport = context.viewport
        val source = context.activeTileSource
        // Past the source's last zoom, keep requesting that zoom and scale the tiles up
        val z = viewport.integerZoom.coerceIn(source.minZoom, source.maxZoom)
        val tiles = viewport.visibleTileCoordinates(z)
        val numTiles = 1 shl z
        val scale = WebMercatorProjection.worldPixelSize(viewport.zoom)
        val filter = if (viewport.integerZoom > source.maxZoom) FilterQuality.Medium else FilterQuality.Low

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

            val cachedBitmap = context.tileRepository.getTileFromCache(tile, source)

            if (cachedBitmap != null) {
                context.drawScope.drawImage(
                    image = cachedBitmap,
                    dstOffset = dstOffset,
                    dstSize = dstSize,
                    alpha = opacity,
                    filterQuality = filter
                )
            } else {
                context.tileRepository.prefetchTiles(listOf(tile), source)
                drawAncestor(context, tile, source, dstOffset, dstSize, filter)
            }
        }
    }

    private fun drawAncestor(
        context: RenderContext,
        tile: TileCoordinate,
        source: TileSource,
        dstOffset: IntOffset,
        dstSize: IntSize,
        filter: FilterQuality
    ) {
        var ancestor = tile.parent
        while (ancestor != null) {
            val bitmap = context.tileRepository.getTileFromCache(ancestor, source)
            if (bitmap != null) {
                val shift = tile.zoom - ancestor.zoom
                val factor = 1 shl shift
                val subW = bitmap.width / factor
                val subH = bitmap.height / factor
                if (subW < 1 || subH < 1) return
                val localX = tile.x - (ancestor.x shl shift)
                val localY = tile.y - (ancestor.y shl shift)
                context.drawScope.drawImage(
                    image = bitmap,
                    srcOffset = IntOffset(localX * subW, localY * subH),
                    srcSize = IntSize(subW, subH),
                    dstOffset = dstOffset,
                    dstSize = dstSize,
                    alpha = opacity * 0.85f,
                    filterQuality = filter
                )
                return
            }
            ancestor = ancestor.parent
        }
    }
}
