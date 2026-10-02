package castbridge.core

import castbridge.core.quiz.*
import castbridge.core.tv.ReceiverServer
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import java.net.URLEncoder
import kotlin.test.*

/** Room logic with a fake clock (no thread, no network). */
class QuizRoomTest {
    private var now = 1_000L
    private val bank = EmbeddedQuestionSource().bank()
    private fun room(max: Int = 8, wallet: WalletProvider = VirtualWallet()) =
        QuizRoom(bank, clock = { now }, random = java.util.Random(7), maxPlayers = max, autoTick = false, wallet = wallet)

    @Suppress("UNCHECKED_CAST") private fun Map<String, Any?>.m(k: String) = this[k] as Map<String, Any?>

    @Test fun joinNeedsTheCodeAndANameAndRespectsTheLimit() {
        val r = room(max = 2)
        assertEquals(4, r.code.length)
        assertEquals(QuizRoom.Join.BAD_CODE, r.join("0000".takeIf { it != r.code } ?: "9999", "Ali").status)
        assertEquals(QuizRoom.Join.BAD_NAME, r.join(r.code, "   ").status)
        val a = r.join(r.code, "Ali").player!!
        val b = r.join(r.code, "ali").player!!
        assertEquals("ali 2", b.name, "same name made unique")
        assertEquals(QuizRoom.Join.FULL, r.join(r.code, "Carine").status)
        assertEquals(a.id, r.join(r.code, "Ali", a.token).player!!.id, "the token gives the same seat back")
        assertNotEquals(a.token, b.token)
        assertEquals("Ali", QuizRoom.cleanName("  <Ali>\n "))
    }

    @Test fun millionaireWithCandidateOnPhoneAndRealAudienceVote() {
        val r = room()
        val cand = r.join(r.code, "Candidat").player!!
        val v1 = r.join(r.code, "Public1").player!!
        val v2 = r.join(r.code, "Public2").player!!
        assertTrue(r.setCandidate(cand.id))
        assertNull(r.startGame(seed = 5))
        val qid = r.game!!.question.id
        assertEquals(QuizRoom.Act.FORBIDDEN, r.act(v1.token, "select", qid, 0), "only the candidate answers")
        assertEquals(QuizRoom.Act.OK, r.act(cand.token, "audience", qid, null))
        assertNotNull(r.view(v1.token)["vote"])
        assertEquals(QuizRoom.Act.OK, r.act(v1.token, "vote", qid, 2))
        assertEquals(QuizRoom.Act.OK, r.act(v1.token, "vote", qid, 2), "repeat is harmless")
        assertEquals(QuizRoom.Act.FORBIDDEN, r.act(v1.token, "vote", qid, 1), "one vote each")
        assertEquals(QuizRoom.Act.IGNORED, r.act(cand.token, "vote", qid, 1), "the candidate does not vote")
        assertEquals(QuizRoom.Act.OK, r.act(v2.token, "vote", qid, 2))
        val g = r.game!!
        assertEquals(QuizGame.Phase.QUESTION, g.phase, "everyone voted: the vote closes")
        assertTrue(g.audienceReal); assertEquals(100, g.audience!![2])
        // answer, lock, suspense then reveal on the clock
        val right = g.question.answer
        assertEquals(QuizRoom.Act.OK, r.act(cand.token, "select", qid, right))
        assertEquals(QuizRoom.Act.OK, r.act(cand.token, "select", qid, right), "same selection again = no-op")
        assertEquals(QuizRoom.Act.OK, r.act(cand.token, "confirm", qid, null))
        r.tick(); assertEquals(QuizGame.Phase.LOCKED, g.phase)
        now += r.suspenseMs; r.tick(); assertEquals(QuizGame.Phase.REVEALED, g.phase)
        assertEquals(QuizRoom.Act.IGNORED, r.act(cand.token, "next", "stale-id", null), "a late command for another question is ignored")
        assertEquals(QuizRoom.Act.OK, r.act(cand.token, "next", qid, null))
        assertEquals(1, g.index)
    }

