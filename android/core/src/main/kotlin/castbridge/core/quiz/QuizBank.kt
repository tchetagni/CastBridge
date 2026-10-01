package castbridge.core.quiz

import castbridge.core.quiz.Json.bool
import castbridge.core.quiz.Json.int
import castbridge.core.quiz.Json.list
import castbridge.core.quiz.Json.str
import kotlin.random.Random

enum class Region(val label: String) { CM("Cameroun"), AF("Afrique"), WORLD("Monde") }

/** Parcours: general knowledge, or the Cameroonian school curriculum (primary, secondary, higher up to the Licence). */
enum class Track(val key: String, val label: String) {
    GENERAL("general", "Culture générale"), PRIMARY("primary", "Primaire"), SECONDARY("secondary", "Secondaire"), HIGHER("higher", "Supérieur");
    companion object { fun of(key: String?): Track? = values().firstOrNull { it.key == key } }
}

data class Question(
    /** Stable id (slug such as "cm-geo-001", or a UUID coming from the future question server). */
    val id: String,
    val region: Region,
    val category: String,
    /** 1 (very easy) .. 5 (expert), relative to the level for school questions. */
    val difficulty: Int,
    val question: String,
    val choices: List<String>,
    /** Index of the right choice in [choices]. */
    val answer: Int,
    val explanation: String,
    val source: String,
    /** Not certain enough to be asked: excluded from games unless explicitly included. */
    val review: Boolean = false,
    val track: Track = Track.GENERAL,
    /** School level ("CM2", "3e", "Tle", "L1"…), null for general knowledge. */
    val level: String? = null,
    /** Field of study at university ("droit", "economie"…), null otherwise. */
    val field: String? = null,
    val lang: String = "fr",
    /** ISO date of the last edit (exchange with the question server), null for the bundled bank. */
    val updatedAt: String? = null,
    /** How the answer was checked: "computed" (the answer comes from a calculation, tested), "fact" (table of sourced facts), "import" (written then imported), null = bundled/reviewed. */
    val verif: String? = null,
    /** Curriculum graph (additive, optional, docs/CONTENT-ARCHITECTURE.md § 5): skill id, learner level "N0".."N4" (`level` stays the school class), lot scope, target success rate by learner level. */
    val skill: String? = null,
    val nlevel: String? = null,
    val lot: String? = null,
    val calib: Map<String, Double> = emptyMap(),
) {
    /** Same question with its choices reordered by [rng] (the bank's answer positions do not leak into the game). */
    fun shuffled(rng: Random): Question {
        val order = choices.indices.shuffled(rng)
        return copy(choices = order.map { choices[it] }, answer = order.indexOf(answer))
    }
}

/** Which questions a game draws from: a track, and for school tracks a level and (university) a field. */
data class QuestionFilter(val track: Track = Track.GENERAL, val level: String? = null, val field: String? = null) {
    fun matches(q: Question) = q.track == track && (level == null || q.level == level) && (field == null || q.field == field)
    val label: String get() = listOfNotNull(track.label, level?.let { QuizCatalog.levelLabel(it) }, this.field?.let { QuizCatalog.fieldLabel(it) }).joinToString(" · ")
    companion object { val GENERAL = QuestionFilter() }
}

/**
 * The question bank and the draw of a game. [draw] returns questions of increasing difficulty, never a question already
 * asked in the session (while possible), reproducible for a given seed; for general knowledge it keeps
 * 70 % Cameroon / 20 % Africa / 10 % World (±1 question).
 */
class QuizBank(val all: List<Question>) {
    val playable: List<Question> by lazy { all.filter { !it.review } }

    fun count(filter: QuestionFilter, includeReview: Boolean = false) = poolOf(filter, includeReview).size

