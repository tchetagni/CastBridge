package castbridge.core.tv

import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

/** SHA-256 of a stream, read in [chunk]-byte blocks (never the whole file in memory). Shared by the phone (before a copy) and the TV (its index). */
object ContentHash {
    /** 64 lowercase hex characters. */
    private val HEX64 = Regex("^[0-9a-f]{64}$")
    fun valid(sha: String?): Boolean = sha != null && HEX64.matches(sha)

    /**
     * The SHA-256 of [input] (closed by the caller), [total] bytes expected (0 = unknown, only for the progress). [onProgress] gets (read, total) at most
     * every [chunk]; [cancelled] is asked between blocks: null = cancelled (nothing is decided on a partial hash).
     */
    fun sha256(input: InputStream, total: Long = 0, chunk: Int = 1 shl 20, cancelled: () -> Boolean = { false }, onProgress: (Long, Long) -> Unit = { _, _ -> }): String? {
        val md = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(chunk)
        var read = 0L
        while (true) {
            if (cancelled()) return null
            val n = input.read(buf)
            if (n < 0) break
            md.update(buf, 0, n); read += n
            onProgress(read, total)
        }
        if (total > 0 && read != total) return null          // the file changed under us: no hash rather than a wrong one
        return hex(md.digest())
    }

    fun hex(b: ByteArray): String = b.joinToString("") { "%02x".format(it) }
}

/** One finished file the TV holds: [dir] = its volume folder, [rel] = path inside it ("Films/X.mkv"), [name] = the TV's key name, [folder] = its category folder. */
data class HeldFile(val volumeId: String, val dir: File, val rel: String, val name: String, val folder: String = "")

/**
 * « Déjà sur la TV ? » by CONTENT (docs/agent-reports/copy-dedup.md): size + SHA-256 of every finished file the TV holds, computed LAZILY in the background.
 *
 * Rules that never bend (R-06 / R-11, docs/agent-reports/fluid-playback-during-copy.md):
 *  - nothing is hashed while [idle] is false (a video plays or buffers, a copy or a move runs, a reception is live): the read in progress PAUSES (its digest is kept)
 *    and goes on only once the TV is idle again, and only if the file did not change meanwhile;
 *  - reads are paced to [bytesPerSec] in [chunk] blocks, on ONE daemon thread at the lowest priority ([startWorker]), never on a request thread;
 *  - a hash is valid for (path, size, mtime): a file that changed (or vanished) is simply unknown again — never « present » on an old hash;
 *  - partial copies are never held files ([held] lists finished files only), so they never count as present;
 *  - the cache lives on each volume (`.cbhash` in the volume folder, written atomically): it survives a reboot and travels with a USB key; a missing or damaged cache is rebuilt.
 * A query never hashes: what is unknown answers « indexing » and is moved to the front of the queue.
 */
