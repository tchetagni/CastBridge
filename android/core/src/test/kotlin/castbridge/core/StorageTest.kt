package castbridge.core

import castbridge.core.tv.*
import java.io.ByteArrayInputStream
import java.io.File
import java.net.ServerSocket
import kotlin.test.*

class StorageTest {
    private val dir = kotlin.io.path.createTempDirectory("st").toFile()
    private val player = FakePlayer()
    @AfterTest fun tearDown() { dir.deleteRecursively() }

    private fun server(p: TvProfile, onSettings: (TvProfile) -> Unit = {}): Pair<ReceiverServer, TvClient> {
        val port = ServerSocket(0).use { it.localPort }
        val s = ReceiverServer(dir, player, port, profile = p, onSettings = onSettings).apply { start(5000, false) }
        return s to TvClient("http://127.0.0.1:$port")
    }

    private fun w(name: String, text: String, mtime: Long? = null) { File(dir, name).apply { writeText(text); if (mtime != null) setLastModified(mtime) } }

    private fun up(tv: TvClient, name: String, size: Int) = tv.upload(name, 0, size.toLong(), ByteArrayInputStream(ByteArray(size))) {}

    @Test fun quotaRefusesBeforeWritingAnything() {
        val (s, tv) = server(TvProfile(minFreeBytes = 0, quotaBytes = 1000))
        try {
            up(tv, "a.mp4", 600)
            val e = assertFailsWith<TvClient.HttpError> { up(tv, "b.mp4", 600) }
            assertEquals(507, e.code); assertTrue(e.message!!.contains("quota"), e.message)
            assertFalse(File(dir, "b.mp4.part").exists(), "nothing written for a refused upload")
            up(tv, "c.mp4", 400)                       // exactly fits
        } finally { s.stop() }
    }

    @Test fun autoQuotaIsAFractionCappedAndInfoReportsIt() {
        val p = TvProfile(minFreeBytes = 0, quotaFraction = 0.5, quotaCapBytes = 10_000)
        assertEquals(10_000, Storage.quota(dir, p))
        val big = TvProfile(minFreeBytes = 0, quotaFraction = 0.5, quotaCapBytes = Long.MAX_VALUE)
        assertTrue(Storage.quota(dir, big) in 1..dir.usableSpace, "half of the free space")
        val (s, tv) = server(p)
        try {
            up(tv, "a.mp4", 100)
            val j = tv.info()
            assertEquals(100, TvClient.num(j, "used")); assertEquals(10_000, TvClient.num(j, "quota"))
        } finally { s.stop() }
    }

    @Test fun evictsOnlyPlayedFilesOldestFirstAndNeverTheOneInUse() {
        val (s, tv) = server(TvProfile(minFreeBytes = 0, quotaBytes = 1000, evictPlayed = true))
        try {
            up(tv, "old.mp4", 400); up(tv, "mid.mp4", 400)
            File(dir, "old.mp4").setLastModified(1_000_000); File(dir, "mid.mp4").setLastModified(2_000_000)
            // nothing has been played: no eviction possible
            assertEquals(507, assertFailsWith<TvClient.HttpError> { up(tv, "new.mp4", 400) }.code)
            tv.play("old.mp4"); tv.play("mid.mp4")                     // mid is the one playing now
            up(tv, "new.mp4", 400)                                       // old.mp4 (played, oldest) makes room
            assertFalse(File(dir, "old.mp4").exists()); assertTrue(File(dir, "mid.mp4").exists()); assertTrue(File(dir, "new.mp4").exists())
            assertEquals(507, assertFailsWith<TvClient.HttpError> { up(tv, "x.mp4", 900) }.code)
            assertTrue(File(dir, "mid.mp4").exists(), "the file being played survives")
        } finally { s.stop() }
    }

    @Test fun deleteAfterPlayAndSettingsApi() {
        var saved: TvProfile? = null
        val (s, tv) = server(TvProfile(minFreeBytes = 0)) { saved = it }
        try {
            up(tv, "a.mp4", 100)
            s.onPlaybackEnded("a.mp4"); assertTrue(File(dir, "a.mp4").exists(), "off by default")
            val j = tv.raw("POST", "/api/storage?deleteAfterPlay=true&evictPlayed=1&quotaMb=64")
            assertTrue(j.contains("\"deleteAfterPlay\":true") && j.contains("\"evictPlayed\":true") && j.contains("\"quotaMb\":64"), j)
            assertEquals(true, saved?.deleteAfterPlay); assertEquals(64L shl 20, saved?.quotaBytes)
            s.onPlaybackEnded("a.mp4"); assertFalse(File(dir, "a.mp4").exists())
            assertEquals(400, assertFailsWith<TvClient.HttpError> { tv.raw("POST", "/api/storage?quotaMb=abc") }.code)
        } finally { s.stop() }
    }

    @Test fun orphanPartsAndSidecarsAreCleanedAtStartup() {
        val old = System.currentTimeMillis() - 25L * 3600_000
        w("dead.mp4.part", "x", old)
        w("dead.mp4.meta", "10", old)
        w("fresh.mp4.part", "x")
        w("fresh.mp4.meta", "10")
        w("keep.mp4", "v", old)
        w("keep.mp4.meta", "1", old)   // meta next to a finished file is stale but harmless: kept until the file goes
        val (s, _) = server(TvProfile(minFreeBytes = 0))
        try {
            assertFalse(File(dir, "dead.mp4.part").exists()); assertFalse(File(dir, "dead.mp4.meta").exists())
            assertTrue(File(dir, "fresh.mp4.part").exists()); assertTrue(File(dir, "keep.mp4").exists())
        } finally { s.stop() }
    }

    @Test fun infoIsCachedBriefly() {
        val (s, tv) = server(TvProfile(minFreeBytes = 0, infoCacheMs = 60_000))
        try {
            File(dir, "a.mp4").writeText("aa")
            assertTrue(tv.info().contains("a.mp4"))
            File(dir, "b.mp4").writeText("bb")                            // external change: hidden by the cache...
            assertFalse(tv.info().contains("b.mp4"))
            tv.raw("POST", "/api/delete?name=zzz.mp4")                    // ...until any mutation through the API invalidates it
            assertTrue(tv.info().contains("b.mp4"))
        } finally { s.stop() }
    }

    @Test fun boundedRunnerServesMoreConnectionsThanThreads() {
        val (s, tv) = server(TvProfile(minFreeBytes = 0, maxHttpThreads = 2))
        try { repeat(20) { assertTrue(tv.info().contains("files")) } } finally { s.stop() }
    }
}

class ThrottleTest {
    @Test fun capsTheAverageRateAndIsExactlyOffWhenNotUsed() {
        var t = 1L; var slept = 0L
        val th = Throttle(1_000_000, now = { t }, sleep = { ms -> slept += ms; t += ms * 1_000_000 })
        repeat(10) { th.onBytes(500_000) }                // 5 MB at 1 MB/s must take ~5 s
        assertTrue(slept in 4900..5100, "slept $slept ms")
    }
}
