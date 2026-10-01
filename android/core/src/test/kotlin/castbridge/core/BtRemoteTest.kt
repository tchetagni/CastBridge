package castbridge.core

import castbridge.core.remote.*
import castbridge.core.remote.hid.*
import castbridge.core.remote.vendor.*
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
private val MASK = byteArrayOf(1, 2, 3, 4)

class VendorProtoTest {
    @Test fun keyVectors() {
        assertEquals("080112023234", hex(VendorProto.key(24)))   // volume up: "24"
        assertEquals("080112023235", hex(VendorProto.key(25)))   // volume down: "25"
        assertEquals("080112023139", hex(VendorProto.key(19)))   // up: "19"
        assertEquals("0801120134", hex(VendorProto.key(4)))      // back: "4"
        assertEquals("08011203313634", hex(VendorProto.key(164))) // mute: "164"
        assertEquals("080112023234", hex(VendorProto.key(RemoteKey.VOLUME_UP)))
    }
    @Test fun varints() {
        assertEquals("00", hex(VendorProto.varint(0))); assertEquals("7f", hex(VendorProto.varint(127)))
        assertEquals("8001", hex(VendorProto.varint(128))); assertEquals("ac02", hex(VendorProto.varint(300)))
    }
    @Test fun infoFrame() {
        val j = """{"status":500,"data":{"code":"x","config":{"AppManager":true,"Camera":false,"Voice":true},"height":720,"width":1280,"httpPort":"9909","ip":"1.2.3.4","name":"TV-1","port":8125,"websocketPort":"8125"},"msg":"successful"}"""
        val i = assertNotNull(VendorInfo.parse(j))
        assertEquals("TV-1", i.name); assertEquals(1280, i.width); assertEquals(720, i.height); assertEquals(8125, i.websocketPort)
        assertEquals(setOf("AppManager", "Voice"), i.capabilities)
        assertNull(VendorInfo.parse("not json"))
    }
}

class WebSocketFramesTest {
    @Test fun maskedShortFrame() {
        assertEquals("828201020304" + "1122", hex(WebSocketFrames.encode(WebSocketFrames.BINARY, byteArrayOf(0x10, 0x20), MASK)))
    }
    @Test fun lengthsAndRoundTrip() {
        for (n in listOf(0, 1, 125, 126, 127, 65535, 65536, 70000)) {
            val p = ByteArray(n) { (it * 7).toByte() }
            val f = WebSocketFrames.encode(WebSocketFrames.BINARY, p, MASK)
            val header = when { n < 126 -> 2; n <= 65535 -> 4; else -> 10 }
            assertEquals(header + 4 + n, f.size, "size for $n")
            assertEquals(0x82, f[0].toInt() and 0xff)
            when { n < 126 -> assertEquals(0x80 or n, f[1].toInt() and 0xff); n <= 65535 -> assertEquals(0xFE, f[1].toInt() and 0xff); else -> assertEquals(0xFF, f[1].toInt() and 0xff) }
            val m = WebSocketFrames.Reader(ByteArrayInputStream(f)).next()
            assertContentEquals(p, m.payload)
        }
    }
    @Test fun masksPayload() {
        val f = WebSocketFrames.encode(WebSocketFrames.TEXT, "AB".toByteArray(), MASK)
        assertEquals(0x81, f[0].toInt() and 0xff); assertEquals(0x82, f[1].toInt() and 0xff)
        assertEquals(listOf(1, 2, 3, 4), f.slice(2..5).map { it.toInt() })
        assertEquals(('A'.code xor 1), f[6].toInt()); assertEquals(('B'.code xor 2), f[7].toInt())
    }
    @Test fun fragmentsWithControlFrameBetween() {
        val a = WebSocketFrames.encode(WebSocketFrames.TEXT, "hel".toByteArray(), MASK, fin = false)
        val ping = WebSocketFrames.encode(WebSocketFrames.PING, "p".toByteArray(), MASK)
        val b = WebSocketFrames.encode(WebSocketFrames.CONT, "lo".toByteArray(), MASK, fin = true)
        val r = WebSocketFrames.Reader(ByteArrayInputStream(a + ping + b))
        val first = r.next(); assertEquals(WebSocketFrames.PING, first.opcode)
        val second = r.next(); assertEquals(WebSocketFrames.TEXT, second.opcode); assertEquals("hello", second.text)
    }
    @Test fun badFramesRefused() {
        assertFailsWith<java.io.IOException> { WebSocketFrames.Reader(ByteArrayInputStream(byteArrayOf((0x80 or 9).toByte(), 126, 0, 200.toByte()) + ByteArray(200))).next() } // ping > 125
        assertFailsWith<java.io.IOException> { WebSocketFrames.Reader(ByteArrayInputStream(byteArrayOf(0x80.toByte(), 0, 0))).next() } // continuation first
        assertFailsWith<java.io.EOFException> { WebSocketFrames.Reader(ByteArrayInputStream(byteArrayOf(0x81.toByte(), 5, 1))).next() }
    }
    @Test fun acceptKeyVector() = assertEquals("s3pPLMBiTxaQ9kYGzzhZRbK+xOo=", WebSocketFrames.acceptKey("dGhlIHNhbXBsZSBub25jZQ=="))
}

