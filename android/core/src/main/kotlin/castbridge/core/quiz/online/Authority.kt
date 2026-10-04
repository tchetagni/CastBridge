package castbridge.core.quiz.online

import castbridge.core.quiz.QuizRoom

/** Ce qu'un client du Quiz attend d'une partie, que l'autorité soit la TV (local) ou le serveur (Internet) : surface exacte de [QuizRoom]. */
interface GameAuthority {
    val scope: PlayScope
    fun view(token: String?): Map<String, Any?>
    fun act(token: String?, action: String, questionId: String?, choice: Int?, arg: String?): QuizRoom.Act
    fun join(code: String?, name: String?, token: String?, device: String?): QuizRoom.JoinResult
    fun awaitChange(since: Long, timeoutMs: Long): Long
    fun safety(): SafetyView

    // --- w20-05a : ce que `QuizHttp` demande en plus à une autorité (valeurs par défaut : une autorité qui ne sait pas, ne casse rien) ---
    /** Ce jeton désigne-t-il un joueur de cette partie ? (401 sinon) */
    fun knows(token: String?): Boolean = token != null
    /** Le joueur est présent (chaque requête). */
    fun touch(token: String?) {}
    /** Le joueur quitte la partie. */
    fun leave(token: String?): Boolean = false
    /** La partie est terminée ou perdue : les routes répondent 410. */
    fun closed(): Boolean = false
}

/** Adaptateur de la salle d'aujourd'hui (sans la modifier) : `view` ajoute seulement la clé additive `safety`. */
class LocalAuthority(private val room: QuizRoom, private val facts: () -> SafetyFacts) : GameAuthority {
    override val scope: PlayScope get() = facts().scope

    override fun view(token: String?): Map<String, Any?> {
        val s = safety()
        val out = LinkedHashMap(room.view(token))
        out["safety"] = linkedMapOf("scope" to s.scope.name, "level" to s.level.name, "word" to s.word, "text" to s.text, "action" to s.action)
        return out
    }

    override fun act(token: String?, action: String, questionId: String?, choice: Int?, arg: String?) = room.act(token, action, questionId, choice, arg)
    override fun join(code: String?, name: String?, token: String?, device: String?) = room.join(code, name, token, device)
    override fun awaitChange(since: Long, timeoutMs: Long) = room.awaitChange(since, timeoutMs)
    override fun safety(): SafetyView = SafetySign.of(facts())
    override fun knows(token: String?) = room.player(token) != null
    override fun touch(token: String?) { room.player(token)?.let { room.touch(it) } }
    override fun leave(token: String?) = room.leave(token)
    override fun closed() = room.stage == QuizRoom.Stage.CLOSED
}
