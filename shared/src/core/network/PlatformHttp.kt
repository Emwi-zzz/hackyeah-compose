package core.network

expect object PlatformHttp {
    suspend fun getBytes(url: String, headers: Map<String, String> = emptyMap()): ByteArray?
    suspend fun getText(url: String, headers: Map<String, String> = emptyMap()): String?
}
