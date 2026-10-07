package castbridge.core.tv.activation

import castbridge.core.tv.PinGuard

/**
 * Every ONLINE attempt at the connection code of a LOCKED TV goes through ONE gate, whatever the way in: the HTTP route ([LockedActivationApi], the local Wi-Fi and the activation group) and the
 * Bluetooth channel without pairing ([castbridge.core.btact.BtActServer], BLE + PAKE). A way in that had its own counters would give an attacker one budget per way; with one gate the budget
 * is shared (docs/TV-ACTIVATION-CLE-USB.md « Limites » and docs/BT-PLUG-AND-PLAY.md « Bluetooth sans appairage »):
 *  - **per peer**, [guard] ([PinGuard]): [PinGuard]'s `maxFailures` wrong codes (5) lock the peer for its `lockMs` (60 s); while locked even the right code is refused. The peer is an IP address for the HTTP
 *    route, `bt:` + the Bluetooth address of the socket for the channel without pairing ([bluetoothPeer]), so the two never share a counter by accident but both read the same rules;
 *  - **all peers together**: at most [GLOBAL_MAX_WRONG] wrong codes per [WINDOW_MS] (20 per 10 minutes); beyond, EVERY way answers « closed » to everybody, the right code included, without comparing
 *    anything, until the window slides (audit M1 a of 24c766e1);
 *  - **allowances per peer** over the same [WINDOW_MS]: [MAX_TRIES] key verifications and [MAX_READS] reads of the device request, counted apart.
 * Tables are bounded ([MAX_ADDRESSES] peers, the least recently seen forgotten first). Memory only: a restart of CastBridge-TV forgets them (documented limit). Nothing here is secret: no code, no key.
 */
class ActivationAttemptGate(val guard: PinGuard, private val now: () -> Long = System::currentTimeMillis) {
    /** Why a peer is turned away BEFORE any code is compared. */
    sealed class Refusal {
        /** The global cap is reached: nobody is served for a while. */
        object Closed : Refusal() { override fun toString() = "Closed" }
        /** This peer made too many wrong codes: [seconds] to wait. */
        data class Locked(val seconds: Long) : Refusal()
    }

    /** Per-peer counters (access-ordered, at most [MAX_ADDRESSES] peers: the least recently seen is forgotten first, audit M1 c). */
    private fun table() = object : LinkedHashMap<String, ArrayDeque<Long>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ArrayDeque<Long>>?) = size > MAX_ADDRESSES
    }
    /** Key verifications per peer. */
    private val tries = table()
    /** Reads of the device request per peer: counted apart, so reading it never uses up the key verifications of the same peer. */
    private val reads = table()
    /** Times of the wrong codes, every peer together: at most [GLOBAL_MAX_WRONG] per [WINDOW_MS]. */
    private val wrong = ArrayDeque<Long>()

    fun trackedAddresses(): Int = synchronized(tries) { tries.size }
    fun trackedReaders(): Int = synchronized(reads) { reads.size }

    /** One key verification for [peer] if fewer than [MAX_TRIES] were made in the last [WINDOW_MS]. */
    fun takeTry(peer: String): Boolean = take(tries, peer, MAX_TRIES)

    /** One read of the device request for [peer] if fewer than [MAX_READS] were made in the last [WINDOW_MS]. */
    fun takeRead(peer: String): Boolean = take(reads, peer, MAX_READS)

    /** The global cap is reached: no code is compared any more, from any peer. */
    fun globalClosed(): Boolean = synchronized(wrong) { prune(wrong, now()); wrong.size >= GLOBAL_MAX_WRONG }

    /** A wrong code from any peer, any way in. */
    fun countWrong() { synchronized(wrong) { val t = now(); prune(wrong, t); wrong.addLast(t) } }

    // ------------------------------------------------------------------ the channel without pairing: a code proven by a PAKE, never compared as a string

    /** Before computing anything for a Bluetooth peer: turned away (closed for all, or locked for this peer), or null. Counts nothing: a refusal is not an attempt. */
    fun refusal(peerKey: String): Refusal? {
        if (globalClosed()) return Refusal.Closed
        if (guard.isLocked(peerKey)) return Refusal.Locked(guard.retryAfterSeconds(peerKey))
        return null
    }

    /** A confirmation that did not match: one wrong code of this peer AND of the global window. The refusal that applies now (this failure locked the peer), else null. */
    fun recordWrong(peerKey: String): Refusal? {
        countWrong()
        return if (guard.recordFailure(peerKey) == PinGuard.Result.LOCKED) Refusal.Locked(guard.retryAfterSeconds(peerKey)) else null
    }

    /** The right code was proven by [peerKey]: its failures are forgotten. */
    fun recordRight(peerKey: String) = guard.recordSuccess(peerKey)

    private fun take(table: MutableMap<String, ArrayDeque<Long>>, peer: String, max: Int): Boolean = synchronized(table) {
        val q = table.getOrPut(peer) { ArrayDeque() }
        val t = now()
        prune(q, t)
        if (q.size >= max) return false
        q.addLast(t); true
    }

    private fun prune(q: ArrayDeque<Long>, t: Long) { while (q.isNotEmpty() && t - q.first() > WINDOW_MS) q.removeFirst() }

    companion object {
        const val MAX_TRIES = 10
        /** Reads of the device request per peer and per [WINDOW_MS] (the phone reads it once or twice); apart from [MAX_TRIES]. */
        const val MAX_READS = 20
        const val WINDOW_MS = 10 * 60_000L
        /** Wrong codes accepted per [WINDOW_MS] from every peer together (audit M1 a), then every way in answers « closed » to everyone until the window slides. */
        const val GLOBAL_MAX_WRONG = 20
        /** Peers tracked by the per-peer limiter (audit M1 c). */
        const val MAX_ADDRESSES = 1_000

        /** The key of a Bluetooth peer in [PinGuard] and in the allowances: never an IP address, never the bare address (an IPv4 literal cannot look like it). */
        fun bluetoothPeer(address: String?): String = "bt:" + (address?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } ?: "?")
    }
}
