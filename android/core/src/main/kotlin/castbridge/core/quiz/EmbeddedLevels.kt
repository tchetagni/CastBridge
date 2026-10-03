package castbridge.core.quiz

import java.security.MessageDigest

/** Where the rotation of the bundled files is remembered, per level (and field). The TV keeps it across launches when the host gives it a store. */
interface Rotation {
    /** Number of files already consumed for [key] (null = never played: the start is derived from the seed and [Memory.salt]). */
    fun get(key: String): Int?
    fun set(key: String, v: Int)

    /** In-memory store (lost at exit): the start differs at each launch because [salt] defaults to the clock; tests inject a fixed one. */
    class Memory(val salt: Long = System.currentTimeMillis()) : Rotation {
        private val m = HashMap<String, Int>()
        @Synchronized override fun get(key: String): Int? = m[key]
        @Synchronized override fun set(key: String, v: Int) { m[key] = v }
    }
}

/**
 * The Quiz bundled in the TV APK (trial edition: EVERY question of the repository, 217 494), built by tools/quiz-bank/build_embedded.py.
 *
 * Layout (castbridge/quiz/): `embedded/<level>/<lot>[.N].json` = FREE questions, `embedded-reserved/<level>/<lot>[.N].json` = RESERVABLE
 * questions (about 30 % of every lot, chosen question by question, never a level as a whole), `embedded/index.json` = levels, totals
 * (`count`, `freeCount`, `reservedCount`) and the files (name, lot, field, count, bytes). A file holds at most 500 questions.
 *
 * Loading is ON DEMAND and BY FILE: a game reads only a few files (at most [maxQuestions] questions and [maxBytes] bytes of JSON, both read
 * from the index), never a whole level (L2 alone is 20 000 questions ≈ 12 MB of JSON; the reference TV is 32-bit). The files of a level are put
 * in a fixed order (seeded, the lots interleaved so that a game mixes subjects), and each game takes the next ones: the cursor moves by the
 * number of files used ([advance]), so every question of the level is reached after a few games. With [trialOpen] false the reserved files are
 * not in that order at all (and are never opened). A missing file (a production build without `embedded-reserved/`) is skipped.
 * Question ids are the lots' ids, so an installed lot of the same level replaces them instead of duplicating them. Status "review" is kept.
 * The old index (one `file` per level, no totals) still works.
 */
