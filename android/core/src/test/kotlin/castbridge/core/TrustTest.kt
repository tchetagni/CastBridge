package castbridge.core

import castbridge.core.trust.*
import castbridge.core.tv.*
import java.io.*
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.random.Random
import kotlin.test.*

/** Trusted phones: registry and tokens, the pairing window and the owner's decision, HELLO, the PIN-less paths, and the phone-side link. */
class TrustTest {
    private val PHONE = "AA:BB:CC:DD:EE:01"
    private val PHONE2 = "AA:BB:CC:DD:EE:02"
    private val TV = "11:22:33:44:55:66"
    private var clock = 1_000_000L
    private val mem = MemoryTrustPersistence()
    private fun registry(ttl: Long = 12 * 3600_000L, p: TrustPersistence = mem) = TrustRegistry(p, { clock }, tokenTtlMs = ttl)

    // ------------------------------------------------------------------ registry and tokens

    @Test fun onlyTrustedPhonesGetTokensAndRevocationKillsThem() {
        val r = registry()
        assertNull(r.issueToken(PHONE), "an unknown phone gets nothing")
        r.trust(PHONE, "Galaxy S21+")
        val t = r.issueToken(PHONE)!!
        assertTrue(Regex("^cbk_[0-9a-f]{64}$").matches(t.token))
        assertEquals(PHONE, r.verifyToken(t.token))
        assertNull(r.verifyToken(t.token.dropLast(1) + "0"), "altered token")
        assertNull(r.verifyToken("123456"), "the PIN is not a token")
        assertNull(r.verifyToken(null))
        assertTrue(r.revoke(PHONE))
        assertNull(r.verifyToken(t.token), "revoked: the token dies at once")
        assertNull(r.issueToken(PHONE))
        assertFalse(r.revoke(PHONE))
    }

    @Test fun tokensExpireAndAreFreshEachTime() {
        val r = registry(ttl = 60_000)
        r.trust(PHONE, "Téléphone")
        val a = r.issueToken(PHONE)!!; clock += 30_000
        val b = r.issueToken(PHONE)!!
        assertNotEquals(a.token, b.token)
        assertEquals(PHONE, r.verifyToken(a.token), "the older token lives until its own end (renewal is seamless)")
        clock += 31_000
        assertNull(r.verifyToken(a.token), "expired")
        assertEquals(PHONE, r.verifyToken(b.token))
        clock += 60_000
        assertNull(r.verifyToken(b.token))
        assertEquals(0, r.tokenCount(PHONE))
    }

    @Test fun aPhoneHoldsAtMostAFewTokens() {
        val r = registry()
        r.trust(PHONE, "x")
        val ts = (1..6).map { clock += 1000; r.issueToken(PHONE)!! }
        assertNull(r.verifyToken(ts[0].token)); assertNull(r.verifyToken(ts[1].token))
        assertNotNull(r.verifyToken(ts[5].token))
        assertEquals(4, r.tokenCount(PHONE))
    }

    @Test fun storedTokensAreHashedAndSurviveARestart() {
        val r = registry()
        r.trust(PHONE, "Mon téléphone\tà moi\nligne2"); r.trust(PHONE2, "Autre")
        val t = r.issueToken(PHONE)!!
        assertFalse(mem.text!!.contains(t.token), "only the hash is stored")
        assertFalse(mem.text!!.contains(t.token.removePrefix("cbk_")), "no part of the token either")
        val again = registry()
        assertEquals(listOf(PHONE, PHONE2), again.list().map { it.address })
        assertEquals("Mon téléphone à moi ligne2", again.get(PHONE)!!.name, "separators in a name are neutralised")
        assertEquals(PHONE, again.verifyToken(t.token), "the TV restarting does not cut the phone")
        assertNull(again.verifyToken(t.token.replace('a', 'b').replace('c', 'd')))
    }

    @Test fun corruptedFileLoadsWhatIsValid() {
        val p = MemoryTrustPersistence("garbage\nP\tnot-an-address\t1\t2\tx\nP\t$PHONE\t5\t6\tOK\nT\t$PHONE\tnothex\t9\nT\t$PHONE2\t${"a".repeat(64)}\t${Long.MAX_VALUE}\n")
        val r = registry(p = p)
        assertEquals(listOf(PHONE), r.list().map { it.address })
        assertEquals(0, r.tokenCount(PHONE2), "a token of an unknown phone is dropped")
    }

