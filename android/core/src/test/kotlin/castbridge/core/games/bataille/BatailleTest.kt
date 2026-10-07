package castbridge.core.games.bataille

import castbridge.core.games.EndReason
import castbridge.core.games.GameJournal
import castbridge.core.games.MoveVerdict
import castbridge.core.games.Outcome
import castbridge.core.games.PlayerId
import castbridge.core.games.TurnEngine
import castbridge.core.games.cards.Card
import castbridge.core.quiz.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Les règles de la Bataille (démonstration) : règles publiques et universelles, deux joueurs, un paquet de 52 cartes, l'as est la plus forte. */
class BatailleTest {
    private val s1 = PlayerId("s1"); private val s2 = PlayerId("s2")
    private val table = listOf(s1, s2)
    private fun c(code: String) = Card.parse(code)!!
    private fun cards(vararg codes: String) = codes.map(::c)

    /** Un état de départ à soi : les deux piles (la carte du dessus d'abord), rien sur la table. */
    private fun state(p1: List<Card>, p2: List<Card>, flips: Int = 0): BatailleState =
        Bataille.initial(1L, table).copy(piles = listOf(p1, p2), flips = flips)

    private fun flip(s: BatailleState): BatailleState = Bataille.apply(s, Flip(Bataille.nextToFlip(s)!!))
    private fun play(s: BatailleState, n: Int): BatailleState { var x = s; repeat(n) { x = flip(x) }; return x }

    // ---------------------------------------------------------------- départ

    @Test fun theDealComesFromTheSeedAndMatchesTheIndependentVector() {
        val s = Bataille.initial(42L, table)
        assertEquals(26, s.piles[0].size); assertEquals(26, s.piles[1].size)
        assertEquals((s.piles[0] + s.piles[1]).toSet().size, 52, "52 cartes différentes")
        assertEquals(listOf("6H", "QC", "2S", "6S", "JH"), s.piles[0].take(5).map { it.code })
        assertEquals(listOf("QS", "3H", "7H", "9S", "2D"), s.piles[1].take(5).map { it.code })
        assertEquals(Bataille.initial(42L, table), s, "même graine, même partie")
        assertNotEquals(Bataille.initial(43L, table), s)
        assertEquals(Outcome.InProgress, Bataille.outcome(s))
        assertTrue(s.table.isEmpty() && s.rounds == 0 && s.flips == 0 && s.last == null)
    }

    @Test fun identityOfTheGame() {
        assertEquals("bataille", Bataille.id); assertEquals(1, Bataille.version)
        assertEquals(2, Bataille.minPlayers); assertEquals(2, Bataille.maxPlayers)
        assertEquals("Bataille (démonstration)", Bataille.NAME, "clairement nommée comme une démonstration")
        assertFailsWith<IllegalArgumentException> { Bataille.initial(1, listOf(s1)) }
        assertFailsWith<IllegalArgumentException> { Bataille.initial(1, listOf(s1, s2, PlayerId("s3"))) }
        assertFailsWith<IllegalArgumentException> { Bataille.initial(1, listOf(s1, s1)) }
    }

    // ---------------------------------------------------------------- une levée

    @Test fun playersFlipOneAfterTheOther() {
        val s = Bataille.initial(42L, table)
        assertEquals(listOf(Flip(s1)), Bataille.legal(s, s1)); assertTrue(Bataille.legal(s, s2).isEmpty())
        val a = flip(s)
        assertEquals(listOf(Flip(s2)), Bataille.legal(a, s2)); assertTrue(Bataille.legal(a, s1).isEmpty())
        assertEquals(listOf(Placed(s1, c("6H"), true)), a.table, "la première carte est sur la table, face visible")
        assertEquals(25, a.piles[0].size)
        assertFailsWith<IllegalArgumentException> { Bataille.apply(s, Flip(s2)) }
        assertFailsWith<IllegalArgumentException> { Bataille.apply(a, Flip(s1)) }
    }

    @Test fun theHigherCardWinsAndTakesBothCardsUnderItsPile() {
        val s = play(Bataille.initial(42L, table), 2)           // 6♥ contre D♠ : la dame de pique gagne
        assertEquals(1, s.rounds); assertTrue(s.table.isEmpty())
        assertEquals(25, s.piles[0].size); assertEquals(27, s.piles[1].size)
        assertEquals(setOf("6H", "QS"), s.piles[1].takeLast(2).map { it.code }.toSet(), "les cartes de la levée passent sous le paquet du gagnant")
        assertEquals(s, play(Bataille.initial(42L, table), 2), "l'ordre de rangement vient de la graine : même partie, même ordre")
        assertEquals(RoundSummary(s2, 2, false, listOf(Placed(s1, c("6H"), true), Placed(s2, c("QS"), true))), s.last)
        assertEquals(listOf(Flip(s1)), Bataille.legal(s, s1), "c'est de nouveau au premier joueur")
    }

