package castbridge.core

import castbridge.core.trust.*
import castbridge.core.tv.BtProtocol
import kotlin.test.*

/** The pure state machine: states, texts, hysteresis, retry policy. No clock but the numbers passed in. */
class LinkMachineTest {
    private val m = LinkMachine()
    private fun connected(route: RouteKind = RouteKind.LAN, wifi: Boolean = true) = Outcome.Connected(route, "TV du salon", wifi)
    private var t = 100_000L

    private fun feed(model: LinkMachine.Model, vararg outs: Outcome, every: Long = 2_000): LinkMachine.Model {
        var x = model
        for (o in outs) { t += every; x = m.reduce(x, o, t) }
        return x
    }

    private fun good(): LinkMachine.Model = feed(m.initial(true, "TV du salon"), connected())

    // ------------------------------------------------------------------------------------------------ states and texts

    @Test fun everyOutcomeHasAStableFrenchStateWithOneAction() {
        val all: List<Pair<Outcome, LinkState>> = listOf(
            Outcome.NoTv to LinkState.NoTv,
            Outcome.Bluetooth(BtUnavailable.Reason.OFF) to LinkState.BtBlocked(BtUnavailable.Reason.OFF),
            Outcome.Bluetooth(BtUnavailable.Reason.NO_PERMISSION) to LinkState.BtBlocked(BtUnavailable.Reason.NO_PERMISSION),
            Outcome.Bluetooth(BtUnavailable.Reason.NO_ADAPTER) to LinkState.BtBlocked(BtUnavailable.Reason.NO_ADAPTER),
            Outcome.NotBonded to LinkState.NotBonded,
            Outcome.Bonding to LinkState.Bonding,
            connected() to LinkState.Connected(RouteKind.LAN, "TV du salon"),
            connected(RouteKind.DIRECT) to LinkState.Connected(RouteKind.DIRECT, "TV du salon"),
            connected(RouteKind.BLUETOOTH, wifi = false) to LinkState.Connected(RouteKind.BLUETOOTH, "TV du salon"),
            connected(RouteKind.BLUETOOTH, wifi = true) to LinkState.Degraded("TV du salon"),
        )
        for ((o, expected) in all) {
            val x = m.reduce(m.reduce(m.initial(true, "TV du salon").copy(since = 0), o, 1_000_000), o, 1_010_000)
            assertEquals(expected.key, x.shown.key, "outcome $o")
            val v = m.view(x)
            assertTrue(v.title.isNotBlank() && v.detail.isNotBlank(), "text for $o")
            assertEquals(v, m.view(x), "stable")
        }
    }

    @Test fun refusalCodesMapToTheirOwnStatesAndEveryCodeHasAText() {
        fun after(code: Int, hint: Int = 0): LinkMachine.Model { var x = m.initial(true).copy(since = 0); t += 10_000; x = m.reduce(x, Outcome.Refused(code, hint), t); t += 10_000; return m.reduce(x, Outcome.Refused(code, hint), t) }
        assertIs<LinkState.TvForgotMe>(after(BtProtocol.ERR_UNTRUSTED, BtProtocol.HINT_OTHER_INSTALL).shown)
        assertEquals(LinkState.Denied.key, after(BtProtocol.ERR_DENIED).shown.key)
        for (c in listOf(BtProtocol.ERR_NOT_OPEN, BtProtocol.ERR_BUSY, BtProtocol.ERR_TIMEOUT)) assertEquals("owner:$c", after(c).shown.key)
        assertEquals(LinkState.TvTooOld.key, after(BtProtocol.ERR_MAGIC).shown.key)
        for (c in listOf(BtProtocol.ERR_PIN, BtProtocol.ERR_NAME, BtProtocol.ERR_SPACE, BtProtocol.ERR_LOCKED, BtProtocol.ERR_IO, BtProtocol.ERR_SIZE, 99, 255)) assertEquals("tverror:$c", after(c).shown.key)
        // table: no code, known or not, is left without a message, and no message leaks a technical word
        for (c in 0..40) for (h in 0..2) {
            val a = LinkText.refused(c, h)
            assertTrue(a.title.isNotBlank() && a.detail.isNotBlank(), "code $c")
            assertNoTechnicalWords(a.title + " " + a.detail)
        }
        for (c in 1..12) assertTrue(c in LinkText.explicitCodes, "ERR code $c has its own wording")
    }

