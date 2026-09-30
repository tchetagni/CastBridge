package castbridge.core.update

import java.math.BigInteger
import java.security.MessageDigest

/**
 * Ed25519 signature verification (RFC 8032, section 5.1.7) in plain Kotlin: java.security only offers Ed25519 from
 * Android 13, and the apps run from Android 8. Verification only (the private key never leaves the server); speed is
 * irrelevant (one manifest now and then).
 */
object Ed25519 {
    // initialization order matters: each constant only uses the ones above it
    private val TWO: BigInteger = BigInteger.valueOf(2)
    private val P: BigInteger = BigInteger.ONE.shiftLeft(255).subtract(BigInteger.valueOf(19))
    private val L: BigInteger = BigInteger.ONE.shiftLeft(252).add(BigInteger("27742317777372353535851937790883648493"))
    private val D: BigInteger = BigInteger.valueOf(-121665).multiply(inv(BigInteger.valueOf(121666))).mod(P)
    private val SQRT_M1: BigInteger = TWO.modPow(P.subtract(BigInteger.ONE).shiftRight(2), P)

    /** Extended homogeneous coordinates (X:Y:Z:T), x = X/Z, y = Y/Z, x*y = T/Z. */
    private class Pt(val x: BigInteger, val y: BigInteger, val z: BigInteger, val t: BigInteger)

    private val IDENTITY = Pt(BigInteger.ZERO, BigInteger.ONE, BigInteger.ONE, BigInteger.ZERO)
    private val B: Pt = run {
        val y = BigInteger.valueOf(4).multiply(inv(BigInteger.valueOf(5))).mod(P)
        val x = recoverX(y, 0) ?: error("base point")
        Pt(x, y, BigInteger.ONE, x.multiply(y).mod(P))
    }

    /**
     * @param publicKey the raw 32-byte public key
     * @return true if [signature] (64 bytes) is a valid signature of [message]
     */
    fun verify(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean {
        if (publicKey.size != 32 || signature.size != 64) return false
        val a = decode(publicKey) ?: return false
        val rBytes = signature.copyOfRange(0, 32)
        val r = decode(rBytes) ?: return false
        val s = leInt(signature.copyOfRange(32, 64))
        if (s >= L) return false
        val h = leInt(MessageDigest.getInstance("SHA-512").run { update(rBytes); update(publicKey); update(message); digest() }).mod(L)
        val left = mul(s, B)
        val right = add(r, mul(h, a))
        return equal(left, right)
    }

    private fun inv(x: BigInteger): BigInteger = x.modPow(P.subtract(TWO), P)

    private fun leInt(b: ByteArray): BigInteger = BigInteger(1, b.reversedArray())

    private fun add(p: Pt, q: Pt): Pt {
        val a = p.y.subtract(p.x).multiply(q.y.subtract(q.x)).mod(P)
        val b = p.y.add(p.x).multiply(q.y.add(q.x)).mod(P)
        val c = p.t.multiply(TWO).multiply(D).multiply(q.t).mod(P)
        val d = p.z.multiply(TWO).multiply(q.z).mod(P)
        val e = b.subtract(a); val f = d.subtract(c); val g = d.add(c); val h = b.add(a)
        return Pt(e.multiply(f).mod(P), g.multiply(h).mod(P), f.multiply(g).mod(P), e.multiply(h).mod(P))
    }

    private fun mul(s: BigInteger, p: Pt): Pt {
        var q = IDENTITY
        var base = p
        var k = s
        while (k.signum() > 0) {
            if (k.testBit(0)) q = add(q, base)
            base = add(base, base)
            k = k.shiftRight(1)
        }
        return q
    }

    private fun equal(p: Pt, q: Pt): Boolean =
        p.x.multiply(q.z).subtract(q.x.multiply(p.z)).mod(P).signum() == 0 &&
            p.y.multiply(q.z).subtract(q.y.multiply(p.z)).mod(P).signum() == 0

    private fun recoverX(y: BigInteger, sign: Int): BigInteger? {
        if (y >= P) return null
        val y2 = y.multiply(y).mod(P)
        val x2 = y2.subtract(BigInteger.ONE).multiply(inv(D.multiply(y2).add(BigInteger.ONE))).mod(P)
        if (x2.signum() == 0) return if (sign == 1) null else BigInteger.ZERO
        var x = x2.modPow(P.add(BigInteger.valueOf(3)).shiftRight(3), P)
        if (x.multiply(x).subtract(x2).mod(P).signum() != 0) x = x.multiply(SQRT_M1).mod(P)
        if (x.multiply(x).subtract(x2).mod(P).signum() != 0) return null
        if (x.testBit(0) != (sign == 1)) x = P.subtract(x)
        return x
    }

    private fun decode(b: ByteArray): Pt? {
        val bytes = b.copyOf()
        val sign = (bytes[31].toInt() shr 7) and 1
        bytes[31] = (bytes[31].toInt() and 0x7f).toByte()
        val y = leInt(bytes)
        val x = recoverX(y, sign) ?: return null
        return Pt(x, y, BigInteger.ONE, x.multiply(y).mod(P))
    }
}
