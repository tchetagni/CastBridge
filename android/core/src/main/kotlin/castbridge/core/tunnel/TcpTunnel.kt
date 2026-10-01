package castbridge.core.tunnel

import castbridge.core.ssh.ByteRelay
import castbridge.core.ssh.PeerRegistry
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Status byte the TV writes first on a link that has a handshake (the API tunnel). The SSH tunnel carries raw SSH bytes only. */
object TunnelStatus {
    const val OK = 0
    /** Too many links at once on the TV. */
    const val BUSY = 1
    /** This device may not use the tunnel (not paired, or the switch is off and the phone is not a trusted one). */
    const val REFUSED = 2
    /** The local server (HTTP API / SSH) does not answer on the TV. */
    const val TARGET_DOWN = 3
    const val INTERNAL = 4

    fun describe(code: Int) = when (code) {
        OK -> "ok"
        BUSY -> "la TV a déjà trop de liaisons Bluetooth ouvertes : réessayez dans quelques secondes"
        REFUSED -> "la TV n'autorise pas ce téléphone sur ce service (appairage Bluetooth, ou « API par Bluetooth » coupée sur la TV)"
        TARGET_DOWN -> "le service de la TV ne répond pas (CastBridge TV vient de démarrer ou redémarre ?)"
        INTERNAL -> "erreur interne sur la TV"
        else -> "erreur de la TV ($code)"
    }
}

/**
 * TV side of a TCP-over-Bluetooth service: each accepted RFCOMM link is joined, byte for byte, to a fresh TCP connection to a
 * local server (127.0.0.1:[targetPort]: the HTTP API or the SSH server). The tunnel never looks at the bytes and bypasses nothing:
 * the server still does all its checking (PIN / token / parental code, SSH keys), with its own lockout.
 *
 * - **Who is behind it.** Every tunnelled connection reaches the server from loopback, so one wrong PIN would lock all of them.
 *   The tunnel records which paired device (the address proven by the secure RFCOMM socket) owns the connection *before* it connects:
 *   by local source port (the SSH server asks [PeerRegistry.clientKey]; the HTTP server gets a per-device virtual address from
 *   [PeerRegistry.virtualAddress], see [AttributedSocket]). Failures are then counted per device ("bt:AA:BB:..").
 * - **Never a stuck slot.** At most [maxConnections] live links. A link silent for [idleMs] is closed; when the tunnel is full, the
 *   link that has been silent longest (and at least [evictIdleMs]) is closed to make room. Every path releases what it took.
 * - **Every refusal says why**: one INFO line through [log], [lastError] (French, for the TV and the phone panel) and, with
 *   [handshake], a status byte to the phone before the link is closed.
 * - **Flow control.** One fixed buffer per direction, blocking writes: a slow TV disk slows the phone down, nothing piles up.
 */
