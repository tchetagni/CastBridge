package castbridge.core

import castbridge.core.remote.*
import castbridge.core.tv.*
import java.io.*
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.*

/** What a fake TV received. */
class FakeSink : RemoteSink {
    data class K(val key: RemoteKey, val action: KeyAction, val repeat: Int, val target: RemoteTarget)
    val keys = CopyOnWriteArrayList<K>()
    val texts = CopyOnWriteArrayList<Pair<String, TextMode>>()
    val globals = CopyOnWriteArrayList<RemoteGlobal>()
    val plays = CopyOnWriteArrayList<Pair<String, Long>>()
    val pauses = java.util.concurrent.atomic.AtomicInteger()
    val resumes = java.util.concurrent.atomic.AtomicInteger()
    val seeks = CopyOnWriteArrayList<Long>()
    val stops = java.util.concurrent.atomic.AtomicInteger()
    val volumes = CopyOnWriteArrayList<Int>()
    val files = CopyOnWriteArrayList<String>()
    var refuse: String? = null
    override fun key(k: RemoteKey, action: KeyAction, repeat: Int, target: RemoteTarget): Outcome {
        refuse?.let { return Outcome.refused(it) }
        keys += K(k, action, repeat, target); return Outcome.done("app")
    }
    override fun text(value: String, mode: TextMode, target: RemoteTarget): Outcome { texts += value to mode; return Outcome.done("app") }
    override fun global(g: RemoteGlobal): Outcome { globals += g; return Outcome.done("system") }
    override fun stateJson() = """{"screen":"PlayerActivity","system":{"enabled":false}}"""
    override fun play(name: String, pos: Long): Outcome { plays += name to pos; return Outcome.done("app") }
    override fun pause(): Outcome { pauses.incrementAndGet(); return Outcome.done("app") }
    override fun resume(): Outcome { resumes.incrementAndGet(); return Outcome.done("app") }
    override fun seek(pos: Long): Outcome { seeks += pos; return Outcome.done("app") }
    override fun stop(): Outcome { stops.incrementAndGet(); return Outcome.done("app") }
    override fun setVolume(pct: Int): Outcome { volumes += pct; return Outcome.done("audio") }
    override fun playerJson() = """{"state":"playing","name":"clip.mp4","pos":1234,"dur":60000}"""
    override fun fileInfo(name: String): String { files += name; return """{"exists":true,"size":42}""" }
}

class RemoteKeysTest {
    @Test fun whitelistAcceptsTheRemoteKeysAndAliases() {
        assertEquals(RemoteKey.DPAD_UP, RemoteKey.parse("DPAD_UP"))
        assertEquals(RemoteKey.DPAD_UP, RemoteKey.parse("keycode_dpad_up"))
        assertEquals(RemoteKey.DPAD_UP, RemoteKey.parse(" up "))
        assertEquals(RemoteKey.DPAD_CENTER, RemoteKey.parse("OK"))
        assertEquals(RemoteKey.PLAY_PAUSE, RemoteKey.parse("MEDIA_PLAY_PAUSE"))
        assertEquals(RemoteKey.VOLUME_MUTE, RemoteKey.parse("mute"))
        assertEquals(RemoteKey.HOME, RemoteKey.parse("HOME"))
        for (d in 0..9) assertEquals(d + 7, RemoteKey.parse("$d")!!.code)
        assertEquals(RemoteKey.NUM_5, RemoteKey.parse("KEYCODE_5"))
        assertEquals(RemoteKey.NUM_5, RemoteKey.parse("NUM_5"))
    }

    @Test fun whitelistRefusesEverythingElse() {
        for (bad in listOf(null, "", "POWER", "KEYCODE_POWER", "26", "SLEEP", "WAKEUP", "APP_SWITCH", "SETTINGS", "TV_POWER",
            "ASSIST", "DPAD_UP; rm", "10", "-1", "x".repeat(200), "KEYCODE_", "NUM_", "CALL", "CAMERA", "NOTIFICATION"))
            assertNull(RemoteKey.parse(bad), "$bad must be refused")
        // No key of the list is a power / system-dangerous one.
        val codes = RemoteKey.values().map { it.code }.toSet()
        assertTrue(26 !in codes && 223 !in codes && 224 !in codes && 187 !in codes && 176 !in codes)
        assertEquals(RemoteKey.values().size, codes.size, "one key per code")
    }

