package castbridge.core.quiz

/**
 * « Duel / Salle » mode: every player answers the same question at the same time on a phone; points depend on
 * being right and on speed, measured with the server's clock (never the phone's). Pure: the clock is passed in.
 *
 * READY → QUESTION → REVEAL → BOARD (standings) → QUESTION … → REVEAL (last) → FINISHED.
 */
class QuizDuel(
    val questions: List<Question>,
    val questionMs: Long = 20_000,
    val revealMs: Long = 6_000,
    val boardMs: Long = 6_000,
) {
    enum class Phase { READY, QUESTION, REVEAL, BOARD, FINISHED }
    enum class Result { OK, SAME, ALREADY_ANSWERED, CLOSED, UNKNOWN_QUESTION }
    data class Answer(val choice: Int, val atMs: Long)
    data class Outcome(val choice: Int?, val correct: Boolean, val points: Int, val ms: Long?)

    init { require(questions.isNotEmpty()) }

    var phase = Phase.READY; private set
    var index = 0; private set
    var openedAt = 0L; private set
    /** When the current phase ends by itself (server time). */
    var phaseEnd = 0L; private set
    private val scores = LinkedHashMap<String, Int>()
    private val answers = LinkedHashMap<String, Answer>()
    /** Result of the current (revealed) question per player. */
    var outcomes: Map<String, Outcome> = emptyMap(); private set
    /** Ranks before the last reveal, for the animated standings. */
    var previousRanks: Map<String, Int> = emptyMap(); private set

    val question get() = questions[index]
    val isLast get() = index == questions.size - 1

    fun addPlayer(id: String) { scores.putIfAbsent(id, 0) }
    fun score(id: String) = scores[id] ?: 0
    fun answered(): Set<String> = answers.keys.toSet()
    fun answerOf(id: String): Int? = answers[id]?.choice

    fun start(now: Long): Boolean {
        if (phase != Phase.READY) return false
        open(0, now); return true
    }

    /** First answer counts; the same answer again is a harmless repeat (retries after a network cut). */
    fun answer(id: String, questionId: String, choice: Int, now: Long): Result {
        if (questionId != question.id) return Result.UNKNOWN_QUESTION
        if (phase != Phase.QUESTION || now > phaseEnd || choice !in 0..3) return Result.CLOSED
        answers[id]?.let { return if (it.choice == choice) Result.SAME else Result.ALREADY_ANSWERED }
        addPlayer(id)
        answers[id] = Answer(choice, now)
        return Result.OK
    }

    /**
     * Advances on time: the question closes when its time is up or when every player in [active] has answered;
     * reveal and standings last [revealMs] / [boardMs]. Returns true if the phase changed.
     */
    fun tick(now: Long, active: Set<String>): Boolean = when (phase) {
        Phase.QUESTION -> if (now >= phaseEnd || (active.isNotEmpty() && answers.keys.containsAll(active))) { close(now); true } else false
        Phase.REVEAL -> if (now >= phaseEnd) { afterReveal(now); true } else false
        Phase.BOARD -> if (now >= phaseEnd) { open(index + 1, now); true } else false
        else -> false
    }

    /** The host (OK on the remote) skips the wait of the current phase. */
    fun skip(now: Long): Boolean = when (phase) {
        Phase.QUESTION -> { close(now); true }
        Phase.REVEAL -> { afterReveal(now); true }
        Phase.BOARD -> { open(index + 1, now); true }
        else -> false
    }

    /** Standings: (player id, score), best first; ties keep join order. */
    fun ranking(): List<Pair<String, Int>> = scores.entries.map { it.key to it.value }.sortedByDescending { it.second }

    fun remainingMs(now: Long) = if (phase == Phase.FINISHED || phase == Phase.READY) 0 else maxOf(0, phaseEnd - now)

    private fun open(i: Int, now: Long) {
        index = i; answers.clear(); outcomes = emptyMap()
        openedAt = now; phaseEnd = now + questionMs; phase = Phase.QUESTION
    }

    private fun close(now: Long) {
        previousRanks = ranks()
        val q = question
        outcomes = scores.keys.associateWith { id ->
            val a = answers[id]
            val ok = a != null && a.choice == q.answer
            val ms = a?.let { it.atMs - openedAt }
            Outcome(a?.choice, ok, if (ok) points(ms!!, questionMs) else 0, ms)
        }
        outcomes.forEach { (id, o) -> scores[id] = (scores[id] ?: 0) + o.points }
        phase = Phase.REVEAL; phaseEnd = now + revealMs
    }

    private fun afterReveal(now: Long) {
        if (isLast) { phase = Phase.FINISHED; phaseEnd = now } else { phase = Phase.BOARD; phaseEnd = now + boardMs }
    }

    private fun ranks(): Map<String, Int> = ranking().mapIndexed { i, p -> p.first to i + 1 }.toMap()

    /** Answer distribution of the revealed question (index = choice). */
    fun distribution(): IntArray = IntArray(4).also { d -> outcomes.values.forEach { o -> o.choice?.let { d[it]++ } } }

    companion object {
        /** Right answer: 1000 points if instant, falling linearly to 500 at the buzzer (rounded to 10). Wrong / none: 0. */
        fun points(elapsedMs: Long, windowMs: Long): Int {
            val f = 1.0 - (elapsedMs.coerceIn(0, windowMs).toDouble() / windowMs)
            return (Math.round((500 + 500 * f) / 10.0) * 10).toInt()
        }
    }
}
