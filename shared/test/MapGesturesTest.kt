package test

import features.map.presentation.wheelZoomDelta
import kotlin.test.Test
import kotlin.test.assertEquals

class MapGesturesTest {
    @Test
    fun wheelZoomDeltaNormalizesBrowserPixelDeltasAndPreservesDesktopTicks() {
        assertEquals(-0.25, wheelZoomDelta(1f))
        assertEquals(-0.25, wheelZoomDelta(100f))
        assertEquals(0.25, wheelZoomDelta(-100f))
    }
}
