package castbridge.core.quiz

import kotlin.random.Random

/** Prize ladder in FCFA: 15 levels, two safe levels (5th and 10th). */
class Ladder(val amounts: List<Long>, val safeLevels: Set<Int>) {
    val size get() = amounts.size
    /** Amount won with [level] right answers (0 = nothing). */
    fun amount(level: Int): Long = if (level <= 0) 0 else amounts[minOf(level, size) - 1]
    /** What a wrong answer leaves after [reached] right answers: the highest safe level reached. */
    fun safeAmount(reached: Int): Long = amount(safeLevels.filter { it <= reached }.maxOrNull() ?: 0)
    fun isSafe(level: Int) = level in safeLevels

    companion object {
        val DEFAULT = Ladder(
            listOf(10_000, 20_000, 30_000, 50_000, 100_000, 200_000, 400_000, 800_000, 1_500_000, 3_000_000,
                6_000_000, 12_000_000, 25_000_000, 50_000_000, 100_000_000),
            setOf(5, 10))

        /** "1 500 000 FCFA" (non-breaking spaces). */
        fun fcfa(v: Long): String = "%,d".format(java.util.Locale.ROOT, v).replace(',', ' ') + " FCFA"
    }
}

enum class Joker(val label: String) { FIFTY("50:50"), AUDIENCE("Avis du public"), PHONE("Appel à un ami") }

/**
 * « Millionnaire » game rules as an explicit state machine, pure (the clock is passed in) and serializable to JSON.
 *
 * READY → QUESTION ⇄ CONFIRM (« C'est votre dernier mot ? ») → LOCKED (suspense) → REVEALED → QUESTION (next) … → FINISHED.
 * QUESTION → JOKER (public vote / phone call in progress, clock paused) → QUESTION.  QUESTION → FINISHED (walk away / time out).
 * Every command returns false (and changes nothing) when it is not allowed in the current phase: callers can repeat
 * commands safely (network idempotency).
 */
