package castbridge.sender

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import castbridge.core.link.HelloIps
import castbridge.core.net.BoundRoute
import castbridge.core.owner.ActivationRoutePlan.Cause
import java.net.Socket
import java.net.URL
import java.net.URLConnection

/**
 * Se relier au groupe Wi-Fi Direct d'activation de la TV (nom et mot de passe dérivés du code à 6 chiffres, `WdCode`) sans rien configurer et sans perdre les données mobiles
 * (DESIGN-ACTIVATION-SIMPLE ACT-F3). Même mécanique que `AutoWifiDirect.joinSpecifier` : `WifiNetworkSpecifier` (Android 10 et plus), réseau LOCAL seulement (sans la capacité
 * Internet : le réseau par défaut du téléphone ne change pas), et seuls les sockets vers 192.168.49.x passent par ce réseau ([BoundRoute]).
 *
 * Avant Android 10 : [bindToJoinedWifi] attend que l'usager ait rejoint le Wi-Fi à la main puis lie les sockets vers 192.168.49.x à ce Wi-Fi (Android peut garder les données mobiles
 * comme réseau par défaut quand le Wi-Fi n'a pas d'Internet : sans cette liaison, le trafic vers la TV partirait par le mobile).
 *
 * Une instance = une tentative. Les rappels d'Android arrivent sur son fil : ici rien ne bloque, et aucun code, mot de passe ni adresse n'est écrit dans un journal.
 */
class ActivationGroupJoin(ctx: Context) {
    private val cm = ctx.applicationContext.getSystemService(ConnectivityManager::class.java)
    private var callback: ConnectivityManager.NetworkCallback? = null
    private var mine: BoundRoute.Binding? = null
    @Volatile private var finished = false

    /**
     * Android 10 et plus : demande au système de rejoindre [ssid] (Android montre sa propre boîte « Se connecter ? » : l'usager touche « Connecter »). [onJoined] une fois,
     * [onFailed] une fois (introuvable ou refusé, autorisation, déjà occupé), [onLost] si la liaison tombe ensuite. [timeoutMs] : la borne du plan.
     */
    @Synchronized fun join(ssid: String, passphrase: String, timeoutMs: Int, onJoined: () -> Unit, onFailed: (Cause) -> Unit, onLost: () -> Unit) {
        if (callback != null) return
        if (android.os.Build.VERSION.SDK_INT < castbridge.core.owner.ActivationRoutePlan.GROUP_MIN_API) return onFailed(Cause(Cause.Kind.NOT_JOINED))   // WifiNetworkSpecifier : Android 10 et plus (le plan ne l'appelle pas avant)
        if (BoundRoute.applies(castbridge.core.tv.WifiDirect.GROUP_OWNER_IP)) return onFailed(Cause(Cause.Kind.BUSY))          // un groupe Wi-Fi Direct de CastBridge est déjà lié
        val spec = runCatching { WifiNetworkSpecifier.Builder().setSsid(ssid).setWpa2Passphrase(passphrase).build() }.getOrNull() ?: return onFailed(Cause(Cause.Kind.NOT_JOINED))
        val req = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).setNetworkSpecifier(spec).build()
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { if (bind(network)) onJoined() }
            override fun onUnavailable() { if (!finished) onFailed(Cause(Cause.Kind.NOT_JOINED)) }
            override fun onLost(network: Network) { if (!finished) { unbind(); onLost() } }
        }
        callback = cb
        try { cm.requestNetwork(req, cb, timeoutMs) }
        catch (e: SecurityException) { callback = null; onFailed(Cause(Cause.Kind.GROUP_PERMISSION)) }
        catch (e: RuntimeException) { callback = null; onFailed(Cause(Cause.Kind.NOT_JOINED)) }
    }

    /**
     * Avant Android 10 : le téléphone est (ou sera) relié par l'usager au Wi-Fi « DIRECT-CB-… » ; dès qu'un réseau Wi-Fi du téléphone porte une adresse 192.168.49.x, les sockets
     * vers la TV lui sont liés et [onReady] est appelé. [onFailed] : la demande de réseau est refusée par Android.
     */
    @Synchronized fun bindToJoinedWifi(onReady: () -> Unit, onFailed: (Cause) -> Unit) {
        if (callback != null) return
        val req = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
        val cb = object : ConnectivityManager.NetworkCallback() {
            private fun check(n: Network, lp: LinkProperties?) {
                val onGroup = lp?.linkAddresses?.any { it.address.hostAddress?.startsWith(HelloIps.GROUP_PREFIX) == true } == true
                if (onGroup && bind(n)) onReady()
            }
            override fun onAvailable(network: Network) = check(network, cm.getLinkProperties(network))
            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) = check(network, linkProperties)
            override fun onLost(network: Network) { unbind() }
        }
        callback = cb
        try { cm.requestNetwork(req, cb) }
        catch (e: SecurityException) { callback = null; onFailed(Cause(Cause.Kind.GROUP_PERMISSION)) }
        catch (e: RuntimeException) { callback = null; onFailed(Cause(Cause.Kind.NOT_JOINED)) }
    }

    /** Lie les sockets vers le groupe à [network] (une seule fois par tentative). */
    @Synchronized private fun bind(network: Network): Boolean {
        if (finished || mine != null) return false
        val b = object : BoundRoute.Binding {
            override fun open(url: URL): URLConnection = network.openConnection(url)
            override fun bind(s: Socket) = network.bindSocket(s)
        }
        mine = b
        BoundRoute.set(HelloIps.GROUP_PREFIX, b)
        return true
    }

    @Synchronized private fun unbind() { mine?.let { BoundRoute.clearIf(it) }; mine = null }

    /** Rend le réseau et la liaison des sockets (fin de l'activation, voie abandonnée, écran quitté). Sans effet une seconde fois. */
    @Synchronized fun release() {
        finished = true
        unbind()
        callback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        callback = null
    }
}
