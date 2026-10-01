package castbridge.core.remote.vendor

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.Base64

/** RFC 6455, just what a loopback client needs: masked client frames, fragments, ping/pong/close. */
object WebSocketFrames {
    const val CONT = 0x0; const val TEXT = 0x1; const val BINARY = 0x2; const val CLOSE = 0x8; const val PING = 0x9; const val PONG = 0xA
    const val MAX_PAYLOAD = 1 shl 20
    private const val GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"

    /** One final client frame (always masked, as the RFC requires of a client). [mask] is 4 random bytes. */
    fun encode(opcode: Int, payload: ByteArray, mask: ByteArray, fin: Boolean = true): ByteArray {
        require(mask.size == 4)
        val out = ByteArrayOutputStream(payload.size + 14)
        out.write((if (fin) 0x80 else 0) or opcode)
        when {
            payload.size < 126 -> out.write(0x80 or payload.size)
            payload.size <= 0xFFFF -> { out.write(0x80 or 126); out.write(payload.size ushr 8); out.write(payload.size and 0xff) }
            else -> { out.write(0x80 or 127); val n = payload.size.toLong(); for (s in 56 downTo 0 step 8) out.write(((n ushr s) and 0xff).toInt()) }
        }
        out.write(mask)
        for (i in payload.indices) out.write(payload[i].toInt() xor mask[i % 4].toInt())
        return out.toByteArray()
    }

    fun acceptKey(clientKey: String): String =
        Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest((clientKey + GUID).toByteArray(Charsets.US_ASCII)))

    class Message(val opcode: Int, val payload: ByteArray) { val text: String get() = String(payload, Charsets.UTF_8) }

    /** Reads whole messages (fragments joined; control frames may arrive between fragments and are returned as they come). */
    class Reader(private val input: InputStream) {
        private var partialOp = -1
        private val partial = ByteArrayOutputStream()

        fun next(): Message {
            while (true) {
                val b0 = input.read(); if (b0 < 0) throw EOFException("closed")
                val b1 = input.read(); if (b1 < 0) throw EOFException("closed")
                val fin = b0 and 0x80 != 0
                if (b0 and 0x70 != 0) throw IOException("reserved bits set")
                val op = b0 and 0x0f
                val masked = b1 and 0x80 != 0
                var len = (b1 and 0x7f).toLong()
                if (len == 126L) len = readN(2).fold(0L) { a, b -> (a shl 8) or (b.toLong() and 0xff) }
                else if (len == 127L) len = readN(8).fold(0L) { a, b -> (a shl 8) or (b.toLong() and 0xff) }
                if (len < 0 || len > MAX_PAYLOAD) throw IOException("frame too large")
                val mask = if (masked) readN(4) else null
                val p = readN(len.toInt())
                if (mask != null) for (i in p.indices) p[i] = (p[i].toInt() xor mask[i % 4].toInt()).toByte()
                if (op >= 8) {
                    if (!fin || p.size > 125) throw IOException("bad control frame")
                    return Message(op, p)
                }
                if (op == CONT) {
                    if (partialOp < 0) throw IOException("unexpected continuation")
                } else {
                    if (partialOp >= 0) throw IOException("new message inside a fragmented one")
                    partialOp = op
                }
                partial.write(p)
                if (partial.size() > MAX_PAYLOAD) throw IOException("message too large")
                if (fin) {
                    val m = Message(partialOp, partial.toByteArray())
                    partialOp = -1; partial.reset()
                    return m
                }
            }
        }

        private fun readN(n: Int): ByteArray {
            val a = ByteArray(n); var o = 0
            while (o < n) { val r = input.read(a, o, n - o); if (r < 0) throw EOFException("closed"); o += r }
            return a
        }
    }
}
