package castbridge.core.btact

import castbridge.core.owner.X25519
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.MessageDigest

/**
 * CPace, a balanced password-authenticated key exchange (PAKE): the cipher suite CPACE-X25519-SHA512 of draft-irtf-cfrg-cpace-13 (CFRG), built on the X25519 that [X25519] already
 * implements (RFC 7748, pure Kotlin: the JCE only offers `XDH` from Android 13) and on SHA-512. Used by « activer par Bluetooth sans appairage » (docs/BT-PLUG-AND-PLAY.md, service `…0008`)
 * to prove the 6-digit connection code of the TV WITHOUT sending it and WITHOUT giving a passive listener anything to test offline: the code (PRS, « password-related string ») only picks
 * a generator `g` of the curve from the session id; each side sends `y·g` for a fresh random scalar `y`; the shared point `K = ya·Yb = yb·Ya` is the same on both sides ONLY when both
 * used the same code. A wrong guess therefore costs one online try per session (that the TV counts, [castbridge.core.tv.activation.ActivationAttemptGate]), never an offline search over the
 * 10^6 codes, which is what a plain « X25519 + HMAC(code) » would allow.
 *
 * The primitives follow the draft byte for byte and are pinned by its own test vector (Appendix B.1, `CpaceTest`): `generator_string`, the hash truncated to 32 bytes, `decodeUCoordinate`,
 * the Elligator 2 map of RFC 9380 § 6.7.1 for curve25519 (only the u-coordinate is needed, so no square root is taken), the scalar multiplication, `ISK`. What is NOT the draft is the
 * protocol around it: who sends what, the key confirmation and the channel ([BtActWire], [BtActKeys], [BtActChannel]).
 *
 * NOT constant time (same limit as [X25519]: [BigInteger]); the scalars are one per session and no remote caller can time them over a Bluetooth link.
 */
object Cpace {
    /** Domain separation of the generator (draft: the DSI of the group, for X25519 `b"CPace255"`). */
    const val DSI = "CPace255"
    private const val DSI_ISK = "CPace255_ISK"
    /** SHA-512 input block, `H.s_in_bytes` of the draft: the zero padding fills the first block so the password never stands alone in it. */
    private const val HASH_BLOCK_BYTES = 128
    private const val FIELD_BYTES = 32

    private val P: BigInteger = BigInteger.ONE.shiftLeft(255).subtract(BigInteger.valueOf(19))
    private val J: BigInteger = BigInteger.valueOf(486_662)                   // Curve25519: y² = x³ + Jx² + x
    private val TWO: BigInteger = BigInteger.valueOf(2)                       // Z of the Elligator 2 map of curve25519 (a non-square)
    private val HALF_P_MINUS_ONE: BigInteger = P.subtract(BigInteger.ONE).shiftRight(1)

    // ------------------------------------------------------------------ encodings of the draft

    /** `prepend_len(data)`: the length as an unsigned LEB128, then the data. */
    fun prependLen(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        var n = data.size
        while (true) {
            val b = n and 0x7F
            n = n ushr 7
            if (n == 0) { out.write(b); break }
            out.write(b or 0x80)
        }
        out.write(data)
        return out.toByteArray()
    }

    /** `lv_cat(a1, …, an)`: every part with its length in front, concatenated. */
    fun lvCat(vararg parts: ByteArray): ByteArray = ByteArrayOutputStream().also { o -> parts.forEach { o.write(prependLen(it)) } }.toByteArray()

    /** `generator_string(DSI, PRS, CI, sid, s_in_bytes)`: `lv_cat(DSI, PRS, zero_bytes(len_zpad), CI, sid)`. */
    fun generatorString(prs: ByteArray, ci: ByteArray, sid: ByteArray): ByteArray {
        val dsi = DSI.toByteArray(Charsets.US_ASCII)
        val zpad = maxOf(0, HASH_BLOCK_BYTES - prependLen(prs).size - prependLen(dsi).size - 1)
        return lvCat(dsi, prs, ByteArray(zpad), ci, sid)
    }

    // ------------------------------------------------------------------ the generator

