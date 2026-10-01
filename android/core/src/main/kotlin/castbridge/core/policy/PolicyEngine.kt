package castbridge.core.policy

import castbridge.core.lots.QueueStore
import castbridge.core.net.JsonLite
import castbridge.core.net.JsonLite.long
import castbridge.core.owner.DeviceContext
import castbridge.core.owner.Envelope
import castbridge.core.owner.KeyRing
import castbridge.core.owner.KeyScope
import castbridge.core.owner.OrderResult
import castbridge.core.owner.OrderVerifier
import castbridge.core.owner.Rejection
import castbridge.core.owner.SeqState
import castbridge.core.owner.TvClock

/** What the engine keeps on disk (one small JSON file, written atomically through [QueueStore]). */
class PolicyStorage(private val store: QueueStore) {
    fun load(): String? = store.load()
    fun save(text: String) = store.save(text)
}

/**
 * The TV's policy engine (docs/ORDRES.md). It receives an order token, whatever its path (Bluetooth from the phone, a file), and:
 *  1. checks it with the COMMON envelope verifier ([OrderVerifier]): signature, key with the `policy` scope, target (device code / licence / group), sequence STRICTLY greater per key
 *     (anti-replay, anti-rollback), `notBefore`/`expiresAt` against the time the TV trusts ([TvClock], not the wall clock);
 *  2. checks the action against the closed list ([PolicyActions]) and that the key holds the extra scopes the action needs (an order never grants more than its key);
 *  3. applies it IDEMPOTENTLY to the current [PolicyState] and persists everything (state, sequences, clock, journal, acknowledgements);
 *  4. returns the [OrderAck] (applied / refused + reason). A refused order never blocks the next one. A token received twice (the phone retries after a lost ack) returns the
 *     SAME acknowledgement again, it is not applied twice.
 * Absence of orders changes nothing: no timer in here locks anything (rights expire by their own dates, locally). A server outage locks nobody.
 */
