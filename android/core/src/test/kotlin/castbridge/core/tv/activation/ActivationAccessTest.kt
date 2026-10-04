package castbridge.core.tv.activation

import java.io.File
import java.io.FileNotFoundException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Real failure 2026-10-04 (Android 14, targetSdk 35): `activation` and `activation_2026_unlimited.txt` in <usb>/Download are invisible to CastBridge-TV without « all files access ».
 * What the screen must say, where the explorer starts, which names are taken (docs/TV-ACTIVATION-CLE-USB.md).
 */
class ActivationAccessTest {
    private val key = "/storage/A379-E209"
    private val dl = File("$key/Download")
    private val own = File("$key/Android/data/castbridge.receiver/files")
    private val ownPath = own.path
    private val vols = listOf(VolumeFact("A379-E209", false))
    private val missingText = "Android n'autorise pas CastBridge-TV à lire Download : donnez « Accès à tous les fichiers » (Réglages), ou déposez le fichier dans le dossier de l'application : "

    private class Fs(val files: Map<String, Any> = emptyMap(), val dirs: Map<String, List<String>?> = emptyMap()) : LookupFs {
        val reads = ArrayList<String>()
        override fun isDir(f: File) = f.path in dirs
        override fun list(dir: File) = dirs[dir.path]
        override fun length(f: File): Long = (files[f.path] as? ByteArray)?.size?.toLong() ?: -1
        override fun read(f: File, max: Int): ByteArray {
            reads += f.path
            return when (val v = files[f.path]) {
                null -> throw FileNotFoundException("${f.path}: open failed: ENOENT (No such file or directory)")
                is Exception -> throw v
                else -> (v as ByteArray).copyOf(minOf(max, (v as ByteArray).size))
            }
        }
    }
    private fun run(fs: LookupFs, access: StorageAccess, verdict: Verdict = Verdict.ACCEPTED) = ActivationLookup.run(
        ActivationLookup.candidates(listOf(dl to "A379-E209"), listOf(own to "A379-E209")),
        ActivationLookup.dirsToList(listOf(dl to "A379-E209"), listOf(own to "A379-E209")), vols, fs, access) { verdict }

    // ---- permission state ----
    @Test fun `permission rule by api level`() {
        assertEquals(StorageAccess.MISSING, StoragePermission.decide(34, managerGranted = false, readGranted = true))   // READ_EXTERNAL_STORAGE does nothing on Android 14
        assertEquals(StorageAccess.GRANTED, StoragePermission.decide(34, true, false))
        assertEquals(StorageAccess.MISSING, StoragePermission.decide(33, false, true))
        assertEquals(StorageAccess.GRANTED, StoragePermission.decide(31, false, true))
        assertEquals(StorageAccess.GRANTED, StoragePermission.decide(31, true, false))
        assertEquals(StorageAccess.MISSING, StoragePermission.decide(31, false, false))
        assertEquals(StorageAccess.GRANTED, StoragePermission.decide(28, false, true))
        assertEquals(StorageAccess.MISSING, StoragePermission.decide(28, false, false))
    }

    // ---- the diagnostic says the real cause ----
    @Test fun `empty or unreadable Download with the permission missing says the permission, never no key alone`() {
        for (fs in listOf(Fs(dirs = mapOf(dl.path to emptyList(), own.path to emptyList())),
            Fs(files = mapOf("${dl.path}/activation" to SecurityException("scoped")), dirs = mapOf(dl.path to null)))) {
            val o = run(fs, StorageAccess.MISSING)
            val lines = ActivationLookupReport.lines(o.facts)
            assertEquals(missingText + ownPath, lines.last())
            assertTrue(lines.none { "introuvable" in it }, lines.toString())
            assertEquals(missingText + ownPath, ActivationLookupReport.noKeyHeadline(StorageAccess.MISSING, ownPath))
        }
        assertEquals("Aucune clé trouvée sur la clé USB.", ActivationLookupReport.noKeyHeadline(StorageAccess.GRANTED, ownPath))
        assertEquals(missingText + ActivationLookup.OWN_DIR_TEXT, ActivationLookupReport.missingAccess(null))
    }

    @Test fun `with the permission the old wording stays and a readable file is still reported as read`() {
        val lines = ActivationLookupReport.lines(run(Fs(dirs = mapOf(dl.path to emptyList())), StorageAccess.GRANTED).facts)
        assertTrue("introuvable" in lines.last() && missingText !in lines.last())
        val read = ActivationLookupReport.lines(run(Fs(files = mapOf("$ownPath/activation" to "T".toByteArray())), StorageAccess.MISSING, Verdict.NOT_VALID).facts)
        assertTrue("lu, mais" in read.last(), read.toString())                  // a file really read hides nothing behind the permission line
    }

