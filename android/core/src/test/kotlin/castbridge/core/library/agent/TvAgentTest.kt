package castbridge.core.library.agent

import castbridge.core.FakePlayer
import castbridge.core.net.JsonLite
import castbridge.core.tv.*
import java.io.File
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import kotlin.random.Random
import kotlin.test.*

/** A real TV server (two folders standing for the internal memory and a USB key) with the bin routes, driven over HTTP like the phone does. */
class AgentRig(pin: String? = "123456", withTrash: Boolean = true, now: () -> Long = System::currentTimeMillis, retentionMs: Long = TrashApi.RETENTION_MS) {
    val root = kotlin.io.path.createTempDirectory("agent").toFile()
    val internalDir = File(root, "internal").apply { mkdirs() }
    val usbDir = File(root, "usb").apply { mkdirs() }
    val player = FakePlayer()
    val capacity = mutableMapOf<String, Long>()
    val registry = VolumeRegistry(StaticVolumes {
        listOf(
            StorageVolume("internal", "Mémoire interne", internalDir, VolumeKind.INTERNAL, Fs.UNKNOWN, 0, 0, false),
            StorageVolume("usb-1234", "Clé USB", usbDir, VolumeKind.REMOVABLE, Fs.EXFAT, 0, 0, true, true, 0),
        )
    }) { v -> capacity[v.id]?.let { it - used(v.dir) } ?: v.dir.usableSpace }.also { it.refresh() }
    val port = ServerSocket(0).use { it.localPort }
    val base = "http://127.0.0.1:$port"
    val trashApi = TrashApi(registry, playing = { player.state().takeIf { it.state != "idle" }?.name }, now = now, retentionMs = retentionMs)
    val server = ReceiverServer(registry, player, port, profile = TvProfile(minFreeBytes = 0, minFreeAfterTransfer = 0), pin = pin,
        guard = pin?.let { PinGuard(it, maxFailures = 1000) }, extension = if (withTrash) trashApi else null,
        contentFlags = object : ContentFlags { override fun childActive() = false; override fun protectedNames(items: List<LibraryItem>) = emptySet<String>() }).apply { start(5000, false) }
    val tv = TvClient(base, pin)

    fun used(d: File) = d.walkTopDown().filter { it.isFile && !it.path.contains(TrashApi.BIN) }.sumOf { it.length() }
    fun put(vol: String, name: String, size: Int, seed: Int = name.hashCode()): File = File(if (vol == "internal") internalDir else usbDir, name).also { it.writeBytes(Random(seed).nextBytes(size)) }
    fun close() { server.stop(); root.deleteRecursively() }
    fun call(method: String, path: String, pin: String? = "123456"): Pair<Int, String> {
        val c = URL(base + path).openConnection() as HttpURLConnection
        c.requestMethod = method
        pin?.let { c.setRequestProperty("X-CB-Pin", it) }
        if (method == "POST") { c.doOutput = true; c.setFixedLengthStreamingMode(0); c.outputStream.close() }
        val code = c.responseCode
        return code to ((if (code < 400) c.inputStream else c.errorStream)?.readBytes()?.decodeToString() ?: "")
    }
    fun listing(): List<String> = listOf(internalDir, usbDir).flatMap { d -> d.listFiles().orEmpty().toList() }.filter { it.isFile }.map { it.name }.sorted()
}

class TvAgentTest {
    private val r = AgentRig()
    @AfterTest fun tearDown() = r.close()

    // ------------------------------------------------------------------ the bin routes

