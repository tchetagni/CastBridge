package castbridge.core.owner

import kotlin.test.*

/** The absolute grace of the locked build (docs/coordination/AUDIT-PROJET-2026-10-02.md A1-1): never restarted, only for installs that predate the lock. */
class GraceMigrationTest {
    private val DAY = 24L * 3600 * 1000
    private val START = 1_790_899_200_000L                      // 2026-10-02 00:00 UTC = LOCK_GRACE_START_MS
    private val req = ActivationRequirement(true, graceDays = 30)
    private val noKey = TvAccess(false, castbridge.core.lots.Access.TRIAL_ONLY, null, "Aucune clé installée")
    private fun state(firstInstall: Long, now: Long) = FeatureGate.state(req, noKey, now, FleetMigration.of(firstInstall, START))

    @Test fun aPreLockInstallGetsGraceUntilTheAbsoluteDate() {
        val m = FleetMigration.of(START - 400 * DAY, START)
        assertEquals(START + 30 * DAY, m.graceUntil(req))
        assertEquals(START + 30 * DAY, assertIs<GateState.Grace>(state(START - 400 * DAY, START + 5 * DAY)).untilMs)
        assertIs<GateState.Locked>(state(START - 400 * DAY, START + 30 * DAY + 1))
    }
    @Test fun overInstallAfterTheGraceEndedStaysLocked() {
        // an over-install keeps PackageInfo.firstInstallTime: the date never moves, so the end is the same absolute date
        val first = START - 10 * DAY
        assertIs<GateState.Locked>(state(first, START + 31 * DAY))
        assertIs<GateState.Locked>(state(first, START + 400 * DAY))
    }
    @Test fun clearingDataCannotRestartIt() {
        // « clear data » drops every app file (first_run*, activations) but not firstInstallTime nor the absolute constants: same answer before and after
        val first = START - DAY
        val before = state(first, START + 20 * DAY); val after = state(first, START + 20 * DAY)
        assertEquals(before, after); assertIs<GateState.Locked>(state(first, START + 45 * DAY))
        assertEquals(START + 30 * DAY, FleetMigration.of(first, START).graceUntil(req), "the end does not depend on when the app first ran")
    }
    @Test fun aFreshInstallOrAReinstallAfterTheLockIsLockedAtOnce() {
        assertIs<GateState.Locked>(state(START, START))
        assertIs<GateState.Locked>(state(START + 3 * DAY, START + 4 * DAY))
        assertNull(FleetMigration.of(START + 1, START).graceUntil(req))
    }
    @Test fun noGraceWhenGraceDaysIsZeroAndAKeyAlwaysWins() {
        assertNull(FleetMigration.of(START - DAY, START).graceUntil(ActivationRequirement(true, 0)))
        val keyed = noKey.copy(keyInstalled = true)
        assertIs<GateState.Activated>(FeatureGate.state(req, keyed, START + 99 * DAY, FleetMigration.of(START + DAY, START)))
    }
}
