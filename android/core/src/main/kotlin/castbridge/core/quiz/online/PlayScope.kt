package castbridge.core.quiz.online

/** Les trois périmètres d'une partie de Quiz (docs/coordination/DESIGN-W20-QUIZ-EN-LIGNE-2026-10-03.md § 1.1) : icône, étiquette et promesse affichées toujours. */
enum class PlayScope(val icon: String, val label: String, val promise: String) {
    TV_ONLY("▣", "TV seule", "Personne d'autre ne peut entrer ; rien ne sort de la maison."),
    LAN("⌂", "Réseau local", "Les téléphones du même Wi-Fi jouent avec la TV ; rien ne sort de la maison."),
    INTERNET("◎", "Internet", "Des joueurs à distance peuvent entrer ; connexion chiffrée, pseudonymes seulement.");

    /** Des joueurs hors de la maison peuvent-ils entrer ? */
    fun allowsRemotePlayers(): Boolean = this == INTERNET

    /** La partie peut-elle faire un appel réseau sortant ? `TV_ONLY` et `LAN` : jamais. */
    fun mayUseNetwork(): Boolean = this == INTERNET

    /** Ce périmètre n'est proposé que si la TV voit Internet. */
    fun requiresInternet(): Boolean = this == INTERNET
}
