package castbridge.core.smart

import castbridge.core.remote.RemoteKey
import castbridge.core.remote.smart.*
import java.security.MessageDigest
import java.util.Base64
import kotlin.test.*

class ProtocolTest {
    private fun hex(b: ByteArray) = Pb.hex(b)
    private fun unhex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    // ---- protobuf: vectors from the protobuf encoding guide ----
    @Test fun protobufGuideVectors() {
        assertEquals("089601", hex(Pb.Writer().int(1, 150).toByteArray()))                       // field 1 = 150
        assertEquals("120774657374696e67", hex(Pb.Writer().string(2, "testing").toByteArray()))  // field 2 = "testing"
        assertEquals("1a03089601", hex(Pb.Writer().message(3, Pb.Writer().int(1, 150)).toByteArray()))
        assertEquals("08ffffffffffffffffff01", hex(Pb.Writer().int(1, -1).toByteArray()))        // int32 -1 = 10 bytes
        assertEquals("0d0000803f", hex(Pb.Writer().float(1, 1.0f).toByteArray()))
        val f = Pb.parse(unhex("089601120774657374696e67"))
        assertEquals(150L, f[0].long); assertEquals("testing", f[1].string)
        assertFailsWith<java.io.IOException> { Pb.parse(unhex("1207746573")) }              // truncated
        assertFailsWith<java.io.IOException> { Pb.parse(unhex("0b")) }                       // unknown wire type 3
    }

    // ---- CVTE: the encoding in docs/REMOTE-VENDOR-CVTE.md ----
    @Test fun cvteKeyMessageMatchesTheDocumentedBytes() {
        assertEquals("08011202" + "3234", hex(CvteMessage.key(24)))                 // volume +
        assertEquals("08011202" + "3235", hex(CvteMessage.key(25)))                 // volume −
        assertEquals("0801120134", hex(CvteMessage.key(4)))                         // back
        val (type, action, pts) = CvteMessage.decode(CvteMessage.key(66))
        assertEquals(1, type); assertEquals("66", action); assertTrue(pts.isEmpty())
        // pointer sub-messages: field 3 {1: x, 2: y}
        val ptr = Pb.Writer().int(1, 4).message(3, Pb.Writer().float(1, 10.5f).float(2, 20f)).toByteArray()
        assertEquals(Triple(4, null, listOf(10.5f to 20f)), CvteMessage.decode(ptr))
    }

    @Test fun cvteInfoFrameIsParsedAndStatus500IsNotAnError() {
        val j = """{"status":500,"data":{"code":"x","config":{"AppManager":true,"AppStore":false,"Voice":true},"height":720,"width":1280,"httpPort":"9909","ip":"192.168.1.20","name":"SMART_TV","port":8125,"websocketPort":"8125"},"msg":"successful"}"""
        val i = CvteInfo.parse(j)!!
        assertEquals("SMART_TV", i.name); assertEquals(1280, i.width); assertEquals(720, i.height); assertEquals(8125, i.websocketPort); assertEquals(9909, i.httpPort)
        assertEquals(setOf("AppManager", "Voice"), i.features)
        assertNull(CvteInfo.parse("not json"))
    }

    @Test fun cvteEndpointFromDnsSd() {
        val r = MdnsRecord("_share._tcp", "BytelloRemoteServer", mapOf("websocket_port" to "9000", "device_ip" to "192.168.1.20"))
        assertEquals("192.168.1.77" to 9000, CvteMessage.endpoint(r, "192.168.1.77"))
        assertEquals("192.168.1.20" to 9000, CvteMessage.endpoint(r, null))            // fallback on device_ip
        assertEquals("1.2.3.4" to 8125, CvteMessage.endpoint(MdnsRecord("_share._tcp", "x"), "1.2.3.4"))
        assertNull(CvteMessage.endpoint(MdnsRecord("_share._tcp", "x"), null))
    }

    // ---- Sony IRCC: well-known community codes (base64) ----
    @Test fun sonyIrccCodesMatchTheKnownValues() {
        assertEquals("AAAAAQAAAAEAAAASAw==", SonyIrcc.CODES[RemoteKey.VOLUME_UP])
        assertEquals("AAAAAQAAAAEAAAATAw==", SonyIrcc.CODES[RemoteKey.VOLUME_DOWN])
        assertEquals("AAAAAQAAAAEAAAAUAw==", SonyIrcc.CODES[RemoteKey.VOLUME_MUTE])
        assertEquals("AAAAAQAAAAEAAAB0Aw==", SonyIrcc.CODES[RemoteKey.DPAD_UP])
        assertEquals("AAAAAQAAAAEAAABlAw==", SonyIrcc.CODES[RemoteKey.DPAD_CENTER])
        assertEquals("AAAAAgAAABoAAABXAw==", SonyIrcc.CODES[RemoteKey.HOME])
        assertEquals("AAAAAgAAAJcAAAAjAw==", SonyIrcc.CODES[RemoteKey.BACK])
        assertEquals("AAAAAgAAABoAAAAaAw==", SonyIrcc.CODES[RemoteKey.PLAY])
        assertEquals("AAAAAQAAAAEAAAAAAw==", SonyIrcc.CODES[RemoteKey.NUM_1])
    }

