package castbridge.core.xfer

import castbridge.core.tv.Fs
import castbridge.core.tv.GrowingStream
import java.io.File
import java.io.IOException
import kotlin.random.Random
import kotlin.test.*

/**
 * Priorité à la lecture pendant une copie (docs/agent-reports/fluid-playback-during-copy.md) : la politique pure (tables lecture/repos, fenêtre
 * devant la tête de lecture, valeurs de bridage), le report des travaux non urgents (vidé à l'arrêt), et les points d'application (progression,
 * flux de réception, assembleur sans préallocation sur FAT/exFAT, flux de lecture d'un fichier qui grandit).
 */
class PlaybackPriorityTest {
    private val normal = PlaybackPriority.Normal(maxStreams = 6, syncEveryBytes = 64L shl 20)

    // ---- la table ----

    @Test fun idleTvKeepsFullSpeedAndEveryNormalValue() {
        val d = PlaybackPriority.decide(PlaybackSignal(), normal)
        assertFalse(d.on)
        assertTrue(d.reasons.isEmpty())
        assertFalse(d.backgroundThreads)
        assertEquals(6, d.maxStreams)
        assertEquals(0, d.receiveCapBps)
        assertEquals(PlaybackPriority.IDLE_PROGRESS_MS, d.progressEveryMs)
        assertEquals(64L shl 20, d.syncEveryBytes)
        assertEquals(0, d.verifyReadBps)
        assertEquals(PlaybackPriority.IDLE_PERSIST_MS, d.statePersistMs)
        assertEquals(PlaybackPriority.IDLE_RETRY_MS, d.busyRetryMs)
    }

    @Test fun pausedEndedOrErrorIsNotPlaying() {
        for (st in listOf("paused", "ended", "error", "idle"))
            assertFalse(PlaybackPriority.decide(PlaybackSignal(playerState = st, playingName = "a.mkv"), normal).on, st)
    }

    @Test fun playingALibraryFileThrottlesTheCopyAndLowersItsThreads() {
        val d = PlaybackPriority.decide(PlaybackSignal(playerState = "playing", playingName = "film.mkv", writeBps = 10_000_000), normal, "autre.mp4")
        assertTrue(d.on)
        assertTrue("player:playing" in d.reasons, d.reasons.toString())
        assertTrue(d.backgroundThreads)
        assertEquals(PlaybackPriority.PLAYING_MAX_STREAMS, d.maxStreams)
        assertEquals(5_000_000, d.receiveCapBps)                       // half of what the disk absorbs: the other half is the player's
        assertTrue(d.progressEveryMs >= 2_000)
        assertEquals(4 * (64L shl 20), d.syncEveryBytes)               // fsync deferred, but bounded
        assertEquals(PlaybackPriority.VERIFY_CAP_BPS, d.verifyReadBps)
        assertEquals(PlaybackPriority.PLAYING_PERSIST_MS, d.statePersistMs)
        assertTrue(d.busyRetryMs > PlaybackPriority.IDLE_RETRY_MS)
    }

    @Test fun bufferingCountsAsPlaying() {
        assertTrue(PlaybackPriority.decide(PlaybackSignal(playerState = "buffering", playingName = "a.mkv"), normal).on)
    }

    @Test fun receiveCapFollowsTheMeasuredDiskWithinBounds() {
        assertEquals(PlaybackPriority.UNKNOWN_DISK_CAP_BPS, PlaybackPriority.receiveCap(0))
        assertEquals(PlaybackPriority.MIN_CAP_BPS, PlaybackPriority.receiveCap(800_000))
        assertEquals(PlaybackPriority.MAX_CAP_BPS, PlaybackPriority.receiveCap(100_000_000))
        assertEquals(4_000_000, PlaybackPriority.receiveCap(8_000_000))
    }

    @Test fun neverMoreStreamsThanTheTvAllowsEvenWhenItAllowsFewer() {
        val d = PlaybackPriority.decide(PlaybackSignal(playerState = "playing", playingName = "a"), PlaybackPriority.Normal(1, 1L shl 20))
        assertEquals(1, d.maxStreams)
    }

