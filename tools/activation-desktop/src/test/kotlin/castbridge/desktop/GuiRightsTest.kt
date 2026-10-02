package castbridge.desktop

import castbridge.core.lots.BundleCatalog
import castbridge.core.lots.Right
import castbridge.core.owner.IssueException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GuiRightsTest {
    private val now = 1_800_000_000_000L
    private val cat = BundleCatalog.parse("""{"bundles":[{"id":"classe-cm2","type":"classe","rentalDays":14,"lots":["learn:cm2"]},{"id":"quiz-cm2","type":"quiz","lots":["quiz:cm2"]}]}""")

    @Test fun productionDurationChoices() {
        assertNull(KeyDuration.production(KeyDuration.UNLIMITED, ""))
        assertEquals(62, KeyDuration.production("62", ""))
        assertEquals(400, KeyDuration.production(KeyDuration.OTHER, " 400 "))
        assertFailsWith<IssueException> { KeyDuration.production(KeyDuration.OTHER, "3661") }
        assertFailsWith<IssueException> { KeyDuration.production(KeyDuration.OTHER, "0") }
        assertFailsWith<IssueException> { KeyDuration.production(KeyDuration.OTHER, "abc") }
        assertFailsWith<IssueException> { KeyDuration.production("30", "", superKey = true) }
        assertNull(KeyDuration.production(KeyDuration.UNLIMITED, "", superKey = true))
    }

    @Test fun trialDurationIsNeverUnlimited() {
        assertEquals(30, KeyDuration.trial("30")); assertEquals(365, KeyDuration.trial("365"))
        assertFailsWith<IssueException> { KeyDuration.trial("366") }
        assertFailsWith<IssueException> { KeyDuration.trial("illimitée") }
    }

    @Test fun checklistBuildsRightsAndExactRentals() {
        val p = GuiRights.plan(listOf("classe-cm2"), mapOf("quiz-cm2" to "2027-06-30"), listOf("classe-cm2", "quiz-cm2"), "", cat, now)
        assertEquals(listOf("ach-classe-cm2", "abo-quiz-cm2"), p.rights.map { (it as? Right.Purchase)?.productId ?: (it as Right.Subscription).productId })
        assertEquals(listOf("loc-classe-cm2" to 14, "loc-quiz-cm2" to 30), p.rentals.map { it.productId to it.days })      // catalogue value, else the default of 30 days
        assertTrue(GuiRights.daysUntil("2027-06-30", now) in 100..200)
        assertFailsWith<IssueException> { GuiRights.daysUntil("2020-01-01", now) }
        assertFailsWith<IssueException> { GuiRights.daysUntil("pas-une-date", now) }
    }

    @Test fun typedLocationLineIsCheckedExactlyAndRefusedWithoutCatalogue() {
        assertEquals(14, GuiRights.plan(emptyList(), emptyMap(), emptyList(), "location x=classe-cm2:14", cat, now).rentals.single().days)
        assertTrue(assertFailsWith<IssueException> { GuiRights.plan(emptyList(), emptyMap(), emptyList(), "location x=classe-cm2:30", cat, now) }.message!!.contains("fixée par le serveur"))
        assertFailsWith<IssueException> { GuiRights.plan(emptyList(), emptyMap(), emptyList(), "location x=classe-cm2:13", cat, now) }
        assertTrue(assertFailsWith<IssueException> { GuiRights.plan(emptyList(), emptyMap(), emptyList(), "location x=classe-cm2:14", null, now) }.message!!.contains("chargez le catalogue du serveur"))
        assertFailsWith<IssueException> { GuiRights.plan(emptyList(), emptyMap(), listOf("classe-cm2"), "", null, now) }
        assertFailsWith<IssueException> { GuiRights.plan(emptyList(), emptyMap(), emptyList(), "rental|x|classe-cm2|1|2", cat, now) }
        assertEquals(1, GuiRights.plan(emptyList(), emptyMap(), emptyList(), "tout-ouvert p:10", null, now).rights.size)
    }
}
