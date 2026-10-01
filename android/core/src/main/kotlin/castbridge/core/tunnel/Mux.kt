package castbridge.core.tunnel

import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Several TCP streams over ONE RFCOMM link ("CastBridge API v2" service). Opening an RFCOMM link is the slow and fragile part of
 * Bluetooth (the stack may still hold the previous one: "already at opened state"), so the phone keeps one link per TV and
 * carries each local HTTP connection as a stream of it. Frame: `type u8 | stream u16 | length u16 | payload`.
 * The old one-link-per-connection service (v1) is untouched and still served, so an old phone or an old TV keeps working.
 */
object MuxFrame {
    const val OPEN = 1
    const val DATA = 2
    const val CLOSE = 3
    const val PING = 4
    const val PONG = 5
    const val MAX_PAYLOAD = 16 * 1024
}

class MuxStream internal constructor(val id: Int, private val session: MuxSession, private val maxQueuedBytes: Int) {
    private val queue = java.util.ArrayDeque<ByteArray>()
    private var queued = 0
    private var eof = false
    private var cur: ByteArray? = null
    private var pos = 0
    private val lock = Object()
    private val closed = AtomicBoolean(false)
    val bytesIn = AtomicLong()
    val bytesOut = AtomicLong()

    internal fun push(b: ByteArray) = synchronized(lock) {
        while (queued >= maxQueuedBytes && !eof) lock.wait(200)     // backpressure: the shared link slows down, nothing piles up
        if (eof) return
        queue.add(b); queued += b.size; bytesIn.addAndGet(b.size.toLong()); lock.notifyAll()
    }

    internal fun remoteClosed() = synchronized(lock) { eof = true; lock.notifyAll() }

    val input: InputStream = object : InputStream() {
        override fun read(): Int { val one = ByteArray(1); return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xff }
        override fun read(b: ByteArray, off: Int, len: Int): Int = synchronized(lock) {
            if (len == 0) return 0
            while (cur == null || pos >= cur!!.size) {
                cur = queue.poll()
                if (cur != null) { pos = 0; queued -= cur!!.size; lock.notifyAll(); break }
                if (eof) return -1
                lock.wait(500)
            }
            val n = minOf(len, cur!!.size - pos)
            System.arraycopy(cur!!, pos, b, off, n); pos += n; n
        }
    }

    val output: OutputStream = object : OutputStream() {
        override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)
        override fun write(b: ByteArray, off: Int, len: Int) {
            if (closed.get()) throw IOException("flux fermé")
            var o = off; var left = len
            while (left > 0) {
                val n = minOf(left, MuxFrame.MAX_PAYLOAD)
                session.send(MuxFrame.DATA, id, b, o, n); bytesOut.addAndGet(n.toLong()); o += n; left -= n
            }
        }
    }

    /** Ends the stream on both sides (a relay never half-closes). Idempotent. */
    fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { session.send(MuxFrame.CLOSE, id, ByteArray(0), 0, 0) }
        remoteClosed(); session.forget(id)
    }
    val isClosed get() = closed.get()
}

class MuxSession(
    private val link: castbridge.core.tv.Link,
    /** TV side: a stream the phone opened (called on its own thread). Phone side: null (the TV never opens streams). */
    private val onOpen: ((MuxStream) -> Unit)? = null,
    private val onClosed: (String) -> Unit = {},
    private val maxStreams: Int = 4,
    private val maxQueuedBytes: Int = 256 * 1024,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val streams = ConcurrentHashMap<Int, MuxStream>()
    private val nextId = AtomicInteger(1)
    private val writeLock = Any()
    private val dead = AtomicBoolean(false)
    @Volatile var lastRx = now(); private set
    @Volatile var lastPong = 0L; private set
    @Volatile var closeReason: String? = null; private set
    val isClosed get() = dead.get()
    val streamCount get() = streams.size
    val bytesIn = AtomicLong()
    val bytesOut = AtomicLong()

    fun start(): MuxSession { Thread(::readLoop, "bt-mux-rx").apply { isDaemon = true; start() }; return this }

    internal fun send(type: Int, id: Int, b: ByteArray, off: Int, len: Int) {
        if (dead.get()) throw IOException("liaison fermée")
        try {
            synchronized(writeLock) {
                val o = link.output
                o.write(byteArrayOf(type.toByte(), (id shr 8).toByte(), id.toByte(), (len shr 8).toByte(), len.toByte()))
                if (len > 0) o.write(b, off, len)
                o.flush()
            }
            if (type == MuxFrame.DATA) bytesOut.addAndGet(len.toLong())
        } catch (e: IOException) { close("écriture impossible : ${e.javaClass.simpleName}"); throw e }
    }

    internal fun forget(id: Int) { streams.remove(id) }

    /** Phone side: a new stream to a fresh connection on the TV; null if the link is dead or full. */
    fun open(): MuxStream? {
        if (dead.get() || streams.size >= maxStreams) return null
        val id = nextId.getAndIncrement() and 0xffff
        val s = MuxStream(id, this, maxQueuedBytes)
        streams[id] = s
        return try { send(MuxFrame.OPEN, id, ByteArray(0), 0, 0); s } catch (e: IOException) { streams.remove(id); null }
    }

    fun ping(): Boolean = try { send(MuxFrame.PING, 0, ByteArray(0), 0, 0); true } catch (e: IOException) { false }

    private fun readFully(b: ByteArray, n: Int) {
        var got = 0
        while (got < n) { val r = link.input.read(b, got, n - got); if (r < 0) throw EOFException(); got += r }
    }

    private fun readLoop() {
        val head = ByteArray(5)
        try {
            while (!dead.get()) {
                readFully(head, 5)
                lastRx = now()
                val type = head[0].toInt() and 0xff
                val id = ((head[1].toInt() and 0xff) shl 8) or (head[2].toInt() and 0xff)
                val len = ((head[3].toInt() and 0xff) shl 8) or (head[4].toInt() and 0xff)
                val payload = if (len > 0) ByteArray(len).also { readFully(it, len) } else ByteArray(0)
                when (type) {
                    MuxFrame.OPEN -> {
                        val h = onOpen
                        val s = MuxStream(id, this, maxQueuedBytes)
                        if (h == null || streams.size >= maxStreams || streams.putIfAbsent(id, s) != null) {
                            runCatching { send(MuxFrame.CLOSE, id, ByteArray(0), 0, 0) }
                        } else Thread({ try { h(s) } catch (_: Throwable) { s.close() } }, "bt-mux-stream").apply { isDaemon = true; start() }
                    }
                    MuxFrame.DATA -> { bytesIn.addAndGet(len.toLong()); streams[id]?.push(payload) }
                    MuxFrame.CLOSE -> streams.remove(id)?.remoteClosed()
                    MuxFrame.PING -> runCatching { send(MuxFrame.PONG, 0, ByteArray(0), 0, 0) }
                    MuxFrame.PONG -> lastPong = now()
                    else -> throw IOException("trame inconnue $type")
                }
            }
        } catch (e: EOFException) { close("la TV a fermé la liaison") }
        catch (e: IOException) { close("liaison coupée : ${e.message ?: e.javaClass.simpleName}") }
    }

    /** Closes the link and every stream; [reason] (French) is kept for the diagnostic. Idempotent. */
    fun close(reason: String = "fermée") {
        if (!dead.compareAndSet(false, true)) return
        closeReason = reason
        runCatching { link.close() }
        streams.values.forEach { it.remoteClosed() }; streams.clear()
        runCatching { onClosed(reason) }
    }
}
