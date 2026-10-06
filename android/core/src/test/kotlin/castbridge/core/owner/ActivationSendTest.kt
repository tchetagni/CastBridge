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

    // ---- « Activer par le Wi-Fi » a TV the phone has never been linked to (a locked TV announces itself on the Wi-Fi) ----
    private val found = listOf(ActivationSend.Found("CastBridge TV salon", "http://192.168.1.21:8765", locked = true), ActivationSend.Found("CastBridge TV chambre", "http://192.168.1.22:8765", locked = false))

    @Test fun theLinkedTvComesFirstThenTheTvFoundOnTheWifi() {
        assertEquals(base to "812345", ActivationSend.lanTarget(base to "812345", found, null))
        assertEquals("http://192.168.1.21:8765" to null, ActivationSend.lanTarget(null, found, null), "a locked TV first: it is the one waiting for a key")
        assertEquals("http://192.168.1.22:8765" to null, ActivationSend.lanTarget(null, found, "CastBridge TV chambre"))
        assertNull(ActivationSend.lanTarget(null, emptyList(), null))
        // the phone's own Bluetooth gateway (127.0.0.1) is not the Wi-Fi
        assertNull(ActivationSend.lanTarget(null, listOf(ActivationSend.Found("TV (Bluetooth)", "http://127.0.0.1:8766", false)), null))
        // a TV found but no code known: the code is asked, Bluetooth stays offered
        assertEquals(Channel.LAN_ASKS_PIN, ActivationSend.choose(ActivationSend.lanTarget(null, found, null)!!.first, null, null).channel)
    }

    @Test fun theCodeIsSaidToBeOnTheTvActivationScreen() {
        assertTrue("écran d'activation" in ActivationSend.ASK_PIN_TEXT && "6 chiffres" in ActivationSend.ASK_PIN_TEXT && "Bluetooth" in ActivationSend.ASK_PIN_TEXT)
    }
}
