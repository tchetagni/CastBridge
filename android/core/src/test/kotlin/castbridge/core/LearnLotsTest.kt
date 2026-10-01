package castbridge.core

import castbridge.core.learn.*
import castbridge.core.lots.LotBudget
import castbridge.core.lots.LotId
import castbridge.core.lots.LotMeta
import castbridge.core.lots.LotSource
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.net.ProxySelector
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.test.*

/** « Apprendre » lots: organisation, builder (sizes, 3 MB cap, versions), consumer (atomic install, rollback), offline use, progress. */
class LearnLotsTest {
    private val content = File(System.getProperty("learn.content") ?: "../../content/learn")
    private val registry get() = LearnLotBuilder.Registry.parse(File(content, "lots.json").takeIf { it.isFile }?.readText())
    private val real: LearnLotBuilder.Result by lazy { LearnLotBuilder.build(content, registry, "2026-10-01", update = false) }
    private fun tmp(): File = kotlin.io.path.createTempDirectory("learnlots").toFile().also { it.deleteOnExit() }

    private fun lotOf(scope: String, r: LearnLotBuilder.Result = real): Pair<LotMeta, File> {
        val b = r.lots.first { it.meta.id.scope == scope }
        return b.meta to File(tmp(), b.file).also { it.writeBytes(b.bytes) }
    }

    /** A copy of the content folder to modify (only the packs of [scopes]). */
    private fun contentCopy(vararg packs: String): File {
        val d = tmp()
        content.resolve("scopes.txt").readLines().filter { l -> l.startsWith("#") || packs.any { p -> Regex("\\b$p\\b").containsMatchIn(l) } }
            .map { l -> if (l.startsWith("#")) l else l.substringBeforeLast('|') + "| " + packs.filter { p -> Regex("\\b$p\\b").containsMatchIn(l) }.joinToString(" ") }
            .let { File(d, "scopes.txt").writeText(it.joinToString("\n")) }
        for (p in packs) content.resolve(p).copyRecursively(File(d, p))
        return d
    }

    // ---- organisation ----
    @Test fun everyPackIsInExactlyOneLot() {
        val table = LearnScopes.parseTable(File(content, "scopes.txt").readText())
        val dirs = LearnTool.packDirs(content).map { it.name }
        for (d in dirs) assertNotNull(table.scopeOf(d), "$d has a lot in scopes.txt")
        assertEquals(dirs.toSet(), table.lots.values.flatMap { it.packs }.toSet(), "scopes.txt lists exactly the pack folders")
        table.lots.keys.forEach { assertTrue(LearnScopes.valid(it), it) }
    }

    @Test fun scopeGuessAgreesWithTheTable() {
        val table = LearnScopes.parseTable(File(content, "scopes.txt").readText())
        for (d in LearnTool.packDirs(content)) {
            val m = PackBuilder.build(PackBuilder.sources(d), knownLessons = LearnTool.allLessonIds(content)).manifest
            assertEquals(table.scopeOf(d.name), LearnScopes.guess(m), "${d.name}: the level rule gives the scope of the table")
        }
    }

    @Test fun tableRefusesDuplicates() {
        assertFailsWith<IllegalArgumentException> { LearnScopes.parseTable("a | A | p1\nb | B | p1") }
        assertFailsWith<IllegalArgumentException> { LearnScopes.parseTable("a | A | p1\na | A | p2") }
        assertFailsWith<IllegalArgumentException> { LearnScopes.parseTable("A B | x | p1") }
    }

    // ---- builder ----
    @Test fun registryMatchesContentAndSizesAreReported() {
        val r = real                                           // update=false: fails if lots.json is stale (a lot changed without a version bump)
        assertTrue(r.bumped.isEmpty())
        assertTrue(r.lots.size >= 7)
        for (b in r.lots) assertTrue(b.bytes.size <= LotFormat.MAX_LOT_BYTES, "${b.file}: ${b.bytes.size} bytes")
        assertTrue(r.totalBytes < LotBudget.PHONE_MAX_BYTES / 10, "the whole catalogue is far below the phone budget (${r.totalBytes})")
        println("lots: " + r.lots.joinToString { "${it.meta.id.scope}=${it.bytes.size}" } + " total=${r.totalBytes}")
    }