    @Test fun trashPutListRestoreKeepsTheFileAndHidesItFromTheLibrary() {
        r.put("internal", "Film.mkv", 50_000)
        val (code, body) = r.call("POST", "/api/trash/put?name=Film.mkv")
        assertEquals(200, code, body)
        val id = JsonLite.obj(body)["id"] as String
        assertFalse(File(r.internalDir, "Film.mkv").exists())
        assertTrue(File(r.internalDir, TrashApi.BIN).listFiles()!!.single().length() == 50_000L, "the bytes are still there")
        assertFalse(r.tv.library().contains("Film.mkv"), "the library no longer lists it")
        val list = JsonLite.obj(r.call("GET", "/api/trash").second)
        assertEquals(30L, list["retentionDays"])
        assertEquals(1L, list["count"])
        val (c2, b2) = r.call("POST", "/api/trash/restore?id=$id")
        assertEquals(200, c2, b2)
        assertEquals(50_000L, File(r.internalDir, "Film.mkv").length())
        assertTrue(File(r.internalDir, TrashApi.BIN).listFiles()!!.isEmpty())
    }

    @Test fun restoreNeverOverwritesAndTheNameSpaceIsSharedAcrossVolumes() {
        r.put("internal", "Film.mkv", 1000)
        val id = JsonLite.obj(r.call("POST", "/api/trash/put?name=Film.mkv").second)["id"] as String
        r.put("usb-1234".let { "usb" }, "Film.mkv", 2000)                       // same name now exists on the USB key
        val b = r.call("POST", "/api/trash/restore?id=$id").second
        assertEquals("Film (restauré).mkv", JsonLite.obj(b)["name"])
        assertEquals(2000L, File(r.usbDir, "Film.mkv").length(), "the file on the key is untouched")
        assertEquals(1000L, File(r.internalDir, "Film (restauré).mkv").length())
    }

    @Test fun filesBeingPlayedCannotBeTrashed() {
        r.put("internal", "Film.mkv", 1000)
        r.player.st = PlayerState("playing", "Film.mkv", 0, 1000)
        assertEquals(409, r.call("POST", "/api/trash/put?name=Film.mkv").first)
        assertTrue(File(r.internalDir, "Film.mkv").exists())
    }

    @Test fun badNamesAndUnknownIdsAreRefusedAndNothingLeavesTheLibrary() {
        r.put("internal", "Film.mkv", 1000)
        assertEquals(400, r.call("POST", "/api/trash/put?name=${TvClient.enc("../x")}").first)
        assertEquals(400, r.call("POST", "/api/trash/put?name=${TvClient.enc(".castbridge-trash")}").first)
        assertEquals(400, r.call("POST", "/api/trash/put").first)
        assertEquals(404, r.call("POST", "/api/trash/put?name=Nope.mkv").first)
        assertEquals(400, r.call("POST", "/api/trash/restore?id=${TvClient.enc("../../etc")}").first)
        assertEquals(400, r.call("POST", "/api/trash/purge?id=1").first)
        assertEquals(404, r.call("POST", "/api/trash/restore?id=1700000000000-abcd").first)
        assertEquals(405, r.call("GET", "/api/trash/put?name=Film.mkv").first)
        assertTrue(File(r.internalDir, "Film.mkv").exists())
    }

    @Test fun theBinIsBehindThePin() {
        r.put("internal", "Film.mkv", 1000)
        assertEquals(401, r.call("POST", "/api/trash/put?name=Film.mkv", pin = null).first)
        assertEquals(401, r.call("GET", "/api/trash", pin = "000000").first)
        assertTrue(File(r.internalDir, "Film.mkv").exists())
    }

    @Test fun purgeAndEmptyAreExplicitAndReallyDelete() {
        r.put("internal", "A.mkv", 1000); r.put("internal", "B.mkv", 1000)
        val a = JsonLite.obj(r.call("POST", "/api/trash/put?name=A.mkv").second)["id"] as String
        r.call("POST", "/api/trash/put?name=B.mkv")
        assertEquals(200, r.call("POST", "/api/trash/purge?id=$a").first)
        assertEquals(1, File(r.internalDir, TrashApi.BIN).listFiles()!!.size)
        assertEquals(1L, JsonLite.obj(r.call("POST", "/api/trash/empty").second)["purged"])
        assertTrue(File(r.internalDir, TrashApi.BIN).listFiles()!!.isEmpty())
    }

