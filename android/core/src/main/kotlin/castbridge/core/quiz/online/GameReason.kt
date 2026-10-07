package castbridge.core.quiz.online

/**
 * Refus propres aux salles de jeu à tour de rôle (`game:chess`) et à leurs mises (DESIGN-JEUX-CARTES-ET-ECHECS-EN-LIGNE, chantier games-G2). Codes STABLES, textes français pour l'écran de la
 * TV ; additif : [PlayReason] (Quiz) n'est pas touché. Le message `error` les porte dans `reason` ; `STAKE_ESCROW_REQUIRED` ajoute `data = {game, cur, per}` (la mise à bloquer).
 */
enum class GameReason(val http: Int, val retryable: Boolean, val message: String) {
    /** Les échecs en ligne sont coupés sur ce service (`CASTBRIDGE_PLAY_CHESS=off`). */
    GAME_UNAVAILABLE(503, true, "Les échecs en ligne ne sont pas ouverts sur ce service pour le moment."),
    /** Jeu inconnu de ce service (TV plus récente que le service). */
    GAME_UNKNOWN(400, false, "Ce jeu n'est pas encore proposé par le service en ligne."),
    /** Interrupteur d'exploitation : les mises sont coupées, les parties libres restent ouvertes. */
    STAKES_SUSPENDED(503, true, "Mises suspendues pour maintenance : les parties sans mise restent ouvertes."),
    /** Les mises en ligne sont réservées aux TV de production (règle du propriétaire : essai = parties libres seulement). */
    STAKE_TRIAL_FREE_ONLY(403, false, "Version d'essai : parties libres seulement, sans mise. Passez en version complète pour miser."),
    /** La salle est misée : la TV doit joindre son blocage `cbe1` (la mise est dans `data`). */
    STAKE_ESCROW_REQUIRED(409, true, "Cette partie se joue avec une mise : bloquez votre mise pour entrer."),
    /** Blocage `cbe1` refusé : signature, audience, échéance, autre TV, autre monnaie ou autre mise, déjà employé. */
    STAKE_ESCROW_INVALID(403, false, "La mise bloquée n'est pas valable pour cette partie : demandez un nouveau blocage."),
    /** Monnaie ou montant de mise hors de ce que le service admet. */
    STAKE_BAD(400, false, "Mise refusée : montant ou monnaie non admis."),
    /** La salle n'a plus de place de joueur (deux TV jouent déjà, ou la partie a commencé). */
    SEATS_TAKEN(409, true, "Cette partie a déjà ses deux joueurs : vous pouvez la regarder."),
    /** La même TV ne joue pas contre elle-même. */
    SAME_TV(409, false, "Votre TV est déjà dans cette partie : une TV ne joue pas contre elle-même.");

    val code: String get() = name

    companion object {
        fun of(code: String?): GameReason? = values().firstOrNull { it.name == code }
    }
}
