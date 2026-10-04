package castbridge.play

import castbridge.play.entitlement.TicketVerifier
import java.io.IOException
import java.io.OutputStream
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Session de repli (SSE ou long-poll) : une file de messages serveur bornée (64 Ko, comptés en OCTETS UTF-8) que le client vide soit par un flux SSE (les messages sont retirés
 * à l'envoi), soit par le long-poll (retirés quand le client les accuse par `since`). Même codec, même hub que le WebSocket. Sans flux ni requête depuis `fallbackIdleMs` : fermée.
 * Attentes par verrou et condition explicites (pas de `synchronized` + `wait` : un fil virtuel qui attend sous `synchronized` épingle son porteur, JEP 444).
 */
class FbConn(id: String, ip: String, private val cfg: PlayConfig, private val owner: PlayFallbackController) : PlayConn(id, ip, cfg.ratePerSec, cfg.burst) {
    class Item(val idx: Long, val text: String, val bytes: Int)

    private val lock = ReentrantLock()
    private val changed = lock.newCondition()
    private val backlog = ArrayDeque<Item>()
    private var bytes = 0
    private var nextIdx = 1L
    @Volatile var dead = false; private set
    @Volatile var lastSeenMs = System.currentTimeMillis()
    @Volatile private var stream: Socket? = null
    @Volatile var streamGen = 0; private set

    /** Le `state` encore en file (jamais livré ou pas encore accusé) : le prochain `state` le REMPLACE (coalescence « dernier état seulement », w20-04b). */
    private var pendingState: Item? = null

    override fun offer(text: String): Boolean = lock.withLock {
        if (dead) return true
        val n = text.toByteArray(Charsets.UTF_8).size
        // Coalescence : une vue `state` est COMPLÈTE, un seul état en file suffit (liaison EDGE à 40 kbps : une rafale de réponses ne doit pas mettre en file 8 états devant la révélation).
        // Seul `state` est remplaçable (question, reveal, ack, ping, error, roomGone, replay, welcome, safety : jamais retirés ni réordonnés) ; le nouveau prend la place EN FIN de file,
        // les indices restent croissants (un trou est permis).
        val isState = text.startsWith(PlayFallbackController.STATE_PREFIX)
        if (isState) pendingState?.let { old -> if (backlog.remove(old)) bytes -= old.bytes }
        val item = Item(nextIdx++, text, n)
        backlog.addLast(item); bytes += n
        pendingState = if (isState) item else pendingState
        if (bytes > cfg.outboxMaxBytes) return false
        changed.signalAll(); true
    }

    override fun close(code: Int, reason: String) {
        lock.withLock { dead = true; changed.signalAll() }
        owner.forget(this)
        runCatching { stream?.close() }
    }

    override fun housekeeping(nowMs: Long) {
        if (!dead && nowMs - lastSeenMs >= cfg.fallbackIdleMs) { close(1001, "inactive"); owner.hub.onClosed(this) }
    }

    fun touch() { lastSeenMs = System.currentTimeMillis() }

    /** Nouveau flux SSE : l'ancien (s'il y en a un) s'arrête. */
    fun attachStream(s: Socket): Int = lock.withLock { streamGen++; runCatching { stream?.close() }; stream = s; changed.signalAll(); streamGen }

    /** SSE : attend jusqu'à [waitMs] des messages et les RETIRE. Vide = rien reçu (envoyer un commentaire de maintien). */
    fun drain(gen: Int, waitMs: Long): List<Item> = lock.withLock {
        var left = TimeUnit.MILLISECONDS.toNanos(waitMs)
        while (!dead && streamGen == gen && backlog.isEmpty() && left > 0) left = changed.awaitNanos(left)
        if (dead || streamGen != gen) return emptyList()
        val out = backlog.toList(); backlog.clear(); bytes = 0; out
    }

    /** Long-poll : accuse `since`, attend jusqu'à [waitMs] des messages d'indice > since, les rend SANS les retirer (retirés à l'accusé suivant). Null = session morte. */
    /** Numéro du long-poll en cours : un NOUVEAU long-poll termine l'ancien (comme `attachStream` pour le flux), une session n'en tient jamais qu'un (H-4). */
    private var pollGen = 0

    fun poll(since: Long, waitMs: Long): List<Item>? = lock.withLock {
        val g = ++pollGen; changed.signalAll()
        while (backlog.isNotEmpty() && backlog.first().idx <= since) { bytes -= backlog.removeFirst().bytes }
        var left = TimeUnit.MILLISECONDS.toNanos(waitMs)
        while (!dead && backlog.isEmpty() && pollGen == g && left > 0) left = changed.awaitNanos(left)
        if (dead) return null
        if (pollGen != g) return emptyList()   // remplacé par un long-poll plus récent
        backlog.toList()
    }

    fun isDeadNow() = dead
}

/**
 * Routes de repli, sous `/play/` comme le WebSocket : `POST /play/act` (un message client par requête), `GET /play/events` (flux SSE, un évènement par message serveur,
 * `event:` = type du message), `GET /play/state?since=<n>` (long-poll 25 s).
 *
 * Audit Opus de w20-03 (B5) : le secret de la session de repli (128 bits) n'est JAMAIS dans une adresse ni dans le corps d'une réponse : le service le pose dans un cookie
 * `__Host-cbp-<nonce>` (un cookie PAR ONGLET, `HttpOnly; Secure; SameSite=Strict; Path=/`) à la création de la session ; les clients sans cookie (CastBridge-TV, CastBridge) le renvoient dans l'en-tête
 * `X-Play-Conn`. Un secret inconnu ou périmé : 410 et cookie effacé.
 */
class PlayFallbackController(private val cfg: PlayConfig, val hub: PlayHub, private val limits: ConnectionLimits, private val verifier: TicketVerifier,
                             private val origin: OriginCheck) {
    private val conns = ConcurrentHashMap<String, FbConn>()
    private val rnd = SecureRandom()
    /** Sockets TENUES (flux SSE et long-polls) par adresse (/64 en IPv6) : plafond `maxHeldPerAddress` (H-4). */
    private val held = ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicInteger>()
    private fun holdTake(ip: String): Boolean { val n = held.computeIfAbsent(ip) { java.util.concurrent.atomic.AtomicInteger() }; if (n.incrementAndGet() > cfg.maxHeldPerAddress) { holdDrop(ip); return false }; return true }
    private fun holdDrop(ip: String) { held.computeIfPresent(ip) { _, v -> if (v.decrementAndGet() <= 0) null else v } }
    private fun tooManyHeld(out: OutputStream) = MiniHttp.json(out, 429, """{"error":"trop de connexions tenues depuis cette adresse"}""", mapOf("Retry-After" to "5"))

    fun forget(c: FbConn) { conns.remove(c.id) }
    fun count() = conns.size

    /** Nom du cookie de CET onglet : `__Host-cbp-<nonce>` (nonce non secret choisi par la page, paramètre `tab`) ; sans nonce valide, `__Host-cbp`. Préfixe `__Host-` : Secure, Path=/, sans Domain. */
    private fun cookieName(req: HttpReq): String = req.query["tab"]?.takeIf { NONCE.matches(it) }?.let { "$COOKIE-$it" } ?: COOKIE
    private fun cred(req: HttpReq): String? = req.cookie(cookieName(req)) ?: req.header("x-play-conn")?.trim()?.takeIf { it.isNotEmpty() }
    private fun find(req: HttpReq): FbConn? = cred(req)?.let { conns[it] }?.takeIf { !it.isDeadNow() }
    private fun gone(req: HttpReq, out: OutputStream, msg: String) = MiniHttp.json(out, 410, """{"error":"$msg"}""", mapOf("Set-Cookie" to "${cookieName(req)}=; Max-Age=0; HttpOnly; Secure; SameSite=Strict; Path=/"))

    /** `POST /play/act` */
    fun act(req: HttpReq, out: OutputStream, ip: String) {
        val ticketHdr = req.header("x-play-ticket")
        // M-6 : le ticket est jugé à la CRÉATION de la session ; ensuite son secret de 128 bits fait foi (un ticket échu en cours de partie ne la coupe pas)
        val existing = find(req)
        val allowed = if (existing != null) origin.allowsRead(req.header("origin")) else origin.allows(req.header("origin"), verifier.verify(ticketHdr, System.currentTimeMillis()))
        if (!allowed) return MiniHttp.json(out, 403, """{"error":"origine refusée"}""")
        val body = req.body(PlayProtocolLimits.MAX_BODY)
            ?: return MiniHttp.json(out, if (req.header("content-length") == null) 411 else 413, """{"error":"corps absent ou trop gros"}""")
        var c = existing
        var created = false
        if (c == null) {
            if (cred(req) != null) return gone(req, out, "connexion terminée : reprenez avec resume")
            val admission = limits.admit(ip)
            when (admission.verdict) {
                ConnectionLimits.Verdict.RATE -> return MiniHttp.json(out, 429, """{"error":"trop de connexions : réessayez dans un instant","retryAfterMs":${admission.retryAfterMs}}""", mapOf("Retry-After" to ((admission.retryAfterMs + 999) / 1000).coerceAtLeast(1).toString()))
                ConnectionLimits.Verdict.IP_FULL -> return MiniHttp.json(out, 429, """{"error":"trop de connexions depuis cette adresse"}""", mapOf("Retry-After" to "10"))
                ConnectionLimits.Verdict.TOTAL_FULL -> return MiniHttp.json(out, 503, """{"error":"service complet"}""", mapOf("Retry-After" to "30"))
                ConnectionLimits.Verdict.OK -> {}
            }
            var registered = false
            try {
                val id = ByteArray(16).also { rnd.nextBytes(it) }.joinToString("") { "%02x".format(it) }
                c = FbConn(id, ip, cfg, this).also { conns[id] = it; it.ticket = ticketHdr }
                hub.register(c); registered = true; created = true
            } finally { if (!registered) { c?.let { conns.remove(it.id) }; limits.release(ip) } }
        }
        c!!.touch()
        if (!hub.onText(c, String(body, Charsets.UTF_8))) return MiniHttp.json(out, 429, """{"error":"trop de messages"}""", mapOf("Set-Cookie" to "${cookieName(req)}=; Max-Age=0; HttpOnly; Secure; SameSite=Strict; Path=/"))
        MiniHttp.json(out, 200, """{"ok":true}""", if (created) mapOf("Set-Cookie" to "${cookieName(req)}=${c.id}; Max-Age=7200; HttpOnly; Secure; SameSite=Strict; Path=/") else emptyMap())
    }

    /** `GET /play/events` : flux SSE jusqu'à la fin de la session ou le départ du client. */
    fun events(req: HttpReq, socket: Socket, out: OutputStream, ip: String) {
        if (!origin.allowsRead(req.header("origin"))) return MiniHttp.json(out, 403, """{"error":"origine refusée"}""")
        val c = find(req) ?: return gone(req, out, "connexion inconnue ou terminée")
        socket.soTimeout = 0
        if (!holdTake(ip)) return tooManyHeld(out)
        val gen = c.attachStream(socket)
        try {
        out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream; charset=utf-8\r\nCache-Control: no-store\r\nX-Accel-Buffering: no\r\nConnection: close\r\n" +
            "X-Content-Type-Options: nosniff\r\nReferrer-Policy: no-referrer\r\n\r\nretry: 2000\n\n").toByteArray(Charsets.ISO_8859_1))
        out.flush()
            while (c.streamGen == gen && !c.isDeadNow()) {
                c.touch()
                val batch = c.drain(gen, cfg.pollMs)
                val sb = StringBuilder()
                if (batch.isEmpty()) sb.append(": ping\n\n")
                for (m in batch) sb.append("id: ").append(m.idx).append("\nevent: ").append(typeOf(m.text)).append("\ndata: ").append(m.text).append("\n\n")
                out.write(sb.toString().toByteArray(Charsets.UTF_8)); out.flush()
            }
        } catch (_: IOException) {} finally { holdDrop(ip) }
    }

    /** `GET /play/state?since=` : long-poll de 25 s ; `{"msgs":[…],"next":n}`. */
    fun state(req: HttpReq, out: OutputStream, ip: String) {
        if (!origin.allowsRead(req.header("origin"))) return MiniHttp.json(out, 403, """{"error":"origine refusée"}""")
        val c = find(req) ?: return gone(req, out, "connexion inconnue ou terminée")
        val since = req.query["since"]?.toLongOrNull() ?: 0L
        c.touch()
        if (!holdTake(ip)) return tooManyHeld(out)
        val items = try { c.poll(since, cfg.pollMs) } finally { holdDrop(ip) } ?: return gone(req, out, "connexion terminée")
        c.touch()
        val next = items.lastOrNull()?.idx ?: since
        MiniHttp.json(out, 200, """{"msgs":[${items.joinToString(",") { it.text }}],"next":$next}""")
    }

    private fun typeOf(json: String): String = TYPE.find(json)?.groupValues?.get(1) ?: "message"

    companion object {
        const val COOKIE = "__Host-cbp"
        /** Préfixe d'un message `state` (le type se lit sans analyser le JSON). */
        internal const val STATE_PREFIX = "{\"t\":\"state\""
        private val NONCE = Regex("^[0-9a-f]{8,32}$")
        private val TYPE = Regex("^\\{\"t\":\"([a-zA-Z]{1,16})\"")
    }
}

/** Limites propres au repli HTTP. */
object PlayProtocolLimits { const val MAX_BODY = castbridge.core.quiz.online.PlayProtocol.MAX_CREATE_BYTES + 512 }   // M-1 : un create de la TV (activation) passe par ce POST
