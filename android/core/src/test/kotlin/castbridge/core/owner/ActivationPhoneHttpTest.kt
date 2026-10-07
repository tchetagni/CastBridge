package castbridge.core.owner

import castbridge.core.lots.HttpTvTransport
import castbridge.core.net.BoundRoute
import castbridge.core.trust.DeviceRequestParse
import castbridge.core.tv.TvDeviceRequestApi
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.net.URLConnection
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

/**
 * Le client du téléphone contre de vraies conversations HTTP (un faux petit serveur JDK qui répond comme la TV) : en-tête du code, statuts, texte brut, verrou, TV ancienne qui répond 403,
 * TV absente, liaison au réseau du groupe, envoi de la clé et traduction du résultat. Indépendant de la route que le chantier act-tv ajoute à la TV : on ne teste ici que ce que le téléphone envoie
 * et comprend.
 */
class ActivationPhoneHttpTest {
    private val code = "482913"
    private val fp = Fingerprints(mapOf(FactorKind.FLASH to "0a1b2c3d4e5f60718293a4b5c6d7e8f9", FactorKind.SYSTEM_SERIAL to "aaaaaaaabbbbbbbbccccccccdddddddd", FactorKind.BLUETOOTH to "00112233445566778899aabbccddeeff"))
    private val routeText = OwnerFrames.deviceInfo(DeviceCode.of(fp), fp, null, ByteArray(32) { (it * 3 + 2).toByte() })
    private val servers = ArrayList<HttpServer>()

    @AfterTest fun tearDown() { servers.forEach { it.stop(0) }; BoundRoute.clear() }

    private fun HttpExchange.reply(status: Int, body: String, type: String = "application/json") {
        val b = body.toByteArray(); responseHeaders.add("Content-Type", "$type; charset=utf-8")
        sendResponseHeaders(status, b.size.toLong()); responseBody.use { it.write(b) }
    }

    /** A fake TV: [handler] answers every request. Returns its base URL. */
    private fun tv(handler: (HttpExchange) -> Unit): String {
        val s = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        s.createContext("/") { ex -> try { handler(ex) } finally { ex.close() } }
        s.start(); servers += s
        return "http://127.0.0.1:${s.address.port}"
    }

    private val oldTvBody = """{"error":"Usage soumis à autorisation : seule l'activation est ouverte sur cette TV","locked":true}"""

    @Test fun aNewLockedTvGivesItsRequestToTheRightCodeOnlyAndLocksAfterThreeWrongOnes() {
        val seen = ArrayList<Triple<String, String, String?>>(); val wrong = AtomicInteger()
        val base = tv { ex ->
            val pin = ex.requestHeaders.getFirst("X-CB-Pin")
            seen += Triple(ex.requestMethod, ex.requestURI.path, pin)
            when {
                ex.requestURI.path != LockedRequestRoute.PATH -> ex.reply(403, oldTvBody)
                pin == code && wrong.get() < 3 -> ex.reply(200, routeText, "text/plain")
                wrong.incrementAndGet() >= 3 -> ex.reply(401, """{"error":"locked","retryAfter":30}""")
                else -> ex.reply(401, """{"error":"bad pin"}""")
            }
        }
        val ok = assertIs<LockedRequestRoute.Reply.Request>(LockedRequestRoute.probe(base, code, locked = true))
        assertEquals(routeText, ok.request.serverText())
        assertEquals(Triple("GET", "/api/activation/device-request", code), seen.single(), "un GET, avec le code dans l'en-tête X-CB-Pin")
        assertEquals(LockedRequestRoute.Reply.CodeRefused, LockedRequestRoute.probe(base, "000000", locked = true))
        assertEquals(LockedRequestRoute.Reply.CodeRefused, LockedRequestRoute.probe(base, "000001", locked = true))
        assertEquals(LockedRequestRoute.Reply.LockedOut(30), LockedRequestRoute.probe(base, "000002", locked = true))
        assertEquals(LockedRequestRoute.Reply.LockedOut(30), LockedRequestRoute.probe(base, code, locked = true), "verrouillée : même le bon code attend")
    }

    @Test fun anOldLockedTvThatAnswers403ToEveryOtherRouteIsReportedAsMissingTheRoute() {
        val base = tv { ex -> ex.reply(403, oldTvBody) }
        assertEquals(LockedRequestRoute.Reply.RouteMissing, LockedRequestRoute.probe(base, code, locked = true))
        // a TV with no such route at all (404) says the same
        assertEquals(LockedRequestRoute.Reply.RouteMissing, LockedRequestRoute.probe(tv { ex -> ex.reply(404, "{}") }, code, locked = true))
    }

