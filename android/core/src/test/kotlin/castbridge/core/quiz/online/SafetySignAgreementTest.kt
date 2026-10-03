package castbridge.core.quiz.online

import castbridge.core.quiz.Json
import castbridge.core.ux.SignalLevel
import kotlin.test.*

/**
 * Checks SafetySign.of against castbridge/quiz/online/safety-table.json (the § 1.3 table). The same file is read by the web page
 * (w20-06) so that both screens provably share one semantics.
 */
class SafetySignAgreementTest {
    @Suppress("UNCHECKED_CAST")
    private fun rows(): List<Map<String, Any?>> {
        val text = javaClass.getResourceAsStream("/castbridge/quiz/online/safety-table.json")!!.readBytes().toString(Charsets.UTF_8)
        return (Json.obj(text)["rows"] as List<Map<String, Any?>>)
    }

    @Suppress("UNCHECKED_CAST")
    private fun facts(m: Map<String, Any?>): SafetyFacts {
        val f = m["facts"] as Map<String, Any?>
        fun b(k: String, d: Boolean) = f[k] as? Boolean ?: d
        fun i(k: String) = (f[k] as? Number)?.toInt() ?: 0
        return SafetyFacts(
            scope = PlayScope.valueOf(f["scope"] as String), tls = TlsState.valueOf(f["tls"] as? String ?: "OK"),
            serverLink = Link3.valueOf(f["serverLink"] as? String ?: "OK"), serverLostSec = i("serverLostSec"),
            lanLink = Link3.valueOf(f["lanLink"] as? String ?: "OK"), remotePlayers = i("remotePlayers"), localPlayers = i("localPlayers"),
            guestsViaQr = i("guestsViaQr"), kidProfile = b("kidProfile", false), internetAllowedByParent = b("internetAllowedByParent", false),
            ticketValid = b("ticketValid", true), clockDoubt = b("clockDoubt", false), tvHasInternet = b("tvHasInternet", true),
            internetDisabledByOwner = b("internetDisabledByOwner", false), resumingPlayer = f["resumingPlayer"] as? String,
            disconnectedPlayer = f["disconnectedPlayer"] as? String, rttMs = i("rttMs"))
    }

    @Test fun everyRowOfTheTableAgrees() {
        val rows = rows()
        assertTrue(rows.size >= 15, "the whole § 1.3 table is there")
        rows.forEach { r ->
            val v = SafetySign.of(facts(r))
            val id = r["id"]
            assertEquals(SignalLevel.valueOf(r["level"] as String), v.level, "$id level")
            assertEquals(r["word"], v.word, "$id word"); assertEquals(r["text"], v.text, "$id text"); assertEquals(r["action"], v.action, "$id action")
            assertEquals(SignalLevel.valueOf(r["level"] as String).shape.name, r["shape"], "$id shape in the table")
        }
        assertEquals(PlayScope.values().toSet(), rows.map { PlayScope.valueOf((it["facts"] as Map<*, *>)["scope"] as String) }.toSet())
    }
}
