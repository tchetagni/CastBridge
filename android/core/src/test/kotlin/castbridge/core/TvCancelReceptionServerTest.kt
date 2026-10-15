package castbridge.core

import castbridge.core.tv.*
import castbridge.core.xfer.*
import castbridge.core.xfer.TransferProgress.Phase
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.channels.FileChannel
import java.nio.channels.WritableByteChannel
import java.nio.file.StandardOpenOption
import kotlin.random.Random
import kotlin.test.*

/**
 * R-51 (2026-10-14, « Une copie interrompue sur la TV n'est pas annulable »), docs/TRANSFER.md « Annulation côté TV ».
 *
 * Before: `receiving()` listed EVERY `<nom>.part` with a `.meta`, written a moment ago or dead for hours (it left only after 24 h, 7 days on a key): the home chip said
 * « Réception de X : 42 % » all that time, « Générer un nouveau PIN » was refused (`TvService.transferInProgress`), and only the phone could cancel. Now: `receiving()` = the
 * LIVE copies, `stalledCopies()` = the others, `cancelReception` removes a copy from the TV and refuses its name for [ReceiverServer.TV_CANCEL_REFUSE_MS] so that the phone, which
 * resumes by itself, does not bring it back; the phone reads a 400 / 409 `TV_CANCELLED` with a French `message`.
 */
class TvCancelReceptionServerTest {
    private var t = 1_000_000L            // the clock of the reception progress (staleness of a silent copy)
    private var wall = 5_000_000L         // the clock of the refusal window
    private val rigs = ArrayList<Rig>()
    private fun rig(fs: Fs = Fs.EXFAT, usb: Boolean = false, pin: String? = null): Rig =
        Rig(pin = pin, fs = fs, progress = TransferProgress(now = { t })).also {
            if (!usb) it.unplug()
            it.server.cancelClock = { wall }; it.server.cancelWaitMs = 150
            rigs += it
        }
    private val work = kotlin.io.path.createTempDirectory("src").toFile()
    @AfterTest fun tearDown() { rigs.forEach { it.close() }; work.deleteRecursively() }

    private val data = Random(51).nextBytes(400_000)
    private val total = data.size.toLong()
    private val half get() = data.copyOf(200_000)
    private val rest get() = data.copyOfRange(200_000, data.size)
    private fun part(r: Rig, name: String = "clip.mp4", dir: File = r.internalDir) = File(dir, "$name.part")
    private fun meta(r: Rig, name: String = "clip.mp4", dir: File = r.internalDir) = File(dir, "$name.meta")
    private fun events(r: Rig) = java.util.Collections.synchronizedList(ArrayList<TransferProgress.Item>()).also { l -> r.server.progress.addListener { l += it } }

    /** Nobody wrote for 2 minutes and the progress sweep ended the item after its 90 s of silence: the copy is dead, as the owner found it hours later. */
    private fun killTheWriter(r: Rig, f: File) {
        f.setLastModified(System.currentTimeMillis() - 120_000)
        t += 91_000; r.server.progress.sweep()          // « plus de nouvelles du téléphone »
        t += 9_000; r.server.progress.sweep()           // and its 8 s end line has left the screen
    }

    private val refusal = """{"error":"cancelled on tv","code":"TV_CANCELLED","message":"Copie annulée sur la TV : renvoyez-la dans quelques minutes si c'était une erreur."}"""

    @Test fun r51_aCopyIsLiveWhileItsItemRunsOrItsFileWasWrittenInTheLastMinute() {
        val r = rig()
        assertEquals(200, r.put("clip.mp4", 0, total, half).first)
        val f = part(r)
        assertEquals(listOf(PartialCopy("clip.mp4", 200_000, total, true)), r.server.partials(), "the item runs and the file is fresh")
        f.setLastModified(System.currentTimeMillis() - 120_000)
        assertTrue(r.server.partials().single().live, "file old but the item still runs: the phone may resume within 90 s")
        t += 91_000; r.server.progress.sweep()
        assertFalse(r.server.partials().single().live, "item ended and file old: stalled")
        f.setLastModified(System.currentTimeMillis() - 5_000)
        assertTrue(r.server.partials().single().live, "item gone but the file was written 5 s ago: a writer may still be there (PART_BUSY_MS)")
        f.setLastModified(System.currentTimeMillis() - ReceiverServer.PART_BUSY_MS - 1_000)
        assertFalse(r.server.partials().single().live)
    }

