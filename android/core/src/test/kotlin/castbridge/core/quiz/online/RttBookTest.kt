package castbridge.core.quiz.online

import kotlin.test.*

class RttBookTest {
    @Test fun firstSampleThenEwmaWithAlpha03() {
        val b = RttBook()
        assertEquals(0, b.rtt("a")); assertFalse(b.known("a"))
        b.sample("a", 100); assertEquals(100, b.rtt("a"))
        b.sample("a", 200); assertEquals(130, b.rtt("a"), "0,3×200 + 0,7×100")
        b.sample("a", 200); assertEquals(151, b.rtt("a"))
        b.forget("a"); assertEquals(0, b.rtt("a"))
    }

    @Test fun samplesAreBoundedTo0And2000() {
        val b = RttBook(); b.sample("a", 99_999); assertEquals(2_000, b.rtt("a")); b.sample("b", -5); assertEquals(0, b.rtt("b"))
    }

    @Test fun compensationIsHalfRttCappedAt400AndGraceIsRttCappedAt1s() {
        val b = RttBook()
        for ((rtt, comp, grace) in listOf(Triple(0L, 0L, 0L), Triple(150L, 75L, 150L), Triple(600L, 300L, 600L), Triple(800L, 400L, 800L), Triple(1_200L, 400L, 1_000L), Triple(2_000L, 400L, 1_000L))) {
            b.sample("c$rtt", rtt)
            assertEquals(comp, b.compensationMs("c$rtt"), "compensation rtt=$rtt"); assertEquals(grace, b.graceMs("c$rtt"), "grâce rtt=$rtt")
        }
    }

    @Test fun elapsedFromOpeningMinusCompensation() {
        assertEquals(9_700, RttBook.elapsed(10_000, 600), "RTT 600 ms, arrivée à 10 000 ms ⇒ 9 700 ms")
        assertEquals(9_600, RttBook.elapsed(10_000, 2_000), "RTT 2 000 (plafonné) ⇒ 9 600 ms")
        assertEquals(10_000, RttBook.elapsed(10_000, 0))
        assertEquals(0, RttBook.elapsed(100, 2_000), "jamais négatif")
    }

    @Test fun tvRelayTakesTheMaxSoTheTvCanOnlyLengthenATime() {
        assertEquals(9_900, RttBook.relayed(9_900, 10_000, 300), "max(9 900, 9 700) = 9 900")
        assertEquals(9_700, RttBook.relayed(9_000, 10_000, 300), "la TV ne peut pas descendre sous ce que le réseau prouve")
        assertEquals(12_000, RttBook.relayed(12_000, 10_000, 300), "la TV peut allonger")
        assertEquals(0, RttBook.relayed(-5, 10, 300))
    }
}
