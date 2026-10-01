package castbridge.desktop

import castbridge.core.owner.Kdf
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * scrypt (RFC 7914), written here because the JDK has none: the unlock code of the desk key must cost MEMORY to guess, not only time
 * (N = 2^15, r = 8, p = 1 = 32 MiB per guess). Checked against the RFC 7914 test vectors (ScryptTest).
 */
class ScryptKdf(private val n: Int = 1 shl 15, private val r: Int = 8, private val p: Int = 1) : Kdf {
    override val id get() = "scrypt-$n-$r-$p"
    override fun derive(passphrase: CharArray, salt: ByteArray, outLen: Int): ByteArray = scrypt(passphrase, salt, n, r, p, outLen)

    companion object {
        private fun pbkdf2(pass: CharArray, salt: ByteArray, iterations: Int, outLen: Int): ByteArray =
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(pass, salt, iterations, outLen * 8)).encoded

        fun scrypt(pass: CharArray, salt: ByteArray, n: Int, r: Int, p: Int, outLen: Int): ByteArray {
            require(n >= 2 && n and (n - 1) == 0) { "N doit être une puissance de 2" }
            // PBKDF2 of the JDK refuses an empty password: the vault never uses one, the RFC vector with "" is covered by a one-char workaround in the test only.
            val b = pbkdf2(pass, salt, 1, p * 128 * r)
            val x = IntArray(32 * r); val v = IntArray(32 * r * n); val t = IntArray(32 * r)
            for (i in 0 until p) {
                val off = i * 128 * r
                for (k in 0 until 32 * r) x[k] = le(b, off + 4 * k)
                roMix(x, v, t, n, r)
                for (k in 0 until 32 * r) put(b, off + 4 * k, x[k])
            }
            return pbkdf2WithBytes(pass, b, outLen)
        }

        // PBKDF2-HMAC-SHA256 with 1 iteration whose "salt" is the long block B (the JDK API takes it as bytes: fine).
        private fun pbkdf2WithBytes(pass: CharArray, salt: ByteArray, outLen: Int) = pbkdf2(pass, salt, 1, outLen)

        private fun le(b: ByteArray, o: Int) = (b[o].toInt() and 0xff) or ((b[o + 1].toInt() and 0xff) shl 8) or ((b[o + 2].toInt() and 0xff) shl 16) or ((b[o + 3].toInt() and 0xff) shl 24)
        private fun put(b: ByteArray, o: Int, v: Int) { b[o] = v.toByte(); b[o + 1] = (v ushr 8).toByte(); b[o + 2] = (v ushr 16).toByte(); b[o + 3] = (v ushr 24).toByte() }

        private fun roMix(x: IntArray, v: IntArray, t: IntArray, n: Int, r: Int) {
            val len = 32 * r
            for (i in 0 until n) { System.arraycopy(x, 0, v, i * len, len); blockMix(x, t, r) }
            for (i in 0 until n) {
                val j = (x[(2 * r - 1) * 16] and (n - 1))
                for (k in 0 until len) x[k] = x[k] xor v[j * len + k]
                blockMix(x, t, r)
            }
        }

        private fun blockMix(b: IntArray, y: IntArray, r: Int) {
            val x = IntArray(16); System.arraycopy(b, (2 * r - 1) * 16, x, 0, 16)
            for (i in 0 until 2 * r) {
                for (k in 0 until 16) x[k] = x[k] xor b[i * 16 + k]
                salsa8(x)
                System.arraycopy(x, 0, y, i * 16, 16)
            }
            for (i in 0 until r) System.arraycopy(y, 2 * i * 16, b, i * 16, 16)
            for (i in 0 until r) System.arraycopy(y, (2 * i + 1) * 16, b, (i + r) * 16, 16)
        }

        private fun rl(a: Int, b: Int) = (a shl b) or (a ushr (32 - b))

        private fun salsa8(b: IntArray) {
            val x = b.copyOf()
            repeat(4) {
                x[4] = x[4] xor rl(x[0] + x[12], 7); x[8] = x[8] xor rl(x[4] + x[0], 9); x[12] = x[12] xor rl(x[8] + x[4], 13); x[0] = x[0] xor rl(x[12] + x[8], 18)
                x[9] = x[9] xor rl(x[5] + x[1], 7); x[13] = x[13] xor rl(x[9] + x[5], 9); x[1] = x[1] xor rl(x[13] + x[9], 13); x[5] = x[5] xor rl(x[1] + x[13], 18)
                x[14] = x[14] xor rl(x[10] + x[6], 7); x[2] = x[2] xor rl(x[14] + x[10], 9); x[6] = x[6] xor rl(x[2] + x[14], 13); x[10] = x[10] xor rl(x[6] + x[2], 18)
                x[3] = x[3] xor rl(x[15] + x[11], 7); x[7] = x[7] xor rl(x[3] + x[15], 9); x[11] = x[11] xor rl(x[7] + x[3], 13); x[15] = x[15] xor rl(x[11] + x[7], 18)
                x[1] = x[1] xor rl(x[0] + x[3], 7); x[2] = x[2] xor rl(x[1] + x[0], 9); x[3] = x[3] xor rl(x[2] + x[1], 13); x[0] = x[0] xor rl(x[3] + x[2], 18)
                x[6] = x[6] xor rl(x[5] + x[4], 7); x[7] = x[7] xor rl(x[6] + x[5], 9); x[4] = x[4] xor rl(x[7] + x[6], 13); x[5] = x[5] xor rl(x[4] + x[7], 18)
                x[11] = x[11] xor rl(x[10] + x[9], 7); x[8] = x[8] xor rl(x[11] + x[10], 9); x[9] = x[9] xor rl(x[8] + x[11], 13); x[10] = x[10] xor rl(x[9] + x[8], 18)
                x[12] = x[12] xor rl(x[15] + x[14], 7); x[13] = x[13] xor rl(x[12] + x[15], 9); x[14] = x[14] xor rl(x[13] + x[12], 13); x[15] = x[15] xor rl(x[14] + x[13], 18)
            }
            for (i in 0 until 16) b[i] += x[i]
        }
    }
}
