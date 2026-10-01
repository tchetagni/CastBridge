package castbridge.core.remote.smart

import castbridge.core.remote.RemoteKey
import java.io.IOException

/** STABLE: tested against a local fake server (and, for some, on real hardware). EXPERIMENTAL: off by default (docs/REMOTE.md). */
enum class StrategyStatus { STABLE, EXPERIMENTAL }

/** What a strategy can do; the remote screen hides the keys that are not in [keys]. */
data class Capabilities(
    val keys: Set<RemoteKey> = emptySet(),
    val text: Boolean = false,
    val touchpad: Boolean = false,
    val apps: Boolean = false,
) {
    val volume: Boolean get() = RemoteKey.VOLUME_UP in keys && RemoteKey.VOLUME_DOWN in keys
    fun has(k: RemoteKey) = k in keys
}

data class StrategyState(val kind: Kind, val detail: String? = null) {
    enum class Kind { IDLE, CONNECTING, READY, NEEDS_PAIRING, FAILED, CLOSED }
    companion object {
        val IDLE = StrategyState(Kind.IDLE)
        val CLOSED = StrategyState(Kind.CLOSED)
    }
}

/** Result of a non-intrusive [RemoteStrategy.probe]: nothing is sent to the TV beyond a read-only request. */
data class ProbeResult(val reachable: Boolean, val detail: String? = null)

/** [needsPairing]: the TV waits for a code / an on-screen approval (not a failure). [message] is French and shown to the user. */
class StrategyException(msg: String, val needsPairing: Boolean = false, cause: Throwable? = null) : IOException(msg, cause)

/** One way of driving a TV. Always bound to ONE TV that the user chose; never created for a device found by scanning. */
interface RemoteStrategy {
    val id: String
    val label: String
    val status: StrategyStatus
    val capabilities: Capabilities
    val state: StrategyState
    /** True when the TV itself answers each key (HTTP status, protocol ack): a successful send is already a proof of delivery. */
    val verifiesDelivery: Boolean get() = false
    /** Plain-language limits for this strategy (shown in « Ma TV »). */
    val limits: String get() = ""
    /** Network-risk notes for the owner (e.g. the TV accepts any device on the Wi-Fi). */
    val warnings: List<String> get() = emptyList()

    fun applicable(fp: TvFingerprint): Boolean
    /** Read-only reachability check (no key sent). Must be short (timeouts of ~1 s). */
    fun probe(): ProbeResult
    /** Opens the link; throws [StrategyException] (pairing needed) or [IOException]. */
    fun connect()
    /** Sends one key press; throws [IOException] when the link is lost (the orchestrator reconnects or falls over). */
    fun send(key: RemoteKey)
    /** Types [text] into the focused field; false when the strategy cannot. */
    fun sendText(text: String): Boolean = false
    fun close()
}

/** A strategy that pairs with a code (Android TV, Vizio). */
interface Pairable {
    fun pair(code: String)
}

/** Where pairing tokens live (the app's private storage on Android). Values are secrets: never logged, never in diagnostics. */
interface SecretStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun remove(key: String)
}

class MemorySecretStore : SecretStore {
    private val m = java.util.concurrent.ConcurrentHashMap<String, String>()
    override fun get(key: String) = m[key]
    override fun put(key: String, value: String) { m[key] = value }
    override fun remove(key: String) { m.remove(key) }
}

/** The chosen TV and the timeouts shared by the network strategies. */
data class TvTarget(
    val id: String,
    val host: String,
    val name: String = host,
    val connectTimeoutMs: Int = 1500,
    val readTimeoutMs: Int = 2500,
) {
    /** The only host any strategy may talk to. */
    fun sameHost(h: String) = h.equals(host, ignoreCase = true)
}

object StrategyIds {
    const val CASTBRIDGE = "castbridge"
    const val CVTE = "cvte"
    const val ANDROID_TV = "androidtv-v2"
    const val SAMSUNG = "samsung-tizen"
    const val LG = "lg-webos"
    const val ROKU = "roku-ecp"
    const val SONY = "sony-ircc"
    const val PHILIPS = "philips-jointspace"
    const val VIZIO = "vizio-smartcast"
    const val DLNA = "dlna"
    const val BT_HID = "bluetooth-hid"
    const val IR = "infrared"
    const val VENDOR_APP = "vendor-app"

    /** Default order when nothing else is known: the verified/general ones first, last resorts at the end. */
    val DEFAULT_ORDER = listOf(CASTBRIDGE, CVTE, ROKU, SAMSUNG, LG, SONY, ANDROID_TV, PHILIPS, VIZIO, DLNA, BT_HID, IR, VENDOR_APP)
}
