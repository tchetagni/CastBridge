package castbridge.core.smart

import castbridge.core.remote.RemoteKey
import castbridge.core.remote.smart.*
import kotlin.test.*

class OrchestratorTest {
    private var now = 1_000_000L
    private val memory = MemoryStrategyMemory()
    private fun fp(vararg candidates: String, vendor: Vendor = Vendor.UNKNOWN) = TvFingerprint(vendor, null, null, 0.8, listOf("test"), candidates.toList())
    private fun orch(fp: TvFingerprint, vararg s: RemoteStrategy, experimental: Boolean = false, retryAfter: Long = 60_000, limiter: RateLimiter = RateLimiter(1000)) =
        Orchestrator("tv1", fp, s.toList(), memory, { now }, DiagLog(), limiter, retryAfter, 0, {}).also { it.allowExperimental = experimental }

    @Test fun planFollowsTheFingerprintAndKeepsCastBridgeFirst() {
        val a = FakeStrategy(StrategyIds.CVTE); val b = FakeStrategy(StrategyIds.ROKU); val n = FakeStrategy(StrategyIds.CASTBRIDGE); val d = FakeStrategy(StrategyIds.DLNA)
        val o = orch(fp(StrategyIds.CASTBRIDGE, StrategyIds.ROKU, vendor = Vendor.CASTBRIDGE), a, b, n, d)
        assertEquals(listOf("castbridge", "roku-ecp", "cvte", "dlna"), o.plan().map { it.id })     // candidates, then the default order
        assertSame(n, o.connect())
    }

    @Test fun experimentalStrategiesAreOffByDefaultButAManualChoiceOverrides() {
        val x = FakeStrategy(StrategyIds.SAMSUNG, StrategyStatus.EXPERIMENTAL); val s = FakeStrategy(StrategyIds.DLNA)
        val o = orch(fp(StrategyIds.SAMSUNG), x, s)
        assertEquals(listOf("dlna"), o.plan().map { it.id })
        o.allowExperimental = true; assertEquals(listOf("samsung-tizen", "dlna"), o.plan().map { it.id })
        o.allowExperimental = false
        assertSame(x, o.choose(StrategyIds.SAMSUNG)); assertEquals(listOf("samsung-tizen"), o.plan().map { it.id })
        o.choose(null); assertEquals(listOf("dlna"), o.plan().map { it.id })
    }

    @Test fun notApplicableStrategiesAreSkipped() {
        val a = FakeStrategy(StrategyIds.ROKU, applicableTo = { false }); val b = FakeStrategy(StrategyIds.DLNA)
        assertEquals(listOf("dlna"), orch(fp(), a, b).plan().map { it.id })
    }

    @Test fun failsOverToTheNextStrategyOnConnectFailureAndRecordsWhy() {
        val a = FakeStrategy(StrategyIds.CVTE, connectError = FakeStrategy.IOExceptionKind.FAIL); val u = FakeStrategy(StrategyIds.ROKU, reachable = false); val c = FakeStrategy(StrategyIds.DLNA)
        val o = orch(fp(StrategyIds.CVTE, StrategyIds.ROKU, StrategyIds.DLNA), a, u, c)
        assertSame(c, o.connect())
        val at = o.attempts().associateBy { it.strategyId }
        assertEquals(AttemptOutcome.FAILED, at["cvte"]!!.outcome); assertTrue(at["cvte"]!!.reason!!.contains("refus"))
        assertEquals(AttemptOutcome.FAILED, at["roku-ecp"]!!.outcome); assertTrue(at["roku-ecp"]!!.reason!!.contains("sondage"))
        assertEquals(AttemptOutcome.TO_CONFIRM, at["dlna"]!!.outcome)
        assertEquals(0, u.connects, "un appareil injoignable au sondage n'est pas contacté")
    }

    @Test fun pairingNeededIsNotAFailureAndPairingThenSucceeds() {
        class P : RemoteStrategy by FakeStrategy("vizio-smartcast"), Pairable { var paired = false
            override fun connect() { if (!paired) throw StrategyException("code requis", needsPairing = true) }
            override fun pair(code: String) { if (code != "1234") throw java.io.IOException("refusé"); paired = true } }
        val p = P(); val fallback = FakeStrategy(StrategyIds.DLNA)
        val o = orch(fp("vizio-smartcast"), p, fallback)
        assertSame(fallback, o.connect()); assertEquals(AttemptOutcome.NEEDS_PAIRING, o.attempts().first { it.strategyId == "vizio-smartcast" }.outcome)
        assertFalse(o.pair("vizio-smartcast", "0000")); assertTrue(o.pair("vizio-smartcast", "1234"))
        assertSame(p, o.active)
    }

