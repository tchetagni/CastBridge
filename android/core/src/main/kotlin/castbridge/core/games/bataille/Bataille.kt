package castbridge.core.games.bataille

import castbridge.core.games.GameMove
import castbridge.core.games.GameRandom
import castbridge.core.games.GameRules
import castbridge.core.games.Outcome
import castbridge.core.games.PlayerId
import castbridge.core.games.cards.Card
import castbridge.core.games.cards.CardDeck
import castbridge.core.games.cards.RankOrder
import castbridge.core.quiz.Json

/** Retourner la carte du dessus de son paquet : le seul coup de la Bataille. */
data class Flip(override val player: PlayerId) : GameMove

/** Une carte posée sur la table, face visible ou cachée. */
data class Placed(val player: PlayerId, val card: Card, val faceUp: Boolean)

/** Un retournement à venir : qui doit retourner une carte, et comment (face visible ou cachée, pour une bataille). */
data class Step(val player: PlayerId, val faceUp: Boolean)

/** La dernière levée terminée, pour l'affichage : qui l'a gagnée, combien de cartes elle a prises, s'il y a eu bataille, et les cartes VISIBLES (jamais les cachées). */
data class RoundSummary(val winner: PlayerId, val won: Int, val battle: Boolean, val shown: List<Placed>)

/**
 * L'état complet d'une partie (chez l'autorité seulement : jamais envoyé à un joueur). [piles] : un paquet par joueur, dans l'ordre des places, la carte du dessus en premier ; [table] : les
 * cartes de la levée en cours dans l'ordre où elles ont été posées ; [plan] : les retournements qui restent dans la levée en cours ; [ended] : l'issue, une fois la partie finie ; [seed] : la
 * graine de la partie, d'où sort l'ordre dans lequel le gagnant glisse les cartes sous son paquet.
 */
data class BatailleState(
    val players: List<PlayerId>, val piles: List<List<Card>>, val table: List<Placed>, val plan: List<Step>,
    val flips: Int, val rounds: Int, val battles: Int, val last: RoundSummary?, val ended: Outcome?, val seed: Long = 0L,
)

/**
 * **Bataille (démonstration)** : la première FICHE DE RÈGLES EXÉCUTABLE de la plateforme de jeux (docs/GAMES.md). Règles publiques et universelles, rien d'inventé ; elle sert à prouver la
 * plateforme (graine, tours, vues, journal, salle maison, solo contre l'ordinateur), pas à être un des jeux du propriétaire.
 *
 * - **Deux joueurs**, un paquet de 52 cartes mélangé par la graine, distribué en entier (26 chacun, faces cachées : personne ne voit son paquet). L'as est la carte la plus forte ; les
 *   couleurs ne comptent pas.
 * - **Une levée** : chacun retourne la carte du dessus de son paquet (le premier joueur d'abord). La plus forte l'emporte : le gagnant prend les deux cartes et les glisse SOUS son paquet.
 *   En vrai il les range dans l'ordre qu'il veut ; ici l'ordre est tiré de la graine de la partie et du numéro de la levée (même partie, même ordre : la partie se rejoue). Sans cela l'ordre
 *   serait toujours le même et presque toutes les parties tourneraient en rond.
 * - **Bataille** : deux cartes de même valeur. Chacun pose une carte face CACHÉE (le premier joueur d'abord), puis retourne une carte face visible ; la plus forte des deux cartes visibles prend
 *   TOUTES les cartes de la table. Nouvelle égalité : nouvelle bataille. Un joueur qui n'a qu'une carte la retourne face visible sans en cacher ; qui n'a plus de carte pour continuer une
 *   bataille perd la partie.
 * - **Fin** : celui qui n'a plus de carte perd. Une partie de Bataille dure très longtemps et peut ne jamais finir : au bout de [MAX_FLIPS] retournements (100 levées), à la fin de la levée en
 *   cours (jamais au milieu d'une bataille), le plus gros paquet gagne, à égalité la partie est nulle. Cette limite est la seule règle ajoutée à la Bataille ordinaire : elle fait une
 *   démonstration de quelques minutes et garde le journal de partie borné.
 *
 * Il n'y a aucun choix à faire : le coup est toujours « retourner ». Rien n'est caché dans la VUE : les deux paquets ne sont montrés que par leur nombre de cartes.
 */
