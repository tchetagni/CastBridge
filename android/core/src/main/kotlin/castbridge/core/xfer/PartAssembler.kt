package castbridge.core.xfer

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.security.MessageDigest
import java.util.BitSet
import java.util.zip.GZIPInputStream

/**
 * How fast the disk really absorbs data (time spent inside `write` only, not waiting for the network). Announced to the phone.
 * Caveat: a `write` returns when the bytes reach the page cache, not the medium, so this over-estimates a slow USB key until the cache fills
 * (then it drops): the policy ([PlaybackPriority.receiveCap]) only halves it and clamps the result, so a transient over-estimate is bounded.
 */
class WriteStats(private val now: () -> Long = System::nanoTime) {
    @Volatile private var bps = 0.0
    @Volatile private var inflight = 0L
    private val written = java.util.concurrent.atomic.AtomicLong()
    /** Cumulative bytes written by every copy (a change of it = a copy is writing). */
    fun total(): Long = written.get()
    fun record(bytes: Int, nanos: Long) {
        if (bytes <= 0) return
        written.addAndGet(bytes.toLong())
        if (nanos <= 0) return
        val sample = bytes * 1e9 / nanos
        synchronized(this) { bps = if (bps == 0.0) sample else bps * 0.85 + sample * 0.15 }
    }
    fun bytesPerSec(): Long = bps.toLong()
    /** Bytes accepted from the network and not yet on disk (a lower bound: what the TV holds in flight). */
    fun queued(): Long = inflight
    @Synchronized fun queue(delta: Long) { inflight += delta }
}

/**
 * The TV's side of one transfer: a file written in place (preallocated where that is free, see [preallocates]), block by block, in any order,
 * from several connections at once.
 * Nothing is trusted: a block counts only once its SHA-256 matches what the phone announced; [finish] reads everything back from the
 * disk and compares again before the file takes its final name. State survives a restart of the app (map + hashes in a sidecar file).
 *
 * Layout: `<dir>/.cbx/<id>.data` and `.cbx/<id>.state` (hidden folder: never listed, never counted as a file). On success the data file is
 * renamed to `<dir>/<diskName>.part` (same volume, atomic) and the caller commits it like any other partial upload.
 */
