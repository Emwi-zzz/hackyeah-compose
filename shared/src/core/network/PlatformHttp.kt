package core.network

class HttpResult(val status: Int, val body: String)

expect object PlatformHttp {
    suspend fun getBytes(url: String, headers: Map<String, String> = emptyMap()): ByteArray?
    suspend fun getText(url: String, headers: Map<String, String> = emptyMap()): String?

    /** Generic request (POST/PUT/DELETE...). Returns null when the server is unreachable; non-2xx statuses are returned as results. */
    suspend fun request(
        method: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: String? = null
    ): HttpResult?
}
