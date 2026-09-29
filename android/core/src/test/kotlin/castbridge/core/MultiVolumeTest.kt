package castbridge.core

import castbridge.core.tv.*
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import kotlin.random.Random
import kotlin.test.*

/** Two temporary folders standing for the internal storage and a USB drive, plus a switchable "hot plug". */
class Rig(pin: String? = null, profile: TvProfile = TvProfile(minFreeBytes = 0, minFreeAfterTransfer = 0), fs: Fs = Fs.EXFAT, settingsOpener: (() -> String?)? = null) {
    val root = kotlin.io.path.createTempDirectory("vol").toFile()
    val internalDir = File(root, "internal").apply { mkdirs() }
    val usbDir = File(root, "usb").apply { mkdirs() }
    var usbPresent = true
    var usbFs = fs
    var usbWritable = true
    var usbBps = 0L
    /** Simulated capacity per volume id (free = capacity - bytes stored); absent = the real disk. */
    val capacity = mutableMapOf<String, Long>()
    val notices = java.util.Collections.synchronizedList(ArrayList<String>())
    val player = FakePlayer()

    val provider = StaticVolumes {
        listOf(StorageVolume("internal", "Mémoire interne", internalDir, VolumeKind.INTERNAL, Fs.UNKNOWN, 0, 0, false)) +
            (if (usbPresent) listOf(StorageVolume("usb-1234", "Clé USB", usbDir, VolumeKind.REMOVABLE, usbFs, 0, 0, true, usbWritable, usbBps)) else emptyList())
    }
    val registry = VolumeRegistry(provider) { v -> capacity[v.id]?.let { it - used(v.dir) } ?: v.dir.usableSpace }.also { it.refresh() }
    val port = ServerSocket(0).use { it.localPort }
    val base = "http://127.0.0.1:$port"
    val server = ReceiverServer(registry, player, port, profile = profile, pin = pin, guard = pin?.let { PinGuard(it, maxFailures = 1000) }, onNotice = { notices += it }, settingsOpener = settingsOpener)
        .apply { start(5000, false) }
    val tv = TvClient(base, pin)

    fun used(d: File) = d.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    fun unplug() { usbPresent = false; registry.refresh() }
    fun replug() { usbPresent = true; registry.refresh() }
    fun close() { server.stop(); root.deleteRecursively() }

    fun put(name: String, offset: Long, total: Long, bytes: ByteArray): Pair<Int, String> {
        val c = URL("$base/upload/${TvClient.enc(name)}?offset=$offset&total=$total").openConnection() as HttpURLConnection
        c.requestMethod = "PUT"; c.doOutput = true; c.setFixedLengthStreamingMode(bytes.size)
        c.outputStream.use { it.write(bytes) }
        val code = c.responseCode
        return code to (if (code < 400) c.inputStream else c.errorStream).readBytes().decodeToString()
    }

    fun call(method: String, path: String): Pair<Int, String> {
        val c = URL(base + path).openConnection() as HttpURLConnection
        c.requestMethod = method
        pin0?.let { c.setRequestProperty("X-CB-Pin", it) }
        if (method == "POST") { c.doOutput = true; c.setFixedLengthStreamingMode(0); c.outputStream.close() }
        val code = c.responseCode
        return code to ((if (code < 400) c.inputStream else c.errorStream)?.readBytes()?.decodeToString() ?: "")
    }
    private val pin0 = pin

    fun up(name: String, data: ByteArray) = tv.upload(name, 0, data.size.toLong(), ByteArrayInputStream(data)) {}

    fun awaitMove(state: String = "done", ms: Long = 20_000): String {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) {
            val j = tv.storage()
            if (j.contains("\"state\":\"$state\"")) return j
            if (state != "failed" && j.contains("\"state\":\"failed\"")) fail("move failed: $j")
            Thread.sleep(30)
        }
        fail("move did not reach $state: ${tv.storage()}")
    }
}

class MultiVolumeServerTest {
    private val r = Rig()
    private val data = Random(7).nextBytes(700_000)
    private fun visible(d: File) = d.listFiles()!!.count { !it.name.startsWith(".") }
    @AfterTest fun tearDown() = r.close()

