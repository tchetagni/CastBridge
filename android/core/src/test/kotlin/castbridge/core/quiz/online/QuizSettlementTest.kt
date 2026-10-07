package castbridge.core.quiz.online

import castbridge.core.wallet.PlayResult
import castbridge.core.wallet.WalletCurrency
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Le règlement PUR d'un Quiz misé (games-G5) : les lignes `[eid, id, utilisé, versé]` que le service signe. Rien ne se crée, rien ne se perd (Σ versé = Σ utilisé), une interruption rend tout, personne n'a marqué
 * rend à chacun sa mise utilisée, le partage est celui de `Pot.split` par SIÈGE puis agrégé par blocage. Les cas de la salle (`ServerRoomStakeTest`) le jouent de bout en bout ; ici les chiffres et la conservation.
 */
class QuizSettlementTest {
    private fun esc(i: Int, k: Int, per: Long) = QuizEscrow("Eid${"%02d".format(i)}".padEnd(22, 'x'), "ID${"%02d".format(i)}-AAAA-BBBB-CCCC", k, per * k)

    @Test fun theResultIdIsDeterministicPerRoomAndLooksLikeAHexIdentifier() {
        assertEquals(QuizSettlement.ridOf("room-1"), QuizSettlement.ridOf("room-1"), "un seul résultat possible par salle : l'idempotence du règlement")
        assertTrue(QuizSettlement.ridOf("room-1") != QuizSettlement.ridOf("room-2"))
        assertTrue(Regex("[0-9a-f]{32}").matches(QuizSettlement.ridOf("room-1")), "128 bits en hexadécimal")
        assertTrue(QuizSettlement.ridOf("room-1") != castbridge.core.chess.online.ChessSettlement.ridOf("room-1"), "le préfixe de domaine sépare le Quiz des échecs : une même salle n'aurait pas le même résultat")
    }

    @Test fun twoTvsShareTheirPotByRankingAndEachTvGetsTheSumOfItsSeats() {
        val a = esc(1, k = 2, per = 20); val b = esc(2, k = 2, per = 20)
        val stakers = linkedMapOf(a.eid to listOf("a1", "a2"), b.eid to listOf("b1"))
        val res = QuizSettlement.result("kid", "room", WalletCurrency.NDEM, 20, 5_000, listOf(a, b), stakers, mapOf("a1" to 500, "a2" to 0, "b1" to 300))
        assertEquals(PlayResult.Kind.END, res.kind); assertEquals("quiz", res.game); assertEquals(QuizSettlement.ridOf("room"), res.rid)
        assertEquals(listOf(40L, 20L), res.lines.map { it.used }, "A : deux sièges (40) ; B : un siège sur deux bloqués (20, le reste est rendu par l'API)")
        assertEquals(listOf(42L, 18L), res.lines.map { it.pay }, "deux joueurs ont marqué : 70 % / 30 % de 60, a2 (0 point) ne reçoit rien, la part de A est celle de a1")
        assertTrue(res.check())
        assertEquals(listOf(2L, -2L), res.lines.map { QuizSettlement.net(it, aborted = false) })
    }

    @Test fun threeScoringSeatsShareSixtyThirtyTenAndATieSharesItsPlaces() {
        val e = (1..3).map { esc(it, k = 1, per = 10) }
        val stakers = e.associate { it.eid to listOf("p${it.id.take(4)}") }
        val ids = stakers.values.map { it.single() }
        val three = QuizSettlement.result("kid", "r", WalletCurrency.MBOKO, 10, 1, e, stakers, mapOf(ids[0] to 900, ids[1] to 600, ids[2] to 100))
        assertEquals(listOf(18L, 9L, 3L), three.lines.map { it.pay }, "60 / 30 / 10 de 30")
        val tie = QuizSettlement.result("kid", "r", WalletCurrency.MBOKO, 10, 1, e, stakers, mapOf(ids[0] to 900, ids[1] to 900, ids[2] to 100))
        assertEquals(listOf(14L, 13L, 3L), tie.lines.map { it.pay }, "les deux premiers se partagent les places 1 et 2 (90 % de 30 = 27 : 13 chacun), le reste de l'arrondi va au premier, 10 % au troisième")
        assertEquals(30L, tie.lines.sumOf { it.pay })
        assertTrue(three.check() && tie.check())
    }

