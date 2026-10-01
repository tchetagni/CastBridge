package castbridge.core

import castbridge.core.trust.*
import castbridge.core.tv.*
import java.io.IOException
import java.net.*
import kotlin.test.*

class DiagnosticsTest {
    private val clock = FakeClock()
    private val tv = FakeTv(clock)
    private val env = FakeEnv(clock, tv)
    private var sdp: Boolean? = true
    private var stored: StoredCredential? = null

    private val denv = object : DiagEnv {
        override fun btProblem() = env.btProblem()
        override fun bond(address: String) = env.bond(address)
        override fun hasCbt1Service(address: String) = sdp
        override fun connect(address: String) = tv.connect(address)
        override fun probe(base: String) = env.probe(base)
        override fun canJoinWifiDirect() = false
        override fun now() = clock.now()
        override fun appVersion() = "1.2.4-beta (14)"
    }
    private fun run(saved: SavedTv? = SavedTv(tv.tvAddress, "TV du salon")) = Diagnostics(denv) { stored }.run(saved)
    private fun DiagReport.step(id: String) = steps.first { it.id == id }

    @Test fun everythingFineListsEveryStepAndNoSecret() {
        tv.reg.trust(tv.phone, "Galaxy")
        stored = StoredCredential("cbk_" + "9".repeat(64), clock.now(), clock.now() + 3_600_000)
        val r = run(SavedTv(tv.tvAddress, "TV du salon", installId = tv.reg.installId))
        assertTrue(r.ok, r.text())  // prints the report when a step failed
        assertEquals(listOf("bluetooth", "permission", "tv", "address", "bond", "sdp", "rfcomm", "hello", "route", "token", "version"), r.steps.map { it.id })
        assertEquals("Wi-Fi (réseau commun)", r.step("route").detail)
        assertTrue(r.step("token").detail.startsWith("valide encore 5"))
        assertTrue(r.step("version").detail.contains("CastBridge-TV 0.13"))
        val text = r.text()
        assertFalse(text.contains("cbk_"), text); assertFalse(Regex("[0-9A-Fa-f]{32}").containsMatchIn(text), "no install id in full")
        assertFalse(text.contains(tv.tvAddress), "no full Bluetooth address"); assertTrue(text.contains("XX:XX:XX:XX:55:66"))
        assertFalse(text.contains("482913"))
        assertTrue(text.startsWith("CastBridge — Diagnostic Bluetooth")); assertTrue(text.contains("Tout est en ordre."))
    }

    @Test fun bluetoothOffAndPermissionAndNoTv() {
        env.bt = BtUnavailable.Reason.OFF
        var r = run(); assertEquals("bluetooth", r.firstFailure!!.id); assertEquals(LinkAction.ENABLE_BLUETOOTH, r.firstFailure!!.next!!.action)
        assertEquals(DiagStep.Status.SKIPPED, r.step("rfcomm").status)
        env.bt = BtUnavailable.Reason.NO_PERMISSION
        r = run(); assertEquals("permission", r.firstFailure!!.id); assertEquals(LinkAction.GRANT_PERMISSION, r.firstFailure!!.next!!.action)
        env.bt = null
        r = run(null); assertEquals("tv", r.firstFailure!!.id); assertEquals(LinkAction.ADD_TV, r.firstFailure!!.next!!.action); assertTrue(r.steps.size >= 10)
    }

    @Test fun notBondedAndStaleBondAndAppNotRunning() {
        env.bonds[tv.tvAddress] = BondState.NONE
        assertEquals(LinkAction.PAIR, run().step("bond").next!!.action)
        env.bonds.clear(); tv.staleBond = true
        val r = run(); assertEquals("rfcomm", r.firstFailure!!.id); assertEquals(LinkAction.REMOVE_BOND, r.firstFailure!!.next!!.action)
        tv.staleBond = false; tv.appRunning = false; sdp = false
        val r2 = run(); assertEquals("sdp", r2.firstFailure!!.id); assertEquals("CastBridge-TV n'est pas ouvert", r2.firstFailure!!.next!!.title)
        tv.appRunning = true; tv.power = false; sdp = null
        val r3 = run(); assertEquals("rfcomm", r3.firstFailure!!.id); assertEquals(DiagStep.Status.INFO, r3.step("sdp").status)
        assertEquals(LinkAction.RETRY, r3.firstFailure!!.next!!.action)
    }

