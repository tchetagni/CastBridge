package castbridge.core.trust

import castbridge.core.tv.Pin
import kotlin.test.*

/**
 * Saisie du code PIN directement dans la boite « Ouvrir avec CastBridge » : la machine d'états pure (l'écran ne fait que la brancher).
 * Aucun vrai code ici : des chiffres d'essai.
 */
class PinEntryStateTest {
    private val good = "123456"
    private val bad = "654321"

    private fun typed(code: String, s: PinEntryState = PinEntryState.Idle()) = PinEntry.input(s, code)

    @Test fun emptyInputStaysIdle() { assertTrue(typed("") is PinEntryState.Idle) }

    @Test fun onlyDigitsAreKeptAndLengthIsCapped() {
        val s = typed("12a3-4 5678") as PinEntryState.Entering
        assertEquals("123456", s.digits)      // Pin.LENGTH chiffres au plus
        assertEquals(Pin.LENGTH, s.digits.length)
    }

    @Test fun autoSubmitOnlyOnTheLastDigit() {
        assertFalse(PinEntry.readyToCheck(typed("12345")))
        assertTrue(PinEntry.readyToCheck(typed(good)))
    }

    @Test fun submitMovesToCheckingOnlyWithAWellFormedCode() {
        assertTrue(PinEntry.submit(typed("123")) is PinEntryState.Entering)       // incomplet : rien ne part
        assertTrue(PinEntry.submit(typed(good)) is PinEntryState.Checking)
        assertTrue(PinEntry.submit(PinEntryState.Idle()) is PinEntryState.Idle)
    }

    @Test fun inputIsIgnoredWhileCheckingOrAccepted() {
        val c = PinEntry.submit(typed(good))
        assertSame(c, PinEntry.input(c, "999999"))
        val a = PinEntry.result(c, PinVerdict.Ok)
        assertSame(a, PinEntry.input(a, "999999"))
    }

    @Test fun correctCodeIsAccepted() {
        assertTrue(PinEntry.result(PinEntry.submit(typed(good)), PinVerdict.Ok) is PinEntryState.Accepted)
    }

    @Test fun wrongCodeCountsDownTheAttemptsAndClearsTheField() {
        var s: PinEntryState = PinEntry.submit(typed(bad))
        s = PinEntry.result(s, PinVerdict.BadPin)
        assertEquals(PinEntry.MAX_ATTEMPTS - 1, (s as PinEntryState.Wrong).attemptsLeft)
        assertEquals("", PinEntry.digitsOf(s))                                        // champ vide
        assertTrue(PinEntry.message(s)!!.contains("incorrect"))
        // taper de nouveau garde le compte des essais
        s = PinEntry.result(PinEntry.submit(PinEntry.input(s, bad)), PinVerdict.BadPin)
        assertEquals(PinEntry.MAX_ATTEMPTS - 2, (s as PinEntryState.Wrong).attemptsLeft)
    }

    @Test fun lastAllowedWrongAttemptLocksAndNeverMoreAttempts() {
        var s: PinEntryState = PinEntryState.Idle()
        repeat(PinEntry.MAX_ATTEMPTS) { s = PinEntry.result(PinEntry.submit(PinEntry.input(s, bad)), PinVerdict.BadPin) }
        assertTrue(s is PinEntryState.Locked)
        // verrouillé : ni saisie ni envoi, même avec le bon code
        assertSame(s, PinEntry.input(s, good))
        assertSame(s, PinEntry.submit(s))
        assertFalse(PinEntry.readyToCheck(PinEntry.input(s, good)))
    }

    @Test fun tvLockoutIsFollowedAndTheMessageSaysTheDelay() {
        val s = PinEntry.result(PinEntry.submit(typed(bad)), PinVerdict.Locked(42))
        assertEquals(42L, (s as PinEntryState.Locked).seconds)
        assertTrue(PinEntry.message(s)!!.contains("42"))
        assertSame(s, PinEntry.submit(PinEntry.input(s, good)))
    }

    @Test fun lockedStartWhenTheTvIsAlreadyLocked() {
        assertTrue(PinEntry.start(30) is PinEntryState.Locked)
        assertTrue(PinEntry.start(null) is PinEntryState.Idle)
        assertTrue(PinEntry.start(0) is PinEntryState.Idle)
    }

