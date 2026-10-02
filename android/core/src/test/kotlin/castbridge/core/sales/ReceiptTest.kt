package castbridge.core.sales

import castbridge.core.owner.AgentFixtures
import castbridge.core.owner.Base32C
import kotlin.test.*

class ReceiptTest {
    private val agent = AgentFixtures.key("agent").signer
    private val t0 = AgentFixtures.T0
    private val device = "ABCD-EFGH-JKMN-PQR0"
    private val shape = Regex("^R-[0-9A-HJKMNP-TV-Z]{4}-[0-9A-HJKMNP-TV-Z]{4}$")

    @Test fun codeHasTheShapeAndACheckCharacter() {
        val c = Receipt.code(agent.keyId, 1, device, t0)
        assertTrue(shape.matches(c), c)
        val data = c.removePrefix("R-").replace("-", "")
        assertEquals(Base32C.check(data.take(7), salt = 0), data[7])
        assertEquals(c, Receipt.code(agent.keyId, 1, device, t0))
    }

    @Test fun codeDependsOnEveryInput() {
        val base = Receipt.code(agent.keyId, 1, device, t0)
        assertNotEquals(base, Receipt.code(agent.keyId, 2, device, t0)); assertNotEquals(base, Receipt.code(agent.keyId, 1, "ABCD-EFGH-JKMN-PQR1", t0))
        assertNotEquals(base, Receipt.code(agent.keyId, 1, device, t0 + 1)); assertNotEquals(base, Receipt.code(AgentFixtures.kid("agent2"), 1, device, t0))
    }

    @Test fun parseToleratesTypingMistakesButCatchesWrongCharacters() {
        val c = Receipt.code(agent.keyId, 7, device, t0)
        assertEquals(c, Receipt.parse(c)); assertEquals(c, Receipt.parse(" " + c.lowercase() + "\n")); assertEquals(c, Receipt.parse(c.removePrefix("R-").replace("-", "")))
        val withZero = Receipt.code(agent.keyId, 7, device, t0).replace('0', 'O')           // O typed for 0
        assertEquals(c, Receipt.parse(withZero))
        val data = c.removePrefix("R-").replace("-", ""); val flipped = (if (data[2] == 'Z') 'Y' else 'Z')
        assertNull(Receipt.parse("R-" + data.replaceRange(2, 3, flipped.toString()).chunked(4).joinToString("-")))   // one wrong character: the check catches it
        assertNull(Receipt.parse("R-ABCD")); assertNull(Receipt.parse("")); assertNull(Receipt.parse("R-ABCD-EFG!"))
    }

    private fun saleEntry(cash: Long? = 5000, item: String = "cle-production|90"): SalesLedger.Entry {
        val e = SalesLedger.Entry(1, t0, SalesLedger.Kind.SALE, agent.keyId, device = device, license = "lic-0001", seat = "0123456789abcdef", item = item, price = 5000, cash = cash ?: 0, grid = "2026-10-02T09:00:00Z",
            receipt = Receipt.code(agent.keyId, 1, device, t0), fp = "ab".repeat(32))
        return e.sign(agent)
    }

    @Test fun textIsFrenchCarriesTheContactGivenAndNoHardCodedNumber() {
        val e = saleEntry()
        val t = Receipt.text(e, "douala-akwa-01", "Contact : exemple@exemple.test")
        assertContains(t, "Reçu ${e.receipt}"); assertContains(t, "TV $device"); assertContains(t, "Version complète 90 jours"); assertContains(t, "5 000 XAF")
        assertContains(t, "Point focal douala-akwa-01"); assertContains(t, "15/01/2027"); assertTrue(t.endsWith("Contact : exemple@exemple.test"))
        assertFalse(Receipt.text(e, "x", "").endsWith(" — "))
        assertContains(Receipt.text(saleEntry(item = "cle-essai|30"), "x", "c"), "Clé d'essai 30 jours")
        assertContains(Receipt.text(saleEntry(item = "bon|jetons-60|SN1"), "x", "c"), "Bon de recharge jetons-60")
        assertContains(Receipt.text(saleEntry(item = "commande|C-1|5000"), "x", "c"), "Commande C-1")
    }

    @Test fun textRefusesAnEntryThatIsNotASale() {
        val note = SalesLedger.Entry(1, t0, SalesLedger.Kind.NOTE, agent.keyId, note = "x").sign(agent)
        assertFailsWith<IllegalArgumentException> { Receipt.text(note, "x", "c") }
    }
}
