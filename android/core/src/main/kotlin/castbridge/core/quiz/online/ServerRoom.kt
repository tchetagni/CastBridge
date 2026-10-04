package castbridge.core.quiz.online

import castbridge.core.quiz.QuizBank
import castbridge.core.quiz.QuizDuel
import castbridge.core.quiz.QuizGame
import castbridge.core.quiz.QuizHistoryBook
import castbridge.core.quiz.QuizRoom

/**
 * Salle Internet autoritaire côté serveur (DESIGN-W20 § 2.4) : `roomId`, code (8 car.), hôte, spectateurs, 1 table (= le MÊME `QuizRoom`,
 * horloge et hasard injectés), états `OPEN → PLAYING → FINISHED → GONE`. PURE : aucune socket, aucun thread ; le service fournit les
 * connexions (`conn`), l'horloge (`now`, ms, horloge du SERVEUR) et appelle [tick]. Toutes les méthodes publiques prennent le verrou de salle.
 *
 * Délai entre deux questions (exigence du propriétaire, [PlayTiming]) : dans une salle `INTERNET`, la question suivante est ANNONCÉE tout de
 * suite (message `question` avec `opensAtServerMs` absolu) et ne s'OUVRE que `PlayTiming.gap` plus tard ; une réponse arrivée avant est refusée
 * `TOO_EARLY` et jamais comptée ; le temps se mesure depuis `opensAtServerMs`. « Tout de suite » = au moment où `QuizDuel` ouvre la question
 * suivante (fin de l'écran de classement) ; la dernière question n'est suivie d'aucun délai ; `LAN` et `TV_ONLY` : délai 0, comportement inchangé.
 * Pour ne pas modifier `QuizDuel`, la table lit une horloge décalée (`serveur − décalage`) : le décalage grandit du délai à chaque ouverture,
 * si bien que la fenêtre de réponse reste entière.
 */
