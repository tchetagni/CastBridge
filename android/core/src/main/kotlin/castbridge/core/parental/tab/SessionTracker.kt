package castbridge.core.parental.tab

/**
 * TV side: turns the usage meter's ticks (what is in front, for how long) into sessions of the [TvJournal]: « Vidéo X, 42 min », « Quiz, 15 min ».
 * First-hand measure, so MESURÉ. A session closes when the activity or its title changes, when nothing runs any more, or after [maxMs] (so that a
 * TV switched off in the middle loses at most that much); sessions under [minMs] are ignored (a screen glimpsed). Pure: the TV glue feeds it.
 */
class SessionTracker(private val journal: TvJournal, private val now: () -> Long = System::currentTimeMillis, val minMs: Long = 30_000, val maxMs: Long = 30 * 60_000L) {
    private var type: EventType? = null
    private var title = ""
    private var profile: String? = null
    private var start = 0L
    private var acc = 0L

    /** [type] null (or no profile) = nothing to measure now. [dtMs] = time since the previous tick, attributed to this activity. */
    @Synchronized fun tick(type: EventType?, title: String, profileId: String?, dtMs: Long) {
        if (type == null || profileId == null) { close(); return }
        if (this.type != type || this.title != title || this.profile != profileId) { close(); this.type = type; this.title = title; this.profile = profileId; start = now() - dtMs; acc = 0 }
        acc += dtMs
        if (acc >= maxMs) { close() }
    }

    @Synchronized fun close() {
        val t = type; val p = profile
        if (t != null && p != null && acc >= minMs) journal.record(t, p, title, ((acc + 30_000) / 60_000).toInt().coerceAtLeast(1), ts = start)
        type = null; title = ""; profile = null; acc = 0
    }
}
