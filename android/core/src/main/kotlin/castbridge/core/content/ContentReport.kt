package castbridge.core.content

import castbridge.core.net.HttpLite
import castbridge.core.net.JsonLite
import castbridge.core.net.JsonLite.int
import castbridge.core.net.JsonLite.long
import castbridge.core.net.JsonLite.str
import java.io.File
import java.io.IOException
import java.net.Proxy
import java.util.UUID

/** Why a tester reports an item (« Signaler une erreur »). */
enum class ReportReason(val key: String, val label: String) {
    WRONG_ANSWER("wrong_answer", "Réponse fausse"), AMBIGUOUS("ambiguous", "Question ambiguë"), LANGUAGE("language", "Faute de langue"),
    OUT_OF_SCOPE("out_of_scope", "Hors programme"), DIFFICULTY("difficulty", "Trop facile ou trop difficile"), OTHER("other", "Autre");
    companion object { fun of(key: String?) = values().firstOrNull { it.key == key } }
}

/**
 * A report about one item: ids, a reason and a short text, nothing else — no name, no device id (the server knows the device
 * from the token that uploads it). Always built with [create], which cleans and bounds the free text.
 */
data class ContentReport(
    val id: String, val kind: ContentKind, val itemId: String, val reason: ReportReason, val note: String,
    /** [ContentHash] of the item as shown (questions, exercises); null for lessons. */
    val hash: String?, /** Lot / pack id and its version, to find the item again after an update. */ val lot: String?, val lotVersion: Int?,
    val createdAt: Long, val channel: Channel,
) {
    /** Reports of the same thing on the same content count once per device. */
    val dedupeKey: String get() = "${kind.key}|$itemId|${reason.key}|${hash.orEmpty()}"

    fun toMap(): Map<String, Any?> = linkedMapOf("id" to id, "kind" to kind.key, "item" to itemId, "reason" to reason.key,
        "note" to note.ifEmpty { null }, "hash" to hash, "lot" to lot, "lotVersion" to lotVersion, "at" to createdAt, "channel" to channel.key)

    fun toJson(): String = JsonLite.write(toMap())

    companion object {
        const val MAX_NOTE = 200

        /** No control character, one space between words, at most [MAX_NOTE] characters. */
        fun cleanNote(s: String?): String =
            (s ?: "").filterNot { it.isISOControl() && it != '\n' && it != '\t' }.replace(Regex("\\s+"), " ").trim().take(MAX_NOTE).trim()

        /** @return null when the item id or the hash is not acceptable */
        fun create(kind: ContentKind, itemId: String, reason: ReportReason, note: String?, hash: String?, lot: String?, lotVersion: Int?,
                   now: Long, channel: Channel, id: String = UUID.randomUUID().toString()): ContentReport? {
            if (!ValidationRecord.ID.matches(itemId)) return null
            if (hash != null && !Regex("[0-9a-f]{16}").matches(hash)) return null
            val lotOk = lot?.takeIf { ValidationRecord.LOT.matches(it) && !it.contains("..") }
            return ContentReport(id, kind, itemId, reason, cleanNote(note), hash, lotOk, lotVersion?.takeIf { it in 0..1_000_000 }, now, channel)
        }

        fun parse(m: Map<String, Any?>): ContentReport? {
            val kind = ContentKind.of(m.str("kind")) ?: return null
            val item = m.str("item") ?: return null
            val reason = ReportReason.of(m.str("reason")) ?: return null
            val at = m.long("at") ?: return null
            val id = m.str("id")?.takeIf { it.length in 8..40 && it.all { c -> c.isLetterOrDigit() || c == '-' } } ?: return null
            return create(kind, item, reason, m.str("note"), m.str("hash"), m.str("lot"), m.int("lotVersion"), at, Channel.of(m.str("channel")), id)
        }
    }
}

/**
 * Local, offline-first queue of reports (one JSON per line in [file]). Bounded ([maxReports], [maxBytes]; the oldest go first),
 * de-duplicated ([ContentReport.dedupeKey]) and rate limited ([maxPerHour], [minGapMs]). The TV queues and hands its reports to the
 * phone ([handoff] / [importHandoff]); the phone uploads them ([ReportUploader]) when it has Internet. A report leaves the queue
 * only when the server (or the phone, for the TV) acknowledged it.
 */
