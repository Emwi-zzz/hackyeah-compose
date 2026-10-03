package api

import sklepsearch.*

/** Server-side mapping: domain model -> wire DTOs. Curved shapes are flattened to polygons. */
object DtoMapper {
    private const val CURVE_STEPS = 12

    fun Point.toDto() = PointDto(x, y)
    fun PointDto.toDomain() = Point(x, y)

    fun Path2D.toPolygon(): List<PointDto> {
        val pts = getVertices(CURVE_STEPS).map { it.toDto() }
        return if (pts.size > 1 && pts.first() == pts.last()) pts.dropLast(1) else pts
    }

    fun GeoPoint.toDto() = GeoPointDto(latitude = latitude, longitude = longitude)

    fun Mall.toSummaryDto() = MallSummaryDto(
        id = id, name = name, sizeX = size.x, sizeY = size.y,
        upperLeft = upperLeft.toDto(), downRight = downRight.toDto(),
        minFloor = minFloor, floorNumbers = floors.map { it.number },
    )

    fun Mall.toDto() = MallDto(
        id = id, name = name, sizeX = size.x, sizeY = size.y,
        upperLeft = upperLeft.toDto(), downRight = downRight.toDto(),
        minFloor = minFloor,
        entryPoints = entryPoints.map { it.toDto() },
        entryPointNames = entryPointNames,
        floors = floors.map { it.toDto() },
        outline = outline?.toPolygon().orEmpty(),
        outlineSvg = outline?.toSvgPath(),
    )

    fun Floor.toDto() = FloorDto(
        number = number,
        outline = box.toPolygon(),
        outlineSvg = box.toSvgPath(),
        stores = stores.map { it.toDto() },
        elevators = elevators.map { ElevatorDto(it.id, it.coordinates.toDto()) },
        escalators = escalators.map { EscalatorDto(it.id, it.coordinates.toDto(), it.direction.name) },
    )

    fun Store.toDto() = StoreDto(
        instanceId = Instanceid, shopId = Shopid, name = name, category = category,
        description = description,
        outline = area.toPolygon(),
        outlineSvg = area.toSvgPath(),
        entryPoints = entryPoints.map { it.toDto() },
    )

    /** Client-side mapping: wire DTOs -> domain model (polygons become closed Path2D). */
    fun MallDto.toDomain() = Mall(
        id = id, name = name, size = Size(sizeX, sizeY),
        upperLeft = GeoPoint(longitude = upperLeft.longitude, latitude = upperLeft.latitude),
        downRight = GeoPoint(longitude = downRight.longitude, latitude = downRight.latitude),
        minFloor = minFloor,
        entryPoints = entryPoints.map { it.toDomain() },
        entryPointNames = entryPointNames,
        outline = if (outlineSvg.isNullOrBlank() && outline.isEmpty()) null else shape(outlineSvg, outline),
        floors = floors.map { f ->
            Floor(
                number = f.number,
                box = shape(f.outlineSvg, f.outline),
                stores = f.stores.map { s ->
                    Store(
                        Instanceid = s.instanceId, Shopid = s.shopId, name = s.name,
                        area = shape(s.outlineSvg, s.outline), entryPoints = s.entryPoints.map { it.toDomain() },
                        category = s.category, description = s.description,
                    )
                },
                elevators = f.elevators.map { Elevator(it.id, it.position.toDomain()) },
                escalators = f.escalators.map {
                    Escalator(it.id, it.position.toDomain(), EscalatorDirection.valueOf(it.direction))
                },
            )
        },
    )

    /** Exact SVG path when given, otherwise a closed polygon through the points. */
    fun shape(svg: String?, polygon: List<PointDto>): Path2D =
        if (svg.isNullOrBlank()) polygon.toPath() else Path2D.fromSvgPath(svg)

    fun List<PointDto>.toPath(): Path2D = Path2D().also { path ->
        forEachIndexed { i, p -> if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
        if (isNotEmpty()) path.closePath()
    }
}
