package castbridge.core

import castbridge.core.tv.*
import java.io.File
import java.nio.file.Files
import kotlin.test.*

class UsbStoreTest {
    private lateinit var drive: File
    private lateinit var root: File

    @BeforeTest fun setUp() {
        drive = Files.createTempDirectory("usb").toFile()
        root = UsbLayout.rootOf(drive)
        assertTrue(UsbLayout.ensure(root))
    }
    @AfterTest fun tearDown() { drive.deleteRecursively() }

    private fun put(rel: String, bytes: ByteArray = "x".toByteArray()): File = File(root, rel).also { it.parentFile.mkdirs(); it.writeBytes(bytes) }
    private fun vol(id: String, kind: VolumeKind = VolumeKind.REMOVABLE, free: Long = 10L shl 30, writable: Boolean = true, bps: Long = 0, fs: Fs = Fs.EXFAT) =
        StorageVolume(id, "Clé $id", File("/x/$id"), kind, fs, free, 64L shl 30, kind != VolumeKind.INTERNAL, writable, bps)
    private val internal = StorageVolume("internal", "Mémoire interne", File("/i"), VolumeKind.INTERNAL, Fs.UNKNOWN, 600L shl 20, 2400L shl 20, false)

    // ---- layout ----
    @Test fun layoutCreatesTheFourFolders() {
        assertEquals(File(drive, "Download/CastBridge"), root)
        UsbLayout.SUBDIRS.forEach { assertTrue(File(root, it).isDirectory) }
        assertEquals(File("/storage/883F-EEB4"), UsbLayout.driveRootOf(File("/storage/883F-EEB4/Android/data/castbridge.receiver/files/videos")))
        assertNull(UsbLayout.driveRootOf(File("/data/user/0/x/files")))
    }

