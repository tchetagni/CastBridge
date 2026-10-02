package castbridge.core

import castbridge.core.learn.*
import java.io.File
import java.nio.file.Files
import java.security.KeyPairGenerator
import java.security.Signature
import java.time.ZoneOffset
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.test.*

class LearnLogicTest {
    // ------------------------------------------------------------------ a tiny pack used by the tests
    private val packJson = """{"format":1,"id":"t-maths","version":2,"title":"Test","lang":"fr","cursus":"secondaire","level":"3e","subject":"maths",
        "chapters":[{"id":"t-maths-c1","title":"Chapitre 1"}],
        "mockExams":[{"id":"t-maths-m1","title":"Blanc","minutes":30,"sections":[{"title":"A","exercises":["t-maths-x1","t-maths-x2","t-maths-p"]}]}]}"""
    private val lessonsJson = """{"chapter":"t-maths-c1","lessons":[
        {"id":"t-maths-l1","title":"Leçon","exercises":["t-maths-x1","t-maths-x2"],"selfCheck":["t-maths-q"],"blocks":[
          {"type":"key","style":"retenir","title":"Retenir","md":"On a **toujours** ${'$'}a^2+b^2=c^2${'$'}."},
          {"type":"example","title":"Ex","statement":"Calcule.","steps":["Étape 1","Étape 2"],"answer":"10"},
          {"type":"illustration","alt":"a","figure":{"kind":"count","n":3,"shape":"star","color":"yellow"}},
          {"type":"illustration","alt":"b","figure":{"kind":"plot","xmin":-2,"xmax":2,"ymin":-1,"ymax":4,"curves":[{"expr":"x^2"}]}},
          {"type":"video","title":"Vidéo à venir","src":null},
          {"type":"exercise","ref":"t-maths-x1"}]},
        {"id":"t-maths-l2","title":"Suite","prerequisites":["t-maths-l1"],"blocks":[{"type":"text","md":"Texte."}]}],
      "exercises":[
        {"id":"t-maths-x1","kind":"numeric","points":4,"prompt":"4,8 ?","answer":4.8,"tolerance":0.01,"explanation":"ok","lesson":"t-maths-l1"},
        {"id":"t-maths-x2","kind":"mcq","points":6,"prompt":"?","choices":["a","b","c","d"],"answer":2,"explanation":"c","lesson":"t-maths-l1"},
        {"id":"t-maths-q","kind":"mcq","tier":"autoeval","prompt":"Q ?","choices":["a","b","c","d"],"answer":0,"explanation":"a"},
        {"id":"t-maths-p","kind":"problem","tier":"examen","prompt":"Problème","lesson":"t-maths-l2","parts":[
          {"id":"t-maths-p-a","kind":"truefalse","points":4,"prompt":"Vrai ?","answer":true,"explanation":"oui"},
          {"id":"t-maths-p-b","kind":"open","points":6,"prompt":"Rédige.","model":"Modèle","explanation":"voir modèle"}]}]}"""

    private fun files(pack: String = packJson, lessons: String = lessonsJson) =
        mapOf("pack.json" to pack.toByteArray(), "lessons/c1.json" to lessons.toByteArray())
    private val pack: Pack get() = LessonJson.parsePack(files().mapValues { String(it.value) })

    @Test fun parsesAndValidatesTheTestPack() {
        val r = LessonValidator().validate(pack)
        assertEquals(emptyList(), r.errors)
        assertEquals(2, pack.lessons.size); assertEquals(4, pack.exercises.size)
        assertEquals(10.0, pack.exercise("t-maths-p")!!.totalPoints)
    }

