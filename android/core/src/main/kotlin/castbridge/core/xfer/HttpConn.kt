package castbridge.core.xfer

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.StandardSocketOptions
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import java.util.concurrent.ConcurrentHashMap

/**
 * One persistent HTTP/1.1 connection (no handshake per block, big socket buffers, zero-copy body from a file). Not thread-safe: one worker owns it.
 * A watchdog closes a connection that has made no progress for [stallMs] (a hung TV must never block a worker forever).
 */
class HttpConn(
    private val connect: () -> SocketChannel,
    private val stallMs: Long = DEFAULT_STALL_MS,
    private val bufferBytes: Int = 1 shl 20,
) : AutoCloseable {
    class Reply(val status: Int, val headers: Map<String, String>, val body: String) { val keepAlive get() = headers["connection"]?.equals("close", true) != true }

    private var ch: SocketChannel? = null
    private var input: java.io.BufferedInputStream? = null
    @Volatile private var lastProgress = System.nanoTime()
    @Volatile private var busy = false
    /** True once the server asked to close or an error happened: the next request opens a new connection. */
    private var dead = false

    private fun open(): SocketChannel {
        ch?.let { if (!dead && it.isOpen) return it }
        close()
        val c = connect()
        runCatching { c.setOption(StandardSocketOptions.TCP_NODELAY, true); c.setOption(StandardSocketOptions.SO_SNDBUF, bufferBytes); c.setOption(StandardSocketOptions.SO_RCVBUF, 64 * 1024); c.setOption(StandardSocketOptions.SO_KEEPALIVE, true) }
        c.socket().soTimeout = stallMs.toInt()
        ch = c; input = java.io.BufferedInputStream(c.socket().getInputStream(), 16 * 1024); dead = false
        Watchdog.watch(this)
        return c
    }

    /** Writes the body through [body] (given the channel); returns the reply. Any failure closes the connection and throws [IOException]. */
    fun request(method: String, path: String, host: String, headers: List<String>, bodyLen: Long, body: (SocketChannel) -> Unit = {}): Reply {
        val c = open()
        busy = true; lastProgress = System.nanoTime()
        try {
            val head = StringBuilder("$method $path HTTP/1.1\r\nHost: $host\r\nConnection: keep-alive\r\nContent-Length: $bodyLen\r\n")
            headers.forEach { head.append(it).append("\r\n") }
            head.append("\r\n")
            try {
                val bb = ByteBuffer.wrap(head.toString().toByteArray(Charsets.ISO_8859_1))
                while (bb.hasRemaining()) c.write(bb)
                body(c)
            } catch (e: IOException) {
                // R-17: the TV may have answered (401/403/404/413/507...) and closed before the end of the body: its status is the real reason.
                if (WriteFailureClassifier.isPeerClose(e)) salvageReply()?.let { dead = true; close(); return it }
                throw e
            }
            lastProgress = System.nanoTime()
            return readReply()
        } catch (e: IOException) { dead = true; close(); throw e }
        catch (e: RuntimeException) { dead = true; close(); throw IOException(e.message, e) }
        finally { busy = false }
    }

    /** Called by body writers after each piece they push, so the watchdog sees progress. */
    fun progress(bytes: Long = 0) {
        lastProgress = System.nanoTime()
        // R-17: every ~512 Kio, look (without blocking) whether the TV has already answered; if so stop writing, the answer is the real reason
        sinceProbe += bytes
        if (sinceProbe >= PROBE_EVERY) { sinceProbe = 0; if (replyWaiting()) throw EarlyReply() }
    }
    private var sinceProbe = 0L
    private fun replyWaiting(): Boolean = try { (input?.available() ?: 0) > 0 } catch (e: Exception) { false }

    /** Bounded (1 s, 4 KiB) read of what the TV said before it closed; null = nothing readable. The connection is never reused after it. */
    private fun salvageReply(): Reply? {
        val sock = ch?.socket() ?: return null
        return try {
            val r = readReply(WriteFailureClassifier.SALVAGE_MAX_BYTES, System.nanoTime() + WriteFailureClassifier.SALVAGE_MAX_MS * 1_000_000, sock)
            Reply(r.status, r.headers + ("connection" to "close"), r.body)
        } catch (e: IOException) { null }
    }

    private fun readReply(bodyCap: Int = 1 shl 20, deadlineNs: Long = 0, sock: java.net.Socket? = null): Reply {
        val inp = input ?: throw IOException("closed")
        fun tick() {
            if (deadlineNs == 0L || sock == null) return
            val rem = (deadlineNs - System.nanoTime()) / 1_000_000
            if (rem <= 0) throw IOException("no reply")
            sock.soTimeout = rem.toInt()
        }
        fun line(): String {
            val sb = StringBuilder()
            while (true) {
                tick(); val b = inp.read(); if (b < 0) throw IOException("connection closed"); if (b == '\n'.code) break; if (b != '\r'.code) sb.append(b.toChar()); if (sb.length > 16_384) throw IOException("header too long") }
            return sb.toString()
        }
        val status = line().split(' ').getOrNull(1)?.toIntOrNull() ?: throw IOException("bad reply")
        val h = HashMap<String, String>()
        while (true) { val l = line(); if (l.isEmpty()) break; val i = l.indexOf(':'); if (i > 0) h[l.substring(0, i).trim().lowercase()] = l.substring(i + 1).trim() }
        val out = ByteArrayOutputStream()
        val len = h["content-length"]?.toLongOrNull()
        if (len != null) {
            if (len > 1 shl 20 && deadlineNs == 0L) throw IOException("reply too large")
            val buf = ByteArray(minOf(len, bodyCap.toLong()).toInt()); var n = 0
            while (n < buf.size) { tick(); val r = inp.read(buf, n, buf.size - n); if (r < 0) { if (deadlineNs != 0L) break; throw IOException("connection closed") }; n += r }
            out.write(buf, 0, n)
        }
        else if (h["transfer-encoding"]?.contains("chunked", true) == true) {
            while (true) {
                val n = line().substringBefore(';').trim().toInt(16)
                if (n == 0) { line(); break }
                // the declared size is the TV's word: never allocate it (a salvaged reply keeps 4 KiB at most, a normal one 1 MiB)
                if (n < 0 || out.size() + n.toLong() > (if (deadlineNs != 0L) bodyCap else 1 shl 20)) { if (deadlineNs != 0L) break; throw IOException("reply too large") }
                val b = ByteArray(n); var k = 0
                while (k < n) { tick(); val r = inp.read(b, k, n - k); if (r < 0) throw IOException("connection closed"); k += r }
                out.write(b); line()
            }
        } else dead = true
        if (h["connection"]?.equals("close", true) == true) dead = true
        return Reply(status, h, out.toString("UTF-8"))
    }

    override fun close() { runCatching { ch?.close() }; ch = null; input = null }
    internal fun stalled(now: Long) = busy && (now - lastProgress) / 1_000_000 > stallMs

    private object Watchdog {
        private val conns = ConcurrentHashMap.newKeySet<HttpConn>()
        private val t = Thread({
            while (true) {
                try { Thread.sleep(2000) } catch (_: InterruptedException) { return@Thread }
                val now = System.nanoTime()
                for (c in conns) { if (c.ch?.isOpen != true) conns.remove(c) else if (c.stalled(now)) { c.dead = true; runCatching { c.ch?.close() } } }
            }
        }, "xfer-watchdog").apply { isDaemon = true }
        @Synchronized fun watch(c: HttpConn) { conns.add(c); if (!t.isAlive) runCatching { t.start() } }
    }

    companion object {
        /** No progress for this long and the phone closes the connection (see PlaybackAwareCopyPolicy.worstProgressGapMs). */
        const val DEFAULT_STALL_MS = 20_000L
        private const val PROBE_EVERY = 512L * 1024
        fun tcp(host: String, port: Int, connectTimeoutMs: Int = 4000): () -> SocketChannel = {
            // a Wi-Fi Direct group joined by WifiNetworkSpecifier: only this socket goes through its network (castbridge.core.net.BoundRoute, R-14)
            SocketChannel.open().also { ch -> try { castbridge.core.net.BoundRoute.bind(host, ch.socket()); ch.socket().connect(InetSocketAddress(host, port), connectTimeoutMs) } catch (e: Throwable) { ch.close(); throw e } }
        }
    }
}
