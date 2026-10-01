package castbridge.receiver

import android.content.Context
import android.util.Log
import castbridge.core.ssh.PeerRegistry
import castbridge.core.tunnel.TcpTunnel
import castbridge.core.tv.ApiReply
import castbridge.core.tv.BtProtocol
import castbridge.core.tv.ReceiverServer

/**
 * "API par Bluetooth" (docs/ADMIN.md, "Tout par Bluetooth"): the TV's HTTP API (127.0.0.1:8765) reachable over a third RFCOMM
 * service ("CastBridge API"), so that everything a phone or a computer does over Wi-Fi also works when Wi-Fi does not.
 *
 * The HTTP server keeps doing ALL the checking (PIN header, trust token, parental code): the tunnel only carries bytes. Who may
 * open a link ([admit]): a device that is paired with the TV (the secure RFCOMM socket proves its address) AND either this switch
 * is on (default: on, because the file service already takes a paired device with the right PIN) or the device is a trusted phone
 * (docs/BT-PLUG-AND-PLAY.md). PIN failures are counted per device through [peers], never against loopback.
 */
class BtApiControl(
    ctx: Context,
    private val prefs: TvPrefs,
    private val bonded: (String) -> Boolean,
    private val trusted: (String) -> Boolean,
    /** An operation (upload, move...) is running on the TV: its links may stay silent much longer. */
    private val busy: () -> Boolean,
    status: (String?) -> Unit,
) {
    val peers = PeerRegistry()

    var enabled: Boolean
        get() = prefs.getBool(PREF, true)
        private set(v) { prefs.putBool(PREF, v) }

    private fun admit(peer: String): String? = when {
        !bonded(peer) -> "appareil non appairé avec la TV"
        enabled || trusted(peer) -> null
        else -> "« API par Bluetooth » est coupée sur la TV (réactivez-la dans Administration) et ce téléphone n'est pas un téléphone de confiance"
    }

    private val tunnel = TcpTunnel("api", peers, ReceiverServer.PORT, maxConnections = 4,
        idleMs = { if (busy()) 10 * 60_000L else 30_000L }, handshake = true, admit = ::admit, log = { Log.i(TAG, it) })

    private val bridge = BtTunnelBridge(ctx, TAG, "API par Bluetooth", "CastBridge API", BtTunnelBridge.uuid(BtProtocol.API_SERVICE_UUID),
        serve = { peer, name, i, o, close, onChange -> tunnel.serve(peer, name, i, o, close, onChange) },
        activeNames = { tunnel.active().map { it.name } }, lastError = { tunnel.lastError }, status = status)

    /** Fourth service ("CastBridge API v2"): ONE shared link per phone carrying several HTTP connections (castbridge.core.tunnel.MuxSession). The old service above stays for old phones. */
    private val sharedBridge = BtTunnelBridge(ctx, "$TAG-v2", "API par Bluetooth", "CastBridge API v2", BtTunnelBridge.uuid(BtProtocol.API_MUX_SERVICE_UUID),
        serve = { peer, name, i, o, close, onChange -> tunnel.serveMux(peer, name, i, o, close, onChange) },
        activeNames = { tunnel.active().map { it.name } }, lastError = { tunnel.lastError }, status = { /* the v1 bridge owns the status line */ })

    fun start() { bridge.start(); sharedBridge.start() }
    fun stop() { sharedBridge.stop(); bridge.stop() }

    fun enable(on: Boolean) { enabled = on; bridge.refresh() }

    private fun json(): String {
        val q = ReceiverServer::q
        return """{"enabled":$enabled,"listening":${bridge.running},"maxConnections":4,"refused":${tunnel.refused},"sharedLinks":${tunnel.muxLinks()},"sharedListening":${sharedBridge.running},"lastClose":${tunnel.lastClose?.let(q) ?: "null"},""" +
            """"lastError":${tunnel.lastError?.let(q) ?: "null"},"active":[""" + tunnel.active().joinToString(",") { q(it.name) } + "]}"
    }

    /** Routes /api/bluetooth/tunnel*, or null if [path] is not one of them. */
    fun api(path: String, method: String): ApiReply? = when {
        path == "/api/bluetooth/tunnel" && method == "GET" -> ApiReply(200, json())
        path == "/api/bluetooth/tunnel/enable" && method == "POST" -> { enable(true); ApiReply(200, json()) }
        path == "/api/bluetooth/tunnel/disable" && method == "POST" -> { enable(false); ApiReply(200, json()) }
        else -> null
    }

    private companion object { const val TAG = "CastBridgeApiBt"; const val PREF = "bt_api" }
}
