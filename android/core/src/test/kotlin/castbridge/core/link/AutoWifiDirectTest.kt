package castbridge.core.link

import castbridge.core.link.BulkRoute.Ask
import castbridge.core.link.BulkRoute.Decision
import castbridge.core.link.BulkRoute.Facts
import castbridge.core.link.BulkRoute.Why
import castbridge.core.link.WdClient.Effect
import castbridge.core.link.WdClient.Event
import castbridge.core.link.WdClient.Fail
import castbridge.core.link.WdClient.State
import castbridge.core.tv.BtProtocol
import castbridge.core.tv.LinkInfo
import castbridge.core.tv.LinkPlanner
import castbridge.core.tv.PinGuard
import castbridge.core.tv.WifiDirect
import castbridge.core.ux.SignalLevel
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.*

/**
 * « Seul le Bluetooth : Wi-Fi Direct automatique » (docs/agent-reports/auto-wifi-direct.md, R-14) : la décision de voie pour la masse,
 * l'automate du contrôleur côté téléphone (horloge simulée), le bail du groupe côté TV et le protocole CBTN (drapeaux, erreurs, secret jamais affiché).
 */
class AutoWifiDirectTest {
    private val MB = 1L shl 20
    private val base = Facts(bytes = 200 * MB, lanAlive = false, btConnected = true, api = 34, permission = WdPermission.GRANTED, now = 1_000_000L)

    // ------------------------------------------------------------------ BulkRoute.decide : la table de décision

    @Test fun onlyBluetoothAndABigFileStartsWifiDirectWithoutAnyAction() {
        assertEquals(Decision.UseWd(start = true), BulkRoute.decide(base))
        assertEquals(Decision.UseWd(start = true), BulkRoute.decide(base.copy(isolated = true, lanAlive = false)), "LAN isolé : Wi-Fi Direct")
    }

    @Test fun aWorkingLanIsNeverReplaced() {
        assertEquals(Decision.UseLan, BulkRoute.decide(base.copy(lanAlive = true)))
        assertEquals(Decision.UseLan, BulkRoute.decide(base.copy(lanAlive = true, wdUp = true)), "même un groupe monté ne remplace pas un LAN vivant")
    }

    @Test fun aGroupAlreadyUpIsReusedEvenForASmallFile() {
        assertEquals(Decision.UseWd(start = false), BulkRoute.decide(base.copy(wdUp = true, bytes = 1000)))
    }

    @Test fun nothingWorksIsRedNoRoute() {
        assertEquals(Decision.NoRoute, BulkRoute.decide(base.copy(btConnected = false)))
        assertEquals(SignalLevel.RED, BulkLine.of(Decision.NoRoute, null).level)
    }

    @Test fun smallFilesStayOnBluetooth() {
        assertEquals(Decision.UseBt(Why.SMALL), BulkRoute.decide(base.copy(bytes = 5 * MB - 1)))
        assertEquals(Decision.UseWd(true), BulkRoute.decide(base.copy(bytes = 5 * MB)), "5 Mo : le groupe vaut la peine")
        assertEquals(Decision.UseBt(Why.SMALL), BulkRoute.decide(base.copy(bytes = -1)), "taille inconnue : pas de groupe pour rien")
    }

    @Test fun settingOffTrialOldPhoneAndTvWithoutWifiDirectKeepBluetooth() {
        assertEquals(Decision.UseBt(Why.SETTING_OFF), BulkRoute.decide(base.copy(autoWifiDirect = false)))
        assertEquals(Decision.UseBt(Why.TRIAL), BulkRoute.decide(base.copy(trialTv = true)))
        assertEquals(Decision.UseBt(Why.TRIAL), BulkRoute.decide(base.copy(tvWdError = WifiDirect.Err.TRIAL)))
        assertEquals(Decision.UseBt(Why.PHONE_TOO_OLD), BulkRoute.decide(base.copy(api = 28)))
        assertEquals(Decision.UseBt(Why.TV_NO_WD), BulkRoute.decide(base.copy(tvOffersWd = false)))
        assertEquals(Decision.UseWd(true), BulkRoute.decide(base.copy(tvOffersWd = null)), "TV qui ne dit rien (ancienne) : on essaie")
        assertEquals(Decision.UseBt(Why.TV_WIFI_OFF), BulkRoute.decide(base.copy(tvWdError = WifiDirect.Err.WIFI_OFF)))
    }

