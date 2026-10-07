package castbridge.core.chess.online

import castbridge.core.chess.GameResult
import castbridge.core.chess.Outcome
import castbridge.core.wallet.PlayResult
import castbridge.core.wallet.WalletCurrency
import java.security.MessageDigest

/** Un blocage de mise vérifié par le service (`cbe1`) : [eid] = identifiant du blocage, [id] = identité du compte (code d'appareil de la TV), [amount] = `per × k` (ici k = 1). */
data class ChessEscrow(val eid: String, val id: String, val amount: Long)

/**
 * Le règlement d'une partie d'échecs misée, PUR (DESIGN-JEUX § 2, W22 § 3.3 option B) : à partir des deux blocages (un par TV) et du résultat de la partie, les lignes `[eid, id, used, pay]` du
 * résultat `cbr1` que le service signe. Le service DÉCRIT, l'API règle : rien ici ne touche un grand livre.
 *
 * Règles (propriétaire, 2026-10-07) : gagnant = les deux mises (`pay = 2 × mise`) ; perdant = mise perdue (`pay = 0`) ; nulle = chacun reprend sa mise (`pay = mise`) ; forfait, abandon,
 * temps dépassé et mat sont des défaites ; partie interrompue (salle fermée, deux TV absentes, service arrêté, adversaire jamais venu) = `ABORT` : rien n'est utilisé, chaque blocage est rendu en
 * entier. Dans tous les cas Σ payé = Σ utilisé (l'API refuse sinon) ; les frais éventuels de la plateforme sont du ressort de l'API (politique), jamais du service.
 */
object ChessSettlement {
    const val GAME = "chess"

    /** Identifiant de résultat DÉTERMINISTE d'une salle : un seul résultat possible par salle (« idempotence par identifiant de salle »), 128 bits en hexadécimal. */
    fun ridOf(roomId: String): String =
        MessageDigest.getInstance("SHA-256").digest("castbridge-chess-rid-v1\n$roomId".toByteArray(Charsets.UTF_8)).take(16).joinToString("") { "%02x".format(it) }

    /**
     * Le résultat à signer. [white] / [black] : le blocage de chaque couleur (null = personne n'est assis de ce côté). [outcome] null = partie interrompue ([PlayResult.Kind.ABORT]).
     * Une partie terminée exige les DEUX blocages (une partie misée ne commence qu'à deux joueurs misés) ; sinon c'est un échec de programmation, refusé.
     */
    fun result(kid: String, roomId: String, cur: WalletCurrency, per: Long, at: Long, white: ChessEscrow?, black: ChessEscrow?, outcome: GameResult?): PlayResult {
        val present = listOfNotNull(white, black)
        require(present.isNotEmpty()) { "aucun blocage à régler" }
        val rid = ridOf(roomId)
        if (outcome == null) {
            return PlayResult(kid, rid, roomId, GAME, cur, per, PlayResult.Kind.ABORT, at, present.map { PlayResult.Line(it.eid, it.id, 0, 0) })
        }
        require(white != null && black != null) { "une partie terminée a ses deux blocages" }
        val (pw, pb) = pays(per, outcome)
        return PlayResult(kid, rid, roomId, GAME, cur, per, PlayResult.Kind.END, at, listOf(PlayResult.Line(white.eid, white.id, per, pw), PlayResult.Line(black.eid, black.id, per, pb)))
    }

    /** Ce que chaque couleur reçoit (blanc, noir) pour une mise [per] par joueur. */
    fun pays(per: Long, outcome: GameResult): Pair<Long, Long> = when (outcome.outcome) {
        Outcome.WHITE_WINS -> 2 * per to 0L
        Outcome.BLACK_WINS -> 0L to 2 * per
        Outcome.DRAW -> per to per
    }

    /** Gain NET d'une couleur (positif : elle gagne la mise de l'autre ; négatif : elle perd la sienne ; 0 : nulle) : pour les écrans, jamais pour régler. */
    fun net(per: Long, outcome: GameResult, color: Int): Long = when {
        outcome.outcome == Outcome.DRAW -> 0L
        outcome.winner == color -> per
        else -> -per
    }
}

/** L'échelle de mises des échecs en ligne (décision du propriétaire, 2026-10-07) : valeurs de REPLI de la TV ; l'échelle qui fait foi est celle de la politique du serveur (`GET /api/v1/wallet/policy`). */
object ChessStakeScale {
    val NDEM: List<Long> = listOf(10, 20, 50, 100, 200)
    val MBOKO: List<Long> = listOf(1, 2, 5, 10)

    fun of(cur: WalletCurrency): List<Long> = if (cur == WalletCurrency.NDEM) NDEM else MBOKO
}
