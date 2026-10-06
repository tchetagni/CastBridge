package castbridge.core.xfer

import castbridge.core.ux.*
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.*

/**
 * R-21 : lien Wi-Fi très dégradé (300 Ko/s utiles, 100 ms de latence, gigue), fichier AVI de 348 Mo (blocs de 4 Mo). Banc à temps accéléré
 * (1 s réelle = [SCALE] s simulées) : les mêmes lois que le vrai lien, sans attendre une minute par test.
 */
class R21SlowLinkTest {
    private companion object { const val SCALE = 10L; const val MIB = 1L shl 20 }
    private object Src : BlockSource { override val size = Long.MAX_VALUE; override fun read(pos: Long, buf: ByteArray, off: Int, len: Int) = len }

    /** Horloge simulée : démarre à la création, avance [SCALE] fois plus vite que le réel. */
    private class VClock { val t0 = System.nanoTime(); fun ns() = (System.nanoTime() - t0) * SCALE; fun ms() = ns() / 1_000_000 }

    /**
     * Une voie Wi-Fi simulée : [bps] partagés par toutes les connexions (seau), latence 100 ms +/- 50 ms par requête, morceaux de 128 Kio.
     * [reports] : appelle ctx.onBytes comme le fait désormais WifiLane ; faux = le comportement d'avant (rien avant la fin du bloc).
     */
    private class SlowWifi(val clock: VClock, val bps: Double = 300_000.0, val reports: Boolean = true, val workers: Int = 2) : Lane {
        override val id = "wifi"; override val maxWorkers = workers; override val sent = AtomicLong()
        private var free = 0L
        private val rnd = java.util.Random(7)
        @Synchronized private fun take(bytes: Long): Long { val now = clock.ns(); val start = maxOf(now, free); free = start + (bytes / bps * 1e9).toLong(); return free }
        private fun sleepUntilV(vns: Long, ctx: SendContext): Boolean {
            while (clock.ns() < vns) { if (ctx.cancelled()) return false; Thread.sleep(1) }
            return true
        }
        override fun send(worker: Int, idx: Int, ctx: SendContext): Outcome {
            val jitter = synchronized(rnd) { rnd.nextInt(100) - 50 }
            if (!sleepUntilV(clock.ns() + (100L + jitter) * 1_000_000, ctx)) return Outcome.Cancelled
            var left = ctx.manifest.length(idx).toLong()
            while (left > 0) {
                val n = minOf(left, 128L * 1024)
                if (!sleepUntilV(take(n), ctx)) return Outcome.Cancelled
                if (reports) ctx.onBytes(n)
                left -= n
            }
            sent.addAndGet(ctx.manifest.length(idx).toLong())
            return Outcome.Ok(ctx.manifest.length(idx).toLong())
        }
    }

    private class Counts { val confirmed = ArrayList<Long>(); val sent = ArrayList<Pair<Long, Long>>(); val sentAt = ArrayList<Long>(); val confirmedAt = ArrayList<Long>() }

    private fun first60s(reports: Boolean): Counts {
        val vc = VClock(); val c = Counts()
        val m = Manifest("film.avi", 348 * MIB, Manifest.blockSizeFor(348 * MIB))
        val lane = SlowWifi(vc, reports = reports)
        val s = Scheduler(m, BlockMap(m.blocks), listOf(lane), { cc -> SendContext(m, Src, HashBook(m, Src), cc, false) },
            clock = { vc.ns() }, sleepMs = { Thread.sleep(maxOf(1, it / SCALE)) }, listener = object : Scheduler.Listener() {
                override fun progress(doneBytes: Long, total: Long) { synchronized(c) { c.confirmed += doneBytes; c.confirmedAt += vc.ms() } }
                override fun sent(sentBytes: Long, confirmedBytes: Long, total: Long) { synchronized(c) { c.sent += sentBytes to confirmedBytes; c.sentAt += vc.ms() } }
            })
        s.run { vc.ms() >= 60_000 }
        return c
    }

    @Test fun blocksAreFourMegabytesForTheReferenceFile() {
        assertEquals(4 shl 20, Manifest.blockSizeFor(348 * MIB))
    }

