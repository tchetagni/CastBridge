package castbridge.core

import castbridge.core.learn.*
import castbridge.core.lots.LotBudget
import castbridge.core.lots.StarterBudget
import castbridge.core.quiz.Json
import java.io.File
import kotlin.test.*

/**
 * « Contenu de base » (docs/LEARN.md § Contenu de base): every class and every subject of scopes.txt has base content embedded in the
 * app, derived at build time from the full packs; a lot overrides it without any duplicated lesson.
 */
class BaseContentTest {
    private val content = File(System.getProperty("learn.content") ?: "../../content/learn")
    private val table by lazy { LearnScopes.parseTable(File(content, "scopes.txt").readText()) }
    private val src = EmbeddedLessonSource()
    private val refs by lazy { src.list() }
    private fun subjectOf(pack: String) = Json.obj(File(content, "$pack/pack.json").readText())["subject"] as String
    private fun tmp(): File = kotlin.io.path.createTempDirectory("basecontent").toFile().also { it.deleteOnExit() }

    @Test fun everyScopeOfScopesTxtHasBaseContentForEverySubject() {
        assertEquals(32, table.lots.size, "32 scopes in scopes.txt")
        var pairs = 0
        for (lot in table.lots.values) {
            val subjects = lot.packs.map { subjectOf(it) }.toSet()
            for (s in subjects) {
                pairs++
                assertTrue(refs.any { it.scope == lot.scope && it.manifest.subject == s }, "${lot.scope} / $s has an embedded pack (full or base)")
            }
        }
        println("scopes × subjects = ${table.lots.size} × → $pairs (scope, subject) pairs covered")
        assertTrue(pairs >= 150)
    }

    @Test fun everyPackHasABasePackUnlessItsScopeAndSubjectAreFullyEmbedded() {
        val full = LearnTool.embeddedIds(content)
        val covered = full.map { table.scopeOf(it) to subjectOf(it) }.toSet()
        val ids = refs.map { it.id }.toSet()
        for (lot in table.lots.values) for (p in lot.packs) {
            if (p in full) assertTrue(p in ids, "$p embedded in full")
            else if ((lot.scope to subjectOf(p)) in covered) assertFalse(BaseContent.idFor(p) in ids, "$p: (${lot.scope}, ${subjectOf(p)}) is already fully embedded: no base pack")
            else assertTrue(BaseContent.idFor(p) in ids, "$p has a base pack")
        }
        for (r in refs.filter { BaseContent.isBase(it.id) }) {
            val full1 = BaseContent.fullIdOf(r.id)!!
            assertNotNull(table.scopeOf(full1), "${r.id} comes from a pack of scopes.txt"); assertEquals(table.scopeOf(full1), r.scope)
            assertFalse(full1 in full, "${r.id}: no base pack for a pack embedded in full")
        }
    }

    @Test fun sizesStayWithinTheBudget() {
        var base = 0L; var all = 0L
        for (r in refs) { val n = r.bytes().use { it.readBytes().size.toLong() }; all += n; if (BaseContent.isBase(r.id)) base += n }
        println("base packs: ${refs.count { BaseContent.isBase(it.id) }} packs, $base bytes zipped; whole embedded socle: $all bytes")
        assertTrue(all < 5L shl 20, "embedded socle $all bytes > 5 MB")
        val r = StarterBudget.measure()
        assertTrue(r.total < LotBudget.TV_MAX_BYTES - (5L shl 20), "starter ${r.total} bytes leaves at least 5 MB for lots")
    }

    @Test fun baseLessonsValidateAndComeFromTheirFullPack() {
        for (r in refs.filter { BaseContent.isBase(it.id) }) {
            val vp = src.open(r)                                     // sha256 + LessonValidator, with NO other pack known: self-contained
            val p = vp.pack; val full = BaseContent.fullIdOf(p.id)!!
            assertTrue(p.title.endsWith(BaseContent.TITLE_SUFFIX), p.title)
            assertTrue(p.lessons.size in 1..BaseContent.LESSONS_PER_PACK, "${p.id}: ${p.lessons.size} fiches")
            assertTrue(p.mockExams.isEmpty() && p.lessons.all { l -> l.exercises.all { p.exercise(it) != null } && l.selfCheck.size >= 0 })
            val dir = File(content, full)
            val source = LessonJson.parsePack(PackBuilder.sources(dir).filterKeys { it.endsWith(".json") }.mapValues { String(it.value, Charsets.UTF_8) })
            assertEquals(source.version, p.version); assertEquals(source.level, p.level); assertEquals(source.subject, p.subject); assertEquals(source.exam, p.exam)
            val firstByOrder = source.lessons.sortedBy { l -> source.chapters.firstOrNull { it.id == l.chapter }?.order ?: Int.MAX_VALUE }.take(p.lessons.size).map { it.id }
            assertEquals(firstByOrder, p.lessons.map { it.id }, "${p.id}: the first fiches by chapter order")
            for (l in p.lessons) {
                val s = source.lesson(l.id)!!
                assertEquals(s.blocks, l.blocks, "${l.id}: blocks (text, examples, figures) unchanged")
                assertEquals(s.exercises, l.exercises); assertEquals(s.selfCheck, l.selfCheck)
            }
        }
    }

