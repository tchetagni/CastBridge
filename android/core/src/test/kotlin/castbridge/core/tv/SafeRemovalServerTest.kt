package castbridge.core.tv

import castbridge.core.FakePlayer
import castbridge.core.Rig
import castbridge.core.xfer.Hash
import castbridge.core.xfer.Manifest
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * « Retrait sûr » côté serveur de la TV (2026-10-07, docs/STORAGE.md) : « Préparer le retrait de la clé USB » barre les nouvelles copies vers la clé (503 « volume removed » : le téléphone attend comme
 * pour une clé retirée, rien n'est redirigé ailleurs, rien n'est perdu), laisse finir (DRAIN) ou coupe au bloc suivant (STOP) les copies en cours en gardant ce qui est arrivé, vide la clé, et la copie
 * reprend octet pour octet quand la clé sert de nouveau. Vrai serveur NanoHTTPD sur deux dossiers temporaires (mémoire interne, clé exFAT).
 */
class SafeRemovalServerTest {
    private val r = Rig()
    private val key = "usb-1234"
    private val data = Random(11).nextBytes(3_000_000)
    @AfterTest fun tearDown() = r.close()

    private fun awaitTrue(ms: Long = 8_000, what: String = "condition", f: () -> Boolean) {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) { if (f()) return; Thread.sleep(15) }
        fail("not reached in $ms ms: $what")
    }

    /** An upload that sends its first bytes now and the rest later: the request stays open in between, like a real copy of a big file. */
    private inner class OpenPut(name: String, offset: Long, private val total: Long, private val bytes: ByteArray) {
        private val c = (URL("${r.base}/upload/${TvClient.enc(name)}?offset=$offset&total=$total").openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"; doOutput = true; setFixedLengthStreamingMode(bytes.size); setRequestProperty("Connection", "close"); connectTimeout = 5000; readTimeout = 15_000
        }
        private val out = c.outputStream
        private var sent = 0
        fun send(n: Int) { out.write(bytes, sent, n); out.flush(); sent += n }
        /** Sends what is left and reads the answer; -1 = the server cut the connection. */
        fun finish(): Pair<Int, String> = try {
            if (sent < bytes.size) { out.write(bytes, sent, bytes.size - sent); sent = bytes.size }
            out.close()
            val code = c.responseCode
            code to (if (code < 400) c.inputStream else c.errorStream).readBytes().decodeToString()
        } catch (e: IOException) { -1 to (e.message ?: "") }
    }

    // ---- what writes to the key ----

    @Test fun `a running upload is listed as a write with its progress, and the list is empty when it ends`() {
        val put = OpenPut("Film.mkv", 0, data.size.toLong(), data)
        put.send(700_000)
        awaitTrue(what = "the upload is listed") { r.server.writesOn(key).isNotEmpty() }
        val w = r.server.writesOn(key).single()
        assertEquals("Film", w.name, "the title the TV shows, never a path")
        assertTrue(w.percent in 0..100, w.percent.toString())
        assertEquals(emptyList(), r.server.writesOn("internal"), "the other volume is not written")
        assertEquals(200, put.finish().first)
        assertEquals(emptyList(), r.server.writesOn(key))
        assertEquals(data.size.toLong(), File(r.usbDir, "Film.mkv").length())
    }

    @Test fun `the progress of a write follows the bytes that reached the key`() {
        val put = OpenPut("Film.mkv", 0, data.size.toLong(), data)
        put.send(2_200_000)
        awaitTrue(what = "more than half of the file written") { (r.server.writesOn(key).singleOrNull()?.percent ?: 0) >= 50 }
        put.finish()
        assertEquals(emptyList(), r.server.writesOn(key))
    }

    @Test fun `a write on the other volume does not hold the key`() {
        r.tv.setTarget("internal")
        val put = OpenPut("Film.mkv", 0, data.size.toLong(), data)
        put.send(700_000)
        awaitTrue(what = "listed on the internal memory") { r.server.writesOn("internal").isNotEmpty() }
        assertEquals(emptyList(), r.server.writesOn(key))
        put.finish()
    }

    // ---- DRAIN: nothing new, the running copy ends ----

    @Test fun `a drain fence refuses a new copy at once, redirects nothing, and the key serves again when lifted`() {
        r.server.fenceVolume(key, stop = false)
        assertTrue(r.server.volumeFenced(key))
        val (code, body) = r.put("A.mp4", 0, 1000, ByteArray(1000))
        assertEquals(503, code)
        assertTrue("volume removed" in body && "\"retry\":true" in body, body)
        assertFalse(File(r.usbDir, "A.mp4.part").exists() || File(r.usbDir, "A.mp4").exists())
        assertFalse(File(r.internalDir, "A.mp4.part").exists() || File(r.internalDir, "A.mp4").exists(), "the copy waits for the key: it is not silently sent to the internal memory")
        r.server.unfenceVolume(key)
        assertFalse(r.server.volumeFenced(key))
        assertEquals(200, r.put("A.mp4", 0, 1000, ByteArray(1000)).first)
        assertTrue(File(r.usbDir, "A.mp4").isFile)
    }

    @Test fun `a drain fence lets the copy that runs end, then nothing else starts`() {
        val put = OpenPut("Film.mkv", 0, data.size.toLong(), data)
        put.send(700_000)
        awaitTrue(what = "listed") { r.server.writesOn(key).isNotEmpty() }
        r.server.fenceVolume(key, stop = false)
        assertEquals(200, put.finish().first, "the running copy is not cut")
        assertContentEquals(data, File(r.usbDir, "Film.mkv").readBytes())
        assertEquals(emptyList(), r.server.writesOn(key))
        assertEquals(503, r.put("Other.mkv", 0, 1000, ByteArray(1000)).first)
    }

    @Test fun `a partial copy on the fenced key is neither continued nor redirected nor lost, and completes byte for byte afterwards`() {
        val total = data.size.toLong()
        assertEquals(200, r.put("Film.mkv", 0, total, data.copyOf(900_000)).first)
        assertEquals(900_000L, File(r.usbDir, "Film.mkv.part").length())
        r.server.fenceVolume(key, stop = false)
        assertEquals(503, r.put("Film.mkv", 900_000, total, data.copyOfRange(900_000, data.size)).first, "the resumed copy waits for the key")
        assertEquals(900_000L, File(r.usbDir, "Film.mkv.part").length(), "the partial copy is intact")
        assertFalse(File(r.internalDir, "Film.mkv.part").exists())
        r.server.unfenceVolume(key)
        assertEquals(200, r.put("Film.mkv", 900_000, total, data.copyOfRange(900_000, data.size)).first)
        assertContentEquals(data, File(r.usbDir, "Film.mkv").readBytes())
    }

    // ---- STOP: the running copy is cut at its next block, what arrived is kept ----

    @Test fun `a stop fence cuts the running copy at its next block, keeps and flushes what arrived, and the copy resumes byte for byte`() {
        val total = data.size.toLong()
        val put = OpenPut("Film.mkv", 0, total, data)
        put.send(600_000)
        awaitTrue(what = "a first block reached the key") { (r.server.writesOn(key).singleOrNull()?.percent ?: 0) >= 8 }
        r.server.fenceVolume(key, stop = true)
        val res = put.finish()                                   // the server stops reading: 503, or the connection is cut
        assertTrue(res.first == 503 || res.first == -1, res.toString())
        awaitTrue(what = "the writer let go of the key") { r.server.writesOn(key).isEmpty() }
        val part = File(r.usbDir, "Film.mkv.part")
        assertTrue(part.isFile && part.length() > 0 && part.length() < total, "what arrived is kept: ${part.length()}")
        assertFalse(File(r.usbDir, "Film.mkv").exists(), "a cut copy is never promoted to a finished file")
        assertTrue(r.server.flushVolume(key), "every partial file reached the medium")
        // the phone resumes when the key serves again: from exactly what the TV kept
        r.server.unfenceVolume(key)
        val have = r.tv.part("Film.mkv").length
        assertEquals(part.length(), have)
        assertEquals(200, r.put("Film.mkv", have, total, data.copyOfRange(have.toInt(), data.size)).first)
        assertContentEquals(data, File(r.usbDir, "Film.mkv").readBytes())
    }

    @Test fun `a drain request never weakens a stop`() {
        val put = OpenPut("Film.mkv", 0, data.size.toLong(), data)
        put.send(600_000)
        awaitTrue(what = "a first block reached the key") { (r.server.writesOn(key).singleOrNull()?.percent ?: 0) >= 8 }
        r.server.onFenceStop = { r.server.fenceVolume(key, stop = false) }          // a late « attendre » arrives right after the « mettre en pause »
        r.server.fenceVolume(key, stop = true)
        val res = put.finish()
        assertTrue(res.first == 503 || res.first == -1, "the copy is still cut: $res")
        assertTrue(File(r.usbDir, "Film.mkv.part").length() < data.size)
    }

    @Test fun `a drain request upgrades to a stop when the owner presses pause`() {
        val put = OpenPut("Film.mkv", 0, data.size.toLong(), data)
        put.send(600_000)
        awaitTrue(what = "a first block reached the key") { (r.server.writesOn(key).singleOrNull()?.percent ?: 0) >= 8 }
        r.server.fenceVolume(key, stop = false)
        r.server.fenceVolume(key, stop = true)
        val res = put.finish()
        assertTrue(res.first == 503 || res.first == -1, "wait, then pause: the copy is cut: $res")
    }

    @Test fun `a cut copy is not reported as a failure of the key`() {
        val put = OpenPut("Film.mkv", 0, data.size.toLong(), data)
        put.send(600_000)
        awaitTrue(what = "a first block reached the key") { (r.server.writesOn(key).singleOrNull()?.percent ?: 0) >= 8 }
        r.server.fenceVolume(key, stop = true)
        put.finish()
        awaitTrue(what = "ended") { r.server.writesOn(key).isEmpty() }
        assertTrue(r.registry.volumes().any { it.id == key }, "the key stays in the registry: it is still plugged")
        assertTrue(r.notices.none { "échec" in it.lowercase() }, r.notices.toString())
    }

    // ---- multi-connection copies ----

    private fun blockPut(id: String, idx: Int, bytes: ByteArray): Int {
        val c = URL("${r.base}/api/transfer/chunk?id=$id&idx=$idx").openConnection() as HttpURLConnection
        c.requestMethod = "PUT"; c.doOutput = true; c.setFixedLengthStreamingMode(bytes.size); c.setRequestProperty("Connection", "close")
        c.setRequestProperty("X-CB-Sha256", Hash.hex(Hash.sha256(bytes))); c.connectTimeout = 5000; c.readTimeout = 15_000
        c.outputStream.use { it.write(bytes) }
        val code = c.responseCode
        (if (code < 400) c.inputStream else c.errorStream)?.readBytes()
        return code
    }

    @Test fun `a multi-connection copy is closed by the stop fence, kept on the key, flushed, and resumes to a byte-identical file`() {
        val bs = 262_144
        val size = 3L * bs - 1000
        val file = data.copyOf(size.toInt())
        fun block(i: Int) = file.copyOfRange(i * bs, minOf((i + 1L) * bs, size).toInt())
        val begin = "/api/transfer/begin?name=Serie.mkv&size=$size&blockSize=$bs"
        val (c0, b0) = r.call("POST", begin)
        assertEquals(200, c0, b0)
        assertTrue("\"volume\":\"$key\"" in b0, b0)
        val id = TvClient.str(b0, "id")!!
        assertEquals(200, blockPut(id, 0, block(0)))
        assertEquals("Serie", r.server.writesOn(key).single().name, "an active session holds the key")

        r.server.fenceVolume(key, stop = true)
        assertEquals(emptyList(), r.server.writesOn(key), "the session is closed")
        assertTrue(File(r.usbDir, ".cbx/$id.data").isFile && File(r.usbDir, ".cbx/$id.state").isFile, "its files stay on the key: the copy resumes from them")
        val late = blockPut(id, 1, block(1))
        assertTrue(late == 404 || late == 503, "a block for a closed session is not accepted: $late")
        val (cb, bb) = r.call("POST", begin)
        assertEquals(503, cb, bb)
        assertTrue("volume removed" in bb, bb)
        assertFalse(File(r.internalDir, ".cbx").exists(), "the copy is not started again on the internal memory")
        assertTrue(r.server.flushVolume(key))

        r.server.unfenceVolume(key)
        val (c1, b1) = r.call("POST", begin)
        assertEquals(200, c1, b1)
        assertEquals(1L, TvClient.num(b1, "done"), "block 0 was kept: $b1")
        assertEquals(200, blockPut(id, 1, block(1)))
        assertEquals(200, blockPut(id, 2, block(2)))
        val root = Manifest.root((0..2).map { Hash.hex(Hash.sha256(block(it))) })
        val (cf, bf) = r.call("POST", "/api/transfer/finish?id=$id&root=$root")
        assertEquals(200, cf, bf)
        assertContentEquals(file, File(r.usbDir, "Serie.mkv").readBytes())
    }

    /** A block sent in 8 pieces, 420 ms apart (about 3.4 s: longer than the 2 s the server gives a refused request to drain by itself); the answer is read after the last byte, like the phone does. */
    private fun slowBlockPut(id: String, idx: Int, bytes: ByteArray): Int {
        val c = URL("${r.base}/api/transfer/chunk?id=$id&idx=$idx").openConnection() as HttpURLConnection
        c.requestMethod = "PUT"; c.doOutput = true; c.setFixedLengthStreamingMode(bytes.size); c.setRequestProperty("Connection", "close")
        c.setRequestProperty("X-CB-Sha256", Hash.hex(Hash.sha256(bytes))); c.connectTimeout = 5000; c.readTimeout = 15_000
        c.outputStream.use { out ->
            val piece = bytes.size / 8
            for (i in 0 until 8) { val from = i * piece; out.write(bytes, from, (if (i == 7) bytes.size else from + piece) - from); out.flush(); Thread.sleep(420) }
        }
        val code = c.responseCode
        (if (code < 400) c.inputStream else c.errorStream)?.readBytes()
        return code
    }

    @Test fun `a block that arrives while the sessions are being closed is refused as held, its body is read first, and the session is still there`() {
        // the phone sends the WHOLE block before it reads the answer: refused without reading it, the connection would be reset under its feet (a failure, not a « retry »)
        val bs = 1 shl 20
        val size = bs.toLong()
        val file = Random(3).nextBytes(bs)
        val (c0, b0) = r.call("POST", "/api/transfer/begin?name=A.mkv&size=$size&blockSize=$bs")
        assertEquals(200, c0, b0)
        val id = TvClient.str(b0, "id")!!
        var during = -1
        var sessionThere = false
        r.server.onFenceStop = { during = slowBlockPut(id, 0, file); sessionThere = r.server.writesOn(key).isNotEmpty() }
        r.server.fenceVolume(key, stop = true)
        assertEquals(503, during, "the fence is set before the sessions are closed: no new block is written meanwhile")
        assertTrue(sessionThere, "the session was still open at that moment: it is the fence, not the closing, that refused the block")
        assertFalse(File(r.usbDir, ".cbx/$id.data").let { it.isFile && it.length() > 0 }, "nothing was written by the refused block")
    }

    @Test fun `a partial multi-connection copy on the fenced key is never started again elsewhere, even when another volume would be chosen`() {
        val bs = 262_144
        val size = 2L * bs
        val file = data.copyOf(size.toInt())
        fun block(i: Int) = file.copyOfRange(i * bs, (i + 1) * bs)
        val begin = "/api/transfer/begin?name=A.mkv&size=$size&blockSize=$bs"
        val (c0, b0) = r.call("POST", begin)
        assertEquals(200, c0, b0)
        val id = TvClient.str(b0, "id")!!
        assertEquals(200, blockPut(id, 0, block(0)))
        r.server.fenceVolume(key, stop = true)
        // the key now measures slow: « auto » would put a new copy on the internal memory first
        r.usbBps = 100_000; r.registry.refresh()
        assertEquals(503, r.call("POST", begin).first, "the partial copy is on the key: it is resumed there, not started again on the internal memory")
        assertFalse(File(r.internalDir, ".cbx").exists())
        r.server.unfenceVolume(key)
    }

    @Test fun `a copy that is being verified at its end is never cut by the stop fence, it ends and then lets go of the key`() {
        val bs = 262_144
        val size = 2L * bs
        val file = data.copyOf(size.toInt())
        fun block(i: Int) = file.copyOfRange(i * bs, (i + 1) * bs)
        val (c0, b0) = r.call("POST", "/api/transfer/begin?name=A.mkv&size=$size&blockSize=$bs")
        val id = TvClient.str(b0, "id")!!
        assertEquals(200, blockPut(id, 0, block(0)))
        assertEquals(200, blockPut(id, 1, block(1)))
        r.server.markFinishingForTest(id, true)                          // the phone asked `finish`: the TV reads the whole file back
        r.server.fenceVolume(key, stop = true)
        assertEquals("A", r.server.writesOn(key).single().name, "the verification still holds the key")
        assertEquals(1, r.server.writesOn(key).size)
        assertTrue(r.server.flushVolume(key), "flushing does not close it either")
        assertEquals(1, r.server.writesOn(key).size)
        r.server.markFinishingForTest(id, false)                         // the verification ended
        assertTrue(r.server.flushVolume(key))
        assertEquals(emptyList(), r.server.writesOn(key), "now it is closed")
    }

    @Test fun `a multi-connection copy the phone left silent no longer counts as writing, but is still closed and flushed`() {
        val bs = 262_144
        val size = 2L * bs
        val file = data.copyOf(size.toInt())
        val (c0, b0) = r.call("POST", "/api/transfer/begin?name=A.mkv&size=$size&blockSize=$bs")
        val id = TvClient.str(b0, "id")!!
        assertEquals(200, blockPut(id, 0, file.copyOf(bs)))
        assertEquals(1, r.server.writesOn(key).size)
        assertEquals("A.mkv", r.server.interruptedOn(key))
        r.server.activeWriteMs = 150
        Thread.sleep(300)
        assertEquals(emptyList(), r.server.writesOn(key), "silent for longer than the delay: the phone is not sending")
        assertNull(r.server.interruptedOn(key))
        assertTrue(r.server.flushVolume(key))
        assertTrue(File(r.usbDir, ".cbx/$id.state").isFile, "closed, its state kept")
        assertEquals(404, blockPut(id, 1, file.copyOfRange(bs, 2 * bs)), "the session is gone: the phone begins again")
    }

    @Test fun `a key pulled during a multi-connection copy remembers the file of the copy it cut`() {
        val bs = 262_144
        val size = 2L * bs
        val file = data.copyOf(size.toInt())
        val (c0, b0) = r.call("POST", "/api/transfer/begin?name=Serie.mkv&size=$size&blockSize=$bs")
        val id = TvClient.str(b0, "id")!!
        assertEquals(200, blockPut(id, 0, file.copyOf(bs)))
        r.unplug()
        assertEquals(503, blockPut(id, 1, file.copyOfRange(bs, 2 * bs)), "the key is gone: the phone waits for it")
        assertEquals("Serie.mkv", r.server.interruptedOn(key))
    }

    @Test fun `a drain fence refuses to start a multi-connection copy, and lets the running session go on`() {
        val bs = 262_144
        val size = 2L * bs
        val file = data.copyOf(size.toInt())
        fun block(i: Int) = file.copyOfRange(i * bs, (i + 1) * bs)
        val (c0, b0) = r.call("POST", "/api/transfer/begin?name=A.mkv&size=$size&blockSize=$bs")
        assertEquals(200, c0, b0)
        val id = TvClient.str(b0, "id")!!
        r.server.fenceVolume(key, stop = false)
        assertEquals(200, blockPut(id, 0, block(0)), "the running copy continues in drain mode")
        assertEquals(503, r.call("POST", "/api/transfer/begin?name=B.mkv&size=$size&blockSize=$bs").first, "a new one does not start")
        assertEquals(200, blockPut(id, 1, block(1)))
        val root = Manifest.root((0..1).map { Hash.hex(Hash.sha256(block(it))) })
        assertEquals(200, r.call("POST", "/api/transfer/finish?id=$id&root=$root").first)
        assertEquals(emptyList(), r.server.writesOn(key))
    }

    @Test fun `a session closed under the feet of a request makes it retry, never fail`() {
        val bs = 262_144
        val size = 2L * bs
        val file = data.copyOf(size.toInt())
        val (c0, b0) = r.call("POST", "/api/transfer/begin?name=A.mkv&size=$size&blockSize=$bs")
        assertEquals(200, c0, b0)
        val id = TvClient.str(b0, "id")!!
        r.server.closeAssemblerOnly(id)
        val url = URL("${r.base}/api/transfer/chunk?id=$id&idx=0")
        val c = url.openConnection() as HttpURLConnection
        val bytes = file.copyOf(bs)
        c.requestMethod = "PUT"; c.doOutput = true; c.setFixedLengthStreamingMode(bytes.size); c.setRequestProperty("Connection", "close")
        c.setRequestProperty("X-CB-Sha256", Hash.hex(Hash.sha256(bytes)))
        c.outputStream.use { it.write(bytes) }
        assertEquals(503, c.responseCode, "a 400 would stop the copy on the phone")
        assertTrue("\"retry\":true" in c.errorStream.readBytes().decodeToString())
    }

    // ---- the cut copy ----

    @Test fun `a key pulled during a copy names the file whose copy was cut, even after the writer ended`() {
        val put = OpenPut("Film 2024.mkv", 0, data.size.toLong(), data)
        put.send(700_000)
        awaitTrue(what = "listed") { r.server.writesOn(key).isNotEmpty() }
        assertEquals("Film 2024.mkv", r.server.interruptedOn(key), "while it still writes: the broadcast may come before the writer notices")
        r.unplug()
        put.finish()                                             // the writer meets the missing key: 503, or the connection is cut
        awaitTrue(what = "the writer ended") { r.server.writesOn(key).isEmpty() }
        assertEquals("Film 2024.mkv", r.server.interruptedOn(key), "remembered for a minute: « Clé retirée pendant une copie : le fichier … est incomplet, il sera repris »")
        assertNull(r.server.interruptedOn("internal"))
    }

    @Test fun `nothing was cut when nothing was written`() {
        assertNull(r.server.interruptedOn(key))
        assertEquals(200, r.put("A.mp4", 0, 1000, ByteArray(1000)).first)
        assertNull(r.server.interruptedOn(key), "a finished copy is not a cut copy")
    }

    // ---- moves ----

    @Test fun `a move to or from a fenced key is refused`() {
        File(r.internalDir, "M.mp4").writeBytes(data.copyOf(100_000))
        r.server.fenceVolume(key, stop = false)
        val (code, body) = r.call("POST", "/api/storage/move?name=M.mp4&to=$key")
        assertEquals(503, code, body)
        assertFalse(File(r.usbDir, "M.mp4").exists() || File(r.usbDir, "M.mp4.part").exists())
        assertTrue(File(r.internalDir, "M.mp4").isFile, "the source is untouched")
    }

    // ---- the flush ----

    @Test fun `flushing a key that is not there says no`() {
        assertFalse(r.server.flushVolume("usb-nobody"))
    }

    @Test fun `flushing the key with nothing to flush says yes`() {
        assertTrue(r.server.flushVolume(key))
    }
}

