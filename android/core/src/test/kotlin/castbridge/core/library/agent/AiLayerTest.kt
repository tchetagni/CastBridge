package castbridge.core.library.agent

import castbridge.core.connect.MemoryKeyValueStore
import castbridge.core.net.JsonLite
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val MB = 1L shl 20
private val CTX = AgentContext(nowMs = 1_800_000_000_000L, currentYear = 2026)

private class MockModel(val answer: (SuggestRequest) -> SuggestResponse) : NamingModel {
    override val id = "mock"
    override val remote = true
    val requests = ArrayList<SuggestRequest>()
    override fun suggest(req: SuggestRequest): SuggestResponse { requests += req; return answer(req) }
}

private fun tv(name: String, size: Long = 700 * MB, dur: Long = 0) = FileRef(Origin.TV, name, size, volumeId = "internal", durationMs = dur)
private fun snap(vararg f: FileRef) = LibrarySnapshot(Origin.TV, f.toList(), listOf(VolumeInfo("internal", "Mémoire interne", "internal", 20L shl 30, 32L shl 30)))

class AiLayerTest {
    private val ambiguous = tv("kaduna nights ep final cut.mp4", dur = 95 * 60_000L)

    @Test fun theModelIsNeverCalledWithoutTheSeparateConsent() {
        val m = MockModel { SuggestResponse("mock", true, emptyList()) }
        LibraryAgent(CTX.copy(aiAllowed = false), model = m).analyze(snap(ambiguous))
        assertTrue(m.requests.isEmpty())
    }

    @Test fun anAiAnswerIsAProposalOfModestConfidenceNeverTickedByDefault() {
        val m = MockModel { req -> SuggestResponse("mock", true, listOf(Suggestion(0, Kind.MOVIE, "Kaduna Nights", 2019, null, null, null, null, 0.99))) }
        val a = LibraryAgent(CTX.copy(aiAllowed = true), model = m).analyze(snap(ambiguous, tv("Prison.Break.S01E01.mkv")))
        assertEquals(1, m.requests.size)
        val r = a.plan.renames.first { it.file.name == ambiguous.name }
        assertEquals("Kaduna Nights (2019).mp4", r.toName)
        assertEquals(Source.AI, r.source)
        assertFalse(r.checked, "an AI answer is never ticked by default")
        assertTrue(r.confidence <= 0.6)
        assertEquals(1, a.stats.aiUsed)
        // the confident rule-based file was not sent
        assertTrue(m.requests.single().items.none { it.text.contains("Prison", true) })
    }

    @Test fun whatLeavesThePhoneIsOnlyCleanedNamesAndMinimalMetadata() {
        val m = MockModel { SuggestResponse("mock", true, emptyList()) }
        val files = arrayOf(
            ambiguous,
            tv("Appelle Jean 06 12 34 56 78 video.mp4"),                   // long digit runs (phone numbers) are dropped
            tv("WhatsApp Video 2024-03-15 at 14.22.11.mp4"),                // personal media: never sent
            tv("IMG-20240315-WA0003.jpg"), tv("CV Esaie.pdf"), tv("secret.film.mp4"),
            tv("Mon voyage.mp4").copy(folder = "Famille/Privé"),            // folder never sent
        )
        val guard = object : ContentGuard { override fun isProtected(file: FileRef) = file.name.startsWith("secret"); override val childProfileActive = false }
        LibraryAgent(CTX.copy(aiAllowed = true, guard = guard), model = m).analyze(snap(*files))
        val sent = m.requests.single()
        val json = sent.toJson()
        val parsed = JsonLite.obj(json)
        assertEquals(setOf("consent", "lang", "items"), parsed.keys)
        @Suppress("UNCHECKED_CAST") val items = parsed["items"] as List<Map<String, Any?>>
        items.forEach { assertTrue(it.keys.all { k -> k in setOf("i", "t", "x", "k", "d") }, "unexpected field in $it") }
        assertTrue(sent.items.none { it.text.contains("WhatsApp") || it.text.contains("secret") || it.text.contains("CV") })
        assertTrue(sent.items.none { Regex("\\d{6,}").containsMatchIn(it.text) }, sent.preview())
        assertFalse(json.contains("Privé") || json.contains("Famille") || json.contains("internal"), "no folder, no volume id")
        assertEquals(95, sent.items.first { it.text.startsWith("kaduna") }.durationMin)
        // the preview shown to the user is exactly the list of names
        assertEquals(sent.items.size, sent.preview().lines().size)
    }

