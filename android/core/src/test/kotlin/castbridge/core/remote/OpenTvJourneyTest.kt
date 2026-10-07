package castbridge.core.remote

import castbridge.core.FakePlayer
import castbridge.core.FakeSink
import castbridge.core.tv.OpenTvPlan
import castbridge.core.tv.OpenTvPlan.Way
import castbridge.core.tv.OpenTvReply
import castbridge.core.tv.OpenTvScreen
import castbridge.core.tv.ReceiverServer
import castbridge.core.tv.VolumeRegistry
import java.io.File
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** What the TV's Android would do, scripted: which ways exist ([state]) and which one finally brings CastBridge-TV up ([opensWith]). The plan itself is the real one. */
private class ScriptedTv(var state: OpenTvPlan.State, var opensWith: Way? = null) : RemoteSink by FakeSink() {
    val log = CopyOnWriteArrayList<String>()
    override fun openTv(screen: OpenTvScreen?): OpenTvReply? = OpenTvPlan.execute(state, screen, object : OpenTvPlan.Actions {
        private var last: Way? = null
        override fun start(way: Way, screen: OpenTvScreen?, fullScreenIntent: Boolean): Boolean { log += "${way.wire}:${screen?.wire ?: "-"}"; last = way; return true }
        override fun awaitFront(maxMs: Long) = last != null && last == opensWith
        override fun withdraw() { log += "withdraw" }
    })
}

private fun tvState(front: Boolean = false, overlay: Boolean = false, overlayScreen: Boolean = true, notifications: Boolean = true, accessibility: Boolean = false) =
    OpenTvPlan.State(front, 34, overlay, overlayScreen, fullScreenAllowed = true, notificationsAllowed = notifications, accessibilityActive = accessibility)

/**
 * Le geste de bout en bout, sans Android : `OpenTvFlow` du téléphone → liaison Wi-Fi ou Bluetooth (CBTR) → vrai serveur de la TV (PIN, jeton) → `RemoteApi` → la vraie règle
 * `OpenTvPlan` avec une TV scriptée. Ce que la TV répond et ce que le téléphone en lit se rejoignent ici (parcours P-89, partie JVM).
 */
class OpenTvJourneyTest {
    private val token = "cbk_" + "0".repeat(64)
    private val dir = kotlin.io.path.createTempDirectory("opentvjourney").toFile()
    private val tv = ScriptedTv(tvState())
    private val server = ReceiverServer(VolumeRegistry.single(File(dir, "tv")), FakePlayer(), 0, pin = "123456", extension = RemoteApi(tv),
        tokenAuth = { if (it == token) "Mon téléphone" else null }).apply { start(5000, false) }
    private val base = "http://127.0.0.1:${server.listeningPort}"

    @AfterTest fun tearDown() { server.stop(); dir.deleteRecursively() }

    private fun wifi(credential: String? = "123456") = OpenTvHttpLink(OpenTvRoute.LAN, base, credential)
    private fun run(vararg links: OpenTvLink, screen: OpenTvScreen? = null) = OpenTvFlow().run(true, links.toList(), screen)

    /** The Bluetooth channel over in-memory pipes: the TV's `RemoteBt.serve` on one end, the phone's `OpenTvWire.send` on the other. */
    private fun bluetooth(): Pair<OpenTvLink, () -> Unit> {
        val c2s = PipedOutputStream(); val tvIn = PipedInputStream(c2s, 1 shl 16)
        val s2c = PipedOutputStream(); val clIn = PipedInputStream(s2c, 1 shl 16)
        Thread { runCatching { RemoteBt.serve(tvIn, s2c, RemoteApi(tv)) }; runCatching { s2c.close() } }.apply { isDaemon = true; start() }
        val t = RemoteBt.Transport(clIn, c2s) { c2s.close() }
        val link = object : OpenTvLink {
            override val route = OpenTvRoute.BLUETOOTH
            override fun open(screen: OpenTvScreen?, timeoutMs: Long) = OpenTvWire.send(t, screen)
        }
        return link to { t.close() }
    }

    @Test fun youTubeIsInFrontThenOneGestureAndCastBridgeTvIsInFront() {
        tv.state = tvState(overlay = true); tv.opensWith = Way.DIRECT
        val o = run(wifi())
        assertEquals(OpenTvOutcome.Opened(OpenTvRoute.LAN, "direct", false), o)
        assertEquals("CastBridge-TV est à l'écran", o.line)
        assertEquals(listOf("direct:-"), tv.log, "one start, and no notification once the screen is up")
    }

    @Test fun aTrustedPhoneTokenDoesTheSame() {
        tv.state = tvState(overlay = true); tv.opensWith = Way.DIRECT
        assertEquals(OpenTvOutcome.Opened(OpenTvRoute.LAN, "direct", false), run(wifi(token)))
    }

