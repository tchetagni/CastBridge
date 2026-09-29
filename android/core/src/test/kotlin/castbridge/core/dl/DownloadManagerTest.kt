package castbridge.core.dl

import castbridge.core.FakePlayer
import castbridge.core.tv.*
import java.io.File
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import java.util.concurrent.TimeUnit
import kotlin.test.*

private const val G = 1L shl 30

/** An internal folder and a hot-pluggable USB drive with simulated free space, a fake aria2 and the manager. */
class DlRig {
    val root = kotlin.io.path.createTempDirectory("dl").toFile()
    val internalDir = File(root, "internal").apply { mkdirs() }
    val usbDir = File(root, "usb").apply { mkdirs() }
    var usbPresent = true
    val free = mutableMapOf("internal" to (300L shl 20), "usb" to 50 * G)
    val provider = StaticVolumes {
        listOf(StorageVolume("internal", "Mémoire interne", internalDir, VolumeKind.INTERNAL, Fs.EXT4, 0, 0, false)) +
            (if (usbPresent) listOf(StorageVolume("usb", "Clé USB", usbDir, VolumeKind.REMOVABLE, Fs.EXFAT, 0, 0, true)) else emptyList())
    }
    val registry = VolumeRegistry(provider) { v -> free[v.id] ?: -1 }.also { it.refresh() }
    val fake = FakeAria2()
    var engineUp = true
    val sizes = mutableMapOf<String, Long>()
    val finished = ArrayList<DownloadManager.Finished>()
    val rpc = Aria2Rpc(0, fake.secret, transport = fake::handle)
    val work = File(root, "work")
    val dm = DownloadManager(registry, work, { if (engineUp) rpc else null },
        { DownloadManager.EngineStatus(true, engineUp, if (engineUp) "RUNNING" else "STOPPED", "") },
        sizeProbe = { sizes[it] }, worker = { it.run() }).also { it.addFinishedListener { f -> finished += f } }

    fun ok(r: DownloadManager.Result) = (r as? DownloadManager.Result.Ok ?: fail("refused: $r")).id
    fun task(id: String) = dm.views().first { it.id == id }
    fun gidOf(id: String) = fake.dls.values.last { it.dir.endsWith(id) }.gid
}

class DownloadManagerTest {
    @Test fun warnsOnceThenAddsToTheUsbDrive() {
        val r = DlRig()
        val first = r.dm.addLink("https://example.org/film.mkv")
        assertEquals("warning", (first as DownloadManager.Result.Refused).code)
        r.dm.acceptWarning()
        r.sizes["https://example.org/film.mkv"] = 2 * G
        val id = r.ok(r.dm.addLink("https://example.org/film.mkv"))
        val dl = r.fake.dls.values.single()
        assertEquals(File(r.usbDir, ".cb-downloads/$id").absolutePath, dl.dir)
        assertEquals("usb", r.task(id).volumeId)
        assertEquals("film.mkv", r.task(id).name)
        // The acknowledgement is remembered across restarts.
        assertTrue(DownloadManager(r.registry, r.work, { r.rpc }, { DownloadManager.EngineStatus(true, true, "RUNNING", "") }).currentSettings.warningAccepted)
    }

    @Test fun refusesWhenOneGigabyteWouldNotStayFree() {
        val r = DlRig(); r.dm.acceptWarning()
        r.free["usb"] = 3 * G
        r.sizes["https://example.org/big.mkv"] = 2 * G + 1
        val no = r.dm.addLink("https://example.org/big.mkv") as DownloadManager.Result.Refused
        assertEquals(507, no.http); assertTrue(no.message.contains("1,0 Go"), no.message)
        assertTrue(r.fake.dls.isEmpty())
        // Two downloads heading to the same drive: the second one sees what the first will still write.
        r.sizes["https://example.org/a.mkv"] = G
        r.ok(r.dm.addLink("https://example.org/a.mkv"))
        r.sizes["https://example.org/b.mkv"] = G + (G / 2)
        assertIs<DownloadManager.Result.Refused>(r.dm.addLink("https://example.org/b.mkv"))
    }

