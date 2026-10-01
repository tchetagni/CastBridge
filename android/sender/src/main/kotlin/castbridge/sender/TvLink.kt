package castbridge.sender

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import castbridge.core.trust.BtTransport
import castbridge.core.trust.BtUnavailable
import castbridge.core.trust.Candidates
import castbridge.core.trust.LinkSession
import castbridge.core.trust.PhoneLink
import castbridge.core.trust.ReconnectPolicy
import castbridge.core.trust.SavedTv
import castbridge.core.trust.SavedTvs
import castbridge.core.trust.TrustPersistence
import castbridge.core.trust.TrustRegistry
import castbridge.core.trust.TvCandidate
import castbridge.core.tv.BtProtocol
import castbridge.core.tv.Link
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** SharedPreferences as a [TrustPersistence] (private; excluded from backups by the manifest rules). */
class PrefsPersistence(private val sp: SharedPreferences, private val key: String) : TrustPersistence {
    override fun load(): String? = sp.getString(key, null)
    override fun save(text: String) { sp.edit().putString(key, text).apply() }
}

/**
 * The secure (paired, encrypted) RFCOMM link to the TV's CBT1 service. `createRfcommSocketToServiceRecord`, never the
 * "insecure" variant: Android authenticates the TV with the pairing key before the socket opens.
 */
@SuppressLint("MissingPermission")
class AndroidBtTransport(private val ctx: Context) : BtTransport {
    override fun connect(address: String): Link {
        val adapter = ctx.getSystemService(BluetoothManager::class.java)?.adapter ?: throw BtUnavailable(BtUnavailable.Reason.NO_ADAPTER)
        if (!hasBtPermission(ctx)) throw BtUnavailable(BtUnavailable.Reason.NO_PERMISSION)
        if (!adapter.isEnabled) throw BtUnavailable(BtUnavailable.Reason.OFF)
        runCatching { adapter.cancelDiscovery() }          // a running discovery slows and breaks connections; needs SCAN, optional
        val sock = adapter.getRemoteDevice(address).createRfcommSocketToServiceRecord(UUID.fromString(BtProtocol.SERVICE_UUID))
        try { sock.connect() } catch (e: IOException) { runCatching { sock.close() }; throw e }
        return object : Link {
            override val input = sock.inputStream
            override val output = sock.outputStream
            override fun close() { runCatching { sock.close() } }
        }
    }
}

/** What the screens show about the plug-and-play link. */
sealed class LinkUi {
    object NoTv : LinkUi()
    data class BluetoothProblem(val reason: BtUnavailable.Reason) : LinkUi()
    data class Connecting(val tv: SavedTv) : LinkUi()
    data class Connected(val session: LinkSession) : LinkUi()
    /** The TV does not answer (off, out of range, Bluetooth off there): retried quietly. */
    data class Absent(val tv: SavedTv, val why: String, val failures: Int = 0) : LinkUi()
    /** The TV answered "I do not know you" (or refused): the user has to add it again. */
    data class Refused(val tv: SavedTv, val code: Int, val message: String, val needsPairing: Boolean) : LinkUi()
}

/**
 * Plug and play, phone side: keeps the link to the default TV alive by itself.
 *
 * Opening the app (or the screen turning on, the network changing, Bluetooth coming back, the TV's link appearing) wakes the loop;
 * it asks the TV "HELLO" over the paired Bluetooth link, receives the TV's name, Wi-Fi addresses and this phone's token, tests which
 * address answers, and publishes [state]. The token is renewed in the background at half of its life, so nobody is ever asked for
 * a code. When the TV is off it retries with a growing delay (2 s ... 1 min) only while the app is visible.
 * The 6-digit PIN is never requested, never stored and never displayed by this path.
 */
object TvLinkManager {
    private const val TAG = "TvLink"
    private lateinit var app: Context
    private lateinit var creds: SharedPreferences
    lateinit var saved: SavedTvs; private set
    private val _state = MutableStateFlow<LinkUi>(LinkUi.NoTv)
    val state: StateFlow<LinkUi> = _state
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val wake = Channel<Unit>(Channel.CONFLATED)
    @Volatile var foreground = false; private set
    @Volatile private var session: LinkSession? = null
    @Volatile private var issuedAt = 0L
    private var registered = false

    @Synchronized fun init(ctx: Context) {
        if (::app.isInitialized) return
        app = ctx.applicationContext
        creds = app.getSharedPreferences("castbridge_trust", Context.MODE_PRIVATE)
        saved = SavedTvs(PrefsPersistence(creds, "tvs"))
        if (saved.list().isEmpty()) _state.value = LinkUi.NoTv
    }

