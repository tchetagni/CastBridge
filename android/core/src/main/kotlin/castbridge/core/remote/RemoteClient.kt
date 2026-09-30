package castbridge.core.remote

import java.io.BufferedInputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URLDecoder
import java.net.URLEncoder

/** One remote action waiting to reach the TV: POST /api/remote/<route>?<params>&sid=…&seq=<seq>. */
data class RemoteEvent(val seq: Long, val route: String, val params: Map<String, String>, val at: Long) {
    /** "up" of a held key: never dropped (a key must not stay held on the TV; the TV's watchdog is only the safety net). */
    val isRelease: Boolean get() = route == "key" && params["action"] == "up"
    fun query(sid: String): String = RemoteWire.query(params + mapOf("sid" to sid, "seq" to seq.toString()))
}

/**
 * Phone-side queue of remote events, in order, each with a sequence number of the session [sid]. An event leaves the queue
 * only when the TV answered it ([ack]); after a link cut it is sent again with the same number, and the TV ([SeqFilter])
 * ignores it if it had already applied it: no lost key, no double key. A press older than [maxAgeMs] is dropped instead
 * of reaching the TV late (a stale "OK" after a 10 s cut would be a surprise); releases are always kept.
 */
class RemoteQueue(
    val sid: String = newSid(),
    private val maxAgeMs: Long = 3000,
    private val capacity: Int = 64,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val q = ArrayDeque<RemoteEvent>()
    private val lock = Object()
    private var nextSeq = 1L
    @Volatile var dropped = 0; private set

    fun offer(route: String, params: Map<String, String>): RemoteEvent = synchronized(lock) {
        val e = RemoteEvent(nextSeq++, route, params, now())
        if (q.size >= capacity) {
            val victim = q.firstOrNull { !it.isRelease }
            if (victim != null) { q.remove(victim); dropped++ } else q.removeFirst()
        }
        q.addLast(e)
        lock.notifyAll()
        e
    }

    /** First event to send (stale presses are dropped on the way), null if none. */
    fun peek(): RemoteEvent? = synchronized(lock) { dropStale(); q.firstOrNull() }

    /** Waits up to [timeoutMs] for an event; null on timeout. */
    fun take(timeoutMs: Long): RemoteEvent? {
        val until = System.currentTimeMillis() + timeoutMs
        synchronized(lock) {
            while (true) {
                dropStale()
                q.firstOrNull()?.let { return it }
                val left = until - System.currentTimeMillis()
                if (left <= 0) return null
                lock.wait(left)
            }
        }
    }

    /** The TV answered event [seq] (applied, refused or duplicate): it is done. */
    fun ack(seq: Long) = synchronized(lock) { q.removeAll { it.seq == seq }; Unit }

    fun size(): Int = synchronized(lock) { q.size }
    fun clear() = synchronized(lock) { q.clear() }
    /** Wakes a waiting [take] (the session is stopping). */
    fun wake() = synchronized(lock) { lock.notifyAll() }

    private fun dropStale() {
        val t = now()
        while (true) {
            val f = q.firstOrNull() ?: return
            if (!f.isRelease && t - f.at > maxAgeMs) { q.removeFirst(); dropped++ } else return
        }
    }

    companion object {
        fun newSid(): String = java.util.UUID.randomUUID().toString().replace("-", "").take(16)
    }
}

/** An HTTP-like answer of the TV. */
data class RemoteReply(val status: Int, val body: String)

/** A link that carries remote requests to the TV: Wi-Fi (HTTP keep-alive) or Bluetooth (line protocol). */
interface RemoteTransport : Closeable {
    val name: String
    /** Sends one request to /api/remote/[route]; throws [IOException] when the link is broken. */
    fun send(method: String, route: String, query: String): RemoteReply
}

