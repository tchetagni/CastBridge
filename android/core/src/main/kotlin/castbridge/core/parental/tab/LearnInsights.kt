package castbridge.core.parental.tab

import castbridge.core.learn.LearnProgress

data class SubjectView(val subject: String, val label: String, val timeMin: Long, val lessons: Int, val successPct: Int?)
data class MockView(val subject: String, val score: Double, val at: Long)

/** Progress of one student of « Apprendre » as the parent sees it. Everything comes from the TV's own counters (MESURÉ). */
data class LearnView(
    val profileId: String, val level: String?, val lessonsCompleted: Int, val stars: Int, val streak: Int, val totalMin: Long,
    val subjects: List<SubjectView>, val weak: List<String>, val mocks: List<MockView>, val badges: List<String>, val reviewsDue: Int,
)

/**
 * TV side: what the daily report carries about « Apprendre » (additive field "learn" of the report; older phones ignore it). Built from the
 * existing progress of the TV ([LearnProgress.dashboard]): no new collection. Weak topics = subjects below 60 % success over at least 3 attempts.
 */
object LearnDigest {
    const val WEAK_BELOW_PCT = 60
    const val WEAK_MIN_ATTEMPTS = 3

    @Suppress("UNCHECKED_CAST")
    fun build(lp: LearnProgress, learnId: String, now: Long): Map<String, Any?>? {
        if (lp.profile(learnId) == null) return null
        val d = lp.dashboard(learnId, now)
        val subjects = (d["subjects"] as? List<Map<String, Any?>>).orEmpty()
        val weak = subjects.filter { s -> (s["attempts"] as? Number)?.toInt() ?: 0 >= WEAK_MIN_ATTEMPTS && ((s["successRate"] as? Number)?.toInt() ?: 100) < WEAK_BELOW_PCT }.map { it["label"] as? String ?: it["subject"] as String }
        return linkedMapOf(
            "level" to ((d["profile"] as? Map<String, Any?>)?.get("level")), "lessons" to d["lessonsCompleted"], "stars" to d["stars"], "streak" to d["streak"],
            "timeMs" to d["timeMs"], "reviewsDue" to d["reviewsDue"],
            "subjects" to subjects.map { linkedMapOf("s" to it["subject"], "label" to it["label"], "timeMs" to it["timeMs"], "lessons" to it["lessonsCompleted"], "rate" to it["successRate"]) },
            "weak" to weak,
            "mocks" to (d["mocks"] as? List<Map<String, Any?>>).orEmpty().takeLast(5).map { linkedMapOf("s" to it["subject"], "score" to it["score"], "at" to it["at"]) },
            "badges" to (d["badges"] as? List<Map<String, Any?>>).orEmpty().mapNotNull { it["label"] as? String },
        )
    }

    /** Phone side. */
    @Suppress("UNCHECKED_CAST")
    fun view(profileId: String, m: Map<String, Any?>): LearnView = LearnView(
        profileId, m["level"] as? String, (m["lessons"] as? Number)?.toInt() ?: 0, (m["stars"] as? Number)?.toInt() ?: 0, (m["streak"] as? Number)?.toInt() ?: 0,
        ((m["timeMs"] as? Number)?.toLong() ?: 0L) / 60_000,
        (m["subjects"] as? List<Map<String, Any?>>).orEmpty().map { SubjectView(it["s"] as? String ?: "", it["label"] as? String ?: (it["s"] as? String ?: ""), ((it["timeMs"] as? Number)?.toLong() ?: 0L) / 60_000, (it["lessons"] as? Number)?.toInt() ?: 0, (it["rate"] as? Number)?.toInt()) },
        (m["weak"] as? List<Any?>).orEmpty().mapNotNull { it as? String },
        (m["mocks"] as? List<Map<String, Any?>>).orEmpty().map { MockView(it["s"] as? String ?: "", (it["score"] as? Number)?.toDouble() ?: 0.0, (it["at"] as? Number)?.toLong() ?: 0L) },
        (m["badges"] as? List<Any?>).orEmpty().mapNotNull { it as? String }, (m["reviewsDue"] as? Number)?.toInt() ?: 0)
}

/** Quiz games and Apprendre exercises of a period, from the timed events of the journal. */
data class QuizStats(val games: Int, val results: List<ActivityEvent>, val bestScore: String?, val learnSessions: Int, val learnMin: Long)

object LearnQuizStats {
    fun of(events: List<ActivityEvent>): QuizStats {
        val q = events.filter { it.type == EventType.QUIZ }.sortedByDescending { it.ts }
        val l = events.filter { it.type == EventType.LEARN }
        return QuizStats(q.size, q, q.mapNotNull { e -> e.score?.let { s -> parseRatio(s)?.let { r -> r to s } } }.maxByOrNull { it.first }?.second, l.size, l.sumOf { (it.durMin ?: 0).toLong() })
    }

    /** "12/15" -> 0.8 */
    fun parseRatio(s: String): Double? { val p = s.split('/'); if (p.size != 2) return null; val a = p[0].trim().toDoubleOrNull() ?: return null; val b = p[1].trim().toDoubleOrNull() ?: return null; return if (b > 0) a / b else null }
}
