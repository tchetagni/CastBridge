package castbridge.core.device

import castbridge.core.net.HttpLite
import castbridge.core.net.JsonLite
import castbridge.core.net.JsonLite.bool
import castbridge.core.net.JsonLite.int
import castbridge.core.net.JsonLite.str
import java.io.IOException
import java.net.Proxy

/** Where the app keeps what the server gave it (SharedPreferences on Android). */
interface DeviceStore {
    var deviceId: String?
    var deviceToken: String?
}

/**
 * Registration and heartbeats towards the CastBridge server. The token received at registration authenticates the
 * following heartbeats and crash reports; if the server no longer knows it (401), the client registers again once.
 * Call [heartbeat] at start-up, after an update, then every [Directives.heartbeatSeconds] (15 min).
 */
class DeviceClient(
    baseUrl: String,
    private val store: DeviceStore,
    proxy: Proxy? = null,
    private val http: HttpLite = HttpLite(proxy, userAgent = "CastBridge-device"),
) {
    private val base = baseUrl.trimEnd('/')

    /** What the server asks of the app in return. */
    data class Directives(
        /** Check for an update now (admin action), within the limits of [castbridge.core.update.UpdateSchedule]. */
        val checkUpdate: Boolean,
        /** Blocked by the admin: no update, no quiz from the server. */
        val blocked: Boolean,
        /** Update channel to use ("stable" / "beta", possibly forced by the admin). */
        val channel: String,
        val heartbeatSeconds: Int,
        val serverTime: String?,
    ) {
        companion object {
            internal fun parse(m: Map<String, Any?>) = Directives(
                checkUpdate = m.bool("checkUpdate") ?: false, blocked = m.bool("blocked") ?: false,
                channel = m.str("channel") ?: "stable", heartbeatSeconds = m.int("heartbeatSeconds") ?: 900,
                serverTime = m.str("serverTime"),
            )
        }
    }

    class ServerError(val code: Int, message: String) : IOException(message)

    @Throws(IOException::class)
    fun register(report: DeviceReport): Directives {
        val r = http.request("POST", "$base/api/v1/devices/register", report.toJson())
        if (r.code != 201 && r.code != 200) throw ServerError(r.code, HttpLite.errorMessage(r))
        val m = JsonLite.obj(r.body)
        store.deviceId = m.str("deviceId")
        store.deviceToken = m.str("deviceToken")
        @Suppress("UNCHECKED_CAST")
        return (m["directives"] as? Map<String, Any?>)?.let { Directives.parse(it) }
            ?: Directives(false, false, report.channel, m.int("heartbeatSeconds") ?: 900, null)
    }

    /** Sends the current report; registers first if needed (no token yet, or token unknown to the server). */
    @Throws(IOException::class)
    fun heartbeat(report: DeviceReport): Directives {
        if (store.deviceToken == null) return register(report)
        var r = post("/api/v1/devices/heartbeat", report.toJson())
        if (r.code == 401) {
            register(report)
            r = post("/api/v1/devices/heartbeat", report.toJson())
        }
        if (r.code != 200) throw ServerError(r.code, HttpLite.errorMessage(r))
        return Directives.parse(JsonLite.obj(r.body))
    }

    /** Short crash / error report (message ≤ 500 characters, detail ≤ 4000, e.g. the top of the stack trace). */
    @Throws(IOException::class)
    fun crash(report: DeviceReport, message: String, detail: String? = null) {
        if (store.deviceToken == null) register(report)
        val body = JsonLite.write(linkedMapOf("message" to message.take(500), "detail" to detail?.take(4000), "versionCode" to report.versionCode))
        var r = post("/api/v1/devices/crash", body)
        if (r.code == 401) { register(report); r = post("/api/v1/devices/crash", body) }
        if (r.code != 204 && r.code != 200) throw ServerError(r.code, HttpLite.errorMessage(r))
    }

    /** Right of access: the JSON the server holds about this device (record + usage events). */
    @Throws(IOException::class)
    fun myData(): String {
        val token = store.deviceToken ?: return "{}"
        val r = http.request("GET", "$base/api/v1/devices/me", headers = mapOf("Authorization" to "Bearer $token"))
        if (r.code != 200) throw ServerError(r.code, HttpLite.errorMessage(r))
        return r.body
    }

    /**
     * Right to erasure asked from the app: the server deletes the device and all its data; the local identifiers are
     * forgotten too (the app should also clear its telemetry queue and draw a new install id).
     */
    @Throws(IOException::class)
    fun eraseMe() {
        val token = store.deviceToken
        if (token != null) {
            val r = http.request("DELETE", "$base/api/v1/devices/me", headers = mapOf("Authorization" to "Bearer $token"))
            if (r.code != 204 && r.code != 401) throw ServerError(r.code, HttpLite.errorMessage(r))
        }
        store.deviceToken = null
        store.deviceId = null
    }

    private fun post(path: String, json: String) =
        http.request("POST", base + path, json, mapOf("Authorization" to "Bearer ${store.deviceToken}"))
}
