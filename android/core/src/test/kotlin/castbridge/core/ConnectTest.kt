package castbridge.core

import castbridge.core.connect.ConnectState
import castbridge.core.connect.ConsentText
import castbridge.core.connect.CrashStore
import castbridge.core.connect.MemoryKeyValueStore
import castbridge.core.connect.Routes
import castbridge.core.connect.ServerLink
import castbridge.core.connect.ServerUrl
import castbridge.core.device.DeviceFacts
import castbridge.core.net.JsonLite
import castbridge.core.quiz.CachedQuestionSource
import castbridge.core.quiz.QuizSync
import castbridge.core.telemetry.EventQueue
import castbridge.core.telemetry.ScreenClock
import castbridge.core.telemetry.SessionTracker
import castbridge.core.telemetry.Telemetry
import castbridge.core.update.UpdateManifest
import castbridge.core.update.UpdateSchedule
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import java.util.zip.GZIPInputStream
import kotlin.random.Random
import kotlin.test.*

/**
 * A fake CastBridge server (backend/, docs/API-SERVER.md): devices, events (consent enforced like the real server), signed
 * update manifests with a resumable download, quiz questions with sync tokens / ETag / deletions.
 */
class FakeCastBridge : AutoCloseable {
    private val key = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    val publicKey: String = Base64.getEncoder().encodeToString(key.public.encoded.copyOfRange(12, 44))
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    val base = "http://127.0.0.1:${server.address.port}"
    val log = CopyOnWriteArrayList<String>()
    val events = CopyOnWriteArrayList<Map<String, Any?>>()
    val crashes = CopyOnWriteArrayList<String>()
    val reports = CopyOnWriteArrayList<Map<String, Any?>>()

    // devices
    @Volatile var token: String? = null
    @Volatile var registrations = 0
    @Volatile var checkUpdateOnce = false
    @Volatile var blocked = false
    @Volatile var channel = "stable"
    @Volatile var consent = "essential"
    @Volatile var erased = false

    // updates
    val apk: ByteArray = Random(7).nextBytes(200_000)
    val sha: String = MessageDigest.getInstance("SHA-256").digest(apk).joinToString("") { "%02x".format(it) }
    @Volatile var latestVersion = 0          // 0 = up to date
    @Volatile var tamper = false
    @Volatile var mandatory = false

    // quiz: id -> (question json map, change counter); deletions with their counter
    val questions = LinkedHashMap<String, Pair<Map<String, Any?>, Int>>()
    val deletions = LinkedHashMap<String, Int>()
    @Volatile var counter = 1
    @Volatile var resetRequired = false