    @Test fun phoneAFriendWhoAnswersOrMissesTheCall() {
        val r = room()
        val friend = r.join(r.code, "Ami").player!!
        assertNull(r.startGame(seed = 9))                      // candidate = TV remote
        val g = r.game!!
        assertTrue(r.hostAct("phone", arg = friend.id))
        assertEquals("friend", (r.view(friend.token).m("me"))["role"])
        val qid = g.question.id
        assertEquals(QuizRoom.Act.OK, r.act(friend.token, "suggest", qid, 1))
        assertEquals(1, g.phone!!.choice); assertEquals("Ami", g.phone!!.friend)
        // next question: the call joker is spent
        r.hostAct("select", g.question.answer); r.hostAct("confirm"); now += 5_000; r.tick(); r.hostAct("next")
        assertFalse(r.hostAct("phone", arg = friend.id))
        // a friend who does not answer in time: simulated suggestion, the TV says so
        val r2 = room(); val f2 = r2.join(r2.code, "Muet").player!!
        r2.startGame(seed = 3); r2.hostAct("phone", arg = f2.id)
        now += r2.callMs; r2.tick()
        assertTrue(r2.game!!.phone!!.simulated); assertEquals("Muet", r2.view(null)["callMissed"])
    }

    @Test fun jokersWithoutPlayersAreSimulated() {
        val r = room()
        assertNull(r.startGame(seed = 1))
        assertTrue(r.hostAct("audience"))
        assertFalse(r.game!!.audienceReal); assertEquals(100, r.game!!.audience!!.sum())
        assertTrue(r.hostAct("phone", arg = ""))
        assertTrue(r.game!!.phone!!.simulated)
    }

    @Test fun duelScoresWithServerTimeAndClosesEarly() {
        val r = room()
        val a = r.join(r.code, "Awa").player!!
        val b = r.join(r.code, "Bello").player!!
        assertTrue(r.setMode(QuizRoom.Mode.DUEL))
        assertNull(r.startGame(seed = 2))
        val d = r.duel!!
        val q = d.question
        now += 2_000
        assertEquals(QuizRoom.Act.OK, r.act(a.token, "answer", q.id, q.answer))
        assertNull(r.view(a.token).m("duel").m("question")["answer"], "not revealed while others can still answer")
        now += 8_000
        assertEquals(QuizRoom.Act.OK, r.act(b.token, "answer", q.id, (q.answer + 1) % 4))
        assertEquals(QuizDuel.Phase.REVEAL, d.phase, "all players answered")
        assertEquals(QuizDuel.points(2_000, r.duelQuestionMs), d.score(a.id)); assertEquals(0, d.score(b.id))
        assertEquals(q.answer, (r.view(b.token).m("duel").m("question")["answer"] as Number).toInt())
    }

    @Test fun stakeDuelPaysThePotOutInVirtualTokens() {
        val w = VirtualWallet(initial = 500)
        val r = room(wallet = w)
        val a = r.join(r.code, "Awa", device = "device-aaaa").player!!
        val b = r.join(r.code, "Bello", device = "device-bbbb").player!!
        assertTrue(r.configure(QuizRoom.Mode.MILLIONAIRE, QuizRoom.Play.STAKE, QuestionFilter.GENERAL, 100))
        assertEquals(QuizRoom.Mode.DUEL, r.mode, "a stake is only played in Duel")
        assertNull(r.startGame(seed = 4))
        assertEquals(400, w.balance("dev:device-aaaa"))
        val pot = r.view(a.token)["pot"] as Map<*, *>
        assertEquals(200L, pot["total"])
        val settings = r.view(a.token)["settings"] as Map<*, *>
        assertEquals(QuizRoom.TOKENS_LABEL, settings["tokens"]); assertEquals(true, settings["virtualTokens"])
        val d = r.duel!!
        while (d.phase != QuizDuel.Phase.FINISHED) {
            if (d.phase == QuizDuel.Phase.QUESTION) r.act(a.token, "answer", d.question.id, d.question.answer)
            r.hostSkip()
        }
        assertEquals(QuizRoom.Stage.FINISHED, r.stage)
        assertEquals(600, w.balance("dev:device-aaaa"), "the only scorer takes the whole pot")
        assertEquals(400, w.balance("dev:device-bbbb"))
        // an abandoned staked game gives the tokens back
        assertNull(r.startGame(seed = 5))
        assertEquals(500, w.balance("dev:device-aaaa")); r.backToLobby()
        assertEquals(600, w.balance("dev:device-aaaa"))
    }

