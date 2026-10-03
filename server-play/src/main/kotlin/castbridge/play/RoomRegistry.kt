package castbridge.play

import castbridge.core.owner.RevocationState
import castbridge.core.quiz.QuizBank
import castbridge.core.quiz.online.BadCodeCounter
import castbridge.core.quiz.online.ClientMsg
import castbridge.core.quiz.online.PlayCodec
import castbridge.core.quiz.online.PlayProtocol
import castbridge.core.quiz.online.HostEdition
import castbridge.core.quiz.online.PlayReason
import castbridge.core.quiz.online.PlayRules
import castbridge.core.quiz.online.PlayScope
import castbridge.core.quiz.online.RoomCode
import castbridge.core.quiz.online.ServerMsg
import castbridge.core.quiz.online.ServerRoom
import castbridge.play.entitlement.DayCounter
import castbridge.play.entitlement.HostRightsEvaluator
import castbridge.play.entitlement.RateWindow
import castbridge.play.entitlement.ReservedBank
import castbridge.play.entitlement.TicketVerifier
import castbridge.play.entitlement.TrustedIssuers
import castbridge.play.entitlement.UsedTickets
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** Une connexion de jeu, quel que soit le transport (WebSocket, SSE + POST, long-poll). Le transport n'a aucune logique de jeu : tout passe par [PlayHub]. */
abstract class PlayConn(val id: String, val ip: String, rate: Int, burst: Int) {
    internal val bucket = TokenBucket(rate, burst)
    @Volatile internal var entry: RoomEntry? = null
    @Volatile var ticket: String? = null
    internal val closed = AtomicBoolean(false)
    @Volatile var lastInboundMs = System.currentTimeMillis()

    /** Met un message serveur en file de sortie ; false = file pleine (la connexion est alors fermée par le hub). */
    abstract fun offer(text: String): Boolean
    /** Ferme la connexion (code WebSocket, ou rupture de la session de repli). */
    abstract fun close(code: Int, reason: String)
    /** Appelée à chaque tick : pings, délais, inactivité. */
    open fun housekeeping(nowMs: Long) {}
}

/** Une salle hébergée : la logique est `ServerRoom` du cœur ; ici seulement les connexions et les dates d'activité. */
class RoomEntry(val room: ServerRoom, /** Appareil attesté par le ticket qui a ouvert la salle (plafond de salles par sujet). */ val subject: String = "", /** Identité d'activation (code d'appareil signé) de l'hôte : un 2e compte du plafond de salles, que les faux appareils API ne contournent pas. */ val identity: String? = null) {
    internal val conns = ConcurrentHashMap<String, PlayConn>()
    /** Horloge RÉELLE (ms) : la salle peut tourner sur l'horloge de test, la purge jamais. */
    val createdRealMs: Long = System.currentTimeMillis()
    @Volatile var lastActiveRealMs: Long = createdRealMs
}

/**
 * Registre des salles et aiguillage des messages (une seule logique, tous transports) : `hello` (ticket), `create` (ticket valide + plafond de salles),
 * `join` par code, `resume` par identifiant de salle, puis tout le reste vers la salle de la connexion. Les sorties d'une salle sont distribuées sous
 * le verrou de la salle, ce qui garde l'ordre des messages de chaque client. Purge : 10 min sans connexion, 2 h de vie.
 */
