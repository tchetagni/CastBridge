package castbridge.core.tv

import castbridge.core.FakePlayer
import castbridge.core.library.agent.TrashApi
import castbridge.core.owner.TrialPolicy
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import castbridge.core.xfer.*
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import kotlin.random.Random
import kotlin.test.*

/** The filing at reception and « Ranger ma bibliothèque » through the real HTTP server (docs/STORAGE.md). */
class FilingServerTest {
    private val root = kotlin.io.path.createTempDirectory("filing").toFile()
    private val internalDir = File(root, "internal").apply { mkdirs() }
    private val usbDir = File(root, "usb/Download/CastBridge/Bibliotheque").apply { mkdirs() }
    private val notices = java.util.Collections.synchronizedList(ArrayList<String>())
    private val folders = FolderIndex(File(root, "folders.db"))
    private val protectedNames = HashSet<String>()
    private var childActive = false
    private var trial = false
    private var lang: String? = "fr"
    private var usbPresent = true
    private val provider = StaticVolumes {
        listOf(StorageVolume("internal", "Mémoire interne", internalDir, VolumeKind.INTERNAL, Fs.UNKNOWN, 0, 0, false)) +
            (if (usbPresent) listOf(StorageVolume("usb-1", "Clé USB", usbDir, VolumeKind.REMOVABLE, Fs.EXFAT, 0, 0, true)) else emptyList())
    }
    private val registry = VolumeRegistry(provider).also { it.refresh() }
    private val player = FakePlayer()
    private val flags = object : ContentFlags {
        override fun childActive() = childActive
        override fun protectedNames(items: List<LibraryItem>) = items.map { it.name }.filter { it in protectedNames }.toSet()
    }
    private val server = ReceiverServer(registry, player, 0, profile = TvProfile(minFreeBytes = 0, minFreeAfterTransfer = 0, target = "internal"), onNotice = { notices += it },
        routeGuard = { path -> if (trial && TrialPolicy.routeBlocked(path)) TrialPolicy.MESSAGE else null },
        contentFlags = flags, folders = folders, filingLang = { lang },
        extension = ApiExtension { path, m, p -> TrashApi(registry, folders = folders, changed = { }).handle(path, m, p) },
        hostCheck = false).apply { start(5000, false) }      // port 0: the server binds a free port itself (no close-then-reuse race)
    private val port = server.listeningPort
    private val base = "http://127.0.0.1:$port"
    private val tv = TvClient(base)
    private val data = Random(7).nextBytes(400_000)

    @AfterTest fun tearDown() { server.stop(); root.deleteRecursively() }

    private fun call(method: String, path: String): Pair<Int, String> {
        val c = URL(base + path).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.setRequestProperty("Connection", "close")        // a refused request must not leave a pooled connection the next call would reuse
        c.connectTimeout = 5000; c.readTimeout = 10_000
        if (method == "POST") { c.doOutput = true; c.setFixedLengthStreamingMode(0); c.outputStream.close() }
        val code = c.responseCode
        return code to ((if (code < 400) c.inputStream else c.errorStream)?.readBytes()?.decodeToString() ?: "")
    }
    private fun put(name: String, offset: Long, total: Long, bytes: ByteArray): Pair<Int, String> {
        val c = URL("$base/upload/${TvClient.enc(name)}?offset=$offset&total=$total").openConnection() as HttpURLConnection
        c.requestMethod = "PUT"; c.doOutput = true; c.setFixedLengthStreamingMode(bytes.size)
        c.setRequestProperty("Connection", "close"); c.connectTimeout = 5000; c.readTimeout = 10_000
        c.outputStream.use { it.write(bytes) }
        val code = c.responseCode
        return code to (if (code < 400) c.inputStream else c.errorStream).readBytes().decodeToString()
    }
    private fun send(name: String, bytes: ByteArray = data) = tv.upload(name, 0, bytes.size.toLong(), ByteArrayInputStream(bytes)) {}
    private fun allFiles(dir: File) = dir.walkTopDown().filter { it.isFile }.map { it.relativeTo(dir).path.replace('\\', '/') }.filter { !it.startsWith(".") && !it.substringAfterLast('/').startsWith(".") }.toList().sorted()

