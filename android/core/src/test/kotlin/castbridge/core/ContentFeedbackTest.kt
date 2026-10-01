package castbridge.core

import castbridge.core.content.Channel
import castbridge.core.content.ContentFeedback
import castbridge.core.content.ContentFeedbackApi
import castbridge.core.content.ContentKind
import castbridge.core.content.ReportHandoffClient
import castbridge.core.content.ReportQueue
import castbridge.core.content.ReportReason
import castbridge.core.net.JsonLite
import castbridge.core.quiz.EmbeddedQuestionSource
import castbridge.core.quiz.QuizRoom
import castbridge.core.quiz.VirtualWallet
import castbridge.core.telemetry.Consent
import castbridge.core.telemetry.EventQueue
import castbridge.core.telemetry.Telemetry
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ContentFeedbackTest {
    private var now = 10_000_000L
    private fun dir() = kotlin.io.path.createTempDirectory("cbfeed").toFile().also { it.deleteOnExit() }
    private fun fb(d: File = dir(), channel: Channel = Channel.BETA, tel: Telemetry? = null) = ContentFeedback(d, { channel }, { tel }, { now })
    private fun room(channel: Channel, fb: ContentFeedback?) = QuizRoom(EmbeddedQuestionSource().bank().forChannel(channel), clock = { now }, random = java.util.Random(7), autoTick = false,
        wallet = VirtualWallet()).also { it.feedback = fb }

    @Suppress("UNCHECKED_CAST") private fun Map<String, Any?>.m(k: String) = this[k] as Map<String, Any?>

    @Test fun aPlayerReportsTheQuestionOnScreen() {
        val f = fb()
        val r = room(Channel.BETA, f)
        val p = r.join(r.code, "Ali").player!!
        assertTrue(r.setCandidate(p.id)); assertNull(r.startGame(seed = 5))
        val q = r.game!!.question
        assertEquals(QuizRoom.Act.IGNORED, r.act(p.token, "report", "another-question", null, "wrong_answer"))
        assertEquals(QuizRoom.Act.BAD_REQUEST, r.act(p.token, "report", q.id, null, "nonsense"))
        assertEquals(QuizRoom.Act.OK, r.act(p.token, "report", q.id, null, "wrong_answer|la bonne réponse est B"))
        val rep = f.queue.pending().single()
        assertEquals(ContentKind.QUESTION, rep.kind); assertEquals(q.id, rep.itemId); assertEquals(ReportReason.WRONG_ANSWER, rep.reason)
        assertEquals("la bonne réponse est B", rep.note); assertEquals(Channel.BETA, rep.channel); assertNotNull(rep.hash); assertTrue(rep.lot!!.startsWith("quiz/"))
        assertEquals(QuizRoom.Act.OK, r.act(p.token, "report", q.id, null, "wrong_answer"), "again: accepted but counted once")
        assertEquals(1, f.queue.count())
        // the TV host reports through the same path (a report right after another is rate limited)
        assertEquals(QuizRoom.Act.IGNORED, r.act(p.token, "report", q.id, null, "language"))
        now += 5_000
        assertTrue(r.hostAct("report", arg = "language"))
        assertEquals(2, f.queue.count())
        // no feedback configured: nothing happens
        val r2 = room(Channel.BETA, null); val p2 = r2.join(r2.code, "Bo").player!!; r2.setCandidate(p2.id); r2.startGame(seed = 5)
        assertEquals(QuizRoom.Act.IGNORED, r2.act(p2.token, "report", r2.game!!.question.id, null, "other"))
    }

    @Test fun theMarkOnlyShowsOnTheBetaChannelForUnvalidatedQuestions() {
        for (ch in listOf(Channel.STABLE, Channel.BETA)) {
            val r = room(ch, null); val p = r.join(r.code, "Ali").player!!; r.setCandidate(p.id); r.startGame(seed = 5)
            assertNull(r.view(p.token).m("game").m("question")["mark"], "the bundled questions are validated: no mark on $ch")
        }
        // a bank with questions under review: marked on beta, not playable on stable
        val review = castbridge.core.quiz.Question("r1", castbridge.core.quiz.Region.CM, "c", 1, "Q ?", listOf("a", "b", "c", "d"), 0, "e", "s", review = true)
        val beta = castbridge.core.quiz.QuizBank(listOf(review), Channel.BETA)
        assertEquals(1, beta.playable.size); assertEquals("bêta : non validé", castbridge.core.content.PlayPolicy.mark(beta.playable[0], beta.channel))
        assertEquals(0, beta.forChannel(Channel.STABLE).playable.size)
    }

    @Test fun statsFollowTheConsentAndReportsAreEssential() {
        val d = dir(); val events = File(d, "e.jsonl"); var consent = Consent.ESSENTIAL
        val tel = Telemetry("tv", 1, EventQueue(events), { consent })
        val f = fb(d, tel = tel)
        f.shown(ContentKind.QUESTION, "q1", true, 3000)
        assertEquals(0, f.flushStats()); assertTrue(!events.isFile || events.readText().isEmpty())          // no consent: no usage statistic
        val rq = castbridge.core.quiz.Question("q9", castbridge.core.quiz.Region.CM, "c", 1, "Q ?", listOf("a", "b", "c", "d"), 0, "e", "s")
        assertEquals(ReportQueue.Add.ACCEPTED, f.reportQuestion(rq, ReportReason.AMBIGUOUS, null))        // but a report is queued
        assertEquals(1, f.queue.count())
        consent = Consent.USAGE
        f.shown(ContentKind.QUESTION, "q1", false, 1000)
        assertEquals(2, f.flushStats())    // q1 (shown) and q9 (reported) → one event each
        assertTrue(events.readText().contains("\"item\":\"q1\"") && events.readText().contains("\"item\":\"q9\""))
        assertEquals("Merci ! Votre signalement sera envoyé dès que possible.", f.message(ReportQueue.Add.ACCEPTED))
    }

    @Test fun handOffTvToPhoneOverHttp() {
        val tv = fb(); val phone = fb()
        val rq = castbridge.core.quiz.Question("q-tv", castbridge.core.quiz.Region.CM, "c", 1, "Q ?", listOf("a", "b", "c", "d"), 0, "e", "s")
        tv.reportQuestion(rq, ReportReason.WRONG_ANSWER, "faux")
        val api = ContentFeedbackApi { tv }
        assertNull(api.handle("/api/other", "GET", emptyMap()))
        assertEquals(405, api.handle("/api/content/reports", "DELETE", emptyMap())!!.status)
        assertEquals(503, ContentFeedbackApi { null }.handle("/api/content/reports", "GET", emptyMap())!!.status)
        // (the PIN check belongs to the TV server, which chains this extension after authentication: the route logic is checked directly)
        val listed = JsonLite.obj(api.handle("/api/content/reports", "GET", emptyMap())!!.json)["reports"] as List<*>
        assertEquals(1, listed.size)
        val acked = phone.queue.importHandoff(JsonLite.write(listed))
        assertEquals(1, phone.queue.count())
        api.handle("/api/content/reports/ack", "POST", mapOf("ids" to acked.joinToString(",")))
        assertEquals(0, tv.queue.count())
        assertEquals(0, ReportHandoffClient("http://127.0.0.1:1", "123456").pull(phone.queue))     // TV unreachable: nothing happens, no crash
    }
}
