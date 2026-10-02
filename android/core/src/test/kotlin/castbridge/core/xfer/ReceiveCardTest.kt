package castbridge.core.xfer

import castbridge.core.xfer.TransferProgress.Transport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** R-04: the home chip during a copy. `legacyChip` is the PRE-FIX logic (old `receiving()` listing only), kept here to show the bug. */
class ReceiveCardTest {
    private var t = 0L
    private val p = TransferProgress(now = { t })
    private fun line(serverUp: Boolean = true, partials: List<Triple<String, Long, Long>> = emptyList()) =
        ReceiveCards.headline(ReceiveCards.of(p.shown(), partials, serverUp))

    /** The pre-fix chip: only the `.part` listing (`receiving()`), which is EMPTY for a multi-connection copy (no .part until the end). */
    private fun legacyChip(receivingFiles: List<Triple<String, Long, Long>>): Pair<String, String?> =
        "Prêt à recevoir" to receivingFiles.firstOrNull()?.let { (n, got, total) -> "⬇ Réception de $n : ${got * 100 / total.coerceAtLeast(1)} %" }

    @Test fun r04_afterBeginTheLegacyChipStaysIdleButTheCardShowsTheLiveLine() {
        p.begin("x:1", "Prison Break [S02 - E03].avi", 1000, Transport.WIFI_MULTI, "Galaxy", 100)
        val receiving = emptyList<Triple<String, Long, Long>>()      // what server.receiving() answers: nothing (the bug's root cause)
        assertNull(legacyChip(receiving).second, "pre-fix: the screen says nothing while a copy runs")
        val now = line(partials = receiving)
        assertTrue(now!!.startsWith("⬇ Réception de Prison Break [S02 - E03] : 10 %"), now)
        assertTrue(now.contains("Wi-Fi multivoie") && now.contains("depuis Galaxy"), now)
    }

    @Test fun idleAndStartingTable() {
        assertNull(line()); assertNull(line(serverUp = false))
        assertEquals("Prêt à recevoir", ReceiveCards.ready(true)); assertEquals("Démarrage…", ReceiveCards.ready(false))
    }

    @Test fun oneThenSeveralTransfers() {
        p.begin("a", "a.mp4", 100, Transport.WIFI, null, 50)
        assertEquals("⬇ Réception de a : 50 % · Wi-Fi", line())
        val c = ReceiveCards.of(p.shown()).single()
        assertEquals(listOf("a", 50L, 100L, "Wi-Fi", "en cours"), listOf(c.name, c.received, c.total, c.via, c.state))
        p.begin("b", "b.mp4", 100, Transport.BLUETOOTH, null, 0)
        assertEquals("⬇ Réception de a : 50 % · Wi-Fi  (+1 autre)", line())
        p.begin("c", "c.mp4", 100, Transport.BLUETOOTH, null, 0)
        assertTrue(line()!!.endsWith("(+2 autres)"))
    }

    @Test fun bluetoothShowsEvenWhenTheHttpServerIsDownAndIsCountedOnce() {
        p.Sink("AA:BB", "Galaxy").progress("film.mp4", 42, 100)
        val cards = ReceiveCards.of(p.shown(), emptyList(), serverUp = false)
        assertEquals(1, cards.size); assertEquals("Bluetooth", cards.single().via)
        assertTrue(ReceiveCards.headline(cards)!!.startsWith("⬇ Réception de film : 42 %"))
        assertEquals("Démarrage…", ReceiveCards.ready(false))
    }

    @Test fun endedLineStaysEightSecondsThenTheChipIsIdleAgain() {
        p.begin("a", "film.mp4", 100, Transport.WIFI, null, 0); p.advance("a", 100); p.finish("a")
        assertEquals("Vidéo reçue ✓ · film", line())
        assertEquals("terminé", ReceiveCards.of(p.shown()).single().state)
        t += 7_900; assertEquals("Vidéo reçue ✓ · film", line())
        t += 200; assertNull(line())
        p.begin("b", "doc.pdf", 10, Transport.WIFI, null, 0); p.fail("b", "disque plein")
        assertEquals("Échec de la réception : disque plein · doc", line())
        assertEquals("échec", ReceiveCards.of(p.shown()).single().state)
    }

    @Test fun listingFallbackOnlyForNamesProgressDoesNotKnow() {
        val l = listOf(Triple("old.mp4", 30L, 60L))
        assertEquals("⬇ Réception de old : 50 %", line(partials = l))
        p.begin("a", "new.mp4", 100, Transport.WIFI, null, 0)
        assertEquals(2, ReceiveCards.of(p.shown(), l).size)
        p.begin("o", "old.mp4", 60, Transport.WIFI, null, 30)
        assertEquals(2, ReceiveCards.of(p.shown(), l).size, "old.mp4 is known to progress: not counted twice")
    }

    @Test fun nameIsTheReadableTitleNotTheRawFileName() {
        p.begin("a", "Film_Name.2020.1080p.mkv", 100, Transport.WIFI, null, 10)
        assertEquals(castbridge.core.tv.LibraryLogic.title("Film_Name.2020.1080p.mkv"), ReceiveCards.of(p.shown()).single().name)
    }
}