    @Test fun buildIsReproducibleAndCatalogRoundTrips() {
        val a = real; val b = LearnLotBuilder.build(content, registry, "2030-01-01", update = false)
        assertEquals(a.lots.map { it.meta }, b.lots.map { it.meta })
        val entries = a.lots.map { LearnLotCatalogFile.Entry(it.meta, it.file, it.index.date, it.packIds, it.index.lessonCount) }
        val back = LearnLotCatalogFile.parse(LearnLotCatalogFile.write(entries))
        assertEquals(a.lots.map { it.meta }, back)
        assertTrue(back.all { it.id.feature == "learn" && it.bytes > 0 && it.sha256.length == 64 })
    }

    @Test fun versionBumpsOnlyWhenContentChanges() {
        val c = contentCopy("cep-maths", "maternelle-decouverte")
        val v1 = LearnLotBuilder.build(c, LearnLotBuilder.Registry(emptyMap()), "2026-10-01", update = true)
        assertEquals(setOf("cm2", "maternelle"), v1.bumped.toSet())
        assertTrue(v1.lots.all { it.meta.version == 1 })
        val same = LearnLotBuilder.build(c, v1.registry, "2026-11-01", update = true)
        assertTrue(same.bumped.isEmpty(), "unchanged content keeps its version")
        assertEquals(v1.lots.map { it.meta.sha256 }, same.lots.map { it.meta.sha256 })
        // change one lesson of cm2 only
        val f = File(c, "cep-maths/lessons").listFiles()!!.first { it.name.startsWith("1-") }
        f.writeText(f.readText().replaceFirst("\"title\": \"", "\"title\": \"Révisé · "))
        assertFailsWith<LearnLotBuilder.Failure>("a changed lot without --update fails") { LearnLotBuilder.build(c, v1.registry, "2026-11-01", update = false) }
        val v2 = LearnLotBuilder.build(c, v1.registry, "2026-11-01", update = true)
        assertEquals(listOf("cm2"), v2.bumped)
        assertEquals(2, v2.lots.first { it.meta.id.scope == "cm2" }.meta.version)
        assertEquals(1, v2.lots.first { it.meta.id.scope == "maternelle" }.meta.version)
        assertEquals("2026-11-01", v2.lots.first { it.meta.id.scope == "cm2" }.index.date)
        assertEquals("2026-10-01", v2.lots.first { it.meta.id.scope == "maternelle" }.index.date, "an untouched lot keeps its date")
    }

    @Test fun aLotOverThreeMegabytesFailsTheBuild() {
        val c = contentCopy("maternelle-decouverte")
        File(c, "maternelle-decouverte/media").mkdirs()
        File(c, "maternelle-decouverte/media/big.bin").writeBytes(ByteArray(3_500_000).also { java.util.Random(1).nextBytes(it) })
        val e = assertFailsWith<LearnLotBuilder.Failure> { LearnLotBuilder.build(c, LearnLotBuilder.Registry(emptyMap()), "2026-10-01", update = true) }
        assertTrue(e.message!!.contains("maternelle") && e.message!!.contains("pèse"), e.message)
    }

    @Test fun packNotInTheTableFailsTheBuild() {
        val c = contentCopy("cep-maths")
        content.resolve("cep-sciences").copyRecursively(File(c, "cep-sciences"))
        assertFailsWith<LearnLotBuilder.Failure> { LearnLotBuilder.build(c, LearnLotBuilder.Registry(emptyMap()), "2026-10-01", update = true) }
    }

    @Test fun lessonHashIgnoresReviewStatusButNotContent() {
        val f = File(content, "cep-maths").let { PackBuilder.sources(it).filterKeys { k -> k.endsWith(".json") }.mapValues { String(it.value, Charsets.UTF_8) } }
        val h = LessonHashes.of(f)
        assertTrue(h.size >= 4)
        val key = f.keys.first { it.startsWith("lessons/1-") }
        val reviewed = f + (key to f.getValue(key).replace("\"status\": \"draft\"", "\"status\": \"reviewed\""))
        assertEquals(h, LessonHashes.of(reviewed), "a teacher's approval is not a content change")
        val edited = f + (key to f.getValue(key).replaceFirst("\"md\": \"", "\"md\": \"Nouveau. "))
        val h2 = LessonHashes.of(edited)
        assertEquals(1, h.keys.count { h[it] != h2[it] }, "exactly the edited lesson changes")
    }

