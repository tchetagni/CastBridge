package castbridge.core.remote.smart

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Base64
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Minimal protobuf wire format, written by hand (no dependency): varint, length-delimited, 32-bit floats.
 * Public format: https://protobuf.dev/programming-guides/encoding/
 */
object Pb {
    class Writer {
        private val out = ByteArrayOutputStream()
        fun varint(v: Long): Writer { var x = v; while (x and 0x7FL.inv() != 0L) { out.write(((x and 0x7F) or 0x80).toInt()); x = x ushr 7 }; out.write(x.toInt()); return this }
        private fun tag(field: Int, wire: Int) = varint(((field shl 3) or wire).toLong())
        fun int(field: Int, v: Int): Writer { tag(field, 0); return varint(v.toLong()) }   // int32: a negative value is sign-extended (10 bytes)
        fun bool(field: Int, v: Boolean) = int(field, if (v) 1 else 0)
        fun bytes(field: Int, b: ByteArray): Writer { tag(field, 2); varint(b.size.toLong()); out.write(b); return this }
        fun string(field: Int, s: String) = bytes(field, s.toByteArray(Charsets.UTF_8))
        fun message(field: Int, w: Writer) = bytes(field, w.toByteArray())
        fun float(field: Int, f: Float): Writer {
            tag(field, 5); val b = java.lang.Float.floatToIntBits(f)
            for (i in 0..3) out.write((b ushr (8 * i)) and 0xFF); return this
        }
        fun toByteArray(): ByteArray = out.toByteArray()
    }

    /** One decoded field: [wire] 0 varint ([long]), 1 fixed64, 2 length-delimited ([bytes]), 5 fixed32. */
    class Field(val number: Int, val wire: Int, val long: Long, val bytes: ByteArray?) {
        val string: String get() = String(bytes ?: ByteArray(0), Charsets.UTF_8)
        val float: Float get() = java.lang.Float.intBitsToFloat(long.toInt())
    }

    fun parse(b: ByteArray, off: Int = 0, end: Int = b.size): List<Field> {
        val r = ArrayList<Field>(); var i = off
        fun varint(): Long {
            var shift = 0; var v = 0L
            while (true) {
                if (i >= end || shift > 63) throw IOException("protobuf tronqué")
                val x = b[i++].toInt() and 0xFF
                v = v or ((x and 0x7F).toLong() shl shift); if (x and 0x80 == 0) return v; shift += 7
            }
        }
        while (i < end) {
            val t = varint(); val n = (t ushr 3).toInt(); val w = (t and 7).toInt()
            when (w) {
                0 -> r += Field(n, 0, varint(), null)
                1 -> { if (i + 8 > end) throw IOException("protobuf tronqué"); var v = 0L; for (k in 0..7) v = v or ((b[i + k].toLong() and 0xFF) shl (8 * k)); i += 8; r += Field(n, 1, v, null) }
                2 -> { val l = varint().toInt(); if (l < 0 || i + l > end) throw IOException("protobuf tronqué"); r += Field(n, 2, 0, b.copyOfRange(i, i + l)); i += l }
                5 -> { if (i + 4 > end) throw IOException("protobuf tronqué"); var v = 0L; for (k in 0..3) v = v or ((b[i + k].toLong() and 0xFF) shl (8 * k)); i += 4; r += Field(n, 5, v, null) }
                else -> throw IOException("type protobuf inconnu ($w)")
            }
        }
        return r
    }

    fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
}

/** HTTP(S) request with short timeouts. [trustHost]: accept the self-signed certificate of THIS host only (TVs have no public certificate). */
object Http {
    class Result(val status: Int, val body: String, val headers: Map<String, String>)