    @Test fun r51_aDeadPartialIsStalledNotReceivingAndNoLongerBlocksWhatReadsReceiving() {
        val r = rig()
        assertEquals(200, r.put("clip.mp4", 0, total, half).first)
        // a moment after the connection broke it is still a live copy: the phone resumes
        assertEquals(listOf(Triple("clip.mp4", 200_000L, total)), r.server.receiving())
        assertTrue(r.server.stalledCopies().isEmpty())
        // hours later (aged here): the same file is stalled
        killTheWriter(r, part(r))
        val dead = PartialCopy("clip.mp4", 200_000, total, false)
        assertEquals(listOf(dead), r.server.partials())
        assertTrue(r.server.receiving().isEmpty(), "pre-fix: receiving() still listed it, for 24 h, and « Générer un nouveau PIN » was refused")
        assertEquals(listOf(dead), r.server.stalledCopies())
        // what the TV home reads: the card says it is interrupted and how to cancel it
        val cards = ReceiveCards.of(r.server.progress.shown(), r.server.receiving(), true, r.server.stalledCopies().map { Triple(it.name, it.received, it.total) })
        assertEquals("⏸ Copie interrompue : clip (50 %) · MENU > Copie : annuler", ReceiveCards.headline(cards))
        assertTrue(r.server.progress.active().isEmpty())
    }

    @Test fun r51_cancelRemovesTheStalledPartialOnEveryVolume() {
        val r = rig(usb = true)
        assertEquals(200, r.put("clip.mp4", 0, total, half).first)
        assertTrue(part(r, dir = r.usbDir).isFile, "auto target: the drive")
        part(r).writeBytes(ByteArray(10)); Meta.write(r.internalDir, "clip.mp4", total)           // a second partial copy, as a returned drive can bring
        killTheWriter(r, part(r, dir = r.usbDir)); part(r).setLastModified(System.currentTimeMillis() - 120_000); r.server.changed()
        assertEquals(2, r.server.stalledCopies().size)
        assertTrue(r.server.cancelReception("clip.mp4"))
        for (d in listOf(r.usbDir, r.internalDir)) assertFalse(part(r, dir = d).exists() || meta(r, dir = d).exists(), "$d")
        assertTrue(r.server.partials().isEmpty()); assertTrue(r.server.stalledCopies().isEmpty())
        assertFalse(r.tv.info().contains("clip.mp4"), r.tv.info())
        assertFalse(r.server.cancelReception("clip.mp4"), "the second time there is nothing left to cancel")
    }

    @Test fun r51_cancellingALiveCopyEndsItsItemWithTheReason() {
        val r = rig(); val ev = events(r)
        assertEquals(200, r.put("clip.mp4", 0, total, half).first)
        assertEquals(Phase.RUNNING, r.server.progress.active().single().phase)
        assertTrue(r.server.cancelReception("clip.mp4"))
        assertEquals(Phase.ABORTED, ev.last().phase); assertEquals("annulée sur la TV", ev.last().message)
        assertEquals("Réception interrompue : annulée sur la TV", ev.last().endLine())
        assertTrue(r.server.progress.active().isEmpty())
        assertFalse(part(r).exists() || meta(r).exists()); assertTrue(r.server.partials().isEmpty())
        // another reason is carried as is
        assertEquals(200, r.put("other.mp4", 0, total, half).first)
        assertTrue(r.server.cancelReception("other.mp4", "annulée par le propriétaire")); assertEquals("annulée par le propriétaire", ev.last().message)
    }