/** Encoding shared by both transports. */
object RemoteWire {
    fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
    fun query(p: Map<String, String>): String = p.entries.joinToString("&") { "${enc(it.key)}=${enc(it.value)}" }
    fun parseQuery(q: String): Map<String, String> = if (q.isEmpty()) emptyMap() else q.split('&').filter { it.isNotEmpty() }.associate {
        val i = it.indexOf('=')
        val k = if (i < 0) it else it.substring(0, i)
        val v = if (i < 0) "" else it.substring(i + 1)
        URLDecoder.decode(k, "UTF-8") to URLDecoder.decode(v, "UTF-8")
    }
}

/**
 * Remote over Wi-Fi: ONE TCP connection kept open (HTTP/1.1 keep-alive, TCP_NODELAY), so a key costs one small write and one
 * small read, no handshake. The TV closes idle connections after 15 s; the session's ping every few seconds keeps it open,
 * and a closed one is simply reopened (the event is resent with the same sequence number).
 */
class HttpRemoteTransport(
    val host: String, val port: Int, private val pin: String,
    private val connectTimeoutMs: Int = 2000, private val readTimeoutMs: Int = 3000,
) : RemoteTransport {
    override val name = "Wi-Fi"
    private var sock: Socket? = null
    private var input: BufferedInputStream? = null
    private var output: OutputStream? = null
    /** TCP connections opened so far (1 for a whole session on a steady network: keep-alive works). */
    @Volatile var opened = 0; private set

    @Synchronized fun connect() {
        if (sock != null) return
        val s = Socket()
        try {
            s.tcpNoDelay = true
            s.connect(InetSocketAddress(host, port), connectTimeoutMs)
            s.soTimeout = readTimeoutMs
        } catch (e: IOException) { runCatching { s.close() }; throw e }
        sock = s; input = BufferedInputStream(s.getInputStream(), 8192); output = s.getOutputStream(); opened++
    }

    @Synchronized override fun send(method: String, route: String, query: String): RemoteReply {
        connect()
        try {
            val path = "/api/remote/$route" + if (query.isNotEmpty()) "?$query" else ""
            val auth = castbridge.core.trust.TvAuth.header(pin).let { (k, v) -> "$k: $v" }
            val req = "$method $path HTTP/1.1\r\nHost: $host:$port\r\n$auth\r\nContent-Length: 0\r\nConnection: keep-alive\r\n\r\n"
            output!!.write(req.toByteArray(Charsets.UTF_8)); output!!.flush()
            val r = Http1.readResponse(input!!)
            if (r.close) close()
            return RemoteReply(r.status, r.body)
        } catch (e: IOException) { close(); throw e }
    }

    @Synchronized override fun close() {
        runCatching { sock?.close() }; sock = null; input = null; output = null
    }
}

/** Minimal HTTP/1.1 response reader (status, Content-Length or chunked body, Connection: close). */
object Http1 {
    class Response(val status: Int, val body: String, val close: Boolean)

    fun readResponse(i: InputStream): Response {
        val status = line(i).let { l ->
            if (!l.startsWith("HTTP/1.")) throw IOException("bad status line")
            l.split(' ').getOrNull(1)?.toIntOrNull() ?: throw IOException("bad status line")
        }
        var len = -1L; var chunked = false; var close = false
        while (true) {
            val h = line(i)
            if (h.isEmpty()) break
            val c = h.indexOf(':'); if (c < 0) continue
            val k = h.substring(0, c).trim().lowercase(); val v = h.substring(c + 1).trim()
            when (k) {
                "content-length" -> len = v.toLongOrNull() ?: throw IOException("bad length")
                "transfer-encoding" -> chunked = v.lowercase().contains("chunked")
                "connection" -> close = v.equals("close", true)
            }
        }
        if (len > MAX_BODY) throw IOException("answer too large")
        val body = when {
            chunked -> buildString {
                while (true) {
                    val n = line(i).substringBefore(';').trim().toLong(16)
                    if (n == 0L) { while (line(i).isNotEmpty()) {}; break }
                    if (length + n > MAX_BODY) throw IOException("answer too large")
                    append(String(exact(i, n.toInt()), Charsets.UTF_8)); line(i)
                }
            }
            len >= 0 -> String(exact(i, len.toInt()), Charsets.UTF_8)
            else -> { close = true; String(i.readBytes().take(MAX_BODY.toInt()).toByteArray(), Charsets.UTF_8) }
        }
        return Response(status, body, close)
    }