    @Test fun permissionIsAskedJustInTimeOnceAndARefusalFallsBackToBluetooth() {
        assertEquals(Decision.AskOnce(Ask.PERMISSION), BulkRoute.decide(base.copy(permission = WdPermission.NOT_ASKED)))
        assertEquals(Decision.Wait(Why.PERMISSION_PENDING), BulkRoute.decide(base.copy(permission = WdPermission.ASKING)))
        assertEquals(Decision.UseBt(Why.PERMISSION_DENIED), BulkRoute.decide(base.copy(permission = WdPermission.DENIED)))
        assertEquals(Decision.UseBt(Why.BACKGROUND), BulkRoute.decide(base.copy(permission = WdPermission.NOT_ASKED, foreground = false)),
            "jamais de demande quand l'app n'est pas devant : Bluetooth")
        assertEquals(Decision.UseWd(true), BulkRoute.decide(base.copy(api = 30, permission = WdPermission.NOT_NEEDED)), "Android 10-12 : aucune permission d'exécution")
        assertTrue(BulkRoute.explain(Why.PERMISSION_DENIED).contains("Bluetooth"))
    }

    @Test fun phoneWifiOffIsExplainedOnceThenBluetooth() {
        assertEquals(Decision.AskOnce(Ask.PHONE_WIFI), BulkRoute.decide(base.copy(phoneWifiOn = false)))
        assertEquals(Decision.UseBt(Why.PHONE_WIFI_OFF), BulkRoute.decide(base.copy(phoneWifiOn = false, wifiAskedOnce = true)))
        assertEquals(Decision.UseBt(Why.PHONE_WIFI_OFF), BulkRoute.decide(base.copy(phoneWifiOn = false, foreground = false)))
    }

    @Test fun threeFailuresMeanBluetoothForTenMinutesNeverALoop() {
        var b = WdBackoff()
        val t0 = 5_000_000L
        b = b.failed(t0); assertFalse(b.blocked(t0 + 1))
        b = b.failed(t0 + 10_000); assertFalse(b.blocked(t0 + 10_001))
        b = b.failed(t0 + 20_000)
        assertTrue(b.blocked(t0 + 20_001))
        assertEquals(Decision.UseBt(Why.BACKOFF), BulkRoute.decide(base.copy(now = t0 + 20_001, backoffUntil = b.until)))
        assertTrue(b.blocked(t0 + 20_000 + WdBackoff.PAUSE_MS - 1))
        assertFalse(b.blocked(t0 + 20_000 + WdBackoff.PAUSE_MS), "après 10 minutes, un nouvel essai est permis")
        assertEquals(Decision.UseWd(true), BulkRoute.decide(base.copy(now = t0 + 20_000 + WdBackoff.PAUSE_MS, backoffUntil = b.until)))
        assertEquals(WdBackoff(), b.failed(t0).succeeded(), "un succès efface les échecs")
        // des échecs espacés de plus de 10 minutes ne s'additionnent pas
        val spaced = WdBackoff().failed(0).failed(WdBackoff.WINDOW_MS + 1).failed(2 * WdBackoff.WINDOW_MS + 2)
        assertFalse(spaced.blocked(2 * WdBackoff.WINDOW_MS + 3))
    }

