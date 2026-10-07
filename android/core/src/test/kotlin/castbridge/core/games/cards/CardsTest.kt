package castbridge.core.games.cards

import castbridge.core.games.PlayerId
import castbridge.core.quiz.Json
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun codes(cs: List<Card>) = cs.map { it.code }
private fun sha16(cs: List<Card>) = MessageDigest.getInstance("SHA-256").digest(cs.joinToString(" ") { it.code }.toByteArray()).joinToString("") { "%02x".format(it) }.take(16)
private val A = PlayerId("s1"); private val B = PlayerId("s2"); private val C = PlayerId("s3")

class CardTest {
    @Test fun codesAreCompactAndRoundTrip() {
        assertEquals("AS", Card(Rank.ACE, Suit.SPADES).code)
        assertEquals("10H", Card(Rank.TEN, Suit.HEARTS).code)
        assertEquals("JK", Card.JOKER.code)
        for (r in Rank.values()) for (s in Suit.values()) if (r != Rank.JOKER) assertEquals(Card(r, s), Card.parse(Card(r, s).code), "${r}${s}")
        assertEquals(Card.JOKER, Card.parse("JK"))
        assertEquals(Card(Rank.JACK, Suit.CLUBS), Card.parse("JC"), "valet de trèfle, pas un joker")
    }

    @Test fun refusesWhatIsNotACard() {
        for (bad in listOf("", "A", "1S", "11H", "AX", "as", "10", "KK", "JKK", "0H", " AS", "AS ")) assertNull(Card.parse(bad), "« $bad »")
        assertFailsWith<IllegalArgumentException> { Card(Rank.JOKER, Suit.HEARTS) }
        assertFailsWith<IllegalArgumentException> { Card(Rank.ACE, null) }
    }

    @Test fun textsForTheScreens() {
        assertEquals("A♠", Card(Rank.ACE, Suit.SPADES).text)
        assertEquals("10♥", Card(Rank.TEN, Suit.HEARTS).text)
        assertEquals("as de pique", Card(Rank.ACE, Suit.SPADES).label)
        assertEquals("valet de cœur", Card(Rank.JACK, Suit.HEARTS).label)
        assertEquals("joker", Card.JOKER.label)
        assertTrue(Suit.HEARTS.red && Suit.DIAMONDS.red && !Suit.CLUBS.red && !Suit.SPADES.red)
    }
}

class RankOrderTest {
    private val c = { r: Rank -> Card(r, Suit.CLUBS) }

    @Test fun aceHighAndAceLowAreParametrable() {
        assertTrue(RankOrder.ACE_HIGH.beats(c(Rank.ACE), c(Rank.KING)))
        assertTrue(RankOrder.ACE_HIGH.beats(c(Rank.TWO).copy(suit = Suit.HEARTS), c(Rank.TWO).copy(suit = Suit.SPADES)).not(), "même valeur : personne ne l'emporte")
        assertTrue(RankOrder.ACE_LOW.beats(c(Rank.TWO), c(Rank.ACE)))
        assertTrue(RankOrder.ACE_LOW.beats(c(Rank.KING), c(Rank.QUEEN)))
        assertFalse(RankOrder.ACE_LOW.beats(c(Rank.ACE), c(Rank.TWO)))
        assertEquals(0, RankOrder.ACE_HIGH.compare(c(Rank.SEVEN), Card(Rank.SEVEN, Suit.SPADES)))
        assertTrue(RankOrder.ACE_HIGH.strength(Rank.ACE) > RankOrder.ACE_HIGH.strength(Rank.KING))
        assertTrue(RankOrder.ACE_LOW.strength(Rank.ACE) < RankOrder.ACE_LOW.strength(Rank.TWO))
    }

