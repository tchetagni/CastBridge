package castbridge.core.quiz

import java.security.SecureRandom
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * A quiz room hosted by the TV: a 4-digit code, up to [maxPlayers] players on phones, one game at a time
 * (« Millionnaire » with a candidate and an audience, or « Duel » where everybody answers). Thread-safe: HTTP threads,
 * the TV's UI thread and the room's ticker all go through [lock]. Every change bumps [version] and wakes waiters
 * (long-poll / Server-Sent Events).
 *
 * Security model (see docs/QUIZ.md): the room code only lets you *join*; after that a random per-player token
 * identifies you. Neither gives access to the TV's admin API (PIN). The right answer never leaves the TV before the
 * question is closed.
 */
class QuizRoom(
    val bank: QuizBank,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
    private val random: java.util.Random = SecureRandom(),
    val maxPlayers: Int = 8,
    /** The room closes by itself after this long without any request or host action. */
    val idleCloseMs: Long = 10 * 60_000L,
    /** Runs [tick] every 200 ms on a daemon thread (tests drive [tick] by hand with a fake clock). */
    autoTick: Boolean = true,
    val gameTimers: IntArray = QuizGame.DEFAULT_TIMERS,
    val duelCount: Int = 10,
    val duelQuestionMs: Long = 20_000,
    /** Suspense between « dernier mot » and the reveal. */
    val suspenseMs: Long = 1_800,
    val voteMs: Long = 15_000,
    val callMs: Long = 20_000,
    /** Ids already asked in this TV session (no repeat until the bank runs dry). */
    private val asked: MutableSet<String> = LinkedHashSet(),
    /** Stakes of the « avec mise » competition: virtual demo tokens in the POC (see [WalletProvider]). */
    val wallet: WalletProvider = VirtualWallet(),
    /** Anti-repetition histories (docs/QUIZ.md, Règle des 300 parties): the host's and those of the phones. In memory by default. */
    val histories: QuizHistoryBook = QuizHistoryBook(null),
    /** A question is not asked again to the same players before this many games of the same course, while the bank allows it. */
    val minGapGames: Int = histories.gap,
) {
    enum class Mode(val label: String) { MILLIONAIRE("Millionnaire"), DUEL("Duel") }
    /** How the game is played: free competition, competition with a (virtual) stake, or practice without anything at stake. */
    enum class Play(val label: String) { FRIENDS("Compétition entre amis"), STAKE("Compétition avec mise"), PRACTICE("Entraînement") }
    enum class Stage { LOBBY, PLAYING, FINISHED, CLOSED }
    enum class Join { OK, BAD_CODE, FULL, CLOSED, BAD_NAME }
    enum class Act { OK, IGNORED, FORBIDDEN, BAD_REQUEST, UNKNOWN_PLAYER, CLOSED }

    class Player(val id: String, val token: String, var name: String, val joinedAt: Long, val walletKey: String = token) {
        var lastSeen = joinedAt
        var streams = 0
        var left = false
    }
    data class JoinResult(val status: Join, val player: Player? = null)
    private class Vote(val questionId: String, val deadline: Long, val votes: MutableMap<String, Int> = LinkedHashMap())
    private class Call(val questionId: String, val friendId: String, val deadline: Long)

    val lock = Object()
    val code: String = "%04d".format(random.nextInt(10_000))
    var mode = Mode.MILLIONAIRE; private set
    var play = Play.FRIENDS; private set
    var filter = QuestionFilter.GENERAL; private set
    /** Tokens each player stakes in [Play.STAKE]. */
    var stake = 100L; private set
    private var gameNo = 0
    private var potGame: String? = null
    private var stakers: Set<String> = emptySet()
    private var pot = 0L
    private var payouts: Map<String, Long>? = null
    var stage = Stage.LOBBY; private set
    /** Candidate of the « Millionnaire » game: a player id, or null = played with the TV remote. */
    var candidate: String? = null; private set
    var game: QuizGame? = null; private set
    var duel: QuizDuel? = null; private set
    @Volatile var version = 1L; private set
    private val players = LinkedHashMap<String, Player>()          // by id
    private val byToken = HashMap<String, Player>()
    private var nextId = 1
    private var lastActivity = clock()
    private var lockedAt = 0L
    private var vote: Vote? = null
    private var call: Call? = null
    private var callMissed: String? = null
    /** Called (on the changing thread, inside the lock: keep it short, e.g. post to a Handler) after every change. */
    @Volatile var onChange: (() -> Unit)? = null
    private val ticker: ScheduledExecutorService? = if (!autoTick) null else
        Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "quiz-tick").apply { isDaemon = true } }.also {
            it.scheduleWithFixedDelay({ runCatching { tick() } }, 200, 200, TimeUnit.MILLISECONDS)
        }

    fun now() = clock()

    // ---------------------------------------------------------------- players

    fun join(code: String?, name: String?, token: String? = null, device: String? = null): JoinResult = synchronized(lock) {
        if (stage == Stage.CLOSED) return JoinResult(Join.CLOSED)
        if (code?.trim() != this.code) return JoinResult(Join.BAD_CODE)
        token?.let { t -> byToken[t]?.let { p -> p.left = false; seen(p); cleanName(name)?.let { n -> if (n != p.name) p.name = unique(n, p) }; changed(); return JoinResult(Join.OK, p) } }
        val n = cleanName(name) ?: return JoinResult(Join.BAD_NAME)
        if (players.values.count { !it.left } >= maxPlayers) return JoinResult(Join.FULL)
        val tok = newToken()
        val p = Player("p${nextId++}", tok, unique(n, null), now(), device?.takeIf { it.length in 8..64 }?.let { "dev:$it" } ?: tok)
        players[p.id] = p; byToken[p.token] = p
        duel?.addPlayer(p.id)
        lastActivity = now(); changed()
        JoinResult(Join.OK, p)
    }

    fun player(token: String?): Player? = token?.let { synchronized(lock) { byToken[it] } }

    fun leave(token: String?): Boolean = synchronized(lock) {
        val p = token?.let { byToken[it] } ?: return false
        if (stage == Stage.LOBBY) { players.remove(p.id); byToken.remove(p.token); if (candidate == p.id) candidate = null }
        else p.left = true
        changed(); true
    }

    /** Players still in the room (not left), in join order. */
    fun players(): List<Player> = synchronized(lock) { players.values.filter { !it.left } }

    fun isConnected(p: Player, now: Long = now()) = !p.left && (p.streams > 0 || now - p.lastSeen < PRESENCE_MS)

    /** Marks a player as present (every request does). */
    fun touch(p: Player) = synchronized(lock) { seen(p) }

    /** An event stream opened / closed for [p] (presence). */
    fun streamOpened(p: Player) = synchronized(lock) { p.streams++; seen(p); changed() }
    fun streamClosed(p: Player) = synchronized(lock) { p.streams = maxOf(0, p.streams - 1); p.lastSeen = now(); changed() }

    private fun seen(p: Player) { p.lastSeen = now(); lastActivity = p.lastSeen }

    // ---------------------------------------------------------------- host (TV) actions

    fun setMode(m: Mode): Boolean = configure(m, if (m == Mode.MILLIONAIRE && play == Play.STAKE) Play.FRIENDS else play, filter, stake)

    /** Game settings, chosen on the TV before the lobby. A stake is only played in Duel (the pot is shared by ranking). */
    /** Duel format and answer window (seconds, 5-20) chosen on the TV. */
    var duelFormat: QuizDuel.Format = QuizDuel.Format.CLASSIC; private set
    var duelSeconds: Int = QuizGame.MAX_SECONDS; private set

    fun configure(m: Mode, p: Play, f: QuestionFilter, stakeTokens: Long = stake,
                  format: QuizDuel.Format = duelFormat, seconds: Int = duelSeconds): Boolean = synchronized(lock) {
        if (stage != Stage.LOBBY && stage != Stage.FINISHED) return false
        duelFormat = format; duelSeconds = seconds.coerceIn(3, QuizGame.MAX_SECONDS)
        mode = if (p == Play.STAKE) Mode.DUEL else m
        play = p; filter = f; stake = stakeTokens.coerceIn(1, 10_000)
        host(); true
    }

    /** Playable questions for the current settings. */
    fun available(): Int = synchronized(lock) { bank.count(filter) }

    /** Profiles whose histories apply to a game with the current settings: identified phones, or empty = the TV's host. */
    private fun historyKeys(t: Long): List<String> {
        fun stable(p: Player) = p.walletKey.startsWith("dev:")
        return if (mode == Mode.MILLIONAIRE) listOfNotNull(candidate?.let { players[it] }?.takeIf { stable(it) }?.walletKey)
        else players.values.filter { !it.left && isConnected(it, t) && stable(it) }.map { it.walletKey }
    }

    /** How fresh the bank is for [f] for whoever would play now (the phones connected in a Duel, else the TV): shown in the game choice. */
    fun freshness(f: QuestionFilter = filter): QuizBank.Freshness = synchronized(lock) {
        bank.remainingFresh(f, histories.viewFor(historyKeys(now())), minGapGames, if (mode == Mode.DUEL) duelCount else 15)
    }

    /** What the last draw had to do for a small bank (null before the first game or when it was perfect). */
    @Volatile var lastDrawReport: QuizBank.DrawReport? = null; private set
    private var gameKeys: List<String> = emptyList()
    private var gameIds: List<String> = emptyList()
    private var shownCount = 0

    /** Notes the questions that were actually shown (a game lost at question 3 does not use up the other twelve). */
    private fun noteShown() {
        if (gameIds.isEmpty()) return
        val upto = when {
            game != null -> game!!.index + 1
            duel != null -> duel!!.index + 1
            else -> return
        }.coerceAtMost(gameIds.size)
        if (upto <= shownCount) return
        val ids = gameIds.subList(shownCount, upto)
        shownCount = upto
        for (k in gameKeys) { histories.profile(k).record(filter.courseKey, ids); histories.save(k) }
    }

    fun setCandidate(playerId: String?): Boolean = synchronized(lock) {
        if (stage == Stage.PLAYING || (playerId != null && players[playerId]?.left != false)) return false
        candidate = playerId; host(); true
    }

    /**
     * Starts a game with the current settings and a fresh draw (no question already asked in this session).
     * Returns null when started, else why not (in plain words, for the TV screen).
     */
    fun startGame(seed: Long = random.nextLong()): String? = synchronized(lock) {
        if (stage != Stage.LOBBY && stage != Stage.FINISHED) return "Une partie est déjà en cours."
        if (bank.count(filter) < MIN_QUESTIONS) return "Pas encore assez de questions pour « ${filter.label} » (il en faut au moins $MIN_QUESTIONS)."
        val t = now()
        settle(refundOnly = true)
        vote = null; call = null; callMissed = null; payouts = null
        val present = players.values.filter { !it.left }
        if (mode == Mode.DUEL && present.none { isConnected(it, t) }) return "Aucun joueur connecté : il faut au moins un téléphone pour le duel."
        if (mode == Mode.MILLIONAIRE) {
            val qs = draw(15, seed)
            val practice = play == Play.PRACTICE
            game = QuizGame(qs, timers = if (practice) QuizGame.NO_TIMERS else gameTimers, seed = seed, practice = practice, markChannel = bank.channel).also { it.start(t) }
            duel = null
            if (candidate != null && players[candidate]?.left != false) candidate = null
        } else {
            val qs = draw(duelCount, seed)
            val window = minOf(duelQuestionMs, duelSeconds * 1000L, QuizGame.MAX_SECONDS * 1000L)   // 20 s max, practice included
            duel = QuizDuel(qs, questionMs = window, revealMs = if (play == Play.PRACTICE) 10_000 else 6_000, format = duelFormat)
                .also { d -> present.forEach { d.addPlayer(it.id) }; d.start(t) }
            game = null
        }
        gameNo++
        if (play == Play.STAKE) {
            val id = "$code-$gameNo"
            stakers = present.filter { isConnected(it, t) && wallet.stake(id, it.walletKey, stake) }.map { it.id }.toSet()
            pot = stake * stakers.size
            potGame = id
        } else { stakers = emptySet(); pot = 0; potGame = null }
        stage = Stage.PLAYING; host(); null
    }

    /** Draws with the players' history (union of the connected phones, else the host's) and opens a game in each of their histories. */
    private fun draw(count: Int, seed: Long): List<Question> {
        val t = now()
        val keys = historyKeys(t).ifEmpty { listOf(QuizHistoryBook.HOST) }
        val view = histories.viewFor(keys.filter { it != QuizHistoryBook.HOST })
        val d = bank.drawDetailed(count, seed, asked, filter, history = view, minGapGames = minGapGames)
        lastDrawReport = d.report.takeUnless { it.clean }
        gameKeys = keys; gameIds = d.questions.map { it.id }; shownCount = 0
        for (k in keys) histories.profile(k).beginGame(filter.courseKey)
        return d.questions
    }

    /** Pays the pot out at the end of a staked Duel, or gives the stakes back if the game did not finish. */
    private fun settle(refundOnly: Boolean) {
        val id = potGame ?: return
        val d = duel
        if (!refundOnly && d != null && d.phase == QuizDuel.Phase.FINISHED) {
            val split = Pot.split(pot, stakers.associateWith { d.score(it) })
            wallet.payout(id, split.mapKeys { (pid, _) -> players[pid]?.walletKey ?: pid })
            payouts = split
        } else wallet.refund(id)
        potGame = null
    }

    /** Back to the waiting room (same code, same players) for another game. */
    fun backToLobby(): Boolean = synchronized(lock) {
        if (stage == Stage.CLOSED) return false
        settle(refundOnly = true)
        stage = Stage.LOBBY; game = null; duel = null; vote = null; call = null; payouts = null
        players.values.removeAll { it.left }
        byToken.values.removeAll { it.left }
        host(); true
    }

    fun close() {
        synchronized(lock) {
            if (stage == Stage.CLOSED) return
            settle(refundOnly = true)
            stage = Stage.CLOSED; changed()
        }
        ticker?.shutdownNow()
    }

    /** OK on the remote in Duel: skip the wait of the current phase. */
    fun hostSkip(): Boolean = synchronized(lock) {
        val d = duel ?: return false
        if (stage != Stage.PLAYING) return false
        d.skip(now()).also { if (it) { afterDuelChange(d); host() } }
    }

    /** Candidate actions from the TV remote (always allowed: the host can take over from a phone that went away). */
    fun hostAct(action: String, choice: Int? = null, arg: String? = null): Boolean = synchronized(lock) {
        val g = game ?: return false
        candidateAct(g, action, choice, arg).also { if (it) host() }
    }

    private fun host() { lastActivity = now(); changed() }

    // ---------------------------------------------------------------- player actions (HTTP)

    /**
     * A player's command. [questionId] must be the current question's id (a late retry of an old command is ignored).
     * Actions: answer (Duel), vote (audience), suggest (phone friend), and for the candidate on a phone:
     * select, cancel, confirm, next, walk, fifty, audience, phone (arg = friend player id, or empty for a virtual friend).
     */
    fun act(token: String?, action: String, questionId: String?, choice: Int?, arg: String? = null): Act = synchronized(lock) {
        val p = token?.let { byToken[it] } ?: return Act.UNKNOWN_PLAYER
        if (stage == Stage.CLOSED) return Act.CLOSED
        seen(p)
        val t = now()
        val r = when (action) {
            "answer" -> {
                val d = duel ?: return Act.IGNORED
                if (stage != Stage.PLAYING) return Act.IGNORED
                when (d.answer(p.id, questionId.orEmpty(), choice ?: -1, t)) {
                    QuizDuel.Result.OK -> { d.tick(t, activeIds(t)); afterDuelChange(d); Act.OK }
                    QuizDuel.Result.SAME -> Act.OK
                    QuizDuel.Result.ALREADY_ANSWERED -> Act.FORBIDDEN
                    else -> Act.IGNORED
                }
            }
            "vote" -> {
                val v = vote ?: return Act.IGNORED
                if (v.questionId != questionId || p.id == candidate || choice !in 0..3) return Act.IGNORED
                if (game?.removed?.contains(choice) == true) return Act.BAD_REQUEST
                val prev = v.votes[p.id]
                if (prev != null) return if (prev == choice) Act.OK else Act.FORBIDDEN
                v.votes[p.id] = choice!!
                if (audienceIds(t).all { it in v.votes }) finishVote()
                Act.OK
            }
            "suggest" -> {
                val c = call ?: return Act.IGNORED
                if (c.questionId != questionId || c.friendId != p.id || choice !in 0..3) return Act.IGNORED
                game?.finishPhone(choice, p.name, t); call = null; Act.OK
            }
            "report" -> reportAct(questionId, arg)   // « Signaler une erreur »: any player, on the question on screen
            else -> {
                val g = game ?: return Act.IGNORED
                if (candidate != p.id) return Act.FORBIDDEN
                if (questionId != g.question.id) return Act.IGNORED
                if (candidateAct(g, action, choice, arg)) Act.OK else Act.IGNORED
            }
        }
        if (r == Act.OK) changed()
        r
    }

    /** Where « Signaler une erreur » goes (set by the app: the queue of reports of this device); null = reports not available. */
    @Volatile var feedback: castbridge.core.content.ContentFeedback? = null

    /** The question on screen (Millionaire or Duel), or null. */
    fun currentQuestion(): Question? = synchronized(lock) { game?.question ?: duel?.question }

    /** The TV host reports the question on screen (Millionaire or Duel). */
    fun hostReport(reason: String): Boolean = synchronized(lock) { currentQuestion()?.let { reportAct(it.id, reason) == Act.OK } ?: false }

    /** [arg] = "reason" or "reason|short text" (reasons: castbridge.core.content.ReportReason keys). */
    private fun reportAct(questionId: String?, arg: String?): Act {
        val q = currentQuestion() ?: return Act.IGNORED
        if (questionId != q.id) return Act.IGNORED
        val fb = feedback ?: return Act.IGNORED
        val reason = castbridge.core.content.ReportReason.of(arg?.substringBefore('|')?.trim()) ?: return Act.BAD_REQUEST
        return when (fb.reportQuestion(q, reason, arg?.substringAfter('|', ""))) {
            castbridge.core.content.ReportQueue.Add.INVALID -> Act.BAD_REQUEST
            castbridge.core.content.ReportQueue.Add.RATE_LIMITED -> Act.IGNORED
            else -> Act.OK   // accepted, or already reported (counted once)
        }
    }

    private fun candidateAct(g: QuizGame, action: String, choice: Int?, arg: String?): Boolean {
        val t = now()
        return when (action) {
            "report" -> reportAct(g.question.id, arg) == Act.OK
            "select" -> choice != null && (g.select(choice, t) || (g.phase == QuizGame.Phase.CONFIRM && g.selected == choice))
            "cancel" -> g.cancel()
            "confirm" -> g.confirm(t).also { if (it) lockedAt = t }
            "next" -> g.next(t).also { if (it && g.phase == QuizGame.Phase.FINISHED) stage = Stage.FINISHED }
            "walk" -> g.walk().also { if (it) stage = Stage.FINISHED }
            "fifty" -> g.useFifty()
            "audience" -> {
                if (!g.beginJoker(Joker.AUDIENCE, t)) return false
                if (audienceIds(t).isEmpty()) g.finishAudience(null, t) else vote = Vote(g.question.id, t + voteMs)
                true
            }
            "phone" -> {
                val friend = arg?.takeIf { it.isNotEmpty() }?.let { players[it] }?.takeIf { isConnected(it, t) && it.id != candidate }
                if (!g.beginJoker(Joker.PHONE, t)) return false
                callMissed = null
                if (friend == null) g.finishPhone(null, null, t) else call = Call(g.question.id, friend.id, t + callMs)
                true
            }
            else -> false
        }
    }

    private fun finishVote() {
        val v = vote ?: return
        val counts = IntArray(4).also { c -> v.votes.values.forEach { c[it]++ } }
        game?.finishAudience(counts, now())
        vote = null
    }

    private fun audienceIds(t: Long) = players.values.filter { it.id != candidate && isConnected(it, t) }.map { it.id }
    private fun activeIds(t: Long) = players.values.filter { isConnected(it, t) }.map { it.id }.toSet()

    private fun afterDuelChange(d: QuizDuel) {
        if (d.phase == QuizDuel.Phase.FINISHED && stage == Stage.PLAYING) { stage = Stage.FINISHED; settle(refundOnly = false) }
    }

    // ---------------------------------------------------------------- time

    /** Clock-driven transitions: time-outs, suspense, vote and call deadlines, Duel phases, idle close. */
    fun tick() {
        synchronized(lock) {
            if (stage == Stage.CLOSED) return
            val t = now()
            if (t - lastActivity > idleCloseMs) { close(); return }
            if (stage != Stage.PLAYING) return
            var ch = false
            game?.let { g ->
                if (g.tick(t)) { stage = Stage.FINISHED; ch = true }
                if (g.phase == QuizGame.Phase.LOCKED && t - lockedAt >= suspenseMs) { g.reveal(); ch = true }
                vote?.let { if (t >= it.deadline) { finishVote(); ch = true } }
                call?.let { c ->
                    if (t >= c.deadline) { callMissed = players[c.friendId]?.name; g.finishPhone(null, null, t); call = null; ch = true }
                }
            }
            duel?.let { d -> if (d.tick(t, activeIds(t))) { afterDuelChange(d); ch = true } }
            if (ch) changed()
        }
    }

    // ---------------------------------------------------------------- change notification

    private fun changed() {
        runCatching { noteShown() }
        version++
        lock.notifyAll()
        runCatching { onChange?.invoke() }
    }

    /** Blocks until [version] > [since], the room closes, or [timeoutMs] passes; returns the current version. */
    fun awaitChange(since: Long, timeoutMs: Long): Long = synchronized(lock) {
        val end = System.nanoTime() / 1_000_000 + timeoutMs
        while (version <= since && stage != Stage.CLOSED) {
            val left = end - System.nanoTime() / 1_000_000
            if (left <= 0) break
            lock.wait(left)
        }
        version
    }

    // ---------------------------------------------------------------- views

    /**
     * What one player's phone sees. Never contains the right answer of an open question (see [QuizGame.toMap],
     * Duel below), nor other players' tokens.
     */
    fun view(token: String?): Map<String, Any?> = synchronized(lock) {
        val me = token?.let { byToken[it] }
        val t = now()
        val role = when {
            me == null -> "host"
            mode == Mode.MILLIONAIRE && game != null && candidate == me.id -> "candidate"
            mode == Mode.MILLIONAIRE && call?.friendId == me.id -> "friend"
            mode == Mode.MILLIONAIRE -> "audience"
            else -> "player"
        }
        val d = duel
        linkedMapOf(
            "v" to version,
            "stage" to stage.name,
            "mode" to mode.name,
            "maxPlayers" to maxPlayers,
            "settings" to linkedMapOf("mode" to mode.name, "play" to play.name, "playLabel" to play.label, "track" to filter.track.key,
                "level" to filter.level, "field" to filter.field, "label" to filter.label, "stake" to (if (play == Play.STAKE) stake else 0),
                "repeats" to lastDrawReport?.let { linkedMapOf("count" to it.repeats, "shortestGap" to it.shortestGap, "quotaBroken" to it.quotaBroken) },
                "virtualTokens" to wallet.virtual, "tokens" to (if (wallet.virtual) TOKENS_LABEL else wallet.unit)),
            "pot" to (if (play == Play.STAKE) linkedMapOf("stake" to stake, "total" to pot, "stakers" to stakers.toList(), "payouts" to payouts) else null),
            "me" to me?.let { linkedMapOf("id" to it.id, "name" to it.name, "role" to role, "score" to (d?.score(it.id) ?: 0),
                "balance" to (if (play == Play.STAKE) wallet.balance(it.walletKey) else null)) },
            "candidate" to candidate?.let { c -> players[c]?.let { linkedMapOf("id" to it.id, "name" to it.name) } },
            "players" to players.values.filter { !it.left }.map { p ->
                linkedMapOf("id" to p.id, "name" to p.name, "connected" to isConnected(p, t), "score" to (d?.score(p.id) ?: 0),
                    "answered" to (d?.phase == QuizDuel.Phase.QUESTION && p.id in d.answered()))
            },
            "game" to game?.toMap(t),
            "duel" to d?.let { duelMap(it, me, t) },
            "vote" to vote?.let { v ->
                linkedMapOf("questionId" to v.questionId, "remainingMs" to maxOf(0, v.deadline - t), "count" to v.votes.size,
                    "expected" to audienceIds(t).size, "mine" to me?.let { v.votes[it.id] })
            },
            "call" to call?.let { c ->
                linkedMapOf("questionId" to c.questionId, "friendId" to c.friendId, "friend" to players[c.friendId]?.name,
                    "remainingMs" to maxOf(0, c.deadline - t))
            },
            "callMissed" to callMissed,
        )
    }

    fun viewJson(token: String?): String = Json.write(view(token))

    private fun duelMap(d: QuizDuel, me: Player?, t: Long): Map<String, Any?> {
        val q = d.question
        val closed = d.phase == QuizDuel.Phase.REVEAL || d.phase == QuizDuel.Phase.BOARD || d.phase == QuizDuel.Phase.FINISHED
        val ranking = d.ranking()
        return linkedMapOf(
            "phase" to d.phase.name,
            "index" to d.index,
            "count" to d.questions.size,
            "questionMs" to d.questionMs,
            "format" to d.format.name, "formatLabel" to d.format.label, "formatText" to d.format.description,
            "remainingMs" to d.remainingMs(t),
            "question" to linkedMapOf(
                "id" to q.id, "text" to q.question, "choices" to q.choices, "category" to q.category, "region" to q.region.name,
                "difficulty" to q.difficulty,
                "answer" to (if (closed) q.answer else null),
                "explanation" to (if (closed) q.explanation else null),
                "mark" to castbridge.core.content.PlayPolicy.mark(q, bank.channel),
            ),
            "answeredCount" to d.answered().size,
            "myAnswer" to me?.let { d.answerOf(it.id) ?: d.outcomes[it.id]?.choice },
            "outcome" to (if (closed) me?.let { d.outcomes[it.id] }?.let { linkedMapOf("correct" to it.correct, "points" to it.points, "ms" to it.ms) } else null),
            "distribution" to (if (closed) d.distribution().toList() else null),
            "ranking" to ranking.mapIndexed { i, (id, score) ->
                linkedMapOf("id" to id, "name" to (players[id]?.name ?: "?"), "score" to score, "rank" to i + 1,
                    "prevRank" to d.previousRanks[id], "gained" to (if (closed) d.outcomes[id]?.points ?: 0 else null),
                    "correct" to (if (closed) d.outcomes[id]?.correct else null))
            },
        )
    }

    // ---------------------------------------------------------------- helpers

    private fun newToken(): String = ByteArray(16).also { random.nextBytes(it) }.joinToString("") { "%02x".format(it) }

    private fun unique(n: String, self: Player?): String {
        val taken = players.values.filter { it !== self && !it.left }.map { it.name.lowercase() }.toSet()
        if (n.lowercase() !in taken) return n
        var i = 2
        while ("$n $i".lowercase() in taken) i++
        return "$n $i"
    }

    companion object {
        /** A player without an open event stream counts as present this long after his last request (long-poll = 25 s). */
        const val PRESENCE_MS = 35_000L
        const val MAX_NAME = 16
        const val MIN_QUESTIONS = 5
        /** Shown wherever tokens appear: they are a demo, without any value. */
        const val TOKENS_LABEL = "Jetons virtuels — démo"

        /** Trimmed, control characters removed, at most [MAX_NAME] characters; null if nothing is left. */
        fun cleanName(n: String?): String? = n?.filter { !it.isISOControl() && it != '<' && it != '>' }?.trim()
            ?.replace(Regex("\\s+"), " ")?.take(MAX_NAME)?.trim()?.takeIf { it.isNotEmpty() }
    }
}
