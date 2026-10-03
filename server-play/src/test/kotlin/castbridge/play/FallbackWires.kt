package castbridge.play

import castbridge.core.quiz.Json
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/** Réseau de confiance des tests : la boucle locale (le pair des tests est 127.0.0.1) ; le service n'en a AUCUN par défaut. */
val LOOPBACK: List<Cidr> = listOf(Cidr.parse("127.0.0.0/8")!!)

private val devCounter = java.util.concurrent.atomic.AtomicInteger()
/** Un identifiant d'appareil neuf (INTERNET exige un `deviceHash`, un par appareil). */
fun dev() = "devtest-%06d".format(devCounter.incrementAndGet())

/** Toutes les adresses demandées par les clients de repli : aucune ne doit contenir un secret (B5). */
object SeenUrls { val all = CopyOnWriteArrayList<String>() }

/**
 * Identifiant de session de repli côté client : `cbp=<secret>` (cookie posé par le service, envoyé en en-tête `Cookie`), ou, pour un service plus ancien,
 * le secret du corps JSON (en-tête `X-Play-Conn`, adresse `?token=`). Les clients de test savent les deux : les tests disent ce que le service DOIT faire.
 */
object Cred {
    fun of(r: HttpResponse<String>): String? =
        r.headers().firstValue("set-cookie").map { it.substringBefore(';') }.orElse(null)
            ?: runCatching { (Json.parse(r.body()) as Map<*, *>)["conn"] as String }.getOrNull()

    fun isCookie(c: String?) = c != null && c.startsWith("cbp=")

    fun apply(b: HttpRequest.Builder, c: String?) { if (isCookie(c)) b.header("Cookie", c!!) else if (c != null) b.header("X-Play-Conn", c) }

    fun url(port: Int, path: String, c: String?, extra: String = ""): String {
        val q = listOfNotNull(if (c != null && !isCookie(c)) "token=$c" else null, extra.takeIf { it.isNotEmpty() }).joinToString("&")
        return "http://127.0.0.1:$port$path" + (if (q.isNotEmpty()) "?$q" else "").also { SeenUrls.all += path + it }
    }
}

/** Repli SSE : un POST pour parler, un flux `GET /play/events` pour écouter. */
class SseWire(private val port: Int, private val origin: String? = "https://bridge.sti-cm.com", private val xff: String? = null) : Wire() {
    @Volatile var conn: String? = null
    private val stopped = AtomicBoolean(false)
    private var started = false
    @Volatile var lastEventId: String? = null

    @Synchronized override fun send(text: String) {
        val r = post(port, text, conn, origin, xff)
        if (r.statusCode() != 200) { failure = "POST ${r.statusCode()} ${r.body()}"; return }
        if (conn == null) conn = Cred.of(r)
        if (!started) { started = true; startStream() }
    }

    private fun startStream() {
        val req = HttpRequest.newBuilder(URI(Cred.url(port, "/play/events", conn))).header("Accept", "text/event-stream")
            .also { b -> Cred.apply(b, conn); origin?.let { b.header("Origin", it) }; xff?.let { b.header("X-Forwarded-For", it) } }.GET().build()
        Thread {
            try {
                val resp = http.send(req, HttpResponse.BodyHandlers.ofLines())
                if (resp.statusCode() != 200) { failure = "SSE ${resp.statusCode()}"; return@Thread }
                val data = StringBuilder()
                resp.body().use { lines ->
                    val it = lines.iterator()
                    while (it.hasNext() && !stopped.get()) {
                        val l = it.next()
                        when {
                            l.startsWith("data:") -> data.append(l.removePrefix("data:").trimStart())
                            l.startsWith("id:") -> lastEventId = l.removePrefix("id:").trim()
                            l.isEmpty() -> if (data.isNotEmpty()) { inbox.add(data.toString()); data.setLength(0) }
                        }
                    }
                }
            } catch (e: Exception) { if (!stopped.get()) failure = e.toString() }
        }.also { it.isDaemon = true }.start()
    }

    override fun close() { stopped.set(true) }

    companion object {
        fun post(port: Int, text: String, conn: String?, origin: String?, xff: String?): HttpResponse<String> {
            SeenUrls.all += "/play/act"
            val b = HttpRequest.newBuilder(URI("http://127.0.0.1:$port/play/act")).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(text)).timeout(Duration.ofSeconds(5))
            Cred.apply(b, conn); origin?.let { b.header("Origin", it) }; xff?.let { b.header("X-Forwarded-For", it) }
            return http.send(b.build(), HttpResponse.BodyHandlers.ofString())
        }
    }
}

/** Repli long-poll : `GET /play/state?since=` (25 s côté service), les messages sont accusés par `since`. */
class PollWire(private val port: Int, private val origin: String? = "https://bridge.sti-cm.com", private val xff: String? = null) : Wire() {
    @Volatile var conn: String? = null
    private val stopped = AtomicBoolean(false)
    private var started = false

    @Synchronized override fun send(text: String) {
        val r = SseWire.post(port, text, conn, origin, xff)
        if (r.statusCode() != 200) { failure = "POST ${r.statusCode()} ${r.body()}"; return }
        if (conn == null) conn = Cred.of(r)
        if (!started) { started = true; Thread { loop() }.also { it.isDaemon = true }.start() }
    }

    private fun loop() {
        var since = 0L
        while (!stopped.get()) {
            try {
                val b = HttpRequest.newBuilder(URI(Cred.url(port, "/play/state", conn, "since=$since"))).timeout(Duration.ofSeconds(35)).GET()
                Cred.apply(b, conn); origin?.let { b.header("Origin", it) }; xff?.let { b.header("X-Forwarded-For", it) }
                val r = http.send(b.build(), HttpResponse.BodyHandlers.ofString())
                if (r.statusCode() != 200) { failure = "GET ${r.statusCode()}"; return }
                val o = Json.parse(r.body()) as Map<*, *>
                for (m in o["msgs"] as List<*>) inbox.add(Json.write(m))
                since = (o["next"] as Number).toLong()
            } catch (e: Exception) { if (!stopped.get()) failure = e.toString(); return }
        }
    }

    override fun close() { stopped.set(true) }
}
