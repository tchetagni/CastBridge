package castbridge.core.smart

import castbridge.core.remote.RemoteKey
import castbridge.core.remote.smart.*
import java.io.IOException
import kotlin.test.*

/** Each strategy against a local fake of the TV: no hardware. */
class StrategyServersTest {
    private val fpUnknown = TvFingerprint.unknown("127.0.0.1")

    // ---- CVTE ----
    private val CVTE_INFO = """{"status":500,"data":{"config":{"Voice":true},"height":720,"width":1280,"httpPort":"9909","name":"SMART_TV","port":8125,"websocketPort":"8125"},"msg":"successful"}"""

    @Test fun cvteReadsInfoSendsKeysAndReconnectsAfterACut() {
        FakeWs { it.sendText(CVTE_INFO) }.use { ws ->
            val s = CvteStrategy(ws.target(), ws.port)
            assertTrue(s.probe().reachable)
            s.connect()
            assertEquals(StrategyState.Kind.READY, s.state.kind); assertEquals("SMART_TV", s.info?.name); assertEquals(1280, s.info?.width)
            s.send(RemoteKey.VOLUME_UP); s.send(RemoteKey.VOLUME_DOWN)
            val c0 = ws.conns[0]
            assertEquals("08011202" + "3234", Pb.hex(c0.next() as ByteArray)); assertEquals("08011202" + "3235", Pb.hex(c0.next() as ByteArray))
            // the TV drops the link (idle timeout): the next key reconnects by itself, once
            c0.close(); Thread.sleep(300)
            s.send(RemoteKey.DPAD_CENTER)
            assertEquals(2, ws.conns.size)
            assertEquals("080112023233", Pb.hex(ws.conns[1].next() as ByteArray))      // key 23, in decimal text
            assertTrue(s.warnings.single().contains("aucune authentification"))
            s.close(); assertEquals(StrategyState.Kind.CLOSED, s.state.kind)
        }
    }

    @Test fun cvteFailsCleanlyWhenNothingListens() {
        val s = CvteStrategy(testTarget(), port = 1)
        assertFalse(s.probe().reachable)
        assertFailsWith<IOException> { s.connect() }
        assertEquals(StrategyState.Kind.FAILED, s.state.kind)
    }

    // ---- Roku ----
    @Test fun rokuKeysTextAndProbe() {
        FakeHttp { r -> if (r.path == "/query/device-info") 200 to "<device-info><model-name>Roku TV</model-name></device-info>" else 200 to "" }.use { h ->
            val s = RokuStrategy(testTarget(), h.port)
            assertTrue(s.probe().reachable); s.connect()
            s.send(RemoteKey.VOLUME_UP); s.send(RemoteKey.DPAD_CENTER)
            assertTrue(s.sendText("é a"))
            val posts = h.requests.filter { it.method == "POST" }.map { it.path }
            assertEquals(listOf("/keypress/VolumeUp", "/keypress/Select", "/keypress/Lit_%C3%A9", "/keypress/Lit_%20", "/keypress/Lit_a"), posts)
            assertFailsWith<KeyUnsupported> { s.send(RemoteKey.NUM_5) }
            assertFalse(RemoteKey.STOP in s.capabilities.keys)
        }
    }

    @Test fun rokuRefusalSurfacesAsAnError() {
        FakeHttp { r -> if (r.method == "GET") 200 to "<device-info/>" else 403 to "" }.use { h ->
            val s = RokuStrategy(testTarget(), h.port); s.connect()
            assertFailsWith<IOException> { s.send(RemoteKey.HOME) }
        }
    }

