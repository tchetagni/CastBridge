package castbridge.core.content

import castbridge.core.learn.Exercise
import castbridge.core.learn.Lesson
import castbridge.core.learn.Pack
import castbridge.core.net.JsonLite
import castbridge.core.quiz.Question
import castbridge.core.quiz.QuestionFilter
import castbridge.core.quiz.courseKey
import castbridge.core.telemetry.Telemetry
import castbridge.core.tv.ApiExtension
import castbridge.core.tv.ApiReply
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * What an app keeps for the content feedback (docs/CONTENT-VALIDATION.md): the local queue of reports (« Signaler une erreur »,
 * offline first) and the per item usage totals. [channel] and [telemetry] are read when needed (the server sets the channel, the
 * consent decides what telemetry keeps).
 *
 * Consent category: a report is something the user sends on purpose, with a short text: it belongs to the ESSENTIAL data and is
 * uploaded whatever the choice about the usage statistics; the usage totals ([stats]) are USAGE data (dropped without consent).
 */
class ContentFeedback(
    dir: File,
    val channel: () -> Channel = { Channel.STABLE },
    private val telemetry: () -> Telemetry? = { null },
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    val queue = ReportQueue(File(dir, "content-reports.jsonl"), clock = clock)
    val stats = ItemStatsCollector()

    /** Lot id of a quiz question: its course ("quiz/secondary/3e"), the same ids as the registry of the server. */
    fun lotOf(q: Question): String = "quiz/" + QuestionFilter(q.track, q.level, q.field).courseKey

    fun reportQuestion(q: Question, reason: ReportReason, note: String?): ReportQueue.Add =
        add(ContentReport.create(ContentKind.QUESTION, q.id, reason, note, ContentHash.question(q), lotOf(q), null, clock(), channel()), ContentKind.QUESTION, q.id)

    fun reportExercise(pack: Pack, x: Exercise, reason: ReportReason, note: String?): ReportQueue.Add =
        add(ContentReport.create(ContentKind.EXERCISE, x.id, reason, note, ContentHash.exercise(x), "learn/" + pack.id, pack.version, clock(), channel()), ContentKind.EXERCISE, x.id)

    fun reportLesson(pack: Pack, l: Lesson, reason: ReportReason, note: String?): ReportQueue.Add =
        add(ContentReport.create(ContentKind.LESSON, l.id, reason, note, null, "learn/" + pack.id, pack.version, clock(), channel()), ContentKind.LESSON, l.id)

    private fun add(r: ContentReport?, kind: ContentKind, id: String): ReportQueue.Add =
        queue.add(r).also { if (it == ReportQueue.Add.ACCEPTED) stats.recordReport(kind, id) }

    /** French sentence for the screen after [report…]. */
    fun message(r: ReportQueue.Add): String = when (r) {
        ReportQueue.Add.ACCEPTED -> "Merci ! Votre signalement sera envoyé dès que possible."
        ReportQueue.Add.DUPLICATE -> "Vous avez déjà signalé cela."
        ReportQueue.Add.RATE_LIMITED -> "Trop de signalements : réessayez plus tard."
        ReportQueue.Add.INVALID -> "Signalement impossible."
    }

    /** One display of an item: [correct] null if it is not answered. */
    fun shown(kind: ContentKind, id: String, correct: Boolean?, ms: Long) = stats.record(kind, id, correct, ms)

    /** Queues the totals as `content_stat` events (a no-op without the usage-statistics consent). Called with the telemetry flush. */
    fun flushStats(): Int = telemetry()?.let { stats.flush(it) } ?: 0

    companion object {
        /** Words shown as the list of reasons, in order. */
        val REASONS: List<ReportReason> = ReportReason.values().toList()
        const val HINT = "Ne mettez aucune donnée personnelle dans le texte."
    }
}

/**
 * TV side of the hand-off: the TV queues reports and the phone fetches them (PIN or token protected, like every /api route):
 * `GET /api/content/reports` lists the pending ones, `POST /api/content/reports/ack?ids=a,b` removes those the phone has stored.
 */
class ContentFeedbackApi(private val feedback: () -> ContentFeedback?) : ApiExtension {
    override fun handle(path: String, method: String, params: Map<String, String>): ApiReply? {
        if (path != "/api/content/reports" && path != "/api/content/reports/ack") return null
        val q = feedback()?.queue ?: return ApiReply(503, "{\"error\":\"signalements indisponibles\"}")
        return when {
            path == "/api/content/reports" && method == "GET" -> ApiReply(200, "{\"reports\":${q.handoff()}}")
            path == "/api/content/reports/ack" && method == "POST" -> {
                val ids = params["ids"].orEmpty().split(',').map { it.trim() }.filter { it.length in 8..40 }.take(ReportUploader.BATCH)
                q.remove(ids)
                ApiReply(200, "{\"removed\":${ids.size}}")
            }
            else -> ApiReply(405, "{\"error\":\"méthode non prise en charge\"}")
        }
    }
}

/** Phone side of the hand-off: fetches the TV's reports, stores them in the phone's queue, then tells the TV to forget them. */
class ReportHandoffClient(private val base: String, private val credential: String) {
    /** @return the number of reports now safely queued on the phone */
    fun pull(into: ReportQueue): Int {
        val list = call("GET", "/api/content/reports") ?: return 0
        val reports = (JsonLite.obj(list)["reports"] as? List<*>) ?: return 0
        if (reports.isEmpty()) return 0
        val acked = into.importHandoff(JsonLite.write(reports))
        if (acked.isNotEmpty()) call("POST", "/api/content/reports/ack?ids=" + acked.joinToString(","))
        return acked.size
    }

    private fun call(method: String, route: String): String? {
        val c = URL(base.trimEnd('/') + route).openConnection() as HttpURLConnection
        return try {
            c.requestMethod = method; c.connectTimeout = 4000; c.readTimeout = 10_000
            castbridge.core.trust.TvCredential.apply(c, credential)
            if (method == "POST") { c.doOutput = true; c.setFixedLengthStreamingMode(0) }
            if (c.responseCode in 200..299) c.inputStream.use { String(it.readBytes(), Charsets.UTF_8) } else null
        } catch (e: IOException) { null } finally { c.disconnect() }
    }
}
