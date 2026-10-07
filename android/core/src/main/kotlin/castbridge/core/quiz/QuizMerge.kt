package castbridge.core.quiz

/**
 * What happens to scores, history and tokens when two devices (TV, phones) meet. Each device keeps its own data locally and
 * works alone; at a meeting the data are merged with rules that are commutative, associative and idempotent (merging twice,
 * or in either order, gives the same result), so no device is « master ». Rules (docs/QUIZ.md, « Fusion des données »):
 * - best scores: union of the entries of both tables (an identical entry counts once), then the best [HighScores] keeps per
 *   board (one board per way of playing and course): the best score per profile and per game wins;
 * - history of questions: per course, a question counts as asked at the MORE RECENT of its two moments, each measured in games
 *   ago on its own device ([QuizHistory.mergeFrom]); when in doubt a question stays « recent », never the opposite;
 * - competition points (« Compétition à points », no value: neither NDEM nor MBOKO; the field keeps its old name `tokens`): last write per player
 *   wins ([mergeBalances]); equal time = the larger balance (deterministic).
 */
object QuizMerge {
    fun mergeScores(a: HighScores, b: HighScores, perBoard: Int = 10): HighScores {
        val out = HighScores(perBoard = perBoard)
        val seen = HashSet<HighScores.Entry>()
        for (hs in listOf(a, b)) for (board in hs.boards()) for (e in hs.top(board, Int.MAX_VALUE)) if (seen.add(e)) out.add(e)
        return out
    }

    /** A competition-points balance with the moment it was written (ms). */
    data class StampedBalance(val player: String, val tokens: Long, val atMs: Long)

    fun mergeBalances(a: List<StampedBalance>, b: List<StampedBalance>): List<StampedBalance> {
        val out = LinkedHashMap<String, StampedBalance>()
        for (x in a + b) {
            val y = out[x.player]
            out[x.player] = if (y == null || x.atMs > y.atMs || (x.atMs == y.atMs && x.tokens > y.tokens)) x else y
        }
        return out.values.toList()
    }

    /** A new history = [a] merged with [b] (neither is changed). */
    fun mergeHistories(a: QuizHistory, b: QuizHistory): QuizHistory = QuizHistory.parse(a.serialize(), a.gap).also { it.mergeFrom(b) }
}