    @Test fun aReceivedFileIsFiledUnderACleanNameAndTheAnswerSaysWhere() {
        val r = send("Prison.Break.S01E04.720p.HDTV.x264-GRP.mkv")
        assertTrue(r.done)
        assertEquals(listOf("Séries/Prison Break/Saison 01/Prison Break – S01E04.mkv"), allFiles(internalDir))
        assertFalse(File(internalDir, "Prison.Break.S01E04.720p.HDTV.x264-GRP.mkv").exists())
        assertContentEquals(data, File(internalDir, "Séries/Prison Break/Saison 01/Prison Break – S01E04.mkv").readBytes())
        assertTrue(notices.any { it.contains("Prison Break – S01E04") && it.contains("→ Séries/Prison Break/Saison 01") }, notices.toString())
        // the phone's library shows the real folder, and the file is reachable by its clean name
        val lib = tv.library()
        assertTrue(lib.contains("\"folder\":\"Séries/Prison Break/Saison 01\""), lib)
        assertEquals("Séries/Prison Break/Saison 01", folders.folderOf("Prison Break – S01E04.mkv"))
        val info = tv.info()
        assertTrue(info.contains("\"folder\":\"Séries/Prison Break/Saison 01\"") && info.contains("\"origin\":\"Prison.Break.S01E04.720p.HDTV.x264-GRP.mkv\""), info)
        val file = TvInfo.parse(info).file("Prison.Break.S01E04.720p.HDTV.x264-GRP.mkv")
        assertEquals("Prison Break – S01E04.mkv", file!!.name)
    }

    @Test fun oldPhonesKeepWorkingByTheOriginalName() {
        send("The.Matrix.1999.1080p.BluRay.x264.mkv")
        // /api/part by the name the phone sent: finished (resume logic of every phone version)
        val p = tv.part("The.Matrix.1999.1080p.BluRay.x264.mkv")
        assertTrue(p.done); assertEquals(data.size.toLong(), p.length)
        val (code, body) = call("GET", "/api/part?name=${TvClient.enc("The.Matrix.1999.1080p.BluRay.x264.mkv")}")
        assertEquals(200, code); assertTrue(body.contains("\"finalName\":\"The Matrix (1999).mkv\"") && body.contains("\"folder\":\"Films\""), body)
        // sending it again (same name, same size): nothing is copied, nothing is duplicated
        val again = put("The.Matrix.1999.1080p.BluRay.x264.mkv", 0, data.size.toLong(), ByteArray(0))
        assertEquals(200, again.first); assertTrue(again.second.contains("\"done\":true"))
        assertEquals(listOf("Films/The Matrix (1999).mkv"), allFiles(internalDir))
        // a same-named file of ANOTHER size is another file: uploaded, filed, numbered, nothing overwritten
        val other = data.copyOf(1000)
        assertTrue(send("The.Matrix.1999.1080p.BluRay.x264.mkv", other).done)
        assertEquals(listOf("Films/The Matrix (1999) (2).mkv", "Films/The Matrix (1999).mkv"), allFiles(internalDir))
        assertContentEquals(data, File(internalDir, "Films/The Matrix (1999).mkv").readBytes())
        assertContentEquals(other, File(internalDir, "Films/The Matrix (1999) (2).mkv").readBytes())
    }

    @Test fun resumeAfterACutThenFilingOnlyWhenComplete() {
        val name = "Inception (2010) MULTI 1080p.mkv"
        val total = data.size.toLong()
        assertEquals(200, put(name, 0, total, data.copyOfRange(0, 150_000)).first)
        // the partial copy is flat, with its real name: the resume bitmap of every phone still works
        assertEquals(listOf("$name.part"), internalDir.listFiles().orEmpty().map { it.name }.filter { it.endsWith(".part") })
        assertEquals(emptyList(), allFiles(internalDir).filter { it.contains('/') })
        assertEquals(150_000L, tv.part(name).length); assertFalse(tv.part(name).done)
        assertEquals(409, put(name, 0, total, data.copyOfRange(0, 10)).first)           // a wrong offset still answers its length
        assertEquals(200, put(name, 150_000, total, data.copyOfRange(150_000, data.size)).first)
        assertEquals(listOf("Films/Inception (2010) [MULTI].mkv"), allFiles(internalDir))
        assertTrue(tv.part(name).done)
        assertFalse(File(internalDir, "$name.part").exists())
    }

