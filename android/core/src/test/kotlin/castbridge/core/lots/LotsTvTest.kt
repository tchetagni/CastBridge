package castbridge.core.lots

import castbridge.core.tv.BtProtocol
import castbridge.core.tv.Link
import castbridge.core.tv.PinGuard
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.net.InetSocketAddress
import kotlin.concurrent.thread
import kotlin.test.*

class TvLotStoreTest {
    private val tvs = ArrayList<FakeTv>()
    private var clock = 5000L
    private fun tv(starter: Long = 0, max: Long = 1000, app: Int = 10, keys: List<String> = listOf(Kit.pub)) = FakeTv(starter, max, { clock++ }, app, keys).also { tvs += it }
    @AfterTest fun tearDown() { tvs.forEach { it.dir.deleteRecursively() } }

    private class L(val meta: LotMeta, val data: ByteArray, val proof: String)
    private fun lot(scope: String, version: Int, size: Int, feature: String = "learn", minApp: Int = 0, seed: Int = scope.hashCode() * 31 + version): L {
        val d = Kit.bytes(seed, size); val m = Kit.meta(feature, scope, version, d, minApp = minApp)
        return L(m, d, Kit.sign(listOf(m)).toJson())
    }
    private fun push(t: FakeTv, l: L, chunk: Int = 100): TvLotStore.Result {
        val name = LotNames.fileName(l.meta)
        var off = 0
        while (off < l.data.size) {
            val n = minOf(chunk, l.data.size - off)
            val r = t.store.receive(name, off.toLong(), l.data.size.toLong(), l.data.copyOfRange(off, off + n))
            if (r !is TvLotStore.Chunk.Received) return TvLotStore.Result.Refused("chunk: $r")
            off += n
        }
        return t.store.installReceived(name, l.proof)
    }

    @Test fun installsAVerifiedLotThroughTheConsumer() {
        val t = tv(); val l = lot("cm2", 1, 300)
        assertEquals(TvLotStore.Result.Ok(emptyList()), push(t, l))
        assertEquals(listOf(l.meta), t.learn.installed()); assertEquals(300, t.store.usedBytes())
        val m = t.store.manifest(); assertEquals(LOT_SCHEMA, m.schema); assertEquals(700, m.remainingBytes); assertTrue(m.holds(l.meta)); assertTrue(m.lots.single().installedAt > 0)
        assertEquals(m, TvManifest.parse(m.toJson()))
    }

    @Test fun capIsExact_oneByteOverIsRefusedWithAFrenchReason() {
        val t = tv(); val a = lot("a", 1, 500); val b = lot("b", 1, 500); val c = lot("c", 1, 1)
        assertIs<TvLotStore.Result.Ok>(push(t, a)); assertIs<TvLotStore.Result.Ok>(push(t, b))
        assertEquals(1000, t.store.usedBytes()); assertFalse(t.store.overBudget())
        t.store.setPriority(listOf(a.meta.id, b.meta.id))              // both priority: nothing may be evicted
        val r = push(t, c) as TvLotStore.Result.Refused
        assertContains(r.reason, "Pas assez de place sur la TV"); assertContains(r.reason, "il manque")
        assertEquals(1000, t.store.usedBytes(), "never over the cap"); assertEquals(2, t.learn.installed().size)
        assertEquals(c.meta.id, t.store.manifest().rejected.single().id, "the phone can read why")
    }

    @Test fun starterDataIsPartOfTheBudget() {
        val t = tv(starter = 800); val a = lot("a", 1, 200); val b = lot("b", 1, 1)
        assertIs<TvLotStore.Result.Ok>(push(t, a)); t.store.setPriority(listOf(a.meta.id))
        assertIs<TvLotStore.Result.Refused>(push(t, b))
        assertEquals(1000, t.store.usedBytes()); assertEquals(800, t.store.manifest().starterBytes)
    }

    @Test fun evictsOldestNonPriorityOnly() {
        val t = tv(); val a = lot("a", 1, 400); val b = lot("b", 1, 400); val c = lot("c", 1, 400, "quiz")
        push(t, a); push(t, b)
        t.store.setPriority(listOf(a.meta.id))
        val r = push(t, c) as TvLotStore.Result.Ok
        assertEquals(listOf(b.meta.id), r.evicted, "a is priority, b is the old non-priority one")
        assertEquals(setOf("a"), t.learn.held.keys.map { it.scope }.toSet()); assertEquals(1, t.quiz.held.size)
        // priority lots are never evicted for a newcomer, even an older one
        t.store.setPriority(listOf(a.meta.id, c.meta.id)); val d = lot("d", 1, 400)
        assertIs<TvLotStore.Result.Refused>(push(t, d)); assertEquals(800, t.store.usedBytes())
    }