    @Test fun engineMissingOrDownIsReportedPlainly() {
        val r = DlRig(); r.dm.acceptWarning(); r.engineUp = false
        val no = r.dm.addLink("https://example.org/a.mkv") as DownloadManager.Result.Refused
        assertEquals(503, no.http)
        val missing = DownloadManager(r.registry, File(r.root, "w2"), { null }, { DownloadManager.EngineStatus(false, false, "UNAVAILABLE", Aria2Supervisor.NOT_SHIPPED) })
        missing.acceptWarning()
        assertTrue((missing.addLink("https://example.org/a.mkv") as DownloadManager.Result.Refused).message.contains("non inclus"))
        assertTrue(missing.listJson().contains("\"available\":false"))
    }

    @Test fun finishedFileGoesToTheLibrary() {
        val r = DlRig(); r.dm.acceptWarning()
        File(r.usbDir, "film.mkv").writeText("older file with the same name")
        val id = r.ok(r.dm.addLink("https://example.org/film.mkv"))
        val gid = r.gidOf(id)
        r.fake.complete(gid)
        r.dm.tick()
        assertTrue(File(r.usbDir, "film (2).mkv").isFile, "moved next to the library, without replacing the older file")
        assertFalse(File(r.usbDir, ".cb-downloads/$id").exists(), "task folder removed")
        assertTrue(r.dm.views().isEmpty())
        val f = r.dm.finished().single()
        assertEquals(listOf("film (2).mkv"), f.files); assertEquals("usb", f.volumeId)
        assertEquals(f, r.finished.single())
        assertFalse(r.fake.dls.containsKey(gid), "result removed from aria2")
        // The library of the CastBridge server lists it (top-level files of the volume).
        assertTrue(FileStore(r.registry["usb"]!!).list().any { it.name == "film (2).mkv" && !it.part })
        // Removing the entry with its files deletes the library file.
        r.ok(r.dm.remove(f.id, deleteFiles = true))
        assertFalse(File(r.usbDir, "film (2).mkv").exists())
    }

    @Test fun multiFileTorrentKeepsOnlyMediaInTheLibrary() {
        val r = DlRig(); r.dm.acceptWarning()
        val b = Benc.torrent("Série", listOf("S01/E01.mkv" to 10L, "S01/E01.srt" to 5L, "lisez-moi.nfo" to 3L))
        val id = r.ok(r.dm.addFile("torrent", b))
        assertEquals("Série", r.task(id).name)
        assertIs<DownloadManager.Result.Refused>(r.dm.addFile("torrent", b), "same torrent twice")
        r.fake.complete(r.gidOf(id))
        r.dm.tick()
        assertTrue(File(r.usbDir, "E01.mkv").isFile); assertTrue(File(r.usbDir, "E01.srt").isFile)
        assertFalse(File(r.usbDir, "lisez-moi.nfo").exists())
        val f = r.dm.finished().single()
        assertTrue(f.extras); assertEquals(setOf("E01.mkv", "E01.srt"), f.files.toSet())
        assertTrue(File(r.usbDir, ".cb-downloads/$id/Série/lisez-moi.nfo").isFile)
    }

    @Test fun pullingTheDrivePausesAndItsReturnResumes() {
        val r = DlRig(); r.dm.acceptWarning()
        val id = r.ok(r.dm.addLink("https://example.org/film.mkv"))
        val gid = r.gidOf(id)
        r.usbPresent = false; r.registry.refresh()
        r.dm.tick()
        assertEquals(DlState.WAITING_DRIVE, r.task(id).state)
        assertEquals("paused", r.fake.dls[gid]!!.status)
        r.usbPresent = true; r.registry.refresh()
        r.dm.tick()
        assertEquals("active", r.fake.dls[gid]!!.status)
        assertNotEquals(DlState.WAITING_DRIVE, r.task(id).state)
    }

