package backend

import api.DtoMapper.toDomain
import api.DtoMapper.toDto
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
        assertEquals(Size(124, 358), krakowska.size)
        assertEquals(listOf(-1, 0, 1), krakowska.floors.map { it.number })
        assertEquals(176, krakowska.floors.sumOf { it.stores.size })
        assertEquals(listOf(0, 1, 8), krakowska.floors.map { it.voids.size })
        assertTrue(krakowska.floors.all { it.stores.isNotEmpty() && it.stores.all { s -> s.entryPoints.isNotEmpty() } })
        assertEquals("Strike Zone Bowling", krakowska.getFloor(-1)!!.stores.first().name)
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

    @Test
    fun routingGeometryAndAccessibilitySurviveStorage() {
        val mall = assertNotNull(repo.findById(1))
        val floor = mall.floors.first()
        val updatedFloor = floor.copy(
            elevators = floor.elevators.mapIndexed { index, elevator ->
                if (index == 0) elevator.copy(isAccessible = false) else elevator
            },
            escalators = floor.escalators.mapIndexed { index, escalator ->
                if (index == 0) escalator.copy(isAccessible = true) else escalator
            },
            voids = listOf(Path2D.rectangle(450.0, 450.0, 20.0, 20.0))
        )
        repo.save(mall.copy(floors = mall.floors.map { if (it.number == floor.number) updatedFloor else it }))

        val reloaded = assertNotNull(repo.findById(mall.id)).floors.first()
        assertEquals(updatedFloor.voids.single().toSvgPath(), reloaded.voids.single().toSvgPath())
        assertFalse(reloaded.elevators.first().isAccessible)
        assertTrue(reloaded.escalators.first().isAccessible)

        val apiRoundTrip = assertNotNull(repo.findById(mall.id)).toDto().toDomain().floors.first()
        assertEquals(reloaded.voids.single().toSvgPath(), apiRoundTrip.voids.single().toSvgPath())
        assertFalse(apiRoundTrip.elevators.first().isAccessible)
        assertTrue(apiRoundTrip.escalators.first().isAccessible)
    }
}
