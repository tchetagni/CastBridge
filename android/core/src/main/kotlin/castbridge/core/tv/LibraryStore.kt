package castbridge.core.tv

import java.io.File
import java.io.IOException
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

/**
 * What the TV remembers per stored file (duration, resume position, watched, last played, player settings), keyed by
 * (stored name, size): the size tells two different files of the same name apart. One small text file, rewritten
 * atomically on change (changes are rare: a pause, a stop, an end of file). At most [maxEntries] entries: the ones
 * played longest ago are dropped first.
 */
class LibraryDb(private val file: File?, private val maxEntries: Int = 2000, private val now: () -> Long = System::currentTimeMillis) {
    data class Entry(
        val durationMs: Long = 0,
        val resumeMs: Long = 0,
        val watched: Boolean = false,
        val playedAtMs: Long = 0,
        /** Per-file player settings (audio/subtitle track, delays...), encoded by [PlayerPrefs]. */
        val prefs: String = "",
    )

    private val map = LinkedHashMap<String, Entry>()

    init { load() }

    private fun key(name: String, size: Long) = "$size:$name"

    @Synchronized fun get(name: String, size: Long): Entry? = map[key(name, size)]

    @Synchronized private fun edit(name: String, size: Long, f: (Entry) -> Entry) {
        val k = key(name, size)
        val e = f(map[k] ?: Entry())
        if (map[k] == e) return
        map[k] = e
        prune(); save()
    }

    fun onPlayStarted(name: String, size: Long) = edit(name, size) { it.copy(playedAtMs = now()) }

    /** Playback stopped or paused at [posMs]: remember where (normalised), and whether it counts as watched. */
    fun onStopped(name: String, size: Long, posMs: Long, durMs: Long) = edit(name, size) { e ->
        val d = if (durMs > 0) durMs else e.durationMs
        e.copy(durationMs = d, resumeMs = LibraryLogic.resumeFrom(posMs, d), watched = e.watched || LibraryLogic.isWatched(posMs, d))
    }

    fun onEnded(name: String, size: Long, durMs: Long) = edit(name, size) { e ->
        e.copy(durationMs = if (durMs > 0) durMs else e.durationMs, resumeMs = 0, watched = true)
    }

    fun setDuration(name: String, size: Long, durMs: Long) { if (durMs > 0) edit(name, size) { it.copy(durationMs = durMs) } }

    fun setWatched(name: String, size: Long, watched: Boolean) = edit(name, size) { it.copy(watched = watched, resumeMs = 0) }

    fun setPrefs(name: String, size: Long, prefs: String) = edit(name, size) { it.copy(prefs = prefs) }

    @Synchronized fun renamed(from: String, to: String, size: Long) {
        val e = map.remove(key(from, size)) ?: return
        map[key(to, size)] = e; save()
    }

    @Synchronized fun deleted(name: String, size: Long) { if (map.remove(key(name, size)) != null) save() }

    @Synchronized fun size() = map.size

    private fun prune() {
        if (map.size <= maxEntries) return
        map.entries.sortedBy { it.value.playedAtMs }.take(map.size - maxEntries).map { it.key }.forEach { map.remove(it) }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun dec(s: String) = URLDecoder.decode(s, "UTF-8")

    private fun load() {
        val f = file ?: return
        val lines = runCatching { f.readLines() }.getOrNull() ?: return
        for (l in lines) {
            val p = l.split('\t')
            if (p.size < 6) continue
            runCatching {
                map[dec(p[0])] = Entry(p[1].toLong(), p[2].toLong(), p[3] == "1", p[4].toLong(), dec(p[5]))
            }
        }
    }

    private fun save() {
        val f = file ?: return
        runCatching {
            f.parentFile?.mkdirs()
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(map.entries.joinToString("") { (k, e) ->
                "${enc(k)}\t${e.durationMs}\t${e.resumeMs}\t${if (e.watched) 1 else 0}\t${e.playedAtMs}\t${enc(e.prefs)}\n"
            })
            if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
        }
    }
}

/**
 * Thumbnails on disk (the app's cache folder), bounded to [maxBytes] with least-recently-used eviction; nothing is
 * kept in RAM. Key = hash of (name, size, modification time): a replaced or modified file gets a new thumbnail.
 * A file that could not be thumbnailed gets a tiny ".fail" marker so it is not retried on every request.
 */
class ThumbCache(private val dir: File, private val maxBytes: Long = 20L shl 20, private val now: () -> Long = System::currentTimeMillis) {
    init { dir.mkdirs() }