class ServerRoom(
    val roomId: String,
    val scope: PlayScope = PlayScope.INTERNET,
    bank: QuizBank,
    private val random: java.util.Random,
    val createdAt: Long,
    val settings: Settings = Settings(),
    private val gapRequestedMs: Long = PlayTiming.INTER_QUESTION_GAP_MS,
    histories: QuizHistoryBook = QuizHistoryBook(null),
) {
    data class Settings(val allowSpectators: Boolean = true, val maxSpectators: Int = 50, val seatsPerTable: Int = 8, val duelCount: Int = 10, val duelQuestionMs: Long = 20_000)

    enum class State { OPEN, PLAYING, FINISHED, GONE }
    enum class ActResult { OK, SAME, CLOSED, UNKNOWN_QUESTION, TOO_EARLY, FORBIDDEN, BAD_REQUEST, UNKNOWN_PLAYER, IGNORED }

    /** Un message à envoyer à la connexion `to`. */
    data class Out(val to: String, val msg: ServerMsg)

    /** Un siège : l'hôte, un joueur ou un spectateur. `token` (128 bits) est le seul secret ; `viaTv` : joueur local relayé par la TV. */
    class Seat internal constructor(val token: String, var role: PlayRole, val name: String, val deviceHash: String?, val viaTv: Boolean) {
        var quizToken: String? = null; internal set
        var playerId: String? = null; internal set
        var conn: String? = null; internal set
        var lostAt: Long? = null; internal set
        var muted = false; internal set
        /** Clé d'adresse du client (IPv4, ou préfixe /64 en IPv6) : sert au bannissement par adresse. */
        var ip: String? = null; internal set
    }

    /** Une réponse retenue. `choice` et `correct` sont additifs (w20-07) : ils nourrissent [BotScore], jamais l'écran. */
    data class AnswerRecord(val questionIndex: Int, val conn: String, val playerId: String, val elapsedMs: Long, val arrivedAtServerMs: Long,
                            val choice: Int = -1, val correct: Boolean = false)

    internal class TableClock { var now = 0L; var shift = 0L; fun read() = now - shift }
    private class Ev(val kind: String, val data: Map<String, Any?> = emptyMap())

    /** Une table = un `QuizRoom` (V1 : une seule par salle). */
    class Table internal constructor(val index: Int, val name: String, internal val clock: TableClock, val room: QuizRoom) {
        var opensAtServerMs = 0L; internal set
        var abandoned: String? = null; internal set
        var pausedAt: Long? = null; internal set
        internal var announcedIndex = -1
        internal var revealedIndex = -1
        internal var lastDuel: QuizDuel? = null
        internal var lastGame: QuizGame? = null
        private val log = ArrayList<AnswerRecord>()

        fun duelPhase(): QuizDuel.Phase? = room.duel?.phase
        fun answeredCount(): Int = room.duel?.answered()?.size ?: 0
        fun answerLog(): List<AnswerRecord> = log.toList()
        fun lastElapsedMs(conn: String): Long? = log.lastOrNull { it.conn == conn }?.elapsedMs
        internal fun record(r: AnswerRecord) { log += r }
        internal fun reset() { log.clear(); announcedIndex = -1; revealedIndex = -1; lastDuel = null; lastGame = null; abandoned = null; pausedAt = null }
    }

    private val lock = Any()
    private val clock = TableClock()
    private val table0 = Table(0, "TV Salon", clock, QuizRoom(bank, clock = clock::read, random = random, maxPlayers = settings.seatsPerTable, idleCloseMs = Long.MAX_VALUE / 4,
        autoTick = false, histories = histories, duelCount = settings.duelCount, duelQuestionMs = settings.duelQuestionMs))
    val tables: List<Table> = listOf(table0)
    fun table(i: Int): Table = tables[i]

    var code: String = RoomCode.generate(random); private set
    private var state = State.OPEN
    private var seq = 1L
    private val ring = EventRing(50)
    val rtt = RttBook()
    private val badCodes = BadCodeCounter()
    private val seats = LinkedHashMap<String, Seat>()      // by token
    private val byConn = HashMap<String, Seat>()
    private val bannedDevices = HashSet<String>()
    private val bannedIps = HashSet<String>()
    private var host: Seat? = null
    private var autoHost = false
    private var scopeClosedAt: Long? = null
    private var abandonedAt: Long? = null
    private var lastPingAt = createdAt
    private var pingCounter = 0L
    private val pendingPings = HashMap<String, Pair<String, Long>>()
    private val pending = ArrayList<Ev>()
    private var lastVersion = 0L
    private var lastSafety: SafetyView? = null
    private val safetySeen = HashSet<String>()
    private val reports = ArrayList<Triple<String, String?, String>>()

    fun phase(): State = synchronized(lock) { state }
    fun currentQuestionId(): String? = synchronized(lock) { table0.room.currentQuestion()?.id }
    fun currentQuestionIndex(): Int = synchronized(lock) { table0.room.duel?.index ?: table0.room.game?.index ?: -1 }
    fun seatCount(): Int = synchronized(lock) { seats.values.count { it.role != PlayRole.SPECTATOR && it.quizToken != null } }
    fun reportCount(): Int = synchronized(lock) { reports.size }
    fun spectatorCount(): Int = synchronized(lock) { seats.values.count { it.role == PlayRole.SPECTATOR } }
    fun eventsSince(lastSeq: Long): List<EventRing.Event>? = synchronized(lock) { ring.since(lastSeq) }
    fun seq(): Long = synchronized(lock) { seq }

    /** Identifiant d'appareil du siège de ce jeton (null : inconnu, ou siège relayé sans appareil). Sert au plafond partagé du service à la REPRISE. */
    fun deviceOfToken(token: String): String? = synchronized(lock) { seats[token]?.deviceHash }

    // ------------------------------------------------------------------ anti-triche (w20-07) : actions DOUCES seulement

    private var botCache: Pair<Int, Map<String, BotScore.Result>>? = null

    /**
     * Soupçon de robot par joueur (clé : identifiant de joueur), sur les réponses déjà données au Duel. Interne : pour la modération, jamais affiché. Mémorisé tant que
     * le journal des réponses ne change pas.
     */
    fun botResults(): Map<String, BotScore.Result> = synchronized(lock) {
        val log = table0.answerLog()
        botCache?.takeIf { it.first == log.size }?.let { return it.second }
        val byPlayer = seats.values.filter { it.playerId != null }.associateBy { it.playerId!! }
        val traces = log.groupBy { it.playerId }.map { (pid, rs) ->
            BotScore.Trace(pid, byPlayer[pid]?.ip, byPlayer[pid]?.deviceHash,
                rs.map { BotScore.Answer(it.questionIndex, it.elapsedMs, it.correct, it.choice) })
        }
        BotScore.scoreAll(traces).also { botCache = log.size to it }
    }

    /**
     * Ce joueur est-il CLASSÉ ? Siège par siège (audit Opus) : seul un siège qui atteint [BotScore.THRESHOLD] sort du classement ; un intrus ne rend pas la partie des autres
     * « non classée ». Hors Internet, et pour un siège sans réponse, toujours vrai. Aucune expulsion : la partie se joue pareil, son écran dit seulement [BotScore.NEUTRAL_TEXT].
     */
    fun isRanked(playerId: String?): Boolean = synchronized(lock) { scope != PlayScope.INTERNET || playerId == null || botResults()[playerId]?.flagged != true }

    /** Combien de sièges sont sortis du classement (journal et modération ; jamais montré). */
    fun unrankedSeats(): Int = synchronized(lock) { if (scope != PlayScope.INTERNET) 0 else botResults().count { it.value.flagged } }

    private var lastRotationAt = Long.MIN_VALUE / 2

    /**
     * Une frappe PROCHE du code de cette salle ([RoomCode.nearMiss]) vient d'être refusée. À la [RoomCode.ROTATE_AFTER]e, le code change (la salle s'annonce par l'évènement
     * `codeRotated`), SEULEMENT en salle d'attente et au plus une fois par [ROTATE_MIN_MS] : une salle en jeu n'a rien à gagner d'un nouveau code, et un énumérateur ne doit
     * pas pouvoir faire tourner le code sous les pieds d'une famille qui tape le sien. Appelée par le service depuis le seul chemin `join` ; rend vrai si le code a changé.
     */
    fun noteNearMiss(now: Long): Boolean = synchronized(lock) {
        if (!badCodes.roomFail() || state != State.OPEN || now - lastRotationAt < ROTATE_MIN_MS) return false
        code = RoomCode.generate(random); lastRotationAt = now; pending += Ev("codeRotated")
        true
    }

    // ------------------------------------------------------------------ messages

    fun handle(conn: String, msg: ClientMsg, now: Long, ip: String? = null): List<Out> = synchronized(lock) {
        val out = ArrayList<Out>()
        clock.now = now
        if (state == State.GONE) { out += err(conn, PlayReason.PLAY_ROOM_GONE); return out }
        when (msg) {
            is ClientMsg.Hello -> {}   // négociation faite par le service (intersection des capacités)
            is ClientMsg.Create -> create(conn, msg, now, out)
            is ClientMsg.Join -> join(conn, msg, now, ip, out)
            is ClientMsg.Resume -> resume(conn, msg, now, ip, out)
            is ClientMsg.Act -> {
                val seat = byConn[conn]
                val r = if (seat == null) ActResult.UNKNOWN_PLAYER else act(seat, conn, msg, now, out)
                out += Out(conn, ServerMsg.Ack(seq, msg.seq, r.name))
            }
            is ClientMsg.RelayAct -> out += Out(conn, ServerMsg.Ack(seq, msg.seq, relay(conn, msg, now).name))
            is ClientMsg.Scope -> setScope(conn, msg.open, now, out)
            is ClientMsg.Kick -> hostOnly(conn, out) { kick(it, msg.playerId, out) }
            is ClientMsg.Mute -> hostOnly(conn, out) { _ -> seats.values.firstOrNull { it.playerId == msg.playerId }?.muted = msg.muted; dirty() }
            is ClientMsg.Report -> { byConn[conn]?.let { if (reports.size < 100) reports += Triple(it.token.take(6), msg.questionId, msg.reason) } }
            is ClientMsg.Pong -> pendingPings[conn]?.let { (id, sentAt) -> if (id == msg.id) { rtt.sample(conn, now - sentAt); pendingPings.remove(conn) } }
        }
        flush(now, out)
        out
    }

    /** Le service signale qu'une connexion est tombée : la place est gardée (reprise par `resume`). */
    fun disconnect(conn: String, now: Long): List<Out> = synchronized(lock) {
        val out = ArrayList<Out>()
        clock.now = now
        val seat = byConn.remove(conn) ?: return out
        seat.conn = null; seat.lostAt = now
        rtt.forget(conn); pendingPings.remove(conn)
        presence(seat, false)
        if (seat === host) seats.values.filter { it.viaTv }.forEach { presence(it, false) }
        pending += Ev("lost", mapOf("role" to seat.role.name, "playerId" to seat.playerId))
        flush(now, out)
        out
    }

    /** Horloge : délais, pauses, abandon, pings, fermeture. Appelé par le service (toutes les 50-200 ms). */
    fun tick(now: Long): List<Out> = synchronized(lock) {
        val out = ArrayList<Out>()
        clock.now = now
        if (state == State.GONE) return out
        if (state != State.PLAYING && RoomCode.expired(createdAt, now)) return gone("EXPIRED", out)
        scopeClosedAt?.let { if (now - it >= SPECTATOR_GRACE_MS) return gone("HOST_CLOSED_INTERNET", out) }
        abandonedAt?.let { if (now - it >= PURGE_MS) return gone("HOST_LOST", out) }
        purgeSpectators(now)
        badCodes.sweep(now)
        hostWatch(now)
        if ((state == State.OPEN || state == State.PLAYING) && now - lastPingAt >= PING_EVERY_MS) {
            lastPingAt = now
            for (c in byConn.keys.toList()) { val id = "p${pingCounter++}"; pendingPings[c] = id to now; out += Out(c, ServerMsg.Ping(seq, id, now)) }
        }
        val t = table0
        if (state == State.PLAYING && t.abandoned == null && t.pausedAt == null) {
            val d = t.room.duel
            val hold = d != null && d.phase == QuizDuel.Phase.QUESTION && scope == PlayScope.INTERNET && now < t.opensAtServerMs + d.questionMs + maxGrace()
            if (!hold) t.room.tick()
        }
        flush(now, out)
        out
    }

    // ------------------------------------------------------------------ create / join / resume

    private fun create(conn: String, m: ClientMsg.Create, now: Long, out: MutableList<Out>) {
        if (host != null || state != State.OPEN) { out += errp(conn, PlayProtocol.FORBIDDEN, "Cette salle a déjà un hôte."); return }
        val seat = Seat(newToken(), PlayRole.HOST, QuizRoom.cleanName(m.name) ?: "TV", null, false)
        if (m.name != null) {
            val j = table0.room.join(table0.room.code, m.name, null, null)
            if (j.status != QuizRoom.Join.OK) { out += errp(conn, PlayProtocol.BAD_REQUEST, "Nom invalide."); return }
            seat.quizToken = j.player!!.token; seat.playerId = j.player.id
        }
        seats[seat.token] = seat; host = seat
        attach(seat, conn)
        m.mode?.let { runCatching { QuizRoom.Mode.valueOf(it) }.getOrNull() }?.let { table0.room.setMode(it) }
        out += welcome(seat, conn)
        pending += Ev("created")
    }

    private fun join(conn: String, m: ClientMsg.Join, now: Long, ip: String?, out: MutableList<Out>) {
        if (RoomCode.expired(createdAt, now)) { out += err(conn, PlayReason.PLAY_ROOM_GONE); return }
        val typed = RoomCode.normalize(m.code)
        // Une connexion déjà assise n'essaie pas de deviner un code : refus sans compter (sinon un spectateur ferait tourner le code de la salle).
        // Seule la TV hôte enregistre ainsi un joueur local : siège relayé, sans connexion propre.
        val sender = byConn[conn]
        if (sender != null) {
            if (sender === host && sender.conn == conn && typed == code) relayJoin(conn, m, out) else out += errp(conn, PlayProtocol.FORBIDDEN, "Vous êtes déjà dans la salle.")
            return
        }
        if (ip != null && badCodes.ipBlocked(ip, now)) { out += err(conn, PlayReason.PLAY_BAD_CODE); return }
        if (scopeClosedAt != null) { out += err(conn, PlayReason.PLAY_SCOPE_FORBIDDEN); return }
        if (typed == null || typed != code) { badAttempt(conn, ip, now, out); return }
        m.token?.let { t -> seats[t]?.let { s -> reattach(s, conn, out, null); return } }
        if (m.deviceHash != null && m.deviceHash in bannedDevices || ip != null && ip in bannedIps) { out += err(conn, PlayReason.PLAY_BANNED); return }
        if (scope == PlayScope.INTERNET && (m.deviceHash == null || m.deviceHash.length < MIN_DEVICE_HASH)) { out += errp(conn, PlayProtocol.BAD_REQUEST, "Appareil non identifié : mettez CastBridge à jour."); return }
        val name = QuizRoom.cleanName(m.name) ?: run { out += errp(conn, PlayProtocol.BAD_REQUEST, "Choisissez un pseudonyme."); return }
        val dup = m.deviceHash != null && seats.values.any { it.deviceHash == m.deviceHash && it.role != PlayRole.SPECTATOR }
        val full = table0.room.players().size >= settings.seatsPerTable
        if (m.spectate || dup) {
            if (!settings.allowSpectators || seats.values.count { it.role == PlayRole.SPECTATOR } >= settings.maxSpectators) { out += err(conn, PlayReason.PLAY_ROOM_FULL); return }
            val s = Seat(newToken(), PlayRole.SPECTATOR, name, m.deviceHash, false).also { it.ip = ip }
            seats[s.token] = s; attach(s, conn); out += welcome(s, conn); announcement(now)?.let { out += Out(conn, it) }; pending += Ev("spectator"); return
        }
        if (full) { out += err(conn, PlayReason.PLAY_ROOM_FULL); return }
        val j = table0.room.join(table0.room.code, name, null, m.deviceHash)
        when (j.status) {
            QuizRoom.Join.FULL -> { out += err(conn, PlayReason.PLAY_ROOM_FULL); return }
            QuizRoom.Join.OK -> {}
            else -> { out += errp(conn, PlayProtocol.BAD_REQUEST, "Impossible de rejoindre."); return }
        }
        val s = Seat(newToken(), PlayRole.PLAYER, j.player!!.name, m.deviceHash, false).also { it.ip = ip }
        s.quizToken = j.player.token; s.playerId = j.player.id
        seats[s.token] = s; attach(s, conn); out += welcome(s, conn)
        announcement(now)?.let { out += Out(conn, it) }   // un retardataire reçoit l'annonce avec l'opensAt ABSOLU
        pending += Ev("joined", mapOf("playerId" to s.playerId))
    }

    private fun relayJoin(conn: String, m: ClientMsg.Join, out: MutableList<Out>) {
        m.token?.let { t -> seats[t]?.takeIf { it.viaTv }?.let { out += welcome(it, conn); return } }
        val name = QuizRoom.cleanName(m.name) ?: run { out += errp(conn, PlayProtocol.BAD_REQUEST, "Choisissez un pseudonyme."); return }
        if (table0.room.players().size >= settings.seatsPerTable) { out += err(conn, PlayReason.PLAY_ROOM_FULL); return }
        val j = table0.room.join(table0.room.code, name, null, m.deviceHash)
        if (j.status != QuizRoom.Join.OK) { out += errp(conn, PlayProtocol.BAD_REQUEST, "Impossible de rejoindre."); return }
        val s = Seat(newToken(), PlayRole.PLAYER, j.player!!.name, m.deviceHash, true)
        s.quizToken = j.player.token; s.playerId = j.player.id
        seats[s.token] = s; presence(s, true)
        out += welcome(s, conn)
        pending += Ev("joined", mapOf("playerId" to s.playerId, "viaTv" to true))
    }

    private fun resume(conn: String, m: ClientMsg.Resume, now: Long, ip: String?, out: MutableList<Out>) {
        // Jamais de blocage par adresse ni de compte des échecs ici : derrière un NAT collectif, une adresse partagée ne doit pas empêcher un joueur déjà assis de reprendre ;
        // deviner un jeton de 128 bits est impossible, le seau de débit du service suffit. Jeton inconnu ou autre salle : même réponse qu'un code faux, sans rotation du code.
        val s = seats[m.token]
        if (m.roomId != roomId || s == null) { out += err(conn, PlayReason.PLAY_BAD_CODE); return }
        reattach(s, conn, out, m.lastSeq)
    }

    /** Un spectateur déconnecté depuis [SPECTATOR_PURGE_MS] perd son siège (les joueurs gardent le leur pour la reprise). */
    private fun purgeSpectators(now: Long) {
        val gone = seats.values.filter { it.role == PlayRole.SPECTATOR && it.conn == null && (it.lostAt?.let { t -> now - t >= SPECTATOR_PURGE_MS } ?: false) }
        for (s in gone) seats.remove(s.token)
    }

    private fun reattach(s: Seat, conn: String, out: MutableList<Out>, lastSeq: Long?) {
        s.conn?.takeIf { it != conn }?.let { old -> byConn.remove(old); rtt.forget(old) }
        attach(s, conn)
        if (s === host) {
            seats.values.filter { it.viaTv }.forEach { presence(it, true) }
            table0.pausedAt?.let { table0.resume(clock.now); pending += Ev("resumed") }
        }
        out += welcome(s, conn)
        val missed = lastSeq?.let { ring.since(it) }
        if (lastSeq != null && missed != null && missed.isNotEmpty()) out += Out(conn, ServerMsg.Replay(seq, missed))
        out += Out(conn, ServerMsg.State(seq, viewFor(s, clock.now), full = lastSeq == null || missed == null))
        announcement(clock.now)?.let { out += Out(conn, it) }
        pending += Ev("back", mapOf("role" to s.role.name))
    }

    private fun badAttempt(conn: String, ip: String?, now: Long, out: MutableList<Out>) {
        out += err(conn, PlayReason.PLAY_BAD_CODE)
        if (ip != null) badCodes.ipFail(ip, now)
        if (badCodes.roomFail()) { code = RoomCode.generate(random); pending += Ev("codeRotated") }
    }

    private fun attach(s: Seat, conn: String) {
        s.conn = conn; s.lostAt = null; byConn[conn] = s
        presence(s, true)
    }

    private fun presence(s: Seat, open: Boolean) {
        val tok = s.quizToken ?: return
        table0.room.player(tok)?.let { p -> if (open) table0.room.streamOpened(p) else table0.room.streamClosed(p) }
    }

    private fun welcome(s: Seat, conn: String): Out {
        val caps = PlayProtocol.CAPS
        return Out(conn, ServerMsg.Welcome(seq, roomId, code, s.token, s.role, s.playerId, PlayProtocol.PROTO, caps))
    }

    // ------------------------------------------------------------------ actions

    private inline fun hostOnly(conn: String, out: MutableList<Out>, f: (Seat) -> Unit) {
        val s = byConn[conn]
        if (s == null || s !== host) out += errp(conn, PlayProtocol.FORBIDDEN, "Réservé à l'hôte.") else f(s)
    }

    private fun act(seat: Seat, conn: String, m: ClientMsg.Act, now: Long, out: MutableList<Out>): ActResult {
        val room = table0.room
        if (seat.role == PlayRole.SPECTATOR) return ActResult.FORBIDDEN
        val hostAction = m.action in HOST_ACTIONS
        if (hostAction && seat !== host) return ActResult.FORBIDDEN
        return when (m.action) {
            "answer" -> answer(seat, conn, m.questionId, m.choice, now, null)
            "mode" -> if (state != State.OPEN && state != State.FINISHED) ActResult.FORBIDDEN
                else (runCatching { QuizRoom.Mode.valueOf(m.arg.orEmpty()) }.getOrNull()?.let { if (room.setMode(it)) { dirty(); ActResult.OK } else ActResult.FORBIDDEN } ?: ActResult.BAD_REQUEST)
            "start" -> {
                if (state != State.OPEN && state != State.FINISHED) return ActResult.FORBIDDEN
                val seed = if (scope == PlayScope.INTERNET) random.nextLong() else m.arg?.toLongOrNull() ?: random.nextLong()   // Internet : la graine est tirée par le serveur seul
                val reason = room.startGame(seed)
                if (reason != null) { out += errp(conn, PlayProtocol.BAD_REQUEST, reason); ActResult.BAD_REQUEST }
                else { table0.reset(); botCache = null; state = State.PLAYING; pending += Ev("started"); ActResult.OK }
            }
            "skip" -> if (state != State.PLAYING || table0.abandoned != null || table0.pausedAt != null) ActResult.IGNORED
                else if (room.duel?.phase == QuizDuel.Phase.QUESTION && now < table0.opensAtServerMs) ActResult.IGNORED   // jamais pendant le délai
                else if (room.hostSkip()) ActResult.OK else ActResult.IGNORED
            "lobby" -> if (room.backToLobby()) { table0.reset(); botCache = null; state = State.OPEN; autoHost = false; pending += Ev("lobby"); ActResult.OK } else ActResult.FORBIDDEN
            "end" -> if (state == State.PLAYING) { table0.abandoned = "HOST_ENDED"; state = State.FINISHED; pending += Ev("ended"); ActResult.OK } else ActResult.FORBIDDEN
            "autohost" -> { autoHost = true; pending += Ev("autohost"); ActResult.OK }
            "candidate" -> if (room.setCandidate(m.arg?.takeIf { it.isNotEmpty() })) ActResult.OK else ActResult.FORBIDDEN
            else -> {
                if (state != State.PLAYING) return ActResult.IGNORED
                if (seat === host && room.candidate == null && room.mode == QuizRoom.Mode.MILLIONAIRE) {
                    if (room.hostAct(m.action, m.choice, m.arg)) ActResult.OK else ActResult.IGNORED
                } else {
                    when (room.act(seat.quizToken, m.action, m.questionId, m.choice, m.arg)) {
                        QuizRoom.Act.OK -> ActResult.OK
                        QuizRoom.Act.IGNORED -> ActResult.IGNORED
                        QuizRoom.Act.FORBIDDEN -> ActResult.FORBIDDEN
                        QuizRoom.Act.BAD_REQUEST -> ActResult.BAD_REQUEST
                        QuizRoom.Act.UNKNOWN_PLAYER -> ActResult.UNKNOWN_PLAYER
                        QuizRoom.Act.CLOSED -> ActResult.CLOSED
                    }
                }
            }
        }
    }

    private fun relay(conn: String, m: ClientMsg.RelayAct, now: Long): ActResult {
        val sender = byConn[conn]
        if (sender == null || sender !== host) return ActResult.FORBIDDEN
        val s = seats[m.token]?.takeIf { it.viaTv && it.role == PlayRole.PLAYER } ?: return ActResult.UNKNOWN_PLAYER
        return answer(s, conn, m.questionId, m.choice, now, m.localElapsedMono)
    }

    /**
     * Réponse d'un joueur au Duel. Ordre des contrôles : table active, question connue (future ⇒ `UNKNOWN_QUESTION`, passée ⇒ `CLOSED`), phase,
     * ouverture (`TOO_EARLY`), rejeu (`SAME`), puis temps = depuis `opensAtServerMs`, compensé RTT (relais TV : `max(local, serveur − rttTV)`).
     * [rttConn] : connexion dont le RTT sert (celle du joueur, ou celle de la TV pour un relais).
     */
    private fun answer(seat: Seat, conn: String, questionId: String?, choice: Int?, now: Long, relayLocalElapsed: Long?): ActResult {
        val t = table0
        val d = t.room.duel ?: return ActResult.IGNORED
        if (state != State.PLAYING || t.abandoned != null || t.pausedAt != null) return ActResult.CLOSED
        val pid = seat.playerId; val token = seat.quizToken
        if (pid == null || token == null) return ActResult.FORBIDDEN
        val q = questionId.orEmpty()
        if (q != d.question.id) return if (d.questions.subList(0, d.index).any { it.id == q }) ActResult.CLOSED else ActResult.UNKNOWN_QUESTION
        if (d.phase != QuizDuel.Phase.QUESTION) return ActResult.CLOSED
        if (choice == null || choice !in 0..3) return ActResult.BAD_REQUEST
        if (PlayTiming.gate(now, t.opensAtServerMs) == PlayTiming.Gate.TOO_EARLY) return ActResult.TOO_EARLY
        d.answerOf(pid)?.let { return if (it == choice) ActResult.SAME else ActResult.FORBIDDEN }
        val raw = PlayTiming.scoringElapsedMs(t.opensAtServerMs, now)!!
        val elapsed = when {
            scope != PlayScope.INTERNET -> raw
            relayLocalElapsed != null -> RttBook.relayed(relayLocalElapsed, raw, rtt.rtt(conn))
            else -> RttBook.elapsed(raw, rtt.rtt(conn))
        }
        if (elapsed > d.questionMs) return ActResult.CLOSED
        clock.now = t.opensAtServerMs + elapsed            // horloge de table = ouverture + temps compté
        val r = try { t.room.act(token, "answer", q, choice, null) } finally { clock.now = now }
        if (r != QuizRoom.Act.OK) return if (r == QuizRoom.Act.CLOSED) ActResult.CLOSED else ActResult.IGNORED
        t.record(AnswerRecord(d.index, conn, pid, elapsed, now, choice, choice == d.question.answer))
        pending += Ev("answered", mapOf("count" to d.answered().size))
        return ActResult.OK
    }

    private fun setScope(conn: String, open: Boolean, now: Long, out: MutableList<Out>) = hostOnly(conn, out) {
        if (!open) {
            if (state == State.PLAYING) { out += errp(conn, PlayProtocol.FORBIDDEN, "Fermer Internet : seulement en salle d'attente."); return@hostOnly }
            scopeClosedAt = now
            for (s in seats.values.filter { it.role == PlayRole.PLAYER && !it.viaTv && it !== host }) {
                s.quizToken?.let { table0.room.leave(it) }
                s.role = PlayRole.SPECTATOR; s.quizToken = null; s.playerId = null
            }
            pending += Ev("scopeClosed")
        } else { scopeClosedAt = null; pending += Ev("scopeOpen") }
    }

    private fun kick(h: Seat, playerId: String, out: MutableList<Out>) {
        val s = seats.values.firstOrNull { it.playerId == playerId && it !== h } ?: return
        s.quizToken?.let { table0.room.leave(it) }
        s.deviceHash?.let { bannedDevices += it }
        s.ip?.let { bannedIps += it }
        s.conn?.let { c -> out += err(c, PlayReason.PLAY_BANNED); byConn.remove(c) }
        seats.remove(s.token); pending += Ev("kicked", mapOf("playerId" to playerId))
    }

    private fun dirty() { pending += Ev("settings") }

    // ------------------------------------------------------------------ host loss, pause, abandon

    private fun hostWatch(now: Long) {
        val h = host ?: return
        val t = table0
        val lost = h.conn == null && h.lostAt != null
        if (!lost || state != State.PLAYING || autoHost || t.abandoned != null) return
        val remote = seats.values.any { it.role == PlayRole.PLAYER && !it.viaTv && it !== h }
        if (!remote && t.pausedAt == null) { t.pausedAt = now; pending += Ev("paused") }
        if (now - h.lostAt!! >= HOST_LOST_MS) {
            t.pausedAt?.let { t.resume(now) }
            t.abandoned = "HOST_LOST"; state = State.FINISHED; abandonedAt = now
            pending += Ev("abandoned", mapOf("reason" to "HOST_LOST"))
        }
    }

    private fun maxGrace(): Long = if (scope != PlayScope.INTERNET) 0L else byConn.entries.filter { it.value.role != PlayRole.SPECTATOR }.maxOfOrNull { rtt.graceMs(it.key) } ?: 0L

    private fun Table.resume(now: Long) {
        val p = pausedAt ?: return
        val d = now - p
        clock.shift += d; opensAtServerMs += d; pausedAt = null
    }

    // ------------------------------------------------------------------ table synchronisation and fan-out

    /** Détecte ouvertures et révélations ; applique le délai inter-questions en décalant l'horloge de la table. */
    private fun sync(t: Table, now: Long, evs: MutableList<Ev>) {
        val d = t.room.duel; val g = t.room.game
        if (d != null && d !== t.lastDuel) { t.lastDuel = d; t.announcedIndex = -1; t.revealedIndex = -1 }
        if (g != null && g !== t.lastGame) { t.lastGame = g; t.announcedIndex = -1; t.revealedIndex = -1 }
        if (d != null) {
            if (d.phase == QuizDuel.Phase.QUESTION && d.index != t.announcedIndex) {
                t.announcedIndex = d.index
                val a = if (d.index == 0) PlayTiming.Announcement(d.question.id, now) else PlayTiming.announce(d.question.id, now, scope, gapRequestedMs)
                t.clock.shift += a.opensAtServerMs - now
                t.opensAtServerMs = a.opensAtServerMs
                evs += Ev("question", mapOf("index" to d.index, "opensAtServerMs" to a.opensAtServerMs))
            }
            if ((d.phase == QuizDuel.Phase.REVEAL || d.phase == QuizDuel.Phase.BOARD || d.phase == QuizDuel.Phase.FINISHED) && t.revealedIndex != d.index) {
                t.revealedIndex = d.index
                evs += Ev("reveal", mapOf("index" to d.index, "answer" to d.question.answer))
            }
        } else if (g != null) {
            if (g.index != t.announcedIndex) { t.announcedIndex = g.index; t.opensAtServerMs = now; evs += Ev("question", mapOf("index" to g.index, "opensAtServerMs" to now)) }
            if ((g.phase == QuizGame.Phase.REVEALED || g.phase == QuizGame.Phase.FINISHED) && t.revealedIndex != g.index) {
                t.revealedIndex = g.index
                evs += Ev("reveal", mapOf("index" to g.index, "answer" to g.question.answer))
            }
        }
    }

    private fun flush(now: Long, out: MutableList<Out>) {
        clock.now = now
        val evs = ArrayList<Ev>(pending); pending.clear()
        for (t in tables) sync(t, now, evs)
        if (state == State.PLAYING && table0.room.stage == QuizRoom.Stage.FINISHED) { state = State.FINISHED; evs += Ev("finished") }
        val version = table0.room.version
        val safety = safety()
        val changed = evs.isNotEmpty() || version != lastVersion
        lastVersion = version
        safetySeen.retainAll(byConn.keys)
        val newcomers = byConn.keys.filter { it !in safetySeen }
        if (!changed && safety == lastSafety && newcomers.isEmpty()) return
        if (changed) {
            seq++
            if (evs.isEmpty()) ring.add(seq, "state") else evs.forEach { ring.add(seq, it.kind, it.data) }
            for (e in evs) when (e.kind) {
                "question" -> announcement(now)?.let { m -> byConn.keys.forEach { out += Out(it, m) } }
                "reveal" -> revealMsg()?.let { m -> byConn.keys.forEach { out += Out(it, m) } }
            }
            for ((c, s) in byConn.entries.toList()) out += Out(c, ServerMsg.State(seq, viewFor(s, now), false))
        }
        val targets = if (safety != lastSafety) byConn.keys.toList() else newcomers
        lastSafety = safety
        for (c in targets) { out += Out(c, ServerMsg.Safety(seq, safety)); safetySeen += c }
    }

    /** L'annonce de la question en cours (Duel ou Millionnaire) avec l'instant d'ouverture ABSOLU ; null s'il n'y en a pas. */
    private fun announcement(now: Long): ServerMsg.Question? {
        val t = table0
        val d = t.room.duel
        if (d != null && d.phase == QuizDuel.Phase.QUESTION) {
            val q = d.question
            return ServerMsg.Question(seq, q.id, d.index, d.questions.size, q.question, q.choices, t.opensAtServerMs, now, d.questionMs)
        }
        val g = t.room.game
        if (g != null && g.phase != QuizGame.Phase.FINISHED && g.phase != QuizGame.Phase.REVEALED) {
            val q = g.question
            return ServerMsg.Question(seq, q.id, g.index, 15, q.question, q.choices, now, now, g.remainingMs(clock.read()))
        }
        return null
    }

    private fun revealMsg(): ServerMsg.Reveal? {
        val q = table0.room.currentQuestion() ?: return null
        return ServerMsg.Reveal(seq, q.id, table0.revealedIndex, q.answer, q.explanation)
    }

    /** Vue d'un siège : celle de `QuizRoom.view` (déjà sans la bonne réponse avant clôture) + clés additives `room` et `timing`. */
    @Suppress("UNCHECKED_CAST")
    fun viewFor(s: Seat, now: Long): Map<String, Any?> = synchronized(lock) {
        clock.now = now
        val t = table0
        val v = LinkedHashMap(t.room.view(s.quizToken))
        val wait = if (t.room.duel?.phase == QuizDuel.Phase.QUESTION) maxOf(0L, t.opensAtServerMs - now) else 0L
        (v["duel"] as? MutableMap<String, Any?>)?.let { if (wait > 0) it["remainingMs"] = t.room.duel!!.questionMs }
        v["room"] = linkedMapOf("id" to roomId, "code" to RoomCode.display(code), "state" to state.name, "scope" to scope.name, "role" to s.role.name, "seq" to seq,
            "serverNowMs" to now, "abandoned" to t.abandoned, "paused" to (t.pausedAt != null), "internetOpen" to (scopeClosedAt == null),
            "spectators" to seats.values.count { it.role == PlayRole.SPECTATOR }, "hostConnected" to (host?.conn != null), "autoHost" to autoHost)
        // `ranked` : seulement en fin de partie et au siège concerné (en direct, ce serait un oracle pour régler un robot juste sous le seuil)
        if (state == State.FINISHED && scope == PlayScope.INTERNET && s.playerId != null) {
            val ranked = isRanked(s.playerId)
            (v["room"] as MutableMap<String, Any?>).let { it["ranked"] = ranked; it["rankNote"] = if (ranked) null else BotScore.NEUTRAL_TEXT }
        }
        v["timing"] = linkedMapOf("gapMs" to PlayTiming.gapFor(scope, gapRequestedMs), "opensAtServerMs" to t.opensAtServerMs, "serverNowMs" to now, "waitMs" to wait)
        v
    }

    /** Le signe « Partie sûre » de la salle (mêmes faits que sur la TV). */
    fun safety(): SafetyView {
        val h = host
        val lostSec = if (h?.conn == null && h?.lostAt != null) ((clock.now - h.lostAt!!) / 1_000).toInt().coerceAtLeast(0) else 0
        val link = when { h == null || h.conn != null -> Link3.OK; lostSec >= SafetySign.LOST_AFTER_SEC -> Link3.LOST; else -> Link3.RESUMING }
        return SafetySign.of(SafetyFacts(scope = scope, serverLink = link, serverLostSec = lostSec,
            remotePlayers = seats.values.count { it.role != PlayRole.HOST && !it.viaTv }, localPlayers = seats.values.count { it.viaTv },
            rttMs = (byConn.keys.maxOfOrNull { rtt.rtt(it) } ?: 0L).toInt()))
    }

    private fun gone(reason: String, out: MutableList<Out>): List<Out> {
        state = State.GONE; seq++
        ring.add(seq, "gone", mapOf("reason" to reason))
        for (c in byConn.keys) out += Out(c, ServerMsg.RoomGone(seq, reason))
        byConn.clear()
        table0.room.close()
        return out
    }

    /** Le service ferme la salle (déploiement, suppression). */
    fun close(reason: String, now: Long): List<Out> = synchronized(lock) { clock.now = now; if (state == State.GONE) emptyList() else gone(reason, ArrayList()) }

    private fun newToken(): String = ByteArray(16).also { random.nextBytes(it) }.joinToString("") { "%02x".format(it) }
    private fun err(conn: String, r: PlayReason) = Out(conn, ServerMsg.Error(seq, r.code, r.message, r.retryable, r.retryAfterMs))
    private fun errp(conn: String, code: String, message: String) = Out(conn, ServerMsg.Error(seq, code, message, false))

    companion object {
        const val PING_EVERY_MS = 5_000L
        /** Délai minimal entre deux rotations du code d'une salle. */
        const val ROTATE_MIN_MS = 60_000L
        const val HOST_LOST_MS = 60_000L
        const val SPECTATOR_GRACE_MS = 30_000L
        const val PURGE_MS = 10 * 60_000L
        const val SPECTATOR_PURGE_MS = 5 * 60_000L
        /** Longueur minimale d'un identifiant d'appareil en Internet (la page en envoie 16 caractères). */
        const val MIN_DEVICE_HASH = 8
        private val HOST_ACTIONS = setOf("mode", "start", "skip", "lobby", "end", "autohost", "candidate")
    }
}