    @Test fun forgetAllPhones() {
        val r = registry()
        r.trust(PHONE, "a"); r.trust(PHONE2, "b"); val t = r.issueToken(PHONE2)!!
        var changes = 0; r.addListener { changes++ }
        assertEquals(2, r.revokeAll())
        assertTrue(r.list().isEmpty()); assertNull(r.verifyToken(t.token)); assertEquals(1, changes)
        assertEquals(0, r.revokeAll())
    }

    @Test fun addressesAreCaseInsensitiveAndNamesAreClean() {
        val r = registry(); r.trust(PHONE.lowercase(), "‮evil\u0007name  with   spaces  ")
        assertTrue(r.isTrusted(PHONE)); assertEquals("evil name with spaces", r.get(PHONE)!!.name)
        assertEquals("Téléphone", PhoneName.sanitize("\n\t "))
        assertEquals(40, PhoneName.sanitize("x".repeat(100)).length)
    }

    // ------------------------------------------------------------------ pairing window and owner decision

    private fun pairing(reg: TrustRegistry, approvalMs: Long = 3000) = PairingSession(reg, { clock }, windowMs = 120_000, approvalMs = approvalMs)

    /** Runs ask() on another thread (as the Bluetooth thread does) and returns its decision queue. */
    private fun askAsync(s: PairingSession, address: String, name: String): LinkedBlockingQueue<PairingSession.Decision> {
        val q = LinkedBlockingQueue<PairingSession.Decision>()
        thread(isDaemon = true) { q.put(s.ask(address, name)) }
        return q
    }
    private fun waitAsking(s: PairingSession) { val end = System.currentTimeMillis() + 3000; while (s.asking() == null && System.currentTimeMillis() < end) Thread.sleep(5) }

    @Test fun noTrustWithoutTheOwnerOpeningTheWindow() {
        val r = registry(); val s = pairing(r)
        assertEquals(PairingSession.Decision.NOT_OPEN, s.ask(PHONE, "Intrus"))
        assertFalse(r.isTrusted(PHONE))
        s.open(); clock += 121_000
        assertEquals(PairingSession.Decision.NOT_OPEN, s.ask(PHONE, "Intrus"), "the window ended")
        assertFalse(s.isOpen)
    }

    @Test fun ownerApprovesOnTheTvThenWindowCloses() {
        val r = registry(); val s = pairing(r)
        s.open(); assertEquals(120, s.secondsLeft())
        val q = askAsync(s, PHONE, "Galaxy S21+"); waitAsking(s)
        assertEquals("Galaxy S21+", s.asking()!!.name)
        assertFalse(r.isTrusted(PHONE), "not trusted until OK is pressed")
        assertTrue(s.approve())
        assertEquals(PairingSession.Decision.APPROVED, q.poll(3, TimeUnit.SECONDS))
        assertTrue(r.isTrusted(PHONE)); assertEquals("Galaxy S21+", r.get(PHONE)!!.name)
        assertFalse(s.isOpen, "one phone per opening")
        assertFalse(s.approve(), "nothing left to approve")
    }

    @Test fun refusalLeavesNothingAndThreeRefusalsBlock() {
        val r = registry(); val s = pairing(r)
        s.open()
        repeat(3) {
            val q = askAsync(s, PHONE, "Intrus"); waitAsking(s); assertTrue(s.deny())
            assertEquals(PairingSession.Decision.DENIED, q.poll(3, TimeUnit.SECONDS))
            assertFalse(r.isTrusted(PHONE)); assertTrue(s.isOpen, "the window stays open after a refusal")
        }
        assertEquals(PairingSession.Decision.BLOCKED, s.ask(PHONE, "Intrus"), "no dialog spam")
        assertEquals(PairingSession.Decision.BLOCKED, s.ask(PHONE.lowercase(), "Intrus"), "the address is compared case-insensitively")
        clock += 11 * 60_000; s.open()
        val q = askAsync(s, PHONE, "Intrus"); waitAsking(s); s.deny()
        assertEquals(PairingSession.Decision.DENIED, q.poll(3, TimeUnit.SECONDS), "the block is temporary")
    }