/** A key whose source files read slowly, so that a move is still running when the removal is prepared. */
class SafeRemovalMoveTest {
    private val root = kotlin.io.path.createTempDirectory("sr-move").toFile()
    private val internalDir = File(root, "internal").apply { mkdirs() }
    private val usbDir = File(root, "usb").apply { mkdirs() }
    private val data = Random(5).nextBytes(3_000_000)

    private class SlowStore(v: StorageVolume) : FileStore(v) {
        override fun open(name: String, from: Long): InputStream {
            val inner = super.open(name, from)
            return object : InputStream() {
                override fun read(): Int = inner.read()
                override fun read(b: ByteArray, off: Int, len: Int): Int { Thread.sleep(25); return inner.read(b, off, len) }
                override fun close() = inner.close()
            }
        }
    }

    private val volumes = listOf(
        StorageVolume("internal", "Mémoire interne", internalDir, VolumeKind.INTERNAL, Fs.UNKNOWN, 0, 0, false),
        StorageVolume("usb-1", "Clé", usbDir, VolumeKind.REMOVABLE, Fs.EXFAT, 0, 0, true),
    )
    private val provider = object : VolumeProvider {
        override fun scan(remeasure: Boolean) = volumes
        override fun storeFor(volume: StorageVolume): VolumeStore = if (volume.id == "internal") SlowStore(volume) else FileStore(volume)
    }
    private val registry = VolumeRegistry(provider).also { it.refresh() }
    private val server = ReceiverServer(registry, FakePlayer(), 0, profile = TvProfile(minFreeBytes = 0, minFreeAfterTransfer = 0)).apply { start(5000, false) }
    private val tv = TvClient("http://127.0.0.1:${server.listeningPort}")

    @AfterTest fun tearDown() { server.stop(); root.deleteRecursively() }

    @Test fun `a stop fence cancels the move that writes to the key, the partial copy is kept and the source is untouched`() {
        File(internalDir, "Film.mkv").writeBytes(data)
        tv.moveFile("Film.mkv", "usb-1")
        val end = System.currentTimeMillis() + 8_000
        while (server.writesOn("usb-1").isEmpty() && System.currentTimeMillis() < end) Thread.sleep(10)
        assertEquals("Film", server.writesOn("usb-1").single().name, "a move to the key is a copy writing to it")
        server.fenceVolume("usb-1", stop = true)
        val end2 = System.currentTimeMillis() + 8_000
        while (!tv.storage().contains("\"state\":\"cancelled\"") && System.currentTimeMillis() < end2) Thread.sleep(20)
        assertTrue(tv.storage().contains("\"state\":\"cancelled\""), tv.storage())
        assertEquals(emptyList(), server.writesOn("usb-1"))
        assertContentEquals(data, File(internalDir, "Film.mkv").readBytes())
        assertFalse(File(usbDir, "Film.mkv").exists(), "never a finished file from a cancelled move")
        assertTrue(File(usbDir, "Film.mkv.part").length() < data.size, "only a part")
    }
}
