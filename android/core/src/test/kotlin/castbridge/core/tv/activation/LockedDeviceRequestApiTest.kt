package castbridge.core.tv.activation

import castbridge.core.owner.DeviceCode
import castbridge.core.owner.FactorKind
import castbridge.core.owner.Fingerprints
import castbridge.core.owner.OwnerFrames
import castbridge.core.tv.PinGuard
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `GET /api/activation/device-request` of a LOCKED TV (docs/DESIGN-ACTIVATION-SIMPLE-2026-10-07.md, F2): the phone that holds the connection code reads the TV's device
 * request by itself instead of having it recopied by hand. Same guards as the installation (local address, `Host`, terms before the code, global cap, [PinGuard]).
 * The answer is the COMPLETE request (same text as « Copier la demande complète »): `code=`, `k=`, `factor=`, `install=` (the installation's PUBLIC X25519 key, nothing secret, needed
 * by a trial key in a v2 envelope; absent when the TV has no key yet) and `install_sig=`, rebuilt from the parsed request, nothing else (ACT-F4 amended on 2026-10-07).
 */
class LockedDeviceRequestApiTest {
    private val pin = "482915"
    private var clock = 1_000_000L
    private var terms = true
    private var supplied = 0
    private val authorized = ArrayList<String>()
    private val fp = Fingerprints(mapOf(FactorKind.FLASH to "0a1b2c3d4e5f60718293a4b5c6d7e8f9", FactorKind.WIFI to "fedcba9876543210fedcba9876543210"))
    private val installPub = ByteArray(32) { (it + 3).toByte() }
    private val sigPub = ByteArray(32) { (it + 7).toByte() }
    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
    private val canaries = listOf("CANARY-MODEL-Bravia", "CANARY-TOKEN-cbk_42", "CANARY-OWNERPW-13")
    /** What the TV's own `requestText()` gives: the FULL request (with the `install=` line) plus lines of other things a newer TV might add. */
    private val fullText = OwnerFrames.deviceInfo(DeviceCode.of(fp), fp, installPub, sigPub) + "\nmodel=${canaries[0]}\ntoken=${canaries[1]}\nowner=${canaries[2]}"
    private var supplier: () -> String? = { fullText }
    private val installed = ArrayList<String>()

    private fun api(guard: PinGuard = PinGuard(pin, now = { clock })) = LockedActivationApi(guard, { key ->
        installed += key
        if (key == "GOOD") LockedActivationApi.Install.Accepted("Licence 1") else LockedActivationApi.Install.Rejected("Cette clé n'est pas celle de cette TV")
    }, { terms }, "1.0-test", now = { clock }, deviceRequest = { supplied++; supplier() }, onAuthorized = { authorized += it })

    private fun get(ip: String? = "192.168.1.30", pin: String? = this.pin, token: String? = null, host: String? = "192.168.1.20:8765", method: String = "GET", path: String = LockedActivationApi.DEVICE_REQUEST_PATH) =
        LockedActivationApi.Request(method, path, ip, host, pin, token, null)
    private fun post(ip: String = "192.168.1.30", pin: String? = this.pin) = LockedActivationApi.Request("POST", LockedActivationApi.PATH, ip, "192.168.1.20:8765", pin, null, 4)
    private val noBody: (Int) -> ByteArray? = { error("a read has no body") }
    private val good: (Int) -> ByteArray? = { n -> "GOOD".toByteArray().copyOf(n) }

    // ---- the code is required ----

    @Test fun `without the connection code nothing is produced`() {
        val a = api()
        val r = a.handle(get(pin = null), noBody)
        assertEquals(401, r.status)
        assertEquals(0, supplied, "the request is not even built")
        assertTrue(authorized.isEmpty())
        assertFalse(fullText.lines().first() in r.json)
    }

    @Test fun `a wrong code is refused, counted, and locks the address for the installation too`() {
        val a = api()
        repeat(4) { assertEquals(401, a.handle(get(pin = "000000"), noBody).status) }
        assertEquals(401, a.handle(get(pin = "000000"), noBody).status, "the 5th wrong code locks the address")
        val r = a.handle(get(), noBody)
        assertEquals(401, r.status); assertTrue("locked" in r.json, "the right code is refused while locked: ${r.json}")
        val i = a.handle(post(), good)
        assertEquals(401, i.status); assertTrue("locked" in i.json, "same counter as the installation: ${i.json}")
        assertEquals(0, supplied); assertTrue(installed.isEmpty()); assertTrue(authorized.isEmpty())
    }

