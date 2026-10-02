package castbridge.core.quiz

/** Optional, paid conveniences of the « Millionnaire » game (always confirmed by the player, never in practice or Duel). */
enum class Boost(val label: String, val maxPerGame: Int) {
    SECOND_CHANCE("Seconde chance", 1),
    EXTRA_JOKER("Joker en plus", 2),
    SWAP_QUESTION("Changer de question", 1),
}

/**
 * What the game needs to sell [Boost]s, without knowing what pays for them (the real token holder is plugged in by the TV app).
 * No payment implementation lives in the quiz package; [NoBoosts] is the free default (nothing available).
 */
interface QuizBoosts {
    /** false when no tokens exist for this device (e.g. TV in trial): the boost is not even offered. */
    fun available(b: Boost): Boolean
    /** Price of [b] in tokens. */
    fun cost(b: Boost): Long
    /** Tokens the player holds. */
    fun balance(): Long
    /**
     * Debits [b] for the game [gameId]; false (nothing taken) when the balance is too low.
     *
     * Idempotence required from the paying implementation: a purchase is identified by
     * (gameId, [b], purchase number within that game, at most [Boost.maxPerGame]). A retry of the same
     * purchase (network, double tap, replayed request) must never debit twice and must return the same
     * result; only a new purchase number may debit again. The game itself only calls this once per
     * accepted purchase, and applies the effect only when the result is true.
     */
    fun charge(b: Boost, gameId: String): Boolean
}

/** Default: no boost at all, the game behaves exactly as before. */
object NoBoosts : QuizBoosts {
    override fun available(b: Boost) = false
    override fun cost(b: Boost) = 0L
    override fun balance() = 0L
    override fun charge(b: Boost, gameId: String) = false
}
