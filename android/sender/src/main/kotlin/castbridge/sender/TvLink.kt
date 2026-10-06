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
import castbridge.core.trust.PinKeys
import castbridge.core.trust.LinkDriver
import castbridge.core.trust.LinkState
import castbridge.core.trust.LinkView
import castbridge.core.trust.PairFlow
import castbridge.core.trust.PairStep
import castbridge.core.trust.Trigger
import castbridge.core.trust.LinkSession
import castbridge.core.trust.LinkStart
import castbridge.core.trust.PhoneLink
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

/** SharedPreferences as a [TrustPersistence] (private; excluded from backups by the manifest rules). Synchronous (R-10): the list of TVs survives a kill right after a pairing. */
class PrefsPersistence(private val sp: SharedPreferences, private val key: String) : TrustPersistence {
    override fun load(): String? = sp.getString(key, null)
    override fun save(text: String) { sp.edit().putString(key, text).commit() }
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

/** What the screens show about the plug-and-play link; the wording and the single action come from the state machine ([LinkView]). */
sealed class LinkUi {
    object NoTv : LinkUi()
    /** A TV whose credential is usable: kept (with [view] "Liaison perdue, reconnexion…") while the link is being re-established. */
    data class Connected(val session: LinkSession, val view: LinkView? = null) : LinkUi()
    /** Everything else: connecting, unreachable, forgotten by the TV, Bluetooth problems... */
    data class Status(val view: LinkView, val tv: SavedTv?) : LinkUi()
}

/**
 * Plug and play, phone side: keeps the link to the default TV alive by itself.
 *
 * The loop only sleeps and calls [LinkDriver.step] (castbridge.core.trust), which decides everything and is tested with a fake TV and a fake
 * clock: HELLO over the paired Bluetooth link, the fastest route, keep-alive, route fallback and return, token renewal at half of its life,
 * reset detection, backoff with jitter, and which failures are never retried. Wake-ups: the app opening, Bluetooth / bond / network / screen
 * broadcasts, the user's « Réessayer »; in the background a periodic job ([LinkJobService]) does the same one step.
 * The 6-digit PIN is never requested, never stored and never displayed by this path.
 */
object TvLinkManager {
    private const val TAG = "TvLink"
    private lateinit var app: Context
    private lateinit var creds: SharedPreferences
    lateinit var saved: SavedTvs; private set
    /** The last refusal of each TV (code + time, never a PIN): « Ouvrir avec » stops promising a copy the TV refused. */
    lateinit var refusals: castbridge.core.trust.LinkRefusals; private set
    lateinit var driver: LinkDriver; private set
    private lateinit var linkEnv: AndroidLinkEnv
    private val _state = MutableStateFlow<LinkUi>(LinkUi.NoTv)
    val state: StateFlow<LinkUi> = _state
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val wake = Channel<Trigger>(Channel.CONFLATED)
    @Volatile var foreground = false; private set
    private var registered = false
    private var pendingReassociate: SavedTv? = null

    @Synchronized fun init(ctx: Context) {
        if (::app.isInitialized) return
        app = ctx.applicationContext
        creds = app.getSharedPreferences("castbridge_trust", Context.MODE_PRIVATE)
        saved = SavedTvs(PrefsPersistence(creds, "tvs"))
        refusals = castbridge.core.trust.LinkRefusals(PrefsPersistence(creds, "refusals"))
        linkEnv = AndroidLinkEnv(app) { foreground }
        // R-14: the control route never picks a Wi-Fi Direct group it has not joined (its address would not answer); the bulk plane joins it when it is
        // worth it (AutoWifiDirect, core BulkRoute) and the control stays on Bluetooth meanwhile
        driver = LinkDriver(PhoneLink(AndroidBtTransport(app), linkEnv::probe, { false },
            tunnelBase = { BtSshGatewayService.apiBase(saved.default()?.address) }),     // route of last resort: the TV's API through the Bluetooth gateway
            linkEnv, saved, PrefsLinkStore(creds),
            canJoinWifiDirect = { false })
        if (_state.value is LinkUi.NoTv) publish(null)       // the first HELLO takes seconds: until then « Vérification… » for a saved TV, not the initial NoTv
        if (saved.list().isNotEmpty()) LinkJobService.schedulePeriodic(app)
    }

