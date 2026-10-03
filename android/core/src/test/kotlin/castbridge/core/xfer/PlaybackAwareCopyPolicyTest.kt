package castbridge.core.xfer

import kotlin.test.*

/**
 * R-15 : « la vidéo lague pendant la copie vers la TV » alors qu'elle est déjà locale. Table pure de la copie qui ménage la lecture :
 * même disque ou non, plancher, reprise, compatibilité avec la détection de blocage du téléphone, et application par le porteur (temps simulé).
 */
class PlaybackAwareCopyPolicyTest {
    private val normal = PlaybackPriority.Normal(maxStreams = 6, syncEveryBytes = 64L shl 20)
    private fun playing(vol: String? = "usb", writeBps: Long = 10_000_000) =
        PlaybackSignal(playerState = "playing", playingName = "film.mkv", writeBps = writeBps, playingVolumeId = vol)

    @Test fun nothingPlayingMeansNoPacingAtAll() {
        val d = PlaybackPriority.decide(PlaybackSignal(), normal, "gros.iso", "usb")
        assertFalse(d.on); assertEquals(0, d.receiveCapBps); assertEquals(0, d.verifyReadBps); assertFalse(d.backgroundThreads)
    }

    @Test fun sameDiskCapsTheCopyAtFortyPercentOfTheMeasuredWriteWithinTheBounds() {
        assertEquals(1_600_000, PlaybackAwareCopyPolicy.receiveCap(4_000_000, true))
        assertEquals(PlaybackAwareCopyPolicy.SAME_VOLUME_MAX_BPS, PlaybackAwareCopyPolicy.receiveCap(50_000_000, true))
        assertEquals(PlaybackAwareCopyPolicy.FLOOR_BPS, PlaybackAwareCopyPolicy.receiveCap(1_000_000, true))
        assertEquals(PlaybackAwareCopyPolicy.SAME_VOLUME_UNKNOWN_BPS, PlaybackAwareCopyPolicy.receiveCap(0, true))
    }

    @Test fun theCapIsNeverBelowTheFloorSoACopyAlwaysFinishes() {
        for (w in listOf(0L, 1L, 10_000L, 400_000L, 1_000_000L, 5_000_000L, 100_000_000L))
            assertTrue(PlaybackAwareCopyPolicy.receiveCap(w, true) >= PlaybackAwareCopyPolicy.FLOOR_BPS, "writeBps=$w")
        assertEquals(512_000L, PlaybackAwareCopyPolicy.FLOOR_BPS)
    }

    @Test fun sameVolumeTruthTable() {
        assertEquals(true, PlaybackAwareCopyPolicy.sameVolume("usb", "usb"))
        assertEquals(false, PlaybackAwareCopyPolicy.sameVolume("usb", "internal"))
        assertNull(PlaybackAwareCopyPolicy.sameVolume(null, "usb"))
        assertNull(PlaybackAwareCopyPolicy.sameVolume("usb", null))
    }

    @Test fun playingFromTheTargetDiskPacesTheDiskAndKeepsTheCpuRelief() {
        val d = PlaybackPriority.decide(playing("usb"), normal, "autre.mp4", "usb")
        assertTrue(d.on); assertTrue(d.backgroundThreads)
        assertEquals(PlaybackAwareCopyPolicy.SAME_VOLUME_MAX_BPS, d.receiveCapBps)          // 40 % of 10 MB/s = 4 MB/s, bounded to 3 MB/s
        assertEquals(PlaybackAwareCopyPolicy.SAME_VOLUME_VERIFY_BPS, d.verifyReadBps)       // slowed, never skipped
        assertEquals(PlaybackPriority.PLAYING_MAX_STREAMS, d.maxStreams)
        assertEquals(64L shl 20 shl 2, d.syncEveryBytes)
    }

    @Test fun anotherDiskIsNotPacedButStillGetsBackgroundThreadsAndFewerStreams() {
        val d = PlaybackPriority.decide(playing("usb"), normal, "autre.mp4", "internal")
        assertTrue(d.on); assertTrue(d.backgroundThreads)
        assertEquals(0, d.receiveCapBps); assertEquals(0, d.verifyReadBps)
        assertEquals(PlaybackPriority.PLAYING_MAX_STREAMS, d.maxStreams)
        assertEquals(64L shl 20, d.syncEveryBytes)                                           // the fsync is on a disk the player does not read
    }

    @Test fun anUnknownVolumeKeepsTheR06Values() {
        val d = PlaybackPriority.decide(playing(null), normal, "autre.mp4", null)
        assertEquals(PlaybackPriority.receiveCap(10_000_000), d.receiveCapBps)
        assertEquals(PlaybackPriority.VERIFY_CAP_BPS, d.verifyReadBps)
    }

    @Test fun fullSpeedComesBackWhenPlaybackStops() {
        val on = PlaybackPriority.decide(playing("usb"), normal, "x", "usb")
        assertTrue(on.receiveCapBps > 0)
        for (st in listOf("paused", "ended", "idle", "error")) {
            val off = PlaybackPriority.decide(playing("usb").copy(playerState = st), normal, "x", "usb")
            assertFalse(off.on, st); assertEquals(0, off.receiveCapBps, st); assertFalse(off.backgroundThreads, st)
        }
    }

    @Test fun theCopyThatFeedsAGrowingPlaybackIsNeverPaced() {
        val s = playing("usb").copy(growing = true, playingName = "film.mkv")
        val d = PlaybackPriority.decide(s, normal, "film.mkv", "usb")
        assertEquals(0, d.receiveCapBps); assertFalse(d.backgroundThreads)
    }

