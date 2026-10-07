package castbridge.core.btact

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.security.SecureRandom

/**
 * The wire of « activer par Bluetooth sans appairage » (service `…0008`, `BtProtocol.ACTIVATION_SERVICE_UUID`; docs/BT-PLUG-AND-PLAY.md). Four bytes of magic, then four CLEAR frames (the PAKE and its
 * two confirmations), then ENCRYPTED frames ([BtActChannel]) that carry what the HTTP route carries: the device request and the key.
 *
 * ```
 * phone → TV   "CBTA"
 * phone → TV   HELLO    u16 len | 0x01 | version u8 | sid[16] | Ya[32]                     (the phone picks the session id)
 * TV → phone   REPLY    u16 len | 0x02 | Yb[32]            or   ERROR u16 len | 0x7E | code u8 | seconds u32     (terms, locked, closed… BEFORE any code is compared)
 * phone → TV   CONFIRM  u16 len | 0x03 | tag[32]           (HMAC under a key derived from the PAKE: proves the phone knew the code)
 * TV → phone   CONFIRM  u16 len | 0x04 | tag[32]           or   ERROR (wrong code, locked): the TV answers ONLY after it verified the phone's tag
 * then, both ways:  u16 len | AES-256-GCM( type u8 | payload )     the TV first sends WELCOME (its name and version)
 * ```
 * A wrong code is known to the TV only here, at the phone's CONFIRM: that is where the TV counts an online attempt ([castbridge.core.tv.activation.ActivationAttemptGate]). Nothing before the
 * CONFIRM depends on the code in a way a listener can test offline (CPace). Pure: two streams in, two out.
 */
object BtActWire {
    const val MAGIC = "CBTA"
    const val VERSION = 1
    const val SID_BYTES = 16
    const val POINT_BYTES = 32
    const val TAG_BYTES = 32
    /** The largest clear frame (type + body): the biggest one is HELLO (50 bytes). */
    const val MAX_CLEAR = 128
    /** The largest payload of an encrypted frame: the key text the HTTP route accepts (`LockedActivationApi.MAX_BODY`, 16 Kio). */
    const val MAX_PAYLOAD = 16_384
    const val GCM_TAG_BYTES = 16
    /** type + payload + GCM tag: the length field of an encrypted frame. */
    const val MAX_SECURE = 1 + MAX_PAYLOAD + GCM_TAG_BYTES

    /** CPace's channel identifier (CI) and the associated data (ADa, ADb) of the two sides: bound into the generator and the session key, so a session of another protocol or role never matches. */
    val CHANNEL_ID: ByteArray = Cpace.lvCat("castbridge-bt-activation-v1".toByteArray(), "initiator:phone".toByteArray(), "responder:tv".toByteArray())
    val AD_PHONE: ByteArray = "castbridge-bt-activation-v1/phone".toByteArray()
    val AD_TV: ByteArray = "castbridge-bt-activation-v1/tv".toByteArray()

    object Msg {
        const val HELLO = 0x01
        const val REPLY = 0x02
        const val CONFIRM_PHONE = 0x03
        const val CONFIRM_TV = 0x04
        const val ERROR = 0x7E
    }

    /** Types of the ENCRYPTED frames. */
    object Type {
        /** TV → phone, first: `tv=<name>\nv=<version>\n`. */
        const val WELCOME = 0x10
        /** phone → TV: read the device request (no payload). */
        const val READ_REQUEST = 0x11
        /** TV → phone: the complete device request, text (`DeviceRequestText.complete`). */
        const val REQUEST = 0x12
        /** TV → phone: no request to give yet (the key safe is not ready). */
        const val UNAVAILABLE = 0x13
        /** phone → TV: the key, text (as the body of `POST /api/activation/install`). */
        const val INSTALL = 0x14
        /** TV → phone: the key was accepted; payload = the label of the activation. */
        const val INSTALLED = 0x15
        /** TV → phone: the key was refused; payload = the reason, in French. */
        const val REJECTED = 0x16
        /** TV → phone: too many reads or verifications for this peer in the window. */
        const val LIMIT = 0x17
        /** Either way: nothing more to say, close. */
        const val FIN = 0x7F
    }

    /** What the TV says in an ERROR frame. The numbers are the wire: never renumber. */
    enum class Err(val code: Int) {
        /** The protocol version of the phone is not served. */
        VERSION(1),
        /** The terms of use are not accepted on the TV yet: said BEFORE the code is looked at, like the HTTP route's 409. */
        TERMS(2),
        /** This peer made too many wrong codes: `seconds` to wait (the HTTP route's 401 « locked »). */
        LOCKED(3),
        /** The TV received too many wrong codes from everybody: closed for a while (the HTTP route's 429). */
        CLOSED(4),
        /** The TV cannot serve now (no connection code, route not open). */
        UNAVAILABLE(5),
        /** The phone's confirmation did not match: the code is wrong. */
        BAD_CODE(6),
        /** A frame that is not understood (garbage, a point of small order). */
        BAD_MESSAGE(7);

        companion object { fun of(code: Int): Err? = values().firstOrNull { it.code == code } }
    }

    /** A peer that does not follow the protocol (bad length, bad type, bad tag): the link is dropped, nothing more is said. */
    class ProtocolException(message: String) : IOException(message)

    class Clear(val type: Int, val body: ByteArray)

    fun writeClear(out: DataOutputStream, type: Int, body: ByteArray) {
        require(1 + body.size <= MAX_CLEAR)
        out.writeShort(1 + body.size); out.writeByte(type); out.write(body); out.flush()
    }

    /** One clear frame; a length outside 1..[MAX_CLEAR] is a [ProtocolException] (never allocated), a link cut inside it is an [java.io.EOFException]. */
    fun readClear(inp: DataInputStream): Clear {
        val len = inp.readUnsignedShort()
        if (len < 1 || len > MAX_CLEAR) throw ProtocolException("trame de longueur $len")
        val b = ByteArray(len); inp.readFully(b)
        return Clear(b[0].toInt() and 0xFF, b.copyOfRange(1, b.size))
    }

    fun errorBody(err: Err, seconds: Long = 0): ByteArray {
        val s = seconds.coerceIn(0, 0xFFFF_FFFFL)
        return byteArrayOf(err.code.toByte(), (s shr 24).toByte(), (s shr 16).toByte(), (s shr 8).toByte(), s.toByte())
    }

    /** The ERROR a clear frame carries, or null when it is not one the protocol knows. */
    fun parseError(f: Clear): Pair<Err, Long>? {
        if (f.type != Msg.ERROR || f.body.size != 5) return null
        val err = Err.of(f.body[0].toInt() and 0xFF) ?: return null
        val s = ((f.body[1].toLong() and 0xFF) shl 24) or ((f.body[2].toLong() and 0xFF) shl 16) or ((f.body[3].toLong() and 0xFF) shl 8) or (f.body[4].toLong() and 0xFF)
        return err to s
    }

    /** Where the random bytes come from: the system's in the apps, a fixed sequence in the tests (so a session can be replayed bit for bit). */
    fun interface Entropy {
        fun bytes(n: Int): ByteArray

        companion object {
            fun secure(): Entropy { val r = SecureRandom(); return Entropy { n -> ByteArray(n).also(r::nextBytes) } }
        }
    }
}