    fun request(
        method: String, url: String, body: String? = null, headers: Map<String, String> = emptyMap(),
        connectTimeoutMs: Int = 1500, readTimeoutMs: Int = 2500, trustHost: String? = null,
    ): Result {
        val c = URL(url).openConnection() as java.net.HttpURLConnection
        try {
            c.requestMethod = method; c.connectTimeout = connectTimeoutMs; c.readTimeout = readTimeoutMs; c.instanceFollowRedirects = false
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            if (c is HttpsURLConnection && trustHost != null) {
                c.sslSocketFactory = TrustAll.factory
                c.hostnameVerifier = HostnameVerifier { h, _ -> h.equals(trustHost, ignoreCase = true) }
            }
            if (body != null) { c.doOutput = true; c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) } }
            val st = c.responseCode
            val stream = if (st >= 400) c.errorStream else c.inputStream
            val text = stream?.use { s -> String(castbridge.core.util.BoundedRead.readUpTo(s, MAX_BODY), Charsets.UTF_8) } ?: ""
            return Result(st, text, c.headerFields.filterKeys { it != null }.mapKeys { it.key.lowercase() }.mapValues { it.value.joinToString(",") })
        } finally { c.disconnect() }
    }

    private const val MAX_BODY = 256 * 1024
}

/** Accepts any certificate. Used ONLY through [Http.request]'s `trustHost` / [WsClient]'s `tls` for the one TV the user picked. */
object TrustAll {
    val factory: SSLSocketFactory by lazy {
        val tm = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        SSLContext.getInstance("TLS").apply { init(null, arrayOf<TrustManager>(tm), SecureRandom()) }.socketFactory
    }
}

/** A WebSocket message. */
class WsMessage(val text: String?, val binary: ByteArray?)

/**
 * Minimal WebSocket client (RFC 6455): handshake, masked client frames, text/binary, ping→pong, close, fragmentation.
 * Blocking; [read] has its own timeout. Not thread-safe for reading; [send*] are synchronized.
 */
class WsClient private constructor(private val sock: Socket, private val input: InputStream, private val output: OutputStream) : Closeable {
    private val rnd = SecureRandom()
    @Volatile var closed = false; private set

    @Synchronized private fun frame(opcode: Int, payload: ByteArray) {
        if (closed) throw IOException("WebSocket fermée")
        val b = ByteArrayOutputStream(payload.size + 14)
        b.write(0x80 or opcode)
        when {
            payload.size < 126 -> b.write(0x80 or payload.size)
            payload.size < 65536 -> { b.write(0x80 or 126); b.write(payload.size ushr 8); b.write(payload.size and 0xFF) }
            else -> { b.write(0x80 or 127); for (i in 7 downTo 0) b.write((payload.size.toLong() ushr (8 * i)).toInt() and 0xFF) }
        }
        val mask = ByteArray(4).also(rnd::nextBytes); b.write(mask)
        for (i in payload.indices) b.write(payload[i].toInt() xor mask[i % 4].toInt())
        try { output.write(b.toByteArray()); output.flush() } catch (e: IOException) { close(); throw e }
    }

    fun sendText(s: String) = frame(1, s.toByteArray(Charsets.UTF_8))
    fun sendBinary(b: ByteArray) = frame(2, b)

    /** Next text/binary message; null on timeout (the link stays open). Throws [IOException] when the peer closed or the link broke. */
    fun read(timeoutMs: Int): WsMessage? {
        sock.soTimeout = timeoutMs
        var opcode = 0; val acc = ByteArrayOutputStream(); var partial = false
        try {
            while (true) {
                partial = acc.size() > 0
                val b0 = input.read(); if (b0 < 0) throw IOException("connexion fermée")
                partial = true
                sock.soTimeout = 5000                      // a frame that has started must finish
                val b1 = input.read(); if (b1 < 0) throw IOException("connexion fermée")
                val fin = b0 and 0x80 != 0; val op = b0 and 0x0F; val masked = b1 and 0x80 != 0
                var len = (b1 and 0x7F).toLong()
                if (len == 126L) len = (exact(2).fold(0L) { a, x -> (a shl 8) or (x.toLong() and 0xFF) })
                else if (len == 127L) len = (exact(8).fold(0L) { a, x -> (a shl 8) or (x.toLong() and 0xFF) })
                if (len > MAX_FRAME) throw IOException("trame trop grande")
                val mask = if (masked) exact(4) else null
                val p = exact(len.toInt()); if (mask != null) for (i in p.indices) p[i] = (p[i].toInt() xor mask[i % 4].toInt()).toByte()
                when (op) {
                    8 -> { close(); throw IOException("fermée par la TV") }
                    9 -> frame(10, p)
                    10 -> {}
                    else -> {
                        if (op != 0) opcode = op
                        acc.write(p)
                        if (acc.size() > MAX_FRAME) throw IOException("message trop grand")
                        if (fin) return if (opcode == 1) WsMessage(String(acc.toByteArray(), Charsets.UTF_8), null) else WsMessage(null, acc.toByteArray())
                    }
                }
                sock.soTimeout = timeoutMs
            }
        } catch (e: java.net.SocketTimeoutException) {
            if (partial) { close(); throw IOException("trame incomplète") }
            return null
        } catch (e: IOException) { close(); throw e }
    }

