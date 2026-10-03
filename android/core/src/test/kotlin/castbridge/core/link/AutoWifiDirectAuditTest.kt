package castbridge.core.link

import castbridge.core.link.BulkRoute.Decision
import castbridge.core.link.BulkRoute.Facts
import castbridge.core.link.BulkRoute.Why
import castbridge.core.net.BoundRoute
import castbridge.core.tv.BtProtocol
import castbridge.core.tv.LinkInfo
import castbridge.core.tv.LinkPlanner
import java.net.URL
import java.net.URLConnection
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.*

/** Correctifs de l'audit Opus de R-14 (docs/agent-reports/auto-wifi-direct.md § 8). */
class AutoWifiDirectAuditTest {
    private val MB = 1L shl 20

    // ------------------------------------------------------------------ I-1 : l'adresse du groupe n'est jamais un « réseau commun »

    @Test fun groupAddressesAreNeverLanAddresses() {
        assertEquals(listOf("192.168.1.20", "10.0.0.5"), HelloIps.lanOnly(listOf("192.168.1.20", "192.168.49.1", "10.0.0.5", "192.168.49.37")))
        assertEquals(listOf("192.168.1.20"), HelloIps.lanOnly(listOf("192.168.1.20", "172.20.3.1"), groupIps = setOf("172.20.3.1")), "toute adresse du groupe courant")
        assertFalse(HelloIps.isLan("192.168.49.1")); assertTrue(HelloIps.isLan("192.168.1.2"))
    }

    @Test fun aPhoneInsideTheGroupNeverTurnsTheControlRouteIntoLan49() {
        // the phone joined the group: 192.168.49.1 answers; the next HELLO (an older TV) still lists it
        val hello = LinkInfo(8765, listOf("192.168.49.1"))
        assertEquals(listOf<LinkPlanner.Route>(LinkPlanner.Route.Bluetooth), LinkPlanner.plan(hello, { true }, canJoinWifiDirect = false))
        assertFalse(BulkRoute.lanRoute(LinkPlanner.Route.Lan("http://192.168.49.1:8765")), "la file ne saute jamais Wi-Fi Direct pour un faux LAN")
        assertTrue(BulkRoute.lanRoute(LinkPlanner.Route.Lan("http://192.168.1.20:8765")))
        assertFalse(BulkRoute.lanRoute(LinkPlanner.Route.Bluetooth))
    }

    @Test fun theTvNeverAnnouncesItsGroupAddressInHello() {
        val d = FakeDriver(); val host = TvWdHost(d, { 0L }, sleep = {})
        host.answer("AA:01", BtProtocol.WANT_WIFI_DIRECT or BtProtocol.WD_LAN_UNREACHABLE, listOf("192.168.1.20"), 8765, trial = false, ownerOn = false)
        val hello = host.answer(null, 0, listOf("192.168.1.20", "192.168.49.1"), 8765, trial = false, ownerOn = false)
        assertEquals(listOf("192.168.1.20"), hello.ips)
    }

    // ------------------------------------------------------------------ identifiants : jamais dans le HELLO, le même groupe pour deux CBTN simultanés

    @Test fun anAutomaticGroupsCredentialsAreNeverInHello() {
        val d = FakeDriver(); val host = TvWdHost(d, { 0L }, sleep = {})
        val cbtn = host.answer("AA:01", BtProtocol.WANT_WIFI_DIRECT or BtProtocol.WD_LAN_UNREACHABLE, emptyList(), 8765, trial = false, ownerOn = false)
        assertNotNull(cbtn.wdPass)
        val hello = host.answer(null, 0, emptyList(), 8765, trial = false, ownerOn = false)
        assertNull(hello.wdPass); assertNull(hello.wdSsid)
        assertFalse(hello.encode().contains(cbtn.wdPass!!))
    }

    @Test fun twoSimultaneousRequestsGetTheSameGroup() {
        val d = FakeDriver(delayMs = 150); val host = TvWdHost(d, { System.currentTimeMillis() })
        val go = CountDownLatch(1); val out = arrayOfNulls<LinkInfo>(2)
        val ts = (0..1).map { i -> thread { go.await(5, java.util.concurrent.TimeUnit.SECONDS); out[i] = host.answer("AA:0$i", BtProtocol.WANT_WIFI_DIRECT or BtProtocol.WD_LAN_UNREACHABLE, emptyList(), 8765, false, false) } }
        go.countDown(); ts.forEach { it.join(5_000) }
        assertEquals(1, d.starts.get(), "une seule création")
        assertNotNull(out[0]!!.wdPass); assertEquals(out[0]!!.wdPass, out[1]!!.wdPass, "le second reçoit le MÊME groupe")
    }

