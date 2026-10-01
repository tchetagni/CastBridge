package castbridge.core.parental.tab

/** How old the local copy is and how it was last refreshed: shown on every screen of the tab (offline-first, docs/PARENTAL.md). */
data class Freshness(val lastReceivedAt: Long, val ageMs: Long?, val level: Level) {
    enum class Level { NONE, FRESH, OLD, STALE }

    fun text(): String = when (level) {
        Level.NONE -> "Aucun rapport reçu de la TV pour l'instant."
        else -> "Dernière synchronisation : " + age(ageMs!!)
    }

    companion object {
        const val FRESH_MS = 6 * 3600_000L
        const val STALE_MS = 48 * 3600_000L

        fun of(lastReceivedAt: Long, now: Long): Freshness {
            if (lastReceivedAt <= 0) return Freshness(0, null, Level.NONE)
            val a = (now - lastReceivedAt).coerceAtLeast(0)
            return Freshness(lastReceivedAt, a, if (a <= FRESH_MS) Level.FRESH else if (a <= STALE_MS) Level.OLD else Level.STALE)
        }

        fun age(ms: Long): String = when {
            ms < 90_000 -> "à l'instant"
            ms < 3600_000 -> "il y a ${ms / 60_000} min"
            ms < 48 * 3600_000L -> "il y a ${ms / 3600_000} h"
            else -> "il y a ${ms / 86_400_000} jours"
        }
    }
}
