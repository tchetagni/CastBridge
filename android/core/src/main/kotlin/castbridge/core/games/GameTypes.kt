package castbridge.core.games

/**
 * Un joueur d'une partie : l'identité d'une PLACE (« s1 », « s2 »…), stable pendant toute la partie. La salle fait le lien entre les personnes (jetons,
 * téléphones) et les places ; les règles ne connaissent que des places.
 */
@JvmInline
value class PlayerId(val id: String) {
    init { require(ID.matches(id)) { "identifiant de place invalide : « $id »" } }
    override fun toString(): String = id

    companion object {
        private val ID = Regex("^[A-Za-z0-9_-]{1,16}$")
        /** La place numéro [index] (à partir de 0) : « s1 », « s2 »… */
        fun seat(index: Int): PlayerId = PlayerId("s${index + 1}")
    }
}

/**
 * Un coup. Il porte son AUTEUR : la plateforme ne fait jamais confiance au client pour dire qui joue (la salle fabrique le coup avec la place de celui qui l'envoie, voir
 * [GameRules.decodeMove]). Doit avoir l'égalité par valeur (`data class`) : un coup est légal s'il figure dans [GameRules.legal].
 */
interface GameMove { val player: PlayerId }

/** Où en est une partie, selon les règles : en cours, un ou des gagnants, nulle, ou des points. */
sealed class Outcome {
    object InProgress : Outcome() { override fun toString() = "InProgress" }
    /** Un gagnant, ou une équipe gagnante : au moins un joueur. */
    data class Winners(val winners: List<PlayerId>) : Outcome() { init { require(winners.isNotEmpty()) { "des gagnants sans joueur" } } }
    object Draw : Outcome() { override fun toString() = "Draw" }
    /** Des points par joueur (plus haut = mieux) ; [leaders] = les meilleurs (plusieurs en cas d'égalité). */
    data class Scores(val scores: Map<PlayerId, Int>) : Outcome() {
        init { require(scores.isNotEmpty()) { "des points sans joueur" } }
        val leaders: List<PlayerId> get() = scores.values.max().let { top -> scores.filterValues { it == top }.keys.toList() }
    }

    val over: Boolean get() = this !is InProgress

    /** `{"kind":"WINNERS","winners":["s1"]}`, `{"kind":"SCORES","scores":{"s1":3,"s2":1}}`… : le format des états et des journaux. */
    fun toJson(): Map<String, Any?> = when (this) {
        is InProgress -> linkedMapOf("kind" to "IN_PROGRESS")
        is Winners -> linkedMapOf("kind" to "WINNERS", "winners" to winners.map { it.id })
        is Draw -> linkedMapOf("kind" to "DRAW")
        is Scores -> linkedMapOf("kind" to "SCORES", "scores" to scores.entries.associate { it.key.id to it.value })
    }

    companion object {
        /** Relit [toJson] ; null si ce n'est pas exactement cela. */
        fun fromJson(m: Map<String, Any?>): Outcome? = runCatching {
            when (m["kind"]) {
                "IN_PROGRESS" -> InProgress
                "WINNERS" -> Winners((m["winners"] as List<*>).map { PlayerId(it as String) })
                "DRAW" -> Draw
                "SCORES" -> Scores((m["scores"] as Map<*, *>).entries.associate { (k, v) -> PlayerId(k as String) to (v as Number).toInt() })
                else -> null
            }
        }.getOrNull()
    }
}

/** Pourquoi une partie s'est arrêtée. */
enum class EndReason {
    /** Les règles du jeu l'ont décidé (fin de manche, plus de cartes…). */
    RULES,
    /** Un joueur a abandonné. */
    RESIGNATION,
    /** Un joueur n'a pas joué dans le temps de la pendule. */
    TIMEOUT,
    /** Un joueur est resté déconnecté plus longtemps que la reprise permise. */
    DISCONNECTED,
    /** La partie a été arrêtée sans perdant (salle fermée, tous les joueurs partis). */
    ABANDONED,
}

/**
 * La fin d'une partie : le motif, l'issue et, pour un abandon, un temps dépassé ou une déconnexion, celui qui en est la cause ([by]). Dans ces trois cas tous LES AUTRES joueurs
 * gagnent ([Outcome.Winners]) ; ce qu'un jeu précis fait d'un abandon à plus de deux (points, équipes) viendra avec ses règles.
 */
