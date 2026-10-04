package features.admin.domain

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
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
    var isBezierDrawing by mutableStateOf(false)
        private set
    var isVertexEditing by mutableStateOf(false)
        private set
    private var cursorGeo by mutableStateOf<GeoPoint?>(null)
    private var shiftPressed by mutableStateOf(false)
    private var ctrlPressed by mutableStateOf(false)
    private var activeVertexDrag: VertexDrag? = null
    val points = mutableStateListOf<GeoPoint>()
    val curvedEdges = mutableStateListOf<Boolean>()

    var storeName by mutableStateOf("")
    var storeCategory by mutableStateOf("Retail")
    var entranceName by mutableStateOf("")
    var newAdminUsername by mutableStateOf("")
    var newAdminPassword by mutableStateOf("")

    private var tempId = 0L
    private var pendingEscalator by mutableStateOf<PendingEscalator?>(null)

    init {
        mapState.mapClickInterceptor = ::onMapClick
    }

    val isDrawingPolygon: Boolean get() = isDrawingNewOutline || tool?.kind == AdminTool.Kind.POLYGON
    val previewPoint: GeoPoint?
        get() = if (isDrawingPolygon) cursorGeo?.let { point ->
            points.lastOrNull()?.let { constrainDrawingPoint(it, point, shiftPressed, ctrlPressed) }
        } else null

    val pendingEscalatorMarker: Pair<GeoPoint, Boolean>?
        get() {
            val mall = draft ?: return null
            val pending = pendingEscalator?.takeIf { it.floor == mapState.currentFloorNumber } ?: return null
            return mall.pointToGeo(pending.entry) to (pending.direction == EscalatorDirection.UP)
        }

    val editableVertexHandles: List<GeoPoint>
        get() {
            if (!isVertexEditing) return emptyList()
            val mall = draft ?: return emptyList()
            return editableShapes().flatMap { shape ->
                AdminShapeFactory.editablePoints(shape.path)
                    .filter { it.anchorIndex != null }
                    .map { mall.pointToGeo(it.point) }
            }
        }

    val editableControlHandles: List<GeoPoint>
        get() {
            if (!isVertexEditing) return emptyList()
            val mall = draft ?: return emptyList()
            return editableShapes().flatMap { shape ->
                AdminShapeFactory.editablePoints(shape.path)
                    .filter { it.controlIndex != null }
                    .map { mall.pointToGeo(it.point) }
            }
        }

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

    fun createAdmin() {
        val currentSession = session ?: return fail("Sign in as an administrator first")
        if (newAdminUsername.isBlank() || newAdminPassword.isEmpty()) {
            return fail("Enter username and password")
        }
        scope.launch {
            busy = true
            client.createAdmin(currentSession.token, newAdminUsername.trim(), newAdminPassword)
                .onSuccess { user ->
                    newAdminUsername = ""
                    newAdminPassword = ""
                    ok("Administrator '${user.username}' created")
                }
                .onFailure(::handleFailure)
            busy = false
        }
    }

    // ---- creating / editing --------------------------------------------------------------

    fun startNewMall() {
        if (newMallName.isBlank()) return fail("Enter the gallery name first")
        if (draft != null) return fail("Save or discard the current gallery first")
        message = null
        tool = null
        isBezierDrawing = false
        isVertexEditing = false
        points.clear()
        curvedEdges.clear()
        isDrawingNewOutline = true
    }

    fun startEditing() {
        val mall = mapState.focusedMall ?: return fail("No gallery in focus")
        pendingEscalator = null
        message = null
        draft = mall
        isNew = false
        isDirty = false
        tool = null
        isBezierDrawing = false
        isVertexEditing = false
        points.clear()
        curvedEdges.clear()
    }

    fun selectTool(newTool: AdminTool) {
        pendingEscalator = null
        points.clear()
        curvedEdges.clear()
        isBezierDrawing = false
        isVertexEditing = false
        tool = if (tool == newTool) null else newTool
    }

    fun toggleVertexEditing() {
        isVertexEditing = !isVertexEditing
        activeVertexDrag = null
        cursorGeo = null
        if (isVertexEditing) {
            tool = null
            points.clear()
            curvedEdges.clear()
            isBezierDrawing = false
        }
    }

    fun updatePointer(geo: GeoPoint, shift: Boolean, ctrl: Boolean) {
        cursorGeo = geo
        shiftPressed = shift
        ctrlPressed = ctrl
    }

    fun beginVertexDrag(screenOffset: Offset): Boolean {
        if (!isVertexEditing || session == null) return false
        val mall = draft ?: return false
        val screenHandles = editableShapes().flatMap { shape ->
            AdminShapeFactory.editablePoints(shape.path).map { editablePoint ->
                val geo = mall.pointToGeo(editablePoint.point)
                val screen = mapState.screenAtGeo(geo)
                val dx = screen.x - screenOffset.x
                val dy = screen.y - screenOffset.y
                Triple(shape, editablePoint, dx * dx + dy * dy)
            }
        }
        val nearest = screenHandles.minByOrNull { it.third } ?: return false
        if (nearest.third > 18f * 18f) return false
        val shape = nearest.first
        activeVertexDrag = VertexDrag(shape.target, shape.path, nearest.second)
        return true
    }

    fun dragVertex(screenOffset: Offset, shift: Boolean, ctrl: Boolean) {
        val drag = activeVertexDrag ?: return
        val mall = draft ?: return
        val target = mapState.geoAtScreen(screenOffset)
        val vertices = AdminShapeFactory.vertices(drag.path)
        val pointIndex = drag.editablePoint.anchorIndex ?: vertices.indices.minByOrNull {
            pointDistanceSquared(vertices[it], drag.editablePoint.point)
        } ?: return
        val moved = constrainDraggedVertex(vertices, pointIndex, mall.geoToPoint(target), shift, ctrl)
        val updatedPath = AdminShapeFactory.moveEditablePoint(drag.path, drag.editablePoint, moved)
        updateShapePath(drag.target, updatedPath)
    }

    fun endVertexDrag() {
        activeVertexDrag = null
    }

    fun selectBezierMode(enabled: Boolean) {
        isBezierDrawing = enabled
    }

    fun undoPoint() {
        if (points.isNotEmpty()) {
            points.removeAt(points.size - 1)
            if (curvedEdges.isNotEmpty()) curvedEdges.removeAt(curvedEdges.size - 1)
        }
    }

    fun cancelDrawing() {
        points.clear()
        curvedEdges.clear()
        isDrawingNewOutline = false
        isBezierDrawing = false
    }

    fun finishDrawing() {
        if (points.size < 3) return fail("A shape needs at least 3 points")
        if (isDrawingNewOutline) {
            val mall = MallFactory.fromOutline(
                newMallName,
                points.toList(),
                curvedEdges = curvedEdges.toList(),
                closingBezier = isBezierDrawing
            )
            points.clear()
            curvedEdges.clear()
            isDrawingNewOutline = false
            isBezierDrawing = false
            draft = mall
            isNew = true
            isDirty = true
            mapState.replaceMall(mall)
            mapState.selectFloor(0)
            ok("Outline created. Add floors, stores and entrances, then Save.")
            return
        }
        val d = draft ?: return
        if (applyPolygon(
                d,
                points.map { d.geoToPoint(it) },
                curvedEdges.toList(),
                isBezierDrawing
            )
        ) {
            points.clear()
            curvedEdges.clear()
            isBezierDrawing = false
        }
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
                    isBezierDrawing = false
                    points.clear()
                    curvedEdges.clear()
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
        pendingEscalator = null
        draft = null
        isNew = false
        isDirty = false
        tool = null
        isDrawingNewOutline = false
        isBezierDrawing = false
        points.clear()
        curvedEdges.clear()
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
            val point = points.lastOrNull()?.let { constrainDrawingPoint(it, geo, shiftPressed, ctrlPressed) } ?: geo
            if (points.isNotEmpty()) curvedEdges.add(isBezierDrawing)
            points.add(point)
            return true
        }
        val d = draft ?: return false
        if (activeTool == null) return false
        applyPoint(d, activeTool, d.geoToPoint(geo))
        return true
    }

    private fun applyPolygon(
        d: Mall,
        local: List<Point>,
        edgeModes: List<Boolean>,
        closingBezier: Boolean
    ): Boolean {
        val shape = AdminShapeFactory.polygon(local, edgeModes, closingBezier)
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
                val snapped = snapToElevator(d, floor.number, p)
                it.copy(elevators = it.elevators + Elevator(nextId(it.elevators.map { e -> e.id }), snapped))
            }
            AdminTool.ESCALATOR_UP, AdminTool.ESCALATOR_DOWN -> placeEscalator(d, floor, tool, p)
            AdminTool.DELETE -> delete(d, floor, p, radius)
            else -> Unit
        }
    }

    /** Elevators do not shift horizontally, so a new one snaps exactly onto a nearby lift of another floor. */
    private fun snapToElevator(d: Mall, floorNumber: Int, p: Point): Point {
        val radius = d.size.x * 0.02
        return d.floors.asSequence()
            .filter { it.number != floorNumber }
            .flatMap { it.elevators.asSequence() }
            .map { it.coordinates }
            .minByOrNull { pointDistanceSquared(it, p) }
            ?.takeIf { pointDistanceSquared(it, p) <= radius * radius }
            ?: p
    }

    /** Two-step placement: entry on the current floor, then the exit on the floor the escalator leads to. */
    private fun placeEscalator(d: Mall, floor: Floor, tool: AdminTool, p: Point) {
        val direction = if (tool == AdminTool.ESCALATOR_UP) EscalatorDirection.UP else EscalatorDirection.DOWN
        val pending = pendingEscalator
        if (pending == null || pending.direction != direction || pending.floor == floor.number) {
            val target = floor.number + direction.step()
            if (d.getFloor(target) == null) return fail("There is no floor $target for this escalator to lead to")
            pendingEscalator = PendingEscalator(floor.number, p, direction)
            ok("Entry placed. Switch to floor $target and click the exit point.")
            return
        }
        val targetNumber = pending.floor + direction.step()
        if (floor.number != targetNumber) {
            return fail("Switch to floor $targetNumber and click the exit point")
        }
        editFloor(pending.floor) {
            val id = nextId(it.escalators.map { e -> e.id })
            it.copy(escalators = it.escalators + Escalator(id, pending.entry, direction, exitCoordinates = p))
        }
        pendingEscalator = null
        ok("Escalator added")
    }

    private fun EscalatorDirection.step() = if (this == EscalatorDirection.UP) 1 else -1

    private data class PendingEscalator(val floor: Int, val entry: Point, val direction: EscalatorDirection)

    private data class EditableShape(val target: ShapeTarget, val path: Path2D)

    private sealed interface ShapeTarget {
        object MallOutline : ShapeTarget
        object FloorOutline : ShapeTarget
        data class StoreArea(val storeId: Long) : ShapeTarget
    }

    private data class VertexDrag(
        val target: ShapeTarget,
        val path: Path2D,
        val editablePoint: AdminShapeFactory.EditablePoint
    )

    private fun editableShapes(): List<EditableShape> {
        val mall = draft ?: return emptyList()
        val floor = mall.getFloor(mapState.currentFloorNumber) ?: return emptyList()
        val detailedView = mall.id == mapState.focusedMall?.id && mapState.isDetailedView
        return buildList {
            if (detailedView) {
                add(EditableShape(ShapeTarget.FloorOutline, floor.box))
                floor.stores.forEach { add(EditableShape(ShapeTarget.StoreArea(it.Instanceid), it.area)) }
            } else {
                val outline = mall.outline
                if (outline != null) add(EditableShape(ShapeTarget.MallOutline, outline))
                else add(EditableShape(ShapeTarget.FloorOutline, floor.box))
            }
        }
    }

    private fun updateShapePath(target: ShapeTarget, path: Path2D) {
        val mall = draft ?: return
        when (target) {
            ShapeTarget.MallOutline -> edit { it.copy(outline = path) }
            ShapeTarget.FloorOutline -> {
                val floor = currentFloor(mall) ?: return
                editFloor(floor.number) { it.copy(box = path) }
            }
            is ShapeTarget.StoreArea -> {
                val floor = currentFloor(mall) ?: return
                editFloor(floor.number) {
                    it.copy(stores = it.stores.map { store ->
                        if (store.Instanceid == target.storeId) store.copy(area = path) else store
                    })
                }
            }
        }
    }

    private fun constrainDrawingPoint(start: GeoPoint, target: GeoPoint, shift: Boolean, ctrl: Boolean): GeoPoint {
        val horizontal = target.copy(latitude = start.latitude)
        val vertical = target.copy(longitude = start.longitude)
        return when {
            shift && ctrl -> if (distanceSquared(target, horizontal) <= distanceSquared(target, vertical)) horizontal else vertical
            shift -> horizontal
            ctrl -> vertical
            else -> target
        }
    }

    private fun constrainDraggedVertex(
        vertices: List<Point>,
        index: Int,
        target: Point,
        shift: Boolean,
        ctrl: Boolean
    ): Point {
        if (!shift && !ctrl || vertices.size < 2) return target
        val previous = vertices[(index - 1 + vertices.size) % vertices.size]
        val next = vertices[(index + 1) % vertices.size]
        val horizontal = listOf(
            target.copy(y = previous.y),
            target.copy(y = next.y)
        ).minBy { pointDistanceSquared(target, it) }
        val vertical = listOf(
            target.copy(x = previous.x),
            target.copy(x = next.x)
        ).minBy { pointDistanceSquared(target, it) }
        return when {
            shift && ctrl -> if (pointDistanceSquared(target, horizontal) <= pointDistanceSquared(target, vertical)) horizontal else vertical
            shift -> horizontal
            else -> vertical
        }
    }

    private fun distanceSquared(a: GeoPoint, b: GeoPoint): Double {
        val lat = a.latitude - b.latitude
        val lon = a.longitude - b.longitude
        return lat * lat + lon * lon
    }

    private fun pointDistanceSquared(a: Point, b: Point): Double {
        val x = a.x - b.x
        val y = a.y - b.y
        return x * x + y * y
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
