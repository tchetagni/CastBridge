package castbridge.core

import castbridge.core.langues.*
import castbridge.core.lots.LotHash
import castbridge.core.lots.LotId
import castbridge.core.lots.LotMeta
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.*

/** The TV's « Langues » lot consumer (pure part): atomic install, refusals, versions, catalog. */
class LangLotConsumerTest {
    private val content = File(System.getProperty("learn.content") ?: "../../content/learn").parentFile.resolve("langues/zh-a0-salut-fr")
    private val realVersion by lazy {
        val json = content.resolve("langue.json").readText()
        val versionRegex = """"version":\s*(\d+)""".toRegex()
        versionRegex.find(json)?.groupValues?.get(1)?.toInt() ?: 1
    }
    private fun tmp(): File = kotlin.io.path.createTempDirectory("langlots").toFile().also { it.deleteOnExit() }

    private fun zip(entries: Map<String, String>): File = File(tmp(), "lot.zip").also { f ->
        ZipOutputStream(f.outputStream()).use { o -> for ((n, t) in entries) { o.putNextEntry(ZipEntry(n)); o.write(t.toByteArray()); o.closeEntry() } }
    }
    private fun real() = mapOf("langue.json" to content.resolve("langue.json").readText(), "media.json" to content.resolve("media.json").readText())
    private fun meta(f: File, scope: String = "zh-a0-salut-fr", version: Int = realVersion, bytes: Long = f.length(), sha: String = LotHash.sha256Hex(f)) =
        LotMeta(LotId("langues", scope), version, bytes, sha, "Chinois A0")

    @Test fun installsAValidLotAndListsIt() {
        val c = LangLotConsumer(tmp()); val f = zip(real())
        assertTrue(c.install(meta(f), f), c.lastError)
        assertEquals(listOf(LotId("langues", "zh-a0-salut-fr")), c.installed().map { it.id })
        val packs = c.packs()
        assertEquals(1, packs.size)
        assertEquals(listOf(Lang.ZH), LangCatalog.languages(packs)); assertEquals(listOf(LangLevel.A0), LangCatalog.levels(packs, Lang.ZH))
        assertEquals(1, LangCatalog.packsFor(packs, Lang.ZH, LangLevel.A0).size)
        assertEquals(emptyList(), LangCatalog.levels(packs, Lang.JA))
        c.remove(LotId("langues", "zh-a0-salut-fr")); assertTrue(c.installed().isEmpty()); assertTrue(c.packs().isEmpty())
    }

    @Test fun refusesWrongFeatureNameSizeHashAndContent() {
        val c = LangLotConsumer(tmp()); val f = zip(real())
        assertFalse(c.install(meta(f).copy(id = LotId("learn", "zh-a0-salut-fr")), f))
        assertFalse(c.install(meta(f, scope = "cm2"), f)); assertContains(c.lastError!!, "nom invalide")
        assertFalse(c.install(meta(f, bytes = f.length() + 1), f)); assertContains(c.lastError!!, "taille")
        assertFalse(c.install(meta(f, sha = "0".repeat(64)), f)); assertContains(c.lastError!!, "sha256")
        val other = zip(real().mapValues { (k, v) -> if (k == "langue.json") v.replace("\"version\": $realVersion", "\"version\": ${realVersion - 1}") else v })
        assertFalse(c.install(meta(other), other)); assertContains(c.lastError!!, "version")
        val noJson = zip(mapOf("media.json" to "{}")); assertFalse(c.install(meta(noJson), noJson))
        val wrongScope = zip(real()); assertFalse(c.install(meta(wrongScope, scope = "zh-a1-salut-fr"), wrongScope)); assertContains(c.lastError!!, "ne correspond pas")
        val badLicence = zip(real().mapValues { (k, v) -> if (k == "media.json") v.replace("CASTBRIDGE-ORIGINAL", "CC-BY-NC-4.0") else v })
        if (real()["media.json"]!!.contains("CASTBRIDGE-ORIGINAL")) { assertFalse(c.install(meta(badLicence), badLicence)); assertContains(c.lastError!!, "contenu refusé") }
        assertTrue(c.installed().isEmpty(), "nothing is installed after refusals")
    }

    @Test fun versionsAreImmutableAndNeverDowngrade() {
        val c = LangLotConsumer(tmp())
        val v3src = real().mapValues { (k, v) -> if (k == "langue.json") v.replace("\"version\": $realVersion", "\"version\": ${realVersion + 1}") else v }
        val fCurrent = zip(real()); val fNewer = zip(v3src)
        assertTrue(c.install(meta(fCurrent), fCurrent)); assertTrue(c.install(meta(fCurrent), fCurrent), "same version, same content: idempotent")
        assertTrue(c.install(meta(fNewer, version = realVersion + 1), fNewer)); assertEquals(realVersion + 1, c.installed().single().version)
        assertFalse(c.install(meta(fCurrent), fCurrent)); assertContains(c.lastError!!, "plus récente")
        assertEquals(realVersion + 1, c.installed().single().version, "the newer version stays")
    }

    @Test fun appVersionTooOldIsRefused() {
        val c = LangLotConsumer(tmp(), appVersion = 5); val f = zip(real())
        assertFalse(c.install(meta(f).copy(minAppVersion = 6), f)); assertContains(c.lastError!!, "plus récente")
    }

    @Test fun emptyCatalogHasTheFrenchMessage() {
        assertEquals("Aucune langue installée. Sur le téléphone : CastBridge › Apprendre › « Données hors ligne » › Langues : choisissez la langue puis « Télécharger mes leçons de langue ».", LangCatalog.EMPTY_MESSAGE)
        assertTrue(LangCatalog.languages(emptyList()).isEmpty())
    }
}