    @Test fun validatorCatchesMistakes() {
        fun errs(l: String) = LessonValidator().validate(LessonJson.parsePack(files(lessons = l).mapValues { String(it.value) })).errors
        assertTrue(errs(lessonsJson.replace("\"answer\":2", "\"answer\":7")).any { "index de réponse" in it })
        assertTrue(errs(lessonsJson.replace("[\"t-maths-l1\"]", "[\"t-maths-nope\"]")).any { "prérequis" in it })
        assertTrue(errs(lessonsJson.replace("a^2+b^2", "\\\\foo{a}")).any { "\\foo non prise en charge" in it })
        assertTrue(errs(lessonsJson.replace("**toujours**", "**toujours")).any { "gras" in it })
        assertTrue(errs(lessonsJson.replace("\"choices\":[\"a\",\"b\",\"c\",\"d\"],\"answer\":2", "\"choices\":[\"a\",\"a\",\"c\",\"d\"],\"answer\":2")).any { "double" in it })
        assertTrue(errs(lessonsJson.replace("\"x^2\"", "\"x^^2\"")).isNotEmpty())
        assertTrue(errs(lessonsJson.replace("\"src\":null", "\"src\":\"file:/x.mp4\"")).any { "vidéo" in it })
        // a mock exam that is not out of 20
        val p2 = LessonJson.parsePack(files(lessons = lessonsJson.replace("\"points\":6,\"prompt\":\"?\"", "\"points\":5,\"prompt\":\"?\"")).mapValues { String(it.value) })
        assertTrue(LessonValidator().validate(p2).errors.any { "barème" in it })
        assertFailsWith<LessonJson.ParseError> { LessonJson.parsePack(files(lessons = lessonsJson.replace("\"kind\":\"numeric\"", "\"kind\":\"essay\"")).mapValues { String(it.value) }) }
    }

    // ------------------------------------------------------------------ formulas, markdown, expressions, figures
    private val fixed = object : TexLayout.Metrics { override fun width(text: String, size: Double, italic: Boolean) = text.length * size * 0.5 }

    @Test fun texParsesTheSubset() {
        assertEquals("a/b", Tex.parse("\\frac{a}{b}").plain())
        assertEquals("(x+1)/2", Tex.parse("\\frac{x+1}{2}").plain())
        assertEquals("x²+y³", Tex.parse("x^2+y^3").plain())
        assertEquals("uₙ₊₁", Tex.parse("u_{n+1}").plain())
        assertEquals("√(x+1)", Tex.parse("\\sqrt{x+1}").plain())
        assertEquals("³√8", Tex.parse("\\sqrt[3]{8}").plain())
        assertEquals("90°", Tex.parse("90^\\circ").plain())
        assertEquals("1,4", Tex.parse("1{,}4").plain())
        assertEquals("ℝ", Tex.parse("\\mathbb{R}").plain())
        assertEquals("lim", (Tex.parse("\\lim_{n \\to \\infty} u_n") as Tex.Row).items.first().let { (it as Tex.Script).base.plain() })
        assertEquals("x au carré", Tex.parse("x^2").spoken())
        assertEquals("a sur b", Tex.parse("\\frac{a}{b}").spoken())
        for (bad in listOf("\\frac{a}", "x^", "\\unknown", "{a", "a}", "\\sqrt[3{8}")) assertFailsWith<IllegalArgumentException>(bad) { Tex.parse(bad) }
    }

    @Test fun texLayoutStacksFractionsAndRaisesExponents() {
        val b = TexLayout.layout(Tex.parse("\\frac{1}{2}"), 20.0, fixed)
        val runs = b.items.filterIsInstance<TexLayout.Item.Run>()
        val one = runs.first { it.text == "1" }; val two = runs.first { it.text == "2" }
        assertTrue(one.y < 0 && two.y > one.y, "numerator above the denominator")
        assertEquals(1, b.items.count { it is TexLayout.Item.Rule }, "one fraction bar")
        assertTrue(b.ascent > 20 * 0.78 && b.descent > 0)
        val e = TexLayout.layout(Tex.parse("x^2"), 20.0, fixed).items.filterIsInstance<TexLayout.Item.Run>()
        assertTrue(e[1].y < e[0].y && e[1].size < e[0].size && e[1].x >= 10.0, "exponent smaller, raised, after the base")
        val s = TexLayout.layout(Tex.parse("\\sqrt{2}"), 20.0, fixed)
        assertTrue(s.items.count { it is TexLayout.Item.Rule } >= 4, "root sign drawn with lines")
        // wider content = wider box (no overlap between neighbours in a row)
        val row = TexLayout.layout(Tex.parse("a+\\frac{b}{c}+d"), 20.0, fixed)
        val xs = row.items.filterIsInstance<TexLayout.Item.Run>().filter { it.text in setOf("a", "d") }.map { it.x }
        assertTrue(xs[1] > xs[0] + 40)
    }

