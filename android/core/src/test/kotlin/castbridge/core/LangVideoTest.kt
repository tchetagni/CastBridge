package castbridge.core

import castbridge.core.langues.*
import java.io.File
import kotlin.test.*

/** The five silent mini-videos of the chinese A0 packs for francophones travel with the APK (same path as the images and the sounds), and the `videos` field of a unit. */
class LangVideoTest {
    private val content = File(System.getProperty("learn.content") ?: "../../content/learn").parentFile.resolve("langues")
    private val starter = EmbeddedLangSource()
    private val expected = mapOf(
        "zh-a0-salut-fr" to listOf("zh-a0-salutations-astra" to "zh-a0-salut-fr-v3"),
        "zh-a0-phonetique-fr" to listOf("zh-a0-tons-astra" to "zh-a0-phonetique-fr-u1-v5"),
        "zh-a0-nombres-fr" to listOf("zh-a0-nombres-1-5-astra" to "zh-a0-nombres-fr-v6", "zh-a0-nombres-6-10-astra" to "zh-a0-nombres-fr-v11"),
        "zh-a0-famille-fr" to listOf("zh-a0-famille-astra" to "zh-a0-famille-fr-v7"),
    )
    private fun pack(id: String) = starter.packs.first { it.parts.scope == id }
    private fun mini(vararg videos: UnitVideo, media: Map<String, MediaEntry>): LangPack {
        val p = pack("zh-a0-salut-fr")
        return p.copy(units = listOf(p.units[0].copy(videos = videos.toList())), media = p.media + media)
    }
    private fun clip(id: String = "zh-a0-salut-fr-vid", silent: Boolean = true, synthetic: Boolean = true, kind: String = "video") =
        MediaEntry(id, "video/x.mp4", kind, 1000, 9000, "CASTBRIDGE-ORIGINAL", "Astra", "synthétique", null, "ffmpeg", synthetic, "zh", null, "alt", silent)

    @Test fun everyVideoIsBundledIntactAndHookedAtItsPlace() {
        var n = 0
        for ((id, list) in expected) {
            val p = pack(id); val u = p.units.first()
            for ((mid, after) in list) {
                val m = assertNotNull(p.media[mid], mid); assertEquals("video", m.kind); assertTrue(m.silent && m.synthetic && !m.alt.isNullOrBlank(), mid)
                assertNull(m.voiceLicense, mid)
                val b = assertNotNull(starter.media(id, m.file), mid)
                assertEquals(m.bytes, b.size.toLong(), mid); assertEquals("ftyp", String(b, 4, 4), mid)
                assertTrue(b.size <= 1_500_000)
                assertEquals(listOf(mid), LangVisuals.videosAfter(p, u, after).filter { it.media.id == mid }.map { it.media.id })
                assertEquals(LangVisuals.SYNTHETIC_VIDEO_NOTICE, LangVisuals.videoNotice(m)); n++
            }
            assertEquals(emptyList(), LangValidator.validate(p), id)
            assertTrue(LangVisuals.videosAtEnd(p, u).isEmpty())
        }
        assertEquals(5, n)
    }
    @Test fun theSourceFolderHoldsOnlyTheReferencedVideos() {
        val wanted = expected.values.flatten().map { "${it.first}.mp4" }.sorted()
        val onDisk = content.listFiles { f -> f.isDirectory }.orEmpty().flatMap { d -> d.resolve("media").listFiles { f -> f.extension == "mp4" }.orEmpty().map { it.name } }.sorted()
        assertEquals(wanted, onDisk)
        // the videos of the packs are those of the starter, byte for byte
        for ((id, list) in expected) for ((mid, _) in list) assertContentEquals(content.resolve("$id/media/$mid.mp4").readBytes(), starter.media(id, pack(id).media.getValue(mid).file))
    }
    @Test fun starterBudgetCountsTheVideos() {
        val videos = expected.entries.sumOf { (id, l) -> l.sumOf { starter.media(id, pack(id).media.getValue(it.first).file)!!.size.toLong() } }
        assertTrue(videos in 300_000..500_000, "$videos octets")
        assertTrue(starter.bytes() >= videos)
    }
    @Test fun anAbsentVideoIsSimplyNotAvailable() {
        val p = pack("zh-a0-salut-fr")
        assertNull(starter.media(p.parts.scope, "video/absent.mp4")); assertNull(starter.media(p.parts.scope, "../langue.json")); assertNull(starter.media("absent", "video/zh-a0-salutations-astra.mp4"))
        // a unit without videos, an unknown reference, a reference to a sound: no clip, no crash
        val none = pack("zh-a0-phonetique-fr").units[1]
        assertTrue(none.videos.isEmpty()); assertTrue(LangVisuals.videosAfter(pack("zh-a0-phonetique-fr"), none, "x").isEmpty() && LangVisuals.videosAtEnd(pack("zh-a0-phonetique-fr"), none).isEmpty())
        val q = mini(UnitVideo("m:inconnue", null, null), UnitVideo("m:zh-nihao", null, null), media = emptyMap())
        assertTrue(LangVisuals.videosAtEnd(q, q.units[0]).isEmpty())
    }
    @Test fun buttonTextAndUnhookedVideoGoToTheEndOfTheVocabulary() {
        val c = clip(); val q = mini(UnitVideo("m:${c.id}", null, "Se saluer"), UnitVideo("m:${c.id}", "zh-a0-salut-fr-v2", null), media = mapOf(c.id to c))
        val u = q.units[0]
        assertEquals(1, LangVisuals.videosAtEnd(q, u).size); assertEquals("Voir la vidéo (muette) : Se saluer", LangVisuals.videoButton(LangVisuals.videosAtEnd(q, u)[0]))
        assertEquals("Voir la vidéo (muette)", LangVisuals.videoButton(LangVisuals.videosAfter(q, u, "zh-a0-salut-fr-v2")[0]))
        assertEquals("Voir la vidéo", LangVisuals.videoButton(LangVisuals.UnitClip(UnitVideo("m:x", null, null), c.copy(silent = false))))
        assertNull(LangVisuals.videoNotice(c.copy(synthetic = false)))
    }

