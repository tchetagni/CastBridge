package castbridge.core.tv

import castbridge.core.FakePlayer
import castbridge.core.FakeSink
import castbridge.core.owner.TrialPolicy
import castbridge.core.remote.RemoteApi
import castbridge.core.remote.RemoteBt
import castbridge.core.remote.RemoteSink
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A TV whose answer to « open CastBridge-TV » is scripted; everything else is the usual fake. */
private class OpenSink(private val inner: RemoteSink = FakeSink()) : RemoteSink by inner {
    val asked = CopyOnWriteArrayList<OpenTvScreen?>()
    var reply: OpenTvReply? = OpenTvReply(true, "direct", null)
    override fun openTv(screen: OpenTvScreen?): OpenTvReply? { asked += screen; return reply }
}

/**
 * `POST /api/tv/open` (PIN ou jeton) et son équivalent Bluetooth (`POST open` sur le canal CBTR) : la TV fait passer CastBridge-TV devant les autres
 * applications. Ici : les routes de [RemoteApi], le vrai serveur HTTP de la TV (corps JSON facultatif, PIN, jeton, essai) et le décodage CBTR.
 */
class OpenTvApiTest {
    private val sink = OpenSink()
    private val api = RemoteApi(sink)
    private fun post(path: String, vararg p: Pair<String, String>) = api.handle(path, "POST", mapOf(*p))!!

    @Test fun bothDoorsGiveTheSameAnswer() {
        val a = post("/api/tv/open"); val b = post("/api/remote/open")
        assertEquals(200, a.status); assertEquals(a, b)
        assertEquals("""{"opened":true,"how":"direct","needs":null}""", a.json)
        assertEquals(listOf<OpenTvScreen?>(null, null), sink.asked.toList())
    }

    @Test fun noScreenMeansThatTheTvDecides() {
        post("/api/tv/open")
        assertEquals(listOf<OpenTvScreen?>(null), sink.asked.toList())
    }

    @Test fun theScreenComesFromTheQuery() {
        for (s in OpenTvScreen.values()) assertEquals(200, post("/api/tv/open", "screen" to s.wire).status)
        assertEquals(OpenTvScreen.values().toList(), sink.asked.toList())
        assertEquals(200, post("/api/remote/open", "screen" to "library").status)
        assertEquals(OpenTvScreen.LIBRARY, sink.asked.last())
    }

    @Test fun anUnknownScreenIsRefusedAndNeverReachesTheTv() {
        for (p in listOf("/api/tv/open", "/api/remote/open")) {
            val r = post(p, "screen" to "settings")
            assertEquals(400, r.status, p)
            assertTrue("home, library, player, games ou quiz" in r.json, r.json)
        }
        assertTrue(sink.asked.isEmpty())
    }

    @Test fun anEmptyScreenIsNoScreen() {
        assertEquals(200, post("/api/tv/open", "screen" to "").status)
        assertEquals(listOf<OpenTvScreen?>(null), sink.asked.toList())
    }

    @Test fun onlyPostIsAccepted() {
        assertEquals(405, api.handle("/api/tv/open", "GET", emptyMap())!!.status)
        assertEquals(405, api.handle("/api/remote/open", "GET", emptyMap())!!.status)
        assertTrue(sink.asked.isEmpty())
    }

    @Test fun theAnswerIsTheTvsOwnEvenWhenItCouldNotOpen() {
        sink.reply = OpenTvReply(false, "fullscreen", "overlay")
        val r = post("/api/tv/open")
        assertEquals(200, r.status, "the TV did answer: the phone reads the body")
        assertEquals("""{"opened":false,"how":"fullscreen","needs":"overlay"}""", r.json)
        sink.reply = OpenTvReply.already()
        assertEquals("""{"opened":true,"already":true,"how":"already","needs":null}""", post("/api/tv/open").json)
    }

    @Test fun aTvThatCannotDoItSays501() {
        sink.reply = null
        assertEquals(501, post("/api/tv/open").status)
        assertEquals(501, RemoteApi(FakeSink()).handle("/api/tv/open", "POST", emptyMap())!!.status, "a sink that never heard of it")
    }

    @Test fun otherRoutesAreNotThisOnes() {
        assertNull(api.handle("/api/info", "GET", emptyMap()))
        assertNull(api.handle("/api/tv/device-request", "GET", emptyMap()))
        assertNull(api.handle("/api/tv/opened", "POST", emptyMap()))
        assertEquals(404, post("/api/remote/openx").status)
        assertTrue(sink.asked.isEmpty())
    }

    // ---------------------------------------------------------------- le corps JSON facultatif

