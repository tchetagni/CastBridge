package castbridge.core.wallet.millions

import castbridge.core.wallet.millions.MillionsGame.State
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MillionsGameTest {
    private fun started(): MillionsGame = MK.game().also { assertTrue(it.start()) }
    private fun restore(text: String, packId: String = MK.PACK_ID, lv: Long = 1) = MillionsGame.restore(text, packId, lv, MillionsLadder.DEFAULT, 30, { MK.NOW }, { 0L })

    @Test fun withdrawRefusedAtEveryNonStop() {
        for (k in listOf(1, 2, 3, 4, 6, 7, 9, 11, 12, 14)) {
            val g = started(); MK.correct(g, 1, k)
            assertEquals(State.Asking(k + 1), g.state, "après $k")
            assertFalse(g.withdraw(), "retrait refusé après la question $k")
            assertEquals(State.Asking(k + 1), g.state)
        }
    }

    @Test fun withdrawAllowedRightAfterCorrectAtStops() {
        for (k in listOf(5, 8, 10, 13)) {
            val g = started()
            MK.correct(g, 1, k)
            assertEquals(State.AtStop(k), g.state)
            assertTrue(g.withdraw())
            assertEquals(State.Withdrawn(k, MillionsLadder.DEFAULT.gainAfter(k)), g.state)
            assertEquals(MillionsLadder.DEFAULT.gainAfter(k), g.gain)
            assertEquals(MillionsJournal.End(MillionsJournal.Kind.WITHDRAW, k), g.end)
        }
    }

    @Test fun cannotWithdrawAfterContinuingPastStop() {
        val g = started(); MK.correct(g, 1, 5)
        assertTrue(g.continueGame())
        assertEquals(State.Asking(6), g.state); assertFalse(g.withdraw())
    }

    @Test fun wrongAnswerGivesZeroNotLastStop() {
        val g = started(); MK.correct(g, 1, 9)       // a passé les paliers 5 et 8 sans retrait
        val q = MK.question(10, 0); g.show(q); g.answer((q.correct + 1) % 4, 2000)
        assertEquals(State.Lost(10, false), g.state)
        assertEquals(0L, g.gain)
        assertEquals(MillionsJournal.End(MillionsJournal.Kind.WRONG, 10), g.end)
    }

    @Test fun fiftyOnlyOnceAndDesignatedByPack() {
        val g = started(); val q = MK.question(1, 0)
        assertNull(g.fifty(), "pas de question affichée")
        g.show(q)
        assertEquals(q.fifty, g.fifty())
        assertNull(g.fifty())
        g.answer(q.correct, 1000)
        val q2 = MK.question(2, 0); g.show(q2)
        assertNull(g.fifty(), "une seule fois par partie")
        assertTrue(g.entries[0].fifty)
    }

    @Test fun timeoutIsLost() {
        val g = started(); val q = MK.question(1, 0); g.show(q)
        assertEquals(State.Lost(1, true), g.answer(q.correct, 30_001))
        assertEquals(MillionsJournal.End(MillionsJournal.Kind.TIMEOUT, 1), g.end)
        val h = started(); h.show(q)
        assertEquals(State.Asking(2), h.answer(q.correct, 30_000))
    }

    @Test fun timeLimitComesFromPackValue() {
        val g = MK.game(MK.pack(timeSec = 10)).also { it.start() }; val q = MK.question(1, 0); g.show(q)
        assertEquals(State.Lost(1, true), g.answer(q.correct, 10_001))
    }

    @Test fun winningAll15() {
        val g = started()
        MK.correct(g, 1, 15)
        assertEquals(State.Won, g.state); assertEquals(10_000L, g.gain)
        assertEquals(MillionsJournal.End(MillionsJournal.Kind.WON, 0), g.end)
        assertFalse(g.withdraw()); assertFalse(g.forfeit())
    }

    @Test fun forfeitGivesZeroOnlyWhileAsking() {
        val g = started(); MK.correct(g, 1, 2)
        assertTrue(g.forfeit()); assertEquals(State.Forfeit(3), g.state); assertEquals(0L, g.gain)
        assertEquals(MillionsJournal.End(MillionsJournal.Kind.FORFEIT, 3), g.end)
        val h = started(); MK.correct(h, 1, 5)
        assertEquals(State.AtStop(5), h.state); assertFalse(h.forfeit(), "au palier : emporter ou continuer")
    }

    @Test fun orderOfCalls() {
        val g = MK.game()
        assertFalse(g.show(MK.question(1, 0)), "pas commencée")
        assertTrue(g.start()); assertFalse(g.start())
        assertFailsWith<IllegalStateException> { g.answer(0, 1000) }
        g.show(MK.question(1, 0)); assertFalse(g.show(MK.question(1, 1)), "pas d'autre question tant que celle-ci n'a pas reçu de réponse")
        assertTrue(g.show(MK.question(1, 0)), "la même question peut être réaffichée")
        assertFailsWith<IllegalArgumentException> { g.answer(4, 1000) }
        assertFailsWith<IllegalArgumentException> { g.answer(0, -1) }
        assertFailsWith<IllegalArgumentException> { g.answer(-1, 1000) }   // « pas de réponse » seulement hors délai
        assertEquals(State.Lost(1, true), g.answer(-1, 31_000))
    }

    @Test fun continueOnlyAtStop() {
        val g = started(); assertFalse(g.continueGame())
        MK.correct(g, 1, 5)
        assertTrue(g.continueGame()); assertEquals(State.Asking(6), g.state)
    }

    @Test fun snapshotRestoreResumesSameQuestion() {
        val g = started(); MK.correct(g, 1, 6)
        val q = MK.question(7, 0); g.show(q); val removed = g.fifty()!!
        val r = assertNotNull(restore(g.snapshot()))
        assertEquals(g.state, r.state); assertEquals(g.entries, r.entries)
        assertFalse(r.show(MK.question(7, 1)), "même question imposée après la reprise")
        assertTrue(r.show(q)); assertNull(r.fifty(), "le joker déjà utilisé le reste")
        assertEquals(removed, r.removedChoices)
        r.answer(q.correct, 3000); g.answer(q.correct, 3000)
        assertEquals(g.snapshot(), r.snapshot())
    }

    @Test fun restoredGameProducesSameJournalAsUninterrupted() {
        val a = started(); MK.correct(a, 1, 8); a.withdraw()
        val b = started(); MK.correct(b, 1, 3)
        val r = restore(b.snapshot())!!
        MK.correct(r, 4, 8); r.withdraw()
        assertEquals(a.toJournal(MK.install.keyId), r.toJournal(MK.install.keyId))
    }

    @Test fun restoreRefusesGarbageAndInconsistentStates() {
        val g = started(); MK.correct(g, 1, 2); val text = g.snapshot()
        assertNotNull(restore(text))
        for (bad in listOf("", "{}", "garbage", text.replace("\"v\":1", "\"v\":2"), text.dropLast(1), text.replace("A", "W").replace("ASKING", "WON"), text.replace(MK.GAME_ID, "zz"), text.replace(",", ", "))) {
            if (bad == text) continue
            assertNull(restore(bad), bad.take(40))
        }
        assertNull(restore(text, packId = "ffffffffffffffffffffffffffffffff"), "autre pack")
        assertNull(restore(text, lv = 2), "autre version d'échelle")
    }

    @Test fun restoreRefusesStateWithoutMatchingHistory() {
        val g = started(); MK.correct(g, 1, 2); val text = g.snapshot()
        // état « question 3 » mais historique réduit à une réponse : refusé
        val short = started(); MK.correct(short, 1, 1)
        val forged = text.replace(Regex("\"e\":\\[.*?\\]\\]"), Regex("\"e\":\\[.*?\\]\\]").find(short.snapshot())!!.value)
        assertNull(restore(forged))
    }

    @Test fun finishedGameIgnoresEverything() {
        val g = started(); val q = MK.question(1, 0); g.show(q); g.answer((q.correct + 1) % 4, 1000)
        assertFalse(g.show(q)); assertNull(g.fifty()); assertFalse(g.withdraw()); assertFalse(g.continueGame()); assertFalse(g.forfeit())
        assertFailsWith<IllegalStateException> { g.answer(q.correct, 1000) }
        assertEquals(1, g.entries.size)
    }

    @Test fun recordsTvTimesAndEntries() {
        var t = 1_000L
        val g = MK.game(t = { t }); g.start(); t = 5_000
        val q = MK.question(1, 0); g.show(q); g.answer((q.correct + 1) % 4, 4000)
        val j = g.toJournal(MK.install.keyId)
        assertEquals(1_000L, j.t0); assertEquals(5_000L, j.t1)
        assertEquals(listOf(MillionsJournal.Entry(q.qid, (q.correct + 1) % 4, false, 4000)), j.entries)
        assertFailsWith<IllegalStateException> { started().toJournal(MK.install.keyId) }
    }

    @Test fun elapsedHelperUsesMonotonicClock() {
        var m = 0L; val g = MK.game(mono = { m }); g.start(); g.show(MK.question(1, 0)); m = 2_500
        assertEquals(2_500L, g.elapsedSinceShown())
    }

    @Test fun stakeIsInPlayFromStart() { val g = MK.game(); assertEquals(0L, g.stakeInPlay); g.start(); assertEquals(500L, g.stakeInPlay) }
}
