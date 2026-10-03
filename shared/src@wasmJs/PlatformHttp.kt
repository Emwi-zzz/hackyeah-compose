package core.network

actual object PlatformHttp {
    actual suspend fun getBytes(url: String, headers: Map<String, String>): ByteArray? {
        return null
    }

    actual suspend fun getText(url: String, headers: Map<String, String>): String? {
        return null
    }
}
