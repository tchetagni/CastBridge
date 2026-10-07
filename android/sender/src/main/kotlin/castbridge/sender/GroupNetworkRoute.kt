package castbridge.sender

import android.net.Network
import castbridge.core.link.HelloIps
import castbridge.core.net.BoundRoute
import java.net.Socket
import java.net.URL
import java.net.URLConnection

/**
 * The phone joined the TV's Wi-Fi Direct group with a `WifiNetworkSpecifier` (Android 10-12, the manual screens): that network has no Internet and is not the default one.
 * Only the sockets towards the group's addresses (192.168.49.x) are tied to it, one by one ([BoundRoute], the same way as the automatic path of [AutoWifiDirect]); the rest of the
 * app keeps its Internet. R-29 (inventory I-6): `bindProcessToNetwork` used to tie EVERY socket of the app to the group, cutting orders, lots, telemetry and the gateway's own exit
 * for the whole join.
 */
internal object GroupNetworkRoute {
    /** Ties the group's addresses to [network]; give the returned binding back to [release] when the group is left. */
    fun install(network: Network): BoundRoute.Binding = object : BoundRoute.Binding {
        override fun open(url: URL): URLConnection = network.openConnection(url)
        override fun bind(s: Socket) = network.bindSocket(s)
    }.also { BoundRoute.set(HelloIps.GROUP_PREFIX, it) }

    /** Removes [binding] if it is still the installed one (a newer join, e.g. the automatic path, keeps its own). */
    fun release(binding: BoundRoute.Binding?) { if (binding != null) BoundRoute.release(binding) }
}
