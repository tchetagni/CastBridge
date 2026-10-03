package castbridge.play.guard

import castbridge.core.quiz.online.ClientMsg
import castbridge.core.quiz.online.Limits
import castbridge.core.quiz.online.PlayCodec
import castbridge.core.quiz.online.PlayProtocol
import castbridge.core.quiz.online.ServerRoom
import java.util.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * T-13 : schéma strict de chaque message entrant, par fuzz à graine fixe (10 000 messages aléatoires ou mutés). Exigences : aucune exception qui sort du service,
 * aucune trace de pile ni nom de classe dans une réponse, aucun effet sur la salle d'un message invalide, trois invalides = fermeture 1008.
 */
class MessageSchemaFuzzTest {
    private val corpus: List<String> = listOf(
        ClientMsg.Hello(1, listOf("play1", "sse"), "device-abcdef01", "v1.AAAAAAAA.BBBBBBBB"),
        ClientMsg.Create("TV Salon", "DUEL"), ClientMsg.Join("ABCD-1234", "Amina N.", "0123456789abcdef0123456789abcdef", "device-abcdef01", false),
        ClientMsg.Resume("0123456789abcdef0123456789abcdef", "fedcba9876543210fedcba9876543210", 12),
        ClientMsg.Act("q1", "answer", 2, null, 5), ClientMsg.Act(null, "vote", null, "abc", 6), ClientMsg.Act(null, "start", null, "3", 7),
        ClientMsg.RelayAct("0123456789abcdef0123456789abcdef", "q1", 1, 640, 8), ClientMsg.Scope(false), ClientMsg.Kick("p3"), ClientMsg.Mute("p2", true),
        ClientMsg.Report("q1", "pseudonyme"), ClientMsg.Pong("p12"),
    ).map { PlayCodec.encode(it) }

    private val glyphs = listOf("é", "ß", "漢", "😀", "\u0000", "‮", "\ud800", "\"", "\\", "{", "}", "[", "]", ",", ":", "null", "true", "1e999", "-0", "9".repeat(40), "\n", "\t", " ")

    private fun mutate(rnd: Random, s: String): String {
        var t = s
        repeat(1 + rnd.nextInt(3)) {
            if (t.isEmpty()) return t
            val i = rnd.nextInt(t.length)
            t = when (rnd.nextInt(12)) {
                0 -> t.substring(0, i)                                                   // tronqué
                1 -> t.removeRange(i, minOf(t.length, i + 1 + rnd.nextInt(8)))           // morceau retiré
                2 -> t.substring(0, i) + glyphs[rnd.nextInt(glyphs.size)] + t.substring(i) // glyphe inséré
                3 -> t.substring(0, i) + (32 + rnd.nextInt(95)).toChar() + t.substring(i + 1) // caractère remplacé
                4 -> t.replace(Regex("\\d+"), "\"x\"")                                   // nombres devenus textes
                5 -> t.replace("\"", "'")                                                // mauvais guillemets
                6 -> t.replaceFirst("{", "{\"t\":\"" + listOf("act", "join", "kick", "hello", "zzz", "")[rnd.nextInt(6)] + "\",")   // clé `t` en double
                7 -> t.substring(0, i) + "[".repeat(1 + rnd.nextInt(60)) + t.substring(i)    // imbrication
                8 -> t.replace("false", "null").replace("true", "0")
                9 -> t.substring(0, i) + "a".repeat(rnd.nextInt(3_000)) + t.substring(i)     // champ géant
                10 -> t + t                                                              // message doublé
                else -> t.replace(Regex("\"[a-zA-Z]+\":"), "\"k${rnd.nextInt(9)}\":")    // clés renommées
            }
        }
        return t
    }

    private fun garbage(rnd: Random): String = when (rnd.nextInt(6)) {
        0 -> ""
        1 -> String(CharArray(rnd.nextInt(300)) { (rnd.nextInt(0x2fff)).toChar() })
        2 -> "[".repeat(rnd.nextInt(5_000))
        3 -> "{\"t\":" + "{\"a\":".repeat(rnd.nextInt(300))
        4 -> (0 until rnd.nextInt(80)).joinToString(",", "[", "]") { rnd.nextInt().toString() }
        else -> "x" + rnd.nextLong()
    }

    private fun ipOf(i: Int) = "10.${i / 65_536}.${(i / 256) % 256}.${i % 256}"