    @Test fun autoTargetsTheDriveAndTheListingAggregates() {
        r.up("a.mp4", data)
        assertTrue(File(r.usbDir, "a.mp4").isFile); assertFalse(File(r.internalDir, "a.mp4").exists())
        File(r.internalDir, "b.mp4").writeBytes(ByteArray(50))
        val j = r.tv.info()
        assertTrue(j.contains("""{"name":"a.mp4","size":${data.size},"received":${data.size},"complete":true,"volume":"usb-1234","duplicate":false}"""), j)
        assertTrue(j.contains("""{"name":"b.mp4","size":50,"received":50,"complete":true,"volume":"internal","duplicate":false}"""), j)
        assertEquals(data.size.toLong(), TvClient.num(j, "used"), "top-level numbers describe the volume the next upload would go to")
        assertTrue(j.contains("\"volumes\":[") && j.contains("\"id\":\"usb-1234\"") && j.contains("\"fs\":\"exFAT\"") && j.contains("\"removable\":true"))
    }

    @Test fun internalTargetAndFallbackWhenTheDriveIsAbsent() {
        r.tv.setTarget("internal"); r.up("i.mp4", data)
        assertTrue(File(r.internalDir, "i.mp4").isFile)
        r.tv.setTarget("auto"); r.unplug(); r.up("j.mp4", data)
        assertTrue(File(r.internalDir, "j.mp4").isFile, "no drive: internal, transparently")
    }

    @Test fun explicitDriveTargetWithoutDriveAnswers503() {
        r.tv.setTarget("usb-1234"); r.unplug()
        val (code, body) = r.put("x.mp4", 0, 1000, ByteArray(1000))
        assertEquals(503, code); assertTrue(body.contains("volume unavailable"), body)
        assertEquals(0, r.internalDir.listFiles()!!.size)
    }

    @Test fun targetApiValidatesAndNeverAcceptsPaths() {
        assertEquals(400, r.call("POST", "/api/storage/target?value=${TvClient.enc("/etc")}").first)
        assertEquals(400, r.call("POST", "/api/storage/target?value=${TvClient.enc("../usb")}").first)
        assertEquals(400, r.call("POST", "/api/storage/target?value=nope").first)
        assertEquals(400, r.call("POST", "/api/storage/target").first)
        assertEquals(200, r.call("POST", "/api/storage/target?value=usb-1234").first)
        assertTrue(r.tv.storage().contains("\"target\":\"usb-1234\""))
    }

    @Test fun targetIsPersistedThroughTheSettingsCallback() {
        var saved: TvProfile? = null
        val port = ServerSocket(0).use { it.localPort }
        val s = ReceiverServer(r.registry, FakePlayer(), port, profile = TvProfile(minFreeBytes = 0, minFreeAfterTransfer = 0), onSettings = { saved = it }).apply { start(5000, false) }
        try {
            TvClient("http://127.0.0.1:$port").setTarget("internal")
            assertEquals("internal", saved?.target)
        } finally { s.stop() }
    }

    @Test fun partAndMetaLiveNextToEachOtherOnTheChosenVolume() {
        assertEquals(200, r.put("p.mp4", 0, data.size.toLong(), data.copyOf(300_000)).first)
        assertEquals(300_000, File(r.usbDir, "p.mp4.part").length()); assertTrue(File(r.usbDir, "p.mp4.meta").isFile)
        assertFalse(File(r.internalDir, "p.mp4.part").exists() || File(r.internalDir, "p.mp4.meta").exists())
        assertEquals(300_000, r.tv.part("p.mp4").length)
        assertEquals(409, r.put("p.mp4", 100, data.size.toLong(), ByteArray(10)).first, "offset must match")
        assertTrue(r.tv.info().contains("""{"name":"p.mp4","size":${data.size},"received":300000,"complete":false,"volume":"usb-1234","""))
    }

    @Test fun everyRouteResolvesAFileOnAnyVolume() {
        File(r.usbDir, "usb.mp4").writeBytes(data)
        File(r.internalDir, "int.mp4").writeBytes(data.copyOf(1000))
        assertTrue(r.tv.part("usb.mp4").done && r.tv.part("int.mp4").done)
        // stream with Range from the drive
        val c = URL("${r.base}/stream/usb.mp4").openConnection() as HttpURLConnection
        c.setRequestProperty("Range", "bytes=10-19")
        assertEquals(206, c.responseCode); assertContentEquals(data.copyOfRange(10, 20), c.inputStream.readBytes())
        // play: the player receives the file inside the drive's folder
        r.tv.play("usb.mp4"); assertEquals("usb.mp4", r.player.st.name)
        r.tv.play("int.mp4"); assertEquals("int.mp4", r.player.st.name)
        // rename on the drive, delete on each
        r.tv.rename("usb.mp4", "renamed.mp4")
        assertTrue(File(r.usbDir, "renamed.mp4").isFile && !File(r.usbDir, "usb.mp4").exists())
        assertEquals(409, r.call("POST", "/api/rename?name=renamed.mp4&to=int.mp4").first, "target name exists on another volume")
        r.tv.delete("renamed.mp4"); r.tv.delete("int.mp4")
        assertEquals(0, visible(r.usbDir)); assertEquals(0, visible(r.internalDir))
    }

