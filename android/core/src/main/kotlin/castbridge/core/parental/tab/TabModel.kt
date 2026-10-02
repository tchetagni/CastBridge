package castbridge.core.parental.tab

import castbridge.core.parental.SupervisionState

/**
 * Data model of the « Parental » tab of the phone (docs/PARENTAL.md, « Onglet Parental »). Everything here is pure and local: the phone
 * rebuilds its views from the reports the TV delivered ([ParentalLedger]); nothing is asked of a server.
 *
 * Truthfulness rule: a figure always travels with its [Quality]. MEASURED = CastBridge-TV, counted systematically by the TV itself;
 * BEST_EFFORT = other apps of the TV seen through the whole-TV supervision; UNAVAILABLE = nobody measured it (no report, supervision
 * off): such a figure is NEVER shown as a number, and never as zero.
 */
enum class Quality(val code: String, val label: String) {
    MEASURED("measured", "MESURÉ"), BEST_EFFORT("best_effort", "MEILLEUR EFFORT"), UNAVAILABLE("unavailable", "INDISPONIBLE");

    /** The weaker of two qualities (a total that mixes both is only as good as its worst part, unavailable parts excluded by the caller). */
    fun min(o: Quality) = if (ordinal >= o.ordinal) this else o
}

enum class EventType(val code: String, val label: String, val group: Group) {
    VIDEO("video", "Vidéo", Group.VIDEOS), GAME("game", "Jeu", Group.GAMES), LEARN("learn", "Apprendre", Group.LEARN), QUIZ("quiz", "Quiz", Group.QUIZ),
    DOWNLOAD("download", "Téléchargement", Group.DOWNLOADS), APP("app", "Application", Group.APPS), BLOCK("block", "Blocage", Group.BLOCKS),
    ALERT("alert", "Alerte", Group.ALERTS), REMOTE("remote", "Télécommande", Group.OTHER), SESSION("session", "Session", Group.OTHER),
    QUOTA("quota", "Temps / horaires", Group.BLOCKS), UNLOCK("unlock", "Tentative de déverrouillage", Group.BLOCKS),
    SUDOKU("sudoku", "Sudoku", Group.GAMES), SCREEN("screen", "Écran de la TV", Group.OTHER), CONNECTION("connection", "Connexion à la TV", Group.OTHER);

    /** The filter chips of the timeline. */
    enum class Group(val label: String) { VIDEOS("Vidéos"), GAMES("Jeux"), LEARN("Apprendre"), QUIZ("Quiz"), DOWNLOADS("Téléchargements"), APPS("Applications"), BLOCKS("Blocages"), ALERTS("Alertes"), OTHER("Autres") }

    companion object { fun of(code: String?) = values().firstOrNull { it.code == code } }
}

enum class Severity(val code: String, val label: String) { INFO("info", "Information"), WARN("warn", "Attention"), CRITICAL("critical", "Important") }

/**
 * One thing that happened on a TV. [id] is stable across deliveries (dedupe key). [ts] is the TV's clock (ms). [durMin] null = the TV did
 * not time it (never shown as 0). [profileId] null = TV-wide (tampering, new app).
 */
data class ActivityEvent(
    val id: String, val tv: String, val ts: Long, val profileId: String?, val type: EventType, val title: String,
    val durMin: Int? = null, val score: String? = null, val detail: String? = null, val quality: Quality = Quality.MEASURED, val severity: Severity? = null,
) {
    val endTs: Long get() = ts + (durMin ?: 0) * 60_000L
}

data class AppMin(val pkg: String, val label: String, val min: Long)

/**
 * What one daily (or weekly) report says about one profile on one day of the TV. [kindsKnown] false = it comes from a weekly report that only
 * carries the total. [supervision] = state code of the whole-TV supervision when the report was made (null = the TV did not say).
 */
data class DayFact(
    val tv: String, val day: String, val profileId: String, val name: String,
    val play: Long, val games: Long, val downloads: Long, val apps: Long, val totalMin: Long, val kindsKnown: Boolean,
    val byApp: List<AppMin>, val limitMin: Int, val window: String?, val supervision: String?, val ts: Long, val source: String,
) {
    val measuredMin: Long get() = play + games + downloads
    /** Minutes the report counted but could not split by kind (a later weekly total bigger than the daily one, or a weekly-only day). */
    val unsplitMin: Long get() = (totalMin - measuredMin - apps).coerceAtLeast(0)
    /** Minutes of other apps: a number only when the supervision really measured them; null (not zero) otherwise. */
    val appsMin: Long? get() = if (kindsKnown && (supervision == SupervisionState.ACTIVE.code || apps > 0)) apps else null
    val appsQuality: Quality get() = if (appsMin != null) Quality.BEST_EFFORT else Quality.UNAVAILABLE
    val key: String get() = "$tv|$day|$profileId"
}

/** A state of the whole-TV supervision, as seen in a report. */
data class SupervisionObs(val tv: String, val ts: Long, val state: String)

/** « Surveillance de toute la TV : active / non autorisée / indisponible sur cette TV » — plain words, never optimistic. */
object SupervisionText {
    fun line(state: SupervisionState?, reachedTv: Boolean = true): String = when {
        !reachedTv || state == null -> "Surveillance de toute la TV : indisponible (aucun rapport reçu de la TV)"
        else -> when (state) {
            SupervisionState.ACTIVE -> "Surveillance de toute la TV : active"
            SupervisionState.NOT_AUTHORIZED -> "Surveillance de toute la TV : non autorisée"
            SupervisionState.UNAVAILABLE -> "Surveillance de toute la TV : indisponible sur cette TV"
            SupervisionState.OFF -> "Surveillance de toute la TV : désactivée (réglage du parent)"
        }
    }

    /** What it means for the figures of other apps. */
    fun consequence(state: SupervisionState?): String = when (state) {
        SupervisionState.ACTIVE -> "Les autres applications sont suivies au mieux (minutes estimées). CastBridge-TV est mesuré en entier."
        null -> "Aucune information : seules les données déjà reçues sont affichées."
        else -> "Les autres applications de la TV ne sont pas suivies : leurs minutes sont indisponibles. CastBridge-TV reste mesuré en entier."
    }

    fun quality(state: SupervisionState?) = if (state == SupervisionState.ACTIVE) Quality.BEST_EFFORT else Quality.UNAVAILABLE
}

/** French duration. */
object Fmt {
    fun min(m: Long): String = if (m >= 60) "${m / 60} h ${"%02d".format(m % 60)}" else "$m min"
    fun minOrNA(m: Long?): String = m?.let(::min) ?: "indisponible"
}
