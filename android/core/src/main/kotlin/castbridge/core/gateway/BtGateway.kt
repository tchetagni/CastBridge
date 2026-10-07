package castbridge.core.gateway

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Internet gateway over one byte link (Bluetooth RFCOMM in the apps, pipes in tests): the phone shares its
 * connection with the TV.
 *
 * TV side ([Entry]): a SOCKS5 proxy on 127.0.0.1 that the TV app's own downloads use; every proxied connection
 * becomes a stream on the link. Phone side ([Exit]): opens the real TCP connections and relays.
 *
 * Frame = type u8 | stream u16 | length u32 | payload. Each stream has a flow-control window (the receiver
 * acknowledges what it wrote to its socket), so one slow connection never stalls the others on the shared link.
 * The link starts with HELLO("CBG1" + PIN): only a phone that knows the TV's PIN can become its gateway.
 */
object Gw {
    const val MAGIC = "CBG1"
    const val HELLO = 1; const val HELLO_OK = 2; const val HELLO_ERR = 3
    const val OPEN = 4; const val OPEN_OK = 5; const val OPEN_ERR = 6
    const val DATA = 7; const val CLOSE = 8; const val ACK = 9; const val PING = 10
    /** One direction finished (TCP half-close): the other one keeps flowing until it ends too. CLOSE = abort. */
    const val EOF = 11
    /** Network diagnostics run by the phone (ICMP cannot cross a TCP tunnel): DIAG "ping host"/"trace host" ->
     *  DIAG_OUT lines -> DIAG_END. */
    const val DIAG = 12; const val DIAG_OUT = 13; const val DIAG_END = 14

    private val HOST = Regex("^[A-Za-z0-9](?:[A-Za-z0-9.:-]{0,252})$")
    /** Only a host name or an IP literal: the phone runs a fixed command, never text from the TV. */
    fun validHost(h: String) = HOST.matches(h) && !h.contains("..")
    const val WINDOW = 256 * 1024L          // bytes in flight per stream
    const val CHUNK = 16 * 1024             // DATA payload size (RFCOMM likes big writes, the window keeps RAM bounded)
    const val MAX_STREAMS = 32
    /** Bluetooth RFCOMM service of the gateway: its own UUID (…0007, the table of services in [castbridge.core.tv.BtProtocol]). */
    const val SERVICE_UUID = castbridge.core.tv.BtProtocol.GATEWAY_SERVICE_UUID
    /**
     * The UUID the gateway had before R-28 (…0002), which is the SSH tunnel's. A phone still asks for it when the TV is old (`R-28-OLD-TV`), and a new TV still serves the gateway
     * there while its SSH over Bluetooth is off, for two versions (`R-28-LEGACY-TV`); see [GatewayService].
     */
    const val LEGACY_SERVICE_UUID = castbridge.core.tv.BtProtocol.SSH_SERVICE_UUID

    // SOCKS5 reply codes, reused in OPEN_ERR
    const val SOCKS_FAIL = 1; const val SOCKS_NOT_ALLOWED = 2; const val SOCKS_NET = 3; const val SOCKS_HOST = 4
    const val SOCKS_REFUSED = 5; const val SOCKS_CMD = 7; const val SOCKS_ATYP = 8
}

class Frame(val type: Int, val stream: Int, val payload: ByteArray = EMPTY) {
    companion object { val EMPTY = ByteArray(0) }
}

/** Frame reader/writer over the link; writes are serialised (several streams share one output). */
class Mux(input: InputStream, output: OutputStream) {
    private val din = DataInputStream(input.buffered(64 * 1024))
    private val dout = DataOutputStream(output.buffered(64 * 1024))
    val sent = AtomicLong(); val received = AtomicLong()

    fun write(f: Frame) = synchronized(dout) {
        dout.writeByte(f.type); dout.writeShort(f.stream); dout.writeInt(f.payload.size); dout.write(f.payload)
        dout.flush()
        sent.addAndGet(7L + f.payload.size)
    }

    fun read(): Frame {
        val t = din.readUnsignedByte(); val s = din.readUnsignedShort(); val n = din.readInt()
        if (n < 0 || n > 1 shl 20) throw IOException("bad frame length $n")
        val p = if (n == 0) Frame.EMPTY else ByteArray(n).also { din.readFully(it) }
        received.addAndGet(7L + n)
        return Frame(t, s, p)
    }
}