    @Test fun anInterruptedSenderResumesWithTheResumableUploadClient() {
        var attempts = 0
        val up = ResumableUpload("Burna Boy - Last Last (Official Video).mp4", data.size.toLong(), { base },
            { off -> attempts++; object : InputStream() {
                var pos = off.toInt(); var emitted = 0
                override fun read(): Int = throw UnsupportedOperationException()
                override fun read(b: ByteArray, o: Int, len: Int): Int {
                    if (attempts == 1 && emitted >= 100_000) throw IOException("wifi lost")
                    if (pos >= data.size) return -1
                    val n = minOf(len, data.size - pos, if (attempts == 1) 100_000 - emitted else Int.MAX_VALUE)
                    System.arraycopy(data, pos, b, o, n); pos += n; emitted += n; return n
                }
            } }, sleep = {})
        assertEquals(ResumableUpload.State.Done, up.run { })
        assertEquals(listOf("Musique/Clips/Burna Boy – Last Last.mp4"), allFiles(internalDir))
    }

    @Test fun installersStayFlatOnReceptionAndAreFiledOnlyOnDemand() {
        send("installer.apk"); send("pack.learn.zip")
        assertEquals(listOf("installer.apk", "pack.learn.zip"), allFiles(internalDir))
        val plan = call("GET", "/api/library/organize").second
        assertTrue(plan.contains("\"to\":\"installer.apk\"") && plan.contains("Applications"), plan)
        assertTrue(plan.contains("pack.learn.zip") && plan.contains("\"skippedCount\":1"), plan)
        call("POST", "/api/library/organize/apply")
        assertEquals(listOf("Applications/installer.apk", "pack.learn.zip"), allFiles(internalDir))
    }

    @Test fun unknownNamesGoToATrierWithTheirOwnName() {
        send("ma_video.mp4"); send("vacances.jpg"); send("facture.pdf")
        assertEquals(setOf("Documents/facture.pdf", "Photos/vacances.jpg", "À trier/ma_video.mp4"), allFiles(internalDir).toSet())
    }

    @Test fun theSettingOffKeepsTheOldFlatBehaviour() {
        lang = null
        send("Prison.Break.S01E04.720p.mkv")
        assertEquals(listOf("Prison.Break.S01E04.720p.mkv"), allFiles(internalDir))
        assertFalse(tv.info().contains("\"origin\""))
    }

    @Test fun theUsbDriveGetsTheSameFoldersUnderBibliotheque() {
        // a drive present and preferred: files are filed inside Download/CastBridge/Bibliotheque (the app folder of the drive)
        assertTrue(server.setTargetValue("usb-1"))
        send("Les.Revenants.S02E03.FRENCH.720p.mkv")
        assertEquals(listOf("Séries/Les Revenants/Saison 02/Les Revenants – S02E03.mkv"), allFiles(usbDir))
        assertEquals(emptyList(), allFiles(internalDir))
        assertTrue(File(root, "usb/Download/CastBridge/Bibliotheque/Séries").isDirectory)
    }

    @Test fun trialRefusesUploadsAndOrganize() {
        trial = true
        assertEquals(403, put("film.2010.mkv", 0, 10, ByteArray(10)).first, "upload")
        assertEquals(403, call("GET", "/api/library/organize").first, "plan")
        assertEquals(403, call("POST", "/api/library/organize/apply").first, "apply")
        assertEquals(403, call("POST", "/api/transfer/begin?name=a.mkv&size=1&blockSize=1024").first, "transfer")
        assertEquals(emptyList(), allFiles(internalDir))
    }

