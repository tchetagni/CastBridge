package castbridge.core.xfer

import castbridge.core.xfer.TransferProgress.Phase
import castbridge.core.xfer.TransferProgress.Transport
import kotlin.test.*

/** The single reception progress of the TV (diag-receiver-progress): begin/advance/end, throttling, resume, staleness, concurrency, Bluetooth sink. */
class TransferProgressTest {
    private var t = 1_000L
    private val events: MutableList<TransferProgress.Item> = java.util.Collections.synchronizedList(ArrayList())
    private val p = TransferProgress(now = { t }, minEmitMs = 1000, staleMs = 90_000, keepEndedMs = 8_000).also { it.addListener { i -> events += i } }

    @Test fun beginAdvanceFinishNotifiesThrottledAndEndsWithVideoReceived() {
        p.begin("put:a.mkv", "Prison.Break.S02E03.mkv", 10_000_000, Transport.WIFI, "Galaxy A52")
        assertEquals(1, events.size)
        t += 200; p.advance("put:a.mkv", 1_000_000)          // too soon: no notification
        assertEquals(1, events.size)
        t += 1000; p.advance("put:a.mkv", 4_000_000)
        assertEquals(2, events.size)
        val running = events.last()
        assertEquals(40, running.percent); assertEquals(Phase.RUNNING, running.phase)
        assertTrue(running.bytesPerSec > 0, "speed measured")
        assertTrue("40 %" in running.detail() && "Wi-Fi" in running.detail() && "depuis Galaxy A52" in running.detail(), running.detail())
        assertEquals(listOf("put:a.mkv"), p.active().map { it.id })
        t += 100; p.advance("put:a.mkv", 10_000_000)          // the last byte always notifies
        assertEquals(3, events.size); assertEquals(100, events.last().percent)
        p.finish("put:a.mkv", "Prison Break - S02E03.mkv")
        val end = events.last()
        assertEquals(Phase.DONE, end.phase); assertEquals("Vidéo reçue ✓", end.endLine()); assertEquals("Prison Break - S02E03.mkv", end.name)
        assertTrue(p.active().isEmpty())
        assertTrue(p.shown().single().screenLine().startsWith("Vidéo reçue ✓"), "the end stays on screen a few seconds")
        t += 9_000; assertTrue(p.shown().isEmpty(), "then the screen goes back to « Prêt à recevoir »")
    }

    @Test fun aNonVideoFileSaysFileReceived() {
        p.begin("x:1", "cours.pdf", 100, Transport.WIFI_MULTI, null); p.finish("x:1")
        assertEquals("Fichier reçu ✓", events.last().endLine())
    }

    @Test fun errorAndAbortEndTheTransferWithTheirReason() {
        p.begin("x:1", "a.mp4", 100, Transport.WIFI_MULTI, null); p.fail("x:1", "plus de place sur Clé USB")
        assertEquals(Phase.FAILED, events.last().phase); assertEquals("Échec de la réception : plus de place sur Clé USB", events.last().endLine())
        p.begin("x:2", "b.mp4", 100, Transport.WIFI_MULTI, null); p.abort("x:2", "annulée par le téléphone")
        assertEquals(Phase.ABORTED, events.last().phase); assertTrue("annulée par le téléphone" in events.last().endLine())
        assertTrue(p.active().isEmpty())
        val n = events.size; p.advance("x:2", 50); p.finish("x:2"); assertEquals(n, events.size, "a late chunk after the end changes nothing")
    }

    @Test fun aResumeKeepsTheSameNotificationIdAndSource() {
        val first = p.begin("put:a", "a.mkv", 1000, Transport.WIFI, "Galaxy")
        t += 2000; p.advance("put:a", 300)
        p.interrupted("put:a")
        assertTrue("reprise" in events.last().detail(), events.last().detail())
        assertEquals(Phase.RUNNING, events.last().phase, "still listed: the phone resumes")
        t += 5000; val again = p.begin("put:a", "a.mkv", 1000, Transport.WIFI, null, received = 300)
        assertEquals(first.seq, again.seq); assertEquals("Galaxy", again.source); assertEquals(300, again.received)
        // even after an end, the same transfer coming back keeps its id (one notification per transfer)
        p.abort("put:a"); assertEquals(first.seq, p.begin("put:a", "a.mkv", 1000, Transport.WIFI, null, 300).seq)
    }

    @Test fun aSilentTransferIsEndedAfterTheStaleDelay() {
        p.begin("x:1", "a.mkv", 1000, Transport.WIFI_MULTI, null)
        t += 60_000; assertEquals(1, p.active().size)
        t += 31_000; assertTrue(p.active().isEmpty())
        assertEquals(Phase.ABORTED, events.last().phase); assertTrue("plus de nouvelles" in events.last().endLine())
    }

    @Test fun concurrentTransfersOnBothTransportsAreKeptApart() {
        val a = p.begin("x:1", "a.mkv", 1000, Transport.WIFI_MULTI, "A")
        val b = p.sink("AA:BB", "Pixel").let { s -> s.progress("b.mp4", 0, 2000); s }
        assertEquals(2, p.active().size)
        assertNotEquals(a.seq, p.active().first { it.transport == Transport.BLUETOOTH }.seq)
        t += 1500; p.advance("x:1", 500); b.progress("b.mp4", 1000, 2000)
        assertEquals(setOf(50), p.active().map { it.percent }.toSet())
        p.finish("x:1"); assertEquals(listOf(Transport.BLUETOOTH), p.active().map { it.transport })
        b.end(true); assertTrue(p.active().isEmpty())
        assertEquals(2, events.count { it.phase == Phase.DONE })
    }

    @Test fun threadsFeedingAtOnceLoseNothing() {
        val ths = (1..8).map { k -> Thread { p.begin("x:$k", "f$k.mkv", 1000, Transport.WIFI_MULTI, null); for (i in 1..1000) p.advance("x:$k", i.toLong()); p.finish("x:$k") } }
        ths.forEach { it.start() }; ths.forEach { it.join() }
        assertTrue(p.active().isEmpty()); assertEquals(8, events.count { it.phase == Phase.DONE })
        assertEquals(8, events.filter { it.phase == Phase.DONE }.map { it.seq }.toSet().size)
    }

    @Test fun bluetoothSinkBeginsOnFirstProgressAndEndsWithTheConnection() {
        val s = p.sink("AA:BB", "Galaxy")
        s.end(true); s.broken(); assertTrue(events.isEmpty(), "a HELLO or a remote link leaves nothing")
        s.progress("film.avi", 0, 1000)
        assertEquals(Transport.BLUETOOTH, events.single().transport); assertEquals("Galaxy", events.single().source)
        t += 1200; s.progress("film.avi", 600, 1000); assertEquals(60, events.last().percent)
        s.broken(); assertEquals(Phase.RUNNING, events.last().phase)
        val s2 = p.sink("AA:BB", "Galaxy"); s2.progress("film.avi", 600, 1000)
        assertEquals(1, events.map { it.seq }.toSet().size, "the resumed Bluetooth copy is the same transfer")
        s2.end(false, "code refusé"); assertEquals(Phase.FAILED, events.last().phase)
        assertEquals("Échec de la réception : code refusé · film", events.last().screenLine())
    }

    private fun TransferProgress.sink(peer: String, name: String) = Sink(peer, name)
}
