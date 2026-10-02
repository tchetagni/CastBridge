package castbridge.core.quiz

/**
 * Stakes of the « Défi en points » mode (formerly « avec mise »), behind an interface. Points have no value, cannot be bought, and are never linked to the tokens of the shop.
 *
 * IMPORTANT — POC: the only implementation is [ChallengePointsWallet]: challenge points WITHOUT ANY VALUE, created from nothing,
 * never bought, never cashed out. No real money, no payment integration, no bank or Mobile Money data. Plugging a real
 * provider requires first checking the Cameroonian legal framework on games of chance / contests (skill vs chance,
 * licence, minimum age, KYC): see docs/QUIZ.md, « Mise payante ».
 */
interface WalletProvider {
    /** true = points without value (the UI must then say « Points de défi — sans valeur »). */
    val virtual: Boolean
    /** Name of the unit shown to players ("points de défi"). */
    val unit: String
    fun balance(player: String): Long
    /** Takes [amount] from [player] for the game [gameId]; false (nothing taken) if the balance is too low. */
    fun stake(gameId: String, player: String, amount: Long): Boolean
    /** Pays the pot of [gameId] out ([awards] per player). Called once per game. */
    fun payout(gameId: String, awards: Map<String, Long>)
    /** Gives back every stake of [gameId] (game abandoned). */
    fun refund(gameId: String)
}

/** In-memory challenge points: no value, not purchasable, never linked to shop tokens. Every new player starts with [initial] points; forgotten when the app stops. */
class ChallengePointsWallet(private val initial: Long = 1_000) : WalletProvider {
    override val virtual = true
    override val unit = "points de défi"
    private val balances = HashMap<String, Long>()
    private val stakes = HashMap<String, MutableMap<String, Long>>()

    @Synchronized override fun balance(player: String): Long = balances.getOrPut(player) { initial }

    @Synchronized override fun stake(gameId: String, player: String, amount: Long): Boolean {
        require(amount >= 0)
        val b = balance(player)
        if (b < amount) return false
        balances[player] = b - amount
        stakes.getOrPut(gameId) { HashMap() }.merge(player, amount, Long::plus)
        return true
    }

    @Synchronized override fun payout(gameId: String, awards: Map<String, Long>) {
        stakes.remove(gameId) ?: return                                  // already settled
        awards.forEach { (p, a) -> balances[p] = balance(p) + a }
    }

    @Synchronized override fun refund(gameId: String) {
        stakes.remove(gameId)?.forEach { (p, a) -> balances[p] = balance(p) + a }
    }
}

/** How a pot is shared out according to the final ranking. Pure, tested. */
object Pot {
    /** Shares of the pot by place: 1 player 100 %; 2: 70/30; 3+: 60/30/10. */
    fun shares(players: Int): List<Int> = when {
        players <= 1 -> listOf(100)
        players == 2 -> listOf(70, 30)
        else -> listOf(60, 30, 10)
    }

    /**
     * Splits [pot] among [scores] (player → score). Tied players share the places they cover equally; players with
     * 0 points win nothing (their share goes to the others); rounding leftovers go to the best. Sum = pot (if anyone scored).
     */
    fun split(pot: Long, scores: Map<String, Int>): Map<String, Long> {
        val out = LinkedHashMap<String, Long>(); scores.keys.forEach { out[it] = 0 }
        val ranked = scores.entries.filter { it.value > 0 }.sortedByDescending { it.value }
        if (pot <= 0 || ranked.isEmpty()) return out
        val shares = shares(ranked.size)
        val totalPct = shares.take(ranked.size).sum()
        var place = 0
        var given = 0L
        val groups = ranked.groupBy { it.value }.toSortedMap(compareByDescending { it })
        for ((_, group) in groups) {
            val pct = (place until place + group.size).sumOf { shares.getOrElse(it) { 0 } }
            val each = pot * pct / totalPct / group.size
            group.forEach { out[it.key] = each; given += each }
            place += group.size
        }
        val best = ranked.first().key
        out[best] = (out[best] ?: 0) + (pot - given)
        return out
    }
}

/** Former name of [ChallengePointsWallet], kept so nothing breaks. */
@Deprecated("Renamed: points de défi, not tokens", ReplaceWith("ChallengePointsWallet(initial)"))
fun VirtualWallet(initial: Long = 1_000): ChallengePointsWallet = ChallengePointsWallet(initial)