class PolicyEngine(
    private val baseKeys: KeyRing,
    private val storage: PolicyStorage,
    private val device: () -> DeviceContext,
    private val wallClock: () -> Long = System::currentTimeMillis,
    private val maxJournal: Int = 200,
    private val maxAcks: Int = 256,
) {
    companion object {
        const val MAX_TOKEN = 4000
        const val SKEW_MS = 24L * 3600 * 1000
        private val CACHEABLE = setOf(AckReason.APPLIED, AckReason.BAD_PARAMS, AckReason.UNKNOWN_ACTION, AckReason.SCOPE_EXCEEDED)
    }

    private val lock = Any()
    private var state = PolicyState()
    private val seqState = SeqState()
    private val clock = TvClock()
    private val journal = ArrayList<JournalEntry>()
    private val acks = LinkedHashMap<String, OrderAck>()      // "kid:seq"
    private var lastToken: String? = null

    init { synchronized(lock) { load() } }

    val current: PolicyState get() = synchronized(lock) { state }
    /** The read-only journal, newest first. */
    fun journal(): List<JournalEntry> = synchronized(lock) { journal.asReversed().toList() }
    /** The time the TV trusts for validity decisions. */
    fun trustedNow(): Long = synchronized(lock) { clock.now(wallClock()) }
    fun lastSeq(kid: String): Long = synchronized(lock) { seqState.last(kid) }
    fun seqSnapshot(): Map<String, Long> = synchronized(lock) { seqState.snapshot() }
    /** Acknowledgements with a sequence number above [haveSeq] for [kid] (what a phone that has [haveSeq] still has to collect). */
    fun acksAfter(have: Map<String, Long>): List<OrderAck> = synchronized(lock) { acks.values.filter { it.seq > (have[it.kid] ?: -1L) } }
    /** The key ring in force: the embedded keys plus what policy revocations removed. */
    fun keyRing(): KeyRing = synchronized(lock) { baseKeys.withRevoked(state.revocations.keys) }

    /** Call at each start and hourly: remembers the wall clock (high-water mark), so a clock set back is detected and never believed. */
    fun observeClock() = synchronized(lock) { clock.observe(wallClock()); persist() }

    fun receive(token: String): OrderAck = synchronized(lock) {
        val wall = wallClock()
        val rolled = clock.rolledBack(wall)
        val now = clock.now(wall)
        val env = if (token.length <= MAX_TOKEN) Envelope.decode(token) else null
        val id = env?.let { "${it.keyId}:${it.seq}" }
        // the same token again: answer again, apply nothing (the phone lost our acknowledgement)
        if (id != null) acks[id]?.let { prev -> if (prev.nonce == env!!.nonce) return@synchronized prev }
        val ack = process(token, env, now, rolled)
        clock.observe(wall)
        persist()
        ack
    }

    private fun process(token: String, env: Envelope?, now: Long, rolled: Boolean): OrderAck {
        fun done(reason: AckReason, action: String, e: Envelope?): OrderAck {
            val applied = reason.applied
            if (applied) state = state.bumped()
            val ack = OrderAck(e?.keyId ?: "0".repeat(16), e?.seq ?: 0, e?.nonce ?: "0".repeat(8), reason, state.version, now)
            // only outcomes decided on a VERIFIED signature and a consumed sequence number are remembered: a forged copy (same kid/seq/nonce, bad signature) must never poison the real order
            if (e != null && reason in CACHEABLE) { acks.putIfAbsent("${e.keyId}:${e.seq}", ack); while (acks.size > maxAcks) acks.remove(acks.keys.first()) }
            val last = journal.lastOrNull()
            if (last == null || last.kid != ack.kid || last.seq != ack.seq || last.reason != reason || last.action != action) journal += JournalEntry(now, ack.kid, ack.seq, action, reason, state.version, rolled)
            while (journal.size > maxJournal) journal.removeAt(0)
            return ack
        }
        if (env == null) return done(if (token.length > MAX_TOKEN) AckReason.TOO_LARGE else AckReason.MALFORMED, "?", null)
        val ring = baseKeys.withRevoked(state.revocations.keys)
        val verifier = OrderVerifier(ring, seqState, state.revocations, SKEW_MS)
        val r = verifier.verify(token, device(), now)
        if (r is OrderResult.Rejected) return done(reasonOf(r.reason), "?", env.takeIf { r.reason != Rejection.MALFORMED && r.reason != Rejection.UNKNOWN_TYPE })
        val order = (r as OrderResult.Accepted).order
        // the common checks accepted it (sequence recorded). From here a refusal is reported but the next order is judged on its own.
        return when (val p = PolicyActions.parse(order, now)) {
            is PolicyActions.Parsed.Bad -> done(p.reason, order.action, env)
            is PolicyActions.Parsed.Ok -> {
                val key = ring.find(env.keyId)!!
                when {
                    p.needs.any { !key.allows(it) } -> done(AckReason.SCOPE_EXCEEDED, order.action, env)
                    !mayRevoke(p.change, key.scopes, ring) -> done(AckReason.SCOPE_EXCEEDED, order.action, env)
                    else -> { state = state.apply(p.change); done(AckReason.APPLIED, order.action, env) }
                }
            }
        }
    }

    /** A key may revoke another key only if it is NOT more powerful: the server key cannot revoke the owner's master key (it would lock the owner out of re-activating). */
    private fun mayRevoke(c: PolicyActions.Change, scopes: Set<KeyScope>, ring: KeyRing): Boolean {
        if (c !is PolicyActions.Change.RevokeKey) return true
        val target = ring.find(c.kid) ?: return true
        return scopes.containsAll(target.scopes)
    }

    private fun reasonOf(r: Rejection): AckReason = when (r) {
        Rejection.MALFORMED -> AckReason.MALFORMED; Rejection.UNKNOWN_TYPE -> AckReason.UNKNOWN_TYPE; Rejection.UNKNOWN_KEY -> AckReason.UNKNOWN_KEY
        Rejection.REVOKED_KEY -> AckReason.REVOKED_KEY; Rejection.BAD_SIGNATURE -> AckReason.BAD_SIGNATURE; Rejection.KEY_NOT_ALLOWED -> AckReason.KEY_NOT_ALLOWED
        Rejection.BAD_ORDER -> AckReason.BAD_ORDER; Rejection.WRONG_TARGET -> AckReason.WRONG_TARGET; Rejection.STALE_SEQUENCE -> AckReason.STALE_SEQUENCE
        Rejection.NOT_YET_VALID -> AckReason.NOT_YET_VALID; Rejection.WINDOW_CLOSED -> AckReason.WINDOW_CLOSED
        else -> AckReason.MALFORMED
    }

    private fun persist() {
        val j = mapOf(
            "state" to state.toJson(), "seq" to seqState.snapshot(), "clock" to mapOf("lastSeen" to clock.lastSeen, "floor" to clock.floor),
            "journal" to journal.map { mapOf("at" to it.atMs, "kid" to it.kid, "seq" to it.seq, "action" to it.action, "reason" to it.reason.name, "v" to it.policyVersion, "rb" to it.clockRolledBack) },
            "acks" to acks.values.map { it.toText() },
        )
        runCatching { storage.save(JsonLite.write(j)) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun load() {
        val text = storage.load() ?: return
        runCatching {
            val m = JsonLite.obj(text)
            state = PolicyState.fromJson(m["state"] as Map<String, Any?>)
            (m["seq"] as? Map<String, Any?>)?.forEach { (k, v) -> (v as? Number)?.let { seqState.record(k, it.toLong()) } }
            (m["clock"] as? Map<String, Any?>)?.let { clock.lastSeen = it.long("lastSeen") ?: 0; clock.floor = it.long("floor") ?: 0 }
            (m["journal"] as? List<Map<String, Any?>>)?.forEach { e ->
                journal += JournalEntry(e.long("at") ?: 0, e["kid"] as String, e.long("seq") ?: 0, e["action"] as String, AckReason.valueOf(e["reason"] as String), e.long("v") ?: 0, e["rb"] as? Boolean ?: false)
            }
            (m["acks"] as? List<Any?>)?.forEach { t -> OrderAck.parse(t as String)?.let { acks["${it.kid}:${it.seq}"] = it } }
        }
    }
}