    // ---- consumer ----
    @Test fun installIsVerifiedAtomicAndListed() {
        val root = tmp(); val c = LearnLotConsumer(root)
        val (meta, file) = lotOf("cm2")
        assertTrue(c.install(meta, file), c.lastError)
        assertEquals(listOf(meta), c.installed())
        val i = c.installedAll().single()
        assertEquals(meta.bytes, i.file.length()); assertEquals("2026-10-01".length, i.date.length)
        assertEquals(meta.bytes, c.diskBytes())
        assertEquals(listOf("lot.zip", "meta.json"), File(root, "cm2/v${meta.version}").list()!!.sorted())
        assertTrue(File(root, "cm2").list()!!.none { it.startsWith(".stage") }, "no staging leftovers")
        assertTrue(c.install(meta, file), "installing the same lot again is a no-op success")
        c.remove(meta.id); assertEquals(emptyList(), c.installed()); assertFalse(File(root, "cm2").exists())
    }

    @Test fun corruptedOrTruncatedLotIsRefusedAndNothingChanges() {
        val root = tmp(); val c = LearnLotConsumer(root)
        val (meta, file) = lotOf("3e")
        val flipped = File(tmp(), "x.zip").also { it.writeBytes(file.readBytes().also { b -> b[b.size / 2] = (b[b.size / 2] + 1).toByte() }) }
        assertFalse(c.install(meta, flipped)); assertTrue(c.lastError!!.contains("sha256"))
        val cut = File(tmp(), "y.zip").also { it.writeBytes(file.readBytes().copyOf(file.length().toInt() / 2)) }
        assertFalse(c.install(meta, cut)); assertTrue(c.lastError!!.contains("taille"))
        // right sha256, but the content does not hold together: a lot whose index lies about a lesson
        val forged = forge(file) { idx -> idx.replaceFirst(Regex("\"hash\":\"[0-9a-f]{16}\""), "\"hash\":\"0000000000000000\"") }
        val fm = meta.copy(bytes = forged.length(), sha256 = LotFormat.sha256(forged))
        assertFalse(c.install(fm, forged)); assertTrue(c.lastError!!.contains("empreintes"), c.lastError)
        assertEquals(emptyList(), c.installed()); assertFalse(File(root, "3e").exists() && File(root, "3e").list()!!.isNotEmpty())
        // another feature, bad scope
        assertFalse(c.install(meta.copy(id = LotId("quiz", "3e")), file))
        assertFalse(c.install(meta.copy(id = LotId("learn", "../x")), file))
    }

    @Test fun minAppVersionIsEnforced() {
        val (meta, file) = lotOf("maternelle")
        val old = LearnLotConsumer(tmp(), appVersion = 3)
        assertFalse(old.install(meta.copy(minAppVersion = 4), file)); assertTrue(old.lastError!!.contains("plus récente"))
        assertTrue(old.install(meta.copy(minAppVersion = 3), file))
    }

    @Test fun upgradeKeepsOldVersionUntilTheNewOneIsValidated() {
        val c1 = contentCopy("cep-maths")
        val v1 = LearnLotBuilder.build(c1, LearnLotBuilder.Registry(emptyMap()), "2026-10-01", update = true)
        val f = File(c1, "cep-maths/lessons").listFiles()!!.first { it.name.startsWith("1-") }
        f.writeText(f.readText().replaceFirst("\"md\": \"", "\"md\": \"Corrigé. "))
        val v2 = LearnLotBuilder.build(c1, v1.registry, "2026-11-01", update = true)
        val root = tmp(); val c = LearnLotConsumer(root)
        val (m1, f1) = lotOf("cm2", v1); val (m2, f2) = lotOf("cm2", v2)
        assertTrue(c.install(m1, f1))
        // a damaged v2 (right size, wrong bytes) is refused: v1 still there, still readable
        val bad = File(tmp(), "bad.zip").also { it.writeBytes(f2.readBytes().also { b -> b[100] = (b[100] + 1).toByte() }) }
        assertFalse(c.install(m2, bad))
        assertEquals(listOf(1), c.installed().map { it.version })
        assertTrue(LearnLibrary(listOf(LearnLotSource(c))).packs().isNotEmpty())
        // the good v2 replaces it, v1 is deleted only afterwards
        assertTrue(c.install(m2, f2), c.lastError)
        assertEquals(listOf(2), c.installed().map { it.version })
        assertEquals(listOf("v2"), File(root, "cm2").list()!!.toList())
        // going back is refused; the same version with other bytes is refused
        assertFalse(c.install(m1, f1)); assertTrue(c.lastError!!.contains("plus récente"))
        assertFalse(c.install(m2.copy(sha256 = m1.sha256, bytes = m1.bytes), f1))
    }

