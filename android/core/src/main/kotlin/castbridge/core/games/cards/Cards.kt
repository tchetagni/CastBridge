package castbridge.core.games.cards

import castbridge.core.games.GameRandom
import castbridge.core.games.PlayerId

/** Les quatre couleurs, dans l'ordre FIXE où un paquet neuf est construit (le mélange à graine en dépend : ne jamais le changer). */
enum class Suit(val code: Char, val symbol: Char, val label: String, val red: Boolean) {
    CLUBS('C', '♣', "trèfle", false), DIAMONDS('D', '♦', "carreau", true), HEARTS('H', '♥', "cœur", true), SPADES('S', '♠', "pique", false);

    companion object { fun of(code: Char): Suit? = values().firstOrNull { it.code == code } }
}

/** Les valeurs, du deux à l'as puis le joker. L'ORDRE DE FORCE n'est pas celui-ci : il appartient au jeu ([RankOrder]). */
enum class Rank(val code: String, val label: String) {
    TWO("2", "deux"), THREE("3", "trois"), FOUR("4", "quatre"), FIVE("5", "cinq"), SIX("6", "six"), SEVEN("7", "sept"), EIGHT("8", "huit"), NINE("9", "neuf"),
    TEN("10", "dix"), JACK("J", "valet"), QUEEN("Q", "dame"), KING("K", "roi"), ACE("A", "as"), JOKER("JK", "joker");

    companion object { fun of(code: String): Rank? = values().firstOrNull { it.code == code } }
}

/**
 * Une carte : une valeur et une couleur ; le joker n'a pas de couleur (et lui seul). Code compact « AS », « 10H », « JC » (valet de trèfle), « JK » (joker), celui des états JSON,
 * des coups et des journaux.
 */
data class Card(val rank: Rank, val suit: Suit?) {
    init { require((rank == Rank.JOKER) == (suit == null)) { "un joker n'a pas de couleur, toute autre carte en a une : $rank $suit" } }

    val code: String get() = if (suit == null) rank.code else rank.code + suit.code
    /** Pour l'écran : « A♠ », « 10♥ », « joker ». */
    val text: String get() = if (suit == null) "joker" else rank.code + suit.symbol
    /** En toutes lettres : « as de pique », « valet de cœur », « joker ». */
    val label: String get() = if (suit == null) "joker" else "${rank.label} de ${suit.label}"

    companion object {
        val JOKER = Card(Rank.JOKER, null)

        /** « AS » → as de pique ; null si ce n'est pas exactement le code d'une carte (casse, espaces et valeurs inconnues refusés). */
        fun parse(code: String): Card? {
            if (code == Rank.JOKER.code) return JOKER
            if (code.length < 2) return null
            val suit = Suit.of(code.last()) ?: return null
            val rank = Rank.of(code.dropLast(1))?.takeIf { it != Rank.JOKER } ?: return null
            return Card(rank, suit)
        }
    }
}

/**
 * Ordre de force des valeurs, du plus faible au plus fort : PARAMÉTRABLE par jeu (as haut, as bas, 10 au-dessus du roi…). Les couleurs ne comptent jamais ici
 * (voir [Trick] pour la couleur demandée et l'atout). Une valeur absente de l'ordre est une erreur de programmation du jeu, jamais une valeur « faible par défaut ».
 */
class RankOrder private constructor(val ranks: List<Rank>) {
    private val strengths: Map<Rank, Int> = ranks.withIndex().associate { (i, r) -> r to i }

    fun strength(rank: Rank): Int = strengths[rank] ?: throw IllegalArgumentException("la valeur $rank n'est pas dans l'ordre de ce jeu")
    fun compare(a: Rank, b: Rank): Int = strength(a).compareTo(strength(b))
    /** Négatif si [a] est plus faible que [b], 0 à valeur égale (quelle que soit la couleur), positif sinon. */
    fun compare(a: Card, b: Card): Int = compare(a.rank, b.rank)
    fun beats(a: Card, b: Card): Boolean = compare(a, b) > 0
    /** La plus forte ; la première rencontrée à force égale ; null pour une liste vide. */
    fun highest(cards: Collection<Card>): Card? = cards.fold(null as Card?) { best, c -> if (best == null || beats(c, best)) c else best }
    /** Du plus faible au plus fort, tri stable (à force égale, l'ordre d'origine est gardé). */
    fun sorted(cards: Collection<Card>): List<Card> = cards.sortedBy { strength(it.rank) }
    /** Le même ordre avec le joker au-dessus de tout ([top]) ou au-dessous de tout. */
    fun withJoker(top: Boolean): RankOrder = of(*(if (top) ranks + Rank.JOKER else listOf(Rank.JOKER) + ranks).toTypedArray())