    @Test fun noDuplicateLessonOrExerciseIdsInTheEmbeddedSocle() {
        val lessons = ArrayList<String>(); val exercises = ArrayList<String>()
        for (r in refs) { val p = src.open(r).pack; lessons += p.lessons.map { it.id }; exercises += p.exercises.flatMap { listOf(it.id) + it.parts.map { q -> q.id } } }
        assertEquals(lessons.size, lessons.toSet().size, "lesson ids unique across the embedded packs")
        assertEquals(exercises.size, exercises.toSet().size, "exercise ids unique across the embedded packs")
    }

    @Test fun deriveIsReproducibleAndLeavesTheSourcesAlone() {
        val a = BaseContent.plan(content).associate { it.dir.name to it.files }
        val b = BaseContent.plan(content).associate { it.dir.name to it.files }
        assertEquals(a.keys, b.keys)
        for ((k, v) in a) assertEquals(v.mapValues { it.value.toList() }, b.getValue(k).mapValues { it.value.toList() }, k)
        for (d in LearnTool.packDirs(content)) assertFalse(File(d, "lessons/base.json").exists(), "${d.name}: sources untouched")
        assertNull(BaseContent.derive(mapOf("pack.json" to """{"id":"x","chapters":[]}""".toByteArray())), "a pack without lesson has no base")
    }

    // ---- override by a lot ----
    private val real: LearnLotBuilder.Result by lazy {
        LearnLotBuilder.build(content, LearnLotBuilder.Registry.parse(File(content, "lots.json").takeIf { it.isFile }?.readText()), "2026-10-01", update = false)
    }

    private fun installed(vararg scopes: String): LearnLotConsumer {
        val c = LearnLotConsumer(tmp())
        for (s in scopes) { val b = real.lots.first { it.meta.id.scope == s }; assertTrue(c.install(b.meta, File(tmp(), b.file).also { it.writeBytes(b.bytes) }), c.lastError) }
        return c
    }

    @Test fun aFullLotOverridesTheBaseContentWithoutDuplicates() {
        val c = installed("cp")
        val lib = LearnLibrary(listOf(LearnLotSource(c), EmbeddedLessonSource()))
        val shown = lib.packs()
        assertTrue(shown.none { BaseContent.isBase(it.id) && it.scope == "cp" }, "no base pack of cp next to its lot")
        assertTrue(shown.any { BaseContent.isBase(it.id) && it.scope == "ce1" }, "the classes without a lot keep their base content")
        assertNotNull(lib.ref("cp-maths")); assertTrue(lib.ref("cp-maths")!!.origin.startsWith("lot"))
        // the id of a base pack now resolves to the complete pack (progress keyed by the base id keeps working)
        assertEquals("cp-maths", lib.ref("base-cp-maths")?.id)
        val ids = shown.flatMap { lib.pack(it.id)!!.lessons.map { l -> l.id } }
        assertEquals(ids.size, ids.toSet().size, "a lesson is never listed twice")
        val baseLessons = EmbeddedLessonSource().let { s -> s.list().filter { it.id == "base-cp-maths" }.flatMap { s.open(it).pack.lessons.map { l -> l.id } } }
        assertTrue(baseLessons.isNotEmpty() && baseLessons.all { it in ids }, "the base lessons are part of the complete lot")
        // « Mes classes »: cp is complete, ce1 base only
        val cat = LearnLotCatalog(c, EmbeddedLessonSource()).classes()
        assertFalse(cat.first { it.scope == "cp" }.baseOnly); assertTrue(cat.first { it.scope == "ce1" }.baseOnly)
        assertEquals(table.lots.keys, cat.map { it.scope }.toSet(), "every class is listed")
        // removing the lot (end of a rental) brings the base content back
        c.remove(castbridge.core.lots.LotId("learn", "cp")); lib.forget()
        assertTrue(lib.packs().any { BaseContent.isBase(it.id) && it.scope == "cp" })
    }

    @Test fun aLoosePackOfADriveReplacesItsBasePackOnly() {
        val dir = tmp()
        val d = File(content, "ce1-maths")
        File(dir, PackFormat.fileName("ce1-maths", 99)).writeBytes(PackBuilder.build(PackBuilder.sources(d), knownLessons = LearnTool.allLessonIds(content)).bytes)
        val lib = LearnLibrary(listOf(DirectoryLessonSource({ listOf(Triple("Clé USB", dir, true)) }), EmbeddedLessonSource()))
        assertNull(lib.packs().firstOrNull { it.id == "base-ce1-maths" }, "its full pack is there")
        assertNotNull(lib.packs().firstOrNull { it.id == "base-ce1-francais" }, "the other subjects of the class keep their base content")
    }

    @Test fun classModeCanOpenABaseLessonOfAnyClassAndSubject() {
        val lib = LearnLibrary(listOf(EmbeddedLessonSource()))
        for (lot in table.lots.values) for (subject in lot.packs.map { subjectOf(it) }.toSet()) {
            val ref = lib.packs().firstOrNull { it.scope == lot.scope && it.manifest.subject == subject }
            assertNotNull(ref, "${lot.scope}/$subject")
            val pack = lib.pack(ref.id); assertNotNull(pack, ref.id)
            val deck = LessonDeck(pack, pack.lessons.first())
            assertTrue(deck.pages.isNotEmpty(), "${ref.id} lesson renders")
        }
        assertEquals(emptyList(), lib.problems.entries.toList())
    }
}
