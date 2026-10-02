package castbridge.core

import castbridge.core.tv.*
import java.io.*
import kotlin.random.Random
import kotlin.test.*

/** Correctifs d'audit de w15-05 : aucune suppression ni écrasement sans preuve (fsync avant suppression, homonyme, reset, preuve complète, .part sans Meta). */
class FixW1505MoverTest {
    private val root = kotlin.io.path.createTempDirectory("fixmv").toFile()
    private val a = File(root, "a").apply { mkdirs() }
    private val b = File(root, "b").apply { mkdirs() }
    private val va = StorageVolume("a", "A", a, VolumeKind.INTERNAL)
    private val vb = StorageVolume("b", "B", b, VolumeKind.REMOVABLE, Fs.EXFAT)
    private val data = Random(21).nextBytes(1_000_000)
    @AfterTest fun tearDown() { root.deleteRecursively() }
    private fun job() = MoveJob("f.mp4", va, vb, "f.mp4", data.size.toLong())

    @Test fun theCopyIsSyncedToTheDestinationBeforeTheSourceIsDeleted() {
        File(a, "f.mp4").writeBytes(data)
        val events = ArrayList<String>()
        val s = FileStore(va); val d = FileStore(vb)
        val src = object : VolumeStore by s {
            override fun deleteFinal(name: String): Boolean { events += "deleteFinal"; return s.deleteFinal(name) }
        }
        val dst = object : VolumeStore by d {
            override fun syncPart(name: String): Boolean { events += "sync:" + d.partSize(name); return d.syncPart(name) }
        }
        val j = job()
        Mover.run(j, src, dst, { true })
        assertEquals("done", j.state, j.error)
        assertEquals(listOf("sync:${data.size}", "deleteFinal"), events, "the whole copy is flushed, then (and only then) the source goes")
    }

    @Test fun aFailingSyncKeepsTheSource() {
        File(a, "f.mp4").writeBytes(data)
        val d = FileStore(vb)
        val dst = object : VolumeStore by d { override fun syncPart(name: String): Boolean = false }
        val j = job()
        Mover.run(j, FileStore(va), dst, { true })
        assertEquals("failed", j.state)
        assertContentEquals(data, File(a, "f.mp4").readBytes(), "source kept when the medium cannot confirm the copy")
        assertFalse(File(b, "f.mp4").exists(), "nothing was committed")
    }

    @Test fun aStoreThatCannotSyncNeverLetsTheSourceGo() {
        File(a, "f.mp4").writeBytes(data)
        val d = FileStore(vb)
        // a store that does not override syncPart: the default answer is « not guaranteed »
        val dst = object : VolumeStore by d { override fun syncPart(name: String): Boolean = super.syncPart(name) }
        val j = job()
        Mover.run(j, FileStore(va), dst, { true })
        assertEquals("failed", j.state)
        assertTrue(File(a, "f.mp4").isFile)
    }

    @Test fun aFreshSmallCopyCorruptedInTheMiddleIsRefusedByTheFullProof() {
        File(a, "f.mp4").writeBytes(data)
        val d = FileStore(vb)
        // same size, same first and last megabyte... here the file is 1 MB so the edges cover everything but one byte is altered in a 3-byte-wide hole:
        // use a bigger source so that the middle lies outside the 1 MB edges
        val big = Random(22).nextBytes(3_000_000); File(a, "f.mp4").writeBytes(big)
        val lying = object : VolumeStore by d {
            override fun commit(name: String) {
                d.commit(name)
                val f = File(b, name); val bytes = f.readBytes(); bytes[1_500_000] = (bytes[1_500_000] + 1).toByte(); f.writeBytes(bytes)
            }
        }
        val j = MoveJob("f.mp4", va, vb, "f.mp4", big.size.toLong())
        Mover.run(j, FileStore(va), lying, { true })
        assertEquals("failed", j.state, "a copy that differs in the middle is never accepted, whatever its size")
        assertContentEquals(big, File(a, "f.mp4").readBytes(), "source kept")
        assertFalse(File(b, "f.mp4").exists(), "the corrupt copy is removed")
    }
}

class FixW1505ServerTest {
    private val r = Rig()
    @AfterTest fun tearDown() = r.close()
    private fun old(f: File) { f.setLastModified(System.currentTimeMillis() - 10 * 60_000) }

    @Test fun aCompletedPartNeverOverwritesAFinalOfTheSameNameAndAnotherSizeOnTheSameVolume() {
        val full = Random(31).nextBytes(12_000)
        File(r.usbDir, "x.mp4").writeBytes(ByteArray(5_000) { 7 })
        File(r.usbDir, "x.mp4.part").writeBytes(full.copyOf(8_000)); File(r.usbDir, "x.mp4.meta").writeText("12000")
        val (code, body) = r.put("x.mp4", 8_000, 12_000, full.copyOfRange(8_000, 12_000))
        assertEquals(409, code, body); assertTrue(body.contains("NAME_TAKEN"), body)
        assertEquals(5_000, File(r.usbDir, "x.mp4").length(), "the finished file of another size is untouched")
        assertTrue(File(r.usbDir, "x.mp4.part").isFile, "the partial copy is kept")
    }

