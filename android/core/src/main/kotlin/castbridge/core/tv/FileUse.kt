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
    private class E { @Volatile var open = 0; @Volatile var last = 0L }
    private val map = ConcurrentHashMap<String, E>()

    /** Wraps [inner]: counts it as a reader of [name] until it is closed. */
    fun track(name: String, inner: InputStream): InputStream {
        val e = map.getOrPut(name) { E() }
        synchronized(e) { e.open++; e.last = now() }
        return object : InputStream() {
            private var closed = false
            override fun read(): Int { e.last = now(); return inner.read() }
            override fun read(b: ByteArray, off: Int, len: Int): Int { e.last = now(); return inner.read(b, off, len) }
            override fun available(): Int = inner.available()
            override fun skip(n: Long): Long = inner.skip(n)
            override fun close() {
                try { inner.close() } finally { synchronized(e) { if (!closed) { closed = true; e.open-- } } }
            }
        }
    }

    fun busy(name: String): Boolean = map[name]?.let { it.open > 0 && now() - it.last < idleMs } == true
}

/** One lock for "who owns this name" decisions (rename target, restore target): the check and the rename must not interleave. */
object NameSpace { val lock = Any() }