    @Test fun theStatesTalkLikeThis() {
        fun v(o: Outcome) = m.view(m.reduce(m.reduce(m.initial(true, "Salon").copy(since = 0), o, 1_000_000), o, 1_010_000))
        assertEquals("Bluetooth éteint", v(Outcome.Bluetooth(BtUnavailable.Reason.OFF)).title)
        assertEquals(LinkAction.ENABLE_BLUETOOTH, v(Outcome.Bluetooth(BtUnavailable.Reason.OFF)).action)
        assertEquals(LinkAction.GRANT_PERMISSION, v(Outcome.Bluetooth(BtUnavailable.Reason.NO_PERMISSION)).action)
        assertEquals(LinkAction.PAIR, v(Outcome.NotBonded).action)
        assertEquals("TV du salon connectée", v(connected()).title)
        assertEquals(Tone.GOOD, v(connected()).tone); assertEquals(Tone.WARN, v(connected(RouteKind.BLUETOOTH)).tone)
        val forgot = LinkText.untrustedAdvice(BtProtocol.HINT_OTHER_INSTALL)
        assertEquals("La TV a été réinitialisée ou réinstallée : elle ne vous reconnaît plus.", forgot.detail)
        assertEquals(LinkAction.REASSOCIATE, forgot.action); assertEquals("Réassocier", forgot.action.label)
        assertTrue(LinkText.lost(LossSide.PHONE_BLUETOOTH).detail.contains("Bluetooth du téléphone"))
        assertTrue(LinkText.lost(LossSide.PHONE_NETWORK).detail.contains("Wi-Fi du téléphone"))
        assertTrue(LinkText.lost(LossSide.TV).detail.contains("La TV ne répond plus"))
        assertEquals("Liaison perdue, reconnexion…", LinkText.lost(LossSide.TV).title)
    }

    // ------------------------------------------------------------------------------------------------ hysteresis

    @Test fun oneFailureNeverChangesTheCard() {
        var x = m.initial(true, "TV du salon")
        x = feed(x, Outcome.Absent(AbsentKind.NO_ANSWER))
        assertEquals(LinkState.Connecting.key, x.shown.key, "still 'Connexion…' after one miss")
        x = feed(x, Outcome.Absent(AbsentKind.NO_ANSWER), every = 5_000)
        assertEquals("unreachable:NO_ANSWER", x.shown.key, "two identical misses: shown")
    }

    @Test fun aGoodLinkKeepsItsCardWithAHintThenReconnectingThenUnreachableOnlyAfterTheGrace() {
        var x = good()
        x = feed(x, Outcome.Lost(LossSide.TV))
        assertTrue(x.shown is LinkState.Connected && x.reconnectingHint, "first loss: same card + hint")
        assertEquals("Liaison perdue, reconnexion…", m.view(x).hint)
        x = feed(x, Outcome.Lost(LossSide.TV), every = 3_500)
        assertIs<LinkState.Reconnecting>(x.shown); assertEquals("Liaison perdue, reconnexion…", m.view(x).title)
        x = feed(x, Outcome.Absent(AbsentKind.NO_ANSWER), Outcome.Absent(AbsentKind.NO_ANSWER), Outcome.Absent(AbsentKind.NO_ANSWER), every = 5_000)
        assertIs<LinkState.Reconnecting>(x.shown, "15 s of silence: still reconnecting, never 'introuvable' yet")
        x = feed(x, Outcome.Absent(AbsentKind.NO_ANSWER), Outcome.Absent(AbsentKind.NO_ANSWER), every = 30_000)
        assertIs<LinkState.TvUnreachable>(x.shown, "after the grace period")
    }

