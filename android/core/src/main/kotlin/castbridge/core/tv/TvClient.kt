package castbridge.core.tv

import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Minimal client for [ReceiverServer]. [base] is like "http://192.168.0.117:8765". Blocking calls. */
class TvClient(val base: String, private val pin: String? = null) {
    data class Part(val length: Long, val done: Boolean)

    fun part(name: String): Part = parsePart(call("GET", "/api/part?name=${enc(name)}"))
    fun reset(name: String) = call("POST", "/api/reset?name=${enc(name)}")
    fun info(): String = call("GET", "/api/info")
    fun play(name: String, posMs: Long = 0) = call("POST", "/api/play?name=${enc(name)}&pos=$posMs")
    fun pause() = call("POST", "/api/pause")
    fun resume() = call("POST", "/api/resume")
    fun stop() = call("POST", "/api/stop")
    fun seek(posMs: Long) = call("POST", "/api/seek?pos=$posMs")
    fun delete(name: String) = call("POST", "/api/delete?name=${enc(name)}")
    fun sysinfo(): String = call("GET", "/api/sysinfo")
    fun setVolume(pct: Int) = call("POST", "/api/volume?pct=$pct")
    fun restart() = call("POST", "/api/restart")
    fun rename(name: String, to: String) = call("POST", "/api/rename?name=${enc(name)}&to=${enc(to)}")
    /** Generic call for extension routes. */
    fun raw(method: String, path: String): String = call(method, path)

    /** Sends bytes [offset, total) read from [src]. Throws [Conflict] if the TV holds a different offset. */
    fun upload(name: String, offset: Long, total: Long, src: InputStream, onBytes: (Long) -> Unit): Part {
        val c = open("PUT", "/upload/${enc(name)}?offset=$offset&total=$total")
        c.doOutput = true
        c.readTimeout = 60_000
        c.setFixedLengthStreamingMode(total - offset)
        c.setRequestProperty("Content-Type", "application/octet-stream")
        c.outputStream.use { out ->
            val buf = ByteArray(256 * 1024)
            var left = total - offset
            while (left > 0) {
                val r = src.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                if (r < 0) throw IOException("source ended early")
                out.write(buf, 0, r)
                left -= r
                onBytes(r.toLong())
            }
        }
        val body = read(c, allow409 = true)
        if (c.responseCode == 409) throw Conflict(parsePart(body).length)
        return parsePart(body)
    }

    class Conflict(val serverLength: Long) : IOException("offset conflict, TV has $serverLength")
    class HttpError(val code: Int, body: String) : IOException("HTTP $code: ${body.take(200)}")

    private fun call(method: String, path: String): String {
        val c = open(method, path)
        if (method == "POST") { c.doOutput = true; c.setFixedLengthStreamingMode(0); c.outputStream.close() }
        return read(c)
    }

    private fun open(method: String, path: String) = (URL(base + path).openConnection() as HttpURLConnection).apply {
        requestMethod = method; connectTimeout = 4000; readTimeout = 8000
        pin?.let { setRequestProperty("X-CB-Pin", it) }
    }

    private fun read(c: HttpURLConnection, allow409: Boolean = false): String {
        val code = c.responseCode
        val text = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code >= 400 && !(allow409 && code == 409)) throw HttpError(code, text)
        return text
    }

    companion object {
        fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
        fun parsePart(json: String) = Part(
            Regex("\"length\":(\\d+)").find(json)?.groupValues?.get(1)?.toLong() ?: 0,
            json.contains("\"done\":true"))
        fun num(json: String, key: String): Long? = Regex("\"$key\":(-?\\d+)").find(json)?.groupValues?.get(1)?.toLong()
        fun str(json: String, key: String): String? = Regex("\"$key\":\"((?:[^\"\\\\]|\\\\.)*)\"").find(json)
            ?.groupValues?.get(1)?.replace("\\\"", "\"")?.replace("\\\\", "\\")
    }
}

/**
 * Resumable upload: on any network failure it waits (backoff up to 5 s), re-resolves the TV
 * (its IP may have changed), asks how much it already has, and continues from there.
 */
class ResumableUpload(
    private val name: String,
    private val total: Long,
    private val resolve: () -> String?,          // current base URL of the TV, null if not found yet
    private val openAt: (Long) -> InputStream,   // source stream positioned at the given offset
    private val cancelled: () -> Boolean = { false },
    private val sleep: (Long) -> Unit = Thread::sleep,
    private val pin: String? = null,
) {
    sealed class State {
        data class Uploading(val sent: Long, val total: Long) : State()
        data class Waiting(val sent: Long, val total: Long, val reason: String) : State()
        object Done : State()
        data class Failed(val reason: String) : State()
    }

    /** Runs until done, cancelled or a non-retryable error. Returns the final state. */
    fun run(onState: (State) -> Unit): State {
        var sent = 0L
        var backoff = 500L
        while (!cancelled()) {
            val base = resolve()
            if (base == null) {
                onState(State.Waiting(sent, total, "TV introuvable"))
                sleep(backoff); backoff = minOf(backoff * 2, 5000); continue
            }
            val tv = TvClient(base, pin)
            try {
                val p = tv.part(name)
                sent = p.length
                if (p.done) return State.Done.also(onState)
                if (sent > total) { tv.reset(name); sent = 0 }
                onState(State.Uploading(sent, total))
                openAt(sent).use { src ->
                    val r = tv.upload(name, sent, total, src) { n ->
                        sent += n; backoff = 500
                        onState(State.Uploading(sent, total))
                        if (cancelled()) throw java.io.InterruptedIOException("cancelled")
                    }
                    if (r.done) return State.Done.also(onState)
                }
            } catch (e: TvClient.HttpError) {
                if (e.code == 507 || e.code == 400 || e.code == 401) return State.Failed(e.message ?: "erreur").also(onState)
                onState(State.Waiting(sent, total, e.message ?: "erreur"))
                sleep(backoff); backoff = minOf(backoff * 2, 5000)
            } catch (e: IOException) {
                if (cancelled()) break
                onState(State.Waiting(sent, total, e.message ?: e.javaClass.simpleName))
                sleep(backoff); backoff = minOf(backoff * 2, 5000)
            }
        }
        return State.Failed("annulé").also(onState)
    }
}