    @Test fun progressivePlaybackReadsThePartOnTheDrive() {
        val big = Random(3).nextBytes(6_000_000)
        r.put("live.mp4", 0, big.size.toLong(), big.copyOf(3_000_000))
        assertEquals(200, r.call("POST", "/api/play?name=live.mp4").first)
        assertNotNull(r.player.lastUrl)
        val c = URL(r.player.lastUrl!!).openConnection() as HttpURLConnection
        c.setRequestProperty("Range", "bytes=0-99")
        assertEquals(206, c.responseCode); assertContentEquals(big.copyOf(100), c.inputStream.readBytes())
    }

    @Test fun duplicatesAreReportedAndReplacedUploadsLeaveNoSilentCopy() {
        File(r.internalDir, "d.mp4").writeBytes(ByteArray(10))
        File(r.usbDir, "d.mp4").writeBytes(ByteArray(20))
        val j = r.tv.info()
        assertEquals(2, Regex("\"name\":\"d.mp4\".*?\"duplicate\":true").findAll(j).count(), j)
        assertTrue(r.tv.storage().contains("en double"), "warning mentions duplicates")
        assertEquals(200, r.call("POST", "/api/delete?name=d.mp4&volume=internal").first)
        assertTrue(File(r.usbDir, "d.mp4").isFile && !File(r.internalDir, "d.mp4").exists())
        // a new upload of the same name replaces the old copy wherever it is
        File(r.internalDir, "r.mp4").writeBytes(ByteArray(5))
        r.up("r.mp4", data)
        assertTrue(File(r.usbDir, "r.mp4").isFile && !File(r.internalDir, "r.mp4").exists())
    }

    @Test fun fat32NamesAreMappedAndFoundByBothNames() {
        r.usbFs = Fs.FAT32; r.registry.refresh()
        val odd = "film: 1*?.mp4"
        r.up(odd, data)
        val stored = r.usbDir.listFiles()!!.single { !it.name.startsWith(".") }.name
        assertTrue(stored.none { it in "\\/:*?\"<>|" } && stored.endsWith(".mp4"), stored)
        assertTrue(r.tv.part(odd).done, "found by the name the phone used")
        assertTrue(r.tv.part(stored).done, "and by the name shown in the listing")
        assertTrue(r.tv.info().contains("\"name\":${ReceiverServer.q(stored)}"))
        r.tv.play(odd); assertEquals(stored, r.player.st.name)
        r.tv.delete(odd); assertEquals(0, visible(r.usbDir))
    }

    @Test fun fat32RefusesAFileOverFourGiBBeforeReceivingItAndAutoFallsBackToInternal() {
        r.usbFs = Fs.FAT32; r.registry.refresh()
        r.capacity["internal"] = 100L shl 30; r.capacity["usb-1234"] = 100L shl 30
        val big = (5L shl 30)
        // pre-flight (what the phone calls first): internal takes it, with a note about the ignored drive
        val ok = r.tv.checkStorage("big.mkv", big)
        assertTrue(ok.ok); assertEquals("internal", ok.volume); assertTrue(ok.warnings.any { it.contains("ignoré") }, ok.warnings.toString())
        assertEquals(200, r.put("big.mkv", 0, big, ByteArray(1000)).first)
        assertEquals(1000, File(r.internalDir, "big.mkv.part").length()); assertFalse(File(r.usbDir, "big.mkv.part").exists())
        // forced onto the FAT32 drive: refused up front, precisely
        r.tv.setTarget("usb-1234")
        val chk = r.tv.checkStorage("big2.mkv", big)
        assertFalse(chk.ok); assertEquals(413, chk.status); assertTrue(chk.message.contains("FAT32") && chk.message.contains("exFAT"), chk.message)
        val (code, body) = r.put("big2.mkv", 0, big, ByteArray(1000))
        assertEquals(413, code); assertTrue(body.contains("FAT32"), body)
        assertFalse(File(r.usbDir, "big2.mkv.part").exists(), "nothing written")
        // just under the limit is fine on FAT32
        assertTrue(r.tv.checkStorage("ok.mkv", (4L shl 30) - 1).ok)
    }

