package castbridge.core.tv.activation

import java.io.File
import java.io.FileNotFoundException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Lookup of the `activation` file on the USB key: what is searched, what each outcome is called, what the owner is told (docs/TV-ACTIVATION-CLE-USB.md). */
class ActivationLookupTest {
    private val key = "/storage/A379-E209"
    private val dl = File("$key/Download")
    private val own = File("$key/Android/data/castbridge.receiver/files")
    private val vols = listOf(VolumeFact("A379-E209", false))

    /** Files by path: ByteArray = readable, Exception = refused, absent = FileNotFoundException(ENOENT). Dirs by path: names or null (denied); missing = not a dir. */
    private class FakeFs(val files: Map<String, Any> = emptyMap(), val dirs: Map<String, List<String>?> = emptyMap(), val sizes: Map<String, Long> = emptyMap()) : LookupFs {
        val reads = ArrayList<String>()
        override fun isDir(f: File) = f.path in dirs
        override fun list(dir: File) = dirs[dir.path]
        override fun length(f: File): Long = sizes[f.path] ?: (files[f.path] as? ByteArray)?.size?.toLong() ?: -1
        override fun read(f: File, max: Int): ByteArray {
            reads += f.path
            return when (val v = files[f.path]) {
                null -> throw FileNotFoundException("${f.path}: open failed: ENOENT (No such file or directory)")
                is Exception -> throw v
                else -> (v as ByteArray).copyOf(minOf(max, v.size))
            }
        }
    }

    private fun cands() = ActivationLookup.candidates(listOf(dl to "A379-E209"), listOf(own to "A379-E209"))
    private fun dirs() = ActivationLookup.dirsToList(listOf(dl to "A379-E209"), listOf(own to "A379-E209"))
    private fun run(fs: LookupFs, verdict: Verdict = Verdict.ACCEPTED, seen: MutableList<String>? = null) =
        ActivationLookup.run(cands(), dirs(), vols, fs) { seen?.add(it); verdict }
    private fun text(s: String) = s.toByteArray()

    @Test fun `candidates keep the Download ones and add the own folder of every volume`() {
        val c = cands().map { it.file.path.removePrefix(key + "/") }
        assertEquals(listOf("Download/CastBridge/activation", "Download/activation", "Android/data/castbridge.receiver/files/activation", "Android/data/castbridge.receiver/files/CastBridge/activation"), c)
        assertEquals(listOf(Place.DOWNLOAD, Place.DOWNLOAD, Place.OWN_DIR, Place.OWN_DIR), cands().map { it.place })
        val two = ActivationLookup.candidates(listOf(dl to "A", File("/x/Download") to null), listOf(own to "A", File("/y/files") to "B"))
        assertEquals(8, two.size)
        assertEquals(two.size, two.map { it.file.path }.toSet().size)
    }

    @Test fun `probe states table`() {
        val own1 = "${own.path}/activation"
        val cases: List<Triple<String, FakeFs, Probe>> = listOf(
            Triple("absent", FakeFs(), Probe.ABSENT),
            Triple("security exception", FakeFs(files = mapOf(own1 to SecurityException("denied"))), Probe.UNREADABLE),
            Triple("EACCES", FakeFs(files = mapOf(own1 to FileNotFoundException("$own1: open failed: EACCES (Permission denied)"))), Probe.UNREADABLE),
            Triple("io error", FakeFs(files = mapOf(own1 to java.io.IOException("I/O error"))), Probe.UNREADABLE),
            Triple("empty", FakeFs(files = mapOf(own1 to text("  \r\n\n "))), Probe.EMPTY),
            Triple("too big by length", FakeFs(files = mapOf(own1 to text("x")), sizes = mapOf(own1 to 20_000L)), Probe.TOO_BIG),
            Triple("too big by content", FakeFs(files = mapOf(own1 to ByteArray(20_000) { 'a'.code.toByte() })), Probe.TOO_BIG),
            Triple("readable", FakeFs(files = mapOf(own1 to text("TOKEN"))), Probe.NOT_VALID),
        )
        for ((name, fs, expected) in cases) {
            val o = run(fs, Verdict.NOT_VALID)
            val p = o.facts.probes.first { it.place == Place.OWN_DIR }.probe
            assertEquals(expected, p, name)
        }
    }

    @Test fun `verdicts map to probes and only accepted stops the walk`() {
        val f = "${own.path}/activation"
        for ((v, p) in listOf(Verdict.ACCEPTED to Probe.ACCEPTED, Verdict.NOT_VALID to Probe.NOT_VALID, Verdict.WRONG_DEVICE to Probe.WRONG_DEVICE, Verdict.EXPIRED to Probe.EXPIRED)) {
            val o = run(FakeFs(files = mapOf(f to text("T"))), v)
            assertEquals(p, o.facts.probes.first { it.probe != Probe.ABSENT }.probe, v.name)
            assertEquals(v == Verdict.ACCEPTED, o.accepted)
        }
    }

