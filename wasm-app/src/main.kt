import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.window.ComposeViewport
import core.network.PlatformHttp

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport {
        // Compose for web cannot use the system emoji font: preload a bundled one as fallback before drawing text
        val fontResolver = LocalFontFamilyResolver.current
        var fontsReady by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            PlatformHttp.getBytes("emoji.ttf")?.let { bytes ->
                runCatching { fontResolver.preload(FontFamily(Font("NotoColorEmoji", bytes))) }
            }
            fontsReady = true
        }
        if (fontsReady) Screen()
    }
}
