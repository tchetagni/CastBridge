package castbridge.receiver

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import castbridge.core.tv.WifiDirect

/**
 * Wi-Fi Direct: the TV creates a group (it becomes group owner at 192.168.49.1) with a fixed network
 * name and a persistent passphrase. The phone joins that network and uses the normal HTTP API, with
 * no router involved. Needs NEARBY_WIFI_DEVICES (API 33+) or fine location (API 29-32); if refused,
 * or if the TV has no Wi-Fi Direct, [start] reports why and the other channels keep working.
 *
 * The Wi-Fi Direct access point and the classic Wi-Fi (the TV as a client of the router) share the same
 * radio: keeping both at once is unstable on many TVs. So while the group exists the TV drops its classic
 * Wi-Fi, and reconnects as soon as the group is gone (stopped, or dropped by the firmware). The reconnect is
 * also the safety net if the group disappears on its own ([monitorTick]).
 */
@SuppressLint("MissingPermission")
class WifiDirectGroup(private val ctx: Context, private val prefs: TvPrefs, private val status: (String?) -> Unit) {
    private val mgr = ctx.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private val wifi = ctx.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val main = Handler(Looper.getMainLooper())
    private var channel: WifiP2pManager.Channel? = null
    @Volatile private var monitoring = false
    /** (network name, password) while the group exists: given to a phone over Bluetooth (CBTN) so it can join by itself. */
    @Volatile var active: Pair<String, String>? = null; private set

    fun permission(): String = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.NEARBY_WIFI_DEVICES else Manifest.permission.ACCESS_FINE_LOCATION
    fun hasPermission() = ctx.checkSelfPermission(permission()) == PackageManager.PERMISSION_GRANTED

    /** Persistent so the phone can remember it; never logged. */
    private fun passphrase(): String = prefs.getString("wd_pass")?.takeIf { WifiDirect.isValidPassphrase(it) }
        ?: WifiDirect.generatePassphrase().also { prefs.putString("wd_pass", it) }

    fun start() {
        val m = mgr
        when {
            m == null || !ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT) -> {
                status("Wi-Fi Direct : non pris en charge par cette TV"); return
            }
            Build.VERSION.SDK_INT < 29 -> { status("Wi-Fi Direct : nécessite Android 10 ou plus"); return }
            !hasPermission() -> { status("Wi-Fi Direct : permission refusée (désactivé)"); return }
        }
        m!!
        val ch = channel ?: m.initialize(ctx, Looper.getMainLooper(), null).also { channel = it }
        val name = WifiDirect.networkName()
        val pass = passphrase()
        val cfg = try { WifiP2pConfig.Builder().setNetworkName(name).setPassphrase(pass).enablePersistentMode(false).build() }
        catch (e: Exception) { Log.w(TAG, "config", e); status("Wi-Fi Direct : configuration refusée"); return }
        // A stale group from a previous run would make createGroup fail with BUSY: remove it first.
        m.removeGroup(ch, object : WifiP2pManager.ActionListener {
            override fun onSuccess() = create(m, ch, cfg, name, pass)
            override fun onFailure(reason: Int) = create(m, ch, cfg, name, pass)
        })
    }

    private fun create(m: WifiP2pManager, ch: WifiP2pManager.Channel, cfg: WifiP2pConfig, name: String, pass: String) {
        m.createGroup(ch, cfg, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                active = name to pass
                stationOff()      // the group owns the radio now: drop the classic Wi-Fi so it does not fight for it
                startMonitor()
                status("Wi-Fi Direct : réseau « $name »  mot de passe : $pass  (${WifiDirect.GROUP_OWNER_IP}:8765)")
            }
            override fun onFailure(reason: Int) {
                Log.w(TAG, "createGroup failed: $reason")
                status("Wi-Fi Direct : échec de création du groupe (" + when (reason) {
                    WifiP2pManager.P2P_UNSUPPORTED -> "non pris en charge"
                    WifiP2pManager.BUSY -> "occupé : réessayez depuis le menu"
                    else -> "code $reason"
                } + ")")
            }
        })
    }

    fun stop() {
        stopMonitor()
        val m = mgr ?: return
        val ch = channel ?: return
        active = null
        runCatching { m.removeGroup(ch, null) }
        runCatching { if (Build.VERSION.SDK_INT >= 27) ch.close() }
        channel = null
        stationOn()
        status(null)
    }

    // ------------------------------------------------------------------ classic Wi-Fi

    /** Best effort: disconnect from the router while the group is up (the two share one radio). */
    @Suppress("DEPRECATION")
    private fun stationOff() { runCatching { wifi?.disconnect() } }

    /** Best effort: reconnect to the router once the group is gone. */
    @Suppress("DEPRECATION")
    private fun stationOn() { runCatching { wifi?.reconnect() } }

    // ------------------------------------------------------------------ monitor

    /** If the group disappears on its own (firmware reset, radio event), restore the classic Wi-Fi. */
    private fun startMonitor() {
        if (monitoring) return
        monitoring = true
        main.post(monitorTick)
    }

    private fun stopMonitor() {
        monitoring = false
        main.removeCallbacks(monitorTick)
    }

    private val monitorTick = object : Runnable {
        override fun run() {
            if (!monitoring) return
            val m = mgr; val ch = channel
            if (m != null && ch != null) runCatching {
                m.requestGroupInfo(ch) { group ->
                    if (group == null && active != null) {
                        active = null
                        stationOn()
                        stopMonitor()
                        status("Wi-Fi Direct : groupe terminé, Wi-Fi rétabli")
                    }
                }
            }
            if (monitoring) main.postDelayed(this, 10_000)
        }
    }

    companion object { private const val TAG = "CastBridgeWD" }
}
