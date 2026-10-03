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


/**
 * One finished file the TV holds: [dir] = its volume folder, [rel] = path inside it ("Films/X.mkv"), [name] = the TV's key name, [folder] = its category folder,
 * [size] = its size as the listing knows it (-1 = unknown): a size question only looks at the files of that size.
 */
data class HeldFile(val volumeId: String, val dir: File, val rel: String, val name: String, val folder: String = "", val size: Long = -1)

/**
 * « Déjà sur la TV ? » by CONTENT (docs/agent-reports/copy-dedup.md): size + SHA-256 of the finished files the TV holds, computed LAZILY in the background.
 *
 * Rules that never bend (R-06 / R-11, docs/agent-reports/fluid-playback-during-copy.md):
 *  - nothing is hashed while [idle] is false (playback, a copy, a move, a /stream/ reader, a download, a USB import): the read in progress PAUSES with the
 *    file CLOSED (a USB key can be pulled) and reopens at its offset once idle, only if the file did not change (size + mtime);
 *  - reads are paced to [bytesPerSec] in [chunk] blocks, on ONE daemon thread at the lowest priority ([startWorker]), never on a request thread;
 *  - a hash is valid for (path, size, mtime) only: a changed or vanished file is unknown again;
 *  - partial copies are never held files ([held] lists finished files only), so they never count as present;
 *  - the cache on each volume (`.cbhash`) is SIGNED with this TV's own key ([key], HMAC-SHA-256, kept in the TV app's private storage, never on the key):
 *    a line whose signature does not match (forged, written by another TV, damaged) is ignored and the file hashed again. No key = no cache read or written;
 *  - a hash read from the cache is never FRESH: [Answer.Present.fresh] is true only for a hash computed by THIS process from the bytes on the disk. A « Déplacer »
 *    needs a fresh hash ([MoveProof.byContentHash]); a query with `fresh` re-reads the file (first in the queue) and answers « indexing » until it is done;
 *  - the list of files is read again only when something changed ([poke]: a reception, a volume event, a question about an unknown file);
 *  - `.cbhash` is written at most every [SAVE_EVERY_MS], at the end of a pass and at a clean stop (a USB key's flash is not worn by the index).
 * A query never hashes: what is unknown answers « indexing » and is moved to the front of the queue.
 */