    @Test fun anyCustomOrderWorksAndRefusesWhatItDoesNotKnow() {
        // l'ordre d'un jeu de 32 cartes où le 10 est au-dessus du roi (exemple d'ordre propre à un jeu, aucune règle réelle ici)
        val o = RankOrder.of(Rank.SEVEN, Rank.EIGHT, Rank.NINE, Rank.JACK, Rank.QUEEN, Rank.KING, Rank.TEN, Rank.ACE)
        assertTrue(o.beats(c(Rank.TEN), c(Rank.KING)))
        assertEquals(c(Rank.ACE), o.highest(listOf(c(Rank.TEN), c(Rank.ACE), c(Rank.SEVEN))))
        assertFailsWith<IllegalArgumentException> { o.strength(Rank.TWO) }
        assertFailsWith<IllegalArgumentException> { o.strength(Rank.JOKER) }
        assertFailsWith<IllegalArgumentException> { RankOrder.of(Rank.ACE, Rank.ACE) }
        assertFailsWith<IllegalArgumentException> { RankOrder.of() }
        assertNull(o.highest(emptyList()))
    }

    @Test fun theJokerIsPlacedOnPurpose() {
        assertFailsWith<IllegalArgumentException> { RankOrder.ACE_HIGH.strength(Rank.JOKER) }
        assertTrue(RankOrder.ACE_HIGH.withJoker(top = true).beats(Card.JOKER, c(Rank.ACE)))
        assertTrue(RankOrder.ACE_HIGH.withJoker(top = false).beats(c(Rank.TWO), Card.JOKER))
    }

    @Test fun sortingIsStableAndWeakestFirst() {
        val hand = listOf(c(Rank.KING), Card(Rank.TWO, Suit.HEARTS), c(Rank.ACE), Card(Rank.TWO, Suit.CLUBS))
        assertEquals(listOf("2H", "2C", "KC", "AC"), codes(RankOrder.ACE_HIGH.sorted(hand)), "à valeur égale, l'ordre d'origine est gardé")
    }
}

class CardDeckTest {
    @Test fun standardDecksHaveTheRightCardsInADocumentedOrder() {
        val d52 = CardDeck.standard(52)
        assertEquals(52, d52.size); assertEquals(52, d52.drawPile.toSet().size)
        assertEquals(listOf("2C", "3C", "4C"), codes(d52.drawPile.take(3)), "trèfle, carreau, cœur, pique ; du 2 à l'as")
        assertEquals("AS", d52.drawPile.last().code)
        val d32 = CardDeck.standard(32)
        assertEquals(32, d32.size)
        assertTrue(d32.drawPile.none { it.rank in listOf(Rank.TWO, Rank.THREE, Rank.FOUR, Rank.FIVE, Rank.SIX) })
        assertEquals(listOf("7C", "8C", "9C", "10C", "JC", "QC", "KC", "AC", "7D"), codes(d32.drawPile.take(9)))
        assertEquals(54, CardDeck.standard(52, jokers = 2).size)
        assertEquals(2, CardDeck.standard(52, jokers = 2).drawPile.count { it == Card.JOKER })
        assertEquals(104, CardDeck.standard(52, decks = 2).size)
        assertFailsWith<IllegalArgumentException> { CardDeck.standard(40) }
        assertFailsWith<IllegalArgumentException> { CardDeck.standard(52, jokers = -1) }
        assertFailsWith<IllegalArgumentException> { CardDeck.standard(52, decks = 0) }
    }

    @Test fun shuffleIsDeterministicByTheSeedAndMatchesTheIndependentVectors() {
        val expected = mapOf(
            (52 to 0L) to ("deaa454c1223ac35" to listOf("6H", "JH", "4H", "JC", "AS", "QD", "7C", "5C")),
            (52 to 42L) to ("aa7cd01b008449de" to listOf("6H", "QS", "QC", "3H", "2S", "7H", "6S", "9S")),
            (52 to -1L) to ("ca0604d7f6a18971" to listOf("AD", "JH", "KD", "6S", "4D", "JS", "3H", "8H")),
            (32 to 0L) to ("fcff4d90b031f538" to listOf("KC", "JD", "7D", "JC", "9D", "10H", "7H", "10S")),
            (32 to 2026L) to ("a24c06953b849cbb" to listOf("7C", "7D", "10H", "AC", "QD", "QH", "JS", "QS")),
        )
        for ((k, v) in expected) {
            val s = CardDeck.standard(k.first).shuffled(k.second)
            assertEquals(v.first, sha16(s.drawPile), "paquet entier ${k.first} cartes, graine ${k.second}")
            assertEquals(v.second, codes(s.drawPile.take(8)))
            assertEquals(codes(s.drawPile), codes(CardDeck.standard(k.first).shuffled(k.second).drawPile), "rejouable")
        }
        val withJokers = CardDeck.standard(52, jokers = 2).shuffled(5L)
        assertEquals("21c27e5f2f3e83f1", sha16(withJokers.drawPile)); assertEquals(listOf("7D", "KD", "4S", "5S", "9S", "AH", "10C", "8C"), codes(withJokers.drawPile.take(8)))
        assertNotEquals(codes(CardDeck.standard(52).shuffled(1L).drawPile), codes(CardDeck.standard(52).shuffled(2L).drawPile))
    }