    override fun equals(other: Any?) = other is RankOrder && other.ranks == ranks
    override fun hashCode() = ranks.hashCode()
    override fun toString() = ranks.joinToString(" < ") { it.code }

    companion object {
        private val NATURAL = listOf(Rank.TWO, Rank.THREE, Rank.FOUR, Rank.FIVE, Rank.SIX, Rank.SEVEN, Rank.EIGHT, Rank.NINE, Rank.TEN, Rank.JACK, Rank.QUEEN, Rank.KING)
        /** 2 < 3 < … < roi < as. Convient aux paquets de 32 cartes (les valeurs absentes ne servent simplement pas). */
        val ACE_HIGH = of(*(NATURAL + Rank.ACE).toTypedArray())
        /** as < 2 < … < roi. */
        val ACE_LOW = of(*(listOf(Rank.ACE) + NATURAL).toTypedArray())

        /** Un ordre à soi : du plus faible au plus fort, sans doublon, au moins une valeur. */
        fun of(vararg ranks: Rank): RankOrder {
            require(ranks.isNotEmpty()) { "un ordre de force a au moins une valeur" }
            require(ranks.toSet().size == ranks.size) { "une valeur ne peut pas figurer deux fois dans l'ordre" }
            return RankOrder(ranks.toList())
        }
    }
}

/** Ce que reçoivent les joueurs d'une distribution, et ce qui reste dans la pioche. */
data class Deal(val hands: Map<PlayerId, List<Card>>, val rest: CardDeck)

/**
 * Un paquet : la PIOCHE (la carte du dessus est la première de la liste) et la DÉFAUSSE (la dernière carte posée est la dernière de la liste). Immuable : chaque opération rend
 * un nouveau paquet, ce qui rend les règles des jeux (états immuables) faciles à tester et à rejouer. Un exemplaire de chaque carte par paquet ; plusieurs paquets mélangés
 * ([standard] avec `decks` > 1) donnent des cartes identiques, interchangeables.
 *
 * Construction d'un paquet NEUF, dans cet ordre figé : pour chaque couleur (trèfle, carreau, cœur, pique), toutes les valeurs de la plus basse à l'as ; puis les jokers ; puis,
 * pour plusieurs paquets, le même contenu de nouveau. Un mélange est [shuffled] avec la graine de l'autorité ([GameRandom] : même résultat partout).
 */
data class CardDeck(val drawPile: List<Card>, val discardPile: List<Card> = emptyList()) {
    /** Cartes restant dans la pioche. */
    val size: Int get() = drawPile.size
    val isEmpty: Boolean get() = drawPile.isEmpty()

    /** La pioche mélangée par la graine [seed] ; la défausse n'est pas touchée. */
    fun shuffled(seed: Long): CardDeck = copy(drawPile = drawPile.toMutableList().also { GameRandom(seed).shuffle(it) })

    /** La carte du dessus (null si la pioche est vide) et le paquet sans elle. */
    fun draw(): Pair<Card?, CardDeck> = drawPile.firstOrNull() to copy(drawPile = drawPile.drop(1))

    /** Jusqu'à [count] cartes du dessus (moins s'il n'y en a pas assez), la première tirée en premier, et le paquet sans elles. */
    fun draw(count: Int): Pair<List<Card>, CardDeck> {
        require(count >= 0) { "nombre de cartes négatif : $count" }
        return drawPile.take(count) to copy(drawPile = drawPile.drop(count))
    }

    /**
     * Distribution comme à la table : une carte à la fois, de joueur en joueur, en commençant par le premier. [perPlayer] cartes chacun, ou tout le paquet si null (certains
     * joueurs ont alors une carte de plus). Refuse une table vide, un joueur en double ou une demande plus grande que la pioche.
     */
    fun deal(players: List<PlayerId>, perPlayer: Int?): Deal {
        require(players.isNotEmpty()) { "personne à qui distribuer" }
        require(players.toSet().size == players.size) { "un joueur ne peut pas figurer deux fois à la table" }
        require(perPlayer == null || perPlayer >= 0) { "nombre de cartes négatif : $perPlayer" }
        val total = if (perPlayer == null) drawPile.size else perPlayer * players.size
        require(total <= drawPile.size) { "pas assez de cartes : $total demandées, ${drawPile.size} dans la pioche" }
        val hands = players.associateWith { ArrayList<Card>() }
        for (i in 0 until total) hands.getValue(players[i % players.size]) += drawPile[i]
        return Deal(hands, copy(drawPile = drawPile.drop(total)))
    }

