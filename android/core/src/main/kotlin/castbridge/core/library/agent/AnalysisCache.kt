package castbridge.core.library.agent

import java.io.File

/**
 * What the assistant remembers between two analyses so that the second one is fast and only works on what changed:
 *  - durations of the phone's videos (read from each file's header: the slowest part of a phone folder analysis);
 *  - partial fingerprints of TV files (64 kB read twice over the network: the slowest part of a TV analysis).
 *
 * Plain text file in the app's private storage, one entry per line ("kind TAB key TAB value"), appended as it goes (a cut loses nothing
 * that matters, and a damaged line is ignored), compacted when it grows. Nothing here is ever sent anywhere; "Effacer" in the settings removes it.
 * An entry is only valid for the same name, size and date: a file that changed is read again.
 */
class FileAnalysisCache(private val file: File, private val maxEntries: Int = 40_000) : DurationCache {
    private val map = LinkedHashMap<String, String>()
    private var lines = 0
    private var loaded = false

    @Synchronized private fun load() {
        if (loaded) return
        loaded = true
        runCatching {
            if (file.exists()) file.forEachLine { l ->
                val i = l.indexOf('\t'); val j = l.lastIndexOf('\t')
                if (i > 0 && j > i) { map.remove(l.substring(0, j)); map[l.substring(0, j)] = l.substring(j + 1); lines++ }
            }
        }
        if (map.size > maxEntries) compact()
    }

    private fun clean(k: String) = k.map { if (it == '\t' || it == '\n' || it == '\r') ' ' else it }.joinToString("")

    @Synchronized private fun getRaw(kind: String, key: String): String? { load(); return map[kind + "\t" + clean(key)] }

    @Synchronized private fun putRaw(kind: String, key: String, value: String) {
        load()
        val k = kind + "\t" + clean(key)
        if (map[k] == value) return
        map.remove(k); map[k] = value
        runCatching { file.parentFile?.mkdirs(); file.appendText("$k\t$value\n"); lines++ }
        if (lines > maxEntries * 2) compact()
    }

    @Synchronized private fun compact() {
        while (map.size > maxEntries) map.remove(map.keys.first())
        runCatching {
            val tmp = File(file.path + ".tmp")
            tmp.bufferedWriter().use { w -> map.forEach { (k, v) -> w.write("$k\t$v\n") } }
            if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
            lines = map.size
        }
    }

    @Synchronized fun clear() { map.clear(); lines = 0; loaded = true; runCatching { file.delete() } }
    @Synchronized fun size(): Int { load(); return map.size }

    override fun get(key: String): Long? = getRaw("d", key)?.toLongOrNull()
    override fun put(key: String, durationMs: Long) = putRaw("d", key, durationMs.toString())

    /** Fingerprints of TV files, valid for the same volume, name, size and date. "-" = the file could not be read (not tried again for a day). */
    fun fingerprint(f: FileRef, now: Long = System.currentTimeMillis()): String? {
        val v = getRaw("f", fpKey(f)) ?: return null
        if (v.startsWith("-")) return if (now - (v.substring(1).toLongOrNull() ?: 0) < 86_400_000L) "" else null
        return v
    }
    fun putFingerprint(f: FileRef, fp: String?, now: Long = System.currentTimeMillis()) = putRaw("f", fpKey(f), fp ?: "-$now")

    private fun fpKey(f: FileRef) = "${f.volumeId}|${f.name}|${f.size}|${f.mtime}"
}

/** A [Fingerprinter] that asks the cache first: files already read in a previous analysis cost nothing. */
class CachingFingerprinter(private val inner: Fingerprinter?, private val cache: FileAnalysisCache) : Fingerprinter {
    /** Reads served by the cache / read for real, for the screen ("12 déjà connus"). */
    @Volatile var hits = 0; private set
    @Volatile var reads = 0; private set

    override fun fingerprint(file: FileRef): String? {
        cache.fingerprint(file)?.let { hits++; return it.takeIf { v -> v.isNotEmpty() } }
        val fpr = inner ?: return null
        reads++
        val v = runCatching { fpr.fingerprint(file) }.getOrNull()
        cache.putFingerprint(file, v)
        return v
    }

    /** True when the cache already knows the answer for this file (it does not count against the budget of new reads). */
    fun known(file: FileRef) = cache.fingerprint(file) != null
}
