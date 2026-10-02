package castbridge.core

import castbridge.core.tv.*
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.ServerSocket
import kotlin.random.Random
import kotlin.test.*

private const val GiB = 1L shl 30
private const val MiB = 1L shl 20

class TransferRuleTest {
    @Test fun sizesAndMessages() {
        assertEquals("1 Go", TransferRule.size(GiB)); assertEquals("640 Mo", TransferRule.size(640 * MiB)); assertEquals("1.5 Go", TransferRule.size(GiB + GiB / 2))
        assertEquals("Espace insuffisant : il resterait 640 Mo sur Mémoire interne après le transfert, il en faut 1 Go : libérez 384 Mo ou branchez la clé USB.",
            TransferRule.message("Mémoire interne", 2 * GiB, 2 * GiB - 640 * MiB, GiB, driveAbsent = true))
        assertTrue(TransferRule.message("Clé USB", 100 * MiB, 300 * MiB, GiB, false).contains("ne tient pas sur Clé USB (il manque 200 Mo)"))
        assertTrue(TransferRule.message("Clé USB", 100 * MiB, 300 * MiB, GiB, false).endsWith("libérez 1.2 Go ou choisissez un autre volume."))
        assertTrue(TransferRule.ok(3 * GiB, 2 * GiB, GiB)); assertFalse(TransferRule.ok(3 * GiB, 2 * GiB + 1, GiB))
        assertTrue(TransferRule.ok(-1, 10 * GiB, GiB), "unknown free space (SAF) is not judged here")
        assertEquals(GiB, TransferRule.minFree(TvProfile())); assertEquals(100 * MiB, TransferRule.minFree(TvProfile(minFreeAfterTransfer = 0)))
    }
}

class OneGigRuleServerTest {
    private val r = Rig(profile = TvProfile(minFreeBytes = 0, minFreeAfterTransfer = GiB))
    @AfterTest fun tearDown() = r.close()

    @Test fun acceptedOnlyIfOneGigStaysFreeAtTheEnd() {
        r.unplug(); r.capacity["internal"] = 2 * GiB
        val c = r.tv.checkStorage("ok.mkv", 900 * MiB)
        assertTrue(c.ok, c.message); assertEquals("internal", c.volume); assertEquals(2 * GiB - 900 * MiB, c.freeAfter); assertEquals(GiB, c.minFreeAfter)
        assertEquals(200, r.put("ok.mkv", 0, 900 * MiB, ByteArray(1000)).first)

        val big = 2 * GiB - r.used(r.internalDir) - 640 * MiB     // exactly 640 MB would be left (ok.mkv's partial copy already counts)
        val no = r.tv.checkStorage("big.mkv", big)
        assertFalse(no.ok); assertEquals(507, no.status)
        assertEquals("Espace insuffisant : il resterait 640 Mo sur Mémoire interne après le transfert, il en faut 1 Go : libérez 384 Mo ou branchez la clé USB.", no.message)
        val (code, body) = r.put("big.mkv", 0, big, ByteArray(1000))
        assertEquals(507, code); assertTrue(body.contains("il resterait 640 Mo"), body)
        assertFalse(File(r.internalDir, "big.mkv.part").exists(), "refused before any byte")
    }

    @Test fun autoTriesAnotherVolumeAndReportsEveryOption() {
        r.capacity["usb-1234"] = GiB + GiB / 2; r.capacity["internal"] = 3 * GiB
        val c = r.tv.checkStorage("film.mkv", GiB)
        assertTrue(c.ok, c.message); assertEquals("internal", c.volume, "the drive would keep only 0.5 GB")
        assertEquals(2 * GiB, c.freeAfter)
        assertEquals(mapOf("internal" to true, "usb-1234" to false), c.options.associate { it.id to it.ok })
        assertEquals(GiB / 2, c.options.first { it.id == "usb-1234" }.freeAfter)
        assertTrue(c.warnings.any { it == "Clé USB ignoré : il resterait 512 Mo après le transfert (il en faut 1 Go)" }, c.warnings.toString())
        assertEquals(200, r.put("film.mkv", 0, GiB, ByteArray(10)).first)
        assertTrue(File(r.internalDir, "film.mkv.part").exists())
    }

    @Test fun explicitVolumePerTransferIsHonouredOrRefusedPrecisely() {
        r.capacity["usb-1234"] = GiB + GiB / 2; r.capacity["internal"] = 3 * GiB
        val c = r.tv.checkStorage("a.mkv", GiB, volume = "usb-1234")
        assertFalse(c.ok); assertTrue(c.message.contains("sur Clé USB") && c.message.endsWith("ou choisissez un autre volume."), c.message)
        // The TV's own target is auto (the drive first), but this transfer asks for internal storage.
        assertTrue(r.tv.checkStorage("small.mkv", 10 * MiB, volume = "internal").let { it.ok && it.volume == "internal" })
        val cl = TvClient(r.base)
        cl.upload("small.mkv", 0, 5, "hello".byteInputStream(), target = "internal") {}
        assertTrue(File(r.internalDir, "small.mkv").exists()); assertFalse(File(r.usbDir, "small.mkv").exists())
        assertFailsWith<TvClient.HttpError> { r.tv.checkStorage("x.mkv", 1, volume = "/etc") }
        val c2 = java.net.URL("${r.base}/upload/y.mkv?offset=0&total=5&target=%2Fetc").openConnection() as java.net.HttpURLConnection
        c2.requestMethod = "PUT"; c2.doOutput = true; c2.setFixedLengthStreamingMode(5); c2.outputStream.use { it.write("hello".toByteArray()) }
        assertEquals(400, c2.responseCode, "a path is never a target")
    }

