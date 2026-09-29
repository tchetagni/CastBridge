package castbridge.core

import castbridge.core.tv.*
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.*

class FakeDevice : Device {
    var vol = 40
    val restarted = CountDownLatch(1)
    override fun sysinfo() = SysInfo("Box \"X\"", "12", "192.168.0.5", null, null, 12345, "0.3")
    override fun volume() = vol
    override fun setVolume(pct: Int) { vol = pct }
    override fun restartApp() = restarted.countDown()
}

class DeviceTest {
    private val dir = kotlin.io.path.createTempDirectory("tvd").toFile()
    private val player = FakePlayer()
    private val dev = FakeDevice()
    private val port = ServerSocket(0).use { it.localPort }
    private val ext = ApiExtension { path, method, _ ->
        if (path == "/api/x-test" && method == "GET") ApiReply(200, """{"x":1}""") else null
    }
    private val server = ReceiverServer(dir, player, port, minFreeBytes = 0, pin = "135790", device = dev, extension = ext)
        .apply { start(5000, false) }
    private val tv = TvClient("http://127.0.0.1:$port", "135790")

    @AfterTest fun tearDown() { server.stop(); dir.deleteRecursively() }

    @Test fun sysinfoVolumeAndRestart() {
        val j = tv.sysinfo()
        assertEquals("Box \"X\"", TvClient.str(j, "model"))
        assertEquals(40, TvClient.num(j, "volume"))
        assertTrue(j.contains("\"battery\":null"))
        tv.setVolume(150)                       // clamped
        assertEquals(100, dev.vol)
        tv.setVolume(-5); assertEquals(0, dev.vol)
        tv.restart()
        assertTrue(dev.restarted.await(3, TimeUnit.SECONDS))
    }

    @Test fun renameFiles() {
        File(dir, "a.mp4").writeText("aaa"); File(dir, "b.mp4").writeText("bbb")
        tv.rename("a.mp4", "c d.mp4")
        assertTrue(File(dir, "c d.mp4").isFile); assertFalse(File(dir, "a.mp4").exists())
        assertEquals(409, assertFailsWith<TvClient.HttpError> { tv.rename("c d.mp4", "b.mp4") }.code)
        assertEquals(400, assertFailsWith<TvClient.HttpError> { tv.rename("c d.mp4", "../x") }.code)
        assertEquals(404, assertFailsWith<TvClient.HttpError> { tv.rename("zzz.mp4", "y.mp4") }.code)
    }

    @Test fun extensionRoutesAreAuthenticated() {
        assertEquals("""{"x":1}""", tv.raw("GET", "/api/x-test"))
        assertEquals(401, assertFailsWith<TvClient.HttpError> { TvClient("http://127.0.0.1:$port").raw("GET", "/api/x-test") }.code)
    }

    @Test fun withoutDeviceReports501() {
        val p2 = ServerSocket(0).use { it.localPort }
        val s2 = ReceiverServer(dir, player, p2, minFreeBytes = 0).apply { start(5000, false) }
        try { assertEquals(501, assertFailsWith<TvClient.HttpError> { TvClient("http://127.0.0.1:$p2").sysinfo() }.code) }
        finally { s2.stop() }
    }
}
