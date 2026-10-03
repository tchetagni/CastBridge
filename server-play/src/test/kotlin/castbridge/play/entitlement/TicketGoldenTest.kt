package castbridge.play.entitlement

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Le ticket doré : émis par l'API Java (`PlayTicketGoldenTest`, clé de TEST, horloge fixe) et vérifié ici par le vérificateur Kotlin du service. Les deux langages s'accordent sur le format
 * (préfixe de domaine, base64url, noms de champs) ; si l'un dérive, l'un des deux tests casse.
 */
class TicketGoldenTest {
    private val vector = File("src/test/resources/play/ticket-golden.txt").readLines().filter { !it.startsWith("#") && '=' in it }.associate { it.substringBefore('=') to it.substringAfter('=') }
    private val now = vector.getValue("now").toLong()
    private val ticket = vector.getValue("ticket")

    @Test fun theKotlinVerifierAcceptsTheTicketIssuedByTheJavaApi() {
        val ok = assertIs<TicketVerifier.Result.Ok>(TicketVerifier(listOf(vector.getValue("pub"))).check(ticket, now + 1_000))
        assertEquals(vector.getValue("deviceId"), ok.ticket.deviceId)
        assertEquals(vector.getValue("deviceCode"), ok.ticket.deviceCode)
        assertEquals(600_000L, ok.ticket.exp - ok.ticket.iat)
        assertEquals("CM", ok.ticket.country)
    }

    @Test fun itExpiresAfterTenMinutesAndAnotherKeyRefusesIt() {
        val v = TicketVerifier(listOf(vector.getValue("pub")))
        assertEquals(TicketVerifier.Refusal.EXPIRED, (v.check(ticket, now + 601_000) as TicketVerifier.Result.Refused).why)
        assertTrue(v.check(ticket, now + 599_000) is TicketVerifier.Result.Ok)
        val other = java.util.Base64.getEncoder().encodeToString(ByteArray(32) { 9 })
        assertTrue(TicketVerifier(listOf(other)).check(ticket, now + 1_000) is TicketVerifier.Result.Refused)
    }
}
