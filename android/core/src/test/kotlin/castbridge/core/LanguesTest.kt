package castbridge.core

import castbridge.core.langues.*
import castbridge.core.lots.LotId
import castbridge.core.lots.LotMeta
import java.io.File
import kotlin.test.*

/** « Langues » skeleton (docs/LANGUES.md): scopes, pack format, licences, marking, spaced repetition, placement, skill graphs, 6 GB budget, lot selection. */
class LanguesTest {
    private val content = File(System.getProperty("learn.content") ?: "../../content/learn").parentFile.resolve("langues")
    private val budget by lazy { LangBudget.parse(content.resolve("budget.json").readText()) }
    private fun samplePack() = LangPackJson.parse(mapOf("langue.json" to content.resolve("zh-a0-salut-fr/langue.json").readText(), "media.json" to content.resolve("zh-a0-salut-fr/media.json").readText()))
    private fun lot(feature: String, scope: String, bytes: Long) = LotMeta(LotId(feature, scope), 1, bytes, "0".repeat(64), scope)

    // ---- scopes ----
    @Test fun scopesRoundTripAndStayValid() {
        val s = LangLots.scope(Lang.ZH, LangLevel.A1, "salut", Lang.FR)
        assertEquals("zh-a1-salut-fr", s)
        assertEquals(LangLots.Parts(Lang.ZH, LangLevel.A1, "salut", Lang.FR), LangLots.parse(s))
        for (l in Lang.entries) for (lv in LangLevel.entries) for (src in Lang.sources) if (l != src)
            assertTrue(Regex("[a-z0-9][a-z0-9-]{0,31}").matches(LangLots.scope(l, lv, "x".repeat(16), src)), "$l $lv $src")
    }
    @Test fun badScopesAreRefused() {
        assertFailsWith<IllegalArgumentException> { LangLots.scope(Lang.ZH, LangLevel.A1, "a-b", Lang.FR) }
        assertFailsWith<IllegalArgumentException> { LangLots.scope(Lang.FR, LangLevel.A1, "salut", Lang.FR) }
        assertNull(LangLots.parse("cm2")); assertNull(LangLots.parse("xx-a1-salut-fr")); assertNull(LangLots.parse("zh-a1-salut-zh"))
        assertFalse(LangLots.isLanguage(LotId("learn", "zh-a1-salut-fr")))
    }

    // ---- pack format ----
    @Test fun samplePackParsesAndValidates() {
        val p = samplePack()
        assertEquals(emptyList(), LangValidator.validate(p))
        assertEquals(LangLots.Parts(Lang.ZH, LangLevel.A0, "salut", Lang.FR), p.parts)
        assertEquals(4, p.units.single().exercises.size)
        assertTrue(p.media.values.all { it.synthetic })
    }
    @Test fun validatorCatchesMissingReadingLicenceAndMedia() {
        val p = samplePack()
        val bad = p.copy(
            units = listOf(p.units[0].copy(vocab = p.units[0].vocab.map { it.copy(reading = null) } + p.units[0].vocab[0].copy(id = "zh-a0-salut-fr-v1", audio = "m:absent"))),
            media = p.media + ("cc" to p.media.values.first().copy(id = "cc", license = "CC-BY-4.0", engine = null, synthetic = false, author = null)) + ("nc" to p.media.values.first().copy(id = "nc", license = "CC-BY-NC-4.0")),
        )
        val e = LangValidator.validate(bad).joinToString("\n")
        assertContains(e, "sans lecture"); assertContains(e, "en double"); assertContains(e, "absent de media.json")
        assertContains(e, "auteur, source et URL"); assertContains(e, "non redistribuable")
    }
    @Test fun synthesizedAudioMustBeMarked() {
        val p = samplePack()
        val m = p.media.values.first().copy(synthetic = false)
        assertContains(LangValidator.validate(p.copy(media = p.media + (m.id to m))).joinToString(), "synthetic")
    }
    @Test fun newerFormatAndWrongTypeAreRefused() {
        val src = content.resolve("zh-a0-salut-fr/langue.json").readText()
        assertFailsWith<LangPackJson.ParseError> { LangPackJson.parse(mapOf("langue.json" to src.replace("\"format\": 1", "\"format\": 9"))) }
        assertFailsWith<LangPackJson.ParseError> { LangPackJson.parse(mapOf("langue.json" to src.replace("\"type\": \"langue\"", "\"type\": \"pack\""))) }
        assertFailsWith<LangPackJson.ParseError> { LangPackJson.parse(mapOf("langue.json" to src.replace("\"target\": \"zh\"", "\"target\": \"ja\""))) }
        assertFailsWith<LangPackJson.ParseError> { LangPackJson.parse(emptyMap()) }
    }
    @Test fun unknownExerciseKindIsAParseError() {
        val src = content.resolve("zh-a0-salut-fr/langue.json").readText().replace("\"dictation\"", "\"karaoke\"")
        assertContains(assertFailsWith<LangPackJson.ParseError> { LangPackJson.parse(mapOf("langue.json" to src)) }.message!!, "karaoke")
    }
    @Test fun existingLearnPacksAreNotLanguagePacks() {
        // backward compatibility: a « learn » pack.json never parses as a language pack, and its folder is not under content/langues
        assertFailsWith<LangPackJson.ParseError> { LangPackJson.parse(mapOf("langue.json" to "{\"format\":1,\"id\":\"6e-english\"}")) }
    }

