package castbridge.core.games

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Le moteur de tours, avec un jeu de test sans cartes ([Nim]) et une horloge fournie par le test (millisecondes). */
class TurnEngineTest {
    private val s1 = PlayerId("s1"); private val s2 = PlayerId("s2"); private val s3 = PlayerId("s3")

    private fun nim(seed: Long = 0L, players: List<PlayerId> = listOf(s1, s2), clock: MoveClock? = null, ai: Set<PlayerId> = emptySet(), grace: Long = 60_000L, start: Long = 1_000L) =
        TurnEngine(Nim, players, seed, start, clock, ai, grace)

    @Test fun startsFromTheRulesAndTheSeed() {
        assertEquals(7, nim(seed = 0).state.pile); assertEquals(9, nim(seed = 2).state.pile); assertEquals(7, nim(seed = 5).state.pile, "même graine, même départ")
        assertEquals(listOf(s1), nim().toMove(), "la première place a la main")
        assertEquals(0, nim().moveNo); assertNull(nim().result); assertFalse(nim().over)
        assertEquals(listOf(s1, s2), nim().players)
        assertEquals(4L, nim(seed = 4).seed)
    }

    @Test fun refusesBadTables() {
        assertFailsWith<IllegalArgumentException> { nim(players = listOf(s1)) }
        assertFailsWith<IllegalArgumentException> { nim(players = List(5) { PlayerId.seat(it) }) }
        assertFailsWith<IllegalArgumentException> { nim(players = listOf(s1, s1)) }
        assertFailsWith<IllegalArgumentException> { nim(ai = setOf(PlayerId("zz"))) }
        assertFailsWith<IllegalArgumentException> { MoveClock(0) }
        assertFailsWith<IllegalArgumentException> { nim(grace = 0L) }
    }

    @Test fun movesAreNumberedAndLateOrDuplicatedOnesAreStale() {
        val e = nim()
        assertEquals(MoveVerdict.STALE, e.submit(Take(s1, 1), expectedNo = 3, nowMs = 1_100))
        assertEquals(MoveVerdict.OK, e.submit(Take(s1, 1), expectedNo = 0, nowMs = 1_200))
        assertEquals(1, e.moveNo)
        assertEquals(MoveVerdict.STALE, e.submit(Take(s1, 1), expectedNo = 0, nowMs = 1_300), "le même coup renvoyé n'est pas joué deux fois")
        assertEquals(6, e.state.pile, "rien n'a changé")
        assertEquals(MoveVerdict.OK, e.submit(Take(s2, 2), expectedNo = 1, nowMs = 1_400))
        assertEquals(MoveVerdict.OK, e.submit(Take(s1, 1), expectedNo = null, nowMs = 1_500), "sans numéro : pas de contrôle de retard (le serveur reste juge du tour)")
        assertEquals(3, e.moveNo)
    }

    @Test fun otherVerdictsChangeNothing() {
        val e = nim()
        assertEquals(MoveVerdict.NOT_YOUR_TURN, e.submit(Take(s2, 1), 0, 1_100))
        assertEquals(MoveVerdict.ILLEGAL, e.submit(Take(s1, 5), 0, 1_100))
        assertEquals(MoveVerdict.UNKNOWN_PLAYER, e.submit(Take(s3, 1), 0, 1_100))
        assertEquals(0, e.moveNo); assertEquals(7, e.state.pile); assertTrue(e.log.isEmpty())
        // l'ordre des contrôles : fini, puis retard, puis tour, puis légalité
        assertEquals(MoveVerdict.STALE, e.submit(Take(s2, 9), 7, 1_100))
        e.resign(s1, 1_200)
        assertEquals(MoveVerdict.OVER, e.submit(Take(s2, 1), 0, 1_300))
        assertEquals(MoveVerdict.OVER, e.submit(Take(s2, 1), 99, 1_300), "fini : on ne parle plus de retard")
    }

    @Test fun theRulesEndTheGame() {
        val e = nim(seed = 0)                       // 7 jetons
        var t = 1_000L
        val plan = listOf(s1 to 3, s2 to 3, s1 to 1)
        plan.forEachIndexed { i, (p, n) -> t += 100; assertEquals(MoveVerdict.OK, e.submit(Take(p, n), i, t)) }
        assertTrue(e.over)
        assertEquals(GameResult(EndReason.RULES, Outcome.Winners(listOf(s1))), e.result)
        assertEquals(emptyList(), e.toMove()); assertEquals(1_300L, e.endedAtMs)
        assertEquals(3, e.moveNo)
    }

