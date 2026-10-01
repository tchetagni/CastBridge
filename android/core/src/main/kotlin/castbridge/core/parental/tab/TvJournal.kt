package castbridge.core.parental.tab

import castbridge.core.learn.LearnState
import castbridge.core.net.JsonLite
import castbridge.core.parental.KvStore

/**
 * TV side: the journal of what CastBridge-TV itself did (videos played with title and duration, games, Apprendre, Quiz, downloads, remote use,
 * unlock attempts, sessions). It is MEASURED because the TV records it first-hand. Bounded (count and age), local, one KvStore key.
 * The daily report carries the last hours of it in the additive field "events" ([forReport]); the phone deduplicates by id.
 */
class TvJournal(private val store: KvStore, private val now: () -> Long = System::currentTimeMillis, val max: Int = 400, val maxAgeMs: Long = 8 * 86_400_000L) {
    /** Records one event. [durMin] null = not timed. Titles are cut; nothing secret (PIN, token, address) may be passed here. */
    @Synchronized fun record(type: EventType, profileId: String?, title: String, durMin: Int? = null, score: String? = null, detail: String? = null, ts: Long = now()): String {
        val seq = (store.get("jseq")?.toLongOrNull() ?: 0L) + 1
        store.put("jseq", seq.toString())
        val id = java.lang.Long.toHexString(ts) + "-" + seq
        val l = load() + linkedMapOf("id" to id, "ts" to ts, "p" to profileId, "t" to type.code, "title" to title.take(120), "min" to durMin, "score" to score?.take(30), "detail" to detail?.take(160))
        save(bound(l))
        return id
    }

    @Synchronized fun all(): List<Map<String, Any?>> = bound(load())

    /** Events of [profileId] (and the TV-wide ones) since [sinceTs], newest last, at most [limit]. */
    @Synchronized fun forReport(profileId: String, sinceTs: Long, limit: Int = 80): List<Map<String, Any?>> =
        bound(load()).filter { (it["ts"] as? Number)?.toLong() ?: 0L >= sinceTs && (it["p"] == profileId || it["p"] == null) }.takeLast(limit)
            .map { linkedMapOf("id" to it["id"], "ts" to it["ts"], "t" to it["t"], "title" to it["title"], "min" to it["min"], "score" to it["score"], "detail" to it["detail"]) }

    @Synchronized fun clear() = store.put("journal", null)

    private fun bound(l: List<Map<String, Any?>>): List<Map<String, Any?>> {
        val t = now()
        return l.filter { t - ((it["ts"] as? Number)?.toLong() ?: 0L) <= maxAgeMs }.takeLast(max)
    }

    @Suppress("UNCHECKED_CAST")
    private fun load(): List<Map<String, Any?>> = store.get("journal")?.let { runCatching { JsonLite.parse(it) as List<Map<String, Any?>> }.getOrNull() } ?: emptyList()
    private fun save(l: List<Map<String, Any?>>) = store.put("journal", JsonLite.write(l))

    companion object {
        /**
         * Events of « Apprendre » of the TV's existing progress (no new collection): lessons completed (with their time), mock exams (with score).
         * Single exercises are summed per hour so that a session of 40 exercises is one line, not forty.
         */
        fun fromLearn(state: LearnState, learnId: String, parentalId: String, sinceTs: Long): List<Map<String, Any?>> {
            val out = ArrayList<Map<String, Any?>>()
            val exo = LinkedHashMap<Long, IntArray>()                       // hour bucket -> [tried, correct]
            for (e in state.events) {
                if (e.profile != learnId || e.at < sinceTs) continue
                when (e.name) {
                    "lesson_complete" -> out += linkedMapOf("id" to "L${e.at}-${e.data["lesson"]}", "ts" to e.at, "t" to "learn", "title" to "Fiche terminée : ${e.data["lesson"]}",
                        "min" to (((e.data["timeMs"] as? Number)?.toLong() ?: 0L) / 60_000).toInt(), "score" to null, "detail" to e.data["pack"]?.toString())
                    "mock_exam_result" -> out += linkedMapOf("id" to "M${e.at}-${e.data["mock"]}", "ts" to e.at, "t" to "learn", "title" to "Épreuve blanche : ${e.data["mock"]}",
                        "min" to (((e.data["durationMs"] as? Number)?.toLong() ?: 0L) / 60_000).toInt(), "score" to "${e.data["score"]}/${e.data["outOf"]}", "detail" to e.data["pack"]?.toString())
                    "exercise_result", "review_result" -> exo.getOrPut(e.at / 3_600_000L) { IntArray(2) }.also { it[0]++; if (e.data["correct"] == true) it[1]++ }
                }
            }
            for ((h, v) in exo) out += linkedMapOf("id" to "X$h-$learnId", "ts" to h * 3_600_000L, "t" to "learn", "title" to "Exercices", "min" to null, "score" to "${v[1]}/${v[0]}", "detail" to "réussis / tentés")
            return out.sortedBy { (it["ts"] as Number).toLong() }
        }
    }
}