    @Test fun neverOnATrialEvenWhenAGroupExists() {
        val d = FakeDriver(); val host = TvWdHost(d, { 0L }, sleep = {})
        host.answer("AA:01", BtProtocol.WANT_WIFI_DIRECT or BtProtocol.WD_LAN_UNREACHABLE, emptyList(), 8765, trial = false, ownerOn = false)
        val t = host.answer("AA:01", BtProtocol.WANT_WIFI_DIRECT or BtProtocol.WD_LAN_UNREACHABLE, emptyList(), 8765, trial = true, ownerOn = false)
        assertNull(t.wdPass); assertEquals(castbridge.core.tv.WifiDirect.Err.TRIAL, t.wdErr)
    }

    // ------------------------------------------------------------------ bail par téléphone ; WD_RELEASE d'un tiers ; groupe supprimé

    @Test fun aReleaseFromAnotherPhoneNeverRemovesTheSharedGroup() {
        var now = 0L
        val d = FakeDriver(); val host = TvWdHost(d, { now }, sleep = {})
        val want = BtProtocol.WANT_WIFI_DIRECT or BtProtocol.WD_LAN_UNREACHABLE
        host.answer("AA:01", want, emptyList(), 8765, false, false)
        host.answer("AA:02", want, emptyList(), 8765, false, false)
        now = 1_000
        host.answer("AA:03", BtProtocol.WD_RELEASE, emptyList(), 8765, false, false)     // never asked: no effect
        assertFalse(host.leaseCheck(busy = 0, clients = 2)); assertNotNull(d.active)
        host.answer("AA:01", BtProtocol.WD_RELEASE, emptyList(), 8765, false, false)
        assertFalse(host.leaseCheck(busy = 0, clients = 1), "AA:02 garde son bail entre deux fichiers")
        host.answer("AA:02", BtProtocol.WD_RELEASE, emptyList(), 8765, false, false)
        assertTrue(host.leaseCheck(busy = 0, clients = 1), "plus aucun bail : le groupe part")
        assertNull(d.active); assertEquals(1, d.stops.get())
    }

    @Test fun anOpenRemoteNeverKeepsTheGroupAlive() {
        assertEquals(0, LeaseBusy.count(httpTransfers = 0, btReceptions = 1, remoteSessions = 1), "seules les réceptions HTTP passent par le groupe")
        assertEquals(2, LeaseBusy.count(httpTransfers = 2, btReceptions = 0, remoteSessions = 1))
        var now = 0L
        val d = FakeDriver(); val host = TvWdHost(d, { now }, sleep = {})
        host.answer("AA:01", BtProtocol.WANT_WIFI_DIRECT or BtProtocol.WD_LAN_UNREACHABLE, emptyList(), 8765, false, false)
        now = WdGroupLease.JOIN_GRACE_MS + WdGroupLease.IDLE_MS
        assertTrue(host.leaseCheck(busy = LeaseBusy.count(0, 0, remoteSessions = 1), clients = null), "télécommande ouverte + file vide : supprimé après 30 s")
    }

    @Test fun theOwnersMenuGroupIsNeverRemovedByTheLease() {
        val d = FakeDriver(); val host = TvWdHost(d, { 10 * 3_600_000L }, sleep = {})
        d.startNow(forPhone = false)
        assertFalse(host.leaseCheck(busy = 0, clients = 0))
        val r = host.answer("AA:01", BtProtocol.WANT_WIFI_DIRECT, emptyList(), 8765, false, ownerOn = true)
        assertNotNull(r.wdPass, "le groupe du MENU se partage comme avant")
    }

    // ------------------------------------------------------------------ I-3 : Android 10-12 (boîte système, une seule radio)

    private val old = Facts(bytes = 200 * MB, lanAlive = false, btConnected = true, api = 30, permission = WdPermission.NOT_NEEDED, now = 0,
        phoneOnWifi = false, lanDeadConfirmed = false)

