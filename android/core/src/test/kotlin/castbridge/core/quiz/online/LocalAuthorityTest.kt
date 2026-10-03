package castbridge.core.quiz.online

import castbridge.core.quiz.*
import kotlin.test.*

class LocalAuthorityTest {
    private var now = 1_000L
    private val bank = EmbeddedQuestionSource().bank()
    private fun room() = QuizRoom(bank, clock = { now }, random = java.util.Random(42), autoTick = false)
    private fun facts(scope: PlayScope = PlayScope.LAN) = SafetyFacts(scope)

    @Suppress("UNCHECKED_CAST") private fun Map<String, Any?>.m(k: String) = this[k] as Map<String, Any?>

    private class Driver(val join: (String, String) -> QuizRoom.JoinResult, val act: (String, String, String?, Int?) -> QuizRoom.Act, val view: (String?) -> Map<String, Any?>)

    /** Plays a 3-player Duel and returns (v, stage, duel.phase) after every step + the outcome of the double answer and the replay. */
    private fun play(room: QuizRoom, d: Driver): List<String> {
        now = 1_000L
        val log = ArrayList<String>()
        fun snap(tag: String) { val v = d.view(null); log += "$tag v=${v["v"]} stage=${v["stage"]} phase=${(v["duel"] as? Map<*, *>)?.get("phase")}" }
        val ps = listOf("Awa", "Bello", "Carine").map { d.join(room.code, it).player!! }
        assertTrue(room.setMode(QuizRoom.Mode.DUEL)); assertNull(room.startGame(seed = 2)); snap("start")
        var guard = 0
        while (room.duel!!.phase != QuizDuel.Phase.FINISHED && guard++ < 200) {
            val q = room.duel!!.question
            if (room.duel!!.phase == QuizDuel.Phase.QUESTION) {
                now += 1_500
                log += "A:" + d.act(ps[0].token, "answer", q.id, q.answer)
                log += "A2:" + d.act(ps[0].token, "answer", q.id, q.answer)          // double answer
                log += "B:" + d.act(ps[1].token, "answer", q.id, (q.answer + 1) % 4)
                log += "Breplay:" + d.act(ps[1].token, "answer", q.id, (q.answer + 1) % 4)  // replay
                snap("answered")
            }
            room.hostSkip(); now += 500; snap("skip")
        }
        log += "score:" + (d.view(ps[0].token).m("duel")["ranking"] as List<*>).joinToString { r -> (r as Map<*, *>)["name"].toString() + "=" + r["score"] }
        return log
    }

    @Test fun sameSequenceAsTheRoomCalledDirectly() {
        val r1 = room(); val direct = play(r1, Driver({ c, n -> r1.join(c, n) }, { t, a, q, c -> r1.act(t, a, q, c) }, { t -> r1.view(t) }))
        val r2 = room(); val auth = LocalAuthority(r2) { facts() }
        val wrapped = play(r2, Driver({ c, n -> auth.join(c, n, null, null) }, { t, a, q, c -> auth.act(t, a, q, c, null) }, { t -> auth.view(t).filterKeys { it != "safety" } }))
        assertEquals(direct, wrapped)
        assertTrue(direct.any { it.startsWith("A2:") }); assertTrue(direct.last().startsWith("score:"))
    }

    @Test fun doubleAnswerAndReplayAreHarmlessAndIdentical() {
        val r = room(); val auth = LocalAuthority(r) { facts() }
        val a = auth.join(r.code, "Awa", null, null).player!!; auth.join(r.code, "Bello", null, null)
        r.setMode(QuizRoom.Mode.DUEL); r.startGame(seed = 2)
        val q = r.duel!!.question
        val first = auth.act(a.token, "answer", q.id, q.answer, null)
        val before = auth.view(a.token).m("duel")["answeredCount"]
        assertEquals(first, auth.act(a.token, "answer", q.id, q.answer, null))
        assertEquals(before, auth.view(a.token).m("duel")["answeredCount"])
    }

    @Test fun viewAddsTheAdditiveSafetyKeyOnly() {
        val r = room(); val auth = LocalAuthority(r) { facts(PlayScope.LAN) }
        val plain = r.view(null); val v = auth.view(null)
        plain.forEach { (k, x) -> assertEquals(x, v[k], "key $k unchanged") }
        assertEquals(plain.keys + "safety", v.keys)
        val s = v.m("safety")
        assertEquals(listOf("scope", "level", "word", "text", "action"), s.keys.toList())
        assertEquals("LAN", s["scope"]); assertEquals("GREEN", s["level"]); assertEquals("Réseau local · rien ne sort de la maison", s["text"])
        assertEquals(PlayScope.LAN, auth.scope); assertEquals(SafetySign.of(facts()), auth.safety())
        assertEquals(r.awaitChange(0, 0), auth.awaitChange(0, 0))
        assertEquals(QuizRoom.Join.BAD_CODE, auth.join("zzzz", "Awa", null, null).status)
    }
}