    @Test fun theTransferFeedingAGrowingPlaybackIsNeverStarved() {
        val sig = PlaybackSignal(playerState = "playing", playingName = "Film.mkv", growing = true, durMs = 100_000, fileBytes = 100_000_000,
            playheadMs = 10_000, contiguousBytes = 40_000_000, feedBps = 3_000_000)
        val feed = PlaybackPriority.decide(sig, normal, "film.mkv")       // same name, other case: the same file
        assertTrue(feed.on)
        assertTrue("growing" in feed.reasons)
        assertFalse(feed.backgroundThreads)                                // the copy IS the playback's lifeline
        assertEquals(0, feed.receiveCapBps)
        assertTrue(feed.progressEveryMs >= 2_000)
        val other = PlaybackPriority.decide(sig, normal, "autre.mkv")      // another copy at the same time: throttled like any other
        assertTrue(other.backgroundThreads)
        assertTrue(other.receiveCapBps > 0)
    }

    @Test fun underrunRiskIsNamedWhenTheVideoNeedsMoreThanTheCopyBrings() {
        // 100 MB over 100 s = 1 MB/s of video, fed at 0.5 MB/s: the player WILL wait (cause D), whatever the CPU does
        val sig = PlaybackSignal(playerState = "playing", playingName = "v.mp4", growing = true, durMs = 100_000, fileBytes = 100_000_000, feedBps = 500_000)
        val d = PlaybackPriority.decide(sig, normal, "v.mp4")
        assertTrue("underrun-risk" in d.reasons, d.reasons.toString())
        assertEquals(1_000_000, PlaybackPriority.videoBps(100_000_000, 100_000))
        val ok = PlaybackPriority.decide(sig.copy(feedBps = 3_000_000), normal, "v.mp4")
        assertFalse("underrun-risk" in ok.reasons)
    }

    @Test fun leadAheadOfThePlayheadIsComputedFromTheContiguousPrefix() {
        // 1 MB/s of video, playhead at 10 s (10 MB), 25 MB contiguous: 15 s of lead
        assertEquals(15_000, PlaybackPriority.leadMs(contiguousBytes = 25_000_000, playheadMs = 10_000, fileBytes = 100_000_000, durMs = 100_000))
        assertEquals(0, PlaybackPriority.leadMs(5_000_000, 10_000, 100_000_000, 100_000))       // behind the playhead: no lead, never negative
        assertNull(PlaybackPriority.leadMs(5_000_000, 10_000, 100_000_000, 0))                    // duration unknown: unknown
    }

    @Test fun headFirstWindowAdmitsOnlyBlocksJustAfterTheContiguousPrefix() {
        val w = PlaybackPriority.HEAD_WINDOW_BYTES
        assertTrue(PlaybackPriority.admitAhead(blockOffset = 0, contiguousBytes = 0))
        assertTrue(PlaybackPriority.admitAhead(blockOffset = w - 1, contiguousBytes = 0))
        assertFalse(PlaybackPriority.admitAhead(blockOffset = w, contiguousBytes = 0))              // too far ahead: later
        assertTrue(PlaybackPriority.admitAhead(blockOffset = 100L shl 20, contiguousBytes = (100L shl 20) - 1))
        assertTrue(PlaybackPriority.admitAhead(blockOffset = 3, contiguousBytes = 50))              // behind the prefix: always (a resend)
    }

    @Test fun playerCachingIsLargerWhileACopyIsWrittenOrTheFileGrows() {
        assertEquals(400, PlaybackPriority.fileCachingMs(receiving = false))
        assertTrue(PlaybackPriority.fileCachingMs(receiving = true) >= 1_500)
        assertEquals(1_200, PlaybackPriority.networkCachingMs(growing = false))
        assertTrue(PlaybackPriority.networkCachingMs(growing = true) >= 3_000)
    }

