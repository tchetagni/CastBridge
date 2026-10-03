package castbridge.play

import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Plafonds de connexions : par adresse IP cliente et au total. S'applique AVANT l'ouverture d'une connexion de jeu (avant l'upgrade WebSocket :
 * le refus est une réponse HTTP 429 ou 503). Une « connexion » = une session WebSocket ou une session de repli (SSE / long-poll).
 */
class ConnectionLimits(private val maxPerIp: Int, private val maxTotal: Int, private val maxPerPrefix48: Int = 64) {
    enum class Verdict { OK, IP_FULL, TOTAL_FULL }

    private val perIp = ConcurrentHashMap<String, AtomicInteger>()
    private val per48 = ConcurrentHashMap<String, AtomicInteger>()
    private val total = AtomicInteger()

    /** Prend une place : plafond total, plafond par clé d'adresse (IPv4, ou /64), et pour l'IPv6 un second plafond par /48 (65 536 /64 dans un /48). */
    fun acquire(ip: String): Verdict {
        if (total.incrementAndGet() > maxTotal) { total.decrementAndGet(); return Verdict.TOTAL_FULL }
        val n = perIp.computeIfAbsent(ip) { AtomicInteger() }
        if (n.incrementAndGet() > maxPerIp) { decrement(perIp, ip); total.decrementAndGet(); return Verdict.IP_FULL }
        ClientIp.group48(ip)?.let { g ->
            val m = per48.computeIfAbsent(g) { AtomicInteger() }
            if (m.incrementAndGet() > maxPerPrefix48) { decrement(per48, g); decrement(perIp, ip); total.decrementAndGet(); return Verdict.IP_FULL }
        }
        return Verdict.OK
    }

    fun release(ip: String) {
        total.decrementAndGet()
        decrement(perIp, ip)
        ClientIp.group48(ip)?.let { decrement(per48, it) }
    }

    private fun decrement(map: ConcurrentHashMap<String, AtomicInteger>, k: String) { map.computeIfPresent(k) { _, v -> if (v.decrementAndGet() <= 0) null else v } }

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
            val a = Cidr.literal(last) ?: throw ForwardedForError("X-Forwarded-For illisible du proxy de confiance")   // le proxy écrit toujours une adresse : sinon la requête est refusée (400)
            return key(a)
        }
        return key(peer)
    }

    /** Clé du /48 qui contient cette clé /64 (IPv6 seulement) ; null pour une IPv4. */
    fun group48(key: String): String? = if (key.startsWith("v6:")) "v6:" + key.removePrefix("v6:").split(":").take(3).joinToString(":") + "::/48" else null

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

/** `X-Forwarded-For` illisible venant du proxy de confiance : la requête est refusée (400). */
class ForwardedForError(message: String) : Exception(message)
