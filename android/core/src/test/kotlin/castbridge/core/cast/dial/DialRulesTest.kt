package castbridge.core.cast.dial

import java.net.InetAddress
import java.util.Random
import kotlin.test.*

class DialRulesTest {
    private fun ip(s: String) = InetAddress.getByName(s)

    @Test fun lanSourcesAreAcceptedAndTheRestIgnored() {
        for (a in listOf("127.0.0.1", "10.0.0.5", "172.16.0.1", "172.31.255.254", "192.168.0.12", "169.254.3.4", "::1", "fd12:3456::1", "fc00::9", "fe80::1"))
            assertTrue(DialRules.isLanSource(ip(a)), a)
        for (a in listOf("8.8.8.8", "172.15.0.1", "172.32.0.1", "192.169.0.1", "11.0.0.1", "169.253.0.1", "100.64.0.1", "2001:4860::1", "fe00::1", "fb00::1", "224.0.0.1"))
            assertFalse(DialRules.isLanSource(ip(a)), a)
        assertFalse(DialRules.isLanSource(null))
    }

    @Test fun hostMustBeExactlyOneOfTheDeviceAddresses() {
        val ok = setOf("192.168.0.20:30765")
        assertTrue(DialRules.hostAllowed(listOf("192.168.0.20:30765"), ok))
        assertFalse(DialRules.hostAllowed(listOf("evil.example:30765"), ok))
        assertFalse(DialRules.hostAllowed(listOf("192.168.0.20"), ok))
        assertFalse(DialRules.hostAllowed(emptyList(), ok))
        assertFalse(DialRules.hostAllowed(listOf("192.168.0.20:30765", "192.168.0.20:30765"), ok), "deux Host = ambigu")
    }

    @Test fun originIsAbsentOrTheYouTubeAppOnly() {
        assertTrue(DialRules.originAllowed(emptyList()))
        assertTrue(DialRules.originAllowed(listOf("package:com.google.android.youtube")))
        assertFalse(DialRules.originAllowed(listOf("https://www.youtube.com")))
        assertFalse(DialRules.originAllowed(listOf("http://evil.example")))
        assertFalse(DialRules.originAllowed(listOf("null")))
        assertFalse(DialRules.originAllowed(listOf("package:com.google.android.youtube", "http://evil.example")))
        assertFalse(DialRules.originAllowed(listOf("")))
    }

    @Test fun identityIsStablePerInstallAndShort() {
        assertEquals(DialRules.udn("abc"), DialRules.udn("abc"))
        assertNotEquals(DialRules.udn("abc"), DialRules.udn("abd"))
        assertTrue(DialRules.udn("abc").startsWith("uuid:"))
        assertTrue(Regex("CastBridge-TV [0-9A-F]{4}").matches(DialRules.friendlyName("abc")), DialRules.friendlyName("abc"))
        assertEquals(DialRules.friendlyName("abc"), DialRules.friendlyName("abc"))
    }

    @Test fun deviceDescriptionAndStatusXml() {
        val d = DialRules.deviceDescription("abc", "Ama<zon>", "M&M")
        assertTrue("<friendlyName>${DialRules.friendlyName("abc")}</friendlyName>" in d)
        assertTrue("<UDN>${DialRules.udn("abc")}</UDN>" in d)
        assertTrue("<manufacturer>Ama&lt;zon&gt;</manufacturer>" in d && "<modelName>M&amp;M</modelName>" in d)
        assertTrue(d.startsWith("<?xml"))
        val r = DialRules.appStatus("YouTube", DialRules.AppState.RUNNING, true)
        assertTrue("<state>running</state>" in r && "<name>YouTube</name>" in r && "dialVer=\"2.1\"" in r && "<link rel=\"run\" href=\"run\"/>" in r)
        val s = DialRules.appStatus("YouTube", DialRules.AppState.STOPPED, true)
        assertTrue("<state>stopped</state>" in s && "rel=\"run\"" !in s)
        assertTrue("<state>hidden</state>" in DialRules.appStatus("YouTube", DialRules.AppState.HIDDEN, false))
    }

    private fun launch(s: String) = DialRules.buildLaunch(s.toByteArray(Charsets.ISO_8859_1))

    @Test fun launchBodyBecomesTheQueryOfTheFixedUrl() {
        val l = launch("pairingCode=1a2b-3c&theme=cl&v=2&t=abc%3D%2B.~") as DialRules.Launch.Ok
        assertEquals("https://www.youtube.com/tv?pairingCode=1a2b-3c&theme=cl&v=2&t=abc%3D%2B.~", l.url)
        assertEquals("https://www.youtube.com/tv", (launch("") as DialRules.Launch.Ok).url)
        assertEquals("a=b", (launch("a=b\r\n") as DialRules.Launch.Ok).query, "fin de ligne finale tolérée")
    }

    @Test fun launchBodyIsStrict() {
        for (bad in listOf("a=b\r\nHost: x", "a=b\nc=d", "a=b&c=https://evil", "a=javascript:x", "a=%0d%0a", "a=%0A", "a=%00", "a=%zz", "a b=c", "=x", "a=<s>", "a=\"q\"", "a=b;c=d", "ké=1", "a=b#frag", "a=b?c", "a=/x", "a=\\x"))
            assertTrue(launch(bad) is DialRules.Launch.Refused, "refusé : $bad")
        assertEquals(413, (launch("a=" + "x".repeat(1025)) as DialRules.Launch.Refused).status)
        assertTrue(launch("a=" + "x".repeat(1022)) is DialRules.Launch.Ok, "1 Ko pile")
        assertEquals(413, (DialRules.buildLaunch(ByteArray(4097) { 'a'.code.toByte() }) as DialRules.Launch.Refused).status)
        assertEquals(400, (DialRules.buildLaunch(byteArrayOf(0xC3.toByte(), 0xA9.toByte())) as DialRules.Launch.Refused).status)
    }

