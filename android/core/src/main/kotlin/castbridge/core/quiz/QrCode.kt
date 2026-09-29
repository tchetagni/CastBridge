package castbridge.core.quiz

/**
 * Minimal QR Code encoder (ISO/IEC 18004), pure Kotlin, no dependency: byte mode, versions 1-10, error correction
 * L/M/Q/H, automatic mask choice. Enough for a URL such as "http://192.168.1.20:8765/quiz?code=1234" (~45 bytes).
 * Structure after Project Nayuki's reference implementation (MIT); cross-checked against the Python "qrcode"
 * package in QrCodeTest. ~200 lines instead of a 300 ko library (ZXing) on a TV short of storage.
 */
class QrCode private constructor(val version: Int, val ecl: Ecl, val mask: Int, private val modules: Array<BooleanArray>) {
    enum class Ecl(val formatBits: Int) { L(1), M(0), Q(3), H(2) }

    val size: Int get() = modules.size
    /** true = dark module. (x, y) from the top-left corner, quiet zone not included. */
    operator fun get(x: Int, y: Int): Boolean = modules[y][x]

    companion object {
        const val MAX_VERSION = 10

        fun encode(text: String, ecl: Ecl = Ecl.M, forceMask: Int = -1): QrCode = encode(text.toByteArray(Charsets.UTF_8), ecl, forceMask)

        fun encode(data: ByteArray, ecl: Ecl = Ecl.M, forceMask: Int = -1): QrCode {
            val version = (1..MAX_VERSION).firstOrNull { v -> 4 + countBits(v) + data.size * 8 <= dataCodewords(v, ecl) * 8 }
                ?: throw IllegalArgumentException("text too long for a version-$MAX_VERSION QR code")
            return encode(data, ecl, version, forceMask)
        }

        /** Encodes with a given version (tests). */
        fun encode(data: ByteArray, ecl: Ecl, version: Int, forceMask: Int): QrCode {
            val cap = dataCodewords(version, ecl) * 8
            val bits = BitBuf()
            bits.append(4, 4)                                  // byte mode
            bits.append(data.size, countBits(version))
            data.forEach { bits.append(it.toInt() and 0xff, 8) }
            require(bits.size <= cap) { "data too long for version $version" }
            bits.append(0, minOf(4, cap - bits.size))          // terminator
            bits.append(0, (8 - bits.size % 8) % 8)
            var pad = 0xEC
            while (bits.size < cap) { bits.append(pad, 8); pad = pad xor 0xEC xor 0x11 }
            val codewords = ByteArray(bits.size / 8) { i -> var b = 0; for (j in 0 until 8) b = (b shl 1) or bits[i * 8 + j]; b.toByte() }
            val all = addEcc(codewords, version, ecl)

            val size = version * 4 + 17
            val m = Array(size) { BooleanArray(size) }
            val fn = Array(size) { BooleanArray(size) }
            fun set(x: Int, y: Int, dark: Boolean) { m[y][x] = dark; fn[y][x] = true }
            // timing
            for (i in 0 until size) { set(6, i, i % 2 == 0); set(i, 6, i % 2 == 0) }
            // finders (+ separators)
            for ((cx, cy) in listOf(3 to 3, size - 4 to 3, 3 to size - 4)) {
                for (dy in -4..4) for (dx in -4..4) {
                    val x = cx + dx; val y = cy + dy
                    if (x in 0 until size && y in 0 until size) set(x, y, maxOf(Math.abs(dx), Math.abs(dy)).let { it != 2 && it != 4 })
                }
            }
            // alignment
            val pos = alignmentPositions(version)
            for (i in pos.indices) for (j in pos.indices) {
                if (i == 0 && j == 0 || i == 0 && j == pos.size - 1 || i == pos.size - 1 && j == 0) continue
                for (dy in -2..2) for (dx in -2..2) set(pos[i] + dx, pos[j] + dy, maxOf(Math.abs(dx), Math.abs(dy)) != 1)
            }
            drawFormat(m, fn, ecl, 0)
            if (version >= 7) {
                var rem = version
                repeat(12) { rem = (rem shl 1) xor ((rem ushr 11) * 0x1F25) }
                val vbits = (version shl 12) or rem
                for (i in 0 until 18) {
                    val bit = (vbits ushr i) and 1 != 0
                    val a = size - 11 + i % 3; val b = i / 3
                    set(a, b, bit); set(b, a, bit)
                }
            }
            // data, zigzag from the bottom-right corner
            var i = 0
            var right = size - 1
            while (right >= 1) {
                if (right == 6) right = 5
                for (vert in 0 until size) for (j in 0..1) {
                    val x = right - j
                    val upward = ((right + 1) and 2) == 0
                    val y = if (upward) size - 1 - vert else vert
                    if (!fn[y][x] && i < all.size * 8) { m[y][x] = ((all[i ushr 3].toInt() ushr (7 - (i and 7))) and 1) != 0; i++ }
                }
                right -= 2
            }
            // mask with the lowest penalty
            val mask = if (forceMask in 0..7) forceMask else (0..7).minBy { k ->
                applyMask(m, fn, k); drawFormat(m, fn, ecl, k)
                val p = penalty(m)
                applyMask(m, fn, k)
                p
            }
            applyMask(m, fn, mask); drawFormat(m, fn, ecl, mask)
            return QrCode(version, ecl, mask, m)
        }

        private fun countBits(version: Int) = if (version < 10) 8 else 16

        // Error correction codewords per block and number of blocks, [ecl][version] (index 0 unused).
        private val ECC_PER_BLOCK = arrayOf(
            intArrayOf(-1, 7, 10, 15, 20, 26, 18, 20, 24, 30, 18),
            intArrayOf(-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26),
            intArrayOf(-1, 13, 22, 18, 26, 18, 24, 18, 22, 20, 24),
            intArrayOf(-1, 17, 28, 22, 16, 22, 28, 26, 26, 24, 28),
        )
        private val BLOCKS = arrayOf(
            intArrayOf(-1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 4),
            intArrayOf(-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5),
            intArrayOf(-1, 1, 1, 2, 2, 4, 4, 6, 6, 8, 8),
            intArrayOf(-1, 1, 1, 2, 4, 4, 4, 5, 6, 8, 8),
        )

        private fun rawModules(v: Int): Int {
            var r = (16 * v + 128) * v + 64
            if (v >= 2) {
                val n = v / 7 + 2
                r -= (25 * n - 10) * n - 55
                if (v >= 7) r -= 36
            }
            return r
        }

        fun dataCodewords(v: Int, ecl: Ecl): Int = rawModules(v) / 8 - ECC_PER_BLOCK[ecl.ordinal][v] * BLOCKS[ecl.ordinal][v]

        private fun alignmentPositions(v: Int): IntArray {
            if (v == 1) return IntArray(0)
            val n = v / 7 + 2
            val step = (v * 8 + n * 3 + 5) / (n * 4 - 4) * 2
            val r = IntArray(n); r[0] = 6
            var p = v * 4 + 17 - 7
            for (i in n - 1 downTo 1) { r[i] = p; p -= step }
            return r
        }

        private fun addEcc(data: ByteArray, v: Int, ecl: Ecl): ByteArray {
            val nBlocks = BLOCKS[ecl.ordinal][v]
            val eccLen = ECC_PER_BLOCK[ecl.ordinal][v]
            val raw = rawModules(v) / 8
            val nShort = nBlocks - raw % nBlocks
            val shortLen = raw / nBlocks
            val div = rsDivisor(eccLen)
            val blocks = ArrayList<ByteArray>()
            var k = 0
            for (i in 0 until nBlocks) {
                val len = shortLen - eccLen + if (i < nShort) 0 else 1
                val dat = data.copyOfRange(k, k + len); k += len
                val ecc = rsRemainder(dat, div)
                val block = ByteArray(shortLen + 1)
                System.arraycopy(dat, 0, block, 0, dat.size)
                // short blocks keep a placeholder byte at index len (skipped when interleaving)
                System.arraycopy(ecc, 0, block, shortLen + 1 - eccLen, eccLen)
                blocks += block
            }
            val out = ArrayList<Byte>(raw)
            for (i in 0 until shortLen + 1) for (j in blocks.indices) {
                if (i != shortLen - eccLen || j >= nShort) out += blocks[j][i]
            }
            return out.toByteArray()
        }

        private fun gfMul(x: Int, y: Int): Int {
            var z = 0
            for (i in 7 downTo 0) {
                z = (z shl 1) xor ((z ushr 7) * 0x11D)
                z = z xor (((y ushr i) and 1) * x)
            }
            return z and 0xff
        }

        private fun rsDivisor(degree: Int): IntArray {
            val r = IntArray(degree); r[degree - 1] = 1
            var root = 1
            repeat(degree) {
                for (j in r.indices) {
                    r[j] = gfMul(r[j], root)
                    if (j + 1 < r.size) r[j] = r[j] xor r[j + 1]
                }
                root = gfMul(root, 2)
            }
            return r
        }

        private fun rsRemainder(data: ByteArray, div: IntArray): ByteArray {
            val r = IntArray(div.size)
            for (b in data) {
                val f = (b.toInt() and 0xff) xor r[0]
                System.arraycopy(r, 1, r, 0, r.size - 1); r[r.size - 1] = 0
                for (i in r.indices) r[i] = r[i] xor gfMul(div[i], f)
            }
            return ByteArray(r.size) { r[it].toByte() }
        }

        private fun drawFormat(m: Array<BooleanArray>, fn: Array<BooleanArray>, ecl: Ecl, mask: Int) {
            val size = m.size
            val data = (ecl.formatBits shl 3) or mask
            var rem = data
            repeat(10) { rem = (rem shl 1) xor ((rem ushr 9) * 0x537) }
            val bits = ((data shl 10) or rem) xor 0x5412
            fun bit(i: Int) = (bits ushr i) and 1 != 0
            fun set(x: Int, y: Int, d: Boolean) { m[y][x] = d; fn[y][x] = true }
            for (i in 0..5) set(8, i, bit(i))
            set(8, 7, bit(6)); set(8, 8, bit(7)); set(7, 8, bit(8))
            for (i in 9 until 15) set(14 - i, 8, bit(i))
            for (i in 0 until 8) set(size - 1 - i, 8, bit(i))
            for (i in 8 until 15) set(8, size - 15 + i, bit(i))
            set(8, size - 8, true)
        }

        private fun applyMask(m: Array<BooleanArray>, fn: Array<BooleanArray>, mask: Int) {
            for (y in m.indices) for (x in m.indices) {
                val inv = when (mask) {
                    0 -> (x + y) % 2 == 0
                    1 -> y % 2 == 0
                    2 -> x % 3 == 0
                    3 -> (x + y) % 3 == 0
                    4 -> (x / 3 + y / 2) % 2 == 0
                    5 -> x * y % 2 + x * y % 3 == 0
                    6 -> (x * y % 2 + x * y % 3) % 2 == 0
                    else -> ((x + y) % 2 + x * y % 3) % 2 == 0
                }
                if (inv && !fn[y][x]) m[y][x] = !m[y][x]
            }
        }

        private val FINDER_A = booleanArrayOf(true, false, true, true, true, false, true, false, false, false, false)
        private val FINDER_B = booleanArrayOf(false, false, false, false, true, false, true, true, true, false, true)

        /** ISO penalty rules N1..N4 (runs, 2x2 blocks, finder-like patterns, dark/light balance). */
        private fun penalty(m: Array<BooleanArray>): Int {
            val n = m.size
            var p = 0
            fun line(get: (Int) -> Boolean) {
                var run = 1
                for (i in 1 until n) {
                    if (get(i) == get(i - 1)) run++ else { if (run >= 5) p += 3 + run - 5; run = 1 }
                }
                if (run >= 5) p += 3 + run - 5
                for (i in 0..n - 11) {
                    if ((0 until 11).all { get(i + it) == FINDER_A[it] } || (0 until 11).all { get(i + it) == FINDER_B[it] }) p += 40
                }
            }
            for (y in 0 until n) line { m[y][it] }
            for (x in 0 until n) line { m[it][x] }
            for (y in 0 until n - 1) for (x in 0 until n - 1) {
                val c = m[y][x]
                if (c == m[y][x + 1] && c == m[y + 1][x] && c == m[y + 1][x + 1]) p += 3
            }
            val dark = m.sumOf { r -> r.count { it } }
            val total = n * n
            p += (Math.abs(dark * 20 - total * 10) + total - 1) / total * 10 - 10
            return maxOf(p, 0)
        }
    }

    private class BitBuf {
        private val bits = ArrayList<Boolean>()
        val size get() = bits.size
        operator fun get(i: Int) = if (bits[i]) 1 else 0
        fun append(v: Int, n: Int) { for (i in n - 1 downTo 0) bits += (v ushr i) and 1 != 0 }
    }
}
