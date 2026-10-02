package castbridge.core.xfer

import java.security.MessageDigest
import java.util.BitSet

/**
 * What a transfer is made of: a file of [size] bytes cut into [blockSize]-byte blocks (the last one is shorter).
 * A block is the unit of work, of verification (SHA-256) and of resume. Offsets are always [Long] (files over 4 GiB).
 * Slow lanes cut a block further into [SLICE] slices (see [PartAssembler]).
 */
data class Manifest(val name: String, val size: Long, val blockSize: Int) {
    init { require(size >= 0 && blockSize >= SLICE && blockSize % SLICE == 0) { "bad manifest" } }

    val blocks: Int get() = ((size + blockSize - 1) / blockSize).toInt()
    fun offset(idx: Int): Long = idx.toLong() * blockSize
    fun length(idx: Int): Int { require(idx in 0 until blocks); return minOf(blockSize.toLong(), size - offset(idx)).toInt() }
    fun slices(idx: Int): Int = (length(idx) + SLICE - 1) / SLICE
    fun sliceLength(idx: Int, k: Int): Int = minOf(SLICE, length(idx) - k * SLICE)

    /** Same name, size and block size = same transfer: lets a restarted phone find the TV's partial copy again. No secret in it. */
    val id: String get() = Hash.hex(Hash.sha256("$name\u0000$size\u0000$blockSize".toByteArray())).take(24)

    companion object {
        const val SLICE = 256 * 1024
        const val MIN_BLOCK = 1 shl 20
        const val MAX_BLOCK = 8 shl 20

        /** Block size by file size (1 MiB for small files, up to 8 MiB for big ones: fewer requests, still fine-grained resume). */
        fun blockSizeFor(size: Long): Int = when {
            size <= 64L shl 20 -> MIN_BLOCK
            size <= 1L shl 30 -> 4 shl 20
            else -> MAX_BLOCK
        }
        fun of(name: String, size: Long) = Manifest(name, size, blockSizeFor(size))

        /** The end-to-end fingerprint of the file: SHA-256 of the block hashes (hex, in order). Equals SHA-256("") for an empty file. */
        fun root(blockHashes: List<String>): String {
            val md = MessageDigest.getInstance("SHA-256")
            blockHashes.forEach { md.update(it.toByteArray()) }
            return Hash.hex(md.digest())
        }
    }
}

object Hash {
    fun sha256(b: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(b)
    fun hex(b: ByteArray): String { val c = "0123456789abcdef"; val s = StringBuilder(b.size * 2); for (x in b) { s.append(c[(x.toInt() shr 4) and 15]).append(c[x.toInt() and 15]) }; return s.toString() }
    fun isHex64(s: String?) = s != null && s.length == 64 && s.all { it in '0'..'9' || it in 'a'..'f' }
}

/** Which blocks are done. Thread-safe; carried over the wire as hex (bit i = block i). */
class BlockMap(val blocks: Int) {
    private val bits = BitSet(blocks)
    @Synchronized fun has(i: Int) = bits.get(i)
    @Synchronized fun set(i: Int): Boolean { val was = bits.get(i); bits.set(i); return !was }
    @Synchronized fun clear(i: Int) = bits.clear(i)
    @Synchronized fun count() = bits.cardinality()
    @Synchronized fun complete() = bits.cardinality() == blocks
    @Synchronized fun missing(): List<Int> = (0 until blocks).filter { !bits.get(it) }
    /** Blocks done from block 0 without a hole: what a reader of the growing file may read. */
    @Synchronized fun leading(): Int = bits.nextClearBit(0).coerceAtMost(blocks)

    @Synchronized fun toHex(): String {
        val bytes = ByteArray((blocks + 7) / 8)
        for (i in 0 until blocks) if (bits.get(i)) bytes[i / 8] = (bytes[i / 8].toInt() or (1 shl (i % 8))).toByte()
        return Hash.hex(bytes)
    }

    companion object {
        fun fromHex(blocks: Int, hex: String): BlockMap {
            val m = BlockMap(blocks)
            if (hex.length != ((blocks + 7) / 8) * 2) return m
            for (i in 0 until blocks) {
                val b = hex.substring(i / 8 * 2, i / 8 * 2 + 2).toIntOrNull(16) ?: return BlockMap(blocks)
                if (b and (1 shl (i % 8)) != 0) m.set(i)
            }
            return m
        }
    }
}

/** Compression is only worth it for text and uncompressed archives; media and packed files are already compressed. */
object Compression {
    private val packed = setOf("mp4", "mkv", "avi", "mov", "webm", "ts", "m4v", "mpg", "mpeg", "3gp", "flv", "wmv",
        "mp3", "m4a", "aac", "ogg", "opus", "flac", "wav", "wma",
        "jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "avif",
        "zip", "gz", "tgz", "bz2", "xz", "7z", "rar", "zst", "apk", "jar", "pdf", "docx", "xlsx", "pptx", "epub", "iso")
    private val compressible = setOf("txt", "srt", "vtt", "ass", "ssa", "csv", "json", "xml", "html", "htm", "log", "md", "tar", "sql", "js", "css", "yaml", "yml", "ini", "bmp")

    fun ext(name: String) = name.substringAfterLast('.', "").lowercase()
    /** Never for packed media; yes for the text/archive list; unknown extensions are sent as they are. */
    fun worthTrying(name: String): Boolean = ext(name).let { it !in packed && it in compressible }
}
