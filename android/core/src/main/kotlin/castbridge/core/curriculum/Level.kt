package castbridge.core.curriculum

import kotlin.math.exp

/**
 * The level ladder of a class (docs/CONTENT-ARCHITECTURE.md § 2, owner rule C):
 * N0 Découverte (from zero) · N1 Fondations (the official Cameroonian programme mastered) · N2 Intermédiaire =
 * Cameroonian national excellence · N3 Avancé = bridge to international excellence · N4 Expert = world-class excellence.
 */
enum class Level(val key: String, val fr: String, val en: String) {
    N0("N0", "Découverte", "Discovery"),
    N1("N1", "Fondations", "Foundations"),
    N2("N2", "Intermédiaire (excellence Cameroun)", "Intermediate (Cameroonian excellence)"),
    N3("N3", "Avancé (vers l'excellence internationale)", "Advanced (bridge to international excellence)"),
    N4("N4", "Expert (excellence mondiale)", "Expert (world-class excellence)");

    companion object {
        val KEYS: Set<String> = values().map { it.key }.toSet()
        fun of(k: String?): Level? = values().firstOrNull { it.key == k }
    }
}

/**
 * The additive difficulty scale: how tiers and difficulty 1..5 relate to the levels, and the default item calibration.
 *
 * - Content WITHOUT a declared level keeps its old meaning: exercises/questions of the class programme = N1, difficulty
 *   relative to the class (an old CM2 question of difficulty 5 is the hardest CM2 *programme* question, not excellence).
 * - Content WITH a declared level uses the absolute scale below.
 */
object LevelScale {
    /** Difficulty window (inclusive) an item of this level must use (checked by the validators). */
    fun difficultyWindow(l: Level): IntRange = when (l) {
        Level.N0 -> 1..2; Level.N1 -> 1..3; Level.N2 -> 3..4; Level.N3 -> 4..5; Level.N4 -> 5..5
    }

    /** Tiers allowed for an exercise of this level (N0-N1 keep the historical tiers). */
    fun tiersFor(l: Level): Set<castbridge.core.learn.ExerciseTier> = when (l) {
        Level.N0 -> setOf(castbridge.core.learn.ExerciseTier.APPLICATION, castbridge.core.learn.ExerciseTier.SELFCHECK)
        Level.N1 -> setOf(castbridge.core.learn.ExerciseTier.APPLICATION, castbridge.core.learn.ExerciseTier.DEEPER,
            castbridge.core.learn.ExerciseTier.EXAM, castbridge.core.learn.ExerciseTier.SELFCHECK)
        Level.N2 -> setOf(castbridge.core.learn.ExerciseTier.EXCELLENCE_CM)
        Level.N3, Level.N4 -> setOf(castbridge.core.learn.ExerciseTier.EXCELLENCE_MONDE)
    }

    /** Level of an item: its declared level, else N1 (class programme: backwards-compatible reading). */
    fun itemLevel(declared: String?): Level = Level.of(declared) ?: Level.N1

    /** Fractional position of an item on the ladder: the declared level plus a bonus for its difficulty inside the level's window. */
    fun itemPosition(declared: String?, difficulty: Int): Double {
        val l = itemLevel(declared); val w = difficultyWindow(l)
        val within = if (w.first == w.last) 0.5 else (difficulty.coerceIn(w.first, w.last) - w.first).toDouble() / (w.last - w.first)
        return l.ordinal + (within - 0.5) * 0.6
    }

    /**
     * Default probability that a learner at level [learner] (position 0..4, fractional allowed) answers an item at position
     * [item] correctly: 0.15 (guessing floor) + 0.80 · logistic. An item at the learner's own level ≈ 0.71, one level
     * above ≈ 0.44, two above ≈ 0.25, one below ≈ 0.87: the « productive zone » is 0.45..0.90.
     */
    fun successRate(learner: Double, item: Double): Double = 0.15 + 0.80 / (1.0 + exp(-1.4 * (learner - item + 0.6)))

    /** Calibrated rate if the item states one for this learner level (field `calibration` / `calib`), else the default model. */
    fun expectedSuccess(learner: Level, declared: String?, difficulty: Int, calibration: Map<String, Double>): Double =
        calibration[learner.key] ?: successRate(learner.ordinal.toDouble(), itemPosition(declared, difficulty))

    const val ZONE_LOW = 0.45
    const val ZONE_HIGH = 0.90
}