    @Test fun restrictedMarkdown() {
        val ps = Markdown.parse("Un **mot** et *un autre* avec \$x^2\$.\n\n- point 1\n- point 2\n1. premier")
        assertEquals(listOf(Markdown.Kind.PARA, Markdown.Kind.BULLET, Markdown.Kind.BULLET, Markdown.Kind.NUMBER), ps.map { it.kind })
        assertTrue(ps[0].spans.any { it.bold && it.text == "mot" })
        assertTrue(ps[0].spans.any { it.italic && it.text == "un autre" })
        assertEquals("x²", ps[0].spans.first { it.tex != null }.tex!!.plain())
        assertEquals("Un mot et un autre avec x².", ps[0].plain)
        assertEquals("5 \$", Markdown.parse("5 \\$")[0].plain)
        assertFailsWith<Markdown.Error> { Markdown.parse("prix : 5 $") }
        assertFailsWith<Markdown.Error> { Markdown.parse("**gras") }
    }

    @Test fun expressions() {
        fun v(s: String, x: Double) = Expr.parse(s).eval(x)
        assertEquals(7.0, v("2x+1", 3.0)); assertEquals(-4.0, v("-x^2", 2.0)); assertEquals(8.0, v("2^3", 0.0))
        assertEquals(2.0, v("2^x^0", 5.0), 1e-9)          // right-associative: 2^(x^0) = 2
        assertEquals(0.0, v("ln(x)", 1.0), 1e-12); assertEquals(Math.E, v("exp(1)", 0.0), 1e-12)
        assertEquals(6.0, v("3(x+1)", 1.0)); assertEquals(100.0 - 2 * 10, v("100 - 2*x", 10.0))
        assertTrue(v("sqrt(x)", -1.0).isNaN())
        assertFailsWith<IllegalArgumentException> { Expr.parse("xln(x)") }
        assertFailsWith<IllegalArgumentException> { Expr.parse("(x+1") }
    }

    @Test fun timelineLabelsNeverOverlapAndStayInside() {
        val events = listOf(1884 to "Traité germano-duala", 1916 to "Défaite allemande au Cameroun", 1919 to "Partage franco-britannique",
            1922 to "Mandats de la SDN", 1946 to "Tutelle de l'ONU", 1955 to "Interdiction de l'UPC", 1960 to "Indépendance du Cameroun oriental",
            1961 to "Réunification", 1972 to "État unitaire").map { Figure.Event(it.first, it.second) }
        val sc = Scene.build(Figure.Timeline(1880, 1975, events, listOf(Figure.Period(1884, 1916, "Protectorat allemand"))))
        val boxes = sc.textBoxes()
        for (i in boxes.indices) for (j in i + 1 until boxes.size) {
            // labels of one event are stacked lines of the same box: only compare different y rows or x ranges
            val a = boxes[i]; val b = boxes[j]
            val overlapX = a[0] < b[2] - 1 && b[0] < a[2] - 1; val overlapY = a[1] < b[3] - 1 && b[1] < a[3] - 1
            assertFalse(overlapX && overlapY, "text boxes $i and $j overlap: ${a.toList()} ${b.toList()}")
        }
        for (b in boxes) assertTrue(b[0] >= -1 && b[2] <= sc.w + 1 && b[1] >= -1 && b[3] <= sc.h + 1, "inside ${b.toList()} (${sc.w}×${sc.h})")
    }

    @Test fun plotsBreakAtDiscontinuitiesAndClip() {
        val sc = Scene.build(Figure.Plot(-3.0, 3.0, -5.0, 5.0, curves = listOf(Figure.Curve("1/x"))))
        val path = sc.ops.filterIsInstance<Op.Path>().first { it.width > 2 }
        assertTrue(path.cmds.count { it is PathCmd.M } >= 2, "1/x is drawn in two pieces")
        assertTrue(sc.ops.any { it is Op.Clip } && sc.ops.any { it === Op.Unclip })
    }