class PlayHub(
    private val cfg: PlayConfig,
    private val clock: () -> Long,
    private val bank: QuizBank,
    private val verifier: TicketVerifier,
    private val random: () -> java.util.Random = { SecureRandom() },
    private val scope: PlayScope = PlayScope.INTERNET,
    private val settings: ServerRoom.Settings = ServerRoom.Settings(),
    private val limits: ConnectionLimits,
    /** Banques réservées (w20-04) : sans elle, toutes les salles jouent la [bank] libre. */
    private val reserved: ReservedBank? = null,
    /** Révocations courantes (liste signée relue toutes les 15 min par [castbridge.play.entitlement.RevocationsFeed]). */
    revocations: () -> RevocationState = { RevocationState() },
    /** Faux tant que les révocations ne sont pas connues (aucune liste acceptée, ou plus de 24 h) : `create` est refusé (fermé). */
    private val revocationsReady: () -> Boolean = { true },
) {
    private val rooms = ConcurrentHashMap<String, RoomEntry>()
    private val evaluator = HostRightsEvaluator(TrustedIssuers.parse(cfg.trustedKeys.joinToString(",")).ring, revocations)
    private val used = UsedTickets(cfg.maxUsedTickets)
    private val createRate = RateWindow(cfg.createsPerIpPerHour, 3_600_000L)
    private val trialDays = DayCounter()
    private val identityDays = DayCounter()
    private val create48 = RateWindow(cfg.createsPer48PerHour, 3_600_000L)
    @Volatile private var lastSweepMs = 0L
    private val all = ConcurrentHashMap<String, PlayConn>()
    private val roomLock = Any()
    @Volatile private var draining = false
    private val badCodes = BadCodeCounter()
    private val badCodes48 = BadCodeCounter(perIpMax = MAX_BAD_CODES_PER_48)   // un /48 IPv6 contient 65 536 /64
    private val rnd = random()

    fun rooms(): List<ServerRoom> = rooms.values.map { it.room }
    fun roomCount() = rooms.size
    fun connectionCount() = all.size

    /** Enregistre une connexion neuve (le plafond a déjà été pris par l'appelant dans [ConnectionLimits]). */
    fun register(c: PlayConn) { all[c.id] = c }

    /** Un message du client. Faux = débit dépassé : la connexion est fermée (WebSocket : 1008 ; repli : session rompue, HTTP 429). */
    fun onText(c: PlayConn, text: String): Boolean {
        if (c.closed.get()) return false
        c.lastInboundMs = System.currentTimeMillis()
        if (!c.bucket.take()) { c.close(1008, "trop de messages"); onClosed(c); return false }
        when (val d = PlayCodec.decodeClient(text)) {
            is PlayCodec.Decoded.Bad -> send(c, ServerMsg.Error(0, d.reason, d.detail, false))
            is PlayCodec.Decoded.Ok -> dispatch(c, d.msg, clock())
        }
        return true
    }

    private fun dispatch(c: PlayConn, msg: ClientMsg, now: Long) {
        val entry = c.entry
        when {
            msg is ClientMsg.Hello -> {
                if (msg.proto != PlayProtocol.PROTO) send(c, ServerMsg.Error(0, PlayProtocol.UNSUPPORTED, "Cette version du jeu en ligne n'est pas prise en charge : mettez CastBridge à jour.", false))
                else if (msg.ticket != null) c.ticket = msg.ticket
            }
            entry != null -> forward(entry, c, msg, now)
            msg is ClientMsg.Create -> create(c, msg, now)
            msg is ClientMsg.Join -> join(c, msg, now)
            msg is ClientMsg.Resume -> resume(c, msg, now)
            else -> send(c, ServerMsg.Error(0, PlayProtocol.FORBIDDEN, "Rejoignez d'abord une salle.", false))
        }
    }

    /**
     * Ouvre une salle. Ordre (le plus bon marché d'abord, FERMÉ à chaque pas) : ticket `cbp1` valide (signature, `aud`, `exp`, appareil non bloqué), maintenance, plafond de
     * créations par adresse, USAGE UNIQUE du `jti` (un ticket brûlé par le premier essai, même refusé ensuite : l'API en redonne 20 par heure), preuves de la TV évaluées par le
     * SERVICE (`HostRights`), règles commerciales (`PlayRules`), puis plafonds de salles (par appareil attesté, global). Le ticket vit en temps RÉEL, même si les salles tournent
     * sur une horloge de test.
     */
    private fun create(c: PlayConn, m: ClientMsg.Create, now: Long) {
        val real = System.currentTimeMillis()
        val ticket = (verifier.check(c.ticket, real) as? TicketVerifier.Result.Ok)?.ticket
        if (ticket == null) { send(c, err(PlayReason.PLAY_TICKET_REFUSED)); return }
        if (draining) { send(c, ServerMsg.Error(0, MAINTENANCE, "Le serveur de jeu est en maintenance : réessayez dans quelques minutes.", true)); return }
        // fermé : tant que les révocations ne sont pas connues (aucune liste acceptée, ou plus de 24 h), aucune salle ; le ticket n'est pas brûlé
        if (!revocationsReady()) { send(c, ServerMsg.Error(0, MAINTENANCE, "Service indisponible : réessayez dans quelques minutes.", true)); return }
        if (!createRate.allow(c.ip, real) || !create48.allow(ClientIp.group48(c.ip) ?: c.ip, real)) { send(c, ServerMsg.Error(0, BUSY, "Trop de parties ouvertes depuis cette adresse : réessayez dans une heure.", true)); return }
        when (used.use(ticket.jti, ticket.exp, real)) {
            UsedTickets.Use.REPLAY -> { send(c, err(PlayReason.PLAY_TICKET_REFUSED)); return }
            UsedTickets.Use.FULL -> { send(c, ServerMsg.Error(0, BUSY, "Le service de jeu est très sollicité. Réessayez dans une minute.", true)); return }
            UsedTickets.Use.OK -> {}
        }
        val rights = evaluator.evaluate(ticket.deviceCode, listOfNotNull(m.activation) + m.rentals, real)
        val trial = rights.edition == HostEdition.TRIAL
        val verdict = PlayRules.canCreate(rights.actor, publicRoom = false, gamesToday = if (trial) trialDays.count(rights.identity ?: "", real) else 0)
        if (!verdict.allowed) {
            val why = verdict.reason ?: PlayReason.PLAY_SCOPE_FORBIDDEN
            send(c, ServerMsg.Error(0, why.code, if (rights.edition == HostEdition.NONE) rights.note else verdict.message, why.retryable)); return
        }
        val subjectCap = if (trial) minOf(1, cfg.maxRoomsPerSubject) else cfg.maxRoomsPerSubject
        val subjectFull = ServerMsg.Error(0, BUSY, "Vous avez déjà $subjectCap partie${if (subjectCap > 1) "s" else ""} ouverte${if (subjectCap > 1) "s" else ""} : terminez-en une avant d'en ouvrir une autre.", true)
        val identity = rights.identity
        fun openRooms() = rooms.values.count { it.subject == ticket.deviceId || (identity != null && it.identity == identity) }   // l'appareil API se recrée à volonté : l'activation signée compte aussi
        if (openRooms() >= subjectCap) { send(c, subjectFull); return }
        // le lot réservé se lit hors verrou (première lecture : un zip) ; sans droit : la banque libre PARTAGÉE
        val roomBank = if (rights.coveredScopes.isEmpty() || reserved == null) bank else reserved.bankFor(rights.coveredScopes)
        val id = ByteArray(16).also { rnd.nextBytes(it) }.joinToString("") { "%02x".format(it) }
        // une salle d'essai : 8 joueurs au plus (PlayRules.TRIAL_MAX_PLAYERS)
        val roomSettings = if (trial) settings.copy(seatsPerTable = minOf(settings.seatsPerTable, PlayRules.TRIAL_MAX_PLAYERS)) else settings
        var refusal: ServerMsg.Error? = null
        val e = synchronized(roomLock) {
            when {
                rooms.size >= cfg.maxRooms -> { refusal = ServerMsg.Error(0, BUSY, "Le service de jeu est très sollicité. Réessayez dans une minute.", true); null }
                openRooms() >= subjectCap -> { refusal = subjectFull; null }
                trial && !trialDays.record(identity ?: "", real) -> { refusal = ServerMsg.Error(0, BUSY, "Le service de jeu est très sollicité. Réessayez dans une minute.", true); null }
                !trial && (identityDays.count(identity ?: "", real) >= cfg.createsPerIdentityPerDay || !identityDays.record(identity ?: "", real)) -> {
                    refusal = ServerMsg.Error(0, BUSY, "Trop de parties ouvertes aujourd'hui avec cette activation : réessayez demain.", true); null }
                else -> RoomEntry(ServerRoom(id, scope, roomBank, rnd, createdAt = now, settings = roomSettings), ticket.deviceId, identity).also { rooms[id] = it }
            }
        }
        if (e == null) { send(c, refusal!!); return }
        attachAndForward(e, c, m, now)
    }

    /** Nombre de `jti` mémorisés (tests, santé). */
    fun usedTicketCount(): Int = used.size()
    fun sweepUsedTickets(realNow: Long) { used.sweep(realNow); createRate.sweep(realNow); create48.sweep(realNow) }

    private fun join(c: PlayConn, m: ClientMsg.Join, now: Long) {
        if (codesBlocked(c.ip, now)) { send(c, err(PlayReason.PLAY_BAD_CODE)); return }
        val typed = RoomCode.normalize(m.code)
        val e = typed?.let { t -> rooms.values.firstOrNull { it.room.code == t } }
        if (e == null) { codeFailed(c.ip, now); send(c, err(PlayReason.PLAY_BAD_CODE)); return }
        attachAndForward(e, c, m, now)
    }

    private fun resume(c: PlayConn, m: ClientMsg.Resume, now: Long) {
        // jamais de blocage ni de compte d'échecs par adresse sur un resume (NAT collectif) : le seau de débit suffit, le jeton fait 128 bits
        val e = rooms[m.roomId]
        if (e == null) { send(c, err(PlayReason.PLAY_BAD_CODE)); return }
        attachAndForward(e, c, m, now)
    }

    /** Attache la connexion à la salle le temps d'un message d'entrée ; sans `welcome` en retour, elle est détachée (et la salle neuve supprimée). */
    private fun attachAndForward(e: RoomEntry, c: PlayConn, m: ClientMsg, now: Long) {
        val overflow = ArrayList<PlayConn>()
        synchronized(e) {
            e.conns[c.id] = c; c.entry = e
            val outs = e.room.handle(c.id, m, now, c.ip)
            e.lastActiveRealMs = System.currentTimeMillis()
            deliver(e, outs, overflow)   // d'abord les réponses (un refus doit atteindre la connexion), puis détachement si elle n'est pas entrée
            if (outs.none { it.to == c.id && it.msg is ServerMsg.Welcome }) {
                e.conns.remove(c.id); c.entry = null
                if (m is ClientMsg.Create) rooms.remove(e.room.roomId)
            }
        }
        overflow.forEach { dropOverflow(it) }
    }

    private fun forward(e: RoomEntry, c: PlayConn, m: ClientMsg, now: Long) {
        val overflow = ArrayList<PlayConn>()
        synchronized(e) {
            e.lastActiveRealMs = System.currentTimeMillis()
            deliver(e, e.room.handle(c.id, m, now, c.ip), overflow)
        }
        overflow.forEach { dropOverflow(it) }
    }

    private fun deliver(e: RoomEntry, outs: List<ServerRoom.Out>, overflow: MutableList<PlayConn>) {
        for (o in outs) {
            val t = e.conns[o.to] ?: continue
            if (!t.offer(PlayCodec.encode(o.msg))) { if (!overflow.contains(t)) overflow += t }
        }
    }

    private fun dropOverflow(c: PlayConn) { c.close(1008, "file de sortie pleine"); onClosed(c) }

    private fun send(c: PlayConn, m: ServerMsg) { if (!c.offer(PlayCodec.encode(m))) dropOverflow(c) }
    private fun err(r: PlayReason) = ServerMsg.Error(0, r.code, r.message, r.retryable)

    /** La connexion est tombée : la place est gardée (reprise par `resume`, 10 min). Idempotent. */
    fun onClosed(c: PlayConn) {
        if (!c.closed.compareAndSet(false, true)) return
        all.remove(c.id)
        limits.release(c.ip)
        val e = c.entry ?: return
        val overflow = ArrayList<PlayConn>()
        synchronized(e) {
            e.conns.remove(c.id)
            deliver(e, e.room.disconnect(c.id, clock()), overflow)
        }
        overflow.forEach { dropOverflow(it) }
    }

    /** Appelé toutes les [PlayConfig.tickMs] : horloge des salles, purge, pings et inactivité des connexions. */
    fun tick() {
        val now = clock()
        val mono = System.currentTimeMillis()
        for (e in ArrayList(rooms.values)) {   // copie sûre d'une table concurrente (toList() peut échouer si une entrée part pendant la copie)
            val overflow = ArrayList<PlayConn>()
            synchronized(e) {
                if (e.room.phase() != ServerRoom.State.GONE) {
                    val tooOld = mono - e.createdRealMs >= cfg.roomMaxMs
                    if (e.conns.isEmpty() && mono - e.lastActiveRealMs >= cfg.roomIdleMs || tooOld) {
                        deliver(e, e.room.close(if (tooOld) "EXPIRED" else "IDLE", now), overflow)
                    } else deliver(e, e.room.tick(now), overflow)
                }
                if (e.room.phase() == ServerRoom.State.GONE) { rooms.remove(e.room.roomId); e.conns.values.forEach { it.entry = null }; e.conns.clear() }
            }
            overflow.forEach { dropOverflow(it) }
        }
        synchronized(badCodes) { badCodes.sweep(now) }; synchronized(badCodes48) { badCodes48.sweep(now) }
        if (mono - lastSweepMs >= 5_000L) { lastSweepMs = mono; sweepUsedTickets(mono) }
        for (c in ArrayList(all.values)) c.housekeeping(mono)
    }

    /**
     * Arrêt annoncé (SIGTERM, déploiement) : plus aucune salle neuve (`PLAY_MAINTENANCE`), et les salles ouvertes sont prévenues ; la partie en cours continue pendant le délai de grâce.
     * À faire de préférence HORS PARTIE : le service ne migre pas les salles (docs/PLAY-OPS-REQUIREMENTS.md).
     */
    fun startDrain() {
        if (draining) return
        draining = true
        val note = ServerMsg.Error(0, MAINTENANCE, "Le serveur de jeu va redémarrer pour maintenance : terminez la partie en cours, puis reconnectez-vous dans quelques minutes.", true)
        val text = PlayCodec.encode(note)
        for (e in ArrayList(rooms.values)) for (c in ArrayList(e.conns.values)) c.offer(text)
    }

    fun connectionsOpen(): Int = all.size

    fun closeAll() {
        for (c in ArrayList(all.values)) { c.close(1001, "arrêt du service"); onClosed(c) }
    }

    private fun codesBlocked(ip: String, now: Long) = badCodes.synchronizedBlocked(ip, now) || (ClientIp.group48(ip)?.let { badCodes48.synchronizedBlocked(it, now) } ?: false)
    private fun codeFailed(ip: String, now: Long) { badCodes.synchronizedFail(ip, now); ClientIp.group48(ip)?.let { badCodes48.synchronizedFail(it, now) } }

    companion object { const val BUSY = "PLAY_BUSY"; const val MAINTENANCE = "PLAY_MAINTENANCE"; const val MAX_BAD_CODES_PER_48 = 120 }
}

private fun BadCodeCounter.synchronizedBlocked(ip: String, now: Long) = synchronized(this) { ipBlocked(ip, now) }
private fun BadCodeCounter.synchronizedFail(ip: String, now: Long) = synchronized(this) { ipFail(ip, now) }
