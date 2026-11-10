package castbridge.core.net

import java.net.Socket
import java.net.URL
import java.net.URLConnection

/**
 * Lier SEULEMENT les sockets vers un groupe Wi-Fi Direct à son réseau (Android 10-12, `WifiNetworkSpecifier` : le réseau du groupe n'est pas le réseau par
 * défaut). Avant (audit R-14, I-3), `bindProcessToNetwork` coupait tout l'Internet de l'app le temps de l'envoi. Le téléphone installe une liaison
 * (`Network.openConnection` / `Network.bindSocket`) pour le préfixe du groupe ; [castbridge.core.tv.TvClient], [castbridge.core.xfer.TransferClient] et
 * [castbridge.core.xfer.HttpConn.tcp] passent par ici ; toute autre adresse s'ouvre comme d'habitude.
 *
 * Une seule méthode de liaison dans toute l'app (R-29, inventaire I-6) : la voie automatique (`AutoWifiDirect`) comme les écrans manuels (envoi Bluetooth
 * `BtUploadService`, `WifiDirectScreen`) passent ici ; plus aucun `bindProcessToNetwork` (test `NoProcessBindingTest`).
 */
object BoundRoute {
    interface Binding {
        fun open(url: URL): URLConnection
        /** Avant `connect` : le socket sortira par le réseau du groupe. */
        fun bind(s: Socket)
    }

    @Volatile private var prefix: String? = null
    @Volatile private var binding: Binding? = null

    @Synchronized fun set(hostPrefix: String, b: Binding) { prefix = hostPrefix; binding = b }
    @Synchronized fun clear() { binding = null; prefix = null }
    /** Retire la liaison [b] seulement si c'est encore celle qui est installée (une jonction plus récente peut l'avoir remplacée : on ne retire pas la sienne). */
    @Synchronized fun release(b: Binding) { if (binding === b) { binding = null; prefix = null } }

    fun applies(host: String?): Boolean {
        val p = prefix ?: return false
        return binding != null && host != null && host.startsWith(p)
    }

    // Repli « réseau local » : le Wi-Fi de la maison sans Internet validé (Android bascule alors le réseau PAR DÉFAUT sur le mobile : un socket non lié
    // vers l'IP de la TV partirait par le mobile et échouerait). Priorité au groupe Wi-Fi Direct ; ce repli ne concerne que les IPv4 privées couvertes par le Wi-Fi.
    @Volatile private var lan: Binding? = null
    @Volatile private var lanCovers: (String) -> Boolean = { false }

    /** [covers] : l'hôte est-il sur un sous-réseau du Wi-Fi courant ? (évalué à chaque connexion, le Wi-Fi peut changer de sous-réseau). */
    @Synchronized fun setLan(b: Binding, covers: (String) -> Boolean) { lan = b; lanCovers = covers }
    @Synchronized fun releaseLan(b: Binding) { if (lan === b) { lan = null; lanCovers = { false } } }
    @Synchronized fun clearLan() { lan = null; lanCovers = { false } }

    fun lanApplies(host: String?): Boolean = lan != null && host != null && LanAddress.isPrivate(host) && lanCovers(host)

    fun open(url: URL): URLConnection {
        val b = binding
        if (b != null && applies(url.host)) return b.open(url)
        val l = lan
        return if (l != null && lanApplies(url.host)) l.open(url) else url.openConnection()
    }

    fun bind(host: String, s: Socket) {
        val b = binding
        if (b != null && applies(host)) { b.bind(s); return }
        val l = lan
        if (l != null && lanApplies(host)) l.bind(s)
    }
}

/** Décisions pures sur les adresses IPv4 littérales (aucune résolution DNS). */
object LanAddress {
    private fun parse(host: String): IntArray? {
        val p = host.split('.')
        if (p.size != 4) return null
        val v = IntArray(4)
        for (i in 0..3) { v[i] = p[i].toIntOrNull()?.takeIf { it in 0..255 && p[i].length <= 3 } ?: return null }
        return v
    }

    /** 10/8, 172.16/12, 192.168/16, 169.254/16. */
    fun isPrivate(host: String): Boolean {
        val v = parse(host) ?: return false
        return v[0] == 10 || (v[0] == 172 && v[1] in 16..31) || (v[0] == 192 && v[1] == 168) || (v[0] == 169 && v[1] == 254)
    }

    private fun toInt(v: IntArray) = (v[0] shl 24) or (v[1] shl 16) or (v[2] shl 8) or v[3]

    /** [host] est dans le sous-réseau de [localAddress]/[prefixLength] (tous deux IPv4 littérales). */
    fun inSubnet(host: String, localAddress: String, prefixLength: Int): Boolean {
        val h = parse(host) ?: return false
        val l = parse(localAddress) ?: return false
        if (prefixLength !in 0..32) return false
        val mask = if (prefixLength == 0) 0 else -1 shl (32 - prefixLength)
        return (toInt(h) and mask) == (toInt(l) and mask)
    }
}
