package castbridge.play

import castbridge.core.chess.ClockMode
import castbridge.core.chess.online.ChessEscrow
import castbridge.core.chess.online.ChessServerRoom
import castbridge.core.owner.Ed25519Signer
import castbridge.core.owner.RevocationState
import castbridge.core.quiz.QuizBank
import castbridge.core.quiz.online.BadCodeCounter
import castbridge.core.quiz.online.ClientMsg
import castbridge.core.quiz.online.GameReason
import castbridge.core.quiz.online.PlayCodec
import castbridge.core.quiz.online.PlayProtocol
import castbridge.core.quiz.online.HostEdition
import castbridge.core.quiz.online.PlayReason
import castbridge.core.quiz.online.PlayRules
import castbridge.core.quiz.online.PlayScope
import castbridge.core.quiz.online.QuizEscrow
import castbridge.core.quiz.online.RoomCode
import castbridge.core.quiz.online.ServerMsg
import castbridge.core.quiz.online.StakeSpec
import castbridge.play.guard.InvalidTally
import castbridge.play.guard.PlayGuard
import castbridge.core.quiz.online.ServerRoom
import castbridge.play.entitlement.DayCounter
import castbridge.play.entitlement.HostRights
import castbridge.play.entitlement.HostRightsEvaluator
import castbridge.play.entitlement.RateWindow
import castbridge.play.entitlement.ReservedBank
import castbridge.play.entitlement.TicketVerifier
import castbridge.play.entitlement.TrustedIssuers
import castbridge.play.entitlement.UsedTickets
import castbridge.play.stake.EscrowGate
import castbridge.play.stake.ResultSpool
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
    /** Messages invalides de cette connexion (3 en une minute : fermeture 1008, w20-07). */
    internal val invalidTally = InvalidTally()
    /** (salle, appareil) du siège de joueur de cette connexion, pour que le plafond relevé des adresses partagées ne compte que des joueurs présents. */
    @Volatile internal var seatKey: Pair<String, String>? = null
    /** Salle de jeu à tour de rôle (échecs en ligne) où cette connexion est assise ; exclusive de [entry] (une connexion = une salle). */
    @Volatile internal var game: ChessEntry? = null

    /** Met un message serveur en file de sortie ; false = file pleine (la connexion est alors fermée par le hub). */
    abstract fun offer(text: String): Boolean
    /** Ferme la connexion (code WebSocket, ou rupture de la session de repli). */
    abstract fun close(code: Int, reason: String)
    /** Appelée à chaque tick : pings, délais, inactivité. */
    open fun housekeeping(nowMs: Long) {}
}

/** Une salle hébergée : la logique est `ServerRoom` du cœur ; ici seulement les connexions et les dates d'activité. */
class RoomEntry(val room: ServerRoom, /** Appareil attesté par le ticket qui a ouvert la salle (plafond de salles par sujet). */ val subject: String = "", /** Identité d'activation (code d'appareil signé) de l'hôte : un 2e compte du plafond de salles, que les faux appareils API ne contournent pas. */ val identity: String? = null, /** Salle d'essai : chaque `start` compte une partie du jour (M-3). */ val trial: Boolean = false) {
    /** Parties démarrées dans cette salle (la première est comptée à la création, les suivantes une à une : M-3). */
    internal val starts = java.util.concurrent.atomic.AtomicInteger()
    /** L'hôte a été assis au moins une fois : avant, la salle compte (course de création) ; après, seulement tant qu'il est connecté (M-2). */
    @Volatile internal var hostSeen = false
    internal fun counts(): Boolean = !hostSeen || room.hostConnected()
    internal val conns = ConcurrentHashMap<String, PlayConn>()
    /** Horloge RÉELLE (ms) : la salle peut tourner sur l'horloge de test, la purge jamais. */
    val createdRealMs: Long = System.currentTimeMillis()
    @Volatile var lastActiveRealMs: Long = createdRealMs
    /** Le journal a déjà dit que la partie n'est pas classée (une seule fois, w20-07). */
    @Volatile internal var unrankedLogged = false
    /** w20-04b : identités de TV (code d'appareil de l'activation) entrées par `join`, par connexion ; sert au plafond de connexions vivantes d'une identité. */
    internal val joined = ConcurrentHashMap<String, String>()
    /** Même identité, par jeton de siège : une reprise (`resume`) la rend à sa nouvelle connexion. */
    internal val joinedTokens = ConcurrentHashMap<String, String>()
}

/**
 * Une salle de jeu à tour de rôle hébergée (`game:chess`, games-G2) : la logique est `ChessServerRoom` du cœur ; ici seulement les connexions, l'identité de l'hôte et les dates d'activité.
 * Même rôle que [RoomEntry] pour le Quiz ; les deux vivent côte à côte dans [PlayHub] (un code de salle est unique sur les deux).
 */
class ChessEntry(val room: ChessServerRoom, /** Appareil attesté par le ticket qui a ouvert la salle (plafond de salles par sujet). */ val subject: String, /** Identité d'activation de l'hôte. */ val identity: String?, val trial: Boolean) {
    internal val conns = ConcurrentHashMap<String, PlayConn>()
    val createdRealMs: Long = System.currentTimeMillis()
    @Volatile var lastActiveRealMs: Long = createdRealMs
    /** L'hôte a été assis au moins une fois : avant, la salle compte (course de création) ; après, seulement tant qu'il est connecté. */
    @Volatile internal var hostSeen = false
    internal fun counts(): Boolean = !hostSeen || room.hostConnected()
    /** Identités de TV (code d'appareil) entrées par `join`, par connexion, et par jeton de siège (reprise) : plafond de connexions vivantes d'une identité. */
    internal val joined = ConcurrentHashMap<String, String>()
    internal val joinedTokens = ConcurrentHashMap<String, String>()
}

