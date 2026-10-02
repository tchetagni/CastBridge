package castbridge.core.owner

import castbridge.core.lots.Right
import kotlin.test.*

class ProductionFormTest {
    private val now = 1_800_000_000_000L
    private val prod = ProductionForm(production = true, durationChoice = null)

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

    @Test fun productionHasNoContentRightEvenWithSuper() {
        assertTrue(prod.build(now).rights.isEmpty())
        assertTrue(prod.copy(durationChoice = 90).build(now).rights.isEmpty())
        assertEquals(listOf("super"), prod.copy(superUnlimited = true).build(now).rights.map { (it as Right.Super).let { "super" } })
        // the trial form never carries the super right
        assertTrue(ProductionForm(superUnlimited = true).build(now).rights.isEmpty())
    }
}
