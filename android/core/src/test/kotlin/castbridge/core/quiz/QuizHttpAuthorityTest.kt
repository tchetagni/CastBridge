package castbridge.core.quiz

import castbridge.core.FakePlayer
import castbridge.core.quiz.online.LocalAuthority
import castbridge.core.quiz.online.PlayScope
import castbridge.core.quiz.online.SafetyFacts
import castbridge.core.tv.ReceiverServer
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import java.net.URLEncoder
import kotlin.test.*

/**
 * `QuizHttp` sur une AUTORITÉ (w20-05a) : mêmes routes, mêmes codes HTTP, même JSON qu'avec la `QuizRoom` d'aujourd'hui (la clé additive `safety` mise à part).
 * Le même scénario est joué contre les deux serveurs ; les réponses doivent être identiques, une à une.
 */
class QuizHttpAuthorityTest {
    private class Setup(val room: QuizRoom, makeHttp: () -> QuizHttp) : AutoCloseable {
        val dir = kotlin.io.path.createTempDirectory("quizhttp-auth").toFile()
        val port = ServerSocket(0).use { it.localPort }
        val server = ReceiverServer(castbridge.core.tv.VolumeRegistry.single(dir), FakePlayer(), port, pin = "123456", publicRoutes = makeHttp()).apply { start(5000, false) }
        override fun close() { room.close(); server.stop(); dir.deleteRecursively() }
    }

    private fun room() = QuizRoom(EmbeddedQuestionSource().bank(), maxPlayers = 3, clock = { 1_000L }, random = java.util.Random(11), autoTick = false)

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun call(base: String, method: String, path: String): Pair<Int, String> {
        val c = URL(base + path).openConnection() as HttpURLConnection
        c.requestMethod = method; c.readTimeout = 10_000
        if (method == "POST") { c.doOutput = true; c.outputStream.close() }
        val code = c.responseCode
        return code to ((if (code < 400) c.inputStream else c.errorStream)?.use { String(it.readBytes(), Charsets.UTF_8) }.orEmpty())
    }

    /** Le corps sans ce qui varie d'un serveur à l'autre : jetons, et la clé additive `safety`. */
    private fun normalize(body: String): String {
        val t = Regex("\"token\":\"[0-9a-f]+\"").replace(body, "\"token\":\"T\"")
        val o = runCatching { Json.parse(t) }.getOrNull() as? Map<*, *> ?: return t
        fun strip(m: Map<*, *>): Map<*, *> = m.filterKeys { it != "safety" }.mapValues { (_, v) -> if (v is Map<*, *>) strip(v) else v }
        return Json.write(strip(o))
    }

    /** Joue le scénario sur un serveur et rend ce qu'il a répondu (code, corps normalisé). */
    private fun scenario(s: Setup): List<String> {
        val base = "http://127.0.0.1:${s.port}"; val log = ArrayList<String>()
        fun rec(tag: String, r: Pair<Int, String>) { log += "$tag ${r.first} ${normalize(r.second)}" }
        val code = s.room.code
        rec("hello", call(base, "GET", "/quiz/api/hello"))
        rec("page", (call(base, "GET", "/quiz").let { it.first to (if (it.second.contains("Rejoindre")) "page" else "?") }))
        val wrong = if (code == "0000") "1111" else "0000"
        rec("wrongcode", call(base, "POST", "/quiz/api/join?code=$wrong&name=Ali"))
        rec("noname", call(base, "POST", "/quiz/api/join?code=$code&name=%20%20"))
        val j1 = call(base, "POST", "/quiz/api/join?code=$code&name=${enc("Awa")}"); rec("join1", j1)
        val t1 = Json.obj(j1.second)["token"] as String
        val j2 = call(base, "POST", "/quiz/api/join?code=$code&name=${enc("Bello")}"); rec("join2", j2)
        val t2 = Json.obj(j2.second)["token"] as String
        rec("rejoin", call(base, "POST", "/quiz/api/join?code=$code&name=Awa&token=$t1"))
        rec("lobby-state", call(base, "GET", "/quiz/api/state?token=$t1"))
        rec("unknown-state", call(base, "GET", "/quiz/api/state?token=nope"))
        rec("unknown-act", call(base, "POST", "/quiz/api/act?token=nope&action=answer&q=x&choice=1"))
        rec("join3", call(base, "POST", "/quiz/api/join?code=$code&name=Carine"))
        rec("full", call(base, "POST", "/quiz/api/join?code=$code&name=Denis"))
        s.room.setMode(QuizRoom.Mode.DUEL); assertNull(s.room.startGame(seed = 11))
        val q = s.room.duel!!.question
        rec("duel-state", call(base, "GET", "/quiz/api/state?token=$t1"))
        rec("answer", call(base, "POST", "/quiz/api/act?token=$t1&action=answer&q=${q.id}&choice=${(q.answer + 1) % 4}"))
        rec("second-answer", call(base, "POST", "/quiz/api/act?token=$t1&action=answer&q=${q.id}&choice=${q.answer}"))
        rec("bad-choice", call(base, "POST", "/quiz/api/act?token=$t2&action=answer&q=${q.id}&choice=9"))
        rec("leave", call(base, "POST", "/quiz/api/leave?token=$t2"))
        rec("wrong-method", call(base, "GET", "/quiz/api/join"))
        s.room.close()
        rec("closed-hello", call(base, "GET", "/quiz/api/hello"))
        rec("closed-state", call(base, "GET", "/quiz/api/state?token=$t1"))
        rec("closed-join", call(base, "POST", "/quiz/api/join?code=$code&name=Tard"))
        return log
    }

    @Test fun sameHttpAnswersWithALocalAuthorityAsWithTheRoomToday() {
        val viaRoom = room().let { r -> Setup(r) { QuizHttp({ r }, pingMs = 500) }.use { scenario(it) } }
        val viaAuthority = room().let { r ->
            val auth = LocalAuthority(r) { SafetyFacts(PlayScope.LAN) }
            Setup(r) { QuizHttp(QuizHttp.AuthoritySource { auth }, pingMs = 500) }.use { scenario(it) }
        }
        assertEquals(viaRoom.size, viaAuthority.size)
        for (i in viaRoom.indices) assertEquals(viaRoom[i], viaAuthority[i], "réponse n°$i (${viaRoom[i].substringBefore(' ')})")
        assertTrue(viaRoom.any { it.startsWith("full 409") } && viaRoom.any { it.startsWith("closed-state 410") } && viaRoom.any { it.startsWith("unknown-state 401") })
    }

    @Test fun theAuthorityViewCarriesTheSafetyKeyAdditively() {
        val r = room(); val auth = LocalAuthority(r) { SafetyFacts(PlayScope.LAN) }
        Setup(r) { QuizHttp(QuizHttp.AuthoritySource { auth }, pingMs = 500) }.use { s ->
            val base = "http://127.0.0.1:${s.port}"
            val t = Json.obj(call(base, "POST", "/quiz/api/join?code=${r.code}&name=Awa").second)["token"] as String
            val st = Json.obj(call(base, "GET", "/quiz/api/state?token=$t").second)
            assertNotNull(st["safety"]); assertNotNull(st["me"]); assertEquals("LOBBY", st["stage"])
        }
    }
}