    @Test fun theAceBeatsTheKingAndSuitsNeverCount() {
        val s = play(state(cards("AH", "2C"), cards("KS", "3D")), 2)
        assertEquals(s1, s.last!!.winner)
        val t = play(state(cards("2C", "9D"), cards("3S", "4H")), 2)
        assertEquals(s2, t.last!!.winner, "un 3 bat un 2, quelles que soient les couleurs")
    }

    // ---------------------------------------------------------------- la bataille

    @Test fun equalValuesMakeABattleWhereEachPutsOneCardDownThenOneUp() {
        val s0 = state(cards("7H", "2C", "9D", "KS", "5C"), cards("7S", "3C", "4D", "AS", "6D"))
        val a = play(s0, 2)
        assertEquals(1, a.battles); assertEquals(0, a.rounds, "la levée n'est pas finie")
        assertEquals(listOf(Placed(s1, c("7H"), true), Placed(s2, c("7S"), true)), a.table)
        assertEquals(listOf(Step(s1, false), Step(s2, false), Step(s1, true), Step(s2, true)), a.plan, "face cachée chacun d'abord, puis face visible")
        val b = play(a, 3)                                      // 2♣ et 3♣ cachées, 9♦ visible, le second joueur n'a pas encore tourné la sienne
        assertEquals(listOf(false, false, true), b.table.drop(2).map { it.faceUp })
        assertEquals(Step(s2, true), Bataille.nextStep(b))
        val done = flip(b)                                      // 4♦ : le 9 l'emporte
        assertEquals(1, done.rounds); assertTrue(done.table.isEmpty())
        assertEquals(listOf("KS", "5C"), done.piles[0].take(2).map { it.code }, "ce que le gagnant avait déjà reste au-dessus")
        assertEquals(setOf("7H", "7S", "2C", "3C", "9D", "4D"), done.piles[0].drop(2).map { it.code }.toSet(), "le gagnant garde tout : les 6 cartes de la table passent sous son paquet")
        assertEquals(8, done.piles[0].size)
        assertEquals(listOf("AS", "6D"), done.piles[1].map { it.code }, "l'autre n'a plus que ses deux dernières cartes")
        assertEquals(RoundSummary(s1, 6, true, listOf(Placed(s1, c("7H"), true), Placed(s2, c("7S"), true), Placed(s1, c("9D"), true), Placed(s2, c("4D"), true))), done.last,
            "on voit les cartes visibles ; les cartes cachées ne sont jamais montrées")
        assertTrue(done.last!!.shown.none { !it.faceUp })
    }

    @Test fun aBattleCanBeTiedAgain() {
        val s0 = state(cards("7H", "2C", "9D", "KS", "5C", "8H", "3D"), cards("7S", "3C", "9S", "AS", "4D", "4H", "2H"))
        val a = play(s0, 6)                                     // 7/7, puis 9/9 : nouvelle bataille
        assertEquals(2, a.battles); assertEquals(0, a.rounds)
        assertEquals(listOf(Step(s1, false), Step(s2, false), Step(s1, true), Step(s2, true)), a.plan)
        assertEquals(6, a.table.size)
        val done = play(a, 4)                                   // R♠ et A♠ cachés, 5♣ contre 4♦ : 5 > 4
        assertEquals(s1, done.last!!.winner); assertEquals(10, done.last!!.won)
        assertEquals(1, done.rounds); assertEquals(2, done.battles)
    }

    @Test fun aPlayerWithOneCardLeftPutsItUpWithoutHidingOne() {
        val s0 = state(cards("7H", "9C"), cards("7S", "3C", "4D"))
        val a = play(s0, 2)
        assertEquals(listOf(Step(s2, false), Step(s1, true), Step(s2, true)), a.plan, "le premier joueur n'a qu'une carte : elle est sa carte visible, il n'en cache pas")
        val done = play(a, 3)                                   // 3♣ cachée par le second, 9♣ contre 4♦
        assertEquals(s1, done.last!!.winner); assertTrue(Bataille.outcome(done) is Outcome.Winners)
        assertEquals(s1, (Bataille.outcome(done) as Outcome.Winners).winners.single(), "le perdant n'a plus de carte : partie finie")
    }

    @Test fun aPlayerWhoCannotContinueABattleLosesTheGame() {
        val s0 = state(cards("7H"), cards("7S", "2C", "3C"))
        val a = play(s0, 2)                                     // égalité, mais le premier joueur n'a plus de carte pour la bataille
        assertEquals(Outcome.Winners(listOf(s2)), Bataille.outcome(a))
        assertTrue(Bataille.legal(a, s1).isEmpty() && Bataille.legal(a, s2).isEmpty())
    }