    @Test fun interruptedInstallLeavesTheInstalledVersionUsable() {
        val root = tmp(); val c = LearnLotConsumer(root)
        val (meta, file) = lotOf("form5")
        assertTrue(c.install(meta, file))
        File(root, "form5/.stage-9-123").mkdirs(); File(root, "form5/.stage-9-123/lot.zip").writeBytes(byteArrayOf(1, 2, 3))   // a crash left this
        File(root, "form5/v9").mkdirs(); File(root, "form5/v9/lot.zip").writeBytes(byteArrayOf(1))                        // half-renamed folder without meta.json
        assertEquals(listOf(meta.version), c.installed().map { it.version }, "a folder without a valid meta.json is ignored")
        assertTrue(LearnLibrary(listOf(LearnLotSource(c))).packs().isNotEmpty())
    }

    @Test fun phoneDownloadsFromAFakeSourceAndDeliversToATv() {
        val src = FakeLotSource(real.lots.filter { it.meta.id.scope in setOf("cm2", "3e") })
        val phone = LearnLotConsumer(tmp()); val tv = LearnLotConsumer(tmp())
        for (m in src.catalog()) {                                  // the phone downloads (framework: LotSync), then pushes to the TV (LotPush)
            val f = File(tmp(), m.sha256).also { it.outputStream().use { o -> src.open(m.id, m.version)!!.copyTo(o) } }
            assertTrue(phone.install(m, f), phone.lastError)
            assertTrue(tv.install(m, f), tv.lastError)
        }
        assertEquals(src.catalog().map { it.id }.toSet(), tv.installed().map { it.id }.toSet())
        val st = LearnClassStatus.of(phone.installedAll(), src.catalog() + real.lots.first { it.meta.id.scope == "form5" }.meta, tv.installed().map { it.id.scope }.toSet())
        val form5 = st.first { it.scope == "form5" }
        assertFalse(form5.downloaded); assertTrue(form5.action { "4,2 Mo" }!!.startsWith("Télécharger "))
        assertNull(st.first { it.scope == "cm2" }.action { "x" }); assertTrue(st.first { it.scope == "cm2" }.onTv)
    }

    // ---- reading the lots: offline, classes, starter fallback ----
    @Test fun apprendreRunsFromInstalledLotsWithNoNetworkAtAll() {
        val calls = ArrayList<String>()
        val old = ProxySelector.getDefault()
        ProxySelector.setDefault(object : ProxySelector() {                     // any URL-based network use would come here
            override fun select(uri: java.net.URI?) = calls.add("proxy:$uri").let { emptyList<java.net.Proxy>() }
            override fun connectFailed(uri: java.net.URI?, sa: java.net.SocketAddress?, ioe: java.io.IOException?) { calls.add("fail:$uri") }
        })
        try {
            val c = LearnLotConsumer(tmp())
            for (scope in listOf("maternelle", "cm2", "3e")) { val (m, f) = lotOf(scope); assertTrue(c.install(m, f), c.lastError) }
            val lib = LearnLibrary(listOf(LearnLotSource(c)))                    // no starter, no server: lots only
            val cat = LearnLotCatalog(c)
            assertEquals(listOf("maternelle", "cm2", "3e"), cat.classes().map { it.scope })
            var lessons = 0; var exercises = 0
            for (ref in lib.packs()) {
                val p = lib.pack(ref.id)!!
                for (l in p.lessons) { val deck = LessonDeck(p, l); for (i in deck.pages.indices) deck.spoken(i); lessons++ }
                for (x in p.exercises.flatMap { listOf(it) + it.parts }) {
                    if (x.kind == ExerciseKind.MCQ) assertTrue(Marking.mark(x, Answer.Choice(x.answerIndex)).correct)
                    exercises++
                }
            }
            assertTrue(lessons >= 30 && exercises >= 300, "lessons=$lessons exercises=$exercises")
            assertEquals(emptyList(), calls, "no network access")
            assertEquals(emptyList(), lib.problems.entries.toList())
        } finally { ProxySelector.setDefault(old) }
    }