class ContentIndex(
    private val held: (Long?) -> List<HeldFile>,
    private val idle: () -> Boolean,
    private val key: ByteArray? = null,
    private val bytesPerSec: Long = DEFAULT_BPS,
    private val clock: () -> Long = System::currentTimeMillis,
    private val sleep: (Long) -> Unit = Thread::sleep,
    private val chunk: Int = 1 shl 20,
    private val maxEntries: Int = 100_000,
) {
    /**
     * [fresh] = computed by this process from the file's bytes (never true for a line read from `.cbhash`); [seq] = the question counter when that read STARTED:
     * a `fresh` question is answered only by a read that started AFTER it (a hash of an hour ago is not a re-read).
     */
    data class Entry(val size: Long, val mtime: Long, val sha256: String, val fresh: Boolean = false, val seq: Long = 0)

    sealed interface Answer {
        /** No finished file of that size (or none of that content, every candidate being hashed). */
        object Absent : Answer
        /** Size-only question: [count] finished files of that size; [indexing] = some of them are not hashed yet. Names are never given here. */
        data class Candidates(val count: Int, val indexing: Boolean) : Answer
        /** Same size, but [pending] candidates are not hashed yet (or not freshly, when asked): not known; the phone copies, never blocked. */
        data class Indexing(val pending: Int) : Answer
        /** A finished file of exactly that size and SHA-256, valid for its size and mtime; [fresh] = hashed by this process (see the class rules). */
        data class Present(val file: HeldFile, val size: Long, val sha256: String, val fresh: Boolean) : Answer
    }

    private class VolumeCache(val dir: File) {
        val map = LinkedHashMap<String, Pair<String, Entry>>()   // lowercase rel -> (rel, entry)
        var dirty = false
        var loaded = false
    }

    private val caches = HashMap<String, VolumeCache>()
    private val urgent = LinkedHashMap<String, HeldFile>()        // asked about by a phone: hashed first
    private val needFresh = HashMap<String, Long>()               // asked with `fresh`: key -> question counter; only a read started after it answers
    private val failures = HashMap<String, Pair<Int, Long>>()     // unreadable files: (count, not before) — growing backoff, never a hot loop
    private var seq = 0L
    private val skipped = HashSet<String>()                       // over [maxEntries]: not hashed again by the background pass
    private val queue = ArrayDeque<HeldFile>()
    @Volatile private var listDirty = true
    private var hashedSinceSave = 0
    private val pacer = castbridge.core.xfer.RatePacer({ clock() * 1_000_000 }, sleep)
    private val wake = Object()
    @Volatile private var stopped = false
    @Volatile private var worker: Thread? = null
    private var lastSave = 0L
    /** Files hashed since start (tests, diagnostics). */
    @Volatile var hashedCount = 0; private set
    /** `.cbhash` writes since start (tests: the key's flash is spared). */
    @Volatile var writes = 0; private set

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
     * Does the TV hold a finished file of [size] bytes (and, with [sha256], of that very content)? With [fresh], only a hash computed by this process counts.
     * Never reads a file (stat of the same-size files only); what is not known yet goes to the front of the background queue.
     */
    fun query(size: Long, sha256: String?, fresh: Boolean = false): Answer {
        if (size <= 0) return Answer.Absent
        val sha = sha256?.lowercase()
        val cands = held(size).filter { f -> (f.size < 0 || f.size == size) && fileOf(f).let { it.isFile && it.length() == size } }
        if (cands.isEmpty()) return Answer.Absent
        var unknown = 0
        for (c in cands) {
            val e = known(c)
            val k = key(c)
            val asked = if (fresh) synchronized(this) { needFresh.getOrPut(k) { ++seq } } else 0L
            if (e == null || (fresh && !(e.fresh && e.seq > asked))) {
                unknown++
                synchronized(this) { urgent[k] = c }
                continue
            }
            if (fresh) synchronized(this) { needFresh.remove(k) }       // answered by a read made after this question; the next question asks again
            if (sha != null && e.sha256 == sha) return Answer.Present(c, size, e.sha256, e.fresh)
        }
        if (unknown > 0) wakeUp()
        if (sha == null) return Answer.Candidates(cands.size, unknown > 0)
        return if (unknown > 0) Answer.Indexing(unknown) else Answer.Absent
    }

    private fun wakeUp() { synchronized(wake) { wake.notifyAll() } }

    /** Something changed (a reception, a volume event): the list of files is read again at the next step. */
    fun poke() { synchronized(this) { listDirty = true; queue.clear() }; wakeUp() }

    /** A file was renamed or filed on its volume: its hash follows it (same bytes), no second read. */
    @Synchronized fun renamed(dir: File, fromRel: String, toRel: String) {
        val c = cache(dir)
        val e = c.map.remove(fromRel.lowercase()) ?: return
        c.map[toRel.lowercase()] = toRel to e.second
        c.dirty = true
    }

    private fun wanted(f: HeldFile): Boolean {
        if (!fileOf(f).isFile) return false
        if (synchronized(this) { failures[key(f)]?.let { clock() < it.second } } == true) return false      // backoff after a failed read
        val e = known(f)
        val asked = synchronized(this) { needFresh[key(f)] }
        if (asked != null) return e == null || !(e.fresh && e.seq > asked)
        return e == null
    }

    /**
     * Hashes ONE file (the ones a phone asked about first, then the smallest unknown), if the TV is idle. Returns false when there was nothing to do (or the
     * TV is busy, or the file changed while it was read). Called by the worker, and by tests directly.
     */
    fun step(): Boolean {
        if (stopped || !idle()) return false
        val f = next()
        if (f == null) { if (hashedSinceSave > 0) flush(force = true); return false }      // end of a pass
        return hashOne(f)
    }

    private fun next(): HeldFile? {
        val asked = synchronized(this) { urgent.values.toList() }
        for (f in asked) {
            if (wanted(f)) return f
            synchronized(this) { if (failures[key(f)]?.let { clock() < it.second } != true) urgent.remove(key(f)) }
        }
        val refill = synchronized(this) { val d = listDirty; listDirty = false; d && queue.isEmpty() }
        if (refill) {
            val all = runCatching { held(null) }.getOrDefault(emptyList())
            val pending = all.filter { synchronized(this) { key(it) !in skipped && failures[key(it)]?.let { f -> clock() < f.second } != true } && known(it) == null && fileOf(it).isFile }
            synchronized(this) { queue.addAll(pending.sortedBy { if (it.size >= 0) it.size else fileOf(it).length() }) }
        }
        while (true) {
            val f = synchronized(this) { queue.removeFirstOrNull() } ?: return null
            if (known(f) == null && fileOf(f).isFile) return f
        }
    }

    private fun hashOne(f: HeldFile): Boolean {
        val ok = hashOnce(f)
        synchronized(this) {
            if (ok) failures.remove(key(f))
            else { val n = (failures[key(f)]?.first ?: 0) + 1; failures[key(f)] = n to clock() + minOf(FAIL_BACKOFF_MS shl minOf(n - 1, 7), 3_600_000L) }
        }
        return ok
    }

    private fun hashOnce(f: HeldFile): Boolean {
        val startSeq = synchronized(this) { ++seq }
        val file = fileOf(f)
        val size0 = file.length(); val mt0 = file.lastModified()
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(chunk)
        var off = 0L
        var inp: FileInputStream? = null
        try {
            while (off < size0) {
                if (stopped) return false
                if (!idle()) {
                    // playback, a copy, a reader: pause with the file CLOSED (a USB key may be pulled), digest kept in memory
                    inp?.close(); inp = null
                    while (!stopped && !idle()) sleep(PAUSE_MS)
                    if (stopped || !file.isFile || file.length() != size0 || file.lastModified() != mt0) return false
                }
                val s = inp ?: FileInputStream(file).also { it.channel.position(off); inp = it }
                val n = s.read(buf, 0, minOf(buf.size.toLong(), size0 - off).toInt())
                if (n < 0) break
                md.update(buf, 0, n); off += n
                pacer.pace(n, bytesPerSec)
            }
        } catch (e: IOException) { return false }                // drive pulled, file deleted under us: unknown, tried again later
        finally { runCatching { inp?.close() } }
        if (off != size0 || !file.isFile || file.length() != size0 || file.lastModified() != mt0) return false
        val sha = ContentHash.hex(md.digest())
        synchronized(this) {
            urgent.remove(key(f))
            val c = cache(f.dir)
            if (c.map.size >= maxEntries && !c.map.containsKey(f.rel.lowercase())) {
                skipped += key(f)                                  // full: kept out (never evicting, never hashing it again in the background)
            } else {
                c.map[f.rel.lowercase()] = f.rel to Entry(size0, mt0, sha, fresh = true, seq = startSeq)
                c.dirty = true; hashedSinceSave++
            }
        }
        hashedCount++
        flush(force = false)
        return true
    }

    /** Writes the dirty caches, at most every [SAVE_EVERY_MS] unless [force] (end of a pass, clean stop). Entries of files that are gone are dropped first. */
    fun flush(force: Boolean = true) {
        if (key == null) return
        val toSave = synchronized(this) {
            if (!force && clock() - lastSave < SAVE_EVERY_MS) return
            lastSave = clock(); hashedSinceSave = 0
            caches.values.filter { it.dirty }.onEach { it.dirty = false }
        }
        for (c in toSave) {
            if (!c.dir.isDirectory) continue                      // never prune or write while the volume is away
            val text = synchronized(this) {
                c.map.entries.removeAll { (_, v) -> !File(c.dir, v.first).isFile }
                c.map.values.joinToString("") { (rel, e) -> "${enc(rel)}\t${e.size}\t${e.mtime}\t${e.sha256}\t${mac(rel, e)}\n" }
            }
            runCatching { AtomicFile.write(File(c.dir, FILE), ("$VERSION\n" + text).toByteArray(Charsets.UTF_8)); writes++ }
        }
    }

    private fun mac(rel: String, e: Entry): String {
        val m = javax.crypto.Mac.getInstance("HmacSHA256")
        m.init(javax.crypto.spec.SecretKeySpec(key ?: return "", "HmacSHA256"))
        return ContentHash.hex(m.doFinal("$rel\n${e.size}\n${e.mtime}\n${e.sha256}".toByteArray(Charsets.UTF_8)))
    }

    private fun load(c: VolumeCache) {
        if (key == null) return                                   // no key of this TV: nothing on a volume is trusted
        val f = File(c.dir, FILE)
        if (!f.isFile) return
        runCatching {
            val lines = f.readLines()
            if (lines.firstOrNull() != VERSION) return            // an older unsigned cache: ignored, rebuilt
            for (l in lines.drop(1)) {
                val p = l.split('\t')
                if (p.size != 5 || !ContentHash.valid(p[3])) continue
                val size = p[1].toLongOrNull() ?: continue; val mt = p[2].toLongOrNull() ?: continue
                val rel = dec(p[0])
                if (rel.isEmpty() || rel.startsWith("/") || rel.split('/').any { it == ".." }) continue
                val e = Entry(size, mt, p[3], fresh = false)
                if (!java.security.MessageDigest.isEqual(mac(rel, e).toByteArray(), p[4].toByteArray())) continue   // forged or another TV's: ignored
                c.map[rel.lowercase()] = rel to e
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
                if (!did) synchronized(wake) { if (!stopped) wake.wait(if (idle()) IDLE_SCAN_MS else PAUSE_MS) }
            }
        }, "cb-content-index").apply { isDaemon = true; priority = Thread.MIN_PRIORITY; start() }
    }

    fun stop() { stopped = true; wakeUp(); runCatching { flush(force = true) } }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
    private fun dec(s: String) = runCatching { java.net.URLDecoder.decode(s, "UTF-8") }.getOrDefault("")

    companion object {
        const val FILE = ".cbhash"
        const val VERSION = "v2"
        /** Read pace of the background hash: well under what a USB 2 key gives, so a remote-control press never waits on the bus. */
        const val DEFAULT_BPS = 16L shl 20
        const val PAUSE_MS = 2_000L
        const val IDLE_SCAN_MS = 60_000L
        /** At most one `.cbhash` write per volume every 5 minutes while indexing (plus the end of a pass and a clean stop). */
        const val SAVE_EVERY_MS = 300_000L
        /** First wait after a failed read (doubles at each failure, at most 1 h). */
        const val FAIL_BACKOFF_MS = 30_000L
    }
}