/** Ce dont le hub a besoin pour les parties misées : la porte des blocages `cbe1` (clés PUBLIQUES), la clé dédiée qui signe `cbr1`, le dépôt des résultats. Absent = mises suspendues, parties libres seules. */
class StakeServices(val gate: EscrowGate, val signer: Ed25519Signer, val spool: ResultSpool)

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
    /** Parties misées (games-G2) : null = mises suspendues (`STAKES_SUSPENDED`), les parties libres restent ouvertes. */
    private val stakes: StakeServices? = null,
) {
    private val rooms = ConcurrentHashMap<String, RoomEntry>()
    /** Salles de jeu à tour de rôle (échecs en ligne) : table distincte, MÊME verrou [roomLock], MÊMES plafonds de salles que le Quiz. */
    private val chessRooms = ConcurrentHashMap<String, ChessEntry>()
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
    private val badCodes = BadCodeCounter(perIpDayMax = MAX_BAD_CODES_PER_IP_PER_DAY)       // une adresse seule : 5 000 par jour (école, CGNAT)
    private val badPairs = BadCodeCounter()                                                  // la PAIRE adresse + appareil : 30 / 5 min, 300 / jour
    private val badCodes48 = BadCodeCounter(perIpMax = MAX_BAD_CODES_PER_48, perIpDayMax = Int.MAX_VALUE)   // un /48 IPv6 contient 65 536 /64
    private val badCodesIdentity = BadCodeCounter()                                          // une identité de TV : 30 / 5 min, 300 / jour (w20-04b)
    private val rnd = random()
    /** Gardes anti-abus (w20-07) : le service y met ses seaux et son journal ; par défaut, ceux d'un hub autonome. */
    @Volatile var guard = PlayGuard()

    /** Essais seulement : appelé entre le contrôle bon marché et le verrou de [tvJoin] (course du compteur d'essai). */
    @Volatile internal var afterJoinPrecheck: (() -> Unit)? = null

    fun rooms(): List<ServerRoom> = rooms.values.map { it.room }
    /** Les salles d'échecs en ligne (tests, santé). */
    fun chessRooms(): List<ChessServerRoom> = chessRooms.values.map { it.room }
    fun roomCount() = rooms.size + chessRooms.size
    /** Les mises sont-elles acceptées par CE hub (clés présentes, interrupteur actif) ? */
    fun stakesOn(): Boolean = stakes != null
    fun connectionCount() = all.size

    /** Enregistre une connexion neuve (le plafond a déjà été pris par l'appelant dans [ConnectionLimits]). */
    fun register(c: PlayConn) { all[c.id] = c }

    /** Un message du client. Faux = débit dépassé : la connexion est fermée (WebSocket : 1008 ; repli : session rompue, HTTP 429). */
    fun onText(c: PlayConn, text: String): Boolean {
        if (c.closed.get()) return false
        c.lastInboundMs = System.currentTimeMillis()
        if (!c.bucket.take()) { c.close(1008, "trop de messages"); onClosed(c); return false }
        when (val d = guard.decode(c, text)) {
            is PlayCodec.Decoded.Bad -> {
                send(c, ServerMsg.Error(0, d.reason, d.detail, false))
                if (d.reason == PlayProtocol.BAD_REQUEST && guard.tooManyInvalid(c)) { c.close(1008, "messages invalides"); onClosed(c); return false }
            }
            is PlayCodec.Decoded.Ok -> dispatch(c, d.msg, clock())
        }
        return true
    }

    private fun dispatch(c: PlayConn, msg: ClientMsg, now: Long) {
        guard.refuse(c, msg)?.let { send(c, it); return }   // pseudonyme interdit, débit d'entrée de l'appareil
        val entry = c.entry
        val gentry = c.game
        when {
            msg is ClientMsg.Hello -> {
                if (msg.proto != PlayProtocol.PROTO) send(c, ServerMsg.Error(0, PlayProtocol.UNSUPPORTED, "Cette version du jeu en ligne n'est pas prise en charge : mettez CastBridge à jour.", false))
                else if (msg.ticket != null) c.ticket = msg.ticket
            }
            entry != null -> forward(entry, c, msg, now)
            gentry != null -> forwardGame(gentry, c, msg, now)
            msg is ClientMsg.Create && msg.game != null -> createGame(c, msg, now)
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
        // Rien n'est CONSOMMÉ avant la fin des contrôles (audit final) : ni le quota de l'adresse (partagée par une classe d'élèves honnêtes), ni le `jti`. Lectures seules ici ;
        // la consommation a lieu au moment de créer la salle, ou quand la preuve de droits est fausse (alors le ticket est brûlé : on ne tâtonne pas avec un ticket).
        val ip48 = ClientIp.group48(c.ip) ?: c.ip
        if (used.seen(ticket.jti, real)) { send(c, err(PlayReason.PLAY_TICKET_REFUSED)); return }
        if (proofRefused(ticket, m.activation, m.proof)) { refuseProof(ticket, real, c); return }   // H-3 : une activation copiée sans la clé d'installation de la TV
        val tooMany = ServerMsg.Error(0, BUSY, "Trop de parties ouvertes depuis cette adresse : réessayez dans une heure.", true)
        if (!createRate.peek(c.ip, real) || !create48.peek(ip48, real)) { send(c, tooMany); return }
        val rights = evaluator.evaluate(ticket.deviceCode, listOfNotNull(m.activation) + m.rentals, real)
        val trial = rights.edition == HostEdition.TRIAL
        val verdict = PlayRules.canCreate(rights.actor, publicRoom = false, gamesToday = if (trial) trialDays.count(rights.identity ?: "", real) else 0)
        if (!verdict.allowed) {
            used.use(ticket.jti, ticket.exp, real)   // preuve de droits fausse ou refusée : le ticket est brûlé
            val why = verdict.reason ?: PlayReason.PLAY_SCOPE_FORBIDDEN
            send(c, ServerMsg.Error(0, why.code, if (rights.edition == HostEdition.NONE) rights.note else verdict.message, why.retryable)); return
        }
        val subjectCap = if (trial) minOf(1, cfg.maxRoomsPerSubject) else cfg.maxRoomsPerSubject
        val subjectFull = ServerMsg.Error(0, BUSY, "Vous avez déjà $subjectCap partie${if (subjectCap > 1) "s" else ""} ouverte${if (subjectCap > 1) "s" else ""} : terminez-en une avant d'en ouvrir une autre.", true)
        val identity = rights.identity
        // Quiz misé (games-G5) : un Duel dont l'hôte a bloqué sa mise (une mise par siège, jusqu'à 8 sièges). Le blocage `cbe1` est vérifié ICI (clé publique du portefeuille, identité de CETTE TV, mise de la
        // salle) et ne sert qu'à UNE salle ; l'essai ne mise jamais (le ticket n'est pas brûlé : la TV peut recommencer en partie libre). Sans mise ni blocage : salle libre, comme avant. Rien n'est consommé ici.
        var ok: StakeCheck.Ok? = null
        if (m.stake != null || m.escrow != null) {
            val who = identity ?: run { send(c, err(PlayReason.PLAY_SCOPE_FORBIDDEN)); return }
            if (m.mode != null && m.mode != "DUEL") { send(c, gerr(GameReason.STAKE_BAD)); return }   // la cagnotte se partage selon le classement : un Duel
            when (val check = stakeCheck(rights, who, m.stake, m.escrow, requireEscrow = true, real, PlayProtocol.GAME_QUIZ, PlayProtocol.MAX_STAKE_SEATS)) {
                is StakeCheck.Refused -> { send(c, check.error); return }
                is StakeCheck.Ok -> ok = check
                StakeCheck.Free -> {}
            }
        }
        // M-2 : une salle dont l'hôte est parti (« Quitter ») ne bloque plus son créateur ; elle vit encore pour ses invités
        fun openRooms() = openRoomsOf(ticket.deviceId, identity)   // l'appareil API se recrée à volonté : l'activation signée compte aussi
        if (openRooms() >= subjectCap) { send(c, subjectFull); return }
        // le lot réservé se lit hors verrou (première lecture : un zip) ; sans droit : la banque libre PARTAGÉE
        val roomBank = if (rights.coveredScopes.isEmpty() || reserved == null) bank else reserved.bankFor(rights.coveredScopes)
        val id = ByteArray(16).also { rnd.nextBytes(it) }.joinToString("") { "%02x".format(it) }
        // une salle d'essai : 8 joueurs au plus (PlayRules.TRIAL_MAX_PLAYERS)
        val baseSettings = settings.copy(maxRelayedPerTv = minOf(settings.maxRelayedPerTv, cfg.maxRelayedPerTv))
        val roomSettings = if (trial) baseSettings.copy(seatsPerTable = minOf(baseSettings.seatsPerTable, PlayRules.TRIAL_MAX_PLAYERS)) else baseSettings
        var refusal: ServerMsg.Error? = null
        // le blocage n'est rendu QUE s'il a été réservé par CET essai : un refus avant la réservation ne doit jamais libérer le blocage d'une autre salle (le même `cbe1` rejoué)
        var reservedHere = false
        fun release() { if (reservedHere) { ok?.let { stakes?.gate?.release(it.escrow.eid) }; reservedHere = false } }
        val e = synchronized(roomLock) {
            fun fail(error: ServerMsg.Error): RoomEntry? { release(); refusal = error; return null }
            when {
                rooms.size + chessRooms.size >= cfg.maxRooms -> fail(err(PlayReason.PLAY_BUSY))
                openRooms() >= subjectCap -> fail(subjectFull)
                !trial && identityDays.count(identity ?: "", real) >= cfg.createsPerIdentityPerDay ->
                    fail(ServerMsg.Error(0, BUSY, "Trop de parties ouvertes aujourd'hui avec cette activation : réessayez demain.", true))
                // les contrôles sont passés : on consomme maintenant (quota de l'adresse, `jti`, compteur du jour), puis on crée
                !createRate.allow(c.ip, real) || !create48.allow(ip48, real) -> fail(tooMany)
                ok != null && !stakes!!.gate.reserve(ok!!.escrow.eid, ok!!.expMs, real).also { reservedHere = it } -> fail(gerr(GameReason.STAKE_ESCROW_INVALID))
                else -> when (used.use(ticket.jti, ticket.exp, real)) {
                    UsedTickets.Use.REPLAY -> fail(err(PlayReason.PLAY_TICKET_REFUSED))
                    UsedTickets.Use.FULL -> fail(err(PlayReason.PLAY_BUSY))
                    UsedTickets.Use.OK -> when {
                        trial && !trialDays.record(identity ?: "", real) -> fail(err(PlayReason.PLAY_BUSY))
                        !trial && !identityDays.record(identity ?: "", real) -> fail(err(PlayReason.PLAY_BUSY))
                        else -> RoomEntry(ServerRoom(id, scope, roomBank, rnd, createdAt = now, settings = roomSettings,
                            stake = if (ok != null) m.stake else null, signer = stakes?.signer, onResult = { rid, token -> stakes?.spool?.write(rid, token) }),
                            ticket.deviceId, identity, trial).also { rooms[id] = it }
                    }
                }
            }
        }
        if (e == null) { send(c, refusal!!); return }
        if (attachAndForward(e, c, m, now, admission = ServerRoom.Admission(identity, ok?.quiz))) {
            e.hostSeen = true
            if (ok != null) guard.log.event("play.game.created", "", mapOf("roomId" to id, "ip" to c.ip, "game" to PlayProtocol.GAME_QUIZ, "staked" to true))
        } else release()
    }

    /** H-3 : la preuve de possession manque ou est fausse. Un ticket qui porte l'empreinte `ik` l'exige toujours ; sans `ik`, seul `requireProof` (défaut) la rend obligatoire. */
    private fun proofRefused(ticket: TicketVerifier.Ticket, activation: String?, proof: String?): Boolean {
        val ik = ticket.installHash
        if (ik == null) return cfg.requireProof
        val code = ticket.deviceCode ?: return true
        return activation == null || !castbridge.core.quiz.online.PlayProof.verify(proof, ticket.jti, code, activation, ik)
    }

    private fun refuseProof(ticket: TicketVerifier.Ticket, real: Long, c: PlayConn) {
        used.use(ticket.jti, ticket.exp, real)   // comme une preuve de droits fausse : le ticket est brûlé
        send(c, ServerMsg.Error(0, PlayReason.PLAY_SCOPE_FORBIDDEN.code, "Mettez CastBridge-TV à jour : cette TV ne prouve pas qu'elle est bien celle de l'activation.", false))
    }

    /** Nombre de `jti` mémorisés (tests, santé). */
    fun usedTicketCount(): Int = used.size()
    fun sweepUsedTickets(realNow: Long) { used.sweep(realNow); createRate.sweep(realNow); create48.sweep(realNow) }

    private fun join(c: PlayConn, m: ClientMsg.Join, now: Long) {
        if (!cfg.webPlay) { tvJoin(c, m, now); return }
        val typed = RoomCode.normalize(m.code)
        if (typed != null && chessRooms.values.any { it.room.code == typed }) { tvJoin(c, m, now); return }   // les échecs en ligne ne se jouent que par une TV : même chaîne que `webPlay` faux
        val e = typed?.let { t -> rooms.values.firstOrNull { it.room.code == t } }
        // Un code VALIDE n'est jamais refusé par un compteur de mauvais codes : derrière une adresse partagée (école, CGNAT), un élève malveillant ne doit pas fermer la porte aux autres.
        if (e != null) { attachAndForward(e, c, m, now); return }
        val pair = c.ip + "|" + (m.deviceHash ?: "-")
        if (codesBlocked(c.ip, pair, now)) { send(c, err(PlayReason.PLAY_BAD_CODE, codesRetryAfterMs(c.ip, pair, now))); return }   // bloqué : réponse identique, rien n'est compté
        codeFailed(c.ip, pair, now); typed?.let { nearMiss(it, now) }; send(c, err(PlayReason.PLAY_BAD_CODE))
    }

    /**
     * Entrer dans une salle d'une CastBridge-TV (w20-04b, `webPlay` faux : le SEUL chemin d'entrée). Même chaîne que [create], FERMÉE à chaque pas, rien consommé avant la fin :
     * ticket `cbp1` ⇒ maintenance ⇒ révocations connues ⇒ `jti` pas encore servi (UNE table pour `create` et `join`) ⇒ activation `cbx1` de CETTE TV (édition ≠ NONE ; sinon le ticket est
     * brûlé) ⇒ ALORS SEULEMENT la recherche du code (un inconnu n'atteint jamais ni les compteurs de codes faux ni la rotation des codes ; la réponse sans ticket est la même
     * pour un code vivant et un code faux) ⇒ code faux : compteurs d'adresse existants PLUS un compteur par identité de TV ⇒ essai : créations et entrées dans le MÊME compteur du jour
     * ⇒ connexions vivantes de l'identité (créées + rejointes) ⇒ consommation (`jti`, compteur d'essai) ⇒ entrée ; la TV qui entre devient siège RELAIS (elle relaie SES téléphones).
     */
    private fun tvJoin(c: PlayConn, m: ClientMsg.Join, now: Long) {
        val real = System.currentTimeMillis()
        val ticket = (verifier.check(c.ticket, real) as? TicketVerifier.Result.Ok)?.ticket
        if (ticket == null) { send(c, err(PlayReason.PLAY_TICKET_REFUSED)); return }
        if (draining) { send(c, ServerMsg.Error(0, MAINTENANCE, "Le serveur de jeu est en maintenance : réessayez dans quelques minutes.", true)); return }
        if (!revocationsReady()) { send(c, ServerMsg.Error(0, MAINTENANCE, "Service indisponible : réessayez dans quelques minutes.", true)); return }
        if (used.seen(ticket.jti, real)) { send(c, err(PlayReason.PLAY_TICKET_REFUSED)); return }
        val rights = evaluator.evaluate(ticket.deviceCode, listOfNotNull(m.activation), real)
        val identity = rights.identity
        if (rights.edition == HostEdition.NONE || identity == null) {
            used.use(ticket.jti, ticket.exp, real)   // preuve de droits fausse, absente ou d'une autre TV : le ticket est brûlé, comme à `create`
            send(c, ServerMsg.Error(0, PlayReason.PLAY_SCOPE_FORBIDDEN.code, rights.note, false)); return
        }
        if (proofRefused(ticket, m.activation, m.proof)) { refuseProof(ticket, real, c); return }   // H-3
        // à partir d'ici l'appelant est une TV authentifiée : la recherche du code est permise
        val typed = RoomCode.normalize(m.code)
        val quizEntry = typed?.let { t -> rooms.values.firstOrNull { it.room.code == t } }
        val chessEntry = if (quizEntry == null) typed?.let { t -> chessRooms.values.firstOrNull { it.room.code == t } } else null   // un code de salle est unique sur les deux tables
        if (quizEntry == null && chessEntry == null) {
            val pair = c.ip + "|" + (m.deviceHash ?: "-")
            if (codesBlocked(c.ip, pair, now) || identityBlocked(identity, now)) { send(c, err(PlayReason.PLAY_BAD_CODE, maxOf(codesRetryAfterMs(c.ip, pair, now), identityRetryAfterMs(identity, now)))); return }
            codeFailed(c.ip, pair, now); identityFailed(identity, now); typed?.let { nearMiss(it, now) }; send(c, err(PlayReason.PLAY_BAD_CODE)); return
        }
        if (chessEntry != null) { chessTvJoin(c, m, now, real, ticket, rights, identity, chessEntry); return }
        val e = quizEntry ?: return
        val trial = rights.edition == HostEdition.TRIAL
        val verdict = PlayRules.canCreate(rights.actor, publicRoom = false, gamesToday = if (trial) trialDays.count(identity, real) else 0)
        if (!verdict.allowed) {
            used.use(ticket.jti, ticket.exp, real)
            val why = verdict.reason ?: PlayReason.PLAY_SCOPE_FORBIDDEN
            send(c, ServerMsg.Error(0, why.code, verdict.message, why.retryable)); return
        }
        // Quiz misé (games-G5) : la TV qui entre dans une salle MISÉE doit joindre SON blocage (vérifié ici : clé publique du portefeuille, identité de cette TV, monnaie et mise de la salle, `k` de 1 à 8) ; l'essai
        // n'y entre jamais (`STAKE_TRIAL_FREE_ONLY`) ; sans blocage, la salle dit la mise à bloquer (rien n'est consommé : la TV bloque puis revient sur la MÊME liaison). Un blocage dans une salle libre est refusé.
        val check = stakeCheck(rights, identity, e.room.stake, m.escrow, requireEscrow = false, real, PlayProtocol.GAME_QUIZ, PlayProtocol.MAX_STAKE_SEATS)
        if (check is StakeCheck.Refused) { send(c, check.error); return }
        val ok = check as? StakeCheck.Ok
        val cap = if (trial) minOf(1, cfg.maxRoomsPerSubject) else cfg.maxRoomsPerSubject
        val ownRoom = e.identity == identity   // rejoindre sa propre salle (spectateur) ne compte pas deux fois
        // M-2 : seules comptent les salles dont l'hôte est encore là ; une entrée vivante compte tant que sa connexion l'est
        fun liveOf(who: String) = liveConnectionsOf(who)
        val full = ServerMsg.Error(0, BUSY, "Vous avez déjà $cap partie${if (cap > 1) "s" else ""} ouverte${if (cap > 1) "s" else ""} : terminez-en une avant d'en rejoindre une autre.", true)
        afterJoinPrecheck?.invoke()
        var refusal: ServerMsg.Error? = null
        // C-1 : la salle JUGE l'admission (nom, appareil banni, salle pleine, code échu…) avant toute consommation ; le `jti` et la partie d'essai ne sont pris qu'APRÈS son `welcome`.
        // Tout se passe sous le verrou des salles (les entrées sont sérialisées : un rejeu ou une course ne passe pas entre le contrôle et la consommation).
        synchronized(roomLock) {
            when {
                rooms[e.room.roomId] !== e -> refusal = err(PlayReason.PLAY_BAD_CODE)
                !ownRoom && liveOf(identity) >= cap -> refusal = full
                trial && trialDays.count(identity, real) >= PlayRules.TRIAL_GAMES_PER_DAY -> refusal = ServerMsg.Error(0, PlayReason.PLAY_SCOPE_FORBIDDEN.code, PlayRules.MSG_TRIAL_DAILY, false)   // recontrôle sous verrou (course)
                !used.admits(ticket.jti, real) -> refusal = err(PlayReason.PLAY_TICKET_REFUSED)
                trial && !trialDays.canRecord(identity, real) -> refusal = err(PlayReason.PLAY_BUSY)
                ok != null && !stakes!!.gate.reserve(ok.escrow.eid, ok.expMs, real) -> refusal = gerr(GameReason.STAKE_ESCROW_INVALID)   // ce blocage sert déjà dans une salle vivante
                else -> {
                    e.joined[c.id] = identity
                    if (attachAndForward(e, c, m, now, grantRelay = true, admission = ServerRoom.Admission(identity, ok?.quiz))) {
                        used.use(ticket.jti, ticket.exp, real)
                        if (trial) trialDays.record(identity, real)
                    } else { e.joined.remove(c.id); ok?.let { stakes!!.gate.release(it.escrow.eid) } }   // refus de la salle : rien n'est consommé, le ticket reste utilisable, le blocage aussi
                }
            }
        }
        refusal?.let { send(c, it) }
    }

    private fun resume(c: PlayConn, m: ClientMsg.Resume, now: Long) {
        // jamais de blocage ni de compte d'échecs par adresse sur un resume (NAT collectif) : le seau de débit suffit, le jeton fait 128 bits
        val e = rooms[m.roomId]
        if (e != null) { attachAndForward(e, c, m, now); return }
        val g = chessRooms[m.roomId]
        if (g != null) { attachChess(g, c, m, now, ChessServerRoom.Admission(null)); return }   // même réponse pour une salle d'échecs
        send(c, err(PlayReason.PLAY_BAD_CODE))
    }

    /** Salles (Quiz et échecs) que ce sujet ou cette identité tient ouvertes : l'hôte parti ne compte plus (M-2). */
    private fun openRoomsOf(subject: String, identity: String?): Int =
        rooms.values.count { it.counts() && (it.subject == subject || (identity != null && it.identity == identity)) } +
            chessRooms.values.count { it.counts() && (it.subject == subject || (identity != null && it.identity == identity)) }

    /** Parties vivantes d'une identité : salles créées (hôte présent) + connexions entrées par `join` encore ouvertes, dans les DEUX tables. */
    private fun liveConnectionsOf(who: String): Int =
        rooms.values.count { it.identity == who && it.counts() } + rooms.values.filter { it.identity != who }.sumOf { r -> r.joined.entries.count { (cid, idn) -> idn == who && r.conns.containsKey(cid) } } +
            chessRooms.values.count { it.identity == who && it.counts() } + chessRooms.values.filter { it.identity != who }.sumOf { r -> r.joined.entries.count { (cid, idn) -> idn == who && r.conns.containsKey(cid) } }

    /** Attache la connexion à la salle le temps d'un message d'entrée ; sans `welcome` en retour, elle est détachée (et la salle neuve supprimée). */
    private fun attachAndForward(e: RoomEntry, c: PlayConn, m: ClientMsg, now: Long, grantRelay: Boolean = false, admission: ServerRoom.Admission = ServerRoom.Admission(null)): Boolean {
        val overflow = ArrayList<PlayConn>()
        var seatedRole: castbridge.core.quiz.online.PlayRole? = null
        synchronized(e) {
            e.conns[c.id] = c; c.entry = e
            val outs = e.room.handle(c.id, m, now, c.ip, admission)
            e.lastActiveRealMs = System.currentTimeMillis()
            val welcome = outs.firstOrNull { it.to == c.id && it.msg is ServerMsg.Welcome }?.msg as? ServerMsg.Welcome
            seatedRole = welcome?.role
            if (grantRelay && welcome != null) {   // (grantRelay : voir tvJoin)
                e.room.grantRelay(c.id)   // une TV authentifiée qui entre devient siège relais (avant toute livraison : aucun message d'elle ne passe entre les deux)
                e.joined[c.id]?.let { e.joinedTokens[welcome.token] = it }
            }
            // la reprise d'une TV entrée par `join` : sa nouvelle connexion porte la même identité (le plafond de connexions vivantes ne s'évade pas par un `resume`)
            if (m is ClientMsg.Resume && welcome != null) e.joinedTokens[m.token]?.let { e.joined[c.id] = it }
            deliver(e, outs, overflow)   // d'abord les réponses (un refus doit atteindre la connexion), puis détachement si elle n'est pas entrée
            if (outs.none { it.to == c.id && it.msg is ServerMsg.Welcome }) {
                e.conns.remove(c.id); c.entry = null
                if (m is ClientMsg.Create) rooms.remove(e.room.roomId)
            }
        }
        overflow.forEach { dropOverflow(it) }
        seatedRole?.let { guard.seated(c, m, e.room.roomId, it, (m as? ClientMsg.Resume)?.let { r -> e.room.deviceOfToken(r.token) }) }   // journal et plafond relevé : hors du verrou de la salle
        return seatedRole != null
    }

    private fun forward(e: RoomEntry, c: PlayConn, m: ClientMsg, now: Long) {
        guard.filter.room(c, e.room.roomId)?.let { send(c, it); return }   // débit de messages de la salle
        // M-3 : « Nouvelle partie » ne contourne pas les 3 parties par jour d'une salle d'essai : la 1re partie est comptée à la création, chaque `start` suivant en compte une
        val trialStart = e.trial && e.identity != null && m is ClientMsg.Act && m.action == "start"
        val real = System.currentTimeMillis()
        if (trialStart && e.starts.get() >= 1 && trialDays.count(e.identity!!, real) >= PlayRules.TRIAL_GAMES_PER_DAY) {
            send(c, ServerMsg.Error(0, PlayReason.PLAY_SCOPE_FORBIDDEN.code, PlayRules.MSG_TRIAL_DAILY, false)); return
        }
        val overflow = ArrayList<PlayConn>()
        synchronized(e) {
            e.lastActiveRealMs = real
            val before = e.room.phase()
            deliver(e, e.room.handle(c.id, m, now, c.ip), overflow)
            if (trialStart && before != ServerRoom.State.PLAYING && e.room.phase() == ServerRoom.State.PLAYING && e.starts.incrementAndGet() > 1) trialDays.record(e.identity!!, real)
        }
        overflow.forEach { dropOverflow(it) }
    }

    // ------------------------------------------------------------------ salles de jeu à tour de rôle : échecs en ligne (games-G2)

    private fun gerr(r: GameReason) = ServerMsg.Error(0, r.code, r.message, r.retryable)

    /** Les mises d'une entrée : libre, blocage vérifié (vu comme blocage d'échecs ET comme blocage de Quiz misé : le jeu choisit), ou refus à envoyer à la TV. */
    private sealed class StakeCheck {
        object Free : StakeCheck()
        class Ok(val escrow: ChessEscrow, val quiz: QuizEscrow, val expMs: Long) : StakeCheck()
        class Refused(val error: ServerMsg.Error) : StakeCheck()
    }

    private fun stakeRequired(spec: StakeSpec, game: String = PlayProtocol.GAME_CHESS) = ServerMsg.Error(0, GameReason.STAKE_ESCROW_REQUIRED.code, GameReason.STAKE_ESCROW_REQUIRED.message, true, 0L,
        linkedMapOf("game" to game, "cur" to spec.cur, "per" to spec.per))

    /**
     * Les mises d'une création ou de l'entrée d'un joueur. Fermé et SANS RIEN CONSOMMER : une TV d'essai ne mise jamais (parties libres seulement), l'interrupteur des mises et les clés doivent être là
     * (`STAKES_SUSPENDED`), la mise reste dans les bornes du service, le blocage `cbe1` (clé publique du portefeuille, identité de CETTE TV, monnaie et mise de la salle) est vérifié. [requireEscrow] :
     * à la création le blocage est obligatoire ; à l'entrée son absence est laissée à la salle, qui répond `STAKE_ESCROW_REQUIRED` avec la mise à bloquer. [game] et [maxSeats] : le jeu de la salle et le nombre
     * de sièges qu'un blocage peut couvrir (échecs : 1 ; Quiz misé : 8, une mise par siège).
     */
    private fun stakeCheck(rights: HostRights, identity: String, spec: StakeSpec?, token: String?, requireEscrow: Boolean, real: Long, game: String = PlayProtocol.GAME_CHESS, maxSeats: Int = 1): StakeCheck {
        if (spec == null) return if (token == null) StakeCheck.Free else StakeCheck.Refused(gerr(GameReason.STAKE_BAD))   // un blocage sans mise : la mise de la TV resterait bloquée pour rien
        if (rights.edition == HostEdition.TRIAL) return StakeCheck.Refused(gerr(GameReason.STAKE_TRIAL_FREE_ONLY))
        val s = stakes ?: return StakeCheck.Refused(gerr(GameReason.STAKES_SUSPENDED))
        if (!s.gate.inBounds(spec)) return StakeCheck.Refused(gerr(GameReason.STAKE_BAD))
        if (token == null) return if (requireEscrow) StakeCheck.Refused(stakeRequired(spec, game)) else StakeCheck.Free
        return when (val r = s.gate.check(token, real, identity, spec, maxSeats)) {
            is EscrowGate.Result.Ok -> StakeCheck.Ok(r.escrow(), r.quizEscrow(), r.ticket.exp)
            is EscrowGate.Result.Refused -> { guard.log.event("play.stake.refused", "blocage refusé", mapOf("why" to r.why.name, "game" to game), level = "warn"); StakeCheck.Refused(gerr(GameReason.STAKE_ESCROW_INVALID)) }
        }
    }

    /** Un code de salle encore libre sur les DEUX tables (à appeler sous [roomLock]). */
    private fun freeCode(): String {
        while (true) {
            val code = RoomCode.generate(rnd)
            if (rooms.values.none { it.room.code == code } && chessRooms.values.none { it.room.code == code }) return code
        }
    }

    /**
     * Ouvre une salle d'échecs (`create{game:"chess"}`). MÊME chaîne que [create], fermée à chaque pas, rien consommé avant la fin : ticket `cbp1`, maintenance, révocations connues, jeu connu et ouvert,
     * `jti` pas encore servi, preuve de possession de la TV, droits évalués par le service, règles commerciales (l'essai : 3 parties par jour), puis les mises ([stakeCheck]) et les plafonds de salles.
     * Aucune location ni question réservée : seule l'édition compte. Le blocage `cbe1` n'est réservé qu'au moment de créer (un blocage ne sert qu'à une salle).
     */
    private fun createGame(c: PlayConn, m: ClientMsg.Create, now: Long) {
        val real = System.currentTimeMillis()
        val ticket = (verifier.check(c.ticket, real) as? TicketVerifier.Result.Ok)?.ticket
        if (ticket == null) { send(c, err(PlayReason.PLAY_TICKET_REFUSED)); return }
        if (draining) { send(c, ServerMsg.Error(0, MAINTENANCE, "Le serveur de jeu est en maintenance : réessayez dans quelques minutes.", true)); return }
        if (!revocationsReady()) { send(c, ServerMsg.Error(0, MAINTENANCE, "Service indisponible : réessayez dans quelques minutes.", true)); return }
        if (m.game !in PlayProtocol.GAMES) { send(c, gerr(GameReason.GAME_UNKNOWN)); return }
        if (!cfg.chess) { send(c, gerr(GameReason.GAME_UNAVAILABLE)); return }
        val ip48 = ClientIp.group48(c.ip) ?: c.ip
        if (used.seen(ticket.jti, real)) { send(c, err(PlayReason.PLAY_TICKET_REFUSED)); return }
        if (proofRefused(ticket, m.activation, m.proof)) { refuseProof(ticket, real, c); return }
        val tooMany = ServerMsg.Error(0, BUSY, "Trop de parties ouvertes depuis cette adresse : réessayez dans une heure.", true)
        if (!createRate.peek(c.ip, real) || !create48.peek(ip48, real)) { send(c, tooMany); return }
        val rights = evaluator.evaluate(ticket.deviceCode, listOfNotNull(m.activation), real)
        val trial = rights.edition == HostEdition.TRIAL
        val verdict = PlayRules.canCreate(rights.actor, publicRoom = false, gamesToday = if (trial) trialDays.count(rights.identity ?: "", real) else 0)
        if (!verdict.allowed) {
            used.use(ticket.jti, ticket.exp, real)   // preuve de droits fausse ou refusée : le ticket est brûlé
            val why = verdict.reason ?: PlayReason.PLAY_SCOPE_FORBIDDEN
            send(c, ServerMsg.Error(0, why.code, if (rights.edition == HostEdition.NONE) rights.note else verdict.message, why.retryable)); return
        }
        val identity = rights.identity ?: run { send(c, err(PlayReason.PLAY_SCOPE_FORBIDDEN)); return }
        val check = stakeCheck(rights, identity, m.stake, m.escrow, requireEscrow = true, real)
        if (check is StakeCheck.Refused) { send(c, check.error); return }   // rien n'est brûlé : la TV corrige (mise, blocage) et recommence avec le MÊME ticket
        val ok = check as? StakeCheck.Ok
        val subjectCap = if (trial) minOf(1, cfg.maxRoomsPerSubject) else cfg.maxRoomsPerSubject
        val subjectFull = ServerMsg.Error(0, BUSY, "Vous avez déjà $subjectCap partie${if (subjectCap > 1) "s" else ""} ouverte${if (subjectCap > 1) "s" else ""} : terminez-en une avant d'en ouvrir une autre.", true)
        if (openRoomsOf(ticket.deviceId, identity) >= subjectCap) { send(c, subjectFull); return }
        val id = ByteArray(16).also { rnd.nextBytes(it) }.joinToString("") { "%02x".format(it) }
        val chess = m.chess
        // une partie misée se joue en compétition seulement : jamais de coup tiré au hasard avec de l'argent en jeu
        val mode = if (m.stake != null) ClockMode.COMPETITION else runCatching { ClockMode.valueOf(chess?.mode.orEmpty()) }.getOrDefault(ClockMode.COMPETITION)
        val options = ChessServerRoom.Options(chess?.perMoveSeconds ?: castbridge.core.chess.MoveTimer.DEFAULT_SECONDS, mode, chess?.color ?: "random", m.stake)
        var refusal: ServerMsg.Error? = null
        // le blocage n'est rendu QUE s'il a été réservé par CET essai (games-G5) : un refus avant la réservation, ou une réservation refusée parce que le blocage sert déjà dans une autre salle vivante, ne doit
        // jamais libérer la réservation de cette autre salle (sinon le même `cbe1` rejoué ouvrirait une seconde salle)
        var reservedHere = false
        fun release() { if (reservedHere) { ok?.let { stakes?.gate?.release(it.escrow.eid) }; reservedHere = false } }
        val e = synchronized(roomLock) {
            fun fail(error: ServerMsg.Error): ChessEntry? { release(); refusal = error; return null }
            when {
                rooms.size + chessRooms.size >= cfg.maxRooms -> fail(err(PlayReason.PLAY_BUSY))
                openRoomsOf(ticket.deviceId, identity) >= subjectCap -> fail(subjectFull)
                !trial && identityDays.count(identity, real) >= cfg.createsPerIdentityPerDay -> fail(ServerMsg.Error(0, BUSY, "Trop de parties ouvertes aujourd'hui avec cette activation : réessayez demain.", true))
                !createRate.allow(c.ip, real) || !create48.allow(ip48, real) -> fail(tooMany)
                ok != null && !stakes!!.gate.reserve(ok.escrow.eid, ok.expMs, real).also { reservedHere = it } -> fail(gerr(GameReason.STAKE_ESCROW_INVALID))
                else -> when (used.use(ticket.jti, ticket.exp, real)) {
                    UsedTickets.Use.REPLAY -> fail(err(PlayReason.PLAY_TICKET_REFUSED))
                    UsedTickets.Use.FULL -> fail(err(PlayReason.PLAY_BUSY))
                    UsedTickets.Use.OK -> when {
                        trial && !trialDays.record(identity, real) -> fail(err(PlayReason.PLAY_BUSY))
                        !trial && !identityDays.record(identity, real) -> fail(err(PlayReason.PLAY_BUSY))
                        else -> {
                            val room = ChessServerRoom(id, rnd, now, options, code = freeCode(), signer = stakes?.signer, onResult = { rid, token -> stakes?.spool?.write(rid, token) })
                            ChessEntry(room, ticket.deviceId, identity, trial).also { chessRooms[id] = it }
                        }
                    }
                }
            }
        }
        if (e == null) { send(c, refusal!!); return }
        if (attachChess(e, c, m, now, ChessServerRoom.Admission(identity, ok?.escrow))) {
            e.hostSeen = true
            guard.log.event("play.game.created", "", mapOf("roomId" to id, "ip" to c.ip, "game" to PlayProtocol.GAME_CHESS, "staked" to (m.stake != null)))
        } else release()
    }

    /**
     * Entrer dans une salle d'échecs d'une TV (joueur ou spectatrice) : MÊME chaîne que [tvJoin] (déjà passée jusqu'à la recherche du code), puis les droits d'essai, les mises (spectateur : aucune),
     * les plafonds de parties vivantes de l'identité, et ALORS SEULEMENT la consommation (`jti`, compteur d'essai, blocage réservé). Un refus de la salle ne consomme rien : le ticket et le blocage
     * restent utilisables (la TV peut bloquer sa mise après que la salle lui a dit laquelle, puis revenir avec le même ticket).
     */
    private fun chessTvJoin(c: PlayConn, m: ClientMsg.Join, now: Long, real: Long, ticket: TicketVerifier.Ticket, rights: HostRights, identity: String, g: ChessEntry) {
        if (!cfg.chess) { send(c, gerr(GameReason.GAME_UNAVAILABLE)); return }
        val trial = rights.edition == HostEdition.TRIAL
        val verdict = PlayRules.canCreate(rights.actor, publicRoom = false, gamesToday = if (trial) trialDays.count(identity, real) else 0)
        if (!verdict.allowed) {
            used.use(ticket.jti, ticket.exp, real)
            val why = verdict.reason ?: PlayReason.PLAY_SCOPE_FORBIDDEN
            send(c, ServerMsg.Error(0, why.code, verdict.message, why.retryable)); return
        }
        val check = if (m.spectate) StakeCheck.Free else stakeCheck(rights, identity, g.room.stake, m.escrow, requireEscrow = false, real)
        if (check is StakeCheck.Refused) { send(c, check.error); return }
        val ok = check as? StakeCheck.Ok
        val cap = if (trial) minOf(1, cfg.maxRoomsPerSubject) else cfg.maxRoomsPerSubject
        val ownRoom = g.identity == identity   // rejoindre sa propre salle (spectateur) ne compte pas deux fois
        val full = ServerMsg.Error(0, BUSY, "Vous avez déjà $cap partie${if (cap > 1) "s" else ""} ouverte${if (cap > 1) "s" else ""} : terminez-en une avant d'en rejoindre une autre.", true)
        afterJoinPrecheck?.invoke()
        var refusal: ServerMsg.Error? = null
        synchronized(roomLock) {
            when {
                chessRooms[g.room.roomId] !== g -> refusal = err(PlayReason.PLAY_BAD_CODE)
                !ownRoom && liveConnectionsOf(identity) >= cap -> refusal = full
                trial && trialDays.count(identity, real) >= PlayRules.TRIAL_GAMES_PER_DAY -> refusal = ServerMsg.Error(0, PlayReason.PLAY_SCOPE_FORBIDDEN.code, PlayRules.MSG_TRIAL_DAILY, false)
                !used.admits(ticket.jti, real) -> refusal = err(PlayReason.PLAY_TICKET_REFUSED)
                trial && !trialDays.canRecord(identity, real) -> refusal = err(PlayReason.PLAY_BUSY)
                ok != null && !stakes!!.gate.reserve(ok.escrow.eid, ok.expMs, real) -> refusal = gerr(GameReason.STAKE_ESCROW_INVALID)
                else -> {
                    g.joined[c.id] = identity
                    if (attachChess(g, c, m, now, ChessServerRoom.Admission(identity, ok?.escrow))) {
                        used.use(ticket.jti, ticket.exp, real)
                        if (trial) trialDays.record(identity, real)
                    } else { g.joined.remove(c.id); ok?.let { stakes!!.gate.release(it.escrow.eid) } }   // refus de la salle : rien n'est consommé
                }
            }
        }
        refusal?.let { send(c, it) }
    }

    /** Attache la connexion à la salle d'échecs le temps d'un message d'entrée ; sans `welcome` en retour, elle est détachée (et la salle neuve supprimée). Vrai si elle est assise. */
    private fun attachChess(g: ChessEntry, c: PlayConn, m: ClientMsg, now: Long, admission: ChessServerRoom.Admission): Boolean {
        val overflow = ArrayList<PlayConn>()
        var seated = false
        synchronized(g) {
            g.conns[c.id] = c; c.game = g
            val outs = g.room.handle(c.id, m, now, c.ip, admission)
            g.lastActiveRealMs = System.currentTimeMillis()
            val welcome = outs.firstOrNull { it.to == c.id && it.msg is ServerMsg.Welcome }?.msg as? ServerMsg.Welcome
            seated = welcome != null
            // la reprise d'une TV entrée par `join` : sa nouvelle connexion porte la même identité (le plafond de connexions vivantes ne s'évade pas par un `resume`)
            if (welcome != null) {
                if (m is ClientMsg.Resume) g.joinedTokens[m.token]?.let { g.joined[c.id] = it }
                else g.joined[c.id]?.let { g.joinedTokens[welcome.token] = it }
            }
            deliverChess(g, outs, overflow)
            if (!seated) { g.conns.remove(c.id); c.game = null; if (m is ClientMsg.Create) chessRooms.remove(g.room.roomId) }
        }
        overflow.forEach { dropOverflow(it) }
        return seated
    }

    private fun forwardGame(g: ChessEntry, c: PlayConn, m: ClientMsg, now: Long) {
        guard.filter.room(c, g.room.roomId)?.let { send(c, it); return }   // débit de messages de la salle
        val overflow = ArrayList<PlayConn>()
        synchronized(g) {
            g.lastActiveRealMs = System.currentTimeMillis()
            deliverChess(g, g.room.handle(c.id, m, now, c.ip), overflow)
        }
        overflow.forEach { dropOverflow(it) }
    }

    private fun deliverChess(g: ChessEntry, outs: List<ChessServerRoom.Out>, overflow: MutableList<PlayConn>) {
        for (o in outs) {
            val t = g.conns[o.to] ?: continue
            if (!t.offer(PlayCodec.encode(o.msg))) { if (!overflow.contains(t)) overflow += t }
        }
    }

    /**
     * Arrêt du service (déploiement, `close`) : chaque salle d'échecs ENCORE OUVERTE ou en jeu est INTERROMPUE (un `cbr1` ABORT par salle misée : envoyé aux TV et déposé pour le collecteur, chaque
     * blocage est rendu en entier), puis fermée (`roomGone`) ; de même chaque salle de Quiz MISÉE (games-G5). Rend le nombre de salles interrompues. Une partie déjà finie a déjà son résultat : elle est
     * seulement fermée.
     */
    fun finishGames(reason: String): Int {
        var n = 0
        for (g in ArrayList(chessRooms.values)) {
            val overflow = ArrayList<PlayConn>()
            synchronized(g) {
                val ongoing = g.room.phase() == ChessServerRoom.State.OPEN || g.room.phase() == ChessServerRoom.State.PLAYING
                if (g.room.phase() != ChessServerRoom.State.GONE) deliverChess(g, g.room.close(reason, clock()), overflow)
                if (ongoing) n++
                chessRooms.remove(g.room.roomId); g.conns.values.forEach { it.game = null }; g.conns.clear()
            }
            overflow.forEach { dropOverflow(it) }
        }
        // Quiz misé (games-G5) : une salle MISÉE encore ouverte ou en jeu est interrompue de la même façon (résultat ABORT envoyé aux TV et déposé pour le collecteur : chaque blocage est rendu en entier) ;
        // une salle LIBRE garde son comportement d'avant (elle tombe avec les connexions)
        for (e in ArrayList(rooms.values)) {
            if (e.room.stake == null) continue
            val overflow = ArrayList<PlayConn>()
            synchronized(e) {
                val ongoing = e.room.phase() == ServerRoom.State.OPEN || e.room.phase() == ServerRoom.State.PLAYING
                if (e.room.phase() != ServerRoom.State.GONE) deliver(e, e.room.close(reason, clock()), overflow)
                if (ongoing) n++
                rooms.remove(e.room.roomId); e.conns.values.forEach { it.entry = null }; e.conns.clear()
            }
            overflow.forEach { dropOverflow(it) }
        }
        return n
    }

    /** Parties MISÉES vivantes (Quiz et échecs, ni finies ni éteintes) : à vider avant un déploiement (l'arrêt les interrompt, mises rendues). */
    fun stakedRoomCount(): Int =
        rooms.values.count { it.room.stake != null && it.room.phase().let { p -> p == ServerRoom.State.OPEN || p == ServerRoom.State.PLAYING } } +
            chessRooms.values.count { it.room.isStaked && it.room.phase().let { p -> p == ChessServerRoom.State.OPEN || p == ChessServerRoom.State.PLAYING } }

    private fun deliver(e: RoomEntry, outs: List<ServerRoom.Out>, overflow: MutableList<PlayConn>) {
        for (o in outs) {
            val t = e.conns[o.to] ?: continue
            if (!t.offer(PlayCodec.encode(o.msg))) { if (!overflow.contains(t)) overflow += t }
        }
    }

    private fun dropOverflow(c: PlayConn) { c.close(1008, "file de sortie pleine"); onClosed(c) }

    private fun send(c: PlayConn, m: ServerMsg) { if (!c.offer(PlayCodec.encode(m))) dropOverflow(c) }
    private fun err(r: PlayReason, retryAfterMs: Long = r.retryAfterMs) = ServerMsg.Error(0, r.code, r.message, r.retryable, retryAfterMs)

    /** La connexion est tombée : la place est gardée (reprise par `resume`, 10 min). Idempotent. */
    fun onClosed(c: PlayConn) {
        if (!c.closed.compareAndSet(false, true)) return
        all.remove(c.id)
        limits.release(c.ip)
        guard.unseated(c)
        c.game?.let { g ->   // salle d'échecs : la place est gardée, la pendule continue, le forfait court à partir d'ici
            val overflow = ArrayList<PlayConn>()
            synchronized(g) {
                g.conns.remove(c.id)
                deliverChess(g, g.room.disconnect(c.id, clock()), overflow)
            }
            overflow.forEach { dropOverflow(it) }
        }
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
            var unranked: Map<String, Any?>? = null
            synchronized(e) {
                if (e.room.phase() != ServerRoom.State.GONE) {
                    val tooOld = mono - e.createdRealMs >= cfg.roomMaxMs
                    if (e.conns.isEmpty() && mono - e.lastActiveRealMs >= cfg.roomIdleMs || tooOld) {
                        deliver(e, e.room.close(if (tooOld) "EXPIRED" else "IDLE", now), overflow)
                    } else deliver(e, e.room.tick(now), overflow)
                }
                if (e.room.phase() != ServerRoom.State.FINISHED) e.unrankedLogged = false
                else if (!e.unrankedLogged && e.room.unrankedSeats() > 0) {
                    e.unrankedLogged = true
                    unranked = mapOf("roomId" to e.room.roomId, "seats" to e.room.unrankedSeats(), "reasons" to e.room.botResults().filterValues { it.flagged }.values.flatMap { it.reasons }.distinct())
                }
                if (e.room.phase() == ServerRoom.State.GONE) { guard.forget(e.room.roomId); rooms.remove(e.room.roomId); e.conns.values.forEach { it.entry = null }; e.conns.clear() }
            }
            overflow.forEach { dropOverflow(it) }
            unranked?.let { guard.log.event("play.game.unranked", "sièges hors classement", it) }   // jamais d'écriture de journal sous le verrou de la salle
        }
        for (g in ArrayList(chessRooms.values)) {   // salles d'échecs : pendule, forfaits, expirations ; une salle sans connexion depuis `roomIdleMs` est fermée (sa partie, si elle est en cours, est interrompue)
            val overflow = ArrayList<PlayConn>()
            synchronized(g) {
                if (g.room.phase() != ChessServerRoom.State.GONE) {
                    if (g.conns.isEmpty() && mono - g.lastActiveRealMs >= cfg.roomIdleMs) deliverChess(g, g.room.close("IDLE", now), overflow)
                    else deliverChess(g, g.room.tick(now), overflow)
                }
                if (g.room.phase() == ChessServerRoom.State.GONE) { guard.forget(g.room.roomId); chessRooms.remove(g.room.roomId); g.conns.values.forEach { it.game = null }; g.conns.clear() }
            }
            overflow.forEach { dropOverflow(it) }
        }
        synchronized(badCodes) { badCodes.sweep(now) }; synchronized(badCodes48) { badCodes48.sweep(now) }; synchronized(badPairs) { badPairs.sweep(now) }; synchronized(badCodesIdentity) { badCodesIdentity.sweep(now) }
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
        for (g in ArrayList(chessRooms.values)) for (c in ArrayList(g.conns.values)) c.offer(text)
    }

    fun connectionsOpen(): Int = all.size

    fun closeAll() {
        runCatching { finishGames("SHUTDOWN") }   // une salle d'échecs misée encore en cours produit son ABORT (déposé pour le collecteur) avant que les connexions ne tombent
        for (c in ArrayList(all.values)) { c.close(1001, "arrêt du service"); onClosed(c) }
    }

    private fun codesBlocked(ip: String, pair: String, now: Long) = badCodes.synchronizedBlocked(ip, now) || badPairs.synchronizedBlocked(pair, now) || (ClientIp.group48(ip)?.let { badCodes48.synchronizedBlocked(it, now) } ?: false)
    /** Attente avant que cette adresse (ou son /48) puisse retaper un code : la plus longue des deux. */
    private fun codesRetryAfterMs(ip: String, pair: String, now: Long): Long =
        maxOf(synchronized(badCodes) { badCodes.retryAfterMs(ip, now) }, synchronized(badPairs) { badPairs.retryAfterMs(pair, now) }, ClientIp.group48(ip)?.let { g -> synchronized(badCodes48) { badCodes48.retryAfterMs(g, now) } } ?: 0L)

    /**
     * Une frappe fausse proche d'un code vivant (un seul symbole d'écart) : compte pour CETTE salle ; à la 50e, son code change (au plus une fois par minute, salle d'attente seulement).
     * Seul chemin qui peut faire tourner un code : `join`.
     */
    private fun nearMiss(typed: String, now: Long) {
        for (e in ArrayList(rooms.values)) {
            if (!RoomCode.nearMiss(typed, e.room.code)) continue
            val rotated = synchronized(e) { e.room.noteNearMiss(now) }
            if (rotated) guard.log.event("play.room.code_rotated", "code de salle renouvelé après des frappes proches", mapOf("roomId" to e.room.roomId))
        }
    }

    // compteur de codes faux PAR IDENTITÉ de TV (w20-04b) : en plus des compteurs d'adresse, car une TV authentifiée change d'adresse et d'appareil API à volonté, pas d'activation signée
    private fun identityBlocked(identity: String, now: Long) = badCodesIdentity.synchronizedBlocked(identity, now)
    private fun identityRetryAfterMs(identity: String, now: Long): Long = synchronized(badCodesIdentity) { badCodesIdentity.retryAfterMs(identity, now) }
    private fun identityFailed(identity: String, now: Long) { badCodesIdentity.synchronizedFail(identity, now) }

    private fun codeFailed(ip: String, pair: String, now: Long) { badCodes.synchronizedFail(ip, now); badPairs.synchronizedFail(pair, now); ClientIp.group48(ip)?.let { badCodes48.synchronizedFail(it, now) } }

    companion object { const val BUSY = "PLAY_BUSY"; const val MAINTENANCE = "PLAY_MAINTENANCE"; const val MAX_BAD_CODES_PER_48 = 120; const val MAX_BAD_CODES_PER_IP_PER_DAY = 5_000 }
}

private fun BadCodeCounter.synchronizedBlocked(ip: String, now: Long) = synchronized(this) { ipBlocked(ip, now) }
private fun BadCodeCounter.synchronizedFail(ip: String, now: Long) = synchronized(this) { ipFail(ip, now) }
