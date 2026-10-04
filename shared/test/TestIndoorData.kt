package test

import features.indoor.domain.*
import features.map.data.TileRepositoryImpl
import features.map.presentation.MapState
import features.rendering.domain.LayerRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import sklepsearch.*

/** Backend-free test data: two small malls at the real Kraków locations. */
object TestIndoorData {
    private fun mall(id: Long, name: String, ul: GeoPoint, dr: GeoPoint) = Mall(
        id = id,
        name = name,
        size = Size(1000, 1000),
        upperLeft = ul,
        downRight = dr,
        minFloor = 0,
        entryPoints = listOf(Point(500.0, 950.0)),
        floors = (0..1).map { n ->
            Floor(
                number = n,
                box = Path2D.rectangle(50.0, 50.0, 900.0, 900.0),
                stores = listOf(
                    Store(
                        Instanceid = id * 100 + n,
                        Shopid = id * 10 + n,
                        name = "Shop $id-$n",
                        area = Path2D.rectangle(100.0, 100.0, 200.0, 200.0),
                        entryPoints = listOf(Point(200.0, 300.0))
                    )
                ),
                elevators = listOf(Elevator(1, Point(500.0, 500.0))),
                escalators = listOf(Escalator(2, Point(480.0, 500.0), EscalatorDirection.UP)),
            )
        }
    )

    val krakowska = mall(1, "Galeria Krakowska", GeoPoint(19.9460, 50.0682), GeoPoint(19.9505, 50.0645))
    val kazimierz = mall(2, "Galeria Kazimierz", GeoPoint(19.9545, 50.0550), GeoPoint(19.9615, 50.0505))

    fun locations(mall: Mall): List<NavLocation> =
        mall.entryPoints.mapIndexed { i, p ->
            NavLocation("exit_${mall.id}_$i", "Exit $i", NavLocationType.EXIT, 0, p)
        } + mall.floors.flatMap { f ->
            f.stores.map { NavLocation("store_${it.Instanceid}", it.name, NavLocationType.STORE, f.number, it.entryPoints.first()) }
        }
}

class FakeIndoorRoutingRepository(
    private val malls: List<Mall> = listOf(TestIndoorData.krakowska, TestIndoorData.kazimierz)
) : IndoorRoutingRepository {
    override suspend fun getMalls() = Result.success(malls)

    override suspend fun getNavLocations(mallId: Long) = malls.find { it.id == mallId }
        ?.let { Result.success(TestIndoorData.locations(it)) }
        ?: Result.failure(IllegalArgumentException("Mall $mallId not found"))

    var lastAccessibleOnly: Boolean? = null

    override suspend fun calculateRoute(
        mallId: Long,
        start: NavLocation,
        end: NavLocation,
        accessibleOnly: Boolean
    ): Result<IndoorRoute> {
        lastAccessibleOnly = accessibleOnly
        if (malls.none { it.id == mallId }) return Result.failure(IllegalArgumentException("Mall $mallId not found"))
        val levels = listOf(start.floorNumber, end.floorNumber).distinct().map { floor ->
            val pts = listOf(start.coordinates, Point(500.0, 500.0), end.coordinates)
            FloorLevelRoute(floor, pts, CatmullRomBezierSpline.createSpline(pts), listOf("Go"), 10.0)
        }
        return Result.success(IndoorRoute(mallId, start, end, levels, 20.0, 60))
    }

    var approachCalls = 0

    /** Straight walk due east from the requested position to a fixed entrance point. */
    override suspend fun calculateApproach(
        mallId: Long,
        from: core.geometry.GeoPoint,
        end: NavLocation?,
        accessibleOnly: Boolean
    ): Result<ApproachRoute> {
        approachCalls++
        lastAccessibleOnly = accessibleOnly
        val mall = malls.find { it.id == mallId } ?: return Result.failure(IllegalArgumentException("Mall $mallId not found"))
        val entrance = TestIndoorData.locations(mall).first { it.type == NavLocationType.EXIT }
        val target = core.geometry.GeoPoint(from.latitude, from.longitude + 0.01)
        val outdoor = OutdoorRoute(listOf(from, target), core.geometry.GeoMath.haversineDistanceMeters(from, target), 550, listOf("Walk"))
        val indoor = end?.let { calculateRoute(mallId, entrance, it, accessibleOnly).getOrThrow() }
        return Result.success(
            ApproachRoute(mallId, entrance, outdoor, indoor, outdoor.distanceMeters + (indoor?.totalDistanceMeters ?: 0.0), 610)
        )
    }
}

/** Location source driven by the test: call [emit] / [fail] to simulate the device. */
class FakeLocationSource : core.location.LocationSource {
    override val isSupported = true
    var running = false
    private var onFix: ((core.location.LocationFix) -> Unit)? = null
    private var onError: ((String) -> Unit)? = null

    override fun start(onFix: (core.location.LocationFix) -> Unit, onError: (String) -> Unit) {
        running = true
        this.onFix = onFix
        this.onError = onError
    }

    override fun stop() {
        running = false
    }

    fun emit(latitude: Double, longitude: Double, accuracy: Double = 5.0) =
        onFix?.invoke(core.location.LocationFix(core.geometry.GeoPoint(latitude, longitude), accuracy))

    fun fail(message: String) = onError?.invoke(message)
}

fun createTestMapState(
    repository: IndoorRoutingRepository = FakeIndoorRoutingRepository(),
    locationSource: core.location.LocationSource = FakeLocationSource()
): MapState =
    MapState(
        TileRepositoryImpl(),
        LayerRegistry(emptyList()),
        CoroutineScope(Dispatchers.Unconfined),
        repository,
        locationSource
    )