    @Test fun resignationGivesTheGameToTheOthers() {
        val two = nim(); assertTrue(two.resign(s1, 2_000))
        assertEquals(GameResult(EndReason.RESIGNATION, Outcome.Winners(listOf(s2)), by = s1), two.result)
        assertFalse(two.resign(s2, 2_100), "déjà fini")
        assertEquals(EndReason.RESIGNATION, two.result?.reason)
        val three = nim(players = listOf(s1, s2, s3)); assertTrue(three.resign(s2, 2_000))
        assertEquals(Outcome.Winners(listOf(s1, s3)), three.result?.outcome, "à trois, tous les autres gagnent")
        assertFalse(nim().resign(PlayerId("zz"), 2_000), "inconnu")
        assertTrue(nim().resign(s2, 2_000), "un joueur peut abandonner même quand ce n'est pas son tour")
    }

    @Test fun theLogKeepsEveryMoveWithItsTimeSinceTheStart() {
        val e = nim(start = 10_000)
        e.submit(Take(s1, 2), 0, 10_500); e.submit(Take(s2, 3), 1, 12_250)
        assertEquals(listOf(Take(s1, 2), Take(s2, 3)), e.log.map { it.move })
        assertEquals(listOf(500L, 2_250L), e.log.map { it.atMs })
        assertEquals(listOf(false, false), e.log.map { it.auto })
        assertEquals(10_000L, e.startMs)
    }

    // ---------------------------------------------------------------- horloge

    private val perMove = MoveClock(30_000)

    @Test fun theClockCountsDownForThePlayerToMoveOnly() {
        val e = nim(clock = perMove, start = 0)
        assertEquals(30_000, e.remainingMs(s1, 0)); assertEquals(30_000, e.remainingMs(s2, 0), "l'autre garde son compte plein")
        assertEquals(12_000, e.remainingMs(s1, 18_000)); assertEquals(30_000, e.remainingMs(s2, 18_000))
        assertEquals(0, e.remainingMs(s1, 99_000), "jamais négatif")
        assertNull(nim().remainingMs(s1, 5), "pas de pendule, pas de temps restant")
    }

    @Test fun theClockRestartsAtEveryMove() {
        val e = nim(clock = perMove, start = 0)
        e.submit(Take(s1, 1), 0, 25_000)
        assertEquals(30_000, e.remainingMs(s2, 25_000)); assertEquals(20_000, e.remainingMs(s2, 35_000))
        assertFalse(e.tick(54_999)); assertNull(e.result)
    }

    @Test fun timeoutLosesTheGameAtTheDeadlineAndNotBefore() {
        val e = nim(clock = perMove, start = 0)
        assertFalse(e.tick(29_999))
        assertTrue(e.tick(30_000))
        assertEquals(GameResult(EndReason.TIMEOUT, Outcome.Winners(listOf(s2)), by = s1), e.result)
        assertFalse(e.tick(60_000), "plus rien à faire")
    }

    @Test fun aMoveArrivingAfterTheDeadlineIsRefusedBecauseTheHostClockDecides() {
        val e = nim(clock = perMove, start = 0)
        assertEquals(MoveVerdict.OVER, e.submit(Take(s1, 1), 0, 30_500))
        assertEquals(EndReason.TIMEOUT, e.result?.reason); assertEquals(0, e.moveNo)
    }

    @Test fun theFallbackPolicyPlaysForTheLatePlayerAndGoesOn() {
        val e = nim(clock = MoveClock(10_000, TimeoutPolicy.AUTO_MOVE), start = 0)
        assertTrue(e.tick(10_000))
        assertNull(e.result); assertEquals(1, e.moveNo)
        assertEquals(Take(s1, 1), e.log.single().move, "le premier coup légal"); assertTrue(e.log.single().auto)
        assertEquals(listOf(s2), e.toMove()); assertEquals(10_000, e.remainingMs(s2, 10_000), "la pendule repart")
        // un coup arrivé après l'échéance : joué d'office d'abord, le coup du client est alors en retard
        assertEquals(MoveVerdict.STALE, e.submit(Take(s2, 1), 1, 25_000))
        assertEquals(2, e.moveNo); assertTrue(e.log.last().auto)
    }

    @Test fun pauseFreezesTheClockAndNeverAddsTime() {
        val e = nim(clock = perMove, start = 0)
        assertTrue(e.pause(10_000)); assertTrue(e.paused)
        assertFalse(e.pause(11_000), "déjà en pause")
        assertFalse(e.tick(500_000), "pas de temps perdu pendant la pause")
        assertEquals(20_000, e.remainingMs(s1, 500_000))
        assertTrue(e.resume(500_000)); assertFalse(e.paused); assertFalse(e.resume(500_001))
        assertEquals(20_000, e.remainingMs(s1, 500_000), "repart où elle s'était arrêtée")
        assertTrue(e.tick(520_000)); assertEquals(EndReason.TIMEOUT, e.result?.reason)
    }

    // ---------------------------------------------------------------- déconnexion et reprise

