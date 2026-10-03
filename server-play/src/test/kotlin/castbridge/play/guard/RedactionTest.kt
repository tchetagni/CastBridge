package castbridge.play.guard

import castbridge.core.quiz.Json
import castbridge.core.quiz.QuizDuel
import castbridge.core.quiz.online.ClientMsg
import castbridge.core.quiz.online.PlayCodec
import castbridge.core.quiz.online.PlayRedact
import castbridge.core.quiz.online.ServerRoom
import castbridge.play.TestKeys
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * T-18 : le journal du service ne contient ni jeton, ni ticket, ni activation `cbx1`, ni pseudonyme en clair, ni code de salle complet, ni adresse complète, ni
 * identifiant d'appareil. Épreuve sur les lignes RÉELLES d'une partie de test (un robot parfait, des pseudonymes refusés, des messages invalides, des essais en rafale).
 */
class RedactionTest {
    private fun tokensIn(vararg conns: FakeConn): Set<String> =
        conns.flatMap { c -> c.got.flatMap { Regex("\"token\":\"([0-9a-f]{32})\"").findAll(it).map { m -> m.groupValues[1] }.toList() } }.toSet()

    @Test fun fiftyOrMoreRealLogLinesOfAGameHoldNoSecret() {
        val lines = ArrayList<String>()
        var logClock = 1_700_000_000_000L
        var now = 1_000L
        val hub = GuardHarness.hub(ServerRoom.Settings(duelCount = 10), clock = { now })
        hub.guard = PlayGuard(castbridge.core.quiz.online.Limits(config = castbridge.core.quiz.online.Limits.Config(joinPerMinutePerDevice = 20)), LogRedactor({ lines += it }, { logClock += 1_500; logClock }))
        val ticket = TestKeys.ticket()
        val (tv, code) = GuardHarness.host(hub, ip = "198.51.100.9")
        val room = hub.rooms().single()
        val robotDevice = "robot-device-0001"; val humanDevice = "human-device-0002"
        val robot = GuardHarness.join(hub, code, "Amina N.", "robot", ip = "203.0.113.77", device = robotDevice)
        val human = GuardHarness.join(hub, code, "Bello", "human", ip = "203.0.113.78", device = humanDevice)
        val watcher = GuardHarness.join(hub, code, "Cyril", "watch", ip = "203.0.113.79", spectate = true)
        assertTrue(robot.welcomed() && human.welcomed() && watcher.welcomed())

        // pseudonymes refusés, messages invalides (dont une tentative d'injection de secrets dans le type), essais en rafale d'un même appareil
        repeat(25) { hub.onText(FakeConn("n$it", "203.0.113.${100 + it}").also { c -> hub.register(c) }, PlayCodec.encode(ClientMsg.Join(code, listOf("Admin CastBridge", "6 99 00 11 22 33", "http://x/$it", "merde$it")[it % 4], null, GuardHarness.dev(), false))) }
        repeat(7) { i ->
            val c = FakeConn("bad$i", "192.0.2.${i + 1}").also { hub.register(it) }
            hub.onText(c, "{\"t\":\"cbx1.SECRETSECRET.abcdef token=${"ab".repeat(16)}\"}"); hub.onText(c, "pas du json $ticket"); hub.onText(c, "[".repeat(20))
        }
        repeat(25) { GuardHarness.join(hub, code, "Dina", "rate$it", ip = "203.0.113.90", device = "burst-device-0003", spectate = true) }

        // la partie : le robot répond 100 ms après l'ouverture, l'humain de 2 à 4 s
        hub.onText(tv, PlayCodec.encode(ClientMsg.Act(null, "mode", null, "DUEL", 1)))
        hub.onText(tv, PlayCodec.encode(ClientMsg.Act(null, "start", null, "3", 2)))
        val answered = HashSet<String>()
        var guard = 0
        while (room.phase() != ServerRoom.State.FINISHED && guard++ < 60_000) {
            now += 50; hub.tick()
            val q = room.currentQuestionId() ?: continue
            val d = room.table(0).room.duel ?: continue
            if (room.table(0).duelPhase() != QuizDuel.Phase.QUESTION) continue
            val opens = room.table(0).opensAtServerMs
            if (now >= opens + 100 && answered.add("r$q")) hub.onText(robot, PlayCodec.encode(ClientMsg.Act(q, "answer", d.question.answer, null, 1)))
            if (now >= opens + 2_000 + (d.index * 211L) % 2_000 && answered.add("h$q")) hub.onText(human, PlayCodec.encode(ClientMsg.Act(q, "answer", if (d.index % 4 == 0) d.question.answer else (d.question.answer + 1) % 4, null, 1)))
        }
        assertEquals(ServerRoom.State.FINISHED, room.phase())
        hub.tick()
        assertTrue(room.unrankedSeats() > 0, "le robot parfait sort de son classement")

        assertTrue(lines.size >= 50, "au moins 50 lignes de journal réelles : ${lines.size}")
        val tokens = tokensIn(tv, robot, human, watcher)
        assertTrue(tokens.size >= 4)
        val joined = lines.joinToString("\n")
        for ((i, l) in lines.withIndex()) {
            val o = Json.parse(l) as Map<*, *>
            assertTrue(o["event.action"] is String && o["@timestamp"] is String, "ligne ECS $i : $l")
            assertFalse(PlayRedact.leaks(l.replace(Regex("\"roomId\":\"[0-9a-f]{32}\""), "")), "secret reconnaissable, ligne $i : $l")   // l'identifiant de salle est permis
        }
        tokens.forEach { assertFalse(it in joined, "jeton dans le journal") }
        listOf(ticket, "cbx1", "SECRETSECRET", "abababab", "v1.").forEach { assertFalse(it in joined, "« $it » dans le journal") }
        listOf("amina", "bello", "cyril", "dina", "castbridge", "merde").forEach { assertFalse(it in joined.lowercase(), "pseudonyme « $it » en clair dans le journal") }
        listOf(code, castbridge.core.quiz.online.RoomCode.display(code), code.take(4) + "-" + code.drop(4)).forEach { assertFalse(it in joined, "code de salle complet dans le journal") }
        listOf("203.0.113.77", "203.0.113.90", "198.51.100.9", "192.0.2.1\"", robotDevice, humanDevice, "burst-device-0003").forEach { assertFalse(it in joined, "« $it » dans le journal") }
        assertTrue("play.game.unranked" in joined && "play.name.refused" in joined && "play.msg.invalid" in joined && "play.limit.exceeded" in joined && "play.room.created" in joined)
        assertTrue(Regex("\"roomId\":\"[0-9a-f]{32}\"").containsMatchIn(joined), "l'identifiant de salle sert à corréler")
        assertTrue(Regex("\"ip\":\"203\\.0\\.x\\.x\"").containsMatchIn(joined), "adresses tronquées")
        assertTrue(Regex("\"name\":\"[0-9a-f]{8}\"").containsMatchIn(joined), "pseudonymes en empreinte de 8 hexadécimaux")
    }