    @Test fun uploadClientFailsFastWithAClearMessageForAFatDriveWithoutFallback() {
        r.usbFs = Fs.FAT32; r.registry.refresh(); r.tv.setTarget("usb-1234")
        r.capacity["usb-1234"] = 100L shl 30
        var warned = false
        val up = ResumableUpload("huge.mkv", 6L shl 30, { r.base }, { throw AssertionError("nothing may be sent") }, sleep = { })
        val res = up.run { if (it is ResumableUpload.State.Failed) warned = true }
        assertTrue(res is ResumableUpload.State.Failed && res.reason.contains("FAT32"), res.toString()); assertTrue(warned)
    }

    @Test fun preflightWarnsAboutASlowDrive() {
        r.usbBps = 900_000; r.registry.refresh(); r.capacity["internal"] = 100L shl 30
        val c = r.tv.checkStorage("s.mp4", 1L shl 30)
        // slow drives are demoted in auto: internal is chosen and the drive is not mentioned as an error
        assertTrue(c.ok); assertEquals("internal", c.volume)
        r.tv.setTarget("usb-1234")
        val forced = r.tv.checkStorage("s.mp4", 1L shl 30, durMs = 600_000)
        assertTrue(forced.ok && forced.warnings.any { it.contains("lente") }, forced.warnings.toString())
    }

    @Test fun readOnlyDriveIsIgnoredAndExplainedInStorageInfo() {
        r.usbFs = Fs.NTFS; r.usbWritable = false; r.registry.refresh()
        r.up("n.mp4", data)
        assertTrue(File(r.internalDir, "n.mp4").isFile)
        val j = r.tv.storage()
        assertTrue(j.contains("\"writable\":false") && j.contains("formatAdvice\":\"Écriture impossible") && j.contains("exFAT") && j.contains("ne formate jamais"), j)
    }

    @Test fun fat32DriveGetsFormatAdviceInStorageInfo() {
        r.usbFs = Fs.FAT32; r.registry.refresh()
        assertTrue(r.tv.storage().contains("FAT32 ne stocke pas"), r.tv.storage())
    }

    @Test fun storageRouteReportsNoAdviceForAGoodDrive() {
        assertTrue(r.tv.storage().contains("\"formatAdvice\":null"))
    }

    @Test fun ejectAndRemountAreDetectedByRescan() {
        r.usbPresent = false
        assertTrue(r.call("POST", "/api/storage/rescan").second.contains("\"id\":\"usb-1234\",\"label\":\"Clé USB\",\"kind\":\"removable\",\"fs\":\"exFAT\",\"present\":false"))
        assertTrue(r.notices.any { it.contains("retiré") })
        r.usbPresent = true
        assertTrue(r.call("POST", "/api/storage/rescan").second.contains("\"present\":true"))
        assertTrue(r.notices.any { it.contains("détecté") })
    }

    @Test fun orphansAreCleanedPerVolumeWithALongerGraceOnDrives() {
        val day = 24L * 3600_000
        fun w(dir: File, n: String, ageMs: Long) = File(dir, n).apply { writeText("x"); setLastModified(System.currentTimeMillis() - ageMs) }
        w(r.internalDir, "old.mp4.part", 2 * day); w(r.usbDir, "away3d.mp4.part", 3 * day); w(r.usbDir, "away9d.mp4.part", 9 * day)
        w(r.usbDir, "away9d.mp4.meta", 9 * day)
        val p2 = ServerSocket(0).use { it.localPort }
        val s2 = ReceiverServer(r.registry, FakePlayer(), p2, profile = TvProfile(minFreeBytes = 0, minFreeAfterTransfer = 0)).apply { start(5000, false) }
        try {
            assertFalse(File(r.internalDir, "old.mp4.part").exists())
            assertTrue(File(r.usbDir, "away3d.mp4.part").exists(), "a drive that was away keeps partial uploads for a week")
            assertFalse(File(r.usbDir, "away9d.mp4.part").exists() || File(r.usbDir, "away9d.mp4.meta").exists())
        } finally { s2.stop() }
    }

    @Test fun openSettingsRouteIsAbsentUnlessTheAppProvidesIt() {
        assertEquals(501, r.call("POST", "/api/storage/open-settings").first)
        val r2 = Rig(settingsOpener = { "Aucun réglage de stockage sur cette TV" })
        try {
            val (c, b) = r2.call("POST", "/api/storage/open-settings")
            assertEquals(200, c); assertTrue(b.contains("\"opened\":false") && b.contains("Aucun réglage"), b)
        } finally { r2.close() }
        val r3 = Rig(settingsOpener = { null })
        try { assertTrue(r3.call("POST", "/api/storage/open-settings").second.contains("\"opened\":true")) } finally { r3.close() }
    }

