package castbridge.core.remote

import castbridge.core.FakePlayer
import castbridge.core.FakeSink
import castbridge.core.tv.OpenTvReply
import castbridge.core.tv.OpenTvScreen
import castbridge.core.tv.ReceiverServer
import castbridge.core.tv.VolumeRegistry
import java.io.IOException
import java.net.ServerSocket
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class Clock { var t = 0L }

/** A route to the TV: takes [takes] ms of the shared fake clock, then answers (or fails) as scripted. */
private class FakeLink(override val route: OpenTvRoute, private val clock: Clock, private val takes: Long = 0, private val script: () -> OpenTvAnswer) : OpenTvLink {
    var calls = 0; var lastTimeout = -1L; var lastScreen: OpenTvScreen? = null
    override fun open(screen: OpenTvScreen?, timeoutMs: Long): OpenTvAnswer {
        calls++; lastTimeout = timeoutMs; lastScreen = screen
        clock.t += takes
        return script()
    }
}

private fun ok(r: OpenTvReply) = OpenTvAnswer(200, r)
private fun silent(): OpenTvAnswer = throw IOException("TV injoignable")

/**
 * Côté téléphone : « TV connue ? route (Wi-Fi, Bluetooth, tunnel) ? résultat ? » → UNE ligne, au plus 10 s d'attente, jamais de réessai sans fin.
 * Tout est faux ici (liaisons et horloge) : la logique seule.
 */
class OpenTvFlowTest {
    private val clock = Clock()
    private val flow = OpenTvFlow({ clock.t })

    private fun lan(takes: Long = 0, s: () -> OpenTvAnswer) = FakeLink(OpenTvRoute.LAN, clock, takes, s)
    private fun bt(takes: Long = 0, s: () -> OpenTvAnswer) = FakeLink(OpenTvRoute.BLUETOOTH, clock, takes, s)
    private fun tunnel(takes: Long = 0, s: () -> OpenTvAnswer) = FakeLink(OpenTvRoute.TUNNEL, clock, takes, s)

    // ---------------------------------------------------------------- TV connue ? route ?

    @Test fun noTvIsSaidBeforeAnythingIsTried() {
        val l = lan { ok(OpenTvReply(true, "direct", null)) }
        val o = flow.run(tvKnown = false, links = listOf(l))
        assertEquals(OpenTvOutcome.NoTv, o)
        assertEquals(0, l.calls)
        assertTrue(o.problem); assertEquals(OpenTvTexts.NO_TV, o.line)
    }

    @Test fun aKnownTvWithoutAnyRouteIsSaidToo() {
        val o = flow.run(true, emptyList())
        assertEquals(OpenTvOutcome.NoRoute(null), o)
        assertTrue(o.problem); assertEquals(OpenTvTexts.NO_ROUTE, o.line)
    }

    // ---------------------------------------------------------------- ce que la TV répond

    @Test fun openedIsOneLineAndTheOtherRoutesAreLeftAlone() {
        val a = lan { ok(OpenTvReply(true, "direct", null)) }; val b = bt { silent() }
        val o = flow.run(true, listOf(a, b))
        assertEquals(OpenTvOutcome.Opened(OpenTvRoute.LAN, "direct", false), o)
        assertEquals("CastBridge-TV est à l'écran", o.line)
        assertFalse(o.problem)
        assertEquals(1, a.calls); assertEquals(0, b.calls)
    }

    @Test fun alreadyInFrontIsAnOpenedOutcomeThatTheButtonTurnsIntoTheRemote() {
        val o = flow.run(true, listOf(lan { ok(OpenTvReply.already()) }))
        assertEquals(OpenTvOutcome.Opened(OpenTvRoute.LAN, "already", true), o)
        assertTrue((o as OpenTvOutcome.Opened).already)
        assertEquals("CastBridge-TV est à l'écran", o.line)
    }

    @Test fun theTvAsksForTheOverlayPermission() {
        val b = bt { silent() }
        val o = flow.run(true, listOf(lan { ok(OpenTvReply(false, "none", "overlay")) }, b))
        assertEquals(OpenTvOutcome.NeedsPermission(OpenTvRoute.LAN), o)
        assertEquals("La TV demande une autorisation : MENU › Afficher par-dessus", o.line)
        assertEquals(0, b.calls, "the TV answered: asking it again by another way would change nothing")
    }

