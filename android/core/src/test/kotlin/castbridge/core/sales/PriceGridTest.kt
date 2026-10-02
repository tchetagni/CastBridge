package castbridge.core.sales

import castbridge.core.net.JsonLite
import castbridge.core.owner.AgentFixtures
import kotlin.test.*

class PriceGridTest {
    private val signer = AgentFixtures.key("desk").signer
    private val pub = listOf(signer.publicKeyBase64)
    private val prices = listOf(PriceGrid.Price("cle-production", 90, 0), PriceGrid.Price("cle-essai", 30, 0), PriceGrid.Price("cle-production", 365, 0), PriceGrid.Price("cle-production", 30, 0))
    private val at = "2026-10-02T09:00:00Z"
    private fun signed(generatedAt: String = at, p: List<PriceGrid.Price> = prices) = PriceGrid.signedJson(signer, generatedAt, p)
    private fun refused(json: String, keys: List<String> = pub, older: String? = null) = assertFailsWith<PriceGrid.Refused> { PriceGrid.verify(json, keys, older) }.message!!

    @Test fun signedGridVerifiesAndAnswersPrices() {
        val v = PriceGrid.verify(signed(), pub)
        assertEquals(at, v.generatedAt); assertEquals("02/10/2026", v.dateFr); assertEquals(signer.keyId, v.keyId)
        assertEquals(0, v.priceOf("cle-production", 90)); assertNull(v.priceOf("cle-production", 91)); assertNull(v.priceOf("loc-maths", 30))
        assertEquals(4, v.items().size)
    }

    @Test fun canonicalPayloadSortsPriceLinesAsPlainText() {
        val p = PriceGrid.canonicalPayload(at, prices.map { it.copy(xaf = 5000) }).split('\n')
        assertEquals(listOf("castbridge-price-grid-v1", "generatedAt=$at", "currency=XAF"), p.take(3))
        assertEquals(listOf("price=cle-essai|30|5000", "price=cle-production|30|5000", "price=cle-production|365|5000", "price=cle-production|90|5000"), p.drop(3))
    }

    @Test fun anAlteredPriceIsRefused() {
        val m = JsonLite.obj(signed()).toMutableMap()
        @Suppress("UNCHECKED_CAST") val list = (m["prices"] as List<Map<String, Any?>>).map { if (it["item"] == "cle-production" && it["days"] == 90L) it + ("price" to 1) else it }
        m["prices"] = list
        assertContains(refused(JsonLite.write(m)), "Signature")
    }

    @Test fun unsignedWrongKeyOrOlderGridIsRefused() {
        assertContains(refused(signed().replace(Regex("\"signature\":\"[^\"]*\""), "\"signature\":\"UNSIGNED\"")), "non signée")
        assertContains(refused(signed(), keys = listOf(AgentFixtures.key("rogue").signer.publicKeyBase64)), "Signature")
        assertContains(refused(signed(), keys = emptyList()), "Aucune clé")
        assertContains(refused(signed(generatedAt = "2026-09-01T00:00:00Z"), older = at), "plus ancienne")
        PriceGrid.verify(signed(), pub, notOlderThan = at)                               // the same date is accepted
        PriceGrid.verify(signed(generatedAt = "2026-11-01T00:00:00Z"), pub, notOlderThan = at)
    }

    @Test fun malformedGridsAreRefusedWithAFrenchMessage() {
        assertContains(refused("pas du json"), "illisible")
        assertContains(refused(signed(generatedAt = "hier")), "date")
        assertContains(refused(signed(p = emptyList())), "vide")
        assertContains(refused(signed(p = prices + PriceGrid.Price("cle-essai", 30, 10))), "double")
        assertContains(refused(signed(p = listOf(PriceGrid.Price("cle-essai", 30, -1)))), "hors bornes")
        assertContains(refused(signed(p = listOf(PriceGrid.Price("Cle Essai", 30, 0)))), "hors bornes")
        assertContains(refused(signed().replace("castbridge-price-grid-v1", "autre")), "format")
        assertContains(refused(signed().replace("XAF", "EUR")), "monnaie")
    }
}