    @Test fun itemsExpireAfterThirtyDays() {
        var t = 1_700_000_000_000L
        val rig = AgentRig(now = { t })
        try {
            rig.put("internal", "Old.mkv", 1000); rig.put("internal", "New.mkv", 1000)
            rig.call("POST", "/api/trash/put?name=Old.mkv")
            t += 29L * 86_400_000
            rig.call("POST", "/api/trash/put?name=New.mkv")
            assertEquals(2L, JsonLite.obj(rig.call("GET", "/api/trash").second)["count"])
            t += 2L * 86_400_000                                                       // Old is 31 days old, New 2 days
            val l = JsonLite.obj(rig.call("GET", "/api/trash").second)
            assertEquals(1L, l["count"])
            assertTrue(l.toString().contains("New.mkv") && !l.toString().contains("Old.mkv"))
        } finally { rig.close() }
    }

    // ------------------------------------------------------------------ the agent against a real TV

    private fun ctx() = AgentContext(zone = java.time.ZoneId.of("UTC"))

    @Test fun readsTheWholeLibraryUsbKeyIncludedWithoutTheContent() {
        r.put("internal", "Prison.Break.S01E04.mkv", 40_000); r.put("usb", "Inception.2010.1080p.mkv", 50_000)
        val s = TvSnapshot.read(r.tv)
        assertEquals(setOf("internal", "usb-1234"), s.files.map { it.volumeId }.toSet())
        assertEquals(setOf("internal", "usb-1234"), s.volumes.map { it.id }.toSet())
        assertEquals("usb", s.volumes.first { it.id == "usb-1234" }.kind)
        assertEquals("exFAT", s.volumes.first { it.id == "usb-1234" }.fs)
    }

    @Test fun planThenExecuteRenamesOnTheRealTvAndUndoRestores() {
        r.put("internal", "Prison.Break.S01E04.FRENCH.DVDRip.avi", 40_000); r.put("usb", "Inception.2010.1080p.BluRay.mkv", 50_000)
        val agent = LibraryAgent(ctx(), fingerprinter = TvFingerprinter(r.tv))
        val a = agent.analyze(TvSnapshot.read(r.tv))
        assertEquals(2, a.plan.renames.size)
        val j = MemoryJournal()
        val ex = Executor(TvLibraryOps(r.tv), j, ctx())
        val run = ex.run(a.plan, a.plan.defaultSelection(), confirmDeletions = false)
        assertEquals(2, run.done, run.reports.toString())
        assertEquals(listOf("Inception (2010).mkv", "Prison Break – S01E04.avi"), r.listing())
        val u = ex.undo()
        assertEquals(2, u.restored, u.reports.toString())
        assertEquals(listOf("Inception.2010.1080p.BluRay.mkv", "Prison.Break.S01E04.FRENCH.DVDRip.avi"), r.listing())
    }

    @Test fun duplicatesAreFoundByAPartialFingerprintOverTheLanAndTrashedOnlyAfterConfirmation() {
        val data = Random(5).nextBytes(700_000)
        File(r.internalDir, "Film A.mkv").writeBytes(data)
        File(r.usbDir, "Film A (1).mkv").writeBytes(data)
        File(r.internalDir, "Other.mkv").writeBytes(Random(6).nextBytes(700_000))        // same size, other content
        val a = LibraryAgent(ctx(), fingerprinter = TvFingerprinter(r.tv, window = 4096)).analyze(TvSnapshot.read(r.tv))
        val t = a.plan.trash.single()
        assertEquals(DupKind.EXACT.let { TrashWhy.DUPLICATE }, t.why)
        assertEquals("Film A.mkv", t.keep!!.name, "the copy without the 'copy' mark is kept")
        val ex = Executor(TvLibraryOps(r.tv), MemoryJournal(), ctx())
        val no = ex.run(a.plan, setOf(t.id), confirmDeletions = false)
        assertEquals(State.SKIPPED, no.reports.single().state)
        assertEquals(3, r.listing().size)
        val yes = ex.run(a.plan, setOf(t.id), confirmDeletions = true)
        assertEquals(State.DONE, yes.reports.single().state, yes.reports.toString())
        assertEquals(listOf("Film A.mkv", "Other.mkv"), r.listing())
        assertEquals(1, File(r.usbDir, TrashApi.BIN).listFiles()!!.size, "recoverable, not deleted")
        assertEquals(1, ex.undo().restored)
        assertEquals(3, r.listing().size)
    }