    @Test fun r51_theSameNameIsRefusedFor10MinutesWith400ThenAccepted() {
        val r = rig(); val ev = events(r)
        assertEquals(200, r.put("clip.mp4", 0, total, half).first)
        assertTrue(r.server.cancelReception("clip.mp4"))
        val before = ev.size
        // the phone resumes at the old offset, then from scratch, then with another case of the name
        for ((name, off, bytes) in listOf(Triple("clip.mp4", 200_000L, rest), Triple("clip.mp4", 0L, data), Triple("CLIP.MP4", 200_000L, rest))) {
            val (code, body) = r.put(name, off, total, bytes)
            assertEquals(400, code, "$name@$off: $body"); assertEquals(refusal, body)
        }
        assertFalse(part(r).exists() || meta(r).exists(), "nothing was written")
        assertEquals(before, ev.size, "no reception began: the screen stays quiet")
        // « où en est la TV ? » says why, with the usual fields
        val j = r.call("GET", "/api/part?name=clip.mp4&size=$total").second
        assertEquals("TV_CANCELLED", TvClient.str(j, "code"), j); assertEquals(0L, TvClient.num(j, "length")); assertTrue(j.contains("\"done\":false"), j)
        assertEquals("TV_CANCELLED", TvClient.str(r.call("GET", "/api/part?name=clip.mp4").second, "code"))
        assertEquals("TV_CANCELLED", r.tv.part("clip.mp4").code); assertEquals("TV_CANCELLED", r.tv.part("clip.mp4", total).code)
        // the phone's final failure shows the TV's words
        val e = assertFailsWith<TvClient.HttpError> { r.tv.upload("clip.mp4", 0, total, java.io.ByteArrayInputStream(data)) {} }
        assertEquals(400, e.code); assertEquals(ReceiverServer.TV_CANCELLED_MESSAGE, TvClient.str(e.message.orEmpty().substringAfter(": "), "message"))
        // another name is untouched
        assertEquals(200, r.put("other.mp4", 0, total, half).first); assertNull(r.tv.part("other.mp4").code)
        // the window lasts TV_CANCEL_REFUSE_MS, to the millisecond
        wall += ReceiverServer.TV_CANCEL_REFUSE_MS - 1; assertEquals(400, r.put("clip.mp4", 0, total, half).first)
        wall += 1
        assertNull(r.tv.part("clip.mp4").code)
        assertEquals(200, r.put("clip.mp4", 0, total, half).first)
        assertEquals(200_000L, r.tv.part("clip.mp4").length)
        assertEquals(200, r.put("clip.mp4", 200_000, total, rest).first); assertTrue(File(r.internalDir, "clip.mp4").isFile, "the copy sent again after the window completes")
    }

    @Test fun r51_aRefusedBigUploadStillReadsItsAnswerInsteadOfAResetConnection() {
        val r = rig()
        assertEquals(200, r.put("clip.mp4", 0, total, half).first)
        assertTrue(r.server.cancelReception("clip.mp4"))
        val big = ByteArray(6 shl 20)          // far more than a socket buffer: answered without being read, the phone would see a reset instead of the status (R-17)
        val (code, body) = r.put("clip.mp4", 0, big.size.toLong(), big)
        assertEquals(400, code, body); assertEquals(refusal, body)
    }

    @Test fun r51_aMultiConnectionBeginOfThatNameIsRefusedWith409ThenAccepted() {
        val r = rig()
        val bs = Manifest.SLICE; val size = 2L * bs
        val q = "name=${TvClient.enc("multi.mkv")}&size=$size&blockSize=$bs"
        assertEquals(200, r.call("POST", "/api/transfer/begin?$q").first)
        val id = Manifest("multi.mkv", size, bs).id
        assertEquals(200, r.call("GET", "/api/transfer/state?id=$id").first)
        assertTrue(File(r.internalDir, ".cbx/$id.data").isFile)
        assertEquals(Phase.RUNNING, r.server.progress.active().single().phase)
        assertTrue(r.server.cancelReception("multi.mkv"))
        assertEquals(404, r.call("GET", "/api/transfer/state?id=$id").first, "the session is gone")
        assertFalse(File(r.internalDir, ".cbx/$id.data").exists() || File(r.internalDir, ".cbx/$id.state").exists(), "and its bytes")
        val end = r.server.progress.shown().single()
        assertEquals(Phase.ABORTED, end.phase); assertEquals("annulée sur la TV", end.message); assertEquals("x:$id", end.id)
        val (code, body) = r.call("POST", "/api/transfer/begin?$q")
        assertEquals(409, code, body); assertEquals(refusal, body)
        assertEquals(409, r.call("POST", "/api/transfer/begin?name=${TvClient.enc("MULTI.mkv")}&size=$size&blockSize=$bs").first, "any case")
        assertEquals(200, r.call("POST", "/api/transfer/begin?name=other.mkv&size=$size&blockSize=$bs").first, "another name")
        wall += ReceiverServer.TV_CANCEL_REFUSE_MS
        assertEquals(200, r.call("POST", "/api/transfer/begin?$q").first)
        assertEquals(Phase.RUNNING, r.server.progress.active().first { it.id == "x:$id" }.phase, "the copy sent again after the window is received")
    }

