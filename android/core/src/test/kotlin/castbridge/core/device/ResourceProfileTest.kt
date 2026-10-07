package castbridge.core.device

import castbridge.core.Rig
import castbridge.core.tv.PictureQuality
import castbridge.core.tv.TvProfile
import castbridge.core.tv.VideoFit
import castbridge.core.xfer.PlayerTuning
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val MB = 1L shl 20

/** Profil de ressources : TV 512 Mo / 32 bits / heap 96 Mo (économe) contre TV normale. Chaque borne est vérifiée (docs/TV-RESSOURCES-FAIBLES.md). */
class ResourceProfileTest {
    private fun box512() = ResourceProfile.decide(ramTotalBytes = 480 * MB, memoryClassMb = 96, isLowRamDevice = false, cores = 4, is64Bit = false, heapBytes = 96 * MB)
    private fun normalTv() = ResourceProfile.decide(ramTotalBytes = 2048 * MB, memoryClassMb = 256, isLowRamDevice = false, cores = 4, is64Bit = true, heapBytes = 256 * MB)

    @Test fun a512MbThirtyTwoBitBoxIsEconomical() {
        val p = box512()
        assertTrue(p.economy); assertEquals("low", p.capsName)
        assertEquals(2, p.maxStreams)
        assertEquals(5, p.httpThreads)
        assertEquals(32 * 1024, p.receiveBufferBytes)
        assertEquals(128 * 1024, p.writeBufferBytes)
        assertEquals(3 * MB, p.thumbCacheBytes)                                // heap 96 Mo / 8 = 12 Mo, capped at 3 Mo
        assertEquals(500, p.libraryEntries)
        assertEquals(20_000, p.indexEntries)
        assertTrue(p.lightPlayer); assertEquals(2_000, p.playerCachingCapMs)
        assertFalse(p.homeAnimations)
        assertTrue(p.releasePlayerInBackground)
        assertEquals(1, p.packsOpenAtOnce)
    }

    @Test fun aNormalTvKeepsEveryPreviousValue() {
        val p = normalTv()
        assertFalse(p.economy); assertEquals("normal", p.capsName)
        assertEquals(6, p.maxStreams)
        assertEquals(8, p.httpThreads)                                         // TvProfile.maxHttpThreads default
        assertEquals(TvProfile().maxHttpThreads, p.httpThreads)
        assertEquals(TvProfile().ioBufferBytes, p.receiveBufferBytes)
        assertEquals(TvProfile().uploadBufferBytes, p.writeBufferBytes)
        assertEquals(4 * MB, p.thumbCacheBytes)                                // the previous fixed LruCache
        assertEquals(2000, p.libraryEntries)
        assertEquals(100_000, p.indexEntries); assertEquals(TvProfile().indexEntries, p.indexEntries)
        assertFalse(p.lightPlayer); assertEquals(4_000, p.playerCachingCapMs)
        assertTrue(p.homeAnimations); assertFalse(p.releasePlayerInBackground)
        assertEquals(3, p.packsOpenAtOnce)
        assertEquals(p.maxStreams, maxOf(2, p.httpThreads - 2))               // unchanged relation
    }

    @Test fun unknownFactsMeanNormal() {
        val p = ResourceProfile.decide(0, 0, false, 4, false)
        assertFalse(p.economy)
        assertFalse(ResourceProfile.NORMAL.economy)
    }

    @Test fun eachCriterionAloneMakesItEconomical() {
        assertFalse(ResourceProfile.decide(0, 0, true, 8, true).economy)                      // isLowRamDevice SEUL : plus économe (M2)
        assertTrue(ResourceProfile.decide(768 * MB, 0, false, 8, true).economy)               // RAM <= 768 Mo
        assertFalse(ResourceProfile.decide(769 * MB, 0, false, 8, true).economy)
        assertTrue(ResourceProfile.decide(0, 96, false, 8, true).economy)                     // memoryClass <= 96
        assertFalse(ResourceProfile.decide(0, 97, false, 8, true).economy)
        assertTrue(ResourceProfile.decide(1024 * MB, 192, false, 2, false).economy)           // slow 32-bit with 2 cores and 1 Go
        assertFalse(ResourceProfile.decide(1024 * MB, 192, false, 4, false).economy)          // reference-like 1 Go TV with 4 cores stays normal
        assertFalse(ResourceProfile.decide(2048 * MB, 192, false, 2, false).economy)          // more RAM: not the 32-bit rule
    }