/** A loopback WebSocket server that records the binary messages it receives and can cut the link. */
class FakeVendorServer(port: Int = 0, private val answer101: Boolean = true) {
    val server = ServerSocket(port, 5, InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)))
    val port get() = server.localPort
    val received = CopyOnWriteArrayList<String>()
    val pongs = AtomicInteger()
    val connections = AtomicInteger()
    @Volatile var current: Socket? = null
    @Volatile var hostHeader: String? = null
    private val t = Thread { try { while (true) serve(server.accept()) } catch (_: Exception) {} }.apply { isDaemon = true; start() }

    private fun serve(s: Socket) {
        connections.incrementAndGet(); current = s
        Thread {
            try {
                val i = s.getInputStream(); val o = s.getOutputStream()
                val sb = StringBuilder()
                while (!sb.endsWith("\r\n\r\n")) sb.append(i.read().also { if (it < 0) return@Thread }.toChar())
                val head = sb.toString()
                hostHeader = Regex("(?i)host: (.*)\r\n").find(head)?.groupValues?.get(1)
                if (!answer101) { o.write("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n".toByteArray()); o.flush(); s.close(); return@Thread }
                val key = Regex("(?i)sec-websocket-key: (.*)\r\n").find(head)!!.groupValues[1].trim()
                o.write("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: ${WebSocketFrames.acceptKey(key)}\r\n\r\n".toByteArray())
                val info = """{"status":500,"data":{"name":"TV-TEST","width":1280,"height":720,"websocketPort":"8125","config":{"Video":true}},"msg":"successful"}""".toByteArray()
                // split in two fragments to exercise the client's reader (server frames are not masked)
                o.write(byteArrayOf(0x01, 10) + info.copyOf(10)); o.write(byteArrayOf(0x80.toByte(), (info.size - 10).toByte()) + info.copyOfRange(10, info.size)); o.flush()
                val r = WebSocketFrames.Reader(i)
                while (true) {
                    val m = r.next()
                    when (m.opcode) {
                        WebSocketFrames.BINARY -> received += hex(m.payload)
                        WebSocketFrames.PING -> { o.write(byteArrayOf(0x8A.toByte(), m.payload.size.toByte()) + m.payload); o.flush() }
                        WebSocketFrames.PONG -> pongs.incrementAndGet()
                        WebSocketFrames.CLOSE -> { o.write(byteArrayOf(0x88.toByte(), 0)); o.flush(); s.close(); return@Thread }
                    }
                }
            } catch (_: Exception) {}
        }.apply { isDaemon = true; start() }
    }
    fun ping() { current?.getOutputStream()?.apply { write(byteArrayOf(0x89.toByte(), 0)); flush() } }
    fun cut() { runCatching { current?.close() } }
    fun close() { runCatching { server.close() }; cut() }
}