    @Test fun recoveryAfterReconnectingIsShownAtOnceButNotBefore3Seconds() {
        var x = feed(good(), Outcome.Lost(LossSide.TV), Outcome.Lost(LossSide.TV), every = 3_500)
        assertIs<LinkState.Reconnecting>(x.shown)
        x = m.reduce(x, connected(), x.since + 500)
        assertIs<LinkState.Reconnecting>(x.shown, "min display time")
        x = m.reduce(x, connected(), x.since + 3_100)
        assertIs<LinkState.Connected>(x.shown)
        assertNull(x.pending)
    }

    @Test fun aLinkThatFlapsEverySecondsNeverBlinks() {
        var x = good(); var changes = 0; var last = x.shown.key
        for (i in 0 until 300) {
            t += 2_000
            x = m.reduce(x, if (i % 2 == 0) Outcome.Lost(LossSide.TV) else connected(), t)
            if (x.shown.key != last) { changes++; last = x.shown.key }
        }
        assertEquals(0, changes, "one failure, one success, one failure...: the card never changes")
        assertTrue(x.shown is LinkState.Connected)
    }

    @Test fun bluetoothSwitchedOffIsSaidAtOnceAndBothSidesAreNamed() {
        val x = m.reduce(good(), Outcome.Bluetooth(BtUnavailable.Reason.OFF), t + 100)
        assertIs<LinkState.BtBlocked>(x.shown)
        val y = feed(good(), Outcome.Lost(LossSide.PHONE_NETWORK), Outcome.Lost(LossSide.PHONE_NETWORK), every = 3_500)
        assertEquals("reconnecting:PHONE_NETWORK", y.shown.key)
    }

    @Test fun threeInstantRefusalsMeanAStaleBond() {
        var x = m.initial(true, "TV du salon")
        x = feed(x, Outcome.Absent(AbsentKind.CLOSED_AT_ONCE), Outcome.Absent(AbsentKind.CLOSED_AT_ONCE), every = 4_000)
        assertNotEquals("stalebond", x.shown.key, "two are not enough")
        x = feed(x, Outcome.Absent(AbsentKind.CLOSED_AT_ONCE), Outcome.Absent(AbsentKind.CLOSED_AT_ONCE), every = 4_000)
        assertEquals(LinkState.StaleBond.key, x.shown.key)
        assertEquals(LinkAction.REMOVE_BOND, m.view(x).action)
        // a slow failure (the TV is simply off) in between resets the count
        var y = feed(m.initial(true), Outcome.Absent(AbsentKind.CLOSED_AT_ONCE), Outcome.Absent(AbsentKind.NO_ANSWER), Outcome.Absent(AbsentKind.CLOSED_AT_ONCE), Outcome.Absent(AbsentKind.CLOSED_AT_ONCE), every = 4_000)
        assertNotEquals("stalebond", y.shown.key)
    }

    @Test fun anExpiredTokenTwiceMeansAccessExpiredButOnceIsSilentRenewal() {
        var x = feed(good(), Outcome.TokenRejected)
        assertTrue(x.shown.isGood, "one rejection: the HELLO that follows fixes it")
        x = feed(x, Outcome.TokenRejected, Outcome.TokenRejected, every = 4_000)
        assertTrue(x.shown is LinkState.CredentialExpired, "refused again after the new HELLO: said, with the retry action")
    }

    // ------------------------------------------------------------------------------------------------ retry policy

    @Test fun neverRetriedByThemselves() {
        for (s in listOf(LinkState.TvForgotMe(0), LinkState.TvForgotMe(1), LinkState.Denied, LinkState.WaitingOwner(11), LinkState.NoTv)) {
            val x = m.initial(true).copy(shown = s)
            assertIs<Retry.Never>(m.nextAttempt(x, true), s.key); assertIs<Retry.Never>(m.nextAttempt(x, false), s.key)
        }
    }