    @Test fun nothingIsSentForAChildProfile() {
        val m = MockModel { SuggestResponse("mock", true, emptyList()) }
        val child = object : ContentGuard { override fun isProtected(file: FileRef) = false; override val childProfileActive = true }
        LibraryAgent(CTX.copy(aiAllowed = true, guard = child), model = m).analyze(snap(ambiguous))
        assertTrue(m.requests.isEmpty())
    }

    @Test fun anUnavailableModelFallsBackToTheRulesWithAnExplanation() {
        val m = MockModel { throw ModelUnavailable("Serveur injoignable") }
        val a = LibraryAgent(CTX.copy(aiAllowed = true), model = m).analyze(snap(ambiguous, tv("Prison.Break.S01E01.mkv")))
        assertEquals(1, a.plan.renames.size)
        assertTrue(a.plan.notes.any { it.contains("n'a pas répondu") })
    }

    @Test fun serverAnswersAreSanitisedBeforeUse() {
        val bad = mapOf("i" to 0, "kind" to "movie", "title" to "../../etc/passwd: <evil>|x", "year" to 3000, "season" to 500, "episode" to -4, "confidence" to 7)
        val s = AiApply.sanitize(bad, 1, 2026)!!
        assertFalse(s.title.contains("/") || s.title.contains("<") || s.title.contains(":") || s.title.contains("|"), s.title)
        assertNull(s.year); assertNull(s.season); assertNull(s.episode)
        assertEquals(1.0, s.confidence)
        assertNull(AiApply.sanitize(mapOf("i" to 5, "kind" to "movie", "title" to "X Y"), 1, 2026), "index out of range")
        assertNull(AiApply.sanitize(mapOf("i" to 0, "kind" to "personal", "title" to "Family"), 1, 2026), "a model cannot invent a kind outside the allowed ones")
        assertNull(AiApply.sanitize(mapOf("i" to 0, "kind" to "movie", "title" to ".."), 1, 2026))
    }

    @Test fun localRulesModelUsesTheSameInterfaceWithoutNetwork() {
        val m: NamingModel = LocalRulesModel(2026)
        assertFalse(m.remote)
        val r = m.suggest(SuggestRequest("fr", listOf(SuggestItem(0, "prison break s01e04", "mkv", null, null), SuggestItem(1, "xyz", "mp4", null, null))))
        val s = r.suggestions.single()
        assertEquals(Kind.SERIES, s.kind); assertEquals("Prison Break", s.title); assertEquals(1, s.season); assertEquals(4, s.episode)
    }

    @Test fun serverModelTalksToTheEndpointWithTheDeviceTokenOnly() {
        var auth: String? = null; var body: String? = null; var path: String? = null
        val srv = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        srv.createContext("/") { ex ->
            auth = ex.requestHeaders.getFirst("Authorization"); path = ex.requestURI.path; body = ex.requestBody.readBytes().toString(Charsets.UTF_8)
            val out = """{"model":"test-llm","available":true,"suggestions":[{"i":0,"kind":"series","title":"Kaduna Nights","season":2,"episode":3,"confidence":0.8},{"i":9,"kind":"movie","title":"Out of range"}]}""".toByteArray()
            ex.responseHeaders.add("Content-Type", "application/json"); ex.sendResponseHeaders(200, out.size.toLong()); ex.responseBody.use { it.write(out) }
        }
        srv.start()
        try {
            val model = ServerNamingModel("http://127.0.0.1:${srv.address.port}", { "tok-123" }, currentYear = 2026)
            val r = model.suggest(SuggestRequest("fr", listOf(SuggestItem(0, "kaduna nights s02e03", "mp4", null, 45))))
            assertEquals("/api/v1/library/suggest", path)
            assertEquals("Bearer tok-123", auth)
            assertEquals(setOf("consent", "lang", "items"), JsonLite.obj(body!!).keys)
            assertEquals("library-ai-v1", JsonLite.obj(body!!)["consent"])
            val s = r.suggestions.single()
            assertEquals("Kaduna Nights", s.title); assertEquals(2, s.season)
            assertEquals("test-llm", r.model)
            // no token yet: refuse locally, nothing is sent
            assertFailsWith<ModelUnavailable> { ServerNamingModel("http://127.0.0.1:${srv.address.port}", { null }).suggest(SuggestRequest("fr", emptyList())) }
        } finally { srv.stop(0) }
    }