    @Test fun drivePulledWhileAria2FailedIsRestartedOnReturn() {
        val r = DlRig(); r.dm.acceptWarning()
        val id = r.ok(r.dm.addLink("https://example.org/film.mkv"))
        val gid = r.gidOf(id)
        r.fake.dls[gid]!!.apply { status = "error"; errorCode = 17 }
        r.usbPresent = false; r.registry.refresh(); r.dm.tick()
        assertEquals(DlState.WAITING_DRIVE, r.task(id).state)
        r.usbPresent = true; r.registry.refresh(); r.dm.tick()
        val again = r.fake.dls.values.last()
        assertNotEquals(gid, again.gid); assertEquals("active", again.status)
        assertEquals(File(r.usbDir, ".cb-downloads/$id").absolutePath, again.dir, "same folder: aria2 continues from its control file")
    }

    @Test fun spaceRunningOutPausesThenResumes() {
        val r = DlRig(); r.dm.acceptWarning()
        val id = r.ok(r.dm.addLink("https://example.org/unknown-size.mkv"))
        val dl = r.fake.dls.values.single()
        dl.total = 4 * G; dl.completed = G
        r.free["usb"] = 3 * G                                  // 3 GB still to come, 3 GB free: 0 left at the end
        r.dm.tick()
        assertEquals(DlState.WAITING_SPACE, r.task(id).state)
        assertEquals("paused", dl.status)
        assertFalse(r.task(id).state.canPause)
        assertIs<DownloadManager.Result.Refused>(r.dm.resume(id), "resume by hand refused while there is no room")
        r.free["usb"] = 5 * G                                  // someone deleted a film
        r.dm.tick()
        assertEquals("active", dl.status)
    }

    @Test fun magnetChecksSpaceOnceTheSizeIsKnown() {
        val r = DlRig(); r.dm.acceptWarning()
        r.free["internal"] = 20 * G; r.free["usb"] = 5 * G + (G / 2)
        val hash = "0123456789abcdef0123456789abcdef01234567"
        val id = r.ok(r.dm.addLink("magnet:?xt=urn:btih:$hash&dn=Film"))
        assertEquals("usb", r.task(id).volumeId)
        r.dm.tick()
        assertEquals(DlState.METADATA, r.task(id).state)
        assertIs<DownloadManager.Result.Refused>(r.dm.addLink("magnet:?xt=urn:btih:$hash"), "same magnet twice")
        // Metadata fetched: aria2 creates the real download, paused (--pause-metadata). It is 5 GB: no room on the drive.
        val meta = r.fake.dls.values.single()
        val real = FakeAria2.Dl(r.fake.gid(), "paused", 5 * G, 0, meta.dir, listOf(Triple(meta.dir + "/Film.mkv", 5 * G, true)), infoHash = hash, btName = "Film")
        r.fake.dls[real.gid] = real
        meta.status = "complete"; meta.followedBy = listOf(real.gid)
        r.dm.tick()
        assertEquals("internal", r.task(id).volumeId, "moved to the volume with room")
        assertEquals(File(r.internalDir, ".cb-downloads/$id").absolutePath, real.dir)
        assertEquals("active", real.status)
        assertEquals("Film", r.task(id).name); assertEquals(5 * G, r.task(id).total)
        assertFalse(r.fake.dls.containsKey(meta.gid))
    }

    @Test fun magnetTooBigForEveryVolumeWaitsForRoom() {
        val r = DlRig(); r.dm.acceptWarning()
        val id = r.ok(r.dm.addLink("magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567"))
        val meta = r.fake.dls.values.single()
        val real = FakeAria2.Dl(r.fake.gid(), "paused", 60 * G, 0, meta.dir, listOf(Triple(meta.dir + "/x.mkv", 60 * G, true)))
        r.fake.dls[real.gid] = real; meta.status = "complete"; meta.followedBy = listOf(real.gid)
        r.dm.tick()
        val v = r.task(id)
        assertEquals(DlState.WAITING_SPACE, v.state); assertTrue(v.error!!.contains("Pas assez de place"))
        assertEquals("paused", real.status)
    }

