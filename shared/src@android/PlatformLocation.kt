package core.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import core.geometry.GeoPoint

actual object PlatformLocation : LocationSource {
    private var context: Context? = null
    private var permissionRequester: (((Boolean) -> Unit) -> Unit)? = null
    private var listener: LocationListener? = null

    /** Called by the activity: the app context plus a way to show the runtime permission dialog. */
    fun init(context: Context, requestPermission: ((Boolean) -> Unit) -> Unit) {
        this.context = context.applicationContext
        permissionRequester = requestPermission
    }

    actual override val isSupported: Boolean get() = context != null

    @SuppressLint("MissingPermission")
    actual override fun start(onFix: (LocationFix) -> Unit, onError: (String) -> Unit) {
        val ctx = context ?: return onError("Location is not initialised")
        if (!hasPermission(ctx)) {
            val requester = permissionRequester ?: return onError("Location permission is missing")
            requester { granted -> if (granted) start(onFix, onError) else onError("Location permission denied") }
            return
        }
        stop()
        val manager = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        fun emit(location: Location) =
            onFix(LocationFix(GeoPoint(location.latitude, location.longitude), location.accuracy.toDouble()))

        // Implements every method: on Android < 11 they are not default methods yet
        val newListener = object : LocationListener {
            override fun onLocationChanged(location: Location) = emit(location)
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        if (providers.isEmpty()) return onError("Turn on location services")
        providers.forEach { manager.requestLocationUpdates(it, 1000L, 2f, newListener, Looper.getMainLooper()) }
        listener = newListener
        providers.mapNotNull { manager.getLastKnownLocation(it) }.maxByOrNull { it.time }?.let(::emit)
    }

    actual override fun stop() {
        val ctx = context ?: return
        listener?.let { (ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager).removeUpdates(it) }
        listener = null
    }

    private fun hasPermission(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
}
