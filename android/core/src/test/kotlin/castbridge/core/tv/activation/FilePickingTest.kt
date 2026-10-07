package castbridge.core.tv.activation

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Chooser plan and built-in explorer model (docs/TV-ACTIVATION-CLE-USB.md): a poor box has no system file picker, the owner chooses the file himself, whatever its name. */
class FilePickingTest {
    private fun bytes(s: String) = PickInput.Bytes(s.toByteArray())

    @Test fun `built-in explorer is always first and the system picker only when it exists`() {
        assertEquals(listOf(PickerPlan.Chooser.BUILT_IN), PickerPlan.choosers(false))
        assertEquals(listOf(PickerPlan.Chooser.BUILT_IN, PickerPlan.Chooser.SYSTEM), PickerPlan.choosers(true))
        assertTrue("n'a pas d'explorateur de fichiers système" in PickerPlan.systemPickerMissing() && "téléphone (Bluetooth)" in PickerPlan.systemPickerMissing() && "collez la clé" in PickerPlan.systemPickerMissing())
    }

    @Test fun `initial uri is built only from a plain volume id`() {
        assertEquals("content://com.android.externalstorage.documents/document/A379-E209%3ADownload", PickerPlan.initialUri("A379-E209"))
        for (bad in listOf(null, "", "../x", "A379/E209", "a b", "x%3Ay", "A".repeat(40))) assertNull(PickerPlan.initialUri(bad), bad.toString())
    }

    @Test fun `decision table on a chosen file`() {
        val big = PickInput.Bytes(ByteArray(PickerPlan.MAX_BYTES + 1) { 'a'.code.toByte() })
        val exact = PickInput.Bytes(("cbx1.A.B\n" + "a".repeat(PickerPlan.MAX_BYTES - 9)).toByteArray())
        val cases: List<Triple<String, PickInput, Class<*>>> = listOf(
            Triple("cancelled", PickInput.Cancelled, PickResult.Cancelled::class.java),
            Triple("nothing", PickInput.Nothing, PickResult.Nothing::class.java),
            Triple("unreadable", PickInput.Unreadable, PickResult.Unreadable::class.java),
            Triple("empty", PickInput.Bytes(ByteArray(0)), PickResult.Empty::class.java),
            Triple("blank", bytes(" \r\n \n"), PickResult.Empty::class.java),
            Triple("too big", big, PickResult.TooBig::class.java),
            Triple("exact limit is text", exact, PickResult.Keys::class.java),
            Triple("binary NUL", PickInput.Bytes(byteArrayOf(0x50, 0x4b, 0, 3, 4)), PickResult.NotText::class.java),
            Triple("invalid utf8", PickInput.Bytes(byteArrayOf(0xff.toByte(), 0xfe.toByte(), 0x41)), PickResult.NotText::class.java),
            Triple("control chars", PickInput.Bytes(byteArrayOf(0x41, 0x07, 0x42)), PickResult.NotText::class.java),
            Triple("key", bytes("cbx1.AAAA.BBBB\n"), PickResult.Keys::class.java),
            Triple("bom crlf", PickInput.Bytes("\uFEFFcbx1.AAAA.C\r\nsecond".toByteArray()), PickResult.Keys::class.java),
            Triple("text without a key", bytes("Bonjour\nvoici la liste des courses\n"), PickResult.NoKey::class.java),
        )
        for ((n, i, c) in cases) assertEquals(c, PickerPlan.decide(i)::class.java, n)
        assertEquals(listOf("cbx1.AAAA.C"), (PickerPlan.decide(PickInput.Bytes("\uFEFFcbx1.AAAA.C\r\nsecond".toByteArray())) as PickResult.Keys).candidates)
        assertEquals("Aucun fichier choisi", PickerPlan.decide(PickInput.Cancelled).message)
        assertTrue("Aucune clé d'activation" in PickerPlan.decide(bytes("Bonjour")).message)
        assertTrue("256 Kio" in PickerPlan.decide(big).message)
    }

