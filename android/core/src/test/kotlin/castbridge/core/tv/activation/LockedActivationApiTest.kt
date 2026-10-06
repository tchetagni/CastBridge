package castbridge.core.tv.activation

import castbridge.core.tv.PinGuard
import java.net.HttpURLConnection
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * « Activer par le Wi-Fi » a LOCKED TV (docs/TV-ACTIVATION-CLE-USB.md, wios-tv-05 decided by the owner, with the PIN kept): the only route is POST /api/activation/install,
 * behind the TV's connection code, from the local network, 16 Kio at most, the key verified by the same path as a pasted one.
 */
class LockedActivationApiTest {
    private val pin = "482915"
    private var clock = 1_000_000L
    private val installed = ArrayList<String>()
    private var terms = true
    private fun api(guard: PinGuard = PinGuard(pin, now = { clock })) = LockedActivationApi(guard, { key ->
        installed += key
        if (key == "GOOD") LockedActivationApi.Install.Accepted("Licence 1") else LockedActivationApi.Install.Rejected("Cette clé n'est pas celle de cette TV")
    }, { terms }, "1.0-test", now = { clock })

    private fun req(path: String = LockedActivationApi.PATH, method: String = "POST", ip: String? = "192.168.1.30", pin: String? = this.pin, token: String? = null, len: Long? = 4, host: String? = "192.168.1.20:8765") =
        LockedActivationApi.Request(method, path, ip, host, pin, token, len)
    private fun body(s: String): (Int) -> ByteArray? = { n -> s.toByteArray().copyOf(n) }

    @Test fun `a valid key with the connection code activates`() {
        val r = api().handle(req(len = 6), body("GOOD\r\n"))
        assertEquals(200, r.status); assertTrue("\"installed\":true" in r.json && "Licence 1" in r.json, r.json)
        assertEquals(listOf("GOOD"), installed, "trimmed, verified once")
    }

    @Test fun `a refused key says why and nothing more`() {
        val r = api().handle(req(), body("BAD!"))
        assertEquals(422, r.status); assertTrue("autre" in r.json || "pas celle" in r.json, r.json)
        assertFalse("BAD!" in r.json, "the key is never echoed")
    }

    @Test fun `no code, a wrong code or a trusted-phone token never reach the verifier`() {
        val a = api()
        assertEquals(401, a.handle(req(pin = null), body("GOOD")).status)
        assertEquals(401, a.handle(req(pin = "000000"), body("GOOD")).status)
        val t = a.handle(req(pin = null, token = "cbt1" + "a".repeat(64)), body("GOOD"))
        assertEquals(403, t.status); assertTrue("code de la TV" in t.json)
        assertTrue(installed.isEmpty())
    }

    @Test fun `wrong codes lock the address out like the full API`() {
        val a = api()
        repeat(5) { a.handle(req(pin = "000000"), body("GOOD")) }
        val r = a.handle(req(), body("GOOD"))
        assertEquals(401, r.status); assertTrue("locked" in r.json)
        assertTrue(installed.isEmpty())
    }

    @Test fun `only the local network is served`() {
        for (ip in listOf("8.8.8.8", "100.64.1.2", null, "example.com")) {
            val r = api().handle(req(ip = ip), body("GOOD"))
            assertEquals(403, r.status, ip.toString())
        }
        for (ip in listOf("10.0.0.5", "172.20.1.1", "192.168.49.2", "fe80::1%wlan0", "127.0.0.1")) assertEquals(200, api().handle(req(ip = ip), body("GOOD")).status, ip)
        assertEquals(403, api().handle(req(host = "evil.example.com"), body("GOOD")).status, "DNS rebinding")
    }

    @Test fun `the body is bounded to 16 Kio and must be there`() {
        assertEquals(413, api().handle(req(len = LockedActivationApi.MAX_BODY + 1L), { error("never read") }).status)
        assertEquals(400, api().handle(req(len = 0), { error("never read") }).status)
        assertEquals(400, api().handle(req(len = null), { error("never read") }).status)
        assertEquals(400, api().handle(req(len = 4), { null }).status, "body cut short")
        assertEquals(200, api().handle(req(len = LockedActivationApi.MAX_BODY.toLong()), { n -> ("GOOD" + " ".repeat(n - 4)).toByteArray() }).status)
    }

    @Test fun `ten verifications per address and per ten minutes`() {
        val a = api()
        repeat(LockedActivationApi.MAX_TRIES) { assertEquals(422, a.handle(req(), body("BAD!")).status) }
        assertEquals(429, a.handle(req(), body("GOOD")).status)
        assertEquals(422, a.handle(req(ip = "192.168.1.31"), body("BAD!")).status, "another address is not blocked")
        clock += LockedActivationApi.WINDOW_MS + 1
        assertEquals(200, a.handle(req(), body("GOOD")).status)
    }

    @Test fun `the terms of use must have been accepted on the TV`() {
        terms = false
        val r = api().handle(req(), body("GOOD"))
        assertEquals(409, r.status); assertTrue("conditions d'usage" in r.json)
        assertTrue(installed.isEmpty())
    }

    @Test fun `every other route stays locked, hello only says who answers`() {
        val a = api()
        for (p in listOf("/api/info", "/api/play", "/upload/x.mp4", "/api/activation", "/api/activation/request", "/api/tv/device-request", "/api/ssh/keys", "/", "/stream/x")) {
            val r = a.handle(req(path = p, method = "GET"), { error("never read") })
            assertEquals(403, r.status, p); assertTrue("\"locked\":true" in r.json, p)
        }
        assertEquals(405, a.handle(req(method = "GET"), { error("never read") }).status)
        val h = a.handle(req(path = "/api/hello", method = "GET", pin = null), { error("never read") })
        assertEquals(200, h.status); assertTrue("\"locked\":true" in h.json && "castbridge-tv" in h.json && "\"pinRequired\":true" in h.json, h.json)
        assertFalse(pin in h.json)
    }

    @Test fun `the activation screen shows the code and says when the TV has no network`() {
        assertEquals("Par le Wi-Fi : code de connexion 482915 · TV 192.168.1.20", LockedWifiTexts.line("482915", listOf("192.168.1.20", "192.168.49.1")))
        assertTrue("Bluetooth" in LockedWifiTexts.line("482915", emptyList()))
    }

    @Test fun `the real server answers over HTTP with the same rules`() {
        val s = LockedActivationServer(api(), 0)
        s.start(5_000, true)
        try {
            val port = s.listeningPort
            fun post(pinHeader: String?, text: String): Pair<Int, String> {
                val c = URL("http://127.0.0.1:$port${LockedActivationApi.PATH}").openConnection() as HttpURLConnection
                c.requestMethod = "POST"; c.doOutput = true; pinHeader?.let { c.setRequestProperty("X-CB-Pin", it) }
                c.outputStream.use { it.write(text.toByteArray()) }
                val code = c.responseCode
                val b = (if (code < 400) c.inputStream else c.errorStream)?.use { String(it.readBytes()) }.orEmpty()
                return code to b
            }
            assertEquals(401, post(null, "GOOD").first)
            val ok = post(pin, "GOOD")
            assertEquals(200, ok.first, ok.second)
            val big = post(pin, "x".repeat(LockedActivationApi.MAX_BODY + 10))
            assertEquals(413, big.first)
            val g = URL("http://127.0.0.1:$port/api/info").openConnection() as HttpURLConnection
            g.setRequestProperty("X-CB-Pin", pin)
            assertEquals(403, g.responseCode)
        } finally { s.stop() }
    }
}