/** One relayed TCP connection, same code on both ends: socket <-> DATA frames with a per-stream window. */
internal class Relay(private val mux: Mux, val id: Int, private val sock: Socket, private val onEnd: (Int) -> Unit) {
    private val credit = AtomicLong(Gw.WINDOW)
    private val lock = Object()
    private val inbox = LinkedBlockingQueue<ByteArray>()
    @Volatile private var closed = false
    private val ended = AtomicInteger(0)
    @Volatile private var upDone = false
    @Volatile private var downDone = false

    private fun maybeEnd() { if (upDone && downDone) finish(sendClose = false) }

    fun start() {
        Thread({ upstream() }, "gw-up-$id").apply { isDaemon = true; start() }
        Thread({ downstream() }, "gw-down-$id").apply { isDaemon = true; start() }
    }

    /** socket -> link */
    private fun upstream() {
        val buf = ByteArray(Gw.CHUNK)
        try {
            val inp = sock.getInputStream()
            while (!closed) {
                synchronized(lock) { while (credit.get() <= 0 && !closed) lock.wait(1000) }
                if (closed) break
                val n = inp.read(buf, 0, minOf(buf.size.toLong(), credit.get()).toInt())
                if (n < 0) { mux.write(Frame(Gw.EOF, id)); upDone = true; maybeEnd(); return }
                credit.addAndGet(-n.toLong())
                mux.write(Frame(Gw.DATA, id, buf.copyOf(n)))
            }
        } catch (_: Exception) { finish(sendClose = true) }
    }

    /** link -> socket, on its own thread so the link reader never blocks on a slow socket */
    private fun downstream() {
        try {
            val out = sock.getOutputStream()
            while (true) {
                val b = inbox.take()
                if (b.isEmpty()) break                     // end marker
                out.write(b); out.flush()
                mux.write(Frame(Gw.ACK, id, int(b.size)))
            }
            runCatching { sock.shutdownOutput() }
            downDone = true; maybeEnd()
        } catch (_: Exception) { finish(sendClose = true) }
    }

    fun onData(p: ByteArray) { if (!closed) inbox.put(p) }
    fun onAck(p: ByteArray) { credit.addAndGet(readInt(p).toLong()); synchronized(lock) { lock.notifyAll() } }
    fun onRemoteEof() { inbox.put(Frame.EMPTY) }
    fun onRemoteClose() { inbox.put(Frame.EMPTY); finish(sendClose = false) }

    fun finish(sendClose: Boolean) {
        if (ended.getAndIncrement() > 0) return
        closed = true
        synchronized(lock) { lock.notifyAll() }
        if (sendClose) runCatching { mux.write(Frame(Gw.CLOSE, id)) }
        // let the downstream writer drain what already arrived before the socket goes away
        Thread({ Thread.sleep(200); runCatching { sock.close() } }, "gw-close-$id").apply { isDaemon = true; start() }
        onEnd(id)
    }
}

internal fun int(v: Int) = byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte())
internal fun readInt(b: ByteArray) = ((b[0].toInt() and 255) shl 24) or ((b[1].toInt() and 255) shl 16) or ((b[2].toInt() and 255) shl 8) or (b[3].toInt() and 255)

/** Destination of a stream, as a SOCKS5 address. */
data class GwTarget(val host: String, val port: Int) {
    fun encode(): ByteArray {
        val h = host.toByteArray(Charsets.UTF_8)
        return byteArrayOf(h.size.toByte()) + h + byteArrayOf((port ushr 8).toByte(), port.toByte())
    }
    companion object {
        fun decode(b: ByteArray): GwTarget {
            val n = b[0].toInt() and 255
            val host = String(b, 1, n, Charsets.UTF_8)
            val port = ((b[1 + n].toInt() and 255) shl 8) or (b[2 + n].toInt() and 255)
            return GwTarget(host, port)
        }
    }
}

// ------------------------------------------------------------------------------------------------ phone side

/**
 * Phone side: authenticates to the TV, then opens the TCP connections the TV asks for. [connect] is injectable
 * (tests); it may refuse targets (e.g. the phone's own LAN) by throwing.
 */
