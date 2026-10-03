package castbridge.core.quiz.online

import castbridge.core.quiz.QuizRoom

/**
 * Adaptateur CLIENT de [GameAuthority] sur un [PlayTransport] (la TV et le téléphone en Internet) : `view` = dernier `state` reçu (+ clé additive
 * `safety`, comme [LocalAuthority]), `act` = message `act` avec `seq` ; file de sortie bornée tant que le transport se connecte ; `awaitChange` sur le
 * `seq` de la salle. Aucune décision de jeu ici : tout vient du serveur. Répond seul aux `ping`.
 */
class ServerAuthority(private val transport: PlayTransport, override val scope: PlayScope = PlayScope.INTERNET) : GameAuthority {
    private val lock = Object()
    private var state: ServerMsg.State? = null
    private var safety: SafetyView? = null
    private var welcome: ServerMsg.Welcome? = null
    private var lastError: ServerMsg.Error? = null
    private val acks = HashMap<Long, String>()
    private val outbox = ArrayDeque<String>()
    private var clientSeq = 0L

    /** Dernière annonce de question reçue (porte `opensAtServerMs`, instant absolu sur l'horloge du serveur). */
    @Volatile var lastQuestion: ServerMsg.Question? = null; private set
    @Volatile var lastReveal: ServerMsg.Reveal? = null; private set
    @Volatile var lastGone: ServerMsg.RoomGone? = null; private set
    val token: String? get() = synchronized(lock) { welcome?.token }
    val role: PlayRole? get() = synchronized(lock) { welcome?.role }
    val roomId: String? get() = synchronized(lock) { welcome?.roomId }
    val code: String? get() = synchronized(lock) { welcome?.code }
    val lastSeq: Long get() = synchronized(lock) { state?.seq ?: 0L }

    init { transport.onMessage { receive(it) } }

    private fun receive(text: String) {
        val m = PlayCodec.decodeServer(text) ?: return
        synchronized(lock) {
            when (m) {
                is ServerMsg.State -> state = m
                is ServerMsg.Safety -> safety = m.view
                is ServerMsg.Welcome -> welcome = m
                is ServerMsg.Error -> lastError = m
                is ServerMsg.Ack -> acks[m.ref] = m.result
                is ServerMsg.Question -> lastQuestion = m
                is ServerMsg.Reveal -> lastReveal = m
                is ServerMsg.RoomGone -> lastGone = m
                is ServerMsg.Ping, is ServerMsg.Replay -> {}
            }
            lock.notifyAll()
        }
        if (m is ServerMsg.Ping) send(ClientMsg.Pong(m.id))
    }

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
    fun create(name: String?, mode: String?, activation: String? = null, rentals: List<String> = emptyList()) { send(ClientMsg.Create(name, mode, activation, rentals)) }
    fun resume(roomId: String, token: String, lastSeq: Long) { send(ClientMsg.Resume(roomId, token, lastSeq)) }
    fun setScope(open: Boolean) { send(ClientMsg.Scope(open)) }
    fun relay(token: String, questionId: String, choice: Int, localElapsedMono: Long) { send(ClientMsg.RelayAct(token, questionId, choice, localElapsedMono, ++clientSeq)) }
    fun lastErrorOrNull(): ServerMsg.Error? = synchronized(lock) { lastError }

    override fun view(token: String?): Map<String, Any?> {
        val s = safety()
        val out = LinkedHashMap<String, Any?>(synchronized(lock) { state?.view } ?: emptyMap())
        out["safety"] = linkedMapOf("scope" to s.scope.name, "level" to s.level.name, "word" to s.word, "text" to s.text, "action" to s.action)
        return out
    }

    override fun act(token: String?, action: String, questionId: String?, choice: Int?, arg: String?): QuizRoom.Act {
        val ref = ++clientSeq
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

    companion object { const val MAX_OUTBOX = 20 }
}
