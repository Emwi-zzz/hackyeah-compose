package core.location

import core.geometry.GeoPoint

@JsFun("() => typeof navigator !== 'undefined' && 'geolocation' in navigator")
private external fun hasGeolocation(): Boolean

// Browsers only allow this on https or localhost, and ask the user for permission on the first call
@JsFun(
    "(onFix, onError) => navigator.geolocation.watchPosition(" +
        "p => onFix(p.coords.latitude, p.coords.longitude, p.coords.accuracy), " +
        "e => onError(e.message || ('Location error ' + e.code)), " +
        "{ enableHighAccuracy: true, maximumAge: 2000, timeout: 20000 })"
)
private external fun watchPosition(onFix: (Double, Double, Double) -> Unit, onError: (String) -> Unit): Int

@JsFun("(id) => navigator.geolocation.clearWatch(id)")
private external fun clearWatch(id: Int)

actual object PlatformLocation : LocationSource {
    actual override val isSupported: Boolean get() = hasGeolocation()

    private var watchId: Int? = null

    actual override fun start(onFix: (LocationFix) -> Unit, onError: (String) -> Unit) {
        if (!hasGeolocation()) {
            onError("This browser has no location support")
            return
        }
        stop()
        watchId = watchPosition(
            { lat, lon, accuracy -> onFix(LocationFix(GeoPoint(lat, lon), accuracy)) },
            { message -> onError(message) }
        )
    }

    actual override fun stop() {
        watchId?.let { clearWatch(it) }
        watchId = null
    }
}