    @Test fun safPickerRouteIsNotSupportedWithoutTheApp() = assertEquals(501, r.call("POST", "/api/storage/saf/pick").first)
}

class HotRemovalTest {
    private val r = Rig()
    private val data = Random(11).nextBytes(24_000_000)
    @AfterTest fun tearDown() = r.close()

    @Test fun uploadAnswers503WhileTheDriveIsAwayThenResumesWhenItReturns() {
        assertEquals(200, r.put("v.mp4", 0, data.size.toLong(), data.copyOf(2_000_000)).first)
        r.unplug()
        val (c, b) = r.put("v.mp4", 2_000_000, data.size.toLong(), data.copyOfRange(2_000_000, 3_000_000))
        assertEquals(503, c); assertEquals("""{"error":"volume removed"}""", b)
        assertEquals(503, r.call("GET", "/api/part?name=v.mp4").first, "must not report 0 bytes: the phone would restart elsewhere")
        assertEquals(503, r.put("v.mp4", 0, 5, ByteArray(5)).first)
        assertEquals(0, r.internalDir.listFiles()!!.filter { it.name.startsWith("v.mp4") }.size, "no second copy started on internal")
        assertFalse(r.tv.checkStorage("v.mp4", data.size.toLong()).ok)
        r.replug()
        assertEquals(2_000_000, r.tv.part("v.mp4").length, "the partial upload on the drive is found again")
        r.up2("v.mp4", data, 2_000_000)
        assertContentEquals(data, File(r.usbDir, "v.mp4").readBytes())
        assertFalse(File(r.usbDir, "v.mp4.part").exists())
    }

    private fun Rig.up2(name: String, data: ByteArray, from: Int) {
        val res = ResumableUpload(name, data.size.toLong(), { base }, { off -> ByteArrayInputStream(data).also { it.skip(off) } }, sleep = {}).run {}
        assertEquals(ResumableUpload.State.Done, res)
    }

    @Test fun driveRemovedInTheMiddleOfAnUploadIsRetriedLikeANetworkCut() {
        val states = ArrayList<ResumableUpload.State>()
        var pulled = false
        val src = { off: Long ->
            object : InputStream() {
                var pos = off.toInt()
                override fun read(): Int = throw UnsupportedOperationException()
                override fun read(b: ByteArray, o: Int, len: Int): Int {
                    if (pos >= data.size) return -1
                    if (pos > 3_000_000 && !pulled) { pulled = true; r.unplug() }       // the drive is pulled mid-transfer
                    val n = minOf(len, data.size - pos, 200_000)
                    System.arraycopy(data, pos, b, o, n); pos += n; return n
                }
            }
        }
        val up = ResumableUpload("mid.mp4", data.size.toLong(), { r.base }, src, sleep = {
            if (!r.usbPresent) { Thread.sleep(50); r.replug() }        // the user puts the drive back
        })
        val res = up.run { states += it }
        assertEquals(ResumableUpload.State.Done, res, states.takeLast(3).toString())
        assertTrue(pulled)
        assertTrue(states.any { it is ResumableUpload.State.Waiting }, "the phone waited instead of failing")
        assertContentEquals(data, File(r.usbDir, "mid.mp4").readBytes())
        assertFalse(File(r.internalDir, "mid.mp4").exists() || File(r.internalDir, "mid.mp4.part").exists())
        assertFalse(File(r.usbDir, "mid.mp4.part").exists())
    }

    @Test fun removalWhilePlayingStopsPlaybackWithAMessage() {
        File(r.usbDir, "p.mp4").writeBytes(ByteArray(1000))
        r.tv.play("p.mp4"); assertEquals("playing", r.player.st.state)
        r.unplug()
        assertEquals("idle", r.player.st.state)
        assertTrue(r.notices.any { it.contains("lecture arrêtée") }, r.notices.toString())
        assertEquals(404, r.call("GET", "/stream/p.mp4").first)
    }

    @Test fun aStreamOfAVanishedDriveFailsCleanlyWithoutWaiting() {
        val d = File(r.root, "gs").apply { mkdirs() }
        File(d, "g.mp4.part").writeBytes(ByteArray(10))
        var alive = true
        val s = GrowingStream(d, "g.mp4", 0, 999, waitMs = 60_000, alive = { alive })
        assertEquals(10, s.read(ByteArray(100), 0, 100))
        alive = false
        val e = assertFailsWith<IOException> { s.read(ByteArray(100), 0, 100) }
        assertTrue(e.message!!.contains("volume removed"))
    }

