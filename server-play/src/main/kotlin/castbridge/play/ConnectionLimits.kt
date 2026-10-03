package castbridge.play

import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Plafonds de connexions : par adresse IP cliente et au total. S'applique AVANT l'ouverture d'une connexion de jeu (avant l'upgrade WebSocket :
 * le refus est une réponse HTTP 429 ou 503). Une « connexion » = une session WebSocket ou une session de repli (SSE / long-poll).
 */
class ConnectionLimits(private val maxPerIp: Int, private val maxTotal: Int) {
    enum class Verdict { OK, IP_FULL, TOTAL_FULL }

    private val perIp = ConcurrentHashMap<String, AtomicInteger>()
    private val total = AtomicInteger()

    fun acquire(ip: String): Verdict {
        if (total.incrementAndGet() > maxTotal) { total.decrementAndGet(); return Verdict.TOTAL_FULL }
        val n = perIp.computeIfAbsent(ip) { AtomicInteger() }
        if (n.incrementAndGet() > maxPerIp) { n.decrementAndGet(); total.decrementAndGet(); return Verdict.IP_FULL }
        return Verdict.OK
    }

    fun release(ip: String) {
        total.decrementAndGet()
        perIp.computeIfPresent(ip) { _, n -> if (n.decrementAndGet() <= 0) null else n }
    }

    fun total(): Int = total.get()
    fun of(ip: String): Int = perIp[ip]?.get() ?: 0
}

/** Adresse du client : celle de la socket, sauf derrière le proxy de confiance (le DERNIER saut de `X-Forwarded-For` que nginx a écrit). */
object ClientIp {
    /**
     * Retourne la CLÉ de limite du client : IPv4 telle quelle ; IPv6 réduite à son préfixe /64 (un abonné reçoit au moins un /64 : sans cela, 2^64 adresses = 2^64 plafonds) ;
     * une IPv4 inscrite en IPv6 redevient IPv4.
     */
    fun resolve(peer: InetAddress, forwardedFor: String?, trusted: List<Cidr>): String {
        if (forwardedFor != null && trusted.any { it.contains(peer) }) {
            val last = forwardedFor.substringAfterLast(',').trim()
            Cidr.literal(last)?.let { return key(it) }
        }
        return key(peer)
    }

    fun key(a: InetAddress): String {
        if (a !is java.net.Inet6Address) return a.hostAddress
        val b = a.address
        return "v6:" + (0 until 4).joinToString(":") { "%02x%02x".format(b[2 * it], b[2 * it + 1]) } + "::/64"
    }
}

/** Seau à jetons : `rate` messages par seconde, rafale `burst`. L'horloge (ms) est injectée. */
class TokenBucket(private val rate: Int, private val burst: Int, private val clock: () -> Long = System::currentTimeMillis) {
    private var tokens = burst.toDouble()
    private var at = clock()

    @Synchronized fun take(): Boolean {
        val now = clock()
        tokens = minOf(burst.toDouble(), tokens + (now - at) * rate / 1000.0); at = now
        if (tokens < 1.0) return false
        tokens -= 1.0
        return true
    }
}