    @Test fun svgPathsIncludingArcs() {
        val c = SvgPath.parse("M10 10 h20 v20 l-20 0 z M50,50 a10,10 0 1,0 20,0 Q 60 40 70 50 T 90 50 C 1 2 3 4 5 6 s 1 1 2 2")
        assertTrue(c.first() == PathCmd.M(10.0, 10.0)); assertTrue(c.contains(PathCmd.L(30.0, 10.0)))
        val arcEnd = c.filterIsInstance<PathCmd.C>().first { Math.abs(it.x - 70) < 1e-6 }
        assertEquals(50.0, arcEnd.y, 1e-6)
        assertFailsWith<IllegalArgumentException> { SvgPath.parse("M 1") }
    }

    // ------------------------------------------------------------------ packs: manifest, sha256, signature, refusal
    @Test fun packRoundTripAndTamperingIsRefused() {
        val b = PackBuilder.build(files(), createdAt = "2026-09-30")
        val v = PackReader.read(b.bytes)
        assertEquals("t-maths", v.manifest.id); assertEquals(2, v.manifest.version)
        assertEquals(PackFormat.sha256(files()["pack.json"]!!), v.manifest.files.first { it.path == "pack.json" }.sha256)

        fun rezip(edit: (MutableMap<String, ByteArray>) -> Unit): ByteArray {
            val m = LinkedHashMap<String, ByteArray>()
            ZipInputStream(b.bytes.inputStream()).use { z -> while (true) { val e = z.nextEntry ?: break; m[e.name] = z.readBytes() } }
            edit(m)
            val out = java.io.ByteArrayOutputStream()
            ZipOutputStream(out).use { z -> m.forEach { (k, x) -> z.putNextEntry(ZipEntry(k)); z.write(x); z.closeEntry() } }
            return out.toByteArray()
        }
        val altered = rezip { it["lessons/c1.json"] = String(it["lessons/c1.json"]!!).replace("\"answer\":2", "\"answer\":1").toByteArray() }
        assertTrue(assertFailsWith<PackReader.Refused> { PackReader.read(altered) }.message!!.contains("sha256"))
        assertFailsWith<PackReader.Refused> { PackReader.read(rezip { it["lessons/extra.json"] = "{}".toByteArray() }) }
        assertFailsWith<PackReader.Refused> { PackReader.read(rezip { it.remove("lessons/c1.json") }) }
        assertFailsWith<PackReader.Refused> { PackReader.read(rezip { it["../evil.json"] = "{}".toByteArray() }) }
        assertFailsWith<PackReader.Refused> { PackReader.read(rezip { it.remove("manifest.json") }) }
        assertFailsWith<PackReader.Refused> { PackReader.read("pas un zip".toByteArray()) }
    }

    @Test fun ed25519SignaturesWhenAKeyIsConfigured() {
        val kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val raw = kp.public.encoded.takeLast(32).toByteArray()
        val keys = PackSignatures(mapOf("server-2026" to Base64.getEncoder().encodeToString(raw)))
        val signer = { data: ByteArray -> val s = Signature.getInstance("Ed25519"); s.initSign(kp.private); s.update(data); "server-2026" to Base64.getEncoder().encodeToString(s.sign()) }
        val signed = PackBuilder.build(files(), signer = signer)
        PackReader.read(signed.bytes, keys)                                            // accepted
        PackReader.read(PackBuilder.build(files()).bytes)                              // unsigned OK while no key is configured
        assertTrue(assertFailsWith<PackReader.Refused> { PackReader.read(PackBuilder.build(files()).bytes, keys) }.message!!.contains("non signé"))
        val other = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val forged = PackBuilder.build(files(), signer = { d -> val s = Signature.getInstance("Ed25519"); s.initSign(other.private); s.update(d); "server-2026" to Base64.getEncoder().encodeToString(s.sign()) })
        assertTrue(assertFailsWith<PackReader.Refused> { PackReader.read(forged.bytes, keys) }.message!!.contains("invalide"))
    }

