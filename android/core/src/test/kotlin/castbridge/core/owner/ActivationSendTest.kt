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

    @Test fun theLinkedTvComesFirstThenTheTvTheCustomerTouched() {
        assertEquals(base to "812345", ActivationSend.lanTarget(base to "812345", found, null))
        assertEquals("http://192.168.1.22:8765" to null, ActivationSend.lanTarget(null, found, "http://192.168.1.22:8765"))
        assertEquals("http://192.168.1.21:8765" to null, ActivationSend.lanTarget(null, found, "http://192.168.1.21:8765"))
        assertNull(ActivationSend.lanTarget(null, emptyList(), null))
        // the phone's own Bluetooth gateway (127.0.0.1) is not the Wi-Fi
        assertNull(ActivationSend.lanTarget(null, listOf(ActivationSend.Found("TV (Bluetooth)", "http://127.0.0.1:8766", false)), "http://127.0.0.1:8766"))
        // a TV found but no code known: the code is asked, Bluetooth stays offered
        assertEquals(Channel.LAN_ASKS_PIN, ActivationSend.choose(ActivationSend.lanTarget(null, found, "http://192.168.1.21:8765")!!.first, null, null).channel)
    }

    // ---- audit M2: never an automatic choice, never a public address ----

    @Test fun noTvIsChosenAutomaticallyWhenThePhoneIsNotLinked() {
        assertNull(ActivationSend.lanTarget(null, found, null), "even a TV announcing locked=1 must be touched by the customer")
        assertNull(ActivationSend.lanTarget(null, listOf(found.first()), null), "even a single one")
        assertNull(ActivationSend.lanTarget(null, found, "http://192.168.1.99:8765"), "a TV that is no longer announced")
    }

    @Test fun onlyPrivateAddressesAreTargets() {
        for (b in listOf("http://8.8.8.8:8765", "http://100.64.1.2:8765", "http://evil.example.com:8765", "http://192.168.1.21.evil.com:8765", "https://192.168.1.21:8765", "192.168.1.21:8765"))
            assertNull(ActivationSend.lanTarget(null, listOf(ActivationSend.Found("TV", b, true)), b), b)
        for (b in listOf("http://10.0.0.5:8765", "http://172.20.1.1:8765", "http://[fe80::1%wlan0]:8765", "http://fd12::5:8765"))
            assertEquals(b to null, ActivationSend.lanTarget(null, listOf(ActivationSend.Found("TV", b, true)), b), b)
        // a linked TV with a public address is refused too
        assertNull(ActivationSend.lanTarget("http://8.8.8.8:8765" to "812345", found, null))
        assertEquals("192.168.1.21", ActivationSend.hostOf("http://192.168.1.21:8765"))
        assertEquals("fe80::1%wlan0", ActivationSend.hostOf("http://[fe80::1%wlan0]:8765"))
        assertEquals("fd12::5", ActivationSend.hostOf("http://fd12::5:8765"))
        assertNull(ActivationSend.hostOf("ftp://192.168.1.21:8765"))
    }

    @Test fun theScreenShowsTheTargetAddressNextToTheCode() {
        assertEquals("TV 192.168.1.21", ActivationSend.targetLabel("http://192.168.1.21:8765"))
        assertNull(ActivationSend.targetLabel(null))
    }

    // ---- audit M2: an announce with the same name from another address does not replace the TV silently ----

    private data class Ann(val name: String, val host: String, val locked: Boolean = false, val other: String? = null)
    private fun merge(cur: List<Ann>, n: Ann) = ActivationSend.mergeAnnounce(cur, n, { it.name }, { it.host }, { it.other }, { a, other -> a.copy(other = other) })

    @Test fun aSameNameAnnounceFromAnotherAddressKeepsTheOldestAndIsFlagged() {
        val first = merge(emptyList(), Ann("Salon", "192.168.1.21", locked = true))
        assertEquals(listOf(Ann("Salon", "192.168.1.21", true)), first)
        val again = merge(first, Ann("Salon", "192.168.1.21", locked = false))
        assertEquals(listOf(Ann("Salon", "192.168.1.21", false)), again, "the same TV re-announced: updated")
        val spoof = merge(again, Ann("Salon", "192.168.1.66", locked = true))
        assertEquals(listOf(Ann("Salon", "192.168.1.21", false, other = "192.168.1.66")), spoof, "the oldest address kept, the other one signalled")
        assertEquals(listOf(Ann("Salon", "192.168.1.21", true, other = "192.168.1.66")), merge(spoof, Ann("Salon", "192.168.1.21", true)), "the flag stays")
        assertEquals(2, merge(spoof, Ann("Chambre", "192.168.1.22")).size)
    }

    @Test fun theCodeIsSaidToBeOnTheTvActivationScreen() {
        assertTrue("écran d'activation" in ActivationSend.ASK_PIN_TEXT && "6 chiffres" in ActivationSend.ASK_PIN_TEXT && "Bluetooth" in ActivationSend.ASK_PIN_TEXT)
    }
}