    @Test fun helloRefusalShowsItsCodeAndTheResetIsNamed() {
        val r = run(SavedTv(tv.tvAddress, "TV", installId = "b".repeat(32)))
        assertEquals("hello", r.firstFailure!!.id)
        assertTrue(r.firstFailure!!.detail.startsWith("refus 8"))
        assertEquals(LinkAction.REASSOCIATE, r.firstFailure!!.next!!.action)
        assertEquals("TV réinitialisée", r.firstFailure!!.next!!.title, "the claimed id differs: the TV is another installation")
        assertEquals(DiagStep.Status.SKIPPED, r.step("route").status)
    }

    @Test fun expiredTokenAndChangedIdentityAreReported() {
        tv.reg.trust(tv.phone, "Galaxy")
        stored = StoredCredential("cbk_" + "9".repeat(64), 0, clock.now() - 1)
        val r = run(SavedTv(tv.tvAddress, "TV", installId = "c".repeat(32)))
        assertTrue(r.step("token").detail.startsWith("expiré"))
        assertEquals("version", r.firstFailure!!.id); assertTrue(r.step("version").detail.contains("identité différente"))
    }

    @Test fun redactionIsDefenceInDepth() {
        val dirty = "token cbk_" + "ab".repeat(32) + " pin 482913 key " + "f".repeat(64) + " addr AA:BB:CC:DD:EE:01 port 8765 ip 192.168.1.20 v 1.2.3"
        val clean = Redact.scrub(dirty)
        assertFalse(clean.contains("cbk_ab")); assertFalse(clean.contains("482913")); assertFalse(clean.contains("f".repeat(32))); assertFalse(clean.contains("AA:BB:CC:DD"))
        assertTrue(clean.contains("XX:XX:XX:XX:EE:01") && clean.contains("8765") && clean.contains("192.168.1.20") && clean.contains("1.2.3"))
    }
}

/** (item 7) every failure of the loop and the pairing flow has a specific French message; no raw exception text. */
class ErrorTextTest {
    private val secret = "cbk_" + "a".repeat(64) + " 482913 AA:BB:CC:DD:EE:01"

    @Test fun everyFailureTypeIsMapped() {
        val failures: List<Throwable> = listOf(
            IOException(secret), SocketTimeoutException(secret), ConnectException(secret), NoRouteToHostException(secret), UnknownHostException(secret),
            java.io.InterruptedIOException(secret), java.io.EOFException(secret), SecurityException(secret), IllegalStateException(secret), RuntimeException(secret), NullPointerException(),
            BtUnavailable(BtUnavailable.Reason.OFF), BtUnavailable(BtUnavailable.Reason.NO_PERMISSION), BtUnavailable(BtUnavailable.Reason.NO_ADAPTER),
            TvCredential.Missing(), TvClient.Conflict(5),
            *(0..14).map { BtProtocol.Refused(it) }.toTypedArray(), *(0..2).map { BtProtocol.Refused(BtProtocol.ERR_UNTRUSTED, it) }.toTypedArray(),
            *listOf(400, 401, 403, 404, 409, 413, 429, 500, 502, 503, 507, 999).map { TvClient.HttpError(it, secret) }.toTypedArray(),
            TvClient.HttpError(401, "bad token"), TvClient.HttpError(401, "locked"))
        val seen = HashSet<String>()
        for (f in failures) {
            val m = LinkText.failure(f)
            assertTrue(m.isNotBlank(), f.toString())
            LinkMachineTest.assertNoTechnicalWords(m)
            assertFalse(m.contains("cbk_") || m.contains("482913") || m.contains("AA:BB"), "never echoes what the exception carried: $m")
            assertTrue((m.first().isUpperCase() || m.first() == '«') && (m.endsWith(".") || m.endsWith("…") || m.endsWith("?")), "a sentence: $m")
            seen += m
        }
        assertTrue(seen.size >= 25, "specific messages, not one for everything: ${seen.size}")
    }