    @Test fun libraryPrefersHigherVersionsAndSkipsBadPacks() {
        val dir = Files.createTempDirectory("packs").toFile(); val usb = Files.createTempDirectory("usb").toFile()
        val v2 = PackBuilder.build(files()).bytes
        val v3 = PackBuilder.build(files(pack = packJson.replace("\"version\":2", "\"version\":3"))).bytes
        File(dir, PackFormat.fileName("t-maths", 2)).writeBytes(v2)
        File(usb, PackFormat.fileName("t-maths", 3)).writeBytes(v3)
        val src = DirectoryLessonSource({ listOf(Triple("Mémoire interne", dir, false), Triple("Clé USB", usb, true)) })
        val lib = LearnLibrary(listOf(src, EmbeddedLessonSource()))
        assertEquals(3, lib.ref("t-maths")!!.version); assertEquals("Clé USB", lib.ref("t-maths")!!.origin)
        assertNotNull(lib.pack("t-maths"))
        assertTrue(lib.packs().any { it.origin == "embarqué" }, "embedded socle listed too")
        // a corrupted pack on the drive: refused, reported, and the other version is used
        File(usb, PackFormat.fileName("t-maths", 3)).writeBytes(v3.copyOf().also { it[it.size / 2] = (it[it.size / 2] + 1).toByte() })
        lib.forget()
        assertNull(runCatching { lib.pack("t-maths") }.getOrNull()?.takeIf { it.version == 3 })
        assertTrue(lib.problems.keys.any { it.startsWith("t-maths v3") })
        assertEquals(2, lib.ref("t-maths")!!.version)
    }

    @Test fun installerKeepsFreeSpaceAndReplacesOlderVersions() {
        val dir = Files.createTempDirectory("inst").toFile()
        val inst = PackInstaller()
        val v2 = PackBuilder.build(files()).bytes
        val full = inst.install(v2, listOf(PackInstaller.Target("Clé USB", dir, free = 900L shl 20)))
        assertTrue(full is PackInstaller.Result.Refused && full.reason.contains("1 Go"), "$full")
        val ok = inst.install(v2, listOf(PackInstaller.Target("Clé USB", File(dir, "usb"), free = 100L shl 20), PackInstaller.Target("Interne", dir, free = 8L shl 30)))
        assertTrue(ok is PackInstaller.Result.Installed && ok.where == "Interne")
        val v3 = PackBuilder.build(files(pack = packJson.replace("\"version\":2", "\"version\":3"))).bytes
        inst.install(v3, listOf(PackInstaller.Target("Interne", dir, free = 8L shl 30)))
        assertEquals(listOf(PackFormat.fileName("t-maths", 3)), dir.listFiles()!!.filter { it.isFile }.map { it.name })
        assertTrue(inst.install("x".toByteArray(), listOf(PackInstaller.Target("Interne", dir, 8L shl 30))) is PackInstaller.Result.Refused)
    }

    // ------------------------------------------------------------------ marking, deck, mock exam
    @Test fun markingAllKinds() {
        val p = pack
        assertEquals(4.8, Marking.parseNumber("4,8")); assertEquals(0.75, Marking.parseNumber("3/4")); assertEquals(1200.0, Marking.parseNumber("1 200"))
        assertEquals(-3.0, Marking.parseNumber("−3")); assertNull(Marking.parseNumber("abc")); assertNull(Marking.parseNumber("1/0"))
        val x1 = p.exercise("t-maths-x1")!!
        assertTrue(Marking.mark(x1, Answer.Number("4,80")).correct); assertTrue(Marking.mark(x1, Answer.Number("4.805")).correct)
        assertFalse(Marking.mark(x1, Answer.Number("4.9")).correct); assertFalse(Marking.mark(x1, Answer.Number("?")).answered)
        val prob = p.exercise("t-maths-p")!!
        val m = Marking.mark(prob, Answer.Parts(listOf(Answer.Bool(true), Answer.Self(0.5))))
        assertEquals(7.0, m.earned); assertEquals(10.0, m.max); assertFalse(m.correct)
        val match = Exercise("m", "c", ExerciseKind.MATCHING, "?", points = 2.0, pairs = listOf("a" to "1", "b" to "2"), explanation = "e")
        assertEquals(1.0, Marking.mark(match, Answer.Pairs(mapOf("a" to "1", "b" to "1"))).earned)
        assertEquals(Shuffle.order(4, 42), Shuffle.order(4, 42))
        assertEquals((0..3).toSet(), Shuffle.order(4, 7).toSet())
    }

