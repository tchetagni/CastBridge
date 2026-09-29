package castbridge.core

import castbridge.core.tv.*
import java.io.File
import kotlin.test.*

class FsDetectTest {
    private val mounts = FsInfo.parseMounts("""
        /dev/block/vold/public:8,1 /mnt/media_rw/1234-ABCD vfat rw,dirsync,nosuid 0 0
        /dev/fuse /storage/1234-ABCD fuse rw,nosuid 0 0
        /dev/block/vold/public:8,17 /mnt/media_rw/AAAA-0001 exfat rw 0 0
        /dev/fuse /storage/AAAA-0001 fuse rw 0 0
        /dev/block/vold/public:8,33 /mnt/media_rw/BBBB-0002 ntfs rw 0 0
        /dev/fuse /storage/BBBB-0002 sdcardfs rw 0 0
        /dev/block/vold/public:8,49 /mnt/media_rw/CCCC-0003 ext4 rw 0 0
        /dev/fuse /storage/CCCC-0003 fuse rw 0 0
        /dev/block/vold/public:8,65 /mnt/media_rw/DDDD-0004 f2fs rw 0 0
        /dev/fuse /storage/DDDD-0004 fuse rw 0 0
        /dev/fuse /storage/EEEE-0005 fuse rw 0 0
        /dev/block/vold/public:8,81 /mnt/media_rw/FFFF-0006 fuseblk rw 0 0
        /dev/fuse /storage/FFFF-0006 fuse rw 0 0
        /dev/fuse /storage/emulated fuse rw 0 0
        /dev/block/dm-5 /data ext4 rw 0 0
        /dev/block/sda1 /direct vfat rw 0 0
    """.trimIndent())

    private fun fs(p: String) = FsInfo.detect(mounts, p)

    @Test fun wrappersAreResolvedThroughTheRawMount() {
        assertEquals(Fs.FAT32, fs("/storage/1234-ABCD/Android/data/castbridge.receiver/files/videos"))
        assertEquals(Fs.EXFAT, fs("/storage/AAAA-0001/Android/data/x/files"))
        assertEquals(Fs.NTFS, fs("/storage/BBBB-0002/Android/data/x"))
        assertEquals(Fs.EXT4, fs("/storage/CCCC-0003/Android"))
        assertEquals(Fs.F2FS, fs("/storage/DDDD-0004/Android"))
    }

    @Test fun mountedDirectlyAndUnknownCases() {
        assertEquals(Fs.FAT32, fs("/direct/videos"))
        assertEquals(Fs.UNKNOWN, fs("/storage/EEEE-0005/Android"), "wrapper with no raw mount visible")
        assertEquals(Fs.UNKNOWN, fs("/storage/FFFF-0006/Android"), "fuseblk: NTFS-3G or exFAT, cannot tell")
        assertEquals(Fs.UNKNOWN, fs("/storage/emulated/0/Android"))
        assertEquals(Fs.UNKNOWN, fs("/nowhere/x"))
        assertEquals(Fs.UNKNOWN, FsInfo.detect("/storage/1234-ABCD/x", File("/definitely/not/there")), "unreadable /proc/mounts")
    }

    @Test fun fatLimitIsFourGibMinusOne() {
        assertEquals(4294967295L, Fs.FAT32.maxFileBytes)
        assertEquals(Long.MAX_VALUE, Fs.EXFAT.maxFileBytes)
    }
}