    @Test fun `the key is searched in the whole file, not only on its first line`() {
        val text = "Objet : votre clé CastBridge\nBonjour,\n\nla voici :\n    cbx1.PAYLOAD.SIG==\n\nCordialement\n"
        val d = PickerPlan.decide(bytes(text))
        assertEquals(listOf("cbx1.PAYLOAD.SIG=="), (d as PickResult.Keys).candidates)
    }

    @Test fun `file name never matters for a chosen file`() {
        // the decision only sees bytes: a file called activation.txt or activation (1) is as good as activation
        assertTrue(PickerPlan.decide(bytes("cbx1.KEY.S")) is PickResult.Keys)
    }

    @Test fun `verdict texts name the cause`() {
        assertTrue("autre TV" in PickerPlan.verdictText(Verdict.WRONG_DEVICE))
        assertTrue("périmée" in PickerPlan.verdictText(Verdict.EXPIRED))
        assertTrue("pas une activation" in PickerPlan.verdictText(Verdict.NOT_VALID))
        assertTrue("vérification" in PickerPlan.verdictText(Verdict.ACCEPTED))
        assertTrue("déjà installée" in PickerPlan.verdictText(Verdict.INSTALLED))
    }

    // ---- built-in explorer ----
    private class FakeFs(val tree: Map<String, List<BrowseEntry>?>) : LookupFsLike()
    private open class LookupFsLike
    private class Fs(val tree: Map<String, List<BrowseEntry>?>) : BrowseFs {
        override fun isDir(f: File) = f.path in tree
        override fun list(dir: File) = tree[dir.path]
    }
    private fun d(n: String) = BrowseEntry(n, true, 0)
    private fun f(n: String, s: Long = 100) = BrowseEntry(n, false, s)
    private val usb = "/storage/A379-E209"
    private val roots = listOf(BrowseRoot("Stockage interne", File("/storage/emulated/0"), null), BrowseRoot("Clé USB", File(usb), "A379-E209"))
    private val tree = mapOf<String, List<BrowseEntry>?>(
        "/storage/emulated/0" to listOf(d("Download")),
        "/storage/emulated/0/Download" to listOf(f("a.txt")),
        usb to listOf(f("zeta.txt"), d("Download"), d("Android"), f("big.txt", 300_000), f("Alpha.txt", 10), f("film.mp4", 10), f("vide.txt", 0), f(".cle", 10)),
        "$usb/Download" to listOf(d("CastBridge"), f("activation (1)")),
        "$usb/Download/CastBridge" to emptyList(),
        "$usb/Android" to listOf(d("data")),
        "$usb/Android/data" to null,
    )
    private fun browser() = FileBrowser(roots, Fs(tree))

    @Test fun `root lists volumes with name and id`() {
        val v = browser().apply { startAt(emptyList()) }.view()
        assertEquals(listOf("Stockage interne", "Clé USB A379-E209"), v.rows.map { it.label })
        assertTrue(v.rows.all { it.isDir && it.selectable })
    }

    @Test fun `every file is listed - folders, then choosable files, then greyed ones with their reason`() {
        val b = browser(); b.startAt(emptyList()); b.open(1)
        val rows = b.view().rows
        assertEquals(listOf("Android/", "Download/", ".cle", "Alpha.txt", "zeta.txt", "big.txt", "film.mp4", "vide.txt"), rows.map { it.label })
        assertEquals(listOf(true, true, true, true, true, false, false, false), rows.map { it.selectable })
        assertEquals(listOf(null, null, null, null, null, "trop gros", "pas du texte", "vide"), rows.map { it.reason })
    }

