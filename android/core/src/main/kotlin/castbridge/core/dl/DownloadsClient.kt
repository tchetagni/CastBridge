package castbridge.core.dl

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Phone side of /api/downloads (blocking calls, run them off the main thread). [base] is like "http://192.168.0.117:8765". */
class DownloadsClient(val base: String, private val pin: String?) {
    /** The TV refused: [code] is the machine reason ("warning", "space", "engine"...), [message] is for people. */
    class Refused(val http: Int, val code: String, message: String) : IOException(message)

    data class Task(val id: String, val name: String, val kind: String, val state: String, val label: String, val total: Long,
                    val done: Long, val down: Long, val up: Long, val eta: Long, val connections: Int, val seeders: Int,
                    val volumeLabel: String, val error: String?, val canPause: Boolean, val canResume: Boolean, val files: Int)
    data class Done(val id: String, val name: String, val files: List<String>, val volumeLabel: String, val size: Long, val at: Long)
    data class State(val engineAvailable: Boolean, val engineRunning: Boolean, val engineMessage: String, val warningAccepted: Boolean,
                     val warning: String, val down: Long, val up: Long, val downLimit: Long, val upLimit: Long, val seeding: Boolean,
                     val tasks: List<Task>, val done: List<Done>)
    data class FileEntry(val index: Int, val path: String, val length: Long, val done: Long, val selected: Boolean)

    fun state(): State = parseState(call("GET", "/api/downloads"))
    fun accept() { call("POST", "/api/downloads/accept") }
    /** Returns a note from the TV (e.g. "went to the internal storage"), or null. */
    fun add(link: String, volume: String? = null): String? =
        Json.parse(call("POST", "/api/downloads/add?url=${enc(link)}" + (volume?.let { "&volume=${enc(it)}" } ?: ""))).obj().s("note")
    fun upload(name: String, bytes: ByteArray): String? =
        Json.parse(call("POST", "/api/downloads/upload?name=${enc(name)}", bytes)).obj().s("note")
    fun pause(id: String) { call("POST", "/api/downloads/pause?id=${enc(id)}") }
    fun resume(id: String) { call("POST", "/api/downloads/resume?id=${enc(id)}") }
    fun remove(id: String, withFiles: Boolean) { call("POST", "/api/downloads/remove?id=${enc(id)}&files=${if (withFiles) 1 else 0}") }
    fun priority(id: String, move: String) { call("POST", "/api/downloads/priority?id=${enc(id)}&move=${enc(move)}") }
    fun settings(downLimit: Long? = null, upLimit: Long? = null, seeding: Boolean? = null) {
        val q = listOfNotNull(downLimit?.let { "downLimit=$it" }, upLimit?.let { "upLimit=$it" }, seeding?.let { "seeding=${if (it) 1 else 0}" })
        call("POST", "/api/downloads/settings?" + q.joinToString("&"))
    }
    fun limit(id: String, bytesPerSec: Long) { call("POST", "/api/downloads/options?id=${enc(id)}&opt.max-download-limit=$bytesPerSec") }
    fun files(id: String): List<FileEntry> = Json.parse(call("GET", "/api/downloads/files?id=${enc(id)}")).obj()["files"].list().map {
        val m = it.obj(); FileEntry(m.n("index").toInt(), m.s("path").orEmpty(), m.n("length"), m.n("done"), m.b("selected"))
    }
    fun select(id: String, indexes: List<Int>) { call("POST", "/api/downloads/select?id=${enc(id)}&files=${indexes.joinToString(",")}") }
    fun about(): String = Json.parse(call("GET", "/api/downloads/about")).obj().s("text").orEmpty()
    /** Plays a finished file on the TV (the library's play route). */
    fun play(name: String) { call("POST", "/api/play?name=${enc(name)}") }

    private fun call(method: String, path: String, body: ByteArray? = null): String {
        val c = URL(base + path).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method; c.connectTimeout = 4000; c.readTimeout = 15_000   // adding a link probes its size (up to ~6 s)
            pin?.let { c.setRequestProperty("X-CB-Pin", it) }
            if (method == "POST") {
                val b = body ?: ByteArray(0)
                c.doOutput = true; c.setFixedLengthStreamingMode(b.size)
                if (body != null) c.setRequestProperty("Content-Type", "application/octet-stream")
                c.outputStream.use { it.write(b) }
            }
            val code = c.responseCode
            val text = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code >= 400) {
                val m = runCatching { Json.parse(text).obj() }.getOrDefault(emptyMap())
                throw Refused(code, m.s("error") ?: "http $code", m.s("message") ?: when (code) {
                    401 -> "PIN refusé par la TV."
                    404 -> "Cette version de CastBridge TV ne gère pas les téléchargements : mettez-la à jour."
                    else -> m.s("error") ?: "Erreur $code"
                })
            }
            return text
        } finally { c.disconnect() }
    }

    companion object {
        fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

        fun parseState(json: String): State {
            val m = Json.parse(json).obj()
            val e = m["engine"].obj(); val g = m["global"].obj()
            return State(e.b("available"), e.b("running"), e.s("message").orEmpty(), m.b("warningAccepted"), m.s("warning").orEmpty(),
                g.n("down"), g.n("up"), g.n("downLimit"), g.n("upLimit"), g.b("seeding"),
                m["tasks"].list().map { it.obj() }.map { t ->
                    Task(t.s("id")!!, t.s("name").orEmpty(), t.s("kind").orEmpty(), t.s("state").orEmpty(), t.s("label").orEmpty(), t.n("total"),
                        t.n("done"), t.n("down"), t.n("up"), t.n("eta"), t.n("connections").toInt(), t.n("seeders").toInt(),
                        t.s("volumeLabel").orEmpty(), t.s("error"), t.b("canPause"), t.b("canResume"), t.n("files").toInt())
                },
                m["done"].list().map { it.obj() }.map { d ->
                    Done(d.s("id")!!, d.s("name").orEmpty(), d["files"].list().map { it.toString() }, d.s("volumeLabel").orEmpty(), d.n("size"), d.n("at"))
                })
        }

        /** What the phone should do with shared text: the first http(s)/ftp/sftp/magnet link in it, or null. */
        fun findLink(text: String): String? {
            Regex("magnet:\\?[^\\s\"'<>]+", RegexOption.IGNORE_CASE).find(text)?.let { return it.value }
            return Regex("(?:https?|ftp|sftp)://[^\\s\"'<>]+", RegexOption.IGNORE_CASE).find(text)?.value?.trimEnd('.', ',', ')', ';')
        }
    }
}
