package castbridge.core.dl

import castbridge.core.tv.*
import fi.iki.elonen.NanoHTTPD
import org.junit.Assume.assumeTrue
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import kotlin.test.*

/**
 * Against a REAL aria2c (same 1.37.0 source, built for the host): `ARIA2C=/path/to/aria2c gradle :core:test`.
 * Skipped when ARIA2C is not set. Checks the command line (every option exists), the supervisor, the RPC client and the
 * manager end to end: an HTTP download split over several connections, moved to the library, then a clean stop.
 */
class Aria2IntegrationTest {
    private val bin = System.getenv("ARIA2C")?.let(::File)?.takeIf { it.canExecute() }

    /** A non-loopback address of this machine (the manager refuses loopback links, as on the TV). */
    private fun lanIp(): String? = NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
        .flatMap { it.inetAddresses.toList() }.firstOrNull { it is Inet4Address && it.isSiteLocalAddress }?.hostAddress

    private class FileServer(port: Int, val data: ByteArray) : NanoHTTPD(port) {
        override fun serve(s: IHTTPSession): Response {
            val range = s.headers["range"]?.removePrefix("bytes=")?.split('-')
            val from = range?.getOrNull(0)?.toLongOrNull() ?: 0
            val to = range?.getOrNull(1)?.toLongOrNull() ?: (data.size - 1L)
            val len = to - from + 1
            val body = java.io.ByteArrayInputStream(data, from.toInt(), len.toInt())
            val r = newFixedLengthResponse(if (range != null) Response.Status.PARTIAL_CONTENT else Response.Status.OK, "video/mp4", body, len)
            r.addHeader("Accept-Ranges", "bytes")
            if (range != null) r.addHeader("Content-Range", "bytes $from-$to/${data.size}")
            return r
        }
    }

    @Test fun realAria2DownloadsIntoTheLibrary() {
        assumeTrue("ARIA2C not set", bin != null)
        val ip = lanIp(); assumeTrue("no LAN address", ip != null)
        val root = kotlin.io.path.createTempDirectory("real").toFile()
        val usb = File(root, "usb").apply { mkdirs() }
        val reg = VolumeRegistry(StaticVolumes { listOf(StorageVolume("usb", "Clé USB", usb, VolumeKind.REMOVABLE, Fs.EXFAT, 0, 0, true)) }) { 50L shl 30 }
            .also { it.refresh() }
        val work = File(root, "work")
        val data = ByteArray(24 shl 20) { (it * 7).toByte() }
        val port = ServerSocket(0).use { it.localPort }
        val http = FileServer(port, data).apply { start(5000, false) }
        lateinit var dm: DownloadManager
        val sup = Aria2Supervisor(bin, work, { conf, p ->
            Aria2Config.args(conf, File(work, "aria2.session").apply { parentFile.mkdirs(); if (!exists()) createNewFile() },
                File(usb, DownloadManager.DIR), p, ProcessHandle.current().pid(), dm.currentSettings, null, emptyList(), File(work, "dht.dat"))
        }, onReady = { dm.onEngineReady(it) }, preferredPort = ServerSocket(0).use { it.localPort })
        dm = DownloadManager(reg, work, { sup.rpc }, { DownloadManager.EngineStatus(sup.available, sup.rpc != null, sup.state.name, sup.message, sup.version) },
            ensureEngine = { sup.start() }, engineWaitMs = 15_000)
        try {
            dm.acceptWarning()
            val id = (dm.addLink("http://$ip:$port/films/Mon%20film.mp4") as? DownloadManager.Result.Ok ?: fail("${dm.addLink("x")}")).id
            assertEquals(Aria2Supervisor.State.RUNNING, sup.state)
            assertEquals("1.37.0", sup.version)
            val deadline = System.currentTimeMillis() + 60_000
            while (dm.finished().isEmpty() && System.currentTimeMillis() < deadline) { dm.tick(); Thread.sleep(300) }
            val f = dm.finished().single()
            assertEquals(listOf("Mon film.mp4"), f.files)
            assertContentEquals(data, File(usb, "Mon film.mp4").readBytes())
            assertFalse(File(usb, ".cb-downloads/$id").exists())
            // Pause/resume/remove on a real daemon (a URL that never answers keeps it "active").
            val hole = ServerSocket(0)                          // accepts, never answers
            val holder = Thread { runCatching { while (true) hole.accept() } }.apply { isDaemon = true; start() }
            val slow = (dm.addLink("http://$ip:${hole.localPort}/x.mkv") as DownloadManager.Result.Ok).id
            dm.tick()
            val p = dm.pause(slow)
            assertIs<DownloadManager.Result.Ok>(p, p.toString()); dm.tick()
            assertEquals(DlState.PAUSED, dm.views().first { it.id == slow }.state)
            assertIs<DownloadManager.Result.Ok>(dm.setOptions(slow, mapOf("max-download-limit" to "100K")))
            assertIs<DownloadManager.Result.Ok>(dm.remove(slow, true))
            hole.close(); holder.join(1000)
            assertIs<DownloadManager.Result.Ok>(dm.changeSettings(mapOf("downLimit" to "5M", "seeding" to "1")))
            assertTrue(sup.log().none { it.contains("Unrecognized option") || it.contains("unrecognized option") }, sup.log().joinToString("\n"))
        } finally {
            sup.stop()
            http.stop()
        }
        assertEquals(Aria2Supervisor.State.STOPPED, sup.state)
        assertTrue(File(work, "aria2.session").exists())
        assertFalse(File(work, "aria2.conf").exists(), "secret file gone")
    }
}