    @Test fun aTvWithoutTheBinRefusesInsteadOfDeleting() {
        val old = AgentRig(withTrash = false)
        try {
            old.put("internal", "Film.mkv", 1000); old.put("internal", "Film (1).mkv", 1000, seed = "Film.mkv".hashCode())
            val ref = TvSnapshot.read(old.tv).files
            val change = Change("t", ChangeType.TRASH, ref.first { it.name == "Film (1).mkv" }, Kind.MOVIE, why = TrashWhy.DUPLICATE, keep = ref.first { it.name == "Film.mkv" }, reason = "x", confidence = 1.0)
            val res = Executor(TvLibraryOps(old.tv), MemoryJournal(), ctx()).run(Plan(listOf(change)), setOf("t"), true)
            assertEquals(State.FAILED, res.reports.single().state)
            assertTrue(res.reports.single().note!!.contains("ne gère pas encore la corbeille"), res.reports.single().note)
            assertEquals(2, old.listing().size, "nothing was deleted: the agent never falls back to /api/delete")
        } finally { old.close() }
    }

    @Test fun aFilePlayingOnTheTvIsNeverTouched() {
        r.put("internal", "Prison.Break.S01E04.mkv", 1000)
        r.player.st = PlayerState("playing", "Prison.Break.S01E04.mkv", 0, 1000)
        val a = LibraryAgent(ctx()).analyze(TvSnapshot.read(r.tv))
        assertTrue(a.plan.changes.isEmpty())
        assertEquals("en cours de lecture", a.plan.skipped.single().reason)
        // even a stale plan made before playback started is refused at execution time
        r.player.st = PlayerState()
        val plan = LibraryAgent(ctx()).analyze(TvSnapshot.read(r.tv)).plan
        r.player.st = PlayerState("playing", "Prison.Break.S01E04.mkv", 0, 1000)
        val res = Executor(TvLibraryOps(r.tv, now = { System.nanoTime() / 1_000_000 + 10_000_000 }), MemoryJournal(), ctx()).run(plan, plan.allSafe(), false)
        assertEquals(State.SKIPPED, res.reports.single().state)
        assertTrue(File(r.internalDir, "Prison.Break.S01E04.mkv").exists())
    }

    @Test fun aMoveToTheUsbKeyKeepsOneGigabyteFreeAndWorksOverTheRealRoute() {
        r.put("internal", "Big.mkv", 2_000_000)
        r.capacity["internal"] = 3_500_000L; r.capacity["usb-1234"] = (1L shl 30) + 5_000_000
        r.registry.refresh()
        val a = LibraryAgent(ctx().copy(lowSpaceBytes = 2_000_000_000L)).analyze(TvSnapshot.read(r.tv))
        val mv = a.plan.moves.single()
        assertEquals("usb-1234", mv.toVolume)
        val ex = Executor(TvLibraryOps(r.tv, sleep = { Thread.sleep(20) }), MemoryJournal(), ctx())
        val res = ex.run(a.plan, setOf(mv.id), false)
        assertEquals(State.DONE, res.reports.single().state, res.reports.toString())
        assertTrue(File(r.usbDir, "Big.mkv").isFile && !File(r.internalDir, "Big.mkv").exists())
    }
}
