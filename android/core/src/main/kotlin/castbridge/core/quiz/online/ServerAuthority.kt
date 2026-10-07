package castbridge.core.quiz.online

import castbridge.core.quiz.QuizRoom

/**
 * Adaptateur CLIENT de [GameAuthority] sur un [PlayTransport] (la TV et le téléphone en Internet) : `view` = dernier `state` reçu (+ clé additive
 * `safety`, comme [LocalAuthority]), `act` = message `act` avec `seq` ; file de sortie bornée tant que le transport se connecte ; `awaitChange` sur le
 * `seq` de la salle. Aucune décision de jeu ici : tout vient du serveur. Répond seul aux `ping`.
 */
class ServerAuthority(transport: PlayTransport, override val scope: PlayScope = PlayScope.INTERNET) : GameAuthority {
    @Volatile private var transport: PlayTransport = transport
    private val lock = Object()
    private var state: ServerMsg.State? = null
    private var safety: SafetyView? = null
    private var welcome: ServerMsg.Welcome? = null
    private var lastError: ServerMsg.Error? = null
    private val acks = HashMap<Long, String>()
    private val outbox = ArrayDeque<String>()
    private var clientSeq = 0L
    private var changes = 0L
    /** w20-05a : un `join` de relais attend SA réponse (le `welcome` du siège du téléphone ne doit pas écraser celui de la TV). */
    private var pendingRelayJoin = false
    private var relayWelcome: ServerMsg.Welcome? = null
    private var relayError: ServerMsg.Error? = null
    private val relayRefs = HashSet<Long>()

    /** Dernière annonce de question reçue (porte `opensAtServerMs`, instant absolu sur l'horloge du serveur). */
    @Volatile var lastQuestion: ServerMsg.Question? = null; private set
    @Volatile var lastReveal: ServerMsg.Reveal? = null; private set
    @Volatile var lastGone: ServerMsg.RoomGone? = null; private set
    val token: String? get() = synchronized(lock) { welcome?.token }
    val role: PlayRole? get() = synchronized(lock) { welcome?.role }
    val roomId: String? get() = synchronized(lock) { welcome?.roomId }
    val code: String? get() = synchronized(lock) { welcome?.code }
    val lastSeq: Long get() = synchronized(lock) { state?.seq ?: 0L }
    /** Compteur de changements locaux : tout message reçu et tout [poke] l'augmentent ; réveille [awaitChanges]. */
    val changeCount: Long get() = synchronized(lock) { changes }
    /** w20-05a : appelé (hors verrou) pour chaque message serveur décodé, après la mise à jour de l'état. */
    @Volatile var onServerMessage: ((ServerMsg) -> Unit)? = null
    /** w20-05a : accusé d'un `relayAct` : (ref, résultat). Les accusés de relais ne sont pas gardés dans la table des `act`. */
    @Volatile var onRelayAck: ((Long, String) -> Unit)? = null

    init { transport.onMessage { receive(it) } }

    /** Reprise sur un NOUVEAU transport (session perdue) : l'état, les jetons et la dernière question sont gardés. */
    fun rebind(t: PlayTransport) { transport = t; t.onMessage { receive(it) } }

    private fun receive(text: String) {
        val m = PlayCodec.decodeServer(text) ?: return
        var relayAck: Pair<Long, String>? = null
        synchronized(lock) {
            changes++
            when (m) {
                is ServerMsg.State -> state = m
                is ServerMsg.Safety -> safety = m.view
                is ServerMsg.Welcome -> {
                    val own = welcome
                    when {
                        own != null && m.token == own.token -> welcome = m                       // reprise de la TV : son propre siège, jamais celui d'un téléphone (H-2)
                        own == null -> welcome = m                                               // la TV n'est pas encore assise : c'est le sien
                        pendingRelayJoin || (m.role == PlayRole.PLAYER && own.role != PlayRole.PLAYER) -> {
                            if (pendingRelayJoin) { relayWelcome = m; pendingRelayJoin = false }   // siège d'un téléphone ; en retard (entrée expirée) : ignoré, jamais `welcome = m`
                        }
                        else -> welcome = m
                    }
                }
                is ServerMsg.Error -> { lastError = m; if (pendingRelayJoin && isJoinRefusal(m.reason)) { relayError = m; pendingRelayJoin = false } }
                is ServerMsg.Ack -> if (relayRefs.remove(m.ref)) relayAck = m.ref to m.result else { acks[m.ref] = m.result; while (acks.size > MAX_ACKS) acks.remove(acks.keys.first()) }   // les accusés d'une partie d'échecs (un par coup) sont lus par l'écouteur de messages : la table reste bornée
                is ServerMsg.Question -> lastQuestion = m
                is ServerMsg.Reveal -> lastReveal = m
                is ServerMsg.RoomGone -> lastGone = m
                is ServerMsg.Ping, is ServerMsg.Replay, is ServerMsg.Result -> {}   // `result` : parties misées d'échecs, jamais dans une salle de Quiz
            }
            lock.notifyAll()
        }
        relayAck?.let { (ref, r) -> onRelayAck?.invoke(ref, r) }
        onServerMessage?.invoke(m)
        if (m is ServerMsg.Ping) send(ClientMsg.Pong(m.id))
    }

