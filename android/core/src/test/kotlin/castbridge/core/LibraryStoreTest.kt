package castbridge.core

import castbridge.core.tv.*
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

class LibraryDbTest {
    private val dir = kotlin.io.path.createTempDirectory("libdb").toFile()
    private var clock = 1_000L
    private fun db() = LibraryDb(File(dir, "library.db"), maxEntries = 3) { clock }

    @AfterTest fun tearDown() { dir.deleteRecursively() }

    @Test fun positionsAreNormalisedAndSurviveARestart() {
        val d = db()
        d.onPlayStarted("a.mp4", 10)
        d.onStopped("a.mp4", 10, 120_000, 600_000)
        assertEquals(LibraryDb.Entry(600_000, 120_000, false, 1_000), d.get("a.mp4", 10))
        d.onStopped("a.mp4", 10, 5_000, 0)               // barely started: from the start, duration kept
        assertEquals(0, d.get("a.mp4", 10)!!.resumeMs); assertEquals(600_000, d.get("a.mp4", 10)!!.durationMs)
        d.onStopped("a.mp4", 10, 590_000, 600_000)       // credits: watched, nothing to resume
        assertTrue(d.get("a.mp4", 10)!!.watched); assertEquals(0, d.get("a.mp4", 10)!!.resumeMs)
        d.setPrefs("a.mp4", 10, "a=2\tb")                 // tab inside a value must not break the file
        val again = db()
        assertEquals(d.get("a.mp4", 10), again.get("a.mp4", 10))
        assertNull(again.get("a.mp4", 11), "same name, other size = other file")
    }

    @Test fun endWatchedRenameDelete() {
        val d = db()
        d.onStopped("x.mkv", 5, 60_000, 100_000); d.onEnded("x.mkv", 5, 0)
        assertEquals(0, d.get("x.mkv", 5)!!.resumeMs); assertTrue(d.get("x.mkv", 5)!!.watched)
        d.setWatched("x.mkv", 5, false); assertFalse(d.get("x.mkv", 5)!!.watched)
        d.renamed("x.mkv", "y.mkv", 5); assertNull(d.get("x.mkv", 5)); assertNotNull(d.get("y.mkv", 5))
        d.deleted("y.mkv", 5); assertNull(db().get("y.mkv", 5))
    }

    @Test fun boundedByDroppingTheLongestUnplayed() {
        val d = db()
        for ((i, n) in listOf("a", "b", "c", "d").withIndex()) { clock = 1000L + i; d.onPlayStarted(n, 1) }
        assertEquals(3, d.size()); assertNull(d.get("a", 1)); assertNotNull(d.get("d", 1))
    }

    @Test fun corruptLinesAreIgnored() {
        File(dir, "library.db").writeText("garbage\nx\t1\t2\n1%3Aok\t10\t0\t0\t5\t\n")
        assertEquals(10, db().get("ok", 1)!!.durationMs)
    }
}

class ThumbCacheTest {
    private val dir = kotlin.io.path.createTempDirectory("thumbs").toFile()
    private var clock = 1_000_000L
    @AfterTest fun tearDown() { dir.deleteRecursively() }

    @Test fun keyChangesWithSizeAndModificationTime() {
        val c = ThumbCache(dir)
        assertNotEquals(c.key("a", 1, 1), c.key("a", 2, 1)); assertNotEquals(c.key("a", 1, 1), c.key("a", 1, 2))
        assertEquals(c.key("a", 1, 1), c.key("a", 1, 1)); assertTrue(c.key("../x", 1, 1).matches(Regex("[0-9a-f]{40}")))
    }

    @Test fun lruStaysUnderTheByteLimit() {
        val c = ThumbCache(dir, maxBytes = 10_000) { clock }
        for (i in 0 until 3) { clock += 1000; c.put("k$i", ByteArray(4000)) }     // 12 000 > 10 000: the oldest goes
        assertFalse(c.has("k0")); assertTrue(c.has("k1")); assertTrue(c.has("k2"))
        clock += 1000; assertNotNull(c.get("k1"))                                      // k1 used: now k2 is the oldest
        clock += 1000; c.put("k3", ByteArray(4000))
        assertTrue(c.has("k1")); assertFalse(c.has("k2")); assertTrue(c.has("k3"))
        assertTrue(c.usedBytes() <= 10_000)
    }

