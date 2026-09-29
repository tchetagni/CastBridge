package castbridge.core.ssh

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
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
 * (127.0.0.1:[sshPort]). At most [maxConnections] at once (more are closed at once). Nothing is bypassed: the SSH server
 * still demands an authorised key, applies its lockout (per Bluetooth device, thanks to [peers]) and its timeouts.
 */
class SshTunnel(
    private val peers: PeerRegistry,
    private val sshPort: Int,
    private val maxConnections: Int = 2,
    private val bufferBytes: Int = 32 * 1024,
    private val connectTimeoutMs: Int = 3000,
) {
    data class Active(val peer: String, val name: String, val since: Long)

    private val slots = Semaphore(maxConnections)
    private val active = ConcurrentHashMap<Int, Active>()

    fun active(): List<Active> = active.values.sortedBy { it.since }

    /**
     * Serves one Bluetooth link until it ends (blocking). [peer] identifies the device (its Bluetooth address).
     * Returns false if refused (too many connections, SSH server not listening); [close] is always called.
     */
    fun serve(peer: String, name: String, input: InputStream, output: OutputStream, close: () -> Unit, onChange: () -> Unit = {}): Boolean {
        if (!slots.tryAcquire()) { runCatching(close); return false }
        val sock = Socket()
        var port = -1
        try {
            // Bind first, register, then connect: the server can never see this connection before it is attributed.
            sock.bind(InetSocketAddress("127.0.0.1", 0))
            port = sock.localPort
            peers.register(port, "bt:$peer")
            sock.connect(InetSocketAddress("127.0.0.1", sshPort), connectTimeoutMs)
            sock.tcpNoDelay = true
            active[port] = Active(peer, name, System.currentTimeMillis())
            runCatching(onChange)
            val r = ByteRelay(input, output, sock.getInputStream(), sock.getOutputStream(), close, { sock.close() }, bufferBytes, "bt-ssh").start()
            r.join()
            return true
        } catch (e: IOException) {
            return false
        } finally {
            runCatching(close); runCatching { sock.close() }
            if (port >= 0) { peers.unregister(port); active.remove(port) }
            slots.release()
            runCatching(onChange)
        }
    }
}