    @Test fun aGroupLostMidTransferReroutesAtMostTwice() {
        assertTrue(BulkRoute.rerouteAfterLoss(viaWd = true, groupLost = true, reroutes = 0))
        assertTrue(BulkRoute.rerouteAfterLoss(true, true, 1))
        assertFalse(BulkRoute.rerouteAfterLoss(true, true, 2), "jamais en boucle")
        assertFalse(BulkRoute.rerouteAfterLoss(true, groupLost = false, reroutes = 0), "un refus de la TV (disque plein…) n'est pas une perte de liaison")
        assertFalse(BulkRoute.rerouteAfterLoss(viaWd = false, groupLost = true, reroutes = 0))
    }

    // ------------------------------------------------------------------ la ligne d'état honnête (sémantique TvSignal)

    @Test fun honestStateLine() {
        val up = State.Up("http://192.168.49.1:8765", since = 0, lastUse = 0)
        assertEquals(StateLine(SignalLevel.GREEN, BulkLine.WD_RUNNING), BulkLine.of(Decision.UseWd(false), up))
        val joining = BulkLine.of(Decision.UseWd(true), State.Joining(at = 0, timeoutMs = 20_000, tvIp = null, port = 8765))
        assertEquals(SignalLevel.ORANGE, joining.level); assertTrue(joining.text.startsWith(BulkLine.BT_SLOW))
        val failed = BulkLine.of(Decision.UseBt(Why.BACKOFF), State.Failed(Fail.JOIN_TIMEOUT, at = 0))
        assertEquals(SignalLevel.ORANGE, failed.level); assertEquals(BulkLine.BT_SLOW, failed.text); assertNotNull(failed.detail)
        assertEquals(StateLine(SignalLevel.GREEN, BulkLine.LAN), BulkLine.of(Decision.UseLan, null))
        assertEquals(SignalLevel.GREEN, BulkLine.of(Decision.UseBt(Why.SMALL), null).level, "petit fichier : le Bluetooth suffit, ce n'est pas une dégradation")
        assertEquals(SignalLevel.ORANGE, BulkLine.of(Decision.UseBt(Why.PERMISSION_DENIED), null).level)
        for (w in Why.values()) assertTrue(BulkRoute.explain(w).isNotBlank(), w.name)
    }

    // ------------------------------------------------------------------ WdJoin : quel mécanisme, quelle permission

    @Test fun joinMechanismPerAndroidVersion() {
        assertEquals(JoinMethod.P2P_CONNECT, WdJoin.method(33)); assertEquals(JoinMethod.P2P_CONNECT, WdJoin.method(34))
        assertEquals(JoinMethod.NETWORK_SPECIFIER, WdJoin.method(29)); assertEquals(JoinMethod.NETWORK_SPECIFIER, WdJoin.method(32))
        assertEquals(JoinMethod.NONE, WdJoin.method(28))
        assertEquals("android.permission.NEARBY_WIFI_DEVICES", WdJoin.permission(34)); assertNull(WdJoin.permission(30), "jamais la localisation")
        assertFalse(WdJoin.systemDialog(JoinMethod.P2P_CONNECT)); assertTrue(WdJoin.systemDialog(JoinMethod.NETWORK_SPECIFIER))
        assertTrue(WdJoin.joinTimeoutMs(JoinMethod.NETWORK_SPECIFIER) > WdJoin.joinTimeoutMs(JoinMethod.P2P_CONNECT), "la boîte système laisse le temps de toucher")
    }

    @Test fun groupOwnerAddress() {
        assertEquals("http://192.168.49.1:8765", WdAddress.base(goIp = "192.168.49.1", tvIp = null, port = 8765))
        assertEquals("http://192.168.49.7:8765", WdAddress.base(goIp = null, tvIp = "192.168.49.7", port = 8765))
        assertEquals("http://192.168.49.1:8765", WdAddress.base(goIp = "evil.example.com", tvIp = "8.8.8.8", port = 8765), "jamais un nom ni une adresse publique")
        assertEquals("http://192.168.49.1:8765", WdAddress.base(null, null, 8765))
    }

    // ------------------------------------------------------------------ WdClient : l'automate du contrôleur, horloge simulée

