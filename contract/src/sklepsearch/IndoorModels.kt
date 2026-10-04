package sklepsearch

data class Size(
    val x: Int,
    val y: Int,
)

data class GeoPoint(
    val longitude: Double,
    val latitude: Double,
)

data class Point(
    val x: Double,
    val y: Double,
)

enum class EscalatorDirection {
    UP,
    DOWN
}

data class Store(
    val Instanceid: Long,
    val Shopid: Long,
    val name: String,
    val area: Path2D,
    val entryPoints: List<Point>,
    val category: String = "Retail",
    val description: String? = null
)

data class Elevator(
    val id: Long,
    val coordinates: Point,
    val isAccessible: Boolean = true,
)

data class Escalator(
    val id: Long,
    val coordinates: Point,
    val direction: EscalatorDirection = EscalatorDirection.UP,
    val isAccessible: Boolean = false,
    // landing point on the target floor (floor + 1 for UP, floor - 1 for DOWN); differs from [coordinates]
    val exitCoordinates: Point = coordinates,
)

data class Floor(
    val number: Int,
    val box: Path2D,
    val stores: List<Store>,
    val elevators: List<Elevator>,
    val escalators: List<Escalator>,
    val voids: List<Path2D> = emptyList(),
)

data class Mall(
    val id: Long,
    val name: String,
    val size: Size,
    val upperLeft: GeoPoint,
    val downRight: GeoPoint,
    val minFloor: Int,
    val entryPoints: List<Point>,
    // parallel to entryPoints; may be shorter if names are unknown
    val entryPointNames: List<String> = emptyList(),
    val floors: List<Floor>,
    // Building footprint shown when no floor details are visible; falls back to a floor box when null
    val outline: Path2D? = null,
)