    @Test fun startupBringsTheTvBackUnderTheCap() {
        var starter = 0L
        val dir = Kit.tmp(); val learn = FakeConsumer("learn")
        try {
            val store = TvLotStore(dir, mapOf("learn" to learn), listOf(Kit.pub), 10, { starter }, 1000, { clock++ })
            val a = lot("a", 1, 400); val b = lot("b", 1, 400)
            learn.held[a.meta.id] = a.meta; learn.held[b.meta.id] = b.meta
            starter = 400                                              // an APK update bundles more starter data
            store.setPriority(listOf(b.meta.id))
            val ev = store.startup()
            assertEquals(listOf(a.meta.id), ev); assertTrue(store.usedBytes() <= 1000)
            starter = 1500; assertTrue(store.overBudget(), "the starter data alone is too big: said, not hidden")
        } finally { dir.deleteRecursively() }
    }

    @Test fun refusesWhatTheServerKeyDoesNotSign() {
        val t = tv(); val l = lot("a", 1, 100)
        val name = LotNames.fileName(l.meta)
        val forged = Kit.sign(listOf(l.meta)).copy(signature = "AAAA").toJson()
        t.store.receive(name, 0, 100, l.data)
        assertContains((t.store.installReceived(name, forged) as TvLotStore.Result.Refused).reason, "non signé")
        // signed by another key
        val t2 = tv(keys = listOf(Kit.otherPub)); assertContains((push(t2, l) as TvLotStore.Result.Refused).reason, "non signé")
        assertTrue(t.learn.installed().isEmpty() && t2.learn.installed().isEmpty())
        // a signed catalog that does not contain this lot / this version
        val other = lot("zz", 1, 10)
        t.store.receive(name, 0, 100, l.data)
        assertContains((t.store.installReceived(name, other.proof) as TvLotStore.Result.Refused).reason, "ne contient pas")
    }

    @Test fun refusesTamperedBytesBadSizeTooNewAndDowngrades() {
        val t = tv(app = 5)
        val l = lot("a", 1, 100)
        val bad = L(l.meta, l.data.copyOf().also { it[3] = (it[3] + 1).toByte() }, l.proof)
        assertContains((push(t, bad) as TvLotStore.Result.Refused).reason, "corrompu")
        val newer = lot("n", 1, 50, minApp = 9)
        assertContains((push(t, newer) as TvLotStore.Result.Refused).reason, "USB")
        val v2 = lot("a", 2, 100); assertIs<TvLotStore.Result.Ok>(push(t, v2))
        assertContains((push(t, l) as TvLotStore.Result.Refused).reason, "plus récente")
        assertEquals(2, t.learn.held.values.single().version)
        assertEquals(TvLotStore.Result.Ok(emptyList()), push(t, v2), "same version again: nothing to do")
        assertIs<TvLotStore.Result.Refused>(t.store.installReceived("movie.mp4", "{}"))
    }

    @Test fun aFailedConsumerInstallKeepsThePreviousVersion() {
        val t = tv(); val v1 = lot("a", 1, 100); val v2 = lot("a", 2, 100)
        push(t, v1); t.learn.failInstall = true
        assertContains((push(t, v2) as TvLotStore.Result.Refused).reason, "version précédente reste")
        assertEquals(1, t.learn.held.values.single().version); assertEquals(100, t.store.usedBytes())
        t.learn.failInstall = false; assertIs<TvLotStore.Result.Ok>(push(t, v2)); assertEquals(2, t.learn.held.values.single().version)
        assertTrue(t.store.manifest().rejected.isEmpty(), "a success clears the old refusal")
    }

    @Test fun updateInPlaceFitsAtTheCap() {
        val t = tv(); val v1 = lot("a", 1, 1000); val v2 = lot("a", 2, 1000)
        push(t, v1); assertIs<TvLotStore.Result.Ok>(push(t, v2)); assertEquals(1000, t.store.usedBytes())
    }

    @Test fun chunksResumeAndConflictsTellWhereTheTvStands() {
        val t = tv(); val l = lot("a", 1, 300); val name = LotNames.fileName(l.meta)
        assertEquals(TvLotStore.Chunk.Received(100), t.store.receive(name, 0, 300, l.data.copyOf(100)))
        assertEquals(TvLotStore.Chunk.Conflict(100), t.store.receive(name, 0, 300, l.data.copyOf(100)))
        assertEquals(TvLotStore.Chunk.Conflict(100), t.store.receive(name, 150, 300, ByteArray(10)))
        assertEquals(TvLotStore.Chunk.Conflict(100), t.store.receive(name, 100, 300, ByteArray(250)), "more than announced")
        assertEquals(100, t.store.received(name))
        val again = t.store.reopenReceived(t)                           // TV restarted: the part is still there
        assertEquals(100, again)
        assertIs<TvLotStore.Chunk.Refused>(t.store.receive(name, 0, 2000, ByteArray(1)), "bigger than the whole budget")
        assertIs<TvLotStore.Chunk.Refused>(t.store.receive("../evil", 0, 10, ByteArray(1)))
    }