    @Test fun beforeFix_noPublicationForTheFirstSecondsAndAlmostNoneInAMinute() {
        // le comportement d'avant : seule la confirmation d'un bloc entier (4 Mo à 150 Ko/s par flux = ~28 s) publie
        val c = first60s(reports = false)
        println("R-21 banc AVANT : publications de confirmation en 60 s = ${c.confirmed.size} ; première à ${c.confirmedAt.firstOrNull()} ms")
        assertTrue(c.confirmed.size <= 6, "confirmations : ${c.confirmed.size}")
        assertTrue((c.confirmedAt.firstOrNull() ?: Long.MAX_VALUE) > 10_000, "première confirmation : ${c.confirmedAt}")
    }

    @Test fun afterFix_progressIsPublishedFromTheFirstSecondsAndAtLeastEvery2Seconds() {
        val c = first60s(reports = true)
        println("R-21 banc APRÈS : publications envoyé/confirmé en 60 s = ${c.sent.size} ; première à ${c.sentAt.firstOrNull()} ms")
        assertTrue(c.sent.size >= 25, "publications : ${c.sent.size}")
        assertTrue(c.sentAt.first() <= 2_500, "première publication : ${c.sentAt.first()} ms")
        val gaps = c.sentAt.zipWithNext { a, b -> b - a }
        assertTrue(gaps.max() <= 3_000, "plus grand écart simulé : ${gaps.max()} ms")
        val sentSeq = c.sent.map { it.first }
        assertEquals(sentSeq.sorted(), sentSeq, "l'envoyé ne recule jamais")
        assertTrue(c.sent.all { it.second <= it.first }, "confirmé <= envoyé")
        // 300 Ko/s pendant 60 s : ~18 Mo envoyés, loin des 0 % d'avant
        assertTrue(c.sent.last().first in 12 * MIB..22 * MIB, "envoyé à 60 s : ${c.sent.last().first}")
    }

    // ---- une copie qui renvoie sans que rien ne soit confirmé s'arrête avec la cause ----

    private fun runWith(lane: Lane, blocks: Int = 4): Scheduler.Result {
        val m = Manifest("a.bin", blocks * MIB, MIB.toInt()); val ns = AtomicLong()
        return Scheduler(m, BlockMap(m.blocks), listOf(lane), { c -> SendContext(m, Src, HashBook(m, Src), c, false) },
            clock = { ns.get() }, sleepMs = { ns.addAndGet(it * 1_000_000 * 20); Thread.yield() }).run { false }
    }
    private fun laneOf(out: () -> Outcome) = object : Lane {
        override val id = "wifi"; override val maxWorkers = 1; override val sent = AtomicLong()
        override fun send(worker: Int, idx: Int, ctx: SendContext) = out()
    }

    @Test fun sameBlockResentThreeTimesWithoutConfirmationStopsWithTheCause() {
        val r = runWith(laneOf { Outcome.Busy(500, "stalled") })
        assertTrue(r is Scheduler.Result.Failed, r.toString())
        assertEquals(CopyCauses.noConfirm("stalled"), (r as Scheduler.Result.Failed).reason)
        assertTrue("disque de la TV" in r.reason && "relancez" in r.reason, r.reason)
    }

    @Test fun busyWithoutKnownCauseStillStopsWithAClearMessage() {
        val r = runWith(laneOf { Outcome.Busy(500) })
        assertEquals(CopyCauses.noConfirm(null), (r as Scheduler.Result.Failed).reason)
    }

    @Test fun twoBusyAnswersThenAConfirmationIsNotAFailure() {
        val n = AtomicInteger()
        val r = runWith(laneOf { if (n.incrementAndGet() % 3 != 0) Outcome.Busy(500) else Outcome.Ok(MIB) })
        assertEquals(Scheduler.Result.Done, r)
    }

