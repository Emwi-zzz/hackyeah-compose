package core.location

import core.geometry.GeoPoint

data class LocationFix(val point: GeoPoint, val accuracyMeters: Double)

/** A stream of device positions. */
interface LocationSource {
    val isSupported: Boolean

    /** Starts watching; callbacks arrive on the platform's main thread. */
    fun start(onFix: (LocationFix) -> Unit, onError: (String) -> Unit)

    fun stop()
}

/**
 * Device position: browser Geolocation API (wasm), LocationManager (Android), CoreLocation (iOS).
 * Plain desktop JVM has no location source, so [isSupported] is false there.
 */
expect object PlatformLocation : LocationSource