    // ---- marking ----
    @Test fun markingIgnoresCasePunctuationWidthAndOptionallyAccents() {
        fun ex(vararg a: String) = LangExercise("x", LangExerciseKind.DICTATION, "p", "m:a", a.toList(), emptyList(), null, emptyList(), emptyList(), null, LangSkill.LISTENING)
        assertTrue(LangMarking.matches("où est la gare", ex("Où est la gare ?")))
        assertTrue(LangMarking.matches("ou est la gare", ex("Où est la gare ?")))
        assertFalse(LangMarking.matches("ou est la gare", ex("Où est la gare ?"), strictAccents = true))
        assertTrue(LangMarking.matches("你 好", ex("你好！")))
        assertTrue(LangMarking.matches("ＨＥＬＬＯ", ex("hello")))   // full-width
        assertFalse(LangMarking.matches("谢谢你", ex("谢谢")))
        assertTrue(LangMarking.sameIgnoringTones("ma", "mǎ")); assertFalse(LangMarking.sameIgnoringTones("ma", "ma"))
    }

    // ---- spaced repetition ----
    @Test fun srsBoxesAndDueOrder() {
        var c = Srs.Card("a")
        c = Srs.review(c, 2, 10); assertEquals(1, c.box); assertEquals(11, c.dueDay)
        c = Srs.review(c, 2, 11); c = Srs.review(c, 2, 14); assertEquals(3, c.box); assertEquals(21, c.dueDay)
        assertEquals(3, Srs.review(c, 1, 21).box)
        assertEquals(0, Srs.review(c, 0, 21).box)
        repeat(20) { c = Srs.review(c, 2, 100) }; assertEquals(Srs.intervals.lastIndex, c.box)
        val due = Srs.due(listOf(Srs.Card("b", 2, 5), Srs.Card("a", 0, 7), Srs.Card("z", 0, 99)), 10)
        assertEquals(listOf("a", "b"), due.map { it.id })
    }

    // ---- placement ----
    private fun answers(skill: LangSkill, vararg rights: Pair<LangLevel, Int>) = rights.flatMap { (lv, n) -> (0 until 3).map { Placement.Answer(lv, skill, it < n) } }
    @Test fun placementStaircaseStopsAtTheFirstFailedStep() {
        val a = answers(LangSkill.READING, LangLevel.A1 to 3, LangLevel.A2 to 2, LangLevel.B1 to 1)
        assertNull(Placement.nextLevel(a, LangSkill.READING))
        assertEquals(LangLevel.A1, Placement.nextLevel(emptyList(), LangSkill.LISTENING))
        assertEquals(LangLevel.A2, Placement.nextLevel(answers(LangSkill.LISTENING, LangLevel.A1 to 3), LangSkill.LISTENING))
        assertEquals(LangLevel.A1, Placement.nextLevel(answers(LangSkill.LISTENING, LangLevel.A1 to 3).take(2), LangSkill.LISTENING))
    }
    @Test fun placementResultKeepsTheProfileAndAveragesDown() {
        val all = LangSkill.entries.flatMap { s ->
            when (s) {
                LangSkill.READING -> answers(s, LangLevel.A1 to 3, LangLevel.A2 to 3, LangLevel.B1 to 3, LangLevel.B2 to 0)
                LangSkill.LISTENING -> answers(s, LangLevel.A1 to 3, LangLevel.A2 to 2, LangLevel.B1 to 3)   // B1 not counted: A2 reached, then B1 ok → but each step needs the previous
                LangSkill.WRITING -> answers(s, LangLevel.A1 to 1)
                LangSkill.SPEAKING -> emptyList()
            }
        }
        val r = Placement.evaluate(all)
        assertEquals(LangLevel.B1, r.bySkill[LangSkill.READING]); assertEquals(LangLevel.B1, r.bySkill[LangSkill.LISTENING])
        assertEquals(LangLevel.A0, r.bySkill[LangSkill.WRITING]); assertEquals(LangLevel.A0, r.bySkill[LangSkill.SPEAKING])
        assertEquals(LangLevel.A1, r.overall)   // (3+3+0+0)/4 = 1
        assertEquals(LangLevel.A0, Placement.evaluate(emptyList()).overall)
    }
    @Test fun aPerfectCandidateReachesNative() {
        val all = LangSkill.entries.flatMap { s -> LangLevel.entries.drop(1).flatMap { lv -> answers(s, lv to 3) } }
        val r = Placement.evaluate(all)
        assertTrue(r.bySkill.values.all { it == LangLevel.NATIF }); assertEquals(LangLevel.NATIF, r.overall)
        assertNull(Placement.nextLevel(all, LangSkill.SPEAKING))
    }

