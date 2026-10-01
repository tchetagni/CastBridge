package castbridge.sender

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import castbridge.core.quiz.Json
import castbridge.core.remote.HttpRemoteTransport
import castbridge.core.remote.KeyAction
import castbridge.core.remote.RemoteBt
import castbridge.core.remote.RemoteEvent
import castbridge.core.remote.RemoteGlobal
import castbridge.core.remote.RemoteKey
import castbridge.core.remote.RemoteQueue
import castbridge.core.remote.RemoteReply
import castbridge.core.remote.RemoteSession
import castbridge.core.remote.RemoteTarget
import castbridge.core.remote.RemoteTransport
import castbridge.core.remote.TextMode
import castbridge.core.tv.BtProtocol
import castbridge.core.tv.ReceiverServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.IOException
import java.util.UUID

/** The TV the remote talks to: found by mDNS (name), typed by hand (host), and/or a paired Bluetooth TV as a fallback. */
data class RemoteTv(val name: String, val host: String?, val port: Int = ReceiverServer.PORT, val btAddress: String? = null) {
    /** Key of the PIN in [PinStore] (TV name for mDNS TVs, "host:port" for a typed address, "bt:<address>" for Bluetooth only). */
    val pinKey: String get() = when { host == null && btAddress != null -> "bt:$btAddress"; name.startsWith("manual:") -> "$host:$port"; else -> name }
    val label: String get() = when {
        name.startsWith("manual:") -> "$host"
        host == null -> "Bluetooth"
        else -> name.removePrefix("CastBridge TV ").ifBlank { name }
    }
}

/** What GET /api/remote/state says. */
data class RemoteTvState(
    val screen: String?, val castbridgeFront: Boolean, val textField: Boolean,
    val systemEnabled: Boolean, val systemConnected: Boolean, val volume: Int?, val muted: Boolean?, val volumeFixed: Boolean,
) {
    companion object {
        fun parse(j: String): RemoteTvState? = runCatching {
            val o = Json.obj(j)
            @Suppress("UNCHECKED_CAST") val sys = o["system"] as? Map<String, Any?> ?: emptyMap()
            RemoteTvState(o["screen"] as? String, o["castbridgeFront"] == true, o["textField"] == true,
                sys["enabled"] == true, sys["connected"] == true, (o["volume"] as? Long)?.toInt(), o["muted"] as? Boolean, o["volumeFixed"] == true)
        }.getOrNull()
    }
}

/** Saved choice of the remote screen (private preferences). */
class RemotePrefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("castbridge_remote", Context.MODE_PRIVATE)
    private val home = ctx.applicationContext.getSharedPreferences("castbridge_home", Context.MODE_PRIVATE)
    var tvName: String? get() = sp.getString("tv", null) ?: home.getString("tv", null); set(v) { sp.edit().putString("tv", v).apply() }
    var manualHost: String? get() = sp.getString("host", null); set(v) { sp.edit().putString("host", v).apply() }
    var manualPort: Int get() = sp.getInt("port", ReceiverServer.PORT); set(v) { sp.edit().putInt("port", v).apply() }
    var btFallback: String? get() = sp.getString("bt", null); set(v) { sp.edit().putString("bt", v).apply() }
    var volumeKeys: Boolean get() = sp.getBoolean("volkeys", true); set(v) { sp.edit().putBoolean("volkeys", v).apply() }
    var haptics: Boolean get() = sp.getBoolean("haptics", true); set(v) { sp.edit().putBoolean("haptics", v).apply() }
    var wholeTv: Boolean get() = sp.getBoolean("whole", false); set(v) { sp.edit().putBoolean("whole", v).apply() }
    /** Keep the remote (notification, phone volume buttons) working when the app is not in front (RemoteService). On by default. */
    var background: Boolean get() = sp.getBoolean("background", true); set(v) { sp.edit().putBoolean("background", v).apply() }
}

/**
 * The phone remote (docs/REMOTE.md): one [RemoteSession] for the chosen TV, shared by the remote screen and the volume keys.
 * Wi-Fi first (one keep-alive HTTP connection); if the TV cannot be reached that way and a Bluetooth fallback TV is chosen,
 * the Bluetooth file service (CBTR). Everything the screen shows comes from the StateFlows here.
 */
