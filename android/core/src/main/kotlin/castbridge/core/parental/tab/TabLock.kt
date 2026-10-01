package castbridge.core.parental.tab

/**
 * Session of the Parental tab: the PIN is asked once, then the tab stays open while the app is in use. It locks again when the app has been in the
 * background longer than [timeoutMs] (a few minutes), so a phone left on the table is not an open window on the child's activity. Holds NO PIN:
 * only whether the parent proved it, and when the app left the screen. Pure (the clock is given), so it is tested.
 */
class TabLock(private val timeoutMs: Long = DEFAULT_TIMEOUT_MS) {
    private var unlocked = false
    private var leftAt = 0L
    private var away = false

    @Synchronized fun unlock() { unlocked = true; away = false }
    @Synchronized fun lock() { unlocked = false; away = false }

    /** The app went to the background (or the screen turned off). */
    @Synchronized fun onBackground(now: Long) { if (unlocked && !away) { away = true; leftAt = now } }

    /** The app came back: locks if it stayed away too long. Returns whether the tab is open. */
    @Synchronized fun onForeground(now: Long): Boolean {
        if (unlocked && away && now - leftAt >= timeoutMs) unlocked = false
        away = false
        return unlocked
    }

    @Synchronized fun isOpen(now: Long): Boolean {
        if (unlocked && away && now - leftAt >= timeoutMs) unlocked = false
        return unlocked
    }

    companion object { const val DEFAULT_TIMEOUT_MS = 3 * 60_000L }
}

/** The alert history: alerts, blocked attempts and quota events, newest first, optionally of one severity. */
object AlertHistory {
    fun of(events: List<ActivityEvent>, minSeverity: Severity? = null): List<ActivityEvent> =
        events.filter { it.type == EventType.ALERT || it.type == EventType.BLOCK || it.type == EventType.QUOTA || it.type == EventType.UNLOCK }
            .filter { minSeverity == null || (it.severity ?: Severity.INFO).ordinal >= minSeverity.ordinal }.sortedByDescending { it.ts }

    /** The action the TV took, in words (shown under each alert). */
    fun action(e: ActivityEvent): String = e.detail ?: when (e.type) {
        EventType.BLOCK -> "Refusé par la TV."
        EventType.UNLOCK -> "Code parental demandé sur la TV."
        else -> "Alerte envoyée à ce téléphone."
    }
}
