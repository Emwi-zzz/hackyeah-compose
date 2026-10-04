package core.location

actual object PlatformLocation : LocationSource {
    actual override val isSupported: Boolean = false

    actual override fun start(onFix: (LocationFix) -> Unit, onError: (String) -> Unit) {
        onError("No GPS on desktop: use the web or Android app, or place yourself on the map")
    }

    actual override fun stop() = Unit
}