    @Test fun `a file is greyed only for a real reason`() {
        assertNull(FileKinds.refusal("note", 10, 1000)); assertNull(FileKinds.refusal("cle.TXT", 1000, 1000)); assertNull(FileKinds.refusal("mail.eml", 10, 1000))
        assertEquals("trop gros", FileKinds.refusal("cle.txt", 1001, 1000))
        assertEquals("vide", FileKinds.refusal("cle.txt", 0, 1000))
        for (n in listOf("a.MP4", "b.jpg", "c.apk", "d.zip", "e.pdf", "f.mkv", "g.png", "h.mp3", "i.7z", "j.docx")) assertEquals("pas du texte", FileKinds.refusal(n, 10, 1000), n)
    }

    @Test fun `start at the first useful place that exists`() {
        val b = browser()
        assertTrue(b.startAt(listOf(File("$usb/Download/CastBridge/missing"), File("$usb/Download/CastBridge"), File("$usb/Download"))))
        assertEquals("Clé USB A379-E209/Download/CastBridge", b.view().title)
        assertEquals("Dossier vide", b.view().notice)
        // a place on no volume or a refused one is skipped
        val c = browser()
        assertTrue(c.startAt(listOf(File("/nowhere/x"), File("$usb/Android/data"), File("$usb/Download"))))
        assertEquals("Clé USB A379-E209/Download", c.view().title)
        assertFalse(browser().startAt(listOf(File("/nowhere"))))
    }

    @Test fun `back goes to the parent then the volumes then quits and never above a root`() {
        val b = browser(); b.startAt(listOf(File("$usb/Download/CastBridge")))
        assertEquals(3, b.depth)
        assertTrue(b.back() is BrowseAction.Redraw); assertEquals("$usb/Download", b.current!!.path)
        assertTrue(b.back() is BrowseAction.Redraw); assertEquals(usb, b.current!!.path)
        assertTrue(b.back() is BrowseAction.Redraw); assertNull(b.current)                   // volume list, not /storage
        assertEquals(listOf("Stockage interne", "Clé USB A379-E209"), b.view().rows.map { it.label })
        assertTrue(b.back() is BrowseAction.Quit)
        assertTrue(b.back() is BrowseAction.Quit)
    }

    @Test fun `selection table`() {
        val b = browser(); b.startAt(emptyList()); b.open(1)
        assertTrue(b.open(0) is BrowseAction.Redraw)                                         // Android/ is entered, not picked
        assertEquals("$usb/Android", b.current!!.path)
        b.back()
        val rows = b.view().rows.map { it.label }
        val picked = b.open(rows.indexOf("Alpha.txt")) as BrowseAction.Picked
        assertEquals("$usb/Alpha.txt", picked.file.path)
        val refused = b.open(rows.indexOf("big.txt")) as BrowseAction.Refused
        assertTrue("trop gros" in refused.message && "256 Kio" in refused.message, refused.message)
        assertTrue("pas du texte" in (b.open(rows.indexOf("film.mp4")) as BrowseAction.Refused).message)
        assertTrue("vide" in (b.open(rows.indexOf("vide.txt")) as BrowseAction.Refused).message)
        assertTrue(b.open(99) is BrowseAction.Redraw)
        // a folder is never returned as a chosen file
        for (i in rows.indices) if (rows[i].endsWith("/")) assertTrue(b.open(i) !is BrowseAction.Picked)
    }

    @Test fun `a refused listing is said with its cause and is not an empty folder`() {
        val b = browser(); b.startAt(emptyList()); b.open(1)
        b.open(b.view().rows.indexOfFirst { it.label == "Android/" }); b.open(0)             // Android/data: listing refused
        val v = b.view()
        assertTrue(v.rows.isEmpty())
        assertTrue("permission de stockage" in v.notice.orEmpty() && "castbridge.receiver/files" in v.notice.orEmpty(), v.notice)
        assertFalse(v.notice == "Dossier vide")
        // a listing that throws is the same refusal
        val thrower = FileBrowser(roots, object : BrowseFs { override fun isDir(f: File) = true; override fun list(dir: File): List<BrowseEntry>? = if (dir.path == usb) throw SecurityException("no") else null })
        thrower.open(1)
        assertTrue("permission de stockage" in thrower.view().notice.orEmpty())
    }

