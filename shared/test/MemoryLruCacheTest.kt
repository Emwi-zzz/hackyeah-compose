package test

import core.cache.MemoryLruCache
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MemoryLruCacheTest {

    @Test
    fun testLruEvictionDirect() {
        val cache = MemoryLruCache<String, String>(maxSize = 3)

        cache.putDirect("a", "1")
        cache.putDirect("b", "2")
        cache.putDirect("c", "3")

        assertEquals("1", cache.getDirect("a"))
        assertEquals("2", cache.getDirect("b"))
        assertEquals("3", cache.getDirect("c"))

        // Put 4th item, should evict least recently used
        cache.putDirect("d", "4")

        assertNull(cache.getDirect("a"), "Key 'a' should have been evicted")
        assertEquals("4", cache.getDirect("d"))
        assertEquals("2", cache.getDirect("b"))
        assertEquals("3", cache.getDirect("c"))
    }

    @Test
    fun testDirectAccess() {
        val cache = MemoryLruCache<String, String>(maxSize = 2)
        cache.putDirect("k1", "v1")
        cache.putDirect("k2", "v2")

        assertEquals("v1", cache.getDirect("k1"))
        assertEquals("v2", cache.getDirect("k2"))

        cache.putDirect("k3", "v3")
        assertNull(cache.getDirect("k1"), "k1 should be evicted")
        assertEquals("v3", cache.getDirect("k3"))
    }
}