class NameRulesTest {
    @Test fun fatForbiddenCharactersAreMappedStablyAndDistinctly() {
        val a = NameRules.store("film: part 1?.mp4", Fs.FAT32)
        assertTrue(a.none { it in "\\/:*?\"<>|" }, a)
        assertTrue(a.startsWith("film_ part 1_~") && a.endsWith(".mp4"), a)
        assertEquals(a, NameRules.store("film: part 1?.mp4", Fs.FAT32), "same input, same output")
        assertNotEquals(a, NameRules.store("film_ part 1_.mp4", Fs.FAT32), "different originals stay different")
        assertEquals("film_ part 1_.mp4", NameRules.store("film_ part 1_.mp4", Fs.FAT32), "a clean name is untouched")
        assertEquals(a, NameRules.store(a, Fs.FAT32), "idempotent: the stored name maps to itself")
        assertTrue(NameRules.store("a|b.mkv", Fs.EXFAT).none { it == '|' }); assertTrue(NameRules.store("a<b>.mkv", Fs.NTFS).none { it == '<' })
    }

    @Test fun trailingDotsSpacesAndControlCharsOnFat() {
        assertFalse(NameRules.store("movie. ", Fs.FAT32).let { it.endsWith(".") || it.endsWith(" ") })
        assertTrue(NameRules.store("tab\there.mp4", Fs.FAT32).none { it.code < 32 })
    }

    @Test fun unixFileSystemsKeepEverythingExceptOverlongNames() {
        assertEquals("a:b*c?.mp4", NameRules.store("a:b*c?.mp4", Fs.EXT4))
        val long = "é".repeat(150) + ".mp4"                                   // 150 chars but 300 bytes
        val s = NameRules.store(long, Fs.EXT4)
        assertTrue(s.toByteArray().size + ".part".length <= 255, "${s.toByteArray().size} bytes")
        assertTrue(s.endsWith(".mp4"))
        assertTrue(s.none { it == '�' }, "no half characters")
        assertEquals(NameRules.store(long, Fs.FAT32), NameRules.store(long, Fs.FAT32))
    }

    @Test fun safeNameStillGuardsWhatClientsMaySend() {
        assertNull(ReceiverServer.safeName("../x.mp4")); assertNull(ReceiverServer.safeName("a\\b.mp4")); assertNull(ReceiverServer.safeName(".hidden"))
        assertEquals("a:b.mp4", ReceiverServer.safeName("a:b.mp4"), "mapped per volume, not refused")
    }
}

class StoragePolicyTest {
    private val GB = 1L shl 30
    private fun vol(id: String, kind: VolumeKind, fs: Fs = Fs.EXFAT, free: Long = 100 * GB, writable: Boolean = true, bps: Long = 0) =
        StorageVolume(id, id, File("/x/$id"), kind, fs, free, free, kind != VolumeKind.INTERNAL, writable, bps)
    private val internal = vol("internal", VolumeKind.INTERNAL, Fs.UNKNOWN, 10 * GB)

    private fun ids(p: StoragePolicy.Plan) = p.candidates.map { it.volume.id }

    @Test fun autoPrefersTheDriveWhenUsableThenInternal() {
        val p = StoragePolicy.plan(listOf(internal, vol("usb", VolumeKind.REMOVABLE)), "auto", 2 * GB, 0)
        assertEquals(listOf("usb", "internal"), ids(p)); assertNull(p.refusal)
    }

    @Test fun driveAbsent() = assertEquals(listOf("internal"), ids(StoragePolicy.plan(listOf(internal), "auto", GB, 0)))

    @Test fun readOnlyDriveIsSkippedInAuto() {
        val p = StoragePolicy.plan(listOf(internal, vol("usb", VolumeKind.REMOVABLE, writable = false)), "auto", GB, 0)
        assertEquals(listOf("internal"), ids(p)); assertTrue(p.skipped.single().reason.startsWith("not writable"))
    }

    @Test fun fullDriveIsSkipped() {
        val p = StoragePolicy.plan(listOf(internal, vol("usb", VolumeKind.REMOVABLE, free = GB / 2)), "auto", GB, 0)
        assertEquals(listOf("internal"), ids(p)); assertEquals("not enough space", p.skipped.single().reason)
    }