    @Test fun theLoggerNeverThrowsAndScrubsFreeTextAndUnknownFields() {
        val out = ArrayList<String>()
        val log = LogRedactor({ out += it })
        log.event("x", "token=" + "ab".repeat(16) + " depuis 203.0.113.9", mapOf("detail" to "cbx1.AAAA.BBB", "obj" to Any(), "list" to listOf("v1.AAAAAAAA.BBBBBBBB"), "ip" to "203.0.113.9", "n" to 3, "ok" to true, "roomId" to "pas-un-id token=" + "cd".repeat(16)))
        val line = out.single()
        listOf("abababab", "203.0.113.9", "cbx1", "v1.AAAA", "cdcdcdcd").forEach { assertFalse(it in line, "« $it » : $line") }
        assertTrue("\"obj\":\"Object\"" in line, "un objet inconnu ne montre jamais son toString : $line")
        LogRedactor({ throw IllegalStateException("puits cassé") }).event("y")   // ne lève rien
    }

    @Test fun noSourceLineOfTheServiceLogsAnythingElseThanThroughTheRedactor() {
        val allowed = setOf("Ticker.kt", "PlayApplication.kt", "PlayConfig.kt", "LogRedactor.kt")
        val offenders = java.io.File("src/main/kotlin").walkTopDown().filter { it.extension == "kt" && it.name !in allowed }
            .filter { Regex("System\\.(err|out)|printStackTrace|println\\(|java\\.util\\.logging|Logger").containsMatchIn(it.readText()) }.map { it.name }.toList()
        assertTrue(offenders.isEmpty(), "journal hors LogRedactor : $offenders")
    }
}
