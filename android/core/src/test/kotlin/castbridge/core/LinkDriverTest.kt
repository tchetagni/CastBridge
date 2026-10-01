package castbridge.core

import castbridge.core.trust.*
import castbridge.core.tv.BtProtocol
import castbridge.core.tv.LinkPlanner
import kotlin.test.*

/** The driver against a fake TV and a fake clock: the failures seen on the owner's hardware and the link-loss matrix. */
class LinkDriverTest {
    private val clock = FakeClock()
    private val tv = FakeTv(clock)
    private val phone = Phone(clock, tv)
    private val TV_ADDR = tv.tvAddress

    private fun connect(): LinkDriver.Step { tv.reg.trust(tv.phone, "Galaxy"); return phone.run(5_000) }
    private fun shown() = phone.driver.currentModel!!.shown
    private fun views(from: Int = 0) = phone.shownHistory.drop(from)

    // ------------------------------------------------------------------------------------------------ plain life

    @Test fun trustedPhoneConnectsByItselfAndKeepsTheLanRoute() {
        val s = connect()
        assertEquals("ok:LAN", s.view.state.key); assertEquals("TV du salon connectée", s.view.title)
        assertEquals(PHONE_TOKEN_OK, tv.reg.verifyToken(phone.driver.credential()) == tv.phone)
        assertEquals(1, tv.hellos, "one HELLO, then only keep-alives on Wi-Fi")
        phone.run(120_000)
        assertEquals(1, tv.hellos, "the Wi-Fi keep-alive does not use Bluetooth")
    }

    // ------------------------------------------------------------------------------------------------ (a) TV reinstalled

    @Test fun reinstalledTvIsExplainedOnceAndNeverFlipsTheCard() {
        connect()
        val before = phone.shownHistory.size
        tv.reinstall()
        tv.bonded += tv.phone
        phone.run(10 * 60_000)
        val history = views(before)
        assertEquals("forgot:${BtProtocol.HINT_OTHER_INSTALL}", shown().key, "the TV says it is another installation")
        val v = phone.driver.step(Trigger.USER).view
        assertEquals("TV réinitialisée", v.title)
        assertEquals("La TV a été réinitialisée ou réinstallée : elle ne vous reconnaît plus.", v.detail)
        assertEquals(LinkAction.REASSOCIATE, v.action)
        assertFalse(history.any { it.startsWith("connecting") || it.startsWith("unreachable") }, "no flapping between 'Connexion…' and 'introuvable': $history")
        assertTrue(history.size <= 3, "at most: [ok →] reconnecting → forgot: $history")
    }

    @Test fun afterTheVerdictNoTimerBroadcastOrWorkerBotherstheTv() {
        connect(); tv.reinstall()
        phone.run(60_000)
        assertEquals("forgot:1", shown().key)
        val connects = tv.connects
        for (t in listOf(Trigger.TIMER, Trigger.ACL_CONNECTED, Trigger.NETWORK, Trigger.WORKER, Trigger.SCREEN_ON, Trigger.APP_OPENED, Trigger.BLUETOOTH_STATE)) { phone.clock.advance(5_000); assertNull(phone.driver.step(t).nextInMs, "$t: waits for the user") }
        phone.run(60 * 60_000)
        assertEquals(connects, tv.connects, "an hour of timers: not one more connection (the old 'every minute' log)")
        phone.clock.advance(5_000); phone.driver.step(Trigger.USER)
        assertEquals(connects + 1, tv.connects, "the user's retry does go through")
    }

    @Test fun phoneRemovedOnTheTvIsToldApartFromAReinstall() {
        connect(); tv.reg.revoke(tv.phone)
        phone.run(5 * 60_000)
        assertEquals("forgot:${BtProtocol.HINT_SAME_INSTALL}", shown().key)
        assertEquals("Téléphone retiré de la TV", phone.driver.step(Trigger.USER).view.title)
    }

    // ------------------------------------------------------------------------------------------------ (b) bonded, accepted then closed

    @Test fun bondedButUnknownPhoneGetsOneExplanationAndTwoAttemptsNotOnePerMinute() {
        // the phone never was trusted (or the TV forgot it): HELLO -> ERR_UNTRUSTED -> close, as seen on the owner's phone and Mac
        val s = phone.run(30 * 60_000)
        assertEquals("forgot:0", s.view.state.key.let { if (it.startsWith("forgot")) "forgot:0" else it })
        assertTrue(tv.connects <= 3, "connections in half an hour: ${tv.connects}")
        assertNull(s.nextInMs)
    }