    @Test fun theTvPostedANotification() {
        val o = flow.run(true, listOf(lan { ok(OpenTvReply(false, "fullscreen", null)) }))
        assertEquals(OpenTvOutcome.Notified(OpenTvRoute.LAN), o)
        assertTrue("notification" in o.line && "CastBridge-TV" in o.line, o.line)
        assertFalse(o.problem)
    }

    @Test fun aTvThatCouldDoNothingSaysSoWithoutBlamingTheNetwork() {
        val o = flow.run(true, listOf(lan { ok(OpenTvReply(false, "none", null)) }))
        assertEquals(OpenTvOutcome.Failed(null), o)
        assertTrue(o.problem); assertTrue("télécommande" in o.line, o.line)
    }

    @Test fun anUnreadableAnswerIsAFailureNotASuccess() {
        val o = flow.run(true, listOf(lan { OpenTvAnswer(200, null) }))
        assertIs<OpenTvOutcome.Failed>(o)
    }

    @Test fun aServerErrorStopsTheSearchWithTheTvsOwnWords() {
        val b = bt { silent() }
        val o = flow.run(true, listOf(lan { OpenTvAnswer(503, null, "L'écran de la TV ne répond pas") }, b))
        assertEquals(OpenTvOutcome.Failed("L'écran de la TV ne répond pas"), o)
        assertTrue("L'écran de la TV ne répond pas" in o.line)
        assertEquals(0, b.calls)
    }

    @Test fun anOldTvDoesNotKnowTheRoute() {
        for (status in listOf(404, 405, 501)) {
            val b = bt { silent() }
            val o = flow.run(true, listOf(lan { OpenTvAnswer(status, null, "not found") }, b))
            assertEquals(OpenTvOutcome.TvTooOld, o, "status $status")
            assertTrue("mettez CastBridge-TV à jour" in o.line, o.line)
            assertEquals(0, b.calls)
        }
    }

    // ---------------------------------------------------------------- les routes, dans l'ordre

    @Test fun routesAreTriedWifiThenBluetoothThenTheTunnelWhateverTheOrderGiven() {
        val order = mutableListOf<OpenTvRoute>()
        fun l(r: OpenTvRoute) = FakeLink(r, clock) { order += r; silent() }
        flow.run(true, listOf(l(OpenTvRoute.TUNNEL), l(OpenTvRoute.BLUETOOTH), l(OpenTvRoute.LAN)))
        assertEquals(listOf(OpenTvRoute.LAN, OpenTvRoute.BLUETOOTH, OpenTvRoute.TUNNEL), order)
    }

    @Test fun aSilentWifiFallsBackToBluetooth() {
        val o = flow.run(true, listOf(lan { silent() }, bt { ok(OpenTvReply(true, "fullscreen", null)) }))
        assertEquals(OpenTvOutcome.Opened(OpenTvRoute.BLUETOOTH, "fullscreen", false), o)
    }

    @Test fun aSilentBluetoothFallsBackToTheTunnel() {
        val o = flow.run(true, listOf(bt { silent() }, tunnel { ok(OpenTvReply(true, "accessibility", null)) }))
        assertEquals(OpenTvOutcome.Opened(OpenTvRoute.TUNNEL, "accessibility", false), o)
    }

    @Test fun aRouteListedTwiceIsTriedOnce() {
        val a = lan { silent() }; val b = lan { silent() }
        flow.run(true, listOf(a, b))
        assertEquals(1, a.calls + b.calls)
    }

    @Test fun theScreenIsPassedToEveryRoute() {
        val a = lan { silent() }; val b = bt { ok(OpenTvReply(true, "direct", null)) }
        flow.run(true, listOf(a, b), OpenTvScreen.QUIZ)
        assertEquals(OpenTvScreen.QUIZ, a.lastScreen); assertEquals(OpenTvScreen.QUIZ, b.lastScreen)
    }

    // ---------------------------------------------------------------- la TV ne répond pas : une phrase, pas de boucle

