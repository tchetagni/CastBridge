package castbridge.core.quiz

/**
 * The Quiz bundled in the TV APK, ONE resource file per atomic level (castbridge/quiz/embedded/<key>.json, built by
 * tools/quiz-bank/build_embedded.py) plus a tiny index. A level is read only when a game asks for it and the previous
 * one is released: the reference TV is 32-bit with little memory, so the whole bank is never in memory at once.
 * Only free levels are bundled (never the reserved ones: Tle, L1, L2, L3). Question ids are the lots' ids, so an installed
 * lot of the same level replaces them instead of duplicating them. Statuses are kept as in the lots ("review").
 */
class EmbeddedLevels(
    private val base: String = "/castbridge/quiz/embedded/",
    private val reader: (String) -> ByteArray? = { path -> EmbeddedLevels::class.java.getResourceAsStream(path)?.use { it.readBytes() } },
) {
    data class Level(val key: String, val level: String?, val track: Track, val count: Int, val file: String)

    /** The levels (index only: a few KB). */
    val levels: List<Level> by lazy {
        val text = read("index.json") ?: return@lazy emptyList()
        val root = Json.obj(text)
        root.list("levels").orEmpty().mapNotNull { o ->
            @Suppress("UNCHECKED_CAST") val m = o as? Map<String, Any?> ?: return@mapNotNull null
            Level(m.str("key") ?: return@mapNotNull null, m.str("level"), Track.of(m.str("track")) ?: return@mapNotNull null,
                m.int("count") ?: 0, m.str("file") ?: return@mapNotNull null)
        }
    }

    fun totalCount() = levels.sumOf { it.count }

    /** The level a game filter draws from (a lycée subject, a university field or an unknown level = none: those come from lots). */
    fun levelFor(f: QuestionFilter): Level? {
        if (f.field != null) return levels.firstOrNull { it.level == f.level && it.track == f.track }
        return levels.firstOrNull { it.track == f.track && it.level == f.level }
    }

    /** Parses one level file. Called once per level change; the caller keeps only the result it needs. */
    fun load(level: Level): QuizBank {
        val root = Json.obj(read(level.file) ?: return QuizBank(emptyList()))
        val sources = root.list("sources").orEmpty().map { it as? String ?: "" }
        val track = Track.of(root.str("track")) ?: level.track
        val lvl = root.str("level")
        val out = ArrayList<Question>(level.count)
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
        return QuizBank(out)
    }

    private fun read(name: String): String? = reader(base + name)?.let { String(it, Charsets.UTF_8) }

    private fun Map<String, Any?>.str(k: String) = this[k] as? String
    private fun Map<String, Any?>.int(k: String) = (this[k] as? Number)?.toInt()
    private fun Map<String, Any?>.list(k: String) = this[k] as? List<*>
}
