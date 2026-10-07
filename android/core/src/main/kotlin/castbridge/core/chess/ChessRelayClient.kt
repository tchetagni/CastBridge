package castbridge.core.chess

import castbridge.core.quiz.online.ChessOptions
import castbridge.core.quiz.online.ClientMsg
import castbridge.core.quiz.online.LinkView
import castbridge.core.quiz.online.PlayErrors
import castbridge.core.quiz.online.PlayLinkScreen
import castbridge.core.quiz.online.PlayTvSession
import castbridge.core.quiz.online.RoomCode
import castbridge.core.quiz.online.ServerMsg
import castbridge.core.quiz.online.StakeSpec
import castbridge.core.quiz.online.TvLink

/** Le service dit la mise d'une salle misée (`STAKE_ESCROW_REQUIRED`) : la TV doit bloquer [spec] puis revenir avec son blocage ; rien n'a été consommé. */
class ChessStakeRequired(val spec: StakeSpec) : ChessTransportException(409, "Cette partie se joue avec une mise de ${spec.per} ${spec.cur} par joueur.")

/**
 * Le client des échecs en ligne de la TV (chantier games-G2) : la TV joue contre une autre TV par le service `castbridge-play` (`/play/`, protocole `play-v1`, salle `game:chess`). Il REMPLACE l'ancien client
 * REST (routes `/api/chess/v1/…`, jamais ouvertes côté serveur) : plus de drapeau `online=1`, la capacité vient de `GET /play/.well-known/caps`. Il parle seulement par une [PlayTvSession] (même liaison que le
 * Quiz : ticket `cbp1`, activation `cbx1`, preuve de possession, SSE + POST, reprise `resume` sur la courbe 0, 2, 4, 8, 15, 30 s, perdue à 60 s, passerelle Bluetooth du téléphone si la TV n'a pas Internet) :
 * AUCUN téléphone ne parle au service ; les téléphones du foyer jouent par leur TV ([castbridge.core.chess.online.OnlineChessHost]).
 *
 * Même contrat bloquant que [ChessTransport] (à appeler hors du fil de l'écran) : [createGame] / [joinGame] attendent le `welcome` et la première vue ; [state] attend qu'une vue plus récente arrive ; [move],
 * [resign], [draw] attendent l'accusé du service (`OK`, `ILLEGAL`, `NOT_YOUR_TURN`, `STALE`, `OVER`, `FORBIDDEN`…). Une partie misée envoie son résultat signé `cbr1` à [onResult] (la TV le poste à l'API).
 * Un fil léger fait avancer la liaison ([PlayTvSession.tick]) tant que le client vit.
 */
