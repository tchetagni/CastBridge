package castbridge.desktop

import castbridge.core.owner.IssueException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class GuiRightsTest {
    private val now = 1_800_000_000_000L
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
}