    // ---- DLNA ----
    @Test fun dlnaPlayPauseAndVolume() {
        var vol = 20; var playing = true
        lateinit var h: FakeHttp
        h = FakeHttp { r ->
            when {
                r.method == "GET" -> 200 to FingerprintTest.DLNA_XML
                "#GetVolume" in (r.headers["soapaction"] ?: "") -> 200 to "<s:Envelope><s:Body><u:GetVolumeResponse><CurrentVolume>$vol</CurrentVolume></u:GetVolumeResponse></s:Body></s:Envelope>"
                "#SetVolume" in (r.headers["soapaction"] ?: "") -> { vol = Regex("<DesiredVolume>(\\d+)").find(r.body)!!.groupValues[1].toInt(); 200 to "<ok/>" }
                "#GetTransportInfo" in (r.headers["soapaction"] ?: "") -> 200 to "<CurrentTransportState>${if (playing) "PLAYING" else "PAUSED_PLAYBACK"}</CurrentTransportState>"
                "#Pause" in (r.headers["soapaction"] ?: "") -> { playing = false; 200 to "<ok/>" }
                "#Play" in (r.headers["soapaction"] ?: "") -> { playing = true; 200 to "<ok/>" }
                else -> 200 to "<ok/>"
            }
        }
        h.use {
            val s = DlnaStrategy(testTarget(), null, h.url + "/description.xml")
            assertTrue(s.probe().reachable); s.connect()
            s.send(RemoteKey.VOLUME_UP); assertEquals(25, vol)
            s.send(RemoteKey.VOLUME_DOWN); s.send(RemoteKey.VOLUME_DOWN); assertEquals(15, vol)
            s.send(RemoteKey.PLAY_PAUSE); assertFalse(playing)
            s.send(RemoteKey.PLAY_PAUSE); assertTrue(playing)
            s.send(RemoteKey.VOLUME_MUTE)
            val mute = h.requests.last { "#SetMute" in (it.headers["soapaction"] ?: "") }
            assertTrue("<DesiredMute>1</DesiredMute>" in mute.body)
            assertTrue(h.requests.any { it.path == "/AVT/control" && it.headers["soapaction"]!!.startsWith("\"urn:schemas-upnp-org:service:AVTransport:1#") })
            assertFailsWith<KeyUnsupported> { s.send(RemoteKey.DPAD_UP) }
        }
    }

    @Test fun dlnaRefusesToLeaveTheChosenTv() {
        val evil = Upnp.parseDescription(FingerprintTest.DLNA_XML, "http://10.9.9.9:80/d.xml")   // control URLs point at another host
        val s = DlnaStrategy(testTarget(), evil, null)
        s.connect()
        assertFailsWith<IllegalArgumentException> { s.send(RemoteKey.VOLUME_UP) }
    }

    // ---- Sony ----
    @Test fun sonySendsIrccWithThePskAndAsksForItWhenMissing() {
        FakeHttp { r -> if (r.headers["x-auth-psk"] == "1234") 200 to "{}" else 403 to "" }.use { h ->
            val secrets = MemorySecretStore()
            val s = SonyStrategy(testTarget(), secrets, h.port)
            val e = assertFailsWith<StrategyException> { s.connect() }
            assertTrue(e.needsPairing); assertEquals(StrategyState.Kind.NEEDS_PAIRING, s.state.kind)
            s.pair("0000"); assertFailsWith<StrategyException> { s.connect() }; assertNull(secrets.get("sony-psk:tv1"), "mauvaise clé oubliée")
            s.pair("1234"); s.connect(); assertEquals(StrategyState.Kind.READY, s.state.kind)
            s.send(RemoteKey.VOLUME_UP)
            val r = h.requests.last { it.path == "/sony/IRCC" }
            assertTrue("<IRCCCode>AAAAAQAAAAEAAAASAw==</IRCCCode>" in r.body)
            assertEquals("\"urn:schemas-sony-com:service:IRCC:1#X_SendIRCC\"", r.headers["soapaction"])
        }
    }

    // ---- Philips ----
    @Test fun philipsV1KeyPostAndV6Detection() {
        FakeHttp { r -> if (r.path == "/1/system") 200 to """{"name":"TV"}""" else 200 to "" }.use { h ->
            val s = PhilipsStrategy(testTarget(), h.port); s.connect(); s.send(RemoteKey.VOLUME_UP)
            val r = h.requests.last { it.method == "POST" }
            assertEquals("/1/input/key", r.path); assertEquals("""{"key":"VolumeUp"}""", r.body)
            assertEquals(StrategyStatus.EXPERIMENTAL, s.status)
        }
        FakeHttp { 404 to "" }.use { h -> assertFailsWith<IOException> { PhilipsStrategy(testTarget(), h.port).connect() } }
    }