    /** Problems of the bank (empty = valid): what the tests and the loader check. */
    fun validate(): List<String> {
        val errs = ArrayList<String>()
        val ids = HashSet<String>(); val texts = HashSet<String>()
        for (q in all) {
            val w = "question ${q.id}"
            if (q.id.isBlank()) errs += "$w: id vide"
            if (!ids.add(q.id)) errs += "$w: id en double"
            if (!texts.add(norm(q.question) + "|" + q.level + "|" + q.field)) errs += "$w: question en double"
            if (q.question.isBlank()) errs += "$w: texte vide"
            if (q.category.isBlank()) errs += "$w: catégorie vide"
            if (q.explanation.isBlank()) errs += "$w: explication vide"
            if (q.source.isBlank()) errs += "$w: source vide"
            if (q.difficulty !in 1..5) errs += "$w: difficulté hors 1..5"
            q.nlevel?.let { if (it !in castbridge.core.curriculum.Level.KEYS) errs += "$w: niveau $it inconnu (N0..N4)" }
            q.calib.forEach { (k, v) -> if (k !in castbridge.core.curriculum.Level.KEYS || v !in 0.0..1.0) errs += "$w: calibration $k=$v invalide" }
            if (q.choices.size != 4) errs += "$w: il faut 4 choix"
            if (q.choices.any { it.isBlank() }) errs += "$w: choix vide"
            if (q.choices.map(::norm).toSet().size != q.choices.size) errs += "$w: choix en double"
            if (q.answer !in q.choices.indices) errs += "$w: index de réponse invalide"
            if (q.track != Track.GENERAL && q.level == null) errs += "$w: niveau manquant pour le parcours ${q.track.key}"
            if (q.track == Track.HIGHER && q.field == null) errs += "$w: filière manquante"
            if (q.level != null && QuizCatalog.levelOf(q.level)?.track != q.track) errs += "$w: niveau ${q.level} inconnu pour ${q.track.key}"
            if (q.field != null && QuizCatalog.fields.none { it.key == q.field }) errs += "$w: filière ${q.field} inconnue"
        }
        return errs
    }

    /**
     * Draws [count] questions (fewer if the filter does not have that many) and forgets how it did. See [drawDetailed].
     */
    fun draw(count: Int = 15, seed: Long = System.nanoTime(), exclude: Set<String> = emptySet(),
             filter: QuestionFilter = QuestionFilter.GENERAL, includeReview: Boolean = false, shuffleChoices: Boolean = true,
             history: QuestionHistory? = null, minGapGames: Int = DEFAULT_MIN_GAP_GAMES): List<Question> =
        drawDetailed(count, seed, exclude, filter, includeReview, shuffleChoices, history, minGapGames).questions

    /** What a draw had to do to cope with a bank too small for the history (repeats = 0 and quotaBroken = 0: a perfect draw). */
    data class DrawReport(
        val requested: Int, val drawn: Int,
        /** Questions asked again although asked less than the minimum gap ago (chosen among the least recently asked). */
        val repeats: Int = 0,
        /** The shortest gap, in games, of a repeated question (null = no repeat). */
        val shortestGap: Int? = null,
        /** Positions filled from another region because the wanted region had no question at all. */
        val quotaBroken: Int = 0,
        /** Positions whose difficulty is 2 or more away from the wanted one. */
        val difficultyOff: Int = 0,
    ) { val clean: Boolean get() = repeats == 0 && quotaBroken == 0 }

    class Draw(val questions: List<Question>, val report: DrawReport)

