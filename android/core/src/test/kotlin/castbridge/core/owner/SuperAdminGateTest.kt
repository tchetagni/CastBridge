package castbridge.core.owner

import at.favre.lib.crypto.bcrypt.BCrypt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SuperAdminGateTest {
    private var t = 1_000_000L
    private val pw = "une-phrase-de-passe-longue-pour-le-test".toCharArray()
    // cost 6 keeps the test fast; the app uses the owner's cost-12 hash (same algorithm, same checks)
    private val hash = BCrypt.withDefaults().hashToString(6, pw)
    private fun guard() = UnlockGuard({ t })
    private fun gate(g: UnlockGuard = guard(), h: String? = hash) = SuperAdminGate(h, g)

    @Test fun rightPasswordOpensWrongOneDoesNot() {
        val g = gate()
        assertIs<SuperAdminGate.Result.Wrong>(g.attempt("mauvais".toCharArray()))
        assertIs<SuperAdminGate.Result.Open>(g.attempt(pw))
    }

    @Test fun threeFreeTriesThenDelaysThenALock() {
        val g = guard(); val s = gate(g)
        repeat(3) { assertIs<SuperAdminGate.Result.Wrong>(s.attempt("x".toCharArray())) }   // the 3 free tries
        assertIs<SuperAdminGate.Result.Wrong>(s.attempt("x".toCharArray()))               // 4th is allowed but now costs a delay
        val locked = s.attempt(pw)
        assertIs<SuperAdminGate.Result.Locked>(locked, "even the right password waits while the delay runs")
        assertTrue(locked.waitMs in 1..5 * 60_000L)
        t += locked.waitMs + 1
        assertIs<SuperAdminGate.Result.Open>(s.attempt(pw), "after the delay the right password opens and resets the counter")
        assertEquals(0, g.state.failures)
        repeat(UnlockGuard.LOCK_AFTER) { t += 6 * 60_000L; s.attempt("x".toCharArray()) }
        val lock = s.attempt(pw)
        assertIs<SuperAdminGate.Result.Locked>(lock); assertTrue(lock.waitMs >= UnlockGuard.LOCK_MS - 6 * 60_000L, "temporary lock of about 30 minutes")
    }

    @Test fun failuresSurviveARestartBecauseTheStateIsPersisted() {
        val a = guard(); val s = gate(a); repeat(5) { s.attempt("x".toCharArray()) }
        val restarted = SuperAdminGate(hash, UnlockGuard({ t }, a.state))               // the phone stores guard.state next to the vault
        assertIs<SuperAdminGate.Result.Locked>(restarted.attempt(pw))
    }

    @Test fun noHashInTheBuildMeansTheEntryDoesNotExist() {
        for (h in listOf(null, "", "pas-un-hache", "\$2a\$12\$trop-court")) {
            val g = gate(h = h); assertFalse(g.enabled)
            assertIs<SuperAdminGate.Result.Disabled>(g.attempt(pw))
        }
    }

    @Test fun aVerifierThatThrowsIsAWrongPasswordNeverAnOpenDoor() {
        val g = SuperAdminGate(hash, guard()) { _, _ -> error("boom") }
        assertIs<SuperAdminGate.Result.Wrong>(g.attempt(pw))
    }

    @Test fun passwordLongerThan72BytesIsTruncatedLikeTheServerEncoder() {
        val long = ("a".repeat(80)).toCharArray()
        val h = BCrypt.with(BCrypt.Version.VERSION_2A, java.security.SecureRandom(), at.favre.lib.crypto.bcrypt.LongPasswordStrategies.truncate(BCrypt.Version.VERSION_2A)).hashToString(6, long)
        assertTrue(BcryptVerifier.verify(("a".repeat(72) + "ZZZZ").toCharArray(), h), "bytes after the 72nd do not count (bcrypt's own limit)")
    }

    @Test fun hiddenGestureNeedsSevenTapsThenALongPressInTime() {
        val s = TapSequence(taps = 7, windowMs = 2_500, longPressWithinMs = 3_000, now = { t })
        repeat(7) { s.tap(); t += 300 }
        assertTrue(s.longPress())
        repeat(6) { s.tap(); t += 300 }; assertFalse(s.longPress(), "six taps are not enough")
        repeat(7) { s.tap(); t += 300 }; t += 4_000; assertFalse(s.longPress(), "long press too late")
        repeat(4) { s.tap(); t += 300 }; t += 3_000; repeat(3) { s.tap(); t += 300 }; assertFalse(s.longPress(), "a pause longer than the window restarts the count")
        assertFalse(s.longPress(), "the counter is reset after every long press")
    }
}