    @Test fun aDisconnectedPlayerToMoveFreezesHisOwnClock() {
        val e = nim(clock = perMove, start = 0)
        e.setConnected(s1, false, nowMs = 10_000)
        assertFalse(e.connected(s1)); assertTrue(e.connected(s2))
        assertEquals(20_000, e.remainingMs(s1, 40_000), "son compte est arrêté")
        assertFalse(e.tick(40_000), "ni perte au temps ni forfait avant 60 s de silence")
        e.setConnected(s1, true, nowMs = 40_000)
        assertTrue(e.connected(s1))
        assertEquals(20_000, e.remainingMs(s1, 40_000), "reprise par jeton de place : le temps restant n'a pas bougé")
        assertEquals(15_000, e.remainingMs(s1, 45_000))
    }

    @Test fun sixtySecondsOfSilenceForfeitTheSeat() {
        val e = nim(start = 0)
        e.setConnected(s1, false, nowMs = 5_000)
        assertEquals(60_000, e.graceLeftMs(s1, 5_000)); assertEquals(25_000, e.graceLeftMs(s1, 40_000)); assertNull(e.graceLeftMs(s2, 40_000))
        assertFalse(e.tick(64_999))
        assertTrue(e.tick(65_000))
        assertEquals(GameResult(EndReason.DISCONNECTED, Outcome.Winners(listOf(s2)), by = s1), e.result)
    }

    @Test fun silenceIsCountedFromTheLastSignOfLifeNotFromWhenTheHostNoticed() {
        val e = nim(start = 0)
        e.setConnected(s1, false, nowMs = 40_000, silentSinceMs = 5_000)   // l'hôte s'en aperçoit 35 s plus tard (délai de présence)
        assertEquals(25_000, e.graceLeftMs(s1, 40_000))
        assertFalse(e.tick(64_999)); assertTrue(e.tick(65_000))
        assertEquals(EndReason.DISCONNECTED, e.result?.reason)
        // un « silence » annoncé dans le futur ne rallonge jamais le délai
        val f = nim(start = 0); f.setConnected(s1, false, nowMs = 1_000, silentSinceMs = 999_999)
        assertEquals(60_000, f.graceLeftMs(s1, 1_000))
    }

    @Test fun theSilenceIsGivenBackToThePlayerWhoHadTheMove() {
        val e = nim(clock = perMove, start = 0)
        e.setConnected(s1, false, nowMs = 25_000, silentSinceMs = 15_000)     // vu pour la dernière fois à 15 s, l'hôte s'en aperçoit à 25 s
        assertEquals(15_000, e.remainingMs(s1, 25_000), "15 s consommées avant son dernier signe de vie : les 10 s de silence lui sont rendues")
        assertEquals(15_000, e.remainingMs(s1, 80_000), "et la pendule est arrêtée")
        e.setConnected(s1, true, nowMs = 30_000)
        assertEquals(15_000, e.remainingMs(s1, 30_000)); assertEquals(10_000, e.remainingMs(s1, 35_000))
        // jamais plus que ce qu'il avait consommé : un silence annoncé plus long que le coup rend le compte plein
        val f = nim(clock = perMove, start = 0)
        f.setConnected(s1, false, nowMs = 10_000, silentSinceMs = 0)
        assertEquals(30_000, f.remainingMs(s1, 10_000))
        // celui qui n'a pas la main ne change rien à la pendule des autres
        val g = nim(clock = perMove, start = 0)
        g.setConnected(s2, false, nowMs = 25_000, silentSinceMs = 15_000)
        assertEquals(5_000, g.remainingMs(s1, 25_000))
    }

    @Test fun comingBackWithinTheGraceCancelsTheForfeit() {
        val e = nim(start = 0)
        e.setConnected(s1, false, 5_000); e.setConnected(s1, true, 64_000)
        assertFalse(e.tick(200_000), "revenu à temps : plus rien ne court"); assertNull(e.result)
        assertNull(e.graceLeftMs(s1, 200_000))
    }

    @Test fun comingBackJustTooLateIsTooLate() {
        val e = nim(start = 0)
        e.setConnected(s1, false, 5_000)
        e.setConnected(s1, true, 65_000)
        assertEquals(EndReason.DISCONNECTED, e.result?.reason, "le retard de la tâche périodique ne sauve personne")
        assertEquals(s1, e.result?.by)
    }

    @Test fun aDisconnectionOfSomeoneWhoDoesNotHaveTheMoveDoesNotStopTheOthersClock() {
        val e = nim(clock = perMove, start = 0)
        e.setConnected(s2, false, 1_000)
        assertEquals(20_000, e.remainingMs(s1, 10_000), "s1 continue de jouer contre la montre")
        assertTrue(e.tick(30_000)); assertEquals(GameResult(EndReason.TIMEOUT, Outcome.Winners(listOf(s2)), by = s1), e.result)
    }

