package castbridge.core.ssh

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Two-way byte pump between two links (a Bluetooth socket and a TCP socket). SSH runs unchanged *through* it: the pump
 * never looks at the bytes (end-to-end encryption and key authentication stay between the SSH client and the TV's server).
 * When either direction ends or fails, both links are closed (RFCOMM has no half-close), which also unblocks the other thread.
 */
class ByteRelay(
    private val aIn: InputStream, private val aOut: OutputStream,
    private val bIn: InputStream, private val bOut: OutputStream,
    private val closeA: () -> Unit, private val closeB: () -> Unit,
    private val bufferBytes: Int = 32 * 1024,
    private val name: String = "relay",
) {
    val bytesAtoB = AtomicLong()
    val bytesBtoA = AtomicLong()
    private val closed = AtomicBoolean(false)
    private val threads = ArrayList<Thread>(2)
    @Volatile var lastActivity = System.currentTimeMillis(); private set

    fun start(): ByteRelay {
        threads += pump(aIn, bOut, bytesAtoB, "$name-a2b")
        threads += pump(bIn, aOut, bytesBtoA, "$name-b2a")
        return this
    }

    private fun pump(src: InputStream, dst: OutputStream, count: AtomicLong, tn: String) = Thread({
        val buf = ByteArray(bufferBytes)
        try {
            while (!closed.get()) {
                val n = src.read(buf)
                if (n < 0) break
                if (n == 0) continue
                dst.write(buf, 0, n); dst.flush()
                count.addAndGet(n.toLong()); lastActivity = System.currentTimeMillis()
            }
        } catch (_: IOException) {
        } finally { close() }
    }, tn).apply { isDaemon = true; start() }

    /** Closes both links once (idempotent). */
    fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { closeA() }; runCatching { closeB() }
    }

    val isClosed get() = closed.get()

    /** Waits for both directions to finish. */
    fun join(timeoutMs: Long = 0) { threads.forEach { it.join(timeoutMs) } }
}

/**
 * Who is behind a loopback TCP connection to the SSH server. Every Bluetooth client reaches the server from 127.0.0.1, so
 * the lockout after failed logins would lock them all together: the tunnel records "local source port -> Bluetooth
 * address" *before* connecting, and the server asks [clientKey] for the identity to count failures against.
 */
class PeerRegistry {
    private val byPort = ConcurrentHashMap<Int, String>()
    /** Stable virtual address per tunnelled device (240.77.x.y: reserved space, never routed) for servers that only look at an address. */
    private val virtualByPeer = ConcurrentHashMap<String, String>()
    private val peerByVirtual = ConcurrentHashMap<String, String>()

    /**
     * The address a server should see for a connection arriving through the tunnel from [remote] (loopback, registered port), or
     * null when it is not a tunnelled one. NanoHTTPD turns every loopback address into "127.0.0.1", so the identity travels as a
     * distinct, non-loopback address and [clientKey] maps it back to "bt:<address>".
     */
    fun virtualAddress(remote: InetSocketAddress): java.net.InetAddress? {
        if (remote.address?.isLoopbackAddress != true) return null
        val peer = byPort[remote.port] ?: return null
        val ip = synchronized(virtualByPeer) {
            virtualByPeer.getOrPut(peer) {
                val n = virtualByPeer.size + 1
                "240.77.${(n shr 8) and 0xff}.${n and 0xff}".also { peerByVirtual[it] = peer }
            }
        }
        return java.net.InetAddress.getByAddress(ip.split('.').map { it.toInt().toByte() }.toByteArray())
    }

    /** Same as [clientKey] for a server that only knows the client's address text. */
    fun keyOfAddress(ip: String?): String = if (ip == null) "?" else peerByVirtual[ip] ?: ip

    fun register(localPort: Int, peer: String) { byPort[localPort] = peer }
    fun unregister(localPort: Int) { byPort.remove(localPort) }
    fun peerOf(localPort: Int): String? = byPort[localPort]

    /** "bt:AA:BB:.." for a tunnelled connection, else the client's IP address (unchanged behaviour). */
    fun clientKey(remote: InetSocketAddress?): String {
        val a = remote?.address ?: return ""
        if (a.isLoopbackAddress) byPort[remote.port]?.let { return it }
        return a.hostAddress.orEmpty()
    }

    fun size() = byPort.size
}

/**
 * TV side of "SSH over Bluetooth": each accepted Bluetooth link is joined to a fresh TCP connection to the local SSH server
 * (127.0.0.1:[sshPort]) by a [castbridge.core.tunnel.TcpTunnel] (no handshake: the link carries the raw SSH bytes, so any
 * plain RFCOMM client works). At most [maxConnections] at once. Nothing is bypassed: the SSH server still demands an authorised
 * key, applies its lockout (per Bluetooth device, thanks to [peers]) and its timeouts.
 */
class SshTunnel(
    peers: PeerRegistry,
    sshPort: Int,
    maxConnections: Int = 2,
    bufferBytes: Int = 32 * 1024,
    connectTimeoutMs: Int = 3000,
    /** One line per refusal / end of link (no keys, no PIN). */
    log: (String) -> Unit = {},
    /** A link silent this long is closed (sshd's own idle limit is 15 min, so this only frees links whose phone vanished). */
    idleMs: () -> Long = { 16 * 60_000L },
) {
    data class Active(val peer: String, val name: String, val since: Long)

    private val tunnel = castbridge.core.tunnel.TcpTunnel("ssh", peers, sshPort, maxConnections, bufferBytes, connectTimeoutMs,
        idleMs = idleMs, handshake = false, targetDownMessage = "le serveur SSH de la TV n'écoute pas (SSH désactivé sur la TV ?)", log = log)

    /** Last failure, in French (null after a link that worked). */
    val lastError get() = tunnel.lastError

    fun active(): List<Active> = tunnel.active().map { Active(it.peer, it.name, it.since) }

    /**
     * Serves one Bluetooth link until it ends (blocking). [peer] identifies the device (its Bluetooth address).
     * Returns false if refused (too many connections, SSH server not listening); [close] is always called.
     */
    fun serve(peer: String, name: String, input: InputStream, output: OutputStream, close: () -> Unit, onChange: () -> Unit = {}): Boolean =
        tunnel.serve(peer, name, input, output, close, onChange).served
}
