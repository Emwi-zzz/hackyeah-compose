package core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI

actual object PlatformHttp {
    actual suspend fun getBytes(url: String, headers: Map<String, String>): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URI(url).toURL().openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            if (conn.responseCode in 200..299) {
                conn.inputStream.use { it.readBytes() }
            } else null
        }.getOrNull()
    }

    actual suspend fun getText(url: String, headers: Map<String, String>): String? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URI(url).toURL().openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            if (conn.responseCode in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else null
        }.getOrNull()
    }

    actual suspend fun request(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String?
    ): HttpResult? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URI(url).toURL().openConnection() as HttpURLConnection
            conn.requestMethod = method
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            if (body != null) {
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            HttpResult(status, stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: "")
        }.getOrNull()
    }
}
