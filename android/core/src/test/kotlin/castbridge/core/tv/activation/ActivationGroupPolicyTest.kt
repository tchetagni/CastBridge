package castbridge.core.tv.activation

import castbridge.core.tv.activation.ActivationGroupPolicy as G
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * When the activation screen of a LOCKED TV makes its own Wi-Fi Direct group (act-tv-2, docs/TV-ACTIVATION-CLE-USB.md « Voie A : deux cas »):
 *  - at once when the TV has no Wi-Fi network (the group is then its only way in), or a cable (Wi-Fi Direct never touches it);
 *  - only on the person's request when the TV is on a Wi-Fi network: a TV with a single Wi-Fi radio (a USB Wi-Fi key) can lose its link to the box while its own group exists;
 *  - once the group is given back, a Wi-Fi link that was cut is relaunched (best effort, bounded).
 * Pure: the Android side only reads the facts and does what this decides.
 */
class ActivationGroupPolicyTest {
    private val none = G.Net(wifiConnected = false, ethernetUp = false)
    private val wifi = G.Net(wifiConnected = true, ethernetUp = false)
    private val cable = G.Net(wifiConnected = false, ethernetUp = true)
    private val both = G.Net(wifiConnected = true, ethernetUp = true)

    // ---- (1) no network: the group at once (the behaviour of act-tv) ----

    @Test fun `a TV with no Wi-Fi network and no cable makes its group at once`() {
        val d = G.decide(none, asked = false)
        assertEquals(G.Decision.NO_WIFI, d)
        assertTrue(d.create)
    }

    // ---- (2) on a Wi-Fi network: the group waits for the person ----

    @Test fun `a TV on a Wi-Fi network keeps the group for the person's request`() {
        val d = G.decide(wifi, asked = false)
        assertEquals(G.Decision.WIFI_PRESENT, d)
        assertFalse(d.create, "creating a group on a single-radio TV may cut its Wi-Fi link: not without being asked")
    }

    @Test fun `the person's request makes the group even on a Wi-Fi network`() {
        val d = G.decide(wifi, asked = true)
        assertEquals(G.Decision.ASKED, d)
        assertTrue(d.create)
    }

    // ---- (3) a cable: never touched by Wi-Fi Direct ----

    @Test fun `a TV on a cable makes its group at once, with or without a Wi-Fi network`() {
        for (n in listOf(cable, both)) {
            val d = G.decide(n, asked = false)
            assertEquals(G.Decision.ETHERNET, d, "$n")
            assertTrue(d.create, "$n")
        }
    }

    @Test fun `every combination of facts has exactly the expected decision`() {
        // (Wi-Fi connected, cable, asked) -> decision
        val table = listOf(
            Triple(false, false, false) to G.Decision.NO_WIFI,
            Triple(false, false, true) to G.Decision.ASKED,
            Triple(true, false, false) to G.Decision.WIFI_PRESENT,
            Triple(true, false, true) to G.Decision.ASKED,
            Triple(false, true, false) to G.Decision.ETHERNET,
            Triple(false, true, true) to G.Decision.ASKED,
            Triple(true, true, false) to G.Decision.ETHERNET,
            Triple(true, true, true) to G.Decision.ASKED,
        )
        assertEquals(8, table.size)
        for ((facts, want) in table) assertEquals(want, G.decide(G.Net(wifiConnected = facts.first, ethernetUp = facts.second), asked = facts.third), "wifi=${facts.first} cable=${facts.second} asked=${facts.third}")
        // the group is kept for later in ONE case only: on a Wi-Fi network, no cable, nobody asked
        val waiting = table.filter { !it.second.create }.map { it.first }
        assertEquals(listOf(Triple(true, false, false)), waiting)
    }

    @Test fun `the facts default to a TV with nothing`() {
        assertEquals(none, G.Net())
    }

    // ---- the journal line (never a code, an address or a password) ----

