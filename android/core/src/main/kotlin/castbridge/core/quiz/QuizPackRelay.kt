package castbridge.core.quiz

import castbridge.core.tv.ApiExtension
import castbridge.core.tv.ApiReply
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Base64

/** "general", "primary/CM2", "higher/L1/droit" -> the filter of that course (null when it is not one). */
fun filterOfCourse(key: String): QuestionFilter? {
    val p = key.split('/')
    val track = Track.of(p[0]) ?: return null
    if (p.size > 3) return null
    val f = QuestionFilter(track, p.getOrNull(1), p.getOrNull(2))
    return f.takeIf { it.courseKey == key }
}

/** What the TV tells the phone about its packs (GET /api/quiz/packs/status). */
data class TvPackStatus(val maxBytes: Long, val usedBytes: Long, val installed: List<Pair<String, Int>>, val needs: List<QuizPackManager.Need>, val thresholdGames: Int,
                       /** Quiz lots installed on the TV through the lots framework (scope + version + size), for « Mes thèmes ». */
                       val lots: List<castbridge.core.lots.LotMeta> = emptyList())

/**
 * TV side of the phone relay (PIN-protected, docs/QUIZ.md): the phone downloads the packs the TV needs (it has the
 * Internet) and pushes them. The TV checks them exactly as if it had downloaded them itself (size, SHA-256, server
 * signature, content): the phone is never trusted.
 * - GET  /api/quiz/packs/status          sizes, installed packs, courses running low
 * - POST /api/quiz/packs/push?info=…     info = base64url JSON of the signed catalog entry; body = the zip (≤ 4 MB)
 * - POST /api/quiz/packs/remove?id=      deletes an installed pack
 */
class QuizPackApi(
    private val store: QuizPackStore,
    private val manager: QuizPackManager,
    /** Courses to watch (the ones played here + general knowledge). */
    private val watched: () -> Collection<QuestionFilter>,
) : ApiExtension {
    /** Quiz lots installed here (set by the app once the lots framework is wired): reported by [status]. */
    var lots: QuizLotConsumer? = null

    override fun wantsBody(path: String) = path == "/api/quiz/packs/push"

    override fun handleBody(path: String, method: String, params: Map<String, String>, body: ByteArray): ApiReply? {
        if (path != "/api/quiz/packs/push" || method != "POST") return null
        val info = params["info"]?.let { runCatching { String(Base64.getUrlDecoder().decode(it), Charsets.UTF_8) }.getOrNull() }?.let { QuizPackInfo.parseJson(it) }
            ?: return ApiReply(400, err("info manquant ou illisible"))
        if (body.size.toLong() != info.size) return ApiReply(422, err("taille reçue ${body.size} au lieu de ${info.size} octets"))
        val tmp = File(store.dir, "downloads/${info.file}.push")
        return try {
            tmp.parentFile?.mkdirs(); tmp.writeBytes(body)
            when (val r = manager.installPushed(info, tmp)) {
                is QuizPackStore.Install.Ok -> ApiReply(200, Json.write(linkedMapOf("installed" to info.id, "version" to info.version, "evicted" to r.evicted)))
                is QuizPackStore.Install.Refused -> ApiReply(422, err(r.reason))
            }
        } catch (e: IOException) { ApiReply(500, err("écriture impossible : ${e.message}")) } finally { tmp.delete() }
    }

    override fun handle(path: String, method: String, params: Map<String, String>): ApiReply? {
        if (!path.startsWith("/api/quiz/packs")) return null
        return when {
            path == "/api/quiz/packs/status" && method == "GET" -> ApiReply(200, status())
            path == "/api/quiz/packs/remove" && method == "POST" -> params["id"]?.let { store.remove(it); ApiReply(200, status()) } ?: ApiReply(400, err("id manquant"))
            path == "/api/quiz/packs/push" -> ApiReply(405, err("POST avec un corps attendu"))
            else -> ApiReply(404, err("route inconnue"))
        }
    }

    fun status(): String {
        val inst = store.installed()
        return Json.write(linkedMapOf("maxBytes" to store.maxBytes, "usedBytes" to inst.sumOf { it.file.length() }, "thresholdGames" to manager.thresholdGames,
            "installed" to inst.map { linkedMapOf("id" to it.info.id, "course" to it.info.course, "version" to it.info.version, "part" to it.info.part, "questions" to it.info.questions, "size" to it.file.length()) },
            "needs" to manager.needs(watched()).map { linkedMapOf("course" to it.course, "freshGames" to it.freshGames) },
            "lots" to lots?.installed().orEmpty().map { linkedMapOf("scope" to it.id.scope, "version" to it.version, "bytes" to it.bytes, "sha256" to it.sha256, "title" to it.title) }))
    }

    private fun err(m: String) = "{\"error\":${Json.quote(m)}}"
}

/** The phone's view of a TV (HTTP with the PIN in tests and in the app; fakes elsewhere). */
interface TvPackEndpoint {
    fun status(): TvPackStatus?
    /** null = installed, else why not (in French). */
    fun push(info: QuizPackInfo, file: File): String?
}