    // ---- name filter ----
    @Test fun `automatic names are read only in the own folder and only as text of 16 KiB`() {
        val seen = Fs(
            files = mapOf(
                "$ownPath/activation_2026_unlimited.txt" to "GOOD".toByteArray(),
                "$ownPath/activation.txt" to "GOOD2".toByteArray(),
                "$ownPath/activation_big.txt" to ByteArray(20_000) { 'a'.code.toByte() },
                "$ownPath/activation_bin.txt" to byteArrayOf(0x50, 0x4b, 0, 3),
                "$ownPath/activation.apk" to "X".toByteArray(),
                "$ownPath/monfichier.txt" to "X".toByteArray(),
                "${dl.path}/activation_2026_unlimited.txt" to "X".toByteArray(),
                "${dl.path}/CastBridge/activation_a.txt" to "X".toByteArray()),
            dirs = mapOf(own.path to listOf("activation_2026_unlimited.txt", "activation.txt", "activation_big.txt", "activation_bin.txt", "activation.apk", "monfichier.txt"),
                dl.path to listOf("activation_2026_unlimited.txt"), "${dl.path}/CastBridge" to listOf("activation_a.txt")))
        val o = run(seen, StorageAccess.MISSING, Verdict.NOT_VALID)
        val read = seen.reads.map { it.substringAfterLast('/') }
        assertTrue("activation_2026_unlimited.txt" in read && "activation.txt" in read)
        assertTrue("activation.apk" !in read && "monfichier.txt" !in read)
        assertTrue(seen.reads.none { it.startsWith(dl.path) && it.endsWith(".txt") }, "Download names are never taken: ${seen.reads}")
        val probes = o.facts.probes.filter { it.place == Place.OWN_DIR }
        assertTrue(Probe.TOO_BIG in probes.map { it.probe })
        val bin = run(Fs(files = mapOf("$ownPath/activation_bin.txt" to byteArrayOf(0x50, 0x4b, 0, 3)), dirs = mapOf(own.path to listOf("activation_bin.txt"))), StorageAccess.MISSING, Verdict.ACCEPTED)
        assertFalse(bin.accepted, "a binary file is never handed to the verifier")
    }

    @Test fun `a good activation_star_txt in the own folder is accepted`() {
        val fs = Fs(files = mapOf("$ownPath/activation_2026_unlimited.txt" to "GOOD\n".toByteArray()), dirs = mapOf(own.path to listOf("activation_2026_unlimited.txt")))
        assertTrue(run(fs, StorageAccess.MISSING).accepted)
    }

    @Test fun `name rules table`() {
        for (ok in listOf("activation", "activation.txt", "Activation.TXT", "activation_2026_unlimited.txt", "activation_.txt")) assertTrue(ActivationNames.isAutoName(ok) || ok == "activation", ok)
        for (no in listOf("activation.apk", "activation.zip", "activation.txt.exe", "activations.txt", "myactivation.txt", "activation_x/../y.txt", "activation_x.png", "activation (1).txt")) assertFalse(ActivationNames.isAutoName(no), no)
        assertTrue(ActivationNames.isOffered("activation_2026_unlimited.txt", 594))
        assertTrue(ActivationNames.isOffered("activation", 594) && ActivationNames.isOffered("Activation (1)", 10))
        for ((n, s) in listOf("activation.apk" to 100L, "activation.zip" to 100L, "activation.mp4" to 100L, "activation.txt" to 20_000L, "activation.txt" to 0L, "other.txt" to 100L)) assertFalse(ActivationNames.isOffered(n, s), "$n $s")
    }

    @Test fun `hint for a txt left in Download is kept`() {
        val lines = ActivationLookupReport.misnamed(listOf("activation_2026_unlimited.txt", "activation.txt"))
        assertEquals(2, lines.size)
        assertTrue(lines.all { "renommez-le « activation »" in it && ActivationLookup.OWN_DIR_TEXT in it && ".txt" in it }, lines.toString())
    }

    // ---- drop folder lines ----
    @Test fun `drop folder lines give the exact path and at most six names`() {
        val names = listOf("g", "f", "e", "d", "c", "b", "a", "device-request.txt")
        val l = DropFolders.lines(listOf(DropFolder("A379-E209", ownPath, names), DropFolder(null, "/storage/emulated/0/Android/data/castbridge.receiver/files", emptyList()), DropFolder("B1B1-0000", "/storage/B1B1-0000/Android/data/castbridge.receiver/files", null)))
        assertEquals("Clé A379-E209 : déposez le fichier « activation » dans Android/data/castbridge.receiver/files/", l[0])
        assertEquals("   CastBridge-TV y voit : a, b, c, d, device-request.txt, e et 2 autre(s)", l[1])
        assertTrue(l[2].startsWith("Stockage interne : déposez") && "dossier vide" in l[3])
        assertTrue(l[4].startsWith("Clé B1B1-0000 :") && "refuse de lister" in l[5])
        assertEquals(6, l.size)
        val long = DropFolders.lines(listOf(DropFolder("X", ownPath, listOf("a".repeat(100), "b\u0007c"))))[1]
        assertFalse("a".repeat(41) in long); assertFalse('\u0007' in long)
    }