data class GameResult(val reason: EndReason, val outcome: Outcome, val by: PlayerId? = null) {
    fun toJson(): Map<String, Any?> = linkedMapOf("reason" to reason.name, "outcome" to outcome.toJson(), "by" to by?.id)
}

/**
 * LES RÈGLES D'UN JEU À TOUR DE RÔLE, pures (aucune horloge, aucun réseau, aucun aléa caché) : le même code tourne sur la TV, sur le serveur et chez celui qui rejoue un journal.
 * Une « fiche de règles exécutable » (docs/GAMES.md) est une implémentation de cette interface accompagnée de la partie exemple du propriétaire comme test de référence.
 *
 * - l'ÉTAT [S] est immuable ; [apply] en rend un nouveau ;
 * - tout ce qui est au hasard sort de la graine donnée à [initial] (voir [GameRandom] et `cards/CardDeck`) : même graine, même partie ;
 * - l'état complet n'existe que chez l'autorité ; ce que voit un joueur est [view] (jamais la main des autres).
 */
interface GameRules<S, M : GameMove> {
    /** Identifiant stable du jeu (« bataille ») : celui de l'URL `/jeux/<id>`, des journaux et de la fiche de règles. */
    val id: String
    /** Version des règles, à augmenter à CHAQUE changement qui modifie une partie : un journal dit avec quelles règles il a été joué. */
    val version: Int
    val minPlayers: Int
    val maxPlayers: Int

    /** L'état de départ de la partie [seed] pour ces joueurs (dans l'ordre des places). Refuse un nombre de joueurs hors de [minPlayers]..[maxPlayers]. */
    fun initial(seed: Long, players: List<PlayerId>): S

    /** Les coups légaux de [player] MAINTENANT : vide s'il n'a pas la main ou si la partie est finie. */
    fun legal(state: S, player: PlayerId): List<M>

    /** L'état après [move]. Refuse (IllegalArgumentException) un coup illégal : la plateforme ne l'appelle qu'avec un coup pris dans [legal]. */
    fun apply(state: S, move: M): S

    /**
     * Ce que voit [player] (null = la table, les spectateurs, l'écran de la TV) : des textes, des nombres, des listes et des tables prêts à devenir du JSON. Sa main, oui ; celles des
     * autres, jamais ; ni la pioche, ni la graine.
     */
    fun view(state: S, player: PlayerId?): Map<String, Any?>

    fun outcome(state: S): Outcome

    /** Le joueur qui doit jouer maintenant (plusieurs si les règles le permettent). Par défaut : ceux qui ont au moins un coup légal. */
    fun toMove(state: S, players: List<PlayerId>): List<PlayerId> = players.filter { legal(state, it).isNotEmpty() }

    /** Le coup joué à la place d'un joueur qui n'a pas joué à temps (pendule en mode « coup d'office »). Par défaut : le premier coup légal. */
    fun fallbackMove(state: S, player: PlayerId): M? = legal(state, player).firstOrNull()

    /** L'état COMPLET en JSON (autorité seulement : sauvegarde, reprise, relais entre autorités). Jamais envoyé à un joueur. */
    fun encodeState(state: S): String
    fun decodeState(text: String): S

    /** Un coup en texte court (celui des requêtes et des journaux : « flip », « play:10H »…) et son contraire pour [player] ; null si le texte n'est pas un coup de ce jeu. */
    fun encodeMove(move: M): String
    fun decodeMove(text: String, player: PlayerId): M?
}

/** Un adversaire ordinateur : choisit un coup légal. Les jeux de cartes réels apportent le leur ; [FirstLegalAi] suffit aux démonstrations. */
fun interface GameAi<S, M : GameMove> {
    fun choose(rules: GameRules<S, M>, state: S, player: PlayerId): M?
}

/** L'ordinateur le plus simple : joue toujours le premier coup légal. */
class FirstLegalAi<S, M : GameMove> : GameAi<S, M> {
    override fun choose(rules: GameRules<S, M>, state: S, player: PlayerId): M? = rules.legal(state, player).firstOrNull()
}
