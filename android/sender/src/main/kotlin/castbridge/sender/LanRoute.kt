package castbridge.sender

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import castbridge.core.net.BoundRoute
import castbridge.core.net.LanAddress
import java.net.Inet4Address
import java.net.Socket
import java.net.URL
import java.net.URLConnection

/**
 * Copie sans Internet. Quand le Wi-Fi de la maison perd l'accès à Internet, Android (Samsung « passer aux données mobiles ») fait du mobile le réseau PAR DÉFAUT : un socket
 * non lié vers l'IP privée de la TV sortirait par le mobile et échouerait. On suit donc le réseau Wi-Fi (sans exiger INTERNET ni VALIDATED, `registerNetworkCallback`) et les
 * sockets vers une IPv4 privée d'un de ses sous-réseaux y sont liés un par un ([BoundRoute], jamais `bindProcessToNetwork`). Le groupe Wi-Fi Direct garde la priorité.
 */
internal object LanRoute {
    @Volatile private var wifi: Network? = null
    @Volatile private var props: LinkProperties? = null
    private var started = false
    private var cb: ConnectivityManager.NetworkCallback? = null
    private var binding: BoundRoute.Binding? = null

    /** Le réseau Wi-Fi courant (même sans Internet), ou null. */
    fun wifiNetwork(): Network? = wifi

    fun request(): NetworkRequest = NetworkRequest.Builder()
        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .build()

    fun covers(host: String): Boolean {
        val lp = props ?: return false
        return lp.linkAddresses.any { la -> (la.address as? Inet4Address)?.hostAddress?.let { LanAddress.inSubnet(host, it, la.prefixLength) } == true }
    }

    @Synchronized fun start(ctx: Context) {
        if (started) return
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return
        val b = object : BoundRoute.Binding {
            override fun open(url: URL): URLConnection = wifi?.openConnection(url) ?: url.openConnection()
            override fun bind(s: Socket) { wifi?.bindSocket(s) }
        }
        val c = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { wifi = network; props = runCatching { cm.getLinkProperties(network) }.getOrNull() }
            override fun onLinkPropertiesChanged(network: Network, lp: LinkProperties) { if (network == wifi) props = lp }
            override fun onLost(network: Network) { if (network == wifi) { wifi = null; props = null } }
        }
        if (runCatching { cm.registerNetworkCallback(request(), c) }.isSuccess) {
            BoundRoute.setLan(b) { covers(it) }; binding = b; cb = c; started = true
        }
    }

    @Synchronized fun stop(ctx: Context) {
        cb?.let { c -> runCatching { ctx.getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(c) } }
        binding?.let { BoundRoute.releaseLan(it) }
        cb = null; binding = null; wifi = null; props = null; started = false
    }
}
