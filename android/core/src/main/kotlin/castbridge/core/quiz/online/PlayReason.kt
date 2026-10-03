package castbridge.core.quiz.online

/**
 * Codes de refus du Quiz en ligne (docs/coordination/DESIGN-W20-QUIZ-EN-LIGNE-2026-10-03.md § 1.3, § 2.8). Constantes locales : `C/sync/Reason.kt`
 * (W19) n'existe pas encore sur cette branche ; au moment de la fusion elles devront être reprises dans `Reason` (codes stables, additifs).
 */
enum class PlayReason(val http: Int, val retryable: Boolean, val message: String, val retryAfterMs: Long = 0L) {
    PLAY_BAD_CODE(404, true, "Ce code de salle n'existe pas. Vérifiez-le et réessayez."),
    PLAY_ROOM_FULL(409, true, "La salle est complète. Réessayez dans un instant ou rejoignez une autre table."),
    PLAY_ROOM_GONE(410, false, "Cette salle est fermée ou a expiré."),
    PLAY_TICKET_REFUSED(403, true, "CastBridge-TV n'a pas pu ouvrir la partie sur Internet : autorisation refusée. Réessayez."),
    PLAY_BANNED(403, false, "Vous avez été retiré de cette salle par l'hôte."),
    PLAY_TLS_INVALID(502, false, "Connexion Internet non sûre : le certificat du serveur n'est pas valide. La partie ne s'ouvre pas."),
    PLAY_SCOPE_FORBIDDEN(403, false, "Ce mode de jeu n'est pas autorisé ici (réglage ou contrôle parental)."),
    /** Service ou salle saturés : l'attente conseillée est STRUCTURÉE (`retryAfterMs`, champ additif du message `error`), jamais seulement dans le texte (w20-07). */
    PLAY_BUSY(503, true, "Le service de jeu est très sollicité. Réessayez dans un instant.", 15_000L),
    /** Pseudonyme refusé : le service ajoute le motif (`Pseudonym.Reason`) au message. */
    BAD_NAME(400, true, "Pseudonyme refusé. Choisissez-en un autre.");

    val code: String get() = name

    companion object {
        fun of(code: String?): PlayReason? = values().firstOrNull { it.name == code }
    }
}