    /**
     * Draws [count] questions of increasing difficulty, reproducible for a given seed.
     *
     * - [exclude] = ids already asked in this session; [history] = what this player (or these players) were asked in the
     *   last games: a question asked less than [minGapGames] games ago is not drawn while a question of the same region
     *   (general knowledge: the 70/20/10 quota is kept) remains that was not.
     * - Bank too small for that: the region's question that was asked the LONGEST ago is taken (never a random recent one),
     *   among those within 1 of the wanted difficulty when possible so that the climb holds; the [DrawReport] says so.
     * - Positions get a target difficulty 1..5 rising with the position; the result is sorted by difficulty.
     */
    fun drawDetailed(count: Int = 15, seed: Long = System.nanoTime(), exclude: Set<String> = emptySet(),
                     filter: QuestionFilter = QuestionFilter.GENERAL, includeReview: Boolean = false, shuffleChoices: Boolean = true,
                     history: QuestionHistory? = null, minGapGames: Int = DEFAULT_MIN_GAP_GAMES): Draw {
        require(count > 0)
        val rng = Random(seed)
        val pool = poolOf(filter, includeReview)
        val n = minOf(count, pool.size)
        if (n == 0) return Draw(emptyList(), DrawReport(count, 0))
        val course = filter.courseKey
        val never = QuestionHistory.NEVER
        val ages = HashMap<String, Int>(pool.size * 2)
        for (q in pool) ages[q.id] = if (q.id in exclude) 0 else history?.age(course, q.id) ?: never
        fun age(q: Question) = ages.getValue(q.id)
        fun fresh(q: Question) = age(q) >= minGapGames
        val byRegion = pool.groupBy { it.region }
        val slots: List<Region?> = if (filter.track == Track.GENERAL) {
            // x.5 worlds: go the way the bank has the most room (fresh World vs fresh Cameroon), else by the seed
            val fw = byRegion[Region.WORLD].orEmpty().count(::fresh) / maxOf(0.1 * n, 0.001)
            val fc = byRegion[Region.CM].orEmpty().count(::fresh) / maxOf(0.7 * n, 0.001)
            val quota = quotas(n, rng, worldUp = if (Math.abs(fw - fc) < 1e-9) null else fw > fc)
            Region.values().flatMap { r -> List(quota.getValue(r)) { r } }.shuffled(rng)
        } else List(n) { null }
        val used = HashSet<String>()
        val picked = ArrayList<Question>(n)
        var repeats = 0; var quotaBroken = 0; var off = 0; var shortest: Int? = null
        for (pos in 0 until n) {
            val target = targetDifficulty(pos, n)
            val region = slots[pos]
            var broke = false
            var cands = (if (region == null) pool else byRegion[region].orEmpty()).filter { it.id !in used }
            if (cands.isEmpty()) { cands = pool.filter { it.id !in used }; broke = region != null }   // region has nothing left at all
            if (cands.isEmpty()) break
            val freshOnes = cands.filter(::fresh)
            val q: Question
            if (freshOnes.isNotEmpty()) {
                val best = freshOnes.minOf { Math.abs(it.difficulty - target) }
                q = freshOnes.filter { Math.abs(it.difficulty - target) == best }.random(rng)
            } else {
                // not enough questions for the gap: the longest-unseen one, near the wanted difficulty if possible
                val near = cands.filter { Math.abs(it.difficulty - target) <= 1 }.ifEmpty { cands }
                val oldest = near.maxOf(::age)
                val tied = near.filter { age(it) == oldest }
                val best = tied.minOf { Math.abs(it.difficulty - target) }
                q = tied.filter { Math.abs(it.difficulty - target) == best }.random(rng)
                repeats++
                val gap = age(q)
                shortest = if (shortest == null) gap else minOf(shortest, gap)
            }
            if (broke) quotaBroken++
            if (Math.abs(q.difficulty - target) >= 2) off++
            used += q.id; picked += q
        }
        val sorted = picked.withIndex().sortedWith(compareBy({ it.value.difficulty }, { it.index })).map { it.value }
        val out = if (shuffleChoices) sorted.map { it.shuffled(rng) } else sorted
        return Draw(out, DrawReport(count, out.size, repeats, shortest, quotaBroken, off))
    }

