package castbridge.core.remote

import java.io.IOException

/**
 * The phone remote's sender: one thread drains [queue] in order over one open link ([connect] gives a fresh one: Wi-Fi first,
 * Bluetooth as a fallback — the caller decides), pings every [pingMs] to keep the link warm and learn the TV's state, and
 * reconnects by itself after a cut. Unanswered events are resent with their sequence number (see [RemoteQueue]).
 */
class RemoteSession(
    val queue: RemoteQueue,
    private val connect: () -> RemoteTransport,
    private val listener: Listener,
    private val pingMs: Long = 4000,
    var target: RemoteTarget = RemoteTarget.AUTO,
    /** The TV refused the phone's token (expired or revoked; not a wrong PIN, so nothing is counted against it): ask the link layer for a new one. [connect] must then present the new credential. */
    private val onTokenRejected: () -> Unit = {},
    private val maxTokenRetries: Int = 8,
) {
    enum class Link { CONNECTING, CONNECTED, OFFLINE, BAD_PIN }
    data class Status(val link: Link, val via: String? = null, val rttMs: Long? = null, val message: String? = null)

    interface Listener {
        fun status(s: Status)
        /** GET /api/remote/state answer (JSON object). */
        fun state(json: String) {}
        /** The TV refused an event (e.g. "no CastBridge screen in front"): shown to the user. */
        fun refused(e: RemoteEvent, message: String) {}
        /** Every answered event with its round trip (latency display, tests). */
        fun answered(e: RemoteEvent, r: RemoteReply, rttMs: Long) {}
    }

    @Volatile private var running = false
    private var thread: Thread? = null
    /** The transport of the link that is up right now (null while connecting, retrying or stopped), see [liveTransport]. */
    @Volatile private var live: RemoteTransport? = null
    @Volatile var rttMs: Long? = null; private set
    @Volatile var status = Status(Link.CONNECTING); private set

    fun start() {
        if (running) return
        running = true
        thread = Thread(::loop, "cb-remote").apply { isDaemon = true; start() }
    }

    fun stop() {
        running = false
        queue.wake()
        thread?.interrupt(); thread = null
    }

    // ---- what the screen calls (never blocks: the thread sends) ----

    fun key(k: RemoteKey, action: KeyAction = KeyAction.PRESS, repeat: Int = 0) =
        queue.offer("key", buildMap {
            put("code", k.wire)
            if (action != KeyAction.PRESS) put("action", action.wire)
            if (repeat > 0) put("repeat", repeat.toString())
            put("target", target.wire)
        })

    fun text(value: String, mode: TextMode = TextMode.INSERT) =
        queue.offer("text", mapOf("value" to value, "mode" to mode.wire, "target" to target.wire))

    fun global(g: RemoteGlobal) = queue.offer("global", mapOf("action" to g.wire))

    // ---- the sender thread ----

    private fun set(s: Status) { status = s; runCatching { listener.status(s) } }

    private fun loop() {
        var link: RemoteTransport? = null
        var failures = 0
        var lastPing = 0L
        var tokenRetries = 0
        while (running) {
            try {
                if (link == null) {
                    set(Status(if (failures == 0) Link.CONNECTING else Link.OFFLINE, message = status.message))
                    link = try { connect() } catch (e: IOException) {
                        failures++
                        set(Status(Link.OFFLINE, message = castbridge.core.trust.LinkText.failure(e)))
                        queue.take(minOf(3000L, 250L shl minOf(failures, 4)))   // wait, but wake up early if a key is pressed
                        continue
                    }
                    failures = 0
                    lastPing = 0
                    live = link
                }
                val l = link
                val now = System.currentTimeMillis()
                if (now - lastPing >= pingMs) { lastPing = now; ping(l) }
                // A wrong PIN: stop here (every retry would count as a failure on the TV and lock it for a minute).
                if (status.link == Link.BAD_PIN) { queue.clear(); break }
                val ev = queue.take(maxOf(1L, pingMs - (System.currentTimeMillis() - lastPing))) ?: continue
                val t0 = System.nanoTime()
                val r = l.send("POST", ev.route, ev.query(queue.sid))
                val rtt = (System.nanoTime() - t0) / 1_000_000
                if (badToken(r)) {                      // the key stays queued (same number): it is sent once the new token is in use
                    if (!tokenLost(++tokenRetries)) break
                    closeQuietly(link); link = null; continue
                }
                tokenRetries = 0
                queue.ack(ev.seq)
                rttMs = rttMs?.let { (it * 3 + rtt) / 4 } ?: rtt
                runCatching { listener.answered(ev, r, rtt) }
                when {
                    r.status == 401 -> { set(Status(Link.BAD_PIN, l.name, message = "Code PIN refusé par la TV")); queue.clear(); break }
                    r.status in 200..299 -> if (status.link != Link.CONNECTED || status.via != l.name) set(Status(Link.CONNECTED, l.name, rttMs))
                    else -> runCatching { listener.refused(ev, message(r)) }
                }
            } catch (e: TokenRefused) {
                if (!tokenLost(++tokenRetries)) break
                closeQuietly(link); link = null
            } catch (e: IOException) {
                closeQuietly(link); link = null
                failures++
                if (running) set(Status(Link.OFFLINE, message = "Liaison perdue : reconnexion…"))
            } catch (e: InterruptedException) {
                break
            }
        }
        closeQuietly(link)
        running = false
    }

    val isRunning: Boolean get() = running

    /**
     * The link of this session while it is up, or null (R-35, audit I-14). « Ouvrir sur la TV » sends its `open` command on it (see [OpenTvSessionLink]) instead of dialling a second link to
     * the same Bluetooth service, which the TV refuses (same RFCOMM channel). The link may close under the caller's feet: its `send` then throws [IOException] like any other.
     */
    fun liveTransport(): RemoteTransport? = live.takeIf { running }

    private class TokenRefused : IOException("token refused")
    private fun badToken(r: RemoteReply) = r.status == 401 && "bad token" in r.body

    /** An expired token: say so calmly, ask for a new one and wait a little (never a wrong-PIN verdict, unless it keeps failing). False = give up. */
    private fun tokenLost(n: Int): Boolean {
        if (n > maxTokenRetries) { set(Status(Link.BAD_PIN, message = "L'autorisation de ce téléphone n'est plus acceptée par la TV : réassociez la TV.")); queue.clear(); return false }
        set(Status(Link.OFFLINE, message = "Autorisation de la TV à renouveler…"))
        runCatching { onTokenRejected() }
        queue.take(minOf(5000L, 500L shl minOf(n, 4)))
        return true
    }

    private fun ping(l: RemoteTransport) {
        val t0 = System.nanoTime()
        val r = l.send("GET", "state", "")
        val rtt = (System.nanoTime() - t0) / 1_000_000
        if (badToken(r)) throw TokenRefused()
        if (r.status == 401) { set(Status(Link.BAD_PIN, l.name, message = if ("locked" in r.body) "Trop d'essais : la TV attend une minute" else "Code PIN refusé par la TV")); return }
        rttMs = rttMs?.let { (it * 3 + rtt) / 4 } ?: rtt
        set(Status(Link.CONNECTED, l.name, rttMs))
        if (r.status == 200) runCatching { listener.state(r.body) }
    }

    private fun closeQuietly(l: RemoteTransport?) { if (l != null && l === live) live = null; runCatching { l?.close() } }

    companion object {
        /** The French message of a refusal ("message" or "error" of the JSON answer). */
        fun message(r: RemoteReply): String =
            Regex("\"message\":\"((?:[^\"\\\\]|\\\\.)*)\"").find(r.body)?.groupValues?.get(1)
                ?: Regex("\"error\":\"((?:[^\"\\\\]|\\\\.)*)\"").find(r.body)?.groupValues?.get(1)
                ?: "Refusé par la TV (${r.status})"
    }
}
