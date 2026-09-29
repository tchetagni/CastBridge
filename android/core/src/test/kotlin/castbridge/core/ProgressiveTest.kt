package castbridge.core

import castbridge.core.tv.*
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import kotlin.random.Random
import kotlin.test.*

class GrowingStreamTest {
    private val dir = kotlin.io.path.createTempDirectory("gs").toFile()
    private val data = Random(3).nextBytes(400_000)
    @AfterTest fun tearDown() { dir.deleteRecursively() }

    @Test fun followsASlowUploadAndSurvivesTheRenameToFinal() {
        val part = File(dir, "v.mp4.part")
        part.writeBytes(ByteArray(0))
        val writer = Thread {
            var off = 0
            while (off < data.size) {
                val n = minOf(50_000, data.size - off)
                java.io.FileOutputStream(part, true).use { it.write(data, off, n) }
                off += n; Thread.sleep(30)
            }
            part.renameTo(File(dir, "v.mp4"))      // upload complete while the reader is mid-file
        }.apply { start() }
        val got = GrowingStream(dir, "v.mp4", 0, data.size - 1L, waitMs = 10_000).readBytes()
        writer.join()
        assertContentEquals(data, got)
    }

    @Test fun readsARangeInTheMiddleOfAFinishedFile() {
        File(dir, "f.mkv").writeBytes(data)
        assertContentEquals(data.copyOfRange(1000, 90_001), GrowingStream(dir, "f.mkv", 1000, 90_000).readBytes())
    }

    @Test fun blocksThenTimesOutCleanly() {
        File(dir, "t.mp4.part").writeBytes(data.copyOf(100))
        var now = 0L
        val s = GrowingStream(dir, "t.mp4", 0, 999, waitMs = 30_000, sleep = { now += it }, clock = { now })
        val b = ByteArray(1000)
        assertEquals(100, s.read(b, 0, 1000))
        val e = assertFailsWith<IOException> { s.read(b, 0, 1000) }
        assertTrue(e.message!!.contains("timeout"))
        assertTrue(now >= 30_000)
    }

    @Test fun failsIfTheFileDisappears() {
        var now = 0L
        val s = GrowingStream(dir, "gone.mp4", 0, 10, sleep = { now += it }, clock = { now })
        assertFailsWith<IOException> { s.read(ByteArray(5), 0, 5) }
    }
}

class Mp4AtomsTest {
    private fun atom(type: String, payload: Int, big: Boolean = false): ByteArray {
        val size = 8 + payload
        val h = if (big) java.nio.ByteBuffer.allocate(16).putInt(1).put(type.toByteArray()).putLong(16L + payload).array()
        else java.nio.ByteBuffer.allocate(8).putInt(size).put(type.toByteArray()).array()
        return h + ByteArray(payload)
    }
    private fun layout(vararg atoms: ByteArray): Mp4Atoms.Layout {
        val f = atoms.reduce { a, b -> a + b }
        return Mp4Atoms.layout(f.size.toLong()) { off, len -> f.copyOfRange(off.toInt().coerceAtMost(f.size), (off + len).toInt().coerceAtMost(f.size)) }
    }

    @Test fun faststartIsDetected() = assertEquals(Mp4Atoms.Layout.FASTSTART, layout(atom("ftyp", 16), atom("moov", 5000), atom("mdat", 100_000)))
    @Test fun moovAtEndIsDetected() = assertEquals(Mp4Atoms.Layout.MOOV_AT_END, layout(atom("ftyp", 16), atom("free", 8), atom("mdat", 100_000), atom("moov", 5000)))
    @Test fun largeSizeAtomsAreSkippedCorrectly() = assertEquals(Mp4Atoms.Layout.MOOV_AT_END, layout(atom("ftyp", 16), atom("wide", 0), atom("mdat", 1000, big = true), atom("moov", 10)))
    @Test fun notAnMp4() = assertEquals(Mp4Atoms.Layout.NOT_ISO, layout("RIFF....AVI LIST".toByteArray()))
    @Test fun truncatedIsUnknown() = assertEquals(Mp4Atoms.Layout.UNKNOWN, layout(atom("ftyp", 16)))
    @Test fun names() { assertTrue(Mp4Atoms.isIsoName("a.MP4")); assertFalse(Mp4Atoms.isIsoName("a.mkv")) }
}

