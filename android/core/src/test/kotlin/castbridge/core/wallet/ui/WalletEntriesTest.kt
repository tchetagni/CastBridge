package castbridge.core.wallet.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WalletEntriesTest {
    private val alphabet = CodeEntry.ALPHABET

    /** Même contrôle que `ReceiveCodeService.check` du serveur (somme pondérée des 9 premiers caractères, modulo 32). */
    private fun check(nine: String): Char = alphabet[nine.indices.sumOf { (it + 1) * alphabet.indexOf(nine[it]) } % 32]

    private fun type(e: CodeEntry, target: String) {
        for (i in 1..9) {
            var guard = 0
            while (e.cursor < i && guard++ < 40) e.right()
            while (e.text().replace("-", "").getOrNull(i) != target[i] && guard++ < 80) e.up()      // borné : un défaut ne doit jamais figer la suite de tests
        }
    }

    @Test fun startsAtTheFirstEditableCharacter() {
        val e = CodeEntry()
        assertEquals("R000-0000-00", e.text()); assertEquals(1, e.cursor); assertFalse(e.complete())
    }

    @Test fun wheelsWrapAndSkipTheConfusingLetters() {
        val e = CodeEntry()
        e.up(); assertEquals("R100-0000-00", e.text())
        e.down(); e.down(); assertEquals("RZ00-0000-00", e.text())                      // 0 -1 = Z (tour complet)
        e.down(); assertEquals("RY00-0000-00", e.text())
        val t = CodeEntry(); repeat(alphabet.indexOf('T')) { t.up() }
        assertEquals('T', t.text()[1]); t.up(); assertEquals('V', t.text()[1])           // ni U, ni I, L, O
        val k = CodeEntry(); repeat(alphabet.indexOf('J')) { k.up() }; k.up(); assertEquals('K', k.text()[1])
    }

    @Test fun cursorStaysOnTheNineEditableCharacters() {
        val e = CodeEntry()
        repeat(5) { e.left() }; assertEquals(1, e.cursor)                                 // le « R » ne se change pas
        repeat(30) { e.right() }; assertEquals(9, e.cursor)
    }

    @Test fun aWellFormedCodeIsComplete() {
        val body = "R" + "7K3M9PQ2"                                                        // R + 8 caractères
        val full = body + check(body)
        val e = CodeEntry(); type(e, full)
        assertEquals(full, e.text().replace("-", ""))
        assertTrue(e.complete())
        assertEquals("${full.substring(0, 4)}-${full.substring(4, 8)}-${full.substring(8)}", e.text())
        assertEquals(full, e.canonical())
    }

    @Test fun aTypoBreaksTheCheck() {
        val body = "R" + "7K3M9PQ2"
        val full = body + check(body)
        val e = CodeEntry(); type(e, full)
        e.up()                                                                             // un caractère de trop : le contrôle échoue
        assertFalse(e.complete())
    }

    @Test fun amountWheelCountsFromTheRight() {
        val a = AmountEntry()
        assertEquals(0, a.value()); assertEquals("000 000 000".replace(" ", ""), a.digits()); assertEquals(8, a.cursor)
        a.up(); assertEquals(1, a.value())
        repeat(8) { a.up() }; assertEquals(9, a.value())
        a.up(); assertEquals(0, a.value())                                                 // la roulette tourne, la dizaine ne bouge pas
        a.left(); a.up(); assertEquals(10, a.value())
        a.left(); a.up(); a.up(); assertEquals(210, a.value())
        a.down(); assertEquals(110, a.value())
        a.right(); a.right(); a.right(); assertEquals(8, a.cursor)
    }

    @Test fun amountWheelBoundsAndWrapDown() {
        val a = AmountEntry(maxDigits = 4)
        a.down(); assertEquals(9, a.value())
        repeat(10) { a.left() }; assertEquals(0, a.cursor)
        a.down(); assertEquals(9009, a.value())
        assertEquals(4, a.digits().length)
    }
}