    // ---- Android TV remote v2 ----
    @Test fun androidTvMessagesAreFramedAndEncoded() {
        val key = AndroidTvMessages.keyInject(24)                                       // {10: {1: 24, 2: 3}}
        assertEquals("5204" + "0818" + "1003", hex(key))
        assertEquals(hex(key).length / 2 + 1, AndroidTvMessages.frame(key).size)
        val ping = AndroidTvMessages.pingResponse(7)
        assertEquals("4a020807", hex(ping))                                             // field 9, len 2, {1: 7}
        val req = Pb.parse(AndroidTvMessages.pairingRequest("Tel"))
        assertEquals(2L, req.first { it.number == 1 }.long); assertEquals(200L, req.first { it.number == 2 }.long)
        val inner = Pb.parse(req.first { it.number == 10 }.bytes!!)
        assertEquals("androidtvremote2", inner[0].string); assertEquals("Tel", inner[1].string)
        // frames round-trip through the reader
        val back = AndroidTvMessages.readFrame(java.io.ByteArrayInputStream(AndroidTvMessages.frame(req.let { AndroidTvMessages.pairingRequest("Tel") })))
        assertContentEquals(AndroidTvMessages.pairingRequest("Tel"), back)
        assertEquals(7, AndroidTvMessages.pingValue(Pb.Writer().message(8, Pb.Writer().int(1, 7)).toByteArray()))
    }

    @Test fun androidTvSecretIsSha256WithACheckByte() {
        val cm = byteArrayOf(1, 2, 3); val ce = byteArrayOf(1, 0, 1); val sm = byteArrayOf(9, 8, 7); val se = byteArrayOf(1, 0, 1)
        val nonce = byteArrayOf(0x12, 0x34)
        val h = MessageDigest.getInstance("SHA-256").digest(cm + ce + sm + se + nonce)
        val code = "%02X".format(h[0].toInt() and 0xFF) + "1234"
        assertContentEquals(h, AndroidTvMessages.secret(cm, ce, sm, se, code))
        assertContentEquals(h, AndroidTvMessages.secret(cm, ce, sm, se, code.lowercase()))
        val wrongCheck = "%02X".format((h[0].toInt() and 0xFF) xor 1) + "1234"
        assertNull(AndroidTvMessages.secret(cm, ce, sm, se, wrongCheck))
        assertNull(AndroidTvMessages.secret(cm, ce, sm, se, "12345")); assertNull(AndroidTvMessages.secret(cm, ce, sm, se, "12345G"))
    }

    // ---- Bluetooth HID ----
    @Test fun hidDescriptorAndReports() {
        val d = HidReports.DESCRIPTOR
        assertEquals(0xC0.toByte(), d.last()); assertEquals(2, d.count { it == 0xC0.toByte() })           // two top-level collections
        assertEquals(0x85.toByte(), d[6]); assertEquals(1.toByte(), d[7])                                // keyboard = report 1
        assertContentEquals(byteArrayOf(0, 0, 0x52, 0, 0, 0, 0, 0), HidReports.keyboardReport(0x52))     // arrow up
        assertContentEquals(byteArrayOf(0xE9.toByte(), 0), HidReports.consumerReport(0xE9))              // volume +
        assertContentEquals(byteArrayOf(0x24, 0x02), HidReports.consumerReport(0x224))                   // AC Back
        assertEquals(HidReports.Usage.Consumer(0xE9), HidReports.USAGES[RemoteKey.VOLUME_UP])
        assertEquals(HidReports.Usage.Keyboard(0x1E), HidReports.USAGES[RemoteKey.NUM_1]); assertEquals(HidReports.Usage.Keyboard(0x27), HidReports.USAGES[RemoteKey.NUM_0])
    }