    @Test fun sendFallsOverWhenTheActiveStrategyDisappears() {
        val a = FakeStrategy(StrategyIds.CVTE); val b = FakeStrategy(StrategyIds.ROKU)
        val o = orch(fp(StrategyIds.CVTE, StrategyIds.ROKU), a, b)
        assertEquals(SendResult.Sent("cvte"), o.send(RemoteKey.VOLUME_UP))
        a.sendFails = true; a.connectError = FakeStrategy.IOExceptionKind.FAIL         // the TV stopped answering that protocol
        assertEquals(SendResult.Sent("roku-ecp"), o.send(RemoteKey.VOLUME_UP))
        assertEquals(listOf(RemoteKey.VOLUME_UP), b.sent); assertSame(b, o.active)
        assertTrue(a.closes > 0)
    }

    @Test fun aDroppedLinkIsRetriedOnTheSameStrategyFirst() {
        val a = FakeStrategy(StrategyIds.CVTE); val b = FakeStrategy(StrategyIds.ROKU)
        val o = orch(fp(StrategyIds.CVTE, StrategyIds.ROKU), a, b)
        o.send(RemoteKey.HOME)
        a.sendFails = true
        // connect() works again, but send keeps failing: after the retry it falls over
        assertEquals(SendResult.Sent("roku-ecp"), o.send(RemoteKey.BACK)); assertTrue(a.connects >= 2)
    }

    @Test fun recoveryAfterACutKeepsWorkingOnceTheTvIsBack() {
        val a = FakeStrategy(StrategyIds.CVTE, reachable = false); val b = FakeStrategy(StrategyIds.ROKU)
        val o = orch(fp(StrategyIds.CVTE, StrategyIds.ROKU), a, b, retryAfter = 10_000)
        assertEquals(SendResult.Sent("roku-ecp"), o.send(RemoteKey.HOME))      // cvte is down: roku carries on
        a.reachable = true; now += 5_000
        assertEquals(SendResult.Sent("roku-ecp"), o.send(RemoteKey.HOME))      // too early to retry the best
        now += 10_000
        assertEquals(SendResult.Sent("cvte"), o.send(RemoteKey.HOME))          // the best one is back and used again
        assertSame(a, o.active)
    }

    @Test fun keysOutsideTheCapabilitiesAreRefusedWithoutFailingOver() {
        val a = FakeStrategy(StrategyIds.DLNA, keys = setOf(RemoteKey.VOLUME_UP)); val b = FakeStrategy(StrategyIds.ROKU)
        val o = orch(fp(StrategyIds.DLNA), a, b)
        val r = o.send(RemoteKey.DPAD_UP)
        assertTrue(r is SendResult.Unavailable && "indisponible" in r.reason)
        assertSame(a, o.active); assertEquals(0, b.connects)
        assertFalse(o.capabilities.has(RemoteKey.DPAD_UP)); assertTrue(o.capabilities.has(RemoteKey.VOLUME_UP))
    }

    @Test fun rememberedStrategyIsTriedFirstOnlyAfterConfirmation() {
        val a = FakeStrategy(StrategyIds.CVTE); val b = FakeStrategy(StrategyIds.DLNA)
        val o = orch(fp(StrategyIds.CVTE), a, b)
        assertTrue(o.test())                                                  // volume + then −
        assertEquals(listOf(RemoteKey.VOLUME_UP, RemoteKey.VOLUME_DOWN), a.sent)
        assertNull(memory.recall("tv1"), "rien n'est mémorisé avant le « oui »")
        o.confirmTest(true)
        assertEquals(StrategyIds.CVTE, memory.recall("tv1")!!.strategyId); assertEquals(now, memory.recall("tv1")!!.at)
        // a new orchestrator for the same TV puts the remembered strategy first even if the fingerprint says otherwise
        val o2 = orch(fp(StrategyIds.DLNA), FakeStrategy(StrategyIds.CVTE), FakeStrategy(StrategyIds.DLNA))
        assertEquals("cvte", o2.plan().first().id)
    }