    @Test fun preallocationOnlyWhereItIsFree() {
        assertTrue(PartAssembler.preallocates(Fs.EXT4))
        assertTrue(PartAssembler.preallocates(Fs.F2FS))
        for (fs in listOf(Fs.EXFAT, Fs.FAT32, Fs.NTFS, Fs.UNKNOWN)) assertFalse(PartAssembler.preallocates(fs), fs.name)   // FAT/exFAT write zeros for the whole size
    }

    // ---- pacing and deferral ----

    @Test fun pacerHoldsTheAverageRate() {
        var now = 0L; var slept = 0L
        val p = RatePacer(clock = { now }, sleep = { ms -> slept += ms; now += ms * 1_000_000 }, burstMs = 0)
        repeat(40) { p.pace(256 * 1024, 5_000_000) }                      // 10 MiB at 5 MB/s ≈ 2.1 s
        assertTrue(slept in 2_000..2_200, "slept $slept ms")
        slept = 0
        p.pace(1 shl 20, 0)                                                // 0 = no cap
        assertEquals(0, slept)
    }

    @Test fun deferredWorkIsKeyedAndDrainedOnce() {
        val q = DeferredWork()
        var runs = 0
        q.defer("sync:a") { runs++ }; q.defer("sync:a") { runs++ }; q.defer("sync:b") { runs++ }
        assertEquals(listOf("sync:a", "sync:b"), q.pending())
        assertEquals(2, q.drain())
        assertEquals(2, runs)
        assertEquals(0, q.drain())
        assertTrue(q.pending().isEmpty())
    }

    @Test fun governorDrainsTheDeferredWorkWhenPlaybackStops() {
        var t = 0L
        var sig = PlaybackSignal(playerState = "playing", playingName = "a.mkv")
        val g = PlaybackGovernor({ sig }, normal, clock = { t }, refreshMs = 500)
        assertTrue(g.current().on)
        var synced = 0
        g.deferred.defer("sync:x") { synced++ }
        t += 100; sig = PlaybackSignal()                    // stopped, but the cached decision is still fresh
        assertTrue(g.current().on)
        assertEquals(0, synced)
        t += 500
        assertFalse(g.current().on)                         // re-read: playback ended → the deferred fsync runs now
        assertEquals(1, synced)
        g.refresh(); assertEquals(1, synced)                // never twice
    }

    @Test fun governorRefreshForcesAReadAndPublishesTheProgressInterval() {
        var t = 0L
        var sig = PlaybackSignal()
        val seen = ArrayList<Long>()
        val g = PlaybackGovernor({ sig }, normal, clock = { t }, refreshMs = 10_000, onChange = { seen += it.progressEveryMs })
        g.current()
        sig = PlaybackSignal(playerState = "playing", playingName = "a")
        assertFalse(g.current().on)                         // cached
        assertTrue(g.refresh().on)                          // forced (the player said its state changed)
        assertEquals(listOf(PlaybackPriority.IDLE_PROGRESS_MS, PlaybackPriority.PLAYING_PROGRESS_MS), seen)
        assertTrue("\"on\":true" in g.json() && "player:playing" in g.json(), g.json())
    }

    @Test fun receiveLowersTheThreadWhilePlayingAndAlwaysRestoresIt() {
        var t = 0L
        var sig = PlaybackSignal(playerState = "playing", playingName = "film.mkv", writeBps = 4_000_000)
        val calls = ArrayList<String>()
        val port = object : ThreadPriorityPort { override fun background() { calls += "bg" }; override fun normal() { calls += "normal" } }
        var slept = 0L
        val g = PlaybackGovernor({ sig }, normal, port, clock = { t }, refreshMs = 500, sleep = { ms -> slept += ms; t += ms })
        g.receive("copie.mp4").use { rx ->
            assertEquals(listOf("bg"), calls)
            repeat(8) { rx.onBytes(1 shl 20) }                            // 8 MiB at 2 MB/s (half of 4 MB/s): paced
            assertTrue(slept >= 3_000, "slept $slept")
            sig = PlaybackSignal(); t += 1_000
            rx.onBytes(1)                                                 // playback stopped mid-copy: priority back at once
            assertEquals(listOf("bg", "normal"), calls)
        }
        assertEquals(listOf("bg", "normal"), calls)                       // close() does not restore twice
        calls.clear(); sig = PlaybackSignal(playerState = "playing", playingName = "film.mkv")
        g.refresh()
        try { g.receive("copie.mp4").use { throw IOException("network") } } catch (_: IOException) { }
        assertEquals(listOf("bg", "normal"), calls)                       // restored even when the request fails
    }