    @Test fun `explorer is read-only on a real folder`() {
        val root = java.nio.file.Files.createTempDirectory("browse").toFile()
        try {
            File(root, "sub").mkdirs(); File(root, "sub/activation").writeText("K\n"); File(root, "note.txt").writeText("x")
            val before = root.walkTopDown().map { it.relativeTo(root).path to it.lastModified() }.toSet()
            val b = FileBrowser(listOf(BrowseRoot("Test", root, "T001")))
            b.open(0); b.open(0); assertTrue(b.view().rows.any { it.label == "activation" })
            b.back(); b.back(); b.back()
            assertEquals(before, root.walkTopDown().map { it.relativeTo(root).path to it.lastModified() }.toSet())
        } finally { root.deleteRecursively() }
    }

    @Test fun `the screen never keeps the right to read a chosen file`() {
        val src = File("../receiver/src/main/kotlin/castbridge/receiver")
        val files = src.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue(files.any { it.name == "ActivationActivity.kt" }, "sources not found from ${File(".").absolutePath}")
        for (f in files.filter { it.name in setOf("ActivationActivity.kt", "FilePickActivity.kt", "ActivationCenter.kt") }) assertFalse("takePersistableUriPermission" in f.readText(), f.name)
        for (f in files.filter { it.name in setOf("ActivationActivity.kt", "FilePickActivity.kt") }) assertFalse(Regex("FileOutputStream|writeText|writeBytes|delete\\(").containsMatchIn(f.readText().replace(Regex("//.*"), "")) && f.name == "FilePickActivity.kt", f.name)
    }

    @Test fun `a folder with more entries than the screen holds says how many are left`() {
        val many = (1..FileBrowser.MAX_ROWS + 7).map { f("f%05d.txt".format(it)) }
        val b = FileBrowser(roots, Fs(mapOf(usb to many)))
        b.startAt(listOf(File(usb)))
        val rows = b.view().rows
        assertEquals(FileBrowser.MAX_ROWS + 1, rows.size)
        assertTrue(rows.last().info && !rows.last().selectable && "7 autres" in rows.last().label, rows.last().label)
    }

    @Test fun `where Android hides files the explorer says so on top and offers both ways out`() {
        val b = FileBrowser(roots, Fs(tree), access = StorageAccess.MISSING, settingsScreen = true, systemPicker = true, ownPath = "$usb/Android/data/castbridge.receiver/files")
        b.startAt(listOf(File("$usb/Download")))
        val v = b.view()
        assertTrue("cache" in v.notice.orEmpty() && "tous les fichiers" in v.notice.orEmpty() && "explorateur du système" in v.notice.orEmpty(), v.notice)
        assertEquals(listOf(AccessTexts.ASK_ALL, AccessTexts.SYSTEM), v.rows.take(2).map { it.label })
        assertTrue(b.open(0) is BrowseAction.AskAccess)
        assertTrue(b.open(1) is BrowseAction.SystemPicker)
        assertTrue(v.rows.drop(2).any { it.label == "activation (1)" }, "the files Android does show are still listed")
        // with the permission: no notice, no extra row
        val g = FileBrowser(roots, Fs(tree), access = StorageAccess.GRANTED, systemPicker = true); g.startAt(listOf(File("$usb/Download")))
        assertNull(g.view().notice); assertTrue(g.view().rows.none { it.action })
        // no system picker on this box: never a dead row
        val n = FileBrowser(roots, Fs(tree), access = StorageAccess.MISSING, settingsScreen = true, systemPicker = false); n.startAt(listOf(File("$usb/Download")))
        assertTrue(n.view().rows.none { it.label == AccessTexts.SYSTEM })
        assertTrue("explorateur du système" !in n.view().notice.orEmpty())
    }
}