    fun key(name: String, size: Long, mtime: Long): String =
        MessageDigest.getInstance("SHA-1").digest("$name\u0000$size\u0000$mtime".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun jpg(key: String) = File(dir, "$key.jpg")
    private fun fail(key: String) = File(dir, "$key.fail")

    fun has(key: String) = jpg(key).isFile
    fun failed(key: String) = fail(key).isFile

    /** The JPEG bytes (marks it as recently used), or null. */
    fun get(key: String): ByteArray? {
        val f = jpg(key)
        val b = runCatching { f.readBytes() }.getOrNull() ?: return null
        f.setLastModified(now())
        return b
    }

    @Synchronized fun put(key: String, jpeg: ByteArray) {
        val tmp = File(dir, "$key.tmp")
        try {
            tmp.writeBytes(jpeg)
            val f = jpg(key)
            if (!tmp.renameTo(f)) { f.delete(); if (!tmp.renameTo(f)) throw IOException("rename") }
            f.setLastModified(now())
            fail(key).delete()
        } catch (e: IOException) { tmp.delete(); return }
        trim()
    }

    @Synchronized fun markFailed(key: String) { runCatching { fail(key).also { it.writeBytes(ByteArray(0)); it.setLastModified(now()) } }; trim() }

    fun usedBytes(): Long = dir.listFiles().orEmpty().filter { it.isFile }.sumOf { it.length() }

    /** Deletes the least recently used entries until the cache fits (fail markers count as 1 kB so they cannot pile up forever). */
    @Synchronized fun trim(limit: Long = maxBytes) {
        val files = dir.listFiles().orEmpty().filter { it.isFile && (it.name.endsWith(".jpg") || it.name.endsWith(".fail")) }
        fun cost(f: File) = maxOf(f.length(), 1024L)
        var total = files.sumOf(::cost)
        if (total <= limit) return
        for (f in files.sortedBy { it.lastModified() }) {
            if (total <= limit) break
            total -= cost(f); f.delete()
        }
    }

    /** Removes everything (e.g. from a settings screen). */
    @Synchronized fun clear() { dir.listFiles().orEmpty().forEach { it.delete() } }
}

/** What a thumbnail generator returns: the JPEG (null = none possible) and the duration it found (0 = unknown). */
data class ThumbResult(val jpeg: ByteArray?, val durationMs: Long = 0)

data class ThumbJob(val key: String, val name: String, val size: Long, val file: File)

/**
 * Makes thumbnails in the background, strictly one at a time (a 32-bit TV with 1 GB of RAM decodes one thing at a time),
 * and only while [canRun] says so (the app pauses it while a video plays: the hardware decoder is busy). Requests are
 * de-duplicated and the queue is bounded; the thread exists only while there is work.
 */
class ThumbWorker(
    private val cache: ThumbCache,
    private val generate: (ThumbJob) -> ThumbResult?,
    private val canRun: () -> Boolean = { true },
    private val onDone: (ThumbJob, ThumbResult?) -> Unit = { _, _ -> },
    private val maxQueue: Int = 200,
    private val pollMs: Long = 2000,
    private val startThread: (Runnable) -> Unit = { r -> Thread(r, "cb-thumbs").apply { isDaemon = true; priority = Thread.MIN_PRIORITY; start() } },
) {
    private val queue = LinkedHashMap<String, ThumbJob>()
    private val running = AtomicBoolean(false)
    @Volatile var stopped = false

    /** Queues [job] unless its thumbnail exists or failed before. Newest requests first (what the user is looking at). */
    fun request(job: ThumbJob) {
        if (stopped || cache.has(job.key) || cache.failed(job.key)) return
        synchronized(queue) {
            queue.remove(job.key)
            if (queue.size >= maxQueue) queue.keys.firstOrNull()?.let { queue.remove(it) }   // drop the oldest request
            queue[job.key] = job
        }
        if (running.compareAndSet(false, true)) startThread(Runnable { loop() })
    }

    fun pending(): Int = synchronized(queue) { queue.size }

    private fun next(): ThumbJob? = synchronized(queue) {
        val k = queue.keys.lastOrNull() ?: return null
        queue.remove(k)
    }

    private fun loop() {
        try {
            while (!stopped) {
                if (!canRun()) { Thread.sleep(pollMs); continue }
                val job = next() ?: break
                if (cache.has(job.key) || cache.failed(job.key)) continue
                runOne(job)
            }
        } catch (_: InterruptedException) {
        } finally {
            running.set(false)
            // A request that arrived between the last next() and here must not be stranded.
            if (!stopped && pending() > 0 && running.compareAndSet(false, true)) startThread(Runnable { loop() })
        }
    }

    /** Generates one thumbnail now (also used by tests). Never throws. */
    fun runOne(job: ThumbJob) {
        val r = try { generate(job) } catch (t: Throwable) { null }       // OutOfMemoryError included: a thumbnail is never worth a crash
        val jpeg = r?.jpeg
        if (jpeg != null && jpeg.isNotEmpty()) cache.put(job.key, jpeg) else cache.markFailed(job.key)
        runCatching { onDone(job, r) }
    }
}

/**
 * The TV app's [LibraryMeta]: saved positions from [db], thumbnails from [cache] made on demand by [worker].
 * Only media files get a thumbnail request; everything else is reported "no thumbnail" at once.
 */
class LibraryProvider(val db: LibraryDb, val cache: ThumbCache, private val workerOf: (LibraryProvider) -> ThumbWorker) : LibraryMeta {
    val worker: ThumbWorker by lazy { workerOf(this) }

