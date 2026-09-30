package castbridge.core

import castbridge.core.tv.*
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.ServerSocket
import kotlin.random.Random
import kotlin.test.*

class FakePlayer : Player {
    var st = PlayerState()
    var lastUrl: String? = null
    override fun play(file: File, posMs: Long) { st = PlayerState("playing", file.name, posMs, 1000) }
    override fun playStream(url: String, name: String, posMs: Long) { lastUrl = url; st = PlayerState("playing", name, posMs, 1000) }
    override fun pause() { st = st.copy(state = "paused") }
    override fun resume() { st = st.copy(state = "playing") }
    override fun seek(posMs: Long) { st = st.copy(posMs = posMs) }
    override fun stop() { st = PlayerState() }
    override fun state() = st
}

class ReceiverTest {
    private val dir = kotlin.io.path.createTempDirectory("tv").toFile()
    private val player = FakePlayer()
    private val port = ServerSocket(0).use { it.localPort }
    private val server = ReceiverServer(dir, player, port, profile = TvProfile(minFreeBytes = 0, minFreeAfterTransfer = 0)).apply { start(5000, false) }
    private val base = "http://127.0.0.1:$port"
    private val tv = TvClient(base)
    private val data = Random(1).nextBytes(3_000_000)

    @AfterTest fun tearDown() { server.stop(); dir.deleteRecursively() }

    /** Emits [data] from [from] but fails after [failAfter] bytes (simulates a Wi-Fi cut). */
    private fun source(from: Long, failAfter: Long = Long.MAX_VALUE): InputStream = object : InputStream() {
        var pos = from.toInt(); var emitted = 0L
        override fun read(): Int = throw UnsupportedOperationException()
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (emitted >= failAfter) throw IOException("wifi lost")
            if (pos >= data.size) return -1
            val n = minOf(len, data.size - pos, (failAfter - emitted).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            System.arraycopy(data, pos, b, off, n); pos += n; emitted += n; return n
        }
    }

    @Test fun resumesAfterNetworkCutAndThenPlays() {
        var attempts = 0
        val states = mutableListOf<ResumableUpload.State>()
        val up = ResumableUpload("film é #1.mp4", data.size.toLong(), { base },
            { off -> attempts++; source(off, if (attempts == 1) 1_000_000 else Long.MAX_VALUE) }, sleep = {})
        assertEquals(ResumableUpload.State.Done, up.run { states += it })
        assertTrue(attempts >= 2)  // a 409 race with the dying first request may add one
        assertTrue(states.any { it is ResumableUpload.State.Waiting })
        val f = File(dir, "film é #1.mp4")
        assertContentEquals(data, f.readBytes())
        assertFalse(File(dir, "film é #1.mp4.part").exists())

        tv.play("film é #1.mp4", 5000)
        assertEquals("playing", player.st.state)
        assertEquals(5000, player.st.posMs)
        val info = tv.info()
        assertEquals("film é #1.mp4", TvClient.str(info, "name"))
        assertEquals(data.size.toLong(), TvClient.num(info, "size"))
        tv.pause(); assertEquals("paused", player.st.state)
        tv.delete("film é #1.mp4"); assertFalse(f.exists()); assertEquals("idle", player.st.state)
    }

    @Test fun conflictReturnsServerOffset() {
        val err = assertFailsWith<IOException> { tv.upload("b.mkv", 5, 10, ByteArrayInputStream(ByteArray(5))) {} }
        assertTrue(err is TvClient.Conflict && err.serverLength == 0L)
    }

    @Test fun rejectsBadNamesAndUnknownFiles() {
        assertNull(ReceiverServer.safeName("../etc"))
        assertNull(ReceiverServer.safeName("x.part"))
        assertFailsWith<TvClient.HttpError> { tv.upload("..", 0, 3, ByteArrayInputStream(ByteArray(3))) {} }
        assertEquals(404, assertFailsWith<TvClient.HttpError> { tv.play("missing.mp4") }.code)
    }

    @Test fun insufficientStorageIsFatal() {
        server.stop()
        val small = ReceiverServer(dir, player, port, profile = TvProfile(minFreeBytes = Long.MAX_VALUE / 2)).apply { start(5000, false) }
        try {
            val r = ResumableUpload("big.mp4", 10, { base }, { ByteArrayInputStream(ByteArray(10)) }, sleep = {}).run {}
            assertTrue(r is ResumableUpload.State.Failed, "$r")
        } finally { small.stop() }
    }

    @Test fun playsALinkFromThePhoneWithoutStoringIt() {
        tv.playUrl("http://192.168.1.20:8089/media/ab12.mkv", "Mon film.mkv", 298_000)
        assertEquals("http://192.168.1.20:8089/media/ab12.mkv", player.lastUrl)
        assertEquals(PlayerState("playing", "Mon film.mkv", 298_000, 1000), player.st)
        tv.playUrl("https://cdn.example/x/master.m3u8?t=a&b=c", "../../etc")   // an unsafe title is replaced, never a path
        assertEquals("https://cdn.example/x/master.m3u8?t=a&b=c", player.lastUrl); assertEquals("Depuis le téléphone", player.st.name)
        for (bad in listOf("file:///sdcard/x.mp4", "content://media/1", "javascript:alert(1)", "http://a b"))
            assertEquals(400, assertFailsWith<TvClient.HttpError> { tv.playUrl(bad, "x") }.code, bad)
        assertTrue(dir.listFiles().orEmpty().none { it.name.startsWith("Mon film") }, "nothing stored")
    }

    @Test fun waitsWhileTvUnreachable() {
        var calls = 0
        val r = ResumableUpload("x.mp4", data.size.toLong(), { if (++calls < 3) null else base },
            { source(it) }, sleep = {}).run {}
        assertEquals(ResumableUpload.State.Done, r)
        assertEquals(3, calls)
    }
}

