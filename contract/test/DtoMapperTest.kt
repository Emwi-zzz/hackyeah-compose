package api

import api.DtoMapper.toDomain
import api.DtoMapper.toDto
import sklepsearch.*
import kotlin.test.*

class DtoMapperTest {
    @Test
    fun curvedShapeIsFlattenedAndRoundTrips() {
        val curved = Path2D().apply {
            moveTo(0.0, 0.0); lineTo(100.0, 0.0); quadTo(150.0, 50.0, 100.0, 100.0); lineTo(0.0, 100.0); closePath()
        }
        val mall = Mall(
            id = 7, name = "T", size = Size(100, 100),
            upperLeft = GeoPoint(19.0, 50.0), downRight = GeoPoint(19.1, 49.9), minFloor = 0,
            entryPoints = listOf(Point(1.0, 2.0)), entryPointNames = listOf("Gate A"),
            floors = listOf(
                Floor(
                    0, curved,
                    listOf(Store(1, 2, "S", Path2D.rectangle(0.0, 0.0, 10.0, 10.0), listOf(Point(5.0, 10.0)))),
                    listOf(Elevator(1, Point(3.0, 3.0))),
                    listOf(Escalator(2, Point(4.0, 4.0), EscalatorDirection.DOWN)),
                )
            ),
        )
        val dto = mall.toDto()
        assertEquals(4, dto.floors[0].stores[0].outline.size)
        assertTrue(dto.floors[0].outline.size > 4, "curve must be sampled into points")

        assertTrue(dto.floors[0].outlineSvg!!.contains(" Q "))
        val back = dto.toDomain()
        assertTrue(back.floors[0].box.segments.any { it is PathSegment.QuadTo }, "curve kept via outlineSvg")
        assertEquals(mall.id, back.id)
        assertEquals(listOf("Gate A"), back.entryPointNames)
        assertEquals(EscalatorDirection.DOWN, back.floors[0].escalators[0].direction)
        assertTrue(back.floors[0].box.contains(Point(50.0, 50.0)))
        assertTrue(back.floors[0].stores[0].area.contains(Point(5.0, 5.0)))
    }
}