    /** The phone's "BLUETOOTH_CONNECT" etc. may be granted later: the screens call this after a grant. */
    fun poke() { wake.trySend(Unit) }

    fun setForeground(on: Boolean) { foreground = on; if (on) { start(); poke() } }

    @Synchronized fun start(ctx: Context? = null) {
        ctx?.let { init(it) }
        if (!::app.isInitialized) return
        if (job?.isActive == true) return
        registerTriggers()
        job = scope.launch { loop() }
    }

    // ---- credential for the existing screens: PinStore.get(key) asks here first ----

    /** The token to use instead of the PIN for [key] (TV name, "bt:<address>", "host:port"), or null (use the PIN, if any). */
    fun credentialFor(key: String?): String? {
        val tv = savedFor(key) ?: return null
        val raw = creds.getString("cred.${tv.address}", null) ?: return null
        val (token, exp) = raw.split('|').takeIf { it.size == 2 } ?: return null
        return token.takeIf { (exp.toLongOrNull() ?: 0) > System.currentTimeMillis() }
    }

    fun savedFor(key: String?): SavedTv? = if (key == null || !::saved.isInitialized) null
        else saved.list().firstOrNull { t -> key == t.mdns || key == t.name || key == "bt:${t.address}" || t.lastIps.any { "$it:${t.port}" == key } }

    private fun storeCredential(s: LinkSession) {
        creds.edit().putString("cred.${s.tv.address}", "${s.credential}|${s.expiresAt}").apply()
    }

    fun forget(address: String) {
        saved.remove(address)
        creds.edit().remove("cred.${TrustRegistry.norm(address)}").apply()
        if (session?.tv?.address == TrustRegistry.norm(address)) { session = null }
        _state.value = saved.default()?.let { LinkUi.Connecting(it) } ?: LinkUi.NoTv
        poke()
    }

    fun makeDefault(address: String) { saved.setDefault(address); session = null; poke() }

    // ---- the loop ----

    private fun btProblem(): BtUnavailable.Reason? {
        val ad = app.getSystemService(BluetoothManager::class.java)?.adapter ?: return BtUnavailable.Reason.NO_ADAPTER
        if (!hasBtPermission(app)) return BtUnavailable.Reason.NO_PERMISSION
        @SuppressLint("MissingPermission") val on = runCatching { ad.isEnabled }.getOrDefault(false)
        return if (on) null else BtUnavailable.Reason.OFF
    }

    private fun link() = PhoneLink(AndroidBtTransport(app), ::reachable, { Build.VERSION.SDK_INT >= 29 },
        tunnelBase = { BtSshGatewayService.apiBase(saved.default()?.address) })     // route of last resort: the TV's API through the Bluetooth gateway

    /** Does this address answer like a CastBridge TV? (short timeout: the phone may simply be on another network). */
    fun reachable(base: String): Boolean = runCatching {
        val c = java.net.URL("$base/api/hello").openConnection() as java.net.HttpURLConnection
        val slow = if (base.startsWith("http://127.0.0.1")) 10_000 else 1200       // through the Bluetooth gateway: connecting the RFCOMM link takes seconds
        c.connectTimeout = slow; c.readTimeout = slow
        c.responseCode == 200 && c.inputStream.use { it.readBytes() }.decodeToString().contains("castbridge-tv")
    }.getOrDefault(false)

    private suspend fun pause(ms: Long) { withTimeoutOrNull(ms) { wake.receive() } }