    @Test fun silenceIsARefusalAndOnlyOnePhoneIsAskedAtATime() {
        val r = registry(); val s = pairing(r, approvalMs = 250)
        s.open()
        val q = askAsync(s, PHONE, "A"); waitAsking(s)
        assertEquals(PairingSession.Decision.BUSY, s.ask(PHONE2, "B"))
        assertEquals(PairingSession.Decision.TIMEOUT, q.poll(3, TimeUnit.SECONDS))
        assertFalse(r.isTrusted(PHONE)); assertFalse(r.isTrusted(PHONE2))
    }

    @Test fun closingTheScreenCancelsAPendingRequest() {
        val r = registry(); val s = pairing(r)
        s.open(); val q = askAsync(s, PHONE, "A"); waitAsking(s)
        s.close()
        assertEquals(PairingSession.Decision.DENIED, q.poll(3, TimeUnit.SECONDS)); assertFalse(r.isTrusted(PHONE))
    }

    // ------------------------------------------------------------------ HELLO through the real protocol

    private val dir = kotlin.io.path.createTempDirectory("trust").toFile()
    @AfterTest fun tearDown() { dir.deleteRecursively() }

    /** A TV (registry + pairing + HELLO + PIN guard) served over in-memory pipes, peer address chosen by the test (as Android gives it). */
    private inner class FakeTv(val reg: TrustRegistry = registry(), val pairing: PairingSession = pairing(reg), val pin: String = "482913") : BtTransport {
        val guard = PinGuard(pin)
        val bonded = mutableSetOf(PHONE, PHONE2)
        var lan = listOf("192.168.1.20")
        var name = "TV du salon"
        var btOn = true
        val connected = mutableListOf<String>()
        val handler = HelloHandler(reg, pairing, { it in bonded }, { name }, "0.12", { "CastBridge TV Test" }, { LinkInfo(8765, lan) }, { connected += it.name })
        var phone = PHONE
        override fun connect(address: String): Link {
            if (!btOn) throw IOException("connection refused")
            return serve(phone)
        }
        fun serve(peer: String): Link {
            val c2s = PipedOutputStream(); val tvIn = PipedInputStream(c2s, 1 shl 16)
            val s2c = PipedOutputStream(); val clIn = PipedInputStream(s2c, 1 shl 16)
            thread(isDaemon = true) {
                try { BtProtocol.serve(dir, tvIn, s2c, guard, peer, 0, hello = { p, req -> handler.handle(p, "Galaxy de test", req) }, trusted = { reg.isTrusted(it) && it in bonded }) }
                catch (_: Exception) {} finally { runCatching { s2c.close() } }
            }
            return object : Link { override val input = clIn; override val output = c2s; override fun close() { runCatching { c2s.close() }; runCatching { clIn.close() } } }
        }
    }

    private fun raw(tv: FakeTv, peer: String, request: ByteArray): ByteArray {
        tv.serve(peer).use { l -> l.output.write(request); l.output.flush(); return l.input.readBytes() }
    }

    @Test fun anUntrustedPeerNeverLearnsAnythingNotEvenWhenAsking() {
        val tv = FakeTv()
        // not asking: one byte, ERR_UNTRUSTED
        val a = raw(tv, PHONE, "CBTH".toByteArray() + byteArrayOf(0))
        assertContentEquals(byteArrayOf(BtProtocol.ERR_UNTRUSTED.toByte()), a)
        // asking while the TV is not in "Ajouter un téléphone" mode
        assertContentEquals(byteArrayOf(BtProtocol.ERR_NOT_OPEN.toByte()), raw(tv, PHONE, "CBTH".toByteArray() + byteArrayOf(1)))
        // a peer that is not paired (no link key) is refused even when asking and the window is open
        tv.pairing.open(); tv.bonded.clear()
        assertContentEquals(byteArrayOf(BtProtocol.ERR_UNTRUSTED.toByte()), raw(tv, PHONE, "CBTH".toByteArray() + byteArrayOf(1)))
        // a peer whose "address" is not a Bluetooth address is refused
        assertContentEquals(byteArrayOf(BtProtocol.ERR_UNTRUSTED.toByte()), raw(tv, "127.0.0.1", "CBTH".toByteArray() + byteArrayOf(1)))
        assertTrue(tv.reg.list().isEmpty()); assertTrue(tv.connected.isEmpty())
    }