class Exit(
    private val mux: Mux,
    private val pin: String,
    private val connect: (GwTarget) -> Socket = { t -> Socket().apply { connect(InetSocketAddress(t.host, t.port), 15_000); tcpNoDelay = true } },
    private val log: (String) -> Unit = {},
    /** Runs "ping"/"trace" towards a validated host, calling the line callback as output arrives. */
    private val diag: (kind: String, host: String, line: (String) -> Unit) -> Unit = { _, _, l -> l("diagnostic indisponible") },
) {
    private val relays = ConcurrentHashMap<Int, Relay>()
    val openStreams get() = relays.size

    /** Runs until the link breaks. Throws [IOException] if the TV refuses the PIN. */
    fun run() {
        mux.write(Frame(Gw.HELLO, 0, (Gw.MAGIC + pin).toByteArray(Charsets.US_ASCII)))
        val first = mux.read()
        if (first.type != Gw.HELLO_OK) throw IOException(if (first.type == Gw.HELLO_ERR) String(first.payload) else "réponse inattendue")
        log("passerelle active")
        try {
            while (true) {
                val f = mux.read()
                when (f.type) {
                    Gw.OPEN -> open(f.stream, GwTarget.decode(f.payload))
                    Gw.DATA -> relays[f.stream]?.onData(f.payload)
                    Gw.ACK -> relays[f.stream]?.onAck(f.payload)
                    Gw.CLOSE -> relays.remove(f.stream)?.onRemoteClose()
                    Gw.EOF -> relays[f.stream]?.onRemoteEof()
                    Gw.PING -> mux.write(Frame(Gw.PING, 0))
                    Gw.DIAG -> runDiag(f.stream, String(f.payload, Charsets.UTF_8))
                }
            }
        } finally { relays.values.toList().forEach { it.finish(sendClose = false) } }
    }

    private fun runDiag(id: Int, cmd: String) = Thread({
        val kind = cmd.substringBefore(' '); val host = cmd.substringAfter(' ', "")
        try {
            if (kind !in setOf("ping", "trace") || !Gw.validHost(host)) mux.write(Frame(Gw.DIAG_OUT, id, "requête refusée".toByteArray()))
            else diag(kind, host) { line -> mux.write(Frame(Gw.DIAG_OUT, id, line.toByteArray(Charsets.UTF_8))) }
        } catch (e: Exception) { runCatching { mux.write(Frame(Gw.DIAG_OUT, id, "erreur : ${e.message}".toByteArray())) } }
        runCatching { mux.write(Frame(Gw.DIAG_END, id)) }
    }, "gw-diag").apply { isDaemon = true; start() }

    private fun open(id: Int, t: GwTarget) {
        Thread({
            val s = try { connect(t) } catch (e: Exception) {
                val code = when (e) { is java.net.UnknownHostException -> Gw.SOCKS_HOST; is java.net.ConnectException -> Gw.SOCKS_REFUSED
                    is SecurityException -> Gw.SOCKS_NOT_ALLOWED; else -> Gw.SOCKS_NET }
                runCatching { mux.write(Frame(Gw.OPEN_ERR, id, byteArrayOf(code.toByte()))) }
                return@Thread
            }
            val r = Relay(mux, id, s) { relays.remove(it) }
            relays[id] = r
            mux.write(Frame(Gw.OPEN_OK, id))
            r.start()
        }, "gw-open-$id").apply { isDaemon = true; start() }
    }
}

// ------------------------------------------------------------------------------------------------ TV side

/**
 * TV side: accepts one phone link at a time ([attach]) and serves SOCKS5 (CONNECT, no auth) on 127.0.0.1:[port]
 * for the TV app's own traffic. Without a phone attached, SOCKS requests fail fast (network unreachable).
 */