    /** A multi-connection copy whose last check is reading the file back is complete: it ends in seconds as a received file, and its read-back holds the name's lock (a cancel must never wait for it). */
    @Test fun r51_aCopyAtItsLastCheckIsNeverCancelledNorRefused() {
        val r = rig()
        val bs = Manifest.SLICE; val size = 2L * bs
        val q = "name=${TvClient.enc("last.mkv")}&size=$size&blockSize=$bs"
        assertEquals(200, r.call("POST", "/api/transfer/begin?$q").first)
        val id = Manifest("last.mkv", size, bs).id
        r.server.markFinishingForTest(id, true)
        assertFalse(r.server.cancelReception("last.mkv"), "complete and reading itself back: nothing to cancel")
        assertEquals(200, r.call("GET", "/api/transfer/state?id=$id").first, "its session is still there")
        assertEquals(Phase.RUNNING, r.server.progress.active().single().phase, "and its item still runs: the screen does not say « annulée » for a file that arrives")
        r.server.markFinishingForTest(id, false)
        assertEquals(200, r.call("POST", "/api/transfer/begin?$q").first, "no refusal was left behind")
    }

    /** The bench's « network alone » run (`discard=1`: nothing is stored) is not a copy. */
    @Test fun r51_theBenchRunIsNeverRefused() {
        val r = rig()
        val bs = Manifest.SLICE; val size = 2L * bs
        assertEquals(200, r.put("bench.bin", 0, total, half).first)
        assertTrue(r.server.cancelReception("bench.bin"))
        assertEquals(409, r.call("POST", "/api/transfer/begin?name=bench.bin&size=$size&blockSize=$bs").first)
        assertEquals(200, r.call("POST", "/api/transfer/begin?name=bench.bin&size=$size&blockSize=$bs&discard=1").first)
    }

    @Test fun r51_theRouteCancelsAnswersLikeResetAnd404sWhenThereIsNothing() {
        val r = rig()
        assertEquals(200, r.put("clip.mp4", 0, total, half).first)
        val (code, body) = r.call("POST", "/api/reception/cancel?name=clip.mp4")
        assertEquals(200, code, body); assertEquals(0L, TvClient.num(body, "length")); assertTrue(body.contains("\"done\":false"), body)
        assertFalse(part(r).exists() || meta(r).exists())
        val (c2, b2) = r.call("POST", "/api/reception/cancel?name=clip.mp4")
        assertEquals(404, c2, b2); assertEquals("""{"error":"not found"}""", b2)
        assertEquals(400, r.call("POST", "/api/reception/cancel").first); assertEquals(400, r.call("POST", "/api/reception/cancel?name=${TvClient.enc("../x")}").first)
        assertEquals(405, r.call("GET", "/api/reception/cancel?name=clip.mp4").first)
        assertEquals(400, r.put("clip.mp4", 200_000, total, rest).first, "the route sets the window too")
    }

    @Test fun r51_theRouteIsBehindThePinLikeReset() {
        val r = rig(pin = "123456")
        part(r).writeBytes(ByteArray(1000)); Meta.write(r.internalDir, "clip.mp4", 5000); r.server.changed()
        val c = URL("${r.base}/api/reception/cancel?name=clip.mp4").openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true; c.setFixedLengthStreamingMode(0); c.outputStream.close()
        assertEquals(401, c.responseCode)
        assertTrue(part(r).exists() && meta(r).exists(), "refused: nothing removed")
        assertEquals(200, r.call("POST", "/api/reception/cancel?name=clip.mp4").first)
        assertFalse(part(r).exists() || meta(r).exists())
    }