    private fun TvLotStore.reopenReceived(t: FakeTv) = t.reopen().received(LotNames.fileName(LotId("learn", "a"), 1))

    @Test fun manifestSurvivesARestartWithPriorityAndRefusals() {
        val t = tv(); val a = lot("a", 1, 100); push(t, a); t.store.setPriority(listOf(a.meta.id)); push(t, lot("z", 1, 5000))
        val m = t.reopen().manifest(); assertEquals(listOf(a.meta.id), m.priority); assertEquals(1, m.rejected.size); assertTrue(m.lots.single().installedAt > 0)
    }

    @Test fun apiRoutes() {
        val t = tv(); val l = lot("a", 1, 100); val name = LotNames.fileName(l.meta); val api = t.api
        assertTrue(api.wantsBody("/api/lots/upload") && api.wantsBody("/api/lots/install") && !api.wantsBody("/api/lots"))
        assertEquals(200, api.handle("/api/lots", "GET", emptyMap())!!.status)
        assertNull(api.handle("/api/quiz/packs", "GET", emptyMap()), "other routes are not ours")
        assertEquals(404, api.handle("/api/lots/whatever", "GET", emptyMap())!!.status)
        assertEquals(405, api.handle("/api/lots/upload", "GET", emptyMap())!!.status)
        assertEquals(400, api.handleBody("/api/lots/upload", "POST", mapOf("name" to name), ByteArray(1))!!.status)
        assertEquals(200, api.handleBody("/api/lots/upload", "POST", mapOf("name" to name, "offset" to "0", "total" to "100"), l.data)!!.status)
        assertEquals("""{"received":100}""", api.handle("/api/lots/part", "GET", mapOf("name" to name))!!.json)
        assertEquals(422, api.handleBody("/api/lots/install", "POST", mapOf("name" to name), "{}".toByteArray())!!.status)
        assertEquals(200, api.handle("/api/lots/priority", "POST", mapOf("ids" to "learn:a,bad"))!!.status)
        assertEquals(listOf(LotId("learn", "a")), t.store.manifest().priority)
        t.store.receive(name, 0, 100, l.data)
        assertEquals(200, api.handleBody("/api/lots/install", "POST", mapOf("name" to name), l.proof.toByteArray())!!.status)
        assertEquals(200, api.handle("/api/lots/remove", "POST", mapOf("id" to "learn:a"))!!.status); assertTrue(t.learn.installed().isEmpty())
    }

    @Test fun bluetoothDeliveryLandsAsFilesAndIsAdopted() {
        val t = tv(max = 100_000); val l = lot("a", 1, 3000); val recv = Kit.tmp(); val guard = PinGuard("482913")
        try {
            val transport = Cbt1LotTransport("482913", {
                val c2s = PipedOutputStream(); val tvIn = PipedInputStream(c2s, 1 shl 16)
                val s2c = PipedOutputStream(); val clIn = PipedInputStream(s2c, 1 shl 16)
                thread(isDaemon = true) { runCatching { BtProtocol.serve(recv, tvIn, s2c, guard, "AA:BB", 0) }; runCatching { s2c.close() } }
                object : Link { override val input: InputStream = clIn; override val output: OutputStream = c2s; override fun close() { runCatching { c2s.close() } } }
            }, { })
            val f = File(recv, "src.bin").also { it.writeBytes(l.data) }
            val seen = ArrayList<Long>()
            assertEquals(SendResult.Delivered, transport.send(l.meta, f, l.proof, { seen += it }))
            assertTrue(File(recv, LotNames.fileName(l.meta) + ".json").isFile)
            assertEquals(listOf(LotNames.fileName(l.meta)), t.store.adoptFrom(recv))
            assertEquals(listOf(l.meta), t.learn.installed()); assertFalse(File(recv, LotNames.fileName(l.meta)).exists())
            assertEquals(emptyList(), t.store.adoptFrom(recv), "nothing twice")
            assertFalse(transport.canReadManifest); assertNull(transport.manifest())
        } finally { recv.deleteRecursively() }
    }