    @Test fun everyRouteSilentMeansTheTvIsOffAndEachRouteWasTriedExactlyOnce() {
        val ls = listOf(lan { silent() }, bt { silent() }, tunnel { silent() })
        val o = flow.run(true, ls)
        assertEquals(OpenTvOutcome.Unreachable(), o)
        assertEquals("La TV ne répond pas : allumez-la (le Wi-Fi ou le Bluetooth de la TV est éteint)", o.line)
        assertTrue(o.problem)
        assertEquals(listOf(1, 1, 1), ls.map { it.calls }, "no retry, ever")
    }

    @Test fun theWholeWaitIsAtMostTenSecondsAndEachRouteGetsWhatIsLeft() {
        val a = lan(takes = 5_000) { silent() }; val b = bt(takes = 3_000) { silent() }; val c = tunnel(takes = 1_000) { silent() }
        val o = flow.run(true, listOf(a, b, c))
        assertEquals(OpenTvOutcome.Unreachable(), o)
        assertEquals(OpenTvRoute.LAN.capMs.coerceAtMost(10_000), a.lastTimeout, "the first route gets its cap")
        assertEquals(5_000L, b.lastTimeout, "5 s are gone: Bluetooth gets what is left (under its own cap)")
        assertEquals(2_000L, c.lastTimeout)
        assertTrue(clock.t <= 10_000, "elapsed ${clock.t} ms")
    }

    @Test fun aRouteIsNotStartedWithAFewHundredMillisecondsLeft() {
        val a = lan(takes = 9_800) { silent() }; val b = bt { ok(OpenTvReply(true, "direct", null)) }
        val o = flow.run(true, listOf(a, b))
        assertEquals(OpenTvOutcome.Unreachable(), o)
        assertEquals(0, b.calls, "200 ms cannot open an RFCOMM link: saying « la TV ne répond pas » now is the honest answer")
    }

    @Test fun noRouteEverGetsMoreThanItsCapNorMoreThanTheBudget() {
        val a = lan { silent() }; val b = bt { silent() }; val c = tunnel { silent() }
        flow.run(true, listOf(a, b, c))
        assertTrue(a.lastTimeout in 1..OpenTvRoute.LAN.capMs && a.lastTimeout <= OpenTvFlow.BUDGET_MS)
        assertTrue(b.lastTimeout in 1..OpenTvFlow.BUDGET_MS); assertTrue(c.lastTimeout in 1..OpenTvFlow.BUDGET_MS)
        assertEquals(10_000L, OpenTvFlow.BUDGET_MS)
    }

    // ---------------------------------------------------------------- le téléphone et la TV ne se reconnaissent pas

    @Test fun aRefusedCredentialOnWifiStillGetsTheBluetoothTrustedWay() {
        val o = flow.run(true, listOf(lan { OpenTvAnswer(401, null, "bad token") }, bt { ok(OpenTvReply(true, "direct", null)) }))
        assertEquals(OpenTvOutcome.Opened(OpenTvRoute.BLUETOOTH, "direct", false), o)
    }

    @Test fun aRefusedCredentialEverywhereIsSaidAsSuchNotAsAnOffTv() {
        val o = flow.run(true, listOf(lan { OpenTvAnswer(401, null, "bad pin") }, bt { silent() }))
        assertEquals(OpenTvOutcome.Refused, o)
        assertTrue(o.problem); assertTrue("réassoci" in o.line || "code" in o.line, o.line)
        val o2 = flow.run(true, listOf(lan { OpenTvAnswer(403, null, "pin required") }))
        assertEquals(OpenTvOutcome.Refused, o2)
    }

    @Test fun aRefusalDoesNotMakeTheFlowRetrySameRoute() {
        val a = lan { OpenTvAnswer(401, null, "bad pin") }
        flow.run(true, listOf(a))
        assertEquals(1, a.calls, "a loop of wrong codes would lock the TV for a minute")
    }

    // ---------------------------------------------------------------- le téléphone lui-même ne peut pas