    @Test fun onlyTheTvRouteReadsABodyAndTheServerKnowsItIsOptional() {
        assertTrue(api.wantsBody("/api/tv/open"))
        assertEquals(setOf(RemoteApi.OPEN_TV_PATH), ReceiverServer.BODY_OPTIONAL, "the one route with an optional body is the one this API serves")
        for (p in listOf("/api/remote/open", "/api/remote/key", "/api/tv/device-request", "/api/info")) assertFalse(api.wantsBody(p), p)
    }

    private fun body(json: String, vararg p: Pair<String, String>) = api.handleBody("/api/tv/open", "POST", mapOf(*p), json.toByteArray())!!

    @Test fun theBodyNamesTheScreen() {
        assertEquals(200, body("""{"screen":"quiz"}""").status)
        assertEquals(OpenTvScreen.QUIZ, sink.asked.last())
        assertEquals(200, body("""{ "screen" : "Games" , "other": [1,2] }""").status)
        assertEquals(OpenTvScreen.GAMES, sink.asked.last())
        assertEquals(200, body("{}").status); assertNull(sink.asked.last())
        assertEquals(200, body("""{"screen":null}""").status); assertNull(sink.asked.last())
    }

    @Test fun theQueryWinsOverTheBody() {
        assertEquals(200, body("""{"screen":"quiz"}""", "screen" to "home").status)
        assertEquals(OpenTvScreen.HOME, sink.asked.last())
    }

    @Test fun aBadBodyIsRefusedAndNeverReachesTheTv() {
        for (b in listOf("""{"screen":"settings"}""", """{"screen":5}""", """{"screen":["home"]}""", "not json", "[1]", """{"screen":"home"} trailing""", "\u0000"))
            assertEquals(400, body(b).status, b)
        assertTrue(sink.asked.isEmpty())
    }

    @Test fun anHugeBodyIsRefused() {
        assertEquals(413, api.handleBody("/api/tv/open", "POST", emptyMap(), ByteArray(5_000) { ' '.code.toByte() })!!.status)
        assertTrue(sink.asked.isEmpty())
    }

    @Test fun bodyAnswersOtherPathsWithNull() {
        assertNull(api.handleBody("/api/remote/key", "POST", emptyMap(), "{}".toByteArray()))
        assertNull(api.handleBody("/api/play", "POST", emptyMap(), "{}".toByteArray()))
    }

    // ---------------------------------------------------------------- le canal Bluetooth (CBTR) : mêmes lignes, mêmes réponses

    @Test fun bluetoothLineOpenIsDecodedLikeTheHttpRoute() {
        val c2s = PipedOutputStream(); val tvIn = PipedInputStream(c2s, 1 shl 16)
        val s2c = PipedOutputStream(); val clIn = PipedInputStream(s2c, 1 shl 16)
        Thread { runCatching { RemoteBt.serve(tvIn, s2c, api) }; runCatching { s2c.close() } }.apply { isDaemon = true; start() }
        val t = RemoteBt.Transport(clIn, c2s) { c2s.close() }
        val plain = t.send("POST", "open", "")
        assertEquals(200, plain.status); assertEquals("""{"opened":true,"how":"direct","needs":null}""", plain.body)
        assertEquals(200, t.send("POST", "open", "screen=library").status)
        assertEquals(400, t.send("POST", "open", "screen=nope").status)
        assertEquals(405, t.send("GET", "open", "").status)
        sink.reply = OpenTvReply(false, "none", "overlay")
        assertEquals("""{"opened":false,"how":"none","needs":"overlay"}""", t.send("POST", "open", "screen=quiz").body)
        t.close()
        assertEquals(listOf<OpenTvScreen?>(null, OpenTvScreen.LIBRARY, OpenTvScreen.QUIZ), sink.asked.toList())
    }
}

/** The real [ReceiverServer] (PIN, trusted-phone token, trial edition): what a phone, `curl` or the owner's relay actually sends. */
class OpenTvServerTest {
    private val dir = kotlin.io.path.createTempDirectory("opentv").toFile()
    private val sink = OpenSink()
    private var trial = false
    /** A route that needs its body, next to ours: the optional body of the TV route must not make it optional too. */
    private val strict = object : ApiExtension {
        override fun handle(path: String, method: String, params: Map<String, String>): ApiReply? = null
        override fun wantsBody(path: String) = path == "/api/test/needs-body"
        override fun handleBody(path: String, method: String, params: Map<String, String>, body: ByteArray): ApiReply? = ApiReply(200, """{"n":${body.size}}""")
    }
    private val server = ReceiverServer(VolumeRegistry.single(java.io.File(dir, "tv")), FakePlayer(), 0, pin = "123456",
        extension = strict.then(RemoteApi(sink)), tokenAuth = { if (it == "cbk_ok") "Mon téléphone" else null },
        routeGuard = { p -> if (trial && TrialPolicy.routeBlocked(p)) TrialPolicy.MESSAGE else null }).apply { start(5000, false) }
    private val port = server.listeningPort

