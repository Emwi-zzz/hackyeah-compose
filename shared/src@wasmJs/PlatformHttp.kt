package core.network

import kotlinx.coroutines.await
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.js.JsAny
import kotlin.js.JsString
import kotlin.js.Promise
import kotlin.js.toJsString

// Both helpers resolve to null on any network/HTTP error or CORS failure.
@JsFun(
    "(url, headers) => fetch(url, { headers: JSON.parse(headers) })" +
        ".then(r => r.ok ? r.text() : null).catch(() => null)"
)
private external fun fetchText(url: String, headersJson: String): Promise<JsString?>

@JsFun(
    "(url, headers) => fetch(url, { headers: JSON.parse(headers) })" +
        ".then(r => r.ok ? r.arrayBuffer().then(b => new Uint8Array(b)) : null).catch(() => null)"
)
private external fun fetchBytes(url: String, headersJson: String): Promise<JsAny?>

// Resolves to JSON {"s": status, "b": body}, or null on network/CORS failure
@JsFun(
    "(url, method, headers, body) => fetch(url, { method, headers: JSON.parse(headers), " +
        "body: body === null ? undefined : body })" +
        ".then(r => r.text().then(t => JSON.stringify({ s: r.status, b: t }))).catch(() => null)"
)
private external fun fetchRequest(url: String, method: String, headersJson: String, body: String?): Promise<JsString?>

@JsFun("(a) => a.length")
private external fun byteLength(a: JsAny): Int

@JsFun("(a, i) => a[i]")
private external fun byteAt(a: JsAny, i: Int): Int

// Firefox sends a custom User-Agent, which turns tile/search requests into CORS preflights that
// tile servers reject; the browser's own User-Agent is used instead.
private fun headersJson(headers: Map<String, String>): String =
    headers.entries.filterNot { it.key.equals("User-Agent", ignoreCase = true) }
        .joinToString(prefix = "{", postfix = "}", separator = ",") { (k, v) ->
        "${quote(k)}:${quote(v)}"
    }

private fun quote(s: String): String = buildString {
    append('"')
    for (c in s) when {
        c == '"' -> append("\\\"")
        c == '\\' -> append("\\\\")
        c < ' ' -> append("\\u").append(c.code.toString(16).padStart(4, '0'))
        else -> append(c)
    }
    append('"')
}

actual object PlatformHttp {
    actual suspend fun getBytes(url: String, headers: Map<String, String>): ByteArray? {
        val array = fetchBytes(url, headersJson(headers)).await<JsAny?>() ?: return null
        return ByteArray(byteLength(array)) { byteAt(array, it).toByte() }
    }

    actual suspend fun getText(url: String, headers: Map<String, String>): String? =
        fetchText(url, headersJson(headers)).await<JsString?>()?.toString()

    actual suspend fun request(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String?
    ): HttpResult? {
        val raw = fetchRequest(url, method, headersJson(headers), body).await<JsString?>()?.toString() ?: return null
        val obj = Json.parseToJsonElement(raw).jsonObject
        return HttpResult(obj["s"]!!.jsonPrimitive.int, obj["b"]!!.jsonPrimitive.content)
    }
}
