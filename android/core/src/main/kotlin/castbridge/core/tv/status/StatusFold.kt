package castbridge.core.tv.status

import castbridge.core.tv.PlayerIcons

/**
 * Barre d'état de CastBridge-TV enroulable (demande du propriétaire du 2026-10-27, photo de la TV : « Elles devraient être enroulables et déroulables, rangées en haut à droite »).
 * ENROULÉE par défaut : une seule pastille dans l'en-tête (pire niveau + nombre, jamais la couleur seule), aucun libellé permanent ; un changement ne déroule rien, il fait
 * pulser la pastille et lui prête un libellé de [LABEL_MS] qui reste dans la bande d'en-tête. DÉROULÉE (OK sur la pastille, accueil seulement) : le panneau des pastilles avec libellés,
 * qui s'enroule seul après [AUTO_ROLL_MS] sans touche. L'état n'est jamais mémorisé (toujours enroulé au départ). Règles PURES ; la présence de la zone reste [PlayerIcons.barVisible] (R-53).
 */
object StatusFold {
    const val AUTO_ROLL_MS = 10_000L
    const val LABEL_MS = 4_000L

    /** Gravité pour « le pire état » : rouge > orange > bleu (en cours) > gris (pas mesuré) > vert > noir (volontairement éteint). */
    fun severity(l: StatusLevel): Int = when (l) {
        StatusLevel.ERROR -> 5; StatusLevel.WARN -> 4; StatusLevel.BUSY -> 3; StatusLevel.UNKNOWN -> 2; StatusLevel.OK -> 1; StatusLevel.OFF -> 0
    }

    /** Le signe imprimé dans la pastille : la forme porte le niveau avec la couleur (caractères sûrs sur les vieilles polices de TV). */
    fun glyph(l: StatusLevel): String = when (l) {
        StatusLevel.ERROR -> "×"; StatusLevel.WARN -> "!"; StatusLevel.BUSY -> "…"; StatusLevel.UNKNOWN -> "?"; StatusLevel.OK -> "OK"; StatusLevel.OFF -> "–"
    }

    /** Ce que montre la pastille enroulée : le pire niveau, le nombre de pastilles et combien sont en alerte (orange ou rouge). */
    data class Pill(val level: StatusLevel, val count: Int, val urgent: Int) {
        val text: String get() = if (count == 0) glyph(level) else "${glyph(level)} $count"
        val description: String get() = when {
            count == 0 -> "Aucune pastille d'état"
            urgent > 0 -> "$count pastilles d'état, dont $urgent en alerte (${level.colour.lowercase()}). OK pour les dérouler"
            else -> "$count pastilles d'état, la plus sévère est ${level.colour.lowercase()}. OK pour les dérouler"
        }
    }

    fun pill(badges: List<Badge>): Pill = Pill(badges.maxByOrNull { severity(it.level) }?.level ?: StatusLevel.OFF, badges.size, badges.count { it.urgent })

    /**
     * Le libellé passager de la pastille enroulée : la pastille changée il y a moins de [LABEL_MS] (la plus grave d'abord, puis l'ordre d'affichage) ; null sinon.
     * Une alerte qui dure ne garde pas de libellé : seul le changement en donne un. [changedAt] : id → date du dernier changement.
     */
    fun transientLabel(badges: List<Badge>, changedAt: Map<String, Long>, now: Long): Badge? =
        badges.filter { b -> changedAt[b.id]?.let { now - it in 0 until LABEL_MS } == true }.maxByOrNull { severity(it.level) }

    /** Date à laquelle le libellé passager doit disparaître (null : rien à programmer). */
    fun labelEndsAt(changedAt: Map<String, Long>, now: Long): Long? = changedAt.values.filter { now - it in 0 until LABEL_MS }.maxOrNull()?.plus(LABEL_MS)

    /** Le déroulé n'existe que sur l'accueil (le seul lieu où la barre prend la télécommande) ; pendant la vidéo, le MENU, la bibliothèque : pastille seule. */
    fun canExpand(place: PlayerIcons.Place): Boolean = place == PlayerIcons.Place.HOME
}

/** L'automate enroulé / déroulé. Le temps est passé en paramètre (testable) ; aucune persistance. */
class StatusFoldState {
    var expanded = false; private set
    private var lastKeyAt = 0L

    /** OK sur la pastille : déroule si le lieu le permet, sinon enroule. Rend l'état obtenu. */
    fun toggle(place: PlayerIcons.Place, now: Long): Boolean = if (expanded) { collapse(); false } else expand(place, now)

    fun expand(place: PlayerIcons.Place, now: Long): Boolean {
        if (!StatusFold.canExpand(place)) return false
        expanded = true; lastKeyAt = now; return true
    }

    fun collapse() { expanded = false }

    /** Toute touche pendant le déroulé repousse l'enroulement automatique. */
    fun key(now: Long) { if (expanded) lastKeyAt = now }

    /** Le lieu change (vidéo, MENU, bibliothèque...) : on enroule. */
    fun placeChanged(place: PlayerIcons.Place) { if (!StatusFold.canExpand(place)) expanded = false }

    /** Enroule si [StatusFold.AUTO_ROLL_MS] sans touche ; vrai si l'état vient de changer. */
    fun tick(now: Long): Boolean { if (expanded && now - lastKeyAt >= StatusFold.AUTO_ROLL_MS) { expanded = false; return true }; return false }

    /** Millisecondes avant l'enroulement automatique (null : enroulé). */
    fun remainingMs(now: Long): Long? = if (expanded) (StatusFold.AUTO_ROLL_MS - (now - lastKeyAt)).coerceAtLeast(0) else null
}
