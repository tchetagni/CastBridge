package castbridge.core.learn

import castbridge.core.quiz.Question
import castbridge.core.quiz.QuizBank
import castbridge.core.quiz.QuizCatalog
import castbridge.core.quiz.Region
import castbridge.core.quiz.Track

/**
 * The self-check questions of the fiches (MCQ with 4 choices) in the quiz format: the quiz's school tracks can play
 * them (Duel in class, solo practice). Only levels the quiz knows are exported; review items stay out.
 */
object LearnQuiz {
    fun questions(pack: Pack): List<Question> {
        val level = QuizCatalog.levelOf(pack.level) ?: return emptyList()
        val subject = LearnCatalog.subject(pack.subject)?.label(pack.lang) ?: pack.subject
        return pack.lessons.flatMap { l -> l.selfCheck.mapNotNull { pack.exercise(it) }.map { l to it } }
            .filter { (_, x) -> x.kind == ExerciseKind.MCQ && x.choices.size == 4 && !x.review }
            .map { (l, x) ->
                Question(
                    id = "learn-${x.id}", region = Region.WORLD, category = subject, difficulty = x.difficulty.coerceIn(1, 5),
                    question = Markdown.plain(x.prompt).let { if (it.trimEnd().endsWith("?")) it else "$it ?" },
                    choices = x.choices.map { Markdown.plain(it) }, answer = x.answerIndex,
                    explanation = Markdown.plain(x.explanation), source = "Apprendre — ${l.title}",
                    track = level.track, level = level.key,
                    field = if (level.track == Track.HIGHER) QuizCatalog.fields.firstOrNull { it.key == pack.subject }?.key else null, lang = pack.lang,
                )
            }
    }

    fun bank(packs: List<Pack>): QuizBank = QuizBank(packs.flatMap { questions(it) })

    @Suppress("unused") private val keep = Track.GENERAL
}
