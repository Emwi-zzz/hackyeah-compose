package backend

import backend.db.PostgresMallRepository
import sklepsearch.*
import kotlin.test.*

class PostgresMallRepositoryTest {
    private val repo = PostgresMallRepository(TestDb.freshDataSource())

    @Test
    fun migrationSeedsBothGalleries() {
        assertFalse(repo.isEmpty())
        assertEquals(listOf(1L, 2L), repo.findAll().map { it.id })
        val krakowska = assertNotNull(repo.findById(1))
        assertEquals(listOf(-1, 0, 1, 2), krakowska.floors.map { it.number })
        assertTrue(krakowska.floors.all { it.stores.isNotEmpty() && it.stores.all { s -> s.entryPoints.isNotEmpty() } })
    }

    @Test
    fun curvedOutlinesSurviveStorage() {
        val kazimierz = assertNotNull(repo.findById(2))
        val box = kazimierz.getFloor(0)!!.box
        assertTrue(box.segments.any { it is PathSegment.CubicTo }, "cubic facade must be preserved")
        assertTrue(box.segments.any { it is PathSegment.QuadTo }, "quadratic facade must be preserved")
        assertTrue(box.contains(Point(400.0, 300.0)))
        assertTrue(box.contains(Point(730.0, 875.0)), "inside curved riverfront")
        assertFalse(box.contains(Point(730.0, 910.0)), "beyond curve apex")
        assertFalse(box.contains(Point(300.0, 700.0)), "courtyard notch")
    }

    @Test
    fun saveRoundTripReplacesMall() {
        val mall = assertNotNull(repo.findById(1))
        val store = mall.floors[0].stores[0]
        repo.save(mall.copy(name = "Renamed"))
        val reloaded = assertNotNull(repo.findById(1))
        assertEquals("Renamed", reloaded.name)
        assertEquals(store, reloaded.floors[0].stores[0].let { it.copy(area = store.area) })
        assertEquals(2, repo.findAll().size)
        assertNull(repo.findById(999))
    }
}