    @Test fun pauseResumeRemoveAndOptions() {
        val r = DlRig(); r.dm.acceptWarning()
        val id = r.ok(r.dm.addLink("https://example.org/a.mkv"))
        val gid = r.gidOf(id)
        r.ok(r.dm.pause(id)); assertEquals(DlState.PAUSED, r.task(id).state)
        r.ok(r.dm.resume(id)); assertEquals("active", r.fake.dls[gid]!!.status)
        r.ok(r.dm.setOptions(id, mapOf("max-download-limit" to "300K")))
        assertEquals("300K", r.fake.dls[gid]!!.options["max-download-limit"])
        assertIs<DownloadManager.Result.Refused>(r.dm.setOptions(id, mapOf("on-download-complete" to "/bin/sh")))
        assertIs<DownloadManager.Result.Refused>(r.dm.priority(id, "top"), "an active download has no place in the queue")
        r.ok(r.dm.changeSettings(mapOf("downLimit" to "2M", "seeding" to "1")))
        assertEquals((2L shl 20).toString(), r.fake.globalOptions["max-overall-download-limit"])
        assertEquals(Aria2Config.SEED_RATIO_ON, r.fake.globalOptions["seed-ratio"])
        File(r.usbDir, ".cb-downloads/$id").mkdirs(); File(r.usbDir, ".cb-downloads/$id/a.mkv").writeText("partial")
        r.ok(r.dm.remove(id, deleteFiles = true))
        assertEquals("removed", r.fake.dls[gid]!!.status)
        assertFalse(File(r.usbDir, ".cb-downloads/$id").exists())
        assertTrue(r.dm.views().isEmpty())
        r.dm.tick()
        assertFalse(r.fake.dls.containsKey(gid), "result purged")
    }

    @Test fun lostByAria2IsAddedAgain() {
        val r = DlRig(); r.dm.acceptWarning()
        val id = r.ok(r.dm.addLink("https://example.org/a.mkv"))
        r.fake.dls.clear()                                     // aria2 crashed before saving its session
        r.dm.tick()
        assertEquals(1, r.fake.dls.size)
        assertEquals(File(r.usbDir, ".cb-downloads/$id").absolutePath, r.fake.dls.values.single().dir)
    }

