package castbridge.core.quiz.online

/**
 * Délai entre deux questions d'une partie en ligne (exigence du propriétaire, 2026-10-03 : « entre 2 questions laisse 1 ou 2 s de latence
 * pour pouvoir synchroniser les parties en ligne »). Fonctions pures, toutes sur l'horloge du SERVEUR ; aucune horloge de client n'entre ici.
 *
 * Protocole : une fois la question révélée à `revealAtServerMs`, la suivante est ANNONCÉE tout de suite mais ne s'OUVRE qu'à
 * `opensAtServerMs = revealAtServerMs + gap`. Personne ne peut répondre avant ; le temps de réponse compte depuis `opensAtServerMs`,
 * jamais depuis l'arrivée d'un message. Les parties `TV_ONLY` et `LAN` gardent leur comportement actuel (délai 0).
 */
object PlayTiming {
    const val INTER_QUESTION_GAP_MS = 1_500L
    const val MIN_GAP_MS = 1_000L
    const val MAX_GAP_MS = 2_000L

    /** Délai demandé ramené dans 1 000..2 000 ms. */
    fun gap(requestedMs: Long): Long = requestedMs.coerceIn(MIN_GAP_MS, MAX_GAP_MS)

    /** Délai appliqué selon le périmètre : 0 pour `TV_ONLY` et `LAN` (inchangé), le délai borné pour `INTERNET`. */
    fun gapFor(scope: PlayScope, requestedMs: Long = INTER_QUESTION_GAP_MS): Long = if (scope == PlayScope.INTERNET) gap(requestedMs) else 0L

    /** Instant (horloge serveur) où la question suivante s'ouvre. */
    fun opensAt(revealAtServerMs: Long, scope: PlayScope, requestedMs: Long = INTER_QUESTION_GAP_MS): Long =
        revealAtServerMs + gapFor(scope, requestedMs)

    /** Annonce de la question suivante : porte l'instant absolu d'ouverture. */
    data class Announcement(val questionId: String, val opensAtServerMs: Long)

    fun announce(questionId: String, revealAtServerMs: Long, scope: PlayScope, requestedMs: Long = INTER_QUESTION_GAP_MS) =
        Announcement(questionId, opensAt(revealAtServerMs, scope, requestedMs))

    enum class Gate { TOO_EARLY, OPEN }

    /** Une réponse reçue à `serverNowMs` n'est acceptée qu'à partir de l'ouverture. */
    fun gate(serverNowMs: Long, opensAtServerMs: Long): Gate = if (serverNowMs >= opensAtServerMs) Gate.OPEN else Gate.TOO_EARLY

    /** Temps de réponse pour le calcul des points : compté depuis l'ouverture ; null si la réponse est arrivée trop tôt. */
    fun scoringElapsedMs(opensAtServerMs: Long, answeredAtServerMs: Long): Long? =
        if (answeredAtServerMs >= opensAtServerMs) answeredAtServerMs - opensAtServerMs else null

    /**
     * Ce que fait un client à la réception de l'annonce. `announcementServerNowMs` est l'heure du SERVEUR inscrite dans le message :
     * une annonce tardive démarre aussitôt, et la fenêtre n'est raccourcie que du temps mesuré par le serveur (jamais par l'horloge du client).
     */
    data class Start(val startNow: Boolean, val waitMs: Long, val remainingMs: Long)

    fun start(opensAtServerMs: Long, windowMs: Long, announcementServerNowMs: Long): Start {
        val wait = opensAtServerMs - announcementServerNowMs
        return if (wait > 0) Start(false, wait, windowMs)
        else Start(true, 0L, (windowMs + wait).coerceAtLeast(0L))
    }

    /** « Question suivante dans 1,5 s » ; null si l'attente est finie. */
    fun countdownLabel(waitMs: Long): String? {
        if (waitMs <= 0) return null
        val tenths = ((waitMs + 99) / 100).toInt()
        val whole = tenths / 10; val frac = tenths % 10
        return "Question suivante dans " + (if (frac == 0) "$whole s" else "$whole,$frac s")
    }
}
