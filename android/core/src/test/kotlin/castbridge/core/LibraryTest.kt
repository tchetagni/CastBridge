package castbridge.core

import castbridge.core.tv.*
import java.io.File
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import kotlin.test.*

class LibraryLogicTest {
    @Test fun resumeIsZeroWhenBarelyStartedOrFinished() {
        assertEquals(0, LibraryLogic.resumeFrom(5_000, 600_000))
        assertEquals(120_000, LibraryLogic.resumeFrom(120_000, 600_000))
        assertEquals(0, LibraryLogic.resumeFrom(580_000, 600_000), "last 30 s = credits")
        assertEquals(0, LibraryLogic.resumeFrom(9_600_000, 10_000_000), "95 % of a long film")
        assertEquals(50_000, LibraryLogic.resumeFrom(50_000, 0), "unknown duration keeps the position")
    }

    @Test fun watchedAndProgress() {
        assertTrue(LibraryLogic.isWatched(590_000, 600_000)); assertFalse(LibraryLogic.isWatched(100_000, 600_000))
        assertFalse(LibraryLogic.isWatched(100_000, 0))
        assertEquals(0.5f, LibraryLogic.progress(300_000, 600_000)); assertEquals(0f, LibraryLogic.progress(5, 0))
        assertEquals(1f, LibraryLogic.progress(700_000, 600_000))
    }

    @Test fun clockAndTitle() {
        assertEquals("1:20", LibraryLogic.clock(80_000)); assertEquals("1:02:03", LibraryLogic.clock(3_723_000)); assertEquals("0:00", LibraryLogic.clock(-5))
        assertEquals("VID 20260929 134749 743", LibraryLogic.title("VID_20260929_134749_743.mp4"))
        assertEquals("Mon film", LibraryLogic.title("Mon__film.mkv")); assertEquals(".mp4", LibraryLogic.title(".mp4"))
    }

    @Test fun newestFirstThenAlphabetical() {
        val l = listOf("b" to 1L, "a" to 2L, "C" to 2L, "d" to 3L)
        assertEquals(listOf("d", "a", "C", "b"), LibraryLogic.sortNewestFirst(l, { it.second }, { it.first }).map { it.first })
    }
}

class LibraryServerTest {
    private val dir = kotlin.io.path.createTempDirectory("lib").toFile()
    private val port = ServerSocket(0).use { it.localPort }
    private val thumbs = mutableMapOf<String, ByteArray>()
    private val provider = object : LibraryMeta {
        override fun meta(name: String, size: Long) =
            if (name == "a.mp4") FileMeta(durationMs = 600_000, resumeMs = 120_000, hasThumb = "a.mp4" in thumbs, playedAtMs = 5) else FileMeta()
        override fun thumb(name: String, size: Long, file: File?) = thumbs[name]
    }
    private val server = ReceiverServer(VolumeRegistry.single(dir), FakePlayer(), port, pin = "123456", library = provider).apply { start(5000, false) }
    private val tv = TvClient("http://127.0.0.1:$port", "123456")

    @AfterTest fun tearDown() { server.stop(); dir.deleteRecursively() }

    private fun put(name: String, bytes: ByteArray, modified: Long) {
        File(dir, name).writeBytes(bytes); File(dir, name).setLastModified(modified)
    }

    @Test fun listsFinishedFilesNewestFirstWithMeta() {
        put("old.mp4", ByteArray(10), 1_000_000); put("a.mp4", ByteArray(20), 2_000_000)
        File(dir, "partial.mp4.part").writeBytes(ByteArray(5))
        val j = tv.library()
        assertEquals(2, TvClient.num(j, "count"))
        assertTrue(j.indexOf("\"a.mp4\"") < j.indexOf("\"old.mp4\""), "newest first: $j")
        assertTrue(j.contains("\"resumeMs\":120000") && j.contains("\"durationMs\":600000"))
        assertTrue(j.contains("\"title\":\"a\"") && j.contains("\"hasThumb\":false"))
        assertFalse(j.contains("partial"))
    }

    @Test fun thumbnailIsServedOrReportedNotReady() {
        put("a.mp4", ByteArray(20), 1_000)
        assertNull(tv.thumb("a.mp4"), "not ready yet -> 202")
        thumbs["a.mp4"] = byteArrayOf(1, 2, 3)
        assertContentEquals(byteArrayOf(1, 2, 3), tv.thumb("a.mp4"))
        assertNull(tv.thumb("missing.mp4"))
    }

    @Test fun libraryAndThumbNeedThePin() {
        put("a.mp4", ByteArray(20), 1_000)
        for (path in listOf("/api/library", "/api/thumb?name=a.mp4")) {
            val c = URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection
            assertEquals(401, c.responseCode, path)
        }
    }

    @Test fun pathTraversalInThumbName() {
        assertNull(tv.thumb("../secret.mp4")); assertNull(tv.thumb("a/b.mp4"))
    }
}
