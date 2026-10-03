package core.network

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURLSession
import platform.Foundation.create
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.HTTPBody
import platform.Foundation.HTTPMethod
import platform.Foundation.setValue
import kotlin.coroutines.resume
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.posix.memcpy

actual object PlatformHttp {
    @OptIn(ExperimentalForeignApi::class)
    actual suspend fun getBytes(url: String, headers: Map<String, String>): ByteArray? {
        val nsUrl = NSURL.URLWithString(url) ?: return null
        val data = NSData.dataWithContentsOfURL(nsUrl) ?: return null
        val length = data.length.toInt()
        val byteArray = ByteArray(length)
        if (length > 0) {
            byteArray.usePinned { pinned ->
                memcpy(pinned.addressOf(0), data.bytes, data.length)
            }
        }
        return byteArray
    }

    actual suspend fun getText(url: String, headers: Map<String, String>): String? {
        return getBytes(url, headers)?.decodeToString()
    }

    @OptIn(ExperimentalForeignApi::class)
    actual suspend fun request(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String?
    ): HttpResult? = suspendCancellableCoroutine { cont ->
        val nsUrl = NSURL.URLWithString(url)
        if (nsUrl == null) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }
        val request = NSMutableURLRequest.requestWithURL(nsUrl)
        request.HTTPMethod = method
        headers.forEach { (k, v) -> request.setValue(v, forHTTPHeaderField = k) }
        if (body != null) {
            val bytes = body.encodeToByteArray()
            request.HTTPBody = if (bytes.isEmpty()) NSData() else bytes.usePinned { pinned ->
                NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
            }
        }
        val task = NSURLSession.sharedSession.dataTaskWithRequest(request) { data, response, error ->
            val status = (response as? NSHTTPURLResponse)?.statusCode?.toInt()
            if (error != null || status == null) {
                cont.resume(null)
            } else {
                val length = data?.length?.toInt() ?: 0
                val bytes = ByteArray(length)
                if (data != null && length > 0) {
                    bytes.usePinned { pinned -> memcpy(pinned.addressOf(0), data.bytes, data.length) }
                }
                cont.resume(HttpResult(status, bytes.decodeToString()))
            }
        }
        cont.invokeOnCancellation { task.cancel() }
        task.resume()
    }
}