    @Test fun aPhoneSideProblemIsSaidWhenNothingElseCouldBeTried() {
        val o = flow.run(true, listOf(bt { throw OpenTvLink.Unusable("Le Bluetooth du téléphone est éteint") }))
        assertEquals(OpenTvOutcome.NoRoute("Le Bluetooth du téléphone est éteint"), o)
        assertTrue("Le Bluetooth du téléphone est éteint" in o.line, o.line)
        assertFalse("allumez-la" in o.line, "the TV is not the one to blame here")
    }

    @Test fun aSilentTvBeatsAPhoneSideProblemInTheAnswerButTheLineSaysBoth() {
        val hint = "Le Bluetooth du téléphone est éteint : allumez-le pour joindre la TV"
        val o = flow.run(true, listOf(lan { silent() }, bt { throw OpenTvLink.Unusable(hint) }))
        assertEquals(OpenTvOutcome.Unreachable(hint), o, "the TV really did not answer on the way that worked")
        assertEquals(OpenTvTexts.UNREACHABLE + ". " + hint, o.line, "not everything is put on the TV's back")
        assertTrue(o.problem)
        assertEquals(OpenTvTexts.UNREACHABLE, OpenTvOutcome.Unreachable("  ").line, "a blank hint adds nothing")
        assertEquals(OpenTvTexts.UNREACHABLE, OpenTvOutcome.Unreachable(null).line)
    }

    @Test fun aRefusalBeatsEverythingElse() {
        val o = flow.run(true, listOf(lan { OpenTvAnswer(401, null, "bad token") }, bt { throw OpenTvLink.Unusable("Bluetooth éteint") }))
        assertEquals(OpenTvOutcome.Refused, o)
    }

    // ---------------------------------------------------------------- quelles voies bâtir : la TV ne se verrouille jamais à cause de nous

    private val token = "cbk_" + "0".repeat(64)

    @Test fun aTrustedTvGetsEveryRouteTheTelephoneHas() {
        val c = OpenTvRoutes.choose(trusted = true, credential = token, hasLan = true, hasBluetooth = true, hasTunnel = true)
        assertEquals(listOf(OpenTvRoute.LAN, OpenTvRoute.BLUETOOTH, OpenTvRoute.TUNNEL), c.routes)
        assertFalse(c.needsCode)
    }

    @Test fun aTrustedTvWithoutAnyUsableCredentialIsStillReachedByBluetoothWhichNeedsNone() {
        for (cred in listOf(null, "", "12", "cbk_short", "12345a")) {
            val c = OpenTvRoutes.choose(trusted = true, credential = cred, hasLan = true, hasBluetooth = true, hasTunnel = true)
            assertEquals(listOf(OpenTvRoute.BLUETOOTH), c.routes, "credential « $cred »: nothing unusable is ever sent over IP")
            assertFalse(c.needsCode)
        }
    }

    @Test fun aTvKnownByItsCodeNeedsTheCodeBeforeAnyContact() {
        for (cred in listOf(null, "", "12", "cbk_short", "12345a", "1234567")) {
            val c = OpenTvRoutes.choose(trusted = false, credential = cred, hasLan = true, hasBluetooth = true, hasTunnel = true)
            assertEquals(OpenTvRoutes.Choice(emptyList(), needsCode = true), c, "credential « $cred »: not even a Bluetooth handshake (a wrong code locks the TV for a minute)")
        }
    }

    @Test fun aTvKnownByItsCodeGetsTheRoutesItHas() {
        assertEquals(listOf(OpenTvRoute.LAN, OpenTvRoute.BLUETOOTH, OpenTvRoute.TUNNEL), OpenTvRoutes.choose(false, "482913", hasLan = true, hasBluetooth = true, hasTunnel = true).routes)
        assertEquals(listOf(OpenTvRoute.BLUETOOTH), OpenTvRoutes.choose(false, "482913", hasLan = false, hasBluetooth = true, hasTunnel = false).routes)
        assertEquals(listOf(OpenTvRoute.LAN), OpenTvRoutes.choose(false, token, hasLan = true, hasBluetooth = false, hasTunnel = false).routes)
        assertEquals(emptyList(), OpenTvRoutes.choose(true, token, hasLan = false, hasBluetooth = false, hasTunnel = false).routes, "a trusted TV without any address: the flow then says so")
    }

