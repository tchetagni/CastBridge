package castbridge.core

import castbridge.core.owner.KeyStatusJson
import castbridge.core.owner.TrialPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KeyStatusJsonTest {
    private val none = KeyStatusJson.fields(emptyList(), 1_000L, emptyList(), trial = false)

    @Test fun noKeyIsEndedAndUnlimitedDateIsNull() {
        assertTrue(none.contains("\"edition\":\"SANS CLÉ\""))
        assertTrue(none.contains("\"ended\":true"))
        assertTrue(none.contains("\"trial\":false"))
        assertTrue(none.contains("\"usageEndsAt\":null"))
        assertTrue(none.contains("\"trialWindow\":{\"state\":\"none\",\"minutesLeft\":0}"))
        assertTrue(!none.contains("restrictions"))
        assertEquals(null, KeyStatusJson.usageEndsAt(emptyList(), 0))
    }

    @Test fun trialCarriesTheRestrictionMessage() {
        val j = KeyStatusJson.fields(emptyList(), 1_000L, emptyList(), trial = true)
        assertTrue(j.contains("\"trial\":true"))
        assertTrue(j.contains("\"restrictions\":\"" + TrialPolicy.MESSAGE.replace("\"", "\\\"") + "\""))
    }
}
