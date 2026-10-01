package castbridge.core.content

import castbridge.core.learn.Exercise
import castbridge.core.learn.ExerciseKind
import castbridge.core.quiz.Question
import java.math.BigDecimal
import java.security.MessageDigest

/**
 * Content hash of a question or exercise: it changes when the text, the choices, the right answer or the explanation change,
 * and not when the choices are only shuffled. A validation record or a report made on one hash does not apply to another
 * (docs/CONTENT-VALIDATION.md § 2). tools/content-validation/cbvalidate.py implements the SAME recipe (test vectors in both).
 */
object ContentHash {
    const val LENGTH = 16
    private const val SEP = "\u001f"
    private const val SEP2 = "\u001e"

    private val WS = Regex("[ \\t\\n\\r\\u000b\\u000c]+")
    private fun isWs(c: Char) = c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\u000b' || c == '\u000c'

    /** Runs of ASCII white space (space, tab, line breaks, VT, FF) become one space; the ends are trimmed. */
    fun norm(s: String?): String = (s ?: "").replace(WS, " ").trim(::isWs)

    /** First [LENGTH] hex digits of the SHA-256 of the normalized [parts] joined by U+001F. */
    fun of(vararg parts: String): String =
        MessageDigest.getInstance("SHA-256").digest(parts.joinToString(SEP) { norm(it) }.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }.take(LENGTH)

    private fun list(xs: List<String>) = xs.map(::norm).sorted().joinToString(SEP2)

    fun question(q: Question): String =
        of("q", q.question, list(q.choices), q.choices.getOrElse(q.answer) { "" }, q.explanation)

    /** Number as plain decimal text without trailing zeros ("2.50" → "2.5", 3.0 → "3"). */
    fun num(d: Double): String = BigDecimal(d.toString()).stripTrailingZeros().toPlainString()

    fun exercise(x: Exercise): String {
        val answer = when (x.kind) {
            ExerciseKind.MCQ -> x.choices.getOrElse(x.answerIndex) { "" }
            ExerciseKind.TRUE_FALSE -> x.answerBool?.toString() ?: ""
            ExerciseKind.NUMERIC -> (x.answerNumber?.let(::num) ?: "") + "|" + num(x.tolerance)
            ExerciseKind.MATCHING -> list(x.pairs.map { it.first + "=" + it.second })
            ExerciseKind.OPEN -> x.model ?: ""
            ExerciseKind.PROBLEM -> x.parts.joinToString(",") { exercise(it) }
        }
        return of("x", x.kind.key, x.prompt, x.tex ?: "", list(x.choices), answer, x.explanation)
    }
}