    init {
        server.createContext("/api/v1/devices/register") { ex ->
            val body = JsonLite.obj(String(ex.requestBody.readBytes()))
            reports += body; consent = body["consent"] as String
            registrations++
            token = "tok$registrations"
            log += "register"
            reply(ex, 201, """{"deviceId":"5e0c1a2b3c4d5e6f","deviceToken":"$token","heartbeatSeconds":900,"directives":${directives()}}""")
        }
        server.createContext("/api/v1/devices/heartbeat") { ex ->
            if (!authorized(ex)) return@createContext reply(ex, 401, """{"message":"Jeton d'appareil inconnu"}""")
            val body = JsonLite.obj(String(ex.requestBody.readBytes()))
            reports += body; consent = body["consent"] as String
            log += "heartbeat"
            reply(ex, 200, directives())
        }
        server.createContext("/api/v1/devices/crash") { ex ->
            if (!authorized(ex)) return@createContext reply(ex, 401, "{}")
            crashes += JsonLite.obj(String(ex.requestBody.readBytes()))["message"] as String
            reply(ex, 204, "")
        }
        server.createContext("/api/v1/devices/me") { ex ->
            if (!authorized(ex)) return@createContext reply(ex, 401, "{}")
            if (ex.requestMethod == "DELETE") { erased = true; token = null; events.clear(); log += "erase"; reply(ex, 204, "") }
            else reply(ex, 200, """{"device":{"deviceId":"5e0c1a2b3c4d5e6f"},"events":${events.size}}""")
        }
        server.createContext("/api/v1/events/batch") { ex ->
            if (!authorized(ex)) return@createContext reply(ex, 401, "{}")
            assertEquals("gzip", ex.requestHeaders.getFirst("Content-Encoding"))
            val body = JsonLite.obj(String(GZIPInputStream(ex.requestBody).readBytes()))
            var accepted = 0; var rejected = 0
            @Suppress("UNCHECKED_CAST")
            for (e in body["events"] as List<Map<String, Any?>>) {
                // like TelemetryService: without "usage", only the essential events are accepted
                if (consent != "usage" && e["name"] !in setOf("error", "crash", "update_install")) rejected++ else { events += e; accepted++ }
            }
            log += "events $accepted/$rejected"
            reply(ex, 200, """{"accepted":$accepted,"duplicates":0,"rejected":$rejected,"errors":[]}""")
        }
        server.createContext("/api/v1/updates/tv/latest") { ex ->
            log += "latest ${ex.requestURI.rawQuery}"
            if (blocked) return@createContext reply(ex, 403, """{"message":"bloqué"}""")
            if (latestVersion == 0) return@createContext reply(ex, 204, "")
            reply(ex, 200, manifestJson())
        }
        server.createContext("/dl/tv/") { ex ->
            log += "dl"
            val range = ex.requestHeaders.getFirst("Range")
            val start = range?.removePrefix("bytes=")?.substringBefore('-')?.toInt() ?: 0
            if (start > 0) ex.responseHeaders.add("Content-Range", "bytes $start-${apk.size - 1}/${apk.size}")
            ex.sendResponseHeaders(if (start > 0) 206 else 200, (apk.size - start).toLong())
            ex.responseBody.use { it.write(apk, start, apk.size - start) }
        }
        server.createContext("/api/v1/quiz/questions") { ex ->
            val q = ex.requestURI.rawQuery.orEmpty().split("&").associate { it.substringBefore("=") to java.net.URLDecoder.decode(it.substringAfter("=", ""), "UTF-8") }
            log += "quiz since=${q["since"]} page=${q["page"]} inm=${ex.requestHeaders.getFirst("If-None-Match")}"
            if (blocked) return@createContext reply(ex, 403, """{"message":"bloqué"}""")
            val since = q["since"]?.toIntOrNull() ?: 0
            val etag = "\"q$counter-$since\""
            if (ex.requestHeaders.getFirst("If-None-Match") == etag) return@createContext reply(ex, 304, "")
            if (resetRequired && q["since"] != null) return@createContext reply(ex, 200,
                """{"version":2,"page":0,"totalPages":1,"syncToken":"$counter","resetRequired":true,"questions":[],"deleted":[]}""")
            val size = q["size"]!!.toInt(); val page = q["page"]!!.toInt()
            val changed = synchronized(questions) { questions.values.filter { it.second > since }.map { it.first } }
            val pages = maxOf(1, (changed.size + size - 1) / size)
            val del = if (page == 0) synchronized(questions) { deletions.filter { it.value > since }.keys.toList() } else emptyList()
            ex.responseHeaders.add("ETag", etag)
            reply(ex, 200, JsonLite.write(linkedMapOf("version" to 2, "page" to page, "totalPages" to pages, "syncToken" to counter.toString(),
                "resetRequired" to false, "questions" to changed.drop(page * size).take(size), "deleted" to del)))
        }
        server.start()
    }

    fun question(id: String, text: String, answer: Int = 0): Map<String, Any?> = linkedMapOf("id" to id, "lang" to "fr", "track" to "general",
        "region" to "CM", "category" to "Test", "difficulty" to 2, "question" to text, "choices" to listOf("A $id", "B $id", "C $id", "D $id"),
        "answer" to answer, "explanation" to "Parce que.", "source" to "Test", "reviewStatus" to "reviewed", "status" to "approved", "review" to false)

    fun putQuestion(q: Map<String, Any?>) = synchronized(questions) { counter++; questions[q["id"] as String] = q to counter; deletions.remove(q["id"]) }
    fun deleteQuestion(id: String) = synchronized(questions) { counter++; questions.remove(id); deletions[id] = counter }

