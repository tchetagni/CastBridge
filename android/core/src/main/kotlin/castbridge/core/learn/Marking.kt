package castbridge.core.learn

import kotlin.math.abs
import kotlin.random.Random

/** What the student answered (with the remote, the phone, or a click). */
sealed class Answer {
    /** MCQ: index in the ORIGINAL choices (the UI maps the shuffled position back, see [Shuffle]). */
    data class Choice(val index: Int) : Answer()
    data class Bool(val value: Boolean) : Answer()
    /** NUMERIC: the text typed (« 4,8 », « 4.8 », « -3 », « 1/2 »). */
    data class Number(val text: String) : Answer()
    /** MATCHING: left → right chosen. */
    data class Pairs(val map: Map<String, String>) : Answer()
    /** OPEN: self-marking after reading the model answer: 0.0, 0.5 or 1.0 of the points. */
    data class Self(val fraction: Double) : Answer()
    /** PROBLEM: one answer per part (null = not answered). */
    data class Parts(val parts: List<Answer?>) : Answer()
}

/** Points earned on an exercise; [parts] for a problem. */
data class Mark(val earned: Double, val max: Double, val parts: List<Mark> = emptyList(), val answered: Boolean = true) {
    val correct: Boolean get() = answered && earned >= max - 1e-9
    val ratio: Double get() = if (max <= 0) 0.0 else earned / max
}

object Marking {
    /** « 4,8 », « 4.8 », « 1 200 », « -3 », « 3/4 » → number; null if not a number. */
    fun parseNumber(s: String): Double? {
        val t = s.trim().replace(" ", "").replace(" ", "").replace(" ", "").replace(',', '.').replace('−', '-')
        if (t.isEmpty()) return null
        if (t.count { it == '/' } == 1) {
            val (a, b) = t.split('/'); val x = a.toDoubleOrNull() ?: return null; val y = b.toDoubleOrNull() ?: return null
            return if (y == 0.0) null else x / y
        }
        return t.toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    fun mark(x: Exercise, a: Answer?): Mark {
        if (a == null) return if (x.kind == ExerciseKind.PROBLEM) Mark(0.0, x.totalPoints, x.parts.map { mark(it, null) }, false) else Mark(0.0, x.points, answered = false)
        return when (x.kind) {
            ExerciseKind.MCQ -> Mark(if ((a as? Answer.Choice)?.index == x.answerIndex) x.points else 0.0, x.points)
            ExerciseKind.TRUE_FALSE -> Mark(if ((a as? Answer.Bool)?.value == x.answerBool) x.points else 0.0, x.points)
            ExerciseKind.NUMERIC -> {
                val v = (a as? Answer.Number)?.let { parseNumber(it.text) }
                val ok = v != null && x.answerNumber != null && abs(v - x.answerNumber) <= x.tolerance + 1e-9 * maxOf(1.0, abs(x.answerNumber))
                Mark(if (ok) x.points else 0.0, x.points, answered = v != null)
            }
            ExerciseKind.MATCHING -> {
                val m = (a as? Answer.Pairs)?.map.orEmpty()
                val good = x.pairs.count { (l, r) -> m[l] == r }
                // proportional: each right pair is worth its share of the points
                Mark(x.points * good / x.pairs.size.coerceAtLeast(1), x.points)
            }
            ExerciseKind.OPEN -> Mark(x.points * ((a as? Answer.Self)?.fraction ?: 0.0).coerceIn(0.0, 1.0), x.points)
            ExerciseKind.PROBLEM -> {
                val ans = (a as? Answer.Parts)?.parts.orEmpty()
                val ms = x.parts.mapIndexed { i, q -> mark(q, ans.getOrNull(i)) }
                Mark(ms.sumOf { it.earned }, ms.sumOf { it.max }, ms, ms.any { it.answered })
            }
        }
    }

    /** The right answer in words (correction screen). */
    fun rightAnswer(x: Exercise, lang: String = "fr"): String = when (x.kind) {
        ExerciseKind.MCQ -> x.choices.getOrNull(x.answerIndex) ?: ""
        ExerciseKind.TRUE_FALSE -> if (x.answerBool == true) (if (lang == "en") "True" else "Vrai") else (if (lang == "en") "False" else "Faux")
        ExerciseKind.NUMERIC -> Scene.fmt(x.answerNumber ?: 0.0).let { if (lang == "en") it.replace(',', '.') else it } + (x.unit?.let { " $it" } ?: "")
        ExerciseKind.MATCHING -> x.pairs.joinToString(" ; ") { "${it.first} → ${it.second}" }
        ExerciseKind.OPEN -> x.model ?: ""
        ExerciseKind.PROBLEM -> x.parts.mapIndexed { i, q -> "${i + 1}) ${rightAnswer(q, lang)}" }.joinToString("\n")
    }
}

/** Display order of choices / matching rights, stable for a given seed (the same on the TV and the phone remote). */
object Shuffle {
    fun order(n: Int, seed: Long): List<Int> = (0 until n).shuffled(Random(seed))
    fun seed(exerciseId: String, round: Int = 0): Long = exerciseId.hashCode().toLong() * 31 + round
}

/**
 * A lesson cut into TV pages: an intro page (title, objectives, duration, review status), one page per block (videos
 * without a source are skipped), an example keeps one page and reveals its steps one by one with OK, and a last page
 * that leads to the exercises and the self-check. The phone uses the same pages.
 */
class LessonDeck(val pack: Pack, val lesson: Lesson) {
    sealed class Page {
        object Intro : Page()
        data class Content(val block: Block) : Page()
        data class Exercise(val exercise: castbridge.core.learn.Exercise) : Page()
        object End : Page()
    }

