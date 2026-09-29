package castbridge.core.tv

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/** PIN helpers. The PIN is never logged anywhere. */
object Pin {
    const val LENGTH = 6

    fun generate(random: java.util.Random = SecureRandom()): String =
        (0 until LENGTH).joinToString("") { random.nextInt(10).toString() }

    fun isValidFormat(p: String?) = p != null && p.length == LENGTH && p.all { it in '0'..'9' }

    /** Constant-time comparison (no early exit on the first differing byte). */
    fun matches(expected: String, given: String?): Boolean {
        if (given == null) return false
        return MessageDigest.isEqual(expected.toByteArray(Charsets.UTF_8), given.toByteArray(Charsets.UTF_8))
    }
}

/**
 * Checks PINs and locks a client (by IP) for [lockMs] after [maxFailures] wrong attempts.
 * While locked, even the right PIN is refused (otherwise the lock would not slow brute force).
 */
class PinGuard(
    private val pin: String,
    private val maxFailures: Int = 5,
    private val lockMs: Long = 60_000,
    private val now: () -> Long = System::currentTimeMillis,
) {
    enum class Result { OK, BAD, LOCKED }

    private class Entry(var failures: Int = 0, var lockedUntil: Long = 0)
    private val entries = ConcurrentHashMap<String, Entry>()

    fun check(ip: String, given: String?): Result {
        val e = entries.getOrPut(ip) { Entry() }
        synchronized(e) {
            val t = now()
            if (e.lockedUntil > t) return Result.LOCKED
            if (e.lockedUntil != 0L) { e.lockedUntil = 0; e.failures = 0 }  // lock expired
            if (Pin.matches(pin, given)) { e.failures = 0; return Result.OK }
            e.failures++
            if (e.failures >= maxFailures) { e.lockedUntil = t + lockMs; return Result.LOCKED }
            return Result.BAD
        }
    }

    fun retryAfterSeconds(ip: String): Long =
        ((entries[ip]?.lockedUntil ?: 0) - now()).coerceAtLeast(0).let { (it + 999) / 1000 }
}
