package features.map.domain

import androidx.compose.ui.graphics.ImageBitmap

interface TileRepository {
    suspend fun getTile(coord: TileCoordinate, source: TileSource): ImageBitmap?
    fun getTileFromCache(coord: TileCoordinate, source: TileSource): ImageBitmap?
    fun prefetchTiles(coords: List<TileCoordinate>, source: TileSource)
    fun clearCache()
}
