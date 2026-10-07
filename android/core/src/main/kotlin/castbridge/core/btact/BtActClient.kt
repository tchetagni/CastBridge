package castbridge.core.btact

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * The phone side of « activer par Bluetooth sans appairage » (`docs/BT-PLUG-AND-PLAY.md`): speaks to ONE connection of the TV's activation service `…0008` over any pair of streams (an insecure RFCOMM
 * socket or an insecure L2CAP channel on Android, in-memory pipes in the tests). The user typed the 6-digit [code] shown on the TV; it is proven by a PAKE ([Cpace]) and never sent.
 *
 * [connect] runs the handshake: it picks the session id, sends its point, derives the keys from the TV's, sends its CONFIRM, and then REQUIRES the TV's CONFIRM before it trusts anything. A TV that
 * did not prove the code (a fake one, a corrupted link) gives [Connect.NotProved], and the phone sends nothing more: the key would only travel inside the encrypted channel anyway. What the TV says
 * before any code is compared (terms not accepted, locked for 60 s, closed, version) and what it says after the CONFIRM (wrong code) are typed ([Connect.Refused]); the plan turns them into the same
 * causes as the HTTP route's (« relisez les 6 chiffres », « la TV attend N s »…).
 *
 * Blocking calls: run them off the main thread. Nothing is logged and nothing that holds the code or the key reaches a `toString`.
 */
class BtActClient(private val entropy: BtActWire.Entropy = BtActWire.Entropy.secure()) {
    sealed class Connect {
        class Ready(val session: Session) : Connect()
        /** The TV said no, with the reason it gave (and [seconds] to wait when it is [BtActWire.Err.LOCKED]). */
        class Refused(val err: BtActWire.Err, val seconds: Long = 0) : Connect() { override fun toString() = "Refused($err, $seconds)" }
        /** The TV answered but did NOT prove it knows the code (its confirmation does not match): not a TV to trust. Nothing was sent to it that depends on the code. */
        object NotProved : Connect() { override fun toString() = "NotProved" }
        /** The link was cut, or the TV does not speak this protocol (garbage, silence, an answer out of order). */
        object LinkLost : Connect() { override fun toString() = "LinkLost" }
    }

    /** The encrypted channel to a TV that proved the code. */
    class Session internal constructor(private val channel: BtActChannel, val tvName: String, val tvVersion: String) {
        sealed class Request {
            class Text(val text: String) : Request()
            /** The TV has no request to give yet (its key safe is not ready): try again in a moment. */
            object Unavailable : Request() { override fun toString() = "Unavailable" }
            /** Too many reads for this peer in the window (the TV's allowance). */
            object Limit : Request() { override fun toString() = "Limit" }
            object Lost : Request() { override fun toString() = "Lost" }
        }

        sealed class Installed {
            class Accepted(val label: String) : Installed()
            class Rejected(val message: String) : Installed()
            object Limit : Installed() { override fun toString() = "Limit" }
            /** The link was cut before the TV said anything about the key: the TV may or may not have been activated. */
            object Lost : Installed() { override fun toString() = "Lost" }
        }

        fun readRequest(): Request = try {
            channel.send(BtActWire.Type.READ_REQUEST)
            val f = channel.receive()
            when (f.type) {
                BtActWire.Type.REQUEST -> Request.Text(String(f.payload, Charsets.UTF_8))
                BtActWire.Type.UNAVAILABLE -> Request.Unavailable
                BtActWire.Type.LIMIT -> Request.Limit
                else -> Request.Lost
            }
        } catch (e: IOException) { Request.Lost }

        /** Sends [key] (the text of the activation key, as the HTTP route's body) and reads what the TV says. */
        fun install(key: String): Installed = try {
            val body = key.toByteArray(Charsets.UTF_8)
            if (body.size > BtActWire.MAX_PAYLOAD) Installed.Rejected("Cette clé est trop longue pour la TV.")
            else {
                channel.send(BtActWire.Type.INSTALL, body)
                val f = channel.receive()
                when (f.type) {
                    BtActWire.Type.INSTALLED -> Installed.Accepted(String(f.payload, Charsets.UTF_8))
                    BtActWire.Type.REJECTED -> Installed.Rejected(String(f.payload, Charsets.UTF_8))
                    BtActWire.Type.LIMIT -> Installed.Limit
                    else -> Installed.Lost
                }
            }
        } catch (e: IOException) { Installed.Lost }

        /** Says goodbye (best effort) and zeroes the keys. */
        fun finish() { runCatching { channel.send(BtActWire.Type.FIN) }; channel.wipe() }
    }

    /** Runs the handshake on a link that is already open. [code] = the 6 digits typed by the user. */
    fun connect(input: InputStream, output: OutputStream, code: String): Connect {
        require(castbridge.core.tv.WdCode.isValid(code)) { "code de connexion : 6 chiffres attendus" }
        val din = DataInputStream(input); val dout = DataOutputStream(output)
        var keys: BtActKeys? = null
        try {
            val sid = entropy.bytes(BtActWire.SID_BYTES)
            val generator = Cpace.generator(code.toByteArray(Charsets.US_ASCII), BtActWire.CHANNEL_ID, sid)
            val secret = entropy.bytes(32)
            val ya = Cpace.message(secret, generator)
            dout.write(BtActWire.MAGIC.toByteArray(Charsets.US_ASCII))
            BtActWire.writeClear(dout, BtActWire.Msg.HELLO, byteArrayOf(BtActWire.VERSION.toByte()) + sid + ya)

            val reply = BtActWire.readClear(din)
            BtActWire.parseError(reply)?.let { (err, s) -> secret.fill(0); return Connect.Refused(err, s) }
            if (reply.type != BtActWire.Msg.REPLY || reply.body.size != BtActWire.POINT_BYTES) { secret.fill(0); return Connect.LinkLost }
            val yb = reply.body
            val k = Cpace.sharedPoint(secret, yb)
            secret.fill(0)
            if (k == null) return Connect.LinkLost                                        // a point of small order: the TV is not following the protocol
            val sessionKeys = BtActKeys.derive(Cpace.isk(sid, k, ya, BtActWire.AD_PHONE, yb, BtActWire.AD_TV), sid, ya, yb).also { keys = it }
            k.fill(0)
            BtActWire.writeClear(dout, BtActWire.Msg.CONFIRM_PHONE, sessionKeys.tagPhone())

            val answer = BtActWire.readClear(din)
            BtActWire.parseError(answer)?.let { (err, s) -> return Connect.Refused(err, s) }       // the TV verified our tag first: this is its « wrong code », or the lock that this very try caused
            if (answer.type != BtActWire.Msg.CONFIRM_TV) return Connect.LinkLost
            if (!BtActKeys.same(answer.body, sessionKeys.tagTv())) return Connect.NotProved
            val channel = BtActChannel(din, dout, sessionKeys.phoneToTv, sessionKeys.tvToPhone)
            val welcome = channel.receive()
            if (welcome.type != BtActWire.Type.WELCOME) { channel.wipe(); return Connect.LinkLost }
            val lines = String(welcome.payload, Charsets.UTF_8).lineSequence().mapNotNull { l -> l.indexOf('=').takeIf { it > 0 }?.let { l.substring(0, it) to l.substring(it + 1) } }.toMap()
            keys = null                                                                      // the channel keeps its own copies: wiped by Session.finish()
            sessionKeys.wipe()
            return Connect.Ready(Session(channel, lines["tv"].orEmpty().take(120), lines["v"].orEmpty().take(40)))
        } catch (e: IOException) {
            return Connect.LinkLost
        } finally {
            keys?.wipe()
        }
    }
}
