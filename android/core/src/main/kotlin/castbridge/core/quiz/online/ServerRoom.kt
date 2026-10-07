package castbridge.core.quiz.online

import castbridge.core.owner.Signer
import castbridge.core.quiz.QuizBank
import castbridge.core.quiz.QuizDuel
import castbridge.core.quiz.QuizGame
import castbridge.core.quiz.QuizHistoryBook
import castbridge.core.quiz.QuizRoom
import castbridge.core.wallet.PlayResult
import castbridge.core.wallet.WalletCurrency

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
 *
 * **Quiz misé (games-G5, W22 § 3.3 option B)** : avec [stake] la salle est un Duel dont chaque TV a bloqué sa mise (blocage `cbe1` DÉJÀ vérifié par le service, [Admission]) : une mise par SIÈGE (les téléphones
 * relayés par la TV, au plus le `k` de son blocage), payée par le compte de la TV. La partie ne commence qu'avec au moins DEUX TV qui misent ; les sièges qui misent sont figés au départ ; à la fin
 * la cagnotte est partagée selon le classement ([QuizSettlement]) et la salle SIGNE le résultat `cbr1` avec la clé dédiée du service ([signer]) ; une partie interrompue rend chaque blocage en entier
 * (`ABORT`). Le service ne détient aucun secret du grand livre, ne parle jamais à l'API et n'écrit aucun solde : l'API règle. Sans [stake], RIEN ne change (salle libre, protocole d'avant).
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
    /** Mise de la salle (monnaie et montant par siège) ; null = salle LIBRE, comportement d'avant. */
    val stake: StakeSpec? = null,
    /** Clé dédiée du service qui signe `cbr1` ; null = le service ne sait pas signer (alors une salle misée n'existe pas : le hub la refuse). */
    private val signer: Signer? = null,
    /** Appelé (sous le verrou de la salle) avec `(rid, jeton cbr1)` : le service le dépose dans son volume pour le collecteur de l'hôte. */
    private val onResult: (String, String) -> Unit = { _, _ -> },
) {
    data class Settings(val allowSpectators: Boolean = true, val maxSpectators: Int = 50, val seatsPerTable: Int = 8, val duelCount: Int = 10, val duelQuestionMs: Long = 20_000,
                        /** w20-04b : joueurs locaux qu'une TV (hôte ou invitée) peut relayer, 8 au plus. */
                        val maxRelayedPerTv: Int = 8)

    enum class State { OPEN, PLAYING, FINISHED, GONE }
    enum class ActResult { OK, SAME, CLOSED, UNKNOWN_QUESTION, TOO_EARLY, FORBIDDEN, BAD_REQUEST, UNKNOWN_PLAYER, IGNORED }

    /** Un message à envoyer à la connexion `to`. */
    data class Out(val to: String, val msg: ServerMsg)

    /**
     * Admission d'une connexion, jugée par le hub AVANT la salle (Quiz misé) : identité de la TV (code d'appareil de son activation) et blocage `cbe1` vérifié. La salle ne revérifie aucune signature.
     * Sans mise, ignorée.
     */
    data class Admission(val identity: String?, val escrow: QuizEscrow? = null)

    /** Un siège : l'hôte, un joueur ou un spectateur. `token` (128 bits) est le seul secret ; `viaTv` : joueur local relayé par la TV. */
    class Seat internal constructor(val token: String, var role: PlayRole, val name: String, val deviceHash: String?, val viaTv: Boolean) {
        var quizToken: String? = null; internal set
        var playerId: String? = null; internal set
        var conn: String? = null; internal set
        var lostAt: Long? = null; internal set
        var muted = false; internal set
        /** w20-04b : cette connexion est une TV qui peut relayer SES joueurs locaux (accordé par le service seul, [grantRelay]) ; l'hôte relaie toujours. */
        var relayer = false; internal set
        /** w20-04b : pour un siège relayé, la TV (siège) qui le relaie ; null pour tout autre siège. */
        var relayedBy: Seat? = null; internal set
        /** Clé d'adresse du client (IPv4, ou préfixe /64 en IPv6) : sert au bannissement par adresse. */
        var ip: String? = null; internal set
        /** Quiz misé : identité de la TV (code d'appareil) et son blocage ; null pour un téléphone relayé, un spectateur sans mise, ou une salle libre. */
        var identity: String? = null; internal set
        var escrow: QuizEscrow? = null; internal set
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

    // ------------------------------------------------------------------ Quiz misé (games-G5)

    /** Les sièges qui misent, par identifiant de blocage et dans l'ordre des sièges : FIGÉS au départ de la partie (personne n'entre ni ne change après). */
    private val stakers = LinkedHashMap<String, List<String>>()
    private var stakeStarted = false
    /** Issue du règlement (une fois signé) : `SPLIT` (cagnotte partagée), `REFUND` (personne n'a marqué : chacun reprend sa mise) ou `ABORT` (partie interrompue). */
    private var stakeOutcome: String? = null
    /** Parts de la cagnotte par joueur, avant frais éventuels de la plateforme (affichage ; seul le règlement de l'API fait foi). */
    private var stakePayouts: Map<String, Long>? = null
    private val pendingOuts = ArrayList<Out>()
    /** Résultat `cbr1` signé (parties misées) une fois la partie finie ou interrompue ; reste dans la salle pour la reprise d'une TV qui l'aurait manqué. */
    var resultToken: String? = null; private set

    val isStaked: Boolean get() = stake != null
    /** Les identifiants de blocage portés par les sièges (pour que le hub n'emploie jamais deux fois le même blocage). */
    fun escrowIds(): List<String> = synchronized(lock) { escrowSeats().mapNotNull { it.escrow?.eid } }
    /** Les sièges qui misent, figés au départ (tests, journal) : identifiant de blocage → joueurs. */
    fun stakers(): Map<String, List<String>> = synchronized(lock) { LinkedHashMap(stakers) }

    /** Les TV qui ont bloqué une mise, dans l'ordre d'arrivée (l'hôte d'abord). */
    private fun escrowSeats(): List<Seat> = seats.values.filter { it.escrow != null }

    /** Les sièges qui misent pour [tv] : son propre siège de joueur s'il en a un, puis les téléphones qu'elle relaie, dans l'ordre d'arrivée ; au plus le `k` de son blocage. */
    private fun stakingSeatsOf(tv: Seat): List<Seat> = ((if (tv.playerId != null) listOf(tv) else emptyList()) + relayedOf(tv).filter { it.playerId != null }).take(tv.escrow?.seats ?: 0)

    private fun stakeRequired(conn: String): Out {
        val st = stake!!
        val r = GameReason.STAKE_ESCROW_REQUIRED
        return Out(conn, ServerMsg.Error(seq, r.code, r.message, r.retryable, 0L, linkedMapOf("game" to PlayProtocol.GAME_QUIZ, "cur" to st.cur, "per" to st.per)))
    }

    private fun err(conn: String, r: GameReason) = Out(conn, ServerMsg.Error(seq, r.code, r.message, r.retryable))

    /**
     * Une TV prend part à la partie si sa liaison est vivante AU DÉPART (l'hôte l'est toujours : c'est lui qui démarre). Une TV invitée partie ou coupée à ce moment ne joue pas : ses téléphones quittent la table
     * (sans leur TV ils n'y joueraient que pour perdre) et son blocage figure au résultat avec « utilisé 0 », rendu en entier : « Quitter avant le départ : votre mise est rendue » reste vrai.
     */
    private fun takesPart(tv: Seat): Boolean = tv.conn != null

    /** Pourquoi une partie misée ne démarre pas encore (texte français), ou null : au moins deux TV doivent miser, chacune avec un joueur ; une salle misée ne joue qu'UNE partie. */
    private fun stakeStartRefusal(): String? = when {
        resultToken != null -> "Cette partie avec mise est terminée : ouvrez une nouvelle salle pour rejouer."
        escrowSeats().count { takesPart(it) && stakingSeatsOf(it).isNotEmpty() } < 2 -> "Une partie avec mise a besoin d'au moins deux TV qui misent, chacune avec un joueur."
        else -> null
    }

    /** Avant le départ : les téléphones des TV qui ne sont plus là quittent la table (ils ne partiraient pas dans le Duel pour rien). */
    private fun dropAbsentTvs() {
        for (tv in escrowSeats().filter { !takesPart(it) }) for (r in relayedOf(tv)) { r.quizToken?.let { table0.room.leave(it) }; seats.remove(r.token) }
    }

    private fun freezeStakers() {
        stakers.clear()
        for (tv in escrowSeats()) stakers[tv.escrow!!.eid] = if (takesPart(tv)) stakingSeatsOf(tv).mapNotNull { it.playerId } else emptyList()
        stakeStarted = true
    }

    /**
     * Signe le résultat `cbr1` UNE fois, dès que la partie est finie ou interrompue : `END` quand le Duel est allé au bout (cagnotte partagée selon le classement des sièges qui misent ; un siège sorti du
     * classement par [BotScore] compte 0 : un robot ne gagne pas de cagnotte, et sa TV garde sa mise en jeu : un joueur ne se fait pas rembourser en imitant un robot), `ABORT` sinon (salle fermée, expirée,
     * annulée avant le départ, arrêt du service : chaque blocage est rendu en entier ; un hôte perdu n'en fait pas partie, voir [hostWatch]). Le résultat part à toutes les TV qui ont bloqué une mise et est
     * déposé pour le collecteur de l'hôte.
     */
    private fun settleIfDue() {
        val st = stake ?: return
        if (resultToken != null) return
        val sig = signer ?: return          // un service sans clé n'ouvre pas de salle misée : défense en profondeur
        val book = escrowSeats().mapNotNull { it.escrow }
        if (book.isEmpty()) return
        val cur = WalletCurrency.values().firstOrNull { it.name == st.cur } ?: return
        val d = table0.room.duel
        val ended = stakeStarted && table0.abandoned == null && d != null && d.phase == QuizDuel.Phase.FINISHED
        val scores = if (ended) stakers.values.flatten().associateWith { pid -> if (isRanked(pid)) d!!.score(pid) else 0 } else null
        val result = QuizSettlement.result(sig.keyId, roomId, cur, st.per, clock.now, book, stakers, scores)
        val token = PlayResult.sign(result, sig)
        resultToken = token
        val shares = if (scores != null) QuizSettlement.shares(st.per, stakers, scores) else null
        stakeOutcome = when { shares == null -> "ABORT"; shares.values.sum() > 0L -> "SPLIT"; else -> "REFUND" }
        stakePayouts = shares?.takeIf { stakeOutcome == "SPLIT" }
        runCatching { onResult(result.rid, token) }
        for (s in escrowSeats()) s.conn?.let { pendingOuts += Out(it, ServerMsg.Result(seq, token)) }
    }

    /** Le bloc `stake` de la vue (additif, absent d'une salle libre) : la mise, la cagnotte et, la partie réglée, l'issue et la part de chaque joueur. */
    private fun stakeView(): Map<String, Any?>? {
        val st = stake ?: return null
        val n = if (stakeStarted) stakers.values.sumOf { it.size } else escrowSeats().filter { takesPart(it) }.sumOf { stakingSeatsOf(it).size }
        val tvs = if (stakeStarted) stakers.values.count { it.isNotEmpty() } else escrowSeats().count { takesPart(it) }   // les TV qui jouent (avant le départ : celles dont la liaison est là)
        return linkedMapOf("game" to PlayProtocol.GAME_QUIZ, "cur" to st.cur, "per" to st.per, "tvs" to tvs, "seats" to n, "pot" to st.per * n,
            "started" to stakeStarted, "settled" to (resultToken != null), "outcome" to stakeOutcome, "payouts" to stakePayouts)
    }

    fun phase(): State = synchronized(lock) { state }
    fun currentQuestionId(): String? = synchronized(lock) { table0.room.currentQuestion()?.id }
    fun currentQuestionIndex(): Int = synchronized(lock) { table0.room.duel?.index ?: table0.room.game?.index ?: -1 }
    fun seatCount(): Int = synchronized(lock) { seats.values.count { it.role != PlayRole.SPECTATOR && it.quizToken != null } }
    fun reportCount(): Int = synchronized(lock) { reports.size }
    fun spectatorCount(): Int = synchronized(lock) { seats.values.count { it.role == PlayRole.SPECTATOR } }
    fun eventsSince(lastSeq: Long): List<EventRing.Event>? = synchronized(lock) { ring.since(lastSeq) }
    fun seq(): Long = synchronized(lock) { seq }
    /** Le siège hôte a une connexion vivante (M-2 : une salle dont l'hôte est parti ne compte plus dans les salles ouvertes de son créateur). */
    fun hostConnected(): Boolean = synchronized(lock) { host?.conn != null }

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

    fun handle(conn: String, msg: ClientMsg, now: Long, ip: String? = null, admission: Admission = Admission(null)): List<Out> = synchronized(lock) {
        val out = ArrayList<Out>()
        clock.now = now
        if (state == State.GONE) { out += err(conn, PlayReason.PLAY_ROOM_GONE); return out }
        when (msg) {
            is ClientMsg.Hello -> {}   // négociation faite par le service (intersection des capacités)
            is ClientMsg.Create -> create(conn, msg, now, admission, out)
            is ClientMsg.Join -> join(conn, msg, now, ip, admission, out)
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
            is ClientMsg.GameAct -> {
                // seule action de jeu d'une salle de Quiz MISÉ : `cancel` (l'hôte renonce avant le départ : chaque blocage est rendu) ; les coups d'échecs vivent dans `ChessServerRoom`
                if (stake != null && msg.op == "cancel") out += Out(conn, ServerMsg.Ack(seq, msg.seq, cancelStaked(conn)))
                else out += errp(conn, PlayProtocol.FORBIDDEN, "Cette salle est une salle de Quiz : les actions de jeu à tour de rôle n'y ont pas cours.")
            }
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
        relayedOf(seat).forEach { presence(it, false) }   // seulement les joueurs de CETTE TV : ceux des autres TV restent présents
        pending += Ev("lost", mapOf("role" to seat.role.name, "playerId" to seat.playerId))
        flush(now, out)
        out
    }

    /**
     * w20-04b : le SERVICE accorde le droit de relayer à la connexion [conn] (une TV invitée, authentifiée par ticket + activation à l'entrée). Jamais appelée depuis un
     * message client. Rend faux si la connexion n'a pas de siège.
     */
    fun grantRelay(conn: String): Boolean = synchronized(lock) { byConn[conn]?.let { it.relayer = true; true } ?: false }

    /** Les sièges relayés par la TV [tv] (et par elle seule). */
    private fun relayedOf(tv: Seat): List<Seat> = seats.values.filter { it.viaTv && it.relayedBy === tv }
    private fun canRelay(s: Seat?): Boolean = s != null && (s === host || s.relayer)

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

    private fun create(conn: String, m: ClientMsg.Create, now: Long, admission: Admission, out: MutableList<Out>) {
        if (host != null || state != State.OPEN) { out += errp(conn, PlayProtocol.FORBIDDEN, "Cette salle a déjà un hôte."); return }
        if (stake != null) {
            // Quiz misé : un Duel (la cagnotte se partage selon le classement) dont l'hôte a bloqué SA mise ; sans blocage, la mise à bloquer est dite
            if (admission.escrow == null) { out += stakeRequired(conn); return }
            if (m.mode != null && m.mode != QuizRoom.Mode.DUEL.name) { out += errp(conn, PlayProtocol.BAD_REQUEST, "Une partie avec mise se joue en Duel."); return }
        }
        val seat = Seat(newToken(), PlayRole.HOST, QuizRoom.cleanName(m.name) ?: "TV", null, false)
        if (m.name != null) {
            val j = table0.room.join(table0.room.code, m.name, null, null)
            if (j.status != QuizRoom.Join.OK) { out += errp(conn, PlayProtocol.BAD_REQUEST, "Nom invalide."); return }
            seat.quizToken = j.player!!.token; seat.playerId = j.player.id
        }
        if (stake != null) { seat.identity = admission.identity; seat.escrow = admission.escrow }
        seats[seat.token] = seat; host = seat
        attach(seat, conn)
        (if (stake != null) QuizRoom.Mode.DUEL else m.mode?.let { runCatching { QuizRoom.Mode.valueOf(it) }.getOrNull() })?.let { table0.room.setMode(it) }
        out += welcome(seat, conn)
        pending += Ev("created")
    }

    private fun join(conn: String, m: ClientMsg.Join, now: Long, ip: String?, admission: Admission, out: MutableList<Out>) {
        if (RoomCode.expired(createdAt, now)) { out += err(conn, PlayReason.PLAY_ROOM_GONE); return }
        val typed = RoomCode.normalize(m.code)
        // Une connexion déjà assise n'essaie pas de deviner un code : refus sans compter (sinon un spectateur ferait tourner le code de la salle).
        // Seule une TV (l'hôte, ou une TV invitée à qui le service a accordé le relais, w20-04b) enregistre ainsi un joueur local : siège relayé, sans connexion propre.
        val sender = byConn[conn]
        if (sender != null) {
            if (canRelay(sender) && sender.conn == conn && typed == code) relayJoin(sender, conn, m, out) else out += errp(conn, PlayProtocol.FORBIDDEN, "Vous êtes déjà dans la salle.")
            return
        }
        if (ip != null && badCodes.ipBlocked(ip, now)) { out += err(conn, PlayReason.PLAY_BAD_CODE); return }
        if (scopeClosedAt != null) { out += err(conn, PlayReason.PLAY_SCOPE_FORBIDDEN); return }
        if (typed == null || typed != code) { badAttempt(conn, ip, now, out); return }
        m.token?.let { t -> seats[t]?.takeIf { !it.viaTv }?.let { s -> reattach(s, conn, out, null); return } }   // un siège relayé ne se reprend que par sa TV (relayJoin)
        if (m.deviceHash != null && m.deviceHash in bannedDevices || ip != null && ip in bannedIps) { out += err(conn, PlayReason.PLAY_BANNED); return }
        if (scope == PlayScope.INTERNET && (m.deviceHash == null || m.deviceHash.length < MIN_DEVICE_HASH)) { out += errp(conn, PlayProtocol.BAD_REQUEST, "Appareil non identifié : mettez CastBridge à jour."); return }
        val name = QuizRoom.cleanName(m.name) ?: if (m.spectate) "TV" else run { out += errp(conn, PlayProtocol.BAD_REQUEST, "Choisissez un pseudonyme."); return }   // audit C-1 : une TV qui regarde n'a pas de pseudonyme
        if (stake != null) { stakedJoin(conn, m, name, ip, admission, now, out); return }   // salle misée : seule une TV qui a bloqué sa mise y entre (aucun téléphone ne parle au service)
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

    /**
     * Entrée d'une TV dans une salle MISÉE : son blocage `cbe1` (vérifié par le hub) est obligatoire, sinon la mise à bloquer lui est dite (`STAKE_ESCROW_REQUIRED`, rien n'est consommé) ; une TV par
     * identité ; jamais une fois la partie commencée (les sièges qui misent sont figés). Elle prend un siège de SPECTATRICE relais (le service lui accorde le droit de relayer) : ses téléphones jouent par elle.
     */
    private fun stakedJoin(conn: String, m: ClientMsg.Join, name: String, ip: String?, admission: Admission, now: Long, out: MutableList<Out>) {
        if (state != State.OPEN) { out += err(conn, GameReason.STAKE_ROOM_STARTED); return }
        val escrow = admission.escrow ?: run { out += stakeRequired(conn); return }
        if (admission.identity != null && seats.values.any { it.identity == admission.identity }) { out += err(conn, GameReason.SAME_TV); return }
        if (escrowSeats().size >= PlayProtocol.MAX_STAKE_TVS || !settings.allowSpectators || seats.values.count { it.role == PlayRole.SPECTATOR } >= settings.maxSpectators) { out += err(conn, PlayReason.PLAY_ROOM_FULL); return }
        val s = Seat(newToken(), PlayRole.SPECTATOR, name, m.deviceHash, false).also { it.ip = ip; it.identity = admission.identity; it.escrow = escrow }
        seats[s.token] = s; attach(s, conn); out += welcome(s, conn); announcement(now)?.let { out += Out(conn, it) }; pending += Ev("spectator")
    }

    private fun relayJoin(tv: Seat, conn: String, m: ClientMsg.Join, out: MutableList<Out>) {
        // reprise d'un siège relayé : seulement par la TV qui le relaie (le jeton d'une autre TV n'ouvre rien)
        m.token?.let { t -> seats[t]?.takeIf { it.viaTv && it.relayedBy === tv }?.let { out += welcome(it, conn); return } }
        val name = QuizRoom.cleanName(m.name) ?: run { out += errp(conn, PlayProtocol.BAD_REQUEST, "Choisissez un pseudonyme."); return }
        if (stake != null) {
            // Quiz misé : on ne s'assoit que dans la salle d'attente (les sièges qui misent sont figés au départ), et au plus autant de joueurs ici que de mises bloquées par cette TV
            if (state != State.OPEN) { out += err(conn, GameReason.STAKE_ROOM_STARTED); return }
            val esc = tv.escrow ?: run { out += stakeRequired(conn); return }
            if (stakingSeatsOf(tv).size >= esc.seats) { out += err(conn, PlayReason.PLAY_ROOM_FULL); return }
        }
        if (table0.room.players().size >= settings.seatsPerTable || relayedOf(tv).size >= settings.maxRelayedPerTv.coerceIn(1, 8)) { out += err(conn, PlayReason.PLAY_ROOM_FULL); return }
        val j = table0.room.join(table0.room.code, name, null, m.deviceHash)
        if (j.status != QuizRoom.Join.OK) { out += errp(conn, PlayProtocol.BAD_REQUEST, "Impossible de rejoindre."); return }
        val s = Seat(newToken(), PlayRole.PLAYER, j.player!!.name, m.deviceHash, true)
        s.relayedBy = tv
        s.quizToken = j.player.token; s.playerId = j.player.id
        seats[s.token] = s; presence(s, true)
        out += welcome(s, conn)
        pending += Ev("joined", mapOf("playerId" to s.playerId, "viaTv" to true))
    }

    private fun resume(conn: String, m: ClientMsg.Resume, now: Long, ip: String?, out: MutableList<Out>) {
        // Jamais de blocage par adresse ni de compte des échecs ici : derrière un NAT collectif, une adresse partagée ne doit pas empêcher un joueur déjà assis de reprendre ;
        // deviner un jeton de 128 bits est impossible, le seau de débit du service suffit. Jeton inconnu ou autre salle : même réponse qu'un code faux, sans rotation du code.
        val s = seats[m.token]
        if (m.roomId != roomId || s == null || s.viaTv) { out += err(conn, PlayReason.PLAY_BAD_CODE); return }   // B-6 : un siège relayé ne se reprend que par sa TV (join à jeton)
        reattach(s, conn, out, m.lastSeq)
    }

    /** Un spectateur déconnecté depuis [SPECTATOR_PURGE_MS] perd son siège (les joueurs gardent le leur pour la reprise). */
    private fun purgeSpectators(now: Long) {
        // un spectateur relais (TV invitée) n'est pas purgé tant qu'il porte des sièges : ses joueurs locaux perdraient leur TV
        // une TV qui a bloqué une mise n'est jamais purgée : son blocage doit figurer dans le résultat signé (sinon il ne serait rendu qu'à l'échéance)
        val gone = seats.values.filter { it.role == PlayRole.SPECTATOR && it.conn == null && it.escrow == null && (it.lostAt?.let { t -> now - t >= SPECTATOR_PURGE_MS } ?: false) && relayedOf(it).isEmpty() }
        for (s in gone) seats.remove(s.token)
    }

    private fun reattach(s: Seat, conn: String, out: MutableList<Out>, lastSeq: Long?) {
        s.conn?.takeIf { it != conn }?.let { old -> byConn.remove(old); rtt.forget(old) }
        attach(s, conn)
        relayedOf(s).forEach { presence(it, true) }   // les joueurs de CETTE TV seulement
        if (s === host) table0.pausedAt?.let { table0.resume(clock.now); pending += Ev("resumed") }
        out += welcome(s, conn)
        val missed = lastSeq?.let { ring.since(it) }
        if (lastSeq != null && missed != null && missed.isNotEmpty()) out += Out(conn, ServerMsg.Replay(seq, missed))
        out += Out(conn, ServerMsg.State(seq, viewFor(s, clock.now), full = lastSeq == null || missed == null))
        announcement(clock.now)?.let { out += Out(conn, it) }
        resultToken?.takeIf { s.escrow != null }?.let { out += Out(conn, ServerMsg.Result(seq, it)) }   // une TV qui revient après la fin retrouve son résultat signé
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
                else if (stake != null) { if (m.arg == QuizRoom.Mode.DUEL.name) ActResult.OK else ActResult.FORBIDDEN }   // une salle misée est un Duel, pour toujours
                else (runCatching { QuizRoom.Mode.valueOf(m.arg.orEmpty()) }.getOrNull()?.let { if (room.setMode(it)) { dirty(); ActResult.OK } else ActResult.FORBIDDEN } ?: ActResult.BAD_REQUEST)
            "start" -> {
                if (state != State.OPEN && state != State.FINISHED) return ActResult.FORBIDDEN
                if (stake != null) stakeStartRefusal()?.let { why -> out += errp(conn, PlayProtocol.BAD_REQUEST, why); return ActResult.BAD_REQUEST }
                val seed = if (scope == PlayScope.INTERNET) random.nextLong() else m.arg?.toLongOrNull() ?: random.nextLong()   // Internet : la graine est tirée par le serveur seul
                if (stake != null) dropAbsentTvs()   // une TV qui a quitté la salle avant le départ ne joue pas : ses téléphones partent, sa mise sera rendue
                val reason = room.startGame(seed)
                if (reason != null) { out += errp(conn, PlayProtocol.BAD_REQUEST, reason); ActResult.BAD_REQUEST }
                else { table0.reset(); botCache = null; state = State.PLAYING; if (stake != null) freezeStakers(); pending += Ev("started"); ActResult.OK }
            }
            "skip" -> if (state != State.PLAYING || table0.abandoned != null || table0.pausedAt != null) ActResult.IGNORED
                else if (room.duel?.phase == QuizDuel.Phase.QUESTION && now < table0.opensAtServerMs) ActResult.IGNORED   // jamais pendant le délai
                else if (room.hostSkip()) ActResult.OK else ActResult.IGNORED
            // une salle misée ne joue qu'UNE partie : pas de retour en salle d'attente une fois la partie commencée (les mises sont figées, le résultat est signé une fois)
            "lobby" -> if (stake != null && (stakeStarted || resultToken != null)) ActResult.FORBIDDEN
                else if (room.backToLobby()) { table0.reset(); botCache = null; state = State.OPEN; autoHost = false; pending += Ev("lobby"); ActResult.OK } else ActResult.FORBIDDEN
            // « fin pour tout le monde » : refusée avec une mise en jeu (un hôte qui perd n'arrête pas la partie pour se faire rembourser) ; quitter fait perdre la mise, comme aux échecs
            "end" -> if (state == State.PLAYING && stake != null) { out += errp(conn, PlayProtocol.FORBIDDEN, "Une partie avec mise ne s'arrête pas en cours de route : elle va jusqu'au bout."); ActResult.FORBIDDEN }
                else if (state == State.PLAYING) { table0.abandoned = "HOST_ENDED"; state = State.FINISHED; pending += Ev("ended"); ActResult.OK } else ActResult.FORBIDDEN
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
        if (!canRelay(sender)) return ActResult.FORBIDDEN
        val s = seats[m.token]?.takeIf { it.viaTv && it.role == PlayRole.PLAYER } ?: return ActResult.UNKNOWN_PLAYER
        if (s.relayedBy !== sender) return ActResult.FORBIDDEN   // une TV ne répond que pour SES joueurs locaux
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

    /**
     * `game{op:"cancel"}` d'une salle MISÉE : l'hôte renonce avant le départ de la partie ; elle est interrompue et chaque blocage est rendu en entier (`cbr1` ABORT, envoyé aux TV qui ont bloqué une mise).
     * Rend le résultat de l'accusé (`OK`, ou `FORBIDDEN` : pas l'hôte, ou la partie a commencé).
     */
    private fun cancelStaked(conn: String): String {
        val s = byConn[conn] ?: return "UNKNOWN_PLAYER"
        if (s !== host || state != State.OPEN) return "FORBIDDEN"
        table0.abandoned = "CANCELLED"; state = State.FINISHED; pending += Ev("ended")
        return "OK"
    }

    private fun setScope(conn: String, open: Boolean, now: Long, out: MutableList<Out>) = hostOnly(conn, out) {
        if (!open) {
            if (state == State.PLAYING) { out += errp(conn, PlayProtocol.FORBIDDEN, "Fermer Internet : seulement en salle d'attente."); return@hostOnly }
            scopeClosedAt = now
            // les sièges relayés par une AUTRE TV sont des joueurs distants : ils passent spectateurs comme les joueurs directs ; seuls ceux de l'hôte restent
            for (s in seats.values.filter { it.role == PlayRole.PLAYER && !(it.viaTv && it.relayedBy === host) && it !== host }) {
                s.quizToken?.let { table0.room.leave(it) }
                s.role = PlayRole.SPECTATOR; s.quizToken = null; s.playerId = null
            }
            pending += Ev("scopeClosed")
        } else { scopeClosedAt = null; pending += Ev("scopeOpen") }
    }

    private fun kick(h: Seat, playerId: String, out: MutableList<Out>) {
        val s = seats.values.firstOrNull { it.playerId == playerId && it !== h } ?: return
        // retirer une TV relais retire aussi les joueurs locaux qu'elle relaie (sans TV, ils n'ont plus de voie vers le service)
        for (r in relayedOf(s)) { r.quizToken?.let { table0.room.leave(it) }; seats.remove(r.token); pending += Ev("kicked", mapOf("playerId" to r.playerId)) }
        s.quizToken?.let { table0.room.leave(it) }
        s.deviceHash?.let { bannedDevices += it }
        if (!s.relayer) s.ip?.let { bannedIps += it }   // B-5 : l'adresse d'une TV (CGNAT) n'est pas bannie, son appareil l'est
        s.conn?.let { c -> out += err(c, PlayReason.PLAY_BANNED); byConn.remove(c) }
        seats.remove(s.token); pending += Ev("kicked", mapOf("playerId" to playerId))
    }

    private fun dirty() { pending += Ev("settings") }

    // ------------------------------------------------------------------ host loss, pause, abandon

    private fun hostWatch(now: Long) {
        val h = host ?: return
        val t = table0
        val lost = h.conn == null && h.lostAt != null
        // Un Quiz MISÉ continue sans son hôte, comme avec l'« hôte automatique » que la TV demande toujours en démarrant : l'hôte ne peut pas débrancher sa TV pour interrompre la partie qu'il perd et reprendre
        // sa mise (jamais d'issue plus favorable à qui coupe) ; ses joueurs ne marquent plus aux questions manquées et sa mise reste en jeu, comme celle d'une TV invitée perdue.
        if (!lost || state != State.PLAYING || autoHost || stake != null || t.abandoned != null) return
        // « derrière l'hôte » = relayé par l'hôte ; les joueurs d'une autre TV (et cette TV) sont distants : la table continue sans l'hôte
        val remote = seats.values.any { it.role == PlayRole.PLAYER && !(it.viaTv && it.relayedBy === h) && it !== h }
        if (!remote && t.pausedAt == null) { t.pausedAt = now; pending += Ev("paused") }
        if (now - h.lostAt!! >= HOST_LOST_MS) {
            t.pausedAt?.let { t.resume(now) }
            t.abandoned = "HOST_LOST"; state = State.FINISHED; abandonedAt = now
            pending += Ev("abandoned", mapOf("reason" to "HOST_LOST"))
        }
    }

    private fun maxGrace(): Long = if (scope != PlayScope.INTERNET) 0L else byConn.entries.filter { it.value.role != PlayRole.SPECTATOR || it.value.relayer }.maxOfOrNull { rtt.graceMs(it.key) } ?: 0L

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
                val a = PlayTiming.announce(d.question.id, now, scope, gapRequestedMs)   // M-9 : la question 1 aussi s'ouvre après le délai (horloge serveur)
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

    /** Diffuse les changements (états, annonces, révélations, signe « Partie sûre »), PUIS les résultats signés d'une partie misée (après le dernier état : une TV les lit dans cet ordre). */
    private fun flush(now: Long, out: MutableList<Out>) {
        flushState(now, out)
        if (pendingOuts.isNotEmpty()) { out += pendingOuts; pendingOuts.clear() }
    }

    private fun flushState(now: Long, out: MutableList<Out>) {
        clock.now = now
        val evs = ArrayList<Ev>(pending); pending.clear()
        for (t in tables) sync(t, now, evs)
        if (state == State.PLAYING && table0.room.stage == QuizRoom.Stage.FINISHED) { state = State.FINISHED; evs += Ev("finished") }
        if (state == State.FINISHED) settleIfDue()   // la partie est finie ou interrompue (fin du Duel, annulation, fermeture) : le résultat signé est produit UNE fois
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
        stakeView()?.let { v["stake"] = it }   // additif : seulement une salle misée (le bloc dit la mise, la cagnotte, l'issue et la part de chacun une fois réglée)
        // une salle MISÉE se dit « Partie avec mise » (et non « Compétition entre amis », le libellé d'une salle libre) : la page des téléphones l'écrit sous « Vous êtes dans la salle »
        if (stake != null) (v["settings"] as? Map<String, Any?>)?.let { s -> v["settings"] = LinkedHashMap(s).also { it["playLabel"] = STAKED_PLAY_LABEL } }
        v
    }

    /** Le signe « Partie sûre » de la salle (mêmes faits que sur la TV). */
    fun safety(): SafetyView = SafetySign.of(safetyFacts())

    /** Les faits du signe (w20-04b : « local » = relayé par l'hôte ; les joueurs d'une autre TV, et cette TV, sont distants). */
    internal fun safetyFacts(): SafetyFacts {
        val h = host
        val lostSec = if (h?.conn == null && h?.lostAt != null) ((clock.now - h.lostAt!!) / 1_000).toInt().coerceAtLeast(0) else 0
        val link = when { h == null || h.conn != null -> Link3.OK; lostSec >= SafetySign.LOST_AFTER_SEC -> Link3.LOST; else -> Link3.RESUMING }
        return SafetyFacts(scope = scope, serverLink = link, serverLostSec = lostSec,
            remotePlayers = seats.values.count { it.role != PlayRole.HOST && !(it.viaTv && it.relayedBy === h) }, localPlayers = seats.values.count { it.viaTv && it.relayedBy === h },
            rttMs = (byConn.keys.maxOfOrNull { rtt.rtt(it) } ?: 0L).toInt())
    }

    private fun gone(reason: String, out: MutableList<Out>): List<Out> {
        // une salle misée qui s'éteint sans résultat (fermée, expirée, arrêt du service) : ABORT, chaque blocage est rendu en entier ; le résultat part AVANT la fin de la salle
        settleIfDue()
        if (pendingOuts.isNotEmpty()) { out += pendingOuts; pendingOuts.clear() }
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
        /** Le libellé de la partie dans la vue d'une salle MISÉE (`settings.playLabel`). */
        const val STAKED_PLAY_LABEL = "Partie avec mise"
        const val SPECTATOR_GRACE_MS = 30_000L
        const val PURGE_MS = 10 * 60_000L
        const val SPECTATOR_PURGE_MS = 5 * 60_000L
        /** Longueur minimale d'un identifiant d'appareil en Internet (la page en envoie 16 caractères). */
        const val MIN_DEVICE_HASH = 8
        private val HOST_ACTIONS = setOf("mode", "start", "skip", "lobby", "end", "autohost", "candidate")
    }
}
