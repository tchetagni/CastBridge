package castbridge.core.games

import castbridge.core.quiz.Json
import java.security.SecureRandom

/**
 * La salle d'un jeu écrit comme un [GameRules] : le socle [GameRoom] (code, jetons, places, spectateurs) + un [TurnEngine] (coups numérotés, pendule, abandon, déconnexion) + un
 * ordinateur. C'est tout ce qu'il faut pour ajouter un jeu à tour de rôle : écrire ses règles ; la salle, l'état commun, la page web `/jeux/<id>` et l'écran de la TV suivent.
 *
 * Qui joue quelle place : [configure] (télécommande, téléphone, ordinateur). L'AUTORITÉ de la partie est cette salle : elle tire la graine, vérifie chaque coup avec les règles, tient la
 * pendule et envoie à chacun [view], jamais la main des autres ni la graine (qui fixe les mains : elle n'est révélée que dans le journal d'une partie FINIE, voir [journal]).
 *
 * Les places s'appellent `s1`, `s2`… ([PlayerId.seat]). Une personne assise sur un téléphone qui se tait voit sa pendule s'arrêter puis, 60 s après son dernier signe de vie, perd la
 * partie ; elle reprend sa place en revenant avec son jeton. L'ordinateur joue [aiDelayMs] après le coup précédent (pour qu'on le voie jouer) ; 0 en test.
 */
