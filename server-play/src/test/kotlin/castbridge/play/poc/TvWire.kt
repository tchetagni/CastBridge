package castbridge.play.poc

import castbridge.play.Cred
import castbridge.play.Wire
import castbridge.play.http
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Le transport d'une CastBridge-TV vu du service (w20-04b) : SSE + POST, SANS en-tête `Origin` (un client natif), le ticket `cbp1` dans `X-Play-Ticket` à chaque POST et le secret de
 * session dans `X-Play-Conn` (la TV n'a pas de cookies). Même forme que le futur transport de la TV (w20-05a), ici pour tester le service sur une vraie socket.
 */
class TvWire(private val port: Int, private val ticket: String, private val openStream: Boolean = true) : Wire() {
    /** Lecture du long-poll `GET /play/state?since=` (sans rien accuser si [since] = 0) : tout ce que le service a en file pour cette TV. Sert au client LENT (aucun flux ouvert). */
    fun backlog(since: Long = 0): List<Map<*, *>> {
        val r = http.send(HttpRequest.newBuilder(URI("http://127.0.0.1:$port/play/state?since=$since")).header("X-Play-Conn", conn!!).timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString())
        check(r.statusCode() == 200) { "long-poll ${r.statusCode()} ${r.body()}" }
        return ((castbridge.core.quiz.Json.parse(r.body()) as Map<*, *>)["msgs"] as List<*>).map { it as Map<*, *> }
    }

    @Volatile var conn: String? = null
    private val stopped = AtomicBoolean(false)
    @Volatile private var stream: java.util.stream.Stream<String>? = null

    @Synchronized override fun send(text: String) {
        if (stopped.get()) return
        val b = HttpRequest.newBuilder(URI("http://127.0.0.1:$port/play/act")).header("Content-Type", "application/json").header("X-Play-Ticket", ticket)
            .timeout(Duration.ofSeconds(5)).POST(HttpRequest.BodyPublishers.ofString(text))
        conn?.let { b.header("X-Play-Conn", it) }
        val r = http.send(b.build(), HttpResponse.BodyHandlers.ofString())
        if (r.statusCode() != 200) { failure = "POST ${r.statusCode()} ${r.body()}"; return }
        if (conn == null) { conn = Cred.of(r)?.substringAfter('=')?.also { if (openStream) startStream(it) } }
    }

    private fun startStream(id: String) {
        val req = HttpRequest.newBuilder(URI("http://127.0.0.1:$port/play/events")).header("Accept", "text/event-stream").header("X-Play-Conn", id).GET().build()
        Thread {
            try {
                val resp = http.send(req, HttpResponse.BodyHandlers.ofLines())
                if (resp.statusCode() != 200) { failure = "SSE ${resp.statusCode()}"; return@Thread }
                stream = resp.body()
                val data = StringBuilder()
                resp.body().use { lines ->
                    val it = lines.iterator()
                    while (it.hasNext() && !stopped.get()) {
                        val l = it.next()
                        when {
                            l.startsWith("data:") -> data.append(l.removePrefix("data:").trimStart())
                            l.isEmpty() -> if (data.isNotEmpty()) { inbox.add(data.toString()); data.setLength(0) }
                        }
                    }
                }
            } catch (e: Exception) { if (!stopped.get()) failure = e.toString() }
        }.also { it.isDaemon = true }.start()
    }

    /** La TV disparaît (câble débranché) : plus aucun POST, flux coupé, aucun au revoir. */
    fun abort() { stopped.set(true); runCatching { stream?.close() } }

    override fun close() { abort() }
}
