package castbridge.core.library.agent

/** What the last analysis found, for the home screen of the assistant and for the proactive check. */
data class LastAnalysis(val origin: Origin, val at: Long, val files: Int, val toRename: Int, val duplicates: Int, val duplicateBytes: Long) {
    fun encode() = listOf(origin.name, at, files, toRename, duplicates, duplicateBytes).joinToString(",")
    companion object {
        fun parse(s: String?): LastAnalysis? {
            val p = s?.split(',') ?: return null
            if (p.size != 6) return null
            return runCatching { LastAnalysis(Origin.valueOf(p[0]), p[1].toLong(), p[2].toInt(), p[3].toInt(), p[4].toInt(), p[5].toLong()) }.getOrNull()
        }
        fun of(a: Analysis, now: Long) = LastAnalysis(a.snapshot.origin, now, a.stats.files, a.stats.toRename, a.stats.duplicateGroups, a.stats.duplicateBytes)
    }
    /** « il y a 2 h », « hier », « il y a 3 jours ». */
    fun ago(now: Long): String {
        val min = ((now - at) / 60_000).coerceAtLeast(0)
        return when { min < 2 -> "à l'instant"; min < 60 -> "il y a $min min"; min < 24 * 60 -> "il y a ${min / 60} h"; min < 48 * 60 -> "hier"; else -> "il y a ${min / 1440} jours" }
    }
}

/**
 * When may the assistant speak up on its own? Only if the user switched the notification on, at most once a week, only when there is something
 * worth the interruption, and never twice for the same finding. The decision is pure so that "never nags" is a tested property.
 */
object ProactivePolicy {
    const val MIN_GAP_MS = 7L * 86_400_000L
    /** Fewer than that many files to rename is not worth a notification. */
    const val MIN_RENAMES = 10

    class Message(val title: String, val text: String, val signature: String)

    fun message(insights: List<Insight>, hiddenUntil: Map<String, Long>, now: Long): Message? {
        val visible = InsightFilter.visible(insights, hiddenUntil, now)
        val warn = visible.firstOrNull { it.severity == Insight.WARNING }
        if (warn != null) return Message("Place presque épuisée", warn.text + ". Voulez-vous voir ce qu'on peut ranger ?", warn.id)
        val names = visible.firstOrNull { it.id == "names" }
        val n = names?.text?.substringBefore(' ')?.toIntOrNull() ?: 0
        val dups = visible.firstOrNull { it.id == "dups" }
        if (n >= MIN_RENAMES) return Message("Quelques fichiers à ranger", names!!.text + (dups?.let { " · " + it.text } ?: "") + ". Rien ne change sans votre accord.", "names:$n")
        if (dups != null && dups.bytes >= (1L shl 30)) return Message("De la place à récupérer", dups.text + ". Rien ne change sans votre accord.", "dups:${dups.bytes shr 30}")
        return null
    }

    /** [message] only if the option is on and the last notification is old enough; otherwise null. Never when a child profile is active. */
    fun decide(enabled: Boolean, childProfile: Boolean, lastNotifiedAt: Long, lastSignature: String?, insights: List<Insight>, hiddenUntil: Map<String, Long>, now: Long): Message? {
        if (!enabled || childProfile || now - lastNotifiedAt < MIN_GAP_MS) return null
        val m = message(insights, hiddenUntil, now) ?: return null
        return if (m.signature == lastSignature) null else m
    }
}