    @Test fun deckPagesAndReveals() {
        val d = LessonDeck(pack, pack.lesson("t-maths-l1")!!)
        assertTrue(d.pages.first() is LessonDeck.Page.Intro && d.pages.last() is LessonDeck.Page.End)
        assertTrue(d.pages.none { (it as? LessonDeck.Page.Content)?.block is Block.Video }, "video without a source skipped")
        val ex = d.pages.indexOfFirst { (it as? LessonDeck.Page.Content)?.block is Block.Example }
        assertEquals(3, d.reveals(ex), "2 steps + the answer")
        assertTrue(d.pages.any { it is LessonDeck.Page.Exercise })
        assertTrue(d.spoken(1).contains("toujours") && d.spoken(1).contains("au carré"))
        assertTrue(d.readAloud.not(), "3e: no reading aloud by default")
    }

    @Test fun mockExamIsTimedAndMarkedOutOf20() {
        var now = 1_000_000L
        val s = MockExamSession(pack, pack.mockExams.first(), now) { now }
        assertEquals(30 * 60_000L, s.remainingMs)
        assertTrue(s.answer("t-maths-x1", Answer.Number("4,8")))
        assertTrue(s.answer("t-maths-x2", Answer.Choice(0)))
        assertFalse(s.answer("t-maths-zzz", Answer.Choice(0)))
        now += 31 * 60_000L
        assertTrue(s.timeUp); assertFalse(s.answer("t-maths-p", Answer.Parts(listOf(Answer.Bool(true), null))), "no answer after the end")
        val r = s.finish()
        assertEquals(4.0, r.earned); assertEquals(20.0, r.max); assertEquals(4.0, r.score)
        assertEquals(listOf("t-maths-l2", "t-maths-l1"), r.revise, "fiches to revise, most points lost first")
        s.selfMark("t-maths-p", 1.0, part = 1)
        assertEquals(10.0, s.result().score)
        val auto = MockExamSession.auto(pack, 1)!!
        assertTrue(auto.exerciseIds.none { it == "t-maths-q" }, "self-check never in a mock exam")
    }

    // ------------------------------------------------------------------ progress, reviews, badges, dashboard, store
    private val day = 86_400_000L

    @Test fun profilesAreLimitedAndHoldNoSensitiveData() {
        val pr = LearnProgress(zone = ZoneOffset.UTC)
        repeat(6) { assertNotNull(pr.addProfile("Enfant$it", it, "CM2", 0).first) }
        assertEquals("6 profils au maximum", pr.addProfile("Septième", 0, null, 0).second)
        val p2 = LearnProgress(zone = ZoneOffset.UTC)
        assertNotNull(p2.addProfile("  Awa  ", 0, "3e", 0).first); assertEquals("Awa", p2.profiles[0].name)
        assertNotNull(p2.addProfile("awa", 0, null, 0).second, "duplicate name")
        assertNotNull(p2.addProfile("x".repeat(21), 0, null, 0).second)
        assertNotNull(p2.addProfile("699123456", 0, null, 0).second, "no phone numbers")
        assertNotNull(p2.addProfile("Paul", 0, "CM9", 0).second)
    }