    // ---- readoption ----
    @Test fun readoptsLibraryPartsAndDuplicatesWithAbsentIndex() {
        put("Bibliotheque/a.mp4", ByteArray(5000) { 1 }); put("Bibliotheque/copy.mp4", ByteArray(5000) { 1 }); put("Bibliotheque/b.mp4", ByteArray(5000) { 2 })
        put("Bibliotheque/c.mp4.part", ByteArray(10)); put("Bibliotheque/c.mp4.meta"); put("Bibliotheque/.played")
        val r = UsbReadopt.scan(root)
        assertEquals(StoreIndex.State.ABSENT, r.index)
        assertEquals(setOf("Bibliotheque/a.mp4", "Bibliotheque/b.mp4", "Bibliotheque/copy.mp4"), r.files.map { it.path }.toSet())
        assertEquals(listOf("Bibliotheque/c.mp4.part"), r.parts.map { it.path })
        assertEquals(1, r.duplicates.size); assertEquals(setOf("Bibliotheque/a.mp4", "Bibliotheque/copy.mp4"), r.duplicates[0].toSet())
        assertTrue(File(root, "Bibliotheque/copy.mp4").exists(), "duplicates are reported, never deleted")
    }
    @Test fun indexPresentThenCorruptThenOversizedAreAllTolerated() {
        StoreIndex.write(root, "usb-883F-EEB4", 3, 1_800_000_000_000)
        assertEquals(StoreIndex.State.PRESENT, UsbReadopt.scan(root).index)
        val d = StoreIndex.read(root).second!!
        assertEquals(1, d.version); assertEquals("usb-883F-EEB4", d.volumeId); assertEquals(1_800_000_000_000, d.updatedMs)
        StoreIndex.file(root).writeText("{\"version\": 1, \"volu")
        assertEquals(StoreIndex.State.CORRUPT, UsbReadopt.scan(root).index)
        StoreIndex.file(root).writeText("[1,2,3]"); assertEquals(StoreIndex.State.CORRUPT, StoreIndex.read(root).first)
        StoreIndex.file(root).writeText("{\"version\":99,\"volumeId\":\"x\"}"); assertEquals(StoreIndex.State.CORRUPT, StoreIndex.read(root).first)
        StoreIndex.file(root).writeBytes(ByteArray(StoreIndex.MAX_BYTES + 1) { ' '.code.toByte() })
        assertEquals(StoreIndex.State.CORRUPT, StoreIndex.read(root).first)
        put("Bibliotheque/a.mp4")
        assertTrue(UsbReadopt.refreshIndex(root, "usb-1", UsbReadopt.scan(root), 1_800_000_000_000))
        assertEquals(StoreIndex.State.PRESENT, StoreIndex.read(root).first)
    }
    @Test fun wrongClockDateIsNotTrusted() {
        StoreIndex.write(root, "v", 0, 5_000)                 // TV clock at 1970
        assertNull(StoreIndex.read(root).second!!.updatedMs)
    }
    @Test fun indexWriteIsAtomicNoTempLeft() {
        StoreIndex.write(root, "v", 1, 1_800_000_000_000)
        assertFalse(File(StoreIndex.file(root).path + ".tmp").exists())
    }
    @Test fun scanStaysInsideTheRootAndIgnoresDangerousEntries() {
        val outside = File(drive, "Films").also { it.mkdirs(); File(it, "secret.mp4").writeText("no") }
        put("Bibliotheque/ok.mp4")
        put("Bibliotheque/installer.apk"); put("Bibliotheque/run.sh"); put("Bibliotheque/Setup.EXE")
        put("Bibliotheque/bad\u0001name.mp4")
        try { Files.createSymbolicLink(File(root, "Bibliotheque/link.mp4").toPath(), File(outside, "secret.mp4").toPath())
              Files.createSymbolicLink(File(root, "Medias/dir").toPath(), outside.toPath()) } catch (e: Exception) { /* no symlinks here */ }
        val r = UsbReadopt.scan(root)
        assertEquals(listOf("Bibliotheque/ok.mp4"), r.files.map { it.path })
        assertTrue(r.ignored.any { it.path.endsWith("installer.apk") && it.reason.contains("executable") })
        assertTrue(r.ignored.any { it.path.endsWith("run.sh") }); assertTrue(r.ignored.any { it.path.endsWith("Setup.EXE") })
        assertTrue(r.files.none { it.path.contains("secret") })
    }
    @Test fun scanIsBoundedInDepthEntriesAndTime() {
        put("Medias/a/b/c/d/e/f/g/deep.mp4")
        assertTrue(UsbReadopt.scan(root, UsbReadopt.Limits(maxDepth = 3)).ignored.any { it.reason == "too deep" })
        repeat(30) { put("Bibliotheque/f$it.mp4") }
        val r = UsbReadopt.scan(root, UsbReadopt.Limits(maxEntries = 10)); assertTrue(r.truncated); assertTrue(r.files.size <= 10)
        var t = 0L
        val slow = UsbReadopt.scan(root, UsbReadopt.Limits(maxMillis = 50)) { t += 20; t }
        assertTrue(slow.truncated)
    }
    @Test fun missingRootIsReportedNotCreated() {
        val r = UsbReadopt.scan(File(drive, "nothing"))
        assertTrue(r.rootMissing); assertFalse(File(drive, "nothing").exists())
    }
    @Test fun scanNeverTouchesFilesOutsideTheRoot() {
        File(drive, "DCIM").mkdirs(); File(drive, "DCIM/a.jpg").writeText("p"); File(drive, "Download/other.mp4").writeText("o")
        put("Bibliotheque/a.mp4")
        assertEquals(listOf("Bibliotheque/a.mp4"), UsbReadopt.scan(root).files.map { it.path })
    }

    // ---- paths & names ----
    @Test fun dangerousRelativePathsAreRejected() {
        for (bad in listOf("../x.mp4", "a/../../x", "/etc/passwd", "a\\b.mp4", "a//b", "", ".", "a/./b", "x\u0000y", "a:b.mp4", "con?.mp4", "a/b ", "d.", "a".repeat(300),
                           (1..9).joinToString("/") { "d$it" } + "/f"))
            assertNull(UsbPaths.resolve(root, bad), "should reject '$bad'")
        assertNotNull(UsbPaths.resolve(root, "Bibliotheque/film 1.mp4"))
        assertNull(UsbPaths.resolve(root, "x".repeat(2000)))
    }
    @Test fun symlinkEscapeIsRejected() {
        val outside = File(drive, "Out").also { it.mkdirs() }
        try { Files.createSymbolicLink(File(root, "Medias/esc").toPath(), outside.toPath()) } catch (e: Exception) { return }
        assertNull(UsbPaths.resolve(root, "Medias/esc/x.mp4"))
    }
    @Test fun phoneNamesBecomeValidExfatNames() {
        val n = UsbPaths.storedName("a:b?.mp4")
        assertNull(UsbPaths.badSegment(n)); assertTrue(n.endsWith(".mp4")); assertEquals(n, UsbPaths.storedName(n))
        assertNull(UsbPaths.badSegment(UsbPaths.storedName("é".repeat(300) + ".mkv")))
    }

