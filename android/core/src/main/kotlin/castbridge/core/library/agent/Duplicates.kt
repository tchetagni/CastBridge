package castbridge.core.library.agent

/** Reads a few bytes of a file to tell two files of the same size apart. Implementations read locally (LAN / storage), never upload. */
fun interface Fingerprinter {
    /** A short string equal for files with the same head and tail, or null when it cannot be computed (then the agent stays cautious). */
    fun fingerprint(file: FileRef): String?
}

/** What makes two files duplicates. */
enum class DupKind { EXACT, PROBABLE, VERSION }

/** [keep] stays, [extras] are the ones the agent would put in the trash (only after the user ticks them). */
data class DupGroup(val kind: DupKind, val keep: FileRef, val extras: List<FileRef>, val reason: String) {
    val bytes: Long get() = extras.sumOf { it.size }
}

object Duplicates {
    /** Below that size two equal sizes prove nothing. */
    const val MIN_BYTES = 512L * 1024

    /** Files that share their size with another: the only ones worth fingerprinting. */
    fun candidates(files: List<FileRef>): List<FileRef> =
        files.filter { it.size >= MIN_BYTES }.groupBy { it.size }.values.filter { it.size > 1 }.flatten()

    private fun sameDuration(a: FileRef, b: FileRef) = a.durationMs > 0 && b.durationMs > 0 && kotlin.math.abs(a.durationMs - b.durationMs) <= 1500
    private fun hasState(f: FileRef) = f.playedAtMs > 0 || f.resumeMs > 0 || f.watched

    /** The copy to keep among identical files: the one with the viewing history, then not a "copy", then on the USB key, then the oldest. */
    private fun best(files: List<FileRef>, parse: (FileRef) -> Parsed, removableVolumes: Set<String>): FileRef =
        files.sortedWith(
            compareByDescending<FileRef> { hasState(it) }
                .thenByDescending { if (hasState(it)) it.playedAtMs else 0L }
                .thenBy { parse(it).copy }
                .thenByDescending { it.volumeId in removableVolumes }
                .thenBy { if (it.mtime > 0) it.mtime else Long.MAX_VALUE }
                .thenBy { it.name.length }
                .thenBy { it.name }
        ).first()

    fun find(files: List<FileRef>, parse: (FileRef) -> Parsed, fingerprints: Map<String, String> = emptyMap(), removableVolumes: Set<String> = emptySet(),
             habits: Habits = Habits.NONE): List<DupGroup> {
        val out = ArrayList<DupGroup>()
        val used = HashSet<String>()

        // 1. same size: exact when the fingerprints agree, probable when they cannot be read but duration and cleaned name agree
        for ((size, same) in files.filter { it.size >= MIN_BYTES }.groupBy { it.size }) {
            if (same.size < 2) continue
            val withFp = same.filter { (it.fingerprint ?: fingerprints[it.key]) != null }
            val byFp = withFp.groupBy { it.fingerprint ?: fingerprints[it.key] }
            for ((_, g) in byFp) if (g.size > 1) {
                val keep = best(g, parse, removableVolumes)
                out += DupGroup(DupKind.EXACT, keep, g - keep, "Contenu identique (${Text.size(size)})")
                g.forEach { used += it.key }
            }
            val rest = same.filter { it.key !in used && (it.fingerprint ?: fingerprints[it.key]) == null }
            // without a fingerprint, equal size alone is not enough: the duration and the cleaned names must agree too
            val byName = rest.groupBy { Text.key(parse(it).title).ifEmpty { Text.key(it.name) } }
            for ((_, g) in byName) if (g.size > 1 && g.all { sameDuration(it, g[0]) }) {
                val keep = best(g, parse, removableVolumes)
                out += DupGroup(DupKind.PROBABLE, keep, g - keep, "Même taille, même durée et même nom nettoyé (${Text.size(size)})")
                g.forEach { used += it.key }
            }
        }

        // 2. same episode / film in several qualities (same language version): keep the best, never touch a different language version
        val pool = files.filter { it.key !in used && it.size >= MIN_BYTES }
        val versions = pool.filter { f -> val p = parse(f); p.confidence >= 0.8 && p.identity != null && (p.media == Media.VIDEO || p.media == Media.AUDIO) }
            .groupBy { val p = parse(it); (p.identity ?: "") + "|" + (p.audio ?: habits.defaultAudio).name }
        for ((_, g) in versions) {
            if (g.size < 2) continue
            val ranked = g.sortedWith(
                compareByDescending<FileRef> { parse(it).resolution ?: 0 }
                    .thenByDescending { hasState(it) }
                    .thenByDescending { it.size }
                    .thenBy { it.name }
            )
            val keep = ranked.first()
            val extras = ranked.drop(1).filter { (parse(it).resolution ?: 0) < (parse(keep).resolution ?: 0) || it.size < keep.size }
            if (extras.isEmpty()) continue
            val r = parse(keep).resolution?.let { "${it}p" } ?: "la plus grosse"
            out += DupGroup(DupKind.VERSION, keep, extras, "Même ${if (parse(keep).kind == Kind.SERIES) "épisode" else "titre"} en plusieurs qualités (conservé : $r)")
        }
        return out
    }
}
