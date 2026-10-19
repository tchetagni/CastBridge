package castbridge.core.tv

import castbridge.core.FakePlayer
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

private const val GiB = 1L shl 30
private const val MiB = 1L shl 20

/**
 * R-55 (2026-10-18, the owner: « l'erreur … n'est pas normale »): a key with 23 Go free, mounted read-write, and a copy refused because « il resterait 211 Mo sur Mémoire
 * interne » without a word about the key. The refusal now says, volume by volume, why each other volume was not used (pure rule, docs/STORAGE.md § 4).
 */
class SpaceRefusalTextTest {
    private val size = 3 * GiB
    private val minFree = GiB
    private val internal = StorageVolume("internal", "Mémoire interne", File("/i"), VolumeKind.INTERNAL, Fs.UNKNOWN, 3 * GiB + 211 * MiB, 0, false)
    private fun key(free: Long = 23 * GiB, writable: Boolean = true, fs: Fs = Fs.EXFAT, bps: Long = 0) =
        StorageVolume("usb-1", "Clé USB", File("/k"), VolumeKind.REMOVABLE, fs, free, 64 * GiB, true, writable, bps)
    private fun sentences(vararg v: StorageVolume, target: String = StoragePolicy.AUTO, skipped: List<StoragePolicy.Skipped> = emptyList(), byPhone: Boolean = false, sz: Long = size) =
        SpaceRefusalText.sentences(v.toList(), skipped, target, sz, minFree, headId = "internal", byPhone = byPhone)

    @Test fun theFieldCaseNamesTheReadOnlyKeyAfterTheUnchangedHeadline() {
        val head = TransferRule.message("Mémoire interne", internal.free, size, minFree, driveAbsent = false)
        assertEquals("Espace insuffisant : il resterait 211 Mo sur Mémoire interne après le transfert, il en faut 1 Go : libérez 813 Mo ou choisissez un autre volume.", head)
        val s = sentences(internal, key(writable = false), skipped = listOf(StoragePolicy.Skipped("usb-1", "not writable (EROFS)"), StoragePolicy.Skipped("internal", "not enough space")))
        assertEquals(listOf("Clé USB (23 Go libres) non utilisée : lecture seule (test d'écriture raté)."), s)
        assertEquals("$head Clé USB (23 Go libres) non utilisée : lecture seule (test d'écriture raté).", SpaceRefusalText.with(head, s))
    }

    @Test fun aFileTooBigForTheKeysFileSystemIsSaid() {
        assertEquals(listOf("Clé USB (23 Go libres) non utilisée : fichier trop gros pour FAT32."), sentences(internal, key(fs = Fs.FAT32), sz = 5 * GiB))
        assertEquals(listOf("Clé USB (23 Go libres) non utilisée : raison non relevée."), sentences(internal, key(fs = Fs.FAT32), sz = 3 * GiB), "3 GB fit FAT32: not that")
    }

    @Test fun theInternalSettingIsNamedWhenTheKeyHadRoom() {
        val tv = "Clé USB (23 Go libres) non utilisée : réglage « Où ranger les nouveaux fichiers : Mémoire interne » ; choisissez Automatique ou la clé dans MENU › Stockage."
        assertEquals(listOf(tv), sentences(internal, key(), target = StoragePolicy.INTERNAL))
        val phone = "Clé USB (23 Go libres) non utilisée : choix « Destination sur la TV : Mémoire interne » du téléphone ; choisissez Automatique ou la clé."
        assertEquals(listOf(phone), sentences(internal, key(), target = StoragePolicy.INTERNAL, byPhone = true))
        // the setting is not the reason when the key could not have taken the copy anyway
        assertEquals(listOf("Clé USB (23 Go libres) non utilisée : lecture seule (test d'écriture raté)."), sentences(internal, key(writable = false), target = StoragePolicy.INTERNAL))
        assertEquals(listOf("Clé USB (1 Go libres) non utilisée : pas assez de place (le fichier ne tient pas, il manque 2 Go)."), sentences(internal, key(free = GiB), target = StoragePolicy.INTERNAL))
    }

    @Test fun aSlowKeyWithRoomIsSaidToHaveBeenKeptForTheLastResort() {
        assertEquals(listOf("Clé USB (23 Go libres) non utilisée : jugée lente, pourtant retenue en dernier recours."), sentences(internal, key(bps = 1_200_000)))
        // a key that is not slow and has room, left out for no known reason: not silent either
        assertEquals(listOf("Clé USB (23 Go libres) non utilisée : raison non relevée."), sentences(internal, key(bps = 8_000_000)))
    }