    @Test fun `wrong codes on this route count in the global cap that closes the installation for everyone`() {
        val a = api()
        repeat(LockedActivationApi.GLOBAL_MAX_WRONG) { i -> assertEquals(401, a.handle(get(ip = "10.0.${i / 200}.${i % 200 + 1}", pin = "000000"), noBody).status) }
        val i = a.handle(post(ip = "192.168.1.77"), good)
        assertEquals(429, i.status, "the installation is closed after 20 wrong codes read on the device-request route")
        assertEquals(429, a.handle(get(ip = "192.168.1.78"), noBody).status, "and the device request too, even with the right code")
        assertEquals(0, supplied); assertTrue(installed.isEmpty())
        clock += LockedActivationApi.WINDOW_MS + 1
        assertEquals(200, a.handle(get(ip = "192.168.1.78"), noBody).status, "open again once the window slides")
    }

    // ---- the answer ----

    @Test fun `the right code gives the complete request as plain text`() {
        val r = api().handle(get(), noBody)
        assertEquals(200, r.status)
        assertEquals("text/plain; charset=utf-8", r.mime)
        val lines = r.json.lines()
        assertEquals("code=${DeviceCode.of(fp)}", lines[0])
        assertTrue(lines[1].startsWith("k="), lines[1])
        assertTrue(lines.any { it == "factor=FLASH|0a1b2c3d4e5f60718293a4b5c6d7e8f9" } && lines.any { it == "factor=WIFI|fedcba9876543210fedcba9876543210" }, r.json)
        assertTrue("install=x25519|" + hex(installPub) in lines, "the installation's public key is there: a trial key in a v2 envelope needs it\n${r.json}")
        assertEquals("install_sig=ed25519|" + hex(sigPub), lines.last(), "the signing key stays: the server signs it into the production activation")
        assertEquals(1, supplied)
        // the very text the phone's « Copier la demande complète » gives: the owner's tools read it
        val parsed = assertNotNull(OwnerFrames.parseDeviceInfo(r.json))
        assertEquals(DeviceCode.of(fp), parsed.code); assertContentEquals(installPub, parsed.installPub); assertContentEquals(sigPub, parsed.installSig)
        assertTrue(parsed.unknown.isEmpty())
        assertEquals(listOf("192.168.1.30"), authorized, "told which address presented the right code")
    }

    @Test fun `the install= line is present, the text is complete, and nothing else leaves whatever the TV supplies`() {
        val r = api().handle(get(), noBody)
        assertEquals(200, r.status)
        assertTrue(fullText.lines().any { it.startsWith("install=x25519|") }, "the fixture does hold the install= line")
        assertEquals(OwnerFrames.deviceInfo(DeviceCode.of(fp), fp, installPub, sigPub), r.json, "the complete request, byte for byte, as the TV's « Copier la demande complète »")
        for (l in r.json.lines()) assertTrue(l.startsWith("code=") || l.startsWith("k=") || l.startsWith("factor=") || l.startsWith("install=x25519|") || l.startsWith("install_sig="), "unexpected line: $l")
        assertEquals(1, r.json.lines().count { it.startsWith("install=") }, r.json)
        for (c in canaries) assertFalse(c in r.json, "$c must not leave the TV")
        assertFalse(pin in r.json, "the connection code is never echoed")
    }

    @Test fun `a TV without its installation key yet answers without the install= line, never an empty one`() {
        supplier = { OwnerFrames.deviceInfo(DeviceCode.of(fp), fp, null, sigPub) }
        val r = api().handle(get(), noBody)
        assertEquals(200, r.status)
        assertEquals(OwnerFrames.deviceInfo(DeviceCode.of(fp), fp, null, sigPub), r.json)
        assertTrue(r.json.lines().none { it.startsWith("install=") }, r.json)
        assertEquals("install_sig=ed25519|" + hex(sigPub), r.json.lines().last())
    }

    @Test fun `the text is rebuilt from the parsed request, never copied raw`() {
        // noise a TV could produce: CRLF, blank lines, indentation, the optional readable fingerprint, an install= of another algorithm, lines of other things
        supplier = { "\r\n  " + fullText.replace("\n", "\r\n\r\n  ") + "\r\ninstall=rsa|0123\r\nname=${canaries[0]}\r\n" }
        val r = api().handle(get(), noBody)
        assertEquals(200, r.status)
        assertEquals(OwnerFrames.deviceInfo(DeviceCode.of(fp), fp, installPub, sigPub), r.json)
        assertFalse("\r" in r.json); assertFalse("rsa" in r.json); assertFalse(r.json.lines().any { it.isBlank() })
    }

