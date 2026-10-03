package castbridge.play

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OriginCheckTest {
    private val check = OriginCheck(setOf("https://bridge.sti-cm.com"))

    @Test fun listedOriginPasses() {
        assertTrue(check.allows("https://bridge.sti-cm.com", false))
        assertTrue(check.allows("HTTPS://Bridge.STI-CM.com", false), "la casse de l'origine ne compte pas")
    }

    @Test fun unknownOriginIsRefusedEvenWithATicket() {
        assertFalse(check.allows("https://evil.example", false))
        assertFalse(check.allows("https://evil.example", true), "un ticket valide ne rachète pas une origine étrangère")
        assertFalse(check.allows("http://bridge.sti-cm.com", false), "autre schéma")
        assertFalse(check.allows("https://bridge.sti-cm.com:8443", false), "autre port")
        assertFalse(check.allows("https://sub.bridge.sti-cm.com", false), "autre sous-domaine")
    }

    @Test fun absentOrNullOriginNeedsAValidTicket() {
        for (o in listOf(null, "", "null", " NULL ")) {
            assertFalse(check.allows(o, false), "origine « $o » sans ticket")
            assertTrue(check.allows(o, true), "origine « $o » avec ticket valide (client natif)")
        }
    }

    @Test fun readRoutesAcceptSameOriginButNotAForeignOne() {
        assertTrue(check.allowsRead(null)); assertTrue(check.allowsRead("https://bridge.sti-cm.com"))
        assertFalse(check.allowsRead("https://evil.example")); assertFalse(check.allowsRead("null"))
    }

    @Test fun emptyAllowListRefusesEveryBrowser() {
        val none = OriginCheck(emptySet())
        assertFalse(none.allows("https://bridge.sti-cm.com", true)); assertTrue(none.allows(null, true))
    }
}
