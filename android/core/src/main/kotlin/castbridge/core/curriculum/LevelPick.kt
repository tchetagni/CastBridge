package castbridge.core.curriculum

import castbridge.core.quiz.Question
import kotlin.random.Random

/**
 * Picks quiz questions by learner level so that an excellent learner is not bored and a beginner is not crushed
 * (docs/CONTENT-ARCHITECTURE.md § 7). Position i of n gets a target success rate falling from 0.90 (warm-up) to 0.45
 * (last question); the unused question whose expected success is closest to that target is chosen. Pure and
 * reproducible for a given seed; questions without `nlevel` count as N1 (class programme).
 */
object LevelPick {
    fun target(i: Int, n: Int): Double = if (n <= 1) 0.7 else LevelScale.ZONE_HIGH - (LevelScale.ZONE_HIGH - LevelScale.ZONE_LOW) * i / (n - 1)

    fun expected(q: Question, learner: Level): Double = LevelScale.expectedSuccess(learner, q.nlevel, q.difficulty, q.calib)

    fun pick(pool: List<Question>, learner: Level, count: Int, seed: Long = System.nanoTime(), exclude: Set<String> = emptySet()): List<Question> {
        val rng = Random(seed)
        val left = pool.filter { it.id !in exclude && !it.review }.toMutableList()
        val out = ArrayList<Question>()
        for (i in 0 until count) {
            if (left.isEmpty()) break
            val t = target(i, count)
            val best = left.minOf { Math.abs(expected(it, learner) - t) }
            val q = left.filter { Math.abs(expected(it, learner) - t) <= best + 1e-9 }.random(rng)
            out += q; left -= q
        }
        return out
    }
}

/**
 * Short adaptive placement (« test de positionnement ») of ONE domain: a staircase over the levels N0..N4.
 * Start at the level of the declared class (N1 by default). Each level is probed with up to [PER_LEVEL] items and passed
 * with [PASS] correct; two correct answers in a row from the start of a probe at the entry level jump straight two levels
 * (an exceptional learner reaches N3/N4 in a few items). The test stops when the highest passed level and the lowest
 * failed one are adjacent, or after [MAX_ITEMS].
 */
class Placement(private val start: Level = Level.N1) {
    companion object { const val PER_LEVEL = 3; const val PASS = 2; const val MAX_ITEMS = 14 }

    class Result(val level: Level?, val items: Int, val fastTracked: Boolean) {
        /** « reprendre les bases » below N0 never happens: a learner who fails N0 starts at N0. */
        val action: String get() = when {
            level == null || level == Level.N0 -> "start-n0"
            level >= Level.N2 -> "excellence-path"
            else -> "standard-path"
        }
    }

    private var passed = -1; private var failed = 5
    private var cur = start.ordinal
    private var asked = 0; private var right = 0; private var wrong = 0
    private var streak = 0; private var fast = false; private var total = 0
    var finished = false; private set

    /** The level of the next item to present. */
    val nextLevel: Level get() = Level.values()[cur]

    fun answer(correct: Boolean) {
        if (finished) return
        total++; asked++
        if (correct) { right++; streak++ } else { wrong++; streak = 0 }
        // exceptional learner: the first two answers (at the entry level) are right → probe two levels higher at once
        if (total == 2 && streak == 2 && cur == start.ordinal && cur + 2 <= 4) {
            passed = cur; cur += 2; asked = 0; right = 0; wrong = 0; fast = true; return
        }
        if (right >= PASS || wrong > PER_LEVEL - PASS || asked >= PER_LEVEL) {
            val ok = right >= PASS
            if (ok) passed = maxOf(passed, cur) else failed = minOf(failed, cur)
            asked = 0; right = 0; wrong = 0
            if (passed + 1 >= failed || total >= MAX_ITEMS) { finished = true; return }
            val lo = passed + 1; val hi = failed - 1
            cur = if (ok) minOf(cur + 1, hi) else maxOf(cur - 1, lo)
        } else if (total >= MAX_ITEMS) finished = true
    }

    fun result(): Result = Result(if (passed < 0) null else Level.values()[passed], total, fast)
}
