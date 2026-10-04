package features.map.data

import androidx.compose.ui.graphics.ImageBitmap
import core.cache.MemoryLruCache
import core.network.PlatformHttp
import features.map.domain.TileCoordinate
import features.map.domain.TileRepository
import features.map.domain.TileSource
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.compose.resources.decodeToImageBitmap

class TileRepositoryImpl(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
    cacheCapacity: Int = 500
) : TileRepository {

    private val cache = MemoryLruCache<String, ImageBitmap>(cacheCapacity)
    private val inFlightMutex = Mutex()
    private val inFlight = mutableMapOf<String, Deferred<ImageBitmap?>>()

    private val headers = mapOf(
        "User-Agent" to "KrakowMapViewer/1.0 (KotlinMultiplatform; CleanArchitecture; Krakow)",
        "Accept" to "image/png,image/jpeg,image/*;q=0.9",
        // Stadia allows local/dev use when the request looks like it comes from localhost
        "Referer" to "http://localhost/"
    )

    private fun makeKey(coord: TileCoordinate, source: TileSource): String {
        return "${source.id}:${coord.key}"
    }

    override fun getTileFromCache(coord: TileCoordinate, source: TileSource): ImageBitmap? {
        val key = makeKey(coord, source)
        return cache.getDirect(key)
    }

    override suspend fun getTile(coord: TileCoordinate, source: TileSource): ImageBitmap? {
        val key = makeKey(coord, source)

        // 1. Check in-memory LRU cache
        val cached = cache.get(key)
        if (cached != null) return cached

        // 2. Check if request is already in-flight to deduplicate concurrent requests
        val deferred = inFlightMutex.withLock {
            inFlight[key] ?: run {
                val newDeferred = scope.async {
                    fetchAndDecodeTile(coord, source, key)
                }
                inFlight[key] = newDeferred
                newDeferred
            }
        }

        val result = try {
            deferred.await()
        } finally {
            inFlightMutex.withLock {
                inFlight.remove(key)
            }
        }

        return result
    }

    private suspend fun fetchAndDecodeTile(
        coord: TileCoordinate,
        source: TileSource,
        cacheKey: String
    ): ImageBitmap? {
        if (coord.zoom !in source.minZoom..source.maxZoom) return null
        val url = source.getTileUrl(coord)
        val bytes = PlatformHttp.getBytes(url, headers) ?: return null
        return runCatching {
            val bitmap = bytes.decodeToImageBitmap()
            cache.put(cacheKey, bitmap)
            bitmap
        }.getOrNull()
    }

    override fun prefetchTiles(coords: List<TileCoordinate>, source: TileSource) {
        scope.launch {
            coords.forEach { coord ->
                val key = makeKey(coord, source)
                if (cache.get(key) == null) {
                    getTile(coord, source)
                }
            }
        }
    }

    override fun clearCache() {
        scope.launch {
            cache.clear()
        }
    }
}