    @Test fun organizeFilesTheFlatLibraryAfterThePlanAndIsIdempotent() {
        lang = null                                                                          // a library received before the feature: flat
        send("The.Matrix.1999.1080p.mkv"); send("Prison.Break.S01E04.720p.mkv"); send("Prison.Break.S01E04.1080p.WEB.mkv"); send("facture.pdf")
        lang = "fr"
        assertEquals(4, allFiles(internalDir).size); assertTrue(allFiles(internalDir).none { it.contains('/') })
        val plan = call("GET", "/api/library/organize").second
        assertTrue(plan.contains("\"count\":4") && plan.contains("\"canApply\":true"), plan)
        assertEquals(4, allFiles(internalDir).size); assertTrue(allFiles(internalDir).none { it.contains('/') }, "a plan moves nothing")
        val (code, res) = call("POST", "/api/library/organize/apply")
        assertEquals(200, code); assertTrue(res.contains("\"moved\":4") && res.contains("\"remaining\":0"), res)
        assertEquals(setOf("Documents/facture.pdf", "Films/The Matrix (1999).mkv", "Séries/Prison Break/Saison 01/Prison Break – S01E04.mkv", "Séries/Prison Break/Saison 01/Prison Break – S01E04 (2).mkv"), allFiles(internalDir).toSet())
        assertTrue(notices.any { it.startsWith("Bibliothèque rangée : 4") })
        // filing twice changes nothing
        val again = call("GET", "/api/library/organize").second
        assertTrue(again.contains("\"count\":0") && again.contains("Rien à ranger"), again)
        val r2 = call("POST", "/api/library/organize/apply").second
        assertTrue(r2.contains("\"moved\":0"), r2)
        assertEquals(4, allFiles(internalDir).size)
        // the old flat names still resolve for « already on the TV »
        assertTrue(tv.part("The.Matrix.1999.1080p.mkv").done)
        // and the files are readable / deletable by their new names
        assertEquals("Films", folders.folderOf("The Matrix (1999).mkv"))
        assertEquals(data.size, URL("$base/stream/${TvClient.enc("The Matrix (1999).mkv")}").openConnection().getInputStream().use { it.readBytes().size })
        tv.delete("The Matrix (1999).mkv")
        assertFalse(File(internalDir, "Films/The Matrix (1999).mkv").exists())
        assertFalse(tv.library().contains("The Matrix"))
    }

    @Test fun parentalProtectedFilesAreNeverMovedNorNamed() {
        lang = null
        send("The.Matrix.1999.1080p.mkv"); send("Secret.Film.2001.1080p.mkv"); send("facture.pdf")
        lang = "fr"
        protectedNames += "Secret.Film.2001.1080p.mkv"
        val plan = call("GET", "/api/library/organize").second
        assertTrue(plan.contains("\"count\":2") && plan.contains("\"protected\":1"), plan)
        assertFalse(plan.contains("Secret"), "the name of a protected file never leaves the TV")
        call("POST", "/api/library/organize/apply")
        assertTrue(File(internalDir, "Secret.Film.2001.1080p.mkv").isFile, "protected: untouched")
        assertTrue(File(internalDir, "Films/The Matrix (1999).mkv").isFile)
    }

    @Test fun nothingIsFiledWhileAChildProfileIsActive() {
        lang = null; send("The.Matrix.1999.1080p.mkv"); lang = "fr"
        childActive = true
        val plan = call("GET", "/api/library/organize").second
        assertTrue(plan.contains("\"canApply\":false") && plan.contains("\"childActive\":true"), plan)
        assertEquals(403, call("POST", "/api/library/organize/apply").first)
        assertTrue(File(internalDir, "The.Matrix.1999.1080p.mkv").isFile)
    }

    @Test fun aFileBeingReadStaysFlatUntilOrganize() {
        lang = null; send("The.Matrix.1999.1080p.mkv"); lang = "fr"
        tv.play("The.Matrix.1999.1080p.mkv")
        val plan = call("GET", "/api/library/organize").second
        assertTrue(plan.contains("\"count\":0") && plan.contains("occupé"), plan)
        tv.stop()
        assertTrue(call("GET", "/api/library/organize").second.contains("\"count\":1"))
    }

    @Test fun renameKeepsAFiledFileInItsFolderAndTheBinWorks() {
        send("The.Matrix.1999.1080p.mkv")
        assertEquals(200, call("POST", "/api/rename?name=${TvClient.enc("The Matrix (1999).mkv")}&to=${TvClient.enc("Matrix.mkv")}").first)
        assertEquals(listOf("Films/Matrix.mkv"), allFiles(internalDir))
        assertEquals("Matrix.mkv", TvInfo.parse(tv.info()).file("The.Matrix.1999.1080p.mkv")!!.name)
        // the recoverable bin takes it from its folder and gives it back
        val put = call("POST", "/api/trash/put?name=Matrix.mkv")
        assertEquals(200, put.first, put.second)
        assertEquals(emptyList(), allFiles(internalDir))
        val id = TvClient.str(put.second, "id")!!
        assertEquals(200, call("POST", "/api/trash/restore?id=${TvClient.enc(id)}").first)
        assertEquals(1, allFiles(internalDir).size)
        assertTrue(tv.library().contains("Matrix.mkv"))
    }