    @Test fun `own folder file is found when Download is unreadable`() {
        val seen = ArrayList<String>()
        val fs = FakeFs(files = mapOf("${dl.path}/CastBridge/activation" to SecurityException("scoped"), "${own.path}/activation" to text("﻿GOOD-LINE\r\nsecond")))
        val o = run(fs, seen = seen)
        assertTrue(o.accepted)
        assertEquals(listOf("GOOD-LINE"), seen)                       // BOM and CRLF tolerated, only the first line is handed over
        assertEquals(listOf(Probe.UNREADABLE, Probe.ABSENT, Probe.ACCEPTED), o.facts.probes.map { it.probe })
    }

    @Test fun `a refused file does not hide a later good one but is reported if none is good`() {
        val a = "${dl.path}/CastBridge/activation"; val b = "${own.path}/CastBridge/activation"
        val fs = FakeFs(files = mapOf(a to text("OLD"), b to text("NEW")))
        val o = ActivationLookup.run(cands(), dirs(), vols, fs) { if (it == "NEW") Verdict.ACCEPTED else Verdict.EXPIRED }
        assertTrue(o.accepted)
        val none = ActivationLookup.run(cands(), dirs(), vols, fs) { Verdict.EXPIRED }
        assertFalse(none.accepted); assertTrue(none.decided)
        assertTrue(ActivationLookupReport.lines(none.facts).any { "périmée" in it })
    }

    @Test fun `report lines table`() {
        fun f(probes: List<Probe> = listOf(Probe.ABSENT), v: List<VolumeFact> = vols, dirs: List<DirFact> = emptyList()) =
            LookupFacts(v, probes.map { ProbeFact(Place.OWN_DIR, "A379-E209", it) }, dirs)
        val dlDir = DirFact(Place.DOWNLOAD, "A379-E209", true, listOf("a", "b", "c"))
        val ownDir = DirFact(Place.OWN_DIR, "A379-E209", true, listOf())
        val cases = listOf(
            Triple("no key", f(v = emptyList()), "Aucune clé USB détectée"),
            Triple("key id", f(), "Clé détectée : A379-E209"),
            Triple("read only", f(v = listOf(VolumeFact("A379-E209", true))), "A379-E209 (lecture seule)"),
            Triple("several", f(v = listOf("A", "B", "C", "D", "E").map { VolumeFact(it, false) }), "Clés détectées : A, B, C et 2 autre(s)"),
            Triple("count", f(dirs = listOf(dlDir)), "dossier Download/CastBridge : 3 éléments visibles"),
            Triple("zero", f(dirs = listOf(ownDir)), "dossier Android/data/castbridge.receiver/files : aucun élément visible"),
            Triple("denied", f(dirs = listOf(DirFact(Place.DOWNLOAD, "A379-E209", true, null))), "Android refuse de lister"),
            Triple("absent", f(), "introuvable (Android ne laisse pas CastBridge-TV lire un fichier déposé dans Download : déposez-le ici : Android/data/castbridge.receiver/files/activation, ou utilisez le téléphone)"),
            Triple("unreadable", f(listOf(Probe.UNREADABLE)), "Android refuse de le lire"),
            Triple("empty", f(listOf(Probe.EMPTY)), "vide"),
            Triple("big", f(listOf(Probe.TOO_BIG)), "trop gros"),
            Triple("invalid", f(listOf(Probe.NOT_VALID)), "pas une clé valable"),
            Triple("wrong device", f(listOf(Probe.WRONG_DEVICE)), "celle d'une autre TV"),
            Triple("expired", f(listOf(Probe.EXPIRED)), "périmée"),
            Triple("accepted", f(listOf(Probe.ACCEPTED)), "vérification de la clé"),
        )
        for ((n, facts, needle) in cases) assertTrue(ActivationLookupReport.lines(facts).any { needle in it }, "$n: ${ActivationLookupReport.lines(facts)}")
    }

