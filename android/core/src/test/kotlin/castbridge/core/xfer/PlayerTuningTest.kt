package castbridge.core.xfer

import castbridge.core.xfer.PlayerTuning.Facts
import kotlin.test.*

/** R-16 : réglage du lecteur pour petits processeurs, table de décision pure. Rien ici ne prouve l'effet sur la vraie TV (P-52). */
class PlayerTuningTest {
    private fun f(codec: String? = "mp4v", w: Int = 1280, h: Int = 720, hw: Boolean = true, copy: Boolean = true, distress: Int = 0, cores: Int = 4,
                  capable: Boolean? = null, heap: Long = 192L shl 20, is64: Boolean = false) =
        Facts(codec, w, h, hw, copy, distress, cores, capable, heap, is64)

    @Test fun atRestOnlyTheLateFramesRuleChangesAndNothingDegradesTheQuality() {
        val t = PlayerTuning.decide(f(copy = false))
        assertTrue(t.dropLateFrames, "the root cause of the freeze: libVLC must be allowed to drop late frames"); assertTrue(t.skipFrames)
        assertEquals(0, t.skipLoopFilter); assertEquals(0, t.threads)
        assertEquals(0, t.skipFrame); assertEquals(0, t.skipIdct)
    }

    @Test fun hardwareIsAskedForButForcedOnlyWhenTheProbeSaysTheTvCan() {
        assertTrue(PlayerTuning.decide(f()).hw); assertFalse(PlayerTuning.decide(f()).forceHw, "unknown capability: auto, never forced")
        assertTrue(PlayerTuning.decide(f(distress = 2)).hw)
        val capable = PlayerTuning.decide(f(capable = true)); assertTrue(capable.hw); assertTrue(capable.forceHw)
        val none = PlayerTuning.decide(f(capable = false)); assertFalse(none.hw); assertFalse(none.forceHw)
        val off = PlayerTuning.decide(f(hw = false, capable = true)); assertFalse(off.hw); assertFalse(off.forceHw, "the owner switched hardware off")
    }

    @Test fun aCopyRunningMakesTheDecoderDropLateFramesAndLeaveACore() {
        val t = PlayerTuning.decide(f(codec = "DIV3", cores = 4))
        assertTrue(t.dropLateFrames); assertTrue(t.skipFrames); assertEquals(3, t.threads)
        assertEquals(0, t.skipLoopFilter, "quality is never degraded at the start"); assertEquals(0, t.skipFrame); assertEquals(0, t.skipIdct)
        assertEquals(1, PlayerTuning.decide(f(cores = 1)).threads); assertEquals(1, PlayerTuning.decide(f(cores = 2)).threads)
    }

    @Test fun unknownCodecWithACopyStillDropsLateFrames() {
        val t = PlayerTuning.decide(f(codec = null))
        assertTrue(t.dropLateFrames); assertEquals(0, t.skipLoopFilter)
    }

    @Test fun distressOneDropsFramesButKeepsQuality() {
        val t = PlayerTuning.decide(f(distress = 1, copy = false))
        assertTrue(t.dropLateFrames); assertTrue(t.skipFrames); assertEquals(0, t.skipLoopFilter); assertEquals(0, t.skipIdct)
    }

    @Test fun sustainedDistressSkipsTheLoopFilterThenMore() {
        val t = PlayerTuning.decide(f(distress = 2))
        assertEquals(4, t.skipLoopFilter); assertTrue(t.skipFrame > 0); assertTrue(t.fileCachingMs >= 2_000)
        assertEquals(0, t.skipIdct, "idct skipping is the last resort: heavy software codec above 720p only")
        val big = PlayerTuning.decide(f(codec = "mp4v", w = 1920, h = 1080, distress = 2))
        assertTrue(big.skipIdct > 0)
    }

    @Test fun aHardwareFriendlyCodecIsNotDegradedBySoftwareTricksWithoutDistress() {
        val t = PlayerTuning.decide(f(codec = "h264", copy = true))
        assertEquals(0, t.skipLoopFilter); assertEquals(0, t.skipFrame)
        assertTrue(t.dropLateFrames, "dropping late frames never hurts: the picture must not freeze")
    }

    @Test fun threadsNeverExceedCoresMinusOneNorDropBelowOne() {
        for (c in 0..16) { val th = PlayerTuning.decide(f(cores = c)).threads; assertTrue(th in 1..maxOf(1, c - 1), "cores=$c threads=$th") }
    }

    // ---- profils de TV fictifs : la même table, des faits différents, aucun nom de décodeur dans le code ----

