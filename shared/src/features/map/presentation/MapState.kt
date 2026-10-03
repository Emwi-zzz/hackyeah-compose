package features.map.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import core.geometry.GeoPoint
import core.geometry.WebMercatorProjection
import features.map.data.OpenTileSources
import features.map.domain.MapViewport
import features.map.domain.TileRepository
import features.map.domain.TileSource
import features.places.domain.Place
import features.rendering.domain.LayerRegistry
import features.rendering.domain.RenderPipeline
import features.tools.domain.ToolController
import features.tools.domain.ToolMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MapState(
    val tileRepository: TileRepository,
    val layerRegistry: LayerRegistry,
    val toolController: ToolController,
    private val scope: CoroutineScope
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

    val renderPipeline = RenderPipeline(layersSupplier = { layerRegistry.layers })

    private var animationJob: Job? = null

    fun updateScreenSize(width: Float, height: Float) {
        if (width > 0f && height > 0f && (viewport.screenWidth != width || viewport.screenHeight != height)) {
            viewport = viewport.withSize(width, height)
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
    }

    fun zoomBy(delta: Double, focalScreenOffset: Offset? = null) {
        val oldZoom = viewport.zoom
        val newZoom = (oldZoom + delta).coerceIn(2.0, 19.0)
        if (newZoom == oldZoom) return

        if (focalScreenOffset != null && viewport.screenWidth > 0f && viewport.screenHeight > 0f) {
            // Keep the geo point under the mouse cursor fixed while zooming
            val geoUnderFocal = WebMercatorProjection.screenToGeo(
                screenOffset = focalScreenOffset,
                center = viewport.center,
                zoom = oldZoom,
                screenWidth = viewport.screenWidth,
                screenHeight = viewport.screenHeight
            )
            // Compute what center is needed so geoUnderFocal remains under focalScreenOffset
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
                // Smooth ease-in-out curve
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
        }
    }

    fun resetToKrakow() {
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

        if (toolController.activeMode == ToolMode.PAN) {
            // Check if clicking near a place marker
            // Markers are handled by POI selection
            toolController.onMapClick(geo)
        } else {
            toolController.onMapClick(geo)
        }
    }
}
