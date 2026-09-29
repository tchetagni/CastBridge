package castbridge.core

import castbridge.core.tv.*
import java.io.File
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import kotlin.test.*

class PlayerParamsTest {
    private val cur = PlayerTracks(subDelayMs = 100, audioDelayMs = -50)

    @Test fun parsingAndBounds() {
        assertEquals(PlayerCommand.SubDelay(150), PlayerParams.command("subdelay", mapOf("delta" to "50"), cur))
        assertEquals(PlayerCommand.SubDelay(-400), PlayerParams.command("subdelay", mapOf("ms" to "-400"), cur))
        assertEquals(PlayerCommand.AudioDelay(-100), PlayerParams.command("audiodelay", mapOf("delta" to "-50"), cur))
        assertEquals(PlayerCommand.SubDelay(30_000), PlayerParams.command("subdelay", mapOf("ms" to "999999"), cur), "clamped to 30 s")
        assertNull(PlayerParams.command("subdelay", mapOf("ms" to "abc"), cur)); assertNull(PlayerParams.command("subdelay", emptyMap(), cur))
        assertEquals(PlayerCommand.Rate(2f), PlayerParams.command("rate", mapOf("value" to "8"), cur))
        assertEquals(PlayerCommand.Rate(0.5f), PlayerParams.command("rate", mapOf("value" to "0.1"), cur))
        assertEquals(PlayerCommand.Rate(1.25f), PlayerParams.command("rate", mapOf("value" to "1,25"), cur))
        assertNull(PlayerParams.command("rate", mapOf("value" to "NaN"), cur))
        assertEquals(PlayerCommand.Aspect("16:9"), PlayerParams.command("aspect", mapOf("value" to "16:9"), cur))
        assertNull(PlayerParams.command("aspect", mapOf("value" to "21:9"), cur))
        assertEquals(PlayerCommand.Subtitle(-1), PlayerParams.command("subtitle", mapOf("id" to "-7"), cur))
        assertEquals(PlayerCommand.SubFile("film.fr.srt"), PlayerParams.command("subtitle", mapOf("file" to "film.fr.srt"), cur))
        assertNull(PlayerParams.command("subtitle", mapOf("file" to "../etc/passwd"), cur), "never a path")
        assertEquals(PlayerCommand.ChapterStep(1), PlayerParams.command("chapter", mapOf("delta" to "5"), cur))
        assertEquals(PlayerCommand.Chapter(3), PlayerParams.command("chapter", mapOf("index" to "3"), cur))
        assertEquals(PlayerCommand.SubScale(400), PlayerParams.command("subsize", mapOf("value" to "1000"), cur))
        assertEquals(PlayerCommand.Hw("off"), PlayerParams.command("hw", mapOf("value" to "OFF"), cur))
        assertNull(PlayerParams.command("hw", mapOf("value" to "gpu"), cur)); assertNull(PlayerParams.command("volume", mapOf("value" to "1"), cur))
        assertEquals("1,25x", PlayerParams.rateLabel(1.25f)); assertEquals("1x", PlayerParams.rateLabel(1f)); assertEquals("0,5x", PlayerParams.rateLabel(0.5f))
        assertEquals("+150 ms", PlayerParams.delayLabel(150)); assertEquals("-50 ms", PlayerParams.delayLabel(-50))
    }

    @Test fun perFileMemoryRoundTrip() {
        val p = PlayerPrefs(audio = 2, subtitle = 5, subFile = "Film; v2.fr.srt", subDelayMs = 150, audioDelayMs = -50, subScale = 125, rate = 1.25f, aspect = "16:9")
        assertEquals(p, PlayerPrefs.decode(p.encode()))
        assertEquals("", PlayerPrefs().encode(), "defaults are not stored")
        assertEquals(PlayerPrefs(), PlayerPrefs.decode(null)); assertEquals(PlayerPrefs(), PlayerPrefs.decode("garbage;x=;=1"))
        val bad = PlayerPrefs.decode("sd=99999999;r=9;ar=weird;ss=5;sf=..%2Fx.srt;future=1")
        assertEquals(30_000, bad.subDelayMs); assertEquals(2f, bad.rate); assertEquals("auto", bad.aspect); assertEquals(25, bad.subScale); assertNull(bad.subFile)
        assertTrue(p.wantsSubtitles); assertFalse(PlayerPrefs(subtitle = -1).wantsSubtitles); assertTrue(PlayerPrefs(subFile = "a.srt").wantsSubtitles)
    }