    @Test fun fullPairingThenPlugAndPlayHello() {
        val tv = FakeTv(); tv.pairing.open()
        val result = LinkedBlockingQueue<Any>()
        thread(isDaemon = true) { result.put(PhoneLink(tv, { true }, now = { clock }).connect(SavedTv(TV, "CastBridge TV", addedAt = 1), requestTrust = true)) }
        waitAsking(tv.pairing)
        assertEquals("Galaxy de test", tv.pairing.asking()!!.name)
        assertTrue(tv.pairing.approve())
        val r = result.poll(5, TimeUnit.SECONDS) as PhoneLink.Result.Connected
        assertEquals("TV du salon", r.session.tv.name)
        assertEquals(LinkPlanner.Route.Lan("http://192.168.1.20:8765"), r.session.route)
        assertEquals(PHONE, tv.reg.verifyToken(r.session.credential), "the token belongs to this phone")
        assertFalse(r.session.credential.contains("482913"), "the PIN never travels")
        assertEquals(12 * 3600_000L, r.session.expiresAt - clock)
        // next time: no window, no flag, no owner: it just works
        val again = PhoneLink(tv, { true }).connect(r.session.tv) as PhoneLink.Result.Connected
        assertNotEquals(r.session.credential, again.session.credential)
        assertEquals(listOf("Galaxy de test", "Galaxy de test"), tv.connected)
    }

    @Test fun refusedOnTheTvMeansNoAccess() {
        val tv = FakeTv(); tv.pairing.open()
        val result = LinkedBlockingQueue<Any>()
        thread(isDaemon = true) { result.put(PhoneLink(tv, { true }).connect(SavedTv(TV, "x"), requestTrust = true)) }
        waitAsking(tv.pairing); tv.pairing.deny()
        val r = result.poll(5, TimeUnit.SECONDS) as PhoneLink.Result.Refused
        assertEquals(BtProtocol.ERR_DENIED, r.code); assertFalse(r.needsPairing)
        assertTrue(tv.reg.list().isEmpty())
        val next = PhoneLink(tv, { true }).connect(SavedTv(TV, "x")) as PhoneLink.Result.Refused
        assertTrue(next.needsPairing, "still not trusted")
    }

    @Test fun revokedPhoneIsLockedOutImmediatelyAndOnBluetoothUnpairToo() {
        val tv = FakeTv(); tv.reg.trust(PHONE, "Galaxy")
        val link = PhoneLink(tv, { true })
        val ok = link.connect(SavedTv(TV, "x")) as PhoneLink.Result.Connected
        tv.reg.revoke(PHONE)
        assertNull(tv.reg.verifyToken(ok.session.credential))
        assertTrue((link.connect(SavedTv(TV, "x")) as PhoneLink.Result.Refused).needsPairing)
        tv.reg.trust(PHONE, "Galaxy"); tv.bonded.remove(PHONE)           // the owner removed the phone in the TV's Bluetooth settings
        assertTrue((link.connect(SavedTv(TV, "x")) as PhoneLink.Result.Refused).needsPairing)
    }

    @Test fun routeFallsBackWhenWifiIsNotShared() {
        val tv = FakeTv(); tv.reg.trust(PHONE, "Galaxy")
        val r = PhoneLink(tv, { false }).connect(SavedTv(TV, "x")) as PhoneLink.Result.Connected
        assertEquals(LinkPlanner.Route.Bluetooth, r.session.route); assertNull(r.session.base)
        assertEquals(PHONE, tv.reg.verifyToken(r.session.credential), "the credential still works for Bluetooth features")
    }

    @Test fun tvOffBluetoothOffAndRenamedTv() {
        val tv = FakeTv(); tv.reg.trust(PHONE, "Galaxy")
        tv.btOn = false
        assertIs<PhoneLink.Result.TvAbsent>(PhoneLink(tv, { true }).connect(SavedTv(TV, "x")))
        val off = PhoneLink(object : BtTransport { override fun connect(address: String) = throw BtUnavailable(BtUnavailable.Reason.OFF) }, { true })
        assertEquals(BtUnavailable.Reason.OFF, (off.connect(SavedTv(TV, "x")) as PhoneLink.Result.BluetoothProblem).reason)
        tv.btOn = true; tv.name = "Salon 4K"; tv.lan = listOf("192.168.1.99")
        val r = PhoneLink(tv, { true }).connect(SavedTv(TV, "TV du salon", lastIps = listOf("192.168.1.20"))) as PhoneLink.Result.Connected
        assertEquals("Salon 4K", r.session.tv.name, "a renamed TV is followed"); assertEquals(listOf("192.168.1.99"), r.session.tv.lastIps)
        assertEquals("CastBridge TV Test", r.session.tv.mdns)
    }