class TcpTunnel(
    private val label: String,
    private val peers: PeerRegistry,
    private val targetPort: Int,
    private val maxConnections: Int,
    private val bufferBytes: Int = 16 * 1024,
    private val connectTimeoutMs: Int = 3000,
    /** Silence after which a link is closed (longer while the TV is busy with an operation: the caller decides). */
    private val idleMs: () -> Long = { 30_000L },
    private val evictIdleMs: Long = 60_000L,
    private val handshake: Boolean,
    private val targetDownMessage: String = TunnelStatus.describe(TunnelStatus.TARGET_DOWN),
    /** null = this device may use the tunnel, else why not (French). Called with the Bluetooth address. */
    private val admit: (String) -> String? = { null },
    private val log: (String) -> Unit = {},
    private val watchStepMs: Long = 1000,
    private val now: () -> Long = System::currentTimeMillis,
) {
    data class Active(val peer: String, val name: String, val since: Long, val bytesUp: Long, val bytesDown: Long)
    class Outcome(val served: Boolean, val code: Int, val message: String?, val bytesUp: Long = 0, val bytesDown: Long = 0)

    private class Entry(val id: Int, val peer: String, val name: String, val since: Long) {
        @Volatile var relay: ByteRelay? = null
        val evicted = AtomicBoolean(false)
        @Volatile var lastSeen = since
    }

    private val lock = Any()
    private val entries = HashMap<Int, Entry>()
    private var nextId = 0
    private val totalUp = AtomicLong()
    private val totalDown = AtomicLong()
    @Volatile var lastError: String? = null; private set
    @Volatile var refused = 0; private set

    fun active(): List<Active> = synchronized(lock) {
        entries.values.filter { !it.evicted.get() }.sortedBy { it.since }
            .map { Active(it.peer, it.name, it.since, it.relay?.bytesAtoB?.get() ?: 0, it.relay?.bytesBtoA?.get() ?: 0) }
    }

    /** Bytes relayed since the start, finished links included: (phone -> TV, TV -> phone). */
    fun totals(): Pair<Long, Long> = (totalUp.get() + active().sumOf { it.bytesUp }) to (totalDown.get() + active().sumOf { it.bytesDown })

    private fun refuse(peer: String, code: Int, message: String, output: OutputStream, close: () -> Unit): Outcome {
        lastError = message; refused++
        log("$label: link from $peer refused: $message")
        if (handshake) runCatching { output.write(code); output.flush() }
        runCatching(close)
        return Outcome(false, code, message)
    }

    /** Takes a slot (null when full). Closes the stalest silent link if that makes room. */
    private fun take(peer: String, name: String): Entry? = synchronized(lock) {
        var live = entries.values.filter { !it.evicted.get() }
        if (live.size >= maxConnections) {
            val stale = live.minByOrNull { it.relay?.lastActivity ?: it.lastSeen }
            val t = stale?.let { it.relay?.lastActivity ?: it.lastSeen }
            if (stale != null && t != null && now() - t >= evictIdleMs && stale.evicted.compareAndSet(false, true)) {
                log("$label: link of ${stale.peer} silent for ${(now() - t) / 1000} s closed to make room")
                stale.relay?.close()
                live = entries.values.filter { !it.evicted.get() }
            }
        }
        if (live.size >= maxConnections) return null
        Entry(nextId++, peer, name, now()).also { entries[it.id] = it }
    }

    /**
     * Serves one Bluetooth link until it ends (blocking). [close] is always called. [onChange] is told whenever the list of
     * links changes. The result says whether data flowed, and why not otherwise.
     */
    fun serve(peer: String, name: String, input: InputStream, output: OutputStream, close: () -> Unit, onChange: () -> Unit = {}): Outcome {
        admit(peer)?.let { return refuse(peer, TunnelStatus.REFUSED, it, output, close) }
        val entry = take(peer, name)
            ?: return refuse(peer, TunnelStatus.BUSY, "toutes les liaisons Bluetooth de la TV sont occupées (max $maxConnections)", output, close)
        val sock = Socket()
        var port = -1
        var relay: ByteRelay? = null
        try {
            runCatching(onChange)
            // Bind first, register, then connect: the server can never see this connection before it is attributed.
            sock.bind(InetSocketAddress("127.0.0.1", 0))
            port = sock.localPort
            peers.register(port, "bt:$peer")
            try { sock.connect(InetSocketAddress("127.0.0.1", targetPort), connectTimeoutMs) }
            catch (e: IOException) { return refuse(peer, TunnelStatus.TARGET_DOWN, targetDownMessage, output, close) }
            sock.tcpNoDelay = true
            if (handshake) { output.write(TunnelStatus.OK); output.flush() }
            log("$label: link from $peer open")
            val r = ByteRelay(input, output, sock.getInputStream(), sock.getOutputStream(), close, { sock.close() }, bufferBytes, "bt-$label").start()
            relay = r; entry.relay = r
            if (entry.evicted.get()) r.close()
            val watch = Thread({
                try {
                    while (!r.isClosed) {
                        Thread.sleep(watchStepMs)
                        val silent = now() - r.lastActivity
                        if (silent > idleMs()) {
                            lastError = "liaison Bluetooth fermée après ${silent / 1000} s sans échange"
                            log("$label: link from $peer silent for ${silent / 1000} s, closed"); r.close(); break
                        }
                    }
                } catch (_: InterruptedException) {}
            }, "bt-$label-watch").apply { isDaemon = true; start() }
            r.join()
            watch.interrupt()
            if (r.bytesAtoB.get() + r.bytesBtoA.get() > 0) lastError = null
            log("$label: link from $peer closed (up ${r.bytesAtoB.get()} B, down ${r.bytesBtoA.get()} B)")
            return Outcome(true, TunnelStatus.OK, null, r.bytesAtoB.get(), r.bytesBtoA.get())
        } catch (e: IOException) {
            lastError = "liaison interrompue : ${e.javaClass.simpleName}"
            log("$label: link from $peer failed: ${e.javaClass.simpleName}")
            return Outcome(false, TunnelStatus.INTERNAL, lastError)
        } finally {
            runCatching(close); runCatching { sock.close() }
            relay?.let { totalUp.addAndGet(it.bytesAtoB.get()); totalDown.addAndGet(it.bytesBtoA.get()) }
            if (port >= 0) peers.unregister(port)
            synchronized(lock) { entries.remove(entry.id) }
            runCatching(onChange)
        }
    }
}

/**
 * An accepted HTTP connection that carries its device identity: for a connection coming through the Bluetooth tunnel, the server
 * is told the client's address is the device's virtual address ([PeerRegistry.virtualAddress]) instead of loopback. Everything else
 * is delegated to the real socket.
 */
class AttributedSocket(private val real: Socket, private val address: java.net.InetAddress) : Socket() {
    override fun getInetAddress(): java.net.InetAddress = address
    override fun getInputStream(): InputStream = real.getInputStream()
    override fun getOutputStream(): OutputStream = real.getOutputStream()
    override fun isClosed(): Boolean = real.isClosed
    override fun isConnected(): Boolean = real.isConnected
    override fun setSoTimeout(timeout: Int) = real.setSoTimeout(timeout)
    override fun getSoTimeout(): Int = real.soTimeout
    override fun close() = real.close()
    override fun shutdownInput() = real.shutdownInput()
    override fun shutdownOutput() = real.shutdownOutput()

    companion object {
        /** [real], or an [AttributedSocket] if it came through the tunnel of [peers]. */
        fun of(real: Socket, peers: PeerRegistry?): Socket {
            val remote = real.remoteSocketAddress as? InetSocketAddress ?: return real
            val v = peers?.virtualAddress(remote) ?: return real
            return AttributedSocket(real, v)
        }
    }
}