    @Test fun learnCodeNeverTouchesTheNetwork() {
        val base = File(content, "../../android/core/src/main/kotlin/castbridge/core")
        val bad = Regex("""java\.net|javax\.net|HttpURLConnection|HttpClient|okhttp|\bSocket\b|URL\(""")
        for (dir in listOf("learn", "lots")) for (f in File(base, dir).walkTopDown().filter { it.extension == "kt" && it.name !in setOf("LotSync.kt", "LotPush.kt") }) {   // phone side only: LotSync downloads from the server, LotPush sends to the TV (the TV never downloads)
            f.readLines().forEachIndexed { n, l -> if (!l.trim().startsWith("*") && !l.trim().startsWith("//") && bad.containsMatchIn(l)) fail("${f.name}:${n + 1} uses the network: $l") }
        }
    }

    @Test fun lotSourceBeatsTheStarterAndStarterFillsTheGaps() {
        val c = LearnLotConsumer(tmp())
        val starter = EmbeddedLessonSource()
        val cat = LearnLotCatalog(c, starter)
        val before = cat.classes()
        assertTrue(before.isNotEmpty() && before.all { it.fromStarter && it.meta == null }, "only the starter on a fresh device")
        assertTrue(cat.lessons("cm2").isNotEmpty())
        val (m, f) = lotOf("cm2"); assertTrue(c.install(m, f))
        val lib = LearnLibrary(listOf(LearnLotSource(c), starter))
        assertNotNull(lib.ref("cep-francais"), "a pack that only the lot holds")
        assertNotNull(lib.ref("bepc-maths"), "a starter pack the lots do not hold")
        assertTrue(lib.ref("cep-maths")!!.origin.startsWith("lot"), "same version: the lot is read first")
        val classes = cat.classes()
        val cm2 = classes.first { it.scope == "cm2" }
        assertFalse(cm2.fromStarter); assertEquals(m, cm2.meta); assertTrue(cm2.lessons >= 10 && cm2.date != null)
        assertTrue(classes.first { it.scope == "3e" }.fromStarter)
        assertEquals(cat.lessons("cm2").map { it.id }.toSet(), lib.packs().filter { it.scope == "cm2" }.flatMap { lib.pack(it.id)!!.lessons.map { l -> l.id } }.toSet())
    }

    // ---- TV budget and priority ----
    @Test fun starterStaysWithinItsShareOfTheTvBudget() {
        val src = EmbeddedLessonSource(); val refs = src.list()
        val table = LearnScopes.parseTable(File(content, "scopes.txt").readText())
        var total = 0L
        for (r in refs) { total += r.bytes().use { it.readBytes().size }; assertNotNull(table.scopeOf(r.id), "${r.id} belongs to a lot"); assertEquals(table.scopeOf(r.id), r.scope) }
        assertTrue(total <= 3L shl 20, "Apprendre starter $total bytes > 3 MB")
        assertTrue(total + (2L shl 20) <= 5L shl 20, "leaves room for the Quiz starter (≈ 2 MB) under 5 MB in all")
        assertEquals(setOf("maternelle", "cm2", "class6", "3e", "form5", "tle-cd"), refs.mapNotNull { it.scope }.toSet(), "one demonstration pack per cycle")
        assertTrue(LotBudget.TV_MAX_BYTES - total >= 5L shl 20, "at least 5 MB remain for pushed lots (${LotBudget.TV_MAX_BYTES - total})")
        println("Apprendre starter on the TV: $total bytes of ${LotBudget.TV_MAX_BYTES}")
    }

    @Test fun everyActiveClassOfTheTvFitsAlongsideTheStarter() {
        // a TV whose students are in 3e and CM2: both own lots + neighbours must fit in what the starter leaves
        val wanted = learnPriority(listOf(Profile("p1", "A", 0, "3e", createdAt = 1), Profile("p2", "B", 0, "CM2", createdAt = 2)))
        assertEquals(listOf("3e", "cm2"), wanted.take(2))
        val sizes = real.lots.associate { it.meta.id.scope to it.bytes.size.toLong() }
        assertTrue(sizes.getValue("3e") + sizes.getValue("cm2") < 5L shl 20)
    }

