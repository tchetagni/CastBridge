package castbridge.sender

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import castbridge.core.tv.DiscoverySupervisor
import castbridge.core.tv.ReceiverServer
import castbridge.core.tv.SerialResolveQueue
import castbridge.core.tv.TvNameMatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * [locked]: the TV announces `locked=1` (not activated yet: its only open route is the activation, docs/TV-ACTIVATION-CLE-USB.md).
 * [otherHost]: another address announced the SAME name after this one (audit M2): the first address is kept, the screen says there are two.
 */
data class Tv(val name: String, val host: String, val port: Int, val locked: Boolean = false, val otherHost: String? = null) {
    val base get() = "http://$host:$port"
}

/**
 * Finds CastBridge TV receivers via mDNS (_castbridge._tcp, attribute role=receiver; the PC server
 * announces role=server and is skipped). Resolutions are serialized: NsdManager rejects parallel ones.
 *
 * R-19: start / stop / restart go through [DiscoverySupervisor] (core): a restart waits for Android's real stop before starting again, a failed start
 * is retried (1 s, 2 s, 5 s, 10 s, then every 15 s) instead of killing the discovery for good; each resolution has a 5 s guard ([SerialResolveQueue]).
 */
class TvDiscovery(ctx: Context) {
    private val app = ctx.applicationContext
    private val nsd = app.getSystemService(NsdManager::class.java)
    private val _tvs = MutableStateFlow<List<Tv>>(emptyList())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    /** The TVs of the network; if none answers and the phone's Bluetooth API gateway runs, the TV through it (127.0.0.1, "Bluetooth"). */
    val tvs: StateFlow<List<Tv>> = combine(_tvs, BtSshGatewayService.state) { lan, gw ->
        // also next to stale Wi-Fi entries (a TV that left the Wi-Fi may still be announced): the TV through the gateway stays choosable
        if (gw.api.running) lan + Tv("${gw.tv} (Bluetooth)", "127.0.0.1", BtSshGatewayService.API_PORT) else lan
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val sup = DiscoverySupervisor()
    private val resolves = SerialResolveQueue<NsdServiceInfo>(RESOLVE_GUARD_MS)
    private var listener: NsdManager.DiscoveryListener? = null
    private var lock: WifiManager.MulticastLock? = null
    /** Bumped at each Android call: a late timer of a previous start/stop changes nothing. */
    private var gen = 0L

    fun start() = act(sup.start())

    @Synchronized fun stop() {
        act(sup.stop())
        resolves.clear()
        runCatching { lock?.release() }; lock = null
    }

    /** Restart discovery, e.g. after a network change, so the TV's new address is found. [clear] = forget the TVs seen so far (their address may be stale). */
    fun restart(clear: Boolean = true) {
        if (clear) { _tvs.value = emptyList(); resolves.clear() }
        act(sup.restart())
    }

    /** The TV of that exact name (screens), see [findTolerant] for the upload. */
    fun find(name: String): Tv? = tvs.value.firstOrNull { it.name == name } ?: _tvs.value.firstOrNull { it.name == name }

    /**
     * R-19: the upload's TV among the announced ones: exact, then the same base name (Android's « (2) », the « CastBridge TV » prefix) when it
     * designates one TV, then the only TV of the network ([TvNameMatch]); never a TV [otherTv] says is another one.
     */
    fun findTolerant(name: String, otherTv: (String) -> Boolean = { false }): Tv? {
        find(name)?.let { return it }
        val lan = _tvs.value.filterNot { it.locked }
        val p = TvNameMatch.pick(name, lan.map { it.name }, otherTv) ?: return null
        return lan.firstOrNull { it.name == p.name }
    }

    @Synchronized private fun act(a: DiscoverySupervisor.Action) {
        when (a) {
            DiscoverySupervisor.Action.None -> {}
            DiscoverySupervisor.Action.Start -> doStart()
            DiscoverySupervisor.Action.Stop -> doStop()
            is DiscoverySupervisor.Action.RetryIn -> { val g = gen; scope.launch { delay(a.ms); if (stillGen(g)) act(sup.onRetryDue()) } }
        }
    }

    @Synchronized private fun stillGen(g: Long) = g == gen

    private fun doStart() {
        val g = ++gen
        if (lock == null) lock = runCatching {
            (app.getSystemService(Context.WIFI_SERVICE) as WifiManager)
                .createMulticastLock("castbridge-nsd").apply { setReferenceCounted(false); acquire() }
        }.getOrNull()
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(t: String) {
                // a start given up by its guard that comes after all: stopped at once (never two discoveries)
                if (!isCurrent(this)) { runCatching { nsd.stopServiceDiscovery(this) }; return }
                act(sup.onStarted())
            }
            override fun onDiscoveryStopped(t: String) { if (isStopping(this)) act(sup.onStopped()) }
            override fun onStartDiscoveryFailed(t: String, e: Int) {
                Log.w(TAG, "discovery failed $e: retried")
                if (dropCurrent(this)) act(sup.onStartFailed())
            }
            override fun onStopDiscoveryFailed(t: String, e: Int) { if (isStopping(this)) act(sup.onStopped()) }
            override fun onServiceFound(i: NsdServiceInfo) = enqueue(i)
            override fun onServiceLost(i: NsdServiceInfo) {
                _tvs.value = _tvs.value.filterNot { it.name == i.serviceName }
            }
        }
        listener = l
        runCatching { nsd.discoverServices(ReceiverServer.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, l) }
            .onFailure { Log.w(TAG, "discoverServices", it); listener = null; scope.launch { act(sup.onStartFailed()) }; return }
        // onDiscoveryStarted never came: treated as a failure, so the bounded retry takes over
        scope.launch {
            delay(START_GUARD_MS)
            if (stillGen(g) && sup.phase == DiscoverySupervisor.Phase.STARTING && dropCurrent(l)) { runCatching { nsd.stopServiceDiscovery(l) }; act(sup.onStartFailed()) }
        }
    }

