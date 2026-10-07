package castbridge.core.btact

import castbridge.core.owner.Hkdf
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * From the PAKE's session key (`ISK`, [Cpace.isk]) to what the protocol uses: one key per direction for the encrypted channel and one per side for the key confirmation. HKDF-SHA256 (the
 * repository's own [Hkdf]) with a label per key, so no key is ever used for two jobs and a confirmation of one side cannot be replayed as the other's.
 *
 * The confirmation tags prove that BOTH sides ended with the same `K`, i.e. used the same code: the phone sends its tag first, and the TV answers with its own only after it verified the phone's
 * (so the TV never gives an active attacker a value that depends on the key before that attacker proved the code). Tags are compared in constant time ([MessageDigest.isEqual]).
 */
class BtActKeys private constructor(
    private val confirmPhone: ByteArray, private val confirmTv: ByteArray,
    /** AES-256 key of the frames the PHONE sends. */
    val phoneToTv: ByteArray,
    /** AES-256 key of the frames the TV sends. */
    val tvToPhone: ByteArray,
    private val transcript: ByteArray,
) {
    fun tagPhone(): ByteArray = tag(confirmPhone, "phone")
    fun tagTv(): ByteArray = tag(confirmTv, "tv")

    /** Zeroes every key held here (the channel keeps its own copies: [BtActChannel.wipe]). */
    fun wipe() { confirmPhone.fill(0); confirmTv.fill(0); phoneToTv.fill(0); tvToPhone.fill(0) }

    private fun tag(key: ByteArray, role: String): ByteArray =
        Mac.getInstance("HmacSHA256").run { init(SecretKeySpec(key, "HmacSHA256")); update("castbridge-bt-activation-v1 confirm $role".toByteArray()); update(transcript); doFinal() }

    companion object {
        private const val SALT = "castbridge-bt-activation-v1"

        /** [isk] = [Cpace.isk]; [sid], [ya], [yb] = the session id and the two PAKE messages (hashed into every tag). */
        fun derive(isk: ByteArray, sid: ByteArray, ya: ByteArray, yb: ByteArray): BtActKeys {
            val prk = Hkdf.extract(SALT.toByteArray(), isk)
            fun key(label: String) = Hkdf.expand(prk, label.toByteArray(), 32)
            val transcript = MessageDigest.getInstance("SHA-256").digest(Cpace.lvCat(BtActWire.MAGIC.toByteArray(), byteArrayOf(BtActWire.VERSION.toByte()), sid, ya, yb))
            return BtActKeys(key("confirm phone"), key("confirm tv"), key("key phone to tv"), key("key tv to phone"), transcript)
        }

        /** Constant time. */
        fun same(a: ByteArray, b: ByteArray): Boolean = MessageDigest.isEqual(a, b)
    }
}