private fun waitFor(ms: Long = 4000, cond: () -> Boolean): Boolean { val end = System.currentTimeMillis() + ms; while (System.currentTimeMillis() < end) { if (cond()) return true; Thread.sleep(10) }; return cond() }
private fun freePort() = ServerSocket(0).use { it.localPort }

class VendorBridgeTest {
    private fun bridge(port: Int) = VendorBridge(port, backoffMs = listOf(30, 60, 120), connectTimeoutMs = 500, readTimeoutMs = 300)

    @Test fun handshakeInfoAndKeys() {
        val srv = FakeVendorServer(); val b = bridge(srv.port)
        try {
            b.start()
            assertTrue(waitFor { b.state == VendorState.READY })
            assertTrue(waitFor { b.info != null }); assertEquals("TV-TEST", b.info?.name)
            assertEquals("127.0.0.1:${srv.port}", srv.hostHeader)
            assertTrue(b.sendKey(24)); assertTrue(b.sendKey(25))
            assertTrue(waitFor { srv.received.size == 2 })
            assertEquals(listOf("08011202" + "3234", "08011202" + "3235"), srv.received.toList())
        } finally { b.stop(); srv.close() }
    }
    @Test fun absentThenReadyWhenServiceAppears() {
        val port = freePort(); val b = bridge(port)
        try {
            b.start()
            assertTrue(waitFor { b.state == VendorState.ABSENT })
            assertFalse(b.sendKey(24))
            val srv = FakeVendorServer(port)
            try { assertTrue(waitFor { b.state == VendorState.READY }) } finally { srv.close() }
        } finally { b.stop() }
    }
    @Test fun errorWhenNotAWebSocket() {
        val srv = FakeVendorServer(answer101 = false); val b = bridge(srv.port)
        try { b.start(); assertTrue(waitFor { b.state == VendorState.ERROR }) } finally { b.stop(); srv.close() }
    }
    @Test fun reconnectsAfterCutAndKeysWorkAgain() {
        val srv = FakeVendorServer(); val b = bridge(srv.port)
        try {
            b.start(); assertTrue(waitFor { b.state == VendorState.READY })
            srv.cut()
            assertTrue(waitFor { srv.connections.get() >= 2 && b.state == VendorState.READY })
            assertTrue(b.sendKey(66)); assertTrue(waitFor { srv.received.contains("08011202" + "3636") })
        } finally { b.stop(); srv.close() }
    }
    @Test fun answersServerPing() {
        val srv = FakeVendorServer(); val b = bridge(srv.port)
        try { b.start(); assertTrue(waitFor { b.state == VendorState.READY }); srv.ping(); assertTrue(waitFor { srv.pongs.get() == 1 }) } finally { b.stop(); srv.close() }
    }
    @Test fun stopClosesAndStaysQuiet() {
        val srv = FakeVendorServer(); val b = bridge(srv.port)
        b.start(); assertTrue(waitFor { b.state == VendorState.READY }); b.stop()
        Thread.sleep(300); val n = srv.connections.get(); Thread.sleep(300)
        assertEquals(n, srv.connections.get()); srv.close()
    }
    @Test fun onlyLoopback() = assertEquals("127.0.0.1", VendorBridge.LOOPBACK.hostAddress)
}

class FakeLink(override var state: VendorState = VendorState.READY) : VendorLink {
    val codes = ArrayList<Int>(); var fail = false
    override fun sendKey(androidCode: Int): Boolean { if (fail) return false; codes += androidCode; return true }
}