    // ---- Vizio ----
    @Test fun vizioPairsOnceThenSendsKeysWithTheToken() {
        FakeHttp { r ->
            when {
                r.path == "/pairing/start" -> 200 to """{"ITEM":{"PAIRING_REQ_TOKEN":42}}"""
                r.path == "/pairing/pair" -> if ("\"RESPONSE_VALUE\":\"1234\"" in r.body && "\"PAIRING_REQ_TOKEN\":42" in r.body) 200 to """{"ITEM":{"AUTH_TOKEN":"TOK"}}""" else 200 to """{"ITEM":{}}"""
                r.path == "/key_command/" -> if (r.headers["auth"] == "TOK") 200 to "{}" else 403 to ""
                else -> 404 to ""
            }
        }.use { h ->
            val secrets = MemorySecretStore()
            val s = VizioStrategy(testTarget(), secrets, listOf(h.port), scheme = "http")
            assertTrue(assertFailsWith<StrategyException> { s.connect() }.needsPairing)
            assertFailsWith<IOException> { s.pair("9999") }
            s.pair("1234"); s.connect(); s.send(RemoteKey.VOLUME_DOWN)
            val k = h.requests.last { it.path == "/key_command/" }
            assertEquals("""{"KEYLIST":[{"CODESET":5,"CODE":0,"ACTION":"KEYPRESS"}]}""", k.body)
        }
    }

    // ---- Samsung ----
    @Test fun samsungPairsStoresTheTokenAndReusesIt() {
        FakeWs { it.sendText("""{"event":"ms.channel.connect","data":{"token":"tok123","clients":[]}}""") }.use { ws ->
            val secrets = MemorySecretStore()
            val s = SamsungStrategy(ws.target(), secrets, listOf(ws.port to false), pairWaitMs = 3000)
            s.connect(); s.send(RemoteKey.VOLUME_UP)
            assertTrue(ws.conns[0].path.startsWith("/api/v2/channels/samsung.remote.control?name="))
            assertFalse("token=" in ws.conns[0].path, "pas encore de jeton à la première connexion")
            val m = ws.conns[0].next() as String
            assertEquals("""{"method":"ms.remote.control","params":{"Cmd":"Click","DataOfCmd":"KEY_VOLUP","Option":"false","TypeOfRemote":"SendRemoteKey"}}""", m)
            assertEquals("tok123", secrets.get("samsung-token:tv1"))
            s.close()
            val s2 = SamsungStrategy(ws.target(), secrets, listOf(ws.port to false)); s2.connect()
            assertTrue(ws.conns[1].path.endsWith("&token=tok123"))
        }
    }

    @Test fun samsungAsksTheUserToAcceptOnTheTv() {
        FakeWs { /* silent: nobody presses OK on the TV */ }.use { ws ->
            val s = SamsungStrategy(ws.target(), MemorySecretStore(), listOf(ws.port to false), pairWaitMs = 700)
            assertTrue(assertFailsWith<StrategyException> { s.connect() }.needsPairing)
            assertEquals(StrategyState.Kind.NEEDS_PAIRING, s.state.kind)
        }
        FakeWs { it.sendText("""{"event":"ms.channel.unauthorized"}""") }.use { ws ->
            assertTrue(assertFailsWith<StrategyException> { SamsungStrategy(ws.target(), MemorySecretStore(), listOf(ws.port to false), pairWaitMs = 2000).connect() }.needsPairing)
        }
    }