class PartAssembler private constructor(
    val dir: File, val manifest: Manifest, private val data: File, private val stateFile: File,
    val stats: WriteStats, private val now: () -> Long,
    /** Bench only ("network alone"): bytes are received, hashed and dropped, nothing touches the disk. */
    val discard: Boolean = false,
    /** Interval between two saves of the block map (spaced out while a video plays: an older map only means blocks sent again). */
    private val persistEveryMs: () -> Long = { 1000L },
) {
    sealed class Block {
        object Ok : Block()
        /** Already stored (or being stored by another connection): the body was not read. */
        object Already : Block()
        class Corrupt(val reason: String) : Block()
        class Bad(val reason: String) : Block()
        /** The network failed while reading the body: nothing is kept for this block, the phone resends it. */
        class Interrupted(val cause: IOException) : Block()
        class DiskFail(val cause: IOException) : Block()
    }
    sealed class Finish {
        class Done(val partFile: File) : Finish()
        class Missing(val blocks: List<Int>) : Finish()
        /** Blocks that did not survive the read-back (disk fault): they are forgotten, the phone resends them. */
        class Corrupt(val blocks: List<Int>) : Finish()
        object RootMismatch : Finish()
        class DiskFail(val cause: IOException) : Finish()
    }

    val map = BlockMap(manifest.blocks)
    private val hashes = arrayOfNulls<String>(manifest.blocks)
    private val busy = HashSet<Int>()
    private val sliceMap = HashMap<Int, BitSet>()
    private val sliceSha = HashMap<Int, String>()
    private val raf: RandomAccessFile? = if (discard) null else RandomAccessFile(data, "rw")
    private val ch: FileChannel? = raf?.channel
    @Volatile private var closed = false
    @Volatile var touched = now(); private set
    private var lastPersist = 0L
    /**
     * Was the data file sized at once? When NOT (FAT/exFAT/unknown), a block written far past the end of the file makes the kernel zero-fill the
     * whole gap synchronously under the inode lock (Linux 5.15 `cont_write_begin`): the TV then only takes blocks near the contiguous prefix
     * ([TransferHost.admitAhead]) and tells the phone to keep its blocks in order (`ordered` in the state).
     */
    @Volatile var preallocated: Boolean = true; private set

    private fun claim(idx: Int): Boolean = synchronized(this) { !map.has(idx) && busy.add(idx) }
    private fun release(idx: Int) = synchronized(this) { busy.remove(idx) }

    /** A whole block in one request. [wireLen] is the body length; [gzip] means the body is a gzip stream of the block. */
    fun writeBlock(idx: Int, sha: String, input: InputStream, wireLen: Long, gzip: Boolean, pace: (Int) -> Unit = {}): Block {
        if (idx !in 0 until manifest.blocks) return Block.Bad("bad block index")
        if (!Hash.isHex64(sha)) return Block.Bad("bad hash")
        val len = manifest.length(idx)
        if (!gzip && wireLen != len.toLong()) return Block.Bad("bad length")
        if (closed) return Block.Bad("transfer closed")
        if (!claim(idx)) return Block.Already
        touched = now()
        try {
            val counted = Bounded(input, wireLen)
            val src = if (gzip) try { GZIPInputStream(counted, 64 * 1024) } catch (e: IOException) { return Block.Bad("bad gzip") } else counted
            val md = MessageDigest.getInstance("SHA-256")
            val buf = ByteArray(BUF)
            var done = 0
            val pos0 = manifest.offset(idx)
            while (done < len) {
                val r = try { src.read(buf, 0, minOf(buf.size, len - done)) } catch (e: IOException) { drain(counted); return if (gzip && e is java.util.zip.ZipException) Block.Bad("bad gzip") else Block.Interrupted(e) }
                if (r < 0) { return Block.Bad("short body") }
                pace(r)
                md.update(buf, 0, r)
                stats.queue(r.toLong())
                try { writeAt(buf, r, pos0 + done) } catch (e: IOException) { stats.queue(-r.toLong()); return Block.DiskFail(e) }
                stats.queue(-r.toLong())
                done += r
            }
            if (gzip) { if (src.read() >= 0) return Block.Bad("body longer than the block"); drain(counted) }
            if (Hash.hex(md.digest()) != sha) return Block.Corrupt("hash mismatch")
            synchronized(this) { hashes[idx] = sha; map.set(idx); sliceMap.remove(idx); sliceSha.remove(idx) }
            persistSoon()
            return Block.Ok
        } finally { release(idx) }
    }

    /** One 256 KiB slice of a block (slow lanes). The block is verified from the disk when its last slice arrives. */
    fun writeSlice(idx: Int, k: Int, sha: String, input: InputStream, wireLen: Long): Block {
        if (idx !in 0 until manifest.blocks) return Block.Bad("bad block index")
        if (k !in 0 until manifest.slices(idx)) return Block.Bad("bad slice index")
        if (!Hash.isHex64(sha)) return Block.Bad("bad hash")
        val len = manifest.sliceLength(idx, k)
        if (wireLen != len.toLong()) return Block.Bad("bad length")
        if (closed) return Block.Bad("transfer closed")
        if (!claim(idx)) return Block.Already
        touched = now()
        try {
            synchronized(this) { val old = sliceSha[idx]; if (old != null && old != sha) { sliceMap.remove(idx) }; sliceSha[idx] = sha }
            val buf = ByteArray(len)
            var n = 0
            try { while (n < len) { val r = input.read(buf, n, len - n); if (r < 0) return Block.Bad("short body"); n += r } }
            catch (e: IOException) { return Block.Interrupted(e) }
            stats.queue(len.toLong())
            try { writeAt(buf, len, manifest.offset(idx) + k.toLong() * Manifest.SLICE) } catch (e: IOException) { stats.queue(-len.toLong()); return Block.DiskFail(e) }
            stats.queue(-len.toLong())
            val complete = synchronized(this) {
                val bs = sliceMap.getOrPut(idx) { BitSet() }; bs.set(k); bs.cardinality() == manifest.slices(idx)
            }
            if (!complete) return Block.Ok
            val md = MessageDigest.getInstance("SHA-256")
            try { readBlock(idx) { b, n2 -> md.update(b, 0, n2) } } catch (e: IOException) { return Block.DiskFail(e) }
            return if (Hash.hex(md.digest()) == sha) {
                synchronized(this) { hashes[idx] = sha; map.set(idx); sliceMap.remove(idx); sliceSha.remove(idx) }
                persistSoon(); Block.Ok
            } else { synchronized(this) { sliceMap.remove(idx); sliceSha.remove(idx) }; Block.Corrupt("hash mismatch") }
        } finally { release(idx) }
    }

    /** Slices of block [idx] already on disk (so that a slow lane resumes in the middle of a block), as a hex-free list. */
    fun slicesOf(idx: Int): List<Int> = synchronized(this) { sliceMap[idx]?.let { bs -> (0 until manifest.slices(idx)).filter { bs.get(it) } } ?: emptyList() }

    /** Everything is here and right: reads it all back, compares with the announced hashes, then renames to `<diskName>.part`. */
    fun finish(root: String, diskName: String, pace: (Int) -> Unit = {}): Finish {
        touched = now()
        val miss = map.missing()
        if (miss.isNotEmpty()) return Finish.Missing(miss)
        val hs = synchronized(this) { hashes.map { it ?: "" } }
        if (Manifest.root(hs) != root) return Finish.RootMismatch
        if (discard) { close(); return Finish.Done(data) }
        val bad = ArrayList<Int>()
        try {
            val md = MessageDigest.getInstance("SHA-256")
            for (i in 0 until manifest.blocks) {
                md.reset()
                // a data file shorter than the manifest (not preallocated, tail lost on a cut): those blocks are resent, it is not a disk failure
                try { readBlock(i) { b, n -> pace(n); md.update(b, 0, n) } } catch (e: ShortRead) { bad += i; continue }
                if (Hash.hex(md.digest()) != hs[i]) bad += i
            }
            if (bad.isNotEmpty()) { synchronized(this) { bad.forEach { map.clear(it); hashes[it] = null } }; persist(); return Finish.Corrupt(bad) }
            ch?.force(true)
        } catch (e: IOException) { return Finish.DiskFail(e) }
        close()
        val part = File(dir, diskName + castbridge.core.tv.Storage.PART)
        try {
            part.delete()
            if (!data.renameTo(part)) throw IOException("rename failed")
        } catch (e: IOException) { return Finish.DiskFail(e) }
        stateFile.delete()
        return Finish.Done(part)
    }

    /** Forgets everything (cancel, or an abandoned transfer swept away). */
    fun discard() { close(); if (!discard) { data.delete(); stateFile.delete() } }

    fun close() { closed = true; runCatching { persist() }; runCatching { raf?.close() } }

    fun hashesJoined(): String = synchronized(this) { hashes.joinToString(",") { it ?: "" } }

    private fun writeAt(buf: ByteArray, n: Int, pos: Long) {
        val c = ch ?: return
        val bb = ByteBuffer.wrap(buf, 0, n)
        var p = pos
        val t0 = System.nanoTime()
        while (bb.hasRemaining()) p += c.write(bb, p)
        stats.record(n, System.nanoTime() - t0)
    }

    private fun readBlock(idx: Int, sink: (ByteArray, Int) -> Unit) {
        val buf = ByteArray(BUF)
        var left = manifest.length(idx); var pos = manifest.offset(idx)
        while (left > 0) {
            val r = (ch ?: throw IOException("no data kept")).read(ByteBuffer.wrap(buf, 0, minOf(buf.size, left)), pos)
            if (r <= 0) throw ShortRead()
            sink(buf, r); left -= r; pos += r
        }
    }

    private fun persistSoon() { val t = now(); if (t - lastPersist > persistEveryMs()) { lastPersist = t; runCatching { persist() } } }

    /** The data file ends before the block does. */
    private class ShortRead : IOException("file shorter than expected")

    @Synchronized private fun persist() {
        if (discard) return
        val tmp = File(stateFile.path + ".tmp")
        tmp.writeText("CBX1\n${manifest.size}\n${manifest.blockSize}\n${map.toHex()}\n${hashes.joinToString(",") { it ?: "" }}\n")
        if (!tmp.renameTo(stateFile)) { stateFile.delete(); tmp.renameTo(stateFile) }
    }

    /** Reads at most [limit] bytes of the request body, then reports EOF (the connection stays usable for the next request). */
    private class Bounded(private val s: InputStream, var left: Long) : InputStream() {
        override fun read(): Int { if (left <= 0) return -1; val r = s.read(); if (r >= 0) left--; return r }
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (left <= 0) return -1
            val r = s.read(b, off, minOf(len.toLong(), left).toInt()); if (r > 0) left -= r; return r
        }
    }
    private fun drain(b: Bounded) { try { val t = ByteArray(8192); while (b.left > 0 && b.read(t, 0, t.size) >= 0) { } } catch (_: IOException) { } }

    companion object {
        const val BUF = 256 * 1024
        const val SUB = ".cbx"
        /**
         * Is sizing the data file at once ([RandomAccessFile.setLength]) free on [fs]? On ext4/f2fs it makes a sparse file. On FAT32/exFAT/NTFS
         * (Linux 5.15 of the reference TV: no sparse files, no exFAT valid-size) the kernel writes ZEROS over the whole size before the first block:
         * 2 GB of extra writes on the key the player reads, minutes of saturated IO. Unknown: treated as FAT (the safe side).
         */
        fun preallocates(fs: castbridge.core.tv.Fs): Boolean = fs == castbridge.core.tv.Fs.EXT4 || fs == castbridge.core.tv.Fs.F2FS

        /** Opens (resuming what a previous run left) or creates the transfer's files in [dir]. Throws [IOException] if the disk refuses. */
        fun open(dir: File, manifest: Manifest, stats: WriteStats = WriteStats(), now: () -> Long = System::currentTimeMillis, discard: Boolean = false,
                 preallocate: Boolean = true, persistEveryMs: () -> Long = { 1000L }): PartAssembler {
            if (discard) return PartAssembler(dir, manifest, File(dir, ".discard"), File(dir, ".discard"), stats, now, discard = true)
            val sub = File(dir, SUB).apply { mkdirs() }
            val data = File(sub, manifest.id + ".data"); val st = File(sub, manifest.id + ".state")
            // without preallocation the data file grows as blocks land (it may be shorter than the manifest until the last one)
            val reuse = data.isFile && st.isFile && (data.length() == manifest.size || (!preallocate && data.length() < manifest.size))
            if (!reuse) { data.delete(); st.delete() }
            val a = PartAssembler(dir, manifest, data, st, stats, now, persistEveryMs = persistEveryMs)
            a.preallocated = preallocate
            if (reuse) a.restore() else { if (preallocate) a.raf!!.setLength(manifest.size); a.persist() }
            return a
        }

        /** Removes the transfer files untouched for [maxAgeMs] (orphans of a phone that never came back); returns how many. */
        fun sweep(dir: File, maxAgeMs: Long, now: Long = System.currentTimeMillis(), keep: Set<String> = emptySet()): Int {
            val sub = File(dir, SUB)
            var n = 0
            for (f in sub.listFiles().orEmpty()) {
                if (f.name.substringBefore('.') in keep) continue
                if (now - f.lastModified() > maxAgeMs && f.delete()) n++
            }
            if (sub.list().isNullOrEmpty()) sub.delete()
            return n
        }
    }

    private fun restore() {
        val l = runCatching { stateFile.readLines() }.getOrNull() ?: return
        if (l.size < 4 || l[0] != "CBX1" || l[1].toLongOrNull() != manifest.size || l[2].toIntOrNull() != manifest.blockSize) return
        val m = BlockMap.fromHex(manifest.blocks, l[3])
        val hs = if (l.size > 4) l[4].split(',') else emptyList()
        for (i in 0 until manifest.blocks) if (m.has(i)) { val h = hs.getOrNull(i); if (Hash.isHex64(h)) { hashes[i] = h; map.set(i) } }
    }
}