    @Test fun failMarkersAreCountedAndClearedBySuccess() {
        val c = ThumbCache(dir, maxBytes = 3_000) { clock }
        c.markFailed("f"); assertTrue(c.failed("f"))
        c.put("f", ByteArray(10)); assertFalse(c.failed("f")); assertTrue(c.has("f"))
        repeat(5) { clock += 10; c.markFailed("m$it") }
        assertTrue(dir.listFiles()!!.size <= 3, "fail markers cost 1 kB each: they cannot pile up")
    }
}

class ThumbWorkerTest {
    private val dir = kotlin.io.path.createTempDirectory("tw").toFile()
    @AfterTest fun tearDown() { dir.deleteRecursively() }

    private fun job(c: ThumbCache, n: String) = ThumbJob(c.key(n, 1, 1), n, 1, File(dir, n))

    @Test fun oneAtATimeAndOnlyWhenAllowed() {
        val c = ThumbCache(File(dir, "c"))
        val concurrent = AtomicInteger(); val maxSeen = AtomicInteger()
        var allowed = false
        val done = CountDownLatch(5)
        val w = ThumbWorker(c, { j ->
            val n = concurrent.incrementAndGet(); maxSeen.accumulateAndGet(n, ::maxOf)
            Thread.sleep(20); concurrent.decrementAndGet()
            if (j.name == "bad") null else ThumbResult(byteArrayOf(1, 2), 1234)
        }, canRun = { allowed }, onDone = { _, _ -> done.countDown() }, pollMs = 20)
        listOf("a", "b", "c", "d", "bad").forEach { w.request(job(c, it)) }
        w.request(job(c, "a"))                              // duplicate
        Thread.sleep(150)
        assertEquals(5, done.count.toInt(), "nothing while a video plays")
        allowed = true
        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertEquals(1, maxSeen.get(), "never two decodes at once")
        assertTrue(c.has(job(c, "a").key)); assertTrue(c.failed(job(c, "bad").key))
        w.request(job(c, "bad")); assertEquals(0, w.pending(), "a failed file is not retried")
    }

    @Test fun generatorCrashIsContained() {
        val c = ThumbCache(File(dir, "c"))
        val w = ThumbWorker(c, { throw OutOfMemoryError("big frame") }, startThread = { })
        w.runOne(job(c, "x")); assertTrue(c.failed(job(c, "x").key))
    }

    @Test fun queueIsBounded() {
        val c = ThumbCache(File(dir, "c"))
        val w = ThumbWorker(c, { null }, maxQueue = 3, startThread = { })
        (1..10).forEach { w.request(job(c, "f$it")) }
        assertEquals(3, w.pending())
    }
}

class LibrarySectionsTest {
    private data class E(override val name: String, override val mtime: Long, override val resumeMs: Long = 0, override val watched: Boolean = false,
                         override val playedAtMs: Long = 0) : LibraryEntry {
        override val title get() = LibraryLogic.title(name)
        override val type get() = MediaType.of(name)
    }

    @Test fun sectionsAndOrder() {
        val items = listOf(E("b.mp4", 1), E("a.mkv", 2, resumeMs = 50_000, playedAtMs = 10), E("c.mp4", 3, resumeMs = 70_000, playedAtMs = 20),
            E("done.mp4", 4, resumeMs = 0, watched = true), E("notes.pdf", 5), E("app.apk", 6), E("song.mp3", 7))
        val s = LibrarySections.build(items, recentCount = 4)
        assertEquals(listOf("resume", "recent", "all", "other"), s.map { it.id })
        assertEquals(listOf("c.mp4", "a.mkv"), s[0].items.map { it.name }, "last played first")
        assertEquals(listOf("song.mp3", "done.mp4", "c.mp4", "a.mkv"), s[1].items.map { it.name }, "newest media first")
        assertEquals(listOf("a.mkv", "b.mp4", "c.mp4", "done.mp4", "song.mp3"), s[2].items.map { it.name })
        assertEquals(listOf("app.apk", "notes.pdf"), s[3].items.map { it.name })
    }