    @Test fun theGameEndsWhenAPlayerHasNoCardLeft() {
        val s = play(state(cards("AS", "2C"), cards("2D")), 2)
        assertEquals(Outcome.Winners(listOf(s1)), Bataille.outcome(s))
        assertEquals(3, s.piles[0].size); assertEquals(0, s.piles[1].size)
        assertTrue(Bataille.legal(s, s1).isEmpty(), "plus aucun coup")
        assertFailsWith<IllegalArgumentException> { Bataille.apply(s, Flip(s1)) }
    }

    @Test fun aGameThatDoesNotEndIsStoppedAtTheLengthLimitAndWonByTheLargerPile() {
        // la levée qui atteint la limite de retournements termine la partie : le plus gros paquet gagne, à égalité c'est nul
        val more = play(state(cards("AS", "2C", "3C"), cards("2D", "4D"), flips = Bataille.MAX_FLIPS - 2), 2)
        assertEquals(Outcome.Winners(listOf(s1)), Bataille.outcome(more), "4 cartes contre 1")
        val equal = play(state(cards("AS"), cards("2D", "3D", "4D"), flips = Bataille.MAX_FLIPS - 2), 2)
        assertEquals(2, equal.piles[0].size); assertEquals(2, equal.piles[1].size)
        assertEquals(Outcome.Draw, Bataille.outcome(equal), "2 cartes contre 2")
        val before = play(state(cards("AS", "2C", "3C"), cards("2D", "4D"), flips = Bataille.MAX_FLIPS - 4), 2)
        assertEquals(Outcome.InProgress, Bataille.outcome(before), "sous la limite la partie continue")
    }

    @Test fun theLimitNeverCutsABattleInTheMiddle() {
        val s = play(state(cards("7H", "2C", "9D"), cards("7S", "3C", "4D"), flips = Bataille.MAX_FLIPS - 2), 2)
        assertEquals(Outcome.InProgress, Bataille.outcome(s), "au milieu d'une bataille : on la finit")
    }

    // ---------------------------------------------------------------- parties entières

    private fun cardsIn(s: BatailleState) = s.piles.flatten() + s.table.map { it.card }

    @Test fun everyFullGameEndsAndNoCardIsEverLostOrDuplicated() {
        for (seed in listOf(1L, 2L, 3L, 42L, 2026L, -7L, 123456789L, 99L)) {
            var s = Bataille.initial(seed, table)
            var n = 0
            while (Bataille.outcome(s) == Outcome.InProgress) {
                s = flip(s); n++
                assertEquals(52, cardsIn(s).size, "seed $seed coup $n : 52 cartes en tout")
                if (n % 50 == 0) assertEquals(52, cardsIn(s).toSet().size)
                assertTrue(n <= Bataille.MAX_FLIPS + 60, "seed $seed : la partie finit (coup $n)")
            }
            val o = Bataille.outcome(s)
            assertTrue(o is Outcome.Winners || o == Outcome.Draw, "seed $seed : $o")
            if (o is Outcome.Winners && s.piles.any { it.isEmpty() }) assertEquals(s.piles.indexOfFirst { it.isNotEmpty() }.let { table[it] }, o.winners.single(), "le gagnant a toutes les cartes")
            assertTrue(Bataille.legal(s, s1).isEmpty() && Bataille.legal(s, s2).isEmpty())
        }
    }

    @Test fun fullGamesMatchTheIndependentPythonImplementation() {
        // (retournements, levées, batailles, cartes du joueur 1, cartes du joueur 2, gagnant) : calculés par une réécriture indépendante des règles (bataille.py du chantier)
        val expected = mapOf(
            42L to listOf(204, 86, 8, 46, 6, 1), 7L to listOf(200, 88, 6, 20, 32, 2), 2026L to listOf(200, 92, 4, 32, 20, 1), -7L to listOf(200, 84, 8, 12, 40, 2), 1L to listOf(200, 84, 8, 10, 42, 2))
        for ((seed, want) in expected) {
            var s = Bataille.initial(seed, table)
            while (Bataille.outcome(s) == Outcome.InProgress) s = flip(s)
            val winner = (Bataille.outcome(s) as Outcome.Winners).winners.single().id.removePrefix("s").toInt()
            assertEquals(want, listOf(s.flips, s.rounds, s.battles, s.piles[0].size, s.piles[1].size, winner), "graine $seed")
        }
    }

    // ---------------------------------------------------------------- vue : jamais le contenu des paquets

