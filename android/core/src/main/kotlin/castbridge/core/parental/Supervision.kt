package castbridge.core.parental

/**
 * The real state of the supervision of the whole TV, as the parent must see it. Never « active » unless a detector really works:
 * a parent who believes the TV is protected when it is not is worse than no protection (docs/PARENTAL.md).
 */
enum class SupervisionState(val code: String, val label: String) {
    OFF("off", "Surveillance de toute la TV : désactivée"),
    ACTIVE("active", "Surveillance de toute la TV : active"),
    NOT_AUTHORIZED("unauthorized", "Surveillance de toute la TV : non autorisée"),
    UNAVAILABLE("unavailable", "Surveillance de toute la TV : indisponible");

    val working get() = this == ACTIVE

    companion object {
        fun of(code: String?) = values().firstOrNull { it.code == code }

        /**
         * [supervise]: the parent asked for it. [usageGranted]: the special access « Statistiques d'utilisation » is given. [usageExists]: the TV has
         * UsageStatsManager at all. [accessibilityOn]: the CastBridge accessibility service is connected (stronger and faster signal).
         * [pollerFresh]: the TV-side loop ran recently (a killed service must not leave a stale « active »).
         * [canEnforce]: the TV may bring the lock screen in front of another app (« Afficher par-dessus les autres apps », or the accessibility
         * service): seeing a blocked app without being able to stop it is not protection.
         */
        fun compute(supervise: Boolean, usageGranted: Boolean, usageExists: Boolean, accessibilityOn: Boolean, pollerFresh: Boolean = true, canEnforce: Boolean = true): SupervisionState = when {
            !supervise -> OFF
            !pollerFresh -> NOT_AUTHORIZED
            (usageGranted || accessibilityOn) && !canEnforce -> NOT_AUTHORIZED
            usageGranted || accessibilityOn -> ACTIVE
            !usageExists -> UNAVAILABLE
            else -> NOT_AUTHORIZED
        }
    }
}

/** Live state for the screens and the API. [source]: "usage", "accessibility" or "none". */
data class SupervisionInfo(val state: SupervisionState, val source: String = "none", val since: Long = 0, val detail: String? = null) {
    fun toMap(): Map<String, Any?> = linkedMapOf("state" to state.code, "label" to state.label, "source" to source, "since" to since, "detail" to detail)
}

/** One entry of the foreground log of UsageStatsManager (ACTIVITY_RESUMED / ACTIVITY_PAUSED), reduced to what matters. */
data class FgEvent(val pkg: String, val resumed: Boolean, val ts: Long)

/**
 * Which package is in front, from the stream of foreground events. Android may deliver « B resumed » before « A paused » for the same
 * second: a pause only clears the package it belongs to. Pure and small: it is the heart of the detector and is tested.
 */
class ForegroundTracker {
    @Volatile var current: String? = null; private set
    private var lastTs = 0L

    /** Feeds events (any order: they are sorted by time). Returns the package in front afterwards. */
    fun feed(events: List<FgEvent>): String? {
        for (e in events.sortedBy { it.ts }) {
            if (e.ts < lastTs) continue
            lastTs = e.ts
            if (e.resumed) current = e.pkg else if (current == e.pkg) current = null
        }
        return current
    }

    /** The screen went off or the detector was paused: forget (the next poll rebuilds the state from recent events). */
    fun reset() { current = null; lastTs = 0 }
}

/** Something a parent wants to hear about (sent to the designated phones by [ParentalReports], never to a server). */
sealed class ParentalEvent {
    abstract val profileId: String?
    /** The daily time or the allowed hours are over ([code] is "limit" or "window"). */
    data class LimitReached(override val profileId: String?, val code: String, val text: String) : ParentalEvent()
    data class AppBlocked(override val profileId: String?, val pkg: String, val label: String, val text: String) : ParentalEvent()
    /** The supervision was weakened (usage access revoked, accessibility service disabled...). */
    data class Tamper(val text: String) : ParentalEvent() { override val profileId: String? get() = null }
    data class NewAppInstalled(val pkg: String, val label: String) : ParentalEvent() { override val profileId: String? get() = null }
}
