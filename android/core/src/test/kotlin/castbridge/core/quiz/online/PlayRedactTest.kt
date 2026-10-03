package castbridge.core.quiz.online

import kotlin.test.*

class PlayRedactTest {
    private val token = "0123456789abcdef0123456789abcdef"
    private val ticket = "v1.eyJpYXQiOjEsImV4cCI6Mn0.c2lnbmF0dXJlc2lnbmF0dXJl"

    @Test fun tokensTicketsActivationAndCookiesAreRemoved() {
        val line = "reprise token=$token ticket=$ticket cbx1.AbCdEf.012345 cookie: __Host-cbp-ab12=$token Authorization: Bearer $ticket X-Play-Ticket: $ticket"
        val s = PlayRedact.scrub(line)
        assertFalse(PlayRedact.leaks(s), s)
        listOf(token, ticket, "cbx1", "Bearer", "__Host-cbp", "eyJpYXQ").forEach { assertFalse(it in s, "$it dans : $s") }
        assertTrue(PlayRedact.scrub("jeton $token").contains(PlayRedact.REDACTED))
    }

    @Test fun addressesAreTruncated() {
        assertEquals("192.168.x.x", PlayRedact.ip("192.168.14.77"))
        assertEquals("2001:0db8::", PlayRedact.ip("v6:2001:0db8:abcd:0001::/64"))
        assertEquals("2001:db8::", PlayRedact.ip("2001:db8:abcd:1::7"))
        assertEquals("?", PlayRedact.ip(null))
        assertEquals("depuis 203.0.x.x et 2001:db8::", PlayRedact.scrub("depuis 203.0.113.9 et 2001:db8:1:2:3:4:5:6"))
    }

    @Test fun roomCodesAreTruncatedPseudonymsAndDevicesAreHashed() {
        assertEquals("ABCD-****", PlayRedact.code("abcd-1234")); assertEquals("****", PlayRedact.code("n'importe quoi")); assertEquals("?", PlayRedact.code(null))
        assertEquals("code=ABCD-****", PlayRedact.scrub("code=ABCD-1234"))
        val p = PlayRedact.pseudo("Amina N.", 20_000); assertTrue(Regex("[0-9a-f]{8}").matches(p)); assertEquals(p, PlayRedact.pseudo(" amina n. ", 20_000))
        assertNotEquals(p, PlayRedact.pseudo("Amina N.", 20_001), "la clé change chaque jour : les empreintes ne se corrèlent pas d'un jour à l'autre")
        assertNotEquals(p, java.security.MessageDigest.getInstance("SHA-256").digest("amina n.".toByteArray()).take(4).joinToString("") { "%02x".format(it) }, "ce n'est pas un SHA-256 sans clé")
        assertNotEquals(p, PlayRedact.pseudo("Bello", 20_000)); assertFalse("amina" in p)
        assertTrue(Regex("[0-9a-f]{8}").matches(PlayRedact.device("device-000001")))
    }

    @Test fun toStringOfSecretCarryingMessagesNeverShowsTheSecret() {
        val msgs: List<Any> = listOf(
            ClientMsg.Hello(1, listOf("play1"), "device-secret-0001", ticket), ClientMsg.Join("ABCD-1234", "Amina N.", token, "device-secret-0001", false),
            ClientMsg.Resume("a".repeat(32), token, 3), ClientMsg.RelayAct(token, "q1", 1, 100, 2),
            ServerMsg.Welcome(1, "b".repeat(32), "ABCD1234", token, PlayRole.PLAYER, "p1", 1, listOf("play1")),
        )
        for (m in msgs) {
            val s = m.toString()
            listOf(token, ticket, "Amina", "ABCD1234", "ABCD-1234", "device-secret-0001").forEach { assertFalse(it in s, "« $it » dans $s") }
            assertFalse(PlayRedact.leaks(s.replace("a".repeat(32), "").replace("b".repeat(32), "")), s)
        }
        assertTrue(msgs.first().toString().contains("ticket=" + PlayRedact.REDACTED))
        assertEquals(ClientMsg.Join("ABCD-1234", "Amina N.", token, "d", false), ClientMsg.Join("ABCD-1234", "Amina N.", token, "d", false), "l'égalité des données ne change pas")
    }

    @Test fun ordinaryTextIsKept() {
        assertEquals("salle ouverte, 3 joueurs", PlayRedact.scrub("salle ouverte, 3 joueurs"))
    }
}
