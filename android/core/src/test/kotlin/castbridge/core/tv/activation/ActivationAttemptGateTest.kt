package castbridge.core.tv.activation

import castbridge.core.tv.PinGuard
import kotlin.test.*

/**
 * The one gate every online attempt at the connection code goes through, whatever the way in (HTTP route, Bluetooth channel without pairing): per-peer lock (5 wrong codes ⇒ 60 s), global cap
 * (20 wrong codes in 10 minutes ⇒ closed for everybody), allowances per peer, bounded tables. And the three methods [PinGuard] gained for a code proven by a PAKE instead of compared.
 */
class ActivationAttemptGateTest {
    private var now = 1_000_000L
    private fun guard() = PinGuard("482913", now = { now })
    private fun gate(g: PinGuard = guard()) = ActivationAttemptGate(g, { now })

    // ------------------------------------------------------------------ PinGuard: isLocked, recordFailure, recordSuccess

    @Test fun recordFailureCountsLikeAWrongPinAndLocksAtTheFifth() {
        val g = guard()
        assertFalse(g.isLocked("bt:A"))
        repeat(4) { assertEquals(PinGuard.Result.BAD, g.recordFailure("bt:A"), "échec ${it + 1}") }
        assertFalse(g.isLocked("bt:A"))
        assertEquals(PinGuard.Result.LOCKED, g.recordFailure("bt:A")); assertTrue(g.isLocked("bt:A"))
        assertEquals(60L, g.retryAfterSeconds("bt:A"))
        assertEquals(PinGuard.Result.LOCKED, g.recordFailure("bt:A"), "pendant le verrou, un échec de plus reste verrouillé")
        assertFalse(g.isLocked("bt:B"), "un autre pair n'est pas touché")
        now += 59_999; assertTrue(g.isLocked("bt:A"))
        now += 2; assertFalse(g.isLocked("bt:A"), "levé après 60 s")
        assertEquals(PinGuard.Result.BAD, g.recordFailure("bt:A"), "le compteur est reparti de zéro")
    }

    @Test fun recordFailureAndCheckShareTheSameCounter() {
        val g = guard()
        repeat(2) { assertEquals(PinGuard.Result.BAD, g.check("bt:A", "000000")) }
        repeat(2) { assertEquals(PinGuard.Result.BAD, g.recordFailure("bt:A")) }
        assertEquals(PinGuard.Result.LOCKED, g.check("bt:A", "000000"), "2 par la comparaison + 2 par la preuve + 1 : le cinquième verrouille")
        assertEquals(PinGuard.Result.LOCKED, g.check("bt:A", "482913"), "le bon code ne lève pas le verrou")
    }

    @Test fun recordSuccessForgetsTheFailuresButNeverALockThatIsRunning() {
        val g = guard()
        repeat(4) { g.recordFailure("bt:A") }
        g.recordSuccess("bt:A")
        repeat(4) { assertEquals(PinGuard.Result.BAD, g.recordFailure("bt:A")) }
        repeat(5) { g.recordFailure("bt:B") }
        assertTrue(g.isLocked("bt:B"))
        g.recordSuccess("bt:B")
        assertTrue(g.isLocked("bt:B"), "le bon code ne lève pas un verrou en cours")
        g.recordSuccess("bt:never-seen")                                                          // no entry, no effect
        assertFalse(g.isLocked("bt:never-seen"))
    }

    @Test fun rotatingTheCodeForgetsTheLocksOfTheProofsToo() {
        val g = guard(); repeat(5) { g.recordFailure("bt:A") }
        assertTrue(g.isLocked("bt:A")); g.rotate("111111"); assertFalse(g.isLocked("bt:A"))
    }

    // ------------------------------------------------------------------ the gate

    @Test fun aRefusalCountsNothingAndAskedTwiceSaysTheSame() {
        val gt = gate()
        assertNull(gt.refusal("bt:A"))
        repeat(5) { gt.recordWrong("bt:A") }
        val r = assertIs<ActivationAttemptGate.Refusal.Locked>(gt.refusal("bt:A"))
        assertEquals(60L, r.seconds)
        repeat(100) { gt.refusal("bt:A") }
        assertNull(gt.refusal("bt:B"), "cent refus d'un pair verrouillé n'ont rien compté pour les autres")
        assertFalse(gt.globalClosed())
    }

    @Test fun theFifthWrongCodeIsToldLockedAtOnce() {
        val gt = gate()
        repeat(4) { assertNull(gt.recordWrong("bt:A"), "essai ${it + 1}") }
        assertEquals(ActivationAttemptGate.Refusal.Locked(60), gt.recordWrong("bt:A"))
    }