    @Test fun `each decision has its own one-line journal text with no digit`() {
        val all = G.Decision.values().toList()
        assertEquals(4, all.size)
        assertEquals(all.size, all.map { it.log }.toSet().size, "one text per decision")
        for (d in all) {
            assertTrue(d.log.isNotBlank() && '\n' !in d.log, "$d")
            assertTrue(d.log.none { it.isDigit() }, "an address or a code could hide in a digit: ${d.log}")
            assertFalse("mot de passe" in d.log.lowercase() || "password" in d.log.lowercase(), d.log)
        }
        assertEquals(setOf(G.Decision.NO_WIFI, G.Decision.ETHERNET, G.Decision.ASKED), all.filter { it.create }.toSet())
    }

    // ---- the words of the screen ----

    @Test fun `the screen's words are the owner's`() {
        assertEquals("Le téléphone n'est pas sur ce Wi-Fi ? OK : réseau direct", G.OFFER_LINE)
        assertEquals("Le Wi-Fi de la TV peut se couper le temps de l'activation.", G.OFFER_WARNING)
        assertEquals("Téléphone sur le même Wi-Fi : tapez le code dans CastBridge › Activer la TV", G.SAME_WIFI_INSTRUCTION)
        for (t in listOf(G.OFFER_LINE, G.OFFER_WARNING, G.SAME_WIFI_INSTRUCTION)) {
            assertFalse("sender" in t.lowercase() || "receiver" in t.lowercase(), "only the product names: $t")
            assertTrue(t.none { it.isDigit() }, "no code in a fixed text: $t")
        }
        assertNotEquals(G.OFFER_LINE, G.OFFER_WARNING)
    }

    // ---- (4) the Wi-Fi link after the group ----

    @Test fun `a Wi-Fi link the group cut is relaunched, and only that one`() {
        assertEquals(G.Restore.RECONNECT, G.restore(wifiBefore = true, wifiNow = false, wifiEnabled = true, attempts = 0))
        assertEquals(G.Restore.NOTHING, G.restore(wifiBefore = false, wifiNow = false, wifiEnabled = true, attempts = 0), "the TV had no Wi-Fi link: nothing was cut")
        assertEquals(G.Restore.NOTHING, G.restore(wifiBefore = true, wifiNow = true, wifiEnabled = true, attempts = 0), "the link came back by itself")
        assertEquals(G.Restore.NOTHING, G.restore(wifiBefore = true, wifiNow = false, wifiEnabled = false, attempts = 0), "the radio was switched off by someone: not ours to switch on")
        assertEquals(G.Restore.NOTHING, G.restore(wifiBefore = true, wifiNow = true, wifiEnabled = true, attempts = 5), "back at last: nothing more, whatever was tried")
        assertEquals(G.Restore.NOTHING, G.restore(wifiBefore = false, wifiNow = false, wifiEnabled = false, attempts = 0))
    }

    @Test fun `the reconnection is tried twice at most, then given up`() {
        assertEquals(2, G.RESTORE_ATTEMPTS)
        assertEquals(G.Restore.RECONNECT, G.restore(true, false, true, attempts = 0))
        assertEquals(G.Restore.RECONNECT, G.restore(true, false, true, attempts = 1))
        assertEquals(G.Restore.GIVE_UP, G.restore(true, false, true, attempts = 2))
        assertEquals(G.Restore.GIVE_UP, G.restore(true, false, true, attempts = 9))
    }

    @Test fun `a link that never comes back stops the sequence by itself`() {
        var attempts = 0
        var reconnects = 0
        var steps = 0
        loop@ while (true) {
            assertTrue(++steps < 20, "the sequence must end")
            when (G.restore(wifiBefore = true, wifiNow = false, wifiEnabled = true, attempts = attempts)) {
                G.Restore.RECONNECT -> { reconnects++; attempts++ }
                G.Restore.GIVE_UP -> break@loop
                G.Restore.NOTHING -> error("the link is not back: something must be tried or given up")
            }
        }
        assertEquals(G.RESTORE_ATTEMPTS, reconnects)
    }

    @Test fun `the link is looked at soon after the group is given back, then at a slower pace, all within half a minute`() {
        assertEquals(3_000L, G.restoreDelayMs(0))
        assertEquals(7_000L, G.restoreDelayMs(1))
        assertEquals(7_000L, G.restoreDelayMs(2))
        assertTrue((0..G.RESTORE_ATTEMPTS).sumOf { G.restoreDelayMs(it) } <= 30_000L, "bounded: no endless retries")
    }
}
