package castbridge.core.learn

/**
 * Content model of « Apprendre » (format documented in docs/LEARN.md):
 * Pack (exam × subject, or level × subject) → Chapter → Lesson (a « fiche ») → Blocks, plus the pack's Exercises and
 * mock exams (« épreuves blanches »). Everything is immutable and parsed from JSON by [LessonJson].
 */

/** Review status of a lesson or pack. Only VALIDATED is shown as « contenu certifié ». */
enum class ReviewStatus(val key: String, val label: String) {
    DRAFT("draft", "Brouillon (à relire)"), REVIEWED("reviewed", "Relu"), VALIDATED("validated", "Contenu certifié");
    companion object { fun of(k: String?) = values().firstOrNull { it.key == k } }
}

data class Pack(
    val id: String,
    /** Integer version of the pack content; a higher version replaces a lower one. */
    val version: Int,
    val title: String,
    val lang: String,
    val cursus: String,
    val level: String,
    val subject: String,
    /** Exam key ([LearnCatalog.exams]) when the pack prepares an exam. */
    val exam: String? = null,
    /** Exam series the pack covers ("C", "D"...), empty = all. */
    val series: List<String> = emptyList(),
    val programRef: String? = null,
    val authors: List<String> = emptyList(),
    val status: ReviewStatus = ReviewStatus.DRAFT,
    val updatedAt: String? = null,
    val description: String? = null,
    val chapters: List<Chapter> = emptyList(),
    val lessons: List<Lesson> = emptyList(),
    val exercises: List<Exercise> = emptyList(),
    val mockExams: List<MockExamSpec> = emptyList(),
) {
    private val exIndex by lazy { exercises.associateBy { it.id } }
    fun exercise(id: String): Exercise? = exIndex[id]
    fun lesson(id: String): Lesson? = lessons.firstOrNull { it.id == id }
    fun lessonsOf(chapter: String) = lessons.filter { it.chapter == chapter }
    fun exercisesOf(chapter: String) = exercises.filter { it.chapter == chapter }
    val subjectInfo get() = LearnCatalog.subject(subject)
}

data class Chapter(val id: String, val title: String, val order: Int = 0, val programRef: String? = null, val summary: String? = null)

data class Lesson(
    val id: String,
    val chapter: String,
    val title: String,
    val blocks: List<Block>,
    val minutes: Int = 10,
    val objectives: List<String> = emptyList(),
    val prerequisites: List<String> = emptyList(),
    val programRef: String? = null,
    val status: ReviewStatus = ReviewStatus.DRAFT,
    val author: String? = null,
    val source: String? = null,
    val lang: String? = null,
    val readAloud: Boolean? = null,
    /** Graded exercises of the fiche (ids of the pack's exercises): application, approfondissement, examen. */
    val exercises: List<String> = emptyList(),
    /** 5-question self-check (MCQ with 4 choices, exportable to the quiz). */
    val selfCheck: List<String> = emptyList(),
    /** Points the author is not sure of (shown to reviewers, never to children as facts). */
    val reviewNotes: List<String> = emptyList(),
    /** Curriculum graph (additive, optional, docs/CONTENT-ARCHITECTURE.md § 5): the skill taught, its level N0..N4, prerequisite skills, lot scope, media ids. */
    val skill: String? = null,
    val level: String? = null,
    val prereqSkills: List<String> = emptyList(),
    val lot: String? = null,
    val media: List<String> = emptyList(),
)

/** One step of a worked example: text (restricted Markdown) and/or a formula. */
data class Step(val md: String, val tex: String? = null)

sealed class Block {
    open val review: Boolean get() = false
    data class Heading(val text: String) : Block()
    data class Text(val md: String, override val review: Boolean = false) : Block()
    /** Boxed key point: style definition | retenir | attention | methode | objectifs | pieges. */
    data class Key(val style: String, val title: String?, val md: String, val tex: String? = null, override val review: Boolean = false) : Block()
    data class Formula(val tex: String, val caption: String? = null, override val review: Boolean = false) : Block()
    /** Worked example: statement, then steps revealed one by one (OK), then the answer. */
    data class Example(val title: String, val statement: String, val steps: List<Step>, val answer: String?, val figure: Figure? = null, override val review: Boolean = false) : Block()
    data class Illustration(val figure: Figure, val caption: String?, val alt: String, override val review: Boolean = false) : Block()
    /**
     * Video: a file of the TV library / USB drive ("library:<name>") or a URL set by an administrator; null = not provided
     * yet (the block is skipped). Never a download from a platform without the rights.
     */
    data class Video(val title: String, val src: String?, val credit: String? = null, val license: String? = null) : Block()
    /** Read aloud with TextToSpeech (nursery / primary). */
    data class Audio(val text: String, val lang: String) : Block()
    data class ExerciseRef(val id: String) : Block()
    /** « Pour aller plus loin ». */
    data class More(val items: List<String>) : Block()
}