    @Test fun twentyWrongCodesFromAnyPeersCloseEverythingAndTheWindowSlides() {
        val gt = gate()
        for (i in 0 until 20) gt.recordWrong("bt:P${i % 10}")           // 10 peers × 2: nobody locked
        assertTrue(gt.globalClosed())
        assertEquals(ActivationAttemptGate.Refusal.Closed, gt.refusal("bt:NEW"))
        now += ActivationAttemptGate.WINDOW_MS; assertTrue(gt.globalClosed(), "exactement à la limite : encore fermé")
        now += 1; assertFalse(gt.globalClosed()); assertNull(gt.refusal("bt:NEW"))
    }

    @Test fun theGlobalWindowIsSlidingNotFixed() {
        val gt = gate()
        repeat(10) { gt.recordWrong("bt:P$it") }                               // at t
        now += 6 * 60_000
        repeat(9) { gt.recordWrong("bt:Q$it") }                                // at t + 6 min: 19 in the last 10 min
        assertFalse(gt.globalClosed())
        gt.recordWrong("bt:R"); assertTrue(gt.globalClosed(), "la vingtième ferme")
        now += 4 * 60_000 + 1                                                  // the first 10 slide out
        assertFalse(gt.globalClosed(), "10 restent dans la fenêtre : rouvert")
    }

    @Test fun readsAndTriesAreCountedApartPerPeerAndPerWindow() {
        val gt = gate()
        repeat(ActivationAttemptGate.MAX_READS) { assertTrue(gt.takeRead("bt:A")) }
        assertFalse(gt.takeRead("bt:A")); assertTrue(gt.takeTry("bt:A"), "les lectures n'usent pas les vérifications")
        repeat(ActivationAttemptGate.MAX_TRIES - 1) { assertTrue(gt.takeTry("bt:A")) }
        assertFalse(gt.takeTry("bt:A")); assertTrue(gt.takeRead("bt:B"), "un autre pair a ses propres plafonds")
        now += ActivationAttemptGate.WINDOW_MS + 1
        assertTrue(gt.takeRead("bt:A")); assertTrue(gt.takeTry("bt:A"))
        assertEquals(20, ActivationAttemptGate.MAX_READS); assertEquals(10, ActivationAttemptGate.MAX_TRIES)
        assertEquals(20, ActivationAttemptGate.GLOBAL_MAX_WRONG); assertEquals(600_000L, ActivationAttemptGate.WINDOW_MS)
    }

    @Test fun theTablesAreBoundedAndTheLeastRecentlySeenIsForgottenFirst() {
        val gt = gate()
        for (i in 0 until ActivationAttemptGate.MAX_ADDRESSES + 500) { gt.takeTry("bt:$i"); gt.takeRead("bt:$i") }
        assertEquals(ActivationAttemptGate.MAX_ADDRESSES, gt.trackedAddresses()); assertEquals(ActivationAttemptGate.MAX_ADDRESSES, gt.trackedReaders())
    }

    @Test fun theRightCodeForgetsTheFailuresOfThatPeerOnly() {
        val gt = gate()
        repeat(4) { gt.recordWrong("bt:A"); gt.recordWrong("bt:B") }
        gt.recordRight("bt:A")
        assertNull(gt.recordWrong("bt:A"), "A est reparti de zéro")
        assertEquals(ActivationAttemptGate.Refusal.Locked(60), gt.recordWrong("bt:B"), "B avait 4 échecs : le suivant (le cinquième) le verrouille")
    }

    @Test fun aBluetoothPeerKeyIsTheUpperCaseAddressUnderAPrefixAndNeverAnIp() {
        assertEquals("bt:AA:BB:CC:DD:EE:FF", ActivationAttemptGate.bluetoothPeer("aa:bb:cc:dd:ee:ff"))
        assertEquals("bt:AA:BB:CC:DD:EE:FF", ActivationAttemptGate.bluetoothPeer(" AA:BB:CC:DD:EE:FF "))
        assertEquals("bt:?", ActivationAttemptGate.bluetoothPeer(null)); assertEquals("bt:?", ActivationAttemptGate.bluetoothPeer(""))
        assertFalse(castbridge.core.ssh.Lan.isLocal("bt:AA:BB:CC:DD:EE:FF"))
    }

    @Test fun theHttpRouteStillUsesTheSameNumbersThroughTheGate() {
        assertEquals(ActivationAttemptGate.MAX_TRIES, LockedActivationApi.MAX_TRIES); assertEquals(ActivationAttemptGate.MAX_READS, LockedActivationApi.MAX_READS)
        assertEquals(ActivationAttemptGate.WINDOW_MS, LockedActivationApi.WINDOW_MS); assertEquals(ActivationAttemptGate.GLOBAL_MAX_WRONG, LockedActivationApi.GLOBAL_MAX_WRONG)
        assertEquals(ActivationAttemptGate.MAX_ADDRESSES, LockedActivationApi.MAX_ADDRESSES)
    }
}