    // ---- secrets never written ----
    @Test fun noSecretCanBeWrittenToTheDrive() {
        val secrets = listOf("pin", "pinCode", "token", "authToken", "sshKey", "ssh_private_key", "parentalCode", "parentalPin", "trustedPhones",
            "parentalReports", "pairingToken", "pairing", "secret", "password")
        for (k in secrets) {
            assertFalse(SafeSettings.accepts(k), k)
            assertFailsWith<IllegalArgumentException>(k) { SafeSettings.write(root, mapOf("language" to "fr", k to "123456")) }
        }
        assertFalse(SafeSettings.file(root).exists(), "a refused write leaves nothing")
        assertTrue(SafeSettings.ALLOWED.none { SafeSettings.forbidden(it) }, "the whitelist itself holds no forbidden word")
    }
    @Test fun settingsRoundTripAndHandEditedFileIsFiltered() {
        SafeSettings.write(root, mapOf("language" to "fr", "quotaMb" to 2048L, "heavyOnUsb" to true, "tiles" to listOf("quiz", "games")))
        val m = SafeSettings.read(root)
        assertEquals("fr", m["language"]); assertEquals(true, m["heavyOnUsb"]); assertEquals(listOf("quiz", "games"), m["tiles"])
        SafeSettings.file(root).writeText("""{"language":"en","pin":"123456","sshKey":"AAAA","unknown":1,"quotaMb":5}""")
        assertEquals(mapOf<String, Any?>("language" to "en", "quotaMb" to 5L), SafeSettings.read(root))
        SafeSettings.file(root).writeText("not json"); assertTrue(SafeSettings.read(root).isEmpty())
        SafeSettings.file(root).writeBytes(ByteArray(SafeSettings.MAX_BYTES + 1) { 'a'.code.toByte() }); assertTrue(SafeSettings.read(root).isEmpty())
        SafeSettings.write(root, mapOf("language" to "fr"))
        assertFalse(SafeSettings.file(root).readText().contains("pin", ignoreCase = true))
    }

    // ---- volume choice ----
    @Test fun heavyContentGoesToTheDriveWhenThere() {
        val d = HeavyStorage.decide(listOf(internal, vol("usb-1")), enabled = true)
        assertEquals(HeavyStorage.Where.USB, d.where); assertEquals("usb-1", d.volume!!.id); assertTrue(d.warnings.isEmpty())
    }
    @Test fun noDriveFallsBackToInternalWithAClearWarning() {
        val d = HeavyStorage.decide(listOf(internal), enabled = true)
        assertEquals(HeavyStorage.Where.INTERNAL, d.where); assertTrue(d.warnings.single().contains("Aucune clé USB"))
    }
    @Test fun settingOffMeansInternalWithoutWarning() {
        val d = HeavyStorage.decide(listOf(internal, vol("usb-1")), enabled = false)
        assertEquals(HeavyStorage.Where.INTERNAL, d.where); assertTrue(d.warnings.isEmpty())
    }
    @Test fun severalDrivesPreferTheChosenOneElseTheEmptiest() {
        val vs = listOf(internal, vol("usb-a", free = 5L shl 30), vol("usb-b", free = 20L shl 30))
        assertEquals("usb-b", HeavyStorage.decide(vs, true).volume!!.id)
        assertEquals("usb-a", HeavyStorage.decide(vs, true, "usb-a").volume!!.id)
        val d = HeavyStorage.decide(vs, true, "usb-gone")
        assertEquals("usb-b", d.volume!!.id); assertTrue(d.warnings.any { it.contains("absente") })
    }
    @Test fun readOnlyOrFullDriveIsNeverChosen() {
        val ro = HeavyStorage.decide(listOf(internal, vol("usb-1", writable = false)), true)
        assertEquals(HeavyStorage.Where.INTERNAL, ro.where); assertTrue(ro.warnings.any { it.contains("lecture seule") })
        val full = HeavyStorage.decide(listOf(internal, vol("usb-1", free = 1L shl 20)), true, need = 5L shl 30)
        assertEquals(HeavyStorage.Where.INTERNAL, full.where); assertTrue(full.warnings.any { it.contains("pas assez de place") })
    }
    @Test fun slowAndFat32DrivesAreUsedWithWarnings() {
        val d = HeavyStorage.decide(listOf(internal, vol("usb-1", bps = 1_400_000, fs = Fs.FAT32)), true)
        assertEquals(HeavyStorage.Where.USB, d.where)
        assertTrue(d.warnings.any { it.contains("lente") }); assertTrue(d.warnings.any { it.contains("FAT32") })
    }
    @Test fun fat32AndExfatLimits() {
        assertEquals((4L shl 30) - 1, Fs.FAT32.maxFileBytes); assertEquals(Long.MAX_VALUE, Fs.EXFAT.maxFileBytes)
    }
}
