package castbridge.core.remote.vendor

import castbridge.core.remote.KeyAction
import castbridge.core.remote.Outcome
import castbridge.core.remote.RemoteGlobal
import castbridge.core.remote.RemoteKey
import castbridge.core.remote.RemoteTarget

/** Where one key may go, in the order they are tried; the first that takes it ends the search (no double send). */
enum class Route { APP, ACCESSIBILITY, VENDOR, AUDIO, MEDIA_SESSION }

object KeyRouting {
    /**
     * The ordered routes for [k] (docs/REMOTE.md). [vendorReady]: the loopback relay is connected. Rules:
     *  - volume: AudioManager, and the relay only if the TV refuses (fixed volume);
     *  - HOME stays CastBridge's home, except when the phone asks for the whole TV ([RemoteTarget.SYSTEM]) and the relay is ready;
     *  - a CastBridge screen in front gets the key first; the relay only when the screen did not consume it and the phone
     *    did not restrict the key to CastBridge ([RemoteTarget.APP]);
     *  - CastBridge not in front: relay first (works in every app, no accessibility needed), then accessibility, then the
     *    media session for media keys. Nothing at all with target APP.
     */
    fun plan(k: RemoteKey, target: RemoteTarget, castbridgeFront: Boolean, accessibility: Boolean, vendorReady: Boolean): List<Route> {
        val vendor = if (vendorReady && target != RemoteTarget.APP) listOf(Route.VENDOR) else emptyList()
        if (k.kind == RemoteKey.Kind.VOLUME) return listOf(Route.AUDIO) + vendor
        if (k == RemoteKey.HOME) return if (target == RemoteTarget.SYSTEM && vendorReady) listOf(Route.VENDOR) else listOf(Route.APP)
        if (castbridgeFront) return if (target == RemoteTarget.SYSTEM && vendorReady) listOf(Route.VENDOR) else listOf(Route.APP) + vendor
        if (target == RemoteTarget.APP) return emptyList()
        return vendor + (if (accessibility) listOf(Route.ACCESSIBILITY) else emptyList()) +
            (if (k.kind == RemoteKey.Kind.MEDIA) listOf(Route.MEDIA_SESSION) else emptyList())
    }

    /** The system-wide actions the relay can stand in for when the accessibility service is not available. */
    fun globalViaVendor(g: RemoteGlobal): RemoteKey? = when (g) { RemoteGlobal.BACK -> RemoteKey.BACK; RemoteGlobal.HOME -> RemoteKey.HOME; else -> null }
}

/** Token bucket: at most [perSecond] events per second (burst [perSecond]). */
class RateLimiter(private val perSecond: Int = 30, private val now: () -> Long = System::nanoTime) {
    private var tokens = perSecond.toDouble()
    private var last = now()
    @Synchronized fun tryAcquire(): Boolean {
        val t = now()
        tokens = minOf(perSecond.toDouble(), tokens + (t - last) / 1e9 * perSecond)
        last = t
        if (tokens < 1.0) return false
        tokens -= 1.0
        return true
    }
}

/**
 * The Bluetooth → manufacturer-service relay on the TV. The only way in is the TV's remote API (PIN or trusted Bluetooth
 * link already checked by the caller), and only [RemoteKey]s (a closed list, never power/sleep) are ever encoded.
 * Rate-limited; the log keeps key names and outcomes, never a code, address or secret.
 */
class VendorRelay(private val link: VendorLink, private val limiter: RateLimiter = RateLimiter(), private val now: () -> Long = System::currentTimeMillis) {
    private val log = ArrayDeque<String>()
    @Volatile var sent = 0; private set

    val ready: Boolean get() = link.state == VendorState.READY

    /** [DOWN] and [PRESS]/[LONG] send one key event (the service has no key-up); [UP] sends nothing. */
    fun relay(k: RemoteKey, action: KeyAction): Outcome {
        if (k.code in FORBIDDEN) return refuse(k, "forbidden")
        if (action == KeyAction.UP) return Outcome.done("vendor")
        if (!ready) return refuse(k, "not ready")
        if (!limiter.tryAcquire()) return refuse(k, "rate limit", "Trop de touches d'un coup : ralentissez.", 429)
        if (!link.sendKey(k.code)) return refuse(k, "send failed", "Le service de la TV ne répond plus.")
        sent++
        note("${k.wire} ok")
        return Outcome.done("vendor")
    }

    @Synchronized fun journal(): List<String> = log.toList()

    private fun refuse(k: RemoteKey, why: String, msg: String = "Relais du fabricant indisponible.", status: Int = 409): Outcome { note("${k.wire} refused: $why"); return Outcome.refused(msg, status) }
    @Synchronized private fun note(s: String) { log.addLast("${now()} $s"); while (log.size > 50) log.removeFirst() }

    companion object {
        /** POWER, SLEEP, WAKEUP, SOFT_SLEEP, SYSRQ: never relayed, whatever the list says. */
        val FORBIDDEN = setOf(26, 223, 224, 276, 120)
    }
}
