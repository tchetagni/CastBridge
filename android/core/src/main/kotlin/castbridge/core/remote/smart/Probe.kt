package castbridge.core.remote.smart

import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Passive gathering for the TV the USER chose (never for a device found by scanning): a handful of TCP connects (connect only,
 * nothing is sent), one standard SSDP search (answers from that TV only) and the UPnP description it points to.
 */
object PortProbe {
    /** Ports of the strategies (docs/REMOTE.md): CastBridge-TV, CVTE, Roku, Samsung, LG, Philips, Vizio, Android TV, web. */
    val KNOWN = listOf(80, 1925, 1926, 3000, 3001, 6466, 6467, 7345, 8001, 8002, 8060, 8125, 8765, 9000)

    /** The ones that accept a TCP connection within [timeoutMs] each, tried one after the other (no parallel burst). */
    fun open(host: String, ports: List<Int> = KNOWN, timeoutMs: Int = 400): Set<Int> = ports.filterTo(LinkedHashSet()) { p ->
        try { Socket().use { it.connect(InetSocketAddress(host, p), timeoutMs); true } } catch (e: IOException) { false }
    }
}

object Ssdp {
    val MULTICAST = InetSocketAddress("239.255.255.250", 1900)

    /**
     * Sends ONE `M-SEARCH` (upnp:rootdevice) to [group] and keeps the first answer that comes from [host], then reads its description.
     * Returns null when the TV does not answer (many do not): that is just one clue less.
     */
    fun discover(host: String, group: InetSocketAddress = MULTICAST, listenMs: Int = 2000, httpTimeoutMs: Int = 1500): UpnpInfo? {
        val msg = ("M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 1\r\nST: upnp:rootdevice\r\n\r\n").toByteArray()
        val headers = try {
            DatagramSocket().use { s ->
                s.soTimeout = 300
                s.send(DatagramPacket(msg, msg.size, group))
                val until = System.currentTimeMillis() + listenMs
                val buf = ByteArray(2048); var found: Map<String, String>? = null
                while (found == null && System.currentTimeMillis() < until) {
                    val p = DatagramPacket(buf, buf.size)
                    try { s.receive(p) } catch (e: java.net.SocketTimeoutException) { continue }
                    if (p.address == InetAddress.getByName(host)) found = Upnp.parseSsdpHeaders(String(p.data, 0, p.length, Charsets.UTF_8))
                }
                found
            }
        } catch (e: IOException) { null } ?: return null
        val loc = headers["location"]
        val server = headers["server"]
        if (loc == null || !runCatching { java.net.URI(loc).host.equals(host, true) }.getOrDefault(false)) return UpnpInfo(server = server)   // never follow a location on another host
        return try {
            val r = Http.request("GET", loc, connectTimeoutMs = httpTimeoutMs, readTimeoutMs = httpTimeoutMs)
            if (r.status == 200) Upnp.parseDescription(r.body, loc, server) else UpnpInfo(server = server)
        } catch (e: IOException) { UpnpInfo(server = server) }
    }
}

object TvProbe {
    /** Hints for [host]: open ports + SSDP/UPnP, merged with what the platform already knows (DNS-SD records, MAC, Bluetooth name). */
    fun gather(host: String, mdns: List<MdnsRecord> = emptyList(), mac: String? = null, bluetoothName: String? = null,
               ports: List<Int> = PortProbe.KNOWN, group: InetSocketAddress = Ssdp.MULTICAST): TvHints =
        TvHints(host = host, mdns = mdns, upnp = Ssdp.discover(host, group), mac = mac, bluetoothName = bluetoothName, openPorts = PortProbe.open(host, ports))
}