    private fun exact(n: Int): ByteArray {
        val b = ByteArray(n); var o = 0
        while (o < n) { val r = input.read(b, o, n - o); if (r < 0) throw IOException("connexion fermée"); o += r }
        return b
    }

    override fun close() { closed = true; runCatching { sock.close() } }

    companion object {
        private const val GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
        private const val MAX_FRAME = 1 shl 20

        /** Opens ws(s)://host:port/path. [tls]: wss with the TV's self-signed certificate accepted (that host only). */
        fun connect(host: String, port: Int, path: String, tls: Boolean = false, headers: Map<String, String> = emptyMap(), timeoutMs: Int = 2000): WsClient {
            var s = Socket()
            try {
                s.tcpNoDelay = true
                s.connect(InetSocketAddress(host, port), timeoutMs)
                s.soTimeout = timeoutMs
                if (tls) s = TrustAll.factory.createSocket(s, host, port, true).also { (it as javax.net.ssl.SSLSocket).startHandshake() }
                val key = Base64.getEncoder().encodeToString(ByteArray(16).also { SecureRandom().nextBytes(it) })
                val req = buildString {
                    append("GET $path HTTP/1.1\r\nHost: $host:$port\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: $key\r\nSec-WebSocket-Version: 13\r\n")
                    headers.forEach { (k, v) -> append("$k: $v\r\n") }
                    append("\r\n")
                }
                val out = s.getOutputStream(); out.write(req.toByteArray(Charsets.US_ASCII)); out.flush()
                val i = java.io.BufferedInputStream(s.getInputStream())
                val status = castbridge.core.remote.Http1.line(i).split(' ').getOrNull(1)?.toIntOrNull() ?: throw IOException("réponse WebSocket invalide")
                var accept: String? = null
                while (true) {
                    val h = castbridge.core.remote.Http1.line(i); if (h.isEmpty()) break
                    if (h.startsWith("Sec-WebSocket-Accept:", true)) accept = h.substringAfter(':').trim()
                }
                if (status != 101) throw IOException("WebSocket refusée (HTTP $status)")
                val want = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest((key + GUID).toByteArray(Charsets.US_ASCII)))
                if (accept != want) throw IOException("poignée de main WebSocket invalide")
                return WsClient(s, i, out)
            } catch (e: IOException) { runCatching { s.close() }; throw e }
        }
    }
}

/** Hides what must never reach a log or a diagnostic: addresses (last octet), MAC (last 3 bytes), tokens/PIN/keys. */
object Redact {
    private val IP = Regex("\\b(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})\\.\\d{1,3}\\b")
    private val MAC = Regex("\\b([0-9A-Fa-f]{2}[:-][0-9A-Fa-f]{2}[:-][0-9A-Fa-f]{2})(?:[:-][0-9A-Fa-f]{2}){3}\\b")
    private val SECRET = Regex("(?i)(token|psk|pin|auth|client-key|clientkey|password|secret|code)(\"?\\s*[:=]\\s*\"?)[^\\s\"&,}]+")
    fun clean(s: String): String = s.replace(MAC) { it.groupValues[1] + ":xx:xx:xx" }.replace(IP) { it.groupValues[1] + ".x" }.replace(SECRET) { it.groupValues[1] + it.groupValues[2] + "***" }
}
