package castbridge.core.net

import java.net.Socket
import java.net.URL
import java.net.URLConnection

/**
 * Lier SEULEMENT les sockets vers un groupe Wi-Fi Direct à son réseau (Android 10-12, `WifiNetworkSpecifier` : le réseau du groupe n'est pas le réseau par
 * défaut). Avant (audit R-14, I-3), `bindProcessToNetwork` coupait tout l'Internet de l'app le temps de l'envoi. Le téléphone installe une liaison
 * (`Network.openConnection` / `Network.bindSocket`) pour le préfixe du groupe ; [castbridge.core.tv.TvClient], [castbridge.core.xfer.TransferClient] et
 * [castbridge.core.xfer.HttpConn.tcp] passent par ici ; toute autre adresse s'ouvre comme d'habitude.
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

    fun applies(host: String?): Boolean {
        val p = prefix ?: return false
        return binding != null && host != null && host.startsWith(p)
    }

    fun open(url: URL): URLConnection {
        val b = binding
        return if (b != null && applies(url.host)) b.open(url) else url.openConnection()
    }

    fun bind(host: String, s: Socket) {
        val b = binding
        if (b != null && applies(host)) b.bind(s)
    }
}
