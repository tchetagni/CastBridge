package castbridge.core.games

import castbridge.core.games.cards.Card
import castbridge.core.games.cards.CardDeck
import castbridge.core.games.cards.Play
import castbridge.core.games.cards.RankOrder
import castbridge.core.games.cards.Trick
import castbridge.core.quiz.Json

/**
 * Deux petits jeux POUR LES TESTS de la plateforme (aucune règle réelle) :
 *  - [Nim] : un tas, chacun prend 1 à 3 jetons à son tour, celui qui prend le dernier gagne ; sans information cachée ; la taille du tas dépend de la graine.
 *  - [Hands] : trois cartes en main (distribuées d'un paquet de 32 mélangé par la graine), une carte jouée par tour, la plus forte prend la levée ; trois levées puis les points.
 *    Sert à vérifier qu'une vue ne montre jamais la main des autres.
 */
internal data class NimState(val players: List<PlayerId>, val pile: Int, val turn: Int, val winner: PlayerId?)
internal data class Take(override val player: PlayerId, val n: Int) : GameMove

internal object Nim : GameRules<NimState, Take> {
    override val id = "nim"
    override val version = 1
    override val minPlayers = 2
    override val maxPlayers = 4

    override fun initial(seed: Long, players: List<PlayerId>): NimState {
        require(players.size in minPlayers..maxPlayers) { "2 à 4 joueurs" }
        return NimState(players, 7 + Math.floorMod(seed, 5L).toInt(), 0, null)
    }

    override fun legal(state: NimState, player: PlayerId): List<Take> =
        if (state.winner != null || state.players[state.turn] != player) emptyList() else (1..minOf(3, state.pile)).map { Take(player, it) }

    override fun apply(state: NimState, move: Take): NimState {
        require(move in legal(state, move.player)) { "coup illégal : $move" }
        val pile = state.pile - move.n
        return if (pile == 0) state.copy(pile = 0, winner = move.player) else state.copy(pile = pile, turn = (state.turn + 1) % state.players.size)
    }

    override fun view(state: NimState, player: PlayerId?): Map<String, Any?> = linkedMapOf("pile" to state.pile, "turn" to state.players[state.turn].id)

    override fun outcome(state: NimState): Outcome = state.winner?.let { Outcome.Winners(listOf(it)) } ?: Outcome.InProgress

    override fun encodeState(state: NimState): String = Json.write(linkedMapOf("players" to state.players.map { it.id }, "pile" to state.pile, "turn" to state.turn, "winner" to state.winner?.id))

    override fun decodeState(text: String): NimState {
        val m = Json.obj(text)
        return NimState((m["players"] as List<*>).map { PlayerId(it as String) }, (m["pile"] as Number).toInt(), (m["turn"] as Number).toInt(), (m["winner"] as String?)?.let(::PlayerId))
    }

    override fun encodeMove(move: Take) = "take:${move.n}"
    override fun decodeMove(text: String, player: PlayerId): Take? = Regex("^take:([1-3])$").matchEntire(text)?.let { Take(player, it.groupValues[1].toInt()) }
}

internal data class HandsState(val players: List<PlayerId>, val hands: Map<PlayerId, List<Card>>, val table: List<Play>, val scores: Map<PlayerId, Int>, val turn: Int, val tricks: Int)
internal data class PlayCard(override val player: PlayerId, val card: Card) : GameMove

internal object Hands : GameRules<HandsState, PlayCard> {
    const val TRICKS = 3
    override val id = "mains"
    override val version = 1
    override val minPlayers = 2
    override val maxPlayers = 4

    override fun initial(seed: Long, players: List<PlayerId>): HandsState {
        require(players.size in minPlayers..maxPlayers) { "2 à 4 joueurs" }
        val deal = CardDeck.standard(32).shuffled(seed).deal(players, TRICKS)
        return HandsState(players, deal.hands, emptyList(), players.associateWith { 0 }, 0, 0)
    }

    override fun legal(state: HandsState, player: PlayerId): List<PlayCard> =
        if (state.tricks >= TRICKS || state.players[state.turn] != player) emptyList() else state.hands.getValue(player).distinct().map { PlayCard(player, it) }

