package hello.world

import Screen
import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import core.location.PlatformLocation

class MainActivity : ComponentActivity() {
    private var onPermissionResult: ((Boolean) -> Unit)? = null

    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        onPermissionResult?.invoke(result.values.any { it })
        onPermissionResult = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PlatformLocation.init(this) { callback ->
            onPermissionResult = callback
            locationPermission.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
        setContent {
            Screen()
        }
    }

    override fun onDestroy() {
        PlatformLocation.stop()
        super.onDestroy()
    }
}