class HttpTvPackEndpoint(private val base: String, private val pin: String?) : TvPackEndpoint {
    private fun open(method: String, path: String) = (URL(base.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
        requestMethod = method; connectTimeout = 8_000; readTimeout = 30_000
        castbridge.core.trust.TvCredential.apply(this, pin)
    }

    override fun status(): TvPackStatus? = try {
        val c = open("GET", "/api/quiz/packs/status")
        if (c.responseCode != 200) { runCatching { c.errorStream?.close() }; null } else {
            val m = Json.obj(c.inputStream.use { String(it.readBytes(), Charsets.UTF_8) })
            @Suppress("UNCHECKED_CAST")
            TvPackStatus((m["maxBytes"] as? Number)?.toLong() ?: QUIZ_PACK_MAX_BYTES, (m["usedBytes"] as? Number)?.toLong() ?: 0,
                (m["installed"] as? List<Map<String, Any?>>).orEmpty().map { (it["id"] as? String).orEmpty() to ((it["version"] as? Number)?.toInt() ?: 0) },
                (m["needs"] as? List<Map<String, Any?>>).orEmpty().map { QuizPackManager.Need((it["course"] as? String).orEmpty(), (it["freshGames"] as? Number)?.toInt() ?: 0) },
                (m["thresholdGames"] as? Number)?.toInt() ?: QUIZ_PACK_THRESHOLD_GAMES,
                (m["lots"] as? List<Map<String, Any?>>).orEmpty().mapNotNull { x ->
                    castbridge.core.lots.LotMeta(castbridge.core.lots.LotId(QuizLotScopes.FEATURE, x["scope"] as? String ?: return@mapNotNull null), (x["version"] as? Number)?.toInt() ?: return@mapNotNull null,
                        (x["bytes"] as? Number)?.toLong() ?: 0L, (x["sha256"] as? String).orEmpty(), (x["title"] as? String).orEmpty())
                })
        }
    } catch (e: Exception) { null }

    override fun push(info: QuizPackInfo, file: File): String? = try {
        val infoParam = URLEncoder.encode(Base64.getUrlEncoder().withoutPadding().encodeToString(Json.write(info.toMap()).toByteArray(Charsets.UTF_8)), "UTF-8")
        val c = open("POST", "/api/quiz/packs/push?info=$infoParam")
        val bytes = file.readBytes()
        c.doOutput = true; c.setFixedLengthStreamingMode(bytes.size); c.outputStream.use { it.write(bytes) }
        if (c.responseCode == 200) { c.inputStream.close(); null }
        else runCatching { (Json.obj(c.errorStream.use { String(it.readBytes(), Charsets.UTF_8) })["error"] as? String) ?: "HTTP ${c.responseCode}" }.getOrElse { "HTTP ${c.responseCode}" }
    } catch (e: IOException) { "TV injoignable : ${e.message}" }
}

/**
 * Phone side of the relay: when the TV has no Internet route of its own, the phone fetches the packs the TV needs from
 * the server and pushes them (Wi-Fi or Bluetooth link). Resumable: a cut download continues from the `.part` file.
 */
class QuizPackRelay(private val server: QuizPackSource, private val tv: TvPackEndpoint, private val workDir: File, private val attempts: Int = 3,
                    private val sleep: (Long) -> Unit = { Thread.sleep(it) }) {
    data class Result(val pushed: List<String>, val message: String)

    fun sync(maxPacks: Int = 4): Result {
        val status = tv.status() ?: return Result(emptyList(), "TV injoignable")
        if (status.needs.isEmpty()) return Result(emptyList(), "La TV a assez de questions (au moins ${status.thresholdGames} parties sans répétition)")
        val catalog = server.catalog() ?: return Result(emptyList(), "${server.name} injoignable")
        val installed = status.installed.toMutableList()
        val pushed = ArrayList<String>()
        var why = "Rien à envoyer"
        for (need in status.needs.sortedBy { it.freshGames }) {
            if (pushed.size >= maxPacks) break
            val next = catalog.filter { it.course == need.course && installed.none { (id, v) -> id == it.id && v >= it.version } }.minByOrNull { it.part } ?: continue
            val part = File(workDir, "${next.file}.part")
            var ok = false
            for (a in 1..attempts) {
                try { ok = server.download(next, part); if (ok) break } catch (e: IOException) { why = "téléchargement coupé : ${e.message}"; if (a < attempts) sleep(minOf(30_000L, 1_000L shl (a - 1))) }
            }
            if (!ok) continue
            val err = tv.push(next, part)
            part.delete()
            if (err == null) { pushed += next.id; installed += next.id to next.version } else why = "${next.id} refusé par la TV : $err"
        }
        return Result(pushed, if (pushed.isEmpty()) why else "${pushed.size} lot(s) envoyé(s) à la TV : ${pushed.joinToString()}")
    }
}