    @Test fun nobodyScoredEveryTvGetsItsUsedStakeBackAndNothingIsWonOrLost() {
        val a = esc(1, k = 3, per = 5); val b = esc(2, k = 1, per = 5)
        val res = QuizSettlement.result("kid", "r", WalletCurrency.NDEM, 5, 1, listOf(a, b), linkedMapOf(a.eid to listOf("a1", "a2"), b.eid to listOf("b1")), mapOf("a1" to 0, "a2" to 0, "b1" to 0))
        assertEquals(listOf(10L, 5L), res.lines.map { it.used }); assertEquals(res.lines.map { it.used }, res.lines.map { it.pay })
        assertEquals(listOf(0L, 0L), res.lines.map { QuizSettlement.net(it, aborted = false) })
    }

    @Test fun aTvThatDidNotPlayHasItsEscrowInTheResultWithNothingUsed() {
        val a = esc(1, k = 1, per = 20); val b = esc(2, k = 1, per = 20); val c = esc(3, k = 1, per = 20)
        val res = QuizSettlement.result("kid", "r", WalletCurrency.NDEM, 20, 1, listOf(a, b, c), linkedMapOf(a.eid to listOf("a1"), b.eid to listOf("b1"), c.eid to emptyList()), mapOf("a1" to 400, "b1" to 100))
        assertEquals(listOf(a.eid, b.eid, c.eid), res.lines.map { it.eid }, "le blocage de la TV absente figure au résultat")
        assertEquals(listOf(20L, 20L, 0L), res.lines.map { it.used }); assertEquals(listOf(28L, 12L, 0L), res.lines.map { it.pay })
    }

    @Test fun anInterruptedGameGivesEveryEscrowBackInFullAndNetsNothing() {
        val e = (1..4).map { esc(it, k = 8, per = 50) }
        val res = QuizSettlement.result("kid", "r", WalletCurrency.NDEM, 50, 1, e, emptyMap(), null)
        assertEquals(PlayResult.Kind.ABORT, res.kind)
        assertEquals(e.map { it.eid }, res.lines.map { it.eid }); assertTrue(res.lines.all { it.used == 0L && it.pay == 0L })
        assertTrue(res.check()); assertEquals(listOf(0L, 0L, 0L, 0L), res.lines.map { QuizSettlement.net(it, aborted = true) })
    }

    @Test fun moreStakingPlayersThanBlockedSeatsIsAProgrammingErrorNeverSigned() {
        val a = esc(1, k = 1, per = 20)
        assertFailsWith<IllegalArgumentException> { QuizSettlement.result("kid", "r", WalletCurrency.NDEM, 20, 1, listOf(a), linkedMapOf(a.eid to listOf("a1", "a2")), mapOf("a1" to 1, "a2" to 1)) }
        assertFailsWith<IllegalArgumentException> { QuizSettlement.result("kid", "r", WalletCurrency.NDEM, 20, 1, emptyList(), emptyMap(), null) }
    }

    @Test fun theLedgerIsConservedForRandomTables() {
        val rnd = java.util.Random(20261007)
        repeat(500) { round ->
            val tvs = 2 + rnd.nextInt(7)                                     // 2 à 8 TV
            val per = (1 + rnd.nextInt(200)).toLong()
            val escrows = (0 until tvs).map { esc(it, 1 + rnd.nextInt(8), per) }
            val stakers = LinkedHashMap<String, List<String>>(); val scores = HashMap<String, Int>()
            for (e in escrows) {
                val n = rnd.nextInt(e.seats + 1)                             // des sièges présents au départ, au plus ceux qui sont bloqués
                stakers[e.eid] = (0 until n).map { "${e.eid}-p$it" }.onEach { scores[it] = if (rnd.nextInt(3) == 0) 0 else rnd.nextInt(4) * 500 }
            }
            val res = QuizSettlement.result("kid", "room-$round", WalletCurrency.NDEM, per, 1_000, escrows, stakers, scores)
            assertTrue(res.check(), "tour $round : Σ versé = Σ utilisé, eid uniques, bornes")
            assertEquals(escrows.map { it.eid }, res.lines.map { it.eid }, "une ligne par blocage, dans l'ordre")
            escrows.forEachIndexed { i, e -> assertEquals(per * stakers.getValue(e.eid).size, res.lines[i].used, "tour $round : utilisé = mise × sièges présents") }
            val shares = QuizSettlement.shares(per, stakers, scores)
            if (shares.values.sum() == 0L) assertEquals(res.lines.map { it.used }, res.lines.map { it.pay }, "tour $round : personne n'a marqué")
            else assertEquals(per * stakers.values.sumOf { it.size }, res.lines.sumOf { it.pay }, "tour $round : toute la cagnotte est distribuée")
            assertTrue(res.lines.all { it.pay >= 0 && it.used <= escrows.first { e -> e.eid == it.eid }.amount }, "tour $round : jamais plus que le blocage")
        }
    }
}