    @Test fun lockEndsOnlyWhenTheTvSaysSo() {
        val locked = PinEntry.start(30)
        assertEquals(12L, (PinEntry.tick(locked, 12) as PinEntryState.Locked).seconds)
        val free = PinEntry.tick(locked, null)
        assertTrue(free is PinEntryState.Idle)
        // après un verrou, le compteur d'essais repart de zéro (la TV a remis son compteur)
        val s = PinEntry.result(PinEntry.submit(PinEntry.input(free, bad)), PinVerdict.BadPin)
        assertEquals(PinEntry.MAX_ATTEMPTS - 1, (s as PinEntryState.Wrong).attemptsLeft)
    }

    @Test fun unreachableTvIsNotAWrongCode() {
        val s = PinEntry.result(PinEntry.submit(typed(good)), PinVerdict.Unreachable)
        assertTrue(s is PinEntryState.Unreachable)
        assertTrue(PinEntry.message(s)!!.contains("TV"))
        // pas d'essai consommé : une nouvelle tentative est possible, le compte est intact
        val again = PinEntry.result(PinEntry.submit(PinEntry.input(s, bad)), PinVerdict.BadPin)
        assertEquals(PinEntry.MAX_ATTEMPTS - 1, (again as PinEntryState.Wrong).attemptsLeft)
    }

    @Test fun clearEmptiesTheTypedDigitsButKeepsTheAttemptCountAndTheLock() {
        val wrong = PinEntry.result(PinEntry.submit(typed(bad)), PinVerdict.BadPin)
        val cleared = PinEntry.clear(PinEntry.input(wrong, "12"))
        assertEquals("", PinEntry.digitsOf(cleared))
        val next = PinEntry.result(PinEntry.submit(PinEntry.input(cleared, good)), PinVerdict.BadPin)
        assertEquals(PinEntry.MAX_ATTEMPTS - 2, (next as PinEntryState.Wrong).attemptsLeft)
        // un verrou n'est pas levé par un effacement (rotation / arrière-plan)
        assertTrue(PinEntry.clear(PinEntry.start(30)) is PinEntryState.Locked)
    }

    @Test fun noStateNorMessageEverShowsTheCode() {
        val states = listOf(typed(good), PinEntry.submit(typed(good)), PinEntry.result(PinEntry.submit(typed(bad)), PinVerdict.BadPin),
            PinEntry.result(PinEntry.submit(typed(good)), PinVerdict.Ok), PinEntry.start(30), PinEntry.result(PinEntry.submit(typed(good)), PinVerdict.Unreachable))
        for (s in states) {
            assertFalse(s.toString().contains(good), s.javaClass.simpleName)
            assertFalse(s.toString().contains(bad), s.javaClass.simpleName)
            PinEntry.message(s)?.let { assertFalse(it.contains(good) || it.contains(bad)) }
        }
        assertTrue(typed(good).toString().contains("6"))      // la longueur seulement
    }

    @Test fun dialogShowsTheFieldOnlyForEnterPin() {
        assertTrue(PinEntry.showsField(SendAction.ENTER_PIN))
        for (a in listOf(SendAction.NONE, SendAction.ADD_TV, SendAction.OPEN_APP)) assertFalse(PinEntry.showsField(a))
    }

    @Test fun afterAcceptanceTheChoiceIsRecomputedWithCopyAndReadActive() {
        val before = SendChoices.decide(SendFacts(pinTvName = "CastBridge TV SMART_TV", pinStored = false))
        assertEquals(SendAction.ENTER_PIN, before.action); assertFalse(before.copyEnabled)
        val after = SendChoices.decide(SendFacts(pinTvName = "CastBridge TV SMART_TV", pinStored = true, pinCheck = PinCheck.OK))
        assertEquals(SendAction.NONE, after.action); assertTrue(after.copyEnabled); assertTrue(after.moveEnabled)
        // TV pas prête : le texte existant reste
        val notReady = SendChoices.decide(SendFacts(pinTvName = "CastBridge TV SMART_TV", pinStored = true, pinCheck = PinCheck.UNREACHABLE))
        assertTrue(notReady.status.contains("ne répond pas"))
    }
}
