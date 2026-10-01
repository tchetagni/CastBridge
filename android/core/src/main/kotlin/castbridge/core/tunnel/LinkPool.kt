package castbridge.core.tunnel

import castbridge.core.tv.Link
import kotlin.random.Random

/**
 * Phone side: the one thing allowed to open an RFCOMM link to a TV. Never two `connect()` in parallel (a lock), never sooner than
 * [gapMs] after the previous link closed (the Bluetooth stack needs that long to release it: "already at opened state"),
 * [backoffMs] growing waits with jitter between failed attempts, and after [backoffMs].size + 1 failures a clear message and a
 * [failCooldownMs] pause during which callers fail at once (no tight loop).
 *
 * With [dialShared] (the "CastBridge API v2" service) the link is kept and reused: [open] returns a stream of the live [MuxSession],
 * pings it every [pingEveryMs] while it is in use (the TV closes a silent link after 30 s), and closes it after [idleCloseMs]
 * of no use. A TV without the v2 service (old CastBridge-TV) is detected once and served through [dialLegacy], one link per
 * connection, as before, with the same lock, gap and backoff.
 */
/** The TV answered the status byte with a refusal: retrying changes nothing, the message says why. */
class TunnelRefused(message: String) : java.io.IOException(message)

class LinkPool(
    private val label: String,
    /** Opens the v2 link and reads the TV's status byte; throws [java.io.IOException] (with a French message for [BtDialException]). */
    private val dialShared: (() -> Link)?,
    /** Opens a v1 link and reads the TV's status byte. */
    private val dialLegacy: () -> Link,
    private val gapMs: Long = 1500,
    private val backoffMs: List<Long> = listOf(1500, 3000),
    private val failCooldownMs: Long = 8000,
    private val pingEveryMs: Long = 15_000,
    private val idleCloseMs: Long = 45_000,
    private val legacyRecheckMs: Long = 10 * 60_000,
    private val now: () -> Long = System::currentTimeMillis,
    private val sleep: (Long) -> Unit = Thread::sleep,
    private val jitter: () -> Double = { 0.75 + Random.nextDouble() * 0.5 },
    private val log: (String) -> Unit = {},
    private val onChange: () -> Unit = {},
    /** Shared with every other user of RFCOMM to the same TV (see [BtConnectLock]). */
    private val connectLock: Any = Any(),
) {
    private val own = Any()      // callers of [open] wait here (one connecting at a time); the shared lock is held only during one dial

    sealed class Opened {
        class Shared(val stream: MuxStream) : Opened()
        /** A v1 link: the caller relays it and calls [LinkPool.legacyClosed] when done. */
        class Legacy(val link: Link) : Opened()
        class Failed(val message: String) : Opened()
    }

    data class Diag(val sharedLinks: Int, val opened: Int, val lastClose: String?, val shared: Boolean?)

    @Volatile private var session: MuxSession? = null
    @Volatile private var lastClosedAt = 0L
    @Volatile private var lastUse = now()
    @Volatile private var failUntil = 0L
    @Volatile private var failMessage = ""
    @Volatile private var legacyUntil = 0L
    @Volatile private var supportsShared: Boolean? = if (dialShared == null) false else null
    @Volatile private var opened = 0
    @Volatile private var lastClose: String? = null
    @Volatile private var keeper: Thread? = null
    @Volatile private var stopped = false

    fun diag() = Diag(if (session?.isClosed == false) 1 else 0, opened, lastClose, supportsShared)

    /** Something outside (a remote session, a transfer) wants the link kept alive although no stream is open right now. */
    @Volatile var wanted: () -> Boolean = { false }

    fun legacyClosed(reason: String = "fermée") { lastClosedAt = now(); lastClose = reason; onChange() }

    fun stop() { stopped = true; session?.close("passerelle arrêtée"); keeper?.interrupt() }

    private fun pause(ms: Long) { if (ms > 0) sleep(ms) }

    /** Opens a stream (shared link) or a link (legacy) to the TV; blocks while another caller is connecting. */
    fun open(): Opened {
        lastUse = now()
        session?.takeIf { !it.isClosed }?.let { s -> s.open()?.let { return Opened.Shared(it) } }
        synchronized(own) {
            // somebody else may have connected while we waited for the lock
            session?.takeIf { !it.isClosed }?.let { s -> s.open()?.let { return Opened.Shared(it) } }
            if (now() < failUntil) return Opened.Failed(failMessage)
            val tries = backoffMs.size + 1
            var last = ""
            for (attempt in 0 until tries) {
                if (stopped) return Opened.Failed("passerelle arrêtée")
                if (attempt > 0) pause((backoffMs[attempt - 1] * jitter()).toLong())
                pause(lastClosedAt + gapMs - now())
                if (dialShared != null && supportsShared == false && now() >= legacyUntil) supportsShared = null    // an updated TV may have appeared
                try {
                    if (dialShared != null && supportsShared != false) {
                        try {
                            val l = synchronized(connectLock) { dialShared() }
                            supportsShared = true
                            val s = MuxSession(l, onClosed = { r -> lastClose = r; lastClosedAt = now(); log("$label: shared link closed ($r)"); onChange() }).start()
                            session = s; opened++; startKeeper(); onChange()
                            s.open()?.let { return Opened.Shared(it) }
                            last = "liaison partagée fermée aussitôt"; continue
                        } catch (e: TunnelRefused) { supportsShared = true; throw e
                        } catch (e: java.io.IOException) {
                            if (supportsShared == true) throw e
                            // first contact with this TV: is it the v2 service that is missing (old TV), or the TV that is unreachable?
                            log("$label: shared service failed (${e.message}); trying the single-use service")
                            val l = try { synchronized(connectLock) { dialLegacy() } } catch (e2: java.io.IOException) { lastClosedAt = now(); throw e2 }
                            supportsShared = false; legacyUntil = now() + legacyRecheckMs; opened++; onChange()
                            return Opened.Legacy(l)
                        }
                    }
                    val l = synchronized(connectLock) { dialLegacy() }; opened++; onChange()
                    return Opened.Legacy(l)
                } catch (e: TunnelRefused) {
                    lastClosedAt = now(); lastClose = e.message; onChange()
                    return Opened.Failed(e.message ?: "la TV refuse")
                } catch (e: java.io.IOException) {
                    last = e.message ?: e.javaClass.simpleName
                    lastClosedAt = now()
                    log("$label: connect attempt ${attempt + 1}/$tries failed: $last")
                }
            }
            failMessage = "Bluetooth : la TV ne répond pas ($last)"
            failUntil = now() + failCooldownMs
            lastClose = failMessage
            onChange()
            return Opened.Failed(failMessage)
        }
    }

    /** Keeps the shared link alive while it is in use; closes it when it has been unused for [idleCloseMs] or the TV stopped answering. */
    private fun startKeeper() {
        if (keeper?.isAlive == true) return
        keeper = Thread({
            try {
                while (!stopped) {
                    Thread.sleep(pingEveryMs)
                    val s = session ?: break
                    if (s.isClosed) break
                    if (s.streamCount > 0 || wanted()) lastUse = now()
                    if (now() - lastUse > idleCloseMs) { s.close("inutilisée depuis ${idleCloseMs / 1000} s"); break }
                    if (now() - s.lastRx > pingEveryMs * 3) { s.close("la TV ne répond plus au garde-vivant"); break }
                    s.ping()
                }
            } catch (_: InterruptedException) {}
        }, "bt-$label-keeper").apply { isDaemon = true; start() }
    }
}

/**
 * One lock per TV address for the whole app: the API tunnel ([LinkPool]) and the remote control (CBTR) must not run `connect()` to the
 * same TV at the same time (the Bluetooth stack answers "already at opened state" to the second one).
 */
object BtConnectLock {
    private val locks = java.util.concurrent.ConcurrentHashMap<String, Any>()
    fun of(address: String): Any = locks.getOrPut(address.uppercase()) { Any() }
}
