package castbridge.play

import castbridge.core.quiz.EmbeddedQuestionSource
import castbridge.core.quiz.QUIZ_PACK_SUFFIX
import castbridge.core.quiz.QuizBank
import castbridge.core.quiz.QuizLotFormat
import castbridge.core.quiz.online.PlayScope
import castbridge.core.quiz.online.Limits
import castbridge.core.quiz.online.ServerRoom
import castbridge.play.entitlement.DirReservedSource
import castbridge.play.entitlement.ReservedBank
import castbridge.play.entitlement.RevocationsFeed
import castbridge.play.entitlement.TicketVerifier
import castbridge.play.entitlement.TrustedIssuers
import castbridge.play.guard.PlayGuard
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore

/**
 * Le service castbridge-play : HTTP/1.1 + WebSocket écrits sur le JDK seul (un fil virtuel par connexion), qui héberge les salles `ServerRoom` du cœur.
 * `/play` (page), `/play/ws` (WebSocket), `/play/events` + `/play/act` + `/play/state` (repli SSE et long-poll), `/play/health`, `/play/.well-known/caps`.
 * Rien sous `/api/`. [clock] est l'horloge du SERVEUR (ms) donnée aux salles (injectable en test) ; [roomScope] ne change qu'en test (production : `INTERNET`).
 */
