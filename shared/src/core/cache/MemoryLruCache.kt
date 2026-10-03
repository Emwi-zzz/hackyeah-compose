package core.cache

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Thread-safe multiplatform LRU Cache.
 * Supports both suspend-safe access and direct read for UI render loops.
 */
class MemoryLruCache<K : Any, V : Any>(
    private val maxSize: Int
) {
    private val mutex = Mutex()
    private val map = linkedMapOf<K, V>()

    suspend fun get(key: K): V? = mutex.withLock {
        val value = map.remove(key)
        if (value != null) {
            map[key] = value
        }
        value
    }

    suspend fun put(key: K, value: V): V? = mutex.withLock {
        val previous = map.remove(key)
        map[key] = value
        if (map.size > maxSize) {
            val oldestKey = map.keys.firstOrNull()
            if (oldestKey != null) {
                map.remove(oldestKey)
            }
        }
        previous
    }

    suspend fun remove(key: K): V? = mutex.withLock {
        map.remove(key)
    }

    suspend fun clear() = mutex.withLock {
        map.clear()
    }

    suspend fun size(): Int = mutex.withLock {
        map.size
    }

    /**
     * Non-suspending direct access for synchronous Compose canvas draw operations.
     */
    fun getDirect(key: K): V? {
        return map[key]
    }

    fun putDirect(key: K, value: V) {
        map.remove(key)
        map[key] = value
        if (map.size > maxSize) {
            val oldestKey = map.keys.firstOrNull()
            if (oldestKey != null) {
                map.remove(oldestKey)
            }
        }
    }
}
