package core.network

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
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
}