    private fun run(s: State, vararg es: Event): Pair<State, List<Effect>> {
        var cur = s; val out = mutableListOf<Effect>()
        for (e in es) { val st = WdClient.reduce(cur, e); cur = st.state; out += st.effects }
        return cur to out
    }

    @Test fun joinSuccessThenIdleRelease() {
        val m = JoinMethod.P2P_CONNECT
        val (s1, e1) = run(State.Off, Event.Start(0, m))
        assertIs<State.Requesting>(s1); assertEquals(listOf<Effect>(Effect.RequestGroup), e1)
        val (s2, e2) = run(s1, Event.Creds("DIRECT-CB-ab12cd", "S3cretPassphrase", "192.168.49.1", 8765, 2_000, m))
        assertIs<State.Joining>(s2); assertEquals(listOf<Effect>(Effect.Join("DIRECT-CB-ab12cd", "S3cretPassphrase", m)), e2)
        assertFalse(s2.toString().contains("S3cretPassphrase"), "l'état ne garde jamais le mot de passe")
        val (s3, e3) = run(s2, Event.Joined("192.168.49.1", 5_000))
        assertEquals(State.Probing("http://192.168.49.1:8765", 5_000, 0), s3); assertEquals(listOf<Effect>(Effect.Probe("http://192.168.49.1:8765")), e3)
        val (s4, e4) = run(s3, Event.ProbeOk(6_000))
        assertEquals(State.Up("http://192.168.49.1:8765", 6_000, 6_000), s4); assertEquals(listOf<Effect>(Effect.Success), e4)
        // the queue is busy: used, kept
        val (s5, e5) = run(s4, Event.Tick(60_000, busy = true))
        assertEquals(State.Up("http://192.168.49.1:8765", 6_000, 60_000), s5); assertTrue(e5.isEmpty())
        // empty queue for 29 s: kept; 30 s: left and the TV is told
        val (s6, e6) = run(s5, Event.Tick(60_000 + WdClient.IDLE_RELEASE_MS - 1, busy = false))
        assertIs<State.Up>(s6); assertTrue(e6.isEmpty())
        val (s7, e7) = run(s6, Event.Tick(60_000 + WdClient.IDLE_RELEASE_MS, busy = false))
        assertEquals(State.Off, s7); assertEquals(listOf(Effect.Leave, Effect.ReleaseTv), e7)
    }

    @Test fun joinDeniedTimeoutAndRejectedCredentialsAreFailuresThatLeave() {
        val m = JoinMethod.NETWORK_SPECIFIER
        val joining = run(State.Off, Event.Start(0, m), Event.Creds("DIRECT-CB-x1y2z3", "p".repeat(16), null, 8765, 1_000, m)).first
        val (d, de) = run(joining, Event.JoinFailed(Fail.JOIN_DENIED, 3_000))
        assertEquals(State.Failed(Fail.JOIN_DENIED, 3_000), d); assertEquals(listOf(Effect.Leave, Effect.Failure(Fail.JOIN_DENIED)), de)
        val (c, ce) = run(joining, Event.JoinFailed(Fail.BAD_CREDENTIALS, 4_000))
        assertEquals(State.Failed(Fail.BAD_CREDENTIALS, 4_000), c); assertTrue(Effect.Failure(Fail.BAD_CREDENTIALS) in ce)
        val (t0, te0) = run(joining, Event.Tick(1_000 + WdJoin.joinTimeoutMs(m) - 1, busy = true))
        assertIs<State.Joining>(t0); assertTrue(te0.isEmpty())
        val (t, te) = run(joining, Event.Tick(1_000 + WdJoin.joinTimeoutMs(m), busy = true))
        assertEquals(State.Failed(Fail.JOIN_TIMEOUT, 1_000 + WdJoin.joinTimeoutMs(m)), t); assertEquals(listOf(Effect.Leave, Effect.Failure(Fail.JOIN_TIMEOUT)), te)
    }