    val pages: List<Page> = buildList {
        add(Page.Intro)
        for (b in lesson.blocks) when {
            b is Block.Video && b.src == null -> {}
            b is Block.ExerciseRef -> pack.exercise(b.id)?.let { add(Page.Exercise(it)) }
            else -> add(Page.Content(b))
        }
        add(Page.End)
    }

    /** OK presses a page takes before « next » moves on: an example reveals its steps, then its answer. */
    fun reveals(page: Int): Int = when (val p = pages.getOrNull(page)) {
        is Page.Content -> (p.block as? Block.Example)?.let { it.steps.size + (if (it.answer != null) 1 else 0) } ?: 0
        else -> 0
    }

    val lang: String get() = lesson.lang ?: pack.lang
    val readAloud: Boolean get() = lesson.readAloud ?: LearnCatalog.readAloudByDefault(pack.level)
    fun graded(): List<Exercise> = lesson.exercises.mapNotNull { pack.exercise(it) }
    fun selfCheck(): List<Exercise> = lesson.selfCheck.mapNotNull { pack.exercise(it) }

    /** Text read aloud for a page (TextToSpeech). */
    fun spoken(page: Int, revealed: Int = Int.MAX_VALUE): String = when (val p = pages.getOrNull(page)) {
        Page.Intro -> lesson.title + ". " + lesson.objectives.joinToString(". ") { Markdown.spoken(it, lang) }
        is Page.Content -> when (val b = p.block) {
            is Block.Heading -> b.text
            is Block.Text -> Markdown.spoken(b.md, lang)
            is Block.Key -> listOfNotNull(b.title, Markdown.spoken(b.md, lang), b.tex?.let { runCatching { Tex.parse(it).spoken(lang) }.getOrNull() }).joinToString(". ")
            is Block.Formula -> runCatching { Tex.parse(b.tex).spoken(lang) }.getOrDefault("") + (b.caption?.let { ". $it" } ?: "")
            is Block.Example -> (listOf(b.title, Markdown.spoken(b.statement, lang)) +
                b.steps.take(revealed).map { s -> Markdown.spoken(s.md, lang) + (s.tex?.let { t -> " " + runCatching { Tex.parse(t).spoken(lang) }.getOrDefault("") } ?: "") } +
                (if (revealed > b.steps.size) listOfNotNull(b.answer?.let { Markdown.spoken(it, lang) }) else emptyList())).joinToString(". ")
            is Block.Illustration -> listOfNotNull(b.caption, b.alt.takeIf { it != b.caption }).joinToString(". ")
            is Block.Audio -> b.text
            is Block.More -> b.items.joinToString(". ") { Markdown.spoken(it, lang) }
            is Block.Video -> b.title
            is Block.ExerciseRef -> ""
        }
        is Page.Exercise -> Markdown.spoken(p.exercise.prompt, lang)
        else -> ""
    }
}

/**
 * « Épreuve blanche »: timed, answers kept until the end, then marked out of 20 with the detail of lost points and the
 * fiches to revise. Self-marked (open) questions are marked by the student on the correction screen ([selfMark]).
 */
class MockExamSession(val pack: Pack, val spec: MockExamSpec, val startedAt: Long, private val clock: () -> Long = { System.currentTimeMillis() }) {
    val exercises: List<Exercise> = spec.exerciseIds.mapNotNull { pack.exercise(it) }
    private val answers = LinkedHashMap<String, Answer>()
    var finishedAt: Long? = null; private set
    val durationMs: Long get() = spec.minutes * 60_000L
    val remainingMs: Long get() = (startedAt + durationMs - (finishedAt ?: clock())).coerceAtLeast(0)
    val timeUp: Boolean get() = remainingMs <= 0

