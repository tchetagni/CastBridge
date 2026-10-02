package castbridge.core.free

import castbridge.core.lots.LotFamilies
import castbridge.core.lots.LotId
import castbridge.core.quiz.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream
import kotlin.test.*

class FreeContentExporterTest {
    private fun pack(id: String, lic: String?, lot: LotId? = null, vararg f: Pair<String, String>) =
        FreePack(id, 1, lic, "Titre $id", lot, f.map { FreeFile(it.first, it.second.toByteArray()) }.asSequence())
    private fun src(vararg p: FreePack) = object : FreeContentSource { override fun packs() = p.toList() }
    private fun unzip(b: ByteArray): Map<String, ByteArray> {
        val m = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(b)).use { z -> generateSequence { z.nextEntry }.forEach { m[it.name] = z.readBytes() } }
        return m
    }
    private val good = src(
        pack("b", "CC-BY-SA-4.0", null, "langue.json" to "{\"a\":1}", "media/x.txt" to "xx"),
        pack("a", "CC BY-SA 4.0", null, "z.json" to "zz"),
    )

    @Test fun validAndReproducible() {
        val o1 = ByteArrayOutputStream(); val o2 = ByteArrayOutputStream()
        val r = FreeContentExporter.export(good, o1, date = "2026-10-02")
        FreeContentExporter.export(good, o2, date = "2026-10-02")
        assertContentEquals(o1.toByteArray(), o2.toByteArray())
        assertEquals(listOf("a", "b"), r.exported)
        val z = unzip(o1.toByteArray())
        for (n in listOf("LISEZ-MOI.txt", "ATTRIBUTION.md", "LICENCE-CC-BY-SA-4.0.txt", "MANIFEST.json", "a/z.json", "b/langue.json", "b/media/x.txt")) assertTrue(n in z, n)
        @Suppress("UNCHECKED_CAST") val files = Json.obj(String(z["MANIFEST.json"]!!))["files"] as List<Map<String, Any?>>
        assertEquals(3, files.size)
        for (f in files) { val b = z[f["path"]]!!; assertEquals(FreeContentExporter.sha256(b), f["sha256"]); assertEquals(b.size.toLong(), (f["bytes"] as Number).toLong()) }
        assertEquals(r.zipBytes, o1.size().toLong())
        assertFalse("NON-EXPORTES.txt" in z)
    }

    @Test fun refusesNonCcBySaReservedAndUntagged() {
        val reserved = pack("r", "CC-BY-SA-4.0", LotId("langues", "r"), "f" to "1")
        val fam = LotFamilies.explicit(emptySet(), setOf("langues:r"))
        val s = src(good.packs()[0], reserved, pack("u", null, null, "f" to "1"), pack("nc", "CC-BY-NC-SA-4.0", null, "f" to "1"), pack("blank", "  ", null, "f" to "1"))
        val r = FreeContentExporter.export(s, ByteArrayOutputStream(), fam)
        assertEquals(listOf("b"), r.exported)
        assertEquals(setOf(SkipReason.RESERVED, SkipReason.UNTAGGED, SkipReason.OTHER_LICENSE), r.skipped.map { it.reason }.toSet())
        assertEquals("4 contenus non exportés : 2 licence non précisée, 1 autre licence, 1 contenu réservé", r.skippedLine())
        val out = ByteArrayOutputStream(); FreeContentExporter.export(s, out, fam)
        val z = unzip(out.toByteArray())
        assertTrue(String(z["NON-EXPORTES.txt"]!!).contains("licence non précisée"))
        assertTrue(z.keys.none { it.startsWith("r/") || it.startsWith("u/") || it.startsWith("nc/") })
        val e = assertFailsWith<FreeExportRefused> { FreeContentExporter.export(s, ByteArrayOutputStream(), fam, strict = true) }
        assertTrue(e.message!!.startsWith("Export refusé"))
        assertTrue(FreeLicense.isCcBySa("cc_by_sa 4.0") && !FreeLicense.isCcBySa("CC-BY-NC-SA-4.0") && !FreeLicense.isCcBySa("CC-BY-4.0"))
    }

    @Test fun nothingToExportAndUnsafePaths() {
        assertFailsWith<FreeExportEmpty> { FreeContentExporter.export(src(pack("u", null, null, "f" to "1")), ByteArrayOutputStream()) }
        assertFailsWith<FreeExportRefused> { FreeContentExporter.export(src(pack("p", "CC-BY-SA-4.0", null, "../x" to "1")), ByteArrayOutputStream()) }
        assertFailsWith<FreeExportRefused> { FreeContentExporter.export(src(pack("../p", "CC-BY-SA-4.0", null, "x" to "1")), ByteArrayOutputStream()) }
    }

    @Test fun progressMonotonicAndCancellation() {
        val seen = ArrayList<Pair<Long, Long>>()
        FreeContentExporter.export(good, ByteArrayOutputStream(), progress = { d, t -> seen += d to t })
        assertEquals(0L, seen.first().first)
        assertEquals(seen.last().first, seen.last().second)
        assertTrue(seen.zipWithNext().all { (a, b) -> b.first >= a.first && a.second == b.second })
        var n = 0
        assertFailsWith<FreeExportCancelled> { FreeContentExporter.export(good, ByteArrayOutputStream(), cancelled = { ++n > 4 }) }
    }

    @Test fun fileExportWritesAtomicallyAndCancelLeavesNothing() {
        val dir = File(System.getProperty("java.io.tmpdir"), "free-test-${System.nanoTime()}/Download/CastBridge")
        try {
            var n = 0
            assertFailsWith<FreeExportCancelled> { FreeExportFiles.export(good, dir, "2026-10-02", cancelled = { ++n > 5 }) }
            assertEquals(emptyList(), dir.list()!!.toList())
            val d = FreeExportFiles.export(good, dir, "2026-10-02")
            assertEquals("castbridge-contenus-libres-2026-10-02.zip", d.file.name)
            assertEquals(listOf(d.file.name), dir.list()!!.toList())
            assertTrue(d.message().contains(d.file.path) && d.message().contains("Copiez ce fichier sur une clé USB ou envoyez-le à votre téléphone"))
            assertTrue(unzip(d.file.readBytes()).containsKey("MANIFEST.json"))
            // empty source: error, nothing left
            assertFailsWith<FreeExportEmpty> { FreeExportFiles.export(src(), dir, "2026-10-03") }
            assertEquals(listOf(d.file.name), dir.list()!!.toList())
        } finally { dir.parentFile.parentFile.deleteRecursively() }
    }

    @Test fun embeddedSourceExportsOnlyTaggedPacks() {
        val s = EmbeddedFreeSource()
        val packs = s.packs()
        val sel = FreeContentExporter.select(packs, s.families())
        assertTrue(sel.exportable.all { FreeLicense.isCcBySa(it.license) })
        if (sel.exportable.isEmpty()) {
            assertEquals("Aucun contenu libre embarqué dans cette version de CastBridge-TV : rien à exporter.", assertFailsWith<FreeExportEmpty> { FreeContentExporter.export(s, ByteArrayOutputStream(), s.families()) }.message)
        } else {
            val out = ByteArrayOutputStream(); val r = FreeContentExporter.export(s, out, s.families())
            assertEquals(sel.exportable.map { it.id }, r.exported)
            assertTrue(unzip(out.toByteArray()).keys.any { it.startsWith("langues-") })
        }
        // Learn base packs carry no licence tag today: they are listed, never exported
        assertTrue(sel.skipped.all { it.reason == SkipReason.UNTAGGED || it.reason == SkipReason.OTHER_LICENSE || it.reason == SkipReason.RESERVED })
    }
}
