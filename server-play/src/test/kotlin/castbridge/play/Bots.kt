package castbridge.play

import castbridge.core.quiz.Json
import castbridge.core.quiz.QuizBank
import castbridge.core.quiz.online.ClientMsg
import castbridge.core.quiz.online.PlayCodec
import castbridge.core.quiz.online.PlayProtocol
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.fail

@Suppress("UNCHECKED_CAST")
fun Any?.m(k: String): Map<String, Any?> = (this as Map<String, Any?>)[k] as Map<String, Any?>

/** Ce que le client a reçu d'une annonce de question, avec l'heure locale de réception. */
class SeenQuestion(val index: Int, val questionId: String, val opensAtServerMs: Long, val serverNowMs: Long, val receivedLocalMs: Long, val choices: List<String>)

/**
 * Un joueur (ou l'hôte) scripté, sur n'importe quel transport. `correct(i)` dit s'il répond juste à la question i ; il répond à
 * `opensAt + delayMs` (horloge du serveur déduite de l'annonce) ; l'hôte « passe » la révélation et le classement.
 */
class Bot(val name: String?, val wire: Wire, private val bank: QuizBank, private val host: Boolean = false, private val correct: (Int) -> Boolean = { true },
          private val delayMs: Long = 100, private val cheatAtIndex: Int = -1) {
    val seen = ConcurrentHashMap<Int, SeenQuestion>()
    val acks = CopyOnWriteArrayList<Pair<Long, String>>()
    val raws = CopyOnWriteArrayList<String>()
    val errors = CopyOnWriteArrayList<String>()
    @Volatile var token: String? = null
    @Volatile var playerId: String? = null
    @Volatile var roomCode: String? = null
    @Volatile var lastView: Map<String, Any?>? = null
    @Volatile var finalRanking: List<String>? = null
    @Volatile private var running = true
    private val seq = AtomicLong(0)
    private val skipped = HashSet<String>()
    val earlyAckSeq = AtomicLong(-1)
    private val answeredIdx = HashSet<Int>()

    private val reader = Thread { loop() }.also { it.isDaemon = true; it.name = "bot-${name ?: "tv"}"; it.start() }

    fun send(m: ClientMsg) = wire.send(PlayCodec.encode(m))
    fun stop() { running = false; wire.close() }
    fun ackOf(ref: Long): String? = acks.firstOrNull { it.first == ref }?.second

    private fun loop() {
        while (running) {
            val raw = wire.next(300) ?: continue
            raws += raw
            val o = Json.parse(raw) as Map<*, *>
            when (o["t"]) {
                "welcome" -> { token = o["token"] as String; playerId = o["playerId"] as String?; roomCode = o["code"] as String }
                "question" -> onQuestion(o)
                "ack" -> acks += (o["ref"] as Number).toLong() to o["result"] as String
                "error" -> errors += o["reason"] as String
                "ping" -> send(ClientMsg.Pong(o["id"] as String))
                "state" -> onState(o["view"] as Map<String, Any?>)
            }
        }
    }

    private fun onQuestion(o: Map<*, *>) {
        val idx = (o["index"] as Number).toInt()
        val sq = SeenQuestion(idx, o["questionId"] as String, (o["opensAtServerMs"] as Number).toLong(), (o["serverNowMs"] as Number).toLong(), System.currentTimeMillis(),
            (o["choices"] as List<*>).map { it as String })
        if (seen.putIfAbsent(idx, sq) != null || host || name == null) return
        Thread {
            if (idx == cheatAtIndex) {   // triche : répondre avant l'ouverture
                val s = seq.incrementAndGet(); earlyAckSeq.set(s)
                send(ClientMsg.Act(sq.questionId, "answer", rightIndex(sq), null, s))
            }
            val wait = sq.receivedLocalMs + (sq.opensAtServerMs - sq.serverNowMs) + delayMs - System.currentTimeMillis()
            if (wait > 0) Thread.sleep(wait)
            val right = rightIndex(sq)
            val choice = if (correct(idx)) right else (right + 1) % 4
            synchronized(answeredIdx) { if (!answeredIdx.add(idx)) return@Thread }
            send(ClientMsg.Act(sq.questionId, "answer", choice, null, seq.incrementAndGet()))
        }.also { it.isDaemon = true }.start()
    }

    /** La bonne réponse dans l'ordre MÉLANGÉ que la salle a annoncé (le test connaît la banque, pas le client réel). */
    private fun rightIndex(sq: SeenQuestion): Int {
        val orig = bank.all.first { it.id == sq.questionId }
        return sq.choices.indexOf(orig.choices[orig.answer]).also { check(it >= 0) { "bonne réponse introuvable" } }
    }

    private fun onState(view: Map<String, Any?>) {
        lastView = view
        val duel = view["duel"] as? Map<String, Any?>
        if (duel != null) {
            val phase = duel["phase"] as String
            if (host && (phase == "REVEAL" || phase == "BOARD") && skipped.add("${duel["index"]}-$phase")) {
                Thread { Thread.sleep(60); send(ClientMsg.Act(null, "skip", null, null, seq.incrementAndGet())) }.also { it.isDaemon = true }.start()
            }
            if (view.m("room")["state"] == "FINISHED" && phase == "FINISHED") {
                finalRanking = (duel["ranking"] as List<*>).map { (it as Map<*, *>)["name"] as String }
            }
        }
    }

    fun waitFinished(timeoutMs: Long) {
        val end = System.currentTimeMillis() + timeoutMs
        while (finalRanking == null && System.currentTimeMillis() < end) Thread.sleep(50)
        if (finalRanking == null) fail("${name ?: "TV"} : partie non terminée (dernière phase ${lastView?.get("duel")?.let { (it as Map<*, *>)["phase"] }}, erreurs $errors, transport ${wire.failure})")
    }
}

/** Séquence attendue d'un hôte : `hello` avec ticket, `create`. */
fun hostStart(b: Bot, ticket: String) {
    b.send(ClientMsg.Hello(PlayProtocol.PROTO, PlayProtocol.CAPS, null, ticket))
    b.send(TestRights.create())
}
