package castbridge.play

import java.io.IOException
import java.io.OutputStream
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/**
 * Session de repli (SSE ou long-poll) : une file de messages serveur bornée (64 Ko) que le client vide soit par un flux SSE (les messages sont retirés à l'envoi),
 * soit par le long-poll (retirés quand le client les accuse par `since`). Même codec, même hub que le WebSocket. Sans flux ni requête depuis `fallbackIdleMs` : fermée.
 */
class FbConn(id: String, ip: String, private val cfg: PlayConfig, private val owner: PlayFallbackController) : PlayConn(id, ip, cfg.ratePerSec, cfg.burst) {
    class Item(val idx: Long, val text: String)

    private val lock = Object()
    private val backlog = ArrayDeque<Item>()
    private var bytes = 0
    private var nextIdx = 1L
    @Volatile var dead = false; private set
    @Volatile var lastSeenMs = System.currentTimeMillis()
    @Volatile private var stream: Socket? = null
    @Volatile var streamGen = 0; private set

    override fun offer(text: String): Boolean = synchronized(lock) {
        if (dead) return true
        backlog.addLast(Item(nextIdx++, text)); bytes += text.length
        if (bytes > cfg.outboxMaxBytes) return false
        lock.notifyAll(); true
    }

    override fun close(code: Int, reason: String) {
        synchronized(lock) { dead = true; lock.notifyAll() }
        owner.forget(this)
        runCatching { stream?.close() }
    }

    override fun housekeeping(nowMs: Long) {
        if (!dead && nowMs - lastSeenMs >= cfg.fallbackIdleMs) { close(1001, "inactive"); owner.hub.onClosed(this) }
    }

    fun touch() { lastSeenMs = System.currentTimeMillis() }

    /** Nouveau flux SSE : l'ancien (s'il y en a un) s'arrête. */
    fun attachStream(s: Socket): Int = synchronized(lock) { streamGen++; runCatching { stream?.close() }; stream = s; lock.notifyAll(); streamGen }

    /** SSE : attend jusqu'à [waitMs] des messages et les RETIRE. Vide = rien reçu (envoyer un commentaire de maintien). */
    fun drain(gen: Int, waitMs: Long): List<Item> = synchronized(lock) {
        val end = System.currentTimeMillis() + waitMs
        while (!dead && streamGen == gen && backlog.isEmpty()) { val left = end - System.currentTimeMillis(); if (left <= 0) break; lock.wait(left) }
        if (dead || streamGen != gen) return emptyList()
        val out = backlog.toList(); backlog.clear(); bytes = 0; out
    }

    /** Long-poll : accuse `since`, attend jusqu'à [waitMs] des messages d'indice > since, les rend SANS les retirer (retirés à l'accusé suivant). Null = session morte. */
    fun poll(since: Long, waitMs: Long): List<Item>? = synchronized(lock) {
        while (backlog.isNotEmpty() && backlog.first().idx <= since) { bytes -= backlog.removeFirst().text.length }
        val end = System.currentTimeMillis() + waitMs
        while (!dead && backlog.isEmpty()) { val left = end - System.currentTimeMillis(); if (left <= 0) break; lock.wait(left) }
        if (dead) return null
        backlog.toList()
    }

    fun isDeadNow() = dead
}

/**
 * Routes de repli, sous `/play/` comme le WebSocket : `POST /play/act` (un message client par requête, en-tête `X-Play-Conn` après le premier ; la réponse donne la connexion),
 * `GET /play/events?token=<conn>` (flux SSE, un évènement par message serveur, `event:` = type du message), `GET /play/state?token=<conn>&since=<n>` (long-poll 25 s).
 * `token` = secret de CONNEXION de repli (128 bits, vie courte), jamais le jeton de siège.
 */
