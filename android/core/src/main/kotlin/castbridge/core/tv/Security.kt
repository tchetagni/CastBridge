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
open class PinGuard(
    pin: String,
    private val maxFailures: Int = 5,
    private val lockMs: Long = 60_000,
    private val now: () -> Long = System::currentTimeMillis,
    /** At most this many addresses are tracked (audit M1 c): the least recently used one is forgotten first, so the table never grows without bound. */
    private val maxEntries: Int = MAX_ENTRIES,
) {
    enum class Result { OK, BAD, LOCKED }

    @Volatile private var pin: String = pin

    /**
     * Nouveau code de la TV (régénération) : l'ancien est refusé aussitôt (le guard est partagé par le serveur HTTP, Bluetooth et la
     * passerelle). Les compteurs et blocages d'essais (par adresse) sont tous effacés : ils comptaient des essais contre l'ANCIEN code ;
     * un téléphone bloqué pour avoir mal saisi l'ancien code peut saisir le nouveau sans attendre. Aucune valeur n'est journalisée.
     */
    @Synchronized fun rotate(newPin: String) { pin = newPin; synchronized(entries) { entries.clear() } }

    private class Entry(var failures: Int = 0, var lockedUntil: Long = 0)
    /** Access-ordered: the eldest entry is the least recently checked address. Every access holds the map's lock. */
    private val entries = object : LinkedHashMap<String, Entry>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?) = size > maxEntries
    }

    /** How many addresses are tracked (tests, audit M1 c). */
    fun trackedAddresses(): Int = synchronized(entries) { entries.size }

    open fun check(ip: String, given: String?): Result {
        val e = synchronized(entries) { entries.getOrPut(ip) { Entry() } }
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
        ((synchronized(entries) { entries[ip] }?.lockedUntil ?: 0) - now()).coerceAtLeast(0).let { (it + 999) / 1000 }

    // ---- for a code that is proven elsewhere than by comparing a string: the PAKE of « activer par Bluetooth sans appairage » (castbridge.core.btact) never receives the code, so it cannot call [check] ----

    /** Is [ip] locked right now? Counts nothing and compares nothing: a PAKE session asks BEFORE it computes anything for a peer. */
    fun isLocked(ip: String): Boolean {
        val e = synchronized(entries) { entries[ip] } ?: return false
        return synchronized(e) { e.lockedUntil > now() }
    }

    /** A wrong code, known by a proof that did not match (a PAKE confirmation): counted exactly like a wrong PIN of [check] ([maxFailures] ⇒ locked for [lockMs]). [Result.BAD] or [Result.LOCKED]. */
    fun recordFailure(ip: String): Result {
        val e = synchronized(entries) { entries.getOrPut(ip) { Entry() } }
        synchronized(e) {
            val t = now()
            if (e.lockedUntil > t) return Result.LOCKED
            if (e.lockedUntil != 0L) { e.lockedUntil = 0; e.failures = 0 }  // lock expired
            e.failures++
            if (e.failures >= maxFailures) { e.lockedUntil = t + lockMs; return Result.LOCKED }
            return Result.BAD
        }
    }

    /** The right code was proven: the failures of [ip] are forgotten (a lock that is running stays: the right code does not lift it, as with [check]). */
    fun recordSuccess(ip: String) {
        val e = synchronized(entries) { entries[ip] } ?: return
        synchronized(e) { if (e.lockedUntil <= now()) { e.failures = 0; e.lockedUntil = 0 } }
    }

    companion object { const val MAX_ENTRIES = 1_000 }
}

/**
 * Anti DNS-rebinding: the TV API answers only requests whose `Host` header is a private / local IP literal or `localhost`
 * (with or without a port). A web page reaching the TV through a DNS name it controls sends that name as Host: refused.
 * An absent or empty Host (HTTP/1.0 clients) is allowed.
 */
object HostGuard {
    fun allowed(host: String?): Boolean {
        val raw = host?.trim().orEmpty()
        if (raw.isEmpty()) return true
        val h: String
        if (raw.startsWith("[")) {
            val end = raw.indexOf(']'); if (end < 0) return false
            val rest = raw.substring(end + 1)
            if (rest.isNotEmpty() && !(rest.startsWith(":") && rest.drop(1).all { it.isDigit() } && rest.length > 1)) return false
            h = raw.substring(1, end)
        } else if (raw.count { it == ':' } > 1) {
            h = raw   // bare IPv6 literal, no port
        } else {
            val i = raw.indexOf(':')
            if (i >= 0) {
                val port = raw.substring(i + 1)
                if (port.isEmpty() || !port.all { it.isDigit() }) return false
                h = raw.substring(0, i)
            } else h = raw
        }
        val name = h.lowercase()
        if (name == "localhost") return true
        return if (':' in name) ipv6Local(name) else ipv4Local(name)
    }

    private fun ipv4Local(h: String): Boolean {
        val parts = h.split('.')
        if (parts.size != 4) return false
        val o = parts.map { p -> if (p.isEmpty() || p.length > 3 || !p.all { it.isDigit() }) return false else p.toInt() }
        if (o.any { it > 255 }) return false
        return o[0] == 10 || o[0] == 127 || (o[0] == 172 && o[1] in 16..31) || (o[0] == 192 && o[1] == 168) || (o[0] == 169 && o[1] == 254)
    }

    private fun ipv6Local(h: String): Boolean {
        if (!h.all { it in '0'..'9' || it in 'a'..'f' || it == ':' || it == '.' }) return false
        if (h == "::1" || h == "0:0:0:0:0:0:0:1") return true
        val first = h.substringBefore(':')
        if (first.isEmpty() || first.length > 4) return false
        val v = first.toIntOrNull(16) ?: return false
        return (v and 0xFFC0) == 0xFE80 || (v and 0xFE00) == 0xFC00   // fe80::/10 link-local, fc00::/7 ULA
    }
}
