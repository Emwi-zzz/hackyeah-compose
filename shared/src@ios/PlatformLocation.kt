package core.location

import core.geometry.GeoPoint
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLLocationAccuracyBest
import platform.Foundation.NSError
import platform.darwin.NSObject

actual object PlatformLocation : LocationSource {
    private val manager = CLLocationManager()
    // CLLocationManager holds its delegate weakly, so keep a strong reference here
    private var delegate: Delegate? = null

    override val isSupported: Boolean get() = CLLocationManager.locationServicesEnabled()

    override fun start(onFix: (LocationFix) -> Unit, onError: (String) -> Unit) {
        val newDelegate = Delegate(onFix, onError)
        delegate = newDelegate
        manager.delegate = newDelegate
        manager.desiredAccuracy = kCLLocationAccuracyBest
        manager.distanceFilter = 2.0
        manager.requestWhenInUseAuthorization()
        manager.startUpdatingLocation()
    }

    override fun stop() {
        manager.stopUpdatingLocation()
        manager.delegate = null
        delegate = null
    }

    private class Delegate(
        private val onFix: (LocationFix) -> Unit,
        private val onError: (String) -> Unit
    ) : NSObject(), CLLocationManagerDelegateProtocol {

        @OptIn(ExperimentalForeignApi::class)
        override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
            val location = didUpdateLocations.lastOrNull() as? CLLocation ?: return
            location.coordinate.useContents {
                onFix(LocationFix(GeoPoint(latitude, longitude), location.horizontalAccuracy))
            }
        }

        override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
            onError(didFailWithError.localizedDescription)
        }
    }
}