    private suspend fun loop() {
        var failures = 0
        while (scope.isActive) {
            val tv = saved.default()
            if (tv == null) { session = null; _state.value = LinkUi.NoTv; wake.receive(); continue }
            val problem = btProblem()
            if (problem != null) {
                session = null; _state.value = LinkUi.BluetoothProblem(problem)
                pause(if (foreground) 5_000 else 10 * 60_000L)       // Bluetooth coming back or a permission grant also wake the loop
                continue
            }
            // Connected: keep it (renew at half of the token's life, verify the Wi-Fi address from time to time).
            val cur = session
            if (cur != null && cur.tv.address == tv.address) {
                val due = ReconnectPolicy.renewAt(issuedAt, cur.expiresAt)
                val now = System.currentTimeMillis()
                val stillGood = now < due && (cur.base?.let { reachable(it) } ?: (now - issuedAt < 60_000))
                if (stillGood) { pause(minOf(due - now, 15_000).coerceAtLeast(1_000)); continue }
            }
            // keep the "introuvable" card steady while retrying: flipping to "Connexion…" at every attempt made its buttons vanish under the finger
            if (session == null && _state.value !is LinkUi.Absent) _state.value = LinkUi.Connecting(tv)
            when (val r = link().connect(tv)) {
                is PhoneLink.Result.Connected -> {
                    failures = 0
                    val s = r.session
                    session = s; issuedAt = System.currentTimeMillis()
                    saved.upsert(s.tv)
                    storeCredential(s)
                    _state.value = LinkUi.Connected(s)
                    runCatching { LotsRuntime.requestDelivery(app) }     // deferred lots waiting for this TV go now (no-op when the queue is empty)
                    // No IP route: start the Bluetooth API gateway (visible app only) and plan again, so that the library, the remote,
                    // the parental settings... work over Bluetooth alone.
                    if (s.base == null && foreground && BtSshGatewayService.ensureApi(app, tv.address, s.tv.name)) { session = null; pause(2_000) }
                }
                is PhoneLink.Result.TvAbsent -> {
                    failures++; session = null
                    _state.value = LinkUi.Absent(tv, r.why, failures)
                    if (foreground) pause(ReconnectPolicy.retryDelayMs(failures)) else wake.receive()
                }
                is PhoneLink.Result.BluetoothProblem -> { session = null; _state.value = LinkUi.BluetoothProblem(r.reason); pause(5_000) }
                is PhoneLink.Result.Refused -> {
                    session = null
                    _state.value = LinkUi.Refused(tv, r.code, r.message, r.needsPairing)
                    // a refusal is not a network glitch: wait for the user (Retry / Add again) or for the app to come back
                    if (r.code == BtProtocol.ERR_MAGIC || r.needsPairing) wake.receive() else pause(30_000)
                }
            }
        }
    }

    // ---- wake-ups: Bluetooth on/off, the TV's link, the network ----

    @Synchronized private fun registerTriggers() {
        if (registered) return
        registered = true
        val f = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED); addAction(BluetoothDevice.ACTION_ACL_CONNECTED); addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }
        val r = object : BroadcastReceiver() { override fun onReceive(c: Context, i: Intent) { poke() } }
        if (Build.VERSION.SDK_INT >= 33) app.registerReceiver(r, f, Context.RECEIVER_EXPORTED) else app.registerReceiver(r, f)
        runCatching {
            app.getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { poke() }
                override fun onLost(network: Network) { poke() }
            })
        }.onFailure { Log.w(TAG, "network callback: ${it.javaClass.simpleName}") }
    }

    // ---- « Ajouter ma TV » ----

    sealed class PairStep {
        object Bonding : PairStep()
        object WaitingOwner : PairStep()
        data class Done(val tv: SavedTv) : PairStep()
        data class Failed(val message: String, val retry: Boolean) : PairStep()
    }

    /** Pairs (Android's numeric comparison on both screens), then asks the TV's owner to approve this phone. Blocks: call from IO. */
    @SuppressLint("MissingPermission")
    fun pair(candidate: TvCandidate, onStep: (PairStep) -> Unit) {
        val ad = app.getSystemService(BluetoothManager::class.java)?.adapter
        if (ad == null || !ad.isEnabled) { onStep(PairStep.Failed("Activez le Bluetooth du téléphone.", true)); return }
        runCatching { ad.cancelDiscovery() }
        val dev = ad.getRemoteDevice(candidate.address)
        onStep(PairStep.Bonding)
        if (!ensureBonded(dev)) { onStep(PairStep.Failed("L'association Bluetooth n'a pas abouti. Vérifiez que les codes affichés sur le téléphone et sur la TV sont identiques, puis réessayez.", true)); return }
        onStep(PairStep.WaitingOwner)
        val tv0 = SavedTv(TrustRegistry.norm(candidate.address), candidate.name.ifBlank { "Ma TV" }, addedAt = System.currentTimeMillis())
        when (val r = link().connect(tv0, requestTrust = true)) {
            is PhoneLink.Result.Connected -> {
                val s = r.session
                saved.upsert(s.tv, makeDefault = true); storeCredential(s)
                session = s; issuedAt = System.currentTimeMillis(); _state.value = LinkUi.Connected(s)
                poke()
                onStep(PairStep.Done(s.tv))
            }
            is PhoneLink.Result.Refused -> onStep(PairStep.Failed(r.message, r.code != BtProtocol.ERR_DENIED))
            is PhoneLink.Result.TvAbsent -> onStep(PairStep.Failed("La TV ne répond pas : ouvrez CastBridge TV sur la TV, écran « Ajouter un téléphone », puis réessayez.", true))
            is PhoneLink.Result.BluetoothProblem -> onStep(PairStep.Failed("Bluetooth indisponible sur ce téléphone.", false))
        }
    }

    @SuppressLint("MissingPermission")
    private fun ensureBonded(dev: BluetoothDevice, timeoutMs: Long = 90_000): Boolean {
        if (dev.bondState == BluetoothDevice.BOND_BONDED) return true
        val latch = CountDownLatch(1)
        var ok = false
        val r = object : BroadcastReceiver() {
            @Suppress("DEPRECATION")
            override fun onReceive(c: Context, i: Intent) {
                val d = i.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                if (d?.address != dev.address) return
                when (i.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)) {
                    BluetoothDevice.BOND_BONDED -> { ok = true; latch.countDown() }
                    BluetoothDevice.BOND_NONE -> if (i.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, 0) == BluetoothDevice.BOND_BONDING) latch.countDown()
                }
            }
        }
        app.registerReceiver(r, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED))
        try {
            if (!dev.createBond()) return dev.bondState == BluetoothDevice.BOND_BONDED
            latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        } finally { runCatching { app.unregisterReceiver(r) } }
        return ok || dev.bondState == BluetoothDevice.BOND_BONDED
    }
}