    @Test fun theTvRefusingOrHavingNoGroupIsAFailureWithItsCause() {
        val req = run(State.Off, Event.Start(0, JoinMethod.P2P_CONNECT)).first
        val (n, ne) = run(req, Event.NoGroup(WifiDirect.Err.WIFI_OFF, 9_000))
        assertEquals(State.Failed(Fail.TV_NO_GROUP, 9_000, WifiDirect.Err.WIFI_OFF), n); assertEquals(listOf<Effect>(Effect.Failure(Fail.TV_NO_GROUP)), ne)
        assertEquals(State.Failed(Fail.TV_REFUSED, 9_000), run(req, Event.TvRefused(9_000)).first)
        val (to, toe) = run(req, Event.Tick(WdClient.REQUEST_TIMEOUT_MS, busy = true))
        assertEquals(State.Failed(Fail.REQUEST_TIMEOUT, WdClient.REQUEST_TIMEOUT_MS), to); assertEquals(listOf<Effect>(Effect.Failure(Fail.REQUEST_TIMEOUT)), toe)
    }

    @Test fun aProbeThatNeverAnswersFailsAfterThreeTries() {
        val probing = State.Probing("http://192.168.49.1:8765", 0, 0)
        val (p1, e1) = run(probing, Event.ProbeFail(1_000))
        assertEquals(State.Probing("http://192.168.49.1:8765", 0, 1), p1); assertEquals(listOf<Effect>(Effect.Probe("http://192.168.49.1:8765")), e1)
        val (p3, e3) = run(p1, Event.ProbeFail(2_000), Event.ProbeFail(3_000))
        assertEquals(State.Failed(Fail.PROBE_FAILED, 3_000), p3); assertEquals(listOf(Effect.Probe("http://192.168.49.1:8765"), Effect.Leave, Effect.Failure(Fail.PROBE_FAILED)), e3)
    }

    @Test fun groupLostMidTransferIsAFailureAndAStartRejoins() {
        val up = State.Up("http://192.168.49.1:8765", 0, 0)
        val (l, le) = run(up, Event.Lost(10_000))
        assertEquals(State.Failed(Fail.LOST, 10_000), l); assertEquals(listOf(Effect.Leave, Effect.Failure(Fail.LOST)), le)
        val (r, re) = run(l, Event.Start(11_000, JoinMethod.P2P_CONNECT))
        assertIs<State.Requesting>(r); assertEquals(listOf<Effect>(Effect.RequestGroup), re)
        // a Start while a join runs, or while up, changes nothing (never two requests)
        assertEquals(r to emptyList<Effect>(), run(r, Event.Start(11_500, JoinMethod.P2P_CONNECT)))
        assertEquals(State.Up("http://192.168.49.1:8765", 0, 12_000) to emptyList<Effect>(), run(up, Event.Start(12_000, JoinMethod.P2P_CONNECT)))
    }

    @Test fun releaseFromAnyStateLeavesAndTellsTheTvOnlyWhenSomethingWasAsked() {
        assertEquals(State.Off to emptyList<Effect>(), run(State.Off, Event.Release(0)))
        assertEquals(State.Off to listOf(Effect.Leave, Effect.ReleaseTv), run(State.Up("http://192.168.49.1:8765", 0, 0), Event.Release(1)))
        assertEquals(State.Off to listOf<Effect>(Effect.ReleaseTv), run(State.Requesting(0), Event.Release(1)))
        assertEquals(State.Off to listOf<Effect>(Effect.ReleaseTv), run(State.Failed(Fail.LOST, 0), Event.Release(1)), "le groupe de la TV part même après un échec")
    }

    @Test fun backoffFedByTheMachineGivesUpAfterThreeFailedCycles() {
        var b = WdBackoff(); var t = 0L
        repeat(3) {
            val (_, e) = run(State.Off, Event.Start(t, JoinMethod.P2P_CONNECT), Event.Tick(t + WdClient.REQUEST_TIMEOUT_MS, true))
            e.filterIsInstance<Effect.Failure>().forEach { _ -> b = b.failed(t + WdClient.REQUEST_TIMEOUT_MS) }
            t += 60_000
        }
        assertEquals(Decision.UseBt(Why.BACKOFF), BulkRoute.decide(base.copy(now = t, backoffUntil = b.until)))
    }

