package castbridge.play

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.EOFException
import java.net.Socket
import java.security.SecureRandom
import java.util.Base64

/** Client WebSocket écrit à la main pour les tests qui doivent maîtriser les trames (pas de pong automatique, trames énormes, etc.). */
class RawWs(port: Int, headers: Map<String, String> = mapOf("Origin" to "https://bridge.sti-cm.com"), path: String = "/play/ws", autoPong: Boolean = true) : AutoCloseable {
    val socket = Socket("127.0.0.1", port).also { it.soTimeout = 10_000 }
    private val din = DataInputStream(socket.getInputStream())
    private val out = socket.getOutputStream()
    var status = 0
    var autoPong = autoPong
    val pings = java.util.concurrent.atomic.AtomicInteger()
    @Volatile var eof = false

    init {
        val key = Base64.getEncoder().encodeToString(ByteArray(16).also { SecureRandom().nextBytes(it) })
        val sb = StringBuilder("GET $path HTTP/1.1\r\nHost: 127.0.0.1\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Version: 13\r\nSec-WebSocket-Key: $key\r\n")
        headers.forEach { (k, v) -> sb.append("$k: $v\r\n") }
        out.write((sb.toString() + "\r\n").toByteArray()); out.flush()
        val head = StringBuilder()
        while (!head.endsWith("\r\n\r\n")) head.append(din.read().also { if (it < 0) throw EOFException("fermé pendant la poignée de main") }.toChar())
        status = head.substring(9, 12).toInt()
        if (status == 101) check(head.contains(WsProtocol.acceptKey(key))) { "Sec-WebSocket-Accept faux" }
    }

    class F(val opcode: Int, val payload: ByteArray) { val text get() = String(payload, Charsets.UTF_8); val closeCode get() = if (payload.size >= 2) ((payload[0].toInt() and 0xff) shl 8) or (payload[1].toInt() and 0xff) else -1 }

    fun send(opcode: Int, payload: ByteArray, fin: Boolean = true) {
        val h = ByteArrayOutputStream()
        h.write((if (fin) 0x80 else 0) or opcode)
        when {
            payload.size < 126 -> h.write(0x80 or payload.size)
            payload.size < 65_536 -> { h.write(0x80 or 126); h.write(payload.size ushr 8); h.write(payload.size and 0xff) }
            else -> { h.write(0x80 or 127); for (s in 56 downTo 0 step 8) h.write(((payload.size.toLong() ushr s) and 0xff).toInt()) }
        }
        val mask = byteArrayOf(1, 2, 3, 4); h.write(mask)
        for (i in payload.indices) h.write(payload[i].toInt() xor mask[i and 3].toInt())
        out.write(h.toByteArray()); out.flush()
    }

    fun sendText(s: String) = send(1, s.toByteArray())

    /** Prochaine trame (les pings sont comptés et, selon [autoPong], acquittés) ; null au bout du délai ou à la fermeture. */
    fun read(timeoutMs: Int = 3_000): F? {
        val end = System.currentTimeMillis() + timeoutMs
        while (true) {
            try {
                val left = (end - System.currentTimeMillis()).toInt()
                if (left <= 0) return null
                socket.soTimeout = left
                val b0 = din.read(); if (b0 < 0) { eof = true; return null }
                val b1 = din.readUnsignedByte()
                var len = (b1 and 0x7f).toLong()
                if (len == 126L) len = din.readUnsignedShort().toLong() else if (len == 127L) len = din.readLong()
                val data = ByteArray(len.toInt()); din.readFully(data)
                val f = F(b0 and 0x0f, data)
                if (f.opcode == 9) { pings.incrementAndGet(); if (autoPong) send(10, data); continue }
                return f
            } catch (_: java.net.SocketTimeoutException) { return null } catch (_: java.io.IOException) { eof = true; return null }
        }
    }

    /** Prochain message texte JSON de type [t] (les autres sont ignorés). */
    fun await(t: String, timeoutMs: Int = 5_000): Map<*, *>? {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            val f = read(500) ?: continue
            if (f.opcode == 1) (castbridge.core.quiz.Json.parse(f.text) as Map<*, *>).let { if (it["t"] == t) return it }
            if (f.opcode == 8) return null
        }
        return null
    }

    /** Vrai si le serveur a fermé la connexion (trame close reçue ou fin de flux) dans le délai. */
    fun closedWithin(timeoutMs: Int): Int? {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            val f = read(200)
            if (f == null) { if (eof) return -1 else continue }
            if (f.opcode == 8) return f.closeCode
        }
        return null
    }

    override fun close() { runCatching { socket.close() } }
}