/**
 * Looks for CastBridge TVs: Bluetooth devices already paired with the phone that offer the CBT1 service, and unpaired ones that are
 * visible right now (the TV makes itself visible for 2 minutes on its « Ajouter un téléphone » screen). The service check is
 * Android's SDP query, so a speaker or a watch is never proposed as a TV.
 */
@SuppressLint("MissingPermission")
class BtFinder(private val ctx: Context) {
    private val _found = MutableStateFlow<List<TvCandidate>>(emptyList())
    val found: StateFlow<List<TvCandidate>> = _found
    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning
    private val seen = LinkedHashMap<String, TvCandidate>()
    private var receiver: BroadcastReceiver? = null
    private val cbt = ParcelUuid(UUID.fromString(BtProtocol.SERVICE_UUID))

    private fun adapter() = ctx.getSystemService(BluetoothManager::class.java)?.adapter

    @Synchronized private fun put(d: BluetoothDevice, uuids: Array<out android.os.Parcelable>?) {
        val name = runCatching { d.name }.getOrNull().orEmpty()
        val has: Boolean? = uuids?.let { u -> u.any { (it as? ParcelUuid) == cbt } }
        val old = seen[d.address]
        seen[d.address] = TvCandidate(d.address, name.ifBlank { old?.name.orEmpty() }, d.bondState == BluetoothDevice.BOND_BONDED, has ?: old?.hasCbt1)
        _found.value = Candidates.merge(seen.values)
    }

    @Suppress("DEPRECATION")
    @Synchronized fun start() {
        if (receiver != null) return
        val ad = adapter() ?: return
        if (!hasBtPermission(ctx) || !ad.isEnabled) return
        seen.clear(); _found.value = emptyList()
        // paired devices first (instant)
        ad.bondedDevices.orEmpty().forEach { d ->
            put(d, d.uuids)
            if (d.uuids.isNullOrEmpty()) runCatching { d.fetchUuidsWithSdp() }
        }
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val d = i.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
                when (i.action) {
                    BluetoothDevice.ACTION_FOUND -> { put(d, null); runCatching { d.fetchUuidsWithSdp() } }
                    BluetoothDevice.ACTION_UUID -> put(d, i.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID))
                    BluetoothDevice.ACTION_NAME_CHANGED -> put(d, null)
                }
            }
        }
        val f = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND); addAction(BluetoothDevice.ACTION_UUID); addAction(BluetoothDevice.ACTION_NAME_CHANGED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        val fin = object : BroadcastReceiver() { override fun onReceive(c: Context, i: Intent) { _scanning.value = false } }
        ctx.registerReceiver(r, f); ctx.registerReceiver(fin, IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED))
        receiver = r
        stopper ={ runCatching { ctx.unregisterReceiver(r) }; runCatching { ctx.unregisterReceiver(fin) } }
        _scanning.value = runCatching { ad.startDiscovery() }.getOrDefault(false)
    }

    private var stopper: (() -> Unit)? = null

    @Synchronized fun stop() {
        stopper?.invoke(); stopper = null; receiver = null
        runCatching { adapter()?.cancelDiscovery() }
        _scanning.value = false
    }
}