    @Test fun aTvThatIsNotThereIsNullSoTheSearchTriesAgain() {
        val s = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0); val port = s.address.port; s.stop(0)           // nobody listens on that port any more
        assertNull(LockedRequestRoute.probe("http://127.0.0.1:$port", code, locked = true))
        assertNull(LockedRequestRoute.probe("http://127.0.0.1:$port", code, locked = false))
    }

    @Test fun theTermsClosedAndErrorAnswersEachHaveTheirOwnReply() {
        assertEquals(LockedRequestRoute.Reply.TermsNotAccepted, LockedRequestRoute.probe(tv { it.reply(409, """{"error":"Les conditions d'usage ne sont pas encore acceptées sur la TV"}""") }, code, true))
        assertEquals(LockedRequestRoute.Reply.Closed, LockedRequestRoute.probe(tv { it.reply(429, """{"error":"Trop de codes faux"}""") }, code, true))
        assertIs<LockedRequestRoute.Reply.Unreadable>(LockedRequestRoute.probe(tv { it.reply(500, "{}") }, code, true))
        assertIs<LockedRequestRoute.Reply.Unreadable>(LockedRequestRoute.probe(tv { it.reply(200, "<html>captive portal</html>", "text/html") }, code, true))
    }

    @Test fun aTvThePhoneIsLinkedToIsReadThroughTheExistingJsonRoute() {
        val json = TvDeviceRequestApi.json(routeText)!!
        val paths = ArrayList<String>()
        val base = tv { ex ->
            paths += ex.requestURI.path
            if (ex.requestURI.path == TvDeviceRequestApi.PATH) { if (ex.requestHeaders.getFirst("X-CB-Pin") == code) ex.reply(200, json) else ex.reply(401, """{"error":"bad pin"}""") } else ex.reply(404, "{}")
        }
        assertIs<LockedRequestRoute.Reply.Request>(LockedRequestRoute.probe(base, code, locked = false))
        assertEquals(LockedRequestRoute.Reply.CodeRefused, LockedRequestRoute.probe(base, "000000", locked = false))
        assertEquals(listOf("/api/tv/device-request", "/api/tv/device-request"), paths, "jamais la route verrouillée sur une TV liée")
        assertEquals(LockedRequestRoute.Reply.RouteMissing, LockedRequestRoute.probe(tv { it.reply(404, "{}") }, code, locked = false))
    }

    @Test fun theProbeLeavesByTheGroupNetworkWhenTheAddressIsTheGroups() {
        val opened = ArrayList<String>()
        val base = tv { it.reply(200, routeText, "text/plain") }
        BoundRoute.set("127.0.0.", object : BoundRoute.Binding {
            override fun open(url: URL): URLConnection { opened += url.host; return url.openConnection() }
            override fun bind(s: Socket) {}
        })
        assertIs<LockedRequestRoute.Reply.Request>(LockedRequestRoute.probe(base, code, locked = true))
        assertEquals(listOf("127.0.0.1"), opened)
    }

    // ------------------------------------------------------------------ l'envoi de la clé, avec le même code, et ce que l'écran en fait

    @Test fun theKeyGoesInWithTheSameCodeAndEveryAnswerBecomesTheRightEvent() {
        val bodies = ArrayList<String>()
        val base = tv { ex ->
            val pin = ex.requestHeaders.getFirst("X-CB-Pin"); val body = ex.requestBody.readBytes().toString(Charsets.UTF_8)
            when {
                ex.requestURI.path != "/api/activation/install" || ex.requestMethod != "POST" -> ex.reply(403, oldTvBody)
                pin != code -> ex.reply(401, """{"error":"bad pin"}""")
                body == "cbx1.GOOD.KEY" -> { bodies += body; ex.reply(200, """{"installed":true,"label":"Version complète","notes":[]}""") }
                else -> ex.reply(422, """{"error":"Cette clé est celle d'une autre TV"}""")
            }
        }
        val ok = KeyAcquisition.resultOf(ActivationSend.sendLan(HttpTvTransport(base, code), "cbx1.GOOD.KEY"), 5)
        assertEquals(KeyAcquisition.ResultKind.OK, ok.kind); assertEquals("Clé : Version complète.", ok.message)
        assertEquals(listOf("cbx1.GOOD.KEY"), bodies)
        val wrongTv = KeyAcquisition.resultOf(ActivationSend.sendLan(HttpTvTransport(base, code), "cbx1.OTHER.KEY"), 5)
        assertEquals(KeyAcquisition.ResultKind.REFUSED, wrongTv.kind); assertEquals("Cette clé est celle d'une autre TV", wrongTv.message)
        assertEquals(KeyAcquisition.ResultKind.CODE_REFUSED, KeyAcquisition.resultOf(ActivationSend.sendLan(HttpTvTransport(base, "000000"), "cbx1.GOOD.KEY"), 5).kind)
        val s = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0); val port = s.address.port; s.stop(0)
        assertEquals(KeyAcquisition.ResultKind.UNREACHABLE, KeyAcquisition.resultOf(ActivationSend.sendLan(HttpTvTransport("http://127.0.0.1:$port", code), "cbx1.GOOD.KEY"), 5).kind)
        // an old TV without the install route (404) is a link problem for the screen, like before
        val none = tv { it.reply(404, "{}") }
        assertEquals(KeyAcquisition.ResultKind.UNREACHABLE, KeyAcquisition.resultOf(ActivationSend.sendLan(HttpTvTransport(none, code), "cbx1.GOOD.KEY"), 5).kind)
    }

    @Test fun theBluetoothAnswerIsMappedToo() {
        assertEquals(KeyAcquisition.ResultKind.OK, KeyAcquisition.resultOfBluetooth(true, "", 1).kind)
        val no = KeyAcquisition.resultOfBluetooth(false, "clé périmée", 1)
        assertEquals(KeyAcquisition.ResultKind.REFUSED, no.kind); assertEquals("clé périmée", no.message)
    }

    @Test fun aTvRequestFromTheNetworkReallyParsesToTheSameRequestAsTheTv() {
        val r = (LockedRequestRoute.parse(routeText) as DeviceRequestParse.Ok).request
        val base = tv { it.reply(200, routeText, "text/plain") }
        assertEquals(r, (LockedRequestRoute.probe(base, code, true) as LockedRequestRoute.Reply.Request).request)
    }
}