    @Test fun aSilentSeatThatIsNotToMoveStillLosesAtSixtySeconds() {
        val e = nim(start = 0)
        e.submit(Take(s1, 1), 0, 100)               // la main passe à s2
        e.setConnected(s1, false, 1_000)             // s1 se tait pendant que s2 réfléchit
        assertTrue(e.tick(61_000)); assertEquals(s1, e.result?.by)
    }

    @Test fun whenEveryHumanIsGoneNobodyLosesTheGameIsAbandoned() {
        val e = nim(start = 0)
        e.setConnected(s1, false, 1_000); e.setConnected(s2, false, 2_000)
        assertTrue(e.tick(62_000))
        assertEquals(GameResult(EndReason.ABANDONED, Outcome.Draw), e.result, "tous partis : pas de perdant")
    }

    @Test fun theComputerNeverGoesSilentAndTheHumanAloneGoingAwayAbandonsTheGame() {
        val e = nim(ai = setOf(s2), start = 0)
        e.setConnected(s1, false, 1_000)
        assertTrue(e.tick(61_000)); assertEquals(GameResult(EndReason.ABANDONED, Outcome.Draw), e.result, "le seul humain parti : pas de perdant")
        val a = nim(ai = setOf(s2), start = 0)
        a.setConnected(s2, false, 1_000)
        assertTrue(a.connected(s2), "on ne signale jamais l'ordinateur absent : il est toujours là")
        assertFalse(a.tick(500_000)); assertNull(a.result)
        // deux humains et l'ordinateur : celui qui se tait perd, les deux autres gagnent
        val m = nim(players = listOf(s1, s2, s3), ai = setOf(s3), start = 0)
        m.setConnected(s2, false, 1_000)
        assertTrue(m.tick(61_000)); assertEquals(GameResult(EndReason.DISCONNECTED, Outcome.Winners(listOf(s1, s3)), by = s2), m.result)
    }

    @Test fun silenceOfSeveralSeatsForfeitsTheFirstInSeatOrderWhenSomeoneIsStillThere() {
        val e = nim(players = listOf(s1, s2, s3), start = 0)
        e.setConnected(s3, false, 1_000); e.setConnected(s2, false, 1_000)
        assertTrue(e.tick(61_000))
        assertEquals(s2, e.result?.by, "dans l'ordre des places, de façon déterministe")
    }

    // ---------------------------------------------------------------- fin

    @Test fun afterTheEndNothingChangesAnymore() {
        val e = nim(clock = perMove, start = 0)
        e.resign(s1, 1_000)
        val frozen = e.result
        e.setConnected(s2, false, 2_000); e.pause(2_000)
        assertFalse(e.tick(10_000_000)); assertEquals(frozen, e.result)
        assertFalse(e.pause(3_000))
        assertEquals(0, e.remainingMs(s1, 5_000), "plus de temps qui court")
        assertEquals(1_000L, e.endedAtMs)
    }

    @Test fun abandonEndsTheGameWithoutALoser() {
        val e = nim(); e.abandon(5_000)
        assertEquals(GameResult(EndReason.ABANDONED, Outcome.Draw), e.result)
        e.abandon(6_000); assertEquals(5_000L, e.endedAtMs, "une seule fin")
    }

    @Test fun aClockGoingBackwardsNeverAddsTime() {
        val e = nim(clock = perMove, start = 10_000)
        assertEquals(20_000, e.remainingMs(s1, 20_000))
        assertFalse(e.tick(15_000))
        assertTrue(e.remainingMs(s1, 15_000)!! <= 30_000, "jamais plus que le compte plein")
        assertEquals(30_000, nim(clock = perMove, start = 10_000).remainingMs(s1, 0), "un instant avant le début : le compte plein, jamais plus")
    }

    @Test fun theSameSeedReplaysTheSameGameWithAnotherEngine() {
        val a = TurnEngine(Hands, listOf(s1, s2), 77L, 0, null)
        val b = TurnEngine(Hands, listOf(s1, s2), 77L, 0, null)
        assertEquals(a.state, b.state)
        assertNotEquals(a.state, TurnEngine(Hands, listOf(s1, s2), 78L, 0, null).state)
        var t = 0L
        while (!a.over) { val p = a.toMove().single(); val m = Hands.legal(a.state, p).first(); t += 10; assertEquals(MoveVerdict.OK, a.submit(m, a.moveNo, t)); assertEquals(MoveVerdict.OK, b.submit(m, b.moveNo, t)) }
        assertEquals(a.state, b.state); assertEquals(a.result, b.result)
        assertNotNull(a.result); assertEquals(6, a.moveNo)
        assertTrue(a.result!!.outcome is Outcome.Scores)
    }
}
