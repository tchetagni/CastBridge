package castbridge.play

import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.Base64

/** Erreur de protocole WebSocket : le service ferme la session avec ce code (1002 protocole, 1003 type, 1007 UTF-8, 1009 trop gros). */
class WsError(val code: Int, message: String) : Exception(message)

/** WebSocket minimal (RFC 6455) côté serveur : poignée de main, trames masquées du client, trames non masquées du serveur. Aucune extension. */
object WsProtocol {
    const val OP_CONT = 0; const val OP_TEXT = 1; const val OP_BINARY = 2; const val OP_CLOSE = 8; const val OP_PING = 9; const val OP_PONG = 10
    private const val GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"

    fun acceptKey(key: String): String =
        Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest((key.trim() + GUID).toByteArray(Charsets.US_ASCII)))

    class Frame(val fin: Boolean, val opcode: Int, val payload: ByteArray)

    /** Lit une trame ; `null` en fin de flux propre. Refuse : trame non masquée, bits réservés, contrôle fragmenté ou > 125 octets, charge > [maxBytes]. */
    fun readFrame(input: InputStream, maxBytes: Int): Frame? {
        val din = DataInputStream(input)
        val b0 = input.read()
        if (b0 < 0) return null
        val b1 = input.read()
        if (b1 < 0) throw EOFException()
        val fin = b0 and 0x80 != 0
        if (b0 and 0x70 != 0) throw WsError(1002, "bits réservés")
        val op = b0 and 0x0f
        if (b1 and 0x80 == 0) throw WsError(1002, "trame client non masquée")
        var len = (b1 and 0x7f).toLong()
        if (len == 126L) { len = din.readUnsignedShort().toLong(); if (len < 126) throw WsError(1002, "longueur non minimale") }
        else if (len == 127L) { len = din.readLong(); if (len < 65_536) throw WsError(1002, "longueur invalide ou non minimale") }
        val control = op >= 8
        if (control && (!fin || len > 125)) throw WsError(1002, "trame de contrôle invalide")
        if (op == OP_CLOSE && len == 1L) throw WsError(1002, "close d'un octet")
        if (op !in 0..2 && op !in 8..10) throw WsError(1002, "opcode inconnu")
        if (len > maxBytes) throw WsError(1009, "message trop gros")
        val mask = ByteArray(4); din.readFully(mask)
        val data = ByteArray(len.toInt()); din.readFully(data)
        for (i in data.indices) data[i] = (data[i].toInt() xor mask[i and 3].toInt()).toByte()
        return Frame(fin, op, data)
    }

    fun writeFrame(out: OutputStream, opcode: Int, payload: ByteArray) {
        val h = java.io.ByteArrayOutputStream(10)
        h.write(0x80 or opcode)
        when {
            payload.size < 126 -> h.write(payload.size)
            payload.size < 65_536 -> { h.write(126); h.write(payload.size ushr 8); h.write(payload.size and 0xff) }
            else -> { h.write(127); for (s in 56 downTo 0 step 8) h.write(((payload.size.toLong() ushr s) and 0xff).toInt()) }
        }
        out.write(h.toByteArray()); out.write(payload); out.flush()   // UN SEUL écrivain par connexion (WsConn tient le verrou d'écriture) : jamais de synchronized autour d'une écriture réseau
    }

    fun closePayload(code: Int, reason: String): ByteArray {
        val r = reason.toByteArray(Charsets.UTF_8).let { if (it.size > 120) it.copyOf(120) else it }
        return byteArrayOf((code ushr 8).toByte(), (code and 0xff).toByte()) + r
    }

    /** Décodage UTF-8 strict d'un message texte. */
    fun utf8(bytes: ByteArray): String = try {
        Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
            .decode(java.nio.ByteBuffer.wrap(bytes)).toString()
    } catch (e: java.nio.charset.CharacterCodingException) { throw WsError(1007, "UTF-8 invalide") }
}