class ProgressiveMathTest {
    @Test fun bootstrap() {
        assertEquals(2L shl 20, Progressive.bootstrapBytes(100L shl 20, 100_000))
        assertEquals(3_000_000, Progressive.bootstrapBytes(100L shl 20, 1_000_000))
        assertEquals(500_000, Progressive.bootstrapBytes(500_000, 9_000_000), "never more than the file")
    }
    @Test fun reachable() {
        assertEquals(0, Progressive.reachableMs(600_000, 0, 1000))
        assertEquals(297_000, Progressive.reachableMs(600_000, 500, 1000))
        assertEquals(600_000, Progressive.reachableMs(600_000, 1000, 1000))
        assertEquals(0, Progressive.reachableMs(0, 5, 10))
    }
    @Test fun handoff() {
        assertEquals(500, Progressive.bytesForPosition(1000, 60_000, 30_000))
        assertEquals(1000, Progressive.bytesForPosition(1000, 60_000, 99_000))
        assertEquals(1000, Progressive.bytesForPosition(1000, 0, 5))
        val total = 600L shl 20
        // at 10 min of 60, +30 s lead: 10.5/60 of the file + the 2 MiB bootstrap
        assertTrue(Math.abs((total * 10.5 / 60).toLong() + (2L shl 20) - Progressive.handoffBytes(total, 3_600_000, 600_000, 30_000, false, 0)) <= 1)
        assertEquals(total, Progressive.handoffBytes(total, 3_600_000, 600_000, 30_000, true, 0), "MP4 without faststart: whole file")
        assertEquals(total, Progressive.handoffBytes(total, 3_600_000, 3_590_000, 30_000, false, 0), "never more than the file")
    }
    @Test fun stall() {
        assertTrue(Progressive.willStall(600L shl 20, 3_600_000, 100_000))     // 167 kB/s video vs 100 kB/s upload
        assertFalse(Progressive.willStall(600L shl 20, 3_600_000, 500_000))
    }
    @Test fun rateMeter() {
        var t = 1_000_000_000L
        val m = RateMeter { t }
        m.add(0); t += 2_000_000_000L; m.add(4_000_000)
        assertEquals(2_000_000, m.bytesPerSec())
    }
    @Test fun httpRange() {
        assertEquals(HttpRange.R.Full, HttpRange.parse(null, 100))
        assertEquals(HttpRange.R.Part(0, 99), HttpRange.parse("bytes=0-", 100))
        assertEquals(HttpRange.R.Part(10, 19), HttpRange.parse("bytes=10-19", 100))
        assertEquals(HttpRange.R.Part(90, 99), HttpRange.parse("bytes=-10", 100))
        assertEquals(HttpRange.R.Part(0, 99), HttpRange.parse("bytes=-500", 100))
        assertEquals(HttpRange.R.Part(50, 99), HttpRange.parse("bytes=50-9999", 100))
        assertEquals(HttpRange.R.Unsatisfiable, HttpRange.parse("bytes=100-", 100))
        assertEquals(HttpRange.R.Unsatisfiable, HttpRange.parse("bytes=30-20", 100))
        assertEquals(HttpRange.R.Unsatisfiable, HttpRange.parse("bytes=-0", 100))
        assertEquals(HttpRange.R.Unsatisfiable, HttpRange.parse("bytes=0-", 0))
        assertEquals(HttpRange.R.Full, HttpRange.parse("items=1-2", 100))
        assertEquals(HttpRange.R.Part(0, 4), HttpRange.parse("bytes=0-4,10-14", 100), "first range only")
    }
}

class StreamServerTest {
    private val dir = kotlin.io.path.createTempDirectory("ss").toFile()
    private val player = FakePlayer()
    private val port = ServerSocket(0).use { it.localPort }
    private val server = ReceiverServer(dir, player, port, profile = TvProfile(minFreeBytes = 0, minFreeAfterTransfer = 0), pin = "112233").apply { start(5000, false) }
    private val base = "http://127.0.0.1:$port"
    private val tv = TvClient(base, "112233")
    private val data = Random(5).nextBytes(3_000_000)

    @AfterTest fun tearDown() { server.stop(); dir.deleteRecursively() }

