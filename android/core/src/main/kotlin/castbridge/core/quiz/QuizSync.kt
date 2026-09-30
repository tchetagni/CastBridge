package castbridge.core.quiz

import castbridge.core.device.DeviceClient
import castbridge.core.net.HttpLite
import castbridge.core.net.JsonLite
import java.io.File
import java.io.IOException

/**
 * Keeps the TV's question cache in step with the server (GET /api/v1/quiz/questions, docs/API-SERVER.md § 2):
 * incremental (`since` = syncToken of the previous run), ETag / 304 when nothing changed, all pages, deletions applied,
 * `resetRequired` = start again from scratch. The result is written through [CachedQuestionSource.update] (size limit,
 * validation, atomic write), so a failed or partial sync never breaks the quiz: the previous cache (or the bundled bank)
 * stays in use.
 *
 * Cache file = the exchange format plus the sync state:
 * `{"version":2,"syncToken":"…","etag":"…","etagSince":"…","deleted":["id",…],"questions":[…]}`.
 */
class QuizSync(private val cache: CachedQuestionSource, private val cacheFile: File, private val pageSize: Int = 500) {
    sealed class Result {
        data class Updated(val changed: Int, val deleted: Int, val total: Int) : Result()
        object NotModified : Result()
        data class Failed(val reason: String) : Result()
    }

    /**
     * @param http client on the route to use (direct or through the Bluetooth gateway)
     * @param reset forget the cache first (the server changed)
     * @throws IOException when the server cannot be reached (the caller may try another route)
     * @throws DeviceClient.ServerError on an HTTP error answer (403 = device blocked)
     */
    @Throws(IOException::class)
    fun sync(baseUrl: String, http: HttpLite, deviceToken: String?, deviceId: String?, reset: Boolean = false): Result {
        val base = baseUrl.trimEnd('/')
        val old = if (reset) null else readCache()
        var since = old?.get("syncToken") as? String
        // the ETag belongs to one query: it is only sent again for the same "since" (nothing changed since the last run)
        val etag = (old?.get("etag") as? String)?.takeIf { old["etagSince"] == since }
        val questions = LinkedHashMap<String, Map<String, Any?>>()
        val deleted = LinkedHashSet<String>()
        if (old != null) {
            (old["questions"] as? List<*>).orEmpty().forEach { q -> (q as? Map<*, *>)?.let { m -> (m["id"] as? String)?.let { @Suppress("UNCHECKED_CAST") questions[it] = m as Map<String, Any?> } } }
            (old["deleted"] as? List<*>).orEmpty().filterIsInstance<String>().forEach { deleted += it }
        }
        val headers = LinkedHashMap<String, String>()
        deviceToken?.let { headers["Authorization"] = "Bearer $it" }

        var page = 0
        var pages = 1
        var syncToken: String? = null
        var newEtag: String? = null
        var changed = 0
        var removed = 0
        var restarted = false
        val askedSince = since
        while (page < pages) {
            val url = "$base/api/v1/quiz/questions?" + HttpLite.query("since" to since, "page" to page, "size" to pageSize, "deviceId" to deviceId)
            val h = if (page == 0 && since != null && etag != null && !restarted) headers + ("If-None-Match" to etag) else headers
            val r = http.request("GET", url, headers = h)
            if (r.code == 304 && page == 0) return Result.NotModified
            if (r.code != 200) throw DeviceClient.ServerError(r.code, HttpLite.errorMessage(r))
            val m = try { JsonLite.obj(r.body) } catch (e: IllegalArgumentException) { return Result.Failed("réponse du serveur illisible") }
            if (page == 0) {
                if (m["resetRequired"] == true && !restarted) {
                    // too long without news: the server no longer knows what was deleted meanwhile, start from scratch
                    questions.clear(); deleted.clear(); since = null; restarted = true; continue
                }
                syncToken = m["syncToken"] as? String
                newEtag = r.header("ETag")
                pages = (m["totalPages"] as? Number)?.toInt()?.coerceIn(1, 1000) ?: 1
                for (id in (m["deleted"] as? List<*>).orEmpty().filterIsInstance<String>()) {
                    if (questions.remove(id) != null) removed++
                    deleted += id
                }
            }
            for (q in (m["questions"] as? List<*>).orEmpty()) {
                @Suppress("UNCHECKED_CAST")
                val qm = q as? Map<String, Any?> ?: continue
                val id = qm["id"] as? String ?: continue
                if (!valid(qm)) continue                          // one bad question never blocks the others
                questions[id] = qm; deleted -= id; changed++
            }
            page++
        }
        // same text twice (the server keys on lang/track too): keep the last one received
        val byText = LinkedHashMap<String, String>()
        for ((id, q) in questions) {
            val key = listOf(q["question"], q["level"], q["field"]).joinToString("|") { it.toString().trim().lowercase().replace(Regex("\\s+"), " ") }
            byText.put(key, id)?.let { questions.remove(it) }
        }
        val json = JsonLite.write(linkedMapOf("version" to QuizBank.FORMAT_VERSION, "syncToken" to syncToken, "etag" to newEtag,
            "etagSince" to (if (restarted) null else askedSince), "deleted" to deleted.toList().takeLast(5000), "questions" to questions.values.toList()))
        cache.update(json)?.let { return Result.Failed(it) }
        return Result.Updated(changed, removed, questions.size)
    }

    private fun readCache(): Map<String, Any?>? = runCatching {
        if (cacheFile.isFile) JsonLite.obj(cacheFile.readText(Charsets.UTF_8)) else null
    }.getOrNull()

    private fun valid(q: Map<String, Any?>): Boolean = runCatching {
        val bank = QuizBank.parse(JsonLite.write(mapOf("version" to QuizBank.FORMAT_VERSION, "questions" to listOf(q))))
        bank.validate().isEmpty()
    }.getOrDefault(false)
}