    @Test fun externalSubtitles() {
        val names = listOf("Film.mkv", "Film.srt", "film.en.srt", "Film.fr.ass", "Film 2.srt", "Other.srt", "Film.mkv.part", "Film.nfo", "Film.verylonglanguage.srt")
        assertEquals(listOf("Film.srt", "Film.fr.ass", "film.en.srt"), SubtitleFinder.find("Film.mkv", names))
        assertTrue(SubtitleFinder.find("Nothing.mp4", names).isEmpty())
        assertTrue(SubtitleFinder.isSubtitle("a.ASS")); assertFalse(SubtitleFinder.isSubtitle("a.txt")); assertFalse(SubtitleFinder.isSubtitle("a.mp4"))
    }

    @Test fun playlistRepeatModes() {
        val p = Playlist(listOf("a", "b", "c"), start = 1)
        assertEquals("b", p.current); assertEquals("c", p.next()); assertNull(p.next(), "end of the list")
        p.repeat = Playlist.Repeat.ALL; assertEquals("a", p.next())
        p.repeat = Playlist.Repeat.ONE; assertEquals("a", p.next(auto = true)); assertEquals("b", p.next(auto = false), "the remote still moves on")
        assertEquals("a", p.previous()); p.repeat = Playlist.Repeat.OFF; assertEquals("a", p.previous(), "stays on the first")
        assertEquals(Playlist.Repeat.ALL, Playlist.Repeat.of("All")); assertNull(Playlist.Repeat.of("sometimes"))
        assertNull(Playlist(emptyList()).next()); assertEquals(0, Playlist(listOf("x"), start = 9).index)
    }
}

/** A player that has tracks, for the /api/player routes. */
class TrackPlayer : Player {
    var st = PlayerState()
    var t = PlayerTracks(audio = listOf(Track(1, "Français"), Track(2, "English")), audioId = 1, subtitles = listOf(Track(-1, "Désactivés"), Track(3, "FR")))
    val played = ArrayList<String>()
    var subFile: File? = null
    override fun play(file: File, posMs: Long) { played += file.name; st = PlayerState("playing", file.name, posMs, 1000) }
    override fun playStream(url: String, name: String, posMs: Long) {}
    override fun pause() {}
    override fun resume() {}
    override fun seek(posMs: Long) {}
    override fun stop() { st = PlayerState() }
    override fun state() = st
    override fun tracks() = if (st.state == "idle") null else t
    override fun command(c: PlayerCommand): Boolean {
        t = when (c) {
            is PlayerCommand.Audio -> if (t.audio.any { it.id == c.id }) t.copy(audioId = c.id) else return false
            is PlayerCommand.SubDelay -> t.copy(subDelayMs = c.ms)
            is PlayerCommand.Rate -> t.copy(rate = c.rate)
            else -> return false
        }
        return true
    }
    override fun subtitleFile(file: File): Boolean { subFile = file; return st.state != "idle" }
}

class PlayerRoutesTest {
    private val dir = kotlin.io.path.createTempDirectory("pr").toFile()
    private val port = ServerSocket(0).use { it.localPort }
    private val player = TrackPlayer()
    private val server = ReceiverServer(VolumeRegistry.single(dir), player, port, pin = "123456").apply { start(5000, false) }
    private val tv = TvClient("http://127.0.0.1:$port", "123456")

    @AfterTest fun tearDown() { server.stop(); dir.deleteRecursively() }

    @Test fun tracksAndSettings() {
        assertEquals("""{"playing":false}""", tv.raw("GET", "/api/player/tracks"))
        assertFailsWith<TvClient.HttpError> { tv.raw("POST", "/api/player/audio?id=2") }.also { assertEquals(409, it.code) }
        File(dir, "film.mkv").writeBytes(ByteArray(10)); tv.play("film.mkv")
        val j = tv.raw("GET", "/api/player/tracks")
        assertTrue(j.contains("\"playing\":true") && j.contains("\"English\"") && j.contains("\"aspects\":[\"auto\""), j)
        assertTrue(tv.raw("POST", "/api/player/audio?id=2").contains("\"audioId\":2"))
        assertEquals(409, assertFailsWith<TvClient.HttpError> { tv.raw("POST", "/api/player/audio?id=9") }.code, "no such track")
        assertTrue(tv.raw("POST", "/api/player/subdelay?delta=50").contains("\"subDelayMs\":50"))
        assertTrue(tv.raw("POST", "/api/player/subdelay?delta=50").contains("\"subDelayMs\":100"))
        assertTrue(tv.raw("POST", "/api/player/rate?value=1.5").contains("\"rate\":1.50"))
        assertEquals(400, assertFailsWith<TvClient.HttpError> { tv.raw("POST", "/api/player/rate?value=fast") }.code)
        assertEquals(404, assertFailsWith<TvClient.HttpError> { tv.raw("POST", "/api/player/explode") }.code)
    }

