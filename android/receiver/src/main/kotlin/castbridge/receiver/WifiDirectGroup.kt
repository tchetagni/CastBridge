package castbridge.receiver

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Looper
import android.util.Log
import castbridge.core.tv.WifiDirect

/**
 * Wi-Fi Direct: the TV creates a group (it becomes group owner at 192.168.49.1). The phone joins that network and uses the normal HTTP API, with
 * no router involved. Needs NEARBY_WIFI_DEVICES (API 33+) or fine location (API 29-32); if refused, or if the TV has no Wi-Fi Direct, [start]
 * reports why ([lastError], a [WifiDirect.Err] word) and the other channels keep working.
 *
 * Two kinds of group (docs/agent-reports/auto-wifi-direct.md):
 *  - the owner's group (MENU « Wi-Fi Direct : activer », `wd_enabled`): a readable name, its password shown on the TV's screen so it can be typed;
 *  - an AUTOMATIC group ([auto]) created because a trusted phone asked over Bluetooth (CBTN): a random name, nothing shown, removed by
 *    [castbridge.core.link.WdGroupLease] when the phone gave it back, left, or no longer uses it.
 * In both cases the passphrase is FRESH for every group (16 random characters), kept in memory only while the group exists, never logged, never stored.
 *
 * A third kind, the ACTIVATION group ([startActivation], docs/TV-ACTIVATION-CLE-USB.md « Réseau d'activation hébergé »): made while the activation screen of a LOCKED TV is open,
 * name and password DERIVED from the 6-digit connection code that screen shows ([castbridge.core.tv.WdCode]), so the phone that read the code (or the QR) joins it by itself.
 */
@SuppressLint("MissingPermission")
class WifiDirectGroup(private val ctx: Context, private val prefs: TvPrefs, private val status: (String?) -> Unit) : castbridge.core.link.WdGroupDriver {
    private val mgr = ctx.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private var channel: WifiP2pManager.Channel? = null
    /** (network name, password) while the group exists: given to a phone over Bluetooth (CBTN) so it can join by itself. */
    @Volatile override var active: Pair<String, String>? = null; private set
    /** The group was created for a phone (not by the owner from the MENU). */
    @Volatile override var auto = false; private set
    @Volatile override var createdAt = 0L; private set
    /** Why the last [start] gave no group ([WifiDirect.Err]); null = none or not tried. */
    @Volatile override var lastError: String? = null; private set
    /** An activation group was asked for and Android has not answered yet. */
    @Volatile var starting = false; private set
    /** Name of the activation group being made or up (never the password). */
    @Volatile private var wanted: String? = null
    /** Bumped by every [startActivation] and by [stop]: a late answer of Android to an older request is ignored and its group removed (the screen closed or reopened meanwhile). */
    @Volatile private var generation = 0

