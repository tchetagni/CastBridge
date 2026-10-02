package castbridge.core.owner

import castbridge.core.lots.TvReply
import castbridge.core.lots.TvTransport
import castbridge.core.owner.ActivationSend.Channel
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ActivationSendTest {
    private val base = "http://192.168.1.20:8765"
    private val token = "cbt_" + "a".repeat(64)

    @Test fun lanWithStoredPin() = ActivationSend.choose(base, "812345", null).let { assertEquals(Channel.LAN_WITH_PIN, it.channel); assertEquals("812345", it.pin) }
    @Test fun tokenOnlyAsksThePin() = ActivationSend.choose(base, token, null).let { assertEquals(Channel.LAN_ASKS_PIN, it.channel); assertNull(it.pin); assertTrue(it.explanation!!.contains("6 chiffres")) }
    @Test fun tokenPlusTypedPin() = ActivationSend.choose(base, token, " 654321 ").let { assertEquals(Channel.LAN_WITH_PIN, it.channel); assertEquals("654321", it.pin) }
    @Test fun badTypedPinAsksAgain() = ActivationSend.choose(base, null, "12").let { assertEquals(Channel.LAN_ASKS_PIN, it.channel); assertTrue(it.explanation!!.contains("6 chiffres")) }
    @Test fun noWifiFallsBackToBluetooth() = ActivationSend.choose(null, "812345", "812345").let { assertEquals(Channel.BLUETOOTH, it.channel); assertTrue(it.explanation!!.contains("Bluetooth")) }

    private fun fake(status: Int, json: String, seen: MutableList<Triple<String, String, String>> = mutableListOf()) =
        TvTransport { m, p, _, body -> seen += Triple(m, p, String(body!!, Charsets.UTF_8)); TvReply(status, json) }

    @Test fun postsTheKeyAsBody() {
        val seen = mutableListOf<Triple<String, String, String>>()
        val r = ActivationSend.sendLan(fake(200, """{"installed":true,"label":"Licence 1"}""", seen), "  KEY-123 \n")
        assertTrue(r.ok); assertEquals(Triple("POST", "/api/activation/install", "KEY-123"), seen.single())
        val o = ActivationScreenState.sendOutcome(r.ok, r.message); assertTrue(o.ok); assertFalse(o.staged); assertTrue(o.text.contains("Licence 1"))
    }
    @Test fun refusedKeyKeepsTheTvReason() {
        val r = ActivationSend.sendLan(fake(422, """{"error":"clé d'une autre TV"}"""), "k")
        assertFalse(r.ok); assertFalse(r.pinRefused); assertEquals("Refusée par la TV : clé d'une autre TV", ActivationScreenState.sendOutcome(r.ok, r.message).text)
    }
    @Test fun wrongPinAndOldTvAndLinkDown() {
        assertTrue(ActivationSend.sendLan(fake(401, "{}"), "k").pinRefused)
        assertTrue(ActivationSend.sendLan(fake(404, "{}"), "k").linkDown)
        assertTrue(ActivationSend.sendLan({ _, _, _, _ -> throw IOException("down") }, "k").linkDown)
    }
}