    @Test fun lessonCompletionStarsAndResume() {
        val pr = LearnProgress(zone = ZoneOffset.UTC); val id = pr.addProfile("Awa", 1, "3e", 0).first!!.id
        val l = pack.lesson("t-maths-l1")!!; val pages = LessonDeck(pack, l).pages.size
        pr.lessonPage(id, pack, l, 2, pages, 60_000, 1000)
        assertEquals(Resume("t-maths", "t-maths-l1", 2, 1000), pr.state.of(id).resume)
        pr.lessonPage(id, pack, l, pages - 1, pages, 10 * 60_000, 2000)
        val st = pr.state.of(id).lessons.getValue("t-maths-l1")
        assertTrue(st.completed); assertEquals(1, st.stars); assertNull(pr.state.of(id).resume)
        assertEquals(60_000 + LearnProgress.MAX_PAGE_MS, st.timeMs, "idle time capped")
        assertTrue("first_lesson" in pr.state.of(id).badges)
        for (x in listOf("t-maths-x1", "t-maths-x2", "t-maths-q")) pr.exerciseResult(id, pack, pack.exercise(x)!!, Mark(1.0, 1.0), 3000)
        assertEquals(3, pr.state.of(id).lessons.getValue("t-maths-l1").stars)
        assertTrue("perfect" in pr.state.of(id).badges)
        assertEquals(listOf("profile_created", "lesson_view", "lesson_complete", "badge_earned"), pr.state.events.take(4).map { it.name })
    }

    @Test fun spacedReviewsBringBackFailedExercises() {
        val pr = LearnProgress(zone = ZoneOffset.UTC); val id = pr.addProfile("Awa", 1, "3e", 0).first!!.id
        val x = pack.exercise("t-maths-x1")!!
        pr.exerciseResult(id, pack, x, Mark(0.0, 4.0), 0)
        assertTrue(pr.dueReviews(id, day / 2).isEmpty(), "not before tomorrow")
        assertEquals(listOf("t-maths-x1"), pr.dueReviews(id, day).map { it.exercise })
        var t = day
        for (box in 2..4) { pr.exerciseResult(id, pack, x, Mark(4.0, 4.0), t, review = true); assertEquals(box, pr.state.of(id).exercises.getValue(x.id).box); t += LearnProgress.INTERVAL_DAYS[box] * day }
        pr.exerciseResult(id, pack, x, Mark(4.0, 4.0), t, review = true)
        assertEquals(0, pr.state.of(id).exercises.getValue(x.id).box, "mastered: out of the reviews")
        assertEquals(0, pr.reviewCount(id, t + 100 * day))
        // right the first time: never in the reviews
        pr.exerciseResult(id, pack, pack.exercise("t-maths-x2")!!, Mark(6.0, 6.0), t)
        assertEquals(0, pr.reviewCount(id, t + 100 * day))
    }

    @Test fun streaksExamCountdownDashboardAndStore() {
        val pr = LearnProgress(zone = ZoneOffset.UTC); val p = pr.addProfile("Awa", 1, "3e", 0).first!!
        pr.updateProfile(p.copy(exam = "BEPC", examDate = "1970-01-11"))
        assertEquals(10, pr.daysToExam(pr.profile(p.id)!!, 0))
        val l = pack.lesson("t-maths-l2")!!
        for (d in 0 until 3) pr.lessonPage(p.id, pack, l, 0, 3, 1000, d * day + 5)
        assertEquals(3, pr.state.of(p.id).streak); assertTrue("streak_3" in pr.state.of(p.id).badges)
        pr.exerciseResult(p.id, pack, pack.exercise("t-maths-x1")!!, Mark(4.0, 4.0), 4 * day)
        pr.exerciseResult(p.id, pack, pack.exercise("t-maths-x2")!!, Mark(0.0, 6.0), 4 * day)
        val s = MockExamSession(pack, pack.mockExams[0], 0) { 0 }; s.answer("t-maths-x1", Answer.Number("4.8")); s.answer("t-maths-x2", Answer.Choice(2)); s.answer("t-maths-p", Answer.Parts(listOf(Answer.Bool(true), Answer.Self(1.0))))
        pr.mockResult(p.id, pack, pack.mockExams[0], s.finish(), 1000, 5 * day)
        val d = pr.dashboard(p.id, 5 * day)
        @Suppress("UNCHECKED_CAST") val subj = (d["subjects"] as List<Map<String, Any?>>).single()
        assertEquals("maths", subj["subject"]); assertEquals(3000L, subj["timeMs"])
        assertEquals(80, subj["successRate"], "4 right out of 5 attempts (3 of them in the mock exam)")
        assertTrue("mock_15" in pr.state.of(p.id).badges)
        // store round trip, and a damaged file is a fresh start
        val f = File(Files.createTempDirectory("learn").toFile(), "progress.json")
        LearnStore.save(f, pr.state)
        val back = LearnProgress(LearnStore.load(f), ZoneOffset.UTC)
        assertEquals(LearnStore.write(pr.state), LearnStore.write(back.state))
        f.writeText("{broken")
        assertTrue(LearnStore.load(f).profiles.isEmpty())
        repeat(600) { pr.event("lesson_view", it.toLong(), p.id, emptyMap()) }
        assertEquals(LearnProgress.MAX_EVENTS, pr.state.events.size)
        assertFailsWith<IllegalArgumentException> { pr.event("made_up", 0, p.id, emptyMap()) }
    }