    fun permission(): String = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.NEARBY_WIFI_DEVICES else Manifest.permission.ACCESS_FINE_LOCATION
    override fun hasPermission() = ctx.checkSelfPermission(permission()) == PackageManager.PERMISSION_GRANTED
    private fun supported() = mgr != null && Build.VERSION.SDK_INT >= 29 && ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT)
    /** Can this TV create a group for a phone right now (capability `wd.cap` of the CBTN / HELLO answer)? */
    override fun capable() = supported() && hasPermission()
    private fun wifiOn() = runCatching { (ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).isWifiEnabled }.getOrDefault(true)

    /**
     * The owner's group (MENU) keeps its configured password (it may be saved on a phone: audit R-14); only an AUTOMATIC group's password is ephemeral
     * (fresh for every group, never stored).
     */
    private fun ownerPassphrase(): String = prefs.getString("wd_pass")?.takeIf { WifiDirect.isValidPassphrase(it) }
        ?: WifiDirect.groupPassphrase().also { prefs.putString("wd_pass", it) }

    /** [forPhone] = an automatic group for a phone that asked over Bluetooth; false = the owner's group (MENU). */
    override fun start(forPhone: Boolean) = start(forPhone, recreated = false)

    /**
     * The ACTIVATION group for the screen of a LOCKED TV: the name and the password come from [code] ([castbridge.core.tv.WdCode]); automatic band (a phone with a 2.4 GHz-only radio must
     * see it); idempotent for the same code. The password is accepted only for this ephemeral group (ACT-NF1), shown nowhere as text, never logged, never stored. When the group cannot be
     * made, [lastError] says why ([WifiDirect.Err], the same words as the CBTN answer) and the local-network way stays open.
     */
    fun startActivation(code: String) {
        if (!castbridge.core.tv.WdCode.isValid(code)) { lastError = WifiDirect.Err.FAILED; status("Wi-Fi Direct : code de connexion inattendu"); return }
        val name = castbridge.core.tv.WdCode.networkName(code)
        if (wanted == name && (active?.first == name || starting)) return
        wanted = name; generation += 1
        start(forPhone = false, recreated = false, forceAuto = true, fixed = name to castbridge.core.tv.WdCode.passphrase(code), ticket = generation)
        // one line for `adb logcat`, never the name, the password or the code: asked, or why it cannot be
        Log.i(TAG, if (starting) "groupe d'activation : demandé à Android" else "groupe d'activation impossible : ${lastError ?: "?"}")
    }

    /** What the activation screen says about the group: up, being made, impossible (and why), or not asked. */
    fun activationState(): castbridge.core.tv.activation.ActivationScreenPlan.Group = when {
        active != null && wanted != null -> castbridge.core.tv.activation.ActivationScreenPlan.Group.Ready
        starting -> castbridge.core.tv.activation.ActivationScreenPlan.Group.Starting
        lastError != null -> castbridge.core.tv.activation.ActivationScreenPlan.Group.Failed(lastError)
        else -> castbridge.core.tv.activation.ActivationScreenPlan.Group.NotTried
    }

    private fun start(forPhone: Boolean, recreated: Boolean, forceAuto: Boolean = false, fixed: Pair<String, String>? = null, ticket: Int = 0) {
        val m = mgr
        when {
            m == null || !ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT) -> {
                lastError = WifiDirect.Err.UNSUPPORTED; status("Wi-Fi Direct : non pris en charge par cette TV"); return
            }
            Build.VERSION.SDK_INT < 29 -> { lastError = WifiDirect.Err.UNSUPPORTED; status("Wi-Fi Direct : nécessite Android 10 ou plus"); return }
            !hasPermission() -> { lastError = WifiDirect.Err.PERMISSION; status("Wi-Fi Direct : permission refusée (désactivé)"); return }
            // the radio is off: P2P is off too, and an app cannot switch it on (API 29+): said, never retried in a loop
            !wifiOn() -> { lastError = WifiDirect.Err.WIFI_OFF; status("Wi-Fi Direct : le Wi-Fi de la TV est éteint (Paramètres › Réseau)"); return }
        }
        m!!
        val activation = fixed != null
        if (activation) { starting = true; lastError = null }
        val ch = channel ?: m.initialize(ctx, Looper.getMainLooper(), null).also { channel = it }
        val name = fixed?.first ?: if (forPhone) WifiDirect.groupNetworkName() else WifiDirect.networkName()
        val pass = fixed?.second ?: if (forPhone) WifiDirect.groupPassphrase() else ownerPassphrase()
        // owner decision 2026-10-06: ask for 5 GHz first (core WdBand), fall back to Android's automatic band if the radio refuses
        // a TV without a 5 GHz radio, or one that already refused a 5 GHz group, goes straight to the automatic band
        val radio5 = runCatching { (ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager)?.is5GHzBandSupported }.getOrNull()
        val band = if (forceAuto) castbridge.core.link.WdBand.Band.AUTO else castbridge.core.link.WdBand.first(Build.VERSION.SDK_INT, radio5, (prefs.getString("wd_5ghz_failed") == "1"), forPhone)
        val cfg = config(name, pass, band) ?: run { starting = false; return }
        // A stale group from a previous run would make createGroup fail with BUSY: remove it first.
        m.removeGroup(ch, object : WifiP2pManager.ActionListener {
            override fun onSuccess() = create(m, ch, cfg, name, pass, forPhone, band, recreated, activation, ticket)
            override fun onFailure(reason: Int) = create(m, ch, cfg, name, pass, forPhone, band, recreated, activation, ticket)
        })
    }

    private fun config(name: String, pass: String, band: castbridge.core.link.WdBand.Band): WifiP2pConfig? = try {
        WifiP2pConfig.Builder().setNetworkName(name).setPassphrase(pass).enablePersistentMode(false)
            .setGroupOperatingBand(if (band == castbridge.core.link.WdBand.Band.GHZ5) WifiP2pConfig.GROUP_OWNER_BAND_5GHZ else WifiP2pConfig.GROUP_OWNER_BAND_AUTO)
            .build()
    } catch (e: Exception) { Log.w(TAG, "config: ${e.javaClass.simpleName}"); lastError = WifiDirect.Err.FAILED; status("Wi-Fi Direct : configuration refusée"); null }

    private fun create(m: WifiP2pManager, ch: WifiP2pManager.Channel, cfg: WifiP2pConfig, name: String, pass: String, forPhone: Boolean, band: castbridge.core.link.WdBand.Band, recreated: Boolean,
                       activation: Boolean = false, ticket: Int = 0) {
        // the screen closed (or reopened) while Android was removing the stale group: this request is not wanted any more
        if (activation && ticket != generation) return
        m.createGroup(ch, cfg, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                if (activation && ticket != generation) { runCatching { m.removeGroup(ch, null) }; return }       // late answer: the group is not wanted, give it back
                auto = forPhone; lastError = null; starting = false
                createdAt = System.currentTimeMillis()
                active = name to pass
                // the owner's group shows its password (it is typed on the phone); an automatic one and the activation group (its password is the QR's) never do
                status(if (activation) "Wi-Fi Direct : réseau d'activation prêt (${WifiDirect.GROUP_OWNER_IP}:8765)"
                    else if (forPhone) "Wi-Fi Direct automatique : liaison rapide avec un téléphone (${WifiDirect.GROUP_OWNER_IP}, ${castbridge.core.link.WdBand.label(band)})"
                    else "Wi-Fi Direct : réseau « $name »  mot de passe : $pass  (${WifiDirect.GROUP_OWNER_IP}:8765, ${castbridge.core.link.WdBand.label(band)})")
                // the band really obtained (AUTO may land on 2.4 GHz): one log line, read on the TV with logcat
                runCatching { m.requestGroupInfo(ch) { g -> castbridge.core.link.WdBand.frequencyLabel(g?.frequency ?: 0)?.let { Log.i(TAG, "groupe Wi-Fi Direct : $it") } } }
                // H3: a 5 GHz group nobody joins in 45 s is recreated once on the automatic band (a 2.4 GHz-only phone cannot see it)
                if (band == castbridge.core.link.WdBand.Band.GHZ5 && !recreated) {
                    val born = createdAt
                    android.os.Handler(Looper.getMainLooper()).postDelayed({
                        if (createdAt != born || active == null) return@postDelayed          // stopped or replaced meanwhile
                        clients { n ->
                            if (createdAt == born && castbridge.core.link.WdBand.recreateAuto(band, n, System.currentTimeMillis() - born, recreated)) {
                                Log.i(TAG, "personne n'a rejoint le groupe 5 GHz en 45 s : recréé en bande automatique")
                                start(forPhone, recreated = true, forceAuto = true)
                            }
                        }
                    }, castbridge.core.link.WdBand.NO_CLIENT_MS)
                }
            }
            override fun onFailure(reason: Int) {
                Log.w(TAG, "createGroup failed: $reason (${castbridge.core.link.WdBand.label(band)})")
                castbridge.core.link.WdBand.retry(band, reason)?.let { next ->
                    prefs.putString("wd_5ghz_failed", "1")      // remembered: next groups skip the 5 GHz attempt
                    val again = config(name, pass, next) ?: return
                    create(m, ch, again, name, pass, forPhone, next, recreated); return
                }
                if (activation && ticket != generation) return          // not wanted any more (screen closed): nothing to report
                starting = false
                lastError = if (reason == WifiP2pManager.P2P_UNSUPPORTED) WifiDirect.Err.UNSUPPORTED else WifiDirect.Err.FAILED
                status("Wi-Fi Direct : échec de création du groupe (" + when (reason) {
                    WifiP2pManager.P2P_UNSUPPORTED -> "non pris en charge"
                    WifiP2pManager.BUSY -> if (activation) "occupé : réessayez" else "occupé : réessayez depuis le menu"
                    else -> "code $reason"
                } + ")")
            }
        })
    }

    /** Number of phones joined to the group (null = unknown), off the caller's thread: Android answers on the main looper. */
    fun clients(cb: (Int?) -> Unit) {
        val m = mgr; val ch = channel
        if (m == null || ch == null || active == null) { cb(null); return }
        runCatching { m.requestGroupInfo(ch) { g -> cb(g?.clientList?.size) } }.onFailure { cb(null) }
    }

    /** The owner's group (MENU). */
    fun start() = start(forPhone = false)

    override fun stop() {
        val m = mgr ?: return
        val ch = channel ?: return
        active = null; auto = false
        starting = false; wanted = null; generation += 1            // an activation request still in flight is dropped (its late answer removes its group)
        runCatching { m.removeGroup(ch, null) }
        runCatching { if (Build.VERSION.SDK_INT >= 27) ch.close() }
        channel = null
        status(null)
    }

    companion object {
        private const val TAG = "CastBridgeWD"
        /** The runtime permission a Wi-Fi Direct group needs: « Appareils à proximité » (API 33+), else the location (API 29-32); null below API 29, where no group can be made at all. */
        fun permissionFor(sdk: Int): String? = when { sdk >= 33 -> Manifest.permission.NEARBY_WIFI_DEVICES; sdk >= 29 -> Manifest.permission.ACCESS_FINE_LOCATION; else -> null }
    }
}