class PlayServer(
    val cfg: PlayConfig,
    private val clock: () -> Long = System::currentTimeMillis,
    random: () -> java.util.Random = { SecureRandom() },
    roomScope: PlayScope = PlayScope.INTERNET,
    settings: ServerRoom.Settings = ServerRoom.Settings(),
    bank: QuizBank? = null,
) : AutoCloseable {
    /** Gardes anti-abus : seaux de débit (CGNAT-friendly, voir `Limits`), plafond relevé des adresses partagées, journal sans secret. */
    private val guard = PlayGuard(Limits(config = Limits.Config(connPerMinute = cfg.connPerMinute, connPerSecondGlobal = cfg.connPerSecond, maxOpenPerIp = cfg.maxPerIp, maxOpenPerIpShared = cfg.maxPerIpShared)))
    private val limits = ConnectionLimits(cfg.maxPerIp, cfg.maxConnections, cfg.maxPer48, gate = guard.limits, maxPerIpShared = cfg.maxPerIpShared)
    private val verifier = TicketVerifier(cfg.ticketPubKeys)
    private val origin = OriginCheck(cfg.origins)
    private val trusted = TrustedIssuers.parse(cfg.trustedKeys.joinToString(","))
    // une adresse de révocations refusée est une ERREUR de démarrage (jamais un échec silencieux)
    private val feed = RevocationsFeed(trusted.ring, cfg.revocationsUrl?.let { RevocationsFeed.httpFetcher(it) } ?: { null }, file = cfg.revocationsFile)
    /** La banque libre du service SANS les ids réservables, et les paquets réservés lus à la demande (w20-04). */
    private val reserved = ReservedBank(bank ?: loadBank(cfg.lotsDir), DirReservedSource(cfg.reservedDir), ReservedBank.readIds(cfg.reservedIdsFile))
    val hub = PlayHub(cfg, clock, reserved.freeBank, verifier, random, roomScope, settings, limits, reserved, feed::current, revocationsReady = { cfg.revocationsMode == RevocationsMode.OFF || feed.usable() }).also { it.guard = guard }   // fermé : plus de « adresse absente ⇒ prêt » (w20-04b)
    private val fallback = PlayFallbackController(cfg, hub, limits, verifier, origin)
    private val pages = PlayPageController(cfg.webPlay)
    private val health = HealthController(cfg, hub, limits, revocations = { if (cfg.revocationsMode == RevocationsMode.OFF) "disabled" else feed.status() })
    private val ticker = Ticker(cfg.tickMs) { hub.tick() }
    private val sockets = ConcurrentHashMap.newKeySet<Socket>()
    private val raw = Semaphore(cfg.maxConnections * 2)
    private lateinit var server: ServerSocket
    @Volatile private var running = false

    val port: Int get() = server.localPort
    fun rooms(): List<ServerRoom> = hub.rooms()
    fun tickerStopped(): Boolean = ticker.isStopped()

    fun start(): PlayServer {
        server = ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(InetAddress.getByName(cfg.bind), cfg.port), 256) }
        running = true
        // les clés de confiance ignorées (format, portées) sont DITES au démarrage, jamais perdues en silence
        trusted.warnings.forEachIndexed { i, w -> guard.log.event("play.config.warning.$i", w, level = "warn") }
        // le mode `off` des révocations est VISIBLE : journal au démarrage, santé « disabled » (une TV révoquée peut encore créer et rejoindre)
        if (cfg.revocationsMode == RevocationsMode.OFF) guard.log.event("play.config.revocations_off", "ATTENTION : révocations désactivées (CASTBRIDGE_PLAY_REVOCATIONS=off) : POC seulement, une TV révoquée n'est pas refusée", level = "warn")
        ticker.start()
        if (cfg.revocationsUrl != null) Thread.ofPlatform().name("play-revocations").daemon(true).start { refreshRevocations() }
        Thread.ofPlatform().name("play-accept").daemon(true).start { acceptLoop() }
        return this
    }

    /** Relit la liste signée des révocations toutes les 15 min (5 min après un échec) ; seul appel sortant du service, en lecture publique. */
    private fun refreshRevocations() {
        while (running) {
            val ok = feed.refresh()
            try { Thread.sleep(if (ok) feed.refreshMs else 5 * 60_000L) } catch (_: InterruptedException) { return }
        }
    }

    private fun acceptLoop() {
        while (running) {
            val s = try { server.accept() } catch (_: IOException) { return }
            if (!raw.tryAcquire()) { runCatching { s.close() }; continue }
            sockets += s
            Thread.ofVirtual().start { try { handle(s) } catch (_: Throwable) {} finally { sockets -= s; raw.release(); runCatching { s.close() } } }
        }
    }

    private fun handle(socket: Socket) {
        socket.tcpNoDelay = true
        val out = socket.getOutputStream()
        val req = try { MiniHttp.readRequest(socket, cfg.headDeadlineMs) } catch (e: HttpError) {
            MiniHttp.json(out, e.status, """{"error":"requête invalide"}""")
            // vide ce que le client envoie encore, pour que la réponse d'erreur ne soit pas perdue par une remise à zéro de la connexion
            runCatching { socket.shutdownOutput(); socket.soTimeout = 200; socket.getInputStream().readNBytes(65_536) }
            return
        } catch (_: IOException) { return }
        val ip = try { ClientIp.resolve(socket.inetAddress, req.header("x-forwarded-for"), cfg.trustedProxies) } catch (_: ForwardedForError) {
            MiniHttp.json(out, 400, """{"error":"X-Forwarded-For illisible"}"""); return   // le proxy de confiance a écrit un en-tête illisible : refus
        }
        val head = req.method == "HEAD"
        val p = req.path
        // Aucun navigateur n'atteint le jeu (w20-04b) : une requête de jeu portant un `Origin` est refusée avant toute autre chose, quelle que soit la liste d'origines
        val browser = !cfg.webPlay && p in GAME_PATHS && !req.header("origin").isNullOrBlank()
        when {
            browser -> MiniHttp.json(out, 403, """{"error":"origine refusée"}""")
            p == "/play/ws" -> ws(req, socket, out, ip)
            p == "/play/act" -> if (req.method == "POST") fallback.act(req, out, ip) else notAllowed(out)
            p == "/play/events" -> if (req.method == "GET") fallback.events(req, socket, out) else notAllowed(out)
            p == "/play/state" -> if (req.method == "GET") fallback.state(req, out) else notAllowed(out)
            req.method != "GET" && !head -> notAllowed(out)
            p == "/play/health" -> MiniHttp.json(out, 200, health.health())
            p == "/play/.well-known/caps" -> MiniHttp.json(out, 200, health.caps())
            pages.serve(p, out, head) -> {}
            else -> MiniHttp.json(out, 404, """{"error":"introuvable"}""")
        }
        // fermeture propre : ce que le client envoie encore (corps non lu) ne doit pas provoquer une remise à zéro qui efface la réponse
        runCatching { socket.shutdownOutput(); socket.soTimeout = 100; socket.getInputStream().readNBytes(65_536) }
    }

    private fun notAllowed(out: java.io.OutputStream) = MiniHttp.json(out, 405, """{"error":"méthode non permise"}""", mapOf("Allow" to "GET, POST"))

    private fun ws(req: HttpReq, socket: Socket, out: java.io.OutputStream, ip: String) {
        val key = req.header("sec-websocket-key")
        val upgrade = req.header("upgrade")?.equals("websocket", ignoreCase = true) == true && req.header("connection")?.split(',')?.any { it.trim().equals("upgrade", ignoreCase = true) } == true
        val keyOk = key != null && runCatching { java.util.Base64.getDecoder().decode(key.trim()).size == 16 }.getOrDefault(false)   // base64 de 16 octets exactement (RFC 6455 § 4.1)
        if (req.method != "GET" || !upgrade || !keyOk || req.header("sec-websocket-version")?.trim() != "13") return MiniHttp.json(out, 400, """{"error":"WebSocket attendu"}""")
        key!!
        val ticket = req.header("x-play-ticket")
        if (!origin.allows(req.header("origin"), verifier.verify(ticket, System.currentTimeMillis()))) return MiniHttp.json(out, 403, """{"error":"origine refusée"}""")
        val admission = limits.admit(ip)
        when (admission.verdict) {
            ConnectionLimits.Verdict.RATE -> { guard.log.event("play.limit.exceeded", "débit de connexions dépassé", mapOf("scope" to "CONNECT", "ip" to ip, "retryAfterMs" to admission.retryAfterMs)); return MiniHttp.json(out, 429, """{"error":"trop de connexions : réessayez dans un instant","retryAfterMs":${admission.retryAfterMs}}""", mapOf("Retry-After" to ((admission.retryAfterMs + 999) / 1000).coerceAtLeast(1).toString())) }
            ConnectionLimits.Verdict.IP_FULL -> return MiniHttp.json(out, 429, """{"error":"trop de connexions depuis cette adresse"}""", mapOf("Retry-After" to "10"))
            ConnectionLimits.Verdict.TOTAL_FULL -> return MiniHttp.json(out, 503, """{"error":"service complet"}""", mapOf("Retry-After" to "30"))
            ConnectionLimits.Verdict.OK -> {}
        }
        // la place prise ci-dessus est rendue quoi qu'il arrive avant l'enregistrement (sinon fuite de place) ; après, c'est `hub.onClosed` qui la rend
        var registered = false
        try {
            val id = ByteArray(12).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
            val conn = WsConn("w$id", ip, socket, cfg, hub)
            out.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: ${WsProtocol.acceptKey(key)}\r\n\r\n").toByteArray(Charsets.ISO_8859_1))
            out.flush()
            conn.ticket = ticket
            hub.register(conn); registered = true
            conn.run(req.input)
        } finally { if (!registered) limits.release(ip) }
    }

    /** Arrêt en douceur : annonce la maintenance aux salles, refuse les nouvelles, attend au plus [graceMs] que les connexions partent, puis ferme. */
    fun drain(graceMs: Long) {
        hub.startDrain()
        val end = System.currentTimeMillis() + graceMs
        while (System.currentTimeMillis() < end && hub.connectionsOpen() > 0) Thread.sleep(50)
        close()
    }

    override fun close() {
        running = false
        ticker.close()
        runCatching { hub.closeAll() }
        runCatching { server.close() }
        for (s in ArrayList(sockets)) runCatching { s.close() }
    }

    companion object {
        private val GAME_PATHS = setOf("/play/ws", "/play/act", "/play/events", "/play/state")

        /** Banque du service : les questions LIBRES intégrées + les lots LIBRES `.quiz.zip` du dossier en lecture seule. Un lot `-reserved-` n'est JAMAIS lu ici (les réservées passent par `ReservedBank` et `CASTBRIDGE_PLAY_RESERVED_DIR`, gelées par `reserved-ids.json`). Un lot illisible est ignoré. */
        fun loadBank(dir: File?): QuizBank {
            var bank = EmbeddedQuestionSource(levels = null).bank()
            val files = dir?.takeIf { it.isDirectory }?.listFiles { f -> f.isFile && f.name.endsWith(QUIZ_PACK_SUFFIX) && !f.name.contains("-reserved-") }?.sortedBy { it.name }.orEmpty()
            for (f in files) runCatching { bank = bank.merge(QuizLotFormat.read(f).bank) }
            return bank
        }
    }
}