    @Test fun verifyReadBackIsPacedWhilePlayingButNotAtRest() {
        var t = 0L
        var sig = PlaybackSignal(playerState = "playing", playingName = "film.mkv")
        var slept = 0L
        val g = PlaybackGovernor({ sig }, normal, clock = { t }, refreshMs = 500, sleep = { ms -> slept += ms; t += ms })
        g.verify("copie.mp4").use { v -> repeat(24) { v.onBytes(1 shl 20) } }   // 24 MiB at 12 MB/s ≈ 2 s
        assertTrue(slept >= 1_500, "slept $slept")
        slept = 0; sig = PlaybackSignal(); g.refresh()
        g.verify("copie.mp4").use { v -> repeat(24) { v.onBytes(1 shl 20) } }
        assertEquals(0, slept)
    }

    // ---- points d'application ----

    @Test fun progressHonoursTheIntervalSetByThePolicy() {
        var t = 1_000L
        val events = ArrayList<TransferProgress.Item>()
        val p = TransferProgress(now = { t }, minEmitMs = 1000).also { it.addListener { i -> events += i } }
        p.emitEveryMs = 2_500
        p.begin("x", "a.mkv", 10_000_000, TransferProgress.Transport.WIFI, null)
        t += 1_500; p.advance("x", 1_000_000)
        assertEquals(1, events.size)                                     // 1.5 s < 2.5 s: no repaint of the notification/chip
        t += 1_100; p.advance("x", 2_000_000)
        assertEquals(2, events.size)
        t += 10; p.advance("x", 10_000_000)                              // the last byte is always announced
        assertEquals(3, events.size)
    }

    @Test fun hostLimitsConcurrentChunksToWhatThePolicyAllows() {
        val h = TransferHost(maxStreams = 6)
        assertEquals(6, h.allowedStreams())
        h.streamLimit = { 2 }; assertEquals(2, h.allowedStreams())
        h.streamLimit = { 0 }; assertEquals(1, h.allowedStreams())       // never zero: the copy always moves
        h.streamLimit = { 99 }; assertEquals(6, h.allowedStreams())
    }

    @Test fun assemblerWithoutPreallocationWritesOutOfOrderAndVerifies() {
        val dir = kotlin.io.path.createTempDirectory("pp").toFile()
        try {
            val data = Random(7).nextBytes((3 shl 20) + 1234)
            val m = Manifest("v.mkv", data.size.toLong(), 1 shl 20)
            val a = PartAssembler.open(dir, m, preallocate = false)
            val data0 = File(File(dir, PartAssembler.SUB), m.id + ".data")
            assertEquals(0, data0.length())                              // no zero-filled 2 GB before the first byte on FAT/exFAT
            val hashes = (0 until m.blocks).map { i -> Hash.hex(Hash.sha256(data.copyOfRange(m.offset(i).toInt(), (m.offset(i) + m.length(i)).toInt()))) }
            for (i in listOf(2, 0, 3, 1)) {
                val b = data.copyOfRange(m.offset(i).toInt(), (m.offset(i) + m.length(i)).toInt())
                var paced = 0
                assertIs<PartAssembler.Block.Ok>(a.writeBlock(i, hashes[i], b.inputStream(), b.size.toLong(), false, pace = { paced += it }))
                assertEquals(b.size, paced)                               // every byte read from the network goes through the governor
            }
            var verified = 0L
            val r = a.finish(Manifest.root(hashes), "v.mkv", pace = { verified += it })
            assertIs<PartAssembler.Finish.Done>(r)
            assertEquals(data.size.toLong(), verified)                    // the whole read-back is paced
            assertContentEquals(data, r.partFile.readBytes())
        } finally { dir.deleteRecursively() }
    }