    /** Does this address answer like a CastBridge TV? (also used by the lots transport). */
    fun reachable(base: String): Boolean = ::linkEnv.isInitialized && linkEnv.probe(base)

    private var lotsDeliveredFor: String? = null
    /** A connected session: deferred lots go now (once per connection) and, without an IP route, the Bluetooth API gateway starts so that everything works over Bluetooth alone. */
    private fun onSession(step: LinkDriver.Step) {
        val s = step.session ?: run { lotsDeliveredFor = null; return }
        val tv = saved.default() ?: return
        if (s.tv.address != tv.address || !step.view.state.isGood) return
        if (lotsDeliveredFor != s.tv.address) { lotsDeliveredFor = s.tv.address; runCatching { LotsRuntime.requestDelivery(app) } }
        if (s.base == null && foreground && BtSshGatewayService.ensureApi(app, tv.address, s.tv.name)) wake.trySend(Trigger.APP_OPENED)
    }

    /** A grant, a token rejected by a screen... : look again soon (never forces a TV that said "I do not know you" to answer). */
    fun poke() { wake.trySend(Trigger.APP_OPENED) }

    /** The user pressed « Réessayer » / came back from the Bluetooth settings. */
    fun retryNow() { wake.trySend(Trigger.USER) }

    fun setForeground(on: Boolean) { foreground = on; if (on) { start(); poke(); recoverKnownTv() } }

    @Volatile private var recovering = false
    private val untrustedBackoff = castbridge.core.trust.UntrustedBackoff(System::currentTimeMillis)
    /** The user typed the TV's code (or the TV answers again): its refusals are forgotten. */
    fun codeAccepted(address: String) { untrustedBackoff.clear(address) }
    /**
     * Reinstalled phone app, TV already paired: the phone forgot its TVs, but Android still keeps the Bluetooth bond and the TV still trusts this phone's address.
     * So, with no TV saved, each paired device that is (or may be) a CastBridge-TV is asked for a HELLO WITHOUT the owner window: a TV that knows this phone answers
     * with a fresh token and is adopted at once; one that does not (it was reset too) is left alone and « Ajouter ma TV » stays the way. Runs in the background.
     */
    fun recoverKnownTv() {
        if (!::app.isInitialized || recovering || saved.list().isNotEmpty()) return
        recovering = true
        scope.launch {
            try {
                if (!castbridge.owner.TvBluetooth.permitted(app)) return@launch
                val link = PhoneLink(AndroidBtTransport(app), linkEnv::probe, { false })
                // R-20: only devices that declare the CastBridge service (or are already saved as a TV): never headphones, a speaker or a car
                for (c in castbridge.owner.TvBluetooth.pairedTvs(app).filter { castbridge.core.trust.RecoveryCandidates.eligible(it.sure, saved.get(it.address) != null) }) {
                    if (saved.list().isNotEmpty()) break
                    val tv = SavedTv(TrustRegistry.norm(c.address), c.name.ifBlank { "Ma TV" }, addedAt = System.currentTimeMillis())
                    // R-20: a TV that said « code 8 » is asked again after 30 s, 1 min, then every 5 min (and not at all once the user types its code)
                    if (!untrustedBackoff.mayTry(tv.address)) continue
                    val r = runCatching { link.connect(tv, requestTrust = false) }.getOrNull()
                    (r as? PhoneLink.Result.Refused)?.let {
                        refusals.record(tv.address, it.code)
                        // one message only: the card « La TV ne reconnaît plus ce téléphone » with the code field (P-62) comes from the record above
                        if (untrustedBackoff.onRefused(tv.address, it.code)) Log.i(TAG, "reprise de ${c.name}: code ${it.code} (${castbridge.core.tv.BtProtocol.describe(it.code)}), prochain essai dans 30 s")
                        return@let
                    }
                    if (r is PhoneLink.Result.Refused && r.code == castbridge.core.tv.BtProtocol.ERR_UNTRUSTED) continue
                    Log.i(TAG, "reprise de ${c.name}: ${r?.javaClass?.simpleName}" + ((r as? PhoneLink.Result.Refused)?.let { " code ${it.code} (${castbridge.core.tv.BtProtocol.describe(it.code)}) indice ${it.hint}" } ?: ""))
                    if (r is PhoneLink.Result.Connected) {
                        saved.upsert(r.session.tv, makeDefault = true)
                        LinkJobService.schedulePeriodic(app); driver.adopt(r.session); publish(driver.step(Trigger.USER)); poke()
                        break
                    }
                }
            } finally { recovering = false }
        }
    }