object Bataille : GameRules<BatailleState, Flip> {
    const val NAME = "Bataille (démonstration)"
    /** Nombre de retournements au bout duquel la partie est arrêtée à la fin de la levée. */
    const val MAX_FLIPS = 200

    override val id = "bataille"
    override val version = 1
    override val minPlayers = 2
    override val maxPlayers = 2

    private val ORDER = RankOrder.ACE_HIGH

    private fun roundPlan(players: List<PlayerId>) = players.map { Step(it, true) }

    override fun initial(seed: Long, players: List<PlayerId>): BatailleState {
        require(players.size == 2) { "la Bataille se joue à deux" }
        require(players.toSet().size == 2) { "deux places différentes" }
        val deal = CardDeck.standard(52).shuffled(seed).deal(players, perPlayer = null)
        return BatailleState(players, players.map { deal.hands.getValue(it) }, emptyList(), roundPlan(players), 0, 0, 0, null, null, seed)
    }

    /** Le prochain retournement, null si la partie est finie. */
    fun nextStep(state: BatailleState): Step? = if (state.ended != null) null else state.plan.firstOrNull()
    fun nextToFlip(state: BatailleState): PlayerId? = nextStep(state)?.player

    override fun legal(state: BatailleState, player: PlayerId): List<Flip> = if (nextToFlip(state) == player) listOf(Flip(player)) else emptyList()

    override fun apply(state: BatailleState, move: Flip): BatailleState {
        val step = nextStep(state)
        require(step != null && step.player == move.player) { "ce n'est pas à ${move.player} de retourner une carte" }
        val idx = state.players.indexOf(step.player)
        val pile = state.piles[idx]
        check(pile.isNotEmpty()) { "un retournement prévu pour un joueur sans carte" }     // le plan ne contient jamais ce cas
        val piles = state.piles.toMutableList().also { it[idx] = pile.drop(1) }
        val next = state.copy(piles = piles, table = state.table + Placed(step.player, pile.first(), step.faceUp), plan = state.plan.drop(1), flips = state.flips + 1)
        return if (next.plan.isNotEmpty()) next else resolve(next)
    }

    /** Tous les retournements de la levée sont faits : comparer les deux dernières cartes visibles. */
    private fun resolve(s: BatailleState): BatailleState {
        val up = s.players.map { p -> s.table.last { it.player == p && it.faceUp }.card }
        val cmp = ORDER.compare(up[0], up[1])
        if (cmp == 0) return startBattle(s)
        val winner = if (cmp > 0) 0 else 1
        // l'ordre dans lequel le gagnant range les cartes : tiré de la graine et du numéro de la levée (voir la fiche de règles)
        val won = s.table.map { it.card }.toMutableList().also { GameRandom(s.seed xor (s.rounds.toLong() * 7919L + 1L)).shuffle(it) }
        val piles = s.piles.toMutableList().also { it[winner] = it[winner] + won }
        val summary = RoundSummary(s.players[winner], s.table.size, s.table.size > 2, s.table.filter { it.faceUp })
        val ended = when {
            piles[1 - winner].isEmpty() -> Outcome.Winners(listOf(s.players[winner]))
            s.flips >= MAX_FLIPS -> byCount(s.players, piles)
            else -> null
        }
        return s.copy(piles = piles, table = emptyList(), plan = if (ended == null) roundPlan(s.players) else emptyList(), rounds = s.rounds + 1, last = summary, ended = ended)
    }

    /** Égalité : une bataille, ou la partie est perdue par celui qui ne peut plus continuer. */
    private fun startBattle(s: BatailleState): BatailleState {
        val n = s.piles.map { it.size }
        val loser = n.indexOfFirst { it == 0 }
        if (loser >= 0) return s.copy(plan = emptyList(), ended = if (n.all { it == 0 }) Outcome.Draw else Outcome.Winners(listOf(s.players[1 - loser])))
        val plan = buildList {
            for (i in 0..1) if (n[i] >= 2) add(Step(s.players[i], false))
            for (i in 0..1) add(Step(s.players[i], true))
        }
        return s.copy(plan = plan, battles = s.battles + 1)
    }

