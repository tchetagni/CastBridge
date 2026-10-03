package castbridge.play

import castbridge.core.quiz.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.WebSocket
import java.security.KeyPairGenerator
import java.security.KeyPair
import java.security.Signature
import java.time.Duration
import java.util.Base64
import java.util.concurrent.CompletionStage
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Clé de test + émetteur de tickets de test : le service vérifie VRAIMENT la signature (w20-04 remplacera l'émetteur, pas le vérificateur). */
object TestKeys {
    val pair: KeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    /** Clé publique au format du service : SPKI X.509 en Base64. */
    val pub: String = Base64.getEncoder().encodeToString(pair.public.encoded)
    private val b64 = Base64.getUrlEncoder().withoutPadding()

    fun ticket(now: Long = System.currentTimeMillis(), lifeMs: Long = 60_000, iat: Long = now, pair: KeyPair = this.pair): String {
        val payload = b64.encodeToString("""{"iat":$iat,"exp":${iat + lifeMs}}""".toByteArray())
        val head = "v1.$payload"
        val sig = Signature.getInstance("Ed25519").run { initSign(pair.private); update(head.toByteArray()); sign() }
        return head + "." + b64.encodeToString(sig)
    }
}

/** Un client du service, quel que soit le transport : texte JSON dans les deux sens (comme `PlayTransport`). */
abstract class Wire {
    val inbox = LinkedBlockingQueue<String>()
    @Volatile var closeCode: Int? = null
    @Volatile var failure: String? = null
    abstract fun send(text: String)
    abstract fun close()
    fun next(timeoutMs: Long = 3_000): String? = inbox.poll(timeoutMs, TimeUnit.MILLISECONDS)
    /** Premier message du type `t` reçu dans le délai (les autres sont consommés). */
    fun await(t: String, timeoutMs: Long = 5_000): Map<*, *>? {
        val end = System.currentTimeMillis() + timeoutMs
        while (true) {
            val left = end - System.currentTimeMillis(); if (left <= 0) return null
            val m = next(left) ?: return null
            val o = Json.parse(m) as Map<*, *>
            if (o["t"] == t) return o
        }
    }
}

private val http: HttpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).build()

class WsWire(port: Int, origin: String? = "https://bridge.sti-cm.com", xff: String? = null, ticket: String? = null, path: String = "/play/ws") : Wire() {
    private val ws: WebSocket
    val buffer = StringBuilder()
    @Volatile var refusedStatus: Int? = null

    init {
        val b = http.newWebSocketBuilder()
        origin?.let { b.header("Origin", it) }
        xff?.let { b.header("X-Forwarded-For", it) }
        ticket?.let { b.header("X-Play-Ticket", it) }
        ws = try {
            b.buildAsync(URI("ws://127.0.0.1:$port$path"), object : WebSocket.Listener {
                override fun onOpen(webSocket: WebSocket) { webSocket.request(1) }
                override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
                    buffer.append(data)
                    if (last) { inbox.add(buffer.toString()); buffer.setLength(0) }
                    webSocket.request(1); return null
                }
                override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String): CompletionStage<*>? { closeCode = statusCode; return null }
                override fun onError(webSocket: WebSocket, error: Throwable) { failure = error.toString() }
            }).get(5, TimeUnit.SECONDS)
        } catch (e: java.util.concurrent.ExecutionException) {
            val c = e.cause
            refusedStatus = (c as? java.net.http.WebSocketHandshakeException)?.response?.statusCode()
            throw WsRefused(refusedStatus, c.toString())
        }
    }

    override fun send(text: String) { ws.sendText(text, true).get(5, TimeUnit.SECONDS) }
    override fun close() { runCatching { ws.sendClose(1000, "bye").get(2, TimeUnit.SECONDS) }; runCatching { ws.abort() } }
    fun abort() { ws.abort() }
}

class WsRefused(val status: Int?, msg: String) : RuntimeException(msg)

/** Repli SSE : un POST pour parler, un flux `GET /play/events?token=` pour écouter. */
class SseWire(private val port: Int, private val origin: String? = "https://bridge.sti-cm.com", private val xff: String? = null) : Wire() {
    @Volatile var conn: String? = null
    private val stopped = AtomicBoolean(false)
    private var started = false
    @Volatile var lastEventId: String? = null

    @Synchronized override fun send(text: String) {
        val r = post(port, text, conn, origin, xff)
        if (r.statusCode() != 200) { failure = "POST ${r.statusCode()} ${r.body()}"; return }
        if (conn == null) { conn = (Json.parse(r.body()) as Map<*, *>)["conn"] as String }
        if (!started) { started = true; startStream() }
    }

    private fun startStream() {
        val req = HttpRequest.newBuilder(URI("http://127.0.0.1:$port/play/events?token=$conn")).header("Accept", "text/event-stream")
            .also { b -> origin?.let { b.header("Origin", it) }; xff?.let { b.header("X-Forwarded-For", it) } }.GET().build()
        Thread {
            try {
                val resp = http.send(req, HttpResponse.BodyHandlers.ofLines())
                if (resp.statusCode() != 200) { failure = "SSE ${resp.statusCode()}"; return@Thread }
                val data = StringBuilder()
                resp.body().use { lines ->
                    lines.iterator().let { it ->
                        while (it.hasNext() && !stopped.get()) {
                            val l = it.next()
                            when {
                                l.startsWith("data:") -> data.append(l.removePrefix("data:").trimStart())
                                l.startsWith("id:") -> lastEventId = l.removePrefix("id:").trim()
                                l.isEmpty() -> if (data.isNotEmpty()) { inbox.add(data.toString()); data.setLength(0) }
                            }
                        }
                    }
                }
            } catch (e: Exception) { if (!stopped.get()) failure = e.toString() }
        }.also { it.isDaemon = true }.start()
    }

    override fun close() { stopped.set(true) }

    companion object {
        fun post(port: Int, text: String, conn: String?, origin: String?, xff: String?): HttpResponse<String> {
            val b = HttpRequest.newBuilder(URI("http://127.0.0.1:$port/play/act")).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(text)).timeout(Duration.ofSeconds(5))
            conn?.let { b.header("X-Play-Conn", it) }; origin?.let { b.header("Origin", it) }; xff?.let { b.header("X-Forwarded-For", it) }
            return http.send(b.build(), HttpResponse.BodyHandlers.ofString())
        }
    }
}

/** Repli long-poll : `GET /play/state?token=&since=` (25 s côté service), les messages sont accusés par `since`. */
class PollWire(private val port: Int, private val origin: String? = "https://bridge.sti-cm.com", private val xff: String? = null) : Wire() {
    @Volatile var conn: String? = null
    private val stopped = AtomicBoolean(false)
    private var started = false

    @Synchronized override fun send(text: String) {
        val r = SseWire.post(port, text, conn, origin, xff)
        if (r.statusCode() != 200) { failure = "POST ${r.statusCode()} ${r.body()}"; return }
        if (conn == null) { conn = (Json.parse(r.body()) as Map<*, *>)["conn"] as String }
        if (!started) { started = true; Thread { loop() }.also { it.isDaemon = true }.start() }
    }

    private fun loop() {
        var since = 0L
        while (!stopped.get()) {
            try {
                val b = HttpRequest.newBuilder(URI("http://127.0.0.1:$port/play/state?token=$conn&since=$since")).timeout(Duration.ofSeconds(35)).GET()
                origin?.let { b.header("Origin", it) }; xff?.let { b.header("X-Forwarded-For", it) }
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