    @Test fun practiceModeHas20sCountdownAndGoesOn() {
        val r = room()
        assertTrue(r.configure(QuizRoom.Mode.MILLIONAIRE, QuizRoom.Play.PRACTICE, QuestionFilter(Track.SECONDARY, "3e")))
        assertNull(r.startGame(seed = 1))
        val g = r.game!!
        assertTrue(g.practice); assertEquals(20_000, g.remainingMs(now), "practice also has the 20 s countdown")
        assertTrue(g.questions.all { it.level == "3e" })
        r.hostAct("select", (g.question.answer + 1) % 4); r.hostAct("confirm"); now += 5_000; r.tick(); r.hostAct("next")
        assertEquals(QuizGame.Phase.QUESTION, g.phase, "a mistake does not end practice")
    }

    @Test fun emptyLevelCannotStart() {
        val r = room()
        r.configure(QuizRoom.Mode.MILLIONAIRE, QuizRoom.Play.FRIENDS, QuestionFilter(Track.PRIMARY, "SIL"))
        assertNotNull(r.startGame())
        assertEquals(QuizRoom.Stage.LOBBY, r.stage)
    }

    @Test fun closesWhenIdle() {
        val r = room()
        now += r.idleCloseMs + 1; r.tick()
        assertEquals(QuizRoom.Stage.CLOSED, r.stage)
        assertEquals(QuizRoom.Join.CLOSED, r.join(r.code, "Tard").status)
    }
}

/** End to end over HTTP, through the TV's real server (PIN on the admin API, room code on the quiz). */
class QuizHttpTest {
    private val dir = kotlin.io.path.createTempDirectory("quizhttp").toFile()
    private val port = ServerSocket(0).use { it.localPort }
    @Volatile private var room: QuizRoom? = QuizRoom(EmbeddedQuestionSource().bank(), maxPlayers = 3)
    private val http = QuizHttp({ room }, pingMs = 500)
    private val server = ReceiverServer(castbridge.core.tv.VolumeRegistry.single(dir), FakePlayer(), port, pin = "123456", publicRoutes = http).apply { start(5000, false) }
    private val base = "http://127.0.0.1:$port"

    @AfterTest fun tearDown() { room?.close(); server.stop(); dir.deleteRecursively() }

    private class Res(val code: Int, val body: String)
    private fun call(method: String, path: String, headers: Map<String, String> = emptyMap(), timeout: Int = 10_000): Res {
        val c = URL(base + path).openConnection() as HttpURLConnection
        c.requestMethod = method; c.readTimeout = timeout
        headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
        if (method == "POST") { c.doOutput = true; c.outputStream.close() }
        val code = c.responseCode
        val body = (if (code < 400) c.inputStream else c.errorStream)?.use { String(it.readBytes(), Charsets.UTF_8) }.orEmpty()
        return Res(code, body)
    }
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun join(name: String, code: String = room!!.code, token: String = ""): Res = call("POST", "/quiz/api/join?code=$code&name=${enc(name)}&token=$token")
    private fun token(r: Res) = Json.obj(r.body)["token"] as String

    @Test fun pageAndHelloAreOpenWithoutPinButAdminIsNot() {
        val page = call("GET", "/quiz")
        assertEquals(200, page.code); assertTrue(page.body.contains("Rejoindre"))
        val hello = Json.obj(call("GET", "/quiz/api/hello").body)
        assertEquals(true, hello["open"]); assertNull(hello["code"], "the code is only on the TV screen")
        assertEquals(401, call("GET", "/api/info").code, "the admin API still wants the PIN")
        val t = token(join("Ali"))
        assertEquals(401, call("GET", "/api/info?pin=$t").code, "a player token is not the PIN")
    }

    @Test fun wrongCodeRefusedAndLimitedPerIp() {
        val wrong = if (room!!.code == "0000") "1111" else "0000"
        assertEquals(403, join("Ali", wrong).code)
        repeat(QuizHttp.MAX_BAD_CODES - 1) { join("Ali", wrong) }
        assertEquals(429, join("Ali", room!!.code).code, "after too many wrong codes, even the right one waits")
    }

    @Test fun reconnectWithTokenKeepsTheSeat() {
        val first = Json.obj(join("Awa").body)
        val again = Json.obj(join("Awa", token = first["token"] as String).body)
        assertEquals(first["id"], again["id"]); assertEquals(first["token"], again["token"])
        val st = Json.obj(call("GET", "/quiz/api/state?token=${first["token"]}").body)
        @Suppress("UNCHECKED_CAST") assertEquals("Awa", (st["me"] as Map<String, Any?>)["name"])
        assertEquals(401, call("GET", "/quiz/api/state?token=nope").code)
    }