    /** Un refus propre à une entrée (nom, salle pleine, code…) : les avis de débit ou de maintenance n'ont rien à voir avec l'entrée d'un téléphone (H-2). */
    private fun isJoinRefusal(reason: String) = reason != PlayReason.PLAY_BUSY.name && reason != "PLAY_MAINTENANCE"

    private fun send(m: ClientMsg): Boolean {
        val text = PlayCodec.encode(m)
        return when (transport.state) {
            PlayTransport.Status.OPEN -> { transport.send(text); true }
            PlayTransport.Status.CONNECTING -> { synchronized(lock) { outbox.addLast(text); while (outbox.size > MAX_OUTBOX) outbox.removeFirst() }; false }
            PlayTransport.Status.CLOSED -> false
        }
    }

    /** À appeler quand le transport passe à OPEN : vide la file locale (bornée) dans l'ordre. */
    fun onTransportOpen() {
        val pending = synchronized(lock) { outbox.toList().also { outbox.clear() } }
        pending.forEach { transport.send(it) }
    }

    fun hello(deviceHash: String? = null, ticket: String? = null) { send(ClientMsg.Hello(PlayProtocol.PROTO, PlayProtocol.CAPS, deviceHash, ticket)) }
    /** Envoie un premier message déjà construit (salles de jeu à tour de rôle : `create{game…}` ou `join{escrow…}`) ; la réponse arrive par [token], [role], [lastErrorOrNull] comme pour [joinRoom]. */
    fun sendOpening(m: ClientMsg) { synchronized(lock) { welcome = null; lastError = null }; send(m) }
    /** Envoie une action de jeu (`move`, `resign`, `draw`, `cancel`) et rend sa référence (`seq`) : l'accusé arrive à l'écouteur de messages. Faux = pas envoyée (liaison en réouverture : mise en file bornée). */
    fun gameAct(op: String, arg: String?, ply: Int?): Long { val ref = synchronized(lock) { ++clientSeq }; send(ClientMsg.GameAct(op, arg, ply, ref)); return ref }
    fun create(name: String?, mode: String?, activation: String? = null, rentals: List<String> = emptyList(), proof: String? = null) { send(ClientMsg.Create(name, mode, activation, rentals, proof)) }
    fun resume(roomId: String, token: String, lastSeq: Long) { send(ClientMsg.Resume(roomId, token, lastSeq)) }
    fun setScope(open: Boolean) { send(ClientMsg.Scope(open)) }
    /** Relaie la réponse d'un téléphone local ; rend la référence (`seq`) dont l'accusé arrive à [onRelayAck]. */
    fun relay(token: String, questionId: String, choice: Int, localElapsedMono: Long): Long {
        val ref = synchronized(lock) { clientSeq += 1; relayRefs += clientSeq; while (relayRefs.size > MAX_RELAY_REFS) relayRefs.remove(relayRefs.first()); clientSeq }
        send(ClientMsg.RelayAct(token, questionId, choice, localElapsedMono, ref)); return ref
    }

    /**
     * Enregistre un joueur LOCAL de cette TV (siège relayé) et attend la réponse du service, au plus [timeoutMs] (par tranches de 50 ms : pas d'horloge ici).
     * Rend le `welcome` du siège, ou l'erreur du service ; `token` non nul = reprise d'un siège déjà accordé (après une reprise de la TV).
     */
    fun relayJoin(code: String, name: String?, device: String?, token: String? = null, timeoutMs: Long = 15_000L): Pair<ServerMsg.Welcome?, ServerMsg.Error?> {
        synchronized(lock) { relayWelcome = null; relayError = null; pendingRelayJoin = true }
        send(ClientMsg.Join(code, name, token, device, false))
        synchronized(lock) {
            var left = timeoutMs
            while (relayWelcome == null && relayError == null && left > 0) { lock.wait(50L); left -= 50L }
            pendingRelayJoin = false
            return relayWelcome to relayError
        }
    }