class ChessRelayClient(
    /** Construit la session de liaison : le câblage de la TV (transport, ticket, preuve) ; les tests donnent une session sur un transport en mémoire. Appelé une fois par partie. */
    private val newSession: () -> PlayTvSession,
    private val activation: () -> String?,
    private val deviceHash: () -> String?,
    private val openTimeoutMs: Long = 30_000L,
    private val ackTimeoutMs: Long = 12_000L,
    private val tickMs: Long = 250L,
    /** Appelé environ toutes les 4 s tant que la liaison vit (la TV y tient vivante sa demande de tuyau quand elle n'a Internet que par un téléphone) ; sans effet par défaut. */
    private val beat: () -> Unit = {},
    private val beatMs: Long = 4_000L,
) : ChessTransport, AutoCloseable {
    override val label = "Internet"

    private val lock = Object()
    @Volatile private var session: PlayTvSession? = null
    @Volatile private var ticker: Thread? = null
    @Volatile private var closed = false
    private var view: Map<String, Any?> = emptyMap()
    private var viewSeq = 0L
    private val acks = HashMap<Long, String>()

    /** Le résultat signé `cbr1` reçu (parties misées), ou null ; reste disponible pour une reprise du règlement. */
    @Volatile var resultToken: String? = null; private set
    /** Appelé (fil du transport) quand le résultat signé arrive : la TV le poste à l'API. */
    @Volatile var onResult: ((String) -> Unit)? = null
    /** Appelé à chaque changement (vue, liaison) : l'écran se redessine. */
    @Volatile var onChange: (() -> Unit)? = null
    /** La salle a été fermée par le service (`roomGone`) : motif, ou null. */
    @Volatile var roomGoneReason: String? = null; private set
    @Volatile private var lastRefusal: ServerMsg.Error? = null
    /** Le service a dit la mise d'une salle misée : la liaison reste ouverte en attendant le blocage de cette TV. */
    @Volatile private var awaitingEscrow = false
    /** Identifiant de la salle (pour reprendre après une coupure longue). */
    @Volatile var roomId: String? = null; private set

    /** Liaison de cette TV avec le service : bonne, en reprise ou perdue. */
    fun link(): TvLink = session?.link ?: TvLink.Lost
    /** Ce que l'écran fait de la liaison (continuer, continuer en orange, quitter) : même règle que le Quiz en ligne ([PlayLinkScreen]) ; [via] = le chemin réseau de la TV (passerelle du téléphone). */
    fun linkView(via: castbridge.core.connect.Routes.Via? = null, tvHasNetwork: Boolean = true): LinkView =
        PlayLinkScreen.of(link(), via, session?.certificateInvalid == true, tvHasNetwork)
    /** Texte du dernier refus du service, en français, ou null. */
    fun lastRefusalText(): String? = lastRefusal?.let { PlayErrors.text(it.reason, it.retryAfterMs) }
    /** La dernière vue reçue du service (copie), vide avant la première : l'écran la lit à chaque changement ([onChange]) au lieu de faire une attente longue. */
    fun latest(): Map<String, Any?> = synchronized(lock) { LinkedHashMap(view) }
    /** Renonce à une ouverture en attente de blocage ([ChessStakeRequired]) : referme la liaison laissée ouverte. */
    fun cancelOpening() { awaitingEscrow = false; stopSession() }

    // ------------------------------------------------------------------ ouverture

    /** Crée la partie (cette TV prend la couleur [color] ou une au hasard) ; [stake] + [escrow] = partie misée. Bloquant jusqu'au `welcome` et à la première vue. */
    fun createGame(name: String, perMoveSeconds: Int, color: String, mode: ClockMode, stake: StakeSpec? = null, escrow: String? = null): ChessSession {
        val opts = ChessOptions(MoveTimer.clamp(perMoveSeconds), mode.name, color)
        return open(name, Opening { act, proof -> ClientMsg.Create(name, null, act, emptyList(), proof, castbridge.core.quiz.online.PlayProtocol.GAME_CHESS, opts, stake, escrow) })
    }

    /**
     * Entre dans la partie du code ; [escrow] = le blocage de cette TV si la salle est misée ; [spectate] : regarder, sans rien miser. Une salle misée sans blocage répond [ChessStakeRequired] (la mise à bloquer) :
     * la liaison reste OUVERTE (rien n'a été consommé), la TV bloque sa mise puis rappelle [joinGame] avec le même code et son blocage : le même ticket et la même connexion servent (aucun nouveau ticket) ;
     * si elle renonce, [leave] ou [close] referme la liaison.
     */
    fun joinGame(code: String, name: String, escrow: String? = null, spectate: Boolean = false): ChessSession {
        val c = RoomCode.normalize(code) ?: throw ChessTransportException(400, "Le code a 8 symboles : XXXX-XXXX.")
        val build = { act: String?, proof: String? -> ClientMsg.Join(c, name, null, deviceHash(), spectate, act, proof, escrow) }
        val s = session
        if (awaitingEscrow && escrow != null && s != null && !s.stopped) {   // la salle a dit sa mise : on revient avec le blocage sur la MÊME liaison
            awaitingEscrow = false
            synchronized(lock) { lastRefusal = null }
            s.reopenWith(PlayTvSession.Intent.Game(build))
            return awaitSeat(s, name)
        }
        return open(name, Opening(build))
    }

    /**
     * Revient dans une partie après une coupure plus longue que la reprise automatique (l'application a été fermée) : `resume` avec le jeton de siège gardé. La salle a pu disparaître ([ChessTransportException] 410).
     */
    fun resumeGame(roomId: String, token: String, name: String): ChessSession =
        open(name, Opening { _, _ -> ClientMsg.Resume(roomId, token, 0) })

    override fun create(name: String, perMoveSeconds: Int, color: String, mode: ClockMode): ChessSession = createGame(name, perMoveSeconds, color, mode)

    override fun join(code: String, name: String, token: String?): ChessSession = joinGame(code, name)

    /** Une ouverture à envoyer : construite à partir de l'activation et de la preuve de possession (qui dépendent du ticket). */
    private class Opening(val build: (String?, String?) -> ClientMsg) { fun asGame() = PlayTvSession.Intent.Game(build) }

    private fun open(name: String, intent: Opening): ChessSession {
        check(!closed) { "client fermé" }
        stopSession()
        reset()
        val s = try { newSession() } catch (e: ChessTransportException) { throw e } catch (e: Exception) { throw ChessTransportException(503, PlayErrors.GENERIC) }
        s.onServerMessage = { onMessage(it) }
        s.onChange = { onChange?.invoke() }
        session = s
        s.start(deviceHash(), activation(), intent.asGame())
        startTicker()
        return awaitSeat(s, name)
    }

    /** Attend le `welcome` puis la première vue ; un refus du service devient une exception avec son texte français (la mise à bloquer pour [ChessStakeRequired]). */
    private fun awaitSeat(s: PlayTvSession, name: String): ChessSession {
        var waited = 0L
        while (waited < openTimeoutMs) {
            lastRefusal?.let { refusal ->
                if (refusal.reason == "STAKE_ESCROW_REQUIRED") {
                    val d = refusal.data
                    val cur = d?.get("cur") as? String; val per = (d?.get("per") as? Number)?.toLong()
                    if (cur != null && per != null) { awaitingEscrow = true; throw ChessStakeRequired(StakeSpec(cur, per)) }   // la liaison reste ouverte : rien n'a été consommé
                }
                stopSession()
                throw ChessTransportException(statusOf(refusal.reason), PlayErrors.text(refusal.reason, refusal.retryAfterMs), refusal.reason)
            }
            if (s.authority.token != null && viewSeq > 0) return seat(s, name)
            if (s.stopped) throw ChessTransportException(503, s.lastError?.let { "Connexion au service impossible ($it)." } ?: PlayErrors.NO_INTERNET)
            synchronized(lock) { lock.wait(50L) }; waited += 50L
        }
        stopSession()
        throw ChessTransportException(504, "Le service de jeu ne répond pas : vérifiez la connexion de la TV et réessayez.")
    }

    /** Un refus d'ouverture qui n'est PAS retentable tel quel (la TV doit changer quelque chose) garde un code HTTP lisible pour l'écran. */
    private fun statusOf(reason: String): Int = castbridge.core.quiz.online.GameReason.of(reason)?.http ?: castbridge.core.quiz.online.PlayReason.of(reason)?.http ?: 400

    private fun seat(s: PlayTvSession, name: String): ChessSession {
        val a = s.authority
        @Suppress("UNCHECKED_CAST") val me = synchronized(lock) { view["me"] as? Map<String, Any?> }
        roomId = a.roomId
        return ChessSession(RoomCode.display(a.code.orEmpty()), a.token.orEmpty(), (me?.get("id") as? String).orEmpty(), name, me?.get("color") as? String, a.roomId.orEmpty())
    }

    // ------------------------------------------------------------------ lecture et commandes

    override fun state(s: ChessSession, since: Long, waitSeconds: Int): Map<String, Any?> {
        val steps = waitSeconds.coerceIn(0, 25) * 20
        var i = 0
        synchronized(lock) {
            while (viewSeq <= since && roomGoneReason == null && !closed && i < steps) { lock.wait(50L); i++ }
            if (closed || (roomGoneReason != null && view.isEmpty())) throw ChessTransportException(410, "La partie n'existe plus.")
            val l = link()
            if (l == TvLink.Lost && viewSeq <= since) throw ChessTransportException(503, "Connexion perdue : la partie n'a pas pu être reprise.")
            return LinkedHashMap(view)
        }
    }

    override fun move(s: ChessSession, uci: String, ply: Int): ChessAct = act("move", uci, ply)
    override fun resign(s: ChessSession): ChessAct = act("resign", null, null)
    override fun draw(s: ChessSession, action: String): ChessAct = act("draw", action, null)

    /** L'hôte renonce avant l'arrivée de l'adversaire : le service rend la mise par un résultat `cbr1` ABORT (reçu par [onResult]). */
    fun cancel(): ChessAct = act("cancel", null, null)

    private fun act(op: String, arg: String?, ply: Int?): ChessAct {
        val s = session ?: throw ChessTransportException(410, "La partie n'existe plus.")
        val ref = s.authority.gameAct(op, arg, ply)
        var waited = 0L
        synchronized(lock) {
            while (!acks.containsKey(ref) && waited < ackTimeoutMs && !closed) { lock.wait(50L); waited += 50L }
            val r = acks.remove(ref) ?: throw ChessTransportException(504, "Le service ne répond pas : votre action n'est peut-être pas arrivée.")
            return ChessAct(r, LinkedHashMap(view))
        }
    }

    override fun leave(s: ChessSession) { awaitingEscrow = false; stopSession() }

    override fun close() { closed = true; stopSession(); synchronized(lock) { lock.notifyAll() } }

    // ------------------------------------------------------------------ messages du service

    private fun onMessage(m: ServerMsg) {
        synchronized(lock) {
            when (m) {
                is ServerMsg.State -> { view = m.view; viewSeq = maxOf(viewSeq, (m.view["v"] as? Number)?.toLong() ?: m.seq) }
                is ServerMsg.Ack -> { acks[m.ref] = m.result; while (acks.size > MAX_ACKS) acks.remove(acks.keys.first()) }
                is ServerMsg.Result -> resultToken = m.token
                is ServerMsg.Error -> if (m.reason != "PLAY_MAINTENANCE") lastRefusal = m
                is ServerMsg.RoomGone -> roomGoneReason = m.reason
                is ServerMsg.Welcome -> roomId = m.roomId
                else -> {}
            }
            lock.notifyAll()
        }
        if (m is ServerMsg.Result) onResult?.invoke(m.token)
        onChange?.invoke()
    }

    private fun reset() {
        awaitingEscrow = false
        synchronized(lock) { view = emptyMap(); viewSeq = 0; acks.clear(); resultToken = null; roomGoneReason = null; lastRefusal = null; roomId = null }
    }

    private fun startTicker() {
        ticker?.interrupt()
        ticker = Thread({
            var n = 0L
            val every = (beatMs / tickMs.coerceAtLeast(1L)).coerceAtLeast(1L)
            while (!closed && session?.stopped == false) {
                runCatching { session?.tick() }
                if (++n % every == 0L) runCatching { beat() }
                try { Thread.sleep(tickMs) } catch (_: InterruptedException) { return@Thread }
            }
        }, "chess-online-link").apply { isDaemon = true; start() }
    }

    private fun stopSession() {
        runCatching { session?.stop() }
        ticker?.interrupt(); ticker = null
        synchronized(lock) { lock.notifyAll() }
    }

    companion object { const val MAX_ACKS = 64 }
}
