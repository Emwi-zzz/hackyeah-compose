package features.admin.domain

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import api.DtoMapper.toDto
import api.TokenResponse
import core.geometry.GeoPoint
import features.admin.data.AdminApiClient
import features.admin.data.ApiFailure
import features.map.presentation.MapState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import sklepsearch.*
import kotlin.math.hypot

/**
 * State and logic of the admin panel: sign-in, creating a gallery from a drawn outline and
 * editing floors / stores / lifts / entrances of a gallery. Edits are applied to a local draft
 * (shown live on the map) and sent to the backend on [save].
 */
class AdminState(
    private val mapState: MapState,
    private val scope: CoroutineScope,
    private val client: AdminApiClient = AdminApiClient()
) {
    var isOpen by mutableStateOf(false)
    var session by mutableStateOf<TokenResponse?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var messageIsError by mutableStateOf(false)
        private set

    var draft by mutableStateOf<Mall?>(null)
        private set
    var isNew by mutableStateOf(false)
        private set
    var isDirty by mutableStateOf(false)
        private set

    var newMallName by mutableStateOf("")
    var isDrawingNewOutline by mutableStateOf(false)
        private set
    var tool by mutableStateOf<AdminTool?>(null)
        private set
    val points = mutableStateListOf<GeoPoint>()

    var storeName by mutableStateOf("")
    var storeCategory by mutableStateOf("Retail")
    var entranceName by mutableStateOf("")

    private var tempId = 0L

    init {
        mapState.mapClickInterceptor = ::onMapClick
    }

    val isDrawingPolygon: Boolean get() = isDrawingNewOutline || tool?.kind == AdminTool.Kind.POLYGON

    // ---- session -------------------------------------------------------------------------

    fun login(username: String, password: String) {
        if (username.isBlank() || password.isEmpty()) return fail("Enter username and password")
        scope.launch {
            busy = true
            client.login(username.trim(), password)
                .onSuccess { response ->
                    if (response.user.role == "ADMIN") {
                        session = response
                        message = null
                    } else {
                        fail("This account is not an administrator")
                    }
                }
                .onFailure(::handleFailure)
            busy = false
        }
    }

    fun logout() {
        discard()
        session = null
        message = null
    }

    // ---- creating / editing --------------------------------------------------------------

    fun startNewMall() {
        if (newMallName.isBlank()) return fail("Enter the gallery name first")
        if (draft != null) return fail("Save or discard the current gallery first")
        message = null
        tool = null
        points.clear()
        isDrawingNewOutline = true
    }

    fun startEditing() {
        val mall = mapState.focusedMall ?: return fail("No gallery in focus")
        message = null
        draft = mall
        isNew = false
        isDirty = false
        tool = null
        points.clear()
    }

    fun selectTool(newTool: AdminTool) {
        points.clear()
        tool = if (tool == newTool) null else newTool
    }

    fun undoPoint() {
        if (points.isNotEmpty()) points.removeAt(points.size - 1)
    }

    fun cancelDrawing() {
        points.clear()
        isDrawingNewOutline = false
    }

    fun finishDrawing() {
        if (points.size < 3) return fail("A shape needs at least 3 points")
        if (isDrawingNewOutline) {
            val mall = MallFactory.fromOutline(newMallName, points.toList())
            points.clear()
            isDrawingNewOutline = false
            draft = mall
            isNew = true
            isDirty = true
            mapState.replaceMall(mall)
            mapState.selectFloor(0)
            ok("Outline created. Add floors, stores and entrances, then Save.")
            return
        }
        val d = draft ?: return
        if (applyPolygon(d, points.map { d.geoToPoint(it) })) points.clear()
    }

    fun renameMall(name: String) = edit { it.copy(name = name) }

    fun addFloor(number: Int) {
        val d = draft ?: return
        if (d.getFloor(number) != null) return fail("Floor $number already exists")
        val base = d.floors.minByOrNull { kotlin.math.abs(it.number - number) } ?: return
        val floors = (d.floors + Floor(number, base.box, emptyList(), emptyList(), emptyList())).sortedBy { it.number }
        edit { it.copy(floors = floors, minFloor = floors.minOf { f -> f.number }) }
        mapState.selectFloor(number)
    }

    fun deleteFloor(number: Int) {
        val d = draft ?: return
        if (d.floors.size <= 1) return fail("A gallery needs at least one floor")
        val floors = d.floors.filter { it.number != number }
        edit { it.copy(floors = floors, minFloor = floors.minOf { f -> f.number }) }
        mapState.selectFloor(floors.first().number)
    }

    fun save() {
        val d = draft ?: return
        val token = session?.token ?: return
        scope.launch {
            busy = true
            val dto = d.toDto()
            val result = if (isNew) client.createMall(token, dto) else client.updateMall(token, dto)
            result
                .onSuccess { id ->
                    mapState.reloadIndoorData()
                    val saved = mapState.malls.find { it.id == id }
                    draft = saved
                    isNew = false
                    isDirty = false
                    tool = null
                    points.clear()
                    if (saved != null) {
                        mapState.replaceMall(saved)
                        if (saved.getFloor(mapState.currentFloorNumber) == null) {
                            mapState.selectFloor(saved.floors.first().number)
                        }
                    }
                    ok("Saved")
                }
                .onFailure(::handleFailure)
            busy = false
        }
    }

    /** Drops unsaved changes and restores the gallery data from the backend. */
    fun discard() {
        val hadDraft = draft != null
        draft = null
        isNew = false
        isDirty = false
        tool = null
        isDrawingNewOutline = false
        points.clear()
        if (hadDraft) scope.launch { mapState.reloadIndoorData() }
    }

    fun deleteMall() {
        val d = draft ?: return
        if (isNew) return discard()
        val token = session?.token ?: return
        scope.launch {
            busy = true
            client.deleteMall(token, d.id)
                .onSuccess {
                    discard()
                    ok("Gallery deleted")
                }
                .onFailure(::handleFailure)
            busy = false
        }
    }

    // ---- map interaction -----------------------------------------------------------------

    /** Returns true when the click was consumed by the admin tools. */
    private fun onMapClick(geo: GeoPoint): Boolean {
        if (session == null) return false
        val activeTool = tool
        if (isDrawingNewOutline || activeTool?.kind == AdminTool.Kind.POLYGON) {
            points.add(geo)
            return true
        }
        val d = draft ?: return false
        if (activeTool == null) return false
        applyPoint(d, activeTool, d.geoToPoint(geo))
        return true
    }

    private fun applyPolygon(d: Mall, local: List<Point>): Boolean {
        val shape = Path2D.of(*local.toTypedArray())
        when (tool) {
            AdminTool.MALL_OUTLINE -> edit { it.copy(outline = shape) }
            AdminTool.FLOOR_OUTLINE -> {
                val floor = currentFloor(d) ?: return false
                editFloor(floor.number) { it.copy(box = shape) }
            }
            AdminTool.STORE -> {
                val floor = currentFloor(d) ?: return false
                if (storeName.isBlank()) {
                    fail("Enter the store name first")
                    return false
                }
                val id = --tempId
                val b = shape.getBounds()
                val store = Store(
                    Instanceid = id, Shopid = id, name = storeName.trim(), area = shape,
                    entryPoints = listOf(Point(b.centerX, b.centerY)),
                    category = storeCategory.trim().ifEmpty { "Retail" }
                )
                editFloor(floor.number) { it.copy(stores = it.stores + store) }
            }
            else -> return false
        }
        return true
    }

    private fun applyPoint(d: Mall, tool: AdminTool, p: Point) {
        if (tool == AdminTool.ENTRANCE) {
            val names = paddedEntranceNames(d)
            val name = entranceName.trim().ifEmpty { "Entrance / Exit #${d.entryPoints.size + 1}" }
            edit { it.copy(entryPoints = it.entryPoints + p, entryPointNames = names + name) }
            return
        }
        val floor = currentFloor(d) ?: return
        val radius = d.size.x * 0.03
        when (tool) {
            AdminTool.ELEVATOR -> editFloor(floor.number) {
                it.copy(elevators = it.elevators + Elevator(nextId(it.elevators.map { e -> e.id }), p))
            }
            AdminTool.ESCALATOR_UP, AdminTool.ESCALATOR_DOWN -> editFloor(floor.number) {
                val dir = if (tool == AdminTool.ESCALATOR_UP) EscalatorDirection.UP else EscalatorDirection.DOWN
                it.copy(escalators = it.escalators + Escalator(nextId(it.escalators.map { e -> e.id }), p, dir))
            }
            AdminTool.DELETE -> delete(d, floor, p, radius)
            else -> Unit
        }
    }

    private fun delete(d: Mall, floor: Floor, p: Point, radius: Double) {
        floor.stores.find { it.area.contains(p) }?.let { store ->
            editFloor(floor.number) { it.copy(stores = it.stores - store) }
            return
        }
        fun dist(a: Point) = hypot(a.x - p.x, a.y - p.y)
        val lift = floor.elevators.minByOrNull { dist(it.coordinates) }?.takeIf { dist(it.coordinates) <= radius }
        val esc = floor.escalators.minByOrNull { dist(it.coordinates) }?.takeIf { dist(it.coordinates) <= radius }
        val entrance = d.entryPoints.indices.minByOrNull { dist(d.entryPoints[it]) }
            ?.takeIf { dist(d.entryPoints[it]) <= radius }
        val liftD = lift?.let { dist(it.coordinates) } ?: Double.MAX_VALUE
        val escD = esc?.let { dist(it.coordinates) } ?: Double.MAX_VALUE
        val entD = entrance?.let { dist(d.entryPoints[it]) } ?: Double.MAX_VALUE
        when {
            lift != null && liftD <= escD && liftD <= entD ->
                editFloor(floor.number) { it.copy(elevators = it.elevators - lift) }
            esc != null && escD <= entD ->
                editFloor(floor.number) { it.copy(escalators = it.escalators - esc) }
            entrance != null -> {
                val names = paddedEntranceNames(d)
                edit {
                    it.copy(
                        entryPoints = it.entryPoints.filterIndexed { i, _ -> i != entrance },
                        entryPointNames = names.filterIndexed { i, _ -> i != entrance }
                    )
                }
            }
        }
    }

    // ---- helpers -------------------------------------------------------------------------

    private fun paddedEntranceNames(d: Mall): List<String> =
        d.entryPoints.indices.map { d.entryPointNames.getOrNull(it) ?: "Entrance / Exit #${it + 1}" }

    private fun nextId(existing: List<Long>): Long = (existing.maxOrNull() ?: 0L) + 1

    private fun currentFloor(d: Mall): Floor? =
        d.getFloor(mapState.currentFloorNumber).also { if (it == null) fail("Select a floor first") }

    private fun editFloor(number: Int, transform: (Floor) -> Floor) =
        edit { m -> m.copy(floors = m.floors.map { if (it.number == number) transform(it) else it }) }

    private fun edit(transform: (Mall) -> Mall) {
        val d = draft ?: return
        val updated = transform(d)
        draft = updated
        isDirty = true
        mapState.replaceMall(updated)
    }

    private fun ok(text: String) {
        message = text
        messageIsError = false
    }

    private fun fail(text: String) {
        message = text
        messageIsError = true
    }

    private fun handleFailure(e: Throwable) {
        if (e is ApiFailure && e.isUnauthorized && session != null) {
            session = null
            fail("Session expired - sign in again")
        } else {
            fail(e.message ?: "Request failed")
        }
    }
}