    @Test fun r51_aLiveUploadIsStoppedAtItsNextReadAndTheNextBytesAreAnswered400() {
        val r = rig(); val ev = events(r)
        val c = URL("${r.base}/upload/live.mp4?offset=0&total=$total").openConnection() as HttpURLConnection
        c.requestMethod = "PUT"; c.doOutput = true; c.setFixedLengthStreamingMode(total); c.readTimeout = 15_000
        val out = c.outputStream
        out.write(data, 0, 100_000); out.flush()
        val end = System.currentTimeMillis() + 5_000
        // registered as a writer AND begun on the screen (the two follow each other within microseconds, the test waits for both)
        while (!(r.server.writesOn("internal").isNotEmpty() && r.server.progress.isRunning("put:live.mp4")) && System.currentTimeMillis() < end) Thread.sleep(10)
        assertEquals(1, r.server.writesOn("internal").size, "the upload is registered and waiting for more bytes")
        assertTrue(r.server.cancelReception("live.mp4"), "the flag is set; the upload is blocked reading, so the grace ends and the call returns")
        // the phone keeps sending: the TV notices at its next read, stops, deletes, answers
        runCatching { out.write(data, 100_000, data.size - 100_000); out.flush() }
        assertEquals(400, c.responseCode)
        assertEquals(refusal, c.errorStream.readBytes().decodeToString())
        assertFalse(part(r, "live.mp4").exists() || meta(r, "live.mp4").exists(), "no byte stays on the TV")
        assertTrue(r.server.partials().isEmpty()); assertTrue(r.server.writesOn("internal").isEmpty())
        assertEquals(Phase.ABORTED, ev.last { it.id == "put:live.mp4" }.phase)
        assertEquals(400, r.put("live.mp4", 0, total, half).first)
    }

    @Test fun r51_aNameTheDriveHadToChangeIsCancelledAndRefusedUnderBothSpellings() {
        val r = rig(fs = Fs.FAT32, usb = true)
        val raw = "Spider-Man: No Way Home.mkv"
        assertEquals(200, r.put(raw, 0, total, half).first)
        val stored = r.server.partials().single().name
        assertNotEquals(raw, stored, "FAT cannot store a colon"); assertTrue(File(r.usbDir, "$stored.part").isFile)
        // the MENU line holds the stored name (that is what the listing shows)
        assertTrue(r.server.cancelReception(stored))
        assertFalse(File(r.usbDir, "$stored.part").exists() || File(r.usbDir, "$stored.meta").exists())
        // the phone asks under the name it used: refused; under the stored name too
        assertEquals(400, r.put(raw, 200_000, total, rest).first)
        assertEquals(400, r.put(stored, 200_000, total, rest).first)
        assertEquals("TV_CANCELLED", r.tv.part(raw).code)
        // and the other way round: cancelled by the name the phone used
        wall += ReceiverServer.TV_CANCEL_REFUSE_MS
        assertEquals(200, r.put(raw, 0, total, half).first)
        assertTrue(r.server.cancelReception(raw)); assertFalse(File(r.usbDir, "$stored.part").exists())
        assertEquals(400, r.put(stored, 0, total, half).first)
    }

    // ---- the phone's real clients (1.2.56): they stop on the TV's words instead of waiting and retrying for ever ----

    @Test fun r51_theSingleStreamPhoneStopsOnTheTvWordsInsteadOfWaitingAndRetrying() {
        val r = rig()
        assertEquals(200, r.put("clip.mp4", 0, total, half).first)
        assertTrue(r.server.cancelReception("clip.mp4"))
        val seen = ArrayList<ResumableUpload.State>()
        val up = ResumableUpload("clip.mp4", total, resolve = { r.base }, openAt = { off -> ByteArrayInputStream(data, off.toInt(), data.size - off.toInt()) }, sleep = { })
        val end = up.run { seen += it }
        assertEquals(ReceiverServer.TV_CANCELLED_MESSAGE, (end as? ResumableUpload.State.Failed)?.reason, "a final failure with the TV's sentence: $end")
        assertTrue(seen.none { it is ResumableUpload.State.Waiting }, "no « reprise automatique » loop: $seen")
        assertFalse(part(r).exists(), "and nothing was written again")
    }

