package castbridge.core.tv

import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * "Modest TV" profile: every tunable that trades memory / storage for comfort, in one place.
 * Defaults target a 32-bit ARMv7 box with little RAM and little flash.
 */
data class TvProfile(
    /** Never let the device drop below this much free space because of us. */
    val minFreeBytes: Long = 100L shl 20,
    /** Explicit quota for the videos folder; 0 = automatic (see [Storage.quota]). */
    val quotaBytes: Long = 0,
    /** Automatic quota: this fraction of (used + free)... */
    val quotaFraction: Double = 0.5,
    /** ...but never more than this. */
    val quotaCapBytes: Long = 8L shl 30,
    /** Buffer for every file copy (HTTP upload, Bluetooth, USB import). Streaming only, nothing is held in RAM. */
    val ioBufferBytes: Int = 64 * 1024,
    /** Concurrent HTTP connections served (each keep-alive connection holds a thread). */
    val maxHttpThreads: Int = 8,
    /** /api/info recomputes its file listing at most this often (polled every second by clients). */
    val infoCacheMs: Long = 1000,
    /** Orphan ".part" files older than this are deleted at start-up. */
    val orphanPartMaxAgeMs: Long = 24L * 3600_000,
    /** Delete a file once it has played to the end. */
    val deleteAfterPlay: Boolean = false,
    /** When the quota is full, delete the oldest already-played files (never the one playing) to make room. */
    val evictPlayed: Boolean = false,
)

/** Storage accounting for the videos folder. Pure file logic. */
object Storage {
    const val PART = ".part"
    private const val PLAYED = ".played"

    fun files(dir: File): List<File> = dir.listFiles().orEmpty()
        .filter { it.isFile && !it.name.endsWith(PART) && !it.name.endsWith(Meta.SUFFIX) && !it.name.startsWith(".") }

    /** Bytes used by finished files and partial uploads. */
    fun used(dir: File): Long = dir.listFiles().orEmpty().filter { it.isFile && !it.name.startsWith(".") }.sumOf { it.length() }

    /** Effective quota: explicit, else min(fraction of what we and the free space add up to, cap). */
    fun quota(dir: File, p: TvProfile, used: Long = used(dir)): Long =
        if (p.quotaBytes > 0) p.quotaBytes
        else minOf(((used + dir.usableSpace) * p.quotaFraction).toLong(), p.quotaCapBytes)

    /** Null if [incoming] more bytes fit, else why not. */
    fun refusal(dir: File, p: TvProfile, incoming: Long): String? {
        val free = dir.usableSpace
        if (free - incoming < p.minFreeBytes) return "not enough space"
        val used = used(dir)
        if (used + incoming > quota(dir, p, used)) return "quota exceeded"
        return null
    }

    /** Deletes partial uploads (and size sidecars without any data file) untouched for [maxAgeMs]; returns how many were removed. */
    fun cleanOrphans(dir: File, maxAgeMs: Long, now: Long = System.currentTimeMillis()): Int {
        // Partial uploads first: a size sidecar is an orphan once its .part is gone.
        val all = dir.listFiles().orEmpty().filter { it.isFile }.sortedBy { !it.name.endsWith(PART) }
        var n = 0
        for (f in all) {
            if (now - f.lastModified() <= maxAgeMs) continue
            val orphan = when {
                f.name.endsWith(PART) -> true
                f.name.endsWith(Meta.SUFFIX) -> {
                    val base = f.name.removeSuffix(Meta.SUFFIX)
                    !File(dir, base).exists() && !File(dir, base + PART).exists()
                }
                else -> false
            }
            if (orphan && f.delete()) n++
        }
        return n
    }

    // ---- "played" marks (persisted next to the videos, hidden from listings) ----

    fun playedNames(dir: File): Set<String> = runCatching { File(dir, PLAYED).readLines().filter { it.isNotEmpty() }.toSet() }.getOrDefault(emptySet())

    fun markPlayed(dir: File, name: String) {
        val set = playedNames(dir)
        if (name !in set) runCatching { File(dir, PLAYED).writeText((set + name).joinToString("\n") + "\n") }
    }

    fun forget(dir: File, name: String) {
        val set = playedNames(dir)
        if (name in set) runCatching { File(dir, PLAYED).writeText((set - name).joinToString("\n") + "\n") }
    }

    /**
     * Oldest already-played finished files (never [protect]) whose deletion would free at least [needed]
     * bytes; null if even all of them are not enough.
     */
    fun evictionPlan(dir: File, needed: Long, protect: String?): List<File>? {
        val played = playedNames(dir)
        val candidates = files(dir).filter { it.name in played && it.name != protect }.sortedBy { it.lastModified() }
        val plan = ArrayList<File>(); var freed = 0L
        for (f in candidates) { if (freed >= needed) break; plan += f; freed += f.length() }
        return plan.takeIf { freed >= needed }
    }
}

/** Bounded HTTP worker pool for NanoHTTPD (its default spawns an unbounded thread per connection). */
class BoundedRunner(private val maxThreads: Int, queue: Int = 32) : NanoHTTPD.AsyncRunner {
    private val open = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<NanoHTTPD.ClientHandler, Boolean>())
    private val pool = ThreadPoolExecutor(maxThreads, maxThreads, 30, TimeUnit.SECONDS, LinkedBlockingQueue(queue)) { r ->
        Thread(r, "cb-http").apply { isDaemon = true }
    }.apply { allowCoreThreadTimeOut(true) }

    override fun closeAll() { open.toList().forEach { it.close() }; pool.shutdownNow() }
    override fun closed(clientHandler: NanoHTTPD.ClientHandler) { open.remove(clientHandler) }
    override fun exec(clientHandler: NanoHTTPD.ClientHandler) {
        open.add(clientHandler)
        try { pool.execute(clientHandler) } catch (e: RejectedExecutionException) { open.remove(clientHandler); clientHandler.close() }
    }
}

/** RFC 7233 single-range parsing for file servers (206 / 416 decisions). */
object HttpRange {
    sealed class R {
        object Full : R()
        data class Part(val start: Long, val end: Long) : R() { val length get() = end - start + 1 }
        object Unsatisfiable : R()
    }

    /** Only the first range of a multi-range request is honoured (allowed by the RFC). An unparsable header is ignored. */
    fun parse(header: String?, total: Long): R {
        val m = header?.let { Regex("^\\s*bytes\\s*=\\s*(\\d*)\\s*-\\s*(\\d*)").find(it) } ?: return R.Full
        val s = m.groupValues[1]; val e = m.groupValues[2]
        if (s.isEmpty() && e.isEmpty()) return R.Full
        if (total <= 0) return R.Unsatisfiable
        val start: Long; var end = total - 1
        if (s.isEmpty()) {                                   // suffix: the last N bytes
            val n = e.toLongOrNull() ?: return R.Full
            if (n == 0L) return R.Unsatisfiable
            start = maxOf(0, total - n)
        } else {
            start = s.toLongOrNull() ?: return R.Full
            if (e.isNotEmpty()) end = minOf(e.toLongOrNull() ?: return R.Full, total - 1)
        }
        return if (start >= total || start > end) R.Unsatisfiable else R.Part(start, end)
    }
}