    @Test fun ioErrorWithAMissingFolderIsTreatedAsRemoval() {
        val failing = object : VolumeProvider {
            override fun scan(remeasure: Boolean) = r.provider.scan()
            override fun storeFor(volume: StorageVolume): VolumeStore = FileStore(volume).let { fs ->
                if (volume.kind != VolumeKind.REMOVABLE) fs else object : VolumeStore by fs {
                    override fun openPart(name: String): OutputStream = object : OutputStream() {
                        override fun write(b: Int) = throw IOException("I/O error")
                        override fun write(b: ByteArray, o: Int, l: Int) { r.usbDir.deleteRecursively(); throw IOException("I/O error") }
                    }
                }
            }
        }
        val reg = VolumeRegistry(failing).also { it.refresh() }
        val port = ServerSocket(0).use { it.localPort }
        val s = ReceiverServer(reg, FakePlayer(), port, profile = TvProfile(minFreeBytes = 0, minFreeAfterTransfer = 0)).apply { start(5000, false) }
        try {
            val c = URL("http://127.0.0.1:$port/upload/x.mp4?offset=0&total=100").openConnection() as HttpURLConnection
            c.requestMethod = "PUT"; c.doOutput = true; c.setFixedLengthStreamingMode(100); c.outputStream.use { it.write(ByteArray(100)) }
            assertEquals(503, c.responseCode); assertTrue(c.errorStream.readBytes().decodeToString().contains("volume removed"))
            assertNull(reg["usb-1234"], "the registry no longer offers the drive")
        } finally { s.stop() }
    }
}

class EvictionPerVolumeTest {
    private val r = Rig(profile = TvProfile(minFreeBytes = 0, minFreeAfterTransfer = 0, evictPlayed = true, quotaBytes = 100))
    @AfterTest fun tearDown() = r.close()

    @Test fun evictsOnlyOnTheTargetVolumeOldestPlayedFirstNeverTheOneInUse() {
        r.capacity["usb-1234"] = 1000; r.capacity["internal"] = 10_000
        r.up("old.mp4", ByteArray(400)); r.up("mid.mp4", ByteArray(400))          // drive: 200 free
        File(r.internalDir, "innocent.mp4").writeBytes(ByteArray(300))
        r.tv.play("innocent.mp4")                                                    // played, on the other volume, oldest of all
        File(r.internalDir, "innocent.mp4").setLastModified(1_000)
        File(r.usbDir, "old.mp4").setLastModified(1_000_000); File(r.usbDir, "mid.mp4").setLastModified(2_000_000)
        r.tv.setTarget("usb-1234")
        // nothing was played on the drive yet: 400 bytes do not fit and nothing may be evicted
        assertEquals(507, assertFailsWith<TvClient.HttpError> { r.up("new.mp4", ByteArray(400)) }.code)
        r.tv.play("old.mp4"); r.tv.play("mid.mp4")                                   // mid is playing now, old is played and oldest
        r.up("new.mp4", ByteArray(400))
        assertFalse(File(r.usbDir, "old.mp4").exists(), "the oldest played file of that drive made room")
        assertTrue(File(r.usbDir, "mid.mp4").exists(), "the file in use survives")
        assertTrue(File(r.usbDir, "new.mp4").exists())
        assertTrue(File(r.internalDir, "innocent.mp4").exists(), "other volumes are never touched")
    }

    @Test fun theExplicitInternalQuotaDoesNotApplyToADrive() {
        r.up("big.mp4", ByteArray(5000))                              // quotaBytes = 100 would refuse this on internal
        assertTrue(File(r.usbDir, "big.mp4").isFile)
        r.tv.setTarget("internal")
        assertEquals(507, assertFailsWith<TvClient.HttpError> { r.up("big2.mp4", ByteArray(5000)) }.code)
    }

    @Test fun autoFallsBackToInternalWhenTheDriveCannotTakeTheFile() {
        val r2 = Rig(profile = TvProfile(minFreeBytes = 0, minFreeAfterTransfer = 0, evictPlayed = true))
        try {
            r2.capacity["usb-1234"] = 300; r2.capacity["internal"] = 10_000
            r2.up("first.mp4", ByteArray(250))
            assertTrue(File(r2.usbDir, "first.mp4").isFile, "the drive is preferred")
            r2.up("second.mp4", ByteArray(250))                        // drive has 50 free and nothing played to evict
            assertTrue(File(r2.internalDir, "second.mp4").isFile, "next file goes to internal")
            val j = r2.tv.info()
            assertTrue(j.contains("\"volume\":\"usb-1234\"") && j.contains("\"volume\":\"internal\""))
        } finally { r2.close() }
    }
}

