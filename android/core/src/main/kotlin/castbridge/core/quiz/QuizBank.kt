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
    val playable: List<Question> get() = all.filter { !it.review }

    fun count(filter: QuestionFilter, includeReview: Boolean = false) = (if (includeReview) all else playable).count { filter.matches(it) }

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
     * Draws [count] questions (fewer if the filter does not have that many). [exclude] = ids already asked in this
     * session (reused only if the pool runs dry). Positions get a target difficulty 1..5 rising with the position;
     * the result is sorted by difficulty.
     */
    fun draw(count: Int = 15, seed: Long = System.nanoTime(), exclude: Set<String> = emptySet(),
             filter: QuestionFilter = QuestionFilter.GENERAL, includeReview: Boolean = false, shuffleChoices: Boolean = true): List<Question> {
        require(count > 0)
        val rng = Random(seed)
        val pool = (if (includeReview) all else playable).filter { filter.matches(it) }
        val n = minOf(count, pool.size)
        if (n == 0) return emptyList()
        val slots: List<Region?> = if (filter.track == Track.GENERAL) {
            val quota = quotas(n, rng)
            Region.values().flatMap { r -> List(quota.getValue(r)) { r } }.shuffled(rng)
        } else List(n) { null }
        val used = HashSet<String>()
        val picked = ArrayList<Question>(n)
        for (pos in 0 until n) {
            val target = targetDifficulty(pos, n)
            val region = slots[pos]
            val inRegion = if (region == null) pool else pool.filter { it.region == region }
            var cands = inRegion.filter { it.id !in used && it.id !in exclude }
            if (cands.isEmpty()) cands = inRegion.filter { it.id !in used }                 // session exhausted: allow a repeat
            if (cands.isEmpty()) cands = pool.filter { it.id !in used && it.id !in exclude } // region empty: any other
            if (cands.isEmpty()) cands = pool.filter { it.id !in used }
            if (cands.isEmpty()) break
            val best = cands.minOf { Math.abs(it.difficulty - target) }
            val q = cands.filter { Math.abs(it.difficulty - target) == best }.random(rng)
            used += q.id; picked += q
        }
        val sorted = picked.withIndex().sortedWith(compareBy({ it.value.difficulty }, { it.index })).map { it.value }
        return if (shuffleChoices) sorted.map { it.shuffled(rng) } else sorted
    }

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
        fun quotas(count: Int, rng: Random): Map<Region, Int> {
            val af = Math.round(count * 0.2).toInt()
            val exactWorld = count * 0.1
            val world = if (exactWorld % 1.0 == 0.5) (exactWorld.toInt() + rng.nextInt(2)) else Math.round(exactWorld).toInt()
            val cm = count - af - world
            return mapOf(Region.CM to cm, Region.AF to af, Region.WORLD to world)
        }

        private fun norm(s: String) = s.trim().lowercase().replace(Regex("\\s+"), " ")

        /** Parses the exchange format (version ≤ [FORMAT_VERSION]). Throws [Json.ParseError] with the faulty question. */
        fun parse(json: String): QuizBank {
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
                    review = (m.bool("review") ?: false) || (status != null && status != "approved"),
                    track = m.str("track")?.let { Track.of(it) ?: throw Json.ParseError("question #$i: bad track") } ?: Track.GENERAL,
                    level = m.str("level"),
                    field = m.str("field"),
                    lang = m.str("lang") ?: "fr",
                    updatedAt = m.str("updatedAt"),
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