    /** Pose [cards] sur la défausse (au-dessus de ce qui y est). */
    fun discard(cards: List<Card>): CardDeck = copy(discardPile = discardPile + cards)

    /** La défausse, mélangée par [seed], passe SOUS ce qui reste de la pioche ; la défausse est vide ensuite (pioche épuisée au milieu d'une manche). */
    fun recycleDiscard(seed: Long): CardDeck =
        CardDeck(drawPile + discardPile.toMutableList().also { GameRandom(seed).shuffle(it) }, emptyList())

    /** `{"draw":["AS",…],"discard":[…]}` : le format des états sérialisés. */
    fun toJson(): Map<String, Any?> = linkedMapOf("draw" to drawPile.map { it.code }, "discard" to discardPile.map { it.code })

    companion object {
        private val RANKS_52 = listOf(Rank.TWO, Rank.THREE, Rank.FOUR, Rank.FIVE, Rank.SIX, Rank.SEVEN, Rank.EIGHT, Rank.NINE, Rank.TEN, Rank.JACK, Rank.QUEEN, Rank.KING, Rank.ACE)
        private val RANKS_32 = listOf(Rank.SEVEN, Rank.EIGHT, Rank.NINE, Rank.TEN, Rank.JACK, Rank.QUEEN, Rank.KING, Rank.ACE)

        /** Un paquet neuf, non mélangé : [size] = 32 (du 7 à l'as) ou 52 (du 2 à l'as), plus [jokers] jokers, en [decks] paquets. */
        fun standard(size: Int = 52, jokers: Int = 0, decks: Int = 1): CardDeck {
            require(size == 32 || size == 52) { "un paquet a 32 ou 52 cartes : $size" }
            require(jokers >= 0) { "nombre de jokers négatif : $jokers" }
            require(decks >= 1) { "au moins un paquet : $decks" }
            val ranks = if (size == 32) RANKS_32 else RANKS_52
            val one = Suit.values().flatMap { s -> ranks.map { r -> Card(r, s) } } + List(jokers) { Card.JOKER }
            return CardDeck(List(decks) { one }.flatten())
        }

        /** Relit [toJson] ; refuse tout ce qui n'est pas exactement cela (clé absente, texte qui n'est pas une carte). */
        fun fromJson(m: Map<String, Any?>): CardDeck {
            fun cards(k: String): List<Card> {
                val l = m[k] as? List<*> ?: throw IllegalArgumentException("« $k » absent ou illisible")
                return l.map { (it as? String)?.let(Card::parse) ?: throw IllegalArgumentException("carte illisible dans « $k » : $it") }
            }
            return CardDeck(cards("draw"), cards("discard"))
        }
    }
}

/** Une carte jouée par un joueur. */
data class Play(val player: PlayerId, val card: Card)

/**
 * Une levée : les cartes jouées, dans l'ordre. La première donne la couleur demandée. [winner] applique la règle universelle des jeux de levées : le plus fort atout s'il y en a,
 * sinon la plus forte carte de la couleur demandée (une carte d'une autre couleur ne gagne jamais). Aucune règle propre à un jeu précis ici.
 */
class Trick(val plays: List<Play>) {
    /** La couleur demandée (celle de la première carte), null si la levée est vide ou ouverte par un joker. */
    val led: Suit? get() = plays.firstOrNull()?.card?.suit

    /**
     * La carte gagnante, ou null s'il n'y a rien à départager : levée vide, ou DEUX cartes de force égale au sommet (une « bataille »). [followSuit] = faux : les couleurs
     * ne comptent pas, seule la valeur (la Bataille). [trump] : la couleur d'atout, s'il y en a une.
     */
    fun winner(order: RankOrder, trump: Suit? = null, followSuit: Boolean = true): Play? {
        if (plays.isEmpty()) return null
        val trumps = if (trump == null) emptyList() else plays.filter { it.card.suit == trump }
        val candidates = when {
            trumps.isNotEmpty() -> trumps
            followSuit && led != null -> plays.filter { it.card.suit == led }
            else -> plays
        }
        val best = candidates.reduce { a, b -> if (order.beats(b.card, a.card)) b else a }
        val tied = candidates.count { order.compare(it.card, best.card) == 0 }
        return if (tied > 1) null else best
    }
}