    @Test fun `a malformed install= line makes the request unreadable, a clean 503, and no raw line leaves`() {
        for (bad in listOf("install=x25519|zz", "install=x25519|" + hex(installPub).dropLast(2), "install=x25519|" + hex(installPub).uppercase())) {
            supplier = { OwnerFrames.deviceInfo(DeviceCode.of(fp), fp, null, sigPub) + "\n" + bad }
            val r = api().handle(get(), noBody)
            assertEquals(503, r.status, bad); assertFalse("x25519" in r.json || "zz" in r.json, r.json)
        }
        // a second install= line is as unreadable as a doubled signing key
        supplier = { fullText + "\ninstall=x25519|" + hex(installPub) }
        assertEquals(503, api().handle(get(), noBody).status)
    }

    @Test fun `a TV without a signing key answers without the install_sig line`() {
        supplier = { OwnerFrames.deviceInfo(DeviceCode.of(fp), fp, installPub, null) }
        val r = api().handle(get(), noBody)
        assertEquals(200, r.status)
        assertEquals(listOf("code=", "k=", "factor=", "factor=", "install="), r.json.lines().map { it.substringBefore('=') + "=" })
    }

    @Test fun `an unavailable or unreadable request is a clean 503`() {
        supplier = { null }; assertEquals(503, api().handle(get(), noBody).status)
        supplier = { "code=pas-un-code\nk=2" }; assertEquals(503, api().handle(get(), noBody).status)
        supplier = { error("keystore down") }
        val r = api().handle(get(), noBody)
        assertEquals(503, r.status); assertFalse("keystore" in r.json, "no internal detail")
    }

    // ---- the same guards as the installation ----

    @Test fun `the terms of use come before the code`() {
        terms = false
        var compared = 0
        val counting = object : PinGuard(pin, now = { clock }) { override fun check(ip: String, given: String?): Result { compared++; return super.check(ip, given) } }
        val a = api(counting)
        assertEquals(409, a.handle(get(pin = "000000"), noBody).status, "a wrong code is not told")
        assertEquals(409, a.handle(get(), noBody).status, "a right code is not told")
        assertEquals(409, a.handle(get(pin = null), noBody).status)
        assertEquals(0, compared); assertEquals(0, supplied)
        repeat(10) { assertEquals(409, a.handle(get(pin = "000000"), noBody).status) }
        terms = true
        assertEquals(200, a.handle(get(), noBody).status, "the refused-terms requests did not count as wrong codes")
    }

    @Test fun `only the local network is served and the Host must be local`() {
        for (ip in listOf("8.8.8.8", "100.64.1.2", null, "example.com")) assertEquals(403, api().handle(get(ip = ip), noBody).status, ip.toString())
        for (ip in listOf("10.0.0.5", "172.20.1.1", "192.168.49.2", "192.168.49.37", "fe80::1%wlan0", "127.0.0.1")) assertEquals(200, api().handle(get(ip = ip), noBody).status, "$ip (a phone in the activation group is 192.168.49.x)")
        assertEquals(403, api().handle(get(host = "evil.example.com"), noBody).status, "DNS rebinding")
    }

    @Test fun `a refused peer never makes the TV build its request`() {
        val a = api()
        a.handle(get(ip = "8.8.8.8"), noBody); a.handle(get(host = "evil.example.com"), noBody); a.handle(get(ip = null), noBody)
        assertEquals(0, supplied)
    }

    @Test fun `a trusted-phone token never opens it`() {
        val r = api().handle(get(pin = null, token = "cbt1" + "a".repeat(64)), noBody)
        assertEquals(403, r.status); assertTrue("code de la TV" in r.json, r.json)
        assertEquals(0, supplied)
    }

    @Test fun `the method is checked before the code`() {
        var compared = 0
        val counting = object : PinGuard(pin, now = { clock }) { override fun check(ip: String, given: String?): Result { compared++; return super.check(ip, given) } }
        val a = api(counting)
        assertEquals(405, a.handle(get(method = "POST"), noBody).status)
        assertEquals(405, a.handle(get(method = "HEAD"), noBody).status)
        assertEquals(405, a.handle(LockedActivationApi.Request("GET", LockedActivationApi.PATH, "192.168.1.30", "192.168.1.20:8765", pin, null, null), noBody).status, "the installation stays POST only")
        assertEquals(0, compared)
    }

    @Test fun `no other path opens, not even a near miss`() {
        val a = api()
        for (p in listOf("/api/tv/device-request", "/api/activation/device-request/", "/api/activation/Device-Request", "/api/activation/device-request.txt", "/api/activation/device", "/api/activation/request", "/api/activation"))
            assertEquals(403, a.handle(get(path = p), noBody).status, p)
        assertEquals(0, supplied)
        val h = a.handle(get(path = "/api/hello", pin = null), noBody)
        assertEquals(200, h.status); assertTrue("\"locked\":true" in h.json)
    }

