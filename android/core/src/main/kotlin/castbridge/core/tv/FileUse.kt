package castbridge.core.tv

import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Who is reading a stored file right now over `/stream/` (a phone playing or casting it, the assistant fingerprinting it):
 * renaming, moving or binning a file under a reader would break the reader (a player reopens the file by name for every Range
 * request). A reader counts as "using" the file while its response is open AND it read something in the last [idleMs]
 * (a player that vanished without closing its socket must not lock a file for ever).
 */
class StreamUse(private val now: () -> Long = System::currentTimeMillis, private val idleMs: Long = 60_000) {
    private class E { @Volatile var open = 0; @Volatile var last = 0L; val readers = ArrayList<InputStream>() }
    private val map = ConcurrentHashMap<String, E>()

    /**
     * Wraps [inner]: counts it as a reader of [name] until it is closed. With more than [maxOpen] readers open, the OLDEST ones are closed (a
     * player that jumps leaves its previous connection behind, waiting for bytes nobody will read: that thread must not stay for minutes).
     */
    fun track(name: String, inner: InputStream, maxOpen: Int = Int.MAX_VALUE): InputStream {
        val e = map.getOrPut(name) { E() }
        val w = object : InputStream() {
            private var closed = false
            override fun read(): Int { e.last = now(); return inner.read() }
            override fun read(b: ByteArray, off: Int, len: Int): Int { e.last = now(); return inner.read(b, off, len) }
            override fun available(): Int = inner.available()
            override fun skip(n: Long): Long = inner.skip(n)
            override fun close() {
                try { inner.close() } finally { synchronized(e) { if (!closed) { closed = true; e.open--; e.readers.remove(this) } } }
            }
        }
        val excess = synchronized(e) {
            e.open++; e.last = now(); e.readers += w
            if (e.readers.size > maxOpen) e.readers.take(e.readers.size - maxOpen) else emptyList()
        }
        excess.forEach { runCatching { it.close() } }
        return w
    }

    /** Readers of [name] open right now. */
    fun openCount(name: String): Int = map[name]?.open ?: 0

    fun busy(name: String): Boolean = map[name]?.let { it.open > 0 && now() - it.last < idleMs } == true

    /** Any reader of any file is active (a phone playing, a TV → phone download): the background content index waits (R-12). */
    fun anyBusy(): Boolean = map.values.any { it.open > 0 && now() - it.last < idleMs }
}

/** One lock for "who owns this name" decisions (rename target, restore target): the check and the rename must not interleave. */
object NameSpace { val lock = Any() }