    override fun apply(state: HandsState, move: PlayCard): HandsState {
        require(move in legal(state, move.player)) { "coup illégal : $move" }
        val hand = state.hands.getValue(move.player).toMutableList().also { it.remove(move.card) }
        val hands = state.hands + (move.player to hand)
        val table = state.table + Play(move.player, move.card)
        if (table.size < state.players.size) return state.copy(hands = hands, table = table, turn = (state.turn + 1) % state.players.size)
        val winner = (Trick(table).winner(RankOrder.ACE_HIGH, followSuit = false) ?: table.first()).player
        return state.copy(hands = hands, table = emptyList(), scores = state.scores + (winner to state.scores.getValue(winner) + 1), turn = state.players.indexOf(winner), tricks = state.tricks + 1)
    }

    override fun view(state: HandsState, player: PlayerId?): Map<String, Any?> = linkedMapOf(
        "hand" to player?.let { state.hands[it]?.map { c -> c.code } },
        "handSizes" to state.hands.entries.associate { it.key.id to it.value.size },
        "table" to state.table.map { listOf(it.player.id, it.card.code) },
        "scores" to state.scores.entries.associate { it.key.id to it.value },
        "turn" to state.players[state.turn].id,
    )

    override fun outcome(state: HandsState): Outcome = if (state.tricks >= TRICKS) Outcome.Scores(state.scores) else Outcome.InProgress

    override fun encodeState(state: HandsState): String = Json.write(linkedMapOf(
        "players" to state.players.map { it.id }, "hands" to state.hands.entries.associate { it.key.id to it.value.map { c -> c.code } },
        "table" to state.table.map { listOf(it.player.id, it.card.code) }, "scores" to state.scores.entries.associate { it.key.id to it.value },
        "turn" to state.turn, "tricks" to state.tricks))

    override fun decodeState(text: String): HandsState {
        val m = Json.obj(text)
        val players = (m["players"] as List<*>).map { PlayerId(it as String) }
        fun card(x: Any?) = Card.parse(x as String)!!
        return HandsState(players,
            (m["hands"] as Map<*, *>).entries.associate { (k, v) -> PlayerId(k as String) to (v as List<*>).map(::card) },
            (m["table"] as List<*>).map { e -> (e as List<*>).let { Play(PlayerId(it[0] as String), card(it[1])) } },
            (m["scores"] as Map<*, *>).entries.associate { (k, v) -> PlayerId(k as String) to (v as Number).toInt() },
            (m["turn"] as Number).toInt(), (m["tricks"] as Number).toInt())
    }

    override fun encodeMove(move: PlayCard) = "play:${move.card.code}"
    override fun decodeMove(text: String, player: PlayerId): PlayCard? = text.removePrefix("play:").takeIf { text.startsWith("play:") }?.let(Card::parse)?.let { PlayCard(player, it) }
}

/** Un jeu qui ne finit jamais (chacun passe à tour de rôle) : pour essayer les bornes des journaux. */
internal data class EndlessState(val players: List<PlayerId>, val moves: Int)
internal data class Pass(override val player: PlayerId) : GameMove

internal object Endless : GameRules<EndlessState, Pass> {
    override val id = "endless"
    override val version = 1
    override val minPlayers = 2
    override val maxPlayers = 2
    override fun initial(seed: Long, players: List<PlayerId>): EndlessState { require(players.size == 2); return EndlessState(players, 0) }
    override fun legal(state: EndlessState, player: PlayerId): List<Pass> = if (state.players[state.moves % 2] == player) listOf(Pass(player)) else emptyList()
    override fun apply(state: EndlessState, move: Pass): EndlessState { require(move in legal(state, move.player)); return state.copy(moves = state.moves + 1) }
    override fun view(state: EndlessState, player: PlayerId?): Map<String, Any?> = linkedMapOf("moves" to state.moves)
    override fun outcome(state: EndlessState): Outcome = Outcome.InProgress
    override fun encodeState(state: EndlessState) = Json.write(linkedMapOf("players" to state.players.map { it.id }, "moves" to state.moves))
    override fun decodeState(text: String): EndlessState = Json.obj(text).let { m -> EndlessState((m["players"] as List<*>).map { PlayerId(it as String) }, (m["moves"] as Number).toInt()) }
    override fun encodeMove(move: Pass) = "pass"
    override fun decodeMove(text: String, player: PlayerId): Pass? = if (text == "pass") Pass(player) else null
}
