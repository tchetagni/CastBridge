package castbridge.core

import castbridge.core.lots.LotBudget
import castbridge.core.lots.LotId
import castbridge.core.lots.LotMeta
import castbridge.core.quiz.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.ProxySelector
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.*

/** Quiz lots (docs/QUIZ.md, « Lots »): adapter, atomic install, rollback, history across updates, priority, themes, merge, starter offline. */
class QuizLotsTest {
    private val key: KeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    private val otherKey: KeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    private fun pub(k: KeyPair) = Base64.getEncoder().encodeToString(k.public.encoded.copyOfRange(12, 44))
    private fun tmp(): File = File.createTempFile("qlot", "").apply { delete(); mkdirs(); deleteOnExit() }
    private fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }
    private val cm2 = QuestionFilter(Track.PRIMARY, "CM2")

    private class BuiltLot(val meta: LotMeta, val file: File, val sig: String)

    private fun qmap(i: Int, text: String = "Combien font $i + $i ?", scope: String = "cm2"): Map<String, Any?> = linkedMapOf(
        "id" to "t-$scope-$i", "track" to "primary", "level" to "CM2", "field" to null, "region" to "WORLD", "category" to "Calcul", "difficulty" to 1 + i % 5,
        "question" to text, "choices" to listOf("${2 * i}", "${2 * i + 1}", "${2 * i + 2}", "${2 * i + 3}"), "answer" to 0, "explanation" to "Addition.", "source" to "test",
        "status" to "approved", "verif" to "computed", "lang" to "fr")

    /** A valid, signed lot of the scope "cm2". [questions] default to [n] numbered questions; [mutate] lets a test break something. */
    private fun lot(version: Int, n: Int = 40, questions: List<Map<String, Any?>> = (0 until n).map { qmap(it) }, scope: String = "cm2", signer: KeyPair = key,
                    indexOverride: ((Map<String, Any?>) -> Map<String, Any?>)? = null, title: String = "Primaire · CM2"): BuiltLot {
        val body = "{\"version\":2,\"questions\":[\n" + questions.joinToString(",\n") { Json.write(it) } + "\n]}\n"
        val qbytes = body.toByteArray(Charsets.UTF_8)
        val hashes = questions.associate { it["id"] as String to QuizLotIndex.questionHash(it) }
        val idx = linkedMapOf<String, Any?>("v" to 1, "scope" to scope, "version" to version, "count" to hashes.size, "contentHash" to QuizLotIndex.contentHash(hashes), "q" to hashes.toSortedMap())
        val ibytes = Json.write(indexOverride?.invoke(idx) ?: idx).toByteArray(Charsets.UTF_8)
        val manifest = Json.write(linkedMapOf("format" to 1, "id" to scope, "track" to "primary", "level" to "CM2", "field" to null, "part" to 1, "parts" to 1, "version" to version,
            "questions" to questions.size, "lot" to linkedMapOf("feature" to "quiz", "scope" to scope, "title" to title),
            "files" to linkedMapOf("questions.json" to linkedMapOf("size" to qbytes.size, "sha256" to sha(qbytes)), "index.json" to linkedMapOf("size" to ibytes.size, "sha256" to sha(ibytes)))))
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z -> for ((nme, d) in listOf("manifest.json" to manifest.toByteArray(), "questions.json" to qbytes, "index.json" to ibytes)) { z.putNextEntry(ZipEntry(nme)); z.write(d); z.closeEntry() } }
        val zip = out.toByteArray()
        val meta = LotMeta(LotId("quiz", scope), version, zip.size.toLong(), sha(zip), title)
        val unsigned = QuizPackInfo(scope, "primary", "CM2", null, 1, 1, version, QuizLotConsumer.fileName(scope, version), zip.size.toLong(), meta.sha256, questions.size, null, "")
        val sig = Signature.getInstance("Ed25519").run { initSign(signer.private); update(unsigned.canonicalPayload().toByteArray()); sign() }
        val f = File(tmp(), "download.lot").also { it.writeBytes(zip) }
        return BuiltLot(meta, f, Base64.getEncoder().encodeToString(sig))
    }

    private fun consumer(dir: File = tmp(), signer: KeyPair = key, max: Long = Long.MAX_VALUE, sigs: MutableMap<Int, String> = HashMap(), clock: () -> Long = { 1_000L }) =
        QuizLotConsumer(dir, listOf(pub(signer)), signatureOf = { sigs[it.version] }, maxBytes = max, clock = clock) to sigs

    private fun QuizLotConsumer.put(l: BuiltLot, sigs: MutableMap<Int, String>): QuizLotConsumer.Result { sigs[l.meta.version] = l.sig; return installDetailed(l.meta, l.file) }

    // ------------------------------------------------------------------------------------------------ install / verify

    @Test fun installsASignedLotAndListsItAndPlaysFromIt() {
        val (c, sigs) = consumer()
        assertEquals("quiz", c.feature)
        val l = lot(1)
        assertTrue(c.put(l, sigs) is QuizLotConsumer.Result.Ok)
        assertEquals(listOf(l.meta), c.installed())
        assertEquals(40, c.bank().count(cm2))
        assertEquals(l.meta.bytes, c.usedBytes()); assertEquals(1, c.version("cm2"))
        assertEquals(1_000L, c.installedAt("cm2"))
        c.remove(LotId("quiz", "cm2"))
        assertTrue(c.installed().isEmpty()); assertEquals(0, c.bank().all.size)
    }

    @Test fun refusesUnsignedWronglySignedCorruptedAndForeignLots() {
        val l = lot(1)
        val (c, sigs) = consumer()
        assertFalse(c.install(l.meta, l.file), "no signature")
        sigs[1] = lot(1, signer = otherKey).sig
        assertTrue((c.installDetailed(l.meta, l.file) as QuizLotConsumer.Result.Refused).reason.contains("signature"), "signed by another key")
        sigs[1] = l.sig
        val flipped = l.file.readBytes().also { it[it.size / 2] = (it[it.size / 2] + 1).toByte() }
        val bad = File(tmp(), "bad.lot").also { it.writeBytes(flipped) }
        assertTrue((c.installDetailed(l.meta, bad) as QuizLotConsumer.Result.Refused).reason.contains("SHA-256"))
        val short = File(tmp(), "short.lot").also { it.writeBytes(l.file.readBytes().copyOf(100)) }
        assertTrue((c.installDetailed(l.meta, short) as QuizLotConsumer.Result.Refused).reason.contains("taille"))
        assertFalse(c.install(l.meta.copy(id = LotId("learn", "cm2")), l.file), "not a quiz lot")
        assertFalse(c.install(l.meta.copy(id = LotId("quiz", "../etc")), l.file), "unsafe scope")
        assertFalse(c.install(l.meta.copy(id = LotId("quiz", "3e")), l.file), "the file is the lot of another scope")
        assertFalse(c.install(l.meta.copy(version = 2), l.file), "announced version differs from the file's")
        assertFalse(QuizLotConsumer(tmp(), requireSignature = false, appVersion = 1).install(l.meta.copy(minAppVersion = 5), l.file), "needs a newer app")
        assertTrue(c.installed().isEmpty(), "nothing was installed by any refusal")
        assertTrue(c.install(l.meta, l.file), "the genuine lot is accepted")
    }

    @Test fun refusesACorruptIndexOrQuestionsOutsideTheScope() {
        val (c, sigs) = consumer()
        val wrongHash = lot(1, indexOverride = { i -> @Suppress("UNCHECKED_CAST") (i + ("q" to ((i["q"] as Map<String, String>) + ("t-cm2-0" to "deadbeef")))) })
        assertTrue((c.put(wrongHash, sigs) as QuizLotConsumer.Result.Refused).reason.contains("index"))
        val foreign = lot(1, questions = listOf(qmap(0), qmap(1).toMutableMap().apply { this["level"] = "Tle" }))
        assertTrue(c.put(foreign, sigs) is QuizLotConsumer.Result.Refused)
        assertTrue(c.installed().isEmpty())
    }

    // ------------------------------------------------------------------------------------------------ versions, atomicity, rollback

    @Test fun versionBumpReplacesAndOlderOrConflictingVersionsAreRefused() {
        val (c, sigs) = consumer()
        val v1 = lot(1); val v2 = lot(2, n = 50)
        c.put(v1, sigs)
        val r = c.put(v2, sigs) as QuizLotConsumer.Result.Ok
        assertEquals(1, r.replaced)
        assertEquals(10, r.diff!!.added.size); assertTrue(r.diff!!.changed.isEmpty() && r.diff!!.removed.isEmpty())
        assertEquals(50, c.bank().count(cm2)); assertTrue(c.hasPrevious("cm2"))
        assertFalse(c.install(v1.meta, v1.file), "a downgrade is refused")
        assertEquals(2, c.version("cm2"))
        assertTrue(c.install(v2.meta, v2.file), "the same lot again is a no-op")
        val twin = lot(2, n = 41)
        sigs[2] = twin.sig
        assertTrue((c.installDetailed(twin.meta, twin.file) as QuizLotConsumer.Result.Refused).reason.contains("même version"))
        assertEquals(v2.meta, c.installed().single())
    }

    @Test fun aFailureAtAnyStepKeepsThePreviousVersionPlayable() {
        for (step in listOf("staged", "backedUp", "zipInPlace")) {
            val dir = tmp(); val (c, sigs) = consumer(dir)
            c.put(lot(1), sigs)
            c.failAt = { if (it == step) throw java.io.IOException("panne à $step") }
            val r = c.put(lot(2, n = 50), sigs)
            assertTrue(r is QuizLotConsumer.Result.Refused, step)
            assertEquals(listOf(1), c.installed().map { it.version }, "still v1 after a failure at $step")
            assertEquals(40, c.bank().count(cm2), step)
            assertFalse(File(dir, "cm2/new.zip").exists() || File(dir, "cm2/new.json").exists(), "no staged leftovers at $step")
            c.failAt = {}
            assertTrue(c.put(lot(2, n = 50), sigs) is QuizLotConsumer.Result.Ok, "and the update works afterwards ($step)")
        }
    }

    @Test fun aCrashInTheMiddleIsRepairedWhenTheAppRestarts() {
        for (step in listOf("staged", "backedUp", "zipInPlace")) {
            val dir = tmp(); val (c, sigs) = consumer(dir)
            c.put(lot(1), sigs)
            c.failAt = { if (it == step) throw OutOfMemoryError("coupure de courant simulée") }     // not an Exception: no cleanup runs, like a power cut
            runCatching { c.put(lot(2, n = 50), sigs) }
            val (after, _) = consumer(dir)
            val v = after.installed().single().version
            assertTrue(v == 1 || v == 2, "a whole version is installed after a crash at $step (got $v)")
            assertEquals(if (v == 1) 40 else 50, after.bank().count(cm2), "and it is complete")
        }
    }

    @Test fun explicitRollbackReturnsToThePreviousVersionOnce() {
        val (c, sigs) = consumer()
        c.put(lot(1), sigs); c.put(lot(2, n = 50), sigs)
        assertTrue(c.rollback(LotId("quiz", "cm2")))
        assertEquals(1, c.version("cm2")); assertEquals(40, c.bank().count(cm2))
        assertFalse(c.rollback(LotId("quiz", "cm2")), "only one level of undo")
        assertFalse(c.rollback(LotId("quiz", "3e")))
    }

    @Test fun theBudgetIsEnforcedAndNothingIsRemovedBehindTheBack() {
        val a = lot(1)
        val (c, sigs) = consumer(max = a.meta.bytes + 10)
        assertTrue(c.put(a, sigs) is QuizLotConsumer.Result.Ok)
        val big = lot(2, n = 400)
        assertTrue(big.meta.bytes > a.meta.bytes + 10)
        val r = c.put(big, sigs)
        assertTrue(r is QuizLotConsumer.Result.Refused && r.reason.contains("budget"), "a bigger version does not fit the budget")
        assertEquals(1, c.version("cm2"), "the installed lot is untouched")
    }

    // ------------------------------------------------------------------------------------------------ history across updates

    @Test fun historySurvivesALotUpdateAndAChangedQuestionKeepsItsSlot() {
        val (c, sigs) = consumer()
        c.put(lot(1, n = 60), sigs)
        val h = QuizHistory()
        val course = cm2.courseKey
        val g1 = c.bank().draw(15, seed = 7, filter = cm2, history = h)
        h.beginGame(course); h.record(course, g1.map { it.id })
        val askedId = g1.first().id
        assertEquals(1, h.age(course, askedId))
        // v2: one asked question reworded (same id), one removed, ten added
        val changedIdx = askedId.removePrefix("t-cm2-").toInt()
        val v2 = (0 until 70).filter { it != 59 || askedId == "t-cm2-59" }.map { i -> if (i == changedIdx) qmap(i, text = "Quelle est la somme de $i et $i ?") else qmap(i) }
        val r = c.put(lot(2, questions = v2), sigs) as QuizLotConsumer.Result.Ok
        assertTrue(askedId in r.diff!!.changed, "the reworded question is reported as changed, not as new")
        assertEquals(1, h.age(course, askedId), "the history slot of the changed question is kept (same id)")
        val g2 = c.bank().draw(15, seed = 8, filter = cm2, history = h)
        assertTrue(g2.none { it.id in g1.map { q -> q.id }.toSet() }, "no question of game 1 comes back in game 2 after the update")
        assertEquals("Quelle est la somme de $changedIdx et $changedIdx ?", c.bank().all.first { it.id == askedId }.question, "the new wording is what is installed")
    }

    // ------------------------------------------------------------------------------------------------ the real lots of the repository

    private fun lotsDir() = File(File(System.getProperty("quiz.dist")).parentFile, "lots")

    @Test fun everyRealLotInstallsMatchesItsCatalogAndStaysUnderTheCap() {
        val catalog = Json.obj(File(lotsDir(), "catalog-lots.json").readText())
        @Suppress("UNCHECKED_CAST") val lots = catalog["lots"] as List<Map<String, Any?>>
        assertTrue(lots.size >= 9)
        val (c, _) = consumer(max = LotBudget.TV_MAX_BYTES)
        val unsigned = QuizLotConsumer(tmp(), requireSignature = false)
        var total = 0L
        for (m in lots) {
            val f = File(lotsDir(), m["file"] as String)
            assertTrue(f.length() <= QuizLotScopes.MAX_LOT_BYTES, "${f.name} above 3 MB")
            val meta = LotMeta(LotId("quiz", m["scope"] as String), (m["version"] as Number).toInt(), (m["bytes"] as Number).toLong(), m["sha256"] as String, m["title"] as String, (m["minAppVersion"] as Number).toInt())
            assertEquals(f.length(), meta.bytes); assertEquals(sha(f.readBytes()), meta.sha256)
            assertTrue(unsigned.install(meta, f), "${f.name} is a valid lot (index hashes written by the Python builder match Kotlin's)")
            assertEquals((m["questions"] as Number).toInt(), QuizLotFormat.read(f).bank.all.size)
            assertEquals(QuizLotScopes.title(meta.id.scope), meta.title)
            total += meta.bytes
        }
        assertEquals((catalog["totalBytes"] as Number).toLong(), total)
        assertTrue(total < LotBudget.TV_MAX_BYTES, "all the quiz lots together fit in the TV budget ($total)")
        assertEquals(QuizLotScopes.specs.map { it.scope }.sorted(), lots.map { it["scope"] as String }.sorted(), "the Kotlin table and the built lots agree")
        assertFalse(c.install(LotMeta(LotId("quiz", "cm2"), 1, 1, "x", "t"), File(lotsDir(), "catalog-lots.json")))
        // the installed lots serve every course of the table
        val bank = unsigned.bank()
        for (s in QuizLotScopes.specs.filter { it.track != Track.GENERAL }) assertTrue(bank.count(s.filter(), includeReview = true) > 100, s.scope)
        assertTrue(bank.count(QuestionFilter.GENERAL, includeReview = true) > 3000)
        assertTrue(bank.count(cm2) > 1000, "computed questions of the lots are playable under the current policy")
    }

    @Test fun lotsKeepEveryQuestionIdOfTheOlderPacks() {
        // a lot is a re-cut of the same pipeline output: ids are stable between pack parts and lots
        val dist = File(System.getProperty("quiz.dist"))
        val packIds = dist.listFiles { f -> f.name.endsWith(QUIZ_PACK_SUFFIX) }!!.flatMap { QuizPackFormat.read(it).bank.all.map { q -> q.id } }.toSet()
        val lotIds = lotsDir().listFiles { f -> f.name.endsWith(QUIZ_PACK_SUFFIX) }!!.flatMap { QuizLotFormat.read(it).bank.all.map { q -> q.id } }.toSet()
        assertEquals(packIds, lotIds)
    }

    // ------------------------------------------------------------------------------------------------ source, play policy

    @Test fun theQuestionSourceDrawsFromInstalledLotsAndFollowsUpdates() {
        val (c, sigs) = consumer()
        val src = PackedQuestionSource(EmbeddedQuestionSource(), null, lots = c)
        val before = src.bank().count(cm2)
        c.put(lot(1, n = 40), sigs)
        assertEquals(before + 40, src.bank().count(cm2), "an installed lot is played without any restart")
        assertTrue(src.origin.contains("thèmes installés"))
        c.put(lot(2, n = 45), sigs)
        assertEquals(before + 45, src.bank().count(cm2))
        c.remove(LotId("quiz", "cm2"))
        assertEquals(before, src.bank().count(cm2))
    }

    @Test fun isPlayableIsTheSinglePlacePolicyAndKeepsTheCurrentBehaviour() {
        fun q(review: Boolean, computed: Boolean = false) = Question("x", Region.CM, "c", 1, "q ?", listOf("a", "b", "c", "d"), 0, "e", "s", review = review, computedOk = computed)
        for (ch in QuizChannel.values()) {
            assertTrue(QuizPlay.isPlayable(q(false), ch)); assertFalse(QuizPlay.isPlayable(q(true), ch)); assertTrue(QuizPlay.isPlayable(q(true, computed = true), ch))
        }
        val json = """{"version":2,"questions":[
            {"id":"a","track":"general","region":"CM","category":"c","difficulty":1,"question":"q1 ?","choices":["a","b","c","d"],"answer":0,"explanation":"e","source":"s","status":"review","verif":"computed"},
            {"id":"b","track":"general","region":"CM","category":"c","difficulty":1,"question":"q2 ?","choices":["a","b","c","d"],"answer":0,"explanation":"e","source":"s","status":"review","verif":"fact"},
            {"id":"c","track":"general","region":"CM","category":"c","difficulty":1,"question":"q3 ?","choices":["a","b","c","d"],"answer":0,"explanation":"e","source":"s","status":"rejected","verif":"computed"}]}"""
        assertEquals(emptyList(), QuizBank.parse(json).playable.map { it.id }, "banks (not packs): flagged questions stay out")
        assertEquals(listOf("a"), QuizBank.parse(json, computedPlayable = true).playable.map { it.id }, "packs and lots: computed questions play before review, facts and rejected ones do not")
        assertTrue(QuizBank.parse(json, computedPlayable = true).all.all { it.review }, "the raw review flag is kept for the validation tooling")
    }

    // ------------------------------------------------------------------------------------------------ priority and themes

    @Test fun priorityPutsTheProfilesOwnCourseFirstThenGeneralKnowledgeThenNeighbours() {
        val p = QuizLotScopes.quizPriority(Track.SECONDARY, "3e", null, played = listOf("primary/CM2"))
        assertEquals("3e", p.first())
        assertEquals(listOf("culture-cm", "culture-afrique", "culture-monde"), p.subList(1, 4))
        assertTrue(p.indexOf("cm2") < p.indexOf("maths-l1"), "a course played here comes before the unrelated ones")
        assertTrue(p.indexOf("tle") < p.indexOf("droit-l1"), "neighbouring levels of the same track come before other tracks")
        assertEquals(p.size, p.toSet().size, "no scope twice")
        assertTrue(p.containsAll(QuizLotScopes.specs.map { it.scope }), "every lot is somewhere in the order")
        assertEquals(listOf("culture-cm", "culture-afrique", "culture-monde"), QuizLotScopes.quizPriority().take(3))
        assertEquals("maths-l1", QuizLotScopes.quizPriority(Track.HIGHER, "L1", "mathematiques").first())
        assertEquals(setOf("culture-cm", "culture-afrique", "culture-monde"), QuizLotScopes.scopesOf(QuestionFilter.GENERAL).toSet())
        assertEquals("class-1", QuizLotScopes.scopeFor(Track.PRIMARY, "Class 1", null)); assertEquals("eco-l1", QuizLotScopes.scopeFor(Track.HIGHER, "L1", "economie"))
        assertEquals("lower-sixth", QuizLotScopes.scopeFor(Track.SECONDARY, "Lower Sixth", null))
    }

    @Test fun themesShowStatusFreshnessAndWhatTheTvHolds() {
        fun m(scope: String, v: Int, bytes: Long = 100_000) = LotMeta(LotId("quiz", scope), v, bytes, "h$v", scope)
        val day = 86_400_000L; val now = 100 * day
        val themes = QuizThemes.build(phone = listOf(m("cm2", 1), m("3e", 2)), catalog = listOf(m("cm2", 2), m("3e", 2), m("tle", 1, 365_000)), tv = listOf(m("3e", 1)),
            installedAt = { if (it == "cm2") now - 3 * day else now }, now = now, questions = { 40 })
        val by = themes.associateBy { it.scope }
        assertEquals(QuizThemes.State.UPDATE_AVAILABLE, by.getValue("cm2").state)
        assertEquals(QuizThemes.State.UP_TO_DATE, by.getValue("3e").state)
        assertEquals(QuizThemes.State.NOT_DOWNLOADED, by.getValue("tle").state)
        assertEquals(false, by.getValue("cm2").onTv); assertEquals(true, by.getValue("3e").tvBehind, "the TV has v1, the phone v2")
        assertEquals("mis à jour il y a 3 jours", by.getValue("cm2").freshness); assertEquals("mis à jour aujourd'hui", by.getValue("3e").freshness)
        assertTrue(QuizThemes.statusLine(by.getValue("tle")).contains("à télécharger"))
        assertTrue(QuizThemes.statusLine(by.getValue("cm2")).contains("mise à jour 2 disponible"))
        assertTrue(QuizThemes.tvLine(by.getValue("cm2")).contains("sera envoyé"))
        assertEquals(listOf("cm2", "tle"), QuizThemes.toDownload(themes, QuizLotScopes.quizPriority(Track.PRIMARY, "CM2")))
        // offline and TV out of reach: still complete from the phone's own data
        val offline = QuizThemes.build(listOf(m("cm2", 1)), catalog = null, tv = null, installedAt = { now }, now = now).associateBy { it.scope }
        assertEquals(QuizThemes.State.UP_TO_DATE, offline.getValue("cm2").state); assertNull(offline.getValue("cm2").onTv)
        assertEquals(QuizThemes.State.NOT_DOWNLOADED, offline.getValue("3e").state)
        assertEquals("1,0 Mo", QuizThemes.sizeLabel(1_000_000)); assertEquals("365 Ko", QuizThemes.sizeLabel(365_000))
        val f = File(tmp(), "cache.json")
        QuizThemes.CatalogCache.write(f, listOf(m("cm2", 2)), 77L)
        assertEquals(listOf(m("cm2", 2)) to 77L, QuizThemes.CatalogCache.read(f))
        assertNull(QuizThemes.CatalogCache.read(File(tmp(), "none.json")))
    }

    // ------------------------------------------------------------------------------------------------ TV starter, offline

    @Test fun theStarterIsWithinBudgetAndAFullGameWorksOnItAlone() {
        val bytes = EmbeddedQuestionSource.DEFAULT_RESOURCES.sumOf { EmbeddedQuestionSource::class.java.getResourceAsStream(it)!!.use { s -> s.readBytes().size.toLong() } }
        assertTrue(bytes <= 2L shl 20, "starter ($bytes bytes) above the 2 MB quiz share")
        assertTrue(bytes + (3L shl 20) <= 5L shl 20, "quiz starter + 3 MB of Apprendre starter stay under 5 MB, so at least 5 MB remain for pushed lots")
        assertTrue(LotBudget.TV_MAX_BYTES - 5L * (1 shl 20) >= 5L * (1 shl 20))
        val calls = ArrayList<String>()
        val old = ProxySelector.getDefault()
        ProxySelector.setDefault(object : ProxySelector() {
            override fun select(uri: java.net.URI?): MutableList<java.net.Proxy> { calls += uri.toString(); return mutableListOf(java.net.Proxy.NO_PROXY) }
            override fun connectFailed(uri: java.net.URI?, sa: java.net.SocketAddress?, ioe: java.io.IOException?) {}
        })
        try {
            val starter = EmbeddedQuestionSource().bank()
            val filters = listOf(QuestionFilter.GENERAL, cm2, QuestionFilter(Track.SECONDARY, "3e"), QuestionFilter(Track.SECONDARY, "Tle"),
                QuestionFilter(Track.HIGHER, "L1", "droit"), QuestionFilter(Track.HIGHER, "L1", "economie"), QuestionFilter(Track.HIGHER, "L1", "mathematiques"))
            for (f in filters) {
                var now = 0L
                val room = QuizRoom(starter, clock = { now }, autoTick = false)
                assertTrue(room.configure(QuizRoom.Mode.MILLIONAIRE, QuizRoom.Play.FRIENDS, f))
                assertNull(room.startGame(seed = 5), f.courseKey)
                val g = room.game!!
                while (g.phase != QuizGame.Phase.FINISHED) {
                    val q = g.question
                    assertTrue(room.hostAct("select", q.answer)); assertTrue(room.hostAct("confirm"))
                    now += 5_000; room.tick()
                    assertTrue(room.hostAct("next"))
                }
                assertEquals(QuizGame.End.WON, g.end, "the whole ladder is played on the starter alone for ${f.courseKey}")
                room.close()
            }
            // a Duel with one phone, still no network
            var now = 0L
            val duelRoom = QuizRoom(starter, clock = { now }, autoTick = false)
            val a = duelRoom.join(duelRoom.code, "Awa").player!!
            assertTrue(duelRoom.setMode(QuizRoom.Mode.DUEL)); assertNull(duelRoom.startGame(seed = 3))
            val d = duelRoom.duel!!
            while (d.phase != QuizDuel.Phase.FINISHED) { if (d.phase == QuizDuel.Phase.QUESTION) { now += 1_000; duelRoom.act(a.token, "answer", d.question.id, d.question.answer) }; duelRoom.hostSkip() }
            assertEquals(QuizRoom.Stage.FINISHED, duelRoom.stage)
            duelRoom.close()
        } finally { ProxySelector.setDefault(old) }
        assertEquals(emptyList(), calls, "no network access at all during offline games")
    }

    // ------------------------------------------------------------------------------------------------ TV / phone merge

    @Test fun scoresMergeByUnionKeepingTheBestAndAreOrderIndependent() {
        fun e(board: String, name: String, score: Long, at: Long) = HighScores.Entry(board, name, score, "", at)
        val tv = HighScores(listOf(e("M", "TV", 500, 1), e("M", "Awa", 100, 2), e("P", "TV", 7, 3)), perBoard = 3)
        val phone = HighScores(listOf(e("M", "Awa", 900, 5), e("M", "Awa", 100, 2), e("M", "Bo", 50, 6), e("M", "Cy", 40, 7)), perBoard = 3)
        val m1 = QuizMerge.mergeScores(tv, phone, 3); val m2 = QuizMerge.mergeScores(phone, tv, 3)
        assertEquals(listOf(900L, 500L, 100L), m1.top("M").map { it.score }, "best per board, the identical entry counted once, top 3")
        assertEquals(m1.top("M"), m2.top("M")); assertEquals(m1.top("P"), m2.top("P"))
        assertEquals(m1.top("M"), QuizMerge.mergeScores(m1, phone, 3).top("M"), "idempotent")
        assertEquals(listOf("P", "M").toSet(), m1.boards().toSet())
    }

    @Test fun balancesMergeLastWriteWinsPerPlayer() {
        val a = listOf(QuizMerge.StampedBalance("dev:1", 900, 10), QuizMerge.StampedBalance("dev:2", 1000, 5))
        val b = listOf(QuizMerge.StampedBalance("dev:1", 400, 20), QuizMerge.StampedBalance("dev:3", 1000, 1), QuizMerge.StampedBalance("dev:2", 800, 5))
        val m = QuizMerge.mergeBalances(a, b).associateBy { it.player }
        assertEquals(400, m.getValue("dev:1").tokens, "the later write wins"); assertEquals(1000, m.getValue("dev:2").tokens, "same time: the larger balance, deterministic")
        assertEquals(3, m.size)
        assertEquals(m, QuizMerge.mergeBalances(b, a).associateBy { it.player })
    }

    @Test fun historiesMergeKeepingTheMostRecentAppearanceOfEachQuestion() {
        val course = cm2.courseKey
        val tv = QuizHistory(); repeat(40) { tv.beginGame(course) }; tv.record(course, listOf("a", "b"))            // a, b asked at game 40 of 40 on the TV
        val phone = QuizHistory(); repeat(10) { phone.beginGame(course) }; phone.record(course, listOf("b", "c"))   // b, c asked at game 10 of 10 on the phone
        val m = QuizMerge.mergeHistories(tv, phone); val m2 = QuizMerge.mergeHistories(phone, tv)
        for (h in listOf(m, m2)) {
            assertEquals(1, h.age(course, "a")); assertEquals(1, h.age(course, "b")); assertEquals(1, h.age(course, "c"), "c was the last question asked on the phone")
            assertEquals(40, h.games(course))
        }
        assertEquals(tv.serialize(), QuizMerge.mergeHistories(tv, QuizHistory()).serialize(), "merging nothing changes nothing")
        assertEquals(m.serialize(), QuizMerge.mergeHistories(m, phone).serialize(), "idempotent")
        val t = QuizHistory(); repeat(40) { t.beginGame(course) }; t.record(course, listOf("z"))                    // z at game 40 of 40... then 19 more games
        repeat(19) { t.beginGame(course) }                                                                            // 59 games: z is 19 games old on the TV
        val p = QuizHistory(); repeat(35) { p.beginGame(course) }; p.record(course, listOf("z")); repeat(5) { p.beginGame(course) }   // 40 games: z is 5 games old on the phone
        assertEquals(6, QuizMerge.mergeHistories(t, p).age(course, "z"), "the more recent of the two (5 games ago) wins, counted on the merged game counter")
    }
}