    private fun directives(): String {
        val c = checkUpdateOnce; checkUpdateOnce = false
        return """{"serverTime":"2026-09-30T10:00:00+01:00","checkUpdate":$c,"blocked":$blocked,"channel":"$channel","heartbeatSeconds":900}"""
    }

    private fun authorized(ex: HttpExchange) = token != null && ex.requestHeaders.getFirst("Authorization") == "Bearer $token"

    fun manifest(): UpdateManifest {
        val unsigned = UpdateManifest("tv", "stable", "armeabi-v7a", latestVersion, "0.$latestVersion", "$base/dl/tv/castbridge-tv-$latestVersion.apk",
            sha, apk.size.toLong(), 26, "Notes", mandatory, 0, "2026-09-30T10:00:00+01:00", "k1", "")
        val sig = Signature.getInstance("Ed25519").run { initSign(key.private); update(unsigned.canonicalPayload().toByteArray()); sign() }
        return unsigned.copy(signature = Base64.getEncoder().encodeToString(sig))
    }

    private fun manifestJson(): String {
        val m = manifest()
        return JsonLite.write(linkedMapOf("app" to m.app, "channel" to m.channel, "abi" to m.abi, "versionCode" to m.versionCode,
            "versionName" to m.versionName, "url" to m.url, "sha256" to (if (tamper) "0".repeat(64) else m.sha256), "size" to m.size,
            "minSdk" to m.minSdk, "notes" to m.notes, "mandatory" to m.mandatory, "minSupportedVersionCode" to m.minSupportedVersionCode,
            "publishedAt" to m.publishedAt, "keyId" to m.keyId, "signature" to m.signature))
    }

    private fun reply(ex: HttpExchange, code: Int, body: String) {
        val b = body.toByteArray()
        ex.sendResponseHeaders(code, if (code == 204 || code == 304 || b.isEmpty()) -1 else b.size.toLong())
        ex.responseBody.use { if (b.isNotEmpty() && code != 204 && code != 304) it.write(b) }
    }

    override fun close() = server.stop(0)
}

class ConnectTest {
    private val dir = kotlin.io.path.createTempDirectory("connect").toFile()
    private var now = 1_790_000_000_000L
    private val installs = CopyOnWriteArrayList<Pair<File, UpdateManifest>>()
    private var acceptInstall = true

    private fun link(srv: FakeCastBridge, state: ConnectState = ConnectState(MemoryKeyValueStore()).also { it.baseUrl = srv.base },
                     versionCode: Int = 7, quiz: QuizSync? = null, routes: Routes = Routes(clock = { now })) = ServerLink(
        app = "tv", installed = ServerLink.Installed(versionCode, "0.$versionCode", listOf("armeabi-v7a"), 34), state = state,
        facts = { object : DeviceFacts { override val app = "tv"; override val installId = "x"; override val versionCode = versionCode
            override val androidId = "9774d56d682e549c"; override val model = "43A4K"; override val usbPresent = true; override val videoCount = 3 } },
        salt = "castbridge-tv", routes = routes, queue = EventQueue(File(dir, "events.jsonl")), crashes = CrashStore(File(dir, "crashes")),
        keys = listOf(srv.publicKey),
        hooks = object : ServerLink.Hooks {
            override fun downloadDir(size: Long) = File(dir, "apk")
            override fun installReady(apk: File, m: UpdateManifest, mandatory: Boolean, userAsked: Boolean): Boolean {
                if (acceptInstall) installs += apk to m
                return acceptInstall
            }
        },
        quiz = quiz, clock = { now }, sleep = {},
    )