    // ---- skill graphs ----
    @Test fun everyGeneratedGraphIsValidAndWalkable() {
        for (l in Lang.entries) {
            val g = LangSkillGraph.parse(content.parentFile.resolve("graph/langue-${l.code}.json").readText())
            assertEquals(l, g.lang); assertEquals(emptyList(), g.errors(), l.code); assertTrue(g.nodes.any { it.id == "${l.code}.a1-co" })
            // walk the graph: repeatedly master everything available; must reach every node (no dead end)
            val mastered = HashSet<String>()
            while (true) { val next = g.available(mastered); if (next.isEmpty()) break; mastered += next.map { it.id } }
            assertEquals(g.nodes.size, mastered.size, "${l.code}: nœuds inaccessibles")
        }
    }
    @Test fun graphErrorsAreReported() {
        val g = LangSkillGraph(Lang.EN, listOf(LangSkillNode("a", LangLevel.A1, null, "a", listOf("b"), null), LangSkillNode("b", LangLevel.A1, null, "b", listOf("a"), null), LangSkillNode("c", LangLevel.A0, null, "c", listOf("a"), null), LangSkillNode("d", LangLevel.A0, null, "d", listOf("zz"), null)))
        val e = g.errors().joinToString()
        assertContains(e, "cycle"); assertContains(e, "plus haut"); assertContains(e, "inconnu")
    }

    // ---- budget ----
    @Test fun budgetWeightsAreConsistentAndCoverTheEnvelope() {
        assertEquals(emptyList(), budget.problems())
        assertEquals(6144, budget.envelopeMb); assertEquals(3072, budget.restOfBaseMb)
        val cells = budget.cells()
        assertEquals(56, cells.size)
        val sum = cells.sumOf { it.totalKb } + budget.transversalKb() + budget.reserveKb()
        assertTrue(sum <= 6144L * 1024 && sum > 6144L * 1024 - 56 * 8, "somme $sum Ko")   // integer rounding only
        assertTrue(budget.languageKb(Lang.ZH) > budget.languageKb(Lang.IT) && budget.languageKb(Lang.JA) == budget.languageKb(Lang.ZH))
        for (c in cells) { assertTrue(c.mediaKb > 0, "${c.lang} ${c.level}"); assertTrue(budget.mediaLots(c) >= 1) }
    }
    @Test fun budgetCheckFlagsOversizeLotsAndOverrun() {
        val ok = budget.check(listOf(lot("langues", "zh-a1-salut-fr", 2_000_000), lot("langues-media", "zh-a1-salut", 90_000_000), lot("learn", "cm2", 9_999_999_999)))
        assertEquals(emptyList(), ok.errors); assertEquals(92_000_000L, ok.totalBytes)   // « learn » belongs to the other 3 GB
        val bad = budget.check(listOf(lot("langues", "zh-a1-salut-fr", 4_000_000), lot("langues-media", "zh-a1-salut", 120_000_000)))
        assertEquals(2, bad.errors.size)
        val over = budget.check((1..11).map { lot("langues-media", "it-b1-t$it", 100_000_000) })   // 1.1 GB of Italian > its 10 % share (614 MB)
        assertTrue(over.errors.any { it.startsWith("it :") })
    }