    @Test fun resumeIsJudgedOnWhatIsStillToCome() {
        r.unplug(); r.capacity["internal"] = 2 * GiB
        assertEquals(200, r.put("r.mkv", 0, 900 * MiB, ByteArray(1000)).first)
        // Room shrinks (another app filled the disk): the rest no longer fits with 1 GB to spare.
        r.capacity["internal"] = GiB + 500 * MiB
        val c = r.tv.checkStorage("r.mkv", 900 * MiB)
        assertFalse(c.ok); assertEquals(900 * MiB - 1000, c.remaining)
        val (code, _) = r.put("r.mkv", 1000, 900 * MiB, ByteArray(1000))
        assertEquals(507, code)
    }

    @Test fun theMarginIsAdjustable() {
        r.unplug(); r.capacity["internal"] = GiB
        assertFalse(r.tv.checkStorage("a.mkv", 200 * MiB).ok)
        val (code, body) = r.call("POST", "/api/storage?minFreeAfterMb=100")
        assertEquals(200, code); assertTrue(body.contains("\"minFreeAfterMb\":100"), body)
        assertTrue(r.tv.checkStorage("a.mkv", 200 * MiB).ok)
        assertEquals(400, r.call("POST", "/api/storage?minFreeAfterMb=lots").first)
    }

    @Test fun uploadClientStopsBeforeTheFirstByteWithTheMessage() {
        r.unplug(); r.capacity["internal"] = GiB
        val up = ResumableUpload("huge.mkv", 200 * MiB, { r.base }, { throw AssertionError("nothing may be sent") }, sleep = { })
        val res = up.run { }
        assertTrue(res is ResumableUpload.State.Failed && res.reason.contains("il en faut 1 Go"), res.toString())
    }
}

class DownloadTest {
    private val dir = kotlin.io.path.createTempDirectory("dl").toFile()
    // port 0: the server binds a free port itself (no close-then-reuse race: the fragile ServerSocket(0).use{}.localPort pattern is gone)
    private val server = ReceiverServer(VolumeRegistry.single(File(dir, "tv")), FakePlayer(), 0, pin = "123456").apply { start(5000, false) }
    private val port = server.listeningPort
    private val base = "http://127.0.0.1:$port"
    private val data = Random(7).nextBytes(2_500_000)
    private val out = File(dir, "phone.bin")

    @AfterTest fun tearDown() { server.stop(); dir.deleteRecursively() }

    /** Appending sink on a file; [failAfter] simulates the Wi-Fi dropping after that many bytes of the first attempt. */
    private fun sink(failAfter: Long = Long.MAX_VALUE): (Long) -> OutputStream {
        var first = true
        return { at ->
            val raf = RandomAccessFile(out, "rw").also { it.seek(at) }
            val limit = if (first) failAfter else Long.MAX_VALUE
            first = false
            object : OutputStream() {
                var n = 0L
                override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)
                override fun write(b: ByteArray, off: Int, len: Int) {
                    if (n + len > limit) { val k = (limit - n).toInt(); raf.write(b, off, k); n += k; throw IOException("wifi lost") }
                    raf.write(b, off, len); n += len
                }
                override fun close() = raf.close()
            }
        }
    }

    @Test fun downloadResumesWithRangeAfterACut() {
        File(dir, "tv/film.mp4").writeBytes(data)
        val states = ArrayList<ResumableDownload.State>()
        // a real (short) pause between two attempts: with `sleep = { }` the 60 attempts were burnt in a few ms under the load of the whole suite
        val d = ResumableDownload("film.mp4", { base }, "123456", { if (out.exists()) out.length() else 0 }, sink(failAfter = 1_000_000), sleep = { Thread.sleep(20) })
        val res = d.run { states += it }
        assertEquals(ResumableDownload.State.Done(data.size.toLong()), res)
        assertContentEquals(data, out.readBytes())
        assertTrue(states.any { it is ResumableDownload.State.Waiting }, "the cut was seen")
        assertTrue(states.filterIsInstance<ResumableDownload.State.Downloading>().first { it.got > 1_000_000 }.total == data.size.toLong())
    }

    @Test fun alreadyCompleteAndMismatch() {
        File(dir, "tv/a.bin").writeBytes(data)
        out.writeBytes(data)
        assertEquals(ResumableDownload.State.Done(data.size.toLong()),
            ResumableDownload("a.bin", { base }, "123456", { out.length() }, sink(), sleep = { }).run { })
        out.writeBytes(data + byteArrayOf(1))
        assertTrue(ResumableDownload("a.bin", { base }, "123456", { out.length() }, sink(), sleep = { }).run { } is ResumableDownload.State.Failed)
    }

    @Test fun rangeFromTheMiddleAndErrors() {
        File(dir, "tv/b.bin").writeBytes(data)
        val tv = TvClient(base, "123456")
        val r = tv.openRange("b.bin", 2_000_000)
        assertEquals(206, r.code); assertEquals(2_000_000, r.start); assertEquals(data.size.toLong(), r.total)
        assertContentEquals(data.copyOfRange(2_000_000, data.size), r.input.use { it.readBytes() })
        assertEquals(416, tv.openRange("b.bin", data.size.toLong()).code)
        val missing = ResumableDownload("nope.bin", { base }, "123456", { 0 }, sink(), sleep = { }).run { }
        assertEquals(ResumableDownload.State.Failed("Fichier introuvable sur la TV"), missing)
        val badPin = ResumableDownload("b.bin", { base }, "000000", { 0 }, sink(), sleep = { }).run { }
        assertEquals(ResumableDownload.State.Failed("Code de la TV incorrect."), badPin)
        assertFailsWith<TvClient.HttpError> { tv.openRange("../etc/passwd", 0) }
    }
}
