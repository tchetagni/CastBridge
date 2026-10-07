package castbridge.receiver

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import castbridge.core.link.HelloIps
import castbridge.core.tv.activation.ActivationGroupPolicy

/**
 * The Android side of [ActivationGroupPolicy] (act-tv-2, docs/TV-ACTIVATION-CLE-USB.md « Voie A : deux cas »): what the TV is linked to right now, and its Wi-Fi link relaunched once the
 * activation group is given back. Nothing here decides: the rules are the policy's (pure, tested). Never a code, an address or a password in the journal.
 */
object ActivationNet {
    private const val TAG = "CastBridgeWD"

    /**
     * Whether the TV has a Wi-Fi network and/or a cable, each with a local IPv4 address. Any network counts, not only the system's default one (mobile data may be the default and is
     * no LAN). The activation group's own interface (« p2p… ») and subnet (192.168.49.x, [HelloIps]) never count: once the group exists, « the TV is on a Wi-Fi network » must still
     * mean the box's network. A link-local address (169.254.x.x: the box never answered) is no network either. Cheap (a few binder calls): read again at every decision.
     */
    @Suppress("DEPRECATION")
    fun read(ctx: Context): ActivationGroupPolicy.Net {
        val cm = runCatching { ctx.applicationContext.getSystemService(ConnectivityManager::class.java) }.getOrNull() ?: return ActivationGroupPolicy.Net()
        fun up(transport: Int) = runCatching {
            cm.allNetworks.any { n ->
                cm.getNetworkCapabilities(n)?.hasTransport(transport) == true && cm.getLinkProperties(n)?.let { lp ->
                    lp.interfaceName?.startsWith("p2p") != true && lp.linkAddresses.any { a ->
                        val ip = a.address
                        ip is java.net.Inet4Address && !ip.isLoopbackAddress && !ip.isLinkLocalAddress && HelloIps.isLan(ip.hostAddress.orEmpty())
                    }
                } == true
            }
        }.getOrDefault(false)
        return ActivationGroupPolicy.Net(wifiConnected = up(NetworkCapabilities.TRANSPORT_WIFI), ethernetUp = up(NetworkCapabilities.TRANSPORT_ETHERNET))
    }

    private fun wifiManager(ctx: Context): WifiManager? = runCatching { ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager }.getOrNull()

    private fun wifiEnabled(ctx: Context): Boolean = runCatching { wifiManager(ctx)?.isWifiEnabled }.getOrNull() ?: true

    /**
     * `WifiManager.reconnect()`: best effort. Android 10+ answers false (and does nothing) to an app that targets API 29 or more, and some boxes bring their Wi-Fi back by themselves anyway:
     * the journal says what Android answered, nothing relies on it. The real protection is [ActivationGroupPolicy]: no group while the TV is on a Wi-Fi network, unless asked.
     */
    @Suppress("DEPRECATION")
    private fun reconnect(ctx: Context): Boolean = runCatching { wifiManager(ctx)?.reconnect() }.getOrNull() ?: false

    /**
     * Once the activation group is given back: looks at the Wi-Fi link again a few seconds later ([ActivationGroupPolicy.restoreDelayMs]) and, if the group had cut a link that was there
     * ([wifiBefore]) and it is not back, asks Android to reconnect; at most [ActivationGroupPolicy.RESTORE_ATTEMPTS] times, then says it once and stops. Main looper, never blocks, no loop.
     */
    fun restoreLater(ctx: Context, wifiBefore: Boolean, attempts: Int = 0) {
        if (!wifiBefore) return                                               // the TV had no Wi-Fi link: nothing was cut
        val app = ctx.applicationContext
        Handler(Looper.getMainLooper()).postDelayed({
            runCatching {
                val now = read(app).wifiConnected
                when (ActivationGroupPolicy.restore(wifiBefore, now, wifiEnabled(app), attempts)) {
                    ActivationGroupPolicy.Restore.NOTHING -> Log.i(TAG, if (now) "Wi-Fi de la TV : relié après le réseau d'activation, rien à relancer" else "Wi-Fi de la TV : éteint, laissé tel quel")
                    ActivationGroupPolicy.Restore.RECONNECT -> {
                        Log.i(TAG, "Wi-Fi de la TV coupé après le réseau d'activation : relance ${attempts + 1}/${ActivationGroupPolicy.RESTORE_ATTEMPTS} (${if (reconnect(app)) "acceptée" else "refusée par Android"})")
                        restoreLater(app, wifiBefore, attempts + 1)
                    }
                    ActivationGroupPolicy.Restore.GIVE_UP -> Log.w(TAG, "Wi-Fi de la TV toujours coupé : à reconnecter dans Paramètres › Réseau")
                }
            }.onFailure { Log.w(TAG, "relance du Wi-Fi : ${it.javaClass.simpleName}") }
        }, ActivationGroupPolicy.restoreDelayMs(attempts))
    }
}
