package castbridge.core.quiz.online

import castbridge.core.connect.Routes

/**
 * Session de jeu en ligne de la TV (automate PUR : horloge MONOTONE et transport injectés). Une TV activée et connectée à Internet est la seule cliente du service
 * (DESIGN-W20-AMENDEMENT § 1, § 2.5) : `hello` (ticket `cbp1`) puis `create` ou `join` (activation `cbx1`), puis [authority] porte la partie ; les téléphones locaux
 * sont relayés par [RelayAuthority].
 *
 * Liaison (§ 2.7) : UNE connexion persistante ; si le flux tombe, le transport rouvre d'abord le flux de la MÊME session ; session perdue (HTTP 410) ⇒ [tick] ouvre un
 * NOUVEAU transport et envoie `resume` (192 octets) avec le dernier `seq`, sur la courbe 0, 2, 4, 8, 15, 30 s ; [link] = [TvLink.Online] | [TvLink.Resuming] | [TvLink.Lost]
 * (perdue à [LOST_AFTER_MS] exactement). Le ticket n'est demandé à la TV (fonction injectée) qu'à l'ouverture d'une session de service : création, entrée, ou reprise sur une NOUVELLE session (voir [reopen]).
 *
 * Règle 11 de docs/PLAY-PROTOCOL.md : `announcementServerNowMs` est l'heure serveur À L'ENVOI ; la TV y ajoute `rtt/2` (son RTT mesuré) pour que la question s'ouvre à
 * `opensAtServerMs` sur l'horloge du serveur ([QuestionClock]).
 */