class PlayFallbackController(private val cfg: PlayConfig, val hub: PlayHub, private val limits: ConnectionLimits, private val verifier: TicketVerifier,
                             private val origin: OriginCheck) {
    private val conns = ConcurrentHashMap<String, FbConn>()
    private val rnd = SecureRandom()

    fun forget(c: FbConn) { conns.remove(c.id) }
    fun count() = conns.size

    private fun find(token: String?): FbConn? = token?.let { conns[it] }?.takeIf { !it.isDeadNow() }

    /** `POST /play/act` */
    fun act(req: HttpReq, out: OutputStream, ip: String) {
        val ticketHdr = req.header("x-play-ticket")
        if (!origin.allows(req.header("origin"), verifier.verify(ticketHdr, System.currentTimeMillis()))) return MiniHttp.json(out, 403, """{"error":"origine refusée"}""")
        val body = req.body(PlayProtocolLimits.MAX_BODY)
            ?: return MiniHttp.json(out, if (req.header("content-length") == null) 411 else 413, """{"error":"corps absent ou trop gros"}""")
        var c = req.header("x-play-conn")?.let { find(it) ?: return MiniHttp.json(out, 410, """{"error":"connexion terminée : reprenez avec resume"}""") }
        if (c == null) {
            when (limits.acquire(ip)) {
                ConnectionLimits.Verdict.IP_FULL -> return MiniHttp.json(out, 429, """{"error":"trop de connexions depuis cette adresse"}""", mapOf("Retry-After" to "10"))
                ConnectionLimits.Verdict.TOTAL_FULL -> return MiniHttp.json(out, 503, """{"error":"service complet"}""", mapOf("Retry-After" to "30"))
                ConnectionLimits.Verdict.OK -> {}
            }
            val id = ByteArray(16).also { rnd.nextBytes(it) }.joinToString("") { "%02x".format(it) }
            c = FbConn(id, ip, cfg, this).also { conns[id] = it; hub.register(it); it.ticket = ticketHdr }
        }
        c.touch()
        if (!hub.onText(c, String(body, Charsets.UTF_8))) return MiniHttp.json(out, 429, """{"error":"trop de messages"}""")
        MiniHttp.json(out, 200, """{"conn":"${c.id}"}""")
    }

    /** `GET /play/events?token=` : flux SSE jusqu'à la fin de la session ou le départ du client. */
    fun events(req: HttpReq, socket: Socket, out: OutputStream) {
        if (!origin.allowsRead(req.header("origin"))) return MiniHttp.json(out, 403, """{"error":"origine refusée"}""")
        val c = find(req.query["token"]) ?: return MiniHttp.json(out, 410, """{"error":"connexion inconnue ou terminée"}""")
        socket.soTimeout = 0
        val gen = c.attachStream(socket)
        out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream; charset=utf-8\r\nCache-Control: no-store\r\nX-Accel-Buffering: no\r\nConnection: close\r\n" +
            "X-Content-Type-Options: nosniff\r\nReferrer-Policy: no-referrer\r\n\r\nretry: 2000\n\n").toByteArray(Charsets.ISO_8859_1))
        out.flush()
        try {
            while (c.streamGen == gen && !c.isDeadNow()) {
                c.touch()
                val batch = c.drain(gen, cfg.pollMs)
                val sb = StringBuilder()
                if (batch.isEmpty()) sb.append(": ping\n\n")
                for (m in batch) sb.append("id: ").append(m.idx).append("\nevent: ").append(typeOf(m.text)).append("\ndata: ").append(m.text).append("\n\n")
                out.write(sb.toString().toByteArray(Charsets.UTF_8)); out.flush()
            }
        } catch (_: IOException) {}
    }

    /** `GET /play/state?token=&since=` : long-poll de 25 s ; `{"msgs":[…],"next":n}`. */
    fun state(req: HttpReq, out: OutputStream) {
        if (!origin.allowsRead(req.header("origin"))) return MiniHttp.json(out, 403, """{"error":"origine refusée"}""")
        val c = find(req.query["token"]) ?: return MiniHttp.json(out, 410, """{"error":"connexion inconnue ou terminée"}""")
        val since = req.query["since"]?.toLongOrNull() ?: 0L
        c.touch()
        val items = c.poll(since, cfg.pollMs) ?: return MiniHttp.json(out, 410, """{"error":"connexion terminée"}""")
        c.touch()
        val next = items.lastOrNull()?.idx ?: since
        MiniHttp.json(out, 200, """{"msgs":[${items.joinToString(",") { it.text }}],"next":$next}""")
    }

    private fun typeOf(json: String): String = TYPE.find(json)?.groupValues?.get(1) ?: "message"

    companion object {
        private val TYPE = Regex("^\\{\"t\":\"([a-zA-Z]{1,16})\"")
    }
}

/** Limites propres au repli HTTP. */
object PlayProtocolLimits { const val MAX_BODY = 4_096 }
