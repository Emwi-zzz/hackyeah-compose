package core.location

actual object PlatformLocation : LocationSource {
    override val isSupported: Boolean = false

    override fun start(onFix: (LocationFix) -> Unit, onError: (String) -> Unit) {
        onError("No GPS on desktop: use the web or Android app, or place yourself on the map")
    }

    override fun stop() = Unit
}