    private fun byCount(players: List<PlayerId>, piles: List<List<Card>>): Outcome = when {
        piles[0].size > piles[1].size -> Outcome.Winners(listOf(players[0]))
        piles[1].size > piles[0].size -> Outcome.Winners(listOf(players[1]))
        else -> Outcome.Draw
    }

    override fun outcome(state: BatailleState): Outcome = state.ended ?: Outcome.InProgress

    /**
     * Ce que voient tous les joueurs (la vue ne dépend pas du joueur : il n'y a rien de caché à leur distinguer) : le NOMBRE de cartes de chaque paquet, la table (les cartes cachées
     * apparaissent comme un dos, sans valeur), qui retourne ensuite et comment, les compteurs, et la dernière levée.
     */
    override fun view(state: BatailleState, player: PlayerId?): Map<String, Any?> = linkedMapOf(
        "piles" to state.players.indices.associate { state.players[it].id to state.piles[it].size },
        "table" to state.table.map { linkedMapOf("player" to it.player.id, "card" to (if (it.faceUp) it.card.code else null), "up" to it.faceUp) },
        "next" to nextStep(state)?.let { linkedMapOf("player" to it.player.id, "up" to it.faceUp) },
        "rounds" to state.rounds, "flips" to state.flips, "battles" to state.battles,
        "last" to state.last?.let { r ->
            linkedMapOf("winner" to r.winner.id, "won" to r.won, "battle" to r.battle, "cards" to r.shown.map { linkedMapOf("player" to it.player.id, "card" to it.card.code) })
        },
    )

    override fun encodeState(state: BatailleState): String = Json.write(linkedMapOf(
        "players" to state.players.map { it.id },
        "piles" to state.piles.map { p -> p.map { it.code } },
        "table" to state.table.map { listOf(it.player.id, it.card.code, if (it.faceUp) 1 else 0) },
        "plan" to state.plan.map { listOf(it.player.id, if (it.faceUp) 1 else 0) },
        "flips" to state.flips, "rounds" to state.rounds, "battles" to state.battles,
        "last" to state.last?.let { r -> linkedMapOf("winner" to r.winner.id, "won" to r.won, "battle" to if (r.battle) 1 else 0, "shown" to r.shown.map { listOf(it.player.id, it.card.code, 1) }) },
        "ended" to state.ended?.toJson(), "seed" to state.seed,
    ))

    override fun decodeState(text: String): BatailleState = try {
        val m = Json.obj(text)
        fun ids(x: Any?) = (x as List<*>).map { PlayerId(it as String) }
        fun card(x: Any?) = Card.parse(x as String) ?: throw IllegalArgumentException("carte illisible : $x")
        fun placed(x: Any?) = (x as List<*>).let { Placed(PlayerId(it[0] as String), card(it[1]), (it[2] as Number).toInt() == 1) }
        fun int(k: String) = (m[k] as Number).toInt().also { require(it >= 0) { "$k négatif" } }
        val players = ids(m["players"])
        require(players.size == 2 && players.toSet().size == 2) { "deux places différentes" }
        val piles = (m["piles"] as List<*>).map { p -> (p as List<*>).map(::card) }
        require(piles.size == 2) { "deux paquets" }
        BatailleState(players, piles, (m["table"] as List<*>).map(::placed),
            (m["plan"] as List<*>).map { e -> (e as List<*>).let { Step(PlayerId(it[0] as String), (it[1] as Number).toInt() == 1) } },
            int("flips"), int("rounds"), int("battles"),
            (m["last"] as Map<*, *>?)?.let { r -> RoundSummary(PlayerId(r["winner"] as String), (r["won"] as Number).toInt(), (r["battle"] as Number).toInt() == 1, (r["shown"] as List<*>).map(::placed)) },
            (m["ended"] as Map<*, *>?)?.let { e -> @Suppress("UNCHECKED_CAST") Outcome.fromJson(e as Map<String, Any?>) ?: throw IllegalArgumentException("issue illisible") },
            (m["seed"] as Number).toLong())
    } catch (e: IllegalArgumentException) { throw e } catch (e: RuntimeException) { throw IllegalArgumentException("état de Bataille illisible : ${e.javaClass.simpleName}", e) }

    override fun encodeMove(move: Flip) = "flip"
    override fun decodeMove(text: String, player: PlayerId): Flip? = if (text == "flip") Flip(player) else null
}