    // ---- LG ----
    @Test fun lgRegistersThenSendsSsapAndPointerButtons() {
        FakeWs { c ->
            val first = c.next() as String
            assertTrue("\"type\":\"register\"" in first && "client-key" !in first)
            c.sendText("""{"type":"response","id":"register_0","payload":{"pairingType":"PROMPT","returnValue":true}}""")
            c.sendText("""{"type":"registered","id":"register_0","payload":{"client-key":"ck-1"}}""")
        }.use { main ->
            FakeWs().use { ptr ->
                val secrets = MemorySecretStore()
                val s = LgStrategy(main.target(), secrets, listOf(main.port to false), pairWaitMs = 3000)
                s.connect()
                assertEquals("ck-1", secrets.get("lg-key:tv1"))
                s.send(RemoteKey.VOLUME_UP)
                val c = main.conns[0]
                assertTrue((c.next() as String).let { "\"uri\":\"ssap://audio/volumeUp\"" in it })
                // direction keys go through the pointer socket the TV hands out
                Thread {
                    val req = c.next(3000) as String
                    assertTrue("getPointerInputSocket" in req)
                    c.sendText("""{"type":"response","id":"cb_ptr","payload":{"returnValue":true,"socketPath":"ws://127.0.0.1:${ptr.port}/pointer"}}""")
                }.start()
                s.send(RemoteKey.HOME)
                assertEquals("type:button\nname:HOME\n\n", ptr.conns[0].next(3000))
                assertTrue(LgStrategy.registerMessage("ck-1").contains("\"client-key\":\"ck-1\""))
                s.close()
            }
        }
    }

    @Test fun lgPointerSocketMustStayOnTheChosenTv() {
        FakeWs { c ->
            c.next(); c.sendText("""{"type":"registered","payload":{"client-key":"k"}}""")
            c.next(3000)?.let { c.sendText("""{"type":"response","id":"cb_ptr","payload":{"socketPath":"ws://10.9.9.9:3000/x"}}""") }
        }.use { main ->
            val s = LgStrategy(main.target(), MemorySecretStore(), listOf(main.port to false), pairWaitMs = 3000)
            s.connect()
            assertFailsWith<IOException> { s.send(RemoteKey.DPAD_UP) }
        }
    }

    // ---- Android TV: message flow on fake channels ----
    @Test fun androidTvWithoutAPlatformIdentityIsHonestAboutIt() {
        val s = AndroidTvStrategy(testTarget(), null)
        assertEquals(StrategyStatus.EXPERIMENTAL, s.status)
        assertFalse(s.applicable(TvFingerprint(Vendor.ANDROID_TV, "Android TV", null, 0.9, emptyList(), listOf(StrategyIds.ANDROID_TV))))
        assertFailsWith<IOException> { s.connect() }
        assertEquals(StrategyState.Kind.FAILED, s.state.kind)
    }

    // ---- CastBridge-TV through the existing transport ----
    @Test fun nativeUsesTheExistingTransportWithSeqNumbers() {
        class T : castbridge.core.remote.RemoteTransport {
            override val name = "Wi-Fi"; val seen = mutableListOf<String>()
            override fun send(method: String, route: String, query: String): castbridge.core.remote.RemoteReply { seen += "$method $route?$query"; return castbridge.core.remote.RemoteReply(200, """{"ok":true,"via":"app"}""") }
            override fun close() {}
        }
        val t = T(); val s = NativeStrategy(testTarget(), { t })
        s.connect(); s.send(RemoteKey.VOLUME_UP); assertTrue(s.sendText("a b"))
        assertEquals("GET state?", t.seen[0]); assertTrue(t.seen[1].startsWith("POST key?code=VOLUME_UP&target=auto&sid=")); assertTrue(t.seen[1].endsWith("&seq=1"))
        assertTrue("value=a%20b" in t.seen[2] && t.seen[2].endsWith("&seq=2"))
        val bad = NativeStrategy(testTarget(), { object : castbridge.core.remote.RemoteTransport { override val name = "x"
            override fun send(method: String, route: String, query: String) = castbridge.core.remote.RemoteReply(401, "{}"); override fun close() {} } })
        assertTrue(assertFailsWith<StrategyException> { bad.connect() }.needsPairing)
    }
}
