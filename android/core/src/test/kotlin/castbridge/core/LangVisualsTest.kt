package castbridge.core

import castbridge.core.langues.*
import castbridge.core.learn.AnimationJson
import castbridge.core.learn.AnimationLint
import castbridge.core.learn.AnimationRules
import castbridge.core.learn.LessonJson
import castbridge.core.quiz.Json
import java.io.File
import kotlin.test.*

/** What the lesson screens draw besides text (illustrations, commented corrections) and the content of the chinese A0 packs revised in October 2026. */
class LangVisualsTest {
    private val content = File(System.getProperty("learn.content") ?: "../../content/learn").parentFile.resolve("langues")
    private fun pack(id: String) = LangPackJson.parse(mapOf("langue.json" to content.resolve("$id/langue.json").readText(), "media.json" to content.resolve("$id/media.json").readText()))
    private val themes = listOf("salut", "phonetique", "nombres", "famille")

    private fun media(kind: String, synthetic: Boolean, voice: String? = null) =
        MediaEntry("m1", "images/a.webp", kind, 10, null, "CASTBRIDGE-ORIGINAL", "A", null, null, if (synthetic) "image_gen" else null, synthetic, "zh", voice, "alt")

    // ---- the voice licence belongs to voices, not to images ----
    @Test fun voiceLicenceIsAskedOfSyntheticAudioAndVideoOnly() {
        assertFalse(LangLicences.voiceLicenseRequired(media("image", true)))
        assertTrue(LangLicences.voiceLicenseRequired(media("audio", true)))
        assertTrue(LangLicences.voiceLicenseRequired(media("video", true)))
        assertFalse(LangLicences.voiceLicenseRequired(media("audio", false)))
    }
    @Test fun syntheticImageWithoutVoiceLicenceValidatesButSyntheticAudioStillNeedsOne() {
        val p = pack("zh-a0-salut-fr")
        val img = media("image", true).copy(id = "zh-a0-salut-fr-img-test", engine = "image_gen")
        assertEquals(emptyList(), LangValidator.validate(p.copy(media = p.media + (img.id to img))))
        val aud = media("audio", true).copy(id = "zh-a0-salut-fr-aud-test", engine = "espeak-ng", file = "audio/a.opus")
        assertContains(LangValidator.validate(p.copy(media = p.media + (aud.id to aud))).joinToString(), "licence de la voix")
    }

    // ---- rules of the screens ----
    @Test fun imageComesFromTheVocabItemOrItsCardAndOnlyIfItIsAnImage() {
        val p = pack("zh-a0-salut-fr"); val u = p.units[0]
        val hello = u.vocab.first { it.id == "zh-a0-salut-fr-v1" }
        val m = assertNotNull(LangVisuals.image(p, u, hello)); assertEquals("image", m.kind)
        assertEquals(LangVisuals.SYNTHETIC_NOTICE, LangVisuals.notice(m)); assertTrue(m.alt!!.isNotBlank())
        // a card alone is enough, and a word without image gives nothing (the screen stays as before)
        val viaCard = hello.copy(image = null)
        assertNotNull(LangVisuals.image(p, u, viaCard))
        assertNull(LangVisuals.image(p, u, u.vocab.first { it.id == "zh-a0-salut-fr-v2" }))
        // a reference to an audio, or to an unknown media, is not an image
        val audioId = p.media.values.first { it.kind == "audio" }.id
        assertNull(LangVisuals.image(p, u, hello.copy(image = "m:$audioId")))
        assertNull(LangVisuals.image(p, u, hello.copy(image = "m:absent")))
        assertNull(LangVisuals.notice(media("image", false))); assertNull(LangVisuals.notice(media("audio", true)))
    }
    @Test fun explanationIsShownOnlyWhenThereIsOne() {
        val x = LangExercise("x", LangExerciseKind.MCQ, "p", null, emptyList(), listOf("a", "b"), 0, emptyList(), emptyList(), null, LangSkill.READING)
        assertNull(LangVisuals.explanation(x)); assertNull(LangVisuals.explanation(x.copy(explanation = "  ")))
        assertEquals("Le ton change.", LangVisuals.explanation(x.copy(explanation = " Le ton change. ")))
    }
    @Test fun fileNamesAreFlatAndSafe() {
        assertEquals("a.webp", LangVisuals.fileName("images/a.webp")); assertNull(LangVisuals.fileName("../a.webp")); assertNull(LangVisuals.fileName("images/"))
        assertNull(LangVisuals.fileName("images/..\\a.webp"))
    }

    // ---- the four revised packs ----
    @Test fun revisedChinesePacksValidateAndEveryExerciseExplainsItself() {
        for (t in themes) {
            val p = pack("zh-a0-$t-fr")
            assertEquals(emptyList(), LangValidator.validate(p), t)
            assertTrue(p.units.flatMap { it.exercises }.all { LangVisuals.explanation(it) != null }, "$t : explication manquante")
        }
        assertEquals(40, themes.sumOf { t -> pack("zh-a0-$t-fr").units.sumOf { it.exercises.size } })
    }
    @Test fun everyIllustrationOfThePacksHasItsFileBesideThePackAndMatchesItsSize() {
        var n = 0
        for (t in themes) {
            val p = pack("zh-a0-$t-fr")
            for (m in p.media.values.filter { it.kind == "image" }) {
                val f = content.resolve("zh-a0-$t-fr/media/${LangVisuals.fileName(m.file)}")
                assertTrue(f.isFile, "${m.id} : ${f.path} absent"); assertEquals(m.bytes, f.length(), m.id)
                assertNull(m.voiceLicense, m.id); assertTrue(m.synthetic && !m.alt.isNullOrBlank(), m.id); n++
            }
        }
        assertEquals(3, n)
    }
    @Test fun packFiguresPassAnimationRulesAndLint() {
        val figs = content.listFiles { f -> f.isDirectory }.orEmpty().flatMap { d -> File(d, "figures").listFiles { f -> f.name.endsWith(".json") }.orEmpty().toList() }
        assertTrue(figs.any { it.name == "zh-a0-phonetique-fr-u1-a1.json" })
        for (f in figs) {
            val m = Json.obj(f.readText())
            @Suppress("UNCHECKED_CAST") val fig = LessonJson.figure(m["figure"] as Map<String, Any?>, f.name)
            @Suppress("UNCHECKED_CAST") val a = AnimationJson.parse(m["animation"] as Map<String, Any?>, f.name, fig)
            val e = ArrayList<String>(); val w = ArrayList<String>()
            AnimationRules.check(a, fig, (m["alt"] as? String) ?: "", f.name, e, w)
            assertEquals(emptyList(), e, f.name); assertEquals(emptyList(), AnimationLint.lint(a), f.name)
        }
    }

    @Test fun starterBundlesTheIllustrationsOfItsPacks() {
        val starter = EmbeddedLangSource()
        for (t in themes) {
            val p = starter.packs.first { it.parts.scope == "zh-a0-$t-fr" }
            for (m in p.media.values.filter { it.kind == "image" }) {
                val b = assertNotNull(starter.media(p.parts.scope, m.file), m.id)
                assertEquals(m.bytes, b.size.toLong(), m.id); assertEquals("RIFF", String(b, 0, 4)); assertEquals("WEBP", String(b, 8, 4))
            }
        }
        assertNull(starter.media("zh-a0-salut-fr", "images/absent.webp")); assertNull(starter.media("zh-a0-salut-fr", "../langue.json")); assertNull(starter.media("absent", "images/a.webp"))
    }
}