    @Test fun viewsShowCountsAndRevealedCardsOnlyNeverThePilesContent() {
        var s = Bataille.initial(42L, table)
        for (n in 0 until 40) {
            val secret = s.piles.flatten().map { it.code }.toSet() - s.table.map { it.card.code }.toSet() - (s.last?.shown?.map { it.card.code } ?: emptyList()).toSet()
            for (viewer in listOf(s1, s2, null)) {
                val text = Json.write(Bataille.view(s, viewer))
                assertFalse(text.contains("seed") || text.contains(s.seed.toString()), "la graine ne sort jamais")
                for (code in secret) assertFalse(text.contains("\"$code\""), "coup $n : la carte $code est dans un paquet, personne ne doit la voir (vue de $viewer)")
            }
            if (Bataille.outcome(s) != Outcome.InProgress) break
            s = flip(s)
        }
        val v = Bataille.view(Bataille.initial(42L, table), s1)
        @Suppress("UNCHECKED_CAST") assertEquals(mapOf("s1" to 26, "s2" to 26), v["piles"] as Map<String, Int>)
        assertEquals(Bataille.view(s, s1), Bataille.view(s, s2), "rien de caché à distinguer entre les joueurs")
    }

    @Test fun theViewTellsWhoFlipsNextAndShowsFaceDownCardsAsBacks() {
        val a = play(state(cards("7H", "2C", "9D"), cards("7S", "3C", "4D")), 3)       // égalité puis la carte cachée du premier joueur
        @Suppress("UNCHECKED_CAST") val v = Bataille.view(a, null)
        assertEquals(mapOf("player" to "s2", "up" to false), v["next"])
        @Suppress("UNCHECKED_CAST") val shown = v["table"] as List<Map<String, Any?>>
        assertEquals(listOf("7H", "7S", null), shown.map { it["card"] }, "la carte cachée n'est pas montrée")
        assertEquals(listOf(true, true, false), shown.map { it["up"] })
        assertEquals(1, v["battles"]); assertEquals(0, v["rounds"])
        assertNull(Bataille.view(Bataille.initial(1L, table), null)["last"])
    }

    // ---------------------------------------------------------------- sérialisation

    @Test fun stateAndMovesRoundTripAtEveryStageOfAGame() {
        var s = Bataille.initial(2026L, table)
        var checked = 0
        while (Bataille.outcome(s) == Outcome.InProgress && checked < 260) {
            if (checked % 3 == 0 || s.table.isNotEmpty()) assertEquals(s, Bataille.decodeState(Bataille.encodeState(s)), "coup $checked")
            s = flip(s); checked++
        }
        assertEquals(s, Bataille.decodeState(Bataille.encodeState(s)), "état final")
        // au milieu d'une bataille, avec les cartes cachées
        val b = play(state(cards("7H", "2C", "9D"), cards("7S", "3C", "4D")), 3)
        assertEquals(b, Bataille.decodeState(Bataille.encodeState(b)))
        assertEquals("flip", Bataille.encodeMove(Flip(s1))); assertEquals(Flip(s2), Bataille.decodeMove("flip", s2))
        for (bad in listOf("", "Flip", "flip ", "flip:1", "play:AS")) assertNull(Bataille.decodeMove(bad, s1), "« $bad »")
        for (bad in listOf("", "{}", "[]", """{"players":["s1","s2"]}""", """{"players":["s1"],"piles":[[]],"table":[],"plan":[],"flips":0,"rounds":0,"battles":0,"last":null,"ended":null}""")) assertFailsWith<IllegalArgumentException>("« $bad »") { Bataille.decodeState(bad) }
    }

    // ---------------------------------------------------------------- avec le moteur de tours : journal rejouable

    @Test fun aFullGameThroughTheEngineYieldsAJournalThatReplays() {
        val e = TurnEngine(Bataille, table, 7L, 0, null)
        var t = 0L
        while (!e.over) { val p = e.toMove().single(); t += 40; assertEquals(MoveVerdict.OK, e.submit(Bataille.legal(e.state, p).single(), e.moveNo, t)) }
        assertEquals(EndReason.RULES, e.result!!.reason)
        val j = GameJournal.of("00112233445566778899aabbccddeeff", "0123456789abcdef", e, 1_790_000_000_000L, 1_790_000_000_000L + t)
        assertNull(GameJournal.impossible(j, Bataille), "la graine et les coups suffisent à rejouer la partie")
        assertTrue(j.entries.all { it.move == "flip" } && j.entries.size == e.moveNo)
        assertTrue(j.entries.size <= GameJournal.MAX_ENTRIES, "un journal de Bataille tient dans la borne des journaux : ${j.entries.size}")
    }
}