    // ---------------------------------------------------------------- les mots

    @Test fun everyLineIsOneShortSentenceInFrenchWithTheRightNames() {
        val all = listOf(OpenTvOutcome.NoTv, OpenTvOutcome.NoRoute(null), OpenTvOutcome.NoRoute("Bluetooth éteint"), OpenTvOutcome.Opened(OpenTvRoute.LAN, "direct", false),
            OpenTvOutcome.Opened(OpenTvRoute.LAN, "already", true), OpenTvOutcome.NeedsPermission(OpenTvRoute.BLUETOOTH), OpenTvOutcome.Notified(OpenTvRoute.LAN),
            OpenTvOutcome.Failed(null), OpenTvOutcome.Failed("x"), OpenTvOutcome.TvTooOld, OpenTvOutcome.Refused, OpenTvOutcome.Unreachable())
        for (o in all) {
            assertTrue(o.line.isNotBlank() && o.line.length <= 130, "${o.line.length}: ${o.line}")
            assertFalse(o.line.contains('\n'), o.line)
            for (bad in listOf("sender", "receiver", "Sender", "Receiver", "récepteur")) assertFalse(bad in o.line, "${o.line} says « $bad »")
        }
        for (t in listOf(OpenTvTexts.BUTTON, OpenTvTexts.KEY, OpenTvTexts.SHORTCUT, OpenTvTexts.WORKING, OpenTvTexts.KEY_DESCRIPTION)) assertFalse("sender" in t.lowercase() || "receiver" in t.lowercase(), t)
        assertEquals("Ouvrir sur la TV", OpenTvTexts.BUTTON)
        assertEquals("TV", OpenTvTexts.KEY)
        assertEquals("Ouvrir CastBridge-TV", OpenTvTexts.SHORTCUT)
    }

    @Test fun theLongestEverWaitDoesNotChangeTheLine() {
        // the same input, the same line: the UI can show it as many times as it likes
        assertEquals(flow.run(true, emptyList()).line, flow.run(true, emptyList()).line)
    }

    @Test fun onlyAnOpenedOutcomeAnswersAlreadyTrue() {
        assertTrue(OpenTvOutcome.Opened(OpenTvRoute.LAN, "already", true).alreadyFront)
        assertFalse(OpenTvOutcome.Opened(OpenTvRoute.LAN, "direct", false).alreadyFront)
        assertFalse(OpenTvOutcome.Unreachable().alreadyFront)
        assertFalse(OpenTvOutcome.NeedsPermission(OpenTvRoute.LAN).alreadyFront)
    }
}

/** The wire: what the phone sends (query of the CBTR line, path of the HTTP route) and how it reads any answer. */
class OpenTvWireTest {
    @Test fun theQueryNamesTheScreenOrNothing() {
        assertEquals("", OpenTvWire.query(null))
        assertEquals("screen=library", OpenTvWire.query(OpenTvScreen.LIBRARY))
        assertEquals("/api/tv/open", OpenTvWire.PATH)
        assertEquals("/api/tv/open?screen=quiz", OpenTvWire.path(OpenTvScreen.QUIZ))
        assertEquals("/api/tv/open", OpenTvWire.path(null))
        assertEquals("open", OpenTvWire.BT_ROUTE)
    }

    @Test fun answersAreReadWhateverTheStatus() {
        assertEquals(OpenTvAnswer(200, OpenTvReply(true, "direct", null)), OpenTvWire.answer(200, """{"opened":true,"how":"direct","needs":null}"""))
        assertEquals(OpenTvAnswer(200, null), OpenTvWire.answer(200, "<html>"))
        val refused = OpenTvWire.answer(401, """{"error":"bad pin"}""")
        assertEquals(401, refused.status); assertNull(refused.reply); assertEquals("bad pin", refused.message)
        assertEquals("Hôte non autorisé", OpenTvWire.answer(403, """{"error":"Hôte non autorisé"}""").message)
        assertEquals("L'écran ne répond pas", OpenTvWire.answer(503, """{"ok":false,"error":"x","message":"L'écran ne répond pas"}""").message, "message wins over error")
        assertNull(OpenTvWire.answer(500, "boom").message)
    }
}