class ContentIndex(
    private val held: () -> List<HeldFile>,
    private val idle: () -> Boolean,
    private val bytesPerSec: Long = DEFAULT_BPS,
    private val clock: () -> Long = System::currentTimeMillis,
    private val sleep: (Long) -> Unit = Thread::sleep,
    private val chunk: Int = 1 shl 20,
    private val maxEntries: Int = 100_000,
) {
    data class Entry(val size: Long, val mtime: Long, val sha256: String)

    sealed interface Answer {
        /** No finished file of that size (or none of that content, every candidate being hashed). */
        object Absent : Answer
        /** Size-only question: [count] finished files of that size; [indexing] = some of them are not hashed yet. Names are never given here. */
        data class Candidates(val count: Int, val indexing: Boolean) : Answer
        /** Same size, but [pending] candidates are not hashed yet: the answer is not known (the phone copies, never blocked). */
        data class Indexing(val pending: Int) : Answer
        /** A finished file of exactly that size and SHA-256 (hash computed from its bytes on the disk, still valid for its size and mtime). */
        data class Present(val file: HeldFile, val size: Long, val sha256: String) : Answer
    }

    private class VolumeCache(val dir: File) {
        val map = LinkedHashMap<String, Pair<String, Entry>>()   // lowercase rel -> (rel, entry)
        var dirty = false
        var loaded = false
    }

    private val caches = HashMap<String, VolumeCache>()
    private val urgent = LinkedHashSet<String>()                  // keys "dir|rel" asked about by a phone
    private val queue = ArrayDeque<HeldFile>()
    private val pacer = castbridge.core.xfer.RatePacer({ clock() * 1_000_000 }, sleep)
    private val wake = Object()
    @Volatile private var stopped = false
    @Volatile private var worker: Thread? = null
    private var lastSave = 0L
    /** Files hashed since start (tests, diagnostics). */
    @Volatile var hashedCount = 0; private set

    private fun key(f: HeldFile) = f.dir.path + "|" + f.rel.lowercase()
    private fun fileOf(f: HeldFile) = File(f.dir, f.rel)

    @Synchronized private fun cache(dir: File): VolumeCache {
        val c = caches.getOrPut(dir.path) { VolumeCache(dir) }
        if (!c.loaded) { c.loaded = true; load(c) }
        return c
    }

    /** The valid hash of [f] (same size and mtime as on the disk now), else null. */
    fun known(f: HeldFile): Entry? {
        val file = fileOf(f)
        if (!file.isFile) return null
        val e = synchronized(this) { cache(f.dir).map[f.rel.lowercase()]?.second } ?: return null
        return e.takeIf { it.size == file.length() && it.mtime == file.lastModified() }
    }

    /**
     * Does the TV hold a finished file of [size] bytes (and, with [sha256], of that very content)? Never reads a file (stat only); what is not known yet
     * is put at the front of the background queue.
     */
    fun query(size: Long, sha256: String?): Answer {
        if (size <= 0) return Answer.Absent
        val sha = sha256?.lowercase()
        val cands = held().filter { f -> fileOf(f).let { it.isFile && it.length() == size } }
        if (cands.isEmpty()) return Answer.Absent
        var unknown = 0
        for (c in cands) {
            val e = known(c)
            if (e == null) { unknown++; synchronized(this) { urgent += key(c) }; continue }
            if (sha != null && e.sha256 == sha) return Answer.Present(c, size, e.sha256)
        }
        if (unknown > 0) poke()
        if (sha == null) return Answer.Candidates(cands.size, unknown > 0)
        return if (unknown > 0) Answer.Indexing(unknown) else Answer.Absent
    }

    /** Wakes the worker and makes it read the list of files again (a reception ended, a phone asked). */
    fun poke() { synchronized(this) { queue.clear() }; synchronized(wake) { wake.notifyAll() } }

    /** A file was renamed or filed on its volume: its hash follows it (same bytes), no second read. */
    @Synchronized fun renamed(dir: File, fromRel: String, toRel: String) {
        val c = cache(dir)
        val e = c.map.remove(fromRel.lowercase()) ?: return
        c.map[toRel.lowercase()] = toRel to e.second
        c.dirty = true
    }

    /**
     * Hashes ONE file not known yet (the ones a phone asked about first, then the smallest), if the TV is idle. Returns false when there was nothing to do
     * (or the TV is busy, or the file changed while it was read). Called by the worker, and by tests directly.
     */
    fun step(): Boolean {
        if (stopped || !idle()) return false
        val f = next() ?: return false
        return hashOne(f)
    }

    private fun next(): HeldFile? {
        val list: List<HeldFile>
        synchronized(this) {
            if (queue.isEmpty()) {
                val all = runCatching(held).getOrDefault(emptyList())
                val pending = all.filter { known(it) == null && fileOf(it).isFile }
                queue.addAll(pending.sortedWith(compareBy({ key(it) !in urgent }, { fileOf(it).length() })))
            } else if (urgent.isNotEmpty()) {
                // a file asked about jumps ahead of what was queued before the question
                val sorted = queue.sortedWith(compareBy { key(it) !in urgent }); queue.clear(); queue.addAll(sorted)
            }
            list = queue.toList()
        }
        for (f in list) {
            synchronized(this) { queue.remove(f) }
            if (known(f) == null && fileOf(f).isFile) return f
        }
        return null
    }

    private fun hashOne(f: HeldFile): Boolean {
        val file = fileOf(f)
        val size0 = file.length(); val mt0 = file.lastModified()
        val sha = try {
            FileInputStream(file).use { inp ->
                ContentHash.sha256(inp, size0, chunk, cancelled = {
                    // playback, a copy, a move: pause here (digest kept) until the TV is idle again
                    while (!stopped && !idle()) sleep(PAUSE_MS)
                    stopped
                }, onProgress = { _, _ -> pacer.pace(chunk, bytesPerSec) })
            }
        } catch (e: IOException) { null }                    // drive pulled, file deleted under us: unknown, tried again on a later pass
        synchronized(this) { urgent.remove(key(f)) }
        if (sha == null || !file.isFile || file.length() != size0 || file.lastModified() != mt0) return false
        synchronized(this) {
            val c = cache(f.dir)
            if (c.map.size >= maxEntries && !c.map.containsKey(f.rel.lowercase())) c.map.remove(c.map.keys.first())
            c.map[f.rel.lowercase()] = f.rel to Entry(size0, mt0, sha)
            c.dirty = true
        }
        hashedCount++
        if (size0 >= BIG_FILE || clock() - lastSave >= SAVE_EVERY_MS) flush()
        return true
    }

    /** Writes the dirty caches (atomically, on each volume). Entries of files that are gone are dropped first. */
    fun flush() {
        val toSave = synchronized(this) { lastSave = clock(); caches.values.filter { it.dirty }.onEach { it.dirty = false } }
        for (c in toSave) {
            if (!c.dir.isDirectory) continue                      // never prune or write while the volume is away
            val text = synchronized(this) {
                c.map.entries.removeAll { (_, v) -> !File(c.dir, v.first).isFile }
                c.map.values.joinToString("") { (rel, e) -> "${enc(rel)}\t${e.size}\t${e.mtime}\t${e.sha256}\n" }
            }
            runCatching { AtomicFile.write(File(c.dir, FILE), ("v1\n" + text).toByteArray(Charsets.UTF_8)) }
        }
    }

    private fun load(c: VolumeCache) {
        val f = File(c.dir, FILE)
        if (!f.isFile) return
        runCatching {
            val lines = f.readLines()
            if (lines.firstOrNull() != "v1") return
            for (l in lines.drop(1)) {
                val p = l.split('\t')
                if (p.size != 4 || !ContentHash.valid(p[3])) continue          // a damaged line is skipped, never trusted
                val size = p[1].toLongOrNull() ?: continue; val mt = p[2].toLongOrNull() ?: continue
                val rel = dec(p[0])
                if (rel.isEmpty() || rel.startsWith("/") || rel.split('/').any { it == ".." }) continue
                c.map[rel.lowercase()] = rel to Entry(size, mt, p[3])
            }
        }
    }

    /** The background pass: one daemon thread at the lowest priority, hashing while the TV is idle, sleeping otherwise. Idempotent. */
    @Synchronized fun startWorker() {
        if (worker?.isAlive == true) return
        stopped = false
        worker = Thread({
            while (!stopped) {
                val did = runCatching { step() }.getOrDefault(false)
                if (!did) {
                    runCatching { flush() }
                    synchronized(wake) { if (!stopped) wake.wait(if (idle()) IDLE_SCAN_MS else PAUSE_MS) }
                }
            }
            runCatching { flush() }
        }, "cb-content-index").apply { isDaemon = true; priority = Thread.MIN_PRIORITY; start() }
    }

    fun stop() { stopped = true; synchronized(wake) { wake.notifyAll() }; runCatching { flush() } }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
    private fun dec(s: String) = runCatching { java.net.URLDecoder.decode(s, "UTF-8") }.getOrDefault("")

    companion object {
        const val FILE = ".cbhash"
        /** Read pace of the background hash: well under what a USB 2 key gives, so a remote-control press never waits on the bus. */
        const val DEFAULT_BPS = 16L shl 20
        const val PAUSE_MS = 2_000L
        const val IDLE_SCAN_MS = 60_000L
        const val SAVE_EVERY_MS = 10_000L
        const val BIG_FILE = 64L shl 20
    }
}