    @Test fun apiIsRelayedOnlyBehindThePin() {
        val r = DlRig()
        val port = ServerSocket(0).use { it.localPort }
        val server = ReceiverServer(r.registry, FakePlayer(), port, pin = "123456", guard = PinGuard("123456", maxFailures = 1000),
            extension = ApiExtension { _, _, _ -> null }.then(r.dm.apiExtension)).apply { start(5000, false) }
        try {
            fun call(method: String, path: String, pin: String?, body: ByteArray? = null): Pair<Int, String> {
                val c = URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection
                c.requestMethod = method
                pin?.let { c.setRequestProperty("X-CB-Pin", it) }
                if (method == "POST") { c.doOutput = true; val b = body ?: ByteArray(0); c.setFixedLengthStreamingMode(b.size); c.outputStream.use { it.write(b) } }
                val code = c.responseCode
                return code to ((if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.readText().orEmpty())
            }
            assertEquals(401, call("GET", "/api/downloads", null).first)
            assertEquals(401, call("POST", "/api/downloads/add?url=https%3A%2F%2Fx.org%2Fa.mkv", "000000").first)
            val (code, list) = call("GET", "/api/downloads", "123456")
            assertEquals(200, code); assertTrue(list.contains("\"warningAccepted\":false"))
            assertEquals(409, call("POST", "/api/downloads/add?url=https%3A%2F%2Fx.org%2Fa.mkv", "123456").first)
            assertEquals(200, call("POST", "/api/downloads/accept", "123456").first)
            val (c2, added) = call("POST", "/api/downloads/add?url=https%3A%2F%2Fx.org%2Fa.mkv&opt.split=4", "123456")
            assertEquals(200, c2, added)
            assertEquals("4", r.fake.dls.values.single().options["split"])
            assertEquals(400, call("POST", "/api/downloads/add?url=https%3A%2F%2Fx.org%2Fb.mkv&opt.on-download-complete=%2Fbin%2Fsh", "123456").first)
            assertEquals(400, call("POST", "/api/downloads/add?url=file%3A%2F%2F%2Fetc%2Fpasswd", "123456").first)
            // .torrent uploaded as the request body.
            val (c3, up) = call("POST", "/api/downloads/upload?name=film.torrent", "123456", Benc.torrent("Film.mkv", length = 1234))
            assertEquals(200, c3, up)
            assertTrue(call("GET", "/api/downloads", "123456").second.contains("Film.mkv"))
            assertEquals(413, call("POST", "/api/downloads/upload?name=x.torrent", "123456", ByteArray(0)).first)
            assertEquals(405, call("GET", "/api/downloads/pause", "123456").first)
            // The rest of the API is untouched.
            assertEquals(200, call("GET", "/api/info", "123456").first)
        } finally { server.stop() }
    }
}

class DownloadsClientTest {
    @Test fun phoneClientDrivesTheTv() {
        val r = DlRig()
        val port = ServerSocket(0).use { it.localPort }
        val server = ReceiverServer(r.registry, FakePlayer(), port, pin = "654321", guard = PinGuard("654321", maxFailures = 1000),
            extension = r.dm.apiExtension).apply { start(5000, false) }
        try {
            val c = DownloadsClient("http://127.0.0.1:$port", "654321")
            assertFalse(c.state().warningAccepted)
            val w = assertFailsWith<DownloadsClient.Refused> { c.add("https://example.org/a.mkv") }
            assertEquals("warning", w.code)
            c.accept()
            c.add("https://example.org/a.mkv")
            c.upload("film.torrent", Benc.torrent("Film.mkv", length = 999))
            val s = c.state()
            assertEquals(2, s.tasks.size); assertTrue(s.engineRunning)
            val t = s.tasks.first { it.name == "Film.mkv" }
            assertEquals(999, t.total); assertEquals("Clé USB", t.volumeLabel)
            assertEquals(1, c.files(t.id).size)
            c.pause(t.id); assertTrue(c.state().tasks.first { it.id == t.id }.canResume)
            c.resume(t.id)
            c.settings(downLimit = 1_000_000, seeding = false)
            assertEquals(1_000_000, c.state().downLimit)
            r.fake.complete(r.fake.dls.values.first { it.btName == "Film.mkv" }.gid); r.dm.tick()
            assertEquals(listOf("Film.mkv"), c.state().done.single().files)
            assertTrue(c.about().contains("GPL"))
            assertEquals(401, assertFailsWith<DownloadsClient.Refused> { DownloadsClient("http://127.0.0.1:$port", "111111").state() }.http)
        } finally { server.stop() }
    }

    @Test fun adminPageHasTheDownloadsSection() {
        val html = ReceiverServer::class.java.getResourceAsStream("/castbridge/admin.html")!!.readBytes().toString(Charsets.UTF_8)
        assertTrue(html.contains("id=\"dl\"") && html.contains("/api/downloads/add") && html.contains("/api/downloads/upload"))
    }