    private fun open(path: String, method: String = "GET", range: String? = null, pin: Boolean = true) =
        (URL(base + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method; if (pin) setRequestProperty("X-CB-Pin", "112233")
            if (range != null) setRequestProperty("Range", range)
        }

    /** Uploads [off, to) without the "total reached" completion (to < total). */
    private fun putRange(off: Int, to: Int) {
        val c = (URL("$base/upload/m.mp4?offset=$off&total=${data.size}").openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"; doOutput = true; setFixedLengthStreamingMode((to - off).toLong()); setRequestProperty("X-CB-Pin", "112233")
        }
        c.outputStream.use { it.write(data, off, to - off) }
        assertEquals(200, c.responseCode)
    }

    @Test fun streamsAFinishedFileWithRanges() {
        File(dir, "done.mp4").writeBytes(data)
        val head = open("/stream/done.mp4", "HEAD")
        assertEquals(200, head.responseCode)
        assertEquals(data.size.toString(), head.getHeaderField("Content-Length"))
        assertEquals("bytes", head.getHeaderField("Accept-Ranges"))
        val r = open("/stream/done.mp4", range = "bytes=1000-1999")
        assertEquals(206, r.responseCode)
        assertEquals("bytes 1000-1999/${data.size}", r.getHeaderField("Content-Range"))
        assertContentEquals(data.copyOfRange(1000, 2000), r.inputStream.readBytes())
        assertEquals(416, open("/stream/done.mp4", range = "bytes=${data.size}-").responseCode)
        assertEquals(404, open("/stream/nope.mp4").responseCode)
    }

    @Test fun streamRequiresPinButNotWrongToken() {
        File(dir, "s.mp4").writeBytes(ByteArray(10))
        assertEquals(401, open("/stream/s.mp4", pin = false).responseCode)
        assertEquals(401, open("/stream/s.mp4?t=wrong", pin = false).responseCode)
    }

    @Test fun readerBlocksUntilTheUploadCatchesUpThenSwitchesToFinal() {
        putRange(0, 1_000_000)                                     // only a third is on the TV
        val c = open("/stream/m.mp4")
        assertEquals(200, c.responseCode)
        assertEquals(data.size.toString(), c.getHeaderField("Content-Length"), "announces the FINAL size from the .meta sidecar")
        var got: ByteArray? = null
        val reader = Thread { got = c.inputStream.readBytes() }.apply { start() }
        Thread.sleep(300); assertTrue(reader.isAlive, "reader waits for the missing bytes")
        putRange(1_000_000, 2_000_000); Thread.sleep(200)
        putRange(2_000_000, data.size)                              // completes: .part -> final during the read
        reader.join(10_000)
        assertContentEquals(data, got)
        assertTrue(File(dir, "m.mp4").isFile); assertFalse(File(dir, "m.mp4.meta").exists())
    }

    @Test fun playNeedsBootstrapThenUsesLoopbackStreamUrl() {
        putRange(0, 1_000_000)
        val e = assertFailsWith<TvClient.HttpError> { tv.play("m.mp4") }
        assertEquals(409, e.code); assertTrue(e.message!!.contains("buffering"))
        assertEquals(0, player.lastUrl?.length ?: 0)
        putRange(1_000_000, 2_500_000)
        tv.play("m.mp4", 0)
        val url = player.lastUrl!!
        assertTrue(url.startsWith("http://127.0.0.1:$port/stream/m.mp4?t="))
        val part = URL(url).openConnection() as HttpURLConnection
        part.setRequestProperty("Range", "bytes=0-9")
        assertEquals(206, part.responseCode)                       // token + loopback: no PIN needed by the TV's own player
        assertContentEquals(data.copyOf(10), part.inputStream.readBytes())
    }

    @Test fun infoShowsReceivedAndComplete() {
        putRange(0, 1_000_000)
        File(dir, "full.mkv").writeBytes(ByteArray(50))
        val j = tv.info()
        assertTrue(j.contains("""{"name":"full.mkv","size":50,"received":50,"complete":true,"""), j)
        assertTrue(j.contains("""{"name":"m.mp4","size":${data.size},"received":1000000,"complete":false,"""), j)
        assertTrue(j.contains("\"used\":") && j.contains("\"quota\":"))
    }

    @Test fun sidecarNamesAreRejected() {
        assertNull(ReceiverServer.safeName("x.meta")); assertNull(ReceiverServer.safeName(".played"))
    }
}

class TvInfoParseTest {
    @Test fun parsesFilesWithEscapesAndPartials() {
        val j = """{"files":[{"name":"a \"q\" \\ é.mp4","size":10,"received":10,"complete":true},{"name":"b.mkv","size":1000,"received":250,"complete":false}],""" +
            """"free":5000,"used":300,"quota":9000,"player":{"state":"buffering","name":"b.mkv","pos":1200,"dur":60000}}"""
        val i = TvInfo.parse(j)
        assertEquals(listOf(TvFile("a \"q\" \\ é.mp4", 10, 10, true), TvFile("b.mkv", 1000, 250, false)), i.files)
        assertEquals(5000, i.free); assertEquals(300, i.used); assertEquals(9000, i.quota)
        assertEquals("buffering", i.state); assertEquals("b.mkv", i.playing); assertEquals(1200, i.pos); assertEquals(60000, i.dur)
        assertEquals(250, i.file("b.mkv")?.received)
    }
}