    @Test fun priorityPutsOwnClassFirstThenAdjacentLevels() {
        assertEquals(listOf("3e", "2nde", "4e", "1ere", "5e", "tle-commun"), learnPriority("3e").take(6))
        assertEquals("cm2", learnPriority("CM2").first()); assertEquals(listOf("cm2", "6e", "cm1"), learnPriority("CM2").take(3))
        assertEquals(listOf("class6", "form1", "class5"), learnPriority("Class 6").take(3))
        assertEquals("tle-cd", learnPriority("Tle").first(), "Terminale = the C/D lot first")
        assertEquals(listOf("tle-cd", "tle-a"), learnPriority("Tle").take(3).filter { it != "tle-commun" }.take(2)); assertTrue("1ere" in learnPriority("Tle"))
        assertEquals(setOf("form5"), setOf(learnPriority("Form 5").first()))
        assertTrue(learnPriority("Form 5").none { it == "3e" || it == "cm2" }, "the other sub-system is not offered")
        assertEquals("droit-l1", learnPriority("L1").first())
        assertEquals(emptyList(), learnPriority(null)); assertEquals(emptyList(), learnPriority("n'importe quoi"))
        assertEquals(learnPriority("3e").toSet().size, learnPriority("3e").size, "no duplicates")
        for (level in LearnCatalog.levels.map { it.key }) assertEquals(LearnScopes.ofLevel(level), learnPriority(level).first(), level)
    }

    @Test fun severalStudentsInterleaveTheirPriorities() {
        val p = listOf(Profile("a", "A", 0, "6e", createdAt = 1), Profile("b", "B", 0, "Class 3", createdAt = 2), Profile("c", "C", 0, null, createdAt = 3))
        val o = learnPriority(p)
        assertEquals(listOf("6e", "class3"), o.take(2))
        assertEquals(o.size, o.toSet().size)
        assertEquals(emptyList(), learnPriority(emptyList<Profile>()))
    }

    // ---- progress: surviving lot updates, merging phone and TV ----
    @Test fun progressSurvivesALotUpdateAndFlagsChangedLessons() {
        val c1 = contentCopy("cep-maths")
        val v1 = LearnLotBuilder.build(c1, LearnLotBuilder.Registry(emptyMap()), "2026-10-01", update = true)
        val consumer = LearnLotConsumer(tmp())
        val (m1, f1) = lotOf("cm2", v1); assertTrue(consumer.install(m1, f1))
        val lib = LearnLibrary(listOf(LearnLotSource(consumer)))
        val pack = lib.pack("cep-maths")!!; val lesson = pack.lessons.first(); val other = pack.lessons.last()
        val pr = LearnProgress(LearnState(), java.time.ZoneOffset.UTC)
        val (p, _) = pr.addProfile("Awa", 1, "CM2", 1000)
        val h1 = consumer.lessonHash(lesson.id)!!; val ho = consumer.lessonHash(other.id)!!
        assertEquals(64.coerceAtMost(16), h1.length)
        for (pg in 0 until 4) pr.lessonPage(p!!.id, pack, lesson, pg, 4, 1000, 2000L + pg, h1)
        pr.lessonPage(p!!.id, pack, other, 0, 4, 1000, 3000, ho)
        val ex = pack.exercises.first { it.id in lesson.exercises && it.kind == ExerciseKind.MCQ }
        pr.exerciseResult(p.id, pack, ex, Marking.mark(ex, Answer.Choice(ex.answerIndex)), 4000)
        val stars = pr.state.of(p.id).lessons[lesson.id]!!.stars
        assertTrue(stars >= 1)
        assertFalse(pr.lessonUpdated(p.id, lesson.id, h1))
        // content update: only `lesson` changes
        val f = File(c1, "cep-maths/lessons").listFiles()!!.first { f -> f.readText().contains("\"id\": \"${lesson.id}\"") }
        f.writeText(f.readText().replaceFirst("\"md\": \"", "\"md\": \"Précision. "))
        val v2 = LearnLotBuilder.build(c1, v1.registry, "2026-11-01", update = true)
        val (m2, f2) = lotOf("cm2", v2); assertTrue(consumer.install(m2, f2), consumer.lastError)
        val h2 = consumer.lessonHash(lesson.id)!!
        assertNotEquals(h1, h2); assertEquals(ho, consumer.lessonHash(other.id), "an untouched lesson keeps its hash")
        val saved = LearnStore.parse(LearnStore.write(pr.state)).let { LearnProgress(it, java.time.ZoneOffset.UTC) }     // survives a restart
        assertTrue(saved.lessonUpdated(p.id, lesson.id, h2), "flagged « mise à jour »")
        assertFalse(saved.lessonUpdated(p.id, other.id, consumer.lessonHash(other.id)))
        assertEquals(stars, saved.state.of(p.id).lessons[lesson.id]!!.stars, "the score is kept")
        assertTrue(saved.state.of(p.id).exercises.containsKey(ex.id))
        for (pg in 0 until 4) saved.lessonPage(p.id, lib.pack("cep-maths")!!, lesson, pg, 4, 500, 9000L + pg, h2)
        assertFalse(saved.lessonUpdated(p.id, lesson.id, h2), "read again: no longer flagged")
        assertEquals(stars, saved.state.of(p.id).lessons[lesson.id]!!.stars)
    }