    // ------------------------------------------------------------------------------------------------ (e) the TV disappears for ~10 s

    @Test fun tvAppRestartIsTransientAndKeepsTheToken() {
        connect()
        val token = phone.driver.credential()
        tv.appRunning = false
        phone.run(10_000)
        assertTrue(shown().isGood || shown() is LinkState.Reconnecting, "never 'forget this TV', never 'introuvable': ${shown().key}")
        assertEquals(token, phone.driver.credential(), "the credential is kept while its token is valid")
        tv.restartApp(); tv.appRunning = true
        phone.run(20_000)
        assertEquals("ok:LAN", shown().key)
        assertEquals(token, phone.driver.credential(), "the very same token is reused: no new HELLO needed")
        assertTrue(phone.shownHistory.none { it.startsWith("unreachable") || it.startsWith("forgot") }, phone.shownHistory.toString())
    }

    @Test fun tvOffThenOnComesBackByItself() {
        connect()
        tv.power = false
        phone.run(5 * 60_000)
        assertEquals("unreachable:NO_ANSWER", shown().key, "after the grace period only")
        assertEquals("TV du salon est introuvable", phone.driver.step().view.title)
        tv.power = true
        val s = phone.run(2 * 60_000)
        assertTrue(s.view.state.isGood, s.view.state.key)
    }

    @Test fun tvAppNotRunningIsSaidSeparatelyFromTvOff() {
        phone.driver.step(); tv.appRunning = false
        phone.run(2 * 60_000)
        assertEquals("unreachable:SERVICE_ABSENT", shown().key)
        assertEquals("CastBridge-TV n'est pas ouvert", phone.driver.step().view.title)
    }

    // ------------------------------------------------------------------------------------------------ bond problems

    @Test fun staleBondIsDetectedFromInstantRefusals() {
        tv.staleBond = true
        phone.run(60_000)
        assertEquals("stalebond", shown().key)
        val v = phone.driver.step().view
        assertEquals(LinkAction.REMOVE_BOND, v.action); assertTrue(v.detail.contains("Supprimez la TV dans les réglages Bluetooth"))
        // the user removes the bond: noticed by itself
        phone.env.bonds[tv.tvAddress] = BondState.NONE
        phone.run(30_000)
        assertEquals("notbonded", shown().key)
        assertEquals(LinkAction.PAIR, phone.driver.step().view.action)
    }

    @Test fun bondInProgressAndBluetoothProblemsAreShownAtOnce() {
        connect()
        phone.env.bonds[tv.tvAddress] = BondState.BONDING; phone.clock.advance(2_000)
        assertEquals("bonding", phone.driver.step(Trigger.BOND_STATE).view.state.key)
        phone.env.bonds.remove(tv.tvAddress); phone.env.bt = BtUnavailable.Reason.OFF; phone.clock.advance(2_000)
        val s = phone.driver.step(Trigger.BLUETOOTH_STATE)
        assertEquals("Bluetooth éteint", s.view.title); assertEquals(LinkAction.ENABLE_BLUETOOTH, s.view.action)
        assertNotNull(phone.driver.credential(), "the token is kept: the Wi-Fi API may still work")
        phone.env.bt = null; phone.clock.advance(5_000)
        assertTrue(phone.run(30_000).view.state.isGood)
    }

    @Test fun tvWhoseBluetoothAddressChangedIsFollowed() {
        connect()
        val newAddr = "99:88:77:66:55:44"
        phone.env.bonds[TV_ADDR] = BondState.NONE
        phone.env.candidates = listOf(TvCandidate(newAddr, "TV du salon", bonded = true, hasCbt1 = true))
        phone.run(30_000)
        assertEquals(newAddr, phone.saved.default()!!.address)
    }

    // ------------------------------------------------------------------------------------------------ token life

    @Test fun tokenIsRenewedAtMidLifeWithoutAnyoneNoticing() {
        connect()
        val first = phone.driver.credential()
        val h = tv.hellos
        phone.run(7 * 3600_000L)       // past 6 h: half of 12 h
        assertNotEquals(first, phone.driver.credential(), "renewed")
        assertEquals(h + 1, tv.hellos, "exactly one HELLO for the renewal")
        assertEquals("ok:LAN", shown().key); assertEquals(1, phone.shownHistory.size, "nobody saw it")
        assertNotNull(tv.reg.verifyToken(first), "the old token lives until its own end: nothing is cut")
    }

