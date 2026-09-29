package castbridge.core.tv

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile

/**
 * "Play while uploading": the TV serves a file that is still arriving. Sidecar `<name>.meta` holds the
 * total size (written by the first PUT), so a reader knows the final Content-Length before the last
 * byte is there.
 */
object Meta {
    const val SUFFIX = ".meta"
    fun file(dir: File, name: String) = File(dir, name + SUFFIX)
    fun write(dir: File, name: String, total: Long) {
        val f = file(dir, name)
        if (read(dir, name) != total) f.writeText(total.toString())
    }
    fun read(dir: File, name: String): Long? = runCatching { file(dir, name).readText().trim().toLong() }.getOrNull()
    fun delete(dir: File, name: String) { file(dir, name).delete() }
}

/**
 * Sequential reader over bytes [start, end] of a file that may still be growing.
 *
 * - The file is re-opened **by name for every block** (<= [BLOCK] bytes): when the upload finishes and
 *   `<name>.part` is renamed to `<name>`, reading carries on without a stale descriptor.
 * - A byte that has not arrived yet makes [read] **block** (polling every [pollMs]) until it does; after
 *   [waitMs] without progress it throws [IOException], which cuts the HTTP connection cleanly (the
 *   player reconnects with a Range request and waits again).
 * - Nothing is buffered in memory beyond the caller's array.
 */
class GrowingStream(
    private val dir: File,
    private val name: String,
    start: Long,
    private val end: Long,
    private val waitMs: Long = 30_000,
    private val pollMs: Long = 50,
    private val sleep: (Long) -> Unit = Thread::sleep,
    private val clock: () -> Long = System::currentTimeMillis,
) : InputStream() {
    private var pos = start
    private var closed = false

    override fun read(): Int {
        val b = ByteArray(1)
        return if (read(b, 0, 1) < 0) -1 else b[0].toInt() and 0xff
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (closed) throw IOException("closed")
        if (len == 0) return 0
        if (pos > end) return -1
        val want = minOf(len.toLong(), BLOCK.toLong(), end - pos + 1).toInt()
        var deadline = clock() + waitMs
        var missingSince = -1L
        while (true) {
            if (closed) throw IOException("closed")
            val fin = File(dir, name)
            val src = if (fin.isFile) fin else File(dir, "$name.part").takeIf { it.isFile }
            if (src == null) {
                // Between the two lookups the upload may have been renamed: tolerate a brief absence only.
                if (missingSince < 0) missingSince = clock()
                if (clock() - missingSince > 500) throw IOException("file removed while streaming")
                sleep(20); continue
            }
            missingSince = -1
            val avail = src.length()
            if (avail > pos) {
                try {
                    RandomAccessFile(src, "r").use { f ->
                        f.seek(pos)
                        val n = f.read(b, off, minOf(want.toLong(), avail - pos).toInt())
                        if (n > 0) { pos += n; return n }
                    }
                } catch (e: java.io.FileNotFoundException) { continue }   // renamed just now: look again by name
            }
            if (clock() >= deadline) throw IOException("timeout waiting for upload at byte $pos")
            sleep(pollMs)
        }
    }

    override fun close() { closed = true }

    companion object { const val BLOCK = 64 * 1024 }
}

/** Thresholds and limits for progressive playback (pure functions). */
object Progressive {
    const val MIN_BOOTSTRAP = 2L shl 20

    /** Bytes that must be on the TV before playback may start: max(2 MiB, ~3 s of the current upload rate), never more than the file. */
    fun bootstrapBytes(total: Long, uploadBytesPerSec: Long): Long =
        minOf(total, maxOf(MIN_BOOTSTRAP, uploadBytesPerSec * 3))

    /** Furthest position (ms) that can be shown so far, from the fraction of the file received, minus a safety margin. */
    fun reachableMs(durMs: Long, received: Long, total: Long, marginMs: Long = 3000): Long {
        if (total <= 0 || durMs <= 0) return 0
        if (received >= total) return durMs
        return (durMs * received / total - marginMs).coerceAtLeast(0)
    }

    /** True if the average video bitrate exceeds the upload rate: the player will end up waiting for data. */
    fun willStall(fileBytes: Long, durMs: Long, uploadBytesPerSec: Long): Boolean =
        durMs > 0 && uploadBytesPerSec > 0 && fileBytes.toDouble() / (durMs / 1000.0) > uploadBytesPerSec
}

/** Byte-rate estimator for one upload (reset after a pause in the data). */
class RateMeter(private val now: () -> Long = System::nanoTime) {
    private var since = 0L
    private var last = 0L
    private var bytes = 0L

    @Synchronized fun add(n: Long) {
        val t = now()
        if (since == 0L || t - last > 10_000_000_000L) { since = t; bytes = 0 }
        last = t; bytes += n
    }

    @Synchronized fun bytesPerSec(): Long {
        val dt = last - since
        return if (dt < 200_000_000L) 0 else (bytes * 1_000_000_000L / dt)
    }
}

/**
 * Can an MP4/MOV be played before it is complete? Only if the `moov` atom (the index) comes before the
 * `mdat` payload ("faststart"). Reads a few atom headers, never the whole file.
 */
object Mp4Atoms {
    enum class Layout { FASTSTART, MOOV_AT_END, NOT_ISO, UNKNOWN }

    /** [readAt] returns up to `len` bytes at `offset` (fewer at end of file, empty past it). */
    fun layout(total: Long, readAt: (offset: Long, len: Int) -> ByteArray): Layout {
        var pos = 0L
        var sawFtyp = false
        repeat(64) {
            if (pos + 8 > total) return if (sawFtyp) Layout.UNKNOWN else Layout.NOT_ISO
            val h = readAt(pos, 16)
            if (h.size < 8) return if (sawFtyp) Layout.UNKNOWN else Layout.NOT_ISO
            var size = u32(h, 0)
            val type = String(h, 4, 4, Charsets.ISO_8859_1)
            if (!sawFtyp && type != "ftyp" && type != "moov" && type != "mdat" && type != "free" && type != "wide" && type != "skip")
                return Layout.NOT_ISO
            var header = 8
            if (size == 1L) { if (h.size < 16) return Layout.UNKNOWN; size = u64(h, 8); header = 16 }
            if (size == 0L) size = total - pos                      // atom runs to the end of the file
            when (type) {
                "ftyp" -> sawFtyp = true
                "moov" -> return Layout.FASTSTART
                "mdat" -> return Layout.MOOV_AT_END                 // payload first: the index can only come after it
            }
            if (size < header) return Layout.UNKNOWN
            pos += size
        }
        return Layout.UNKNOWN
    }

    fun isIsoName(name: String) = name.substringAfterLast('.', "").lowercase() in setOf("mp4", "m4v", "mov", "3gp", "m4a")

    private fun u32(b: ByteArray, o: Int) = ((b[o].toLong() and 255) shl 24) or ((b[o + 1].toLong() and 255) shl 16) or
        ((b[o + 2].toLong() and 255) shl 8) or (b[o + 3].toLong() and 255)
    private fun u64(b: ByteArray, o: Int) = (u32(b, o) shl 32) or u32(b, o + 4)
}