    /**
     * How much of the bank this player has not been asked about lately. [fresh] = questions not asked in the last
     * [minGapGames] games (what the next draw picks from), [gamesLeft] = how many games of [perGame] questions they
     * allow without any repeat (general knowledge: the 70/20/10 split limits it), [capacityGames] = the same for the whole
     * pool, history ignored: « banque suffisante pour N parties sans répétition ».
     */
    data class Freshness(val pool: Int, val fresh: Int, val freshByRegion: Map<Region, Int>, val gamesLeft: Int, val capacityGames: Int,
                         val minGapGames: Int) {
        /** The bank guarantees the wanted gap (capacity >= [minGapGames] games). */
        val sufficient: Boolean get() = capacityGames >= minGapGames
    }

    fun remainingFresh(filter: QuestionFilter, history: QuestionHistory? = null, minGapGames: Int = DEFAULT_MIN_GAP_GAMES,
                       perGame: Int = 15, includeReview: Boolean = false): Freshness {
        val pool = poolOf(filter, includeReview)
        val course = filter.courseKey
        val freshQs = pool.filter { history == null || history.age(course, it.id) >= minGapGames }
        fun games(qs: List<Question>): Int =
            if (filter.track == Track.GENERAL) {
                val by = qs.groupingBy { it.region }.eachCount()
                minOf(((by[Region.CM] ?: 0) / (0.7 * perGame)).toInt(), ((by[Region.AF] ?: 0) / (0.2 * perGame)).toInt(),
                    ((by[Region.WORLD] ?: 0) / (0.1 * perGame)).toInt())
            } else qs.size / perGame
        return Freshness(pool.size, freshQs.size, freshQs.groupingBy { it.region }.eachCount(), games(freshQs), games(pool), minGapGames)
    }

    private val pools = java.util.concurrent.ConcurrentHashMap<String, List<Question>>()
    private fun poolOf(filter: QuestionFilter, includeReview: Boolean): List<Question> =
        pools.getOrPut(filter.courseKey + if (includeReview) "+r" else "") { (if (includeReview) all else playable).filter { filter.matches(it) } }

    /** This bank plus [other]; a question of [other] replaces one of this bank with the same id. */
    fun merge(other: QuizBank): QuizBank {
        val ids = other.all.map { it.id }.toSet()
        return QuizBank(all.filter { it.id !in ids } + other.all)
    }