    @Test fun aShortDataFileMeansBlocksToResendNeverADiskFailure() {
        val dir = kotlin.io.path.createTempDirectory("pp").toFile()
        try {
            val data = Random(8).nextBytes(2 shl 20)
            val m = Manifest("w.mkv", data.size.toLong(), 1 shl 20)
            val a = PartAssembler.open(dir, m, preallocate = false)
            val hashes = (0 until m.blocks).map { i -> Hash.hex(Hash.sha256(data.copyOfRange(m.offset(i).toInt(), (m.offset(i) + m.length(i)).toInt()))) }
            for (i in 0 until m.blocks) { val b = data.copyOfRange(m.offset(i).toInt(), (m.offset(i) + m.length(i)).toInt()); a.writeBlock(i, hashes[i], b.inputStream(), b.size.toLong(), false) }
            java.io.RandomAccessFile(File(File(dir, PartAssembler.SUB), m.id + ".data"), "rw").use { it.setLength((1 shl 20) + 10) }   // the tail never reached the medium
            val r = a.finish(Manifest.root(hashes), "w.mkv")
            assertIs<PartAssembler.Finish.Corrupt>(r)
            assertEquals(listOf(1), r.blocks)
            assertFalse(a.map.has(1))                                     // forgotten: the phone resends it; nothing declared complete
            assertFalse(File(dir, "w.mkv.part").exists())
        } finally { dir.deleteRecursively() }
    }

    @Test fun growingStreamNeverReadsPastTheContiguousPrefix() {
        val dir = kotlin.io.path.createTempDirectory("pp").toFile()
        try {
            val data = Random(9).nextBytes(300_000)
            File(dir, "g.mkv.part").writeBytes(data)                     // the file is long (sparse, out-of-order blocks)...
            var prefix = 100_000L                                        // ...but only 100 000 bytes are known good
            var t = 0L
            val s = GrowingStream(dir, "g.mkv", 0, data.size - 1L, waitMs = 1_000, pollMs = 50, sleep = { ms -> t += ms; if (t >= 500) prefix = data.size.toLong() },
                clock = { t }, available = { prefix })
            val first = ByteArray(200_000); var n = 0
            while (n < 100_000) n += s.read(first, n, 200_000 - n)
            assertEquals(100_000, n)                                     // stopped exactly at the prefix
            assertEquals(0, t)
            val rest = s.readBytes()                                     // then waited politely for the prefix to grow
            assertTrue(t >= 500)
            assertContentEquals(data, first.copyOf(100_000) + rest)
        } finally { dir.deleteRecursively() }
    }

    @Test fun growingStreamWaitsLongerWhileTheCopyIsStillComing() {
        val dir = kotlin.io.path.createTempDirectory("pp").toFile()
        try {
            File(dir, "h.mkv.part").writeBytes(ByteArray(10))
            var t = 0L
            var coming = true
            val s = GrowingStream(dir, "h.mkv", 0, 99, waitMs = 1_000, pollMs = 100, sleep = { ms -> t += ms; if (t >= 5_000) coming = false },
                clock = { t }, stillComing = { coming }, maxWaitMs = 60_000)
            s.read(ByteArray(10), 0, 10)
            val e = assertFailsWith<IOException> { s.read(ByteArray(10), 0, 10) }
            assertTrue(t in 5_000..6_200, "gave up after $t ms (${e.message})")   // past waitMs while the copy was alive, then 1 s more
        } finally { dir.deleteRecursively() }
    }
}