    @Test fun writeFailuresAnsweredByTheTvAreFatalWithAClearReason() {
        val io = statusOutcome(500, """{"error":"write failed","cause":"io","detail":"EIO"}""", 0)
        assertTrue(io is Outcome.Failed && io.fatal && io.reason == CopyCauses.IO, io.toString())
        val ro = statusOutcome(503, """{"error":"volume read-only","cause":"readonly"}""", 0)
        assertTrue(ro is Outcome.Failed && ro.fatal && ro.reason == CopyCauses.READONLY, ro.toString())
        val st = statusOutcome(429, """{"error":"busy","cause":"stalled","retryMs":2000}""", 0)
        assertTrue(st is Outcome.Busy && st.cause == "stalled" && st.retryMs == 2000L, st.toString())
        // une TV 0.14.39 (sans champ cause) : comportement d'avant
        assertTrue(statusOutcome(429, """{"error":"busy","retryMs":300}""", 0).let { it is Outcome.Busy && it.cause == null })
        assertTrue(statusOutcome(503, """{"error":"volume read-only"}""", 0).let { it is Outcome.Failed && !it.fatal })
        assertTrue("lecture seule" in CopyCauses.READONLY && "TV ne peut pas écrire" in CopyCauses.IO)
    }

    // ---- la TV : disque bloqué, voies et sessions ----

    @Test fun diskStallIsSeenWhenBytesWaitAndNoWriteCompletes() {
        val t = AtomicLong(0)
        val st = WriteStats { t.get() }
        assertEquals(0, st.stalledMs())
        st.queue(4 * MIB)                                  // des octets attendent le disque
        t.set(16_000_000_000L)
        assertTrue(st.stalledMs() >= TransferHost.DISK_STALL_MS)
        st.record(262144, 1_000_000)                       // une écriture aboutit : plus bloqué
        assertTrue(st.stalledMs() < 1_000)
        st.queue(-4 * MIB); assertEquals(0, st.stalledMs())
    }

    @Test fun silentLaneIsClosedAfter30sAndItsSocketReleased() {
        val t = AtomicLong(0); var closed = 0
        val reg = LaneRegistry({ t.get() })
        val h = reg.register("s1") { closed++ }
        t.set(29_000); assertEquals(0, reg.sweep())
        h.touch(); t.set(58_000); assertEquals(0, reg.sweep(), "un octet lu a relancé le compte")
        t.set(60_000); assertEquals(1, reg.sweep()); assertEquals(1, closed); assertTrue(h.wasClosed); assertEquals(0, reg.open())
    }

    @Test fun restingKeepAliveLaneGetsTwoMinutes() {
        val t = AtomicLong(0); var closed = 0
        val reg = LaneRegistry({ t.get() })
        reg.register("s1") { closed++ }.idle()
        t.set(100_000); assertEquals(0, reg.sweep())
        t.set(120_000); assertEquals(1, reg.sweep()); assertEquals(1, closed)
    }

    @Test fun lanesPerSessionAreBoundedByAnnouncedMaxStreams() {
        val t = AtomicLong(0); val closedIds = ArrayList<Int>()
        val reg = LaneRegistry({ t.get() }, maxStreams = { 2 })
        for (i in 0 until 5) { t.set(i * 1000L); reg.register("s1") { closedIds += i } }
        reg.register("s2") { closedIds += 99 }
        assertEquals(3, reg.sweep())
        assertEquals(listOf(0, 1, 2), closedIds.sorted(), "les plus anciennes sont fermées")
        assertEquals(2, reg.openOf("s1")); assertEquals(1, reg.openOf("s2"))
    }

    @Test fun sessionIdleFor10MinutesIsClosedButItsStateStaysOnDiskForTheResume() {
        val dir = kotlin.io.path.createTempDirectory("r21").toFile()
        try {
            val t = AtomicLong(1_000_000)
            val h = TransferHost(maxStreams = 4, now = { t.get() })
            val m = Manifest("a.bin", 2 * MIB, MIB.toInt())
            val s = (h.begin(m) { Allocation.At(dir, "a.bin", "internal") } as TransferHost.Begin.Ok).s
            val body = ByteArray(MIB.toInt()) { 1 }
            val sha = Hash.hex(Hash.sha256(body))
            assertTrue(s.assembler.writeBlock(0, sha, body.inputStream(), body.size.toLong(), false) is PartAssembler.Block.Ok)
            t.addAndGet(9 * 60_000L); assertEquals(0, h.closeIdle())
            t.addAndGet(2 * 60_000L); assertEquals(1, h.closeIdle()); assertEquals(0, h.active())
            val again = h.begin(m) { Allocation.At(dir, "a.bin", "internal") } as TransferHost.Begin.Ok
            assertTrue(again.resumed && again.s.assembler.map.has(0), "le bloc reçu est repris")
        } finally { dir.deleteRecursively() }
    }

