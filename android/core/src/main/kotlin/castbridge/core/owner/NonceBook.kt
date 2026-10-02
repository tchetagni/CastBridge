package castbridge.core.owner

import java.security.SecureRandom

/**
 * The challenges a phone has issued to TVs and not yet consumed (docs/coordination/DESIGN-W6-PARENTAL-PHONE-GATE.md § 3.3). A nonce is accepted ONCE and for [ttlMs] (10 min) on the phone's
 * [TvClock] (high-water mark + monotonic time: a wall clock wound back lengthens nothing). The book is bounded ([maxSize]): the oldest challenge goes first. In memory only: a restart
 * drops pending challenges, the phone just asks again. Methods are synchronized.
 */
class NonceBook(private val clock: TvClock, val ttlMs: Long = TTL_MS, val maxSize: Int = MAX_SIZE, private val random: SecureRandom = SecureRandom()) {
    companion object {
        const val TTL_MS = 10L * 60 * 1000
        const val MAX_SIZE = 32
        private val HEX64 = Regex("^[0-9a-f]{64}$")
    }

    private val issuedAt = LinkedHashMap<String, Long>()

    private fun purge(now: Long) {
        issuedAt.entries.removeAll { now - it.value > ttlMs }
        while (issuedAt.size >= maxSize) issuedAt.remove(issuedAt.keys.first())
    }

    /** A fresh challenge (64 lowercase hex chars), or [nonce] when given (tests, vectors: must be 64 lowercase hex chars). */
    @Synchronized fun issue(wallNowMs: Long, nonce: String? = null): String {
        clock.observe(wallNowMs)
        val now = clock.now(wallNowMs)
        val n = nonce ?: ByteArray(32).also(random::nextBytes).joinToString("") { "%02x".format(it) }
        require(HEX64.matches(n)) { "défi de 64 caractères hexadécimaux attendu" }
        purge(now)
        issuedAt[n] = now
        return n
    }

    /** True when [nonce] was issued here, is not consumed and is not older than [ttlMs]. */
    @Synchronized fun isLive(nonce: String, wallNowMs: Long): Boolean { val t = issuedAt[nonce] ?: return false; return clock.now(wallNowMs) - t <= ttlMs }

    /** Consumes [nonce]: true the first time while it is live, false afterwards (or when unknown or expired). */
    @Synchronized fun consume(nonce: String, wallNowMs: Long): Boolean { val t = issuedAt.remove(nonce) ?: return false; return clock.now(wallNowMs) - t <= ttlMs }

    @Synchronized fun size(): Int = issuedAt.size
}
