package castbridge.core.lots

import castbridge.core.owner.RentalSpec
import kotlin.test.*

class RentalDurationsTest {
    private val catalog = BundleCatalog.parse("""{"bundles":[
        {"id":"classe-cm2","type":"classe","lots":["learn:cm2"],"title":"CM2","rentalDays":30},
        {"id":"classe-cp","type":"classe","lots":["learn:cp"],"title":"CP","rentalDays":45},
        {"id":"classe-ce1","type":"classe","lots":["learn:ce1"],"title":"CE1"}]}""")

    @Test fun oneRentalPerBundleWithTheServersExactDuration() {
        val s = RentalDurations.specsFor(catalog, listOf("classe-cp", "classe-cm2"))
        assertEquals(listOf("loc-classe-cm2" to 30, "loc-classe-cp" to 45), s.map { it.productId to it.days })
        assertTrue(s.all { it.bundleIds.size == 1 })
    }

    @Test fun aBundleWithoutADurationGetsTheOwnersDefaultOf30DaysAndAnUnknownOneIsRefused() {
        assertEquals(30, RentalDurations.DEFAULT_DAYS)
        assertEquals(30, RentalDurations.specsFor(catalog, listOf("classe-ce1")).single().days)
        assertNull(RentalDurations.check(RentalSpec("loc-classe-ce1", listOf("classe-ce1"), 30), catalog))
        assertNotNull(RentalDurations.check(RentalSpec("loc-classe-ce1", listOf("classe-ce1"), 60), catalog))
        assertTrue(assertFailsWith<IllegalArgumentException> { RentalDurations.specsFor(catalog, listOf("nope")) }.message!!.contains("inconnu"))
        assertFailsWith<IllegalArgumentException> { RentalDurations.specsFor(catalog, emptyList()) }
    }

    @Test fun checkWantsTheExactDurationNeitherShorterNorLonger() {
        assertNull(RentalDurations.check(RentalSpec("loc-classe-cm2", listOf("classe-cm2"), 30), catalog))
        assertNotNull(RentalDurations.check(RentalSpec("loc-classe-cm2", listOf("classe-cm2"), 29), catalog))
        assertNotNull(RentalDurations.check(RentalSpec("loc-classe-cm2", listOf("classe-cm2"), 31), catalog))
        assertNotNull(RentalDurations.check(RentalSpec("x", listOf("classe-cm2", "classe-cp"), 30), catalog), "a spec on two bundles must agree with both")
    }
}