    // ------------------------------------------------------------------ WdGroupLease : le groupe automatique de la TV

    @Test fun tvGroupLifecycle() {
        val f = WdGroupLease.Facts(auto = true, createdAt = 0, lastUse = 0, activeTransfers = 0, clients = null, releaseAsked = false, now = 0)
        assertFalse(WdGroupLease.shouldRemove(f.copy(auto = false, now = 10 * 3_600_000L)), "le groupe de l'utilisateur (MENU) reste")
        assertFalse(WdGroupLease.shouldRemove(f.copy(now = WdGroupLease.JOIN_GRACE_MS - 1, clients = 0)), "le téléphone a le temps de rejoindre")
        assertTrue(WdGroupLease.shouldRemove(f.copy(now = WdGroupLease.JOIN_GRACE_MS, clients = 0)), "le téléphone est parti")
        assertTrue(WdGroupLease.shouldRemove(f.copy(now = 1_000, releaseAsked = true)), "le téléphone l'a rendu")
        assertFalse(WdGroupLease.shouldRemove(f.copy(now = 1_000, releaseAsked = true, activeTransfers = 1)), "jamais pendant une réception")
        assertFalse(WdGroupLease.shouldRemove(f.copy(now = 3_600_000, clients = 0, activeTransfers = 1)))
        // client count unknown: 30 s without use after the grace
        assertFalse(WdGroupLease.shouldRemove(f.copy(now = WdGroupLease.JOIN_GRACE_MS, lastUse = WdGroupLease.JOIN_GRACE_MS - WdGroupLease.IDLE_MS + 1)))
        assertTrue(WdGroupLease.shouldRemove(f.copy(now = WdGroupLease.JOIN_GRACE_MS, lastUse = WdGroupLease.JOIN_GRACE_MS - WdGroupLease.IDLE_MS)))
        // a phone still joined but silent for 10 minutes (crashed): removed
        assertFalse(WdGroupLease.shouldRemove(f.copy(clients = 1, now = WdGroupLease.STALE_MS - 1)))
        assertTrue(WdGroupLease.shouldRemove(f.copy(clients = 1, now = WdGroupLease.STALE_MS)))
    }

    @Test fun theTvCreatesItsGroupWhenThePhoneCannotReachItsLanButNeverOnATrial() {
        assertTrue(LinkPlanner.mayStartWifiDirect(true, enabledByOwner = false, tvHasNetwork = true, phoneCannotReachLan = true), "LAN isolé")
        assertFalse(LinkPlanner.mayStartWifiDirect(true, enabledByOwner = false, tvHasNetwork = true, phoneCannotReachLan = false))
        assertTrue(LinkPlanner.mayStartWifiDirect(true, enabledByOwner = false, tvHasNetwork = false), "aucun réseau sur la TV")
        assertFalse(LinkPlanner.mayStartWifiDirect(true, enabledByOwner = true, tvHasNetwork = false, trial = true), "version d'essai : jamais")
    }

    // ------------------------------------------------------------------ secrets : frais par groupe, jamais affichés

    @Test fun freshStrongSecretsPerGroup() {
        val p = WifiDirect.groupPassphrase()
        assertTrue(p.length >= 16 && WifiDirect.isValidPassphrase(p), "WPA2-PSK de 16 caractères aléatoires au moins")
        assertNotEquals(p, WifiDirect.groupPassphrase())
        val n = WifiDirect.groupNetworkName()
        assertTrue(WifiDirect.isValidNetworkName(n), n); assertTrue(n.startsWith("DIRECT-CB"), n)
        assertNotEquals(n, WifiDirect.groupNetworkName(), "un nom par groupe : deux TV voisines ne se confondent pas")
    }