    @Test fun hidStrategySendsPressThenRelease() {
        class Port : HidPort { val r = mutableListOf<Pair<Int, List<Byte>>>(); override val available = true; override fun connect() {}
            override fun sendReport(id: Int, data: ByteArray) { r += id to data.toList() }; override fun close() {} }
        val p = Port(); val s = BluetoothHidStrategy(testTarget(), p)
        s.connect(); s.send(RemoteKey.VOLUME_DOWN)
        assertEquals(listOf(2 to listOf<Byte>(0xEA.toByte(), 0), 2 to listOf<Byte>(0, 0)), p.r)
        assertFailsWith<KeyUnsupported> { s.send(RemoteKey.AUDIO_TRACK) }
        assertEquals(StrategyStatus.EXPERIMENTAL, s.status)
        assertFalse(BluetoothHidStrategy(testTarget(), null).applicable(TvFingerprint.unknown()))
    }

    // ---- Infrared ----
    @Test fun nechasTheDocumentedShape() {
        val s = IrCodes.nec(0x04, 0x08)                                  // LG power: 0x20DF10EF in the usual MSB-first notation
        assertEquals(38_000, s.carrierHz); assertEquals(2 + 32 * 2 + 1, s.pattern.size)
        assertEquals(listOf(9000, 4500), s.pattern.take(2)); assertEquals(560, s.pattern.last())
        // bits LSB first: address 0x04 = 0,0,1,0,0,0,0,0
        fun bit(i: Int) = if (s.pattern[2 + 2 * i + 1] == 1690) 1 else 0
        assertEquals(listOf(0, 0, 1, 0, 0, 0, 0, 0), (0..7).map(::bit))
        assertEquals(listOf(1, 1, 0, 1, 1, 1, 1, 1), (8..15).map(::bit))     // ~0x04
        assertEquals(listOf(0, 0, 0, 1, 0, 0, 0, 0), (16..23).map(::bit))    // 0x08
        assertEquals(listOf(1, 1, 1, 0, 1, 1, 1, 1), (24..31).map(::bit))    // ~0x08
        // the 32-bit word read MSB-first from the pattern is the commonly published LG power code 0x20DF10EF
        val word = (0..31).fold(0L) { a, i -> a or (bit(i).toLong() shl (8 * (3 - i / 8) + 7 - i % 8)) }
        assertEquals(0x20DF10EFL, word and 0xFFFFFFFFL)
    }

    @Test fun samsungAndSircPatterns() {
        val sam = IrCodes.signal(Vendor.SAMSUNG, RemoteKey.VOLUME_UP)!!
        assertEquals(listOf(4500, 4500), sam.pattern.take(2)); assertEquals(2 + 32 * 2 + 1, sam.pattern.size)
        val sony = IrCodes.signal(Vendor.SONY, RemoteKey.VOLUME_UP)!!
        assertEquals(40_000, sony.carrierHz); assertEquals(listOf(2400, 600), sony.pattern.take(2))
        assertEquals(2 + 12 * 2, sony.pattern.size)
        assertNull(IrCodes.signal(Vendor.ROKU, RemoteKey.VOLUME_UP)); assertNull(IrCodes.signal(Vendor.LG, RemoteKey.CHANNEL_UP))
    }

    @Test fun infraredStrategyTransmitsAndSonyRepeats() {
        class Em : IrEmitter { var n = 0; override val available = true; override fun transmit(carrierHz: Int, pattern: IntArray) { n++ } }
        val e = Em(); val s = InfraredStrategy(testTarget(), e, Vendor.SONY); s.connect(); s.send(RemoteKey.VOLUME_UP)
        assertEquals(3, e.n)
        val lg = Em(); InfraredStrategy(testTarget(), lg, Vendor.LG).also { it.connect(); it.send(RemoteKey.HOME) }; assertEquals(1, lg.n)
        assertFalse(InfraredStrategy(testTarget(), null, Vendor.LG).applicable(TvFingerprint.unknown()))
        assertFalse(InfraredStrategy(testTarget(), Em(), Vendor.ROKU).applicable(TvFingerprint.unknown()))
    }

    // ---- Redaction ----
    @Test fun diagnosticsNeverKeepSecretsOrFullAddresses() {
        val raw = "token=abc123 PSK: s3cr3t client-key\":\"k-9 pin=123456 MAC b0:a7:37:11:22:33 host 192.168.1.20 ok"
        val c = Redact.clean(raw)
        for (bad in listOf("abc123", "s3cr3t", "k-9", "123456", "11:22:33", "192.168.1.20")) assertFalse(bad in c, "$bad fuite : $c")
        assertTrue("192.168.1.x" in c); assertTrue("b0:a7:37:xx:xx:xx" in c)
    }
}
