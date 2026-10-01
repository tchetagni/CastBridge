package castbridge.core.xfer

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.WritableByteChannel
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong

/** The file being sent, positioned reads only (so every lane can read at once). The phone wraps the content Uri's descriptor in a [FileBlockSource]. */
interface BlockSource {
    val size: Long
    fun read(pos: Long, buf: ByteArray, off: Int, len: Int): Int
    /** Zero-copy to a socket where the platform can ([FileChannel.transferTo]); returns the bytes moved (> 0 unless at the end). */
    fun transferTo(pos: Long, count: Long, out: WritableByteChannel): Long {
        val b = ByteArray(minOf(count, 256L * 1024).toInt()); val n = read(pos, b, 0, b.size)
        if (n <= 0) return n.toLong()
        val bb = ByteBuffer.wrap(b, 0, n); while (bb.hasRemaining()) out.write(bb); return n.toLong()
    }
}

class FileBlockSource(private val ch: FileChannel, override val size: Long = ch.size()) : BlockSource {
    override fun read(pos: Long, buf: ByteArray, off: Int, len: Int): Int = ch.read(ByteBuffer.wrap(buf, off, len), pos)
    override fun transferTo(pos: Long, count: Long, out: WritableByteChannel): Long = ch.transferTo(pos, count, out)
}

/** SHA-256 of each block, computed once (the first lane that needs it) and shared; hashes a TV already holds are preloaded. */
class HashBook(private val manifest: Manifest, private val source: BlockSource) {
    private val h = arrayOfNulls<String>(manifest.blocks)
    fun preload(idx: Int, hash: String) { synchronized(h) { if (h[idx] == null && Hash.isHex64(hash)) h[idx] = hash } }
    fun get(idx: Int): String {
        synchronized(h) { h[idx]?.let { return it } }
        val md = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(256 * 1024)
        var pos = manifest.offset(idx); var left = manifest.length(idx)
        while (left > 0) {
            val r = source.read(pos, buf, 0, minOf(buf.size, left))
            if (r <= 0) throw IOException("source ended early")
            md.update(buf, 0, r); pos += r; left -= r
        }
        val hex = Hash.hex(md.digest())
        synchronized(h) { h[idx] = hex }
        return hex
    }
    fun all(): List<String> = (0 until manifest.blocks).map { get(it) }
}

class SendContext(val manifest: Manifest, val source: BlockSource, val hashes: HashBook, val cancelled: () -> Boolean, val compress: Boolean)

sealed class Outcome {
    class Ok(val bytes: Long) : Outcome()
    /** The TV already had the block (another lane was faster). */
    object Already : Outcome()
    /** The TV's hash check failed: the block is sent again. */
    class Corrupt(val reason: String) : Outcome()
    /** The TV's disk is behind: slow down, retry later. */
    class Busy(val retryMs: Long) : Outcome()
    /** The TV no longer knows the transfer (restart, sweep): begin again. */
    object SessionLost : Outcome()
    /** Link problem: retry elsewhere; [fatal] = nothing will fix it (bad credential, no space). */
    class Failed(val reason: String, val fatal: Boolean = false) : Outcome()
    /** The block was finished by another lane while this one was sending: not an error. */
    object Cancelled : Outcome()
}

/** One way to the TV (Wi-Fi, Bluetooth...). [send] runs on a worker thread of the scheduler and may block; at most [allowedWorkers] run at once. */
interface Lane {
    val id: String
    /** Slow lanes take work from the end of the queue and never duplicate blocks. */
    val slow: Boolean get() = false
    val maxWorkers: Int
    fun allowedWorkers(): Int = maxWorkers
    fun send(worker: Int, idx: Int, ctx: SendContext): Outcome
    fun close() {}
    /** Bytes confirmed through this lane, for the fairness report. */
    val sent: AtomicLong
}

/** Wi-Fi Direct: experimental, off by default (see docs/TRANSFER.md). */
object LaneSwitches { @Volatile var wifiDirect = false }

/** The phone cabled to the TV over USB-C: no implementation (nothing is simulated). The research notes are in docs/TRANSFER.md. */
interface UsbLane : Lane {
    companion object { const val IMPLEMENTED = false }
}