    @Test fun helloAnswerIsStrictlyParsed() {
        val ok = HelloInfo("TV", "1", null, "cbk_" + "a".repeat(64), 100, LinkInfo(8765, listOf("10.0.0.2")))
        assertEquals(ok.copy(ttlSec = 100), HelloInfo.decode(ok.encode()))
        assertNull(HelloInfo.decode("tv=TV\nttl=1\nport=8765\nip=10.0.0.2"), "no token: unusable")
        assertNull(HelloInfo.decode("token=123456\nip=10.0.0.2"), "the PIN is not a token")
        assertEquals(listOf("10.0.0.2"), HelloInfo.decode(ok.encode() + "ip=evil.example.com\n")!!.link.ips, "host names are never followed")
        assertEquals(7 * 24 * 3600L, HelloInfo.decode(ok.encode().replace("ttl=100", "ttl=99999999"))!!.ttlSec)
    }

    // ------------------------------------------------------------------ CBT1 and friends: no regression, PIN-less only for trusted phones

    @Test fun existingClientsWithThePinStillWork() {
        val tv = FakeTv()                                               // nobody trusted: exactly the old behaviour
        val data = Random(5).nextBytes(300_000)
        tv.serve("AA:00:00:00:00:99").use { l ->
            BtProtocol.send(l.input, l.output, "mac.bin", data.size.toLong(), "482913", { off -> ByteArrayInputStream(data, off.toInt(), data.size - off.toInt()) })
        }
        assertContentEquals(data, File(dir, "mac.bin").readBytes())
        tv.serve("AA:00:00:00:00:99").use { l ->
            assertEquals(BtProtocol.ERR_PIN, assertFailsWith<BtProtocol.Refused> { BtProtocol.send(l.input, l.output, "x.bin", 1, "000000", { ByteArrayInputStream(ByteArray(1)) }) }.code)
        }
        // a peer that does not know HELLO (old phone) and a TV that does not offer it (old TV) keep working
        val old = BtProtocol.serve(dir, ByteArrayInputStream("CBTH\u0000".toByteArray()), ByteArrayOutputStream(), null, "x", 0)
        assertEquals(BtProtocol.ERR_MAGIC, old)
    }

    @Test fun aTrustedPhoneSendsFilesWithoutPinAndOthersCannot() {
        val tv = FakeTv(); tv.reg.trust(PHONE, "Galaxy")
        val data = Random(6).nextBytes(50_000)
        tv.serve(PHONE).use { l ->
            BtProtocol.send(l.input, l.output, "phone.bin", data.size.toLong(), TvAuth.btPin("cbk_" + "0".repeat(64)), { off -> ByteArrayInputStream(data, off.toInt(), data.size - off.toInt()) })
        }
        assertContentEquals(data, File(dir, "phone.bin").readBytes())
        // same bytes from a peer that is not trusted: refused as a wrong PIN (and counted by the lockout)
        tv.serve(PHONE2).use { l ->
            assertEquals(BtProtocol.ERR_PIN, assertFailsWith<BtProtocol.Refused> { BtProtocol.send(l.input, l.output, "evil.bin", 10, TvAuth.NO_PIN, { ByteArrayInputStream(ByteArray(10)) }) }.code)
        }
        assertFalse(File(dir, "evil.bin").exists())
        // trusted in the registry but no longer paired at the Bluetooth level: the PIN is needed again
        tv.bonded.remove(PHONE)
        tv.serve(PHONE).use { l ->
            assertEquals(BtProtocol.ERR_PIN, assertFailsWith<BtProtocol.Refused> { BtProtocol.send(l.input, l.output, "evil.bin", 10, TvAuth.NO_PIN, { ByteArrayInputStream(ByteArray(10)) }) }.code)
        }
        // negotiation (CBTN) too
        tv.bonded.add(PHONE)
        tv.serve(PHONE).use { l -> assertFailsWith<BtProtocol.Refused> { BtProtocol.negotiate(l.input, l.output, TvAuth.NO_PIN, false) } }   // TV built without negotiate: old-TV answer
    }

