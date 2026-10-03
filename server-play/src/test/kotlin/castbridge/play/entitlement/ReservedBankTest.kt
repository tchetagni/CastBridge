package castbridge.play.entitlement

import castbridge.core.quiz.EmbeddedQuestionSource
import castbridge.core.quiz.Json
import castbridge.core.quiz.Question
import castbridge.core.quiz.QuestionFilter
import castbridge.core.quiz.QuizBank
import castbridge.core.quiz.QuizLotIndex
import castbridge.core.quiz.Track
import castbridge.core.quiz.online.ClientMsg
import castbridge.core.quiz.online.PlayCodec
import castbridge.core.quiz.online.PlayScope
import castbridge.core.quiz.online.ServerMsg
import castbridge.core.quiz.online.ServerRoom
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Les 30 % réservées : jamais sans droit, seulement les lots couverts, une question à la fois ; le gel absent ferme tout (DESIGN-W20 § 2.5, T-5). */
class ReservedBankTest {
    private fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }
    private val cm2 = QuestionFilter(Track.PRIMARY, "CM2")

    private fun qmap(id: String, i: Int, scope: String): Map<String, Any?> = when (scope) {
        "culture-cm" -> linkedMapOf("id" to id, "track" to "general", "level" to null, "field" to null, "region" to "CM", "category" to "Culture", "difficulty" to 1 + i % 5,
            "question" to "Question de culture numéro $i du lot $id ?", "choices" to listOf("a$i", "b$i", "c$i", "d$i"), "answer" to 0, "explanation" to "Explication $i de $id.", "source" to "test",
            "status" to "approved", "verif" to "computed", "lang" to "fr")
        else -> linkedMapOf("id" to id, "track" to "primary", "level" to (if (scope == "3e") "3e" else "CM2"), "field" to null, "region" to "WORLD", "category" to "Calcul", "difficulty" to 1 + i % 5,
            "question" to "Combien font $i + $i dans $id ?", "choices" to listOf("${2 * i}", "${2 * i + 1}", "${2 * i + 2}", "${2 * i + 3}"), "answer" to 0, "explanation" to "Addition de $id.",
            "source" to "test", "status" to "approved", "verif" to "computed", "lang" to "fr")
    }

    private fun bankOf(maps: List<Map<String, Any?>>) = QuizBank.parse("{\"version\":2,\"questions\":[" + maps.joinToString(",") { Json.write(it) } + "]}", computedPlayable = true)

    /** Un lot au format réel (manifest, questions, index) : `quiz-<scope>-reserved-p1-v1.quiz.zip`. */
    private fun writeLot(dir: File, scope: String, prefix: String, n: Int, version: Int = 1, name: String = "quiz-$scope-reserved-p1-v$version.quiz.zip"): List<String> {
        val maps = (0 until n).map { qmap("$prefix-$it", it, scope) }
        val qbytes = ("{\"version\":2,\"questions\":[\n" + maps.joinToString(",\n") { Json.write(it) } + "\n]}\n").toByteArray()
        val hashes = maps.associate { it["id"] as String to QuizLotIndex.questionHash(it) }
        val ibytes = Json.write(linkedMapOf("v" to 1, "scope" to scope, "version" to version, "count" to hashes.size, "contentHash" to QuizLotIndex.contentHash(hashes), "q" to hashes.toSortedMap())).toByteArray()
        val general = scope == "culture-cm"
        val manifest = Json.write(linkedMapOf("format" to 1, "id" to scope, "track" to (if (general) "general" else "primary"), "level" to (if (general) null else if (scope == "3e") "3e" else "CM2"), "field" to null,
            "part" to 1, "parts" to 1, "version" to version, "questions" to maps.size, "lot" to linkedMapOf("feature" to "quiz", "scope" to scope, "title" to scope),
            "files" to linkedMapOf("questions.json" to linkedMapOf("size" to qbytes.size, "sha256" to sha(qbytes)), "index.json" to linkedMapOf("size" to ibytes.size, "sha256" to sha(ibytes)))))
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z -> for ((nme, d) in listOf("manifest.json" to manifest.toByteArray(), "questions.json" to qbytes, "index.json" to ibytes)) { z.putNextEntry(ZipEntry(nme)); z.write(d); z.closeEntry() } }
        File(dir, name).writeBytes(out.toByteArray())
        return hashes.keys.toList()
    }

    private fun tmp(): File = kotlin.io.path.createTempDirectory("reserved").toFile().also { it.deleteOnExit() }

    private class Fake(val byScope: Map<String, List<Question>>) : ReservedSource {
        override fun scopes() = byScope.keys
        override fun load(scope: String) = byScope[scope].orEmpty()
    }

    private val freeMaps = (0 until 60).map { qmap("f-cm2-$it", it, "cm2") } + (0 until 3).map { qmap("leak-cm2-$it", 100 + it, "cm2") }
    private val free = bankOf(freeMaps)
    private val r2 = bankOf((0 until 40).map { qmap("r-cm2-$it", 200 + it, "cm2") }).all
    private val r3 = bankOf((0 until 40).map { qmap("r-3e-$it", 300 + it, "3e") }).all
    private val ids = (r2 + r3).map { it.id }.toSet() + setOf("leak-cm2-0", "leak-cm2-1", "leak-cm2-2")
    private fun bank(frozen: Set<String>? = ids) = ReservedBank(free, Fake(mapOf("cm2" to r2, "3e" to r3)), frozen)

    private fun served(b: QuizBank, games: Int = 1_000): Set<String> {
        val s = HashSet<String>()
        for (seed in 0 until games) s += b.draw(count = 15, seed = seed.toLong(), filter = cm2).map { it.id }
        return s
    }

    @Test fun withoutARightNoReservedQuestionIsDrawnInAThousandGames() {
        val rb = bank()
        val got = served(rb.bankFor(emptySet()))
        assertTrue(got.isNotEmpty() && got.all { it.startsWith("f-cm2-") }, "seulement des libres : $got")
        assertTrue(got.none { it in ids }, "aucun id réservé, y compris ceux qui traînaient dans la source libre (leak-*)")
        assertTrue(rb.bankFor(emptySet()) === rb.freeBank, "la banque libre est partagée : pas de copie par salle")
    }

    @Test fun withACm2RightOnlyCm2ReservedQuestionsAppearInAThousandGames() {
        val rb = bank()
        val got = served(rb.bankFor(setOf("cm2")))
        assertTrue(got.any { it.startsWith("r-cm2-") }, "des réservées cm2 sont servies")
        assertTrue(got.none { it.startsWith("r-3e-") || it.startsWith("leak-") }, "aucune réservée d'un lot non couvert, aucune fuite")
        assertTrue(rb.bankFor(setOf("3e")).all.none { it.id.startsWith("r-cm2-") })
        val all = rb.bankFor(setOf("tout")).all.map { it.id }
        assertTrue(all.any { it.startsWith("r-cm2-") } && all.any { it.startsWith("r-3e-") }, "« tout » couvre tous les lots")
    }

    @Test fun aReservedIdOutsideAReservedPackIsExcludedAndAPackQuestionNotFrozenIsNotServed() {
        val rb = bank(ids - "r-cm2-7")
        val all = rb.bankFor(setOf("tout")).all.map { it.id }
        assertTrue(all.none { it.startsWith("leak-") }, "un id réservé hors d'un paquet réservé est exclu de la banque libre")
        assertFalse("r-cm2-7" in all, "une question de paquet absente du gel n'est pas servie")
    }

    @Test fun withoutTheFrozenListNothingReservedIsServedFailClosed() {
        for (frozen in listOf<Set<String>?>(null, emptySet())) {
            val rb = bank(frozen)
            assertFalse(rb.frozen)
            assertTrue(rb.bankFor(setOf("tout")) === rb.freeBank, "gel absent : la banque libre, rien d'autre")
        }
    }

    @Test fun theFrozenListFileIsReadAndABrokenOneIsAbsent() {
        val d = tmp()
        File(d, "reserved-ids.json").writeText("""{"version":1,"ids":["a-1","b-2"]}""")
        assertEquals(setOf("a-1", "b-2"), ReservedBank.readIds(File(d, "reserved-ids.json")))
        File(d, "list.json").writeText("""["x-1","y-2"]""")
        assertEquals(setOf("x-1", "y-2"), ReservedBank.readIds(File(d, "list.json")))
        File(d, "broken.json").writeText("{pas du json")
        assertEquals(null, ReservedBank.readIds(File(d, "broken.json")))
        assertEquals(null, ReservedBank.readIds(File(d, "absent.json")))
        assertEquals(null, ReservedBank.readIds(null))
    }

    @Test fun theDirectorySourceReadsTheRealLotFormatByScopeOnDemandAndNeverWrites() {
        val d = tmp()
        val cm2Ids = writeLot(d, "cm2", "r-cm2", 40)
        File(d, "quiz-3e-reserved-p1-v1.quiz.zip").writeText("pas un zip")
        File(d, "quiz-cm2-p1-v1.quiz.zip").writeBytes(File(d, "quiz-cm2-reserved-p1-v1.quiz.zip").readBytes())   // un lot LIBRE dans le dossier : ignoré (pas « -reserved- »)
        File(d, "notes.txt").writeText("ignoré")
        writeLot(d, "culture-cm", "r-other", 5, name = "quiz-cm2-reserved-p2-v1.quiz.zip")                       // portée du lot ≠ portée du nom de fichier : refusé
        d.listFiles()!!.forEach { it.setReadOnly() }; d.setReadOnly()
        try {
            val before = d.list()!!.sorted()
            val src = DirReservedSource(d)
            assertEquals(setOf("cm2", "3e"), src.scopes(), "l'index vient des NOMS de fichiers (aucun lot n'est lu au démarrage)")
            assertEquals(cm2Ids.toSet(), src.load("cm2").map { it.id }.toSet(), "le paquet p2 de portée contradictoire est refusé, p1 est lu")
            assertEquals(emptyList(), src.load("3e"), "un lot illisible est ignoré")
            assertEquals(emptyList(), src.load("../../etc"), "une portée hors liste ne sort jamais du dossier")
            assertEquals(before, d.list()!!.sorted(), "dossier en lecture seule : rien n'est créé ni supprimé")
        } finally { d.setWritable(true); d.listFiles()?.forEach { it.setWritable(true) } }
    }

    @Test fun loadsAreCachedPerScopeWithABoundedNumberOfScopes() {
        var loads = 0
        val src = object : ReservedSource { override fun scopes() = (0 until 12).map { "s$it" }.toSet(); override fun load(scope: String): List<Question> { loads++; return emptyList() } }
        val rb = ReservedBank(free, src, ids, maxCachedScopes = 4)
        repeat(5) { rb.bankFor(setOf("s1")) }
        assertEquals(1, loads, "un lot est lu une fois tant qu'il est en cache")
        for (i in 0 until 12) rb.bankFor(setOf("s$i"))
        assertTrue(rb.cachedScopes() <= 4, "au plus 4 lots en mémoire : ${rb.cachedScopes()}")
    }

    @Test fun aGameOnAnEntitledBankServesOneQuestionPerMessageAndOnlyAllowedQuestions() {
        val base = EmbeddedQuestionSource().bank()
        val cm = bankOf((0 until 400).map { qmap("r-cm-$it", it, "culture-cm") }).all
        val general = ReservedBank(base, Fake(mapOf("culture-cm" to cm)), cm.map { it.id }.toSet())
        var reservedSeen = 0
        for ((label, rb) in listOf("avec droit" to general.bankFor(setOf("culture-cm")), "sans droit" to general.bankFor(emptySet()))) {
            var seen = 0
            for (seed in 0L until 40L) {
                val r = ServerRoom("r$seed", PlayScope.INTERNET, rb, java.util.Random(seed), createdAt = 0)
                r.handle("tv", ClientMsg.Create(null, "MILLIONAIRE"), 0)
                r.handle("a", ClientMsg.Join(r.code, "Awa", null, "device-$label-$seed-xx", false), 0)
                r.handle("tv", ClientMsg.Act(null, "mode", null, "MILLIONAIRE", 1), 0)
                val outs = r.handle("tv", ClientMsg.Act(null, "start", null, seed.toString(), 2), 0)
                for (o in outs) {
                    val json = PlayCodec.encode(o.msg)
                    assertTrue(Regex("\"choices\"").findAll(json).count() <= 1, "$label : plus d'une question dans ${o.msg.type}")
                    if (o.msg is ServerMsg.State) { val m = Regex("\"id\":\"(r-cm-\\d+)\"").find(json); if (m != null) seen++ }
                }
            }
            if (label == "avec droit") reservedSeen = seen else assertEquals(0, seen, "sans droit : aucune réservée dans aucun message")
        }
        assertTrue(reservedSeen > 0, "avec droit, des questions réservées sont servies (une à la fois)")
    }
}