class EmbeddedLevels(
    private val base: String = "/castbridge/quiz/",
    private val reader: (String) -> ByteArray? = { path -> EmbeddedLevels::class.java.getResourceAsStream(path)?.use { it.readBytes() } },
    val rotation: Rotation = Rotation.Memory(),
    /** `QuizEdition.TRIAL_OPEN`: reservable questions are playable in the trial edition; false = free questions only. */
    val trialOpen: Boolean = QuizEdition.TRIAL_OPEN,
    val maxQuestions: Int = MAX_QUESTIONS,
    val maxBytes: Int = MAX_BYTES,
) {
    data class FileRef(val name: String, val lot: String, val field: String?, val count: Int, val bytes: Int)

    /**
     * [count] = every question of the level (what the screen shows, read from the index); [freeCount] / [reservedCount] its two families.
     * [file] is only set by the old index (one file per level; `files` empty).
     */
    data class Level(
        val key: String, val level: String?, val track: Track, val count: Int, val file: String = "",
        val freeCount: Int = count, val reservedCount: Int = 0,
        val files: List<FileRef> = emptyList(), val reservedFiles: List<FileRef> = emptyList(), val lots: List<String> = emptyList(),
    )

    /** What the last [load] read (for the tests and the about screen). */
    class LoadStats(val files: List<String>, val questions: Int, val jsonBytes: Int)
    @Volatile var lastLoad: LoadStats = LoadStats(emptyList(), 0, 0); private set

    private val seed: String by lazy { (root["seed"] as? String) ?: "castbridge-embedded-quiz" }

    private val root: Map<String, Any?> by lazy { read("embedded/index.json")?.let { runCatching { Json.obj(it) }.getOrNull() } ?: emptyMap() }

    /** The levels (index only: a few tens of KB). */
    val levels: List<Level> by lazy {
        root.list("levels").orEmpty().mapNotNull { o ->
            @Suppress("UNCHECKED_CAST") val m = o as? Map<String, Any?> ?: return@mapNotNull null
            val count = m.int("count") ?: 0
            Level(m.str("key") ?: return@mapNotNull null, m.str("level"), Track.of(m.str("track")) ?: return@mapNotNull null,
                count, m.str("file") ?: "", m.int("freeCount") ?: count, m.int("reservedCount") ?: 0,
                refs(m.list("files")), refs(m.list("reservedFiles")), m.list("lots").orEmpty().filterIsInstance<String>())
        }
    }

    private fun refs(l: List<*>?): List<FileRef> = l.orEmpty().mapNotNull { o ->
        @Suppress("UNCHECKED_CAST") val m = o as? Map<String, Any?> ?: return@mapNotNull null
        FileRef(m.str("name") ?: return@mapNotNull null, m.str("lot") ?: "", m.str("field"), m.int("count") ?: 0, m.int("bytes") ?: 0)
    }

    /** Every question of the levels (index total, reservable ones included). */
    fun totalCount() = levels.sumOf { it.count }

    /** What the level offers in this edition: all of it in the trial edition, the free questions only otherwise. */
    fun openCount(level: Level) = if (trialOpen) level.count else level.freeCount

    /** The level a game filter draws from (a lycée subject, a university field or an unknown level = none: those come from lots). */
    fun levelFor(f: QuestionFilter): Level? {
        if (f.field != null) return levels.firstOrNull { it.level == f.level && it.track == f.track }
        return levels.firstOrNull { it.track == f.track && it.level == f.level }
    }

    // ---- choice of the files of a game ----

    private fun hash(s: String): Long = MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8)).let { d ->
        var h = 0L; for (i in 0 until 8) h = (h shl 8) or (d[i].toLong() and 0xff); h
    }

    private val orders = HashMap<String, List<FileRef>>()

    /** The files of [level] (of [field] only, for a university/lycée subject) in rotation order: seeded, lots interleaved. */
    private fun order(level: Level, field: String?): List<FileRef> = synchronized(orders) {
        orders.getOrPut(level.key + "/" + (field ?: "") + "/" + trialOpen) {
            val all = (level.files + if (trialOpen) level.reservedFiles else emptyList()).filter { field == null || it.field == field }
            val byLot = all.groupBy { it.lot }
            val lots = byLot.keys.sortedBy { hash("$seed|lot|${level.key}|$it") }
            val queues = lots.map { lot -> byLot.getValue(lot).sortedBy { hash("$seed|file|${it.name}") }.toMutableList() }
            val out = ArrayList<FileRef>(all.size)
            while (out.size < all.size) for (q in queues) if (q.isNotEmpty()) out += q.removeAt(0)
            out
        }
    }

    private fun cursorKey(level: Level, field: String?) = level.key + "/" + (field ?: "")

    private fun cursor(level: Level, field: String?, n: Int): Int {
        val v = rotation.get(cursorKey(level, field))
        val start = v ?: ((hash("$seed|start|${cursorKey(level, field)}|${(rotation as? Rotation.Memory)?.salt ?: 0}") ushr 1) % n).toInt()
        return (start % n)
    }

    /** The files the next game of [level] reads: consecutive in the rotation order, within [maxQuestions] questions and [maxBytes] bytes (at least one). */
    fun selection(level: Level, field: String? = null): List<FileRef> {
        if (level.files.isEmpty()) return emptyList()
        val o = order(level, field)
        if (o.isEmpty()) return emptyList()
        val start = cursor(level, field, o.size)
        val out = ArrayList<FileRef>(); var q = 0; var b = 0
        for (i in o.indices) {
            val f = o[(start + i) % o.size]
            if (out.isNotEmpty() && (q + f.count > maxQuestions || b + f.bytes > maxBytes)) break
            out += f; q += f.count; b += f.bytes
        }
        return out
    }

    /** The next game starts on the files after those of the current one. */
    fun advance(level: Level, field: String? = null) {
        val n = order(level, field).size
        if (n == 0) return
        rotation.set(cursorKey(level, field), (cursor(level, field, n) + selection(level, field).size) % n)
    }

    /**
     * The questions of the current game of [level] (see [selection]); the same ones until [advance]. Called when a game asks for a level,
     * the caller keeps only the result it needs. Old index: the whole (small) level file.
     */
    fun load(level: Level, field: String? = null): QuizBank {
        if (level.files.isEmpty() && level.file.isNotEmpty()) return loadWhole(level)
        val out = ArrayList<Question>(maxQuestions)
        val read = ArrayList<String>(); var bytes = 0
        val o = order(level, field)
        if (o.isNotEmpty()) {
            val start = cursor(level, field, o.size)
            var q = 0; var b = 0
            for (i in o.indices) {
                val f = o[(start + i) % o.size]
                if (read.isNotEmpty() && (q + f.count > maxQuestions || b + f.bytes > maxBytes)) break
                q += f.count; b += f.bytes
                val data = reader(base + f.name) ?: continue          // a missing file (production build without the reserved folder) is skipped
                read += f.name; bytes += data.size
                out += parse(level, String(data, Charsets.UTF_8))
            }
        }
        lastLoad = LoadStats(read, out.size, bytes)
        return QuizBank(out)
    }

    private fun loadWhole(level: Level): QuizBank {
        val data = reader(base + "embedded/" + level.file) ?: return QuizBank(emptyList())
        val qs = parse(level, String(data, Charsets.UTF_8))
        lastLoad = LoadStats(listOf(level.file), qs.size, data.size)
        return QuizBank(qs)
    }

    /** One file (a test or a tool reading a given file). */
    fun loadFile(level: Level, ref: FileRef): List<Question> = reader(base + ref.name)?.let { parse(level, String(it, Charsets.UTF_8)) }.orEmpty()

    private fun parse(level: Level, text: String): List<Question> {
        val root = Json.obj(text)
        val sources = root.list("sources").orEmpty().map { it as? String ?: "" }
        val track = Track.of(root.str("track")) ?: level.track
        val lvl = root.str("level")
        val out = ArrayList<Question>()
        for (r in root.list("q").orEmpty()) {
            val row = r as? List<*> ?: continue
            @Suppress("UNCHECKED_CAST") val choices = (row[5] as List<Any?>).map { it as? String ?: "" }
            val status = row[10] as? String
            val verif = row[11] as? String
            out += Question(
                id = row[0] as String, region = Region.valueOf(row[1] as String), category = row[2] as String,
                difficulty = (row[3] as Number).toInt(), question = row[4] as String, choices = choices, answer = (row[6] as Number).toInt(),
                explanation = row[7] as? String ?: "", source = sources.getOrElse((row[8] as Number).toInt()) { "" },
                review = status != null && status != "approved", track = track, level = lvl, field = row[9] as? String,
                verif = verif, status = status,
                // same policy as the lots: a computed-and-tested answer is playable before the human review (PlayPolicy decides)
                computedOk = verif == "computed" && status != "rejected",
            )
        }
        return out
    }

    private fun read(name: String): String? = reader(base + name)?.let { String(it, Charsets.UTF_8) }

    private fun Map<String, Any?>.str(k: String) = this[k] as? String
    private fun Map<String, Any?>.int(k: String) = (this[k] as? Number)?.toInt()
    private fun Map<String, Any?>.list(k: String) = this[k] as? List<*>

    companion object {
        /** A game never loads more than this many questions … */
        const val MAX_QUESTIONS = 2000
        /** … nor more than this many bytes of JSON (3 MB ≈ a few MB of heap once parsed: the TV is 32-bit). */
        const val MAX_BYTES = 3_000_000
    }
}