    @Test fun repeatableKeys() {
        assertTrue(RemoteKey.DPAD_DOWN.repeatable); assertTrue(RemoteKey.VOLUME_UP.repeatable); assertTrue(RemoteKey.DEL.repeatable)
        assertFalse(RemoteKey.DPAD_CENTER.repeatable); assertFalse(RemoteKey.BACK.repeatable); assertFalse(RemoteKey.VOLUME_MUTE.repeatable)
        assertFalse(RemoteKey.HOME.repeatable)
    }

    @Test fun globalsAndModes() {
        assertEquals(RemoteGlobal.RECENTS, RemoteGlobal.parse("recents"))
        assertNull(RemoteGlobal.parse("REBOOT"))
        assertEquals(KeyAction.PRESS, KeyAction.parse(null)); assertNull(KeyAction.parse("smash"))
        assertEquals(RemoteTarget.AUTO, RemoteTarget.parse("")); assertNull(RemoteTarget.parse("kernel"))
        assertEquals(TextMode.INSERT, TextMode.parse(null)); assertEquals(TextMode.CLEAR, TextMode.parse("clear"))
    }
}

class RemoteApiTest {
    private var t = 1000L
    private val sink = FakeSink()
    private val api = RemoteApi(sink) { t }
    private fun post(route: String, vararg p: Pair<String, String>) = api.handle("/api/remote/$route", "POST", mapOf(*p))!!

    @Test fun onlyItsOwnRoutes() {
        assertNull(api.handle("/api/info", "GET", emptyMap()))
        assertEquals(404, post("nope").status)
        assertEquals(405, api.handle("/api/remote/key", "GET", mapOf("code" to "OK"))!!.status)
        assertEquals(405, api.handle("/api/remote/state", "POST", emptyMap())!!.status)
        assertEquals(200, api.handle("/api/remote/state", "GET", emptyMap())!!.status)
        assertEquals(200, post("ping").status)
    }

    @Test fun keyParsingAndLimits() {
        assertEquals(200, post("key", "code" to "DPAD_DOWN").status)
        assertEquals(FakeSink.K(RemoteKey.DPAD_DOWN, KeyAction.PRESS, 0, RemoteTarget.AUTO), sink.keys.single())
        assertEquals(400, post("key", "code" to "POWER").status)
        assertEquals(400, post("key").status)
        assertEquals(400, post("key", "code" to "OK", "action" to "smash").status)
        assertEquals(400, post("key", "code" to "OK", "target" to "kernel").status)
        assertEquals(400, post("key", "code" to "UP", "action" to "down", "repeat" to "-1").status)
        assertEquals(400, post("key", "code" to "UP", "action" to "down", "repeat" to "100001").status)
        assertEquals(400, post("key", "code" to "UP", "action" to "down", "repeat" to "abc").status)
        assertEquals(1, sink.keys.size, "refused requests never reach the TV")
        assertEquals(200, post("key", "code" to "5", "target" to "app").status)
        assertEquals(RemoteTarget.APP, sink.keys.last().target)
    }

    @Test fun refusalIsAFrenchMessage() {
        sink.refuse = "Aucun écran de CastBridge n'est affiché"
        val r = post("key", "code" to "OK")
        assertEquals(409, r.status)
        assertTrue("Aucun écran" in r.json)
        assertEquals("Aucun écran de CastBridge n'est affiché", RemoteSession.message(RemoteReply(r.status, r.json)))
    }

    @Test fun textLimits() {
        assertEquals(200, post("text", "value" to "Astérix et Obélix").status)
        assertEquals("Astérix et Obélix" to TextMode.INSERT, sink.texts.single())
        assertEquals(400, post("text", "value" to "x".repeat(RemoteApi.MAX_TEXT + 1)).status)
        assertEquals(400, post("text", "value" to "a\nb").status)
        assertEquals(400, post("text", "value" to "a\u0000").status)
        assertEquals(400, post("text").status, "empty insert")
        assertEquals(200, post("text", "mode" to "clear").status)
        assertEquals(400, post("text", "value" to "a", "mode" to "format").status)
        assertEquals(200, post("text", "value" to "x".repeat(RemoteApi.MAX_TEXT)).status)
    }