    @Test fun playerLimit() {
        repeat(3) { assertEquals(200, join("J$it").code) }
        val full = join("J4")
        assertEquals(409, full.code); assertTrue(full.body.contains("complète"))
    }

    @Test fun rightAnswerNeverLeaksBeforeTheQuestionCloses() {
        val a = token(join("Awa")); val b = token(join("Bello"))
        val r = room!!
        r.setMode(QuizRoom.Mode.DUEL)
        assertNull(r.startGame(seed = 11))
        val q = r.duel!!.question
        val secret = q.choices[q.answer]
        fun leaks(body: String) = Regex("\"answer\":\\d").containsMatchIn(body) || body.contains("\"explanation\":\"")
        val st = call("GET", "/quiz/api/state?token=$a").body
        assertFalse(leaks(st), st)
        assertTrue(st.contains(secret), "the choices are there, just not which one is right")
        val acted = call("POST", "/quiz/api/act?token=$a&action=answer&q=${q.id}&choice=${(q.answer + 1) % 4}")
        assertEquals(200, acted.code); assertFalse(leaks(acted.body), "not even in the answer's reply")
        assertEquals(409, call("POST", "/quiz/api/act?token=$a&action=answer&q=${q.id}&choice=${q.answer}").code, "no second chance")
        call("POST", "/quiz/api/act?token=$b&action=answer&q=${q.id}&choice=${q.answer}")
        val after = Json.obj(call("GET", "/quiz/api/state?token=$a").body)
        @Suppress("UNCHECKED_CAST") val dq = (after["duel"] as Map<String, Any?>)["question"] as Map<String, Any?>
        assertEquals(q.answer.toLong(), dq["answer"], "revealed once everybody answered")
    }

    @Test fun eventStreamPushesStateAndLongPollWakesUp() {
        val t = token(join("Awa"))
        val c = URL("$base/quiz/api/events?token=$t").openConnection() as HttpURLConnection
        c.setRequestProperty("Accept-Encoding", "gzip")          // must not be gzip-buffered
        c.readTimeout = 5_000
        assertEquals(200, c.responseCode)
        assertTrue(c.contentType.startsWith("text/event-stream")); assertNull(c.contentEncoding)
        val reader = c.inputStream.bufferedReader()
        fun nextData(): String { while (true) { val l = reader.readLine() ?: fail("stream ended"); if (l.startsWith("data: ")) return l.removePrefix("data: ") } }
        assertTrue(nextData().contains("\"stage\":\"LOBBY\""))
        Thread { Thread.sleep(300); join("Bello") }.start()
        assertTrue(nextData().contains("Bello"), "a new player is pushed at once")
        c.disconnect()
        // long-poll: waits, then answers as soon as something changes
        val v = (Json.obj(call("GET", "/quiz/api/state?token=$t").body)["v"] as Number).toLong()
        Thread { Thread.sleep(400); room!!.setMode(QuizRoom.Mode.DUEL) }.start()
        val t0 = System.nanoTime()
        val res = call("GET", "/quiz/api/state?token=$t&since=$v&wait=10")
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertTrue(ms in 300..5_000, "woke up on the change ($ms ms)")
        assertTrue(res.body.contains("\"mode\":\"DUEL\""))
    }

    @Test fun closedRoomSaysSo() {
        val t = token(join("Awa"))
        room!!.close()
        assertEquals(410, call("GET", "/quiz/api/state?token=$t").code)
        assertEquals(false, Json.obj(call("GET", "/quiz/api/hello").body)["open"])
        room = null
        assertEquals(410, join("Awa", "1234").code)
    }

    @Test fun boostErrorMentionsTheTvOnlyWhenForbidden() {
        val a = token(join("Awa"))
        val forbidden = call("POST", "/quiz/api/act?token=$a&action=boost&arg=EXTRA_JOKER")
        assertEquals(409, forbidden.code); assertTrue(forbidden.body.contains("réservé à la TV"))
        val unknown = call("POST", "/quiz/api/act?token=nope&action=boost&arg=EXTRA_JOKER")
        assertEquals(401, unknown.code); assertFalse(unknown.body.contains("réservé à la TV"))
        val other = call("POST", "/quiz/api/act?token=$a&action=answer&q=x&choice=0")
        assertFalse(other.body.contains("réservé à la TV"))
    }
}