    @Test fun httpTransportEndToEndWithResume() {
        val t = tv(max = 5_000_000); val l = lot("a", 1, 1_300_000)
        val seenAuth = ArrayList<String?>(); val posts = ArrayList<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { ex ->
            val path = ex.requestURI.rawPath
            val params = (ex.requestURI.rawQuery ?: "").split('&').filter { it.contains('=') }.associate { java.net.URLDecoder.decode(it.substringBefore('='), "UTF-8") to java.net.URLDecoder.decode(it.substringAfter('='), "UTF-8") }
            seenAuth += ex.requestHeaders.getFirst(castbridge.core.trust.TvAuth.PIN_HEADER)
            posts += "${ex.requestMethod} $path"
            val body = ex.requestBody.readBytes()
            val r = (if (ex.requestMethod == "POST" && t.api.wantsBody(path)) t.api.handleBody(path, "POST", params, body) else t.api.handle(path, ex.requestMethod, params))!!
            val out = r.json.toByteArray(); ex.sendResponseHeaders(r.status, out.size.toLong()); ex.responseBody.use { it.write(out) }
        }
        server.start()
        try {
            val tr = HttpLotTransport("http://127.0.0.1:${server.address.port}", "482913")
            assertEquals(5_000_000, tr.manifest()!!.maxBytes)
            // half of it is already on the TV (a previous attempt over Bluetooth, say): the phone resumes from there
            t.store.receive(LotNames.fileName(l.meta), 0, l.data.size.toLong(), l.data.copyOf(600_000))
            val file = Kit.tmp().resolve("lot.bin").also { it.writeBytes(l.data) }
            val offsets = ArrayList<Long>()
            assertEquals(SendResult.Installed, tr.send(l.meta, file, l.proof, { offsets += it }))
            assertEquals(listOf(l.meta), t.learn.installed())
            assertEquals(600_000L + TvLotApi.CHUNK, offsets.first(), "first chunk of 512 kB continues at 600 000"); assertEquals(1_300_000L, offsets.last())
            assertTrue(tr.setPriority(listOf(l.meta.id)) && tr.remove(l.meta.id))
            assertTrue(seenAuth.all { it != null }, "authenticated on every call"); assertTrue(posts.none { it.contains("482913") }, "never in the URL")
            // the TV is gone
            server.stop(0)
            assertNull(tr.manifest()); assertIs<SendResult.LinkDown>(tr.send(l.meta, file, l.proof, { }))
        } finally { server.stop(0) }
    }
}

/** w1-02: durable index / queue writes (fsync + .bak) and tolerant reads. */
class LotsDurabilityTest {
    private val dirs = ArrayList<File>()
    private fun dir() = Kit.tmp().also { dirs += it }
    @AfterTest fun tearDown() { dirs.forEach { it.deleteRecursively() } }
    private val a = LotId("learn", "a"); private val b = LotId("learn", "b")
    private fun open(d: File, warn: (String) -> Unit = {}) = TvLotStore(d, emptyMap(), listOf(Kit.pub), 10, { 0L }, 1000, { 5000L }, warn)

    @Test fun tvIndexIsKeptAcrossReopenAndWithoutTemporaryFile() {
        val d = dir(); open(d).setPriority(listOf(a, b))
        assertEquals(listOf(a, b), open(d).manifest().priority)
        assertFalse(File(d, "lots.json.tmp").exists())
    }

    @Test fun tvIndexTruncatedOrEmptyFallsBackToTheBackupAndWarns() {
        val d = dir(); val s = open(d); s.setPriority(listOf(a)); s.setPriority(listOf(a, b))     // .bak = the previous good index
        for (damage in listOf("", "{\"priority\":[", "garbage")) {
            File(d, "lots.json").writeText(damage)
            val warnings = ArrayList<String>()
            assertEquals(listOf(a), open(d) { warnings += it }.manifest().priority, "damage=<$damage>")
            assertEquals(1, warnings.size); assertTrue("secours" in warnings[0])
        }
    }

    @Test fun deliveryQueueFileIsDurableAndFallsBackToItsBackup() {
        val f = File(dir(), "q/queue.json"); val st = FileQueueStore(f)
        st.save("""{"deliveries":[],"seen":[],"n":1}"""); st.save("""{"deliveries":[],"seen":[],"n":2}""")
        assertTrue(st.load()!!.contains("\"n\":2")); assertFalse(File(f.path + ".tmp").exists())
        f.writeText("")                                    // power cut: empty main file
        assertTrue(st.load()!!.contains("\"n\":1"), "the last good copy is read")
        f.writeText("{trunc"); assertTrue(st.load()!!.contains("\"n\":1"))
        assertNull(FileQueueStore(File(dir(), "none.json")).load())
    }

    @Test fun playedListIsRewrittenAtomically() {
        val d = dir(); castbridge.core.tv.Storage.markPlayed(d, "a.mp4"); castbridge.core.tv.Storage.markPlayed(d, "b.mp4")
        assertEquals(setOf("a.mp4", "b.mp4"), castbridge.core.tv.Storage.playedNames(d))
        castbridge.core.tv.Storage.forget(d, "a.mp4")
        assertEquals(setOf("b.mp4"), castbridge.core.tv.Storage.playedNames(d)); assertFalse(File(d, ".played.tmp").exists())
    }
}
