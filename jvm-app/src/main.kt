import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() {
    System.getenv("BACKEND_URL")?.let { core.network.BackendConfig.baseUrl = it }
    System.getProperty("backend.url")?.let { core.network.BackendConfig.baseUrl = it }
    application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Kraków Map - Interactive Vector & Raster Engine",
        state = rememberWindowState(width = 1280.dp, height = 820.dp)
    ) {
        Screen()
    }
}
}