    @Test fun emptySectionsAreLeftOutAndSmallLibrariesHaveNoRecentRow() {
        val s = LibrarySections.build(listOf(E("a.mp4", 1)))
        assertEquals(listOf("all"), s.map { it.id })
        assertTrue(LibrarySections.build(emptyList<E>()).isEmpty())
    }

    @Test fun typesAndThumbTime() {
        assertEquals(MediaType.VIDEO, MediaType.of("X.MKV")); assertEquals(MediaType.AUDIO, MediaType.of("a.flac"))
        assertEquals(MediaType.OTHER, MediaType.of("a.apk")); assertEquals(MediaType.OTHER, MediaType.of("noext"))
        assertEquals(5_000, LibraryLogic.thumbTimeMs(0)); assertEquals(60_000, LibraryLogic.thumbTimeMs(600_000))
        assertEquals(300_000, LibraryLogic.thumbTimeMs(10 * 3600_000L)); assertEquals(1_000, LibraryLogic.thumbTimeMs(4_000))
        assertEquals(250, LibraryLogic.thumbTimeMs(500))
    }
}

class LibraryProviderServerTest {
    private val dir = kotlin.io.path.createTempDirectory("libp").toFile()
    private val videos = File(dir, "videos").apply { mkdirs() }
    private val port = ServerSocket(0).use { it.localPort }
    private val generated = CountDownLatch(1)
    private val provider = LibraryProvider(LibraryDb(File(dir, "db")), ThumbCache(File(dir, "thumbs"))) { p ->
        ThumbWorker(p.cache, { j -> if (j.name.startsWith("broken")) null else ThumbResult(byteArrayOf(9, 9), 42_000) },
            onDone = { j, r -> p.onGenerated(j, r); generated.countDown() })
    }
    private val server = ReceiverServer(VolumeRegistry.single(videos), FakePlayer(), port, pin = "123456", library = provider).apply { start(5000, false) }
    private val tv = TvClient("http://127.0.0.1:$port", "123456")

    @AfterTest fun tearDown() { server.stop(); dir.deleteRecursively() }

    @Test fun thumbnailIsMadeOnDemandThenServedWithTheDuration() {
        File(videos, "film.mp4").writeBytes(ByteArray(100))
        assertNull(tv.thumb("film.mp4"))                              // 202, generation queued
        assertTrue(generated.await(5, TimeUnit.SECONDS))
        assertContentEquals(byteArrayOf(9, 9), tv.thumb("film.mp4"))
        val j = tv.library()
        assertTrue(j.contains("\"durationMs\":42000") && j.contains("\"hasThumb\":true") && j.contains("\"type\":\"video\""), j)
    }

    @Test fun otherFilesHaveNoThumbnailAnd404() {
        File(videos, "notes.pdf").writeBytes(ByteArray(10))
        val c = java.net.URL("http://127.0.0.1:$port/api/thumb?name=notes.pdf").openConnection() as java.net.HttpURLConnection
        c.setRequestProperty("X-CB-Pin", "123456")
        assertEquals(404, c.responseCode)
        assertTrue(tv.library().contains("\"type\":\"other\""))
    }

    @Test fun watchedRenameAndDeleteKeepTheMetadataConsistent() {
        File(videos, "a.mp4").writeBytes(ByteArray(10))
        provider.db.onStopped("a.mp4", 10, 100_000, 1_000_000)
        assertTrue(tv.library().contains("\"resumeMs\":100000"))
        tv.setWatched("a.mp4", true)
        assertTrue(tv.library().contains("\"watched\":true") && tv.library().contains("\"resumeMs\":0"))
        tv.setWatched("a.mp4", false)
        tv.rename("a.mp4", "b.mp4")
        assertNotNull(provider.db.get("b.mp4", 10)); assertNull(provider.db.get("a.mp4", 10))
        tv.delete("b.mp4"); assertNull(provider.db.get("b.mp4", 10))
        assertFailsWith<TvClient.HttpError> { tv.setWatched("missing.mp4", true) }
    }

    @Test fun watchedNeedsThePin() {
        val c = java.net.URL("http://127.0.0.1:$port/api/library/watched?name=a.mp4").openConnection() as java.net.HttpURLConnection
        c.requestMethod = "POST"
        assertEquals(401, c.responseCode)
    }
}
