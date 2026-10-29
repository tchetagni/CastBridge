package castbridge.core.tv

import castbridge.core.FakePlayer
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.random.Random
import kotlin.test.*

/** `POST /api/playnext` through the real HTTP server: the mark, the end of the copy, the queue, the child profile, the trial. */
class PlayNextServerTest {
    private val dir = kotlin.io.path.createTempDirectory("playnext").toFile()
    private val player = FakePlayer()
    private val notices = java.util.Collections.synchronizedList(ArrayList<String>())
    private var childActive = false
    private val flags = object : ContentFlags {
        override fun childActive() = childActive
        override fun protectedNames(items: List<LibraryItem>) = emptySet<String>()
    }
    private val registry = VolumeRegistry(StaticVolumes { listOf(StorageVolume("internal", "Mémoire interne", dir, VolumeKind.INTERNAL, Fs.UNKNOWN, 0, 0, false)) }).also { it.refresh() }
    private val server = ReceiverServer(registry, player, 0, profile = TvProfile(minFreeBytes = 0, minFreeAfterTransfer = 0, target = "internal"), onNotice = { notices += it },
        contentFlags = flags, hostCheck = false).apply { start(5000, false) }
    private val base = "http://127.0.0.1:${server.listeningPort}"
    private val tv = TvClient(base)
    private val data = Random(3).nextBytes(200_000)

    @AfterTest fun tearDown() { server.stop(); dir.deleteRecursively() }

    private fun call(method: String, path: String): Pair<Int, String> {
        val c = URL(base + path).openConnection() as HttpURLConnection
        c.requestMethod = method; c.setRequestProperty("Connection", "close"); c.connectTimeout = 5000; c.readTimeout = 10_000
        if (method == "POST") { c.doOutput = true; c.setFixedLengthStreamingMode(0); c.outputStream.close() }
        val code = c.responseCode
        return code to ((if (code < 400) c.inputStream else c.errorStream)?.readBytes()?.decodeToString() ?: "")
    }
    private fun mark(name: String, size: Long = data.size.toLong()) = call("POST", "/api/playnext?name=${TvClient.enc(name)}&size=$size")
    private fun send(name: String) = tv.upload(name, 0, data.size.toLong(), ByteArrayInputStream(data)) {}
    private fun playlistItems(): List<String> = Regex("\"items\":\\[(.*?)]").find(call("GET", "/api/info").second)?.groupValues?.get(1).orEmpty()
        .split(',').filter { it.isNotBlank() }.map { it.trim().trim('"') }

    @Test fun onlyPostIsAnswered() {
        assertEquals(405, call("GET", "/api/playnext?name=a.mp4&size=10").first)
    }

    @Test fun anInvalidNameOrMissingSizeIsRefused() {
        assertEquals(400, call("POST", "/api/playnext?size=10").first)
        assertEquals(400, call("POST", "/api/playnext?name=..%2Fx.mp4&size=10").first)
        assertEquals(400, call("POST", "/api/playnext?name=a.mp4").first)
        assertEquals(400, call("POST", "/api/playnext?name=a.mp4&size=0").first)
    }

    @Test fun nothingPlaysTheCopyStartsAtTheEndOfTheReception() {
        val (c, b) = mark("a.mp4"); assertEquals(200, c); assertTrue("\"pending\":true" in b, b)
        assertEquals("idle", player.st.state)
        assertTrue(send("a.mp4").done)
        assertEquals("playing", player.st.state); assertEquals("a.mp4", player.st.name)
    }

    @Test fun somethingPlaysTheCopyIsQueuedAfterItAndPlayedWhenItEnds() {
        send("cur.mp4"); call("POST", "/api/play?name=cur.mp4")
        assertEquals("cur.mp4", player.st.name)
        mark("n1.mp4"); send("n1.mp4")
        assertEquals("cur.mp4", player.st.name, "the running video is not interrupted")
        assertEquals(listOf("cur.mp4", "n1.mp4"), playlistItems())
        assertTrue(notices.any { it == "À suivre : n1.mp4" }, notices.toString())
        mark("n2.mp4"); send("n2.mp4")
        assertEquals(listOf("cur.mp4", "n1.mp4", "n2.mp4"), playlistItems(), "arrival order")
        assertTrue(server.onPlaybackEnded("cur.mp4"))
        assertEquals("n1.mp4", player.st.name)
    }

    @Test fun aCopyWithoutTheMarkIsNeverQueued() {
        send("cur.mp4"); call("POST", "/api/play?name=cur.mp4")
        send("plain.mp4")
        assertEquals("cur.mp4", player.st.name)
        assertEquals(emptyList(), playlistItems())
    }

    @Test fun aFileAlreadyOnTheTvIsQueuedAtOnce() {
        send("cur.mp4"); send("old.mp4"); call("POST", "/api/play?name=cur.mp4")
        val (c, b) = mark("old.mp4"); assertEquals(200, c); assertTrue("\"pending\":false" in b, b)
        assertEquals(listOf("cur.mp4", "old.mp4"), playlistItems())
    }

    @Test fun aFileAlreadyOnTheTvStartsAtOnceWhenNothingPlays() {
        send("old.mp4")
        val (_, b) = mark("old.mp4"); assertTrue("\"now\":true" in b, b)
        assertEquals("old.mp4", player.st.name)
    }

    @Test fun aChildProfileRefusesTheMark() {
        childActive = true
        val (c, b) = mark("a.mp4")
        assertEquals(403, c); assertTrue("profil enfant" in b, b)
        childActive = false
        send("cur.mp4"); call("POST", "/api/play?name=cur.mp4"); childActive = true
        send("a.mp4")
        assertEquals(emptyList(), playlistItems(), "no mark was kept")
    }
}