    private fun sixMiB(name: String) = File(work, name).apply { writeBytes(Random(6).nextBytes(6 shl 20)) }
    private fun lanes(r: Rig) = { id: String, max: Int -> listOf<Lane>(WifiLane("wifi", "127.0.0.1:${r.port}", id, HttpConn.tcp("127.0.0.1", r.port), { null }, maxStreams = max, fixedK = 1)) }

    @Test fun r51_theMultiConnectionPhoneEndsWithTheTvWordsWhetherItWasCancelledBeforeOrDuringTheCopy() {
        val r = rig()
        // before: the name was cancelled a moment ago (a classic partial of it), the phone begins and is refused
        assertEquals(200, r.put("film.mkv", 0, total, half).first)
        assertTrue(r.server.cancelReception("film.mkv"))
        val f = sixMiB("film.mkv")
        FileChannel.open(f.toPath(), StandardOpenOption.READ).use { ch ->
            val res = TransferClient(HttpTransferApi(r.base) { null }, FileBlockSource(ch), "film.mkv", lanes(r), retryDelayMs = 20).run()
            assertEquals(ReceiverServer.TV_CANCELLED_MESSAGE, (res as? TransferClient.Result.Failed)?.reason, "begin refused, final: $res")
        }
        // during: the window is over, the copy runs, the owner cancels it on the TV after a few reads of the source
        wall += ReceiverServer.TV_CANCEL_REFUSE_MS
        val calls = java.util.concurrent.atomic.AtomicInteger()
        val cancelled = java.util.concurrent.atomic.AtomicBoolean()
        FileChannel.open(f.toPath(), StandardOpenOption.READ).use { ch ->
            val inner = FileBlockSource(ch)
            val hooked = object : BlockSource {
                override val size = inner.size
                private fun tick() { if (calls.incrementAndGet() == 10) cancelled.set(r.server.cancelReception("film.mkv")) }
                override fun read(pos: Long, buf: ByteArray, off: Int, len: Int): Int { tick(); return inner.read(pos, buf, off, len) }
                override fun transferTo(pos: Long, count: Long, out: WritableByteChannel): Long { tick(); Thread.sleep(10); return inner.transferTo(pos, count, out) }
            }
            val res = TransferClient(HttpTransferApi(r.base) { null }, hooked, "film.mkv", lanes(r), retryDelayMs = 20).run()
            assertTrue(calls.get() >= 10 && cancelled.get(), "the cancel came mid-copy (${calls.get()} reads, cancelled=${cancelled.get()})")
            assertEquals(ReceiverServer.TV_CANCELLED_MESSAGE, (res as? TransferClient.Result.Failed)?.reason, "the phone re-begins, is refused, and says why: $res")
        }
        assertFalse(File(r.internalDir, "film.mkv").exists(), "nothing was filed")
        assertTrue(r.server.progress.active().isEmpty())
    }

    @Test fun r51_cancellingWhatIsNotThereTouchesNothingAndRefusesNothing() {
        val r = rig()
        File(r.internalDir, "done.mp4").writeBytes(ByteArray(1000))
        assertFalse(r.server.cancelReception("done.mp4")); assertEquals(1000, File(r.internalDir, "done.mp4").length(), "a finished file is never a cancellable copy")
        assertFalse(r.server.cancelReception("nothing.mp4"))
        assertFalse(r.server.cancelReception("../etc/passwd"), "never a path"); assertFalse(r.server.cancelReception(""))
        assertEquals(200, r.put("nothing.mp4", 0, total, half).first, "a cancel that found nothing leaves no window behind")
        assertNull(r.tv.part("done.mp4").code)
    }
}
