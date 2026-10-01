package castbridge.core.ssh

import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * When may the SSH server run? Off by default; switched on only by an explicit action (remote control
 * menu on the TV, or the PIN-protected API); switched off again after [idleMinutes] without any
 * session or activity. Pure logic with an injectable clock; the server polls [shouldStop].
 */
class SshPolicy(
    idleMinutes: Int = DEFAULT_IDLE_MINUTES,
    private val now: () -> Long = System::currentTimeMillis,
) {
    @Volatile var enabled = false; private set
    @Volatile var idleMinutes: Int = idleMinutes.coerceIn(MIN_IDLE_MINUTES, MAX_IDLE_MINUTES); private set
    @Volatile private var lastActivity = 0L
    @Volatile private var sessions = 0

    /** Told the number of authenticated sessions each time it changes (the TV shows it at the top of the screen). */
    @Volatile var onSessions: ((Int) -> Unit)? = null
    private fun changed() { onSessions?.invoke(sessions) }

    /** [minutes] overrides the idle timeout for this run (clamped to 1..[MAX_IDLE_MINUTES]). */
    @Synchronized fun enable(minutes: Int? = null) {
        if (minutes != null) idleMinutes = minutes.coerceIn(MIN_IDLE_MINUTES, MAX_IDLE_MINUTES)
        enabled = true; sessions = 0; lastActivity = now(); changed()
    }

    @Synchronized fun disable() { enabled = false; sessions = 0; changed() }

    @Synchronized fun onSessionOpened() { sessions++; lastActivity = now(); changed() }
    @Synchronized fun onSessionClosed() { sessions = (sessions - 1).coerceAtLeast(0); lastActivity = now(); changed() }
    @Synchronized fun onActivity() { lastActivity = now() }

    val openSessions get() = sessions
    val idleMillis get() = idleMinutes * 60_000L

    /** True when the server is on, nobody is connected, and nothing happened for the idle timeout. */
    @Synchronized fun shouldStop(): Boolean = enabled && sessions == 0 && now() - lastActivity >= idleMillis

    /** Seconds left before the automatic stop (0 if a session keeps it alive or it is off). */
    @Synchronized fun secondsLeft(): Long =
        if (!enabled || sessions > 0) 0 else ((idleMillis - (now() - lastActivity)).coerceAtLeast(0) + 999) / 1000

    companion object {
        const val DEFAULT_IDLE_MINUTES = 30
        const val MIN_IDLE_MINUTES = 1
        const val MAX_IDLE_MINUTES = 24 * 60
    }
}

/** Counts failures per client address and locks it for [lockMs] after [maxFailures] (like the PIN guard, for SSH). */
class FailureTracker(
    private val maxFailures: Int = 5,
    private val lockMs: Long = 60_000,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private class E(var fails: Int = 0, var until: Long = 0)
    private val map = ConcurrentHashMap<String, E>()

    fun isLocked(ip: String): Boolean {
        val e = map[ip] ?: return false
        synchronized(e) {
            if (e.until != 0L && e.until <= now()) { e.until = 0; e.fails = 0 }
            return e.until > now()
        }
    }

    fun recordFailure(ip: String) {
        val e = map.getOrPut(ip) { E() }
        synchronized(e) {
            if (e.until > now()) return
            if (++e.fails >= maxFailures) e.until = now() + lockMs
        }
    }

    fun recordSuccess(ip: String) { map.remove(ip) }
}

/** Address classification: the SSH server only talks to the local network. */
object Lan {
    /** Loopback, RFC 1918, link-local, CGNAT-free IPv4; IPv6 loopback, link-local and unique-local (fc00::/7). */
    fun isLocal(ip: String): Boolean {
        val lit = ip.substringBefore('%')
        // Literals only: never let InetAddress do a DNS lookup on attacker-influenced text.
        if (!Regex("^\\d{1,3}(\\.\\d{1,3}){3}$").matches(lit) && ':' !in lit) return false
        return try {
            val a = InetAddress.getByName(lit)
            a.isLoopbackAddress || a.isSiteLocalAddress || a.isLinkLocalAddress ||
                (a.address.size == 16 && (a.address[0].toInt() and 0xfe) == 0xfc)
        } catch (e: Exception) { false }
    }
}