    @Test fun globalActions() {
        assertEquals(200, post("global", "action" to "HOME").status)
        assertEquals(RemoteGlobal.HOME, sink.globals.single())
        assertEquals(400, post("global", "action" to "REBOOT").status)
    }

    @Test fun playbackRoutes() {
        assertEquals(200, post("play", "name" to "clip.mp4", "pos" to "1500").status)
        assertEquals("clip.mp4" to 1500L, sink.plays.single())
        assertEquals(400, post("play").status)
        assertEquals(400, post("play", "name" to "").status)
        assertEquals(400, post("play", "name" to "x".repeat(RemoteApi.MAX_NAME + 1)).status)
        assertEquals(400, post("play", "name" to "clip.mp4", "pos" to "-1").status)
        assertEquals(400, post("play", "name" to "clip.mp4", "pos" to "abc").status)
        assertEquals(200, post("pause").status); assertEquals(1, sink.pauses.get())
        assertEquals(200, post("resume").status); assertEquals(1, sink.resumes.get())
        assertEquals(200, post("seek", "pos" to "9000").status); assertEquals(listOf(9000L), sink.seeks.toList())
        assertEquals(400, post("seek").status)
        assertEquals(200, post("stop").status); assertEquals(1, sink.stops.get())
        assertEquals(200, post("volume", "pct" to "42").status); assertEquals(listOf(42), sink.volumes.toList())
        assertEquals(400, post("volume", "pct" to "101").status)
        assertEquals(405, api.handle("/api/remote/play", "GET", mapOf("name" to "a"))!!.status)
    }

    @Test fun playerStateRoute() {
        val r = api.handle("/api/remote/player", "GET", emptyMap())!!
        assertEquals(200, r.status)
        assertTrue("\"state\":\"playing\"" in r.json && "\"pos\":1234" in r.json)
        assertEquals(405, api.handle("/api/remote/player", "POST", emptyMap())!!.status)
    }

    @Test fun fileInfoRoute() {
        val r = api.handle("/api/remote/file", "GET", mapOf("name" to "clip.mp4"))!!
        assertEquals(200, r.status)
        assertTrue("\"size\":42" in r.json)
        assertEquals(listOf("clip.mp4"), sink.files.toList())
        assertEquals(405, api.handle("/api/remote/file", "POST", emptyMap())!!.status)
    }

    @Test fun pointerBecomesArrows() {
        val r = post("pointer", "dx" to "130", "dy" to "10")
        assertEquals(200, r.status)
        assertEquals(listOf(RemoteKey.DPAD_RIGHT, RemoteKey.DPAD_RIGHT), sink.keys.map { it.key })
        assertEquals(200, post("pointer", "tap" to "1").status)
        assertEquals(RemoteKey.DPAD_CENTER, sink.keys.last().key)
        assertEquals(400, post("pointer", "dx" to "99999", "dy" to "0").status)
        assertEquals(400, post("pointer", "dx" to "1").status)
        sink.keys.clear()
        assertEquals(200, post("pointer", "dx" to "0", "dy" to "-4000").status)
        assertEquals(10, sink.keys.size, "a huge move is capped")
    }

    @Test fun duplicateSequenceNumbersAreIgnored() {
        assertEquals(200, post("key", "code" to "OK", "sid" to "abc", "seq" to "1").status)
        val again = post("key", "code" to "OK", "sid" to "abc", "seq" to "1")
        assertEquals(200, again.status); assertTrue("\"dup\":true" in again.json)
        assertEquals(1, sink.keys.size)
        assertEquals(200, post("key", "code" to "DOWN", "sid" to "abc", "seq" to "2").status)
        assertTrue("dup" in post("key", "code" to "DOWN", "sid" to "abc", "seq" to "2").json)
        assertEquals(2, sink.keys.size)
        // another phone (session) has its own numbers
        assertEquals(200, post("key", "code" to "UP", "sid" to "other", "seq" to "1").status)
        assertEquals(3, sink.keys.size)
        // malformed
        assertEquals(400, post("key", "code" to "UP", "sid" to "abc").status)
        assertEquals(400, post("key", "code" to "UP", "sid" to "a b", "seq" to "9").status)
        assertEquals(400, post("key", "code" to "UP", "sid" to "abc", "seq" to "-3").status)
    }