    @Test
    fun serverAddressIsHttpsExceptForALocalTestServer() {
        assertEquals("https://bridge.sti-cm.com", ServerUrl.normalize(" bridge.sti-cm.com/ "))
        assertEquals("https://bridge.sti-cm.com", ServerUrl.normalize("https://bridge.sti-cm.com/api/v1"))
        assertEquals("https://exemple.org/castbridge", ServerUrl.normalize("https://exemple.org/castbridge/admin"))
        assertEquals("http://10.0.2.2:7090", ServerUrl.normalize("http://10.0.2.2:7090"))
        assertEquals("http://127.0.0.1:7090", ServerUrl.normalize("http://127.0.0.1:7090/"))
        assertNull(ServerUrl.normalize("http://bridge.sti-cm.com"))
        assertNotNull(ServerUrl.problem("http://192.168.1.10:7090"))
        assertNull(ServerUrl.normalize("ftp://x"))
        assertNull(ServerUrl.normalize("https://user:pw@x.org"))
        assertTrue(ServerUrl.allowedDownload("https://bridge.sti-cm.com/dl/tv/a.apk"))
        assertFalse(ServerUrl.allowedDownload("http://evil.example/dl/a.apk"))
        val s = ConnectState(MemoryKeyValueStore())
        assertEquals(ServerUrl.DEFAULT, s.baseUrl)
        assertFailsWith<IllegalArgumentException> { s.baseUrl = "http://evil.example" }
        assertEquals(ServerUrl.DEFAULT, s.baseUrl)
    }

    @Test
    fun nothingLeavesTheDeviceBeforeTheInformationScreenAndNoUsageEventWithoutConsent() = FakeCastBridge().use { srv ->
        val l = link(srv)
        assertTrue(l.state.needsConsent)
        assertFalse(l.telemetry.featureUsed("quiz"), "no usage event before the choice")
        l.onStartup(); l.tick(startup = true)
        assertEquals(emptyList(), srv.log, "no request at all before the information screen was answered")

        // "Seulement l'essentiel"
        l.setConsent(usage = false)
        assertFalse(l.state.needsConsent)
        assertEquals(ConsentText.VERSION, l.state.consentVersion)
        assertFalse(l.telemetry.featureUsed("quiz"))
        assertFalse(l.telemetry.track("quiz_game", mapOf("mode" to "solo")))
        assertTrue(l.telemetry.error("home", "io", "échec de /storage/emulated/0/film.mp4"))
        l.tick(startup = true)
        assertEquals("essential", srv.consent)
        assertEquals(listOf("error"), srv.events.map { it["name"] }, "only the essential event reached the server")
        assertEquals("essential", srv.reports.last()["consent"])
        assertEquals(ConsentText.VERSION, srv.reports.last()["consentVersion"])
        assertNull(srv.reports.last()["androidId"], "raw ANDROID_ID never sent")
        assertEquals(64, (srv.reports.last()["androidIdHash"] as String).length)

        // accepts the usage statistics: the server is told at once, usage events flow
        l.setConsent(usage = true)
        assertTrue(l.telemetry.featureUsed("quiz", "tile"))
        assertTrue(l.telemetry.featureUsed("learn", "tile"))
        assertFalse(l.telemetry.featureUsed("not_a_feature"), "closed list")
        assertTrue(l.telemetry.track("learn", mapOf("action" to "exercise_result", "pack" to "bepc-maths", "correct" to true, "profile" to "p1")))
        assertFalse(l.telemetry.track("learn", mapOf("action" to "unknown")))
        assertFalse(l.telemetry.track("playback_start", mapOf("codec" to "h264", "title" to "Mon film")), "forbidden key")
        now += 60_000; l.tick()
        assertEquals("usage", srv.consent)
        now += 16 * 60_000; l.tick()
        val names = srv.events.map { it["name"] }
        assertTrue(names.containsAll(listOf("feature_used", "learn")), "$names")
        @Suppress("UNCHECKED_CAST")
        val learn = srv.events.first { it["name"] == "learn" }["props"] as Map<String, Any?>
        assertNull(learn["profile"], "keys outside the white list are dropped on the device")

        // withdraws: queued usage events are dropped, nothing more is collected
        assertTrue(l.telemetry.featureUsed("chess"))
        l.setConsent(usage = false)
        assertEquals(0, l.queue.size())
        assertFalse(l.telemetry.featureUsed("chess"))
        assertTrue(srv.events.none { (it["props"] as? Map<*, *>)?.get("feature") == "chess" })
    }