    @Test fun pinLockoutStillAppliesToGuessingOverBluetooth() {
        val tv = FakeTv()
        repeat(5) { tv.serve("AA:00:00:00:00:77").use { l -> assertFailsWith<BtProtocol.Refused> { BtProtocol.send(l.input, l.output, "g.bin", 1, "111111", { ByteArrayInputStream(ByteArray(1)) }) } } }
        tv.serve("AA:00:00:00:00:77").use { l ->
            assertEquals(BtProtocol.ERR_LOCKED, assertFailsWith<BtProtocol.Refused> { BtProtocol.send(l.input, l.output, "g.bin", 1, "482913", { ByteArrayInputStream(ByteArray(1)) }) }.code)
        }
    }

    // ------------------------------------------------------------------ the Wi-Fi API with a token

    private fun http(base: String, path: String, method: String = "GET", header: Pair<String, String>? = null): Int {
        val c = URL(base + path).openConnection() as HttpURLConnection
        c.requestMethod = method; header?.let { c.setRequestProperty(it.first, it.second) }
        if (method == "POST") { c.doOutput = true; c.setFixedLengthStreamingMode(0); c.outputStream.close() }
        return try { c.responseCode } finally { runCatching { c.errorStream?.close() }; runCatching { c.inputStream?.close() } }
    }

    @Test fun wifiApiAcceptsATokenButKeepsSensitiveRoutesForThePin() {
        val reg = registry(); reg.trust(PHONE, "Galaxy")
        val tok = reg.issueToken(PHONE)!!.token
        val port = ServerSocket(0).use { it.localPort }
        val server = ReceiverServer(VolumeRegistry.single(dir), FakePlayer(), port, pin = "482913", tokenAuth = reg::verifyToken).apply { start(5000, false) }
        try {
            val base = "http://127.0.0.1:$port"
            assertEquals(401, http(base, "/api/info"), "nothing without credentials")
            assertEquals(200, http(base, "/api/info", header = TvAuth.TOKEN_HEADER to tok))
            assertEquals(200, http(base, "/api/info", header = TvAuth.PIN_HEADER to "482913"), "the PIN keeps working for browsers and others")
            assertEquals(200, http(base, "/api/info?token=$tok"))
            assertEquals(401, http(base, "/api/info", header = TvAuth.TOKEN_HEADER to "cbk_" + "0".repeat(64)))
            assertEquals(401, http(base, "/api/info", header = TvAuth.TOKEN_HEADER to "482913"), "the PIN is not accepted as a token")
            assertEquals(403, http(base, "/api/ssh", header = TvAuth.TOKEN_HEADER to tok), "SSH needs the owner's PIN")
            assertEquals(403, http(base, "/api/apk/install?names=a.apk", "POST", TvAuth.TOKEN_HEADER to tok))
            assertEquals(200, http(base, "/api/hello"))
            assertEquals(200, TvClient(base, tok).info().let { 200 }, "TvClient sends a token in the right header")
            reg.revoke(PHONE)
            assertEquals(401, http(base, "/api/info", header = TvAuth.TOKEN_HEADER to tok), "revoked: cut on the next request")
            assertEquals(200, http(base, "/api/info", header = TvAuth.PIN_HEADER to "482913"))
        } finally { server.stop() }
    }

    @Test fun helloNeverExposesThePin() {
        val tv = FakeTv(); tv.reg.trust(PHONE, "Galaxy")
        val bytes = raw(tv, PHONE, "CBTH".toByteArray() + byteArrayOf(0))
        assertEquals(0, bytes[0].toInt())
        assertFalse(String(bytes, Charsets.UTF_8).contains("482913"))
    }

    // ------------------------------------------------------------------ phone side: reconnect, several TVs, changes

    @Test fun reconnectSchedule() {
        assertEquals(listOf(2000L, 4000, 8000, 15000, 30000, 60000, 60000), (1..7).map { ReconnectPolicy.retryDelayMs(it) })
        assertEquals(2000L, ReconnectPolicy.retryDelayMs(0))
        assertEquals(1_000L + 30_000, ReconnectPolicy.renewAt(1_000, 61_000))
        val tv = FakeTv(); tv.reg.trust(PHONE, "G")
        val s = (PhoneLink(tv, { true }, now = { clock }).connect(SavedTv(TV, "x")) as PhoneLink.Result.Connected).session
        assertFalse(ReconnectPolicy.needsRenewal(s, clock + 5 * 3600_000L))
        assertTrue(ReconnectPolicy.needsRenewal(s, clock + 7 * 3600_000L), "renewed in the background before it runs out")
    }

