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
        assertEquals(T1 + 5 * D, c.now(T1 + 5000 * D), "an AHEAD jump is not believed: the time only advances with the monotonic clock")
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

    // ---- w1-05 : uptime cumulé persisté, règle AHEAD ----

    @Test fun clockFileV2RoundTripAndOldFormatStillRead() {
        val c = clock(); c.observe(T1); mono += 3 * D; c.observe(T1 + 3 * D)
        assertEquals("${T1 + 3 * D} 0 ${3 * D}", c.encode())
        val d = TvClock.decode(c.encode())!!
        assertEquals(c.lastSeen, d.lastSeen); assertEquals(c.floor, d.floor); assertEquals(c.uptimeMs, d.uptimeMs)
        val old = TvClock.decode("$T1 77")!!
        assertEquals(T1, old.lastSeen); assertEquals(77L, old.floor); assertEquals(0L, old.uptimeMs)
        assertNull(TvClock.decode("")); assertNull(TvClock.decode("a b")); assertNull(TvClock.decode("1 2 3 4")); assertNull(TvClock.decode("1 2 x"))
    }

    @Test fun rollbackAcrossARebootKeepsCountingUptimeAndTime() {
        val wallBack = T1 - 10 * D
        var m = 0L
        val first = TvClock(mono = { m }); first.observe(T1)
        m += 10 * 60_000L; first.observe(wallBack)                                    // 10 minutes of running, wall clock wound back
        val text = first.encode()
        // reboot: a NEW instance decoded from the text, the monotonic source restarts at 0, the wall clock is still wound back
        var m2 = 0L
        val second = TvClock.decode(text) { m2 }!!
        assertEquals(10 * 60_000L, second.uptimeNow())
        assertEquals(T1 + 10 * 60_000L, second.lastSeen.coerceAtLeast(second.now(wallBack)).coerceAtMost(T1 + 10 * 60_000L), "never rejuvenates")
        assertTrue(second.now(wallBack) >= T1, "the restart does not freeze nor rejuvenate")
        m2 += 31 * D; second.observe(wallBack)
        assertEquals(10 * 60_000L + 31 * D, second.uptimeNow(), "cumulative uptime continues across the reboot")
        // and a trial with a 30-day usage right is over by uptime even though the clock never moved
        val a = trial(Right.Usage(T1, T1 + 30 * D))
        assertFalse(TvGate.evaluate(listOf(a), emptyList(), T1, uptimeNowMs = second.uptimeNow()).keyInstalled)
        assertTrue(TvGate.evaluate(listOf(a), emptyList(), T1, uptimeNowMs = 29 * D).keyInstalled)
        assertTrue(TvGate.evaluate(listOf(a), emptyList(), T1, uptimeNowMs = null).keyInstalled, "no uptime given: only the date is judged (old callers)")
    }

    @Test fun repeatedRebootsWithAWoundBackClockStillConsumeTheTrial() {
        val a = trial(Right.Usage(T1, T1 + 30 * D))
        var saved = clock().also { it.observe(T1) }.encode()
        val wallBack = T1 - 10 * D
        var ended = false
        for (boot in 1..40) {                                                          // 40 boots of one day each, wall clock stuck 10 days back
            var m = 0L
            val c = TvClock.decode(saved) { m }!!
            m += D; c.observe(wallBack); saved = c.encode()
            val access = TvGate.evaluate(listOf(a), emptyList(), c.now(wallBack), uptimeNowMs = c.uptimeNow())
            if (!access.keyInstalled) { ended = true; assertTrue(boot in 30..31, "ended at boot $boot"); break }
        }
        assertTrue(ended)
    }

    @Test fun uptimeCeilingCountsFromTheInstall() {
        val a = trial(Right.Usage(T1, T1 + 30 * D))
        val ceilingFrom = { _: Activation -> 100 * D }
        assertTrue(TvGate.evaluate(listOf(a), emptyList(), T1, uptimeNowMs = 120 * D, uptimeAtInstall = ceilingFrom).keyInstalled, "20 days since the install")
        assertFalse(TvGate.evaluate(listOf(a), emptyList(), T1, uptimeNowMs = 131 * D, uptimeAtInstall = ceilingFrom).keyInstalled, "31 days since the install")
        val compact = trial(issuedAt = 0L, notBefore = T1)                             // implicit 30 days apply to the running time too
        assertFalse(TvGate.evaluate(listOf(compact), emptyList(), T1, uptimeNowMs = 31 * D).keyInstalled)
        val prod = production(Right.Purchase("p-cm2", listOf("cm2"), T1))              // production without usage: unlimited
        assertTrue(TvGate.evaluate(listOf(prod), emptyList(), T1, uptimeNowMs = 5000 * D).keyInstalled)
    }

    @Test fun aheadJumpOfSixtyAndFourHundredDaysSuspendsInsteadOfBurningTheTrial() {
        val a = trial(Right.Usage(T1, T1 + 30 * D))
        for (jump in listOf(60L, 400L, 3000L)) {
            val c = clock(); c.observe(T1); mono += 60_000L
            val wall = T1 + jump * D
            assertTrue(c.isAhead(wall), "+$jump days")
            val judged = castbridge.core.lots.RentalEngine.judge(c, wall)
            assertEquals(ClockDoubt.AHEAD, judged.doubt)
            val acc = TvGate.evaluate(listOf(a), emptyList(), c.now(wall), clockDoubt = judged.doubt, monotonicNowMs = c.monotonicNow(), uptimeNowMs = c.uptimeNow())
            assertTrue(acc.keyInstalled, "+$jump days: the trial is not burnt"); assertTrue(acc.suspended); assertEquals("Vérifiez l'heure de la TV", acc.label)
            // even a caller that passes the far-ahead time itself does not burn the ceiling
            val naive = TvGate.evaluate(listOf(a), emptyList(), wall, clockDoubt = ClockDoubt.AHEAD, monotonicNowMs = c.monotonicNow())
            assertTrue(naive.keyInstalled && naive.suspended)
        }
    }

    @Test fun aheadJumpOfThirtyDaysIsBelieved() {
        val c = clock(); c.observe(T1); mono += 60_000L
        val wall = T1 + 30 * D
        assertFalse(c.isAhead(wall)); assertEquals(wall, c.now(wall))
        assertNull(castbridge.core.lots.RentalEngine.judge(c, wall).doubt)
        val a = trial(Right.Usage(T1, T1 + 20 * D))
        val acc = TvGate.evaluate(listOf(a), emptyList(), c.now(wall), clockDoubt = null, monotonicNowMs = c.monotonicNow())
        assertFalse(acc.keyInstalled, "a believed +30 days ends a 20-day ceiling"); assertFalse(acc.suspended)
        assertEquals(TvClock.AHEAD_MAX_MS, castbridge.core.lots.RentalConfig().aheadDoubtMs, "same threshold as the rentals")
    }

    @Test fun aheadIsNotRememberedAndAConfirmedOrSignedJumpIsAccepted() {
        val c = clock(); c.observe(T1); mono += 60_000L
        val wall = T1 + 60 * D
        c.observe(wall); assertEquals(T1 + 60_000L, c.lastSeen, "the doubted reading is not the new high-water mark")
        assertTrue(c.isAhead(wall))
        assertTrue(c.confirmAhead(wall)); assertEquals(wall, c.lastSeen); assertFalse(c.isAhead(wall))
        assertFalse(c.confirmAhead(wall - 100 * D), "a clock behind is never confirmed")
        val d = clock(); d.observe(T1); mono += 60_000L
        d.observe(wall, signedIssuedAt = wall - 1000)                                  // a signed message proves the time has reached it
        assertFalse(d.isAhead(wall)); assertTrue(d.lastSeen >= wall - 1000)
    }
}