    @Test fun renewalFailureKeepsAStillValidTokenAndRetriesSooner() {
        connect()
        val first = phone.driver.credential()
        tv.appRunning = false                          // Bluetooth service gone, but the Wi-Fi API of the fake still answers? no: app down means both
        tv.appRunning = true; tv.power = true
        // only the Bluetooth service is unreachable at renewal time
        val noBt = FakeTv(clock)
        phone.clock.advance(6 * 3600_000L + 1_000)
        tv.btRadioOn = false                           // HELLO impossible; Wi-Fi API fine
        val s = phone.step()
        assertEquals(first, phone.driver.credential(), "valid token kept")
        assertTrue(s.view.state.isGood, "Wi-Fi works, so all is well: ${s.view.state.key}")
        assertTrue(s.nextInMs!! in 5_000..5 * 60_000L, "renewal retried well before the token dies: ${s.nextInMs}")
        tv.btRadioOn = true
        phone.run(60_000)
        assertNotEquals(first, phone.driver.credential())
    }

    @Test fun tokenExpiringDuringALongOutageIsNeverUsedAndTheHelloFixesIt() {
        connect()
        val first = phone.driver.credential()!!
        tv.power = false
        phone.run(13 * 3600_000L)
        assertNull(phone.driver.credential(), "expired (with the clock-skew margin): never presented")
        assertEquals("unreachable:NO_ANSWER", shown().key)
        tv.power = true
        val s = phone.run(3 * 60_000)
        assertTrue(s.view.state.isGood)
        assertNotEquals(first, phone.driver.credential())
    }

    @Test fun aTokenTheTvRefusedIsNeverSentAgain() {
        connect()
        val bad = phone.driver.credential()!!
        phone.driver.reportTokenRejected(bad)
        assertNull(phone.driver.credential(), "refused: not offered any more")
        assertFalse(phone.driver.gate.allows(TV_ADDR, bad))
        phone.clock.advance(2_000)
        val s = phone.driver.step(Trigger.USER)
        val fresh = phone.driver.credential()
        assertNotNull(fresh); assertNotEquals(bad, fresh)
        assertTrue(s.view.state.isGood)
    }

    // ------------------------------------------------------------------------------------------------ routes

    @Test fun wifiFailureFallsBackToBluetoothAndComesBackToWifi() {
        connect()
        val token = phone.driver.credential()
        tv.wifiUp = false                               // the TV's API cannot be reached, Bluetooth still works
        val s = phone.run(40_000)
        assertEquals("degraded", s.view.state.key); assertTrue(s.view.detail.contains("Bluetooth seulement"))
        assertNotNull(phone.driver.credential(), "a credential is held on the fallback route")
        assertIs<LinkPlanner.Route.Bluetooth>(s.session!!.route)
        tv.wifiUp = true
        val back = phone.run(60_000)
        assertEquals("ok:LAN", back.view.state.key, "the faster route is taken again without reopening any screen")
    }

    @Test fun phoneLosesItsWifiIsNamedAsSuch() {
        connect()
        phone.env.network = false; tv.wifiUp = true
        val s = phone.run(40_000)
        assertTrue(s.view.state.isGood, "Bluetooth keeps the link: reduced, not lost")
        // and if Bluetooth dies too, the phone side is what is said
        tv.btRadioOn = false
        phone.run(15_000, Trigger.ACL_DISCONNECTED)
        assertEquals("reconnecting:PHONE_NETWORK", shown().key)
        assertTrue(phone.driver.step().view.detail.contains("Wi-Fi du téléphone"))
    }

    // ------------------------------------------------------------------------------------------------ drops during HELLO

    @Test fun dropAtEveryByteOfTheHelloIsTransientAndNeverForgetsTheTv() {
        connect()
        val token = phone.driver.credential()
        tv.wifiUp = false                                // so every step needs a Bluetooth HELLO
        phone.run(40_000)
        for (cut in listOf(0, 1, 3, 4, 5, 6, 7, 8, 20)) {
            tv.dropReadAfter = cut; tv.dropWriteAfter = null
            phone.run(10_000)
            assertTrue(shown().isGood || shown() is LinkState.Reconnecting, "read cut at $cut: ${shown().key}")
            tv.dropReadAfter = null; tv.dropWriteAfter = cut.coerceAtMost(5)
            phone.run(10_000)
            assertTrue(shown().isGood || shown() is LinkState.Reconnecting, "write cut at ${cut.coerceAtMost(5)}: ${shown().key}")
        }
        tv.dropReadAfter = null; tv.dropWriteAfter = null; tv.wifiUp = true
        phone.run(2 * 60_000)
        assertEquals("ok:LAN", shown().key); assertEquals(tv.phone, tv.reg.verifyToken(phone.driver.credential()))
    }

