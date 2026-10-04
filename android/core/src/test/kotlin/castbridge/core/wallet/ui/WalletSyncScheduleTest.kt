package castbridge.core.wallet.ui

import castbridge.core.wallet.ui.WalletSyncSchedule.Trigger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WalletSyncScheduleTest {
    private val s = WalletSyncSchedule()
    private val t = 1_790_000_000_000L
    private val min = 60_000L

    @Test fun periodIsFifteenMinutes() { assertEquals(15 * min, s.periodMs) }

    @Test fun syncsWhenTheScreenOpensIfOnline() {
        assertTrue(s.shouldSync(Trigger.OPEN, true, t, null, false))
        assertTrue(s.shouldSync(Trigger.OPEN, true, t, t - 10_000, false))
        assertFalse(s.shouldSync(Trigger.OPEN, false, t, null, false))
        assertFalse(s.shouldSync(Trigger.OPEN, true, t, null, true))                 // déjà en cours : pas de doublon
        assertFalse(s.shouldSync(Trigger.OPEN, true, t, t - 1_000, false))           // réouverture à la seconde : pas de rafale
    }

    @Test fun syncsAfterEachOperationWhateverTheLastSync() {
        assertTrue(s.shouldSync(Trigger.AFTER_OPERATION, true, t, t - 1, false))
        assertTrue(s.shouldSync(Trigger.AFTER_OPERATION, true, t, null, false))
        assertFalse(s.shouldSync(Trigger.AFTER_OPERATION, false, t, t - 99 * min, false))
        assertFalse(s.shouldSync(Trigger.AFTER_OPERATION, true, t, t - 99 * min, true))
    }

    @Test fun tickSyncsEveryFifteenMinutesOnlineOnly() {
        assertTrue(s.shouldSync(Trigger.TICK, true, t, null, false))
        assertFalse(s.shouldSync(Trigger.TICK, true, t, t - 14 * min, false))
        assertFalse(s.shouldSync(Trigger.TICK, true, t, t - (15 * min - 1), false))
        assertTrue(s.shouldSync(Trigger.TICK, true, t, t - 15 * min, false))
        assertFalse(s.shouldSync(Trigger.TICK, false, t, t - 60 * min, false))
        assertFalse(s.shouldSync(Trigger.TICK, true, t, t - 60 * min, true))
    }

    @Test fun aClockGoingBackDoesNotBlockTheNextTick() {
        assertTrue(s.shouldSync(Trigger.TICK, true, t, t + 5 * min, false))           // dernière synchro « dans le futur » : on resynchronise
    }
}