    private fun student(name: String, id: String = "p1") = LearnState().also { it.profiles += Profile(id, name, 0, "3e", createdAt = 1) }
    private fun LearnState.ex(id: String, correct: Boolean, at: Long, best: Double, attempts: Int, ok: Int, box: Int = 0, who: String = "p1") {
        of(who).exercises[id] = ExerciseState(id, "pk", "maths", null).also { it.lastCorrect = correct; it.lastAt = at; it.best = best; it.attempts = attempts; it.correct = ok; it.box = box; it.dueAt = at + 1 }
    }

    @Test fun mergeKeepsBestScoreAndLastWriteWins() {
        val tv = student("Awa"); val phone = student("awa")
        tv.ex("e1", correct = false, at = 100, best = 0.5, attempts = 2, ok = 1, box = 1)
        phone.ex("e1", correct = true, at = 200, best = 1.0, attempts = 3, ok = 2, box = 0)
        tv.ex("e2", correct = true, at = 500, best = 1.0, attempts = 1, ok = 1)
        phone.ex("e2", correct = false, at = 300, best = 0.2, attempts = 1, ok = 0, box = 1)     // older and worse
        phone.ex("e3", correct = true, at = 50, best = 1.0, attempts = 1, ok = 1)                // only on the phone
        val r = LearnMerge.merge(tv, phone)
        assertEquals(1, r.profiles); assertEquals(1, tv.profiles.size)
        val e = tv.of("p1").exercises
        assertTrue(e.getValue("e1").lastCorrect && e.getValue("e1").box == 0 && e.getValue("e1").lastAt == 200L, "last write wins")
        assertEquals(1.0, e.getValue("e1").best); assertEquals(3, e.getValue("e1").attempts); assertEquals(2, e.getValue("e1").correct)
        assertTrue(e.getValue("e2").lastCorrect && e.getValue("e2").lastAt == 500L, "the older phone result does not overwrite")
        assertEquals(1.0, e.getValue("e2").best, "best score kept")
        assertEquals(setOf("e1", "e2", "e3"), e.keys, "nothing lost")
    }

    @Test fun mergeIsIdempotentAndOrderIndependent() {
        fun mk(seed: Int) = student("Awa").also { s ->
            s.ex("a", seed % 2 == 0, 100L * seed, seed / 5.0, seed, seed / 2, seed % 3)
            s.ex("b", true, 1000L - seed, 1.0, 1, 1)
            s.of("p1").lessons["l1"] = LessonState("l1", "pk", "maths").also { it.seen = true; it.completed = seed > 2; it.stars = seed; it.timeMs = 1000L * seed; it.lastAt = 10L * seed; it.page = seed; it.hash = "h$seed" }
            s.of("p1").mocks += MockRecord("pk", "m", "maths", 10.0 + seed, 100L * seed, 1)
            s.of("p1").badges += "b$seed"
        }
        fun snapshot(s: LearnState) = LearnStore.write(s).replace(Regex("\"events\":.*"), "")
        val ab = mk(1).also { LearnMerge.merge(it, mk(4)) }; val ba = mk(4).also { LearnMerge.merge(it, mk(1)) }
        assertEquals(snapshot(ab), snapshot(ba), "same result in either order")
        val again = snapshot(ab); LearnMerge.merge(ab, mk(4)); LearnMerge.merge(ab, mk(1))
        assertEquals(again, snapshot(ab), "repeating the meeting changes nothing")
        val l = ab.of("p1").lessons.getValue("l1")
        assertTrue(l.completed && l.stars == 4 && l.hash == "h4" && l.timeMs == 4000L)
        assertEquals(2, ab.of("p1").mocks.size); assertEquals(setOf("b1", "b4"), ab.of("p1").badges)
    }

