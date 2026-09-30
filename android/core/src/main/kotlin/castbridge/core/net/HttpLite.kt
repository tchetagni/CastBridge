package castbridge.core.net

import java.io.IOException
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URL
import java.net.URLEncoder

/**
 * Minimal HTTP client over HttpURLConnection for the CastBridge server, with an optional [proxy] (the TV goes through
 * the local SOCKS proxy 127.0.0.1:1080 when the phone shares its Internet over Bluetooth).
 */
class HttpLite(
    private val proxy: Proxy? = null,
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 30_000,
    private val userAgent: String = "CastBridge",
) {
    class Response(val code: Int, val body: String, val headers: Map<String, List<String>>) {
        fun header(name: String): String? = headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.firstOrNull()
    }

    fun open(url: String): HttpURLConnection {
        val u = URL(url)
        require(u.protocol == "https" || u.protocol == "http") { "unsupported URL scheme: ${u.protocol}" }
        val c = (if (proxy != null) u.openConnection(proxy) else u.openConnection()) as HttpURLConnection
        c.connectTimeout = connectTimeoutMs
        c.readTimeout = readTimeoutMs
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", userAgent)
        return c
    }

    /** One JSON request; the body of error answers is read too (French message from the server). */
    @Throws(IOException::class)
    fun request(method: String, url: String, jsonBody: String? = null, headers: Map<String, String> = emptyMap()): Response {
        val c = open(url)
        try {
            c.requestMethod = method
            c.setRequestProperty("Accept", "application/json")
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            if (jsonBody != null) {
                val bytes = jsonBody.toByteArray(Charsets.UTF_8)
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                c.setFixedLengthStreamingMode(bytes.size)
                c.outputStream.use { it.write(bytes) }
            }
            val code = c.responseCode
            val stream = if (code >= 400) c.errorStream else c.inputStream
            val body = stream?.use { String(it.readBytes(), Charsets.UTF_8) } ?: ""
            return Response(code, body, c.headerFields.filterKeys { it != null })
        } finally {
            c.disconnect()
        }
    }

    companion object {
        fun query(vararg params: Pair<String, Any?>): String = params.filter { it.second != null }
            .joinToString("&") { URLEncoder.encode(it.first, "UTF-8") + "=" + URLEncoder.encode(it.second.toString(), "UTF-8") }

        /** The "message" of a server error, else the HTTP code. */
        fun errorMessage(r: Response): String =
            runCatching { JsonLite.obj(r.body)["message"] as? String }.getOrNull() ?: "HTTP ${r.code}"
    }
}
