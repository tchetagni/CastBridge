package castbridge.play.guard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Câblage des gardes dans le service (w20-07) : messages invalides, pseudonymes refusés avec motif. */
class GuardWiringTest {
    @Test fun threeInvalidMessagesCloseWith1008() {
        val hub = GuardHarness.hub()
        val c = FakeConn("c1").also { hub.register(it) }
        hub.onText(c, "pas du json"); hub.onText(c, "{\"t\":1}")
        assertNull(c.closedWith, "deux invalides : la session reste ouverte")
        assertFalse(hub.onText(c, "[1,2,3]"), "le troisième invalide ferme la session")
        assertEquals(1008, c.closedWith)
    }

    @Test fun refusedPseudonymsAnswerBadNameWithAReason() {
        val hub = GuardHarness.hub()
        val (_, code) = GuardHarness.host(hub)
        for ((i, bad) in listOf("Admin CastBridge", "6 99 00 11 22 33", "http://x").withIndex()) {
            val c = GuardHarness.join(hub, code, bad, "j$i")
            assertFalse(c.welcomed(), "« $bad » ne doit pas entrer")
            val e = c.errors().single()
            assertTrue(e.contains("\"reason\":\"BAD_NAME\""), e)
            assertTrue(e.contains("Pseudonyme refusé"), "le motif est dit en français : $e")
        }
        assertTrue(GuardHarness.join(hub, code, "Amina N.", "ok").welcomed())
        // le numéro complet « +237 6 99 00 11 22 » dépasse 16 caractères : le codec le refuse déjà (BAD_REQUEST), jamais accepté
        val long = GuardHarness.join(hub, code, "+237 6 99 00 11 22", "long")
        assertFalse(long.welcomed()); assertTrue(long.errors().single().contains("BAD_REQUEST") || long.errors().single().contains("BAD_NAME"))
    }
}
