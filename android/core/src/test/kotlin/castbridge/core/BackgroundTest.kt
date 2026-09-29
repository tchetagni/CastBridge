package castbridge.core

import castbridge.core.tv.*
import java.io.File
import java.net.ServerSocket
import kotlin.test.*

class BackgroundPolicyTest {
    @Test fun bootStartsOnlyWhenEnabledAndForKnownActions() {
        assertTrue(BootPolicy.shouldStart(BootPolicy.BOOT, true))
        assertTrue(BootPolicy.shouldStart(BootPolicy.QUICKBOOT, true))
        assertTrue(BootPolicy.shouldStart(BootPolicy.REPLACED, true), "restart after a Wi-Fi update")
        assertFalse(BootPolicy.shouldStart(BootPolicy.BOOT, false), "« Démarrer avec la TV » off")
        assertFalse(BootPolicy.shouldStart("android.intent.action.SCREEN_ON", true)); assertFalse(BootPolicy.shouldStart(null, true))
    }

    private fun s(visible: Boolean = false, overlay: Boolean = false, fs: Boolean = false, notif: Boolean = true, sdk: Int = 34) =
        LaunchPolicy.State(visible, overlay, fs, notif, sdk)

    @Test fun howTheScreenIsBroughtUp() {
        assertEquals(listOf(LaunchPolicy.Way.DIRECT), LaunchPolicy.ways(s(visible = true)))
        assertEquals(listOf(LaunchPolicy.Way.START_ACTIVITY), LaunchPolicy.ways(s(overlay = true)), "display-over-apps is exempt")
        assertEquals(listOf(LaunchPolicy.Way.START_ACTIVITY), LaunchPolicy.ways(s(sdk = 28)), "no restriction before Android 10")
        assertEquals(listOf(LaunchPolicy.Way.FULL_SCREEN_NOTIFICATION, LaunchPolicy.Way.NOTIFICATION), LaunchPolicy.ways(s(fs = true)))
        assertEquals(listOf(LaunchPolicy.Way.NOTIFICATION), LaunchPolicy.ways(s()))
        assertEquals(emptyList(), LaunchPolicy.ways(s(notif = false, fs = true)))
        assertTrue(LaunchPolicy.needsSomeone(LaunchPolicy.ways(s()))); assertFalse(LaunchPolicy.needsSomeone(LaunchPolicy.ways(s(overlay = true))))
        assertTrue(LaunchPolicy.message(LaunchPolicy.ways(s())).contains("notification"))
        assertTrue(LaunchPolicy.message(emptyList()).startsWith("Ouvrez l'app CastBridge TV"))
    }

    @Test fun pendingRequestsExpire() {
        assertTrue(LaunchPolicy.pendingValid(1_000, 60_000)); assertFalse(LaunchPolicy.pendingValid(1_000, 1_000 + 121_000))
        assertFalse(LaunchPolicy.pendingValid(10_000, 5_000), "clock went back")
    }
}

class NeedsForegroundTest {
    private val dir = kotlin.io.path.createTempDirectory("nf").toFile()
    private val port = ServerSocket(0).use { it.localPort }
    private val player = object : Player {
        override fun play(file: File, posMs: Long) = throw NeedsForeground("Ouvrez l'app CastBridge TV sur la TV")
        override fun playStream(url: String, name: String, posMs: Long) = throw NeedsForeground("x")
        override fun pause() {}
        override fun resume() {}
        override fun seek(posMs: Long) {}
        override fun stop() {}
        override fun state() = PlayerState()
    }
    private val server = ReceiverServer(VolumeRegistry.single(dir), player, port, pin = "123456").apply { start(5000, false) }
    @AfterTest fun tearDown() { server.stop(); dir.deleteRecursively() }

    @Test fun playWhileInTheBackgroundAnswersClearly() {
        File(dir, "a.mp4").writeBytes(ByteArray(3))
        val e = assertFailsWith<TvClient.HttpError> { TvClient("http://127.0.0.1:$port", "123456").play("a.mp4") }
        assertEquals(409, e.code); assertTrue(e.message!!.contains("\"needsForeground\":true") && e.message!!.contains("Ouvrez l'app"), e.message)
    }

    @Test fun transfersAreCounted() {
        assertEquals(0, server.activeTransfers())
        TvClient("http://127.0.0.1:$port", "123456").upload("x.bin", 0, 3, byteArrayOf(1, 2, 3).inputStream()) { }
        assertEquals(0, server.activeTransfers(), "back to zero once the upload returned")
    }
}
