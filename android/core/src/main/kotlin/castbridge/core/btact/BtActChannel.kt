package castbridge.core.btact

import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The encrypted channel that follows the PAKE of « activer par Bluetooth sans appairage »: AES-256-GCM, one key per direction ([BtActKeys]), one frame = `u16 length | ciphertext+tag` where the plain
 * text is `type u8 | payload`. The GCM nonce is NOT sent: it is the frame counter of that direction (4 zero bytes then the counter, 8 bytes big endian), so
 *  - a frame replayed in the same session, dropped, duplicated or reordered fails to authenticate (the counter the receiver expects is not the one the sender used);
 *  - a frame of ANOTHER session fails too (other keys);
 *  - the length is authenticated (it is the additional data), a bit flipped anywhere is refused;
 *  - a link cut inside a frame is an [java.io.EOFException]: the reader never returns half a frame as a message.
 * A frame that does not authenticate ends the channel with a [BtActWire.ProtocolException] (the peer does not follow the protocol or someone tampers with the link; nothing is answered).
 * One thread may read while another writes (a lock per direction); the protocol itself is request then response, one thread at a time.
 *
 * AES-GCM, not ChaCha20-Poly1305: the JCE has the first on every Android CastBridge runs on (API 26), the second only from Android 9-11 depending on the provider.
 */
class BtActChannel(private val input: DataInputStream, private val output: DataOutputStream, sendKey: ByteArray, receiveKey: ByteArray) {
    class Frame(val type: Int, val payload: ByteArray)

    private val sendKey = sendKey.copyOf()
    private val receiveKey = receiveKey.copyOf()
    private var sent = 0L
    private var received = 0L
    private val sendLock = Any()
    private val receiveLock = Any()

    /** Sends one frame. [payload] up to [BtActWire.MAX_PAYLOAD] bytes. */
    fun send(type: Int, payload: ByteArray = ByteArray(0)) {
        require(payload.size <= BtActWire.MAX_PAYLOAD) { "trame trop grande" }
        synchronized(sendLock) {
            val plain = ByteArray(1 + payload.size).also { it[0] = type.toByte(); payload.copyInto(it, 1) }
            val len = plain.size + BtActWire.GCM_TAG_BYTES
            val sealed = try { cipher(Cipher.ENCRYPT_MODE, sendKey, sent, len).doFinal(plain) } catch (e: GeneralSecurityException) { throw java.io.IOException("chiffrement impossible") }
            sent++
            output.writeShort(len); output.write(sealed); output.flush()
        }
    }

    /** The next frame; throws [BtActWire.ProtocolException] when it does not authenticate (or is not a legal length) and [java.io.IOException] when the link is cut. */
    fun receive(): Frame = synchronized(receiveLock) {
        val len = input.readUnsignedShort()
        if (len < 1 + BtActWire.GCM_TAG_BYTES || len > BtActWire.MAX_SECURE) throw BtActWire.ProtocolException("trame chiffrée de longueur $len")
        val sealed = ByteArray(len); input.readFully(sealed)
        val plain = try { cipher(Cipher.DECRYPT_MODE, receiveKey, received, len).doFinal(sealed) } catch (e: GeneralSecurityException) { throw BtActWire.ProtocolException("trame refusée") }
        received++
        Frame(plain[0].toInt() and 0xFF, plain.copyOfRange(1, plain.size))
    }

    /** Zeroes the keys held here: a finished session leaves nothing usable in memory. */
    fun wipe() { sendKey.fill(0); receiveKey.fill(0) }

    private fun cipher(mode: Int, key: ByteArray, counter: Long, length: Int): Cipher {
        val nonce = ByteArray(12); for (i in 0 until 8) nonce[11 - i] = (counter ushr (8 * i)).toByte()
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            updateAAD(byteArrayOf((length shr 8).toByte(), length.toByte()))
        }
    }
}
