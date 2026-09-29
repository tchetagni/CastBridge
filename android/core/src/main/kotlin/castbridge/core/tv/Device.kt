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

data class ApiReply(val status: Int, val json: String)

/** Plug-in routes under /api/, called after PIN authentication. [method] is "GET", "POST"... */
fun interface ApiExtension {
    fun handle(path: String, method: String, params: Map<String, String>): ApiReply?
}