    @Test fun profileFullHardwareTvForcesMpeg4HardwareAndLeavesLibavcodecAlone() {
        val t = PlayerTuning.decide(f(codec = "xvid", capable = true, cores = 4))
        assertTrue(t.hw && t.forceHw); assertEquals(0, t.threads, "libavcodec is not used"); assertTrue(t.dropLateFrames)
        val sustained = PlayerTuning.decide(f(codec = "xvid", capable = true, distress = 2))
        assertEquals(0, sustained.skipLoopFilter, "libavcodec knobs are moot on a forced hardware decoder"); assertEquals(0, sustained.skipIdct)
    }

    @Test fun profileTvWithoutMpeg4HardwareFallsBackToSoftwareWithRelief() {
        val t = PlayerTuning.decide(f(codec = "mp4v", capable = false, cores = 4))
        assertFalse(t.hw); assertFalse(t.forceHw); assertEquals(3, t.threads); assertTrue(t.dropLateFrames)
        assertEquals(0, t.skipLoopFilter, "not at the start")
        val d = PlayerTuning.decide(f(codec = "mp4v", capable = false, distress = 2))
        assertEquals(4, d.skipLoopFilter); assertTrue(d.skipFrame > 0)
        assertTrue(PlayerTuning.decide(f(codec = "DIV3", capable = false)).let { !it.hw && it.threads == 3 })
    }

    @Test fun profileLowEndOneGigabyteTvKeepsItsMemoryAndItsCores() {
        val d = PlayerTuning.decide(f(codec = "mp4v", cores = 2, heap = 64L shl 20, capable = false, distress = 2))
        assertEquals(1, d.threads); assertTrue(d.fileCachingMs in 2_000..2_200, "read-ahead bounded by the heap: ${d.fileCachingMs}")
        assertTrue(PlayerTuning.decide(f(heap = 512L shl 20, distress = 2)).fileCachingMs <= 4_000)
    }

    @Test fun profileSixtyFourBitEightCoreTvNeverSpendsMoreThanFourDecoderThreads() {
        val t = PlayerTuning.decide(f(codec = "mp4v", cores = 8, is64 = true, capable = null))
        assertEquals(PlayerTuning.MAX_THREADS, t.threads); assertTrue(t.hw); assertFalse(t.forceHw)
    }

    // ---- fourcc libVLC -> type MIME -> capacité (sonde injectée) ----

    @Test fun codecMimeBridgeKnowsMpeg4AspAndMsMpeg4WithoutAnyDecoderName() {
        for (c in listOf("mp4v", "XVID", "divx", "DX50", "FMP4")) assertEquals("video/mp4v-es", CodecMime.mimeOf(c), c)
        assertEquals("video/avc", CodecMime.mimeOf("h264")); assertEquals("video/hevc", CodecMime.mimeOf("hevc"))
        assertNull(CodecMime.mimeOf("DIV3")); assertTrue(CodecMime.noAndroidDecoder("DIV3")); assertTrue(CodecMime.noAndroidDecoder("MP43"))
        assertFalse(CodecMime.noAndroidDecoder("xvid")); assertNull(CodecMime.mimeOf(null)); assertNull(CodecMime.mimeOf("zzzz"))
    }

    @Test fun hwCapableCombinesTheTableAndTheProbe() {
        val asked = ArrayList<String>()
        val probe = { m: String -> asked += m; m == "video/mp4v-es" }
        assertEquals(true, CodecMime.hwCapable("xvid", probe)); assertEquals(listOf("video/mp4v-es"), asked)
        assertEquals(false, CodecMime.hwCapable("h264", probe))
        assertEquals(false, CodecMime.hwCapable("DIV3", probe)); assertEquals(2, asked.size, "DIV3 never asks: no Android decoder exists")
        assertNull(CodecMime.hwCapable("zzzz", probe)); assertNull(CodecMime.hwCapable(null, probe)); assertNull(CodecMime.hwCapable("  ", probe))
        assertNull(CodecMime.hwCapable("xvid") { error("boom") }, "a failing probe is unknown, never a crash")
    }

    @Test fun softwareDecoderNamesAreRecognisedByGenericAndroidConventionsOnly() {
        for (n in listOf("c2.android.avc.decoder", "OMX.google.h264.decoder", "c2.google.mpeg4.decoder", "c2.vendor.vp8.decoder.sw")) assertTrue(CodecMime.isSoftwareName(n), n)
        for (n in listOf("c2.vendor.mpeg4.decoder", "OMX.vendor.video.decoder.avc")) assertFalse(CodecMime.isSoftwareName(n), n)
    }
}