class ReportQueue(
    private val file: File, private val maxReports: Int = 100, private val maxBytes: Long = 64L shl 10,
    private val maxPerHour: Int = 20, private val minGapMs: Long = 2_000, private val clock: () -> Long = { System.currentTimeMillis() },
) {
    enum class Add { ACCEPTED, DUPLICATE, RATE_LIMITED, INVALID }

    private val recent = ArrayDeque<Long>()   // when the last reports were accepted (also those already uploaded)

    @Synchronized
    fun add(r: ContentReport?): Add {
        if (r == null) return Add.INVALID
        val now = clock()
        val all = read()
        if (all.any { it.dedupeKey == r.dedupeKey || it.id == r.id }) return Add.DUPLICATE
        while (recent.isNotEmpty() && now - recent.first() > HOUR) recent.removeFirst()
        if (recent.size >= maxPerHour || (recent.isNotEmpty() && now - recent.last() < minGapMs)) return Add.RATE_LIMITED
        recent.addLast(now)
        var list = all + r
        while (list.size > maxReports || size(list) > maxBytes) list = list.drop(1)
        write(list)
        return Add.ACCEPTED
    }

    @Synchronized fun pending(max: Int = 50): List<ContentReport> = read().take(max)
    @Synchronized fun count(): Int = read().size

    /** Removes the reports the server (or the phone) acknowledged. */
    @Synchronized
    fun remove(ids: Collection<String>) { if (ids.isEmpty()) return; val s = ids.toSet(); val l = read(); val k = l.filterNot { it.id in s }; if (k.size != l.size) write(k) }

    /** What the TV gives the phone: a JSON array of at most [max] reports. */
    @Synchronized fun handoff(max: Int = 50): String = JsonLite.write(pending(max).map { it.toMap() })

    /**
     * The phone receives reports of a TV: invalid ones are dropped, duplicates ignored, the batch is bounded, and the rate limit
     * does not apply (the reports were already limited on the TV).
     * @return the ids to acknowledge to the TV: every report that is now safely queued here (new or already known)
     */
    @Synchronized
    fun importHandoff(json: String, maxBatch: Int = 50): List<String> {
        if (json.length > 128 shl 10) return emptyList()
        val items = try { JsonLite.parse(json) as? List<*> } catch (e: IllegalArgumentException) { null } ?: return emptyList()
        val all = read().toMutableList()
        val acked = ArrayList<String>()
        for (o in items.take(maxBatch)) {
            @Suppress("UNCHECKED_CAST") val r = ContentReport.parse(o as? Map<String, Any?> ?: continue) ?: continue
            if (all.any { it.id == r.id || it.dedupeKey == r.dedupeKey }) { acked += r.id; continue }
            all += r; acked += r.id
        }
        var list: List<ContentReport> = all
        while (list.size > maxReports || size(list) > maxBytes) list = list.drop(1)
        write(list)
        return acked
    }

    private fun size(l: List<ContentReport>) = l.sumOf { it.toJson().toByteArray().size + 1L }
    private fun read(): List<ContentReport> = if (!file.isFile) emptyList() else file.readLines(Charsets.UTF_8).mapNotNull { l ->
        if (l.isBlank()) null else try { @Suppress("UNCHECKED_CAST") ContentReport.parse(JsonLite.obj(l)) } catch (e: IllegalArgumentException) { null }
    }
    private fun write(l: List<ContentReport>) {
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        tmp.writeText(if (l.isEmpty()) "" else l.joinToString("\n", postfix = "\n") { it.toJson() }, Charsets.UTF_8)
        if (!tmp.renameTo(file)) { file.delete(); if (!tmp.renameTo(file)) throw IOException("rename failed") }
    }

    companion object { const val HOUR = 3_600_000L }
}

/** POST /api/v1/content/reports with the device token; at most [BATCH] reports per call, the queue keeps what the server did not answer for. */
class ReportUploader(baseUrl: String, proxy: Proxy? = null, private val http: HttpLite = HttpLite(proxy, userAgent = "CastBridge-reports")) {
    private val base = baseUrl.trimEnd('/')

    sealed class Result {
        data class Sent(val accepted: Int, val duplicates: Int, val rejected: Int) : Result()
        object NeedsRegistration : Result()
        data class Failed(val reason: String) : Result()
    }

    fun flush(queue: ReportQueue, deviceToken: String): Result {
        val reports = queue.pending(BATCH)
        if (reports.isEmpty()) return Result.Sent(0, 0, 0)
        val body = "{\"reports\":${JsonLite.write(reports.map { it.toMap() })}}"
        val r = try { post(body, deviceToken) } catch (e: IOException) { return Result.Failed("serveur injoignable : ${e.message ?: e.javaClass.simpleName}") }
        return when (r.code) {
            200 -> {
                val m = runCatching { JsonLite.obj(r.body) }.getOrDefault(emptyMap())
                queue.remove(reports.map { it.id })
                Result.Sent(m.int("accepted") ?: 0, m.int("duplicates") ?: 0, m.int("rejected") ?: 0)
            }
            401 -> Result.NeedsRegistration
            400, 413 -> { queue.remove(reports.map { it.id }); Result.Failed("signalements refusés : ${HttpLite.errorMessage(r)}") }  // never resend them
            else -> Result.Failed(HttpLite.errorMessage(r))
        }
    }

    private fun post(body: String, token: String): HttpLite.Response {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val c = http.open("$base/api/v1/content/reports")
        try {
            c.requestMethod = "POST"; c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            c.setRequestProperty("Authorization", "Bearer $token")
            c.setFixedLengthStreamingMode(bytes.size)
            c.outputStream.use { it.write(bytes) }
            val code = c.responseCode
            val text = (if (code >= 400) c.errorStream else c.inputStream)?.use { String(it.readBytes(), Charsets.UTF_8) } ?: ""
            return HttpLite.Response(code, text, emptyMap())
        } finally { c.disconnect() }
    }

    companion object { const val BATCH = 50 }
}