    /** The discovery being stopped (its onDiscoveryStopped ends the stop). */
    private var stopping: NsdManager.DiscoveryListener? = null
    @Synchronized private fun isCurrent(l: NsdManager.DiscoveryListener) = listener === l
    @Synchronized private fun dropCurrent(l: NsdManager.DiscoveryListener): Boolean { if (listener !== l) return false; listener = null; return true }
    @Synchronized private fun isStopping(l: NsdManager.DiscoveryListener): Boolean { if (stopping !== l) return false; stopping = null; return true }

    private fun doStop() {
        val g = ++gen
        val l = listener
        listener = null; stopping = l
        if (l == null || runCatching { nsd.stopServiceDiscovery(l) }.isFailure) { stopping = null; scope.launch { act(sup.onStopped()) }; return }
        // onDiscoveryStopped is sometimes never delivered: after a moment the stop is taken as done
        scope.launch { delay(STOP_GUARD_MS); if (stillGen(g) && isStopping(l)) act(sup.onStopTimeout()) }
    }

    private fun enqueue(i: NsdServiceInfo) { resolves.enqueue(i, System.currentTimeMillis())?.let { resolve(it) } }

    private fun resolve(n: SerialResolveQueue.Next<NsdServiceInfo>) {
        val tok = n.token
        // the guard: a resolution whose callback never comes no longer freezes the queue
        scope.launch { delay(RESOLVE_GUARD_MS); resolves.timeout(tok, System.currentTimeMillis())?.let { resolve(it) } }
        @Suppress("DEPRECATION")
        runCatching {
            nsd.resolveService(n.item, object : NsdManager.ResolveListener {
                override fun onResolveFailed(s: NsdServiceInfo, e: Int) { Log.w(TAG, "resolve ${s.serviceName}: $e"); next(tok) }
                override fun onServiceResolved(s: NsdServiceInfo) {
                    val role = s.attributes["role"]?.let { String(it) }
                    val host = s.host?.hostAddress
                    if (role == "receiver" && host != null) {
                        val tv = Tv(s.serviceName, host, s.port, locked = s.attributes["locked"]?.let { String(it) } == "1")
                        // never replaced silently by a same-name announce from another address (audit M2): the oldest is kept, the other one flagged
                        _tvs.value = castbridge.core.owner.ActivationSend.mergeAnnounce(_tvs.value, tv, { it.name }, { "${it.host}:${it.port}" }, { it.otherHost }, { t, o -> t.copy(otherHost = o) })
                    }
                    next(tok)
                }
            })
        }.onFailure { next(tok) }
    }

    private fun next(tok: Long) { resolves.done(tok, System.currentTimeMillis())?.let { resolve(it) } }

    companion object {
        private const val TAG = "TvDiscovery"
        private const val RESOLVE_GUARD_MS = 5_000L
        private const val STOP_GUARD_MS = 3_000L
        private const val START_GUARD_MS = 10_000L
    }
}
