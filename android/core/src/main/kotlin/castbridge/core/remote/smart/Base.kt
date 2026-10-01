package castbridge.core.remote.smart

import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/** Shared plumbing: state, logging (messages are kept free of secrets; [Redact] is applied again by the journal), TCP reachability. */
abstract class BaseStrategy(protected val tv: TvTarget, protected val log: (String) -> Unit = {}) : RemoteStrategy {
    @Volatile override var state: StrategyState = StrategyState.IDLE
        protected set

    protected fun ready(detail: String? = null) { state = StrategyState(StrategyState.Kind.READY, detail) }
    protected fun failed(detail: String): Nothing { state = StrategyState(StrategyState.Kind.FAILED, detail); throw IOException(detail) }
    protected fun needsPairing(detail: String): Nothing {
        state = StrategyState(StrategyState.Kind.NEEDS_PAIRING, detail); throw StrategyException(detail, needsPairing = true)
    }

    /** True when something accepts a TCP connection on [port] (read-only: nothing is sent). */
    protected fun tcpOpen(port: Int): Boolean = try {
        Socket().use { it.connect(InetSocketAddress(tv.host, port), minOf(tv.connectTimeoutMs, 1000)); true }
    } catch (e: IOException) { false }

    protected fun http(method: String, url: String, body: String? = null, headers: Map<String, String> = emptyMap(), trust: Boolean = false): Http.Result {
        require(java.net.URI(url).host.let { it != null && tv.sameHost(it) }) { "adresse hors de la TV choisie" }
        return Http.request(method, url, body, headers, tv.connectTimeoutMs, tv.readTimeoutMs, if (trust) tv.host else null)
    }

    override fun probe(): ProbeResult = ProbeResult(false, "non sondée")
    override fun close() { state = StrategyState.CLOSED }
}

/** A key that this strategy cannot send. The orchestrator does NOT fall over on it (the link is fine). */
class KeyUnsupported(val key: castbridge.core.remote.RemoteKey, strategy: String) : IOException("touche ${key.label} indisponible avec $strategy")