    private fun exact(i: InputStream, n: Int): ByteArray {
        val b = ByteArray(n); var off = 0
        while (off < n) { val r = i.read(b, off, n - off); if (r < 0) throw IOException("connection closed"); off += r }
        return b
    }

    /** One line (without CR LF), UTF-8. */
    fun line(i: InputStream): String {
        val b = java.io.ByteArrayOutputStream(64)
        while (true) {
            val c = i.read()
            if (c < 0) throw IOException("connection closed")
            if (c == '\n'.code) break
            if (c != '\r'.code) b.write(c)
            if (b.size() > 16384) throw IOException("line too long")
        }
        return b.toString("UTF-8")
    }

    private const val MAX_BODY = 256 * 1024L
}

/**
 * Remote over Bluetooth, when phone and TV share no network: the TV's Bluetooth file service (BtProtocol) answers the magic
 * "CBTR" + PIN, then carries one request per line, the same requests as over HTTP:
 *
 *   phone -> TV : "CBTR" | PIN (6 ASCII)          TV -> phone : status byte (BtProtocol.OK / ERR_PIN / ERR_LOCKED)
 *   phone -> TV : "POST key?code=DPAD_UP&sid=…&seq=3\n"
 *   TV -> phone : "200 {\"ok\":true,…}\n"
 */
object RemoteBt {
    const val MAGIC = "CBTR"

    /** TV side, after the PIN check: answers lines until the phone closes the link. [onActivity] feeds a watchdog. */
    fun serve(input: InputStream, output: OutputStream, api: RemoteApi, onActivity: () -> Unit = {}) {
        val out = output.buffered()
        while (true) {
            val l = try { Http1.line(input) } catch (e: IOException) { return }
            onActivity()
            if (l.isEmpty()) continue
            val sp = l.indexOf(' ')
            val method = if (sp > 0) l.substring(0, sp) else "GET"
            val target = if (sp > 0) l.substring(sp + 1) else l
            val route = target.substringBefore('?')
            val params = runCatching { RemoteWire.parseQuery(target.substringAfter('?', "")) }.getOrNull()
            val r = if (params == null) castbridge.core.tv.ApiReply(400, """{"ok":false,"error":"bad query"}""")
                else api.handle(RemoteApi.PREFIX + route, method, params) ?: castbridge.core.tv.ApiReply(404, """{"ok":false,"error":"not found"}""")
            out.write("${r.status} ${r.json.replace('\n', ' ')}\n".toByteArray(Charsets.UTF_8)); out.flush()
        }
    }

    /** Phone side: the handshake; throws [castbridge.core.tv.BtProtocol.Refused] on a bad PIN. */
    fun handshake(input: InputStream, output: OutputStream, pin: String) {
        output.write((MAGIC + castbridge.core.trust.TvAuth.btPin(pin)).toByteArray(Charsets.US_ASCII)); output.flush()   // a token = trusted phone: no PIN on the wire
        val st = input.read()
        if (st < 0) throw IOException("connection closed")
        if (st != castbridge.core.tv.BtProtocol.OK) throw castbridge.core.tv.BtProtocol.Refused(st)
    }

    /** Phone side transport over an open, handshaken link. */
    class Transport(private val input: InputStream, private val output: OutputStream, private val link: Closeable) : RemoteTransport {
        override val name = "Bluetooth"
        @Synchronized override fun send(method: String, route: String, query: String): RemoteReply {
            output.write("$method $route${if (query.isNotEmpty()) "?$query" else ""}\n".toByteArray(Charsets.UTF_8)); output.flush()
            val l = Http1.line(input)
            val sp = l.indexOf(' ')
            val st = (if (sp > 0) l.substring(0, sp) else l).toIntOrNull() ?: throw IOException("bad answer")
            return RemoteReply(st, if (sp > 0) l.substring(sp + 1) else "")
        }
        override fun close() { runCatching { link.close() } }
    }
}