    @Test fun aPacedTransferIsNeverDeclaredStalledByThePhone() {
        // worst case: a 1 MiB piece at the floor shared by 2 connections, after the longest hold the buffer loop may impose
        val gap = PlaybackAwareCopyPolicy.worstProgressGapMs() + PlaybackAwareCopyPolicy.MAX_HOLD_MS
        assertTrue(gap < HttpConn.DEFAULT_STALL_MS, "gap=$gap ms vs phone watchdog ${HttpConn.DEFAULT_STALL_MS} ms")
        assertTrue(gap > PlaybackAwareCopyPolicy.MAX_HOLD_MS)
        assertTrue(PlaybackAwareCopyPolicy.MAX_HOLD_MS < 30_000)
        assertEquals("Copie ralentie pour ne pas gêner la lecture", PlaybackAwareCopyPolicy.SLOWED_TEXT)
    }

    @Test fun theReadAheadGrowsDuringACopyWithinTheMemoryOfTheTv() {
        assertEquals(400, PlaybackPriority.fileCachingMs(false, 256L shl 20))
        assertEquals(4_000, PlaybackPriority.fileCachingMs(true, 192L shl 20))
        assertTrue(PlaybackPriority.fileCachingMs(true, 64L shl 20) in 2_000..2_200)   // 4 MiB of budget: about the old 2 s, not more
        assertEquals(2_000, PlaybackPriority.fileCachingMs(true, 0))
        for (mem in listOf(16L shl 20, 64L shl 20, 128L shl 20, 512L shl 20, 4096L shl 20))
            assertTrue(PlaybackPriority.fileCachingMs(true, mem) in 2_000..4_000)
    }

    // ---- le porteur : volume cible, temps simulé, texte honnête ----

    private class Fake { var now = 1_000_000L; val slept = arrayListOf<Long>(); fun sleep(ms: Long) { slept += ms; now += ms } }

    @Test fun theGovernorPacesTheSameDiskCopyAndNotTheOtherOne() {
        val f = Fake()
        val g = PlaybackGovernor({ playing("usb", 4_000_000) }, normal, clock = { f.now }, sleep = f::sleep, drainer = { it() })
        g.receive("a.mp4", "usb").use { rx -> repeat(8) { rx.onBytes(256 * 1024) } }
        val sameSlept = f.slept.sum()
        assertTrue(sameSlept > 500, "paced: slept $sameSlept ms")
        f.slept.clear(); f.now += 60_000
        val g2 = PlaybackGovernor({ playing("usb", 4_000_000) }, normal, clock = { f.now }, sleep = f::sleep, drainer = { it() })
        g2.receive("a.mp4", "internal").use { rx -> repeat(8) { rx.onBytes(256 * 1024) } }
        assertEquals(0L, f.slept.sum())
    }

    @Test fun theSlowedNoticeIsShownOnlyWhileTheCopyIsReallySlowedAndNeverAtRest() {
        val f = Fake()
        var sig = playing("usb", 4_000_000)
        val g = PlaybackGovernor({ sig }, normal, clock = { f.now }, sleep = f::sleep, drainer = { it() })
        assertFalse(g.slowedNow())
        g.receive("a.mp4", "usb").use { rx -> repeat(8) { rx.onBytes(256 * 1024) } }
        assertTrue(g.slowedNow())
        f.now += 10_000
        assertFalse(g.slowedNow(), "no copy byte was held back for 10 s: the notice is gone")
        sig = PlaybackSignal()
        g.receive("a.mp4", "usb").use { rx -> repeat(8) { rx.onBytes(256 * 1024) } }
        assertFalse(g.slowedNow())
    }

    @Test fun theTvCardAndTheLineSayTheCopyIsSlowedOnlyWhileItIsAndTheCopyKeepsGoing() {
        var t = 0L; var slowed = true
        val seen = arrayListOf<TransferProgress.Item>()
        val p = TransferProgress(now = { t }, minEmitMs = 1000).also { it.addListener { i -> seen += i }; it.slowedNow = { slowed } }
        p.begin("a", "gros.iso", 1000, TransferProgress.Transport.WIFI, null)
        t += 2_000; p.advance("a", 200)
        assertTrue(seen.last().detail().contains("Copie ralentie pour ne pas gêner la lecture"), seen.last().detail())
        assertTrue(seen.last().screenLine().contains("Copie ralentie"))
        slowed = false; t += 2_000; p.advance("a", 400)
        assertFalse(seen.last().detail().contains("ralentie"))
        assertEquals(400, seen.last().received)
    }

    @Test fun thePhoneReadsTheSlowedNoticeFromTheTransferStateOfTheTv() {
        val h = TransferHost(maxStreams = 4)
        var slowed = true
        h.slowedNote = { if (slowed) PlaybackAwareCopyPolicy.SLOWED_TEXT else null }
        val m = Manifest.of("a.bin", 4L shl 20)
        val dir = java.nio.file.Files.createTempDirectory("cbslow").toFile().also { it.deleteOnExit() }
        val sess = (h.begin(m) { Allocation.At(dir, "a.bin", "vol") } as TransferHost.Begin.Ok).s
        assertTrue(h.stateJson(sess).contains("Copie ralentie pour ne pas gêner la lecture"))
        slowed = false
        assertFalse(h.stateJson(sess).contains("ralentie"))
    }
}