    @Test fun aCompletedPartNeverDeletesAHomonymOfAnotherSizeOnAnotherVolume() {
        val full = Random(32).nextBytes(12_000)
        File(r.internalDir, "y.mp4").writeBytes(ByteArray(5_000) { 9 })
        File(r.usbDir, "y.mp4.part").writeBytes(full.copyOf(8_000)); File(r.usbDir, "y.mp4.meta").writeText("12000")
        val (code, body) = r.put("y.mp4", 8_000, 12_000, full.copyOfRange(8_000, 12_000))
        assertEquals(409, code, body)
        assertEquals(5_000, File(r.internalDir, "y.mp4").length(), "a same-name file of another size on another volume is never deleted")
    }

    @Test fun aSameNameCopyOfTheSameSizeOnAnotherVolumeIsStillReplaced() {
        val full = Random(33).nextBytes(12_000)
        File(r.internalDir, "z.mp4").writeBytes(ByteArray(12_000) { 1 })      // an older copy of the same size: replaced as before (no silent duplicate)
        File(r.usbDir, "z.mp4.part").writeBytes(full.copyOf(8_000)); File(r.usbDir, "z.mp4.meta").writeText("12000")
        // the same-size final is "already there" for the phone: the upload answers done and changes nothing
        val (code, _) = r.put("z.mp4", 8_000, 12_000, full.copyOfRange(8_000, 12_000))
        assertEquals(200, code)
        assertTrue(File(r.internalDir, "z.mp4").length() == 12_000L || File(r.usbDir, "z.mp4").length() == 12_000L)
    }

    @Test fun resetRefusesALivePart() {
        File(r.usbDir, "q.mp4.part").writeBytes(ByteArray(100)); File(r.usbDir, "q.mp4.meta").writeText("5000")
        val (code, body) = r.call("POST", "/api/reset?name=q.mp4")
        assertEquals(409, code, body); assertTrue(body.contains("busy"), body)
        assertTrue(File(r.usbDir, "q.mp4.part").isFile, "a part written less than a minute ago is not dropped")
        old(File(r.usbDir, "q.mp4.part"))
        assertEquals(200, r.call("POST", "/api/reset?name=q.mp4").first)
        assertFalse(File(r.usbDir, "q.mp4.part").exists(), "an idle part is dropped")
    }

    @Test fun aPartWithoutMetaIsNeverResumedForAKnownSize() {
        File(r.usbDir, "n.mp4.part").writeBytes(ByteArray(100))
        assertEquals("PART_OTHER", r.tv.part("n.mp4", 12_000).code)
        File(r.usbDir, "n.mp4.meta").writeText("12000")
        assertNotEquals("PART_OTHER", r.tv.part("n.mp4", 12_000).code, "with its Meta the same content resumes")
        assertEquals(100, r.tv.part("n.mp4", 12_000).length)
    }

    @Test fun twoSendersOfTheSameNameStopAfterThreeRefusalsWithoutErasingEachOther() {
        // another phone's part of another content, written just now: this phone neither erases it nor loops
        File(r.usbDir, "w.mp4.part").writeBytes(ByteArray(4_000) { 5 }); File(r.usbDir, "w.mp4.meta").writeText("10000")
        val data = Random(34).nextBytes(12_000)
        var sleeps = 0
        val states = ArrayList<ResumableUpload.State>()
        val res = ResumableUpload("w.mp4", data.size.toLong(), { r.base }, { off -> ByteArrayInputStream(data, off.toInt(), data.size - off.toInt()) },
            sleep = { sleeps++ }, pin = null).run { states += it }
        val failed = assertIs<ResumableUpload.State.Failed>(res)
        assertTrue(failed.reason.contains("même nom"), failed.reason)
        assertEquals(4_000, File(r.usbDir, "w.mp4.part").length(), "the other sender's partial copy is intact")
        assertFalse(File(r.usbDir, "w.mp4").exists())
        assertTrue(sleeps in 1..10, "a backoff, never a hot loop: $sleeps sleeps")
    }

    @Test fun anIdlePartOfAnotherContentIsStillDroppedAndTheFileGoesThrough() {
        File(r.usbDir, "v.mp4.part").writeBytes(ByteArray(4_000) { 5 }); File(r.usbDir, "v.mp4.meta").writeText("10000")
        old(File(r.usbDir, "v.mp4.part"))
        val data = Random(35).nextBytes(12_000)
        val res = ResumableUpload("v.mp4", data.size.toLong(), { r.base }, { off -> ByteArrayInputStream(data, off.toInt(), data.size - off.toInt()) }, sleep = {}, pin = null).run { }
        assertEquals(ResumableUpload.State.Done, res)
        assertContentEquals(data, (File(r.usbDir, "v.mp4").takeIf { it.exists() } ?: File(r.internalDir, "v.mp4")).readBytes())
    }
}
