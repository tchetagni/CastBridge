package castbridge.core.quiz.online

import kotlin.test.*

class RttBookTest {
    @Test fun rttIsTheMinimumOfTheLastEightSamples() {
        val b = RttBook()
        assertEquals(0, b.rtt("a")); assertFalse(b.known("a"))
        b.sample("a", 100); assertEquals(100, b.rtt("a"))
        b.sample("a", 200); assertEquals(100, b.rtt("a"), "min(100, 200)")
        b.sample("a", 50); assertEquals(50, b.rtt("a"))
        repeat(8) { b.sample("a", 300) }; assertEquals(300, b.rtt("a"), "les anciens échantillons sont sortis de la fenêtre de 8")
        b.forget("a"); assertEquals(0, b.rtt("a"))
    }

    @Test fun samplesAreBoundedTo0And2000() {
        val b = RttBook(); b.sample("a", 99_999); assertEquals(2_000, b.rtt("a")); b.sample("b", -5); assertEquals(0, b.rtt("b"))
    }

    @Test fun compensationIsHalfOfRttCappedAt200AndGraceIsRttCappedAt1s() {
        val b = RttBook()
        for ((rtt, comp, grace) in listOf(Triple(0L, 0L, 0L), Triple(150L, 75L, 150L), Triple(200L, 100L, 200L), Triple(600L, 100L, 600L), Triple(1_200L, 100L, 1_000L), Triple(2_000L, 100L, 1_000L))) {
            b.sample("c$rtt", rtt)
            assertEquals(comp, b.compensationMs("c$rtt"), "compensation rtt=$rtt"); assertEquals(grace, b.graceMs("c$rtt"), "grâce rtt=$rtt")
        }
    }

    @Test fun elapsedFromOpeningMinusCompensation() {
        assertEquals(9_900, RttBook.elapsed(10_000, 600), "RTT 600 ms plafonné à 200 ⇒ 100 ms rendus")
        assertEquals(9_900, RttBook.elapsed(10_000, 2_000))
        assertEquals(9_925, RttBook.elapsed(10_000, 150))
        assertEquals(10_000, RttBook.elapsed(10_000, 0))
        assertEquals(0, RttBook.elapsed(50, 2_000), "jamais négatif")
    }

    @Test fun tvRelayTakesTheMaxSoTheTvCanOnlyLengthenATime() {
        assertEquals(9_900, RttBook.relayed(9_900, 10_000, 300), "max(9 900, 9 700) = 9 900")
        assertEquals(9_700, RttBook.relayed(9_000, 10_000, 300), "la TV ne peut pas descendre sous ce que le réseau prouve")
        assertEquals(12_000, RttBook.relayed(12_000, 10_000, 300), "la TV peut allonger")
        assertEquals(0, RttBook.relayed(-5, 10, 300))
    }
}