    @Test fun apiRoutes() {
        val pr = LearnProgress(zone = ZoneOffset.UTC); pr.addProfile("Awa", 1, "3e", 0)
        val cmds = ArrayList<String>()
        val lib = LearnLibrary(listOf(EmbeddedLessonSource()))
        val api = LearnApi(object : LearnApi.Host {
            var open = false
            override fun screenJson() = if (open) """{"screen":"lesson"}""" else null
            override fun open(profile: String?): String? { open = true; return null }
            override fun command(action: String, params: Map<String, String>): String? { cmds += action + ":" + (params["value"] ?: ""); return if (open) null else "« Apprendre » n'est pas ouvert" }
            override fun progress() = pr
            override fun library() = lib
            override fun install(bytes: ByteArray) = PackInstaller().install(bytes, emptyList())
            override fun importFile(name: String) = PackInstaller.Result.Refused("absent")
            override fun remove(id: String): String? = "pack embarqué"
            override fun now() = 0L
        })
        assertNull(api.handle("/api/other", "GET", emptyMap()))
        assertTrue(api.handle("/api/learn", "GET", emptyMap())!!.json.contains("\"open\":false"))
        assertEquals(409, api.handle("/api/learn/cmd", "POST", mapOf("action" to "next"))!!.status)
        assertEquals(200, api.handle("/api/learn/open", "POST", emptyMap())!!.status)
        assertEquals(200, api.handle("/api/learn/cmd", "POST", mapOf("action" to "choice", "value" to "2"))!!.status)
        assertEquals(400, api.handle("/api/learn/cmd", "POST", mapOf("action" to "rm -rf"))!!.status)
        assertEquals(listOf("next:", "choice:2"), cmds)
        assertTrue(api.handle("/api/learn/dashboard", "GET", emptyMap())!!.json.contains("\"name\":\"Awa\""))
        assertTrue(api.handle("/api/learn/packs", "GET", emptyMap())!!.json.contains("embarqué"))
        assertTrue(api.wantsBody("/api/learn/packs/install"))
        assertEquals(422, api.handleBody("/api/learn/packs/install", "POST", emptyMap(), "zip?".toByteArray())!!.status)
        assertEquals(405, api.handle("/api/learn/dashboard", "POST", emptyMap())!!.status)
    }

    @Test fun progressFileIsDurableAndFallsBackToItsBackup() {
        val f = File(Files.createTempDirectory("learn").toFile(), "progress.json")
        val pr = LearnProgress(LearnState(), ZoneOffset.UTC)
        val p1 = pr.addProfile("Ada", 1, null, 1000).first!!
        LearnStore.save(f, pr.state)
        pr.addProfile("Bob", 2, null, 2000)
        LearnStore.save(f, pr.state)                              // main = 2 profiles, .bak = 1 profile
        assertEquals(2, LearnStore.load(f).profiles.size); assertFalse(File(f.path + ".tmp").exists())
        for (damage in listOf("", "{\"profiles\":[", "{broken")) {
            f.writeText(damage)
            assertEquals(listOf(p1.id), LearnStore.load(f).profiles.map { it.id }, "damage=<$damage>: the previous good copy is read")
        }
    }
}