class MoveTest {
    private val r = Rig()
    private val data = Random(5).nextBytes(1_500_000)
    @AfterTest fun tearDown() = r.close()

    @Test fun movesADriveFileToInternalAndBackWithVerification() {
        r.up("m.mp4", data)
        assertTrue(File(r.usbDir, "m.mp4").isFile)
        val resp = r.tv.moveFile("m.mp4", "internal")
        assertTrue(resp.contains("\"moving\":true"), resp)
        val done = r.awaitMove()
        assertTrue(done.contains("\"name\":\"m.mp4\"") && done.contains("\"total\":${data.size}"), done)
        assertContentEquals(data, File(r.internalDir, "m.mp4").readBytes())
        assertFalse(File(r.usbDir, "m.mp4").exists(), "source removed after verification")
        assertFalse(File(r.internalDir, "m.mp4.part").exists() || File(r.internalDir, "m.mp4.meta").exists())
        assertTrue(r.tv.info().contains("\"volume\":\"internal\""))
        r.tv.moveFile("m.mp4", "usb-1234"); r.awaitMove()
        assertContentEquals(data, File(r.usbDir, "m.mp4").readBytes()); assertFalse(File(r.internalDir, "m.mp4").exists())
    }

    @Test fun neverWhilePlayingAndNeverOverAnExistingFile() {
        r.up("busy.mp4", data)
        r.tv.play("busy.mp4")
        assertEquals(409, assertFailsWith<TvClient.HttpError> { r.tv.moveFile("busy.mp4", "internal") }.code)
        r.tv.stop()
        File(r.internalDir, "busy.mp4").writeBytes(ByteArray(3))
        assertEquals(409, assertFailsWith<TvClient.HttpError> { r.tv.moveFile("busy.mp4", "internal") }.code)
        File(r.usbDir, "solo.mp4").writeBytes(ByteArray(3))
        assertEquals(400, assertFailsWith<TvClient.HttpError> { r.tv.moveFile("solo.mp4", "usb-1234") }.code, "already there")
        assertEquals(404, assertFailsWith<TvClient.HttpError> { r.tv.moveFile("busy.mp4", "../internal") }.code)
        assertEquals(404, assertFailsWith<TvClient.HttpError> { r.tv.moveFile("nope.mp4", "internal") }.code)
        assertEquals(400, assertFailsWith<TvClient.HttpError> { r.tv.moveFile("../x", "internal") }.code)
    }

    @Test fun refusedWhenTheTargetHasNoRoomAndTheSourceIsIntact() {
        r.up("room.mp4", data)
        r.capacity["internal"] = 1_000_000                            // less than the file
        val e = assertFailsWith<TvClient.HttpError> { r.tv.moveFile("room.mp4", "internal") }
        assertEquals(507, e.code)
        assertContentEquals(data, File(r.usbDir, "room.mp4").readBytes())
    }

    @Test fun deleteDuringAMoveIsRefusedOrHarmlessButTheDataIsNeverLost() {
        r.up("slow.mp4", data)
        r.tv.moveFile("slow.mp4", "internal")
        val c = r.call("POST", "/api/delete?name=slow.mp4&volume=usb-1234").first
        assertTrue(c == 200 || c == 409, "$c")                        // 409 while moving; 200 only if the move had already finished
        r.awaitMove()
        if (c == 409) assertContentEquals(data, File(r.internalDir, "slow.mp4").readBytes())
    }
}

class MoverUnitTest {
    private val root = kotlin.io.path.createTempDirectory("mv").toFile()
    private val a = File(root, "a").apply { mkdirs() }
    private val b = File(root, "b").apply { mkdirs() }
    private val va = StorageVolume("a", "A", a, VolumeKind.INTERNAL)
    private val vb = StorageVolume("b", "B", b, VolumeKind.REMOVABLE, Fs.EXFAT)
    private val data = Random(9).nextBytes(1_000_000)
    @AfterTest fun tearDown() { root.deleteRecursively() }

    private fun job() = MoveJob("f.mp4", va, vb, "f.mp4", data.size.toLong())

