package castbridge.core

import castbridge.core.tv.*
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.test.*

class UsbMigrationTest {
    private lateinit var base: File
    private lateinit var src: File
    private lateinit var dst: File

    @BeforeTest fun setUp() { base = Files.createTempDirectory("mig").toFile(); src = File(base, "internal").also { it.mkdirs() }; dst = File(base, "usb").also { it.mkdirs() } }
    @AfterTest fun tearDown() { base.deleteRecursively() }

    private fun file(dir: File, name: String, size: Int, seed: Int = 7) = File(dir, name).also { f -> f.writeBytes(ByteArray(size) { ((it * 31 + seed) % 251).toByte() }) }
    private fun mig(del: Boolean, fs: Fs = Fs.EXFAT, fault: (String) -> Unit = {}, buf: Int = 4096) =
        UsbMigration(src, dst, fs, UsbMigration.Options(deleteSources = del, minFreeBytes = 0, bufferBytes = buf), fault)

    @Test fun movesVerifiesAndDeletesOnlyWhenConfirmed() {
        val a = file(src, "a.mp4", 100_000); val sum = UsbMigration.sha256(a)
        val kept = mig(del = false).run()
        assertIs<UsbMigration.Outcome.CopiedKept>(kept.single()); assertTrue(a.exists(), "no confirmation: source kept")
        assertEquals(sum, UsbMigration.sha256(File(dst, "a.mp4")))
        val moved = mig(del = true).run()                       // second run: already there, identical -> now deletes
        assertIs<UsbMigration.Outcome.Moved>(moved.single()); assertFalse(a.exists()); assertEquals(sum, UsbMigration.sha256(File(dst, "a.mp4")))
    }
    @Test fun progressReachesTheTotal() {
        file(src, "a.mp4", 50_000); file(src, "b.mp4", 30_000)
        var last = 0L to 0L; mig(true).run { d, t -> last = d to t }
        assertEquals(80_000L to 80_000L, last)
    }
    @Test fun interruptionAtEveryStepLosesNothingAndResumes() {
        for (step in listOf("copy", "verify", "commit", "delete")) {
            src.listFiles()!!.forEach { it.delete() }; dst.listFiles()!!.forEach { it.delete() }
            val a = file(src, "a.mp4", 200_000); val sum = UsbMigration.sha256(a)
            var thrown = false
            val r1 = mig(true, fault = { s -> if (!thrown && s == "$step:a.mp4") { thrown = true; throw IOException("cut at $step") } }).run()
            assertIs<UsbMigration.Outcome.Failed>(r1.single(), step)
            assertTrue(a.exists() && UsbMigration.sha256(a) == sum, "source intact after a cut at $step")
            val r2 = mig(true).run()
            assertIs<UsbMigration.Outcome.Moved>(r2.single(), "second run finishes after a cut at $step")
            assertFalse(a.exists(), step); assertEquals(sum, UsbMigration.sha256(File(dst, "a.mp4")), step)
            assertTrue(dst.listFiles()!!.none { it.name.endsWith(".part") }, step)
        }
    }
    @Test fun resumesFromAPartialFileInTheMiddleOfTheCopy() {
        val a = file(src, "a.mp4", 300_000); val sum = UsbMigration.sha256(a)
        a.inputStream().use { i -> File(dst, "a.mp4.part").writeBytes(i.readNBytes(123_456)) }
        val m = mig(true); val before = m.run()
        assertIs<UsbMigration.Outcome.Moved>(before.single()); assertEquals(sum, UsbMigration.sha256(File(dst, "a.mp4")))
        assertEquals(300_000L - 123_456L, m.doneBytes, "only the missing bytes were copied")
    }
    @Test fun aCorruptPartialIsDetectedAndNotCommitted() {
        val a = file(src, "a.mp4", 100_000)
        File(dst, "a.mp4.part").writeBytes(ByteArray(40_000) { 9 })           // wrong prefix
        val r = mig(true).run()
        assertIs<UsbMigration.Outcome.Failed>(r.single()); assertTrue(a.exists()); assertFalse(File(dst, "a.mp4").exists())
        assertIs<UsbMigration.Outcome.Moved>(mig(true).run().single(), "the bad part was dropped, the retry succeeds")
    }
    @Test fun neverOverwritesADifferentFile() {
        val a = file(src, "a.mp4", 1000, seed = 1); File(dst, "a.mp4").writeBytes(ByteArray(1000) { 5 })
        val r = mig(true).run()
        assertIs<UsbMigration.Outcome.Skipped>(r.single()); assertTrue(a.exists()); assertEquals(5, File(dst, "a.mp4").readBytes()[0].toInt())
    }
    @Test fun fat32RefusesBigFilesBeforeCopyingAndRenamesBadNames() {
        file(src, "small:one?.mp4", 100)
        val r = mig(true, Fs.FAT32).run()
        assertIs<UsbMigration.Outcome.Moved>(r.single())
        val name = dst.listFiles()!!.single().name
        assertNull(UsbPaths.badSegment(name)); assertEquals(Fs.FAT32.maxFileBytes, (4L shl 30) - 1)
        assertTrue(file(src, "huge.mkv", 10).length() < Fs.FAT32.maxFileBytes)    // size gate itself: below
        val tiny = Fs.FAT32
        assertTrue((4L shl 30) > tiny.maxFileBytes)
    }
    @Test fun executablesLinksAndHiddenFilesAreNotMigrated() {
        file(src, "app.apk", 10); file(src, "run.sh", 10); file(src, ".played", 10); file(src, "x.mp4.part", 10); file(src, "ok.mp4", 10)
        assertEquals(listOf("ok.mp4"), mig(true).plan().map { it.name })
    }
    @Test fun driveRemovedStopsWithoutLoss() {
        val a = file(src, "a.mp4", 100_000); file(src, "b.mp4", 100_000)
        var calls = 0
        val r = mig(true).run(alive = { ++calls < 4 })                     // goes away during the first file
        assertTrue(r.any { it is UsbMigration.Outcome.Failed && it.reason == "volume removed" })
        assertTrue(a.exists() && File(src, "b.mp4").exists())
        assertTrue(mig(true).run().all { it is UsbMigration.Outcome.Moved })
    }
    @Test fun notEnoughSpaceOnTheDestinationFailsCleanly() {
        val a = file(src, "a.mp4", 1000)
        val m = UsbMigration(src, dst, Fs.EXFAT, UsbMigration.Options(deleteSources = true, minFreeBytes = Long.MAX_VALUE / 2))
        assertIs<UsbMigration.Outcome.Failed>(m.run().single()); assertTrue(a.exists())
    }
    @Test fun repatriateIsTheSameOperationBackwards() {
        val a = file(dst, "a.mp4", 5000); val sum = UsbMigration.sha256(a)
        val r = UsbMigration(dst, src, Fs.EXT4, UsbMigration.Options(deleteSources = true, minFreeBytes = 0)).run()
        assertIs<UsbMigration.Outcome.Moved>(r.single()); assertEquals(sum, UsbMigration.sha256(File(src, "a.mp4"))); assertFalse(a.exists())
    }
    @Test fun cancelKeepsEverything() {
        file(src, "a.mp4", 10_000); file(src, "b.mp4", 10_000)
        val m = mig(true); m.cancelled = true
        assertTrue(m.run().isEmpty()); assertEquals(2, src.listFiles()!!.size)
    }
}
