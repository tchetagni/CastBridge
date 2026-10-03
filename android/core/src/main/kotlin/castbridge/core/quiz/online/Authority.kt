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
}
