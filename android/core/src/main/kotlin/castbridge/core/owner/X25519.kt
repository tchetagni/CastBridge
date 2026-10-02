package castbridge.core.owner

import java.math.BigInteger

/**
 * X25519 (RFC 7748 § 5) in plain Kotlin: the JCE only offers `XDH` from Android 13 and CastBridge-TV runs from Android 8 (minSdk 26), the same reason as
 * [castbridge.core.update.Ed25519]. Montgomery ladder over [BigInteger].
 *
 * NOT constant time: [BigInteger] arithmetic and the ladder's conditional swap branch on secret bits. Accepted here because the private keys live only on a TV and on the issuer's tool,
 * one ephemeral scalar per box, and no remote caller can time them (a local timing measurement needs code running in the app, which could read the key anyway). Do not reuse this
 * class in a server that answers requests with a long-lived private key.
 */
object X25519 {
    private val P: BigInteger = BigInteger.ONE.shiftLeft(255).subtract(BigInteger.valueOf(19))
    private val A24: BigInteger = BigInteger.valueOf(121665)
    private val NINE = ByteArray(32).also { it[0] = 9 }

    /** The scalar as RFC 7748 § 5 clamps it (new array; [k] is not modified). */
    fun clamp(k: ByteArray): ByteArray {
        require(k.size == 32) { "scalaire de 32 octets attendu" }
        return k.copyOf().also { it[0] = (it[0].toInt() and 248).toByte(); it[31] = ((it[31].toInt() and 127) or 64).toByte() }
    }

    private fun decodeLe(b: ByteArray): BigInteger = BigInteger(1, b.reversedArray())

    private fun encodeLe(v: BigInteger): ByteArray {
        val be = v.toByteArray()                       // big endian, may carry a sign byte
        val out = ByteArray(32)
        for (i in 0 until 32) { val j = be.size - 1 - i; if (j >= 0) out[i] = be[j] }
        return out
    }

    /** `k * u` on Curve25519: [k] is clamped, the top bit of [u] is ignored (RFC 7748 § 5). 32 bytes in, 32 bytes out, little endian. */
    fun scalarMult(k: ByteArray, u: ByteArray): ByteArray {
        require(k.size == 32 && u.size == 32) { "32 octets attendus" }
        val scalar = decodeLe(clamp(k))
        val x1 = decodeLe(u.copyOf().also { it[31] = (it[31].toInt() and 127).toByte() }).mod(P)
        var x2 = BigInteger.ONE; var z2 = BigInteger.ZERO; var x3 = x1; var z3 = BigInteger.ONE
        var swap = false
        for (t in 254 downTo 0) {
            val bit = scalar.testBit(t)
            if (swap != bit) { val tx = x2; x2 = x3; x3 = tx; val tz = z2; z2 = z3; z3 = tz }
            swap = bit
            val a = x2.add(z2).mod(P); val aa = a.multiply(a).mod(P)
            val b = x2.subtract(z2).mod(P); val bb = b.multiply(b).mod(P)
            val e = aa.subtract(bb).mod(P)
            val c = x3.add(z3).mod(P); val d = x3.subtract(z3).mod(P)
            val da = d.multiply(a).mod(P); val cb = c.multiply(b).mod(P)
            val s = da.add(cb).mod(P); val dd = da.subtract(cb).mod(P)
            x3 = s.multiply(s).mod(P)
            z3 = x1.multiply(dd.multiply(dd).mod(P)).mod(P)
            x2 = aa.multiply(bb).mod(P)
            z2 = e.multiply(aa.add(A24.multiply(e)).mod(P)).mod(P)
        }
        if (swap) { x2 = x3; z2 = z3 }
        return encodeLe(x2.multiply(z2.modPow(P.subtract(BigInteger.valueOf(2)), P)).mod(P))
    }

    /** The public key of a 32-byte private key (any 32 bytes: the clamp is applied on use). */
    fun publicKey(priv: ByteArray): ByteArray = scalarMult(priv, NINE)

    /** The shared secret, or null when it is all zeros (a point of small order: RFC 7748 § 6.1 asks to refuse it). */
    fun sharedSecret(priv: ByteArray, pub: ByteArray): ByteArray? {
        if (priv.size != 32 || pub.size != 32) return null
        val s = scalarMult(priv, pub)
        return if (s.all { it == 0.toByte() }) null else s
    }
}