    @Test
    fun registersHeartbeatsAppliesDirectivesAndSendsCrashes() = FakeCastBridge().use { srv ->
        val l = link(srv)
        l.setConsent(usage = false)
        l.crashes.record(IllegalStateException("lecture de /storage/emulated/0/Movies/film.mkv impossible"), 7, "player", now)
        l.onStartup(); l.tick(startup = true)
        assertEquals(1, srv.registrations)
        assertEquals("5e0c1a2b", l.state.shortId)
        assertEquals("tok1", l.state.deviceToken)
        assertTrue(l.state.lastContactOk)
        assertEquals("direct", l.state.lastContactVia)
        assertEquals(1, srv.crashes.size, "crash of the previous run sent")
        assertFalse(srv.crashes[0].contains("film.mkv"), "file names never sent: ${srv.crashes[0]}")
        assertTrue(l.crashes.pending().isEmpty())
        assertEquals(true, srv.reports.last()["usbPresent"])
        assertEquals(3L, srv.reports.last()["videoCount"])

        // 14 minutes later: nothing; 15: heartbeat
        now += 14 * 60_000; l.tick(); assertEquals(0, srv.log.count { it == "heartbeat" })
        now += 60_000; l.tick(); assertEquals(1, srv.log.count { it == "heartbeat" })

        // admin: beta channel + "check for an update now" (given once)
        srv.channel = "beta"; srv.checkUpdateOnce = true; srv.latestVersion = 8
        now += 2 * 3_600_000; l.tick()
        assertEquals("beta", l.state.channel)
        assertTrue(srv.log.any { it.startsWith("latest") && "channel=beta" in it }, "${srv.log}")
        assertEquals(1, installs.size)

        // admin blocks the device: no more update check nor quiz
        srv.blocked = true
        now += 15 * 60_000; l.tick()
        assertTrue(l.state.blocked)
        assertEquals(ServerLink.Phase.BLOCKED, l.update.phase)
        val before = srv.log.count { it.startsWith("latest") }
        l.checkUpdate(UpdateSchedule.Trigger.USER)
        assertEquals(before, srv.log.count { it.startsWith("latest") }, "blocked: no check")
        srv.blocked = false
        now += 15 * 60_000; l.tick()
        assertFalse(l.state.blocked)

        // the admin forgot the device: 401 -> registers again by itself
        srv.token = "gone"
        now += 15 * 60_000; l.tick()
        assertEquals(2, srv.registrations)
        assertTrue(l.state.lastContactOk)
    }

    @Test
    fun updateIsCheckedVerifiedDownloadedAndInstalledAndATamperedManifestIsRefused() = FakeCastBridge().use { srv ->
        val l = link(srv)
        l.setConsent(usage = false)
        l.tick(startup = true)
        assertEquals(ServerLink.Phase.UP_TO_DATE, l.update.phase)

        // a tampered manifest (hash changed after signing) is refused: nothing downloaded
        srv.latestVersion = 9; srv.tamper = true
        now += 20_000
        l.checkUpdate(UpdateSchedule.Trigger.USER)
        assertEquals(ServerLink.Phase.FAILED, l.update.phase)
        assertTrue("signature" in l.update.message, l.update.message)
        assertTrue(srv.log.none { it == "dl" })
        assertTrue(installs.isEmpty())

        // the timer does not check again before 12 h (± jitter) — the user can
        srv.tamper = false
        now += 3_600_000; l.tick()
        assertTrue(installs.isEmpty())
        now += 20_000
        l.checkUpdate(UpdateSchedule.Trigger.USER)
        assertEquals(ServerLink.Phase.INSTALLING, l.update.phase)
        val (file, m) = installs.single()
        assertEquals(9, m.versionCode)
        assertContentEquals(srv.apk, file.readBytes(), "verified file handed to the installer")
        assertEquals(7 to 9, l.state.pendingInstall)

        // the new version starts: the installation is reported (essential event), then a heartbeat
        val l2 = link(srv, state = l.state, versionCode = 9)
        l2.onStartup()
        assertNull(l2.state.pendingInstall)
        now += 1000; l2.tick(startup = true)
        @Suppress("UNCHECKED_CAST")
        val ev = srv.events.single { it["name"] == "update_install" }["props"] as Map<String, Any?>
        assertEquals(mapOf("from" to 7L, "to" to 9L, "ok" to true), ev)
    }