    @Test fun theSystemDialogPathNeedsTheAppVisible() {
        assertEquals(Decision.UseWd(true), BulkRoute.decide(old))
        assertEquals(Decision.UseBt(Why.BACKGROUND), BulkRoute.decide(old.copy(foreground = false)), "« Ouvrir avec » / arrière-plan : Bluetooth tout de suite")
        assertEquals(Decision.UseWd(true), BulkRoute.decide(old.copy(api = 34, permission = WdPermission.GRANTED, foreground = false)), "Android 13+ : aucune boîte, pas besoin d'être devant")
    }

    @Test fun leavingAWifiNeedsTwoSpacedNegativeLanProbes() {
        assertEquals(Decision.UseBt(Why.LAN_UNCONFIRMED), BulkRoute.decide(old.copy(phoneOnWifi = true, lanDeadConfirmed = false)))
        assertEquals(Decision.UseWd(true), BulkRoute.decide(old.copy(phoneOnWifi = true, lanDeadConfirmed = true)))
        assertEquals(Decision.UseWd(true), BulkRoute.decide(old.copy(api = 34, permission = WdPermission.GRANTED, phoneOnWifi = true)), "P2P ne quitte pas le Wi-Fi")
        assertFalse(BulkRoute.lanConfirmedDead(listOf(0L to false)), "une sonde ne suffit pas")
        assertFalse(BulkRoute.lanConfirmedDead(listOf(0L to false, 500L to false)), "trop rapprochées")
        assertTrue(BulkRoute.lanConfirmedDead(listOf(0L to false, BulkRoute.LAN_PROBE_GAP_MS to false)))
        assertFalse(BulkRoute.lanConfirmedDead(listOf(0L to false, 3_000L to true, 6_000L to false)), "une réponse : le LAN vit")
    }

    @Test fun onlyTheUploadSocketIsBoundToTheGroupNetwork() {
        val opened = mutableListOf<String>()
        BoundRoute.set("192.168.49.", object : BoundRoute.Binding {
            override fun open(url: URL): URLConnection { opened += url.host; return url.openConnection() }
            override fun bind(s: java.net.Socket) { throw IllegalStateException("bound") }
        })
        try {
            BoundRoute.open(URL("http://192.168.49.1:8765/api/hello"))
            BoundRoute.open(URL("http://192.168.1.20:8765/api/hello"))
            assertEquals(listOf("192.168.49.1"), opened, "les autres adresses (Internet de l'app) ne passent pas par le groupe")
            assertEquals("bound", assertFailsWith<IllegalStateException> { castbridge.core.xfer.HttpConn.tcp("192.168.49.1", 1, 50)() }.message,
                "le transfert rapide lie aussi son socket")
        } finally { BoundRoute.clear() }
        assertFalse(BoundRoute.applies("192.168.49.1"))
    }

    // ------------------------------------------------------------------ repli : horloge monotone, fenêtre depuis le PREMIER échec

    @Test fun backoffWindowCountsFromTheFirstFailure() {
        val m = 60_000L
        val b = WdBackoff().failed(0).failed(9 * m).failed(11 * m)
        assertFalse(b.blocked(11 * m + 1), "3 échecs mais pas dans 10 minutes depuis le premier")
        assertTrue(WdBackoff().failed(0).failed(4 * m).failed(9 * m).blocked(9 * m + 1))
    }

    /** Fake Android group: [start] creates it after [delayMs] on another thread (like WifiP2pManager's callback). */
    private class FakeDriver(private val delayMs: Long = 0) : WdGroupDriver {
        val starts = AtomicInteger(); val stops = AtomicInteger()
        @Volatile override var active: Pair<String, String>? = null
        @Volatile override var auto = false
        @Volatile override var createdAt = 0L
        override var lastError: String? = null
        override fun capable() = true
        override fun hasPermission() = true
        fun startNow(forPhone: Boolean) { auto = forPhone; active = castbridge.core.tv.WifiDirect.groupNetworkName() to castbridge.core.tv.WifiDirect.groupPassphrase() }
        override fun start(forPhone: Boolean) {
            starts.incrementAndGet()
            if (delayMs == 0L) startNow(forPhone) else thread { Thread.sleep(delayMs); startNow(forPhone) }
        }
        override fun stop() { stops.incrementAndGet(); active = null; auto = false }
    }
}