    companion object {
        /** Current version of the exchange format (see docs/QUIZ.md). Readers accept this version and older ones. */
        const val FORMAT_VERSION = 2

        /** 1..5, rising evenly with the position (15 questions: 3 per level). */
        fun targetDifficulty(pos: Int, count: Int): Int = 1 + (pos * 5) / count

        /** 70/20/10: Africa and World are rounded, Cameroon takes the rest; for 15 the World share alternates 1 or 2 (seeded). */
        fun quotas(count: Int, rng: Random, worldUp: Boolean? = null): Map<Region, Int> {
            val af = Math.round(count * 0.2).toInt()
            val exactWorld = count * 0.1
            val world = if (exactWorld % 1.0 == 0.5) (exactWorld.toInt() + (worldUp?.let { if (it) 1 else 0 } ?: rng.nextInt(2))) else Math.round(exactWorld).toInt()
            val cm = count - af - world
            return mapOf(Region.CM to cm, Region.AF to af, Region.WORLD to world)
        }

        private fun norm(s: String) = s.trim().lowercase().replace(Regex("\\s+"), " ")

        /** Parses the exchange format (version ≤ [FORMAT_VERSION]). Throws [Json.ParseError] with the faulty question. */
        fun parse(json: String, computedPlayable: Boolean = false): QuizBank {
            val root = Json.obj(json)
            val v = root.int("version") ?: 1
            if (v > FORMAT_VERSION) throw Json.ParseError("format version $v not supported (max $FORMAT_VERSION)")
            val qs = root.list("questions") ?: throw Json.ParseError("missing \"questions\"")
            return QuizBank(qs.mapIndexed { i, o ->
                @Suppress("UNCHECKED_CAST")
                val m = o as? Map<String, Any?> ?: throw Json.ParseError("question #$i is not an object")
                fun req(k: String) = m.str(k) ?: throw Json.ParseError("question #$i: missing \"$k\"")
                val status = m.str("status")
                Question(
                    id = req("id"),
                    region = runCatching { Region.valueOf(req("region")) }.getOrElse { throw Json.ParseError("question #$i: bad region") },
                    category = req("category"),
                    difficulty = m.int("difficulty") ?: throw Json.ParseError("question #$i: missing difficulty"),
                    question = req("question"),
                    choices = (m.list("choices") ?: throw Json.ParseError("question #$i: missing choices")).map { it as? String ?: "" },
                    answer = m.int("answer") ?: throw Json.ParseError("question #$i: missing answer"),
                    explanation = m.str("explanation").orEmpty(),
                    source = m.str("source").orEmpty(),
                    // `computedPlayable` (content packs): a question whose answer was computed and tested may be played before a human review
                    review = if (computedPlayable && m.str("verif") == "computed" && status != "rejected") false
                        else (m.bool("review") ?: false) || (status != null && status != "approved"),
                    verif = m.str("verif"),
                    track = m.str("track")?.let { Track.of(it) ?: throw Json.ParseError("question #$i: bad track") } ?: Track.GENERAL,
                    level = m.str("level"),
                    field = m.str("field"),
                    lang = m.str("lang") ?: "fr",
                    updatedAt = m.str("updatedAt"),
                    skill = m.str("skill"), nlevel = m.str("nlevel"), lot = m.str("lot"),
                    calib = (m["calib"] as? Map<*, *>)?.mapNotNull { (k, v) -> (k as? String)?.let { kk -> (v as? Number)?.toDouble()?.let { kk to it } } }?.toMap() ?: emptyMap(),
                )
            })
        }
    }
}

/**
 * The levels and fields offered on the TV (Mode → Parcours → Niveau → Filière). Levels without questions yet are
 * shown as « bientôt » : adding questions with that `level` / `field` to the bank is enough to open them.
 */
object QuizCatalog {
    data class Level(val key: String, val label: String, val track: Track)
    data class Field(val key: String, val label: String)

    val levels: List<Level> = listOf(
        // francophone primary, then anglophone
        "SIL" to "SIL", "CP" to "CP", "CE1" to "CE1", "CE2" to "CE2", "CM1" to "CM1", "CM2" to "CM2",
    ).map { Level(it.first, it.second, Track.PRIMARY) } +
        (1..6).map { Level("Class $it", "Class $it", Track.PRIMARY) } +
        listOf("6e", "5e", "4e", "3e", "2nde", "1re", "Tle").map { Level(it, it, Track.SECONDARY) } +
        (1..5).map { Level("Form $it", "Form $it", Track.SECONDARY) } +
        listOf(Level("Lower Sixth", "Lower Sixth", Track.SECONDARY), Level("Upper Sixth", "Upper Sixth", Track.SECONDARY)) +
        listOf("L1", "L2", "L3").map { Level(it, it, Track.HIGHER) }

    val fields: List<Field> = listOf(
        "droit" to "Droit", "economie" to "Économie", "mathematiques" to "Mathématiques", "physique" to "Physique",
        "psychologie" to "Psychologie", "geographie" to "Géographie", "litterature" to "Littérature", "histoire" to "Histoire",
        "informatique" to "Informatique", "chimie" to "Chimie", "biologie" to "Biologie", "philosophie" to "Philosophie",
        "sociologie" to "Sociologie",
    ).map { Field(it.first, it.second) }

    fun levels(track: Track) = levels.filter { it.track == track }
    fun levelOf(key: String) = levels.firstOrNull { it.key == key }
    fun levelLabel(key: String) = levelOf(key)?.label ?: key
    fun fieldLabel(key: String) = fields.firstOrNull { it.key == key }?.label ?: key
}