    @Test
    fun cancelledInstallIsNotOfferedAgainByItselfAndPostponedInstallIsRetried() = FakeCastBridge().use { srv ->
        val l = link(srv)
        l.setConsent(usage = false)
        srv.latestVersion = 8
        acceptInstall = false                       // e.g. a video is playing: postponed
        l.tick(startup = true)
        assertEquals(ServerLink.Phase.READY, l.update.phase)
        acceptInstall = true
        now += 60_000; l.tick()
        assertEquals(1, installs.size, "offered again at the next tick")
        l.installFailed("annulée")
        now += 60_000; l.tick()
        assertEquals(1, installs.size, "the user said no: not again by itself")
        l.offerInstall(userAsked = true)
        assertEquals(2, installs.size)
        @Suppress("UNCHECKED_CAST")
        assertEquals(false, (srv.events.firstOrNull { it["name"] == "update_install" } ?: run { now += 16 * 60_000; l.tick(); srv.events.first { it["name"] == "update_install" } })
            .let { (it["props"] as Map<String, Any?>)["ok"] })
    }

    @Test
    fun gatewayIsUsedWhenTheOwnNetworkDoesNotAnswer() {
        var t = 0L
        val gw = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", 1080))
        val calls = ArrayList<String>()
        val r = Routes(gateway = { gw }, clock = { t })
        fun attempt(directWorks: Boolean) = r.call { p ->
            calls += if (p == null) "direct" else "gw"
            if (p == null && !directWorks) throw IOException("timeout") else "ok"
        }
        assertEquals("ok", attempt(directWorks = false))
        assertEquals(listOf("direct", "gw"), calls)
        assertEquals(Routes.Via.GATEWAY, r.lastVia)
        calls.clear(); t += 60_000
        attempt(directWorks = false)
        assertEquals(listOf("gw"), calls, "the gateway is tried first for a while")
        calls.clear(); t += 11 * 60_000
        attempt(directWorks = true)
        assertEquals(listOf("direct"), calls)
        // no gateway: the error comes out
        assertFailsWith<IOException> { Routes(gateway = { null }).call<String> { throw IOException("down") } }
        // a server answer (HTTP error) is not a network failure: no second path
        calls.clear()
        assertFailsWith<castbridge.core.device.DeviceClient.ServerError> {
            Routes(gateway = { gw }).call<String> { p -> calls += (if (p == null) "direct" else "gw"); throw castbridge.core.device.DeviceClient.ServerError(403, "bloqué") }
        }
        assertEquals(listOf("direct"), calls)
        // result-based failure (UpdateClient returns Failed(network = true))
        calls.clear()
        val res = Routes(gateway = { gw }).call({ it == "unreachable" }) { p -> if (p == null) "unreachable" else "ok" }
        assertEquals("ok", res)
    }

    @Test
    fun rightOfAccessAndErasure() = FakeCastBridge().use { srv ->
        val l = link(srv)
        l.setConsent(usage = true)
        val installId = l.state.installId
        assertTrue(l.myData().contains("5e0c1a2b3c4d5e6f"))
        l.telemetry.featureUsed("library")
        l.eraseMyData()
        assertTrue(srv.erased)
        assertEquals(0, l.queue.size())
        assertNull(l.state.deviceToken)
        assertTrue(l.state.needsConsent, "the information screen comes back")
        assertNotEquals(installId, l.state.installId, "new install id")
        val before = srv.log.size
        now += 3_600_000; l.tick()
        assertEquals(before, srv.log.size, "nothing sent until the screen is answered again")
    }

