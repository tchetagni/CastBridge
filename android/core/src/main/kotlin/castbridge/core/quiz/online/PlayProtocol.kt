package castbridge.core.quiz.online

/** Constantes du protocole `play-v1` (docs/PLAY-PROTOCOL.md). Additif : un nouveau champ obligatoire = un nouveau `PROTO`. */
object PlayProtocol {
    const val PROTO = 1
    const val NAME = "play-v1"
    /** Capacités annoncées dans `hello` ; le serveur répond avec l'intersection. */
    val CAPS = listOf("play1", "sse", "longpoll", "relay", "spectate")

    /** Taille maximale d'un message client (octets UTF-8). */
    const val MAX_MESSAGE_BYTES = 2_048
    const val MAX_NAME = 16
    const val MAX_ID = 64
    const val MAX_TOKEN = 64
    const val MAX_ARG = 256
    const val MAX_TICKET = 1_200
    const val MAX_CAPS = 12
    const val MAX_REASON = 64
    const val MAX_ELAPSED_MS = 60_000L

    /** Limites reprises de `QuizHttp` (existantes) : flux ouverts, rafale, débit, codes faux. */
    const val MAX_STREAMS = 12
    const val BURST = 30
    const val RATE_PER_SEC = 10
    const val MAX_BAD_CODES_PER_IP = 10
    const val BAD_CODE_WINDOW_MS = 5 * 60_000L

    /** Actions permises dans `act` (la liste ferme le champ). */
    val ACTIONS = setOf("answer", "vote", "suggest", "report", "select", "cancel", "confirm", "next", "walk", "fifty", "audience", "phone",
        "mode", "start", "skip", "lobby", "end", "autohost", "candidate")

    /** Codes d'erreur du protocole en plus des `PlayReason` : `UNSUPPORTED`, `BAD_REQUEST`, `FORBIDDEN`, `UNKNOWN_PLAYER`. */
    const val UNSUPPORTED = "UNSUPPORTED"
    const val BAD_REQUEST = "BAD_REQUEST"
    const val FORBIDDEN = "FORBIDDEN"
    const val UNKNOWN_PLAYER = "UNKNOWN_PLAYER"
}

/** Rôle d'une connexion dans une salle. */
enum class PlayRole { HOST, PLAYER, SPECTATOR }

/** Messages client → serveur. Chacun porte `t` (type) ; `seq` est le compteur du client (écho dans `ack`). */
sealed class ClientMsg {
    abstract val type: String

    data class Hello(val proto: Int, val caps: List<String>, val deviceHash: String?, val ticket: String?) : ClientMsg() { override val type get() = "hello" }
    /** Premier message de l'hôte (TV) : crée la salle ; `mode` = MILLIONAIRE | DUEL (facultatif). `name` null = la TV ne joue pas. */
    data class Create(val name: String?, val mode: String?) : ClientMsg() { override val type get() = "create" }
    data class Join(val code: String, val name: String?, val token: String?, val deviceHash: String?, val spectate: Boolean) : ClientMsg() { override val type get() = "join" }
    data class Resume(val roomId: String, val token: String, val lastSeq: Long) : ClientMsg() { override val type get() = "resume" }
    data class Act(val questionId: String?, val action: String, val choice: Int?, val arg: String?, val seq: Long) : ClientMsg() { override val type get() = "act" }
    /** La TV relaie la réponse d'un de ses joueurs locaux avec son temps mesuré sur SON horloge monotone. */
    data class RelayAct(val token: String, val questionId: String, val choice: Int, val localElapsedMono: Long, val seq: Long) : ClientMsg() { override val type get() = "relayAct" }
    data class Scope(val open: Boolean) : ClientMsg() { override val type get() = "scope" }
    data class Kick(val playerId: String) : ClientMsg() { override val type get() = "kick" }
    data class Mute(val playerId: String, val muted: Boolean) : ClientMsg() { override val type get() = "mute" }
    data class Report(val questionId: String?, val reason: String) : ClientMsg() { override val type get() = "report" }
    data class Pong(val id: String) : ClientMsg() { override val type get() = "pong" }
}

/** Messages serveur → client. `seq` est le numéro d'évènement de la salle (croissant). */
sealed class ServerMsg {
    abstract val type: String
    abstract val seq: Long

    data class Welcome(override val seq: Long, val roomId: String, val code: String, val token: String, val role: PlayRole, val playerId: String?,
                       val proto: Int, val caps: List<String>) : ServerMsg() { override val type get() = "welcome" }
    /** Vue complète du joueur (la vue différentielle viendra comme capacité additive). `full` : resynchronisation complète. */
    data class State(override val seq: Long, val view: Map<String, Any?>, val full: Boolean) : ServerMsg() { override val type get() = "state" }
    /** Annonce de la question : UNE question, sans réponse ; `opensAtServerMs` = instant absolu (horloge du serveur) de l'ouverture. */
    data class Question(override val seq: Long, val questionId: String, val index: Int, val count: Int, val text: String, val choices: List<String>,
                        val opensAtServerMs: Long, val serverNowMs: Long, val windowMs: Long) : ServerMsg() { override val type get() = "question" }
    /** Révélation, envoyée seulement après la clôture. */
    data class Reveal(override val seq: Long, val questionId: String, val index: Int, val answer: Int, val explanation: String?) : ServerMsg() { override val type get() = "reveal" }
    data class Safety(override val seq: Long, val view: SafetyView) : ServerMsg() { override val type get() = "safety" }
    data class Ping(override val seq: Long, val id: String, val serverNowMs: Long) : ServerMsg() { override val type get() = "ping" }
    data class Error(override val seq: Long, val reason: String, val message: String, val retryable: Boolean) : ServerMsg() { override val type get() = "error" }
    data class RoomGone(override val seq: Long, val reason: String) : ServerMsg() { override val type get() = "roomGone" }
    data class Replay(override val seq: Long, val events: List<EventRing.Event>) : ServerMsg() { override val type get() = "replay" }
    /** Accusé d'un `act` : `ref` = `seq` du client ; `result` ∈ OK, SAME, CLOSED, UNKNOWN_QUESTION, TOO_EARLY, FORBIDDEN, BAD_REQUEST, UNKNOWN_PLAYER, IGNORED. */
    data class Ack(override val seq: Long, val ref: Long, val result: String) : ServerMsg() { override val type get() = "ack" }
}
