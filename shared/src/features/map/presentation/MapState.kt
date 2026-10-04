package features.map.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import core.geometry.GeoMath
import core.geometry.GeoPoint
import core.geometry.PolylineMath
import core.geometry.PolylineProgress
import core.geometry.WebMercatorProjection
import core.location.LocationSource
import core.location.PlatformLocation
import features.indoor.data.RemoteIndoorRoutingRepository
import features.indoor.domain.*
import features.indoor.presentation.IndoorBuildingLayer
import features.map.data.OpenTileSources
import features.map.domain.MapViewport
import features.map.domain.TileRepository
import features.map.domain.TileSource
import features.places.domain.Place
import features.rendering.domain.LayerRegistry
import features.rendering.domain.RenderPipeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sklepsearch.*
import kotlin.math.ln
import kotlin.random.Random

class MapState(
    val tileRepository: TileRepository,
    val layerRegistry: LayerRegistry,
    private val scope: CoroutineScope,
    val indoorRoutingRepository: IndoorRoutingRepository = RemoteIndoorRoutingRepository(),
    private val locationSource: LocationSource = PlatformLocation
) {
    var viewport by mutableStateOf(
        MapViewport(
            center = GeoPoint.KRAKOW_CENTER,
            zoom = 14.0,
            screenWidth = 800f,
            screenHeight = 600f
        )
    )
    val isDetailedView: Boolean get() = viewport.zoom >= IndoorBuildingLayer.DETAIL_ZOOM_THRESHOLD

    var activeTileSource by mutableStateOf(OpenTileSources.DEFAULT)
    var selectedPlace by mutableStateOf<Place?>(null)
    var isLayerManagerOpen by mutableStateOf(false)
    var isTileSelectorOpen by mutableStateOf(false)
    var isSearchOpen by mutableStateOf(false)
    var isDebugStatsOpen by mutableStateOf(false)

    // Indoor buildings are loaded from the backend (see loadIndoorData)
    var malls by mutableStateOf<List<Mall>>(emptyList())
        private set
    private var navLocationsByMall by mutableStateOf<Map<Long, List<NavLocation>>>(emptyMap())
    var indoorDataError by mutableStateOf<String?>(null)
        private set

    // Focus state: one active building is in focus based on scale and position, or user click
    var focusedMall by mutableStateOf<Mall?>(null)
    val activeMall: Mall? get() = focusedMall
    var currentFloorNumber by mutableStateOf(0)
    var selectedStore by mutableStateOf<Store?>(null)
    var userLockedMallId by mutableStateOf<Long?>(null)

    // Indoor Multi-floor Routing Engine & State
    var activeIndoorRoute by mutableStateOf<IndoorRoute?>(null)
    var isIndoorRouteLoading by mutableStateOf(false)
    var indoorRouteError by mutableStateOf<String?>(null)
    var indoorRouteStartLocation by mutableStateOf<NavLocation?>(null)
    var indoorRouteEndLocation by mutableStateOf<NavLocation?>(null)
    var isIndoorNavigationOpen by mutableStateOf(false)
    var isAccessibleRouting by mutableStateOf(false)

    // Outdoor navigation: user position (GPS on phones, a map click on desktop) and the walk to the mall
    var userLocation by mutableStateOf<GeoPoint?>(null)
        private set
    var isPickingUserLocation by mutableStateOf(false)
    var activeApproach by mutableStateOf<ApproachRoute?>(null)
        private set
    var outdoorProgress by mutableStateOf<PolylineProgress?>(null)
        private set
    var rerouteCount by mutableStateOf(0)
        private set

    val userLocationNav: NavLocation?
        get() = userLocation?.let {
            NavLocation(USER_LOCATION_ID, "My location", NavLocationType.USER_LOCATION, 0, Point(0.0, 0.0), "Street position")
        }

    val renderPipeline = RenderPipeline(layersSupplier = { layerRegistry.layers })

    private var animationJob: Job? = null

    init {
        loadIndoorData()
    }

    /** Loads malls and their navigation targets from the backend. Safe to call again to retry. */
    fun loadIndoorData() {
        scope.launch { reloadIndoorData() }
    }

    /** Reloads indoor data and keeps the focus on the same mall (matched by id). */
    suspend fun reloadIndoorData(): Boolean {
        val result = indoorRoutingRepository.getMalls()
        result.onSuccess { loaded ->
            indoorDataError = null
            malls = loaded
            val focusedId = focusedMall?.id
            focusedMall = loaded.find { it.id == focusedId } ?: loaded.firstOrNull()
            val locations = mutableMapOf<Long, List<NavLocation>>()
            for (mall in loaded) {
                indoorRoutingRepository.getNavLocations(mall.id).onSuccess { locations[mall.id] = it }
            }
            navLocationsByMall = locations
        }.onFailure {
            indoorDataError = it.message ?: "Indoor backend is unavailable"
        }
        return result.isSuccess
    }

    /** Admin tools hook: return true to consume a map click. */
    var mapClickInterceptor: ((GeoPoint) -> Boolean)? = null

    fun geoAtScreen(offset: Offset): GeoPoint = WebMercatorProjection.screenToGeo(
        screenOffset = offset,
        center = viewport.center,
        zoom = viewport.zoom,
        screenWidth = viewport.screenWidth,
        screenHeight = viewport.screenHeight
    )

    fun screenAtGeo(point: GeoPoint): Offset = WebMercatorProjection.geoToScreen(
        geo = point,
        center = viewport.center,
        zoom = viewport.zoom,
        screenWidth = viewport.screenWidth,
        screenHeight = viewport.screenHeight
    )

    /** Replaces (or adds) a mall in the list, e.g. an admin draft, and focuses it. */
    fun replaceMall(mall: Mall) {
        malls = if (malls.any { it.id == mall.id }) malls.map { if (it.id == mall.id) mall else it } else malls + mall
        focusedMall = mall
        userLockedMallId = mall.id
    }
    /**
     * Determines whether the focused mall is on screen and scaled to detailed indoor view.
     */
    fun isMallOnScreen(): Boolean {
        val mall = focusedMall ?: return false
        val visibleBounds = viewport.visibleBounds()
        val mallBounds = mall.getBoundingBox()
        return visibleBounds.intersects(mallBounds) && viewport.zoom >= IndoorBuildingLayer.DETAIL_ZOOM_THRESHOLD
    }

    /**
     * Updates building focus based on current map position and viewport scale.
     */
    fun updateFocusBasedOnScaleAndPosition() {
        val visibleBounds = viewport.visibleBounds()
        val visibleMalls = malls.filter { visibleBounds.intersects(it.getBoundingBox()) }

        if (visibleMalls.isEmpty()) return

        // If user manually refocused a mall and it remains visible, prioritize it
        val lockedId = userLockedMallId
        if (lockedId != null) {
            val locked = visibleMalls.find { it.id == lockedId }
            if (locked != null) {
                if (focusedMall?.id != locked.id) {
                    focusedMall = locked
                }
                return
            }
        }

        // Focus on the building closest to the current viewport center
        val center = viewport.center
        val closest = visibleMalls.minByOrNull {
            GeoMath.haversineDistanceMeters(center, it.getBoundingBox().center)
        }

        if (closest != null && closest.id != focusedMall?.id) {
            focusedMall = closest
            currentFloorNumber = maxOf(0, closest.minFloor)
            selectedStore = null
        }
    }

    fun selectFloor(floorNumber: Int) {
        currentFloorNumber = floorNumber
        selectedStore = null
    }

    fun jumpTo(target: GeoPoint, targetZoom: Double = viewport.zoom) {
        animationJob?.cancel()
        viewport = viewport.copy(center = target, zoom = targetZoom)
        updateFocusBasedOnScaleAndPosition()
    }

    fun refocusOnMall(mall: Mall, animate: Boolean = true) {
        val mallChanged = focusedMall?.id != mall.id
        if (mallChanged) {
            clearIndoorRoute()
        }
        userLockedMallId = mall.id
        focusedMall = mall
        currentFloorNumber = maxOf(0, mall.minFloor)
        selectedStore = null
        val targetZoom = if (viewport.zoom < IndoorBuildingLayer.DETAIL_ZOOM_THRESHOLD) 16.5 else viewport.zoom
        if (animate) {
            flyTo(mall.getBoundingBox().center, targetZoom)
        } else {
            jumpTo(mall.getBoundingBox().center, targetZoom)
        }
    }

    fun flyToMall(mall: Mall) {
        refocusOnMall(mall)
    }

    fun getAvailableNavLocations(): List<NavLocation> {
        val mall = focusedMall ?: return emptyList()
        return navLocationsByMall[mall.id] ?: emptyList()
    }

    fun requestIndoorRoute(start: NavLocation, end: NavLocation) {
        if (start.type == NavLocationType.USER_LOCATION) {
            requestApproachRoute(end)
            return
        }
        val mall = focusedMall ?: return
        indoorRouteStartLocation = start
        indoorRouteEndLocation = end
        isIndoorRouteLoading = true
        indoorRouteError = null
        activeApproach = null
        outdoorProgress = null
        scope.launch {
            val result = indoorRoutingRepository.calculateRoute(mall.id, start, end, isAccessibleRouting)
            isIndoorRouteLoading = false
            result.onSuccess { route ->
                activeIndoorRoute = route
                selectFloor(start.floorNumber)
            }.onFailure { err ->
                activeIndoorRoute = null
                indoorRouteError = err.message ?: "Failed to calculate route"
            }
        }
    }

    /**
     * Street route from [userLocation] to the best entrance of the focused mall, continued indoors to [end].
     * Calculated once; afterwards [updateUserLocation] only re-routes when the user leaves the path.
     */
    fun requestApproachRoute(end: NavLocation, fitToScreen: Boolean = true, mallId: Long? = focusedMall?.id) {
        mallId ?: return
        val from = userLocation ?: return
        indoorRouteStartLocation = userLocationNav
        indoorRouteEndLocation = end
        isIndoorRouteLoading = true
        indoorRouteError = null
        scope.launch {
            val result = indoorRoutingRepository.calculateApproach(mallId, from, end, isAccessibleRouting)
            isIndoorRouteLoading = false
            result.onSuccess { approach ->
                userLockedMallId = mallId
                activeApproach = approach
                activeIndoorRoute = approach.indoor
                outdoorProgress = userLocation?.let { PolylineMath.locate(approach.outdoor.points, it) }
                selectFloor(approach.entrance.floorNumber)
                if (fitToScreen) fitToRoute(approach.outdoor.points)
            }.onFailure { err ->
                activeApproach = null
                activeIndoorRoute = null
                outdoorProgress = null
                indoorRouteError = err.message ?: "Failed to calculate route"
            }
        }
    }

    /**
     * New position from GPS or a map click. Progress along the current route is computed locally;
     * the backend is only asked again when the user is more than [OFF_ROUTE_METERS] away from the path.
     */
    fun updateUserLocation(location: GeoPoint, accuracyMeters: Double = 0.0) {
        userLocation = location
        isPickingUserLocation = false
        val approach = activeApproach ?: return
        val progress = PolylineMath.locate(approach.outdoor.points, location)
        outdoorProgress = progress
        val end = indoorRouteEndLocation
        // A vague fix (e.g. Wi-Fi only, ±100 m) moves the dot but is not trusted to decide the user left the path
        val reliable = accuracyMeters <= MAX_REROUTE_ACCURACY_METERS
        if (reliable && progress.offRouteMeters > OFF_ROUTE_METERS && end != null && !isIndoorRouteLoading) {
            rerouteCount++
            requestApproachRoute(end, fitToScreen = false, mallId = approach.mallId)
        }
    }

    // Real device position (browser / Android / iOS); off by default so the user decides when to share it
    val isGpsSupported: Boolean get() = locationSource.isSupported
    var isGpsOn by mutableStateOf(false)
        private set
    var gpsAccuracyMeters by mutableStateOf<Double?>(null)
        private set
    var locationError by mutableStateOf<String?>(null)
        private set

    fun toggleGps() {
        if (isGpsOn) {
            locationSource.stop()
            isGpsOn = false
            gpsAccuracyMeters = null
            return
        }
        stopWalkSimulation()
        locationError = null
        isGpsOn = true
        var firstFix = true
        locationSource.start(
            onFix = { fix ->
                if (isGpsOn) {
                    gpsAccuracyMeters = fix.accuracyMeters
                    locationError = null
                    updateUserLocation(fix.point, fix.accuracyMeters)
                    if (firstFix) {
                        firstFix = false
                        if (activeApproach == null) flyTo(fix.point, maxOf(viewport.zoom, 16.0))
                    }
                }
            },
            onError = { message ->
                locationError = message
                if (userLocation == null || gpsAccuracyMeters == null) {
                    locationSource.stop()
                    isGpsOn = false
                }
            }
        )
    }

    // Walk simulator: moves the user along the street route as fake GPS fixes, for testing without a phone
    var isSimulatingWalk by mutableStateOf(false)
        private set
    private var simulationJob: Job? = null

    fun toggleWalkSimulation(speedup: Double = 8.0) {
        if (isSimulatingWalk) {
            stopWalkSimulation()
            return
        }
        if (activeApproach == null) return
        if (isGpsOn) toggleGps() // simulated fixes would fight with real ones
        isSimulatingWalk = true
        simulationJob = scope.launch {
            while (isSimulatingWalk) {
                delay(SIMULATION_TICK_MS)
                if (!simulateStep(WALKING_SPEED * speedup * SIMULATION_TICK_MS / 1000.0)) break
            }
            isSimulatingWalk = false
        }
    }

    fun stopWalkSimulation() {
        isSimulatingWalk = false
        simulationJob?.cancel()
        simulationJob = null
    }

    /**
     * Advances the simulated user [meters] along the current street route, with a few meters of GPS-like jitter.
     * Returns false once the entrance is reached (or there is no route).
     */
    fun simulateStep(meters: Double, jitterMeters: Double = 4.0): Boolean {
        val approach = activeApproach ?: return false
        if (isIndoorRouteLoading) return true // waiting for a re-route
        val line = approach.outdoor.points
        val along = (outdoorProgress?.alongMeters ?: 0.0) + meters
        val total = outdoorProgress?.totalMeters ?: PolylineMath.locate(line, line.first()).totalMeters
        if (along >= total) {
            finishApproachAtEntrance(approach)
            return false
        }
        val onLine = PolylineMath.pointAt(line, along)
        val heading = PolylineMath.heading(PolylineMath.pointAt(line, along - 3.0), PolylineMath.pointAt(line, along + 3.0))
        val jitter = (Random.nextDouble() * 2 - 1) * jitterMeters
        val fix = PolylineMath.offsetSideways(onLine, heading, jitter)
        updateUserLocation(fix)
        viewport = viewport.withCenter(fix)
        return true
    }

    private fun finishApproachAtEntrance(approach: ApproachRoute) {
        val entrancePoint = approach.outdoor.points.last()
        userLocation = entrancePoint
        isPickingUserLocation = false
        userLockedMallId = approach.mallId
        malls.find { it.id == approach.mallId }?.let { focusedMall = it }

        val destination = indoorRouteEndLocation
        indoorRouteStartLocation = approach.entrance
        activeApproach = null
        outdoorProgress = null
        activeIndoorRoute = approach.indoor
        selectFloor(approach.entrance.floorNumber)

        if (approach.indoor == null && destination != null) {
            requestIndoorRoute(approach.entrance, destination)
        }

        val target = focusedMall?.getBoundingBox()?.center ?: entrancePoint
        val zoom = maxOf(viewport.zoom, IndoorBuildingLayer.DETAIL_ZOOM_THRESHOLD + 0.5).coerceAtMost(17.0)
        val shiftX = viewport.screenWidth * 0.16f
        val shiftY = viewport.screenHeight * 0.12f
        val cameraCenter = WebMercatorProjection.screenToGeo(
            screenOffset = Offset(viewport.screenWidth / 2f - shiftX, viewport.screenHeight / 2f - shiftY),
            center = target,
            zoom = zoom,
            screenWidth = viewport.screenWidth,
            screenHeight = viewport.screenHeight
        )
        flyTo(cameraCenter, zoom)
    }

    /** Jumps the user [meters] to the side of the route, which must trigger exactly one re-route. */
    fun simulateWrongTurn(meters: Double = 60.0) {
        val approach = activeApproach ?: return
        val line = approach.outdoor.points
        val along = outdoorProgress?.alongMeters ?: 0.0
        val here = PolylineMath.pointAt(line, along)
        val heading = PolylineMath.heading(PolylineMath.pointAt(line, along - 3.0), PolylineMath.pointAt(line, along + 3.0))
        updateUserLocation(PolylineMath.offsetSideways(here, heading, meters))
    }

    fun clearUserLocation() {
        stopWalkSimulation()
        if (isGpsOn) toggleGps()
        userLocation = null
        isPickingUserLocation = false
        if (indoorRouteStartLocation?.type == NavLocationType.USER_LOCATION) clearIndoorRoute()
    }

    private fun fitToRoute(points: List<GeoPoint>) {
        if (points.size < 2 || viewport.screenWidth <= 0f) return
        val xs = points.map { WebMercatorProjection.toWorldX(it.longitude) }
        val ys = points.map { WebMercatorProjection.toWorldY(it.latitude) }
        val spanX = (xs.max() - xs.min()).coerceAtLeast(1e-9)
        val spanY = (ys.max() - ys.min()).coerceAtLeast(1e-9)
        val scale = minOf(viewport.screenWidth * 0.7 / spanX, viewport.screenHeight * 0.55 / spanY)
        val zoom = (ln(scale / WebMercatorProjection.TILE_SIZE) / ln(2.0)).coerceIn(12.0, 17.0)
        val center = GeoPoint(
            WebMercatorProjection.toGeoLatitude((ys.max() + ys.min()) / 2),
            WebMercatorProjection.toGeoLongitude((xs.max() + xs.min()) / 2)
        )
        flyTo(center, zoom)
    }

    /** Switches between any route and step-free routes (accessible elevators only), recalculating the open route. */
    fun toggleAccessibleRouting() {
        isAccessibleRouting = !isAccessibleRouting
        val start = indoorRouteStartLocation
        val end = indoorRouteEndLocation
        val approachMall = activeApproach?.mallId
        when {
            start?.type == NavLocationType.USER_LOCATION && end != null ->
                requestApproachRoute(end, fitToScreen = false, mallId = approachMall ?: focusedMall?.id)
            start != null && end != null -> requestIndoorRoute(start, end)
        }
    }

    fun clearIndoorRoute() {
        stopWalkSimulation()
        activeIndoorRoute = null
        activeApproach = null
        outdoorProgress = null
        indoorRouteStartLocation = null
        indoorRouteEndLocation = null
        indoorRouteError = null
    }

    fun swapIndoorRouteEndpoints() {
        val s = indoorRouteStartLocation
        val e = indoorRouteEndLocation
        // Routes only run from the street into the mall, not back out to a street position
        if (s?.type == NavLocationType.USER_LOCATION || e?.type == NavLocationType.USER_LOCATION) return
        if (s != null && e != null) {
            requestIndoorRoute(start = e, end = s)
        } else {
            indoorRouteStartLocation = e
            indoorRouteEndLocation = s
        }
    }

    fun startRouteToStore(store: Store) {
        val locations = getAvailableNavLocations()
        val dest = locations.find { it.id == "store_${store.Instanceid}" } ?: return
        val outside = userLocation?.let { loc -> focusedMall?.getBoundingBox()?.contains(loc) == false } == true
        val defaultStart = (if (outside) userLocationNav else null)
            ?: locations.firstOrNull { it.type == NavLocationType.EXIT }
            ?: locations.firstOrNull() ?: return
        isIndoorNavigationOpen = true
        requestIndoorRoute(start = defaultStart, end = dest)
    }

    fun updateScreenSize(width: Float, height: Float) {
        if (width > 0f && height > 0f && (viewport.screenWidth != width || viewport.screenHeight != height)) {
            viewport = viewport.withSize(width, height)
            updateFocusBasedOnScaleAndPosition()
        }
    }

    fun panBy(dx: Float, dy: Float) {
        if (viewport.screenWidth <= 0f || viewport.screenHeight <= 0f) return
        val currentCenterScreen = Offset(viewport.screenWidth / 2f, viewport.screenHeight / 2f)
        val newCenterScreen = Offset(currentCenterScreen.x - dx, currentCenterScreen.y - dy)
        val newCenterGeo = WebMercatorProjection.screenToGeo(
            screenOffset = newCenterScreen,
            center = viewport.center,
            zoom = viewport.zoom,
            screenWidth = viewport.screenWidth,
            screenHeight = viewport.screenHeight
        )
        viewport = viewport.withCenter(newCenterGeo)
        userLockedMallId = null
        updateFocusBasedOnScaleAndPosition()
    }

    fun zoomBy(delta: Double, focalScreenOffset: Offset? = null) {
        val oldZoom = viewport.zoom
        val newZoom = (oldZoom + delta).coerceIn(2.0, 19.0)
        if (newZoom == oldZoom) return

        if (focalScreenOffset != null && viewport.screenWidth > 0f && viewport.screenHeight > 0f) {
            val geoUnderFocal = WebMercatorProjection.screenToGeo(
                screenOffset = focalScreenOffset,
                center = viewport.center,
                zoom = oldZoom,
                screenWidth = viewport.screenWidth,
                screenHeight = viewport.screenHeight
            )
            val scaleNew = WebMercatorProjection.worldPixelSize(newZoom)
            val focalWorldX = WebMercatorProjection.toWorldX(geoUnderFocal.longitude)
            val focalWorldY = WebMercatorProjection.toWorldY(geoUnderFocal.latitude)

            val newCenterWorldX = focalWorldX - (focalScreenOffset.x - viewport.screenWidth / 2f) / scaleNew
            val newCenterWorldY = focalWorldY - (focalScreenOffset.y - viewport.screenHeight / 2f) / scaleNew

            val newCenter = GeoPoint(
                latitude = WebMercatorProjection.toGeoLatitude(newCenterWorldY),
                longitude = WebMercatorProjection.toGeoLongitude(newCenterWorldX)
            )
            viewport = viewport.copy(center = newCenter, zoom = newZoom)
        } else {
            viewport = viewport.withZoom(newZoom)
        }
        updateFocusBasedOnScaleAndPosition()
    }

    fun zoomIn() {
        zoomBy(1.0)
    }

    fun zoomOut() {
        zoomBy(-1.0)
    }

    fun flyTo(target: GeoPoint, targetZoom: Double = 14.5) {
        animationJob?.cancel()
        animationJob = scope.launch {
            val startCenter = viewport.center
            val startZoom = viewport.zoom
            val steps = 24
            for (i in 1..steps) {
                val t = i.toFloat() / steps
                val ease = t * t * (3f - 2f * t)
                val currentLat = startCenter.latitude + (target.latitude - startCenter.latitude) * ease
                val currentLon = startCenter.longitude + (target.longitude - startCenter.longitude) * ease
                val currentZoom = startZoom + (targetZoom - startZoom) * ease

                viewport = viewport.copy(
                    center = GeoPoint(currentLat, currentLon),
                    zoom = currentZoom
                )
                delay(16)
            }
            viewport = viewport.copy(center = target, zoom = targetZoom)
            updateFocusBasedOnScaleAndPosition()
        }
    }

    fun resetToKrakow() {
        userLockedMallId = null
        flyTo(GeoPoint.KRAKOW_CENTER, 14.0)
    }

    fun handleMapClick(screenOffset: Offset) {
        val geo = WebMercatorProjection.screenToGeo(
            screenOffset = screenOffset,
            center = viewport.center,
            zoom = viewport.zoom,
            screenWidth = viewport.screenWidth,
            screenHeight = viewport.screenHeight
        )

        if (isPickingUserLocation) {
            updateUserLocation(geo)
            return
        }
        if (mapClickInterceptor?.invoke(geo) == true) return

        // Check if any building was clicked to refocus or select a store
        val clickedMall = malls.find { mall ->
            val pt = mall.geoToPoint(geo)
            val inBounds = pt.x in -20.0..(mall.size.x + 20.0) && pt.y in -20.0..(mall.size.y + 20.0)
            if (!inBounds) return@find false
            val floorBox = mall.outline ?: mall.floors.firstOrNull()?.box
            if (floorBox != null && floorBox.contains(pt)) true else mall.getBoundingBox().contains(geo)
        } ?: malls.find { it.getBoundingBox().contains(geo) }

        if (clickedMall != null) {
            if (clickedMall.id != focusedMall?.id) {
                // Refocus on clicked building!
                refocusOnMall(clickedMall)
                return
            } else {
                // Clicked on already focused building: check for store hit or zoom in
                if (viewport.zoom >= IndoorBuildingLayer.DETAIL_ZOOM_THRESHOLD) {
                    val pt = clickedMall.geoToPoint(geo)
                    val store = clickedMall.findStoreAt(pt, currentFloorNumber)
                    selectedStore = store
                    if (store != null) {
                        selectedPlace = null
                        return
                    }
                } else {
                    // Zoom into detailed view of this building
                    flyTo(clickedMall.getBoundingBox().center, 16.5)
                    return
                }
            }
        }

        selectedStore = null
    }

    companion object {
        const val USER_LOCATION_ID = "user_location"
        /** GPS is often 10-20 m off between buildings, so only re-route beyond this distance from the path. */
        const val OFF_ROUTE_METERS = 35.0
        const val MAX_REROUTE_ACCURACY_METERS = 50.0
        const val WALKING_SPEED = 1.3
        const val SIMULATION_TICK_MS = 200L
    }
}