    @Test fun serverErrorsBecomeFriendlyMessages() {
        val srv = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var code = 429
        srv.createContext("/") { ex -> ex.requestBody.readBytes(); val b = "{}".toByteArray(); ex.sendResponseHeaders(code, b.size.toLong()); ex.responseBody.use { it.write(b) } }
        srv.start()
        try {
            val m = ServerNamingModel("http://127.0.0.1:${srv.address.port}", { "t" })
            assertTrue(assertFailsWith<ModelUnavailable> { m.suggest(SuggestRequest("fr", emptyList())) }.message!!.contains("Trop de demandes"))
            code = 401
            assertTrue(assertFailsWith<ModelUnavailable> { m.suggest(SuggestRequest("fr", emptyList())) }.message!!.contains("ne reconnaît pas"))
        } finally { srv.stop(0) }
    }

    // ------------------------------------------------------------------ settings, consent, auto-rename

    @Test fun everythingThatActsOrSendsIsOffByDefaultAndConsentIsVersioned() {
        val kv = MemoryKeyValueStore()
        val s = AgentSettings(kv, now = { 1000L })
        assertFalse(s.autoRename); assertFalse(s.aiEnabled); assertNull(s.phoneTreeUri)
        s.grantAi(); assertTrue(s.aiEnabled)
        kv.put(AgentSettings.K_AI, "2020-01")          // consent given for an older wording
        assertFalse(AgentSettings(kv).aiEnabled)
        s.autoRename = true; s.snooze("names", 7)
        assertTrue(s.hiddenUntil().containsKey("names"))
        s.clearAll()
        assertFalse(s.autoRename); assertTrue(s.hiddenUntil().isEmpty())
    }

    @Test fun consentTextSaysWhatLeavesAndWhatDoesNot() {
        assertTrue(AiConsent.WHAT_LEAVES.contains("nom du fichier déjà nettoyé"))
        assertTrue(AiConsent.WHAT_STAYS.contains("contenu des fichiers"))
        assertTrue(AiConsent.WHAT_STAYS.contains("identifiant"))
    }

    @Test fun autoRenameOnlyTouchesSureNamesAndNeverFoldersOrOthers() {
        assertEquals("Prison Break – S01E04.mkv", AutoRename.nameFor("Prison.Break.S01E04.FRENCH.720p.HDTV.x264-JMT.mkv", currentYear = 2026))
        assertEquals("Inception (2010).mkv", AutoRename.nameFor("Inception.2010.1080p.BluRay.x264.mkv", currentYear = 2026))
        assertEquals("Vidéo WhatsApp – 2024-03-15 14h22.mp4", AutoRename.nameFor("WhatsApp Video 2024-03-15 at 14.22.11.mp4", currentYear = 2026))
        assertNull(AutoRename.nameFor("Burna Boy - Last Last (Official Video).mp4", currentYear = 2026), "clips are not a safe rule")
        assertNull(AutoRename.nameFor("Mon voyage.mp4", currentYear = 2026))
        assertNull(AutoRename.nameFor("Prison Break – S01E04.mkv", currentYear = 2026), "already fine")
        assertNull(AutoRename.nameFor("Episode 4.mkv", currentYear = 2026))
        val kv = MemoryKeyValueStore(); val l = LearnedRules(kv); l.ignore("prison break")
        assertNull(AutoRename.nameFor("Prison.Break.S01E04.mkv", learned = l, currentYear = 2026))
    }

    @Test fun learnedRulesAreBoundedPersistedAndErasable() {
        val kv = MemoryKeyValueStore()
        val l = LearnedRules(kv, max = 5)
        for (i in 1..9) l.recordFolder("title $i", "Séries/Mes séries $i")
        assertEquals(5, l.size())
        assertNull(l.folderFor("title 1")); assertNotNull(l.folderFor("title 9"))
        assertEquals("Séries/Mes séries 9", LearnedRules(kv, max = 5).folderFor("title 9"))
        l.recordFolder("evil", "../x"); assertNull(l.folderFor("evil"))
        l.clear()
        assertEquals(0, LearnedRules(kv).size())
    }
}