    @Test fun mergeKeepsDifferentStudentsApartAndRenumbersCollidingIds() {
        val tv = student("Awa", "p1"); val phone = student("Brice", "p1")
        phone.ex("x", true, 5, 1.0, 1, 1)
        val r = LearnMerge.merge(tv, phone)
        assertEquals(listOf("Awa", "Brice"), tv.profiles.map { it.name })
        assertEquals(listOf("p1", "p2"), tv.profiles.map { it.id })
        assertTrue(tv.of("p2").exercises.containsKey("x")); assertTrue(tv.of("p1").exercises.isEmpty())
        assertEquals(emptyList(), r.skippedProfiles)
        val full = LearnState().also { s -> for (i in 1..LearnProgress.MAX_PROFILES) s.profiles += Profile("p$i", "N$i", 0, null) }
        assertEquals(listOf("Brice"), LearnMerge.merge(full, phone).skippedProfiles)
    }

    @Test fun progressFilesWrittenBeforeLotsStillLoad() {
        val old = """{"version":1,"profiles":[{"id":"p1","name":"Awa","avatar":0,"level":"3e","createdAt":1}],"progress":[{"profile":"p1","streak":1,"lastDay":"2026-09-01","reviewsDone":0,"badges":[],"time":{},"lessons":[{"l":"L","p":"P","s":"maths","page":2,"pages":3,"seen":true,"done":false,"stars":1,"t":5,"at":9}],"exercises":[{"x":"X","p":"P","s":"maths","l":"L","n":2,"ok":1,"last":true,"box":0,"due":0,"at":9}],"mocks":[]}],"events":[]}"""
        val s = LearnStore.parse(old)
        assertNull(s.of("p1").lessons["L"]!!.hash); assertEquals(1.0, s.of("p1").exercises["X"]!!.best)
        assertFalse(LearnProgress(s).lessonUpdated("p1", "L", "anything"), "an old record without hash is never flagged")
    }

    @Test fun frenchWordingOfSizesAndFreshness() {
        assertEquals("77 Ko", LearnFormat.size(78_638)); assertEquals("4,2 Mo", LearnFormat.size(4_400_000)); assertEquals("900 o", LearnFormat.size(900))
        assertEquals("données du 12 sept.", LearnFormat.dataDate("2026-09-12")); assertEquals("données du 1er oct.", LearnFormat.dataDate("2026-10-01"))
        assertEquals("date des données inconnue", LearnFormat.dataDate(null))
        val s = LearnClassStatus("3e", "3e – BEPC", null, null, null, 2, 4_400_000, false)
        assertEquals("Télécharger 3e – BEPC : 4,2 Mo", s.action())
        assertEquals("Mettre à jour 3e – BEPC : 4,2 Mo", s.copy(installedVersion = 1, installedBytes = 5).action())
        assertNull(s.copy(installedVersion = 2, availableVersion = 2).action())
        for (t in listOf(s.action(), LearnFormat.dataDate("2026-10-01"))) assertFalse(t!!.contains("nternet"), "no message implies Internet")
    }

    // ---- review report ----
    @Test fun reviewReportIsUpToDate() {
        val doc = File(content, "../../docs/LEARN-REVIEW.md")
        assertTrue(doc.isFile, "docs/LEARN-REVIEW.md missing: run gradle :core:reviewLearn")
        assertEquals(LearnReview.report(content), doc.readText(Charsets.UTF_8), "docs/LEARN-REVIEW.md is stale: run gradle :core:reviewLearn")
        assertTrue(LearnReview.report(content).contains("Total :"))
    }

    // ---- helpers ----
    private class FakeLotSource(private val lots: List<LearnLotBuilder.Built>) : LotSource {
        override fun catalog() = lots.map { it.meta }
        override fun open(id: LotId, version: Int): InputStream? = lots.firstOrNull { it.meta.id == id && it.meta.version == version }?.let { ByteArrayInputStream(it.bytes) }
    }

    /** Rewrites lot.json of a lot zip with [edit], keeping the packs: a lot that lies about its content. */
    private fun forge(src: File, edit: (String) -> String): File {
        val out = File(tmp(), "forged.zip")
        ZipFile(src).use { z -> ZipOutputStream(out.outputStream()).use { o ->
            for (e in z.entries().toList()) {
                val b = z.getInputStream(e).readBytes()
                val ne = ZipEntry(e.name)
                o.putNextEntry(ne); o.write(if (e.name == LotFormat.INDEX) edit(String(b, Charsets.UTF_8)).toByteArray(Charsets.UTF_8) else b); o.closeEntry()
            }
        } }
        return out
    }
}