class VendorRelayTest {
    @Test fun relaysWhitelistedKeysOnce() {
        val l = FakeLink(); val r = VendorRelay(l)
        assertTrue(r.relay(RemoteKey.VOLUME_UP, KeyAction.PRESS).ok); assertTrue(r.relay(RemoteKey.DPAD_UP, KeyAction.DOWN).ok)
        assertTrue(r.relay(RemoteKey.DPAD_UP, KeyAction.UP).ok)   // up: nothing sent
        assertEquals(listOf(24, 19), l.codes)
    }
    @Test fun neverPowerOrSleep() {
        for (k in RemoteKey.values()) assertFalse(k.code in VendorRelay.FORBIDDEN, k.name)
        assertEquals(setOf(26, 223, 224, 276, 120).size, VendorRelay.FORBIDDEN.size)
        assertNull(RemoteKey.parse("POWER")); assertNull(RemoteKey.parse("SLEEP")); assertNull(RemoteKey.parse("26"))
    }
    @Test fun notReadyRefuses() {
        val l = FakeLink(VendorState.ABSENT); val r = VendorRelay(l)
        assertFalse(r.relay(RemoteKey.BACK, KeyAction.PRESS).ok); assertTrue(l.codes.isEmpty())
        l.state = VendorState.READY; l.fail = true
        assertFalse(r.relay(RemoteKey.BACK, KeyAction.PRESS).ok)
    }
    @Test fun rateLimit30PerSecond() {
        var t = 0L; val l = FakeLink(); val r = VendorRelay(l, RateLimiter(30) { t })
        val oks = (1..100).count { r.relay(RemoteKey.VOLUME_UP, KeyAction.PRESS).ok }
        assertEquals(30, oks)
        val over = r.relay(RemoteKey.VOLUME_UP, KeyAction.PRESS); assertEquals(429, over.status)
        t += 1_000_000_000L; assertTrue(r.relay(RemoteKey.VOLUME_UP, KeyAction.PRESS).ok)
        t += 34_000_000L; assertTrue(r.relay(RemoteKey.VOLUME_UP, KeyAction.PRESS).ok) // refilled one token
    }
    @Test fun journalHasNoCodesOrSecrets() {
        val r = VendorRelay(FakeLink()); r.relay(RemoteKey.VOLUME_UP, KeyAction.PRESS)
        val j = r.journal().joinToString("\n"); assertTrue("VOLUME_UP ok" in j); assertFalse("24" in j.substringAfter("VOLUME_UP"))
    }
}

class KeyRoutingTest {
    private fun plan(k: RemoteKey, t: RemoteTarget = RemoteTarget.AUTO, front: Boolean, a11y: Boolean = false, v: Boolean) = KeyRouting.plan(k, t, front, a11y, v)
    @Test fun volumeAudioThenVendor() {
        assertEquals(listOf(Route.AUDIO, Route.VENDOR), plan(RemoteKey.VOLUME_UP, front = true, v = true))
        assertEquals(listOf(Route.AUDIO), plan(RemoteKey.VOLUME_UP, front = true, v = false))
        assertEquals(listOf(Route.AUDIO), plan(RemoteKey.VOLUME_UP, RemoteTarget.APP, front = false, v = true))
    }
    @Test fun frontScreenFirstThenVendorOnlyIfNotConsumed() {
        assertEquals(listOf(Route.APP, Route.VENDOR), plan(RemoteKey.DPAD_UP, front = true, v = true))
        assertEquals(listOf(Route.APP), plan(RemoteKey.DPAD_UP, front = true, v = false))
        assertEquals(listOf(Route.APP), plan(RemoteKey.DPAD_UP, RemoteTarget.APP, front = true, v = true))
        assertEquals(listOf(Route.VENDOR), plan(RemoteKey.DPAD_UP, RemoteTarget.SYSTEM, front = true, v = true))
    }
    @Test fun notFrontVendorThenAccessibilityThenMedia() {
        assertEquals(listOf(Route.VENDOR, Route.ACCESSIBILITY), plan(RemoteKey.DPAD_UP, front = false, a11y = true, v = true))
        assertEquals(listOf(Route.VENDOR, Route.MEDIA_SESSION), plan(RemoteKey.PLAY_PAUSE, front = false, a11y = true, v = true))
        assertEquals(listOf(Route.ACCESSIBILITY), plan(RemoteKey.DPAD_UP, front = false, a11y = true, v = false))
        assertEquals(emptyList(), plan(RemoteKey.DPAD_UP, front = false, v = false))
        assertEquals(emptyList(), plan(RemoteKey.DPAD_UP, RemoteTarget.APP, front = false, v = true))
    }
    @Test fun homeStaysCastBridgeUnlessWholeTv() {
        assertEquals(listOf(Route.APP), plan(RemoteKey.HOME, front = false, v = true))
        assertEquals(listOf(Route.VENDOR), plan(RemoteKey.HOME, RemoteTarget.SYSTEM, front = false, v = true))
    }
    @Test fun globals() {
        assertEquals(RemoteKey.BACK, KeyRouting.globalViaVendor(RemoteGlobal.BACK)); assertEquals(RemoteKey.HOME, KeyRouting.globalViaVendor(RemoteGlobal.HOME))
        assertNull(KeyRouting.globalViaVendor(RemoteGlobal.POWER_DIALOG)); assertNull(KeyRouting.globalViaVendor(RemoteGlobal.RECENTS))
    }
}

