package castbridge.core.tv

/** What the app can honestly report about the TV it runs on (no root, no system privileges). */
data class SysInfo(
    val model: String,
    val androidVersion: String,
    val ip: String?,
    val batteryPct: Int?,          // null: no battery (typical for a TV)
    val charging: Boolean?,
    val uptimeMs: Long,            // since boot
    val appVersion: String,
    // Memory of the app and of the device, to follow RAM use on a low-memory 32-bit TV.
    val pssMb: Int? = null,
    val memAvailMb: Int? = null,
    val memTotalMb: Int? = null,
    val lowMemory: Boolean? = null,
) {
    fun toJson(volumePct: Int?): String = buildString {
        append("{\"model\":").append(ReceiverServer.q(model))
        append(",\"android\":").append(ReceiverServer.q(androidVersion))
        append(",\"ip\":").append(ip?.let(ReceiverServer::q) ?: "null")
        append(",\"battery\":").append(batteryPct ?: "null")
        append(",\"charging\":").append(charging ?: "null")
        append(",\"uptime\":").append(uptimeMs)
        append(",\"app\":").append(ReceiverServer.q(appVersion))
        append(",\"volume\":").append(volumePct ?: "null")
        append(",\"pssMb\":").append(pssMb ?: "null")
        append(",\"memAvailMb\":").append(memAvailMb ?: "null")
        append(",\"memTotalMb\":").append(memTotalMb ?: "null")
        append(",\"lowMemory\":").append(lowMemory ?: "null")
        append('}')
    }
}

/**
 * Device-level actions, implemented by the TV app. Everything here is within what a normal Android
 * app may do: read public device info, set the media volume, restart itself. It cannot reboot the TV
 * or change system settings.
 */
interface Device {
    fun sysinfo(): SysInfo
    /** Media (STREAM_MUSIC) volume as 0..100, null if unavailable. */
    fun volume(): Int?
    fun setVolume(pct: Int)
    /** Restarts the CastBridge TV app (not the TV). Called after the HTTP response was sent. */
    fun restartApp()
}

data class ApiReply(val status: Int, val json: String, val bytes: ByteArray? = null, val mime: String = "application/json") {
    companion object { fun binary(bytes: ByteArray, mime: String) = ApiReply(200, "", bytes, mime) }
}

/** Plug-in routes under /api/, called after PIN authentication. [method] is "GET", "POST"... */
fun interface ApiExtension {
    fun handle(path: String, method: String, params: Map<String, String>): ApiReply?
    /** True for POST routes that take the raw request body (small file uploads, at most [ReceiverServer.MAX_EXT_BODY]). */
    fun wantsBody(path: String): Boolean = false
    fun handleBody(path: String, method: String, params: Map<String, String>, body: ByteArray): ApiReply? = null
}

/** Chains two extensions: the first one that handles a route answers it. */
fun ApiExtension.then(next: ApiExtension): ApiExtension {
    val first = this
    return object : ApiExtension {
        override fun handle(path: String, method: String, params: Map<String, String>) = first.handle(path, method, params) ?: next.handle(path, method, params)
        override fun wantsBody(path: String) = first.wantsBody(path) || next.wantsBody(path)
        override fun handleBody(path: String, method: String, params: Map<String, String>, body: ByteArray) =
            (if (first.wantsBody(path)) first.handleBody(path, method, params, body) else null) ?: next.handleBody(path, method, params, body)
    }
}
