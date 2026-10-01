package castbridge.core

import castbridge.core.net.HttpLite
import castbridge.core.quiz.*
import com.sun.net.httpserver.HttpServer
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.random.Random
import kotlin.test.*

/** Question packs: signed, size-capped, resumable, evicting, with several sources (docs/QUIZ.md). */
class QuizPacksTest {
    private val key: KeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    private val otherKey: KeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    private fun pub(k: KeyPair) = Base64.getEncoder().encodeToString(k.public.encoded.copyOfRange(12, 44))
    private val ALNUM = (('a'..'z') + ('A'..'Z') + ('0'..'9')).joinToString("")
    private val school = QuestionFilter(Track.SECONDARY, "3e")
    private val general = QuestionFilter.GENERAL

    private fun tmp(): File = File.createTempFile("qpack", "").apply { delete(); mkdirs(); deleteOnExit() }
    private fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    private class Built(val info: QuizPackInfo, val bytes: ByteArray, val file: File)

    /** A valid signed pack of [n] questions; [pad] extra random characters per explanation make big, incompressible packs. */
    private fun build(filter: QuestionFilter, part: Int, n: Int = 30, version: Int = 1, pad: Int = 0, signer: KeyPair = key, parts: Int = 4, idBase: String = "x"): Built {
        val rnd = Random(part * 31 + version)
        val qs = (0 until n).joinToString(",\n") { i ->
            val id = "$idBase-${filter.courseKey.replace('/', '_')}-p$part-$i"
            val region = if (filter.track == Track.GENERAL) listOf("CM", "CM", "CM", "AF", "WORLD")[i % 5] else "WORLD"
            val extra = if (pad > 0) " " + String(CharArray(pad) { ALNUM[rnd.nextInt(ALNUM.length)] }) else ""
            """{"id":"$id","track":"${filter.track.key}","level":${filter.level?.let { "\"$it\"" } ?: "null"},"field":null,"region":"$region","category":"Cat","difficulty":${1 + i % 5},"question":"Question $id ?","choices":["a$i","b$i","c$i","d$i"],"answer":${i % 4},"explanation":"Explication de $id$extra","source":"test","status":"review","verif":"computed","lang":"fr"}"""
        }
        val qjson = "{\"version\":2,\"questions\":[\n$qs\n]}\n".toByteArray(Charsets.UTF_8)
        val manifest = """{"format":1,"id":"${filter.courseKey.replace('/', '-')}-p$part","track":"${filter.track.key}","level":${filter.level?.let { "\"$it\"" } ?: "null"},"field":null,"part":$part,"parts":$parts,"version":$version,"questions":$n,"files":{"questions.json":{"size":${qjson.size},"sha256":"${sha(qjson)}"}}}"""
        val zip = zip("manifest.json" to manifest.toByteArray(), "questions.json" to qjson)
        return finish(filter, part, parts, version, n, zip, signer)
    }

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z -> for ((name, data) in entries) { z.putNextEntry(ZipEntry(name)); z.write(data); z.closeEntry() } }
        return out.toByteArray()
    }

    private fun finish(filter: QuestionFilter, part: Int, parts: Int, version: Int, n: Int, zip: ByteArray, signer: KeyPair, fileName: String? = null): Built {
        val id = filter.courseKey.replace('/', '-') + "-p$part"
        val file = fileName ?: "quiz-${filter.courseKey.lowercase().replace(Regex("[^a-z0-9]+"), "-")}-p$part-v$version$QUIZ_PACK_SUFFIX"
        val unsigned = QuizPackInfo(id, filter.track.key, filter.level, filter.field, part, parts, version, file, zip.size.toLong(), sha(zip), n, "k", "")
        val sig = Signature.getInstance("Ed25519").run { initSign(signer.private); update(unsigned.canonicalPayload().toByteArray()); sign() }
        val info = unsigned.copy(signature = Base64.getEncoder().encodeToString(sig))
        val f = File(tmp(), file); f.writeBytes(zip)
        return Built(info, zip, f)
    }

    private fun store(max: Long = QUIZ_PACK_MAX_BYTES) = QuizPackStore(tmp(), max, listOf(pub(key)))

    // ------------------------------------------------------------------------------------------- install checks
    @Test fun signedPackInstallsAndItsQuestionsArePlayable() {
        val s = store(); val p = build(school, 1)
        assertEquals(QuizPackStore.Install.Ok(emptyList()), s.install(p.info, p.file))
        assertEquals(1, s.installed().size)
        val bank = s.bank()
        assertEquals(30, bank.all.size)
        assertEquals(30, bank.count(school), "computed questions of a pack are playable")
        assertTrue(s.has("secondary-3e-p1", 1))
        // survives a restart
        assertEquals(30, QuizPackStore(s.dir, QUIZ_PACK_MAX_BYTES, listOf(pub(key))).bank().all.size)
    }

    @Test fun signatureFromAnotherKeyIsRefused() {
        val s = store(); val p = build(school, 1, signer = otherKey)
        val r = s.install(p.info, p.file)
        assertTrue(r is QuizPackStore.Install.Refused && "signature" in r.reason, "$r")
        assertEquals(0, s.installed().size)
    }

    @Test fun tamperedOrWrongSizeOrUnsafePacksAreRefused() {
        val s = store(); val p = build(school, 1)
        val bad = p.bytes.copyOf().also { it[it.size / 2] = (it[it.size / 2] + 1).toByte() }
        File(p.file.path).writeBytes(bad)
        assertTrue(s.install(p.info, p.file) is QuizPackStore.Install.Refused, "same size, one byte changed: hash differs")
        val short = build(school, 2); short.file.writeBytes(short.bytes.copyOf(short.bytes.size - 5))
        assertTrue((s.install(short.info, short.file) as QuizPackStore.Install.Refused).reason.contains("taille"))
        val evil = finish(school, 3, 4, 1, 30, build(school, 3).bytes, key, fileName = "../../evil.quiz.zip")
        assertTrue(s.install(evil.info, evil.file) is QuizPackStore.Install.Refused)
        assertNull(QuizPackInfo.parse(evil.info.toMap()), "an unsafe file name is not even a catalog entry")
        assertEquals(0, s.installed().size)
    }

    @Test fun packWithForeignContentIsRefusedEvenIfSignedAndHashed() {
        val s = store()
        // questions of the 3e inside a pack that announces CM2
        val good = build(school, 1)
        val cm2 = QuestionFilter(Track.PRIMARY, "CM2")
        val spoof = finish(cm2, 1, 4, 1, 30, good.bytes, key)
        assertTrue(s.install(spoof.info, spoof.file) is QuizPackStore.Install.Refused)
        val extra = finish(school, 1, 4, 1, 30, zip("manifest.json" to "{}".toByteArray(), "questions.json" to "{}".toByteArray(), "evil.sh" to "x".toByteArray()), key)
        assertTrue(s.install(extra.info, extra.file) is QuizPackStore.Install.Refused)
        val notZip = finish(school, 2, 4, 1, 30, "not a zip".toByteArray(), key)
        assertTrue(s.install(notZip.info, notZip.file) is QuizPackStore.Install.Refused)
        assertEquals(0, s.installed().size)
    }

    @Test fun aNewerVersionReplacesTheOlderAndAnOlderOneIsRefused() {
        val s = store()
        val v1 = build(school, 1, version = 1); val v2 = build(school, 1, version = 2, n = 40)
        assertTrue(s.install(v1.info, v1.file) is QuizPackStore.Install.Ok)
        assertTrue(s.install(v2.info, v2.file) is QuizPackStore.Install.Ok)
        assertEquals(listOf(2), s.installed().map { it.info.version })
        assertEquals(40, s.bank().all.size)
        assertTrue(s.install(v1.info, v1.file) is QuizPackStore.Install.Refused)
        assertEquals(1, s.dir.listFiles { f -> f.name.endsWith(QUIZ_PACK_SUFFIX) }!!.size, "the old file is gone")
    }

    // ------------------------------------------------------------------------------------------- the 11 MB cap
    @Test fun neverMoreThan11MegabytesOfPacksEvenAfterManyInstalls() {
        val s = store()
        assertEquals(11_000_000L, s.maxBytes)
        val sizes = ArrayList<Long>()
        for (part in 1..16) {
            val p = build(school, part, n = 600, pad = 2200)
            assertTrue(p.info.size in 800_000..1_200_000, "a pack of about 1 MB: ${p.info.size}")
            val r = s.install(p.info, p.file)
            assertTrue(r is QuizPackStore.Install.Ok, "$r")
            assertTrue(s.usedBytes() <= 11_000_000L, "cap respected after pack $part: ${s.usedBytes()}")
            sizes += s.usedBytes()
        }
        assertTrue(s.installed().size in 8..11, "about ten packs of 1 MB fit: ${s.installed().size}")
        assertEquals(16, s.installed().maxOf { it.info.part }, "the newest is kept")
        assertTrue(s.installed().none { it.info.part == 1 }, "the oldest were evicted first")
        val huge = build(school, 20, n = 700, pad = 21_000)
        assertTrue(huge.info.size > 11_000_000L)
        assertTrue((s.install(huge.info, huge.file) as QuizPackStore.Install.Refused).reason.contains("plafond"))
        assertTrue(s.usedBytes() <= 11_000_000L)
    }

    @Test fun nothingIsEvictedWhenItWouldLoseUsefulQuestions() {
        val a = build(school, 1, n = 100, pad = 400); val b = build(school, 2, n = 100, pad = 400); val c = build(school, 3, n = 100, pad = 400)
        val s = store(max = a.info.size + b.info.size + 1000)
        assertTrue(s.install(a.info, a.file) is QuizPackStore.Install.Ok)
        assertTrue(s.install(b.info, b.file) is QuizPackStore.Install.Ok)
        val r = s.install(c.info, c.file) { emptyList() }          // the caller protects everything
        assertTrue(r is QuizPackStore.Install.Refused, "$r")
        assertEquals(2, s.installed().size)
    }

    // ------------------------------------------------------------------------------------------- sources, thresholds, resume
    private class FakeSource(override val name: String, private val cat: List<QuizPackInfo>?, private val files: Map<String, File>, private val cutOnce: Boolean = false) : QuizPackSource {
        val downloads = ArrayList<String>(); private var cut = cutOnce
        override fun catalog() = cat
        override fun download(info: QuizPackInfo, part: File, progress: (Long, Long) -> Unit, cancelled: () -> Boolean): Boolean {
            downloads += info.id
            val src = files.getValue(info.file).readBytes()
            val have = if (part.isFile) part.length().toInt() else 0
            part.parentFile.mkdirs()
            if (cut) { cut = false; part.appendBytes(src.copyOfRange(have, have + src.size / 3)); throw IOException("connexion perdue") }
            part.appendBytes(src.copyOfRange(have, src.size)); return true
        }
    }

    private fun manager(s: QuizPackStore, base: QuizBank, history: QuizHistory = QuizHistory(), threshold: Int = 60, played: Set<String> = emptySet()): Pair<QuizPackManager, () -> QuizBank> {
        val combined = { QuizBank(base.all + s.bank().all) }
        return QuizPackManager(s, combined, { history }, thresholdGames = threshold, played = { played }, sleep = {}) to combined
    }

    private fun bankOf(filter: QuestionFilter, n: Int) = QuizBank((0 until n).map {
        Question("emb-$it", Region.WORLD, "Cat", 1 + it % 5, "Q$it ?", listOf("a", "b", "c", "d"), 0, "e", "s", track = filter.track, level = filter.level)
    })

    @Test fun downloadsOnlyWhenFewFreshGamesRemain() {
        val s = store()
        val packs = (1..4).map { build(school, it, n = 1500 / 10) }          // 150 questions per part = 10 games each
        val src = FakeSource("serveur", packs.map { it.info }, packs.associate { it.info.file to it.file })
        // 1 000 bundled questions = 66 games: enough, nothing is downloaded
        val (mgrRich, _) = manager(s, bankOf(school, 1000))
        val rich = mgrRich.refill(school, listOf(src))
        assertEquals(0, src.downloads.size); assertEquals(66, rich.freshBefore); assertTrue(rich.installed.isEmpty())
        // 150 bundled questions = 10 games: the TV asks for parts until 60 games are available
        val (mgr, _) = manager(s, bankOf(school, 150))
        val r = mgr.refill(school, listOf(src))
        assertEquals(10, r.freshBefore)
        assertEquals(listOf("secondary-3e-p1", "secondary-3e-p2", "secondary-3e-p3", "secondary-3e-p4"), r.installed, "parts in order, at most 4 per run")
        assertTrue(r.freshAfter >= 50, "fresh games after: ${r.freshAfter}")
        assertEquals(1, src.downloads.count { it == "secondary-3e-p1" })
        // with the history showing 300 games the questions come back: nothing more to download for the course
        assertTrue(mgr.refill(school, listOf(src)).installed.isEmpty())
    }

    @Test fun aCutDownloadResumesWhereItStopped() {
        val s = store()
        val p = build(school, 1, n = 300, pad = 300)
        val src = FakeSource("serveur", listOf(p.info), mapOf(p.info.file to p.file), cutOnce = true)
        val (mgr, _) = manager(s, bankOf(school, 15))
        val r = mgr.refill(school, listOf(src))
        assertEquals(listOf("secondary-3e-p1"), r.installed, r.message)
        assertEquals(2, src.downloads.size, "one cut, one resume")
        assertEquals(300 + 0, s.bank().all.size)
        assertFalse(File(s.dir, "downloads/${p.info.file}.part").exists())
    }

    @Test fun resumeOverHttpWithRangeRequests() {
        val p = build(school, 1, n = 400, pad = 400)
        var calls = 0
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/v1/quiz/packs/${p.info.file}") { ex ->
            calls++
            val range = ex.requestHeaders.getFirst("Range")
            val start = range?.removePrefix("bytes=")?.substringBefore('-')?.toInt() ?: 0
            if (start > 0) ex.responseHeaders.add("Content-Range", "bytes $start-${p.bytes.size - 1}/${p.bytes.size}")
            ex.sendResponseHeaders(if (start > 0) 206 else 200, (p.bytes.size - start).toLong())
            ex.responseBody.use { out ->
                if (calls == 1) { out.write(p.bytes, 0, p.bytes.size / 2); out.flush(); ex.close(); return@use }
                out.write(p.bytes, start, p.bytes.size - start)
            }
        }
        server.start()
        try {
            val src = ServerPackSource("http://127.0.0.1:${server.address.port}", HttpLite(), publicKeys = listOf(pub(key)))
            val part = File(tmp(), "x.part")
            assertFailsWith<IOException> { src.download(p.info, part) }
            assertTrue(part.length() in 1 until p.bytes.size)
            assertTrue(src.download(p.info, part))
            assertEquals(p.bytes.size.toLong(), part.length()); assertEquals(2, calls)
            assertTrue(store().install(p.info, part) is QuizPackStore.Install.Ok)
        } finally { server.stop(0) }
    }

    @Test fun catalogEntriesWithABadSignatureAreDropped() {
        val good = build(school, 1); val forged = build(school, 2, signer = otherKey)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/v1/quiz/packs") { ex ->
            val body = Json.write(linkedMapOf("format" to 1, "version" to 1, "packs" to listOf(good.info.toMap(), forged.info.toMap()))).toByteArray()
            ex.sendResponseHeaders(200, body.size.toLong()); ex.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val cat = ServerPackSource("http://127.0.0.1:${server.address.port}", HttpLite(), publicKeys = listOf(pub(key))).catalog()!!
            assertEquals(listOf("secondary-3e-p1"), cat.map { it.id })
        } finally { server.stop(0) }
        assertNull(ServerPackSource("http://127.0.0.1:1", HttpLite(connectTimeoutMs = 300), publicKeys = listOf(pub(key))).catalog(), "unreachable server = null")
    }

    @Test fun switchesToTheNextSourceWhenTheFirstOneFails() {
        val s = store()
        val p = build(school, 1, n = 300)
        val down = FakeSource("serveur", null, emptyMap())
        val phone = FakeSource("téléphone", listOf(p.info), mapOf(p.info.file to p.file))
        val (mgr, _) = manager(s, bankOf(school, 15))
        val r = mgr.refill(school, listOf(down, phone))
        assertEquals("téléphone", r.source, r.message)
        assertEquals(0, down.downloads.size); assertEquals(1, phone.downloads.size)
        // a source whose pack is refused (wrong key) is skipped too
        val s2 = store(); val forged = build(school, 1, n = 300, signer = otherKey)
        val bad = FakeSource("serveur", listOf(forged.info), mapOf(forged.info.file to forged.file))
        val r2 = manager(s2, bankOf(school, 15)).first.refill(school, listOf(bad, phone))
        assertEquals("téléphone", r2.source, r2.message)
        assertEquals(1, s2.installed().size)
    }

    @Test fun offlineTheBundledBankStillPlays() {
        val base = object : QuestionSource { override val origin = "embarquée"; override fun bank() = bankOf(school, 40) }
        val packed = PackedQuestionSource(base, store())
        assertEquals(40, packed.bank().all.size)
        assertEquals(0, manager(store(), base.bank()).first.refill(school, listOf(FakeSource("serveur", null, emptyMap()))).installed.size)
        assertEquals(40, PackedQuestionSource(base, null).bank().all.size)
    }

    @Test fun usbDrivePacksAreReadAsTheyAre() {
        val usb = tmp(); val p = build(school, 7, n = 50)
        p.file.copyTo(File(usb, p.info.file))
        File(usb, "notes.txt").writeText("x"); File(usb, "quiz-broken-p1-v1$QUIZ_PACK_SUFFIX").writeText("pas un zip")
        val packed = PackedQuestionSource(object : QuestionSource { override val origin = "embarquée"; override fun bank() = bankOf(school, 20) }, null, { listOf(usb) })
        assertEquals(70, packed.bank().all.size)
        assertTrue(packed.origin.contains("clé USB"))
    }

    // ------------------------------------------------------------------------------------------- eviction policy
    @Test fun exhaustedPacksGoFirstThenCoursesNeverPlayed() {
        val hist = QuizHistory()
        val a = build(school, 1, n = 40, pad = 3000); val b = build(school, 2, n = 40, pad = 3000); val g = build(general, 1, n = 40, pad = 3000)
        val big = build(school, 4, n = 40, pad = 3000)
        // room for the three installed packs and the new one minus half of one pack: exactly one eviction is needed
        val s = store(max = a.info.size + b.info.size + g.info.size + big.info.size - b.info.size / 2)
        for (x in listOf(a, b, g)) assertTrue(s.install(x.info, x.file) is QuizPackStore.Install.Ok)
        // part 2 of the 3e was asked entirely, recently: exhausted
        hist.beginGame(school.courseKey); hist.record(school.courseKey, s.bankOf(s.installed().first { it.info.id == b.info.id })!!.all.map { it.id })
        val (mgr, _) = manager(s, bankOf(school, 10), hist, played = setOf(school.courseKey))
        val r = mgr.installPushed(big.info, big.file)
        assertTrue(r is QuizPackStore.Install.Ok, "$r")
        assertEquals(listOf(b.info.id), (r as QuizPackStore.Install.Ok).evicted, "the exhausted pack leaves first")
        assertTrue(s.has(a.info.id), "a pack of the course in use that still has fresh questions stays")
        assertTrue(s.has(g.info.id))
    }

    @Test fun packsOfUnplayedCoursesGoBeforePlayedOnes() {
        val cm2 = QuestionFilter(Track.PRIMARY, "CM2")
        val p1 = build(school, 1, n = 40, pad = 3000); val o = build(cm2, 1, n = 40, pad = 3000); val p2 = build(school, 2, n = 40, pad = 3000)
        val s = store(max = p1.info.size + o.info.size + p2.info.size - o.info.size / 2)
        val (mgr, _) = manager(s, bankOf(school, 10), QuizHistory(), played = setOf(school.courseKey))
        assertEquals(QuizPackStore.Install.Ok(emptyList()), s.install(p1.info, p1.file))
        assertEquals(QuizPackStore.Install.Ok(emptyList()), s.install(o.info, o.file))
        val r = mgr.installPushed(p2.info, p2.file)
        assertTrue(r is QuizPackStore.Install.Ok && r.evicted == listOf(o.info.id), "the unplayed course's pack goes first: $r")
        assertEquals(setOf("secondary-3e-p1", "secondary-3e-p2"), s.installed().map { it.info.id }.toSet())
    }

    // ------------------------------------------------------------------------------------------- phone relay
    @Test fun phoneRelayPushesWhatTheTvNeedsAndTheTvChecksEverything() {
        val tvStore = store()
        val (mgr, _) = manager(tvStore, bankOf(school, 15))
        val api = QuizPackApi(tvStore, mgr) { listOf(school) }
        val p1 = build(school, 1, n = 300); val forged = build(school, 2, n = 300, signer = otherKey)
        val endpoint = object : TvPackEndpoint {
            override fun status(): TvPackStatus? = Json.obj(api.status()).let { m ->
                @Suppress("UNCHECKED_CAST")
                TvPackStatus((m["maxBytes"] as Number).toLong(), (m["usedBytes"] as Number).toLong(), (m["installed"] as List<Map<String, Any?>>).map { it["id"] as String to (it["version"] as Number).toInt() },
                    (m["needs"] as List<Map<String, Any?>>).map { QuizPackManager.Need(it["course"] as String, (it["freshGames"] as Number).toInt()) }, (m["thresholdGames"] as Number).toInt())
            }
            override fun push(info: QuizPackInfo, file: File): String? {
                val r = api.handleBody("/api/quiz/packs/push", "POST", mapOf("info" to Base64.getUrlEncoder().withoutPadding().encodeToString(Json.write(info.toMap()).toByteArray())), file.readBytes())!!
                return if (r.status == 200) null else (Json.obj(r.json)["error"] as String)
            }
        }
        val phone = QuizPackRelay(FakeSource("serveur", listOf(p1.info, forged.info), mapOf(p1.info.file to p1.file, forged.info.file to forged.file)), endpoint, tmp(), sleep = {})
        val res = phone.sync()
        assertEquals(listOf("secondary-3e-p1"), res.pushed, res.message)
        assertEquals(1, tvStore.installed().size)
        assertEquals(1, Json.obj(api.status())["needs"].let { (it as List<*>).size }, "still low on fresh games after one part")
        // a forged pack pushed straight to the TV is refused
        val r = api.handleBody("/api/quiz/packs/push", "POST", mapOf("info" to Base64.getUrlEncoder().withoutPadding().encodeToString(Json.write(forged.info.toMap()).toByteArray())), forged.bytes)!!
        assertEquals(422, r.status)
        assertEquals(400, api.handleBody("/api/quiz/packs/push", "POST", mapOf("info" to "!!!"), ByteArray(3))!!.status)
        // TV has enough: the phone does nothing
        val rich = QuizPackApi(tvStore, manager(tvStore, bankOf(school, 2000)).first) { listOf(school) }
        assertTrue(rich.status().contains("\"needs\":[]"))
    }

    @Test fun courseKeysRoundTrip() {
        for (f in listOf(QuestionFilter.GENERAL, school, QuestionFilter(Track.HIGHER, "L1", "droit"), QuestionFilter(Track.PRIMARY, "Class 3")))
            assertEquals(f, filterOfCourse(f.courseKey))
        assertNull(filterOfCourse("bogus/1")); assertNull(filterOfCourse("general/x/y/z"))
    }

    // ------------------------------------------------------------------------------------------- the real packs of the repository
    @Test fun repositoryPacksAreIntactSmallAndCountedPerMegabyte() {
        val dist = System.getProperty("quiz.dist")?.let { File(it) }?.takeIf { it.isDirectory } ?: return          // content/quiz/dist
        val cat = Json.obj(File(dist, "catalog.json").readText())
        @Suppress("UNCHECKED_CAST")
        val infos = (cat["packs"] as List<Map<String, Any?>>)
        assertTrue(infos.isNotEmpty())
        var total = 0L; var questions = 0; val ids = HashSet<String>()
        val perCourse = HashMap<String, Int>()
        for (m in infos) {
            val file = File(dist, m["file"] as String)
            assertEquals((m["size"] as Number).toLong(), file.length(), file.name)
            assertEquals(m["sha256"], QuizPackFormat.sha256(file), file.name)
            val c = QuizPackFormat.read(file)
            assertEquals((m["questions"] as Number).toInt(), c.bank.all.size, file.name)
            assertTrue(c.bank.all.size <= 1_600, "a part stays small: ${c.bank.all.size}")
            for (q in c.bank.all) assertTrue(ids.add(q.id), "id unique across packs: ${q.id}")
            total += file.length(); questions += c.bank.all.size
            val course = listOfNotNull(m["track"] as String, m["level"] as String?, m["field"] as String?).joinToString("/")
            perCourse.merge(course, c.bank.all.size, Int::plus)
        }
        assertTrue(total < QUIZ_PACK_MAX_BYTES, "all the packs together: $total bytes")
        println("QUIZ PACKS: ${infos.size} packs, $questions questions, $total bytes = ${questions * 1_000_000L / total} questions per MB; per course $perCourse")
    }
}