    @Test fun logsNeverShowValues() {
        val r = DialRules.redact("pairingCode=SECRET123&theme=cl")
        assertFalse("SECRET123" in r)
        assertEquals("pairingCode=***&theme=***", r)
    }

    @Test fun rateLimiterAllowsSixPerMinute() {
        var now = 0L
        val l = LaunchRateLimiter(6, 60_000) { now }
        repeat(6) { assertTrue(l.tryAcquire(), "lancement $it") }
        assertFalse(l.tryAcquire())
        assertEquals(60, l.retryAfterSeconds())
        now = 59_999; assertFalse(l.tryAcquire())
        now = 60_000; assertTrue(l.tryAcquire(), "la fenêtre glisse")
    }

    @Test fun ssdpSearchParsing() {
        fun req(vararg h: String) = "M-SEARCH * HTTP/1.1\r\n" + h.joinToString("") { "$it\r\n" } + "\r\n"
        val ok = Ssdp.parseSearch(req("HOST: 239.255.255.250:1900", "MAN: \"ssdp:discover\"", "MX: 3", "ST: urn:dial-multiscreen-org:service:dial:1", "USER-AGENT: Youtube/1"))!!
        assertEquals(3, ok.mx); assertEquals(DialRules.SEARCH_TARGET, ok.st)
        assertEquals(5, Ssdp.parseSearch(req("MAN: \"ssdp:discover\"", "MX: 120", "ST: ssdp:all"))!!.mx, "MX borné à 5")
        assertEquals(1, Ssdp.parseSearch(req("man: \"ssdp:discover\"", "st: upnp:rootdevice"))!!.mx, "MX absent = 1 ; noms insensibles à la casse")
        assertNull(Ssdp.parseSearch(req("MAN: ssdp:discover", "MX: 3", "ST: ssdp:all")), "MAN sans guillemets")
        assertNull(Ssdp.parseSearch(req("MAN: \"ssdp:discover\"", "MX: abc", "ST: ssdp:all")))
        assertNull(Ssdp.parseSearch(req("MAN: \"ssdp:discover\"", "MX: 0", "ST: ssdp:all")))
        assertNull(Ssdp.parseSearch(req("MAN: \"ssdp:discover\"", "MX: 3")), "ST manquant")
        assertNull(Ssdp.parseSearch("NOTIFY * HTTP/1.1\r\nMAN: \"ssdp:discover\"\r\nST: ssdp:all\r\n\r\n"))
        assertNull(Ssdp.parseSearch("GET / HTTP/1.1\r\n\r\n"))
        assertNull(Ssdp.parseSearch(req("MAN: \"ssdp:discover\"", "ST: ssdp:all", "X: " + "a".repeat(1600))), "datagramme trop grand")
    }

    @Test fun ssdpTargets() {
        assertEquals(DialRules.SEARCH_TARGET, Ssdp.answerTarget(DialRules.SEARCH_TARGET))
        assertEquals(DialRules.SEARCH_TARGET, Ssdp.answerTarget("ssdp:all"))
        assertEquals("upnp:rootdevice", Ssdp.answerTarget("upnp:rootdevice"))
        assertNull(Ssdp.answerTarget("urn:schemas-upnp-org:device:MediaRenderer:1"))
        assertNull(Ssdp.answerTarget("uuid:other"))
    }

    @Test fun ssdpJitterStaysWithinMx() {
        val rnd = Random(42)
        repeat(2000) { val j = Ssdp.jitterMs(3, rnd); assertTrue(j in 0 until 3000, "$j") }
        repeat(500) { val j = Ssdp.jitterMs(99, rnd); assertTrue(j in 0 until 5000, "$j") }
        assertTrue((0 until 500).map { Ssdp.jitterMs(5, rnd) }.toSet().size > 50, "pas constant")
    }

    @Test fun ssdpMessagesHaveTheExactFormat() {
        val udn = "uuid:11111111-2222-3333-4444-555555555555"
        assertEquals(
            "HTTP/1.1 200 OK\r\nLOCATION: http://192.168.0.20:30765/dd.xml\r\nCACHE-CONTROL: max-age=1800\r\nEXT:\r\nSERVER: ${Ssdp.SERVER}\r\n" +
                "ST: urn:dial-multiscreen-org:service:dial:1\r\nUSN: $udn::urn:dial-multiscreen-org:service:dial:1\r\n\r\n",
            Ssdp.searchResponse(DialRules.SEARCH_TARGET, "http://192.168.0.20:30765/dd.xml", udn))
        assertTrue("USN: $udn::upnp:rootdevice\r\n" in Ssdp.searchResponse("upnp:rootdevice", "http://x/dd.xml", udn))
        assertEquals(
            "NOTIFY * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nCACHE-CONTROL: max-age=1800\r\nLOCATION: http://h/dd.xml\r\nNT: upnp:rootdevice\r\nNTS: ssdp:alive\r\nSERVER: ${Ssdp.SERVER}\r\nUSN: $udn::upnp:rootdevice\r\n\r\n",
            Ssdp.notifyMessage("upnp:rootdevice", udn, "http://h/dd.xml", true))
        assertEquals("NOTIFY * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nNT: $udn\r\nNTS: ssdp:byebye\r\nUSN: $udn\r\n\r\n", Ssdp.notifyMessage(udn, udn, null, false))
        assertEquals(listOf("upnp:rootdevice", udn, DialRules.SEARCH_TARGET), Ssdp.notifyTargets(udn))
    }
}
