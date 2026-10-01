package castbridge.core.remote.vendor

import java.io.IOException
import java.io.InputStream
import java.net.ConnectException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.SecureRandom
import java.util.Base64

enum class VendorState { ABSENT, CONNECTING, READY, ERROR }

/** What the relay needs from the bridge (a fake in tests). */
interface VendorLink {
    val state: VendorState
    fun sendKey(androidCode: Int): Boolean
}

/**
 * WebSocket client to the white-label TV remote service (docs/REMOTE-VENDOR-CVTE.md), written by hand, no dependency.
 * It talks ONLY to the loopback address of the TV it runs on (the constructor takes no host): CastBridge TV relays what an
 * authenticated phone asked, it never contacts another machine. [ABSENT] = nothing listens (the TV has no such service),
 * [CONNECTING] = trying / waiting to retry, [READY] = handshake done, [ERROR] = something answered but not as a WebSocket.
 * Reconnects with a growing wait ([backoffMs], last value repeated) until [stop].
 */
class VendorBridge(
    private val port: Int = DEFAULT_PORT,
    private val backoffMs: List<Long> = listOf(1_000, 2_000, 4_000, 8_000, 16_000, 30_000),
    private val connectTimeoutMs: Int = 800,
    private val readTimeoutMs: Int = 10_000,
    private val onState: (VendorState) -> Unit = {},
) : VendorLink {
    @Volatile override var state: VendorState = VendorState.CONNECTING; private set
    @Volatile var info: VendorInfo? = null; private set
    @Volatile private var running = false
    @Volatile private var socket: Socket? = null
    private var worker: Thread? = null
    private val lock = Object()
    private val rnd = SecureRandom()

    @Synchronized fun start() {
        if (running) return
        running = true
        worker = Thread({ loop() }, "cb-vendor-bridge").apply { isDaemon = true; start() }
    }

    @Synchronized fun stop() {
        running = false
        runCatching { socket?.close() }
        synchronized(lock) { lock.notifyAll() }
        worker?.interrupt(); worker = null
    }

    override fun sendKey(androidCode: Int): Boolean {
        if (state != VendorState.READY) return false
        return send(WebSocketFrames.BINARY, VendorProto.key(androidCode))
    }

    private fun send(op: Int, payload: ByteArray): Boolean {
        val s = socket ?: return false
        val mask = ByteArray(4).also { rnd.nextBytes(it) }
        return try {
            synchronized(s) { s.getOutputStream().apply { write(WebSocketFrames.encode(op, payload, mask)); flush() } }
            true
        } catch (_: IOException) { runCatching { s.close() }; false }
    }

    private fun set(s: VendorState) { if (state != s) { state = s; runCatching { onState(s) } } }

    private fun loop() {
        var failures = 0
        while (running) {
            set(VendorState.CONNECTING)
            var reachedReady = false
            try {
                val s = Socket()
                socket = s
                s.tcpNoDelay = true
                s.connect(InetSocketAddress(LOOPBACK, port), connectTimeoutMs)
                s.soTimeout = connectTimeoutMs.coerceAtLeast(2_000)
                handshake(s)
                s.soTimeout = readTimeoutMs
                info = null
                set(VendorState.READY); reachedReady = true; failures = 0
                readLoop(s)
                set(VendorState.CONNECTING)
            } catch (_: ConnectException) {
                set(VendorState.ABSENT)
            } catch (_: IOException) {
                if (running && !reachedReady) set(VendorState.ERROR)
            } finally {
                runCatching { socket?.close() }; socket = null; info = null
            }
            if (!running) break
            val wait = backoffMs[failures.coerceAtMost(backoffMs.size - 1)]
            if (!reachedReady) failures++
            synchronized(lock) { try { lock.wait(wait) } catch (_: InterruptedException) { return } }
        }
    }

    private fun handshake(s: Socket) {
        val key = Base64.getEncoder().encodeToString(ByteArray(16).also { rnd.nextBytes(it) })
        val req = "GET / HTTP/1.1\r\nHost: $LOOPBACK_TEXT:$port\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
            "Sec-WebSocket-Key: $key\r\nSec-WebSocket-Version: 13\r\n\r\n"
        s.getOutputStream().apply { write(req.toByteArray(Charsets.US_ASCII)); flush() }
        val head = readHead(s.getInputStream())
        // 101 is what matters; the Accept value is not enforced (this is a loopback talk with the TV's own service).
        if (!head.lineSequence().first().contains(" 101")) throw IOException("not a WebSocket upgrade")
    }

    private fun readHead(i: InputStream): String {
        val sb = StringBuilder()
        while (sb.length < 8192) {
            val b = i.read(); if (b < 0) throw IOException("closed")
            sb.append(b.toChar())
            if (sb.endsWith("\r\n\r\n")) return sb.toString()
        }
        throw IOException("header too long")
    }

    private fun readLoop(s: Socket) {
        val reader = WebSocketFrames.Reader(s.getInputStream())
        var lastRx = System.currentTimeMillis()
        while (running) {
            val m = try { reader.next() } catch (_: SocketTimeoutException) {
                if (System.currentTimeMillis() - lastRx > readTimeoutMs * 3L) return
                if (!send(WebSocketFrames.PING, ByteArray(0))) return
                continue
            }
            lastRx = System.currentTimeMillis()
            when (m.opcode) {
                WebSocketFrames.TEXT -> VendorInfo.parse(m.text)?.let { info = it }
                WebSocketFrames.PING -> send(WebSocketFrames.PONG, m.payload)
                WebSocketFrames.CLOSE -> { send(WebSocketFrames.CLOSE, m.payload.copyOf(minOf(2, m.payload.size))); return }
            }
        }
    }

    companion object {
        const val DEFAULT_PORT = 8125
        const val LOOPBACK_TEXT = "127.0.0.1"
        val LOOPBACK: InetAddress = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))
    }
}
