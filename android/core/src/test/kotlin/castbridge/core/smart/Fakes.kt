package castbridge.core.smart

import castbridge.core.remote.smart.*
import com.sun.net.httpserver.HttpServer
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** A request seen by [FakeHttp]. */
class Req(val method: String, val path: String, val headers: Map<String, String>, val body: String)

/** Local HTTP server with a scripted answer (the JDK's own server: no dependency). */
class FakeHttp(private val answer: (Req) -> Pair<Int, String> = { 200 to "" }) : Closeable {
    val requests = CopyOnWriteArrayList<Req>()
    private val server = HttpServer.create(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0).also { s ->
        s.createContext("/") { ex ->
            val body = ex.requestBody.readBytes().toString(Charsets.UTF_8)
            val r = Req(ex.requestMethod, ex.requestURI.toString(), ex.requestHeaders.entries.associate { it.key.lowercase() to it.value.joinToString(",") }, body)
            requests += r
            val (st, out) = answer(r)
            val b = out.toByteArray(); ex.sendResponseHeaders(st, if (b.isEmpty()) -1 else b.size.toLong())
            if (b.isNotEmpty()) ex.responseBody.use { it.write(b) } else ex.close()
        }
        s.start()
    }
    val port: Int get() = server.address.port
    val url: String get() = "http://127.0.0.1:$port"
    override fun close() = server.stop(0)
}

/** One accepted WebSocket connection of [FakeWs]. */
class WsConn(private val sock: Socket, val path: String) : Closeable {
    private val i = sock.getInputStream(); private val o = sock.getOutputStream()
    val received = LinkedBlockingQueue<Any>()      // String (text) or ByteArray (binary)
    @Volatile var closedByPeer = false

    @Synchronized private fun frame(op: Int, p: ByteArray) {
        val b = ByteArrayOutputStream(); b.write(0x80 or op)
        if (p.size < 126) b.write(p.size) else { b.write(126); b.write(p.size ushr 8); b.write(p.size and 0xFF) }
        b.write(p); o.write(b.toByteArray()); o.flush()
    }
    fun sendText(s: String) = frame(1, s.toByteArray())
    fun sendPing() = frame(9, ByteArray(0))
    fun next(ms: Long = 2000): Any? = received.poll(ms, TimeUnit.MILLISECONDS)
    override fun close() { runCatching { sock.close() } }

    internal fun pump() {
        try {
            while (true) {
                val b0 = i.read(); if (b0 < 0) break
                val b1 = i.read(); var len = b1 and 0x7F
                if (len == 126) len = (i.read() shl 8) or i.read()
                val mask = ByteArray(4).also { i.readNBytes(it, 0, 4) }
                val p = ByteArray(len).also { i.readNBytes(it, 0, len) }
                for (k in p.indices) p[k] = (p[k].toInt() xor mask[k % 4].toInt()).toByte()
                when (b0 and 0x0F) { 1 -> received += String(p); 2 -> received += p; 8 -> break }
            }
        } catch (e: Exception) {}
        closedByPeer = true
    }
}

/** Local WebSocket server (RFC 6455 server side, just enough for the tests). [onConnect] runs for each client, e.g. to push a first frame. */
class FakeWs(private val onConnect: (WsConn) -> Unit = {}) : Closeable {
    private val ss = ServerSocket(0, 5, InetAddress.getByName("127.0.0.1"))
    val port get() = ss.localPort
    val conns = CopyOnWriteArrayList<WsConn>()
    private val t = Thread {
        while (!ss.isClosed) {
            val s = try { ss.accept() } catch (e: Exception) { break }
            Thread { serve(s) }.apply { isDaemon = true; start() }
        }
    }.apply { isDaemon = true; start() }

    private fun serve(s: Socket) {
        try {
            val i = s.getInputStream()
            val lines = ArrayList<String>()
            while (true) { val l = castbridge.core.remote.Http1.line(i); if (l.isEmpty()) break; lines += l }
            val path = lines[0].split(' ')[1]
            val key = lines.first { it.startsWith("Sec-WebSocket-Key:", true) }.substringAfter(':').trim()
            val acc = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray()))
            s.getOutputStream().write("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: $acc\r\n\r\n".toByteArray())
            s.getOutputStream().flush()
            val c = WsConn(s, path); conns += c
            Thread { onConnect(c) }.apply { isDaemon = true; start() }
            c.pump()
        } catch (e: Exception) { runCatching { s.close() } }
    }

    fun target(id: String = "tv1") = TvTarget(id, "127.0.0.1", connectTimeoutMs = 800, readTimeoutMs = 1500)
    override fun close() { runCatching { ss.close() }; conns.forEach { it.close() } }
}

fun testTarget(id: String = "tv1") = TvTarget(id, "127.0.0.1", connectTimeoutMs = 800, readTimeoutMs = 1500)

/** Scripted strategy for orchestrator tests. */
class FakeStrategy(
    override val id: String, override val status: StrategyStatus = StrategyStatus.STABLE,
    keys: Set<castbridge.core.remote.RemoteKey> = castbridge.core.remote.RemoteKey.values().toSet(),
    override val verifiesDelivery: Boolean = false,
    var reachable: Boolean = true, var connectError: IOExceptionKind? = null, var sendFails: Boolean = false,
    private val applicableTo: (TvFingerprint) -> Boolean = { true },
) : RemoteStrategy {
    enum class IOExceptionKind { FAIL, PAIRING }
    override val label = id
    override val capabilities = Capabilities(keys)
    override var state = StrategyState.IDLE
    val sent = CopyOnWriteArrayList<castbridge.core.remote.RemoteKey>()
    var connects = 0; var closes = 0
    override fun applicable(fp: TvFingerprint) = applicableTo(fp)
    override fun probe() = ProbeResult(reachable)
    override fun connect() {
        connects++
        when (connectError) {
            IOExceptionKind.FAIL -> throw java.io.IOException("refus simulé")
            IOExceptionKind.PAIRING -> throw StrategyException("code requis", needsPairing = true)
            null -> state = StrategyState(StrategyState.Kind.READY)
        }
    }
    override fun send(key: castbridge.core.remote.RemoteKey) { if (sendFails) throw java.io.IOException("liaison coupée"); sent += key }
    override fun close() { closes++; state = StrategyState.CLOSED }
}
