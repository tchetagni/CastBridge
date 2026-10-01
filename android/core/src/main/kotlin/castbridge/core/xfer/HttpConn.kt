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
    private val stallMs: Long = 20_000,
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
            val bb = ByteBuffer.wrap(head.toString().toByteArray(Charsets.ISO_8859_1))
            while (bb.hasRemaining()) c.write(bb)
            body(c)
            lastProgress = System.nanoTime()
            return readReply()
        } catch (e: IOException) { dead = true; close(); throw e }
        catch (e: RuntimeException) { dead = true; close(); throw IOException(e.message, e) }
        finally { busy = false }
    }

    /** Called by body writers after each piece they push, so the watchdog sees progress. */
    fun progress() { lastProgress = System.nanoTime() }

    private fun readReply(): Reply {
        val inp = input ?: throw IOException("closed")
        fun line(): String {
            val sb = StringBuilder()
            while (true) { val b = inp.read(); if (b < 0) throw IOException("connection closed"); if (b == '\n'.code) break; if (b != '\r'.code) sb.append(b.toChar()); if (sb.length > 16_384) throw IOException("header too long") }
            return sb.toString()
        }
        val status = line().split(' ').getOrNull(1)?.toIntOrNull() ?: throw IOException("bad reply")
        val h = HashMap<String, String>()
        while (true) { val l = line(); if (l.isEmpty()) break; val i = l.indexOf(':'); if (i > 0) h[l.substring(0, i).trim().lowercase()] = l.substring(i + 1).trim() }
        val out = ByteArrayOutputStream()
        val len = h["content-length"]?.toLongOrNull()
        if (len != null) { if (len > 1 shl 20) throw IOException("reply too large"); val buf = ByteArray(len.toInt()); var n = 0; while (n < buf.size) { val r = inp.read(buf, n, buf.size - n); if (r < 0) throw IOException("connection closed"); n += r }; out.write(buf) }
        else if (h["transfer-encoding"]?.contains("chunked", true) == true) {
            while (true) { val n = line().substringBefore(';').trim().toInt(16); if (n == 0) { line(); break }; val b = ByteArray(n); var k = 0; while (k < n) { val r = inp.read(b, k, n - k); if (r < 0) throw IOException("connection closed"); k += r }; out.write(b); line() }
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
        fun tcp(host: String, port: Int, connectTimeoutMs: Int = 4000): () -> SocketChannel = {
            SocketChannel.open().also { ch -> try { ch.socket().connect(InetSocketAddress(host, port), connectTimeoutMs) } catch (e: IOException) { ch.close(); throw e } }
        }
    }
}
