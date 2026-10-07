package castbridge.core.games

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Le mélange doit être identique partout et pour toujours (TV Android 32 bits, serveur Java, vérificateur de journal) : [GameRandom] est un SplitMix64 écrit ici,
 * pas `java.util.Random`. Les vecteurs ci-dessous viennent d'une implémentation indépendante (Python, `vectors.py` du chantier) ; ceux de la graine 1234567 sont aussi
 * les valeurs publiées de la référence SplitMix64.
 */
class GameRandomTest {
    @Test fun matchesTheReferenceSplitMix64() {
        assertEquals(listOf(6457827717110365317L, 3203168211198807973L, -8629252141511181193L, 4593380528125082431L, -2037821214251327795L),
            GameRandom(1234567L).let { r -> List(5) { r.nextLong() } })
        assertEquals(listOf(-2152535657050944081L, 7960286522194355700L, 487617019471545679L, -537132696929009172L, 1961750202426094747L),
            GameRandom(0L).let { r -> List(5) { r.nextLong() } })
        assertEquals(listOf(-1956407806741107680L, -1612297016619662647L, 4048727598324417001L, 7862637804313477842L, -5431262886246717010L),
            GameRandom(-1L).let { r -> List(5) { r.nextLong() } }, "une graine négative est valable")
    }

    @Test fun boundedIntsAreUnbiasedAndFixed() {
        val r = GameRandom(42L)
        assertEquals(listOf(11, 21, 15, 23, 45, 45, 42, 3, 15, 24, 9, 15), List(12) { r.nextInt(52) })
        val n = GameRandom(1L)
        repeat(1_000) { assertTrue(n.nextInt(7) in 0..6) }
        assertEquals(0, GameRandom(5L).nextInt(1), "une seule valeur possible")
        assertFailsWith<IllegalArgumentException> { GameRandom(1L).nextInt(0) }
        assertFailsWith<IllegalArgumentException> { GameRandom(1L).nextInt(-3) }
        // pas de biais visible : 70 000 tirages parmi 7 valeurs, chacune entre 9 000 et 11 000 fois
        val counts = IntArray(7); val big = GameRandom(99L); repeat(70_000) { counts[big.nextInt(7)]++ }
        assertTrue(counts.all { it in 9_000..11_000 }, counts.joinToString())
    }

    @Test fun shuffleIsTheDocumentedFisherYates() {
        val l = (0..9).toMutableList(); GameRandom(7L).shuffle(l)
        assertEquals(listOf(3, 2, 1, 6, 7, 5, 4, 8, 9, 0), l)
        val again = (0..9).toMutableList(); GameRandom(7L).shuffle(again)
        assertEquals(l, again, "même graine, même mélange")
        val other = (0..9).toMutableList(); GameRandom(8L).shuffle(other)
        assertNotEquals(l, other)
        assertEquals((0..9).toList(), l.sorted(), "rien n'est perdu ni dupliqué")
        val empty = mutableListOf<Int>(); GameRandom(1L).shuffle(empty); assertTrue(empty.isEmpty())
        val one = mutableListOf(5); GameRandom(1L).shuffle(one); assertEquals(listOf(5), one)
    }
}