    @AfterTest fun tearDown() { server.stop(); dir.deleteRecursively() }

    private fun call(path: String = "/api/tv/open", body: String? = null, headers: Map<String, String> = mapOf("X-CB-Pin" to "123456"), method: String = "POST"): Pair<Int, String> {
        val c = URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection
        c.requestMethod = method; c.connectTimeout = 3000; c.readTimeout = 5000
        headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
        if (method == "POST") {
            c.doOutput = true
            val bytes = body?.toByteArray() ?: ByteArray(0)
            c.setFixedLengthStreamingMode(bytes.size)
            if (body != null) c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(bytes) }
        }
        val code = c.responseCode
        val text = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        return code to text
    }

    @Test fun aBodylessPostWorksWithThePin() {
        val (code, text) = call()
        assertEquals(200, code, text)
        assertEquals("""{"opened":true,"how":"direct","needs":null}""", text)
        assertEquals(listOf<OpenTvScreen?>(null), sink.asked.toList())
    }

    @Test fun theOptionalJsonBodyReachesTheTv() {
        val (code, text) = call(body = """{"screen":"library"}""")
        assertEquals(200, code, text)
        assertEquals(OpenTvScreen.LIBRARY, sink.asked.single())
    }

    @Test fun theScreenInTheQueryWorksToo() {
        assertEquals(200, call("/api/tv/open?screen=quiz").first)
        assertEquals(OpenTvScreen.QUIZ, sink.asked.single())
    }

    @Test fun aTrustedPhoneTokenIsEnough() {
        assertEquals(200, call(headers = mapOf("X-CB-Token" to "cbk_ok")).first)
        assertEquals(401, call(headers = mapOf("X-CB-Token" to "cbk_old")).first, "an expired token is refused like everywhere")
        assertEquals(1, sink.asked.size)
    }

    @Test fun withoutAnyCredentialItIsRefusedAndTheTvIsNotTouched() {
        assertEquals(401, call(headers = emptyMap()).first)
        assertEquals(401, call(headers = mapOf("X-CB-Pin" to "000000")).first)
        assertTrue(sink.asked.isEmpty())
    }

    @Test fun aBadScreenIsA400() {
        assertEquals(400, call(body = """{"screen":"settings"}""").first)
        assertEquals(400, call("/api/tv/open?screen=settings").first)
        assertTrue(sink.asked.isEmpty())
    }

    @Test fun getIsNotAllowed() {
        assertEquals(405, call(method = "GET").first)
        assertTrue(sink.asked.isEmpty())
    }

    @Test fun aTrialTvOpensToo() {
        trial = true
        assertEquals(200, call().first, "like the rest of the remote, the shortcut must work on a trial TV")
        assertTrue(TrialPolicy.routeAllowed("/api/tv/open"))
        assertEquals(403, call("/api/library", method = "GET").first, "the allowlist is still closed to everything else")
    }

    @Test fun theOptionalBodyDoesNotMakeAnotherBodyRouteOptional() {
        assertEquals(413, call("/api/test/needs-body").first, "still « body missing or too large »")
        assertEquals(200, call("/api/test/needs-body", body = "abc").first)
        assertEquals(200, call().first, "and ours still works next to it in the same chain")
    }

    @Test fun aChunkedBodyIsNotMistakenForNoBody() {
        // no Content-Length does not mean « no body »: an unread chunked body would corrupt the next request of the connection
        val c = URL("http://127.0.0.1:$port/api/tv/open").openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true; c.setChunkedStreamingMode(16); c.connectTimeout = 3000; c.readTimeout = 5000
        c.setRequestProperty("X-CB-Pin", "123456")
        c.outputStream.use { it.write("""{"screen":"library"}""".toByteArray()) }
        val code = runCatching { c.responseCode }.getOrDefault(-1)
        assertTrue(code != 200, "code $code")
        assertTrue(sink.asked.isEmpty())
    }

    @Test fun anOversizedBodyOnTheOpenRouteIsRefusedWithoutReachingTheTv() {
        val (code, _) = call(body = " ".repeat(40_000))
        assertTrue(code == 413 || code == 400, "code $code")
        assertTrue(sink.asked.isEmpty())
    }
}