    @Test fun noRoomAndQuotaAreSaidToo() {
        assertEquals(listOf("Clé USB (3.5 Go libres) non utilisée : pas assez de place (il resterait 512 Mo après le transfert, il en faut 1 Go)."),
            sentences(internal, key(free = 3 * GiB + 512 * MiB), skipped = listOf(StoragePolicy.Skipped("usb-1", "not enough space"))))
        assertEquals(listOf("Clé USB (1 Go libres) non utilisée : pas assez de place (le fichier ne tient pas, il manque 2 Go)."), sentences(internal, key(free = GiB)))
        assertEquals(listOf("Clé USB (23 Go libres) non utilisée : quota de stockage atteint."), sentences(internal, key(), skipped = listOf(StoragePolicy.Skipped("usb-1", "quota exceeded"))))
    }

    @Test fun withoutAnyOtherVolumeTheMessageIsUnchanged() {
        val head = TransferRule.message("Mémoire interne", internal.free, size, minFree, driveAbsent = true)
        assertEquals(emptyList(), sentences(internal))
        assertEquals(head, SpaceRefusalText.with(head, sentences(internal)))
        // a key asked for by name: the other volume is not judged, the headline keeps saying « choisissez un autre volume »
        val usbHead = SpaceRefusalText.sentences(listOf(internal.copy(free = 9 * GiB), key(free = 2 * GiB)), emptyList(), "usb-1", size, minFree, headId = "usb-1")
        assertEquals(emptyList(), usbHead)
    }

    @Test fun theMinuteRuleOfTheKeyRetest() {
        val now = 1_000_000L
        assertTrue(KeyRetest.allowed(null, now)); assertTrue(KeyRetest.allowed(now - 60_000, now)); assertTrue(KeyRetest.allowed(now - 3_600_000, now))
        assertFalse(KeyRetest.allowed(now - 59_999, now)); assertFalse(KeyRetest.allowed(now, now))
        assertTrue(KeyRetest.allowed(now + 5_000, now), "a clock set back never blocks the retest for good")
    }

    @Test fun onlyAReadOnlyRemovableVolumeOfThePoolIsRetestedAndOncePerMinute() {
        val ro = listOf(StoragePolicy.Skipped("usb-1", "not writable (EROFS)"), StoragePolicy.Skipped("internal", "not enough space"))
        val vols = listOf(internal, key(writable = false))
        val now = 5_000_000L
        assertEquals(listOf("usb-1"), KeyRetest.due(ro, vols, StoragePolicy.AUTO, emptyMap(), now))
        assertEquals(emptyList(), KeyRetest.due(ro, vols, StoragePolicy.AUTO, mapOf("usb-1" to now - 10_000), now), "tested 10 s ago")
        assertEquals(listOf("usb-1"), KeyRetest.due(ro, vols, StoragePolicy.AUTO, mapOf("usb-1" to now - 61_000), now))
        assertEquals(listOf("usb-1"), KeyRetest.due(ro + ro, vols, "usb-1", emptyMap(), now), "never twice in one call")
        assertEquals(emptyList(), KeyRetest.due(ro, vols, StoragePolicy.INTERNAL, emptyMap(), now), "an explicit setting is not a failed test: nothing is tested")
        assertEquals(emptyList(), KeyRetest.due(listOf(StoragePolicy.Skipped("usb-1", "not enough space")), vols, StoragePolicy.AUTO, emptyMap(), now))
        assertEquals(emptyList(), KeyRetest.due(listOf(StoragePolicy.Skipped("internal", "not writable")), vols, StoragePolicy.AUTO, emptyMap(), now), "the internal storage has no mount test")
    }
}

/** The same sentences through the real HTTP server (classic PUT, pre-flight check, multi-connection begin), and the key tested again at most once a minute. */
class SpaceRefusalServerTest {
    private val root = kotlin.io.path.createTempDirectory("space").toFile()
    private val internalDir = File(root, "internal").apply { mkdirs() }
    private val usbDir = File(root, "usb").apply { mkdirs() }
    private var usbWritable = false
    private var healsOnRetest = false
    private val retests = AtomicInteger()
    private val capacity = mutableMapOf("internal" to (3 * GiB + 211 * MiB), "usb-1" to 23 * GiB)
    private val size = 3 * GiB

    private val provider = object : VolumeProvider {
        override fun scan(remeasure: Boolean) = listOf(
            StorageVolume("internal", "Mémoire interne", internalDir, VolumeKind.INTERNAL, Fs.UNKNOWN, 0, 0, false),
            StorageVolume("usb-1", "Clé USB", usbDir, VolumeKind.REMOVABLE, Fs.EXFAT, 0, 0, true, usbWritable, 0, if (usbWritable) null else "EROFS"))
        override fun reprobe(volumeId: String) { retests.incrementAndGet(); if (healsOnRetest) usbWritable = true }
    }
    private val registry = VolumeRegistry(provider) { v -> capacity.getValue(v.id) - used(v.dir) }.also { it.refresh() }
    private val server = ReceiverServer(registry, FakePlayer(), 0, profile = TvProfile(minFreeBytes = 0, minFreeAfterTransfer = GiB)).apply { start(5000, false) }
    private val base = "http://127.0.0.1:${server.listeningPort}"
    private val tv = TvClient(base)

