package castbridge.core.quiz.online

import kotlin.test.*

class BadCodeCounterTest {
    @Test fun perAddressWindowThenRetryAfter() {
        val c = BadCodeCounter()
        repeat(29) { assertFalse(c.ipFail("a", 1_000L + it)) }
        assertTrue(c.ipFail("a", 2_000)); assertTrue(c.ipBlocked("a", 2_001))
        assertEquals(PlayProtocol.BAD_CODE_WINDOW_MS + 1_000 - 2_001, c.retryAfterMs("a", 2_001))
        assertFalse(c.ipBlocked("a", 1_000 + PlayProtocol.BAD_CODE_WINDOW_MS + 100), "la fenêtre de 5 minutes a passé")
        assertEquals(0L, c.retryAfterMs("never", 0))
    }

    @Test fun dailyCeilingIsCgnatFriendlyButReal() {
        val c = BadCodeCounter()
        assertEquals(300, BadCodeCounter.MAX_PER_DAY)
        var t = 0L
        var blocked = false
        // un faux toutes les 11 s (27 par fenêtre de 5 minutes) : jamais bloqué par la fenêtre courte ; le jour ferme la porte à la 300e
        for (i in 1..300) { t += 11_000L; blocked = c.ipFail("n", t); if (i < 300) assertFalse(blocked, "faux n°$i") }
        assertTrue(blocked, "300 par jour")
        assertTrue(c.ipBlocked("n", t + 10)); assertTrue(c.retryAfterMs("n", t + 10) > 60 * 60_000L, "attente jusqu'à la fin du jour")
        assertFalse(c.ipBlocked("n", 11_000L + BadCodeCounter.DAY_MS + 1), "le jour suivant, le compteur est remis")
    }

    @Test fun roomCounterRotatesAtFiftyAndRestarts() {
        val c = BadCodeCounter()
        repeat(49) { assertFalse(c.roomFail()) }
        assertTrue(c.roomFail()); repeat(49) { assertFalse(c.roomFail()) }; assertTrue(c.roomFail())
    }

    @Test fun memoryStaysBoundedAndSweepClears() {
        val c = BadCodeCounter(maxEntries = 100)
        repeat(1_000) { c.ipFail("ip$it", 0) }
        assertEquals(100, c.size()); assertEquals(100, c.daySize())
        c.sweep(PlayProtocol.BAD_CODE_WINDOW_MS + BadCodeCounter.DAY_MS + 1)
        assertEquals(0, c.size()); assertEquals(0, c.daySize())
    }

    @Test fun nearMissIsExactlyOneSymbolOff() {
        assertTrue(RoomCode.nearMiss("ABCD1235", "ABCD1234"))
        assertFalse(RoomCode.nearMiss("ABCD1234", "ABCD1234")); assertFalse(RoomCode.nearMiss("ABCD1255", "ABCD1234")); assertFalse(RoomCode.nearMiss("ABCD123", "ABCD1234"))
    }
}