    @Test fun aRejectedRequestDoesNotBurnItsNumber() {
        assertEquals(400, post("key", "code" to "POWER", "sid" to "s", "seq" to "5").status)
        assertEquals(200, post("key", "code" to "OK", "sid" to "s", "seq" to "5").status)
        assertEquals(1, sink.keys.size)
    }

    @Test fun heldKeyRepeatsAndIsReleased() {
        post("key", "code" to "DOWN", "action" to "down")
        t += 100; post("key", "code" to "DOWN", "action" to "down", "repeat" to "1")
        t += 100; post("key", "code" to "DOWN", "action" to "down", "repeat" to "2")
        assertTrue(api.holding)
        t += 100; post("key", "code" to "DOWN", "action" to "up")
        assertFalse(api.holding)
        assertEquals(listOf(KeyAction.DOWN to 0, KeyAction.DOWN to 1, KeyAction.DOWN to 2, KeyAction.UP to 0), sink.keys.map { it.action to it.repeat })
    }

    @Test fun linkLostWhileHoldingReleasesTheKey() {
        post("key", "code" to "RIGHT", "action" to "down")
        t += 300; assertTrue(api.releaseStale().isEmpty())
        t += 1000
        assertEquals(listOf(RemoteKey.DPAD_RIGHT), api.releaseStale())
        assertEquals(KeyAction.UP, sink.keys.last().action)
        assertFalse(api.holding)
        // the late "up" from the phone does not release twice
        val late = post("key", "code" to "RIGHT", "action" to "up")
        assertTrue("ignored" in late.json)
        assertEquals(2, sink.keys.size)
        // a late repeat starts a new press (repeat 0), never an orphan repeat
        post("key", "code" to "RIGHT", "action" to "down", "repeat" to "7")
        assertEquals(0, sink.keys.last().repeat)
    }
}

class RemoteQueueTest {
    private var t = 0L
    private val q = RemoteQueue("sid1", maxAgeMs = 3000, capacity = 8) { t }

    @Test fun keepsOrderAndNumbers() {
        val a = q.offer("key", mapOf("code" to "DPAD_DOWN"))
        val b = q.offer("key", mapOf("code" to "DPAD_DOWN", "action" to "down", "repeat" to "1"))
        val c = q.offer("text", mapOf("value" to "é"))
        assertEquals(listOf(1L, 2L, 3L), listOf(a.seq, b.seq, c.seq))
        assertEquals(a, q.peek()); q.ack(a.seq)
        assertEquals(b, q.peek()); q.ack(b.seq)
        assertEquals(c, q.take(10)); q.ack(c.seq)
        assertNull(q.peek())
        assertEquals("value=%C3%A9&sid=sid1&seq=3", c.query("sid1"))
    }

    @Test fun unansweredEventStaysForResend() {
        val a = q.offer("key", mapOf("code" to "OK"))
        assertEquals(a, q.peek())      // sent, link broke before the answer: not acked
        assertEquals(a, q.peek())      // still first, same number
    }

    @Test fun stalePressesAreDroppedButReleasesKept() {
        q.offer("key", mapOf("code" to "OK"))
        val up = q.offer("key", mapOf("code" to "DPAD_UP", "action" to "up"))
        t += 5000
        val fresh = q.offer("key", mapOf("code" to "BACK"))
        assertEquals(up, q.peek()); q.ack(up.seq)
        assertEquals(fresh, q.peek())
        assertEquals(1, q.dropped)
    }

    @Test fun capacityDropsOldestPress() {
        repeat(8) { q.offer("key", mapOf("code" to "DPAD_DOWN")) }
        q.offer("key", mapOf("code" to "OK"))
        assertEquals(8, q.size()); assertEquals(2L, q.peek()!!.seq)
    }

    @Test fun takeWaitsForAnEvent() {
        val q2 = RemoteQueue("s")
        val th = Thread { Thread.sleep(50); q2.offer("key", mapOf("code" to "OK")) }.apply { start() }
        val e = q2.take(2000)
        assertNotNull(e); th.join()
        assertNull(RemoteQueue("s").take(20))
    }
}