    // ---- the field itself: additive, optional, validated ----
    @Test fun theFieldIsParsedAndOptional() {
        val p = pack("zh-a0-nombres-fr")
        assertEquals(listOf("zh-a0-nombres-fr-v6", "zh-a0-nombres-fr-v11"), p.units[0].videos.map { it.after })
        assertEquals("Compter de 1 à 5", p.units[0].videos[0].label)
        // a pack without the key reads as « no video »: nothing changes for it
        assertTrue(pack("zh-a0-salut-en").units.all { it.videos.isEmpty() })
        assertTrue(p.mediaRefs().containsAll(listOf("m:zh-a0-nombres-1-5-astra", "m:zh-a0-nombres-6-10-astra")))
        // an old reader (no `videos` field in the model) just ignores the key: the text lot stays valid JSON for any reader that skips unknown keys
        val text = content.resolve("zh-a0-nombres-fr/langue.json").readText()
        assertTrue(text.contains("\"videos\"") && castbridge.core.quiz.Json.obj(text).containsKey("units"))
    }
    @Test fun validationOfTheField() {
        val c = clip()
        assertEquals(emptyList(), LangValidator.validate(mini(UnitVideo("m:${c.id}", "zh-a0-salut-fr-v1", null), media = mapOf(c.id to c))))
        assertContains(LangValidator.validate(mini(UnitVideo("m:absente", null, null), media = emptyMap())).joinToString(), "absent de media.json")
        assertContains(LangValidator.validate(mini(UnitVideo("m:zh-nihao", null, null), media = emptyMap())).joinToString(), "n'est pas une vidéo")
        assertContains(LangValidator.validate(mini(UnitVideo("m:${c.id}", "zh-a0-salut-fr-v99", null), media = mapOf(c.id to c))).joinToString(), "absent de l'unité")
        val bad = clip(id = "zh-a0-salut-fr-aud", kind = "audio").copy(file = "audio/a.opus", voiceLicense = "Apache-2.0")
        assertContains(LangValidator.validate(mini(media = mapOf(bad.id to bad))).joinToString(), "« silent » ne s'applique qu'à une vidéo")
    }

    // ---- voice licence: a silent video has no voice ----
    @Test fun voiceLicenceIsAskedOfAVideoOnlyIfItHasASoundTrack() {
        assertFalse(LangLicences.voiceLicenseRequired(clip(silent = true)))
        assertTrue(LangLicences.voiceLicenseRequired(clip(silent = false)))
        assertTrue(LangLicences.voiceLicenseRequired(clip(kind = "audio", silent = false)))
        assertFalse(LangLicences.voiceLicenseRequired(clip(synthetic = false, silent = false)))
        val c = clip()
        assertEquals(emptyList(), LangValidator.validate(mini(UnitVideo("m:${c.id}", null, null), media = mapOf(c.id to c))))
        val talking = c.copy(silent = false)
        assertContains(LangValidator.validate(mini(UnitVideo("m:${c.id}", null, null), media = mapOf(c.id to talking))).joinToString(), "licence de la voix")
        assertEquals(emptyList(), LangValidator.validate(mini(UnitVideo("m:${c.id}", null, null), media = mapOf(c.id to talking.copy(voiceLicense = "Apache-2.0")))))
    }
}