    @Test fun subtitleFileMustBeAStoredSubtitle() {
        File(dir, "film.mkv").writeBytes(ByteArray(10)); File(dir, "film.fr.srt").writeText("1\n00:00:01,000 --> 00:00:02,000\nSalut\n")
        tv.play("film.mkv")
        tv.raw("POST", "/api/player/subfile?name=film.fr.srt")
        assertEquals(File(dir, "film.fr.srt").canonicalFile, player.subFile!!.canonicalFile)
        assertEquals(400, assertFailsWith<TvClient.HttpError> { tv.raw("POST", "/api/player/subfile?name=film.mkv") }.code)
        assertEquals(400, assertFailsWith<TvClient.HttpError> { tv.raw("POST", "/api/player/subfile?name=..%2F..%2Fetc%2Fpasswd.srt") }.code)
        assertEquals(404, assertFailsWith<TvClient.HttpError> { tv.raw("POST", "/api/player/subfile?name=missing.srt") }.code)
    }

    @Test fun playlistPlaysOneAfterTheOtherWithRepeat() {
        listOf("a.mp4", "b.mp4", "c.mp4").forEach { File(dir, it).writeBytes(ByteArray(5)) }
        tv.raw("POST", "/api/playlist?names=${TvClient.enc("a.mp4/b.mp4/missing.mp4/c.mp4")}&start=1")
        assertEquals(listOf("b.mp4"), player.played)
        assertTrue(tv.info().contains("\"playlist\":{\"items\":[\"a.mp4\",\"b.mp4\",\"c.mp4\"],\"index\":1"), tv.info())
        assertTrue(server.onPlaybackEnded("b.mp4")); assertEquals("c.mp4", player.played.last())
        assertFalse(server.onPlaybackEnded("c.mp4"), "end of the list, no repeat")
        tv.raw("POST", "/api/playlist?names=${TvClient.enc("a.mp4/b.mp4")}&repeat=all")
        assertTrue(server.onPlaybackEnded("a.mp4")); assertTrue(server.onPlaybackEnded("b.mp4")); assertEquals("a.mp4", player.played.last())
        tv.raw("POST", "/api/player/prev"); assertEquals("b.mp4", player.played.last())
        tv.raw("POST", "/api/player/repeat?value=one"); assertTrue(server.onPlaybackEnded("b.mp4")); assertEquals("b.mp4", player.played.last())
        tv.play("c.mp4")                                                    // a single play ends the playlist
        assertFalse(server.onPlaybackEnded("c.mp4"))
        assertEquals(409, assertFailsWith<TvClient.HttpError> { tv.raw("POST", "/api/player/next") }.code)
        assertEquals(400, assertFailsWith<TvClient.HttpError> { tv.raw("POST", "/api/playlist?names=a.mp4&repeat=maybe") }.code)
        assertEquals(404, assertFailsWith<TvClient.HttpError> { tv.raw("POST", "/api/playlist?names=zz.mp4") }.code)
    }

    @Test fun everyPlayerRouteNeedsThePin() {
        for ((m, path) in listOf("GET" to "/api/player/tracks", "POST" to "/api/player/audio?id=1", "POST" to "/api/player/rate?value=1",
                "POST" to "/api/playlist?names=a.mp4", "POST" to "/api/player/next", "POST" to "/api/player/subfile?name=a.srt")) {
            val c = URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection
            c.requestMethod = m
            if (m == "POST") { c.doOutput = true; c.setFixedLengthStreamingMode(0); c.outputStream.close() }
            assertEquals(401, c.responseCode, path)
        }
    }
}