class HidRemoteTest {
    @Test fun descriptorBytes() {
        val d = HidRemote.DESCRIPTOR
        assertEquals(72, d.size)
        assertEquals("05010906a1018501", hex(d.copyOf(8)))
        assertEquals(0xC0, d.last().toInt() and 0xff)
        assertTrue(hex(d).contains("050c0901a1018502"))   // consumer control collection, report id 2
        assertTrue(hex(d).endsWith("75109501" + "8100c0"))
    }
    @Test fun keyboardReports() {
        assertEquals(HidReport(1, byteArrayOf(0, 0, 0x52, 0, 0, 0, 0, 0)), HidRemote.press(RemoteKey.DPAD_UP))
        assertEquals(HidReport(1, byteArrayOf(0, 0, 0x28, 0, 0, 0, 0, 0)), HidRemote.press(RemoteKey.DPAD_CENTER))
        assertEquals(HidReport(1, ByteArray(8)), HidRemote.release(RemoteKey.DPAD_UP))
        assertEquals(0x29, HidRemote.press(RemoteKey.BACK)!!.data[2].toInt())
        assertEquals(0x27, HidRemote.press(RemoteKey.NUM_0)!!.data[2].toInt()); assertEquals(0x1E, HidRemote.press(RemoteKey.NUM_1)!!.data[2].toInt())
    }
    @Test fun consumerReportsLittleEndian() {
        assertEquals(HidReport(2, byteArrayOf(0xE9.toByte(), 0)), HidRemote.press(RemoteKey.VOLUME_UP))
        assertEquals(HidReport(2, byteArrayOf(0x23, 0x02)), HidRemote.press(RemoteKey.HOME))
        assertEquals(HidReport(2, byteArrayOf(0x40, 0x00)), HidRemote.press(RemoteKey.MENU))
        assertEquals(HidReport(2, byteArrayOf(0xCD.toByte(), 0)), HidRemote.press(RemoteKey.PLAY_PAUSE))
        assertEquals(HidReport(2, ByteArray(2)), HidRemote.release(RemoteKey.VOLUME_MUTE))
    }
    @Test fun unmappedKeys() { assertNull(HidRemote.press(RemoteKey.INFO)); assertFalse(HidRemote.supports(RemoteKey.AUDIO_TRACK)); assertTrue(HidRemote.supports(RemoteKey.NEXT)) }
    @Test fun usagesAreUnique() {
        val kb = HidRemote.supportedKeys.mapNotNull { (HidRemote.usage(it) as? HidRemote.Usage.Keyboard)?.code }
        // OK and Enter intentionally share Return
        assertEquals(1, kb.groupBy { it }.count { it.value.size > 1 })
        val cc = HidRemote.supportedKeys.mapNotNull { (HidRemote.usage(it) as? HidRemote.Usage.Consumer)?.code }
        assertEquals(cc.size, cc.toSet().size)
    }

    class FakeHid(override var connected: Boolean = true) : HidTransport { val sent = ArrayList<HidReport>(); override fun send(report: HidReport): Boolean { sent += report; return true } }

