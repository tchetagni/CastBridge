package castbridge.core.owner

import castbridge.core.lots.BundleCatalog
import castbridge.core.lots.Right
import kotlin.test.*

class ProductionFormTest {
    private val catalog = BundleCatalog.parse("""{"bundles":[
        {"id":"classe-cm2","type":"classe","lots":["learn:cm2"],"title":"CM2","rentalDays":30},
        {"id":"classe-cp","type":"classe","lots":["learn:cp"],"title":"CP","rentalDays":45},
        {"id":"classe-ce1","type":"classe","lots":["learn:ce1"],"title":"CE1"}]}""")
    private val now = 1_800_000_000_000L
    private val prod = ProductionForm(production = true, durationChoice = null, license = "LIC-1", catalog = catalog)

    @Test fun productionDefaultsToUnlimitedWithNoUsageRight() {
        assertNull(prod.build(now).usageDays)
        assertNull(ProductionForm.withProduction(ProductionForm(), true).durationChoice)
    }

    @Test fun presetsAndOtherAreBounded() {
        assertEquals(62, prod.copy(durationChoice = 62).build(now).usageDays)
        assertEquals(3660, prod.copy(durationChoice = ProductionForm.OTHER, otherDays = "3660").usageDays())
        assertFailsWith<IssueException> { prod.copy(durationChoice = ProductionForm.OTHER, otherDays = "3661").usageDays() }
        assertFailsWith<IssueException> { prod.copy(durationChoice = ProductionForm.OTHER, otherDays = "0").usageDays() }
        assertFailsWith<IssueException> { prod.copy(durationChoice = ProductionForm.OTHER, otherDays = "").usageDays() }
    }

    @Test fun aTrialAlwaysHasADuration() {
        val t = ProductionForm()
        assertEquals(30, t.usageDays())
        assertFalse(null in ProductionForm.options(false)); assertTrue(null in ProductionForm.options(true))
        assertFailsWith<IssueException> { t.copy(durationChoice = null).usageDays() }
        assertFailsWith<IssueException> { t.copy(durationChoice = ProductionForm.OTHER, otherDays = "366").usageDays() }
        assertEquals(30, ProductionForm.withProduction(prod, false).durationChoice)
    }

    @Test fun superUnlimitedForcesNoDuration() {
        val p = prod.copy(superUnlimited = true, durationChoice = 90).build(now)
        assertNull(p.usageDays); assertTrue(p.rights.any { it is Right.Super })
    }

    @Test fun rentalDurationIsTheServersAndPerBundle() {
        val p = prod.copy(rentalBundles = setOf("classe-cp", "classe-cm2")).build(now)
        assertEquals(listOf("loc-classe-cm2" to 30, "loc-classe-cp" to 45), p.rentals.map { it.productId to it.days })
        val d = prod.copy(rentalBundles = setOf("classe-ce1")).build(now)      // no duration in the catalogue: the owner's default, 30 days
        assertEquals(listOf("loc-classe-ce1" to 30), d.rentals.map { it.productId to it.days })
        assertTrue(ProductionForm.bundleLine(catalog.find("classe-ce1")!!, true).contains("30 jours (par défaut)"))
        assertTrue(ProductionForm.bundleLine(catalog.find("classe-cp")!!, true).contains("45 jours (fixé par le serveur)"))
        assertFailsWith<IssueException> { prod.copy(rentalBundles = setOf("inconnu")).build(now) }
    }

    @Test fun purchasesAndSubscriptionsComeFromTheCatalogue() {
        val p = prod.copy(purchaseBundles = setOf("classe-cp"), subscriptionBundles = setOf("classe-cm2"), subscriptionEnd = "2030-01-01").build(now)
        assertEquals(2, p.rights.size)
        assertFailsWith<IssueException> { prod.copy(purchaseBundles = setOf("nope")).build(now) }
        assertFailsWith<IssueException> { prod.copy(subscriptionBundles = setOf("classe-cm2"), subscriptionEnd = "demain").build(now) }
        assertFailsWith<IssueException> { prod.copy(subscriptionBundles = setOf("classe-cm2"), subscriptionEnd = "2001-01-01").build(now) }
    }

    @Test fun withoutCatalogueOnlyOpenAllOrSuperRemain() {
        val n = prod.copy(catalog = null)
        assertEquals(ProductionForm.NO_CATALOG, assertFailsWith<IssueException> { n.copy(purchaseBundles = setOf("a")).build(now) }.message)
        assertFailsWith<IssueException> { n.copy(rentalBundles = setOf("a")).build(now) }
        assertTrue(n.copy(openProduct = "tout", openDays = "7").build(now).rights.single() is Right.OpenAll)
    }
}