class QuizGame(
    val questions: List<Question>,
    val ladder: Ladder = Ladder.DEFAULT,
    /** Seconds per question level (index 0 = question 1). Every question has a countdown, never above [MAX_SECONDS]. */
    timers: IntArray = DEFAULT_TIMERS,
    seed: Long = System.nanoTime(),
    /** « Entraînement »: a wrong answer does not end the game, every question is played, no prize (score = right answers). */
    val practice: Boolean = false,
    /** Channel of the device: on the beta channel a question not validated yet carries a visible mark in the state (castbridge.core.content.PlayPolicy). */
    val markChannel: castbridge.core.content.Channel? = null,
) {
    enum class Phase { READY, QUESTION, CONFIRM, JOKER, LOCKED, REVEALED, FINISHED }

    /** Clock per question: missing or 0 means the maximum, anything longer is cut to [MAX_SECONDS]. */
    val timers: IntArray = IntArray(maxOf(questions.size, timers.size)) { i ->
        timers.getOrElse(i) { 0 }.let { if (it <= 0) MAX_SECONDS else minOf(it, MAX_SECONDS) }
    }
    enum class End { WON, WRONG, WALKED, TIMEOUT, PRACTICE_DONE }
    data class PhoneResult(val choice: Int, val confidence: Int, val friend: String?, val simulated: Boolean)

    init { require(questions.isNotEmpty() && questions.size <= ladder.size) }

    private val rng = Random(seed)
    var phase = Phase.READY; private set
    /** Index of the current question (0-based) = number of right answers so far while playing. */
    var index = 0; private set
    var selected: Int? = null; private set
    /** Choices removed by 50:50 for the current question. */
    var removed: Set<Int> = emptySet(); private set
    val jokersUsed: MutableSet<Joker> = LinkedHashSet()
    var activeJoker: Joker? = null; private set
    var audience: IntArray? = null; private set
    var audienceReal = false; private set
    var phone: PhoneResult? = null; private set
    var lastCorrect: Boolean? = null; private set
    var end: End? = null; private set
    /** Server time (ms) at which the clock runs out; 0 = no clock right now. */
    var deadline = 0L; private set
    private var pausedLeft = 0L
    /** Right answers given (the score of a practice game). */
    var correct = 0; private set

    val question: Question get() = questions[index]
    val levels get() = questions.size
    /** Right answers so far. */
    val reached: Int get() = if (phase == Phase.REVEALED && lastCorrect == true || phase == Phase.FINISHED && end == End.WON) index + 1 else index
    val winnings: Long get() = if (practice) 0 else when (end) {
        End.PRACTICE_DONE -> 0
        End.WON -> ladder.amount(levels)
        End.WALKED -> ladder.amount(index)
        End.WRONG, End.TIMEOUT -> ladder.safeAmount(index)
        null -> ladder.amount(reached)
    }

    fun start(now: Long): Boolean {
        if (phase != Phase.READY) return false
        enterQuestion(0, now); return true
    }

    fun select(choice: Int, now: Long): Boolean {
        if (phase != Phase.QUESTION || choice !in 0..3 || choice in removed) return false
        if (expired(now)) return false
        selected = choice; phase = Phase.CONFIRM; return true
    }

    fun cancel(): Boolean {
        if (phase != Phase.CONFIRM) return false
        selected = null; phase = Phase.QUESTION; return true
    }

    /** Final answer: the clock stops, suspense begins. */
    fun confirm(now: Long): Boolean {
        if (phase != Phase.CONFIRM || expired(now)) return false
        phase = Phase.LOCKED; deadline = 0; return true
    }

    fun reveal(): Boolean {
        if (phase != Phase.LOCKED) return false
        lastCorrect = selected == question.answer
        if (lastCorrect == true) correct++
        phase = Phase.REVEALED; return true
    }

    fun next(now: Long): Boolean {
        if (phase != Phase.REVEALED) return false
        when {
            practice && index + 1 >= levels -> finish(End.PRACTICE_DONE)
            practice -> enterQuestion(index + 1, now)
            lastCorrect != true -> finish(End.WRONG)
            index + 1 >= levels -> { index = levels - 1; finish(End.WON) }
            else -> enterQuestion(index + 1, now)
        }
        return true
    }

    /** Stop and keep the winnings of the last right answer. */
    fun walk(): Boolean {
        if (phase != Phase.QUESTION && phase != Phase.CONFIRM) return false
        selected = null; finish(End.WALKED); return true
    }

    fun canUse(j: Joker) = phase == Phase.QUESTION && j !in jokersUsed

    /** 50:50: removes two wrong answers (never the right one). */
    fun useFifty(): Boolean {
        if (!canUse(Joker.FIFTY)) return false
        val wrong = (0..3).filter { it != question.answer }.shuffled(rng)
        removed = setOf(wrong[0], wrong[1])
        jokersUsed += Joker.FIFTY; return true
    }

    /** Starts the public vote or the phone call: the clock is paused until [finishAudience] / [finishPhone]. */
    fun beginJoker(j: Joker, now: Long): Boolean {
        if (j == Joker.FIFTY || !canUse(j)) return false
        jokersUsed += j; activeJoker = j; phase = Phase.JOKER
        if (deadline > 0) { pausedLeft = maxOf(0, deadline - now); deadline = 0 }
        return true
    }

    /** Ends the public vote with real votes per choice ([votes] size 4), or a plausible simulation if null / nobody voted. */
    fun finishAudience(votes: IntArray?, now: Long): Boolean {
        if (phase != Phase.JOKER || activeJoker != Joker.AUDIENCE) return false
        val real = votes != null && votes.sum() > 0
        audience = if (real) percentages(votes!!) else simulateAudience(question, removed, rng)
        audienceReal = real
        resume(now); return true
    }

    /** Ends the phone call with the friend's suggestion, or a simulated friend if null (no friend / no reply in time). */
    fun finishPhone(choice: Int?, friend: String?, now: Long): Boolean {
        if (phase != Phase.JOKER || activeJoker != Joker.PHONE) return false
        phone = if (choice != null && choice in 0..3) PhoneResult(choice, 0, friend, false) else simulatePhone(question, removed, rng)
        resume(now); return true
    }

    /** Clock: a question left unanswered past its deadline ends the game (safe level kept). */
    fun tick(now: Long): Boolean {
        if ((phase == Phase.QUESTION || phase == Phase.CONFIRM) && expired(now)) { selected = null; finish(End.TIMEOUT); return true }
        return false
    }

    fun remainingMs(now: Long): Long = when {
        deadline > 0 -> maxOf(0, deadline - now)
        phase == Phase.JOKER -> pausedLeft
        else -> 0
    }

    private fun expired(now: Long) = deadline in 1..now

    private fun resume(now: Long) {
        activeJoker = null; phase = Phase.QUESTION
        if (pausedLeft > 0) { deadline = now + pausedLeft; pausedLeft = 0 }
    }

    private fun enterQuestion(i: Int, now: Long) {
        index = i; selected = null; removed = emptySet(); audience = null; audienceReal = false; phone = null; lastCorrect = null
        activeJoker = null; pausedLeft = 0
        val t = timers.getOrElse(i) { 0 }
        deadline = if (t > 0) now + t * 1000L else 0
        phase = Phase.QUESTION
    }

    private fun finish(e: End) { end = e; phase = Phase.FINISHED; deadline = 0; activeJoker = null }

    /**
     * Public state. The right answer (and the explanation) is only included once the current question is revealed
     * or the game is over: before that, nothing sent to a phone can give it away.
     */
    fun toMap(now: Long): Map<String, Any?> {
        val q = question
        val open = phase == Phase.REVEALED || phase == Phase.FINISHED
        return linkedMapOf(
            "phase" to phase.name,
            "index" to index,
            "levels" to levels,
            "reached" to reached,
            "practice" to practice,
            "correct" to correct,
            "question" to linkedMapOf(
                "id" to q.id, "text" to q.question, "choices" to q.choices, "category" to q.category,
                "region" to q.region.name, "difficulty" to q.difficulty,
                "answer" to (if (open) q.answer else null),
                "explanation" to (if (open) q.explanation else null),
                "mark" to markChannel?.let { castbridge.core.content.PlayPolicy.mark(q, it) },
            ),
            "selected" to selected,
            "removed" to removed.sorted(),
            "jokersUsed" to jokersUsed.map { it.name },
            "activeJoker" to activeJoker?.name,
            "audience" to audience?.toList(),
            "audienceReal" to audienceReal,
            "phone" to phone?.let { linkedMapOf("choice" to it.choice, "confidence" to it.confidence, "friend" to it.friend, "simulated" to it.simulated) },
            "lastCorrect" to lastCorrect,
            "end" to end?.name,
            "winnings" to winnings,
            "safe" to ladder.safeAmount(index),
            "remainingMs" to remainingMs(now),
            "ladder" to ladder.amounts,
            "safeLevels" to ladder.safeLevels.sorted(),
        )
    }

    fun toJson(now: Long): String = Json.write(toMap(now))

    companion object {
        /** Longest time to answer a question, in every mode (Esaie: countdown, never more than 20 s). */
        const val MAX_SECONDS = 20
        /** 20 s for every question. */
        val DEFAULT_TIMERS = IntArray(15) { MAX_SECONDS }
        /** Kept for callers of the former "no clock" mode: practice also has the 20 s countdown now. */
        val NO_TIMERS = DEFAULT_TIMERS

        /** Integer percentages summing to 100 (largest remainder). */
        fun percentages(votes: IntArray): IntArray {
            val total = votes.sum()
            if (total <= 0) return IntArray(votes.size)
            val raw = votes.map { it * 100.0 / total }
            val out = raw.map { it.toInt() }.toIntArray()
            var left = 100 - out.sum()
            raw.indices.sortedByDescending { raw[it] - out[it] }.forEach { if (left > 0) { out[it]++; left-- } }
            return out
        }

        /** A plausible audience: the right answer gets more votes the easier the question, the rest is spread at random. */
        fun simulateAudience(q: Question, removed: Set<Int>, rng: Random): IntArray {
            val visible = (0..3).filter { it !in removed }
            val base = doubleArrayOf(0.74, 0.62, 0.52, 0.44, 0.37)[q.difficulty.coerceIn(1, 5) - 1]
            val bonus = if (visible.size == 2) 0.1 else 0.0
            val pRight = (base + bonus + (rng.nextDouble() - 0.5) * 0.16).coerceIn(0.2, 0.95)
            val others = visible.filter { it != q.answer }
            val weights = others.map { 0.3 + rng.nextDouble() }
            val w = DoubleArray(4)
            w[q.answer] = pRight
            others.forEachIndexed { i, c -> w[c] = (1 - pRight) * weights[i] / weights.sum() }
            return percentages(IntArray(4) { Math.round(w[it] * 1000).toInt() })
        }

        /** A friend who is often right on easy questions, less so on hard ones, with a confidence that says so. */
        fun simulatePhone(q: Question, removed: Set<Int>, rng: Random): PhoneResult {
            val pRight = doubleArrayOf(0.95, 0.85, 0.72, 0.6, 0.5)[q.difficulty.coerceIn(1, 5) - 1]
            val right = rng.nextDouble() < pRight
            val wrong = (0..3).filter { it != q.answer && it !in removed }
            val choice = if (right || wrong.isEmpty()) q.answer else wrong.random(rng)
            val conf = if (right) 60 + rng.nextInt(36) else 35 + rng.nextInt(30)
            return PhoneResult(choice, conf, null, true)
        }
    }
}