    @Test fun pressReleaseIdempotent() {
        val t = FakeHid(); val s = HidKeySender(t)
        assertTrue(s.down(RemoteKey.DPAD_UP)); assertTrue(s.down(RemoteKey.DPAD_UP))   // second down: nothing
        assertEquals(1, t.sent.size)
        assertTrue(s.up(RemoteKey.DPAD_UP)); assertTrue(s.up(RemoteKey.DPAD_UP))       // second up: nothing
        assertEquals(2, t.sent.size); assertEquals(HidReport(1, ByteArray(8)), t.sent[1])
        s.up(RemoteKey.VOLUME_UP); assertEquals(2, t.sent.size)                         // never pressed
    }
    @Test fun newKeyReleasesTheHeldOneOfTheSameKind() {
        val t = FakeHid(); val s = HidKeySender(t)
        s.down(RemoteKey.DPAD_UP); s.down(RemoteKey.DPAD_DOWN)
        assertEquals(listOf(HidRemote.press(RemoteKey.DPAD_UP), HidRemote.release(RemoteKey.DPAD_UP), HidRemote.press(RemoteKey.DPAD_DOWN)), t.sent)
        s.releaseAll(); assertEquals(HidReport(1, ByteArray(8)), t.sent.last())
    }
    @Test fun disconnectedSendsNothing() { val t = FakeHid(false); val s = HidKeySender(t); assertFalse(s.tap(RemoteKey.VOLUME_UP)); assertTrue(t.sent.isEmpty()) }
    @Test fun strategyDisabledByDefaultUntilConfirmed() {
        val t = FakeHid(); val st = HidStrategy(t)
        assertFalse(st.send(RemoteKey.VOLUME_UP, KeyAction.PRESS)); assertTrue(t.sent.isEmpty())
        assertEquals(RouteStatus.TO_TEST, st.state)
        st.enabled = true; assertTrue(st.send(RemoteKey.VOLUME_UP, KeyAction.PRESS)); assertEquals(2, t.sent.size)
        st.confirmed = true; assertEquals(RouteStatus.CONFIRMED, st.state)
        assertEquals(RouteStatus.UNSUPPORTED, HidStrategy(t, hostRefused = { true }).state)
        assertEquals(RouteStatus.AVAILABLE, HidStrategy(FakeHid(false)).state)
    }
}

class RemotePlanTest {
    @Test fun btOnlyNeverWifi() {
        assertEquals(listOf(LinkKind.WIFI, LinkKind.BLUETOOTH), RemotePlan.links(true, true, false))
        assertEquals(listOf(LinkKind.BLUETOOTH), RemotePlan.links(true, true, true))
        assertEquals(emptyList(), RemotePlan.links(true, false, true))
        assertEquals(listOf(LinkKind.WIFI), RemotePlan.links(true, false, false))
    }
    @Test fun statusMessage() { assertEquals("Bluetooth seulement", RemotePlan.linkMessage("Bluetooth", false)); assertEquals("Bluetooth exclusivement", RemotePlan.linkMessage("bt", true)); assertNull(RemotePlan.linkMessage("Wi-Fi", false)) }
    @Test fun hiddenKeys() {
        val none = RemotePlan.availableKeys(false, false, false)
        assertFalse(RemoteKey.DPAD_UP in none); assertTrue(RemoteKey.VOLUME_UP in none); assertTrue(RemoteKey.PLAY_PAUSE in none); assertTrue(RemoteKey.HOME in none)
        assertTrue(RemoteKey.DPAD_UP in RemotePlan.availableKeys(false, false, true)); assertTrue(RemoteKey.DPAD_UP in RemotePlan.availableKeys(true, false, false))
        assertFalse(RemoteKey.MENU in RemotePlan.availableKeys(false, true, false))
    }
    @Test fun diagnosticHasNoSecrets() {
        val r = RemoteDiagnostics.report("1.2 (build 3)", true, mapOf(BtRoute.VENDOR to RouteStatus.CONFIRMED), listOf("TV AA:BB:CC:DD:EE:FF à 192.168.1.20"))
        assertFalse("AA:BB:CC:DD:EE" in r); assertTrue("**:**:**:**:**:FF" in r); assertFalse("192.168" in r)
        assertTrue("confirmée par un test" in r); assertTrue("Bluetooth exclusivement : oui" in r)
        assertEquals("**:**:**:**:**:FF", RemoteDiagnostics.maskMac("aa-bb-cc-dd-ee-ff"))
    }
}