    @Test fun everyAbsenceKindAndLossSideAndStateHasText() {
        for (k in AbsentKind.values()) { val a = LinkText.absent(k, "Salon"); assertTrue(a.title.contains("Salon") || a.detail.isNotBlank()); LinkMachineTest.assertNoTechnicalWords(a.title + a.detail) }
        for (s in LossSide.values()) LinkMachineTest.assertNoTechnicalWords(LinkText.lost(s).detail)
        for (r in RouteKind.values()) LinkMachineTest.assertNoTechnicalWords(LinkText.connected("Salon", r).detail)
        for (a in LinkAction.values()) if (a != LinkAction.NONE) assertTrue(a.label.isNotBlank())
        // the machine's view of every state class, with the table of all states: none is left unmapped
        val states = listOf(LinkState.NoTv, LinkState.Connecting, LinkState.BtBlocked(BtUnavailable.Reason.OFF), LinkState.NotBonded, LinkState.Bonding, LinkState.StaleBond,
            LinkState.TvUnreachable(AbsentKind.NO_ANSWER), LinkState.TvUnreachable(AbsentKind.SERVICE_ABSENT), LinkState.TvUnreachable(AbsentKind.CLOSED_AT_ONCE),
            LinkState.TvForgotMe(0), LinkState.WaitingOwner(11), LinkState.Denied, LinkState.CredentialExpired, LinkState.TvTooOld, LinkState.TvError(6),
            LinkState.Connected(RouteKind.LAN, "Salon"), LinkState.Degraded("Salon"), LinkState.Reconnecting(LossSide.TV, null))
        val m = LinkMachine()
        assertEquals(LinkState::class.java.declaredClasses.size, states.map { it::class }.toSet().size, "every state class is in this table")
        for (s in states) {
            val v = m.view(LinkMachine.Model(shown = s, tvName = "Salon"))
            assertTrue(v.title.isNotBlank() && v.detail.isNotBlank(), s.key); LinkMachineTest.assertNoTechnicalWords(v.title + " " + v.detail)
            assertEquals(s.key, v.state.key)
        }
        assertEquals(10, Outcome::class.java.declaredClasses.size, "a new outcome needs a test row here")
    }
}

class PhonePresenceTest {
    private val clock = FakeClock()
    private val p = PhonePresence(clock::now)
    private val A = "AA:BB:CC:DD:EE:01"

    @Test fun bannerOncePerTenMinutesAndQuietReconnections() {
        assertEquals(PhonePresence.Event.BANNER, p.seen(A, "Galaxy"))
        clock.advance(10_000); assertEquals(PhonePresence.Event.NONE, p.seen(A))
        clock.advance(120_000)                                      // the link was lost for 2 minutes
        assertEquals(PhonePresence.Event.RECONNECTED, p.seen(A), "back within 10 min of the banner: quiet")
        assertEquals(listOf("Galaxy : liaison reprise"), p.lines())
        clock.advance(11 * 60_000); p.seen(A)
        clock.advance(11 * 60_000)
        assertEquals(PhonePresence.Event.BANNER, p.seen(A), "after 10 minutes the banner may speak again")
    }

    @Test fun truthfulStatus() {
        p.seen(A, "Galaxy"); assertEquals(listOf("Galaxy : connecté"), p.lines())
        clock.advance(61_000); assertEquals(listOf("Galaxy : téléphone déconnecté"), p.lines())
        p.linkOpened(A); assertEquals(listOf("Galaxy : connecté"), p.lines(), "an open link counts")
        clock.advance(10 * 60_000); assertEquals(PhonePresence.State.CONNECTED, p.statuses().single().state, "a long remote session stays connected")
        p.linkClosed(A); clock.advance(61_000); assertEquals(PhonePresence.State.DISCONNECTED, p.statuses().single().state)
        p.forget(A); assertTrue(p.lines().isEmpty())
    }

    @Test fun tvStatusLine() {
        assertEquals("Bluetooth prêt · aucun téléphone de confiance", TvBtStatus.line(true, null, 0, 0, PairingSession.State.Closed))
        assertEquals("Bluetooth prêt · 2 téléphones de confiance (1 connecté) · « Ajouter un téléphone » ouvert", TvBtStatus.line(true, null, 2, 1, PairingSession.State.Open(1)))
        assertEquals("Bluetooth : désactivé dans les réglages de la TV · 1 téléphone de confiance (0 connecté)", TvBtStatus.line(false, "désactivé dans les réglages de la TV", 1, 0, PairingSession.State.Closed))
        assertTrue(TvBtStatus.line(true, null, 1, 1, PairingSession.State.Asking(A, "Pixel", 1, 1)).endsWith("Pixel demande l'accès"))
    }
}
