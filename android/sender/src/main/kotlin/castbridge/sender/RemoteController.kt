package castbridge.sender

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import castbridge.core.quiz.Json
import castbridge.core.remote.BtRoute
import castbridge.core.remote.LinkKind
import castbridge.core.remote.RemotePlan
import castbridge.core.remote.hid.HidStrategy
import castbridge.core.remote.RouteStatus
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
import castbridge.core.trust.PinKeys
import castbridge.core.trust.TrustRegistry
import castbridge.core.tv.BtProtocol
import castbridge.core.tv.ReceiverServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.IOException
import java.util.UUID

/** The TV the remote talks to: found by mDNS (name), typed by hand (host), and/or a paired Bluetooth TV as a fallback. */
data class RemoteTv(val name: String, val host: String?, val port: Int = ReceiverServer.PORT, val btAddress: String? = null) {
    /** Key of the PIN in [PinStore] (TV name for mDNS TVs, "host:port" for a typed address, "bt:<address>" for Bluetooth only). */
    val pinKey: String get() = when { host == null && btAddress != null -> PinKeys.btKey(btAddress); name.startsWith("manual:") -> "$host:$port"; else -> name }
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
    /** False when the TV says the whole-TV mode cannot be switched on there (older TVs omit it: then true). */
    val systemAvailable: Boolean = true, val systemReason: String? = null,
    /** The TV relays keys to the manufacturer's remote service (docs/REMOTE-VENDOR-CVTE.md); older TVs omit it: false. */
    val vendorAvailable: Boolean = false,
) {
    companion object {
        fun parse(j: String): RemoteTvState? = runCatching {
            val o = Json.obj(j)
            @Suppress("UNCHECKED_CAST") val sys = o["system"] as? Map<String, Any?> ?: emptyMap()
            RemoteTvState(o["screen"] as? String, o["castbridgeFront"] == true, o["textField"] == true,
                sys["enabled"] == true, sys["connected"] == true, (o["volume"] as? Long)?.toInt(), o["muted"] as? Boolean, o["volumeFixed"] == true,
                sys["available"] != false, sys["reason"] as? String,
                ((o["vendor"] as? Map<*, *>)?.get("available")) == true)
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
    /** "Bluetooth exclusivement": the remote never uses the Wi-Fi (docs/REMOTE.md, three Bluetooth routes). */
    var btOnly: Boolean get() = sp.getBoolean("btonly", false); set(v) { sp.edit().putBoolean("btonly", v).apply() }
    /** Result of the user's test of a Bluetooth route (name of [BtRoute]); null = never tested. */
    fun routeResult(r: BtRoute): RouteStatus? = sp.getString("route_${r.name}", null)?.let { n -> RouteStatus.values().firstOrNull { it.name == n } }
    fun setRouteResult(r: BtRoute, v: RouteStatus?) { sp.edit().apply { if (v == null) remove("route_${r.name}") else putString("route_${r.name}", v.name) }.apply() }
    /** The phone-as-keyboard route (HID) only sends keys once the user turned it on. */
    var hidEnabled: Boolean get() = sp.getBoolean("hid", false); set(v) { sp.edit().putBoolean("hid", v).apply() }
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
        RemotePrefs(app).let { p -> hid.enabled = p.hidEnabled; hid.confirmed = p.routeResult(BtRoute.HID) == RouteStatus.CONFIRMED }
        val s = RemoteSession(RemoteQueue(), { open(app, tv, TvLinkManager.credentialFor(tv.pinKey) ?: pin, btFallback) }, object : RemoteSession.Listener {
            override fun status(s: RemoteSession.Status) { _status.value = s }
            override fun state(json: String) { RemoteTvState.parse(json)?.let { _tv.value = it } }
            override fun refused(e: RemoteEvent, message: String) { _notice.value = System.currentTimeMillis() to message }
            override fun answered(e: RemoteEvent, r: RemoteReply, rttMs: Long) { _lastRtt.value = rttMs }
        }, pingMs = 3000, onTokenRejected = { TvLinkManager.tokenRejected(TvLinkManager.credentialFor(tv.pinKey) ?: pin) })
        s.target = if (RemotePrefs(app).wholeTv) RemoteTarget.AUTO else RemoteTarget.APP
        session = s
        s.start()
    }

    @Synchronized fun disconnect() { session?.stop(); session = null; current = null; _status.value = RemoteSession.Status(RemoteSession.Link.OFFLINE, message = "Déconnectée") }

    /** Reconnects with the current preferences (after "Bluetooth exclusivement" changed). */
    @Synchronized fun restart(ctx: Context) { current?.let { (tv, pin, bt) -> session?.stop(); session = null; current = null; connect(ctx, tv, pin, bt) } }

    /** Sends [k] once with an explicit [target] (route tests: SYSTEM = the relay), then puts the usual target back. */
    fun keyVia(k: RemoteKey, target: RemoteTarget) { val s = session ?: return; val old = s.target; s.target = target; s.key(k, KeyAction.PRESS, 0); s.target = old }

    fun setWholeTv(on: Boolean) { session?.target = if (on) RemoteTarget.AUTO else RemoteTarget.APP }

    /** Route C (phone as a Bluetooth keyboard): off until the user enabled it and a test confirmed that the TV reacts (docs/REMOTE.md). */
    val hid = HidStrategy(BtHidRemote, hostRefused = { BtHidRemote.hostRefused })

    /** Status line "Bluetooth seulement" / "Bluetooth exclusivement" when the link goes over Bluetooth, else null. */
    fun statusLine(s: RemoteSession.Status, btOnly: Boolean): String? = if (s.link == RemoteSession.Link.CONNECTED) RemotePlan.linkMessage(s.via, btOnly) else null

    /** Keys go to the smart remote (docs/REMOTE.md) when « Ma TV » drives a TV with another strategy than CastBridge-TV's own link. */
    fun key(k: RemoteKey, action: KeyAction = KeyAction.PRESS, repeat: Int = 0) {
        if (SmartRemote.handles()) { if (action != KeyAction.UP) SmartRemote.send(k); return }
        // No link to CastBridge TV: the keyboard route, only if enabled and confirmed.
        if (!connected && hid.enabled && hid.confirmed) hid.send(k, action) else session?.key(k, action, repeat)
    }
    fun text(value: String, mode: TextMode = TextMode.INSERT) {
        if (SmartRemote.handles()) { if (mode != TextMode.CLEAR) SmartRemote.sendText(value); return }
        session?.text(value, mode)
    }
    fun global(g: RemoteGlobal) { session?.global(g) }
    fun setup() { session?.queue?.offer("system/setup", emptyMap()) }

    /** A session exists (connected or retrying): the background service keeps it alive. */
    val hasSession: Boolean get() = session != null

    /**
     * R-35 (audit I-14): the Bluetooth link the remote already holds to the TV at [address], while it is up, or null (no session, another TV, Wi-Fi link, link down). « Ouvrir sur la TV »
     * (touche « TV », [OpenTvSessionLink]) sends its `open` command on it: a second RFCOMM link to the same service is refused by the TV, which made the TV look off while the remote worked.
     */
    fun liveBluetooth(address: String): RemoteTransport? {
        val cur = current ?: return null
        val sessionAddress = cur.first.btAddress ?: cur.third ?: return null
        if (TrustRegistry.norm(sessionAddress) != TrustRegistry.norm(address)) return null
        return session?.liveTransport()
    }

    val connected: Boolean get() = SmartRemote.handles() || session != null && _status.value.link == RemoteSession.Link.CONNECTED

    @SuppressLint("MissingPermission")
    private fun open(ctx: Context, tv: RemoteTv, pin: String, btFallback: String?): RemoteTransport {
        var wifiError: IOException? = null
        val bt = tv.btAddress ?: btFallback
        val btOnly = RemotePrefs(ctx).btOnly
        val links = RemotePlan.links(tv.host != null, bt != null, btOnly)
        if (LinkKind.WIFI in links && tv.host != null) {
            val t = HttpRemoteTransport(tv.host, tv.port, pin, connectTimeoutMs = 1500)
            try { t.connect(); return t } catch (e: IOException) { wifiError = e }
        }
        if (LinkKind.BLUETOOTH in links && bt != null) {
            if (!hasBtPermission(ctx)) throw IOException("Secours Bluetooth : autorisation « Appareils à proximité » refusée")
            val adapter = ctx.getSystemService(BluetoothManager::class.java)?.adapter
            if (adapter == null || !adapter.isEnabled) throw IOException(if (wifiError != null) "TV injoignable par le Wi-Fi, et le Bluetooth du téléphone est éteint" else "Bluetooth éteint")
            val sock = adapter.getRemoteDevice(bt).createRfcommSocketToServiceRecord(UUID.fromString(BtProtocol.SERVICE_UUID))
            try {
                synchronized(castbridge.core.tunnel.BtConnectLock.of(bt)) { sock.connect() }      // never beside the API tunnel's connect() to the same TV
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
        if (btOnly) throw IOException("Bluetooth exclusivement : choisissez une TV appairée comme secours Bluetooth (CastBridge TV ouverte).")
        throw IOException(if (tv.host == null) "Aucune adresse pour cette TV"
            else "Télécommande indisponible : la TV ne répond pas sur le réseau (même Wi-Fi ? TV allumée ?). " +
                "Sans réseau commun, choisissez un secours Bluetooth.")
    }
}