    // ---- lot selection ----
    @Test fun textLotsFollowTheLearnerAndTheTvBudget() {
        val l = LearnerLang(Lang.ZH, Lang.FR, LangLevel.A1)
        val cat = listOf(lot("langues", "zh-a1-salut-fr", 900_000), lot("langues", "zh-a2-voyage-fr", 800_000), lot("langues", "zh-a0-pinyin-fr", 500_000),
            lot("langues", "zh-a1-salut-en", 100_000), lot("langues", "ja-a1-salut-fr", 100_000), lot("langues", "zh-c1-presse-fr", 2_900_000), lot("langues-media", "zh-a1-salut", 80_000_000), lot("learn", "cm2", 1))
        val p = LangPlanner.textForTv(l, cat, 2_500_000)
        assertEquals(listOf("zh-a1-salut-fr", "zh-a0-pinyin-fr", "zh-a2-voyage-fr"), p.selected.map { it.id.scope })   // current level, previous before next? rank: a1=0, a0=1, a2=2
        assertEquals(1, p.skipped.size); assertEquals("zh-c1-presse-fr", p.skipped[0].first.id.scope)
        assertEquals(LangPlanner.textForTv(l, cat.reversed(), 2_500_000), p)   // deterministic
    }
    @Test fun mediaIsPlayableOnlyWithItsTextTwin() {
        val t = LotId("langues", "zh-a1-salut-fr"); val m1 = LotId("langues-media", "zh-a1-salut"); val m2 = LotId("langues-media", "zh-a2-voyage")
        assertEquals(setOf(m1), LangPlanner.playableMedia(setOf(t), setOf(m1, m2)))
        val plan = LangPlanner.mediaFor(LearnerLang(Lang.ZH, Lang.FR, LangLevel.A1), listOf(lot("langues-media", "zh-a1-salut", 80_000_000), lot("langues-media", "zh-a2-voyage", 60_000_000)), 100_000_000)
        assertEquals(listOf("zh-a1-salut"), plan.selected.map { it.id.scope })
    }
    @Test fun oneMediaLotServesBothStartLanguages() {
        val media = lot("langues-media", "zh-a1-salut", 80_000_000)
        for (src in listOf(Lang.FR, Lang.EN)) assertEquals(0, LangPlanner.rank(LearnerLang(Lang.ZH, src, LangLevel.A1), media.id))
        assertEquals(LangLots.MediaParts(Lang.ZH, LangLevel.A1, "salut"), LangLots.parseMedia("zh-a1-salut"))
        assertEquals("zh-a1-salut", LangLots.mediaId(LangLots.parse("zh-a1-salut-en")!!).scope)
        assertEquals(setOf(media.id), LangPlanner.playableMedia(setOf(LotId("langues", "zh-a1-salut-en")), setOf(media.id)))   // the English text twin is enough
        assertNull(LangLots.parseMedia("zh-a1-salut-fr")); assertEquals(Lang.ZH, LangLots.targetOf(media.id)); assertNull(LangLots.targetOf(LotId("quiz", "zh-a1-salut")))
    }
    @Test fun freeLicencesAreAcceptedAndSyntheticVoicesNeedAFreeVoiceLicence() {
        val p = samplePack()
        val m = p.media.values.first()
        for (l in listOf("CC-BY-SA-3.0", "MIT", "Apache-2.0", "OFL-1.1", "CC-BY-3.0")) assertTrue(l in LangLicences.allowed, l)
        for (l in listOf("CC-BY-NC-4.0", "CC-BY-ND-4.0", "CPML")) assertFalse(l in LangLicences.allowed, l)
        assertEquals(emptyList(), LangValidator.validate(p.copy(media = p.media + (m.id to m.copy(voiceLicense = "Apache-2.0", engine = "kokoro-82m")))))
        assertContains(LangValidator.validate(p.copy(media = p.media + (m.id to m.copy(voiceLicense = null)))).joinToString(), "licence de la voix")
        assertContains(LangValidator.validate(p.copy(media = p.media + (m.id to m.copy(voiceLicense = "CPML")))).joinToString(), "licence de la voix")
        assertEquals(2048, LangBudget.PHONE_LANG_DEFAULT_MB)
    }
}