    /** RFC 7748 `decodeUCoordinate` for 255 bits: little endian, the most significant bit masked. */
    internal fun decodeU(b: ByteArray): BigInteger {
        require(b.size == FIELD_BYTES)
        val c = b.copyOf(); c[31] = (c[31].toInt() and 0x7F).toByte()
        return BigInteger(1, c.reversedArray())
    }

    private fun encodeLe(v: BigInteger): ByteArray {
        val be = v.toByteArray()
        val out = ByteArray(FIELD_BYTES)
        for (i in 0 until FIELD_BYTES) { val j = be.size - 1 - i; if (j >= 0) out[i] = be[j] }
        return out
    }

    private fun isSquare(a: BigInteger): Boolean = a.modPow(HALF_P_MINUS_ONE, P).let { it == BigInteger.ONE || it.signum() == 0 }

    private fun curve(x: BigInteger): BigInteger = x.multiply(x).mod(P).multiply(x.add(J)).add(x).mod(P)           // x³ + Jx² + x

    /**
     * `map_to_curve_elligator2` (RFC 9380 § 6.7.1, curve25519: J = 486662, K = 1, Z = 2), u-coordinate only. [u] is any field element: the result is the u-coordinate of a point OF THE CURVE (not of its
     * twist): one of the two candidates `x1`, `x2 = -x1 - J` always has a square `x³ + Jx² + x`.
     */
    fun elligator2(u: BigInteger): BigInteger {
        val uu = u.mod(P)
        val denominator = BigInteger.ONE.add(TWO.multiply(uu.multiply(uu))).mod(P)                      // 1 + Z·u²
        val inverse = if (denominator.signum() == 0) BigInteger.ZERO else denominator.modPow(P.subtract(TWO), P)   // inv0
        var x1 = J.negate().multiply(inverse).mod(P)
        if (x1.signum() == 0) x1 = J.negate().mod(P)
        if (isSquare(curve(x1))) return x1
        return x1.negate().subtract(J).mod(P)                                                           // x2
    }

    /** Is [u] (32 bytes, little endian) the u-coordinate of a point of curve25519 itself? (what [generator] always returns; used by the tests) */
    fun onCurve(u: ByteArray): Boolean = isSquare(curve(decodeU(u).mod(P)))

    /**
     * `calculate_generator(H, PRS, CI, sid)`: `gen_str = generator_string(…)`, `gen_str_hash = SHA-512(gen_str)[0..32)`, `g = elligator2(decodeUCoordinate(gen_str_hash))`. 32 bytes, little endian.
     * [prs] = the connection code (6 ASCII digits), [ci] = the channel identifier of the protocol, [sid] = the session id (16 random bytes, chosen by the initiator and sent in clear).
     */
    fun generator(prs: ByteArray, ci: ByteArray, sid: ByteArray): ByteArray {
        val hash = MessageDigest.getInstance("SHA-512").digest(generatorString(prs, ci, sid)).copyOf(FIELD_BYTES)
        return encodeLe(elligator2(decodeU(hash)))
    }

    // ------------------------------------------------------------------ the exchange

    /** The message of one side: `y·g` for the secret scalar [y] (32 bytes, clamped by X25519). */
    fun message(y: ByteArray, g: ByteArray): ByteArray = X25519.scalarMult(y, g)

    /** `K = y·Y`; null when it is the all-zero point (the peer sent a point of small order: the exchange is aborted, RFC 7748 § 6.1). */
    fun sharedPoint(y: ByteArray, peerMessage: ByteArray): ByteArray? = X25519.sharedSecret(y, peerMessage)

    /**
     * `ISK = SHA-512(lv_cat(DSI + "_ISK", sid, K) || transcript_ir(Ya, ADa, Yb, ADb))` with `transcript_ir = lv_cat(Ya, ADa) || lv_cat(Yb, ADb)` (initiator then responder). 64 bytes: the intermediate
     * session key, from which [BtActKeys] derives the confirmation and channel keys.
     */
    fun isk(sid: ByteArray, k: ByteArray, ya: ByteArray, ada: ByteArray, yb: ByteArray, adb: ByteArray): ByteArray {
        val md = MessageDigest.getInstance("SHA-512")
        md.update(lvCat(DSI_ISK.toByteArray(Charsets.US_ASCII), sid, k))
        md.update(lvCat(ya, ada)); md.update(lvCat(yb, adb))
        return md.digest()
    }
}
