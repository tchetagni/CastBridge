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

    // ---- W16-04: the pilot's chosen durations (checkChosen) ; `check` above stays EXACT for userChosen = 0 ----
    private val pilot = PilotParams(pilotStart = 1_000L, pilotEnd = 2_000L)
    private fun chosen(days: Int, usage: Int = 0, concurrent: Int = 3, grace: Int = 0, bundle: String = "classe-cm2", params: PilotParams = pilot) =
        RentalDurations.checkChosen(RentalSpec("loc-$bundle", listOf(bundle), days, usage, grace, concurrent), catalog, params)

    @Test fun checkChosenBoundsDaysByMaxDaysAndTheBundlesRentalDays() {
        assertNull(chosen(1)); assertNull(chosen(30))
        assertTrue(chosen(31)!!.contains("30")); assertNotNull(chosen(0))
        assertNull(chosen(30, bundle = "classe-ce1"))
        val short = BundleCatalog.parse("""{"bundles":[{"id":"b7","type":"classe","lots":["learn:x"],"rentalDays":7}]}""")
        assertNull(RentalDurations.checkChosen(RentalSpec("loc-b7", listOf("b7"), 7, 0, 0, 3), short, pilot))
        assertTrue(RentalDurations.checkChosen(RentalSpec("loc-b7", listOf("b7"), 8, 0, 0, 3), short, pilot)!!.contains("7"))
    }

    @Test fun checkChosenBoundsHoursAtNinetySixAndTheSafetyDays() {
        assertNull(chosen(14, usage = 60)); assertNull(chosen(30, usage = 96 * 60))
        assertTrue(chosen(30, usage = 97 * 60)!!.contains("96")); assertNotNull(chosen(30, usage = 90)); assertNotNull(chosen(30, usage = -60))
        assertNotNull(chosen(31, usage = 60)); assertNotNull(chosen(0, usage = 60))
    }

    @Test fun checkChosenRefusesSeveralBundlesGraceAndTooManyConcurrent() {
        assertNotNull(RentalDurations.checkChosen(RentalSpec("x", listOf("classe-cm2", "classe-cp"), 7, 0, 0, 3), catalog, pilot))
        assertNotNull(chosen(7, grace = 1)); assertNotNull(chosen(7, concurrent = 4)); assertNotNull(chosen(7, concurrent = 0))
        assertNotNull(RentalDurations.checkChosen(RentalSpec("loc-nope", listOf("nope"), 7, 0, 0, 3), catalog, pilot))
    }

    @Test fun checkChosenWithoutUserChoiceIsTheExactRule() {
        val off = pilot.copy(userChosen = false)
        assertNull(chosen(30, params = off, concurrent = 0)); assertNotNull(chosen(29, params = off)); assertNotNull(chosen(30, usage = 60, params = off))
        assertNull(RentalDurations.checkChosen(RentalSpec("loc-classe-cp", listOf("classe-cp"), 45), catalog, off))
    }
}