    @Test fun backoffGrowsWithJitterAndIsSlowInTheBackground() {
        val steps = (1..8).map { f -> (m.nextAttempt(m.initial(true).copy(shown = LinkState.TvUnreachable(AbsentKind.NO_ANSWER), failures = f), true, { 0.5 }) as Retry.After).ms }
        assertEquals(listOf(1_500L, 4_000, 8_000, 15_000, 30_000, 60_000, 60_000, 60_000), steps, "foreground: 1.5 s confirmation, then 4 s ... 1 min")
        val bg = (1..7).map { f -> (m.nextAttempt(m.initial(true).copy(shown = LinkState.TvUnreachable(AbsentKind.NO_ANSWER), failures = f), false, { 0.5 }) as Retry.After).ms }
        assertTrue(bg.drop(1).all { it >= 120_000 } && bg.last() == 900_000L, "background: minutes, battery friendly: $bg")
        val lo = (m.nextAttempt(m.initial(true).copy(shown = LinkState.TvUnreachable(AbsentKind.NO_ANSWER), failures = 4), true, { 0.0 }) as Retry.After).ms
        val hi = (m.nextAttempt(m.initial(true).copy(shown = LinkState.TvUnreachable(AbsentKind.NO_ANSWER), failures = 4), true, { 0.999 }) as Retry.After).ms
        assertTrue(lo in 11_000..11_500 && hi in 18_500..19_000, "±25 % jitter: $lo..$hi")
    }

    @Test fun keepAliveIsFastInTheForegroundAndRareInTheBackground() {
        val x = good()
        assertEquals(15_000L, (m.nextAttempt(x, true, { 0.5 }) as Retry.After).ms)
        assertEquals(300_000L, (m.nextAttempt(x, false, { 0.5 }) as Retry.After).ms)
        val failing = feed(x, Outcome.Lost(LossSide.TV))
        assertEquals(1_500L, (m.nextAttempt(failing, true, { 0.5 }) as Retry.After).ms, "a miss is confirmed quickly")
    }

    @Test fun backoffResetsOnlyAfterAStableConnection() {
        var x = m.initial(true)
        repeat(6) { x = feed(x, Outcome.Absent(AbsentKind.NO_ANSWER), every = 20_000) }
        assertEquals(6, x.failures)
        x = feed(x, connected(), every = 1_000)
        assertEquals(6, x.failures, "a single success is not stability")
        x = feed(x, Outcome.Alive, every = 10_000)
        assertEquals(6, x.failures, "10 s")
        x = feed(x, Outcome.Alive, every = 25_000)
        assertEquals(0, x.failures, "35 s of success: reset")
        // a link that comes and goes keeps its long delays
        var y = m.initial(true)
        repeat(5) { y = feed(y, Outcome.Absent(AbsentKind.NO_ANSWER), every = 20_000) }
        repeat(5) { y = feed(y, connected(), every = 1_000); y = feed(y, Outcome.Lost(LossSide.TV), every = 1_000) }
        assertTrue(y.failures >= 5)
    }

    // ------------------------------------------------------------------------------------------------ persistence

    @Test fun stateSurvivesAProcessDeathWithoutRehammeringTheTv() {
        val forgot = feed(m.initial(true, "Salon"), Outcome.Refused(BtProtocol.ERR_UNTRUSTED, BtProtocol.HINT_OTHER_INSTALL), Outcome.Refused(BtProtocol.ERR_UNTRUSTED, BtProtocol.HINT_OTHER_INSTALL), every = 4_000)
        val back = LinkMachine.Model.decode(forgot.encode(), t + 1_000_000)!!
        assertEquals(forgot.shown.key, back.shown.key); assertIs<Retry.Never>(m.nextAttempt(back, true))
        val g = LinkMachine.Model.decode(good().encode(), t + 1_000_000)!!
        assertIs<LinkState.Reconnecting>(g.shown, "after a reboot nobody knows if the link survived")
        assertNull(LinkMachine.Model.decode("garbage", 0)); assertNull(LinkMachine.Model.decode(null, 0))
        assertFalse(good().encode().contains("cbk_"))
    }

    companion object {
        val TECHNICAL = listOf("exception", "ioexception", "socket", "errno", "null", "stack", "java.", "kotlin.", "http ", "timeout", "read ret")
        fun assertNoTechnicalWords(text: String) { val l = text.lowercase(); for (w in TECHNICAL) assertFalse(l.contains(w), "'$w' in: $text") }
    }
}