object RemoteController {
    private val _status = MutableStateFlow(RemoteSession.Status(RemoteSession.Link.OFFLINE, message = "Aucune TV choisie"))
    val status: StateFlow<RemoteSession.Status> = _status
    private val _tv = MutableStateFlow<RemoteTvState?>(null)
    val tvState: StateFlow<RemoteTvState?> = _tv
    private val _notice = MutableStateFlow<Pair<Long, String>?>(null)
    /** Last refusal of the TV (time, message), for a short banner. */
    val notice: StateFlow<Pair<Long, String>?> = _notice
    private val _lastRtt = MutableStateFlow<Long?>(null)
    val lastRtt: StateFlow<Long?> = _lastRtt

    @Volatile private var session: RemoteSession? = null
    @Volatile private var current: Triple<RemoteTv, String, String?>? = null

    /** Starts (or keeps) the session for [tv] with [pin]; [btFallback] = a paired TV's Bluetooth address, or null. */
    @Synchronized fun connect(ctx: Context, tv: RemoteTv, pin: String, btFallback: String?) {
        val key = Triple(tv, pin, btFallback)
        if (current == key && session?.isRunning == true) return
        session?.stop()
        current = key
        _tv.value = null
        val app = ctx.applicationContext
        val s = RemoteSession(RemoteQueue(), { open(app, tv, pin, btFallback) }, object : RemoteSession.Listener {
            override fun status(s: RemoteSession.Status) { _status.value = s }
            override fun state(json: String) { RemoteTvState.parse(json)?.let { _tv.value = it } }
            override fun refused(e: RemoteEvent, message: String) { _notice.value = System.currentTimeMillis() to message }
            override fun answered(e: RemoteEvent, r: RemoteReply, rttMs: Long) { _lastRtt.value = rttMs }
        }, pingMs = 3000)
        s.target = if (RemotePrefs(app).wholeTv) RemoteTarget.AUTO else RemoteTarget.APP
        session = s
        s.start()
    }

    @Synchronized fun disconnect() { session?.stop(); session = null; current = null; _status.value = RemoteSession.Status(RemoteSession.Link.OFFLINE, message = "Déconnectée") }

    fun setWholeTv(on: Boolean) { session?.target = if (on) RemoteTarget.AUTO else RemoteTarget.APP }

    fun key(k: RemoteKey, action: KeyAction = KeyAction.PRESS, repeat: Int = 0) { session?.key(k, action, repeat) }
    fun text(value: String, mode: TextMode = TextMode.INSERT) { session?.text(value, mode) }
    fun global(g: RemoteGlobal) { session?.global(g) }
    fun setup() { session?.queue?.offer("system/setup", emptyMap()) }

    /** A session exists (connected or retrying): the background service keeps it alive. */
    val hasSession: Boolean get() = session != null

    val connected: Boolean get() = session != null && _status.value.link == RemoteSession.Link.CONNECTED

    @SuppressLint("MissingPermission")
    private fun open(ctx: Context, tv: RemoteTv, pin: String, btFallback: String?): RemoteTransport {
        var wifiError: IOException? = null
        if (tv.host != null) {
            val t = HttpRemoteTransport(tv.host, tv.port, pin, connectTimeoutMs = 1500)
            try { t.connect(); return t } catch (e: IOException) { wifiError = e }
        }
        val bt = tv.btAddress ?: btFallback
        if (bt != null) {
            if (!hasBtPermission(ctx)) throw IOException("Secours Bluetooth : autorisation « Appareils à proximité » refusée")
            val adapter = ctx.getSystemService(BluetoothManager::class.java)?.adapter
            if (adapter == null || !adapter.isEnabled) throw IOException(if (wifiError != null) "TV injoignable par le Wi-Fi, et le Bluetooth du téléphone est éteint" else "Bluetooth éteint")
            val sock = adapter.getRemoteDevice(bt).createRfcommSocketToServiceRecord(UUID.fromString(BtProtocol.SERVICE_UUID))
            try {
                sock.connect()
                RemoteBt.handshake(sock.inputStream, sock.outputStream, pin)
                return RemoteBt.Transport(sock.inputStream, sock.outputStream, sock)
            } catch (e: BtProtocol.Refused) {
                runCatching { sock.close() }
                throw IOException("Bluetooth : ${e.message}" + if (e.code == BtProtocol.ERR_MAGIC) " (mettez à jour CastBridge TV)" else "")
            } catch (e: IOException) {
                runCatching { sock.close() }
                throw IOException("TV injoignable par le Wi-Fi et par le Bluetooth (CastBridge TV ouverte ? appareils appairés ?)")
            }
        }
        throw IOException(if (tv.host == null) "Aucune adresse pour cette TV"
            else "Télécommande indisponible : la TV ne répond pas sur le réseau (même Wi-Fi ? TV allumée ?). " +
                "Sans réseau commun, choisissez un secours Bluetooth.")
    }
}