    @Test fun linkInfoNeverPrintsThePassphraseAndCarriesCapabilityAndCause() {
        val i = LinkInfo(8765, listOf("192.168.1.5"), "DIRECT-CB-ab12cd", "S3cretPassphrase", "192.168.49.1", wdCap = true, wdErr = null)
        assertFalse(i.toString().contains("S3cretPassphrase"), i.toString())
        assertEquals(i, LinkInfo.decode(i.encode()))
        val e = LinkInfo(8765, emptyList(), wdCap = true, wdErr = WifiDirect.Err.WIFI_OFF)
        assertEquals(e, LinkInfo.decode(e.encode()))
        assertNull(LinkInfo.decode("port=8765\nwd.err=../../etc\n").wdErr, "cause : un mot connu seulement")
        assertNull(LinkInfo.decode("port=8765\n").wdCap, "ancienne TV : ne dit rien")
    }

    // ------------------------------------------------------------------ CBTN : drapeaux additifs, rendu du groupe

    private fun link(neg: (String, Int) -> LinkInfo, guard: PinGuard = PinGuard("482913"), trusted: (String) -> Boolean = { false }): Triple<InputStream, OutputStream, LinkedBlockingQueue<Any>> {
        val dir = kotlin.io.path.createTempDirectory("wdlink").toFile()
        val c2s = PipedOutputStream(); val tvIn = PipedInputStream(c2s, 1 shl 16)
        val s2c = PipedOutputStream(); val clIn = PipedInputStream(s2c, 1 shl 16)
        val res = LinkedBlockingQueue<Any>()
        thread(isDaemon = true) {
            try { res.put(BtProtocol.serve(dir, tvIn, s2c, guard, "AA:BB:CC:DD:EE:FF", 0, negotiateFlags = neg, trusted = trusted)) } catch (e: Exception) { res.put(e) }
            finally { runCatching { s2c.close() }; dir.deleteRecursively() }
        }
        return Triple(clIn, c2s, res)
    }

    @Test fun cbtnCarriesTheFlagsAndThePeerToTheTv() {
        var seen: Pair<String, Int>? = null
        val (i, o, res) = link({ peer, flags -> seen = peer to flags; LinkInfo(8765, emptyList(), "DIRECT-CB-ab12cd", "S3cretPassphrase", "192.168.49.1", wdCap = true) },
            trusted = { it == "AA:BB:CC:DD:EE:FF" })
        val info = BtProtocol.negotiate(i, o, castbridge.core.trust.TvAuth.NO_PIN, BtProtocol.WANT_WIFI_DIRECT or BtProtocol.WD_LAN_UNREACHABLE)
        assertEquals("AA:BB:CC:DD:EE:FF" to (BtProtocol.WANT_WIFI_DIRECT or BtProtocol.WD_LAN_UNREACHABLE), seen)
        assertEquals("S3cretPassphrase", info.wdPass); assertEquals(true, info.wdCap)
        assertEquals(BtProtocol.OK, res.poll(3, TimeUnit.SECONDS))
    }

    @Test fun anUntrustedPeerWithoutThePinGetsNoCredentials() {
        var called = false
        val (i, o, _) = link({ _, _ -> called = true; LinkInfo(8765, emptyList(), "DIRECT-CB-ab12cd", "S3cretPassphrase") })
        assertEquals(BtProtocol.ERR_PIN, assertFailsWith<BtProtocol.Refused> { BtProtocol.negotiate(i, o, "000000", BtProtocol.WANT_WIFI_DIRECT) }.code)
        assertFalse(called)
    }

    @Test fun theOldBooleanNegotiationStillWorks() {
        val (i, o, _) = link({ _, flags -> LinkInfo(8765, emptyList(), wdErr = if (flags and BtProtocol.WD_RELEASE != 0) "x" else null) })
        assertEquals(LinkInfo(8765, emptyList()), BtProtocol.negotiate(i, o, "482913", wantWifiDirect = true))
    }
}
