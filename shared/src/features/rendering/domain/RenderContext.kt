package features.rendering.domain

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import core.geometry.BoundingBox
import core.geometry.GeoPoint
import core.geometry.WebMercatorProjection
import features.map.domain.MapViewport
import features.map.domain.TileRepository
import features.map.domain.TileSource

class RenderContext(
    val drawScope: DrawScope,
    val viewport: MapViewport,
    val tileRepository: TileRepository,
    val activeTileSource: TileSource,
    val frameTimeNanos: Long = 0L
) {
    val visibleBounds: BoundingBox = viewport.visibleBounds()

    fun geoToScreen(point: GeoPoint): Offset {
        return WebMercatorProjection.geoToScreen(
            geo = point,
            center = viewport.center,
            zoom = viewport.zoom,
            screenWidth = viewport.screenWidth,
            screenHeight = viewport.screenHeight
        )
    }

    fun screenToGeo(offset: Offset): GeoPoint {
        return WebMercatorProjection.screenToGeo(
            screenOffset = offset,
            center = viewport.center,
            zoom = viewport.zoom,
            screenWidth = viewport.screenWidth,
            screenHeight = viewport.screenHeight
        )
    }

    fun metersToPixels(meters: Double, atLatitude: Double = viewport.center.latitude): Float {
        val ppm = WebMercatorProjection.pixelsPerMeter(atLatitude, viewport.zoom)
        return (meters * ppm).toFloat()
    }

    fun isPointVisible(point: GeoPoint): Boolean {
        return visibleBounds.contains(point)
    }
}