    /** M2 : valeurs MESURÉES sur la TV de référence (SMART_TV, GaiaOS) : le drapeau low_ram seul donne NORMAL, avec le seul cache de vignettes réduit. */
    @Test fun referenceTvMeasuredValuesAreNormalWithASmallerThumbCache() {
        val p = ResourceProfile.decide(ramTotalBytes = 981 * MB, memoryClassMb = 160, isLowRamDevice = true, cores = 4, is64Bit = false, heapBytes = 224 * MB)
        assertFalse(p.economy)
        assertEquals(6, p.maxStreams); assertEquals(8, p.httpThreads); assertEquals(64 * 1024, p.receiveBufferBytes)
        assertEquals(3 * MB, p.thumbCacheBytes, "heap/8 borné, plus petit que les 4 Mo d'avant")
        assertTrue(ResourceProfile.decide(512 * MB, 160, true, 4, false, 224 * MB).economy, "512 Mo : économe")
        assertEquals(4 * MB, ResourceProfile.decide(981 * MB, 160, false, 4, false, 224 * MB).thumbCacheBytes, "sans le drapeau : 4 Mo d'avant")
    }

    @Test fun thumbCacheFollowsTheHeapInsideBounds() {
        fun cache(heapMb: Int) = ResourceProfile.decide(480 * MB, 64, true, 4, false, heapMb * MB).thumbCacheBytes
        assertEquals(1 * MB, cache(4))
        assertEquals(2 * MB, cache(16))
        assertEquals(3 * MB, cache(64))
        assertEquals(3 * MB, cache(128))
    }

    @Test fun theLogLineAndTheInfoLineAreTheAgreedOnes() {
        assertEquals("profil ressources : économe (RAM 480 Mo, heap 96 Mo)", box512().logLine())
        assertEquals("profil ressources : normal (RAM ?, heap ?)", ResourceProfile.NORMAL.logLine())
        assertTrue(box512().infoLine().startsWith("économe · RAM 480 Mo · heap 96 Mo · 32 bits · 2 flux"), box512().infoLine())
        assertTrue(normalTv().infoLine().contains("64 bits"))
    }

    @Test fun theLightPlayerSkipsDeinterlaceGuessingAndSoftwareScaling() {
        fun q(light: Boolean, cores: Int = 8, interlaced: Boolean? = null, sw: Boolean? = true) = PictureQuality.decide(
            PictureQuality.Facts(VideoFit.Mode.FIT, "h264", 1920, 1080, interlaced, false, sw, cores, false, 0, 1280, 720, null, light))
        assertEquals(-1, q(false).deinterlace)                                  // before: automatic, « au cas où »
        assertEquals(0, q(true).deinterlace)
        assertEquals(PictureQuality.SWSCALE_LANCZOS, q(false).swscaleMode)
        assertEquals(null, q(true).swscaleMode)
        assertEquals(1, q(true, interlaced = true).deinterlace)                 // a track that says it is interlaced is still handled (blend, cheap)
        assertEquals("blend", q(true, cores = 8, interlaced = true).deinterlaceMode)
    }

    @Test fun theLightPlayerCapsTheReadAheadUnderDistress() {
        fun t(light: Boolean, cap: Int) = PlayerTuning.decide(PlayerTuning.Facts("h264", 1280, 720, true, true, 2, 4, null, 512L * MB, false, light, cap))
        assertEquals(4_000, t(false, 2_000).fileCachingMs)                      // normal TV: the previous value
        assertEquals(2_000, t(true, 2_000).fileCachingMs)
    }

    @Test fun capsAnnounceTheProfileOnlyWhenKnownAndMaxStreamsReflectsTheBound() {
        fun caps(p: TvProfile): String {
            val r = Rig(profile = p)
            try { return URL("${r.base}/api/transfer/caps").readText() } finally { r.close() }
        }
        val base = TvProfile(minFreeBytes = 0, minFreeAfterTransfer = 0)
        val low = caps(base.copy(maxTransferStreams = 2, maxHttpThreads = 5, resourceProfile = "low"))
        assertTrue(low.contains("\"maxStreams\":2") && low.contains("\"profile\":\"low\""), low)
        val normal = caps(base.copy(maxTransferStreams = 6, resourceProfile = "normal"))
        assertTrue(normal.contains("\"maxStreams\":6") && normal.contains("\"profile\":\"normal\""), normal)
        val old = caps(base)                                                    // an old TV (no profile): no field, 6 streams as before
        assertTrue(old.contains("\"maxStreams\":6") && !old.contains("profile"), old)
    }
}
