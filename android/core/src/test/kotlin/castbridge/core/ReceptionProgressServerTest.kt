package castbridge.core

import castbridge.core.tv.TvClient
import castbridge.core.xfer.*
import castbridge.core.xfer.TransferProgress.Phase
import castbridge.core.xfer.TransferProgress.Transport
import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import kotlin.random.Random
import kotlin.test.*

/**
 * Field bug 2026-10-02 (docs/agent-reports/diag-receiver-progress.md): during a copy the TV kept saying « Prêt à recevoir ».
 * The multi-connection copy keeps its bytes in `.cbx/<id>.data` until the end, so the listing-based [castbridge.core.tv.ReceiverServer.receiving]
 * saw nothing; every path now feeds [castbridge.core.tv.ReceiverServer.progress].
 */
class ReceptionProgressServerTest {
    private val rigs = ArrayList<Rig>()
    private fun rig() = Rig().also { it.unplug(); rigs += it }
    private val work = kotlin.io.path.createTempDirectory("src").toFile()
    @AfterTest fun tearDown() { rigs.forEach { it.close() }; work.deleteRecursively() }

    private fun events(r: Rig) = java.util.Collections.synchronizedList(ArrayList<TransferProgress.Item>()).also { l -> r.server.progress.addListener { l += it } }

    @Test fun theMultiConnectionCopyIsVisibleWhileTheOldListingSeesNothing() {
        val r = rig(); val ev = events(r)
        val size = 3L shl 20; val bs = 1 shl 20
        val (code, _) = r.call("POST", "/api/transfer/begin?name=${TvClient.enc("Prison Break [S02 - E03].avi")}&size=$size&blockSize=$bs")
        assertEquals(200, code)
        assertTrue(r.server.receiving().isEmpty(), "the root cause: no .part, no .meta while the blocks arrive")
        val live = r.server.progress.active().single()
        assertEquals(Transport.WIFI_MULTI, live.transport); assertEquals(size, live.total); assertEquals(Phase.RUNNING, live.phase)
        assertTrue(live.screenLine().startsWith("⬇ Réception de Prison Break [S02 - E03]"), live.screenLine())
        // the phone gives up: the TV ends it with the reason
        val id = Manifest("Prison Break [S02 - E03].avi", size, bs).id
        assertEquals(200, r.call("POST", "/api/transfer/abort?id=$id").first)
        assertTrue(r.server.progress.active().isEmpty()); assertEquals(Phase.ABORTED, ev.last().phase)
    }

    @Test fun aFullMultiConnectionCopyGoesFromRunningToReceived() {
        val r = rig(); val ev = events(r)
        val data = Random(3).nextBytes(5 shl 20)
        val f = File(work, "film.mkv").apply { writeBytes(data) }
        FileChannel.open(f.toPath(), StandardOpenOption.READ).use { ch ->
            val tc = TransferClient(HttpTransferApi(r.base) { null }, FileBlockSource(ch), "film.mkv", { id, max ->
                listOf(WifiLane("wifi", "127.0.0.1:${r.port}", id, HttpConn.tcp("127.0.0.1", r.port), { null }, maxStreams = max, fixedK = 3))
            }, retryDelayMs = 50)
            assertEquals(TransferClient.Result.Done, tc.run())
        }
        assertEquals(Transport.WIFI_MULTI, ev.first().transport); assertEquals(Phase.RUNNING, ev.first().phase)
        assertEquals(Phase.DONE, ev.last().phase); assertEquals("Vidéo reçue ✓", ev.last().endLine())
        assertEquals(1, ev.map { it.seq }.toSet().size, "one notification id for the whole transfer")
        assertTrue(r.server.progress.active().isEmpty())
    }

    @Test fun theClassicUploadIsPublishedResumedAndFinished() {
        val r = rig(); val ev = events(r)
        val data = Random(4).nextBytes(400_000)
        assertEquals(200, r.put("clip.mp4", 0, data.size.toLong(), data.copyOfRange(0, 150_000)).first)
        val live = r.server.progress.active().single()
        assertEquals(Transport.WIFI, live.transport); assertEquals(150_000, live.received); assertEquals(37, live.percent)
        assertEquals(200, r.put("clip.mp4", 150_000, data.size.toLong(), data.copyOfRange(150_000, data.size)).first)
        assertEquals(Phase.DONE, ev.last().phase); assertEquals(1, ev.map { it.seq }.toSet().size)
        assertTrue(r.server.progress.active().isEmpty())
        assertContentEquals(data, File(r.internalDir, "clip.mp4").readBytes())
    }

    private fun chunk(r: Rig, id: String, idx: Int, data: ByteArray): Int {
        val c = java.net.URL("${r.base}/api/transfer/chunk?id=$id&idx=$idx").openConnection() as java.net.HttpURLConnection
        c.requestMethod = "PUT"; c.doOutput = true; c.setFixedLengthStreamingMode(data.size)
        c.setRequestProperty("X-CB-Sha256", Hash.hex(Hash.sha256(data)))
        c.outputStream.use { it.write(data) }
        return c.responseCode.also { runCatching { (if (it < 400) c.inputStream else c.errorStream)?.readBytes() } }
    }

    /** After the 90 s silence the copy is closed ABORTED; the phone then sends chunks only (no new begin): it must show again, same id. */
    @Test fun chunksAfterTheSilenceSweepMakeTheCopyVisibleAgain() {
        var t = 0L
        val progress = TransferProgress(now = { t })
        val r = Rig(progress = progress).also { it.unplug(); rigs += it }
        val bs = Manifest.SLICE; val size = 2L * bs
        val name = "film.mkv"
        assertEquals(200, r.call("POST", "/api/transfer/begin?name=$name&size=$size&blockSize=$bs").first)
        val id = Manifest(name, size, bs).id
        val data = Random(7).nextBytes(bs)
        assertEquals(200, chunk(r, id, 0, data))
        val seq = progress.active().single().seq
        t += 91_000
        assertTrue(progress.active().isEmpty(), "silent for 90 s: closed")
        assertEquals(Phase.ABORTED, progress.shown().single().phase)
        assertEquals(200, chunk(r, id, 1, data))
        val back = progress.active().single()
        assertEquals(seq, back.seq, "same notification id"); assertEquals(Phase.RUNNING, back.phase)
        assertEquals(size, back.received); assertEquals(Transport.WIFI_MULTI, back.transport)
    }

    /** A clean end of the stream before the last byte (no IOException) does not leave the copy RUNNING until the sweep. */
    @Test fun aShortUploadIsMarkedWaitingAtOnce() {
        val r = rig()
        val data = Random(8).nextBytes(100_000)
        assertEquals(200, r.put("short.mp4", 0, 300_000, data).first)
        val it = r.server.progress.active().single()
        assertEquals("connexion coupée, reprise en attente", it.message)
    }
}