    private fun keyOf(name: String, size: Long, file: File?) = file?.let { cache.key(name, size, it.lastModified()) }

    override fun meta(name: String, size: Long) = meta(name, size, null)

    override fun meta(name: String, size: Long, file: File?): FileMeta {
        val e = db.get(name, size)
        val k = keyOf(name, size, file)
        return FileMeta(e?.durationMs ?: 0, e?.resumeMs ?: 0, e?.watched ?: false, k != null && cache.has(k), e?.playedAtMs ?: 0)
    }

    override fun thumb(name: String, size: Long, file: File?): ByteArray? {
        val k = keyOf(name, size, file) ?: return null
        cache.get(k)?.let { return it }
        if (file != null && MediaType.of(name) != MediaType.OTHER) worker.request(ThumbJob(k, name, size, file))
        return null
    }

    override fun thumbFailed(name: String, size: Long, file: File?): Boolean {
        if (MediaType.of(name) == MediaType.OTHER || file == null) return true
        return keyOf(name, size, file)?.let { cache.failed(it) } ?: true
    }

    override fun setWatched(name: String, size: Long, watched: Boolean) = db.setWatched(name, size, watched)
    override fun renamed(from: String, to: String, size: Long) = db.renamed(from, to, size)
    override fun deleted(name: String, size: Long) = db.deleted(name, size)

    /** Called by the worker with what the generator found (the duration is worth keeping even without a picture). */
    fun onGenerated(job: ThumbJob, r: ThumbResult?) { r?.durationMs?.let { if (it > 0 && (db.get(job.name, job.size)?.durationMs ?: 0) <= 0) db.setDuration(job.name, job.size, it) } }
}
