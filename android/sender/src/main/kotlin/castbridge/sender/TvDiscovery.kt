package castbridge.sender

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import castbridge.core.tv.ReceiverServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.StateFlow
import java.util.ArrayDeque

data class Tv(val name: String, val host: String, val port: Int) {
    val base get() = "http://$host:$port"
}

/**
 * Finds CastBridge TV receivers via mDNS (_castbridge._tcp, attribute role=receiver; the PC server
 * announces role=server and is skipped). Resolutions are serialized: NsdManager rejects parallel ones.
 */
class TvDiscovery(ctx: Context) {
    private val app = ctx.applicationContext
    private val nsd = app.getSystemService(NsdManager::class.java)
    private val _tvs = MutableStateFlow<List<Tv>>(emptyList())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    /** The TVs of the network; if none answers and the phone's Bluetooth API gateway runs, the TV through it (127.0.0.1, "Bluetooth"). */
    val tvs: StateFlow<List<Tv>> = combine(_tvs, BtSshGatewayService.state) { lan, gw ->
        if (lan.isEmpty() && gw.api.running) listOf(Tv("${gw.tv} (Bluetooth)", "127.0.0.1", BtSshGatewayService.API_PORT)) else lan
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val queue = ArrayDeque<NsdServiceInfo>()
    private var resolving = false
    private var listener: NsdManager.DiscoveryListener? = null
    private var lock: WifiManager.MulticastLock? = null

    @Synchronized fun start() {
        if (listener != null) return
        lock = runCatching {
            (app.getSystemService(Context.WIFI_SERVICE) as WifiManager)
                .createMulticastLock("castbridge-nsd").apply { setReferenceCounted(false); acquire() }
        }.getOrNull()
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(t: String) {}
            override fun onDiscoveryStopped(t: String) {}
            override fun onStartDiscoveryFailed(t: String, e: Int) { Log.w(TAG, "discovery failed $e"); synchronized(this@TvDiscovery) { listener = null } }
            override fun onStopDiscoveryFailed(t: String, e: Int) {}
            override fun onServiceFound(i: NsdServiceInfo) = enqueue(i)
            override fun onServiceLost(i: NsdServiceInfo) {
                _tvs.value = _tvs.value.filterNot { it.name == i.serviceName }
            }
        }
        listener = l
        runCatching { nsd.discoverServices(ReceiverServer.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, l) }
            .onFailure { Log.w(TAG, "discoverServices", it); listener = null }
    }

    @Synchronized fun stop() {
        listener?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        listener = null
        runCatching { lock?.release() }
    }

    /** Restart discovery, e.g. after a network change, so the TV's new address is found. */
    fun restart() { stop(); _tvs.value = emptyList(); start() }

    fun find(name: String): Tv? = tvs.value.firstOrNull { it.name == name } ?: _tvs.value.firstOrNull { it.name == name }

    @Synchronized private fun enqueue(i: NsdServiceInfo) {
        queue.add(i)
        if (!resolving) next()
    }

    @Synchronized private fun next() {
        val i = queue.poll() ?: run { resolving = false; return }
        resolving = true
        @Suppress("DEPRECATION")
        runCatching {
            nsd.resolveService(i, object : NsdManager.ResolveListener {
                override fun onResolveFailed(s: NsdServiceInfo, e: Int) { Log.w(TAG, "resolve ${s.serviceName}: $e"); next() }
                override fun onServiceResolved(s: NsdServiceInfo) {
                    val role = s.attributes["role"]?.let { String(it) }
                    val host = s.host?.hostAddress
                    if (role == "receiver" && host != null) {
                        val tv = Tv(s.serviceName, host, s.port)
                        _tvs.value = _tvs.value.filterNot { it.name == tv.name } + tv
                    }
                    next()
                }
            })
        }.onFailure { next() }
    }

    companion object { private const val TAG = "TvDiscovery" }
}
