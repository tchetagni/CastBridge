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

    // ---- R-46 : confirmé en production (280 refus en 2 h, 2 appels par minute) — la TV recommençait chaque minute quand le serveur refusait ----

    private fun refused(at: Long, n: Int = 1) = WalletSyncSchedule.Attempt(at, WalletSyncSchedule.Outcome.REFUSED, n)
    private fun unreachable(at: Long) = WalletSyncSchedule.Attempt(at, WalletSyncSchedule.Outcome.UNREACHABLE)
    private fun ok(at: Long) = WalletSyncSchedule.Attempt(at, WalletSyncSchedule.Outcome.OK)
    private fun tick(last: WalletSyncSchedule.Attempt?, now: Long, online: Boolean = true) = s.shouldSyncAfter(Trigger.TICK, online, now, last, false)

    @Test fun aServerRefusalIsNotRetriedAtTheNextTick() {
        // le tick part chaque minute : une TV non activée (409) reçoit un refus, et ne doit pas redemander 60 s plus tard
        val last = refused(t)
        assertFalse(tick(last, t + 60_000 - 1), "moins d'une minute après le refus")
        assertFalse(tick(last, t + 30_000), "même au tick d'après, 30 s plus tard")
        assertTrue(tick(last, t + 60_000), "une minute après un premier refus : une nouvelle tentative")
    }

    @Test fun refusalsBackOffExponentiallyUpToThePeriod() {
        val waits = (1..8).map { n -> s.tickWaitMs(refused(t, n)) / min }
        assertEquals(listOf(1L, 2L, 4L, 8L, 15L, 15L, 15L, 15L), waits, "1, 2, 4, 8 minutes puis la période (15 min) au plus")
        for (n in 1..8) assertFalse(tick(refused(t, n), t + s.tickWaitMs(refused(t, n)) - 1), "refus n°$n : pas une milliseconde avant")
        for (n in 1..8) assertTrue(tick(refused(t, n), t + s.tickWaitMs(refused(t, n))), "refus n°$n : à l'échéance")
    }

    @Test fun theBackoffNeverExceedsAShorterPeriod() {
        val short = WalletSyncSchedule(periodMs = 3 * min)
        assertEquals(3 * min, short.tickWaitMs(refused(t, 10)), "plafonné à periodMs, pas à 15 minutes")
        assertEquals(2 * min, short.tickWaitMs(unreachable(t)))
        assertEquals(min, WalletSyncSchedule(periodMs = min).tickWaitMs(unreachable(t)), "jamais plus que la période")
    }

    @Test fun anUnreachableServerIsNotRetriedBeforeTwoMinutes() {
        val last = unreachable(t)
        assertFalse(tick(last, t + 60_000), "au tick d'après : non")
        assertFalse(tick(last, t + 2 * min - 1))
        assertTrue(tick(last, t + 2 * min))
        assertEquals(2 * min, WalletSyncSchedule.UNREACHABLE_RETRY_MS)
    }

    @Test fun aSuccessKeepsTheFifteenMinutePeriodSinceTheLastAttempt() {
        assertFalse(tick(ok(t), t + 14 * min)); assertTrue(tick(ok(t), t + 15 * min))
        assertTrue(tick(null, t), "aucune tentative encore : tout de suite")
    }

    @Test fun refusalsStartOverAfterASuccessOrAnotherFailure() {
        var a: WalletSyncSchedule.Attempt? = null
        repeat(3) { a = s.after(a, t + it, WalletSyncSchedule.Outcome.REFUSED) }
        assertEquals(3, a!!.refusals)
        a = s.after(a, t + 10, WalletSyncSchedule.Outcome.UNREACHABLE); assertEquals(0, a!!.refusals, "un échec réseau interrompt la série")
        a = s.after(a, t + 11, WalletSyncSchedule.Outcome.REFUSED); assertEquals(1, a!!.refusals)
        a = s.after(a, t + 12, WalletSyncSchedule.Outcome.OK); assertEquals(0, a!!.refusals); assertEquals(15 * min, s.tickWaitMs(a!!))
    }

    @Test fun openingTheScreenAndAnOperationAreStillImmediateAfterARefusal() {
        val last = refused(t, 5)                                                       // un long repli en cours (15 min)
        assertTrue(s.shouldSyncAfter(Trigger.OPEN, true, t + 3_000, last, false), "l'utilisateur ouvre l'écran : tout de suite (débounce de 2 s)")
        assertFalse(s.shouldSyncAfter(Trigger.OPEN, true, t + 1_000, last, false), "pas de rafale si l'écran est rouvert à la seconde")
        assertTrue(s.shouldSyncAfter(Trigger.AFTER_OPERATION, true, t + 1, last, false), "après une opération : tout de suite")
        assertTrue(s.shouldSyncAfter(Trigger.OPEN, true, t + 3_000, unreachable(t), false))
    }

    @Test fun neverOfflineNeverTwiceAtOnceWhateverTheLastAttempt() {
        for (last in listOf(null, ok(t), refused(t), unreachable(t))) for (trigger in Trigger.values()) {
            assertFalse(s.shouldSyncAfter(trigger, false, t + 99 * min, last, false), "hors ligne : $trigger $last")
            assertFalse(s.shouldSyncAfter(trigger, true, t + 99 * min, last, true), "déjà en cours : $trigger $last")
        }
    }

    @Test fun aClockGoingBackDoesNotBlockTheTickAfterARefusalEither() {
        assertTrue(tick(refused(t + 5 * min, 4), t), "dernière tentative « dans le futur » : on réessaie, la nouvelle date remet l'horloge d'aplomb")
    }

    @Test fun theOutcomeComesFromTheAnswerOfTheServerNotOnlyFromItsSuccess() {
        val shown = castbridge.core.wallet.ui.WalletMessages.of(409, "NOT_ACTIVATED")
        assertEquals(WalletSyncSchedule.Outcome.OK, s.outcomeOf(WalletResult.Ok(Unit)))
        assertEquals(WalletSyncSchedule.Outcome.REFUSED, s.outcomeOf(WalletResult.Fail(shown, 409, "NOT_ACTIVATED", network = false)), "le serveur a répondu non")
        assertEquals(WalletSyncSchedule.Outcome.REFUSED, s.outcomeOf(WalletResult.Fail(shown, 500, null, network = false)), "une erreur du serveur n'est pas une panne de réseau : on ne martèle pas")
        assertEquals(WalletSyncSchedule.Outcome.UNREACHABLE, s.outcomeOf(WalletResult.Fail(shown, null, null, network = true)))
    }

    @Test fun theProductionStormIsOverTwoHoursOfRefusals() {
        // 2 h de ticks d'une minute face à un serveur qui refuse toujours : 280 refus avant, une douzaine de tentatives maintenant
        var last: WalletSyncSchedule.Attempt? = null; var attempts = 0
        var now = t
        repeat(120) {
            if (tick(last, now)) { attempts++; last = s.after(last, now, WalletSyncSchedule.Outcome.REFUSED) }
            now += min
        }
        assertTrue(attempts in 8..13, "tentatives en 2 h : $attempts")
    }
}