    @Test
    fun quizQuestionsSyncIncrementallyWithDeletionsAndFallBackOffline() = FakeCastBridge().use { srv ->
        val cacheFile = File(dir, "quiz/cache.json")
        val source = CachedQuestionSource(cacheFile)
        val embedded = source.bank().all.size
        val sync = QuizSync(source, cacheFile, pageSize = 2)
        val l = link(srv, quiz = sync)
        l.setConsent(usage = false)
        for (i in 1..5) srv.putQuestion(srv.question("srv-$i", "Question serveur numéro $i ?"))
        l.tick(startup = true)
        assertTrue(l.state.quizMessage!!.startsWith("Questions à jour : 5"), l.state.quizMessage)
        assertEquals(3, srv.log.count { it.startsWith("quiz") }, "3 pages of 2")
        assertEquals(embedded + 5, source.bank().all.size)
        assertEquals(5, source.serverCount())

        // next day: nothing changed; the day after, the same query is answered 304 (If-None-Match)
        now += 25 * 3_600_000
        l.tick()
        assertEquals("Questions déjà à jour", l.state.quizMessage)
        now += 25 * 3_600_000
        l.tick()
        assertEquals("Questions déjà à jour", l.state.quizMessage)
        assertTrue(srv.log.last().contains("since=6") && srv.log.last().contains("inm=\"q6-6\""), srv.log.last())
        assertEquals(5, source.serverCount())

        // a question changes, one is deleted, an embedded one is withdrawn by the server
        srv.putQuestion(srv.question("srv-2", "Question serveur numéro 2, corrigée ?", answer = 3))
        srv.deleteQuestion("srv-5")
        srv.deleteQuestion("cm-geo-001")
        assertEquals("Questions à jour : 4 du serveur (1 reçue(s), 1 retirée(s))", l.syncQuiz())
        assertTrue(srv.log.last().contains("since=6"), srv.log.last())
        val bank = source.bank().all
        assertEquals(3, bank.first { it.id == "srv-2" }.answer)
        assertNull(bank.firstOrNull { it.id == "srv-5" })
        assertNull(bank.firstOrNull { it.id == "cm-geo-001" }, "withdrawn by the server: out of the bundled bank too")
        assertEquals(embedded - 1 + 4, bank.size)

        // resetRequired: everything again from scratch
        srv.resetRequired = true
        srv.putQuestion(srv.question("srv-6", "Question serveur numéro 6 ?"))
        l.syncQuiz()
        srv.resetRequired = false
        assertEquals(5, source.serverCount())

        // an invalid question never blocks the others
        srv.putQuestion(srv.question("bad", "Question invalide ?").toMutableMap().apply { put("choices", listOf("a", "a", "b", "c")) })
        srv.putQuestion(srv.question("srv-7", "Question serveur numéro 7 ?"))
        l.syncQuiz()
        assertNotNull(source.bank().all.firstOrNull { it.id == "srv-7" })
        assertNull(source.bank().all.firstOrNull { it.id == "bad" })

        // blocked device: no online quiz, the cache (or the bundled bank) keeps working
        srv.blocked = true
        assertTrue(l.syncQuiz().contains("bloqué"))
        assertTrue(source.bank().all.size > embedded)

        // server unreachable: bundled bank + last cache
        srv.close()
        l.state.blocked = false
        assertTrue(l.syncQuiz().contains("injoignable"))
        assertNotNull(source.bank().all.firstOrNull { it.id == "srv-7" })

        // a corrupt or oversized cache = the bundled bank alone
        cacheFile.writeText("{ corrompu")
        assertEquals(embedded, CachedQuestionSource(cacheFile).bank().all.size)
        cacheFile.writeText("x".repeat(3_000_000))
        assertEquals(embedded, CachedQuestionSource(cacheFile).bank().all.size)
    }

    @Test
    fun screenTimeAndSessions() {
        val q = EventQueue(File(dir, "s.jsonl"))
        val t = Telemetry("tv", 7, q, consent = { castbridge.core.telemetry.Consent.USAGE }, clock = { now })
        val screens = ScreenClock({ t }, clock = { now })
        val sessions = SessionTracker({ t }, clock = { now })
        sessions.shown()
        screens.enter("home"); now += 5_000
        screens.enter("quiz"); now += 60_000
        sessions.hidden(); screens.leave()
        sessions.shown(); now += 1000; sessions.hidden()          // back within the grace time: same session
        now += 31_000; sessions.check()
        val names = q.peek().map { JsonLite.obj(it)["name"] }
        assertEquals(listOf("session_start", "screen_view", "screen_time", "screen_view", "screen_time", "session_end"), names)
        val last = JsonLite.obj(q.peek().last())
        assertEquals(66_000L, (last["props"] as Map<*, *>)["ms"])
        assertFalse(t.screenView("nowhere"), "unknown screen refused")
        assertEquals("Lower_Sixth", Telemetry.code("Lower Sixth"))
    }
}
