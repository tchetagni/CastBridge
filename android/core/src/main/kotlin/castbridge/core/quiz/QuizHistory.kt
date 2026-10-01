package castbridge.core.quiz

import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Anti-repetition history (docs/QUIZ.md, « Règle des 300 parties »): for one profile (the TV's host, or one phone
 * identified by its device id) and per course (general knowledge, one school level / field), the number of the game at
 * which each question was last asked. A question asked at game g may only come back at game g + [DEFAULT_MIN_GAP_GAMES]
 * or later, as long as the bank is big enough (see [QuizBank.draw]).
 */
const val DEFAULT_MIN_GAP_GAMES = 300

/** Key of a course in the history: "general", "primary/CM2", "higher/L1/droit". */
val QuestionFilter.courseKey: String get() = listOfNotNull(track.key, level, field).joinToString("/")

/** What [QuizBank.draw] needs to know about the past. */
interface QuestionHistory {
    /**
     * How many games ago the question was last asked in [course], counting the game being drawn as 1 (asked in the
     * previous game = 1). [NEVER] when it was never asked (or so long ago that it is forgotten).
     */
    fun age(course: String, id: String): Int
    companion object { const val NEVER = Int.MAX_VALUE }
}

/** Union of several profiles (multiplayer): a question is as recent as its most recent appearance in any of them. */
class UnionHistory(private val parts: List<QuestionHistory>) : QuestionHistory {
    override fun age(course: String, id: String): Int = parts.minOfOrNull { it.age(course, id) } ?: QuestionHistory.NEVER
}

/**
 * History of one profile. Thread-safe. Text format (compact, line-based, so that a half-written or damaged file still
 * yields everything before the damage): header `CBQH1`, then per course `@<course> <games played>` followed by
 * `<question id> <game number>` lines. Only the last [gap] games are kept: older entries no longer block anything.
 */
class QuizHistory(val gap: Int = DEFAULT_MIN_GAP_GAMES, val maxEntriesPerCourse: Int = 20_000) : QuestionHistory {
    private class Course { var games = 0; val seen = LinkedHashMap<String, Int>() }
    private val courses = LinkedHashMap<String, Course>()
    @Volatile var dirty = false; private set

    @Synchronized fun games(course: String): Int = courses[course]?.games ?: 0

    @Synchronized override fun age(course: String, id: String): Int {
        val c = courses[course] ?: return QuestionHistory.NEVER
        val g = c.seen[id] ?: return QuestionHistory.NEVER
        return c.games + 1 - g
    }

    /** Starts a new game of [course]; returns its number (1, 2, 3…). Questions are then noted with [record]. */
    @Synchronized fun beginGame(course: String): Int {
        val c = courses.getOrPut(course) { Course() }
        c.games++; dirty = true
        prune(c)
        return c.games
    }

    /** Notes that [ids] were asked in the current game of [course] (call [beginGame] first). */
    @Synchronized fun record(course: String, ids: Collection<String>) {
        val c = courses.getOrPut(course) { Course() }
        if (c.games == 0) c.games = 1
        for (id in ids) {
            if (!isSafeId(id)) continue
            c.seen.remove(id); c.seen[id] = c.games      // remove + put keeps the map in game order
        }
        dirty = true
    }

    /** Forgets everything about [course] (or all courses). */
    @Synchronized fun clear(course: String? = null) { if (course == null) courses.clear() else courses.remove(course); dirty = true }

    @Synchronized fun courses(): Set<String> = courses.keys.toSet()
    @Synchronized fun entries(course: String): Int = courses[course]?.seen?.size ?: 0

    private fun prune(c: Course) {
        val limit = c.games + 1 - gap            // entries with age >= gap (game number <= games+1-gap) are free again
        val it = c.seen.entries.iterator()
        while (it.hasNext()) { if (it.next().value <= limit) it.remove() else break }   // insertion order = game order
        while (c.seen.size > maxEntriesPerCourse) { val e = c.seen.entries.iterator(); e.next(); e.remove() }
    }

    /**
     * Merges what [other] (the same profile on another device) remembers into this history. Per course, a question is as recent
     * as its most recent appearance on either device, measured in games ago on that device; the game counter becomes the
     * larger of the two. Question ids are stable across lot updates, so a changed question keeps its slot.
     */
    fun mergeFrom(other: QuizHistory) {
        if (other === this) return
        val theirs = other.serialize().let { parse(it, gap) }       // a consistent snapshot
        synchronized(this) {
            for (key in theirs.courses.keys) {
                val t = theirs.courses.getValue(key); val m = courses.getOrPut(key) { Course() }
                val games = maxOf(m.games, t.games)
                val ago = HashMap<String, Int>()
                for ((id, g) in m.seen) ago[id] = m.games - g
                for ((id, g) in t.seen) ago.merge(id, t.games - g, ::minOf)
                val ordered = ago.entries.map { it.key to games - it.value }.sortedBy { it.second }
                m.games = games; m.seen.clear(); ordered.forEach { (id, g) -> m.seen[id] = g }
                prune(m)
            }
            dirty = true
        }
    }

    /** Serialized form, ~12-20 bytes per remembered question. */
    @Synchronized fun serialize(): String {
        val sb = StringBuilder("CBQH1\n")
        for ((k, c) in courses) {
            sb.append('@').append(k).append(' ').append(c.games).append('\n')
            for ((id, g) in c.seen) sb.append(id).append(' ').append(g).append('\n')
        }
        return sb.toString()
    }

