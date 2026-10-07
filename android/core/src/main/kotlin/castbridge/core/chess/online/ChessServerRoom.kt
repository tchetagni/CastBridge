package castbridge.core.chess.online

import castbridge.core.chess.ChessGame
import castbridge.core.chess.ChessRoom
import castbridge.core.chess.ClockMode
import castbridge.core.chess.GameResult
import castbridge.core.chess.Move
import castbridge.core.chess.MoveTimer
import castbridge.core.chess.Piece
import castbridge.core.chess.Position
import castbridge.core.owner.Signer
import castbridge.core.quiz.online.BadCodeCounter
import castbridge.core.quiz.online.ClientMsg
import castbridge.core.quiz.online.GameReason
import castbridge.core.quiz.online.PlayProtocol
import castbridge.core.quiz.online.PlayReason
import castbridge.core.quiz.online.PlayRole
import castbridge.core.quiz.online.RoomCode
import castbridge.core.quiz.online.ServerMsg
import castbridge.core.quiz.online.StakeSpec
import castbridge.core.wallet.PlayResult
import castbridge.core.wallet.WalletCurrency

/**
 * Salle d'échecs en ligne autoritaire côté service (`game:chess`, chantier games-G2) : DEUX TV jouent (celle qui crée, puis celle qui entre par le code ; couleur de l'hôte au choix ou au hasard),
 * des TV spectatrices regardent. PURE : aucune socket, aucun thread, aucun fichier ; le service fournit les connexions (`conn`), l'horloge du SERVEUR (`now`, ms) et appelle [tick]. Même contrat que
 * `ServerRoom` du Quiz (`handle` / `disconnect` / `tick` / `close`, messages `Out`), mêmes règles que `ChessRoom` de la TV :
 * - le moteur `core/chess` vérifie chaque coup ; un client n'envoie que des coups numérotés (`ply` = demi-coup auquel il répond : doublon ou retard = accusé `STALE`) ; seul le trait joue ;
 * - la PENDULE est celle du serveur (10 à 60 s par coup, jamais plus) ; en compétition, temps dépassé = partie perdue (nulle si l'adversaire ne peut plus mater) ; avec une mise : compétition seulement ;
 * - déconnexion : la place est gardée (reprise `resume{roomId, token, lastSeq}`) ; une TV absente [Settings.forfeitMs] (60 s) PERD par forfait si l'autre est là, la partie est INTERROMPUE si les
 *   deux sont absentes ; la pendule continue pendant l'absence ;
 * - mise (option B) : le service reçoit des blocages `cbe1` DÉJÀ vérifiés par le hub ([Admission]), arbitre, puis SIGNE le résultat `cbr1` ([ChessSettlement]) avec sa clé dédiée ([signer]) ; il ne
 *   détient aucun secret du grand livre, ne parle jamais à l'API et n'écrit aucun solde : l'API règle.
 *
 * La vue d'un siège ([viewFor]) reprend le format d'état commun des échecs (docs/CHESS.md § 5) : `legal` n'est rempli que pour celui qui a le trait, aucun jeton d'un autre joueur n'y paraît.
 */