class RulesRoom<S, M : GameMove>(
    val rules: GameRules<S, M>,
    private val ai: GameAi<S, M> = FirstLegalAi(),
    clock: () -> Long = { System.nanoTime() / 1_000_000 },
    random: java.util.Random = SecureRandom(),
    maxPlayers: Int = GameRoom.DEFAULT_MAX_PLAYERS,
    autoTick: Boolean = true,
    private val aiDelayMs: Long = DEFAULT_AI_DELAY_MS,
    /** L'heure murale (époque, ms) pour dater les journaux ; distincte de [clock], monotone. */
    private val wallClock: () -> Long = { System.currentTimeMillis() },
) : GameRoom(clock, random, maxPlayers, rules.minPlayers) {
    /** Qui joue chaque place. Au départ : la télécommande contre l'ordinateur. */
    var kinds: List<SeatKind> = List(rules.minPlayers) { if (it == 0) SeatKind.REMOTE else SeatKind.AI }; private set
    var moveClock: MoveClock? = null; private set
    var engine: TurnEngine<S, M>? = null; private set
    /** Identifiant unique (128 bits) de la partie en cours ou de la dernière ; null avant la première. */
    var gameId: String? = null; private set
    /** Appelé UNE fois à la fin de chaque partie (sous le verrou) : la TV en tire le journal signé. */
    @Volatile var onFinished: (() -> Unit)? = null

    private var startedAtWall = 0L
    private var endedAtWall = 0L
    private var notice: String? = null
    private var aiDueAt: Long? = null
    private val seatConnected = HashMap<Int, Boolean>()

    init { if (autoTick) startTicker("game-tick") }

    override fun isPhoneSeat(seat: Int): Boolean = kinds.getOrNull(seat) == SeatKind.PHONE

    // ---------------------------------------------------------------- réglages et départ

    /**
     * Qui joue chaque place ([kinds] : de [GameRules.minPlayers] à [GameRules.maxPlayers] places, au moins une qui n'est pas l'ordinateur) et la pendule éventuelle. Au salon ou après une
     * partie seulement. Les personnes déjà là prennent les places de téléphone libres.
     */
    fun configure(kinds: List<SeatKind>, clock: MoveClock? = this.moveClock): Boolean = synchronized(lock) {
        if (roomStage == RoomStage.PLAYING || roomStage == RoomStage.CLOSED) return false
        require(kinds.size in rules.minPlayers..rules.maxPlayers) { "${rules.id} se joue de ${rules.minPlayers} à ${rules.maxPlayers} joueurs, pas ${kinds.size}" }
        require(kinds.any { it != SeatKind.AI }) { "l'ordinateur ne joue pas tout seul" }
        this.kinds = kinds.toList(); this.moveClock = clock
        resizeSeats(kinds.size)
        reseat()
        engine = null; notice = null; aiDueAt = null
        roomStage = RoomStage.LOBBY
        changed(); true
    }

    /** Pourquoi la partie ne peut pas commencer (en français, pour l'écran de la TV), ou null si elle le peut. */
    fun missing(): String? = synchronized(lock) {
        val need = missingSeats(now())
        when (need.size) {
            0 -> null
            1 -> "En attente du joueur ${need[0] + 1} sur téléphone"
            else -> "En attente de ${need.size} joueurs sur téléphone"
        }
    }

    /** Lance une partie avec les réglages en cours (nouvelle graine). Rend null si elle a commencé, sinon la raison. */
    fun start(force: Boolean = false): String? = synchronized(lock) {
        if (roomStage == RoomStage.PLAYING) return "Une partie est déjà en cours."
        if (roomStage == RoomStage.CLOSED) return "La salle est fermée."
        if (!force) missing()?.let { return it }
        val t = now()
        val seats = kinds.indices.map(PlayerId::seat)
        val seed = random.nextLong()
        gameId = ByteArray(16).also { random.nextBytes(it) }.joinToString("") { "%02x".format(it) }
        engine = TurnEngine(rules, seats, seed, t, moveClock, ai = seats.filter { kinds[seatIndex(it)] == SeatKind.AI }.toSet())
        startedAtWall = wallClock(); endedAtWall = 0L; notice = null; aiDueAt = null; seatConnected.clear()
        roomStage = RoomStage.PLAYING
        syncPresence(t)
        changed()
        if (engine!!.over) finish() else driveAi(t)
        null
    }

    /** Retour au salon (même code, mêmes places) pour changer les réglages ou rejouer ; la partie en cours, s'il y en a une, est abandonnée. */
    fun backToLobby(): Boolean = synchronized(lock) {
        if (roomStage == RoomStage.CLOSED) return false
        engine?.abandon(now())
        engine = null; notice = null; aiDueAt = null
        roomStage = RoomStage.LOBBY
        pruneLeft()
        changed(); true
    }

    override fun onClosing() { engine?.abandon(now()); aiDueAt = null }

    // ---------------------------------------------------------------- télécommande de la TV

    /** Les places jouées par la télécommande. */
    private fun remoteSeats(): List<Int> = kinds.indices.filter { kinds[it] == SeatKind.REMOTE }

    /** La TV voit la vue de SA place si elle n'en a qu'une (partie solo) ; avec deux télécommandes ou aucune, la vue de la table (rien de caché). */
    private fun soleRemoteSeat(): Int? = remoteSeats().singleOrNull()

    /** Un coup de la télécommande : pour la place de télécommande qui a la main. */
    fun hostMove(text: String): ActResult = synchronized(lock) {
        val e = engine ?: return ActResult.IGNORED
        if (roomStage != RoomStage.PLAYING) return ActResult.IGNORED
        val seat = e.toMove().map(::seatIndex).firstOrNull { kinds[it] == SeatKind.REMOTE } ?: return ActResult.NOT_YOUR_TURN
        playFor(seat, text, null)
    }

    /** La télécommande abandonne (avec deux télécommandes : celle qui a la main). */
    fun hostResign(): Boolean = synchronized(lock) {
        val e = engine ?: return false
        if (roomStage != RoomStage.PLAYING) return false
        val seat = e.toMove().map(::seatIndex).firstOrNull { kinds[it] == SeatKind.REMOTE } ?: remoteSeats().firstOrNull() ?: return false
        e.resign(PlayerId.seat(seat), now()).also { if (it) finish() }
    }

    /** La pendule ne peut s'arrêter que si personne ne joue depuis un téléphone (un adversaire distant n'est jamais mis en attente). */
    val canPause: Boolean get() = kinds.none { it == SeatKind.PHONE }

    /** Menu Pause de la TV : arrête (true) ou relance (false) la pendule d'une partie jouée à la TV seule. */
    fun hostPause(pause: Boolean): Boolean = synchronized(lock) {
        val e = engine ?: return false
        if (roomStage != RoomStage.PLAYING || !canPause) return false
        (if (pause) e.pause(now()) else e.resume(now())).also { if (it) changed() }
    }

    // ---------------------------------------------------------------- commandes des téléphones (HTTP)

    /**
     * La commande d'un téléphone : `move` (arg = le coup en texte court, seq = le numéro du coup auquel il répond : un doublon ou un coup en retard est refusé, `STALE`), `resign`,
     * `sit` (arg = numéro de place, de 1 à N : prendre une place libre avant la partie) et `stand` (regarder).
     */
    fun act(token: String?, action: String, arg: String? = null, seq: Int? = null): ActResult = synchronized(lock) {
        val p = player(token) ?: return ActResult.UNKNOWN_PLAYER
        if (roomStage == RoomStage.CLOSED) return ActResult.CLOSED
        tickLocked()                                   // l'horloge de l'hôte d'abord : le silence de ce joueur lui a peut-être déjà coûté la partie
        val t = now()
        p.lastSeen = t
        syncPresence(t)
        val seat = seatOf(p)
        val e = engine
        when (action) {
            "move" -> {
                if (e == null || roomStage != RoomStage.PLAYING) return ActResult.IGNORED
                if (seat == null) return ActResult.FORBIDDEN
                if (arg.isNullOrBlank()) return ActResult.BAD_REQUEST
                playFor(seat, arg, seq)
            }
            "resign" -> {
                if (e == null || roomStage != RoomStage.PLAYING || seat == null) return ActResult.FORBIDDEN
                if (e.resign(PlayerId.seat(seat), t)) { finish(); ActResult.OK } else ActResult.IGNORED
            }
            "sit" -> {
                if (roomStage == RoomStage.PLAYING) return ActResult.FORBIDDEN
                val n = arg?.toIntOrNull()?.takeIf { it in 1..kinds.size } ?: return ActResult.BAD_REQUEST
                if (sitAt(p, n - 1)) ActResult.OK else ActResult.FORBIDDEN
            }
            "stand" -> if (standUp(p)) ActResult.OK else ActResult.FORBIDDEN
            else -> ActResult.BAD_REQUEST
        }
    }

    private fun playFor(seat: Int, text: String, seq: Int?): ActResult {
        val e = engine ?: return ActResult.IGNORED
        val move = rules.decodeMove(text, PlayerId.seat(seat)) ?: return ActResult.ILLEGAL
        val before = e.moveNo
        return when (e.submit(move, seq, now())) {
            MoveVerdict.OK -> { notice = null; afterEngine(before); ActResult.OK }
            MoveVerdict.ILLEGAL -> ActResult.ILLEGAL
            MoveVerdict.NOT_YOUR_TURN -> ActResult.NOT_YOUR_TURN
            MoveVerdict.STALE -> { afterEngine(before); ActResult.STALE }
            MoveVerdict.OVER -> { if (roomStage == RoomStage.PLAYING) finish(); ActResult.IGNORED }
            MoveVerdict.UNKNOWN_PLAYER -> ActResult.FORBIDDEN
        }
    }

    /** Ce que fait la salle après que le moteur a bougé : finir si c'est fini, sinon avertir tout le monde et laisser l'ordinateur répondre. */
    private fun afterEngine(movesBefore: Int) {
        val e = engine ?: return
        if (e.over) finish()
        else if (e.moveNo != movesBefore) { changed(); driveAi(now()) }
    }

    override fun httpAct(token: String?, action: String, arg: String?, seq: Int?): ActResult = act(token, action, arg, seq)
    override fun joinReply(p: RoomPlayer): String {
        val seat = synchronized(lock) { seatOf(p) }
        return ",\"seat\":${seat ?: "null"},\"seatId\":${seat?.let { "\"${PlayerId.seat(it)}\"" } ?: "null"}"
    }

    // ---------------------------------------------------------------- le temps, la présence, l'ordinateur

    /** Le tic de fond : présence des téléphones, pendule et déconnexions de l'hôte, ordinateur. */
    override fun tick() { synchronized(lock) { tickLocked() } }

    override fun beforeRequest() = tickLocked()

    /** Quelqu'un vient de donner signe de vie (entrée, retour avec le jeton, flux rouvert) : le moteur le sait tout de suite, sans attendre le tic de fond. */
    override fun afterRequest() {
        if (roomStage == RoomStage.PLAYING && syncPresence(now())) changed()
    }

    private fun tickLocked() {
        if (roomStage != RoomStage.PLAYING) return
        val e = engine ?: return
        val t = now()
        var dirty = syncPresence(t)
        val before = e.moveNo
        if (e.tick(t)) dirty = true
        if (e.over) { finish(); return }
        if (e.moveNo != before) notice = "Temps écoulé : un coup a été joué d'office"
        if (dirty) changed()
        driveAi(t)
    }

    /** Dit au moteur qui est joignable (un téléphone absent est « silencieux » depuis son dernier signe de vie). Vrai si l'état de présence d'une place a changé. */
    private fun syncPresence(t: Long): Boolean {
        val e = engine ?: return false
        var flipped = false
        for (i in kinds.indices) {
            if (kinds[i] != SeatKind.PHONE) continue
            val h = holder(i)
            val ok = h != null && isConnected(h, t)
            if (ok) e.setConnected(PlayerId.seat(i), true, t) else e.setConnected(PlayerId.seat(i), false, t, silentSinceMs = h?.lastSeen ?: t)
            if ((seatConnected.put(i, ok) ?: true) != ok) flipped = true
        }
        return flipped
    }

    /** L'ordinateur joue quand son délai est écoulé ; avec un délai nul, tout de suite (et de suite si c'est encore à lui). Vrai s'il a joué. */
    private fun driveAi(t: Long): Boolean {
        var played = false
        while (roomStage == RoomStage.PLAYING) {
            val e = engine ?: break
            if (e.over) break
            val seat = e.toMove().firstOrNull { kinds[seatIndex(it)] == SeatKind.AI }
            if (seat == null) { aiDueAt = null; break }
            val due = aiDueAt ?: (t + aiDelayMs).also { aiDueAt = it }
            if (t < due) break
            val move = ai.choose(rules, e.state, seat) ?: rules.fallbackMove(e.state, seat) ?: break
            aiDueAt = null
            if (e.submit(move, e.moveNo, t) != MoveVerdict.OK) break
            played = true
            if (e.over) { finish(); break }
            changed()
        }
        return played
    }

    private fun finish() {
        if (roomStage != RoomStage.PLAYING) return
        aiDueAt = null
        roomStage = RoomStage.FINISHED
        endedAtWall = wallClock()
        changed()
        runCatching { onFinished?.invoke() }
    }

    /**
     * Le journal de la partie FINIE, à signer avec la clé de l'autorité [kid] ; null tant que la partie n'est pas finie (la graine fixe les mains : elle ne sort qu'à la fin).
     * Aucune main dedans ([GameJournal]). Refuse (IllegalArgumentException) un identifiant de clé mal formé.
     */
    fun journal(kid: String): GameJournal? = synchronized(lock) {
        val e = engine?.takeIf { it.over } ?: return null
        GameJournal.of(gameId ?: return null, kid, e, startedAtWall, maxOf(endedAtWall, startedAtWall))
    }

    // ---------------------------------------------------------------- vues

    private fun seatIndex(p: PlayerId): Int = p.id.removePrefix("s").toInt() - 1

    /** Le nom de la place [i] pour l'écran : le pseudo du téléphone, « Vous » (partie solo à la télécommande), « Ordinateur »… */
    private fun seatName(i: Int): String = when (kinds[i]) {
        SeatKind.AI -> "Ordinateur"
        SeatKind.REMOTE -> when {
            remoteSeats().size > 1 -> "Joueur ${i + 1}"
            kinds.none { it == SeatKind.PHONE } -> "Vous"
            else -> "Joueur de la TV"
        }
        SeatKind.PHONE -> holder(i)?.name ?: "Téléphone (libre)"
    }

    /** Les noms des places, dans l'ordre (pour l'écran de fin de la TV). */
    fun names(): List<String> = synchronized(lock) { kinds.indices.map(::seatName) }

    /**
     * L'état au format commun (docs/GAMES.md § État commun) tel que le voit le porteur de [token] (null = la TV : la vue de sa place de télécommande s'il n'en a qu'une, sinon celle de
     * la table). `legal` n'est rempli que pour celui qui a la main ; `table` est la vue du jeu ([GameRules.view]) : jamais la main des autres ; aucun jeton, ni graine, ni état complet.
     */
    fun view(token: String?): Map<String, Any?> = synchronized(lock) {
        val me = token?.let { player(it) }
        val mySeat = seatOf(me)
        val viewerSeat = if (me != null) mySeat else soleRemoteSeat()
        val viewer = viewerSeat?.let(PlayerId::seat)
        val e = engine
        val t = now()
        val playing = roomStage == RoomStage.PLAYING && e != null && !e.over
        val toMove = if (playing) e!!.toMove() else emptyList()
        linkedMapOf(
            "v" to version,
            "protocol" to PROTOCOL,
            "game" to rules.id,
            "rules" to rules.version,
            "stage" to roomStage.name,
            "code" to code,
            "settings" to linkedMapOf("seats" to kinds.size, "kinds" to kinds.map { it.name },
                "perMoveSeconds" to (moveClock?.let { it.perMoveMs / 1000 } ?: 0L), "onTimeout" to moveClock?.onTimeout?.name),
            "seats" to kinds.indices.map { seatMap(it, viewerSeat, toMove, e, t) },
            "me" to me?.let { linkedMapOf("id" to it.id, "name" to it.name, "seat" to mySeat?.let { s -> PlayerId.seat(s).id }, "index" to mySeat) },
            "spectators" to spectatorCount(),
            "players" to activePlayers().map { p -> linkedMapOf("id" to p.id, "name" to p.name, "connected" to isConnected(p, t), "seat" to seatOf(p)?.let { PlayerId.seat(it).id }) },
            "moveNo" to (e?.moveNo ?: 0),
            "toMove" to toMove.map { it.id },
            "legal" to (if (playing && viewer != null) rules.legal(e!!.state, viewer).map(rules::encodeMove) else emptyList<String>()),
            "clock" to clockMap(e, toMove, playing, t),
            "notice" to notice,
            "result" to e?.result?.let(::resultMap),
            "table" to e?.let { rules.view(it.state, viewer) },
        )
    }

    override fun viewJson(token: String?): String = Json.write(view(token))

    private fun seatMap(i: Int, viewerSeat: Int?, toMove: List<PlayerId>, e: TurnEngine<S, M>?, t: Long): Map<String, Any?> {
        val pid = PlayerId.seat(i)
        val connected = when (kinds[i]) { SeatKind.PHONE -> holder(i)?.let { isConnected(it, t) } ?: false; else -> true }
        return linkedMapOf("id" to pid.id, "index" to i, "kind" to kinds[i].name, "name" to seatName(i), "connected" to connected,
            "playerId" to (if (kinds[i] == SeatKind.PHONE) holder(i)?.id else null), "toMove" to (pid in toMove), "me" to (viewerSeat == i),
            "remainingMs" to e?.takeIf { moveClock != null && !it.over }?.remainingMs(pid, t), "graceLeftMs" to e?.graceLeftMs(pid, t))
    }

    private fun clockMap(e: TurnEngine<S, M>?, toMove: List<PlayerId>, playing: Boolean, t: Long): Map<String, Any?> {
        val c = moveClock
        return linkedMapOf("perMoveMs" to c?.perMoveMs,
            "remainingMs" to (if (c != null && playing) toMove.firstOrNull()?.let { e!!.remainingMs(it, t) } else null),
            "running" to (playing && c != null && e != null && !e.paused && toMove.all { e.connected(it) }), "paused" to (e?.paused == true))
    }

    private fun resultMap(r: GameResult): Map<String, Any?> {
        val o = r.outcome
        val winners = when (o) { is Outcome.Winners -> o.winners; is Outcome.Scores -> o.leaders; else -> emptyList() }
        return linkedMapOf("kind" to o.toJson()["kind"], "winners" to winners.map { it.id }, "scores" to (o as? Outcome.Scores)?.scores?.entries?.associate { it.key.id to it.value },
            "reason" to r.reason.name, "by" to r.by?.id, "text" to resultText(r))
    }

    private fun nameOf(p: PlayerId?): String = p?.let { seatName(seatIndex(it)) } ?: ""
    private fun joinNames(ids: List<PlayerId>): String = ids.map(::nameOf).let { if (it.size <= 1) it.joinToString() else it.dropLast(1).joinToString(", ") + " et " + it.last() }
    private fun winsText(ids: List<PlayerId>) = if (ids.size == 1) "${nameOf(ids[0])} gagne la partie" else "${joinNames(ids)} gagnent la partie"

    /** La phrase de fin pour l'écran (les noms sont ceux des places). */
    private fun resultText(r: GameResult): String {
        val o = r.outcome
        return when (r.reason) {
            EndReason.RULES -> when (o) {
                is Outcome.Winners -> winsText(o.winners)
                is Outcome.Scores -> o.leaders.let { l -> if (l.size == 1) "${nameOf(l[0])} gagne avec ${o.scores.getValue(l[0])} point${if (o.scores.getValue(l[0]) > 1) "s" else ""}" else "Égalité entre ${joinNames(l)}" }
                else -> "Partie nulle"
            }
            EndReason.RESIGNATION -> "${nameOf(r.by)} a abandonné : ${(o as? Outcome.Winners)?.let { winsText(it.winners) } ?: "partie terminée"}"
            EndReason.TIMEOUT -> "${nameOf(r.by)} n'a pas joué à temps : ${(o as? Outcome.Winners)?.let { winsText(it.winners) } ?: "partie terminée"}"
            EndReason.DISCONNECTED -> "${nameOf(r.by)} est resté déconnecté trop longtemps : ${(o as? Outcome.Winners)?.let { winsText(it.winners) } ?: "partie terminée"}"
            EndReason.ABANDONED -> "Partie abandonnée"
        }
    }

    companion object {
        /** Version du format d'état commun (docs/GAMES.md). */
        const val PROTOCOL = 1
        /** Délai avant que l'ordinateur ne joue : assez pour qu'on le voie jouer. */
        const val DEFAULT_AI_DELAY_MS = 700L
    }
}