    @Test fun castBridgeTvAlreadyInFrontNothingIsTouchedAndTheButtonOpensTheRemote() {
        tv.state = tvState(front = true, overlay = true)
        val o = run(wifi())
        assertEquals(OpenTvOutcome.Opened(OpenTvRoute.LAN, "already", true), o)
        assertTrue(o.alreadyFront)
        assertEquals(emptyList<String>(), tv.log.toList(), "a playing video is never interrupted")
    }

    @Test fun theNotificationOpensItWhenTheDirectStartIsBlocked() {
        tv.state = tvState(overlay = true); tv.opensWith = Way.FULLSCREEN
        val o = run(wifi())
        assertEquals(OpenTvOutcome.Opened(OpenTvRoute.LAN, "fullscreen", false), o)
        assertEquals(listOf("direct:-", "fullscreen:-", "withdraw"), tv.log.toList())
    }

    @Test fun firstTimeNothingWorksAndThePhoneTellsWhereTheAuthorizationIs() {
        tv.state = tvState(overlay = false, overlayScreen = true); tv.opensWith = null
        val o = run(wifi())
        assertEquals(OpenTvOutcome.NeedsPermission(OpenTvRoute.LAN), o)
        assertEquals("La TV demande une autorisation : MENU › Afficher par-dessus", o.line)
        assertEquals(listOf("fullscreen:-"), tv.log.toList(), "the notification stays: the viewer can still open it with the TV's remote")
    }

    @Test fun aBoxWithoutTheOverlayScreenOnlyHasTheNotification() {
        tv.state = tvState(overlayScreen = false); tv.opensWith = null
        val o = run(wifi())
        assertEquals(OpenTvOutcome.Notified(OpenTvRoute.LAN), o)
        assertTrue("notification" in o.line)
    }

    @Test fun aTvThatCanDoNothingSaysSo() {
        tv.state = tvState(notifications = false, overlayScreen = false)
        val o = run(wifi())
        assertEquals(OpenTvOutcome.Failed(null), o)
        assertEquals(emptyList<String>(), tv.log.toList())
    }

    @Test fun theRequestedScreenReachesEveryAttemptOfTheTv() {
        tv.state = tvState(overlay = true, accessibility = true); tv.opensWith = Way.ACCESSIBILITY
        val o = run(wifi(), screen = OpenTvScreen.QUIZ)
        assertEquals(OpenTvOutcome.Opened(OpenTvRoute.LAN, "accessibility", false), o)
        assertEquals(listOf("direct:quiz", "fullscreen:quiz", "accessibility:quiz", "withdraw"), tv.log.toList())
    }

    @Test fun aTvThatIsOffIsOneLineAndFast() {
        server.stop()
        val t0 = System.nanoTime()
        val o = run(wifi())
        assertEquals(OpenTvOutcome.Unreachable(), o)
        assertEquals("La TV ne répond pas : allumez-la (le Wi-Fi ou le Bluetooth de la TV est éteint)", o.line)
        assertTrue((System.nanoTime() - t0) / 1_000_000 < OpenTvFlow.BUDGET_MS, "well inside the ten seconds")
    }

    @Test fun aWrongCodeIsSaidAsSuchNotAsAnOffTv() {
        val o = run(wifi("000000"))
        assertEquals(OpenTvOutcome.Refused, o)
        assertTrue(tv.log.isEmpty())
    }

    @Test fun aSilentWifiFallsBackToTheBluetoothChannelWhichKnowsTheTrustedPhone() {
        tv.state = tvState(overlay = true); tv.opensWith = Way.DIRECT
        val closed = ServerSocket(0).use { it.localPort }
        val (bt, close) = bluetooth()
        val o = run(OpenTvHttpLink(OpenTvRoute.LAN, "http://127.0.0.1:$closed", "123456"), bt)
        close()
        assertEquals(OpenTvOutcome.Opened(OpenTvRoute.BLUETOOTH, "direct", false), o)
        assertEquals(listOf("direct:-"), tv.log.toList())
    }

    @Test fun theBluetoothChannelCarriesTheScreenAndTheTvsAnswerBack() {
        tv.state = tvState(overlay = false, overlayScreen = true); tv.opensWith = null
        val (bt, close) = bluetooth()
        val o = run(bt, screen = OpenTvScreen.LIBRARY)
        close()
        assertEquals(OpenTvOutcome.NeedsPermission(OpenTvRoute.BLUETOOTH), o)
        assertEquals(listOf("fullscreen:library"), tv.log.toList())
    }

    @Test fun anOldTvWithoutTheRouteIsSaidToNeedAnUpdate() {
        // a TV that predates the route: its API answers 404 « not found » to /api/tv/open
        val old = ReceiverServer(VolumeRegistry.single(File(dir, "old")), FakePlayer(), 0, pin = "123456", extension = castbridge.core.tv.ApiExtension { _, _, _ -> null }).apply { start(5000, false) }
        try {
            val o = run(OpenTvHttpLink(OpenTvRoute.LAN, "http://127.0.0.1:${old.listeningPort}", "123456"))
            assertEquals(OpenTvOutcome.TvTooOld, o)
        } finally { old.stop() }
    }
}