/** The Wi-Fi / tunnel link against the real TV server: PIN or token, bounded, never a hang. */
class OpenTvHttpLinkTest {
    private val dir = kotlin.io.path.createTempDirectory("opentvlink").toFile()
    private val asked = java.util.concurrent.CopyOnWriteArrayList<OpenTvScreen?>()
    private val sink = object : RemoteSink by FakeSink() {
        override fun openTv(screen: OpenTvScreen?): OpenTvReply? { asked += screen; return OpenTvReply(true, "direct", null) }
    }
    private val server = ReceiverServer(VolumeRegistry.single(java.io.File(dir, "tv")), FakePlayer(), 0, pin = "123456",
        extension = RemoteApi(sink), tokenAuth = { if (it == "cbk_" + "0".repeat(64)) "Mon téléphone" else null }).apply { start(5000, false) }
    private val base = "http://127.0.0.1:${server.listeningPort}"

    @AfterTest fun tearDown() { server.stop(); dir.deleteRecursively() }

    @Test fun openWithThePin() {
        val a = OpenTvHttpLink(OpenTvRoute.LAN, base, "123456").open(null, 5_000)
        assertEquals(OpenTvAnswer(200, OpenTvReply(true, "direct", null)), a)
        assertEquals(listOf<OpenTvScreen?>(null), asked.toList())
    }

    @Test fun openWithATrustedPhoneTokenAndAScreen() {
        val a = OpenTvHttpLink(OpenTvRoute.TUNNEL, base, "cbk_" + "0".repeat(64)).open(OpenTvScreen.GAMES, 5_000)
        assertEquals(200, a.status); assertEquals(OpenTvScreen.GAMES, asked.single())
        assertEquals(OpenTvRoute.TUNNEL, OpenTvHttpLink(OpenTvRoute.TUNNEL, base, null).route)
    }

    @Test fun aWrongPinIsAnAnswerNotAnException() {
        val a = OpenTvHttpLink(OpenTvRoute.LAN, base, "000000").open(null, 5_000)
        assertEquals(401, a.status); assertNull(a.reply)
        assertTrue(asked.isEmpty())
    }

    @Test fun aCredentialThatCannotBeUsedNeverLeavesThePhone() {
        // too short: the TV would count it as a wrong PIN and lock the whole phone out
        val a = OpenTvHttpLink(OpenTvRoute.LAN, base, "12").open(null, 5_000)
        assertEquals(401, a.status)
        assertTrue(asked.isEmpty())
    }

    @Test fun anUnusableCredentialDoesNotEvenOpenAConnection() {
        ServerSocket(0).use { s ->
            s.soTimeout = 400
            val a = OpenTvHttpLink(OpenTvRoute.LAN, "http://127.0.0.1:${s.localPort}", "12").open(null, 2_000)
            assertEquals(401, a.status)
            assertNull(runCatching { s.accept().also { it.close() } }.getOrNull(), "nothing was sent, not even a connection: the TV cannot count it as a wrong code")
        }
    }

    @Test fun aTvThatIsNotThereFailsFastWithAnIoException() {
        val closed = ServerSocket(0).use { it.localPort }
        val t0 = System.nanoTime()
        assertFailsWith<IOException> { OpenTvHttpLink(OpenTvRoute.LAN, "http://127.0.0.1:$closed", "123456").open(null, 5_000) }
        assertTrue((System.nanoTime() - t0) / 1_000_000 < 3_000)
    }

    @Test fun aTvThatAcceptsButNeverAnswersIsGivenUpOnAtTheTimeout() {
        ServerSocket(0).use { mute ->
            val t = Thread { runCatching { mute.accept().use { Thread.sleep(5_000) } } }.apply { isDaemon = true; start() }
            val t0 = System.nanoTime()
            assertFailsWith<IOException> { OpenTvHttpLink(OpenTvRoute.LAN, "http://127.0.0.1:${mute.localPort}", "123456").open(null, 700) }
            val ms = (System.nanoTime() - t0) / 1_000_000
            assertTrue(ms in 500..2_500, "gave up after $ms ms")
            t.interrupt()
        }
    }
}
