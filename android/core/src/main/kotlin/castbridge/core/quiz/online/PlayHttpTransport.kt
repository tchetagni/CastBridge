package castbridge.core.quiz.online

import castbridge.core.quiz.Json
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.NoRouteToHostException
import java.net.Proxy
import java.net.URL
import java.net.UnknownHostException
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

/**
 * Transport du protocole `play` de la TV : SSE + POST sur `HttpURLConnection` / `HttpsURLConnection` SEULS (la TV n'a pas d'OkHttp), à travers le proxy SOCKS de la passerelle
 * Bluetooth quand [proxy] en rend un (null = direct : une passerelle qui disparaît donne une nouvelle tentative directe). AUCUNE confiance TLS ajoutée, aucune redirection suivie :
 * un certificat non valide donne [certificateInvalid] et l'état `CLOSED`, jamais un contournement.
 *
 * Protocole (docs/PLAY-PROTOCOL.md, `PlayFallbackController`) : chaque `POST /play/act` porte `X-Play-Ticket` (le service l'exige d'un client sans `Origin`) ; le secret de la session est lu dans `Set-Cookie: __Host-cbp…=<id>` puis
 * renvoyé en `X-Play-Conn` (jamais dans une adresse, jamais dans un journal : [toString] est masqué). Les messages serveur arrivent par le flux `GET /play/events` (une seule connexion
 * persistante) ; si le flux tombe, il est rouvert d'abord sur la MÊME session ; après trois échecs, repli sur `GET /play/state?since=`. HTTP 410 ⇒ `CLOSED` (la session de jeu reprendra
 * par `resume`). Les envois partent par UN fil écrivain qui COALESCE les `relayAct` en attente (une réponse par joueur et par question, la dernière) et les envoie en parallèle
 * (jusqu'à [MAX_PARALLEL] connexions gardées vivantes) : huit réponses n'attendent pas huit allers-retours à la suite.
 *
 * `state` : OPEN dès qu'on peut envoyer (la session se crée au premier envoi) tant que le flux va bien ; CONNECTING pendant la réouverture du flux ; CLOSED à la fin de la session.
 * Les horloges sont injectées ([clock], monotone, ms) : ce fichier est le SEUL du paquet à toucher le réseau (`OnlinePurityTest`).
 */