    @AfterTest fun tearDown() { server.stop(); root.deleteRecursively() }

    private fun used(d: File) = d.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    private val headline = "Espace insuffisant : il resterait 211 Mo sur Mémoire interne après le transfert, il en faut 1 Go : libérez 813 Mo ou choisissez un autre volume."
    private val readOnly = "Clé USB (23 Go libres) non utilisée : lecture seule (test d'écriture raté)."

    private fun put(name: String, total: Long = size, target: String? = null): Pair<Int, String> {
        val bytes = ByteArray(10)
        val c = URL("$base/upload/${TvClient.enc(name)}?offset=0&total=$total" + (target?.let { "&target=${TvClient.enc(it)}" } ?: "")).openConnection() as HttpURLConnection
        c.requestMethod = "PUT"; c.doOutput = true; c.setFixedLengthStreamingMode(bytes.size)
        c.setRequestProperty("Connection", "close"); c.connectTimeout = 5000; c.readTimeout = 10_000
        c.outputStream.use { it.write(bytes) }
        val code = c.responseCode
        return code to (if (code < 400) c.inputStream else c.errorStream).readBytes().decodeToString()
    }

    private fun post(path: String): Pair<Int, String> {
        val c = URL(base + path).openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true; c.setFixedLengthStreamingMode(0); c.setRequestProperty("Connection", "close"); c.outputStream.close()
        val code = c.responseCode
        return code to (if (code < 400) c.inputStream else c.errorStream).readBytes().decodeToString()
    }

    @Test fun theRefusalNamesTheReadOnlyKeyAndTestsItAgainOnlyOncePerMinute() {
        val (code, body) = put("film.mkv")
        assertEquals(507, code, body)
        assertEquals("$headline $readOnly", TvClient.str(body, "message"), body)
        assertEquals(1, retests.get(), "the key set aside as read-only was tested again before the answer")
        assertEquals(507, put("film.mkv").first)
        assertEquals(1, retests.get(), "a second refusal within the minute does not test the key again")
        tv.checkStorage("film.mkv", size)
        assertEquals(1, retests.get(), "nor does the pre-flight check")
    }

    @Test fun aKeyThatOnlyFailedItsSingleTestIsUsedAfterTheRetest() {
        healsOnRetest = true
        val (code, body) = put("film.mkv")
        assertEquals(200, code, body)
        assertEquals(1, retests.get())
        assertTrue(File(usbDir, "film.mkv.part").exists(), "the copy went to the key"); assertFalse(File(internalDir, "film.mkv.part").exists())
    }

    @Test fun theInternalSettingIsNamedAndNothingIsTested() {
        usbWritable = true; registry.refresh()
        assertTrue(server.setTargetValue("internal"))
        val (code, body) = put("film.mkv")
        assertEquals(507, code, body)
        assertEquals("$headline Clé USB (23 Go libres) non utilisée : réglage « Où ranger les nouveaux fichiers : Mémoire interne » ; choisissez Automatique ou la clé dans MENU › Stockage.",
            TvClient.str(body, "message"), body)
        // a key that is read-only while the setting says internal: the setting is not the gap, and no test is made (the setting left the key out)
        usbWritable = false; registry.refresh()
        assertEquals("$headline $readOnly", TvClient.str(put("other.mkv").second, "message"))
        assertEquals(0, retests.get())
    }

    @Test fun thePhonesOwnChoiceIsNamedAsTheirs() {
        usbWritable = true; registry.refresh()
        val (code, body) = put("film.mkv", target = "internal")
        assertEquals(507, code, body)
        assertEquals("$headline Clé USB (23 Go libres) non utilisée : choix « Destination sur la TV : Mémoire interne » du téléphone ; choisissez Automatique ou la clé.",
            TvClient.str(body, "message"), body)
        assertEquals(0, retests.get())
    }

    @Test fun thePreFlightCheckSaysTheSameAndRespectsTheMinute() {
        val c = tv.checkStorage("film.mkv", size)
        assertFalse(c.ok); assertEquals(507, c.status)
        assertEquals("$headline $readOnly", c.message)
        assertEquals(1, retests.get())

        healsOnRetest = true; usbWritable = false; registry.refresh()
        val other = TvClient(base).checkStorage("film2.mkv", size)         // the minute is not over: no new test, still read-only
        assertFalse(other.ok); assertEquals(1, retests.get())
    }

    @Test fun thePreFlightCheckUsesAKeyThatPassesTheRetest() {
        healsOnRetest = true
        val c = tv.checkStorage("film.mkv", size)
        assertTrue(c.ok, c.message); assertEquals("usb-1", c.volume); assertEquals(1, retests.get())
    }

    @Test fun theMultiConnectionBeginSaysTheSame() {
        val (code, body) = post("/api/transfer/begin?name=film.mkv&size=$size&blockSize=1048576")
        assertEquals(507, code, body)
        assertEquals("$headline $readOnly", TvClient.str(body, "message"), body)
        assertEquals(1, retests.get())
    }
}
