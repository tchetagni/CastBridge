package castbridge.core.quiz.online

import castbridge.core.quiz.Pot
import castbridge.core.wallet.PlayResult
import castbridge.core.wallet.WalletCurrency
import java.security.MessageDigest

/**
 * Un blocage de mise vérifié par le service (`cbe1`) pour UNE TV d'un Quiz misé (games-G5, conception W22 § 3.3 règle 1) : [eid] = identifiant du blocage, [id] = identité du compte (code d'appareil de
 * la TV), [seats] = sièges de cette TV qui misent (`k`, 1 à 8 : ses téléphones relayés), [amount] = mise par siège × [seats]. Une mise est PAR SIÈGE et payée par le compte de la TV.
 */
data class QuizEscrow(val eid: String, val id: String, val seats: Int, val amount: Long)

/**
 * Le règlement d'un Quiz misé, PUR (W22 § 3.3 option B) : à partir des blocages (un par TV), des joueurs qui misent (figés au départ) et de leurs points, les lignes `[eid, id, utilisé, versé]` du résultat
 * `cbr1` que le service signe. Le service DÉCRIT, l'API règle : rien ici ne touche un grand livre ni ne calcule un solde.
 *
 * Règles (W22 § 3.3 : cagnotte par `Pot.split` du Quiz, puis agrégée PAR BLOCAGE) :
 * - utilisé d'une TV = mise × ses sièges qui misent au départ (au plus son `k` ; le non-utilisé est rendu par l'API) ; cagnotte = somme des utilisés ;
 * - la cagnotte est partagée selon le classement des sièges qui misent : 1 siège 100 % ; 2 sièges 70/30 ; 3 et plus 60/30/10 ; **ex æquo à parts égales** (ils se partagent les places qu'ils couvrent) ;
 *   0 point = rien ; le reste de l'arrondi va au meilleur ; la part d'une TV = la somme des parts de ses sièges ;
 * - **personne n'a marqué** : la cagnotte n'est pas distribuable, chaque TV reprend sa mise utilisée (versé = utilisé) ; la conservation prime ;
 * - partie interrompue (salle fermée, expirée, annulée avant le départ, arrêt du service) : `ABORT`, rien n'est utilisé, chaque blocage est rendu en entier ; un hôte perdu n'interrompt PAS une partie misée
 *   (elle continue, ses joueurs ne marquent plus, sa mise reste en jeu) ;
 * - dans tous les cas Σ versé = Σ utilisé (l'API refuse sinon) ; les frais de plateforme éventuels sont du ressort de l'API (politique du jeu), jamais du service.
 */
object QuizSettlement {
    const val GAME = PlayProtocol.GAME_QUIZ

    /** Identifiant de résultat DÉTERMINISTE d'une salle (un seul résultat possible par salle : l'idempotence du règlement), 128 bits en hexadécimal. */
    fun ridOf(roomId: String): String =
        MessageDigest.getInstance("SHA-256").digest("castbridge-quiz-rid-v1\n$roomId".toByteArray(Charsets.UTF_8)).take(16).joinToString("") { "%02x".format(it) }

    /**
     * Les parts de la cagnotte par joueur (avant frais) : [stakers] = les joueurs qui misent, par blocage et dans l'ordre des sièges ; [scores] = leurs points (un joueur absent de la table vaut 0).
     * Tous à zéro quand personne n'a marqué. Somme = Σ des mises utilisées quand quelqu'un a marqué.
     */
    fun shares(per: Long, stakers: Map<String, List<String>>, scores: Map<String, Int>): Map<String, Long> {
        val seats = stakers.values.flatten()
        val pot = per * seats.size
        return Pot.split(pot, seats.associateWith { scores[it] ?: 0 })
    }

    /**
     * Le résultat à signer. [escrows] : un blocage par TV, dans l'ordre d'arrivée. [stakers] : par identifiant de blocage, les joueurs qui misent (au plus `seats` du blocage). [scores] null = partie
     * interrompue ([PlayResult.Kind.ABORT]) ; sinon les points finaux de ces joueurs.
     */
    fun result(kid: String, roomId: String, cur: WalletCurrency, per: Long, at: Long, escrows: List<QuizEscrow>, stakers: Map<String, List<String>>, scores: Map<String, Int>?): PlayResult {
        require(escrows.isNotEmpty()) { "aucun blocage à régler" }
        val rid = ridOf(roomId)
        if (scores == null) return PlayResult(kid, rid, roomId, GAME, cur, per, PlayResult.Kind.ABORT, at, escrows.map { PlayResult.Line(it.eid, it.id, 0, 0) })
        val used = escrows.associate { e ->
            val n = stakers[e.eid]?.size ?: 0
            require(n <= e.seats && per * n <= e.amount) { "plus de joueurs qui misent que de sièges bloqués" }
            e.eid to per * n
        }
        val shares = shares(per, stakers, scores)
        val distributable = shares.values.sum() > 0L
        val lines = escrows.map { e ->
            val u = used.getValue(e.eid)
            val pay = if (!distributable) u else stakers[e.eid].orEmpty().sumOf { shares[it] ?: 0L }
            PlayResult.Line(e.eid, e.id, u, pay)
        }
        return PlayResult(kid, rid, roomId, GAME, cur, per, PlayResult.Kind.END, at, lines)
    }

    /** Gain NET avant frais d'une TV d'après sa ligne : positif = elle gagne ; négatif = elle perd ; 0 = mise rendue. Pour les écrans, jamais pour régler. */
    fun net(line: PlayResult.Line, aborted: Boolean): Long = if (aborted) 0L else line.pay - line.used
}