    // ------------------------------------------------------------------------------------------------ flapping, storms, doze

    @Test fun aTvThatFlapsEveryFewSecondsGivesBoundedAttemptsAndNoBlinking() {
        connect()
        val c0 = tv.connects
        val changes0 = phone.shownHistory.size
        repeat(40) { i -> tv.power = i % 2 == 0; tv.appRunning = tv.power; phone.run(4_000) }
        assertTrue(tv.connects - c0 <= 40, "bounded: ${tv.connects - c0}")
        assertTrue(phone.shownHistory.size - changes0 <= 6, "the card changed ${phone.shownHistory.size - changes0} times: ${phone.shownHistory}")
        assertFalse(phone.shownHistory.contains("forgot:0"))
    }

    @Test fun hundredAttemptsPerMinuteAreImpossibleEvenWithBroadcastStorms() {
        tv.power = false
        val c0 = tv.connects
        val end = clock.now() + 60_000
        var calls = 0
        while (clock.now() < end) { clock.advance(300); phone.driver.step(Trigger.ACL_CONNECTED); calls++ }
        assertTrue(calls > 5)
        assertTrue(tv.connects - c0 <= 12, "connections in a minute: ${tv.connects - c0}")
    }

    @Test fun phoneRebootRebuildsTheStateFromPersistedDataAndReusesTheToken() {
        connect()
        val token = phone.driver.credential()
        val store = phone.store
        // new process: same saved TV and store, no memory
        val saved = phone.saved
        val driver2 = LinkDriver(phone.link, phone.env, saved, store, phone.machine, { 0.5 })
        assertEquals(token, driver2.credential(), "the credential survives a reboot")
        val s = driver2.step(Trigger.APP_OPENED)
        assertIs<LinkState.Reconnecting>(driver2.currentModel!!.shown.let { if (it.isGood) LinkState.Reconnecting(LossSide.TV, it) else it })
        phone.clock.advance(5_000)
        val s2 = driver2.step(Trigger.TIMER)
        assertTrue(s2.view.state.isGood, s2.view.state.key)
    }

    @Test fun backgroundPhoneIsBatteryFriendly() {
        connect(); phone.env.fg = false
        val s = phone.step()
        assertTrue(s.nextInMs!! >= 200_000, "connected in the background: a check every ~5 min, not 15 s: ${s.nextInMs}")
        tv.power = false
        val c0 = tv.connects
        phone.run(60 * 60_000)
        assertTrue(tv.connects - c0 <= 14, "an hour of silence in the background: ${tv.connects - c0} attempts")
    }

    @Test fun twoPhonesLosingTheTvTogetherDoNotComeBackTogether() {
        val delays = (1..8).map { seed ->
            val c = FakeClock(); val t = FakeTv(c); t.reg.trust(t.phone, "x"); val p = Phone(c, t, seed = seed.toLong())
            p.run(5_000); t.power = false; p.run(20_000)
            p.driver.step().nextInMs!!
        }
        assertTrue(delays.toSet().size >= 6, "jitter spreads the retries: $delays")
    }

    @Test fun twoPhonesPairingAtOnceOnlyOneIsAsked() {
        val q = java.util.concurrent.LinkedBlockingQueue<PairingSession.Decision>()
        tv.pairing.open()
        val t1 = kotlin.concurrent.thread { q.put(tv.pairing.ask("AA:BB:CC:DD:EE:01", "A")) }
        while (tv.pairing.asking() == null) Thread.sleep(5)
        assertEquals(PairingSession.Decision.BUSY, tv.pairing.ask("AA:BB:CC:DD:EE:02", "B"))
        tv.pairing.approve(); assertEquals(PairingSession.Decision.APPROVED, q.poll(3, java.util.concurrent.TimeUnit.SECONDS)); t1.join()
        assertTrue(tv.reg.isTrusted("AA:BB:CC:DD:EE:01")); assertFalse(tv.reg.isTrusted("AA:BB:CC:DD:EE:02"))
    }

    companion object { const val PHONE_TOKEN_OK = true }
}