class TouchpadMapperTest {
    @Test fun slidesBecomeArrows() {
        val m = TouchpadMapper(60f)
        assertEquals(emptyList(), m.move(30f, 0f))
        assertEquals(listOf(RemoteKey.DPAD_RIGHT), m.move(35f, 5f))
        assertEquals(listOf(RemoteKey.DPAD_DOWN, RemoteKey.DPAD_DOWN), m.move(10f, 125f))
        assertEquals(listOf(RemoteKey.DPAD_LEFT), m.move(-70f, 0f))
        assertEquals(listOf(RemoteKey.DPAD_UP), m.move(0f, -61f))
        assertEquals(RemoteKey.DPAD_CENTER, m.tap())
    }

    @Test fun diagonalFollowsTheDominantAxis() {
        val m = TouchpadMapper(60f)
        assertEquals(listOf(RemoteKey.DPAD_RIGHT), m.move(70f, 50f))
        // the sideways part was forgotten: a small vertical move does not fire
        assertEquals(emptyList(), m.move(0f, 20f))
    }

    @Test fun resetForgetsPartialMoves() {
        val m = TouchpadMapper(60f)
        m.move(50f, 0f); m.reset()
        assertEquals(emptyList(), m.move(20f, 0f))
    }

    @Test fun flingIsCapped() {
        val m = TouchpadMapper(60f, maxStepsPerMove = 8)
        assertEquals(8, m.move(0f, 5000f).size)
        assertEquals(emptyList(), m.move(0f, 10f), "the rest of the fling is forgotten")
        assertEquals(3, TouchpadMapper.steps(-190, 0).size)
    }
}

class TextDiffTest {
    @Test fun diffs() {
        assertEquals(TextDiff.Edit(0, "c"), TextDiff.between("ab", "abc"))
        assertEquals(TextDiff.Edit(1, ""), TextDiff.between("abc", "ab"))
        assertEquals(TextDiff.Edit(2, "llo"), TextDiff.between("hexy", "hello"))
        assertEquals(TextDiff.Edit(0, "é"), TextDiff.between("", "é"))
        assertEquals(TextDiff.Edit(0, ""), TextDiff.between("same", "same"))
    }
}

/** A transport that loses the answer of chosen events (the TV applied them, the phone does not know). */
private class FlakyTransport(private val api: RemoteApi, private val loseAnswerOf: MutableSet<Long>) : RemoteTransport {
    override val name = "fake"
    override fun send(method: String, route: String, query: String): RemoteReply {
        val p = RemoteWire.parseQuery(query)
        val r = api.handle(RemoteApi.PREFIX + route, method, p)!!
        val seq = p["seq"]?.toLong()
        if (seq != null && loseAnswerOf.remove(seq)) throw IOException("wifi lost after the TV applied it")
        return RemoteReply(r.status, r.json)
    }
    override fun close() {}
}

class RemoteSessionTest {
    @Test fun reconnectionNeverDuplicatesNorLosesKeys() {
        val sink = FakeSink()
        val api = RemoteApi(sink)
        val lose = mutableSetOf(3L, 4L, 9L)
        var connects = 0
        val done = CountDownLatch(1)
        val q = RemoteQueue("phone1")
        val s = RemoteSession(q, { connects++; if (connects == 2) throw IOException("TV injoignable") else FlakyTransport(api, lose) },
            object : RemoteSession.Listener {
                override fun status(s: RemoteSession.Status) {}
                override fun answered(e: RemoteEvent, r: RemoteReply, rttMs: Long) { if (e.seq == 12L) done.countDown() }
            }, pingMs = 60_000)
        val sent = listOf(RemoteKey.DPAD_DOWN, RemoteKey.DPAD_DOWN, RemoteKey.DPAD_RIGHT, RemoteKey.DPAD_CENTER, RemoteKey.BACK,
            RemoteKey.NUM_1, RemoteKey.NUM_2, RemoteKey.DPAD_UP, RemoteKey.DPAD_UP, RemoteKey.DPAD_LEFT, RemoteKey.MENU, RemoteKey.HOME)
        sent.forEach { s.key(it) }
        s.start()
        assertTrue(done.await(10, TimeUnit.SECONDS))
        s.stop()
        assertEquals(sent, sink.keys.map { it.key }, "same keys, same order, each once")
        assertTrue(connects >= 4)
        assertEquals(0, q.size())
    }