    /**
     * Entrée d'une TV NON assise dans la salle d'un autre hôte (fire-and-forget : la réponse arrive par [token]/[role]/[lastErrorOrNull]).
     * Une TV invitée entre en SPECTATRICE par défaut : elle relaie les téléphones de SON salon (sièges relayés) et ne prend pas elle-même de siège de table (sinon son nom figure au classement).
     * L'activation `cbx1` est jointe au `join` (w20-04b : le service l'exige quand le jeu hors TV est fermé).
     */
    @Suppress("UNUSED_PARAMETER")
    fun joinRoom(code: String, name: String?, deviceHash: String?, activation: String?, spectate: Boolean = true, proof: String? = null) { synchronized(lock) { welcome = null; lastError = null }; send(ClientMsg.Join(code, name, null, deviceHash, spectate, activation, proof)) }

    /** Réveille [awaitChanges] (changement local sans message serveur). */
    fun poke() = synchronized(lock) { changes++; lock.notifyAll() }

    /** Attend que [changeCount] dépasse [since] (ou la fin de la salle) ; par tranches de 50 ms. */
    fun awaitChanges(since: Long, timeoutMs: Long): Long = synchronized(lock) {
        var left = timeoutMs
        while (changes <= since && lastGone == null && left > 0) { val step = minOf(left, 50L); lock.wait(step); left -= step }
        changes
    }
    fun lastErrorOrNull(): ServerMsg.Error? = synchronized(lock) { lastError }

    override fun view(token: String?): Map<String, Any?> {
        val s = safety()
        val out = LinkedHashMap<String, Any?>(synchronized(lock) { state?.view } ?: emptyMap())
        out["safety"] = linkedMapOf("scope" to s.scope.name, "level" to s.level.name, "word" to s.word, "text" to s.text, "action" to s.action)
        return out
    }

    override fun act(token: String?, action: String, questionId: String?, choice: Int?, arg: String?): QuizRoom.Act {
        val ref = synchronized(lock) { ++clientSeq }   // B-2 : même verrou que `relay`
        val sent = send(ClientMsg.Act(questionId, action, choice, arg, ref))
        if (!sent) return if (transport.state == PlayTransport.Status.CLOSED) QuizRoom.Act.CLOSED else QuizRoom.Act.IGNORED
        val r = synchronized(lock) { acks.remove(ref) } ?: return QuizRoom.Act.OK   // transport asynchrone : l'accusé viendra plus tard
        return when (r) {
            "OK", "SAME" -> QuizRoom.Act.OK
            "CLOSED" -> QuizRoom.Act.CLOSED
            "FORBIDDEN" -> QuizRoom.Act.FORBIDDEN
            "BAD_REQUEST" -> QuizRoom.Act.BAD_REQUEST
            "UNKNOWN_PLAYER" -> QuizRoom.Act.UNKNOWN_PLAYER
            else -> QuizRoom.Act.IGNORED
        }
    }

    override fun join(code: String?, name: String?, token: String?, device: String?): QuizRoom.JoinResult {
        synchronized(lock) { welcome = null; lastError = null }
        send(ClientMsg.Join(code.orEmpty(), name, token, device, false))
        synchronized(lock) {
            welcome?.let { w -> return QuizRoom.JoinResult(QuizRoom.Join.OK, QuizRoom.Player(w.playerId ?: "?", w.token, name.orEmpty(), 0L)) }
            val e = lastError ?: return QuizRoom.JoinResult(QuizRoom.Join.CLOSED)
            return QuizRoom.JoinResult(when (e.reason) {
                PlayReason.PLAY_BAD_CODE.name -> QuizRoom.Join.BAD_CODE
                PlayReason.PLAY_ROOM_FULL.name -> QuizRoom.Join.FULL
                PlayProtocol.BAD_REQUEST -> QuizRoom.Join.BAD_NAME
                else -> QuizRoom.Join.CLOSED
            })
        }
    }

    override fun awaitChange(since: Long, timeoutMs: Long): Long = synchronized(lock) {
        var left = timeoutMs   // pas d'horloge ici (OnlinePurityTest) : on attend par tranches de 50 ms au plus
        while ((state?.seq ?: 0L) <= since && lastGone == null && left > 0) {
            val step = minOf(left, 50L)
            lock.wait(step); left -= step
        }
        state?.seq ?: 0L
    }

    override fun safety(): SafetyView = synchronized(lock) { safety } ?: SafetySign.of(SafetyFacts(scope))

    companion object { const val MAX_OUTBOX = 20; const val MAX_RELAY_REFS = 64; const val MAX_ACKS = 128 }
}