    @Synchronized fun start(ctx: Context? = null) {
        ctx?.let { init(it) }
        if (!::app.isInitialized) return
        if (job?.isActive == true) return
        registerTriggers()
        job = scope.launch { loop() }
    }

    /** One step from the background job: no loop, no UI. */
    fun stepOnce(trigger: Trigger) {
        if (!::app.isInitialized || saved.list().isEmpty()) return
        publish(driver.step(trigger))
    }

    // ---- credential for the existing screens: PinStore.get(key) asks here first ----

    /** The token to use instead of the PIN for [key] (TV name, "bt:<address>", "host:port"), or null (use the PIN, if any). Never a token the TV refused or that expired. */
    fun credentialFor(key: String?): String? {
        val tv = savedFor(key) ?: return null
        return driver.credential(tv.address)
    }

    /** The saved TV a screen key designates (any form: name, "(Bluetooth)", mDNS, bt:, ip, ip:port, URL; the tunnel loopback only while the gateway runs, = the TV it is connected to), see [PinKeys.resolve]. */
    fun savedFor(key: String?): SavedTv? = if (key == null || !::saved.isInitialized) null
        else PinKeys.resolve(key, saved.list(), saved.default(), tunnelPort = BtSshGatewayService.API_PORT, tunnelTv = BtSshGatewayService.apiTunnelTv())

    /** The live token for the TV answering at this base URL ("http://host:port", also the Bluetooth tunnel "http://127.0.0.1:18765" = the gateway's TV, only while it runs), or null. */
    fun credentialForBase(base: String): String? = savedFor(base)?.let { driver.credential(it.address) }

    fun savedForHost(host: String): SavedTv? = savedFor(host)

    /** What the code book ([castbridge.core.trust.PinBook]) needs to give every screen key of a TV the same record (R-10). */
    fun pinScope(): castbridge.core.trust.PinScope = if (!::saved.isInitialized) castbridge.core.trust.PinScope()
        else castbridge.core.trust.PinScope(saved.list(), saved.default(), tunnelPort = BtSshGatewayService.API_PORT, tunnelTv = BtSshGatewayService.apiTunnelTv(),
            hasToken = { driver.credential(it.address) != null })

    /** What the trusted link says about the credential of the TV [key] designates (only the default TV has a live link state). */
    fun linkFacts(key: String?): castbridge.core.trust.CredentialDecision.LinkFacts? {
        val tv = savedFor(key) ?: return null
        if (tv.address != saved.default()?.address) return null
        return when (val l = _state.value) {
            is LinkUi.Connected -> castbridge.core.trust.CredentialDecision.LinkFacts(pending = false, tokenRefused = false, tvReset = false, phoneRemoved = false)
            is LinkUi.Status -> castbridge.core.trust.CredentialDecision.linkFacts(l.view.state)
            else -> castbridge.core.trust.CredentialDecision.linkFacts(null)
        }
    }

    /** A call to the TV's API was answered "bad token": the token is dropped for good and a new HELLO follows. */
    fun tokenRejected(token: String) { if (::driver.isInitialized) { driver.reportTokenRejected(token); wake.trySend(Trigger.USER) } }

    /** Off the main thread: a step may be in the middle of a HELLO (seconds). */
    fun forget(address: String) {
        scope.launch {
            driver.forget(address)
            publish(driver.step(Trigger.USER))
            poke()
        }
    }

    fun makeDefault(address: String) { saved.setDefault(address); retryNow() }

    // ---- « Réassocier »: the TV stays saved (token dropped), then the whole pairing flow starts by itself in « Ajouter ma TV » ----

    fun requestReassociate(address: String) {
        pendingReassociate = saved.get(address)
        scope.launch { driver.prepareReassociate(address) }     // off the main thread: a step may be in the middle of a HELLO
    }

    fun takeReassociate(): SavedTv? = pendingReassociate.also { pendingReassociate = null }

    // ---- diagnostics ----

    fun diagnose(onStep: (castbridge.core.trust.DiagStep) -> Unit): castbridge.core.trust.DiagReport {
        val tv = saved.default()
        return castbridge.core.trust.Diagnostics(AndroidDiagEnv(app, linkEnv, AndroidBtTransport(app))) { addr -> PrefsLinkStore(creds).loadCredential(addr) }.run(tv, onStep)
    }