    private fun assertClean(replies: List<String>, context: String) {
        for (r in replies) {
            assertFalse(Regex("Exception|Error:|\\sat [a-z]+\\.|StackOverflow|castbridge\\.|kotlin\\.|java\\.|\\.kt:").containsMatchIn(r), "fuite dans la réponse à « ${context.take(80)} » : ${r.take(200)}")
        }
    }

    @Test fun tenThousandSeededMessagesNeverCrashLeakOrTouchARoom() {
        val rnd = Random(20_261_003L)
        val hub = GuardHarness.hub(ServerRoom.Settings(maxSpectators = 100_000))
        hub.guard = PlayGuard(Limits(config = Limits.Config(connPerSecondGlobal = 1_000_000, joinPerMinutePerDevice = 1_000_000, roomMessagesPerSecond = 1_000_000, roomBurst = 1_000_000)), LogRedactor.silent())
        val (_, code) = GuardHarness.host(hub)
        val room = hub.rooms().single()
        var invalid = 0; var valid = 0
        for (i in 0 until 10_000) {
            val seated = i % 2 == 0
            val c = if (seated) GuardHarness.join(hub, code, "Fz$i", "f$i", ip = ipOf(i), spectate = true).also { assertTrue(it.welcomed(), "spectateur $i") } else FakeConn("f$i", ipOf(i)).also { hub.register(it) }
            val text = if (rnd.nextInt(5) == 0) garbage(rnd) else mutate(rnd, corpus[rnd.nextInt(corpus.size)])
            val before = c.got.size
            val seqBefore = room.seq(); val seatsBefore = room.seatCount(); val spectatorsBefore = room.spectatorCount()
            try { hub.onText(c, text) } catch (e: Throwable) { throw AssertionError("exception non gérée au message n°$i : « ${text.take(120)} »", e) }
            val bad = MessageSchema.decode(text) is PlayCodec.Decoded.Bad
            if (bad) {
                invalid++
                assertEquals(seqBefore, room.seq(), "un message invalide ne change pas la salle (n°$i)")
                assertEquals(seatsBefore, room.seatCount()); assertEquals(spectatorsBefore, room.spectatorCount())
                assertTrue(c.got.drop(before).any { it.startsWith("{\"t\":\"error\"") }, "un message invalide reçoit une erreur (n°$i)")
            } else valid++
            assertClean(c.got.drop(before), text)
            hub.onClosed(c)
        }
        assertTrue(invalid > 4_000 && valid > 500, "le fuzz couvre les deux cas : $invalid invalides, $valid valides")
    }

    @Test fun threeInvalidMessagesInAMinuteCloseWith1008AndTwoDoNot() {
        val rnd = Random(7)
        val hub = GuardHarness.hub()
        repeat(500) { i ->
            val c = FakeConn("g$i").also { hub.register(it) }
            hub.onText(c, "x" + garbage(rnd)); hub.onText(c, "x" + garbage(rnd))
            assertNull(c.closedWith, "deux invalides : ouverte (n°$i)")
            hub.onText(c, "x" + garbage(rnd))
            assertEquals(1008, c.closedWith, "troisième invalide : 1008 (n°$i)")
        }
    }

    @Test fun validMessagesBetweenInvalidOnesDoNotResetTheTallyButTimeDoes() {
        var now = 1_000L
        val t = InvalidTally(clock = { now })
        assertFalse(t.bad()); assertFalse(t.bad()); now += 61_000; assertFalse(t.bad(), "les deux premiers ont plus d'une minute")
        assertFalse(t.bad()); assertTrue(t.bad())
    }

    @Test fun nestingBombsAndHugeMessagesAreRefusedWithoutRecursion() {
        val hub = GuardHarness.hub()
        val c = FakeConn("bomb").also { hub.register(it) }
        hub.onText(c, "[".repeat(2_000)); hub.onText(c, "{\"t\":" + "[".repeat(1_500)); hub.onText(c, "[".repeat(10))
        assertEquals(1008, c.closedWith)
        assertTrue(c.errors().all { it.contains("BAD_REQUEST") })
        val d = FakeConn("big").also { hub.register(it) }
        hub.onText(d, "{\"t\":\"pong\",\"id\":\"" + "a".repeat(100_000) + "\"}")
        assertTrue(d.errors().single().contains("message trop long"))
        assertEquals(PlayProtocol.MAX_MESSAGE_BYTES, 2_048)
    }

    @Test fun unknownKeysAreStillAcceptedAdditiveRule() {
        val d = MessageSchema.decode("{\"t\":\"pong\",\"id\":\"p1\",\"futur\":{\"a\":[1,2]}}")
        assertTrue(d is PlayCodec.Decoded.Ok)
    }
}