    @Synchronized fun markClean() { dirty = false }

    companion object {
        private fun isSafeId(id: String) = id.isNotEmpty() && id.none { it == ' ' || it == '\n' || it == '\r' }

        /** Lenient: lines that do not parse are skipped, an unknown header gives an empty history. */
        fun parse(text: String, gap: Int = DEFAULT_MIN_GAP_GAMES): QuizHistory {
            val h = QuizHistory(gap)
            val lines = text.lineSequence().iterator()
            if (!lines.hasNext() || lines.next().trim() != "CBQH1") return h
            var cur: Course? = null
            for (raw in lines) {
                val line = raw.trimEnd('\r')
                if (line.isEmpty()) continue
                val sp = line.lastIndexOf(' ')
                if (sp <= 0) continue
                val n = line.substring(sp + 1).toIntOrNull() ?: continue
                if (n < 0) continue
                if (line[0] == '@') cur = h.courses.getOrPut(line.substring(1, sp)) { Course() }.also { it.games = n }
                else cur?.let { c -> if (n in 1..maxOf(c.games, 1)) c.seen[line.substring(0, sp)] = n }
            }
            for (c in h.courses.values) {          // game order is what pruning relies on
                val sorted = c.seen.entries.sortedBy { it.value }.map { it.key to it.value }
                c.seen.clear(); sorted.forEach { (k, v) -> c.seen[k] = v }
                h.prune(c)
            }
            return h
        }
    }
}

/**
 * The histories kept by a TV: one file per profile in [dir] (`host` = the TV itself, `dev:<id>` = one phone), each
 * written atomically (temp file + rename, the previous version kept as `.bak`), in the background: a game never waits
 * for the disk. [maxProfiles] files at most (the least recently used are deleted), each capped at [maxFileBytes].
 * With no [dir] everything stays in memory.
 */
class QuizHistoryBook(
    private val dir: File?,
    val gap: Int = DEFAULT_MIN_GAP_GAMES,
    private val maxProfiles: Int = 24,
    private val maxFileBytes: Long = 1L shl 20,
    private val executor: ExecutorService? = if (dir == null) null else Executors.newSingleThreadExecutor { r -> Thread(r, "quiz-history").apply { isDaemon = true } },
) {
    private val profiles = LinkedHashMap<String, QuizHistory>()

    /** History of the TV's own player (solo games, host). */
    val host: QuizHistory get() = profile(HOST)

    @Synchronized fun profile(key: String): QuizHistory = profiles.getOrPut(key) { load(key) }

    /** History to use for a game played by [keys] (union), or the host's when [keys] is empty. */
    fun viewFor(keys: Collection<String>): QuestionHistory =
        if (keys.isEmpty()) host else if (keys.size == 1) profile(keys.first()) else UnionHistory(keys.distinct().map { profile(it) })

    /** Persists [key] in the background (several saves in a row write the latest state). */
    fun save(key: String) {
        val h = synchronized(this) { profiles[key] } ?: return
        val ex = executor ?: return
        try { ex.execute { writeNow(key, h) } } catch (_: Exception) {}
    }

    /** Waits until everything requested so far is on disk (tests, app shutdown). */
    fun flush() {
        val ex = executor ?: return
        try { ex.submit {}.get() } catch (_: Exception) {}
    }

    private fun fileOf(key: String) = File(dir, "h-" + sha(key).take(16) + ".qh")
    private fun sha(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun load(key: String): QuizHistory {
        if (dir == null) return QuizHistory(gap)
        val f = fileOf(key)
        for (candidate in listOf(f, File(f.path + ".bak"))) {
            val h = runCatching {
                if (candidate.isFile && candidate.length() <= maxFileBytes) QuizHistory.parse(candidate.readText(Charsets.UTF_8), gap).takeIf { it.courses().isNotEmpty() } else null
            }.getOrNull()
            if (h != null) return h
        }
        return QuizHistory(gap)
    }

    private fun writeNow(key: String, h: QuizHistory) {
        val d = dir ?: return
        try {
            d.mkdirs()
            val bytes = h.serialize().toByteArray(Charsets.UTF_8)
            if (bytes.size > maxFileBytes) return
            val f = fileOf(key)
            val tmp = File(f.path + ".tmp")
            java.io.FileOutputStream(tmp).use { it.write(bytes); it.fd.sync() }
            val bak = File(f.path + ".bak")
            if (f.isFile) { bak.delete(); f.renameTo(bak) }
            if (!tmp.renameTo(f)) { f.delete(); if (!tmp.renameTo(f)) throw IOException("rename failed") }
            h.markClean()
            evict(d)
        } catch (_: Exception) { /* never blocks or breaks a game: the history just stays in memory */ }
    }

    private fun evict(d: File) {
        val files = d.listFiles { x -> x.name.startsWith("h-") && x.name.endsWith(".qh") }?.sortedByDescending { it.lastModified() } ?: return
        for (old in files.drop(maxProfiles)) { old.delete(); File(old.path + ".bak").delete() }
    }

    /** Total size on disk (diagnostics and tests). */
    fun diskBytes(): Long = dir?.listFiles()?.sumOf { it.length() } ?: 0

    companion object { const val HOST = "host" }
}
