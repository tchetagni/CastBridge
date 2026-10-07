package castbridge.core.relay

import castbridge.core.connect.ServerUrl
import castbridge.core.gateway.GwTarget
import java.io.IOException
import java.net.Inet6Address
import java.net.InetAddress
import java.net.Socket

/**
 * Portée du tuyau (relay-R1 § 4, inventaire I-5) : le téléphone n'ouvre, pour la TV, que des connexions vers les hôtes CastBridge, sur leurs ports, et jamais vers
 * son propre réseau (box, autres appareils de la maison, bouclage du téléphone). Avant : tout hôte, tout port, seul le bouclage était refusé.
 *
 * Ce que la TV a le droit d'atteindre par ce chemin : le serveur (`bridge.sti-cm.com` : API, mises à jour, jeu en ligne sous `/play/`) en HTTPS et le tunnel d'assistance.
 * Un serveur d'essai (adresse personnalisée de la TV), un test de débit d'un tiers ou une sonde `connectivitycheck.gstatic.com` n'y passent plus.
 */
object RelayScope {
    val SERVER_HOST: String = java.net.URI(ServerUrl.DEFAULT).host.lowercase()
    /** Hôte du service de jeu : aujourd'hui le même nom que le serveur (`/play/`) ; une ligne à changer le jour où il aura le sien. */
    const val PLAY_HOST = "bridge.sti-cm.com"

    /** Liste des hôtes CastBridge (le cœur, pas l'application : un seul endroit à relire). */
    val HOSTS: Set<String> = setOf(SERVER_HOST, PLAY_HOST)

    /** 443 : HTTPS (API, jeu) ; 2200 : tunnel SSH d'assistance (docs/REMOTE-TUNNEL.md). */
    private val PORTS = setOf(443, 2200)

    fun hostAllowed(host: String?): Boolean = host != null && host.trim().lowercase().trimEnd('.') in HOSTS
    fun portAllowed(port: Int): Boolean = port in PORTS

    /** Adresse du côté du téléphone (jamais joignable pour la TV) : bouclage, « toutes interfaces », réseau local, lien local, partage CGNAT, multidiffusion, IPv6 local unique. */
    fun isLocalOrPrivate(a: InetAddress): Boolean {
        if (a.isLoopbackAddress || a.isAnyLocalAddress || a.isSiteLocalAddress || a.isLinkLocalAddress || a.isMulticastAddress) return true
        val b = a.address
        if (a is Inet6Address) return (b[0].toInt() and 0xFE) == 0xFC            // fc00::/7
        // 100.64.0.0/10 : espace de partage d'adresses des opérateurs (CGNAT), jamais une adresse de serveur public
        return b.size == 4 && (b[0].toInt() and 255) == 100 && (b[1].toInt() and 0xC0) == 64
    }
}

/**
 * Ouvre les connexions sortantes demandées par la TV, dans la portée de [RelayScope] : nom d'hôte et port autorisés AVANT toute résolution, puis toutes les adresses
 * résolues doivent être publiques (un nom permis qui viserait la box du téléphone est refusé : DNS rebinding), puis la connexion se fait sur l'adresse VÉRIFIÉE
 * (pas de seconde résolution entre la vérification et l'ouverture). Un refus est une [SecurityException] (le tuyau répond « non autorisé » à la TV).
 */
class RelayDialer(
    private val resolve: (String) -> List<InetAddress>,
    private val open: (InetAddress, Int) -> Socket,
) {
    @Throws(IOException::class)
    fun connect(t: GwTarget): Socket {
        if (!RelayScope.hostAllowed(t.host) || !RelayScope.portAllowed(t.port)) throw SecurityException("hors de la portée du relais")
        val addrs = resolve(t.host.trim().lowercase().trimEnd('.'))
        if (addrs.isEmpty()) throw java.net.UnknownHostException(t.host)
        if (addrs.any { RelayScope.isLocalOrPrivate(it) }) throw SecurityException("réseau local")
        var last: IOException? = null
        for (a in addrs) {
            try { return open(a, t.port) } catch (e: IOException) { last = e }
        }
        throw last ?: IOException("aucune adresse")
    }
}
