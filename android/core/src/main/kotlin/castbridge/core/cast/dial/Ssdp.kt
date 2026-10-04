package castbridge.core.cast.dial

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.util.Random
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** SSDP (UPnP 1.1 / DIAL 2.1) : formats exacts des messages, purs ; la partie socket est [SsdpServer]. */
object Ssdp {
    const val GROUP = "239.255.255.250"
    const val PORT = 1900
    const val MAX_AGE = 1800
    const val MAX_DATAGRAM = 1500
    const val SERVER = "Linux/4.9 UPnP/1.1 CastBridge-TV/1"
    const val ROOT = "upnp:rootdevice"
    const val ALL = "ssdp:all"

    class Search(val st: String, val mx: Int)

    /** Requête M-SEARCH valide (`MAN: "ssdp:discover"`, MX numérique), sinon null. MX borné à 1..5 s. */
    fun parseSearch(data: String): Search? {
        if (data.length > MAX_DATAGRAM) return null
        val lines = data.split("\r\n")
        if (lines.isEmpty() || lines[0].trim() != "M-SEARCH * HTTP/1.1") return null
        val h = HashMap<String, String>()
        for (l in lines.drop(1)) {
            if (l.isEmpty()) continue
            val i = l.indexOf(':'); if (i <= 0) return null
            h.putIfAbsent(l.substring(0, i).trim().lowercase(), l.substring(i + 1).trim())
        }
        if (h["man"] != "\"ssdp:discover\"") return null
        val st = h["st"] ?: return null
        val mx = h["mx"]?.let { it.toIntOrNull() ?: return null } ?: 1
        if (mx < 1) return null
        return Search(st, mx.coerceAtMost(5))
    }

    /** La cible à annoncer pour un ST demandé, ou null : DIAL, `upnp:rootdevice`, `ssdp:all` (→ DIAL). */
    fun answerTarget(st: String): String? = when (st) {
        DialRules.SEARCH_TARGET, ALL -> DialRules.SEARCH_TARGET
        ROOT -> ROOT
        else -> null
    }

    fun usn(udn: String, target: String) = if (target == udn) udn else "$udn::$target"

    /** Délai aléatoire dans [0, MX s[ . */
    fun jitterMs(mx: Int, rnd: Random): Long = rnd.nextInt(mx.coerceIn(1, 5) * 1000).toLong()

    fun searchResponse(target: String, location: String, udn: String): String =
        "HTTP/1.1 200 OK\r\n" +
        "LOCATION: $location\r\n" +
        "CACHE-CONTROL: max-age=$MAX_AGE\r\n" +
        "EXT:\r\n" +
        "SERVER: $SERVER\r\n" +
        "ST: $target\r\n" +
        "USN: ${usn(udn, target)}\r\n" +
        "\r\n"

    fun notifyTargets(udn: String) = listOf(ROOT, udn, DialRules.SEARCH_TARGET)

    fun notifyMessage(nt: String, udn: String, location: String?, alive: Boolean): String =
        "NOTIFY * HTTP/1.1\r\n" +
        "HOST: $GROUP:$PORT\r\n" +
        (if (alive) "CACHE-CONTROL: max-age=$MAX_AGE\r\nLOCATION: $location\r\n" else "") +
        "NT: $nt\r\n" +
        "NTS: ssdp:${if (alive) "alive" else "byebye"}\r\n" +
        (if (alive) "SERVER: $SERVER\r\n" else "") +
        "USN: ${usn(udn, nt)}\r\n" +
        "\r\n"
}

/**
 * Répondeur SSDP : écoute [port] (1900 en vrai, join du groupe 239.255.255.250 si [multicast]), répond aux M-SEARCH
 * des sources du réseau local après un délai aléatoire borné par MX, annonce alive/byebye vers [notifyTarget].
 * Les tests le font tourner sur la boucle locale sans multicast.
 */
class SsdpServer(
    private val udn: String,
    private val locationFor: (InetAddress?) -> String?,
    private val port: Int = Ssdp.PORT,
    private val multicast: Boolean = true,
    private val bindAddress: InetAddress? = null,
    private val notifyTarget: InetSocketAddress = InetSocketAddress(Ssdp.GROUP, Ssdp.PORT),
    private val random: Random = Random(),
    private val aliveEverySeconds: Long = 900,
    private val log: (String) -> Unit = {},
) {
    private var socket: DatagramSocket? = null
    private var pool: ScheduledExecutorService? = null
    private val pending = AtomicInteger(0)
    val localPort: Int get() = socket?.localPort ?: -1

    @Synchronized fun start(): Boolean {
        if (socket != null) return true
        val s = try {
            if (multicast) MulticastSocket(null).also {
                it.reuseAddress = true
                it.bind(InetSocketAddress(port))
                @Suppress("DEPRECATION") it.joinGroup(InetAddress.getByName(Ssdp.GROUP))
            } else DatagramSocket(null).also { it.reuseAddress = true; it.bind(InetSocketAddress(bindAddress, port)) }
        } catch (e: Exception) { log("SSDP : écoute impossible (${e.javaClass.simpleName})"); return false }
        socket = s
        val p = Executors.newScheduledThreadPool(2) { r -> Thread(r, "cb-ssdp").apply { isDaemon = true } }
        pool = p
        p.execute { loop(s) }
        if (aliveEverySeconds > 0) p.scheduleWithFixedDelay({ announce(true) }, 1, aliveEverySeconds, TimeUnit.SECONDS)
        return true
    }

    @Synchronized fun stop() {
        val s = socket ?: return
        announce(false)
        socket = null
        runCatching { s.close() }
        pool?.shutdownNow(); pool = null
    }

    private fun loop(s: DatagramSocket) {
        val buf = ByteArray(Ssdp.MAX_DATAGRAM + 1)
        while (!s.isClosed) {
            val pk = DatagramPacket(buf, buf.size)
            try { s.receive(pk) } catch (e: Exception) { if (s.isClosed) return else continue }
            runCatching { onDatagram(s, pk.address, pk.port, String(pk.data, 0, pk.length, Charsets.ISO_8859_1)) }
        }
    }

    private fun onDatagram(s: DatagramSocket, from: InetAddress, fromPort: Int, text: String) {
        if (!DialRules.isLanSource(from)) return
        val q = Ssdp.parseSearch(text) ?: return
        val target = Ssdp.answerTarget(q.st) ?: return
        val loc = locationFor(from) ?: return
        if (pending.get() >= 64) return
        pending.incrementAndGet()
        val data = Ssdp.searchResponse(target, loc, udn).toByteArray(Charsets.ISO_8859_1)
        val delay = Ssdp.jitterMs(q.mx, random)
        val p = pool
        if (p == null) { pending.decrementAndGet(); return }
        try {
            p.schedule({
                try { if (!s.isClosed) s.send(DatagramPacket(data, data.size, from, fromPort)) } catch (_: Exception) {} finally { pending.decrementAndGet() }
            }, delay, TimeUnit.MILLISECONDS)
        } catch (e: Exception) { pending.decrementAndGet() }
    }

    /** NOTIFY alive (avec LOCATION) ou byebye pour les trois cibles. */
    fun announce(alive: Boolean) {
        val s = socket ?: return
        val loc = if (alive) (locationFor(null) ?: return) else null
        for (nt in Ssdp.notifyTargets(udn)) {
            val d = Ssdp.notifyMessage(nt, udn, loc, alive).toByteArray(Charsets.ISO_8859_1)
            runCatching { s.send(DatagramPacket(d, d.size, notifyTarget)) }
        }
    }
}