class ChessServerRoom(
    val roomId: String,
    private val random: java.util.Random,
    val createdAt: Long,
    val options: Options = Options(),
    code: String = RoomCode.generate(random),
    /** Clé dédiée du service qui signe `cbr1` ; null = le service ne sait pas signer (alors une salle misée n'existe pas : le hub la refuse). */
    private val signer: Signer? = null,
    /** Appelé (sous le verrou de la salle) avec `(rid, jeton cbr1)` : le service le dépose dans son volume pour le collecteur de l'hôte. */
    private val onResult: (String, String) -> Unit = { _, _ -> },
    val settings: Settings = Settings(),
) {
    /** Réglages de la partie ; [stake] null = partie libre. Une partie misée se joue en compétition (jamais de coup tiré au hasard avec de l'argent en jeu). */
    data class Options(val perMoveSeconds: Int = MoveTimer.DEFAULT_SECONDS, val mode: ClockMode = ClockMode.COMPETITION, val color: String = "random", val stake: StakeSpec? = null) {
        val clampedSeconds: Int get() = MoveTimer.clamp(perMoveSeconds)
    }

    data class Settings(
        /** TV spectatrices au plus. */
        val maxSpectators: Int = 8,
        /** Une salle qui attend son adversaire vit au plus ce temps (le blocage `cbe1` de l'hôte vit 30 min). */
        val openTtlMs: Long = 30 * 60_000L,
        /** Une partie ne dure jamais plus que cela : au-delà elle est INTERROMPUE (mises rendues) ; bien en dessous de l'échéance d'un blocage non réglé (échéance + 6 h). */
        val maxGameMs: Long = 3 * 60 * 60_000L,
        /** Une partie finie reste consultable (résultat, reprise, `cbr1`) ce temps. */
        val finishedKeepMs: Long = 5 * 60_000L,
        /** Absence continue d'une TV au-delà de laquelle elle perd par forfait. */
        val forfeitMs: Long = FORFEIT_MS,
    )

    enum class State { OPEN, PLAYING, FINISHED, GONE }

    /** Un message à envoyer à la connexion `to`. */
    data class Out(val to: String, val msg: ServerMsg)

    /** Admission d'une connexion, jugée par le hub AVANT la salle : identité (code d'appareil de l'activation) et blocage vérifié. La salle ne revérifie aucune signature. */
    data class Admission(val identity: String?, val escrow: ChessEscrow? = null)

    /** Un siège : hôte, joueur ou spectateur. `token` (128 bits) est le seul secret du siège. */
    class Seat internal constructor(val token: String, var role: PlayRole, val name: String, val identity: String?, val color: Int?, val escrow: ChessEscrow?, val playerId: String) {
        var conn: String? = null; internal set
        var lostAt: Long? = null; internal set
    }

    private val lock = Any()
    var code: String = code; private set
    private var state = State.OPEN
    private var seq = 1L
    private val seats = LinkedHashMap<String, Seat>()      // par jeton
    private val byConn = HashMap<String, Seat>()
    private var host: Seat? = null
    private val bySide = arrayOfNulls<Seat>(2)             // [blanc, noir]
    private var game: ChessGame? = null
    private var startedAt = 0L
    private var finishedAt = 0L
    private var nextPlayer = 1
    private var nextSpectator = 1
    private val badCodes = BadCodeCounter()
    private var lastRotationAt = Long.MIN_VALUE / 2
    private var pendingChange = false
    private val pendingOuts = ArrayList<Out>()
    /** Résultat signé (parties misées) une fois la partie finie ou interrompue ; reste dans la salle pour la reprise d'une TV qui l'aurait manqué. */
    var resultToken: String? = null; private set
    private var abortReason: String? = null
    private val hostColor: Int = when (options.color.lowercase()) { "white" -> Piece.WHITE; "black" -> Piece.BLACK; else -> if (random.nextBoolean()) Piece.WHITE else Piece.BLACK }

    val stake: StakeSpec? get() = options.stake
    val isStaked: Boolean get() = options.stake != null

    fun phase(): State = synchronized(lock) { state }
    fun seq(): Long = synchronized(lock) { seq }
    fun hostConnected(): Boolean = synchronized(lock) { host?.conn != null }
    fun spectatorCount(): Int = synchronized(lock) { seats.values.count { it.role == PlayRole.SPECTATOR } }
    /** Les identifiants de blocage portés par les sièges (pour que le hub n'emploie jamais deux fois le même blocage). */
    fun escrowIds(): List<String> = synchronized(lock) { seats.values.mapNotNull { it.escrow?.eid } }
    /** Le salle attend un adversaire. */
    fun waitingForOpponent(): Boolean = synchronized(lock) { state == State.OPEN && bySide.any { it == null } }
    /** Identité de l'hôte (code d'appareil), pour le refus « une TV ne joue pas contre elle-même ». */
    fun hostIdentity(): String? = synchronized(lock) { host?.identity }
    /** Résultat du dernier jeu, ou null tant qu'il n'est pas fini. */
    fun gameResult(): GameResult? = synchronized(lock) { game?.result }
    fun playedPlies(): Int = synchronized(lock) { game?.ply ?: 0 }

    // ------------------------------------------------------------------ messages

    fun handle(conn: String, msg: ClientMsg, now: Long, ip: String? = null, admission: Admission = Admission(null)): List<Out> = synchronized(lock) {
        val out = ArrayList<Out>()
        if (state == State.GONE) { out += err(conn, PlayReason.PLAY_ROOM_GONE); return out }
        when (msg) {
            is ClientMsg.Hello, is ClientMsg.Pong, is ClientMsg.Report -> {}
            is ClientMsg.Create -> create(conn, msg, now, admission, out)
            is ClientMsg.Join -> join(conn, msg, now, ip, admission, out)
            is ClientMsg.Resume -> resume(conn, msg, now, out)
            is ClientMsg.GameAct -> {
                val seat = byConn[conn]
                val r = if (seat == null) "UNKNOWN_PLAYER" else gameAct(seat, msg, now, out)
                out += Out(conn, ServerMsg.Ack(seq, msg.seq, r))
            }
            is ClientMsg.Act, is ClientMsg.RelayAct, is ClientMsg.Scope, is ClientMsg.Kick, is ClientMsg.Mute ->
                out += errp(conn, PlayProtocol.FORBIDDEN, "Cette action n'existe pas dans une partie d'échecs.")
        }
        flush(now, out)
        out
    }

    /** Une connexion est tombée : la place est gardée (reprise par `resume`), la pendule continue, le forfait court à partir d'ici. */
    fun disconnect(conn: String, now: Long): List<Out> = synchronized(lock) {
        val out = ArrayList<Out>()
        val seat = byConn.remove(conn) ?: return out
        seat.conn = null; seat.lostAt = now
        pendingChange = true
        flush(now, out)
        out
    }

    /** Horloge : pendule, forfaits, expirations. Appelé par le service (toutes les 200 ms). */
    fun tick(now: Long): List<Out> = synchronized(lock) {
        val out = ArrayList<Out>()
        when (state) {
            State.GONE -> return out
            State.OPEN -> if (now - createdAt >= settings.openTtlMs) { abort("EXPIRED", now); flush(now, out); return gone("EXPIRED", now, out) }   // personne n'est venu : l'hôte reçoit le cbr1 ABORT, puis la fin de la salle
            State.PLAYING -> {
                val g = game!!
                if (now - startedAt >= settings.maxGameMs) abort("TOO_LONG", now)
                else {
                    if (g.tick(now)) { pendingChange = true; if (g.over) finish(now) }
                    if (state == State.PLAYING) forfeitCheck(now)
                }
            }
            State.FINISHED -> if (now - finishedAt >= settings.finishedKeepMs) return gone("DONE", now, out)
        }
        purgeSpectators(now)
        badCodes.sweep(now)
        flush(now, out)
        out
    }

    /** Le service ferme la salle (déploiement, suppression) : une partie en cours est INTERROMPUE (les blocages sont rendus par un `cbr1` ABORT), les TV reçoivent `roomGone`. */
    fun close(reason: String, now: Long): List<Out> = synchronized(lock) {
        if (state == State.GONE) return emptyList()
        if (state == State.OPEN || state == State.PLAYING) abort(reason, now)
        val out = ArrayList<Out>()
        flush(now, out)       // la dernière vue et le résultat, puis la fin de la salle
        gone(reason, now, out)
    }

    /**
     * Une frappe PROCHE du code de cette salle vient d'être refusée par le service ([RoomCode.nearMiss]) : à la [RoomCode.ROTATE_AFTER]e, le code change (salle d'attente seulement, au plus une
     * fois par minute) ; rend vrai si le code a changé. Même garde que `ServerRoom`.
     */
    fun noteNearMiss(now: Long): Boolean = synchronized(lock) {
        if (!badCodes.roomFail() || state != State.OPEN || now - lastRotationAt < ROTATE_MIN_MS) return false
        code = RoomCode.generate(random); lastRotationAt = now; pendingChange = true
        true
    }

    // ------------------------------------------------------------------ create / join / resume

    private fun create(conn: String, m: ClientMsg.Create, now: Long, admission: Admission, out: MutableList<Out>) {
        if (host != null || state != State.OPEN) { out += errp(conn, PlayProtocol.FORBIDDEN, "Cette salle a déjà un hôte."); return }
        if (isStaked && admission.escrow == null) { out += stakeRequired(conn); return }
        val seat = Seat(newToken(), PlayRole.HOST, ChessRoom.cleanName(m.name) ?: DEFAULT_NAME, admission.identity, hostColor, admission.escrow, "p${nextPlayer++}")
        seats[seat.token] = seat; host = seat; bySide[hostColor] = seat
        attach(seat, conn)
        out += welcome(seat, conn)
        pendingChange = true
    }

    private fun join(conn: String, m: ClientMsg.Join, now: Long, ip: String?, admission: Admission, out: MutableList<Out>) {
        if (byConn[conn] != null) { out += errp(conn, PlayProtocol.FORBIDDEN, "Vous êtes déjà dans la salle."); return }
        val typed = RoomCode.normalize(m.code)
        if (typed == null || typed != code) { badAttempt(conn, ip, now, out); return }
        // un siège reprend sa place par son jeton (la reprise ordinaire est `resume`)
        m.token?.let { t -> seats[t]?.let { s -> reattach(s, conn, now, out); return } }
        if (m.spectate) { spectate(conn, m, admission, out); return }
        // place de joueur
        if (state != State.OPEN || bySide.none { it == null }) { out += err(conn, GameReason.SEATS_TAKEN); return }
        val h = host
        if (h != null && admission.identity != null && admission.identity == h.identity) { out += err(conn, GameReason.SAME_TV); return }
        if (isStaked && admission.escrow == null) { out += stakeRequired(conn); return }
        val color = if (bySide[Piece.WHITE] == null) Piece.WHITE else Piece.BLACK
        val seat = Seat(newToken(), PlayRole.PLAYER, ChessRoom.cleanName(m.name) ?: DEFAULT_NAME, admission.identity, color, admission.escrow, "p${nextPlayer++}")
        seats[seat.token] = seat; bySide[color] = seat
        attach(seat, conn)
        out += welcome(seat, conn)
        start(now)
    }

    private fun spectate(conn: String, m: ClientMsg.Join, admission: Admission, out: MutableList<Out>) {
        if (seats.values.count { it.role == PlayRole.SPECTATOR } >= settings.maxSpectators) { out += err(conn, PlayReason.PLAY_ROOM_FULL); return }
        val s = Seat(newToken(), PlayRole.SPECTATOR, ChessRoom.cleanName(m.name) ?: DEFAULT_SPECTATOR, admission.identity, null, null, "s${nextSpectator++}")
        seats[s.token] = s; attach(s, conn)
        out += welcome(s, conn)
        pendingChange = true
    }

    private fun resume(conn: String, m: ClientMsg.Resume, now: Long, out: MutableList<Out>) {
        // Jamais de blocage par adresse ni de compte d'échecs ici (voir `ServerRoom.resume`) : un jeton de 128 bits ne se devine pas. Jeton inconnu ou autre salle : même réponse qu'un code faux.
        val s = seats[m.token]
        if (m.roomId != roomId || s == null) { out += err(conn, PlayReason.PLAY_BAD_CODE); return }
        reattach(s, conn, now, out)
    }

    /** Un siège revient sur une NOUVELLE connexion : `welcome` (même jeton), vue COMPLÈTE (une vue d'échecs tient en ≈ 1 à 2 Ko : pas de rejeu d'évènements), et le `cbr1` si la partie est finie. */
    private fun reattach(s: Seat, conn: String, now: Long, out: MutableList<Out>) {
        s.conn?.takeIf { it != conn }?.let { old -> byConn.remove(old) }
        attach(s, conn)
        out += welcome(s, conn)
        out += Out(conn, ServerMsg.State(seq, viewFor(s, now), full = true))
        resultToken?.takeIf { s.role != PlayRole.SPECTATOR }?.let { out += Out(conn, ServerMsg.Result(seq, it)) }
        pendingChange = true    // les autres voient « de retour »
    }

    private fun attach(s: Seat, conn: String) { s.conn = conn; s.lostAt = null; byConn[conn] = s }

    private fun badAttempt(conn: String, ip: String?, now: Long, out: MutableList<Out>) {
        out += err(conn, PlayReason.PLAY_BAD_CODE)
        if (ip != null) badCodes.ipFail(ip, now)
        if (badCodes.roomFail() && state == State.OPEN) { code = RoomCode.generate(random); pendingChange = true }
    }

    private fun stakeRequired(conn: String): Out {
        val s = options.stake!!
        val r = GameReason.STAKE_ESCROW_REQUIRED
        return Out(conn, ServerMsg.Error(seq, r.code, r.message, r.retryable, 0L, linkedMapOf("game" to PlayProtocol.GAME_CHESS, "cur" to s.cur, "per" to s.per)))
    }

    // ------------------------------------------------------------------ la partie

    private fun start(now: Long) {
        val g = ChessGame(options.clampedSeconds, options.mode, Position.START_FEN, now, random)
        game = g; startedAt = now; state = State.PLAYING
        pendingChange = true
    }

    private fun gameAct(seat: Seat, m: ClientMsg.GameAct, now: Long, out: MutableList<Out>): String {
        if (seat.role == PlayRole.SPECTATOR) return "FORBIDDEN"
        val color = seat.color ?: return "FORBIDDEN"
        if (m.op == "cancel") {
            // l'hôte renonce avant l'arrivée de l'adversaire : partie interrompue (son blocage est rendu par un cbr1 ABORT)
            if (seat !== host || state != State.OPEN) return "FORBIDDEN"
            abort("CANCELLED", now); return "OK"
        }
        val g = game
        if (g == null || state != State.PLAYING) return "IGNORED"
        return when (m.op) {
            "move" -> {
                val uci = m.arg
                // `ply` est OBLIGATOIRE en ligne : sans lui le serveur ne pourrait pas écarter un doublon ou un coup en retard (liaison à 40 kbps, renvois)
                if (uci.isNullOrBlank() || m.ply == null) return "BAD_REQUEST"
                val r = g.play(uci, color, now, m.ply)
                when (r) {
                    ChessGame.Play.OK -> { pendingChange = true; if (g.over) finish(now); "OK" }
                    ChessGame.Play.ILLEGAL -> "ILLEGAL"
                    ChessGame.Play.NOT_YOUR_TURN -> "NOT_YOUR_TURN"
                    ChessGame.Play.STALE -> { if (g.over) finish(now); "STALE" }
                    ChessGame.Play.OVER -> { if (g.over) finish(now); "OVER" }
                }
            }
            "resign" -> if (g.resign(color)) { pendingChange = true; finish(now); "OK" } else "IGNORED"
            "draw" -> {
                val ok = when (m.arg) {
                    "offer" -> g.offerDraw(color)
                    "accept" -> g.answerDraw(color, true)
                    "decline" -> g.answerDraw(color, false)
                    else -> return "BAD_REQUEST"
                }
                if (g.over) finish(now)
                if (ok) { pendingChange = true; "OK" } else "IGNORED"
            }
            else -> "BAD_REQUEST"
        }
    }

    /** Une TV absente depuis [Settings.forfeitMs] perd par forfait si l'autre est là ; si les deux le sont, la partie est interrompue ; sinon on attend. */
    private fun forfeitCheck(now: Long) {
        val g = game ?: return
        fun absent(c: Int): Boolean { val s = bySide[c]; return s != null && s.conn == null && s.lostAt?.let { now - it >= settings.forfeitMs } == true }
        fun here(c: Int): Boolean = bySide[c]?.conn != null
        val w = absent(Piece.WHITE); val b = absent(Piece.BLACK)
        when {
            w && b -> abort("BOTH_ABSENT", now)
            w && here(Piece.BLACK) -> { if (g.forfeit(Piece.WHITE)) { pendingChange = true; finish(now) } }
            b && here(Piece.WHITE) -> { if (g.forfeit(Piece.BLACK)) { pendingChange = true; finish(now) } }
        }
    }

    /** La partie est finie par les règles : état FINISHED et, si elle était misée, résultat `cbr1` signé. */
    private fun finish(now: Long) {
        if (state == State.FINISHED || state == State.GONE) return
        state = State.FINISHED; finishedAt = now
        sign(now, game?.result)
        pendingChange = true
    }

    /** La partie ne se joue pas jusqu'au bout (salle fermée, expirée, deux TV absentes…) : rien n'est utilisé, chaque blocage est rendu en entier (`cbr1` ABORT). */
    private fun abort(reason: String, now: Long) {
        if (state == State.FINISHED || state == State.GONE) return
        game?.abandon()
        abortReason = reason
        state = State.FINISHED; finishedAt = now
        sign(now, null)
        pendingChange = true
    }

    private fun sign(now: Long, outcome: GameResult?) {
        val stake = options.stake ?: return
        val sig = signer ?: return          // un service sans clé n'ouvre pas de salle misée : défense en profondeur
        val white = bySide[Piece.WHITE]?.escrow; val black = bySide[Piece.BLACK]?.escrow
        if (white == null && black == null) return
        val cur = WalletCurrency.values().firstOrNull { it.name == stake.cur } ?: return
        // une partie « terminée » sans les deux blocages ne peut pas être décisive : interrompue
        val effective = if (outcome != null && white != null && black != null) outcome else null
        val result = ChessSettlement.result(sig.keyId, roomId, cur, stake.per, now, white, black, effective)
        val token = PlayResult.sign(result, sig)
        resultToken = token
        runCatching { onResult(result.rid, token) }
        for (s in seats.values) if (s.role != PlayRole.SPECTATOR) s.conn?.let { pendingOuts += Out(it, ServerMsg.Result(seq, token)) }
    }

    private fun purgeSpectators(now: Long) {
        val gone = seats.values.filter { it.role == PlayRole.SPECTATOR && it.conn == null && (it.lostAt?.let { t -> now - t >= SPECTATOR_PURGE_MS } ?: false) }
        for (s in gone) seats.remove(s.token)
    }

    private fun gone(reason: String, now: Long, out: MutableList<Out>): List<Out> {
        state = State.GONE; seq++
        for (c in byConn.keys) out += Out(c, ServerMsg.RoomGone(seq, reason))
        byConn.clear()
        return out
    }

    // ------------------------------------------------------------------ diffusion

    private fun flush(now: Long, out: MutableList<Out>) {
        if (!pendingChange && pendingOuts.isEmpty()) return
        if (pendingChange) {
            pendingChange = false
            seq++
            for ((c, s) in byConn.entries.toList()) out += Out(c, ServerMsg.State(seq, viewFor(s, now), false))
        }
        out += pendingOuts; pendingOuts.clear()
    }

    /** Vue d'un siège, au format d'état commun des échecs (CHESS.md § 5) plus `room` et `stake`. `legal` seulement pour le joueur qui a le trait. */
    fun viewFor(s: Seat, now: Long): Map<String, Any?> = synchronized(lock) {
        val g = game
        val stage = when (state) { State.OPEN -> "LOBBY"; State.PLAYING -> "PLAYING"; State.FINISHED -> "FINISHED"; State.GONE -> "CLOSED" }
        val toMove = g?.takeIf { !it.over && state == State.PLAYING }?.turn
        val mine = toMove != null && s.color == toMove
        // une partie interrompue n'est pas une nulle : « Partie interrompue : mises rendues » (jamais « partie nulle »)
        val resultMap = if (abortReason != null) linkedMapOf("winner" to null, "reason" to "ABANDONED", "text" to abortText(), "pgn" to "*")
            else g?.result?.let { r -> linkedMapOf("winner" to r.winner?.let(ChessRoom::colorKey), "reason" to r.reason.name, "text" to r.text, "pgn" to r.pgn) }
        linkedMapOf(
            "v" to seq,
            "protocol" to ChessRoom.PROTOCOL,
            "stage" to stage,
            "code" to code,
            "settings" to linkedMapOf("perMoveSeconds" to options.clampedSeconds, "mode" to options.mode.name, "modeLabel" to options.mode.label, "modeRule" to options.mode.rule,
                "level" to 0, "white" to "ONLINE", "black" to "ONLINE"),
            "white" to sideMap(Piece.WHITE), "black" to sideMap(Piece.BLACK),
            "me" to linkedMapOf("id" to s.playerId, "name" to s.name, "color" to s.color?.let(ChessRoom::colorKey)),
            "spectators" to seats.values.count { it.role == PlayRole.SPECTATOR },
            "fen" to (g?.position?.fen() ?: Position.START_FEN),
            "startFen" to Position.START_FEN,
            "ply" to (g?.ply ?: 0),
            "turn" to ChessRoom.colorKey(g?.turn ?: Piece.WHITE),
            "check" to (g?.position?.inCheck() == true),
            "san" to (g?.san?.toList() ?: emptyList<String>()),
            "uci" to (g?.uci?.toList() ?: emptyList<String>()),
            "lastMove" to g?.lastMove(),
            "auto" to (g?.autoPlayed?.sorted() ?: emptyList<Int>()),
            "legal" to (if (mine) g!!.position.legalMoves().map(Move::uci) else emptyList<String>()),
            "clock" to linkedMapOf("perMoveMs" to options.clampedSeconds * 1000L,
                "remainingMs" to (if (g != null && state == State.PLAYING) g.remainingMs(now) else null),
                "running" to (g != null && state == State.PLAYING && !g.over), "paused" to false, "turn" to ChessRoom.colorKey(g?.turn ?: Piece.WHITE)),
            "drawOffer" to g?.drawOffer?.let(ChessRoom::colorKey),
            "thinking" to false,
            "notice" to null,
            "result" to resultMap,
            "pgn" to (if (g != null && state == State.FINISHED) g.pgn(sideName(Piece.WHITE), sideName(Piece.BLACK), "Partie en ligne CastBridge") else null),
            "room" to linkedMapOf("id" to roomId, "game" to PlayProtocol.GAME_CHESS, "code" to RoomCode.display(code), "state" to state.name, "role" to s.role.name, "seq" to seq,
                "serverNowMs" to now, "hostConnected" to (host?.conn != null), "forfeitMs" to settings.forfeitMs,
                "away" to linkedMapOf("w" to awayMs(Piece.WHITE, now), "b" to awayMs(Piece.BLACK, now))),
            "stake" to options.stake?.let { st -> linkedMapOf("cur" to st.cur, "per" to st.per, "pot" to (if (bySide.all { it?.escrow != null }) 2 * st.per else st.per), "settled" to (resultToken != null)) },
        )
    }

    private fun sideName(c: Int): String = bySide[c]?.name ?: "En attente…"

    private fun sideMap(c: Int): Map<String, Any?> {
        val p = bySide[c]
        return linkedMapOf("kind" to "ONLINE", "name" to sideName(c), "connected" to (p?.conn != null), "level" to null, "playerId" to p?.playerId)
    }

    /** Depuis combien de ms cette couleur est absente (null : présente, ou personne de ce côté). Le client décompte le forfait lui-même. */
    private fun awayMs(c: Int, now: Long): Long? = bySide[c]?.takeIf { it.conn == null }?.lostAt?.let { (now - it).coerceAtLeast(0) }

    private fun abortText(): String = when (abortReason) {
        "CANCELLED" -> "Partie annulée : mises rendues"
        "EXPIRED" -> "Personne n'est venu : partie annulée, mises rendues"
        "BOTH_ABSENT" -> "Les deux TV ont quitté la partie : partie interrompue, mises rendues"
        "TOO_LONG" -> "Partie trop longue : interrompue, mises rendues"
        else -> "Partie interrompue : mises rendues"
    }

    private fun welcome(s: Seat, conn: String): Out = Out(conn, ServerMsg.Welcome(seq, roomId, code, s.token, s.role, s.playerId, PlayProtocol.PROTO, PlayProtocol.CAPS, PlayProtocol.GAME_CHESS))

    private fun newToken(): String = ByteArray(16).also { random.nextBytes(it) }.joinToString("") { "%02x".format(it) }
    private fun err(conn: String, r: PlayReason) = Out(conn, ServerMsg.Error(seq, r.code, r.message, r.retryable, r.retryAfterMs))
    private fun err(conn: String, r: GameReason) = Out(conn, ServerMsg.Error(seq, r.code, r.message, r.retryable))
    private fun errp(conn: String, code: String, message: String) = Out(conn, ServerMsg.Error(seq, code, message, false))

    companion object {
        /** Une TV absente plus longtemps perd par forfait (la pendule d'un coup est de 60 s au plus : même ordre de grandeur que le Quiz, HOST_LOST_MS). */
        const val FORFEIT_MS = 60_000L
        const val ROTATE_MIN_MS = 60_000L
        const val SPECTATOR_PURGE_MS = 5 * 60_000L
        const val DEFAULT_NAME = "TV"
        const val DEFAULT_SPECTATOR = "TV"
    }
}
