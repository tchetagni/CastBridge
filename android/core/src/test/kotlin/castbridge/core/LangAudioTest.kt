package castbridge.core

import castbridge.core.langues.*
import java.io.File
import kotlin.test.*

/** The sounds of the four chinese A0 packs for francophones travel with the APK (no media lot reaches a device): same path as the illustrations. */
class LangAudioTest {
    private val content = File(System.getProperty("learn.content") ?: "../../content/learn").parentFile.resolve("langues")
    private val themes = listOf("salut", "phonetique", "nombres", "famille")
    private val starter = EmbeddedLangSource()
    private fun refs(id: String) = Regex("\"m:([A-Za-z0-9_-]+)\"").findAll(content.resolve("$id/langue.json").readText()).map { it.groupValues[1] }.toSet()

    @Test fun everyReferencedSoundIsBundledAndIntact() {
        var n = 0
        for (t in themes) {
            val p = starter.packs.first { it.parts.scope == "zh-a0-$t-fr" }
            for (id in refs(p.parts.scope)) {
                val m = p.media.getValue(id).takeIf { it.kind == "audio" } ?: continue
                val b = assertNotNull(starter.media(p.parts.scope, m.file), m.id)
                assertEquals(m.bytes, b.size.toLong(), m.id); assertEquals("OggS", String(b, 0, 4), m.id); assertEquals("OpusHead", String(b, 28, 8), m.id)
                assertNotNull(LangVisuals.audio(p, "m:$id")); n++
            }
        }
        assertEquals(47, n)
    }
    @Test fun theSourceFolderHoldsOnlyTheReferencedSounds() {
        for (t in themes) {
            val id = "zh-a0-$t-fr"
            val p = starter.packs.first { it.parts.scope == id }
            val wanted = refs(id).mapNotNull { p.media[it] }.filter { it.kind == "audio" }.map { it.file.substringAfterLast('/') }.toSet()
            val onDisk = content.resolve("$id/media").listFiles { f -> f.extension == "opus" }.orEmpty().map { it.name }.toSet()
            assertEquals(wanted, onDisk, id)
        }
    }
    @Test fun famille_d1StaysUnplugged() {
        val p = starter.packs.first { it.parts.scope == "zh-a0-famille-fr" }
        assertTrue("zh-famille-d1" in p.media); assertFalse("zh-famille-d1" in refs(p.parts.scope))
        assertNull(starter.media(p.parts.scope, p.media.getValue("zh-famille-d1").file))
        assertFalse(content.resolve("zh-a0-famille-fr/media/zh-famille-d1.opus").exists())
    }
    @Test fun onlyTheFourFrenchPacksCarrySounds() {
        val withSound = content.listFiles { f -> f.isDirectory }.orEmpty().filter { d -> d.resolve("media").listFiles { f -> f.extension == "opus" }.orEmpty().isNotEmpty() }.map { it.name }.sorted()
        assertEquals(themes.map { "zh-a0-$it-fr" }.sorted(), withSound)
    }
    @Test fun anAbsentSoundIsSimplyNotAvailable() {
        val p = starter.packs.first { it.parts.scope == "zh-a0-salut-fr" }
        assertNull(starter.media(p.parts.scope, "audio/absent.opus")); assertNull(starter.media(p.parts.scope, "../langue.json")); assertNull(starter.media("absent", "audio/zh-bu.opus"))
        assertNull(LangVisuals.audio(p, "m:absent")); assertNull(LangVisuals.audio(p, null))
        // a reference to an image or a video is not a sound
        assertNull(LangVisuals.audio(p, "m:zh-a0-salut-astra-rencontre")); assertNull(LangVisuals.audio(p, "m:zh-salut-salut"))
    }
    @Test fun syntheticVoiceCarriesTheBetaMention() {
        val p = starter.packs.first { it.parts.scope == "zh-a0-salut-fr" }
        assertEquals(LangVisuals.SYNTHETIC_VOICE_NOTICE, LangVisuals.voiceNotice(p.media.getValue("zh-nihao")))
        assertNull(LangVisuals.voiceNotice(p.media.getValue("zh-nihao").copy(synthetic = false)))
        assertNull(LangVisuals.voiceNotice(p.media.getValue("zh-a0-salut-astra-rencontre")))
    }
    @Test fun starterBudgetCountsTheSounds() {
        val withSounds = starter.bytes()
        val textAndImages = starter.packs.sumOf { p -> p.media.values.filter { it.kind == "image" }.sumOf { m -> starter.media(p.parts.scope, m.file)?.size?.toLong() ?: 0L } }
        assertTrue(withSounds >= textAndImages + 190_000, "sounds counted ($withSounds octets)")
    }
}
