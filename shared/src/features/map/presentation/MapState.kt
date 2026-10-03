package features.map.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import core.geometry.GeoMath
import core.geometry.GeoPoint
import core.geometry.WebMercatorProjection
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

class MapState(
    val tileRepository: TileRepository,
    val layerRegistry: LayerRegistry,
    private val scope: CoroutineScope,
    val indoorRoutingRepository: IndoorRoutingRepository = RemoteIndoorRoutingRepository()
) {
    var viewport by mutableStateOf(
        MapViewport(
            center = GeoPoint.KRAKOW_CENTER,
            zoom = 14.0,
            screenWidth = 800f,
            screenHeight = 600f
        )
    )

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
        val mall = focusedMall ?: return
        indoorRouteStartLocation = start
        indoorRouteEndLocation = end
        isIndoorRouteLoading = true
        indoorRouteError = null
        scope.launch {
            val result = indoorRoutingRepository.calculateRoute(mall.id, start, end)
            isIndoorRouteLoading = false
            result.onSuccess { route ->
                activeIndoorRoute = route
                selectFloor(start.floorNumber)
            }.onFailure { err ->
                indoorRouteError = err.message ?: "Failed to calculate route"
            }
        }
    }

    fun clearIndoorRoute() {
        activeIndoorRoute = null
        indoorRouteStartLocation = null
        indoorRouteEndLocation = null
        indoorRouteError = null
    }

    fun swapIndoorRouteEndpoints() {
        val s = indoorRouteStartLocation
        val e = indoorRouteEndLocation
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
        val defaultStart = locations.firstOrNull { it.type == NavLocationType.EXIT }
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
}