    @Test fun `no key text can appear in a path or name line`() {
        val token = "cbx1.SECRETTOKENVALUE"
        // the lookup never echoes the first line it read, whatever the verdict
        for (v in Verdict.values()) {
            val o = run(Fs(files = mapOf("$ownPath/activation" to "$token\nsecond".toByteArray())), StorageAccess.MISSING, v)
            assertTrue(ActivationLookupReport.lines(o.facts).none { token in it || "SECRET" in it }, v.name)
        }
        assertTrue(DropFolders.lines(listOf(DropFolder("A", ownPath, listOf("activation")))).none { "cbx1" in it })
    }

    // ---- explorer ----
    private class BFs(val tree: Map<String, List<BrowseEntry>?>) : BrowseFs {
        override fun isDir(f: File) = f.path in tree
        override fun list(dir: File) = tree[dir.path]
    }
    private fun d(n: String) = BrowseEntry(n, true, 0)
    private fun f(n: String, s: Long = 594) = BrowseEntry(n, false, s)
    private val roots = listOf(BrowseRoot("Clé USB", File(key), "A379-E209"))
    private val tree = mapOf<String, List<BrowseEntry>?>(
        key to listOf(d("Download"), d("Android")),
        dl.path to emptyList(),
        "$key/Android" to listOf(d("data")),
        ownPath to listOf(f("zz.txt"), f("activation_2026_unlimited.txt"), d("CastBridge"), f("activation.apk"), f("device-request.txt")),
    )
    private fun browser(access: StorageAccess, screen: Boolean = true) = FileBrowser(roots, BFs(tree), access = access, settingsScreen = screen, ownPath = ownPath)

    @Test fun `permission row is first and unmissable when missing and the settings screen exists`() {
        val b = browser(StorageAccess.MISSING); b.startAt(emptyList())
        val r = b.view().rows
        assertEquals(AccessTexts.ASK_ALL, r[0].label); assertTrue(r[0].action && r[0].selectable)
        assertTrue(b.open(0) is BrowseAction.AskAccess)
        assertTrue(b.open(1) is BrowseAction.Redraw && b.current!!.path == key, "row 1 is the first volume")
        assertEquals(AccessTexts.ASK_ALL, b.view().rows[0].label, "also inside a folder")
    }

    @Test fun `without the settings screen the row is an explanation and no dead button exists`() {
        val b = browser(StorageAccess.MISSING, screen = false); b.startAt(emptyList())
        val r = b.view().rows[0]
        assertEquals("Ce boîtier n'a pas l'écran d'autorisation : utilisez le téléphone (Bluetooth) ou le dossier de l'application", r.label)
        assertTrue(r.info && !r.selectable && !r.action)
        assertTrue(b.open(0) is BrowseAction.Redraw)
        assertTrue(b.view().rows.drop(1).none { it.action })
    }

    @Test fun `no permission row when access is granted`() {
        val b = browser(StorageAccess.GRANTED); b.startAt(emptyList())
        assertEquals(listOf("Clé USB A379-E209"), b.view().rows.map { it.label })
        assertTrue(b.open(0) is BrowseAction.Redraw)
    }

    @Test fun `own folder comes first as start place when the permission is missing`() {
        val places = ExplorerStart.places(roots, listOf(own), StorageAccess.MISSING)
        assertEquals(own.path, places.first().path)
        assertEquals(dl.path + "/CastBridge", ExplorerStart.places(roots, listOf(own), StorageAccess.GRANTED).first().path)
        val b = browser(StorageAccess.MISSING); assertTrue(b.startAt(places))
        assertEquals("Clé USB A379-E209/Android/data/castbridge.receiver/files", b.view().title)
    }

    @Test fun `activation files are offered first even with an extension, binaries never`() {
        val b = browser(StorageAccess.MISSING); b.startAt(listOf(own))
        val labels = b.view().rows.drop(1).map { it.label }
        assertEquals("activation_2026_unlimited.txt", labels[0])
        assertEquals(listOf("CastBridge/", "activation.apk", "device-request.txt", "zz.txt"), labels.drop(1))
    }

    @Test fun `an empty Download with the permission missing explains the permission`() {
        val b = browser(StorageAccess.MISSING); b.startAt(listOf(dl))
        assertEquals(missingText + ownPath, b.view().notice)
        val c = browser(StorageAccess.GRANTED); c.startAt(listOf(dl))
        assertEquals("Dossier vide", c.view().notice)
        assertNotEquals(missingText + ownPath, c.view().notice)
    }
}