enum class ExerciseKind(val key: String) {
    MCQ("mcq"), TRUE_FALSE("truefalse"), NUMERIC("numeric"), MATCHING("matching"), OPEN("open"), PROBLEM("problem");
    companion object { fun of(k: String?) = values().firstOrNull { it.key == k } }
}

/**
 * Where the exercise sits in a fiche: application, approfondissement, examen (« type examen »), a self-check question,
 * or (additive, docs/CONTENT-ARCHITECTURE.md § 6) the excellence tiers: [EXCELLENCE_CM] = Cameroonian national
 * excellence (level N2), [EXCELLENCE_MONDE] = world-class excellence (levels N3-N4). [rank] orders the difficulty
 * (self-check questions rank like application ones); a reader that meets a tier it does not know uses [HARDEST].
 */
enum class ExerciseTier(val key: String, val label: String, val rank: Int) {
    APPLICATION("application", "Application", 0), DEEPER("approfondissement", "Approfondissement", 1),
    EXAM("examen", "Type examen", 2), SELFCHECK("autoeval", "Auto-évaluation", 0),
    EXCELLENCE_CM("excellence-cm", "Excellence Cameroun", 3), EXCELLENCE_MONDE("excellence-monde", "Excellence monde", 4);
    val excellence: Boolean get() = rank >= 3
    companion object {
        /** The hardest tier this reader knows: what an unknown (future) tier is read as. */
        val HARDEST = EXCELLENCE_MONDE
        fun of(k: String?) = values().firstOrNull { it.key == k }
        /** Tolerant reading: null stays null, an unknown key gives [HARDEST] (never an exception). */
        fun ofOrHardest(k: String?): ExerciseTier? = if (k == null) null else of(k) ?: HARDEST
    }
}

data class Exercise(
    val id: String,
    val chapter: String,
    val kind: ExerciseKind,
    val prompt: String,
    /** Points of the indicative marking scheme (barème); for a PROBLEM, the sum of its parts. */
    val points: Double = 1.0,
    val tier: ExerciseTier = ExerciseTier.APPLICATION,
    val difficulty: Int = 1,
    val tex: String? = null,
    val figure: Figure? = null,
    /** MCQ: the choices, [answerIndex] the right one. */
    val choices: List<String> = emptyList(),
    val answerIndex: Int = -1,
    /** TRUE_FALSE. */
    val answerBool: Boolean? = null,
    /** NUMERIC: the value, the accepted absolute [tolerance], an optional unit. */
    val answerNumber: Double? = null,
    val tolerance: Double = 0.0,
    val unit: String? = null,
    /** MATCHING: the right (left, right) pairs; shown shuffled. */
    val pairs: List<Pair<String, String>> = emptyList(),
    /** OPEN (self-marked against a model answer): the model answer and the marking points. */
    val model: String? = null,
    val rubric: List<String> = emptyList(),
    /** PROBLEM: the sub-questions (each auto-marked or self-marked). */
    val parts: List<Exercise> = emptyList(),
    val explanation: String = "",
    val steps: List<String> = emptyList(),
    val method: String? = null,
    val mistakes: List<String> = emptyList(),
    val review: Boolean = false,
    val reviewNote: String? = null,
    val source: String? = null,
    /** Lesson to reopen when this exercise is failed (« renvoi vers la fiche »). */
    val lesson: String? = null,
    /** Curriculum graph (additive, optional): the skill assessed, its level N0..N4, lot scope, media ids. */
    val skill: String? = null,
    val level: String? = null,
    val lot: String? = null,
    val media: List<String> = emptyList(),
    /** Item calibration: target success rate (0..1) by learner level key ("N0".."N4"); empty = default of [castbridge.core.curriculum.LevelScale]. */
    val calibration: Map<String, Double> = emptyMap(),
) {
    val autoMarked: Boolean get() = kind != ExerciseKind.OPEN && (kind != ExerciseKind.PROBLEM || parts.all { it.autoMarked })
    val totalPoints: Double get() = if (kind == ExerciseKind.PROBLEM) parts.sumOf { it.points } else points
}

/** A mock exam of a pack (« épreuve blanche »): timed sections of exercises, marked out of [outOf]. */
data class MockExamSpec(
    val id: String, val title: String, val minutes: Int, val sections: List<Section>, val outOf: Double = 20.0,
    val instructions: String? = null,
) {
    data class Section(val title: String, val exercises: List<String>)
    val exerciseIds: List<String> get() = sections.flatMap { it.exercises }
}
