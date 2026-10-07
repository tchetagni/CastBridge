package castbridge.core.quiz.online

import castbridge.core.quiz.QuizRoom
import java.security.SecureRandom

/**
 * [GameAuthority] des téléphones LOCAUX d'une TV connectée au service (DESIGN-W20-AMENDEMENT § 2.5) : le téléphone ne parle qu'à sa TV (page `/quiz`, comme aujourd'hui) ;
 * la TV relaie. `join` ⇒ `relayJoin` sur le siège de la TV (jeton LOCAL ↔ jeton de SIÈGE serveur, au plus [maxPhones]) ; `act(answer)` ⇒ `relayAct` avec `localElapsedMono`,
 * mesuré sur l'horloge MONOTONE de la TV depuis l'ouverture locale de la question ; `view(token)` = la vue du siège de la TV (classement, joueurs) recomposée au format de
 * `QuizRoom.view` pour CE téléphone, avec la dernière `question`, le dernier `reveal` et l'accusé de ce joueur. Aucune bonne réponse avant `reveal`.
 *
 * Le jeton de siège serveur ne quitte jamais la TV : ni vue, ni journal.
 */
class RelayAuthority(
    private val session: PlayTvSession,
    private val localClock: () -> Long,
    private val maxPhones: Int = MAX_PHONES,
    private val random: java.util.Random = SecureRandom(),
    private val safetyOf: () -> SafetyView = { session.safety() },
    /** relay-R1: added to the window the home phones see when this TV has Internet only through a phone ([PlayRelayProfile.windowFor], bounded by the service's own grace); 0 otherwise. Read at each call. */
    private val extraWindowMs: () -> Long = { 0L },
) : GameAuthority {
    override val scope: PlayScope get() = PlayScope.INTERNET

    private class Phone(val local: String, val seatToken: String, val playerId: String, var name: String) {
        /** Réponse envoyée par question : (choix, temps local, accusé). */
        val answers = HashMap<String, Answer>()
    }
    private class Answer(val choice: Int, val elapsedMs: Long, var ack: String? = null)

    private val lock = Object()
    private val phones = LinkedHashMap<String, Phone>()        // par jeton local
    private val byRef = HashMap<Long, Pair<Phone, String>>()  // référence d'un relayAct → (téléphone, question)

    init { session.onRelayAck = { ref, result -> onAck(ref, result) } }

    private fun onAck(ref: Long, result: String) {
        synchronized(lock) { byRef.remove(ref)?.let { (p, q) -> p.answers[q]?.ack = result } }
        session.authority.poke()
    }

    /** Nombre de téléphones locaux assis. */
    fun phoneCount(): Int = synchronized(lock) { phones.size }

    override fun join(code: String?, name: String?, token: String?, device: String?): QuizRoom.JoinResult {
        val clean = QuizRoom.cleanName(name)
        synchronized(lock) {   // reprise : le téléphone revient avec son jeton local
            token?.let { t -> phones[t]?.let { p -> clean?.let { n -> p.name = n }; return QuizRoom.JoinResult(QuizRoom.Join.OK, QuizRoom.Player(p.playerId, p.local, p.name, 0L)) } }
            if (session.gone || session.stopped) return QuizRoom.JoinResult(QuizRoom.Join.CLOSED)
            if (clean == null) return QuizRoom.JoinResult(QuizRoom.Join.BAD_NAME)
            if (phones.size >= maxPhones) return QuizRoom.JoinResult(QuizRoom.Join.FULL)   // « salle complète » : au plus 8 téléphones par TV
        }
        val a = session.authority
        val roomCode = a.code ?: return QuizRoom.JoinResult(QuizRoom.Join.CLOSED)
        val (welcome, error) = a.relayJoin(roomCode, clean, device)
        if (welcome == null) return QuizRoom.JoinResult(when (error?.reason) {
            PlayReason.PLAY_ROOM_FULL.name -> QuizRoom.Join.FULL
            PlayProtocol.BAD_REQUEST, PlayReason.BAD_NAME.name -> QuizRoom.Join.BAD_NAME   // B-1 : un pseudonyme refusé par le service se dit « choisissez un autre nom »
            else -> QuizRoom.Join.CLOSED
        })
        synchronized(lock) {
            val local = ByteArray(16).also { random.nextBytes(it) }.joinToString("") { "%02x".format(it) }
            val p = Phone(local, welcome.token, welcome.playerId ?: "?", clean ?: "?")
            phones[local] = p
            a.poke()
            return QuizRoom.JoinResult(QuizRoom.Join.OK, QuizRoom.Player(p.playerId, p.local, p.name, 0L))
        }
    }

    override fun act(token: String?, action: String, questionId: String?, choice: Int?, arg: String?): QuizRoom.Act {
        val p = synchronized(lock) { token?.let { phones[it] } } ?: return QuizRoom.Act.UNKNOWN_PLAYER
        if (session.gone || session.stopped) return QuizRoom.Act.CLOSED
        if (action != "answer") return QuizRoom.Act.IGNORED          // POC : seules les réponses sont relayées
        val qc = session.questionClock
        val q = questionId.orEmpty()
        if (qc == null || qc.questionId != q) return QuizRoom.Act.IGNORED
        if (choice == null || choice !in 0..3) return QuizRoom.Act.BAD_REQUEST
        val now = localClock()
        if (now < qc.opensAtLocalMono) return QuizRoom.Act.IGNORED    // pas encore ouverte : rien ne part (le service dirait TOO_EARLY)
        val ref: Long
        synchronized(lock) {
            p.answers[q]?.let { return if (it.choice == choice) QuizRoom.Act.OK else QuizRoom.Act.FORBIDDEN }   // idempotent, une seule réponse par question
            val elapsed = (now - qc.opensAtLocalMono).coerceIn(0L, PlayProtocol.MAX_ELAPSED_MS)
            p.answers[q] = Answer(choice, elapsed)
            ref = session.relayAnswer(p.seatToken, q, choice, elapsed)
            byRef[ref] = p to q
            while (byRef.size > 64) byRef.remove(byRef.keys.first())
        }
        session.authority.poke()
        return QuizRoom.Act.OK
    }

    override fun knows(token: String?): Boolean = synchronized(lock) { token != null && token in phones }
    override fun touch(token: String?) {}
    override fun leave(token: String?): Boolean = synchronized(lock) { token != null && phones.containsKey(token) }
    override fun closed(): Boolean = session.gone || session.stopped

    override fun awaitChange(since: Long, timeoutMs: Long): Long = session.authority.awaitChanges(since, timeoutMs)
    override fun safety(): SafetyView = safetyOf()

    @Suppress("UNCHECKED_CAST")
    override fun view(token: String?): Map<String, Any?> {
        val a = session.authority
        val out = LinkedHashMap<String, Any?>(a.view(null))
        out["v"] = a.changeCount
        val s = safety()
        out["safety"] = linkedMapOf("scope" to s.scope.name, "level" to s.level.name, "word" to s.word, "text" to s.text, "action" to s.action)
        val duel = (out["duel"] as? Map<String, Any?>)?.let { composeDuel(it, a) }
        if (duel != null) out["duel"] = duel
        val p = synchronized(lock) { token?.let { phones[it] } } ?: return out
        val players = (out["players"] as? List<Map<String, Any?>>).orEmpty()
        val mine = players.firstOrNull { it["id"] == p.playerId }
        val qid = (duel?.get("question") as? Map<String, Any?>)?.get("id") as? String
        val ans = synchronized(lock) { qid?.let { p.answers[it] } }
        out["me"] = linkedMapOf("id" to p.playerId, "name" to (mine?.get("name") ?: p.name), "role" to "player", "score" to (mine?.get("score") ?: 0), "balance" to null)
        if (duel != null) {
            val d = LinkedHashMap(duel)
            d["myAnswer"] = ans?.choice
            val closed = d["phase"] == "REVEAL" || d["phase"] == "BOARD" || d["phase"] == "FINISHED"
            val rank = (d["ranking"] as? List<Map<String, Any?>>)?.firstOrNull { it["id"] == p.playerId }
            d["outcome"] = if (closed && ans != null) linkedMapOf("correct" to (rank?.get("correct") ?: false), "points" to (rank?.get("gained") ?: 0), "ms" to ans.elapsedMs) else null
            out["duel"] = d
        }
        return out
    }

    /**
     * La vue du siège de la TV peut être en retard sur `question` / `reveal` (l'état part après, et il peut être coalescé) : la dernière annonce reçue et la dernière
     * révélation complètent la vue. La bonne réponse n'entre QUE par `reveal` (ou par la vue du serveur, déjà filtrée) ; le temps restant est celui de l'horloge LOCALE.
     */
    @Suppress("UNCHECKED_CAST")
    private fun composeDuel(duel: Map<String, Any?>, a: ServerAuthority): Map<String, Any?> {
        val d = LinkedHashMap(duel)
        val q = a.lastQuestion
        val curIndex = (d["index"] as? Number)?.toInt() ?: -1
        val curId = (d["question"] as? Map<String, Any?>)?.get("id") as? String
        if (q != null && curId != q.questionId && q.index > curIndex) {      // annonce plus récente que la vue : nouvelle question ouverte (ou en attente d'ouverture)
            d["phase"] = "QUESTION"; d["index"] = q.index; d["count"] = q.count
            d["question"] = linkedMapOf("id" to q.questionId, "text" to q.text, "choices" to q.choices, "answer" to null, "explanation" to null)
            d["answeredCount"] = 0; d["distribution"] = null
        }
        val id = (d["question"] as? Map<String, Any?>)?.get("id") as? String
        val r = a.lastReveal
        if (r != null && id == r.questionId && d["phase"] == "QUESTION" && (d["question"] as Map<String, Any?>)["answer"] == null) {   // révélation plus récente que la vue
            d["phase"] = "REVEAL"
            d["question"] = LinkedHashMap(d["question"] as Map<String, Any?>).also { it["answer"] = r.answer; it["explanation"] = r.explanation }
        }
        val qc = session.questionClock
        if (d["phase"] == "QUESTION" && qc != null && qc.questionId == id) {
            val sinceOpen = localClock() - qc.opensAtLocalMono
            val window = qc.windowMs + extraWindowMs().coerceAtLeast(0L)
            d["remainingMs"] = if (sinceOpen < 0) window else (window - sinceOpen).coerceAtLeast(0L)
            d["waitMs"] = (-sinceOpen).coerceAtLeast(0L)
        }
        return d
    }

    companion object {
        /** Au plus 8 téléphones par TV. TODO : utiliser `TrustRegistry.MAX_PHONES` quand la branche claude/tv-phones-max8 en portera la constante (absente à ce jour). */
        const val MAX_PHONES = 8
    }
}