    // ---- la ligne d'état honnête ----

    private fun facts(link: LinkFacts?, pct: Int? = 10) = TransferFacts(pct, CopyRouteKind.WIFI, copying = true, slowed = false, waiting = false, failure = null, link = link)

    @Test fun slowLinkSaysTheRateAndTheRemainingTime() {
        val l = TransferStatusLine.of(facts(LinkFacts(10, 10, 300_000, 290_000, 360_000_000L)))
        assertEquals("Wi-Fi de la TV lent : ~300 Ko/s · ~20 min restantes", l.text)
        assertEquals(SignalLevel.ORANGE, l.level)
        assertFalse("introuvable" in l.text)
    }

    @Test fun twoNumbersWhenSentAndConfirmedDiffer() {
        val l = TransferStatusLine.of(facts(LinkFacts(74, 60, 300_000, 250_000, 90_000_000L)))
        assertTrue("74 % envoyés · 60 % confirmés" in l.text, l.text)
    }

    @Test fun goodRateButSlowConfirmationsBlamesTheDisk() {
        val l = TransferStatusLine.of(facts(LinkFacts(50, 30, 5_000_000, 1_000_000, 100_000_000L)))
        assertTrue(l.text.startsWith(TransferStatusLine.DISK_SLOW), l.text)
        assertEquals(SignalLevel.ORANGE, l.level)
    }

    @Test fun healthyCopyKeepsTheUsualLine() {
        assertEquals("Copie en cours · 65 % · par le Wi-Fi", TransferStatusLine.of(facts(LinkFacts(65, 65, 6_000_000, 6_000_000, 1L), 65)).text)
        assertEquals("Copie en cours · 65 % · par le Wi-Fi", TransferStatusLine.of(facts(null, 65)).text)
    }

    @Test fun roundingAndBoundsOfTheEstimate() {
        assertEquals("~300 Ko/s", TransferStatusLine.rateText(296_000))
        assertEquals("~10 Ko/s", TransferStatusLine.rateText(1_200))
        assertEquals("~45 s restantes", TransferStatusLine.remainingText(13_500_000, 300_000))
        assertEquals("~3 h restantes", TransferStatusLine.remainingText(3_240_000_000L, 300_000))
        assertNull(TransferStatusLine.remainingText(900_000_000_000L, 300_000), "plus de 48 h : pas d'estimation")
        assertNull(TransferStatusLine.remainingText(1_000, 0))
        assertEquals("Wi-Fi de la TV lent : ~50 Ko/s", TransferStatusLine.of(facts(LinkFacts(1, 1, 50_000, 0, 0))).text)
    }

    @Test fun slowedByPlaybackKeepsItsOwnExplanation() {
        val f = facts(LinkFacts(10, 10, 300_000, 300_000, 1_000_000)).copy(slowed = true)
        assertTrue(PlaybackAwareCopyPolicy.SLOWED_TEXT in TransferStatusLine.of(f).text)
        assertFalse("Wi-Fi de la TV lent" in TransferStatusLine.of(f).text)
    }

    @Test fun linkSpeedIsMeasuredOverTenSeconds() {
        val s = LinkSpeed()
        assertEquals(0, s.bytesPerSec())
        for (i in 0..20) s.add(i * 1000L, i * 300_000L)
        assertEquals(300_000, s.bytesPerSec())
        for (i in 21..30) s.add(i * 1000L, 20 * 300_000L)        // plus rien : la fenêtre de 10 s le voit
        assertEquals(0, s.bytesPerSec())
    }

    @Test fun slowConnectionSwitchesToSlices() {
        val lane = WifiLane("wifi", "x:1", "id", { throw java.io.IOException("no net") }, { null }, maxStreams = 2)
        assertFalse(lane.slicing(0, 4 * MIB))                        // pas encore mesuré
    }
}