class PlayTvSession(
    private val clock: () -> Long,
    private val transports: TransportFactory,
    private val ticket: () -> String?,
    private val via: () -> Routes.Via? = { null },
    private val tvHasNetwork: () -> Boolean = { true },
    private val curveSec: IntArray = CURVE_SEC,
    /** M-6 : ticket pour rouvrir une session par `resume` (un ticket encore valable suffit : rien n'est consommé) ; null = [ticket]. */
    private val resumeTicket: (() -> String?)? = null,
    /** H-3 : fabrique la preuve de possession de la clé d'installation pour (ticket, activation) ; null = pas de preuve (anciens montages, tests). */
    private val prover: ((String?, String) -> String?)? = null,
) {
    sealed class Intent {
        /** [stake] + [escrow] : Quiz MISÉ (games-G5), le blocage `cbe1` de cette TV ; sans eux, salle libre (message d'avant). `toString` ne montre jamais le blocage. */
        data class Create(val name: String?, val mode: String?, val rentals: List<String> = emptyList(), val stake: StakeSpec? = null, val escrow: String? = null) : Intent() {
            override fun toString() = "Create(name=$name, mode=$mode, stake=$stake, escrow=${if (escrow == null) "-" else PlayRedact.REDACTED})"
        }
        /** [escrow] : le blocage de cette TV quand la salle est misée (la TV l'a bloqué après que le service lui a dit la mise). */
        data class Join(val code: String, val name: String?, val escrow: String? = null) : Intent() {
            override fun toString() = "Join(code=${PlayRedact.code(code)}, name=$name, escrow=${if (escrow == null) "-" else PlayRedact.REDACTED})"
        }
        /**
         * Une salle de JEU à tour de rôle (échecs en ligne, games-G2) : le premier message est construit par l'appelant à partir de l'activation et de la preuve de possession (qui dépendent du ticket) ;
         * `create{game:"chess", chess, stake, escrow}` ou `join{code, escrow, spectate}`. Rejoué tel quel si la liaison tombe avant le `welcome` (comme une création de Quiz).
         */
        class Game(val open: (activation: String?, proof: String?) -> ClientMsg) : Intent() { override fun toString() = "Game" }
    }

    /** L'ouverture locale d'une question : `opensAtLocalMono` sur l'horloge monotone de la TV (peut être dans le passé si l'annonce est tardive). */
    data class QuestionClock(val questionId: String, val opensAtLocalMono: Long, val windowMs: Long, val receivedAtLocal: Long, val startNow: Boolean, val waitMs: Long, val remainingMs: Long)

    private val rtt = RttBook()
    private val relaySentAt = HashMap<Long, Long>()
    @Volatile private var transport: PlayTransport? = null
    @Volatile private var deviceHash: String? = null
    @Volatile private var activation: String? = null
    @Volatile private var intent: Intent? = null
    @Volatile private var unhealthySince: Long? = null
    @Volatile private var awaitingResume = false
    @Volatile private var nextAttemptAt: Long? = null
    @Volatile private var attemptNo = 0
    @Volatile var stopped = false; private set
    @Volatile var attempts = 0; private set
    @Volatile var questionClock: QuestionClock? = null; private set
    @Volatile var lastError: String? = null; private set
    /** Accusé d'un `relayAct` (ref, résultat) : branché par [RelayAuthority]. */
    @Volatile var onRelayAck: ((Long, String) -> Unit)? = null
    @Volatile var onChange: (() -> Unit)? = null
    lateinit var authority: ServerAuthority; private set

    val started: Boolean get() = transport != null
    /** La TV est assise dans la salle (son `welcome` est arrivé). */
    val seated: Boolean get() = started && authority.token != null
    val gone: Boolean get() = started && authority.lastGone != null
    fun rttMs(): Long = rtt.rtt(RTT_KEY)

    /** Ouvre la session : un seul appel du ticket (une création ou une entrée). */
    fun start(deviceHash: String?, activation: String?, intent: Intent) {
        this.deviceHash = deviceHash; this.activation = activation; this.intent = intent
        val tkt = ticket()
        openedTicket = tkt
        val tr = open(tkt)
        authority = ServerAuthority(tr).also { wire(it) }
        sendOpening(tkt)
    }

    /** Le ticket avec lequel la session de service courante a été ouverte (la preuve de possession lui est liée) ; jamais exposé. */
    @Volatile private var openedTicket: String? = null

    private fun open(tkt: String?): PlayTransport = transports.open(tkt).also { tr ->
        transport = tr
        if (tr is RttSource) tr.onRtt { rtt.sample(RTT_KEY, it) }
    }

    private fun wire(a: ServerAuthority) {
        a.onServerMessage = { onMessage(it) }
        a.onRelayAck = { ref, result ->
            relaySentAt.remove(ref)?.let { sent -> rtt.sample(RTT_KEY, (clock() - sent).coerceAtLeast(0)) }
            onRelayAck?.invoke(ref, result)
        }
    }

    private fun proofFor(tkt: String?): String? = activation?.let { a -> prover?.invoke(tkt, a) }

    private fun sendOpening(tkt: String?) {
        authority.hello(deviceHash, tkt)
        sendIntent(intent!!, tkt)
    }

    private fun sendIntent(i: Intent, tkt: String?) {
        when (i) {
            is Intent.Create -> authority.create(i.name, i.mode, activation, i.rentals, proofFor(tkt), i.stake, i.escrow)
            is Intent.Join -> authority.joinRoom(i.code, i.name, deviceHash, activation, proof = proofFor(tkt), escrow = i.escrow)
            is Intent.Game -> authority.sendOpening(i.open(activation, proofFor(tkt)))
        }
    }

    /**
     * Remplace l'ouverture à rejouer si la liaison tombe avant le `welcome` ET l'envoie tout de suite sur la liaison courante (même ticket, même connexion) : la TV qui apprend, par `STAKE_ESCROW_REQUIRED`, la mise d'une
     * salle, bloque sa mise puis revient avec son blocage SANS redemander de ticket (le refus n'a rien consommé côté service). Échecs : [Intent.Game] ; Quiz misé : [Intent.Join] avec son blocage.
     */
    fun reopenWith(next: Intent) {
        intent = next
        sendIntent(next, openedTicket)
    }

    /** Branche un écouteur de TOUS les messages serveur (en plus du traitement de la session) : le client de jeu y lit accusés, résultat et refus. Un seul écouteur. */
    @Volatile var onServerMessage: ((ServerMsg) -> Unit)? = null

    /** Relaie la réponse d'un téléphone local ; le départ est daté pour mesurer l'aller-retour (accusé). */
    fun relayAnswer(seatToken: String, questionId: String, choice: Int, localElapsedMono: Long): Long {
        val sentAt = clock()
        val ref = authority.relay(seatToken, questionId, choice, localElapsedMono)
        synchronized(relaySentAt) { relaySentAt[ref] = sentAt; while (relaySentAt.size > 64) relaySentAt.remove(relaySentAt.keys.first()) }
        return ref
    }

    private fun onMessage(m: ServerMsg) {
        when (m) {
            is ServerMsg.Question -> {
                val now = clock()
                // Règle 11 : l'annonce porte l'heure serveur À L'ENVOI ; elle est arrivée rtt/2 plus tard.
                val seenAt = m.serverNowMs + rtt.rtt(RTT_KEY) / 2
                val st = PlayTiming.start(m.opensAtServerMs, m.windowMs, seenAt)
                questionClock = QuestionClock(m.questionId, now + (m.opensAtServerMs - seenAt), m.windowMs, now, st.startNow, st.waitMs, st.remainingMs)
            }
            is ServerMsg.Welcome, is ServerMsg.State, is ServerMsg.Replay -> if (awaitingResume) { awaitingResume = false; refreshHealth() }
            // B-3 : une reprise refusée (salle disparue, siège purgé) ne se retente pas pendant 60 s
            is ServerMsg.Error -> if (awaitingResume && (m.reason == PlayReason.PLAY_BAD_CODE.name || m.reason == PlayReason.PLAY_ROOM_GONE.name)) fatal("salle perdue")
            else -> {}
        }
        onChange?.invoke()
        onServerMessage?.invoke(m)
    }

    /** À appeler souvent (200 ms à 1 s) : suit la santé de la liaison, rouvre une session perdue (courbe), passe à `Lost` à 60 s. */
    @Synchronized fun tick() {
        val tr = transport ?: return
        if (stopped) return
        val now = clock()
        if ((tr as? TransportHealth)?.certificateInvalid == true) { fatal("certificat non valide"); return }
        val state = tr.state
        if (state == PlayTransport.Status.OPEN && unhealthySince != null && !awaitingResume && authority.token != null) {
            // la liaison est revenue (même session) : un `resume` récupère ce qui a pu être perdu pendant la coupure ; la liaison n'est dite bonne qu'à sa réponse
            awaitingResume = true; authority.resume(authority.roomId!!, authority.token!!, authority.lastSeq)
        } else refreshHealth()
        if (state == PlayTransport.Status.OPEN) authority.onTransportOpen()   // vide la file locale (bornée) des envois faits pendant la réouverture ; sans effet si elle est vide
        if (unhealthySince != null && now - unhealthySince!! >= LOST_AFTER_MS) { stopped = true; closeTransport(); onChange?.invoke(); return }
        if (state == PlayTransport.Status.CLOSED) {
            // Première tentative aussitôt (courbe[0] = 0) ; la suivante est datée au moment de la tentative : 2, 4, 8, 15, 30, 30… s plus tard.
            if (now >= (nextAttemptAt ?: now)) { attemptNo++; nextAttemptAt = now + curveSec[minOf(attemptNo, curveSec.lastIndex)] * 1_000L; reopen() }
        }
    }

    private fun refreshHealth() {
        val healthy = transport?.state == PlayTransport.Status.OPEN && !awaitingResume
        if (healthy) { if (unhealthySince != null) { unhealthySince = null; attemptNo = 0; nextAttemptAt = null; onChange?.invoke() } }
        else if (unhealthySince == null) unhealthySince = clock()
    }

    /** Session de service perdue (410, flux irrécupérable) : nouveau transport, `hello` sans ticket, puis `resume(roomId, token, lastSeq)`. */
    private fun reopen() {
        attempts++
        val seatToken = authority.token
        // Un ticket frais à chaque NOUVELLE session de service : jamais assise = nouvelle création ou entrée (un ticket sert une fois) ; assise = reprise, mais le service
        // exige un ticket valide sur chaque POST d'un client sans `Origin` (OriginCheck) ; la fonction injectée peut mettre le dernier en cache quelques dizaines de secondes.
        val tkt = if (seatToken != null) (resumeTicket ?: ticket)() else ticket()   // M-6 : une reprise réutilise le ticket courant (rien n'est consommé)
        openedTicket = tkt
        val tr = open(tkt)
        authority.rebind(tr)
        if (seatToken == null) { sendOpening(tkt); return }
        awaitingResume = true
        authority.hello(deviceHash, tkt)
        authority.resume(authority.roomId!!, seatToken, authority.lastSeq)
    }

    private fun fatal(why: String) { lastError = why; stopped = true; if (unhealthySince == null) unhealthySince = clock() - LOST_AFTER_MS; closeTransport(); onChange?.invoke() }
    private fun closeTransport() { (transport as? AutoCloseable)?.let { runCatching { it.close() } } }

    /** Arrêt volontaire (retour au menu). */
    fun stop() { stopped = true; closeTransport() }

    /** Liaison de cette TV : bonne, en reprise (secondes) ou perdue (≥ 60 s, ou certificat non valide). */
    val link: TvLink get() {
        val since = unhealthySince ?: return TvLink.Online
        val elapsed = clock() - since
        return if (elapsed >= LOST_AFTER_MS) TvLink.Lost else TvLink.Resuming((elapsed / 1_000).toInt())
    }

    val certificateInvalid: Boolean get() = (transport as? TransportHealth)?.certificateInvalid == true

    /** Le signe « Partie sûre » reçu du serveur, ABAISSÉ par la cause locale (passerelle, reprise, perte, certificat, réseau). */
    fun safety(): SafetyView {
        val server = if (started) authority.safety() else SafetySign.of(SafetyFacts(PlayScope.INTERNET))
        return LinkCause.lower(server, via(), link, tvHasNetwork(), certificateInvalid)
    }

    companion object {
        val CURVE_SEC = intArrayOf(0, 2, 4, 8, 15, 30)
        const val LOST_AFTER_MS = 60_000L
        private const val RTT_KEY = "tv"
    }
}
