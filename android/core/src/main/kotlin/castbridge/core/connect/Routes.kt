package castbridge.core.connect

import castbridge.core.device.DeviceClient
import castbridge.core.net.JsonLite
import java.io.File
import java.io.IOException
import java.net.Proxy

/**
 * Network paths to the server, tried in order: the device's own network, then — on the TV — the Internet of a phone
 * shared over Bluetooth (local SOCKS proxy 127.0.0.1:1080 of BtGatewayHost), only when the first path does not answer.
 * The path that worked through the gateway is tried first for [stickyMs] (no repeated time-outs on a TV without Wi-Fi).
 * Certificates are checked as usual on both paths (plain HttpURLConnection, no custom trust).
 */
class Routes(private val gateway: () -> Proxy? = { null }, private val clock: () -> Long = { System.currentTimeMillis() },
             private val stickyMs: Long = 10 * 60_000L) {
    enum class Via(val key: String, val label: String) {
        DIRECT("direct", "réseau de l'appareil"), GATEWAY("passerelle", "passerelle Bluetooth du téléphone")
    }

    @Volatile var lastVia: Via? = null; private set
    @Volatile private var gatewayUntil = 0L

    /** An answer of the server (HTTP status): the path works, trying another one would not help. */
    private fun answered(e: IOException) = e is DeviceClient.ServerError

    /**
     * Runs [block] on each path until one reaches the server. [networkFailure] tells a result that means "unreachable"
     * (for clients that return failures instead of throwing). The last failure is returned / thrown if none works.
     */
    fun <T> call(networkFailure: (T) -> Boolean = { false }, block: (Proxy?) -> T): T {
        val gw = runCatching { gateway() }.getOrNull()
        val order = if (gw != null && clock() < gatewayUntil) listOf(Via.GATEWAY, Via.DIRECT) else listOf(Via.DIRECT, Via.GATEWAY)
        var error: IOException? = null
        var failed: Any? = NONE
        for (via in order) {
            val proxy = if (via == Via.GATEWAY) gw ?: continue else null
            try {
                val r = block(proxy)
                if (networkFailure(r)) { failed = r; continue }
                lastVia = via
                gatewayUntil = if (via == Via.GATEWAY) clock() + stickyMs else 0
                return r
            } catch (e: IOException) {
                if (answered(e)) { lastVia = via; throw e }
                error = e
            }
        }
        @Suppress("UNCHECKED_CAST")
        if (failed !== NONE) return failed as T
        throw error ?: IOException("aucun réseau")
    }

    private object NONE
}

/** Removes what may name a file, a path or a URL from an error text (never content in clear; same rules as the server). */
object Scrub {
    fun text(s: String): String = s
        .replace(Regex("[a-zA-Z][a-zA-Z0-9+.-]*://\\S+"), "[url]")
        .replace(Regex("(?<![\\w])(/[^\\s/]+){2,}/?"), "[chemin]")
        .replace(Regex("\\S+\\.(?i:mp4|mkv|avi|mov|webm|ts|m4v|mp3|m4a|flac|wav|apk|jpg|jpeg|png|srt|torrent|pdf|zip)\\b"), "[fichier]")
}

/**
 * Crashes recorded by the app's uncaught-exception handler (a small file written at once, nothing else in the dying
 * process) and sent to POST /api/v1/devices/crash at the next start. At most [max] kept.
 */
class CrashStore(private val dir: File, private val max: Int = 5) {
    data class Crash(val file: File, val message: String, val detail: String, val versionCode: Int, val at: Long, val screen: String?)

    fun record(t: Throwable, versionCode: Int, screen: String? = null, now: Long = System.currentTimeMillis()) {
        runCatching {
            dir.mkdirs()
            val (message, detail) = describe(t)
            val json = JsonLite.write(linkedMapOf("message" to message, "detail" to detail, "versionCode" to versionCode, "at" to now, "screen" to screen))
            File(dir, "crash-$now.json").writeText(json, Charsets.UTF_8)
            dir.listFiles { f -> f.name.startsWith("crash-") }.orEmpty().sortedByDescending { it.name }.drop(max).forEach { it.delete() }
        }
    }

    fun pending(): List<Crash> = dir.listFiles { f -> f.name.startsWith("crash-") && f.name.endsWith(".json") }.orEmpty().sortedBy { it.name }
        .mapNotNull { f ->
            runCatching {
                val m = JsonLite.obj(f.readText(Charsets.UTF_8))
                Crash(f, m["message"] as String, m["detail"] as? String ?: "", (m["versionCode"] as Number).toInt(), (m["at"] as Number).toLong(),
                    m["screen"] as? String)
            }.getOrElse { f.delete(); null }
        }

    fun remove(c: Crash) { c.file.delete() }

    fun clear() { dir.listFiles { f -> f.name.startsWith("crash-") }.orEmpty().forEach { it.delete() } }

    companion object {
        /** ("NullPointerException: …", top of the stack, causes included), scrubbed of paths and file names. */
        fun describe(t: Throwable): Pair<String, String> {
            val message = Scrub.text("${t.javaClass.simpleName}: ${t.message ?: ""}".trim().removeSuffix(":")).take(500)
            val b = StringBuilder()
            var c: Throwable? = t
            var depth = 0
            while (c != null && depth < 4 && b.length < 3800) {
                if (depth > 0) b.append("Caused by: ")
                b.append(c.javaClass.name).append(": ").append(Scrub.text(c.message ?: "")).append('\n')
                c.stackTrace.take(if (depth == 0) 25 else 8).forEach { b.append("  at ").append(it.toString()).append('\n') }
                c = c.cause.takeIf { it !== c }
                depth++
            }
            return message to b.toString().take(4000)
        }
    }
}