    @Test fun `classic naming mistakes are spotted by name`() {
        val cases = listOf(
            "activation.txt" to "Fichier trouvé sous le nom « activation.txt » : renommez-le « activation »",
            "Activation" to "Fichier trouvé sous le nom « Activation » : renommez-le « activation »",
            "activation (1)" to "Fichier trouvé sous le nom « activation (1) » : renommez-le « activation »",
            "activation.zip" to "décompressez-le",
            "ACTIVATION.TXT" to "renommez-le « activation »",
            "activation.txt.txt" to "renommez-le « activation »",
        )
        for ((name, needle) in cases) assertTrue(ActivationLookupReport.misnamed(listOf("video.mp4", name)).any { needle in it }, name)
        assertEquals(emptyList(), ActivationLookupReport.misnamed(listOf("activation", "device-request.txt", "film.mp4")))
        // through the whole pipeline: names come from File.list() of the candidate folders
        val fs = FakeFs(dirs = mapOf("${dl.path}/CastBridge" to listOf("activation.txt"), "${own.path}" to listOf("device-request.txt")))
        val lines = ActivationLookupReport.lines(run(fs).facts)
        assertTrue(lines.any { "« activation.txt » : renommez-le" in it }, lines.toString())
        // listing denied is said, never guessed
        val denied = ActivationLookupReport.lines(run(FakeFs(dirs = mapOf("${dl.path}/CastBridge" to null))).facts)
        assertTrue(denied.any { "refuse de lister" in it }, denied.toString())
    }

    @Test fun `no secret or odd name is ever echoed and the report is short`() {
        val secret = "SECRETTOKEN-AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        val fs = FakeFs(
            files = mapOf("${own.path}/activation" to text(secret)),
            dirs = mapOf("${own.path}" to listOf("activation $secret", "activation.txt", "activation.zip", "Activation (2)", "activation - copie", "activation copy.txt"), "${dl.path}/CastBridge" to (1..40).map { "f$it" }))
        for (v in Verdict.values()) {
            val lines = ActivationLookupReport.lines(run(fs, v).facts)
            assertTrue(lines.size <= ActivationLookupReport.MAX_LINES, lines.toString())
            assertFalse(lines.any { "SECRETTOKEN" in it || secret.take(12) in it }, lines.toString())
        }
        val odd = ActivationLookupReport.misnamed(listOf("activation $secret"))
        assertEquals(1, odd.size); assertFalse("SECRET" in odd[0])
        // the many hints are capped at two lines
        val facts = LookupFacts(vols, listOf(ProbeFact(Place.OWN_DIR, "x", Probe.ABSENT)), listOf(DirFact(Place.OWN_DIR, "x", true, listOf("activation.txt", "activation.zip", "Activation", "activation (1)"))))
        assertTrue(ActivationLookupReport.lines(facts).count { "activation" in it && "renommez" in it || "décompressez" in it } <= 2)
    }

    @Test fun `lookup never writes, a read-only key keeps its content and dates`() {
        val root = java.nio.file.Files.createTempDirectory("lookup").toFile()
        try {
            val d = File(root, "files").apply { mkdirs() }
            val f = File(d, "activation").apply { writeText("TOKEN\n") }
            f.setReadOnly()
            val before = d.list()!!.sorted() to listOf(f.lastModified(), d.lastModified())
            val o = ActivationLookup.run(ActivationLookup.candidates(emptyList(), listOf(d to "K")), ActivationLookup.dirsToList(emptyList(), listOf(d to "K")), vols) { Verdict.NOT_VALID }
            assertEquals(Probe.NOT_VALID, o.facts.probes.first().probe)
            d.setReadOnly()
            ActivationLookup.run(ActivationLookup.candidates(emptyList(), listOf(d to "K")), emptyList(), listOf(VolumeFact("K", true))) { Verdict.NOT_VALID }
            assertEquals(before, d.list()!!.sorted() to listOf(f.lastModified(), d.lastModified()))
            assertEquals("TOKEN\n", f.readText())
        } finally { root.walkTopDown().forEach { it.setWritable(true) }; root.deleteRecursively() }
    }

    @Test fun `a different file name is never taken for the key`() {
        // exact name only: a candidate list never contains activation.txt, and a folder holding only that file yields ABSENT plus a hint
        assertTrue(cands().none { it.file.name != "activation" })
        val fs = FakeFs(files = mapOf("${own.path}/activation.txt" to text("TOKEN")), dirs = mapOf(own.path to listOf("activation.txt")))
        val o = run(fs)
        assertFalse(o.accepted); assertFalse(fs.reads.any { it.endsWith("activation.txt") })
        assertTrue(ActivationLookupReport.lines(o.facts).any { "renommez-le" in it })
    }

    @Test fun `screen text gives the three real ways`() {
        val w = castbridge.core.owner.LockedTexts.KEY_WAYS
        assertEquals(3, w.size)
        assertTrue("Activer la TV" in w[0] && "Bluetooth" in w[0])
        assertTrue("« activation »" in w[1] && "Download/CastBridge" in w[1] && ActivationLookup.OWN_DIR_TEXT in w[1])
        assertTrue("champ" in w[2])
    }
}