    @Test fun answeringNoForgetsTheStrategyAndMovesOn() {
        val a = FakeStrategy(StrategyIds.CVTE); val b = FakeStrategy(StrategyIds.DLNA)
        val o = orch(fp(StrategyIds.CVTE), a, b)
        memory.put("tv1", Remembered("cvte", now))
        o.test(); o.confirmTest(false)
        assertNull(memory.recall("tv1")); assertNull(o.active)
        assertEquals(AttemptOutcome.FAILED, o.attempts().first { it.strategyId == "cvte" }.outcome)
    }

    @Test fun aStrategyThatAcknowledgesKeysIsRememberedByItself() {
        val a = FakeStrategy(StrategyIds.ROKU, verifiesDelivery = true)
        val o = orch(fp(StrategyIds.ROKU), a)
        assertNull(memory.recall("tv1")); o.send(RemoteKey.HOME)
        assertEquals("roku-ecp", memory.recall("tv1")!!.strategyId)
    }

    @Test fun testNeedsVolumeAndNeverLeavesTheVolumeUp() {
        val noVol = FakeStrategy(StrategyIds.DLNA, keys = setOf(RemoteKey.HOME))
        assertFalse(orch(fp(StrategyIds.DLNA), noVol).test())
        val a = FakeStrategy(StrategyIds.CVTE); val o = orch(fp(StrategyIds.CVTE), a)
        o.test(); assertEquals(RemoteKey.VOLUME_DOWN, a.sent.last())
    }

    @Test fun rateLimiterBlocksFloods() {
        val a = FakeStrategy(StrategyIds.CVTE)
        val o = orch(fp(StrategyIds.CVTE), a, limiter = RateLimiter(3) { now })
        val r = (1..5).map { o.send(RemoteKey.VOLUME_UP) }
        assertEquals(3, r.count { it is SendResult.Sent }); assertEquals(2, r.count { it == SendResult.RateLimited })
        now += 1000; assertTrue(o.send(RemoteKey.VOLUME_UP) is SendResult.Sent)
    }

    @Test fun noStrategyAtAllGivesAClearAnswer() {
        val r = orch(fp(), FakeStrategy(StrategyIds.CVTE, reachable = false)).send(RemoteKey.HOME)
        assertTrue(r is SendResult.Unavailable && "Ma TV" in r.reason)
    }

    @Test fun diagnosticHasNoSecretsAndExplainsTheAttempts() {
        val a = FakeStrategy(StrategyIds.CVTE, connectError = FakeStrategy.IOExceptionKind.FAIL)
        val h = TvHints(host = "192.168.1.20", mac = "b0:a7:37:11:22:33")
        val f = TvFingerprint(Vendor.CVTE, "CVTE/Amlogic", "SMART_TV", 0.97, listOf("mDNS _share._tcp at 192.168.1.20 token=SECRET1"), listOf("cvte"), hints = h)
        val o = Orchestrator("tv1", f, listOf(a), memory, { now })
        o.connect()
        o.diag.add("pairing pin=654321 psk: TOPSECRET host 192.168.1.20")
        val d = o.diagnostic()
        for (bad in listOf("SECRET1", "654321", "TOPSECRET", "192.168.1.20")) assertFalse(bad in d, "$bad dans le diagnostic")
        assertTrue("CVTE" in d && "cvte: FAILED" in d && "refus simulé" in d && "97 %" in d)
    }

    @Test fun catalogBuildsEveryStrategyInOrderAndRealOnesAreHonestAboutStatus() {
        val env = StrategyEnv(MemorySecretStore(), native = { throw java.io.IOException("x") })
        val all = StrategyCatalog.build(testTarget(), TvFingerprint.unknown(), env)
        assertEquals(StrategyIds.DEFAULT_ORDER, all.map { it.id })
        val st = all.associate { it.id to it.status }
        for (id in listOf("castbridge", "cvte", "roku-ecp", "dlna", "sony-ircc", "vendor-app")) assertEquals(StrategyStatus.STABLE, st[id], id)
        for (id in listOf("samsung-tizen", "lg-webos", "androidtv-v2", "philips-jointspace", "vizio-smartcast", "bluetooth-hid", "infrared")) assertEquals(StrategyStatus.EXPERIMENTAL, st[id], id)
    }
}
