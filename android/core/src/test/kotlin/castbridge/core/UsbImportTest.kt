package castbridge.core

import castbridge.core.tv.*
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.random.Random
import kotlin.test.*

class UsbImportTest {
    private val usb = kotlin.io.path.createTempDirectory("usb").toFile()
    private val dest = kotlin.io.path.createTempDirectory("dst").toFile()
    @AfterTest fun tearDown() { usb.deleteRecursively(); dest.deleteRecursively() }

    private fun write(rel: String, size: Int): ByteArray = Random(rel.hashCode()).nextBytes(size).also {
        File(usb, rel).apply { parentFile.mkdirs(); writeBytes(it) }
    }

    @Test fun scanFindsVideosRecursivelyAndSkipsHiddenAndOthers() {
        write("a.MP4", 10); write("films/b.mkv", 20); write("films/notes.txt", 5); write(".Trash/c.mp4", 5); write("films/deep/d.avi", 7)
        val names = UsbImport.scan(listOf(usb)).map { it.name }.sorted()
        assertEquals(listOf("a.MP4", "b.mkv", "d.avi"), names)
    }

    @Test fun copiesWithProgressAndRenamesPart() {
        val a = write("a.mp4", 700_000); val b = write("sub/b.mkv", 300_000)
        val prog = mutableListOf<ImportProgress>()
        val r = UsbImport.copyAll(UsbImport.scan(listOf(usb)), dest, 0, onProgress = { prog += it })
        assertEquals(2, r.copied); assertTrue(r.failed.isEmpty())
        assertContentEquals(a, File(dest, "a.mp4").readBytes()); assertContentEquals(b, File(dest, "b.mkv").readBytes())
        assertTrue(dest.listFiles()!!.none { it.name.endsWith(".part") })
        assertEquals(1_000_000, prog.last().bytesTotal); assertEquals(1_000_000, prog.last().bytesDone)
        assertEquals(2, prog.last().count)
    }

    @Test fun secondRunSkipsExistingAndKeepsBothWhenContentDiffers() {
        write("a.mp4", 1000)
        UsbImport.copyAll(UsbImport.scan(listOf(usb)), dest, 0)
        val again = UsbImport.copyAll(UsbImport.scan(listOf(usb)), dest, 0)
        assertEquals(0, again.copied); assertEquals(1, again.skipped)
        File(dest, "a.mp4").writeBytes(ByteArray(5))            // same name, other size
        val r = UsbImport.copyAll(UsbImport.scan(listOf(usb)), dest, 0)
        assertEquals(1, r.copied)
        assertTrue(File(dest, "a (1).mp4").isFile)
    }

    @Test fun resumesPartAndReportsInsufficientSpace() {
        val data = write("big.mp4", 500_000)
        File(dest, "big.mp4.part").writeBytes(data.copyOf(200_000))
        val r = UsbImport.copyAll(UsbImport.scan(listOf(usb)), dest, 0)
        assertEquals(1, r.copied); assertContentEquals(data, File(dest, "big.mp4").readBytes())
        val full = UsbImport.copyAll(listOf(ImportEntry("x.mp4", 10) { ByteArrayInputStream(ByteArray(10)) }), dest, Long.MAX_VALUE / 2)
        assertEquals(1, full.failed.size); assertFalse(File(dest, "x.mp4").exists())
    }

    @Test fun truncatedSourceAndCancellation() {
        val bad = UsbImport.copyAll(listOf(ImportEntry("t.mp4", 100) { ByteArrayInputStream(ByteArray(40)) }), dest, 0)
        assertEquals(1, bad.failed.size)
        val c = UsbImport.copyAll(listOf(ImportEntry("c.mp4", 10) { ByteArrayInputStream(ByteArray(10)) }), dest, 0, cancelled = { true })
        assertTrue(c.cancelled); assertEquals(0, c.copied)
    }

    @Test fun sanitizesForeignNames() {
        assertEquals("a_b.mp4", UsbImport.sanitize("a/b.mp4"))
        assertNull(UsbImport.sanitize(".."))
        assertNull(UsbImport.sanitize("x.part"))
        assertTrue(UsbImport.isVideo("FILM.MKV")); assertFalse(UsbImport.isVideo("doc.pdf"))
    }
}