    @Test fun heldKeySequence() {
        val sink = FakeSink()
        val api = RemoteApi(sink)
        val done = CountDownLatch(1)
        val q = RemoteQueue("p")
        val lose = mutableSetOf(2L)
        val s = RemoteSession(q, { FlakyTransport(api, lose) }, object : RemoteSession.Listener {
            override fun status(s: RemoteSession.Status) {}
            override fun answered(e: RemoteEvent, r: RemoteReply, rttMs: Long) { if (e.isRelease) done.countDown() }
        }, pingMs = 60_000)
        s.key(RemoteKey.DPAD_DOWN, KeyAction.DOWN)
        (1..4).forEach { s.key(RemoteKey.DPAD_DOWN, KeyAction.DOWN, it) }
        s.key(RemoteKey.DPAD_DOWN, KeyAction.UP)
        s.start()
        assertTrue(done.await(10, TimeUnit.SECONDS)); s.stop()
        assertEquals(listOf(0, 1, 2, 3, 4), sink.keys.filter { it.action == KeyAction.DOWN }.map { it.repeat })
        assertEquals(1, sink.keys.count { it.action == KeyAction.UP })
    }

    @Test fun badPinStopsTheSession() {
        val statuses = CopyOnWriteArrayList<RemoteSession.Status>()
        var sends = 0
        val t = object : RemoteTransport {
            override val name = "fake"
            override fun send(method: String, route: String, query: String): RemoteReply { sends++; return RemoteReply(401, """{"error":"bad pin"}""") }
            override fun close() {}
        }
        val s = RemoteSession(RemoteQueue("p"), { t }, object : RemoteSession.Listener { override fun status(s: RemoteSession.Status) { statuses += s } })
        s.key(RemoteKey.DPAD_CENTER); s.start()
        val until = System.currentTimeMillis() + 5000
        while (s.isRunning && System.currentTimeMillis() < until) Thread.sleep(10)
        assertFalse(s.isRunning)
        assertEquals(RemoteSession.Link.BAD_PIN, statuses.last().link)
        assertEquals(1, sends, "one try only: retries would lock the TV")
    }
}

/** The real thing over loopback: ReceiverServer (PIN) + RemoteApi, phone transport on one keep-alive connection. */
class RemoteHttpTest {
    private val dir = kotlin.io.path.createTempDirectory("remote").toFile()
    private val sink = FakeSink()
    private val api = RemoteApi(sink)
    private val port = ServerSocket(0).use { it.localPort }
    private val server = ReceiverServer(dir, FakePlayer(), port, pin = "123456", extension = api).apply { start(15_000, false) }

    @AfterTest fun tearDown() { server.stop(); dir.deleteRecursively() }

    @Test fun keysOverOneKeepAliveConnection() {
        val t = HttpRemoteTransport("127.0.0.1", port, "123456")
        val n = 300
        val times = LongArray(n)
        repeat(n) { i ->
            val t0 = System.nanoTime()
            val r = t.send("POST", "key", "code=${if (i % 2 == 0) "DPAD_DOWN" else "DPAD_UP"}&sid=s&seq=${i + 1}")
            times[i] = System.nanoTime() - t0
            assertEquals(200, r.status, r.body)
        }
        assertEquals(n, sink.keys.size)
        assertEquals(1, t.opened, "every key on the same TCP connection")
        val sorted = times.sorted()
        val p50 = sorted[n / 2] / 1e6; val p95 = sorted[n * 95 / 100] / 1e6
        println("loopback latency per key: p50=%.2f ms p95=%.2f ms".format(p50, p95))
        assertTrue(p95 < 50, "p95 $p95 ms")
        // the state route through the same connection
        val st = t.send("GET", "state", "")
        assertEquals(200, st.status); assertTrue("PlayerActivity" in st.body)
        t.close()
    }

    @Test fun wrongPinIsRefused() {
        val t = HttpRemoteTransport("127.0.0.1", port, "000000")
        assertEquals(401, t.send("POST", "key", "code=OK").status)
        assertTrue(sink.keys.isEmpty())
    }