    @Test fun fat32WithABigFileFallsBackToInternalWhenItFits() {
        val p = StoragePolicy.plan(listOf(internal, vol("usb", VolumeKind.REMOVABLE, Fs.FAT32)), "auto", 5 * GB, 0)
        assertEquals(listOf("internal"), ids(p)); assertTrue(p.skipped.single().reason.startsWith("file too large for FAT32"))
    }

    @Test fun fat32WithABigFileAndNoFallbackIsRefusedWith413() {
        val small = vol("internal", VolumeKind.INTERNAL, Fs.UNKNOWN, 1 * GB)
        val p = StoragePolicy.plan(listOf(small, vol("usb", VolumeKind.REMOVABLE, Fs.FAT32)), "auto", 5 * GB, 0)
        assertTrue(p.candidates.isEmpty()); assertEquals(413, p.refusal?.http)
        assertEquals(413, StoragePolicy.plan(listOf(vol("usb", VolumeKind.REMOVABLE, Fs.FAT32)), "auto", 4 * GB, 0).refusal?.http, "4 GiB exactly is one byte too many")
        assertNull(StoragePolicy.plan(listOf(vol("usb", VolumeKind.REMOVABLE, Fs.FAT32)), "auto", 4 * GB - 1, 0).refusal, "4 GiB - 1 fits")
    }

    @Test fun exfatHasNoFourGigLimit() =
        assertEquals("usb", ids(StoragePolicy.plan(listOf(internal, vol("usb", VolumeKind.REMOVABLE, Fs.EXFAT)), "auto", 30 * GB, 0)).first())

    @Test fun explicitTargets() {
        val usb = vol("usb", VolumeKind.REMOVABLE, Fs.FAT32)
        assertEquals(listOf("internal"), ids(StoragePolicy.plan(listOf(internal, usb), "internal", GB, 0)))
        assertEquals(listOf("usb"), ids(StoragePolicy.plan(listOf(internal, usb), "usb", GB, 0)))
        assertEquals(413, StoragePolicy.plan(listOf(internal, usb), "usb", 5 * GB, 0).refusal?.http, "forced FAT32: refused, no silent fallback")
        assertEquals(503, StoragePolicy.plan(listOf(internal), "usb", GB, 0).refusal?.http, "forced drive missing")
        assertEquals(503, StoragePolicy.plan(listOf(internal, vol("usb", VolumeKind.REMOVABLE, writable = false)), "usb", GB, 0).refusal?.http)
        assertEquals(507, StoragePolicy.plan(listOf(internal), "internal", 20 * GB, 0).refusal?.http)
    }

    @Test fun slowDriveOrUnknownFsWithHugeFileIsOnlyALastResort() {
        val slow = vol("slow", VolumeKind.REMOVABLE, bps = 800_000)
        assertEquals(listOf("internal", "slow"), ids(StoragePolicy.plan(listOf(internal, slow), "auto", GB, 0)))
        val unk = vol("unk", VolumeKind.REMOVABLE, Fs.UNKNOWN)
        assertEquals(listOf("unk", "internal"), ids(StoragePolicy.plan(listOf(internal, unk), "auto", GB, 0)))
        val p = StoragePolicy.plan(listOf(internal, unk), "auto", 6 * GB, 0)
        assertEquals("internal", ids(p).first()); assertTrue(p.candidates.last().warnings.any { it.contains("unknown") })
        // the file only fits on the drive: it is used, with the warning
        val tiny = vol("internal", VolumeKind.INTERNAL, Fs.UNKNOWN, GB)
        assertEquals(listOf("slow"), ids(StoragePolicy.plan(listOf(tiny, slow), "auto", 2 * GB, 0)))
    }

    @Test fun slowWriteWarningComparesWithTheVideoBitrate() {
        val usb = vol("usb", VolumeKind.REMOVABLE, bps = 3_000_000)
        val size = 3 * GB
        val fast = StoragePolicy.plan(listOf(usb), "auto", size, 0, durMs = 3 * 3600_000L)   // ~290 kB/s video
        assertTrue(fast.candidates.single().warnings.isEmpty())
        val hard = StoragePolicy.plan(listOf(usb), "auto", size, 0, durMs = 20 * 60_000L)    // ~2.7 MB/s video
        assertTrue(hard.candidates.single().warnings.any { it.startsWith("slow drive") })
    }

