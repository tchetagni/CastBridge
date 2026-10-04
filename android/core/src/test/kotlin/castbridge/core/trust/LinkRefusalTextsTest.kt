package castbridge.core.trust

import castbridge.core.tv.BtProtocol
import kotlin.test.*

class LinkRefusalTextsTest {
    private val all = (BtProtocol.ERR_MAGIC..BtProtocol.ERR_FULL_TIMEOUT).toList()

    @Test fun everyKnownCodeHasAFrenchCauseAndAnAction() {
        assertEquals(all, LinkRefusalTexts.KNOWN_CODES)
        for (c in all) {
            assertTrue(LinkRefusalTexts.cause(c).isNotBlank(), "cause $c")
            assertTrue(LinkRefusalTexts.action(c).isNotBlank(), "action $c")
            assertTrue("(code $c)" in LinkRefusalTexts.banner(c), "banner $c")
            assertTrue(LinkRefusalTexts.banner(c).startsWith(LinkRefusalTexts.cause(c)), "banner starts with the cause $c")
            assertTrue(LinkRefusalTexts.ticket(c).startsWith("Échec : "), "ticket $c")
        }
    }

    @Test fun causesAreDistinct() { assertEquals(all.size, all.map { LinkRefusalTexts.cause(it) }.toSet().size) }

    @Test fun code8IsTheOwnersCase() {
        assertEquals("La TV ne reconnaît plus ce téléphone (code 8) : saisissez le code PIN affiché sur la TV pour le ré-associer", LinkRefusalTexts.banner(BtProtocol.ERR_UNTRUSTED))
        assertEquals("Échec : la TV ne reconnaît plus ce téléphone. Touchez pour saisir le code PIN", LinkRefusalTexts.ticket(BtProtocol.ERR_UNTRUSTED))
        assertTrue(LinkRefusalTexts.asksPin(BtProtocol.ERR_UNTRUSTED))
    }

    @Test fun onlyCredentialCodesAskForThePin() {
        assertEquals(setOf(BtProtocol.ERR_PIN, BtProtocol.ERR_LOCKED, BtProtocol.ERR_UNTRUSTED, BtProtocol.ERR_DENIED), all.filter { LinkRefusalTexts.asksPin(it) }.toSet())
        assertFalse(LinkRefusalTexts.asksPin(BtProtocol.ERR_SPACE))
        assertFalse("PIN" in LinkRefusalTexts.ticket(BtProtocol.ERR_SPACE))
    }

    @Test fun unknownCodeGetsAGenericTextWithItsNumber() {
        for (c in listOf(0, 17, 99, -1)) {
            assertTrue(LinkRefusalTexts.cause(c).isNotBlank())
            assertTrue("(code $c)" in LinkRefusalTexts.banner(c))
            assertTrue("code $c" in LinkRefusalTexts.ticket(c))
            assertFalse(LinkRefusalTexts.asksPin(c))
        }
    }

    @Test fun noTextCarriesAPinOrASecret() {
        val six = Regex("\\d{6}")
        for (c in all + listOf(0, 99)) for (t in listOf(LinkRefusalTexts.cause(c), LinkRefusalTexts.action(c), LinkRefusalTexts.banner(c), LinkRefusalTexts.ticket(c))) {
            assertFalse(six.containsMatchIn(t), t)
            assertFalse("token" in t.lowercase() || "jeton" in t.lowercase(), t)
        }
    }
}
