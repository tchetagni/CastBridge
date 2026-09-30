package castbridge.core.trust

/**
 * TV side of « Ajouter un téléphone »: the pairing window and the owner's decision.
 *
 *   Closed --open()--> Open(until) --ask(peer)--> Asking(peer) --approve()--> Closed (phone trusted)
 *                                         |                  \--deny()/timeout--> Open (or Closed if the window ended)
 *
 * Nothing here talks to Bluetooth: Android has already paired the phone (numeric comparison shown on both screens) before
 * a request reaches [ask]. What this class adds is the second lock: the owner must press OK on the TV, in a window
 * he opened himself, for one phone at a time. A phone refused [maxDenials] times is ignored for [blockMs].
 */
class PairingSession(
    private val registry: TrustRegistry,
    private val now: () -> Long = System::currentTimeMillis,
    val windowMs: Long = 120_000,
    val approvalMs: Long = 60_000,
    private val maxDenials: Int = 3,
    private val blockMs: Long = 10 * 60_000,
) {
    enum class Decision { APPROVED, DENIED, TIMEOUT, NOT_OPEN, BUSY, BLOCKED }

    sealed class State {
        object Closed : State()
        data class Open(val until: Long) : State()
        data class Asking(val address: String, val name: String, val deadline: Long, val until: Long) : State()
    }

    private val lock = Object()
    private var state: State = State.Closed
    private var decision: Boolean? = null
    private val denials = HashMap<String, Pair<Int, Long>>()    // address -> (count, blocked until)
    private val listeners = java.util.concurrent.CopyOnWriteArrayList<(State) -> Unit>()

    fun addListener(l: (State) -> Unit) { listeners += l }
    fun removeListener(l: (State) -> Unit) { listeners -= l }
    private fun publish() { val s = state(); listeners.forEach { runCatching { it(s) } } }

    /** The state now (an expired window reads as Closed). */
    fun state(): State = synchronized(lock) { expire(); state }

    val isOpen: Boolean get() = state() !is State.Closed

    /** Seconds left in the window (for the countdown). */
    fun secondsLeft(): Long = when (val s = state()) {
        is State.Open -> ((s.until - now()) / 1000).coerceAtLeast(0)
        is State.Asking -> ((s.until - now()) / 1000).coerceAtLeast(0)
        State.Closed -> 0
    }

    /** Opens (or restarts) the window; a request being decided is left alone. */
    fun open() { synchronized(lock) { if (state !is State.Asking) state = State.Open(now() + windowMs) }; publish() }

    fun close() { synchronized(lock) { if (state is State.Asking) { decision = false; lock.notifyAll() }; state = State.Closed }; publish() }

    /** What the dialog shows, if a phone is waiting for the owner. */
    fun asking(): State.Asking? = state() as? State.Asking

    fun approve(): Boolean = answer(true)
    fun deny(): Boolean = answer(false)

    private fun answer(yes: Boolean): Boolean {
        val ok = synchronized(lock) {
            if (state !is State.Asking) false else { decision = yes; lock.notifyAll(); true }
        }
        return ok
    }

    /**
     * Called by the Bluetooth thread for an unknown (but authenticated and paired) phone that asked for trust. Blocks until the
     * owner answers or [approvalMs] passes. [address] must be a real Bluetooth address (as given by the socket, never by the peer).
     */
    fun ask(address: String, rawName: String): Decision {
        val a = TrustRegistry.norm(address)
        val name = PhoneName.sanitize(rawName)
        synchronized(lock) {
            expire()
            denials[a]?.let { (_, until) -> if (until > now()) return Decision.BLOCKED }
            when (val s = state) {
                State.Closed -> return Decision.NOT_OPEN
                is State.Asking -> return Decision.BUSY
                is State.Open -> state = State.Asking(a, name, now() + approvalMs, s.until)
            }
            decision = null
        }
        publish()
        val limit = System.nanoTime() + approvalMs * 1_000_000
        var result: Boolean? = null
        synchronized(lock) {
            while (decision == null) {
                val left = (limit - System.nanoTime()) / 1_000_000
                if (left <= 0) break
                lock.wait(left)
            }
            result = decision
            val until = (state as? State.Asking)?.until ?: 0
            decision = null
            state = if (result == true) State.Closed else if (until > now()) State.Open(until) else State.Closed
            if (result == true) denials.remove(a)
            else if (result == false) {
                val n = (denials[a]?.first ?: 0) + 1
                denials[a] = n to (if (n >= maxDenials) now() + blockMs else 0L)
            }
        }
        // Approved: the trust is recorded BEFORE the answer leaves, so the very next request of the phone finds it.
        if (result == true) registry.trust(a, name)
        publish()
        return when (result) { true -> Decision.APPROVED; false -> Decision.DENIED; else -> Decision.TIMEOUT }
    }

    /** True when the window just ended (and moves the state to Closed). Call with [lock] held. */
    private fun expire(): Boolean {
        val s = state
        val until = when (s) { is State.Open -> s.until; else -> return false }
        if (until <= now()) { state = State.Closed; return true }
        return false
    }
}
