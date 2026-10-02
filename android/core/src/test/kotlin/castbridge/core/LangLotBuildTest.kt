package castbridge.core

import castbridge.core.langues.*
import castbridge.core.lots.LotFamily
import castbridge.core.lots.LotId
import java.io.File
import kotlin.test.*

/** Langues lots build + free starter (docs/LANGUES.md § 14). */
class LangLotBuildTest {
    private val content = File(System.getProperty("learn.content") ?: "../../content/learn").parentFile.resolve("langues")
    private val registry get() = LangLotRegistry.parse(content.resolve("lots.json").readText())
    private val scope = "zh-a0-salut-fr"
    private val embeddedPacks by lazy {
        content.resolve("embedded.txt").readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .sorted()
    }

    @Test fun aFreeLotCarriesTheCcBySaLicenceFieldTheServerRequires() {
        val r = LangLotBuilder.build(content, registry, "2026-10-02", update = false)
        val b = r.lots.first { it.family == "free" }
        val json = java.util.zip.ZipInputStream(b.bytes.inputStream()).let { z -> generateSequence { z.nextEntry }.first { it.name == "langue.json" }.let { String(z.readBytes()) } }
        assertEquals("CC-BY-SA-4.0", castbridge.core.quiz.Json.obj(json)["license"])
        assertEquals("CC-BY-SA-4.0", LangLotBuilder.LICENSE_TAG)
        // the tagged lot still passes the TV's own checks
        val dir = createTempDir(); val f = File(dir, b.file).also { it.writeBytes(b.bytes) }
        assertTrue(LangLotConsumer.verifyContent(b.meta, f) is LangLotConsumer.Companion.Verified.Ok)
        // the untagged pack folder does not carry it
        assertFalse(String(LangLotBuilder.zip(content.resolve(b.meta.id.scope)).let { z -> java.util.zip.ZipInputStream(z.inputStream()).let { s -> s.nextEntry; s.readBytes() } }).contains("\"license\": \"CC-BY-SA-4.0\","))
    }

    @Test fun builtLotParsesWithTheConsumerAndIsDeterministic() {
        // update = true with the registry's own entry: the content hash is recomputed, the version is kept when the content did not change
        val r = LangLotBuilder.build(content, registry, "2026-10-02", update = false)
        val b = r.lots.single { it.meta.id.scope == scope }
        assertEquals("castbridge-lot-langues-$scope-v${b.meta.version}.lot", b.file)
        assertTrue(b.bytes.size < LangLotBuilder.MAX_TEXT_LOT_BYTES, "lot texte ≤ 3 Mo")
        assertContentEquals(b.bytes, LangLotBuilder.build(content, registry, "2026-10-03", update = false).lots.single { it.meta.id.scope == scope }.bytes)
        val dir = createTempDir(); val lotFile = File(dir, b.file).also { it.writeBytes(b.bytes) }
        val consumer = LangLotConsumer(File(dir, "store"))
        assertTrue(consumer.install(b.meta, lotFile), consumer.lastError)
        assertEquals(listOf(LotId("langues", scope)), consumer.installed().map { it.id })
        assertEquals(scope, consumer.packs().single().id)
        dir.deleteRecursively()
    }

    @Test fun lotIsFreeNeverReserved() {
        val reg = registry
        assertEquals("free", reg.familyOf(scope))
        assertEquals(LotFamily.FREE, reg.families().of(LotId("langues", scope)))
        assertNull(reg.families().of(LotId("langues", "zh-a1-salut-fr")), "unknown family fails closed")
        assertFailsWith<IllegalArgumentException> { LangLotRegistry.parse("""{"free":["langues:a"],"reserved":["langues:a"]}""") }
        val built = LangLotBuilder.build(content, reg, "2026-10-02", update = false)
        val freeScopes = built.lots.filter { it.family == "free" }.map { it.meta.id.scope }.toSet()
        assertTrue(embeddedPacks.all { it in freeScopes }, "tous les packs embarqués doivent être libres (embarqué ⊆ libre)")
        for (lot in built.lots) {
            assertEquals("free", lot.family)
            assertTrue(reg.familyOf(lot.meta.id.scope) == "free", "${lot.meta.id.scope} should be free")
        }
    }

    @Test fun catalogHasTheLearnShapeAndIsUnsigned() {
        val b = LangLotBuilder.build(content, registry, "2026-10-02", update = false)
        val metas = castbridge.core.learn.LearnLotCatalogFile.parse(LangLotBuilder.catalogJson(b.lots))
        assertEquals(b.lots.map { it.meta.copy(edition = it.meta.edition) }, metas)
        assertTrue(LangLotBuilder.catalogJson(b.lots).contains("\"UNSIGNED\""))
    }

    @Test fun changedContentNeedsUpdate() {
        val stale = LangLotRegistry(registry.free, registry.reserved, mapOf(scope to LangLotRegistry.Entry(1, "0".repeat(32), "2026-01-01")))
        assertFailsWith<LangLotBuilder.Failure> { LangLotBuilder.build(content, stale, "2026-10-02", update = false) }
    }

    @Test fun starterListsZhA0AndStaysSmall() {
        val s = EmbeddedLangSource()
        assertEquals(embeddedPacks, s.packs.map { it.id }.sorted(), "embedded starter should list all free packs in embedded.txt")
        assertTrue(s.bytes() in 1..(1L shl 20), "starter < 1 Mo (${s.bytes()} octets)")
        assertEquals(listOf(Lang.ZH, Lang.JA), LangCatalog.languages(s.packs).sorted())
    }

    @Test fun installedLotOverridesStarterByVersion() {
        val s = EmbeddedLangSource().packs
        val testPack = s.single { it.id == scope }
        val newer = s.map { if (it.id == scope) it.copy(version = it.version + 1, title = "lot") else it }
        val older = s.map { if (it.id == scope) it.copy(version = 0, title = "vieux") else it }
        assertEquals("lot", EmbeddedLangSource.merge(s, newer).single { it.id == scope }.title)
        assertEquals(testPack.title, EmbeddedLangSource.merge(s, older).single { it.id == scope }.title)
        assertEquals(s.map { it.id }.sorted(), EmbeddedLangSource.merge(s, emptyList()).map { it.id }.sorted(), "merge with empty list should preserve starter")
        assertTrue(EmbeddedLangSource.merge(emptyList(), emptyList()).isEmpty())
    }
}