    fun answer(id: String, a: Answer): Boolean {
        if (finishedAt != null || timeUp || exercises.none { it.id == id }) return false
        answers[id] = a; return true
    }
    fun answerOf(id: String): Answer? = answers[id]
    fun answered(): Int = exercises.count { answers.containsKey(it.id) }

    fun finish(): Result { if (finishedAt == null) finishedAt = minOf(clock(), startedAt + durationMs); return result() }

    /** The student marks an open question (or a part of a problem) after reading the model answer. */
    fun selfMark(id: String, fraction: Double, part: Int? = null) {
        val x = exercises.firstOrNull { it.id == id } ?: return
        if (part == null) { answers[id] = Answer.Self(fraction); return }
        val cur: MutableList<Answer?> = (answers[id] as? Answer.Parts)?.parts?.toMutableList() ?: MutableList(x.parts.size) { null }
        while (cur.size < x.parts.size) cur.add(null)
        cur[part] = Answer.Self(fraction); answers[id] = Answer.Parts(cur)
    }

    class Line(val exercise: Exercise, val mark: Mark, val section: String)
    class Result(val lines: List<Line>, val earned: Double, val max: Double, val outOf: Double) {
        /** The mark out of [outOf] (20), rounded to the quarter point. */
        val score: Double get() = if (max <= 0) 0.0 else Math.round(earned / max * outOf * 4) / 4.0
        /** Fiches to revise: lessons of the exercises where points were lost, most points lost first. */
        val revise: List<String> get() = lines.filter { it.mark.earned < it.mark.max }.sortedByDescending { it.mark.max - it.mark.earned }
            .mapNotNull { it.exercise.lesson }.distinct()
        val pendingSelfMarks: Int get() = lines.count { l -> l.exercise.kind == ExerciseKind.OPEN || l.exercise.parts.any { it.kind == ExerciseKind.OPEN } }
    }

    fun result(): Result {
        val section = spec.sections.flatMap { s -> s.exercises.map { it to s.title } }.toMap()
        val lines = exercises.map { Line(it, Marking.mark(it, answers[it.id]), section[it.id] ?: "") }
        return Result(lines, lines.sumOf { it.mark.earned }, lines.sumOf { it.mark.max }, spec.outOf)
    }

    companion object {
        /**
         * A mock exam assembled from the pack when it has none: exam-tier exercises (never self-check nor review items),
         * one chapter after the other, until about [targetPoints]; marked out of 20 whatever the total.
         */
        fun auto(pack: Pack, seed: Long, minutes: Int = 60, targetPoints: Double = 20.0): MockExamSpec? {
            val pool = pack.exercises.filter { !it.review && it.tier != ExerciseTier.SELFCHECK && it.parts.none { p -> p.review } }
                .sortedByDescending { it.tier.rank }
            if (pool.isEmpty()) return null
            val rng = Random(seed)
            val byChapter = pack.chapters.associate { c -> c.id to pool.filter { it.chapter == c.id }.shuffled(rng).toMutableList() }
            val picked = ArrayList<Exercise>(); var total = 0.0
            while (total < targetPoints && byChapter.values.any { it.isNotEmpty() }) {
                for (c in pack.chapters) { val l = byChapter[c.id] ?: continue; if (l.isNotEmpty() && total < targetPoints) { val x = l.removeAt(0); picked += x; total += x.totalPoints } }
            }
            return MockExamSpec("${pack.id}-auto-$seed", "Épreuve blanche", minutes,
                pack.chapters.mapNotNull { c -> picked.filter { it.chapter == c.id }.map { it.id }.takeIf { it.isNotEmpty() }?.let { MockExamSpec.Section(c.title, it) } },
                20.0, "Sujet assemblé automatiquement à partir des exercices du pack.")
        }
    }
}