    @Test fun safIsAnAutoFallbackBetweenTheDriveAndInternalAndNeverProgressive() {
        val saf = vol("saf", VolumeKind.SAF, Fs.UNKNOWN, -1)
        val p = StoragePolicy.plan(listOf(internal, saf), "auto", GB, 0)
        assertEquals(listOf("saf", "internal"), ids(p)); assertTrue(p.candidates.first().warnings.any { it.startsWith("no play-while") })
    }

    @Test fun targetValidation() {
        val vs = listOf(internal, vol("usb", VolumeKind.REMOVABLE))
        assertTrue(StoragePolicy.isValidTarget("auto", vs) && StoragePolicy.isValidTarget("internal", vs) && StoragePolicy.isValidTarget("usb", vs))
        assertFalse(StoragePolicy.isValidTarget("/etc", vs)); assertFalse(StoragePolicy.isValidTarget("../usb", vs))
    }
}

class WriteProbeTest {
    private val dir = kotlin.io.path.createTempDirectory("probe").toFile()
    @AfterTest fun tearDown() { dir.setWritable(true); dir.deleteRecursively() }

    @Test fun writableFolderLeavesNothingBehindAndMeasuresSpeed() {
        val r = WriteProbe.run(dir, speedBytes = 2L shl 20)
        assertTrue(r.writable, r.error); assertTrue(r.bytesPerSec > 0)
        assertEquals(0, dir.listFiles()!!.size)
        assertEquals(0L, WriteProbe.run(dir).bytesPerSec, "no speed test asked")
    }

    @Test fun speedIsComputedFromTheClock() {
        var t = 0L
        val r = WriteProbe.run(dir, speedBytes = 4L shl 20, now = { val v = t; t += 2_000_000_000L; v })
        assertEquals((4L shl 20) / 2, r.bytesPerSec)
    }

    @Test fun readOnlyFolderIsReported() {
        dir.setWritable(false)
        if (dir.canWrite()) return        // running as root: cannot simulate
        val r = WriteProbe.run(dir)
        assertFalse(r.writable); assertNotNull(r.error)
    }
}

class StorageLineTest {
    private val GB = 1L shl 30
    private val internal = StorageVolume("internal", "Mémoire interne", File("/i"), VolumeKind.INTERNAL, Fs.UNKNOWN, 3 * GB, 8 * GB, false)
    private val usb = StorageVolume("usb", "Clé USB", File("/u"), VolumeKind.REMOVABLE, Fs.EXFAT, 28 * GB, 32 * GB)

    @Test fun drivePrimaryComesFirst() {
        val l = StorageLine.render(listOf(internal, usb), "usb")
        assertEquals("Stockage : Clé USB (exFAT) 28 Go libres  |  Mémoire interne 3.0 Go libres", l)
    }

    @Test fun readOnlyAndAbsentDrives() {
        assertTrue(StorageLine.render(listOf(internal, usb.copy(writable = false)), "internal").contains("lecture seule"))
        assertTrue(StorageLine.render(listOf(internal), "internal", listOf(usb)).endsWith("Clé USB : retirée"))
    }

    @Test fun adminPageHasTheStorageControlsAndNeverInlinesDeviceDataUnescaped() {
        val html = ReceiverServer::class.java.getResourceAsStream("/castbridge/admin.html")!!.readBytes().decodeToString()
        for (k in listOf("/api/storage/target", "/api/storage/move", "/api/storage/saf/pick", "/api/storage/open-settings", "formatAdvice"))
            assertTrue(html.contains(k), k)
        assertFalse(html.contains("innerHTML"), "device-provided labels are only ever set through textContent")
    }
}