    // ---- bounded work ----

    @Test fun `reads are bounded per address and never eat the installation tries`() {
        val a = api()
        repeat(LockedActivationApi.MAX_READS) { assertEquals(200, a.handle(get(), noBody).status, "read ${it + 1}") }
        val over = a.handle(get(), noBody)
        assertEquals(429, over.status); assertEquals(LockedActivationApi.MAX_READS, supplied, "the request is not rebuilt beyond the bound")
        assertEquals(200, a.handle(get(ip = "192.168.1.31"), noBody).status, "another address is not blocked")
        assertEquals(200, a.handle(post(), good).status, "the same address can still install: reads are not key verifications")
        clock += LockedActivationApi.WINDOW_MS + 1
        assertEquals(200, a.handle(get(), noBody).status, "the window slides")
    }

    @Test fun `the read table is bounded like the others`() {
        val a = api()
        repeat(LockedActivationApi.MAX_ADDRESSES + 50) { i -> a.handle(get(ip = "fd00::${Integer.toHexString(i)}"), noBody) }
        assertTrue(a.trackedAddresses() <= LockedActivationApi.MAX_ADDRESSES)
        assertTrue(a.trackedReaders() <= LockedActivationApi.MAX_ADDRESSES, "readers: ${a.trackedReaders()}")
    }

    @Test fun `the authorised hook hears the address of a right code only`() {
        val a = api()
        a.handle(get(pin = "000000"), noBody); a.handle(get(pin = null), noBody)
        assertTrue(authorized.isEmpty(), "a wrong or missing code is not a linked phone")
        a.handle(post(ip = "192.168.49.2"), good)
        a.handle(get(ip = "192.168.49.2"), noBody)
        assertEquals(listOf("192.168.49.2", "192.168.49.2"), authorized)
        val boom = LockedActivationApi(PinGuard(pin, now = { clock }), { LockedActivationApi.Install.Accepted("x") }, { true }, "1.0", now = { clock }, deviceRequest = { fullText }, onAuthorized = { error("listener bug") })
        assertEquals(200, boom.handle(get(), noBody).status, "a failing listener never breaks the answer")
    }

    // ---- the real server ----

    @Test fun `the real server answers in plain text and listens on every interface`() {
        val s = LockedActivationServer(api(), 0)
        s.start(5_000, true)
        try {
            assertNull(s.hostname, "no host name given: NanoHTTPD binds the wildcard address, so the Wi-Fi Direct group interface (192.168.49.1) is served as soon as it exists")
            val port = s.listeningPort
            fun call(host: String, pinHeader: String?): Triple<Int, String?, String> {
                val c = URL("http://$host:$port${LockedActivationApi.DEVICE_REQUEST_PATH}").openConnection() as HttpURLConnection
                c.connectTimeout = 4000; c.readTimeout = 4000
                pinHeader?.let { c.setRequestProperty("X-CB-Pin", it) }
                val code = c.responseCode
                return Triple(code, c.contentType, (if (code < 400) c.inputStream else c.errorStream)?.use { String(it.readBytes(), Charsets.UTF_8) }.orEmpty())
            }
            assertEquals(401, call("127.0.0.1", null).first)
            assertEquals(401, call("127.0.0.1", "000000").first)
            val ok = call("127.0.0.1", pin)
            assertEquals(200, ok.first, ok.third)
            assertEquals("text/plain; charset=utf-8", ok.second)
            assertTrue(ok.third.startsWith("code="), ok.third); assertTrue(ok.third.lines().any { it.startsWith("install=x25519|") }, "the complete request, install= included")
            // through the machine's own LAN address (not the loopback): what a phone in the group does on 192.168.49.1
            val lan = runCatching { NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }.flatMap { it.inetAddresses.toList() }.filterIsInstance<Inet4Address>().firstOrNull { it.isSiteLocalAddress }?.hostAddress }.getOrNull()
            if (lan != null) assertEquals(200, call(lan, pin).first, "reachable through $lan")
        } finally { s.stop() }
    }

    @Test fun `the install route keeps answering JSON`() {
        val s = LockedActivationServer(api(), 0)
        s.start(5_000, true)
        try {
            val c = URL("http://127.0.0.1:${s.listeningPort}${LockedActivationApi.PATH}").openConnection() as HttpURLConnection
            c.requestMethod = "POST"; c.doOutput = true; c.setRequestProperty("X-CB-Pin", pin)
            c.outputStream.use { it.write("GOOD".toByteArray()) }
            assertEquals(200, c.responseCode)
            assertEquals("application/json; charset=utf-8", c.contentType)
        } finally { s.stop() }
    }
}
