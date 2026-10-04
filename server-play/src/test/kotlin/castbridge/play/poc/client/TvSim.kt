package castbridge.play.poc.client

import castbridge.core.connect.Routes
import castbridge.core.quiz.Json
import castbridge.core.quiz.QuizBank
import castbridge.core.quiz.QuizHttp
import castbridge.core.quiz.online.PlayHttpTransport
import castbridge.core.quiz.online.PlayTransport
import castbridge.core.quiz.online.PlayTvSession
import castbridge.core.quiz.online.RelayAuthority
import castbridge.core.quiz.online.RttSource
import castbridge.core.quiz.online.TransportHealth
import castbridge.play.TestKeys
import castbridge.play.TestRights
import fi.iki.elonen.NanoHTTPD
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Le client HTTP des TV de test est `HttpURLConnection` (comme sur la vraie TV) : sa réutilisation des connexions est un réglage de la JVM, lu UNE fois. Le build de
 * `server-play` met `http.keepAlive=false` pour ses autres tests (clients `java.net.http`, qui l'ignorent) ; aucun autre test de ce module n'utilise `HttpURLConnection`,
 * donc on le remet à `true` AVANT le premier usage. [effective] dit si cela a marché (sinon les mesures EDGE seraient pessimistes : une connexion par POST).
 */
object KeepAlive {
    val effective: Boolean = run {
        System.setProperty("http.keepAlive", "true"); System.setProperty("http.maxConnections", "10")
        System.getProperty("http.keepAlive") == "true"
    }
}

/** Horloge monotone des TV de test (ms). */
val mono: () -> Long = { System.nanoTime() / 1_000_000 }

/**
 * Transport espion : enveloppe le vrai [PlayHttpTransport], date (horloge du test, la MÊME JVM que le service) chaque message serveur reçu et chaque `relayAct` envoyé.
 * Clés des messages : `type|seq` (+ `|ref` pour un accusé) ; pour les `state`, aussi `PHASE|index` (première vue d'une phase d'une question).
 */
class Tap(private val inner: PlayTransport) : PlayTransport, RttSource, TransportHealth, AutoCloseable {
    val received = ConcurrentHashMap<String, Long>()
    /** Les `state` reçus, dans l'ordre : (instant, index de la question, phase du Duel). */
    val states = CopyOnWriteArrayList<Triple<Long, Int, String>>()
    val receivedBytes = ConcurrentHashMap<String, Long>()
    val sentRelay = ConcurrentHashMap<Long, Long>()
    val ackResults = ConcurrentHashMap<String, Int>()
    val ackLatency = CopyOnWriteArrayList<Pair<Long, Long>>()   // (seq du relayAct, ms)
    override val state get() = inner.state
    val failure: String? get() = (inner as? PlayHttpTransport)?.lastFailure
    override val certificateInvalid get() = (inner as? TransportHealth)?.certificateInvalid == true
    override fun onRtt(listener: (Long) -> Unit) { (inner as? RttSource)?.onRtt(listener) }
    override fun close() { (inner as? AutoCloseable)?.close() }

    override fun send(text: String) {
        if (text.startsWith("{\"t\":\"relayAct\"")) Regex("\"seq\":(\\d+)").find(text)?.groupValues?.get(1)?.toLong()?.let { sentRelay.putIfAbsent(it, System.currentTimeMillis()) }
        inner.send(text)
    }

    override fun onMessage(listener: (String) -> Unit) = inner.onMessage { text ->
        val now = System.currentTimeMillis()
        val type = Regex("^\\{\"t\":\"([a-zA-Z]+)\"").find(text)?.groupValues?.get(1) ?: "?"
        val seq = Regex("\"seq\":(\\d+)").find(text)?.groupValues?.get(1) ?: "0"
        val ref = if (type == "ack") Regex("\"ref\":(\\d+)").find(text)?.groupValues?.get(1) else null
        val key = "$type|$seq" + (ref?.let { "|$it" } ?: "")
        received.putIfAbsent(key, now); receivedBytes[type] = (receivedBytes[type] ?: 0L) + text.length
        if (type == "state") Regex("\"duel\":\\{\"phase\":\"(\\w+)\",\"index\":(\\d+)").find(text)?.let { received.putIfAbsent("${it.groupValues[1]}|${it.groupValues[2]}", now); states += Triple(now, it.groupValues[2].toInt(), it.groupValues[1]) }
        if (type == "ack") Regex("\"result\":\"(\\w+)\"").find(text)?.groupValues?.get(1)?.let { ackResults.merge(it, 1, Int::plus) }
        if (ref != null) sentRelay[ref.toLong()]?.let { ackLatency += ref.toLong() to (now - it) }
        listener(text)
    }
}

/** Un téléphone simulé : parle UNIQUEMENT à `/quiz` de sa TV (page locale), répond en [delayMs] après l'ouverture ; `correct(i)` dit s'il répond juste à la question i. */
class SimPhone(private val tvPort: Int, val name: String, private val bank: QuizBank, private val delayMs: Long, private val correct: (Int) -> Boolean = { true }) {
    init { check(KeepAlive.effective) }   // avant tout HttpURLConnection de ce processus (réglage lu une seule fois par la JVM)
    @Volatile var token: String? = null
    @Volatile var id: String? = null
    @Volatile var joinStatus = 0
    @Volatile var lastView: Map<String, Any?>? = null
    @Volatile var finalRanking: List<String>? = null
    val outcomes = ConcurrentHashMap<Int, Map<*, *>?>()
    val answered = ConcurrentHashMap.newKeySet<String>()
    val errors = CopyOnWriteArrayList<String>()
    private val running = AtomicBoolean(true)
    private val base = "http://127.0.0.1:$tvPort"

    private fun call(method: String, path: String, timeout: Int = 15_000): Pair<Int, String> {
        val c = URL(base + path).openConnection() as HttpURLConnection
        c.requestMethod = method; c.readTimeout = timeout; c.connectTimeout = 5_000
        if (method == "POST") { c.doOutput = true; c.outputStream.close() }
        val code = c.responseCode
        return code to ((if (code < 400) c.inputStream else c.errorStream)?.use { String(it.readBytes(), Charsets.UTF_8) }.orEmpty())
    }

    fun join(code: String) {
        val (st, body) = call("POST", "/quiz/api/join?code=$code&name=${java.net.URLEncoder.encode(name, "UTF-8")}&dev=phone-$name-000001")
        joinStatus = st
        if (st == 200) { val o = Json.obj(body); token = o["token"] as String; id = o["id"] as String; Thread({ loop() }, "phone-$name").apply { isDaemon = true; start() } }
    }

    @Suppress("UNCHECKED_CAST")
    private fun loop() {
        var since = 0L
        while (running.get()) {
            try {
                val (st, body) = call("GET", "/quiz/api/state?token=$token&since=$since&wait=2", 10_000)
                if (st == 410) return
                Thread.sleep(150)   // la page réelle ne redemande pas plus vite (QuizHttp : 10 requêtes par seconde et par adresse)
                if (st != 200) { Thread.sleep(200); continue }
                val v = Json.obj(body); lastView = v
                since = (v["v"] as? Number)?.toLong() ?: since
                val duel = v["duel"] as? Map<String, Any?> ?: continue
                val phase = duel["phase"] as String
                val idx = (duel["index"] as Number).toInt()
                (duel["outcome"] as? Map<*, *>)?.let { outcomes[idx] = it }
                if (phase == "FINISHED" && (v["stage"] == "FINISHED")) finalRanking = (duel["ranking"] as List<Map<String, Any?>>).map { it["name"] as String }
                val q = duel["question"] as Map<String, Any?>
                val qid = q["id"] as String
                val open = (duel["waitMs"] as? Number)?.toLong() == 0L
                if (phase == "QUESTION" && open && answered.add(qid)) {
                    Thread({
                        Thread.sleep(delayMs)
                        val choices = (q["choices"] as List<*>).map { it as String }
                        val orig = bank.all.first { it.id == qid }
                        val right = choices.indexOf(orig.choices[orig.answer])
                        val choice = if (correct(idx)) right else (right + 1) % 4
                        runCatching { call("POST", "/quiz/api/act?token=$token&action=answer&q=$qid&choice=$choice") }.onFailure { errors += it.toString() }
                    }, "phone-$name-answer").apply { isDaemon = true; start() }
                }
            } catch (e: Exception) { errors += e.javaClass.simpleName; if (running.get()) Thread.sleep(200) }
        }
    }

    fun stop() { running.set(false) }
}

/**
 * Une TV de test : [PlayTvSession] + [RelayAuthority] + les routes `/quiz` (QuizHttp sur autorité, vrai NanoHTTPD sur port aléatoire) + des téléphones simulés. Le transport
 * est le VRAI [PlayHttpTransport] (enveloppé par [Tap]) ; [proxyPort] non nul = derrière un [SlowSocksProxy] (la TV B reliée par la passerelle). Une TV hôte passe la
 * révélation et le classement comme la télécommande le ferait (60 ms).
 */
class SimTv(val name: String, servicePort: Int, private val bank: QuizBank, proxyPort: Int? = null, private val autoSkip: Boolean = false, ticketLifeMs: Long = 10 * 60_000L, ticketOnEveryPost: Boolean = true) {
    init { check(KeepAlive.effective) }
    private val proxy: Proxy? = proxyPort?.let { Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", it)) }
    val taps = CopyOnWriteArrayList<Tap>()
    val session = PlayTvSession(clock = mono, transports = { tk ->
        Tap(PlayHttpTransport("http://127.0.0.1:$servicePort", tk, { proxy }, mono, refreshTicket = { TestKeys.ticket(lifeMs = ticketLifeMs) }, ticketOnEveryPost = ticketOnEveryPost)).also { taps += it }
    }, ticket = { TestKeys.ticket(lifeMs = ticketLifeMs) }, via = { if (proxy != null) Routes.Via.GATEWAY else Routes.Via.DIRECT })
    val relay = RelayAuthority(session, mono)
    private val http = QuizHttp(QuizHttp.AuthoritySource { relay })
    private val web = object : NanoHTTPD("127.0.0.1", 0) {
        override fun serve(s: IHTTPSession): Response = http.serve(s) ?: newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "404")
    }.also { it.start(5_000, true) }
    val tvPort: Int get() = web.listeningPort
    val phones = ArrayList<SimPhone>()
    private val running = AtomicBoolean(true)
    private val skipped = HashSet<String>()
    @Volatile var finalRanking: List<String>? = null
    val linkSamples = CopyOnWriteArrayList<Pair<Long, String>>()   // (ms, "niveau texte") à chaque changement de signe
    private var lastSign = ""

    init {
        Thread({
            while (running.get()) {
                runCatching { session.tick() }
                if (session.started) {
                    val s = session.safety(); val sign = "${s.level}|${s.text}"
                    if (sign != lastSign) { lastSign = sign; linkSamples += System.currentTimeMillis() to sign }
                    runCatching { drive() }
                }
                Thread.sleep(50)
            }
        }, "sim-tv-$name").apply { isDaemon = true; start() }
    }

    @Suppress("UNCHECKED_CAST")
    private fun drive() {
        val v = session.authority.view(null)
        val duel = v["duel"] as? Map<String, Any?> ?: return
        val phase = duel["phase"] as String
        if (autoSkip && (phase == "REVEAL" || phase == "BOARD") && skipped.add("${duel["index"]}-$phase"))
            Thread({ Thread.sleep(60); session.authority.act(null, "skip", null, null, null) }).apply { isDaemon = true; start() }
        if (phase == "FINISHED" && (v["room"] as Map<String, Any?>)["state"] == "FINISHED") finalRanking = (duel["ranking"] as List<Map<String, Any?>>).map { it["name"] as String }
    }

    fun addPhone(name: String, delayMs: Long, correct: (Int) -> Boolean = { true }) = SimPhone(tvPort, name, bank, delayMs, correct).also { phones += it }

    /** Toutes les mesures de réception de cette TV (une entrée par session de transport ouverte). */
    fun received(key: String): Long? = taps.firstNotNullOfOrNull { it.received[key] }

    fun stop() {
        running.set(false); phones.forEach { it.stop() }; session.stop(); runCatching { web.stop() }
    }
}

/** Les clés d'activation de test : une TV de test = `TestRights.PROD` pour sa création/entrée. */
val prodActivation: String get() = TestRights.PROD