    @Test fun quotaCountsFiledFiles() {
        send("The.Matrix.1999.1080p.mkv")
        assertEquals(data.size.toLong(), Storage.used(internalDir))
        assertEquals(1, Storage.files(internalDir).size)
    }

    @Test fun lostIndexStillRecognisesTheSameFileByItsCleanName() {
        send("The.Matrix.1999.1080p.mkv")
        File(internalDir, FiledIndex.FILE).delete()
        // a TV restarted with a lost index (new store instance): files are re-adopted from the category folders, the clean name still answers
        server.stop()
        val s2 = ReceiverServer(VolumeRegistry(StaticVolumes { listOf(StorageVolume("internal", "Mémoire interne", internalDir, VolumeKind.INTERNAL, Fs.UNKNOWN, 0, 0, false)) }.let { it }).also { it.refresh() },
            player, 0, profile = TvProfile(minFreeBytes = 0, minFreeAfterTransfer = 0), filingLang = { "fr" }, hostCheck = false)
        s2.start(5000, false)
        try {
            val port2 = s2.listeningPort
            val c = TvClient("http://127.0.0.1:$port2")
            assertTrue(c.part("The Matrix (1999).mkv").done)
            // the original name is recognised through the name it would be filed as (same name, same size), so no "(2)" copy appears
            assertEquals(200, run {
                val u = URL("http://127.0.0.1:$port2/upload/${TvClient.enc("The.Matrix.1999.1080p.mkv")}?offset=0&total=${data.size}").openConnection() as HttpURLConnection
                u.requestMethod = "PUT"; u.doOutput = true; u.setFixedLengthStreamingMode(1); u.outputStream.use { it.write(1) }; u.responseCode
            })
            assertEquals(listOf("Films/The Matrix (1999).mkv"), allFiles(internalDir))
        } finally { s2.stop() }
    }

    @Test fun aMultiConnectionTransferIsFiledToo() {
        val big = Random(3).nextBytes(5_000_000)
        val f = File(root, "src.bin").apply { writeBytes(big) }
        val ch = FileChannel.open(f.toPath(), StandardOpenOption.READ)
        fun run() = ch.use {
            TransferClient(HttpTransferApi(base) { null }, FileBlockSource(it), "Les.Revenants.S02E03.FRENCH.720p.mkv", { id, max ->
                listOf(WifiLane("wifi", "127.0.0.1:$port", id, HttpConn.tcp("127.0.0.1", port), { null }, maxStreams = max, fixedK = 3))
            }, retryDelayMs = 50).run()
        }
        assertEquals(TransferClient.Result.Done, run())
        assertEquals(listOf("Séries/Les Revenants/Saison 02/Les Revenants – S02E03.mkv"), allFiles(internalDir))
        assertContentEquals(big, File(internalDir, "Séries/Les Revenants/Saison 02/Les Revenants – S02E03.mkv").readBytes())
        assertTrue(notices.any { it.contains("→ Séries/Les Revenants/Saison 02") })
        // the same file offered again (same name, same size): « already there », nothing copied, nothing numbered
        val ch2 = FileChannel.open(f.toPath(), StandardOpenOption.READ)
        val again = ch2.use {
            TransferClient(HttpTransferApi(base) { null }, FileBlockSource(it), "Les.Revenants.S02E03.FRENCH.720p.mkv", { id, max ->
                listOf(WifiLane("wifi", "127.0.0.1:$port", id, HttpConn.tcp("127.0.0.1", port), { null }, maxStreams = max, fixedK = 3))
            }, retryDelayMs = 50).run()
        }
        assertEquals(TransferClient.Result.Done, again)
        assertEquals(1, allFiles(internalDir).size)
    }
}
