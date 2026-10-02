package castbridge.core.owner

import castbridge.core.lots.ClockDoubt
import castbridge.core.lots.Right
import kotlin.test.*

private const val D = 24L * 3600 * 1000
private const val T1 = 1_800_000_000_000L

/** Audit A/B: a wall clock wound back never freezes the TV time; a trial without a usage right (compact key) ends after 30 days. */
class ClockRollbackTest {
    private var mono = 0L
    private fun clock() = TvClock(mono = { mono })

    private fun trial(vararg rights: Right, issuedAt: Long = T1, notBefore: Long = T1) =
        Activation(ActivationKind.TRIAL, Subject.TV, "k1", 1, "n", issuedAt, notBefore, notBefore + 2 * D, Activation.TRIAL_LICENSE, "", 0, emptyMap(), rights.toList(), "")
    private fun production(vararg rights: Right) =
        Activation(ActivationKind.PRODUCTION, Subject.TV, "k1", 1, "n", T1, T1, T1 + 2 * D, "lic-1", "seat", 0, emptyMap(), rights.toList(), "")

    @Test fun wallRolledBackTenDaysKeepsAdvancingWithMonotonicTime() {
        val c = clock(); c.observe(T1)
        val wallBack = T1 - 10 * D
        assertEquals(T1, c.now(wallBack))
        mono += 31 * D
        assertEquals(T1 + 31 * D, c.now(wallBack), "time goes forward during the boot, never frozen")
        c.observe(wallBack); assertEquals(T1 + 31 * D, c.lastSeen)
        mono += D; assertEquals(T1 + 32 * D, c.now(wallBack))
    }

    @Test fun normalRunningAndAheadJumpsBehaveAsBefore() {
        val c = clock(); c.observe(T1)
        mono += 5 * D
        assertEquals(T1 + 5 * D, c.now(T1 + 5 * D))
        assertEquals(T1, c.now(T1 + 5000 * D), "an AHEAD jump is capped (not believed)")
        assertEquals(T1, c.now(T1 - 30_000L), "a few seconds of jitter are not a rollback")
    }

    @Test fun usageCeilingIsReachedEvenWithTheWallClockWoundBack() {
        val a = trial(Right.Usage(T1, T1 + 30 * D))
        val c = clock(); c.observe(T1)
        val wallBack = T1 - 10 * D
        mono += 29 * D
        assertTrue(TvGate.evaluate(listOf(a), emptyList(), c.now(wallBack)).keyInstalled)
        mono += 2 * D                                                                // 31 days of monotonic time, wall still 10 days back
        assertFalse(TvGate.evaluate(listOf(a), emptyList(), c.now(wallBack)).keyInstalled, "ceiling reached")
        // a caller that kept a frozen time but reports the doubt gets the monotonic-advanced ceilings
        assertFalse(TvGate.evaluate(listOf(a), emptyList(), T1, clockDoubt = ClockDoubt.BEHIND, monotonicNowMs = c.monotonicNow()).keyInstalled)
        assertTrue(TvGate.evaluate(listOf(a), emptyList(), T1, clockDoubt = null, monotonicNowMs = c.monotonicNow()).keyInstalled, "the monotonic time is only used when the clock is in doubt")
    }

    @Test fun compactTrialKeyWithoutUsageEndsAfterThirtyDays() {
        val compact = trial(issuedAt = 0L, notBefore = T1)                             // a typed key: no rights, no issuedAt
        assertEquals(T1 + 30 * D, TvGate.implicitUsageEnd(compact))
        assertTrue(TvGate.evaluate(listOf(compact), emptyList(), T1 + 29 * D).keyInstalled)
        val after = TvGate.evaluate(listOf(compact), emptyList(), T1 + 30 * D + 1000)
        assertFalse(after.keyInstalled); assertEquals("Activation terminée", after.label)
    }

    @Test fun trialWithExplicitUsageKeepsItAndProductionWithoutUsageIsUnlimited() {
        val longer = trial(Right.Usage(T1, T1 + 90 * D))
        assertNull(TvGate.implicitUsageEnd(longer))
        assertTrue(TvGate.evaluate(listOf(longer), emptyList(), T1 + 60 * D).keyInstalled, "the explicit 90 days win over the 30-day default")
        val prod = production(Right.Purchase("p-cm2", listOf("cm2"), T1))
        assertNull(TvGate.implicitUsageEnd(prod))
        assertTrue(TvGate.evaluate(listOf(prod), emptyList(), T1 + 3000 * D).keyInstalled)
    }

    @Test fun badgeShowsTheImplicitEnd() {
        val zone = java.time.ZoneId.of("UTC")
        val compact = trial(issuedAt = 0L, notBefore = T1)
        val b = KeyBadge.of(listOf(compact), T1 + D, zone = zone)
        assertTrue(b.lines.first().startsWith("Clé valable jusqu'au"), b.text)
        assertTrue(KeyBadge.of(listOf(compact), T1 + 31 * D, zone = zone).ended)
    }
}