    @Test fun severalTvsWithADefault() {
        val p = MemoryTrustPersistence(); val s = SavedTvs(p)
        assertNull(s.default())
        s.upsert(SavedTv(TV.lowercase(), "Salon", addedAt = 1)); assertEquals("Salon", s.default()!!.name, "the only TV is the default")
        s.upsert(SavedTv("66:55:44:33:22:11", "Chambre", addedAt = 2))
        assertEquals("Salon", s.default()!!.name, "the first one stays the default until the user chooses")
        assertTrue(s.setDefault("66:55:44:33:22:11")); assertEquals("Chambre", s.default()!!.name)
        assertFalse(s.setDefault("00:00:00:00:00:00"))
        val reloaded = SavedTvs(p); assertEquals("Chambre", reloaded.default()!!.name); assertEquals(2, reloaded.list().size)
        reloaded.remove("66:55:44:33:22:11"); assertEquals("Salon", reloaded.default()!!.name)
        reloaded.remove(TV); assertNull(reloaded.default())
    }

    @Test fun twoTvsAndNoChoiceMeansNoAutoConnect() {
        val s = SavedTvs(MemoryTrustPersistence())
        s.upsert(SavedTv("11:11:11:11:11:11", "A"), makeDefault = false); s.upsert(SavedTv("22:22:22:22:22:22", "B"))
        assertEquals("A", s.default()!!.name)                          // explicit first choice kept
        val q = SavedTvs(MemoryTrustPersistence().also { it.text = "S\t11:11:11:11:11:11\tA\t\t\t8765\t0\nS\t22:22:22:22:22:22\tB\t\t\t8765\t0\n" })
        assertNull(q.default(), "several TVs and no default: the user picks")
    }

    @Test fun tvBluetoothAddressChanged() {
        val s = SavedTvs(MemoryTrustPersistence()); s.upsert(SavedTv(TV, "Salon"), true)
        val seen = listOf(TvCandidate("AA:AA:AA:AA:AA:AA", "Salon", bonded = true, hasCbt1 = true))
        val ch = s.addressChanges(seen).single()
        assertEquals(TV, ch.old.address); assertEquals("AA:AA:AA:AA:AA:AA", ch.newAddress)
        s.moveAddress(ch.old.address, ch.newAddress)
        assertEquals("AA:AA:AA:AA:AA:AA", s.default()!!.address); assertTrue(s.addressChanges(seen).isEmpty())
        assertTrue(s.addressChanges(listOf(TvCandidate(TV, "Autre", true, true))).isEmpty(), "a different name is a different TV")
    }

    @Test fun candidateListShowsConfirmedTvsFirst() {
        val all = listOf(
            TvCandidate("01:01:01:01:01:01", "Enceinte", bonded = true, hasCbt1 = false),
            TvCandidate("02:02:02:02:02:02", "Salon", bonded = false, hasCbt1 = true),
            TvCandidate("03:03:03:03:03:03", "Chambre", bonded = true, hasCbt1 = true),
            TvCandidate("04:04:04:04:04:04", "Inconnu", bonded = false, hasCbt1 = null),
            TvCandidate("02:02:02:02:02:02", "Salon", bonded = true, hasCbt1 = null),   // same device seen twice: merged
        )
        assertEquals(listOf("Chambre", "Salon"), Candidates.tvs(all).map { it.name })
        assertTrue(Candidates.tvs(all).all { it.bonded }, "merged: bonded wins, and CBT1 stays confirmed")
        assertEquals(setOf("Enceinte", "Inconnu"), Candidates.others(all).map { it.name }.toSet())
    }

    // ------------------------------------------------------------------ server statistics: nothing about Bluetooth

    @Test fun noBluetoothAddressCanReachTheStatisticsServer() {
        val keys = castbridge.core.telemetry.EventCatalog.EVENTS.values.flatten().toSet()
        assertTrue(keys.none { it in setOf("address", "bt", "bt_address", "mac", "phone", "phones", "trusted", "token", "name", "device") })
        assertTrue("mac" in castbridge.core.telemetry.EventCatalog.FORBIDDEN && "token" in castbridge.core.telemetry.EventCatalog.FORBIDDEN)
        assertTrue(castbridge.core.telemetry.EventCatalog.EVENTS.keys.none { it.contains("pair") || it.contains("trust") }, "no pairing/trust event exists")
    }
}