    @Test fun cancelKeepsAResumablePartialThenResumeCompletes() {
        File(a, "f.mp4").writeBytes(data)
        val j1 = job()
        var blocks = 0
        Mover.run(j1, FileStore(va), FileStore(vb), { blocks++; if (blocks == 5) j1.cancelled = true; true }, bufferBytes = 64 * 1024)
        assertEquals("cancelled", j1.state)
        val part = File(b, "f.mp4.part")
        assertTrue(part.length() in 1 until data.size, "partial copy kept: ${part.length()}")
        assertTrue(File(a, "f.mp4").isFile, "source untouched")
        assertFalse(File(b, "f.mp4").exists())
        val j2 = job()
        Mover.run(j2, FileStore(va), FileStore(vb), { true })
        assertEquals("done", j2.state, j2.error)
        assertContentEquals(data, File(b, "f.mp4").readBytes()); assertFalse(File(a, "f.mp4").exists())
        assertFalse(part.exists())
    }

    @Test fun aFailedSizeVerificationKeepsTheSource() {
        File(a, "f.mp4").writeBytes(data)
        val dst = FileStore(vb)
        val lying = object : VolumeStore by dst {
            override fun commit(name: String) { dst.commit(name); File(b, name).writeBytes(ByteArray(10)) }    // the medium "lost" data
        }
        val j = job()
        Mover.run(j, FileStore(va), lying, { true })
        assertEquals("failed", j.state); assertTrue(j.error!!.contains("size mismatch"), j.error)
        assertContentEquals(data, File(a, "f.mp4").readBytes(), "source still there")
    }

    @Test fun aVolumeRemovedDuringTheCopyFailsWithoutLosingTheSource() {
        File(a, "f.mp4").writeBytes(data)
        val j = job(); var n = 0
        Mover.run(j, FileStore(va), FileStore(vb), { v -> n++; !(v.id == "b" && n > 6) })
        assertEquals("failed", j.state); assertTrue(j.error!!.contains("volume removed"))
        assertTrue(File(a, "f.mp4").isFile); assertTrue(File(b, "f.mp4.part").exists(), "resumable")
    }

    @Test fun aStalePartialFromADifferentSourceRestartsFromZero() {
        File(a, "f.mp4").writeBytes(data)
        File(b, "f.mp4.part").writeBytes(ByteArray(500)); File(b, "f.mp4.meta").writeText("12345")
        val j = job(); Mover.run(j, FileStore(va), FileStore(vb), { true })
        assertEquals("done", j.state); assertContentEquals(data, File(b, "f.mp4").readBytes())
    }
}

class StorageSecurityTest {
    private val r = Rig(pin = "123456")
    @AfterTest fun tearDown() = r.close()

    @Test fun everyNewRouteNeedsThePin() {
        for ((m, p) in listOf("GET" to "/api/storage", "GET" to "/api/storage/check?name=a.mp4&size=10", "POST" to "/api/storage/target?value=auto",
            "POST" to "/api/storage/move?name=a.mp4&to=internal", "POST" to "/api/storage/move/cancel", "POST" to "/api/storage/rescan",
            "POST" to "/api/storage/saf/pick", "POST" to "/api/storage/open-settings", "PUT" to "/upload/a.mp4?offset=0&total=1")) {
            val c = URL(r.base + p).openConnection() as HttpURLConnection
            c.requestMethod = m
            if (m != "GET") { c.doOutput = true; c.setFixedLengthStreamingMode(0); c.outputStream.close() }
            assertEquals(401, c.responseCode, "$m $p")
        }
        assertTrue(TvClient(r.base, "123456").storage().contains("volumes"))
    }

    @Test fun noAbsolutePathOrTraversalIsEverAccepted() {
        for (n in listOf("../x.mp4", "..%2Fx.mp4", "/etc/passwd", "a/b.mp4", "..\\x.mp4")) {
            assertEquals(400, r.call("POST", "/api/delete?name=${TvClient.enc(n)}").first, n)
            assertEquals(400, r.call("GET", "/api/storage/check?name=${TvClient.enc(n)}&size=5").first, n)
            assertEquals(400, r.call("POST", "/api/storage/move?name=${TvClient.enc(n)}&to=internal").first, n)
        }
        assertEquals(404, r.call("POST", "/api/storage/move?name=a.mp4&to=${TvClient.enc("/tmp")}").first)
        assertEquals(400, r.call("POST", "/api/storage/target?value=${TvClient.enc("/tmp")}").first)
        assertFalse(r.call("GET", "/api/storage").second.contains(r.root.absolutePath), "no absolute path is ever disclosed")
    }
}

class ClientHelpersTest {
    @Test fun strListParsesEscapedStrings() {
        val j = """{"a":1,"warnings":["x \"q\"","é, ] y"],"z":[]}"""
        assertEquals(listOf("x \"q\"", "é, ] y"), TvClient.strList(j, "warnings"))
        assertEquals(emptyList(), TvClient.strList(j, "z")); assertEquals(emptyList(), TvClient.strList(j, "nope"))
    }
}