class Entry(
    private val checkPin: (String) -> Boolean, val port: Int = 1080,
    /** relay-R1: the session token the local SOCKS requires ([SocksAuth], RFC 1929); null (or a supplier answering null) = no authentication, as before. */
    private val socksToken: (() -> String?)? = null,
    private val log: (String) -> Unit = {},
) {
    @Volatile private var mux: Mux? = null
    private val relays = ConcurrentHashMap<Int, Relay>()
    private val pending = ConcurrentHashMap<Int, java.util.concurrent.CompletableFuture<Int>>()
    private val diags = ConcurrentHashMap<Int, Pair<(String) -> Unit, java.util.concurrent.CountDownLatch>>()
    private val nextId = AtomicInteger(1)
    /** relay-R1: PING frames waiting for the phone's echo (one measurement at a time, see [ping]). */
    private val pings = java.util.concurrent.LinkedBlockingQueue<java.util.concurrent.CompletableFuture<Unit>>()
    private var server: ServerSocket? = null
    @Volatile var peerName: String? = null; private set
    /** relay-R1: how many phone links this Entry accepted (changes when a phone attaches, even when it replaces another): the identity of « the current pipe ». */
    @Volatile var attaches = 0; private set
    val connected get() = mux != null
    val openStreams get() = relays.size
    fun stats(): Pair<Long, Long> = mux?.let { it.received.get() to it.sent.get() } ?: (0L to 0L)

    fun startSocks(): Int {
        val ss = ServerSocket(port, 50, InetAddress.getByName("127.0.0.1"))
        server = ss
        Thread({
            while (!ss.isClosed) {
                val c = try { ss.accept() } catch (e: IOException) { break }
                Thread({ runCatching { socks(c) }.onFailure { runCatching { c.close() } } }, "gw-socks").apply { isDaemon = true; start() }
            }
        }, "gw-socks-accept").apply { isDaemon = true; start() }
        return ss.localPort
    }

    fun stop() { runCatching { server?.close() } }

    /** Serves one phone link until it breaks. Returns normally when the link ends. */
    fun attach(link: Mux, peer: String) {
        val hello = link.read()
        val txt = String(hello.payload, Charsets.US_ASCII)
        if (hello.type != Gw.HELLO || !txt.startsWith(Gw.MAGIC) || !checkPin(txt.removePrefix(Gw.MAGIC))) {
            runCatching { link.write(Frame(Gw.HELLO_ERR, 0, "PIN incorrect".toByteArray())) }
            log("passerelle refusée ($peer)"); return
        }
        link.write(Frame(Gw.HELLO_OK, 0))
        mux?.let { log("nouvelle passerelle, l'ancienne est remplacée") }
        mux = link; peerName = peer; attaches++
        log("Internet via $peer")
        try {
            while (true) {
                val f = link.read()
                when (f.type) {
                    Gw.OPEN_OK -> pending.remove(f.stream)?.complete(0)
                    Gw.OPEN_ERR -> pending.remove(f.stream)?.complete(f.payload.firstOrNull()?.toInt() ?: Gw.SOCKS_FAIL)
                    Gw.DATA -> relays[f.stream]?.onData(f.payload)
                    Gw.ACK -> relays[f.stream]?.onAck(f.payload)
                    Gw.CLOSE -> relays.remove(f.stream)?.onRemoteClose()
                    Gw.EOF -> relays[f.stream]?.onRemoteEof()
                    Gw.DIAG_OUT -> diags[f.stream]?.first?.invoke(String(f.payload, Charsets.UTF_8))
                    Gw.DIAG_END -> diags.remove(f.stream)?.second?.countDown()
                    Gw.PING -> pings.poll()?.complete(Unit)
                }
            }
        } catch (_: IOException) {
        } finally {
            if (mux === link) { mux = null; peerName = null }
            relays.values.toList().forEach { it.finish(sendClose = false) }
            pending.values.forEach { it.complete(Gw.SOCKS_NET) }; pending.clear()
            diags.values.forEach { (l, d) -> l("lien Bluetooth coupé"); d.countDown() }; diags.clear()
            pings.forEach { it.cancel(false) }; pings.clear()
            log("passerelle déconnectée")
        }
    }

    /** Asks the phone to run ping/traceroute; [onLine] gets each output line. Blocks until done (max [timeoutS]). */
    fun diag(kind: String, host: String, timeoutS: Long = 90, onLine: (String) -> Unit): Boolean {
        val m = mux ?: return false
        require((kind == "ping" || kind == "trace") && Gw.validHost(host))
        val id = nextId.getAndUpdate { if (it >= 65000) 1 else it + 1 }
        val done = java.util.concurrent.CountDownLatch(1)
        diags[id] = onLine to done
        m.write(Frame(Gw.DIAG, id, "$kind $host".toByteArray(Charsets.UTF_8)))
        val ok = done.await(timeoutS, java.util.concurrent.TimeUnit.SECONDS)
        diags.remove(id)
        return ok
    }

    /**
     * relay-R1: round trip of one PING frame over the Bluetooth link itself (ms), or null without a phone or without an answer within [timeoutMs]. It measures the
     * link, not the phone's Internet: the phone echoes the frame at once. One measurement at a time (the echo carries no id).
     */
    @Synchronized fun ping(timeoutMs: Long = 5_000): Long? {
        val m = mux ?: return null
        val f = java.util.concurrent.CompletableFuture<Unit>()
        pings.add(f)
        val t0 = System.nanoTime()
        try { m.write(Frame(Gw.PING, 0)) } catch (e: IOException) { pings.remove(f); return null }
        return try { f.get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS); (System.nanoTime() - t0) / 1_000_000 }
        catch (e: Exception) { pings.remove(f); null }
    }

    /** "TCP ping": time to open a connection to host:port through the phone (what the TV app really experiences). */
    fun tcpPing(host: String, port: Int = 443): Long? {
        val t0 = System.nanoTime()
        return runCatching {
            Socket(java.net.Proxy(java.net.Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", server!!.localPort))).use {
                it.connect(InetSocketAddress.createUnresolved(host, port), 15_000)
            }
            (System.nanoTime() - t0) / 1_000_000
        }.getOrNull()
    }

    private fun socks(c: Socket) {
        c.soTimeout = 30_000
        val i = DataInputStream(c.getInputStream()); val o = c.getOutputStream()
        if (i.readUnsignedByte() != 5) { c.close(); return }
        if (!SocksAuth.negotiate(i, o, socksToken?.invoke())) { c.close(); return }   // session token (RFC 1929) when configured, else none (loopback only)
        if (i.readUnsignedByte() != 5) { c.close(); return }
        val cmd = i.readUnsignedByte(); i.readUnsignedByte()
        val host = when (i.readUnsignedByte()) {
            1 -> ByteArray(4).also { i.readFully(it) }.joinToString(".") { (it.toInt() and 255).toString() }
            3 -> String(ByteArray(i.readUnsignedByte()).also { i.readFully(it) }, Charsets.UTF_8)
            4 -> InetAddress.getByAddress(ByteArray(16).also { i.readFully(it) }).hostAddress
            else -> { reply(o, Gw.SOCKS_ATYP); c.close(); return }
        }
        val port = i.readUnsignedShort()
        if (cmd != 1) { reply(o, Gw.SOCKS_CMD); c.close(); return }
        val m = mux
        if (m == null || relays.size >= Gw.MAX_STREAMS) { reply(o, Gw.SOCKS_NET); c.close(); return }
        val id = nextId.getAndUpdate { if (it >= 65000) 1 else it + 1 }
        val fut = java.util.concurrent.CompletableFuture<Int>()
        // the stream is registered BEFORE the phone is asked to open: a server that speaks first (an SSH banner) has its bytes sent right behind OPEN_OK, and a frame for a stream
        // that is not registered yet is dropped (relay-R1: found with a phone that sends OPEN_OK, DATA and EOF in one burst). Not started: DATA/EOF only queue until then.
        val r = Relay(m, id, c) { relays.remove(it) }
        relays[id] = r
        pending[id] = fut
        try { m.write(Frame(Gw.OPEN, id, GwTarget(host, port).encode())) } catch (e: IOException) { relays.remove(id); pending.remove(id); throw e }
        val code = try { fut.get(30, java.util.concurrent.TimeUnit.SECONDS) } catch (e: Exception) { pending.remove(id); Gw.SOCKS_NET }
        reply(o, code)
        if (code != 0) { relays.remove(id); c.close(); return }
        c.soTimeout = 0
        r.start()
    }

    private fun reply(o: OutputStream, code: Int) {
        o.write(byteArrayOf(5, code.toByte(), 0, 1, 0, 0, 0, 0, 0, 0)); o.flush()
    }
}