class PlayHttpTransport(
    baseUrl: String,
    ticket: String?,
    private val proxy: () -> Proxy?,
    private val clock: () -> Long,
    private val connectTimeoutMs: Int = 10_000,
    private val streamReadTimeoutMs: Int = 75_000,
    private val postReadTimeoutMs: Int = 20_000,
    private val sleeper: (Long) -> Unit = { Thread.sleep(it) },
    private val streamRetryMs: LongArray = longArrayOf(0L, 1_000L, 2_000L, 4_000L, 8_000L),
    /** Ticket frais demandé à la TV quand celui qu'on porte a plus de [ticketMaxAgeMs] (le service juge CHAQUE POST ; un ticket vit 10 min). */
    private val refreshTicket: (() -> String?)? = null,
    private val ticketMaxAgeMs: Long = 8 * 60_000L,
    /** Vrai tant que le service exige un ticket valide sur chaque POST d'un client sans `Origin` ; faux = seulement au premier (service qui ne juge que la création de session). */
    private val ticketOnEveryPost: Boolean = true,
) : PlayTransport, RttSource, TransportHealth, AutoCloseable {
    private val base = baseUrl.trimEnd('/')
    @Volatile private var ticket: String? = ticket
    @Volatile private var ticketAt = clock()
    @Volatile private var secret: String? = null
    @Volatile override var state = PlayTransport.Status.OPEN; private set
    @Volatile override var certificateInvalid = false; private set
    /** Dernier échec, sans secret ni adresse (pour le journal de la TV et les tests). */
    @Volatile var lastFailure: String? = null; private set
    @Volatile private var closed = false
    @Volatile private var listener: ((String) -> Unit)? = null
    @Volatile private var rttListener: ((Long) -> Unit)? = null
    @Volatile private var lastEventId = 0L
    private val queue = LinkedBlockingQueue<String>(MAX_QUEUE)
    private var writer: Thread? = null
    private var streamThread: Thread? = null
    private val pool by lazy { Executors.newFixedThreadPool(MAX_PARALLEL) { r -> Thread(r, "play-post").apply { isDaemon = true } } }
    private val live = java.util.Collections.synchronizedSet(HashSet<HttpURLConnection>())
    /** Octets envoyés et reçus (corps seulement) : mesure du débit de la liaison. */
    @Volatile var bytesUp = 0L; private set
    @Volatile var bytesDown = 0L; private set

    override fun onMessage(listener: (String) -> Unit) { this.listener = listener }
    override fun onRtt(listener: (Long) -> Unit) { rttListener = listener }

    @Synchronized override fun send(text: String) {
        if (closed || state == PlayTransport.Status.CLOSED) return
        if (!queue.offer(text)) { queue.poll(); queue.offer(text) }   // file pleine : le plus ancien cède la place
        if (writer == null) writer = Thread({ writeLoop() }, "play-writer").apply { isDaemon = true; start() }
    }

    override fun close() {
        closed = true; state = PlayTransport.Status.CLOSED
        synchronized(live) { live.toList() }.forEach { runCatching { it.disconnect() } }
        runCatching { pool.shutdownNow() }
    }

    override fun toString() = "PlayHttpTransport(host=${runCatching { URL(base).host }.getOrDefault("?")}, state=$state, secret=***, ticket=***)"

    // ------------------------------------------------------------------ envoi

    private fun writeLoop() {
        while (!closed) {
            val first = queue.poll(500, TimeUnit.MILLISECONDS) ?: continue
            val batch = ArrayList<String>().also { it += first; queue.drainTo(it) }
            var i = 0
            while (i < batch.size && !closed) {
                if (isRelay(batch[i])) {
                    val run = ArrayList<String>()
                    while (i < batch.size && isRelay(batch[i])) run += batch[i++]
                    sendParallel(coalesce(run))
                } else if (secret != null && batch[i].startsWith("{\"t\":\"pong\"")) sendParallel(listOf(batch[i++]))   // M-8 : un pong lent (3 essais, attentes ≤ 5 s) ne retient jamais la réponse d'un joueur derrière lui
                else deliverOne(batch[i++])
            }
        }
    }

    private fun isRelay(text: String) = text.startsWith("{\"t\":\"relayAct\"")

    /** Une réponse par joueur et par question : la dernière écrase les précédentes (le service accepte la première, le doublon serait `SAME`). */
    private fun coalesce(run: List<String>): List<String> {
        val byKey = LinkedHashMap<String, String>()
        for (t in run) { val d = PlayCodec.decodeClient(t); val k = (d as? PlayCodec.Decoded.Ok)?.msg?.let { it as? ClientMsg.RelayAct }?.let { it.token + "|" + it.questionId } ?: t; byKey[k] = t }
        return byKey.values.toList()
    }

    /** Chaque réponse part tout de suite sur sa propre connexion gardée vivante (au plus [MAX_PARALLEL] à la fois) : l'écrivain n'attend PAS la fin, sinon la 5e réponse attendrait quatre allers-retours. */
    private fun sendParallel(msgs: List<String>) { for (m in msgs) runCatching { pool.execute { deliverOne(m) } } }

    private fun deliverOne(text: String) {
        val establishing = secret == null
        var attempt = 0
        while (!closed) {
            when (val r = post(text)) {
                is PostResult.Ok -> return
                is PostResult.Gone -> { fail("session terminée (410)"); state = PlayTransport.Status.CLOSED; return }
                is PostResult.Refused -> { lastFailure = "refusé : HTTP ${r.status} ${r.detail}"; if (establishing) state = PlayTransport.Status.CLOSED; return }
                is PostResult.Fatal -> { lastFailure = r.why; state = PlayTransport.Status.CLOSED; return }
                is PostResult.Retry -> {
                    // un envoi qui crée la session (création, entrée) n'est rejoué que s'il n'a PAS atteint le service
                    if (attempt >= 3 || (establishing && !r.notDelivered)) { lastFailure = r.why; if (establishing) state = PlayTransport.Status.CLOSED; else if (state == PlayTransport.Status.OPEN) state = PlayTransport.Status.CONNECTING; return }
                    sleeper(r.waitMs.coerceAtMost(5_000L)); attempt++
                }
            }
        }
    }

    private sealed class PostResult {
        object Ok : PostResult()
        object Gone : PostResult()
        class Refused(val status: Int, val detail: String) : PostResult()
        class Fatal(val why: String) : PostResult()
        class Retry(val why: String, val notDelivered: Boolean, val waitMs: Long) : PostResult()
    }

    private fun open(path: String, method: String): HttpURLConnection {
        val px = proxy() ?: Proxy.NO_PROXY
        return (URL(base + path).openConnection(px) as HttpURLConnection).apply {
            requestMethod = method
            instanceFollowRedirects = false
            useCaches = false
            connectTimeout = connectTimeoutMs
            setRequestProperty("User-Agent", "CastBridge-TV")
            secret?.let { setRequestProperty("X-Play-Conn", it) }
        }.also { live += it }
    }

    private fun post(text: String): PostResult {
        val body = text.toByteArray(Charsets.UTF_8)
        if (refreshTicket != null && clock() - ticketAt >= ticketMaxAgeMs) runCatching { refreshTicket!!.invoke() }.getOrNull()?.let { ticket = it; ticketAt = clock() }
        val started = clock()
        var conn: HttpURLConnection? = null
        try {
            conn = open("/play/act", "POST").apply {
                readTimeout = postReadTimeoutMs
                doOutput = true
                setFixedLengthStreamingMode(body.size)
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                // Le service (OriginCheck) exige un ticket VALIDE sur chaque POST d'un client natif (sans `Origin`) : il part donc à chaque envoi, jamais ailleurs.
                if (ticketOnEveryPost || secret == null) ticket?.let { setRequestProperty("X-Play-Ticket", it) }
                setRequestProperty("Accept", "*/*")
            }
            conn.outputStream.use { it.write(body) }
            bytesUp += body.size
            val code = conn.responseCode
            val answer = (if (code < 400) conn.inputStream else conn.errorStream)?.use { it.readBytes() } ?: ByteArray(0)
            bytesDown += answer.size
            val elapsed = (clock() - started).coerceAtLeast(0L)
            return when {
                code == 200 -> {
                    if (secret == null) {
                        val s = cookieOf(conn) ?: return PostResult.Fatal("Set-Cookie absent : la session de jeu n'a pas été créée")
                        secret = s
                        streamThread = Thread({ streamLoop() }, "play-stream").apply { isDaemon = true; start() }
                    }
                    rttListener?.invoke(elapsed)
                    PostResult.Ok
                }
                code == 410 -> PostResult.Gone
                code == 429 || code == 503 -> PostResult.Retry("HTTP $code", true, (conn.getHeaderField("Retry-After")?.toLongOrNull() ?: 1L) * 1_000L)
                else -> PostResult.Refused(code, String(answer, Charsets.UTF_8).take(100))
            }
        } catch (e: SSLException) {
            certificateInvalid = true; return PostResult.Fatal("certificat non valide")
        } catch (e: IOException) {
            return PostResult.Retry(e.javaClass.simpleName, e is ConnectException || e is UnknownHostException || e is NoRouteToHostException, 500L)
        } finally { conn?.let { live -= it; if (closed) runCatching { it.disconnect() } } }
    }

    /** Le secret de session : `__Host-cbp…=<id>` dans `Set-Cookie` (jamais dans le corps ni dans l'adresse). */
    private fun cookieOf(conn: HttpURLConnection): String? =
        conn.headerFields.entries.firstOrNull { it.key.equals("Set-Cookie", ignoreCase = true) }?.value.orEmpty()
            .firstOrNull { it.startsWith("__Host-cbp") }?.substringAfter('=')?.substringBefore(';')?.trim()?.takeIf { it.isNotEmpty() }

    // ------------------------------------------------------------------ réception

    private fun streamLoop() {
        var failures = 0
        var polls = 0
        while (!closed && state != PlayTransport.Status.CLOSED) {
            val viaPoll = failures >= 3 && polls < POLLS_BEFORE_RETRY_STREAM
            val ended = try { if (viaPoll) { polls++; pollOnce() } else { polls = 0; readStream() } } catch (e: SSLException) {
                certificateInvalid = true; lastFailure = "certificat non valide"; state = PlayTransport.Status.CLOSED; return
            } catch (e: IOException) { lastFailure = e.javaClass.simpleName; End.FAILED }
            when (ended) {
                End.GONE -> { fail("session terminée (410)"); state = PlayTransport.Status.CLOSED; return }
                End.FAILED -> { failures++; if (!closed) { state = PlayTransport.Status.CONNECTING; sleeper(streamRetryMs[minOf(failures - 1, streamRetryMs.lastIndex)]) } }
                End.CLEAN -> { failures = 0; if (!closed) state = PlayTransport.Status.CONNECTING }   // le service a fermé le flux (durée max) : on le rouvre tout de suite
            }
        }
    }

    private enum class End { CLEAN, FAILED, GONE }

    private fun readStream(): End {
        val conn = open("/play/events", "GET").apply { readTimeout = streamReadTimeoutMs; setRequestProperty("Accept", "text/event-stream") }
        try {
            val code = conn.responseCode
            if (code == 410) return End.GONE
            if (code != 200) return End.FAILED
            state = PlayTransport.Status.OPEN
            var data = StringBuilder()
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { r ->
                while (true) {
                    val line = r.readLine() ?: break
                    bytesDown += line.length + 1
                    when {
                        line.startsWith("data:") -> data.append(line.removePrefix("data:").trimStart())
                        line.startsWith("id:") -> line.removePrefix("id:").trim().toLongOrNull()?.let { lastEventId = it }
                        line.isEmpty() -> if (data.isNotEmpty()) { val t = data.toString(); data = StringBuilder(); listener?.invoke(t) }
                    }
                }
            }
            return End.CLEAN
        } finally { live -= conn; runCatching { conn.disconnect() } }
    }

    /** Long-poll de secours : `{"msgs":[…],"next":n}`. */
    @Suppress("UNCHECKED_CAST")
    private fun pollOnce(): End {
        val conn = open("/play/state?since=$lastEventId", "GET").apply { readTimeout = POLL_READ_TIMEOUT_MS }
        try {
            val code = conn.responseCode
            if (code == 410) return End.GONE
            if (code != 200) return End.FAILED
            val text = conn.inputStream.use { it.readBytes() }.toString(Charsets.UTF_8)
            bytesDown += text.length
            state = PlayTransport.Status.OPEN
            val o = Json.parse(text) as? Map<String, Any?> ?: return End.FAILED
            (o["msgs"] as? List<*>)?.forEach { m -> if (m is Map<*, *>) listener?.invoke(Json.write(m)) }
            (o["next"] as? Number)?.toLong()?.let { lastEventId = it }
            return End.CLEAN
        } finally { live -= conn; runCatching { conn.disconnect() } }
    }

    private fun fail(why: String) { lastFailure = why }

    companion object {
        const val MAX_PARALLEL = 8
        const val MAX_QUEUE = 64
        const val POLL_READ_TIMEOUT_MS = 40_000
        const val POLLS_BEFORE_RETRY_STREAM = 5
    }
}