    @Test fun textWithAccentsAndSpaces() {
        val t = HttpRemoteTransport("127.0.0.1", port, "123456")
        assertEquals(200, t.send("POST", "text", RemoteWire.query(mapOf("value" to "Le Roi & l'Oiseau été"))).status)
        assertEquals("Le Roi & l'Oiseau été", sink.texts.single().first)
        t.close()
    }

    @Test fun sessionReconnectsAfterTheServerClosedTheConnection() {
        val done = CountDownLatch(2)
        val q = RemoteQueue("x")
        var transport: HttpRemoteTransport? = null
        val s = RemoteSession(q, { HttpRemoteTransport("127.0.0.1", port, "123456").also { transport = it } }, object : RemoteSession.Listener {
            override fun status(s: RemoteSession.Status) {}
            override fun answered(e: RemoteEvent, r: RemoteReply, rttMs: Long) { done.countDown() }
        }, pingMs = 60_000)
        s.start()
        s.key(RemoteKey.DPAD_DOWN)
        val until = System.currentTimeMillis() + 5000
        while (sink.keys.isEmpty() && System.currentTimeMillis() < until) Thread.sleep(5)
        transport!!.close()               // like the TV's idle timeout or a Wi-Fi hiccup
        s.key(RemoteKey.DPAD_UP)
        assertTrue(done.await(5, TimeUnit.SECONDS)); s.stop()
        assertEquals(listOf(RemoteKey.DPAD_DOWN, RemoteKey.DPAD_UP), sink.keys.map { it.key })
    }
}

/** Remote over the Bluetooth file service (in-memory pipes stand for RFCOMM). */
class RemoteBtTest {
    @Test fun handshakeThenLines() {
        val dir = kotlin.io.path.createTempDirectory("rbt").toFile()
        val sink = FakeSink(); val api = RemoteApi(sink)
        val c2s = PipedOutputStream(); val tvIn = PipedInputStream(c2s, 1 shl 16)
        val s2c = PipedOutputStream(); val clIn = PipedInputStream(s2c, 1 shl 16)
        val result = java.util.concurrent.LinkedBlockingQueue<Int>()
        Thread {
            val r = runCatching { BtProtocol.serve(dir, tvIn, s2c, PinGuard("482913"), "AA", remote = { i, o -> RemoteBt.serve(i, o, api) }) }
            result.put(r.getOrDefault(-1)); runCatching { s2c.close() }
        }.apply { isDaemon = true; start() }
        RemoteBt.handshake(clIn, c2s, "482913")
        val t = RemoteBt.Transport(clIn, c2s) { c2s.close() }
        assertEquals(200, t.send("POST", "key", "code=OK&sid=b&seq=1").status)
        assertTrue("dup" in t.send("POST", "key", "code=OK&sid=b&seq=1").body)
        val refused = t.send("POST", "key", "code=POWER")
        assertEquals(400, refused.status)
        assertEquals(200, t.send("POST", "text", RemoteWire.query(mapOf("value" to "Kirikou é"))).status)
        val st = t.send("GET", "state", "")
        assertEquals(200, st.status); assertTrue(st.body.startsWith("{"))
        t.close()
        assertEquals(BtProtocol.OK, result.poll(5, TimeUnit.SECONDS))
        assertEquals(1, sink.keys.size); assertEquals("Kirikou é", sink.texts.single().first)
        dir.deleteRecursively()
    }

    @Test fun wrongPinOverBluetooth() {
        val dir = kotlin.io.path.createTempDirectory("rbt").toFile()
        val c2s = PipedOutputStream(); val tvIn = PipedInputStream(c2s, 1 shl 16)
        val s2c = PipedOutputStream(); val clIn = PipedInputStream(s2c, 1 shl 16)
        Thread { runCatching { BtProtocol.serve(dir, tvIn, s2c, PinGuard("482913"), "AA", remote = { _, _ -> fail("must not run") }) } }
            .apply { isDaemon = true; start() }
        val e = assertFailsWith<BtProtocol.Refused> { RemoteBt.handshake(clIn, c2s, "111111") }
        assertEquals(BtProtocol.ERR_PIN, e.code)
        dir.deleteRecursively()
    }
}