    // ---- the loop ----

    /** [step] null = nothing observed yet (cold process): never « Aucune TV » while a TV is saved ([LinkStart]). */
    private fun publish(step: LinkDriver.Step?) {
        val list = saved.list()
        val tv = saved.default()
        val s = step?.session
        val v = LinkStart.view(list.size, tv?.name, step?.view)
        // the TV's answer, remembered: a refusal (code 8…) is what the next « Ouvrir avec » must know; a good link wipes it
        if (tv != null && ::refusals.isInitialized) {
            val st = step?.view?.state
            val code = st?.let { castbridge.core.trust.LinkRefusalTexts.codeOf(it) }
            if (code != null) refusals.record(tv.address, code) else if (st != null && st.isGood) refusals.clear(tv.address)
        }
        _state.value = when {
            v == null -> LinkUi.NoTv
            tv == null -> LinkUi.Status(v, list.first())
            s != null && s.tv.address == tv.address && (v.state.isGood || v.state is LinkState.Reconnecting) -> LinkUi.Connected(s, v)
            else -> LinkUi.Status(v, tv)
        }
        val pending = (_state.value as? LinkUi.Status)?.let { castbridge.core.trust.CredentialDecision.linkFacts(it.view.state).pending } == true
        pendingSince = if (!pending) null else pendingSince ?: System.currentTimeMillis()
    }

    @Volatile private var pendingSince: Long? = null
    /** How long the default TV's link has been connecting / reconnecting (0 = not): « aucun code à saisir » is said for a bounded time only. */
    fun pendingForMs(): Long = pendingSince?.let { System.currentTimeMillis() - it } ?: 0

    private suspend fun loop() {
        var trigger = Trigger.APP_OPENED
        while (scope.isActive) {
            val step = driver.step(trigger)
            publish(step)
            onSession(step)
            val d = step.nextInMs
            trigger = if (d == null) wake.receive() else withTimeoutOrNull(d) { wake.receive() } ?: Trigger.TIMER
        }
    }

    // ---- wake-ups: Bluetooth on/off, ACL up/down, bond, screen, the network ----

    @Synchronized private fun registerTriggers() {
        if (registered) return
        registered = true
        val f = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED); addAction(BluetoothDevice.ACTION_ACL_CONNECTED); addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED); addAction(Intent.ACTION_SCREEN_ON)
        }
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                wake.trySend(when (i.action) {
                    BluetoothDevice.ACTION_ACL_CONNECTED -> Trigger.ACL_CONNECTED
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> Trigger.ACL_DISCONNECTED
                    BluetoothAdapter.ACTION_STATE_CHANGED -> Trigger.BLUETOOTH_STATE
                    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> Trigger.BOND_STATE
                    else -> Trigger.SCREEN_ON
                })
            }
        }
        registerSystemReceiver(app, r, f)
        runCatching {
            app.getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { wake.trySend(Trigger.NETWORK) }
                override fun onLost(network: Network) { wake.trySend(Trigger.NETWORK) }
            })
        }.onFailure { Log.w(TAG, "network callback: ${it.javaClass.simpleName}") }
    }

    // ---- « Ajouter ma TV » / « Réassocier » ----

    /**
     * Pairs (Android's numeric comparison on both screens), asks the TV's owner to approve this phone, and repairs a stale bond on the way
     * ([PairFlow]). Blocks: call from IO. The last step passed to [onStep] is [PairStep.Done] or [PairStep.Failed].
     */
    fun pair(candidate: TvCandidate, onStep: (PairStep) -> Unit) {
        val address = TrustRegistry.norm(candidate.address)
        // the install id of an earlier pairing is meaningless for a new one: do not claim it
        val tv0 = (saved.get(address) ?: SavedTv(address, candidate.name.ifBlank { "Ma TV" }, addedAt = System.currentTimeMillis())).copy(installId = null)
        val flow = PairFlow(PhoneLink(AndroidBtTransport(app), linkEnv::probe, { false }), AndroidPairEnv(app, linkEnv))
        val r = flow.run(tv0, onStep)
        if (r is PairStep.Done) { LinkJobService.schedulePeriodic(app); driver.adopt(r.session); publish(driver.step(Trigger.USER)); poke() }
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
