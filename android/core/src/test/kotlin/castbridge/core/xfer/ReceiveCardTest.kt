package castbridge.core.xfer

import castbridge.core.xfer.TransferProgress.Transport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    // ---- R-51 : « Une copie interrompue sur la TV n'est pas annulable » (2026-10-14) ----

    private fun stalledLine(stalled: List<Triple<String, Long, Long>>, partials: List<Triple<String, Long, Long>> = emptyList()) =
        ReceiveCards.headline(ReceiveCards.of(p.shown(), partials, true, stalled))

    @Test fun r51_aStalledCopyIsSaidInterruptedAndTellsHowToCancelIt() {
        val cards = ReceiveCards.of(p.shown(), emptyList(), true, listOf(Triple("Film_Name.mkv", 42L, 100L)))
        val c = cards.single()
        assertEquals(listOf("Film Name", 42L, 100L, "interrompu"), listOf(c.name, c.received, c.total, c.state))
        assertEquals("⏸ Copie interrompue : Film Name (42 %) · MENU > Copie : annuler", c.line)
        assertEquals("⏸ Copie interrompue : Film Name (42 %) · MENU > Copie : annuler", ReceiveCards.headline(cards))
        // the pre-fix chip said « Réception de … » for it, for hours
        assertFalse(c.line.contains("Réception"), c.line)
    }

    @Test fun r51_stalledCardsComeAfterTheRunningOnesAndAreCountedInTheHeadline() {
        p.begin("a", "a.mp4", 100, Transport.WIFI, null, 50)
        val cards = ReceiveCards.of(p.shown(), listOf(Triple("b.mp4", 10L, 100L)), true, listOf(Triple("c.mp4", 30L, 100L)))
        assertEquals(listOf("en cours", "en cours", "interrompu"), cards.map { it.state })
        assertEquals(listOf("a", "b", "c"), cards.map { it.name })
        assertEquals("⬇ Réception de a : 50 % · Wi-Fi  (+2 autres)", ReceiveCards.headline(cards))
    }

    @Test fun r51_aStalledCopyIsNeverListedTwiceNorBesideALiveOrJustEndedItemOfTheSameName() {
        p.begin("put:clip.mp4", "Clip.MP4", 100, Transport.WIFI, null, 40)
        val running = ReceiveCards.of(p.shown(), emptyList(), true, listOf(Triple("clip.mp4", 40L, 100L)))
        assertEquals(1, running.size, "the live item of the same name (any case) hides the stalled one"); assertEquals("en cours", running.single().state)
        p.abort("put:clip.mp4", "plus de nouvelles du téléphone")
        val ended = ReceiveCards.of(p.shown(), emptyList(), true, listOf(Triple("clip.mp4", 40L, 100L)))
        assertEquals(listOf("interrompu"), ended.map { it.state })
        assertTrue(ended.single().line.startsWith("Réception interrompue : plus de nouvelles du téléphone"), "the 8 s end line, not a second card: ${ended.single().line}")
        t += 9_000
        assertEquals("⏸ Copie interrompue : clip (40 %) · MENU > Copie : annuler", stalledLine(listOf(Triple("clip.mp4", 40L, 100L))), "then the stalled card takes over")
        // the same name on two volumes is one card
        assertEquals(1, ReceiveCards.of(emptyList(), emptyList(), true, listOf(Triple("x.mp4", 1L, 10L), Triple("X.mp4", 2L, 10L))).size)
        // and never next to the live fallback of the same name
        assertEquals(1, ReceiveCards.of(emptyList(), listOf(Triple("y.mp4", 5L, 10L)), true, listOf(Triple("y.mp4", 5L, 10L))).size)
    }

    @Test fun r51_nothingStalledChangesNothing() {
        assertNull(stalledLine(emptyList())); assertEquals("Prêt à recevoir", ReceiveCards.ready(true))
        p.begin("a", "a.mp4", 100, Transport.WIFI, null, 50)
        assertEquals("⬇ Réception de a : 50 % · Wi-Fi", stalledLine(emptyList()))
    }

    @Test fun r51_theMenuLineNamesTheCopyItsPercentAndWhetherItIsAliveOrInterrupted() {
        assertEquals("Copie : annuler « Film Name » (42 %, interrompue)", ReceiveCards.cancelLine("Film_Name.mkv", 42, 100, live = false))
        assertEquals("Copie : annuler « Film Name » (42 %, en cours)", ReceiveCards.cancelLine("Film_Name.mkv", 42, 100, live = true))
        assertEquals("Copie : annuler « a » (0 %, interrompue)", ReceiveCards.cancelLine("a.mp4", 0, 0, live = false), "an unknown size never divides by zero")
        assertEquals("Copie : annuler « a » (100 %, en cours)", ReceiveCards.cancelLine("a.mp4", 150, 100, live = true), "never above 100 %")
    }
}