    @Test fun shuffleKeepsEveryCardAndTheDiscardPile() {
        val base = CardDeck.standard(52).let { it.copy(drawPile = it.drawPile.drop(5), discardPile = it.drawPile.take(5)) }
        val s = base.shuffled(9L)
        assertEquals(base.drawPile.toSet(), s.drawPile.toSet()); assertEquals(base.discardPile, s.discardPile, "la défausse n'est pas mélangée")
        assertEquals(47, s.drawPile.size)
    }

    @Test fun drawTakesFromTheTopAndNeverMoreThanThereIs() {
        val d = CardDeck.standard(32)
        val (three, rest) = d.draw(3)
        assertEquals(listOf("7C", "8C", "9C"), codes(three)); assertEquals(29, rest.size); assertEquals(32, d.size, "l'ancien paquet n'a pas changé")
        val (all, empty) = rest.draw(100)
        assertEquals(29, all.size); assertTrue(empty.isEmpty)
        assertTrue(empty.draw(1).first.isEmpty())
        assertFailsWith<IllegalArgumentException> { d.draw(-1) }
        val (one, _) = d.draw(); assertEquals("7C", one!!.code)
        assertNull(empty.draw().first)
    }

    @Test fun dealGoesRoundTheTableOneCardAtATime() {
        val d = CardDeck.standard(52).shuffled(42L)
        val deal = d.deal(listOf(A, B), perPlayer = null)
        assertEquals(26, deal.hands.getValue(A).size); assertEquals(26, deal.hands.getValue(B).size); assertTrue(deal.rest.isEmpty)
        assertEquals(listOf("6H", "QC", "2S", "6S", "JH"), codes(deal.hands.getValue(A).take(5)), "première carte au premier joueur, deuxième au second, etc. (vecteur indépendant)")
        assertEquals(listOf("QS", "3H", "7H", "9S", "2D"), codes(deal.hands.getValue(B).take(5)))
        val five = CardDeck.standard(32).deal(listOf(A, B, C), perPlayer = 5)
        assertEquals(listOf(5, 5, 5), five.hands.values.map { it.size }); assertEquals(17, five.rest.size)
        assertEquals(listOf("7C", "10C", "KC", "8D", "JD"), codes(five.hands.getValue(A)), "A reçoit les cartes 1, 4, 7…")
        // 52 cartes à trois : 18 / 17 / 17, tout est distribué
        val uneven = CardDeck.standard(52).deal(listOf(A, B, C), perPlayer = null)
        assertEquals(listOf(18, 17, 17), uneven.hands.values.map { it.size })
        assertEquals(52, uneven.hands.values.flatten().toSet().size)
        assertFailsWith<IllegalArgumentException> { CardDeck.standard(32).deal(listOf(A, B), perPlayer = 17) }
        assertFailsWith<IllegalArgumentException> { CardDeck.standard(32).deal(emptyList(), perPlayer = 1) }
        assertFailsWith<IllegalArgumentException> { CardDeck.standard(32).deal(listOf(A, A), perPlayer = 1) }
        assertFailsWith<IllegalArgumentException> { CardDeck.standard(32).deal(listOf(A, B), perPlayer = -1) }
    }

