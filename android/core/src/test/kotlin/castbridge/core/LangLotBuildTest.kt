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

    @Test fun builtLotParsesWithTheConsumerAndIsDeterministic() {
        // update = true with the registry's own entry: the content hash is recomputed, the version is kept when the content did not change
        val r = LangLotBuilder.build(content, registry, "2026-10-02", update = false)
        val b = r.lots.single { it.meta.id.scope == scope }
        assertEquals("castbridge-lot-langues-$scope-v${b.meta.version}.lot", b.file)
        assertTrue(b.bytes.size < LangLotBuilder.MAX_TEXT_LOT_BYTES, "lot texte ≤ 3 Mo")
        assertContentEquals(b.bytes, LangLotBuilder.build(content, registry, "2026-10-03", update = false).lots.single().bytes)
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
        assertEquals(1, LangLotBuilder.build(content, reg, "2026-10-02", update = false).lots.count { it.family == "free" })
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
        assertEquals(listOf(scope), s.packs.map { it.id })
        assertTrue(s.bytes() in 1..(1L shl 20), "starter < 1 Mo (${s.bytes()} octets)")
        assertEquals(listOf(Lang.ZH), LangCatalog.languages(s.packs))
    }

    @Test fun installedLotOverridesStarterByVersion() {
        val s = EmbeddedLangSource().packs
        val newer = s.map { it.copy(version = it.version + 1, title = "lot") }
        val older = s.map { it.copy(version = 0, title = "vieux") }
        assertEquals("lot", EmbeddedLangSource.merge(s, newer).single().title)
        assertEquals(s.single().title, EmbeddedLangSource.merge(s, older).single().title)
        assertEquals(s, EmbeddedLangSource.merge(s, emptyList()))
        assertTrue(EmbeddedLangSource.merge(emptyList(), emptyList()).isEmpty())
    }
}
