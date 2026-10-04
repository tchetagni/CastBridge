package castbridge.core.quiz.online

import java.io.File
import kotlin.test.*

/** Aucune occurrence du secret de session ni du ticket dans un `toString`, une adresse ou un journal (test de source + de comportement). */
class PlayTvSecretsSourceTest {
    private val files = listOf("PlayHttpTransport", "PlayTvSession", "RelayAuthority", "LinkCause").map { File("src/main/kotlin/castbridge/core/quiz/online/$it.kt") }

    @Test fun noLoggingAndNoSecretInAnAddress() {
        for (f in files) {
            val src = f.readText()
            for (bad in listOf("println(", "System.out", "System.err", "printStackTrace", "Log.d(", "Log.i(", "Log.w(", "Log.e(", "Logger")) assertFalse(src.contains(bad), "${f.name} : « $bad »")
        }
        val t = files[0].readText()
        assertFalse(t.contains("?token=") || t.contains("token=\$") || t.contains("conn=\$") || t.contains("secret=\$"), "aucun secret dans une adresse")
        // les adresses ouvertes sont des chemins fixes ; le secret et le ticket ne passent que par des en-têtes
        assertEquals(setOf("/play/act", "/play/events", "/play/state?since=\$lastEventId"), Regex("open\\(\"([^\"]+)\"").findAll(t).map { it.groupValues[1] }.toSet())
        for (m in Regex("X-Play-(Conn|Ticket)").findAll(t)) {
            val line = t.substring(0, m.range.first).substringAfterLast('\n') + t.substring(m.range.first).substringBefore('\n')
            assertTrue(line.contains("setRequestProperty") || line.trimStart().startsWith("*") || line.trimStart().startsWith("//") || line.contains("/**"), "X-Play-* seulement en en-tête : $line")
        }
    }

    @Test fun toStringsAreMasked() {
        val t = PlayHttpTransport("https://bridge.sti-cm.com", "cbp1.TICKETSECRET.SIGSECRET", { null }, { 0L })
        val s = t.toString()
        assertFalse(s.contains("TICKETSECRET") || s.contains("SIGSECRET") || s.contains("cbp1"), s)
        assertTrue(s.contains("secret=***") && s.contains("ticket=***") && s.contains("bridge.sti-cm.com"))
        t.close()
        // les messages client qui portent un secret se masquent déjà (PlayRedact) : le relais aussi
        assertFalse(ClientMsg.RelayAct("SEATSECRET0123456789", "q1", 1, 500, 3).toString().contains("SEATSECRET"))
        assertFalse(ServerMsg.Welcome(1, "r", "K7M2QX4T", "TOKENSECRET", PlayRole.HOST, null, 1, emptyList()).toString().contains("TOKENSECRET"))
    }
}