    @Test fun discardAndRecycleKeepEveryCard() {
        val d = CardDeck.standard(32)
        val (hand, rest) = d.draw(10)
        val played = rest.discard(hand)
        assertEquals(22, played.size); assertEquals(codes(hand), codes(played.discardPile))
        val (one, afterOne) = played.draw(1)
        val withDiscard = afterOne.discard(one)            // 21 cartes dans la pioche, 11 dans la défausse
        assertEquals(21, withDiscard.size); assertEquals(11, withDiscard.discardPile.size)
        val back = withDiscard.recycleDiscard(3L)
        assertTrue(back.discardPile.isEmpty()); assertEquals(32, back.size)
        assertEquals(d.drawPile.toSet(), back.drawPile.toSet(), "aucune carte perdue ni dupliquée")
        assertEquals(afterOne.drawPile, back.drawPile.take(21), "la défausse mélangée passe SOUS ce qui reste de la pioche")
        assertEquals(codes(back.drawPile), codes(withDiscard.recycleDiscard(3L).drawPile), "rejouable")
        assertNotEquals(codes(back.drawPile), codes(withDiscard.recycleDiscard(4L).drawPile), "la graine compte")
    }

    @Test fun jsonRoundTripAndRefusal() {
        val d = CardDeck.standard(32, jokers = 1).shuffled(3L).let { it.copy(drawPile = it.drawPile.drop(4), discardPile = it.drawPile.take(4)) }
        val text = Json.write(d.toJson())
        assertEquals(d, CardDeck.fromJson(Json.obj(text)))
        assertTrue(text.startsWith("{\"draw\":[\""))
        assertFailsWith<IllegalArgumentException> { CardDeck.fromJson(Json.obj("""{"draw":["AS","ZZ"],"discard":[]}""")) }
        assertFailsWith<IllegalArgumentException> { CardDeck.fromJson(Json.obj("""{"draw":"AS"}""")) }
        assertFailsWith<IllegalArgumentException> { CardDeck.fromJson(Json.obj("""{"discard":[]}""")) }
        assertEquals(CardDeck(emptyList(), emptyList()), CardDeck.fromJson(Json.obj("""{"draw":[],"discard":[]}""")))
    }
}

class TrickTest {
    private fun p(who: PlayerId, code: String) = Play(who, Card.parse(code)!!)

    @Test fun highestCardOfTheLedSuitWins() {
        val t = Trick(listOf(p(A, "7H"), p(B, "KS"), p(C, "10H")))
        assertEquals(C, t.winner(RankOrder.ACE_HIGH)?.player, "le roi de pique n'a pas la couleur demandée")
        assertEquals(Suit.HEARTS, t.led)
        assertEquals(A, Trick(listOf(p(A, "AH"), p(B, "KH"))).winner(RankOrder.ACE_HIGH)?.player)
        assertEquals(B, Trick(listOf(p(A, "AH"), p(B, "KH"))).winner(RankOrder.ACE_LOW)?.player, "as bas : le roi est plus fort")
    }

    @Test fun aTrumpBeatsEverythingEvenTheLowest() {
        val t = Trick(listOf(p(A, "AH"), p(B, "2S"), p(C, "KH")))
        assertEquals(B, t.winner(RankOrder.ACE_HIGH, trump = Suit.SPADES)?.player)
        assertEquals(A, t.winner(RankOrder.ACE_HIGH, trump = Suit.CLUBS)?.player, "aucun atout joué : la couleur demandée décide")
        val two = Trick(listOf(p(A, "2S"), p(B, "9S"), p(C, "AH")))
        assertEquals(B, two.winner(RankOrder.ACE_HIGH, trump = Suit.SPADES)?.player, "le plus fort des atouts")
    }

    @Test fun withoutSuitsAndOnATieNobodyWinsYet() {
        val war = Trick(listOf(p(A, "7H"), p(B, "7S")))
        assertNull(war.winner(RankOrder.ACE_HIGH, followSuit = false), "égalité de valeur : c'est une bataille")
        assertEquals(B, Trick(listOf(p(A, "7H"), p(B, "9S"))).winner(RankOrder.ACE_HIGH, followSuit = false)?.player)
        assertEquals(A, Trick(listOf(p(A, "7H"), p(B, "9S"))).winner(RankOrder.ACE_HIGH, followSuit = true)?.player, "avec la règle de la couleur, le 9 de pique ne compte pas")
        assertNotNull(Trick(listOf(p(A, "7H"))).winner(RankOrder.ACE_HIGH))
        assertNull(Trick(emptyList()).winner(RankOrder.ACE_HIGH))
    }
}