    @Test fun findsTheLinkInSharedText() {
        assertEquals("https://example.org/a.mkv", DownloadsClient.findLink("Regarde ça : https://example.org/a.mkv."))
        assertEquals("magnet:?xt=urn:btih:abc&dn=x", DownloadsClient.findLink("magnet:?xt=urn:btih:abc&dn=x"))
        assertNull(DownloadsClient.findLink("pas de lien ici"))
    }
}

class SupervisorTest {
    /** A process that "runs" until destroyed or [crash]ed; serves a fake aria2 RPC on the port given on its command line. */
    class FakeProc(cmd: List<String>, val fake: FakeAria2) : Process() {
        private val done = java.util.concurrent.CountDownLatch(1)
        val port = cmd.first { it.startsWith("--rpc-listen-port=") }.substringAfter('=').toInt()
        val conf = File(cmd.first { it.startsWith("--conf-path=") }.substringAfter('='))
        val secret = conf.readText().substringAfter("rpc-secret=").trim()
        private val rpcFake = FakeAria2(secret).also { f -> f.dls.putAll(fake.dls); f.onShutdown = { crash() } }
        private val http = rpcFake.serve(port)
        val shutdownAsked get() = rpcFake.shutdownCalled
        var destroyed = false
        fun crash() { http.stop(); done.countDown() }
        override fun getOutputStream() = java.io.ByteArrayOutputStream()
        override fun getInputStream() = java.io.ByteArrayInputStream("[WARN] something\n".toByteArray())
        override fun getErrorStream() = java.io.ByteArrayInputStream(ByteArray(0))
        override fun waitFor(): Int { done.await(); return 0 }
        override fun waitFor(timeout: Long, unit: TimeUnit) = done.await(timeout, unit)
        override fun exitValue(): Int { if (done.count > 0) throw IllegalThreadStateException(); return 0 }
        override fun destroy() { destroyed = true; crash() }
        override fun isAlive() = done.count > 0
    }

    @Test fun startsRestartsAfterACrashAndStopsCleanly() {
        val dir = kotlin.io.path.createTempDirectory("sup").toFile()
        val bin = File(dir, "libaria2c.so").apply { writeText("#!/bin/sh\n") }
        val procs = java.util.Collections.synchronizedList(ArrayList<FakeProc>())
        val ready = java.util.concurrent.LinkedBlockingQueue<Aria2Rpc>()
        val sup = Aria2Supervisor(bin, dir, { conf, port -> listOf("--conf-path=${conf.absolutePath}", "--rpc-listen-port=$port") },
            launcher = { cmd, _, _ -> FakeProc(cmd, FakeAria2()).also { procs += it } }, onReady = { ready += it },
            backoffMs = { 10 }, preferredPort = ServerSocket(0).use { it.localPort })
        sup.start()
        assertNotNull(ready.poll(10, TimeUnit.SECONDS))
        assertEquals(Aria2Supervisor.State.RUNNING, sup.state)
        assertEquals("1.37.0", sup.version)
        val first = procs.single()
        assertFalse(first.conf.exists(), "the conf file holding the secret is deleted once aria2 is up")
        assertFalse(sup.log().any { it.contains(first.secret) })
        first.crash()
        assertNotNull(ready.poll(10, TimeUnit.SECONDS), "restarted")
        assertEquals(2, procs.size)
        assertNotEquals(first.secret, procs[1].secret, "a new secret at every start")
        sup.stop()
        assertEquals(Aria2Supervisor.State.STOPPED, sup.state)
        assertFalse(procs[1].isAlive, "no process left behind")
        assertTrue(procs[1].shutdownAsked, "clean shutdown through the RPC first (aria2 saves its session)")
        assertFalse(procs[1].destroyed, "no kill needed")
        assertNull(sup.rpc)
    }

    @Test fun missingEngineIsReportedNotStarted() {
        val sup = Aria2Supervisor(File("/nonexistent/libaria2c.so"), kotlin.io.path.createTempDirectory("sup").toFile(), { _, _ -> emptyList() })
        sup.start()
        assertEquals(Aria2Supervisor.State.UNAVAILABLE, sup.state)
        assertTrue(sup.message.contains("non inclus"))
    }

    @Test fun givesUpAfterRepeatedCrashes() {
        val dir = kotlin.io.path.createTempDirectory("sup").toFile()
        val bin = File(dir, "libaria2c.so").apply { writeText("x") }
        val sup = Aria2Supervisor(bin, dir, { _, _ -> emptyList() },
            launcher = { _, _, _ -> throw java.io.IOException("exec format error") }, backoffMs = { 1 }, maxCrashes = 3)
        sup.start()
        val deadline = System.currentTimeMillis() + 10_000
        while (sup.state != Aria2Supervisor.State.FAILED && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertEquals(Aria2Supervisor.State.FAILED, sup.state)
        sup.stop()
    }
}
