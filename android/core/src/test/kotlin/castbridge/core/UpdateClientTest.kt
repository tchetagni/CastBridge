package castbridge.core

import castbridge.core.net.JsonLite
import castbridge.core.update.UpdateClient
import castbridge.core.update.UpdateManifest
import castbridge.core.update.UpdateSchedule
import castbridge.core.update.UpdateSchedule.Trigger
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.random.Random
import kotlin.test.*

/** A fake update server: signed manifest + APK download that cuts the first transfer in the middle. */
class FakeUpdateServer(private val key: KeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()) : AutoCloseable {
    val apk: ByteArray = Random(3).nextBytes(300_000)
    val sha: String = MessageDigest.getInstance("SHA-256").digest(apk).joinToString("") { "%02x".format(it) }
    val publicKey: String = Base64.getEncoder().encodeToString(key.public.encoded.copyOfRange(12, 44))
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    val base = "http://127.0.0.1:${server.address.port}"
    val requests = CopyOnWriteArrayList<String>()
    @Volatile var latestCode = 200
    @Volatile var cutFirstDownloadAt = 120_000
    @Volatile var tamper = false
    @Volatile var mandatory = false
    private var downloads = 0

    init {
        server.createContext("/api/v1/updates/tv/latest") { ex ->
            requests += "latest ${ex.requestURI.rawQuery} auth=${ex.requestHeaders.getFirst("Authorization")}"
            if (latestCode != 200) return@createContext reply(ex, latestCode, ByteArray(0))
            val m = manifest()
            val json = JsonLite.write(linkedMapOf("app" to m.app, "channel" to m.channel, "abi" to m.abi, "versionCode" to m.versionCode,
                "versionName" to m.versionName, "url" to (if (tamper) "$base/dl/evil.apk" else m.url), "sha256" to m.sha256, "size" to m.size,
                "minSdk" to m.minSdk, "notes" to m.notes, "mandatory" to m.mandatory, "minSupportedVersionCode" to m.minSupportedVersionCode,
                "publishedAt" to m.publishedAt, "signature" to m.signature))
            reply(ex, 200, json.toByteArray())
        }
        server.createContext("/dl/tv/app-42.apk") { ex ->
            val range = ex.requestHeaders.getFirst("Range")
            requests += "dl range=$range ifRange=${ex.requestHeaders.getFirst("If-Range")}"
            downloads++
            val start = range?.removePrefix("bytes=")?.substringBefore('-')?.toInt() ?: 0
            ex.responseHeaders.add("ETag", "\"$sha\"")
            if (start > 0) ex.responseHeaders.add("Content-Range", "bytes $start-${apk.size - 1}/${apk.size}")
            ex.sendResponseHeaders(if (start > 0) 206 else 200, (apk.size - start).toLong())
            ex.responseBody.use { out ->
                if (downloads == 1 && cutFirstDownloadAt in 1 until apk.size) {
                    out.write(apk, 0, cutFirstDownloadAt); out.flush()
                    ex.close() // connection cut in the middle (Bluetooth link lost)
                    return@createContext
                }
                out.write(apk, start, apk.size - start)
            }
        }
        server.start()
    }

    fun manifest(): UpdateManifest {
        val unsigned = UpdateManifest("tv", "stable", "armeabi-v7a", 42, "0.6", "$base/dl/tv/app-42.apk", sha, apk.size.toLong(), 26,
            "Notes « FR »", mandatory, 30, "2026-09-30T10:00:00+01:00", null, "")
        val sig = Signature.getInstance("Ed25519").run { initSign(key.private); update(unsigned.canonicalPayload().toByteArray()); sign() }
        return unsigned.copy(signature = Base64.getEncoder().encodeToString(sig))
    }

    private fun reply(ex: HttpExchange, code: Int, body: ByteArray) {
        ex.sendResponseHeaders(code, if (code == 204 || body.isEmpty()) -1 else body.size.toLong())
        ex.responseBody.use { if (body.isNotEmpty()) it.write(body) }
    }

    override fun close() = server.stop(0)
}

class UpdateClientTest {
    private val me = UpdateClient.Installed(versionCode = 31, supportedAbis = listOf("armeabi-v7a", "armeabi"), sdk = 34,
        deviceId = "0f0e0d0c-0000-4000-8000-000000000001", deviceToken = "tok")

    @Test
    fun checkVerifyAndResumeAfterACut() = FakeUpdateServer().use { srv ->
        val client = UpdateClient(srv.base, "tv", listOf(srv.publicKey), sleep = {})
        val check = client.check(me)
        assertTrue(check is UpdateClient.Check.Available, check.toString())
        assertFalse(check.mandatory)
        assertTrue(srv.requests[0].contains("abis=armeabi-v7a%2Carmeabi"), srv.requests[0])
        assertTrue(srv.requests[0].contains("versionCode=31") && srv.requests[0].contains("auth=Bearer tok"), srv.requests[0])

        val dir = kotlin.io.path.createTempDirectory("upd").toFile()
        val seen = ArrayList<Long>()
        val file = client.download(check.manifest, dir, progress = { done, _ -> seen += done })
        assertContentEquals(srv.apk, file.readBytes())
        assertEquals("castbridge-tv-42.apk", file.name)
        // first transfer cut at 120 000 bytes, the second one resumed from there with If-Range on the hash
        val dl = srv.requests.filter { it.startsWith("dl") }
        assertEquals(2, dl.size, dl.toString())
        assertEquals("dl range=null ifRange=null", dl[0])
        assertEquals("dl range=bytes=120000- ifRange=\"${srv.sha}\"", dl[1])
        assertEquals(srv.apk.size.toLong(), seen.last())
        assertFalse(File(dir, "castbridge-tv-42.apk.part").exists())
        // already there and valid: no new download
        client.download(check.manifest, dir)
        assertEquals(2, srv.requests.count { it.startsWith("dl") })
    }

    @Test
    fun refusesWhatTheKeyDoesNotSign() = FakeUpdateServer().use { srv ->
        srv.tamper = true
        val other = KeyPairGenerator.getInstance("Ed25519").generateKeyPair().public.encoded.copyOfRange(12, 44)
        val r = UpdateClient(srv.base, "tv", listOf(srv.publicKey)).check(me)
        assertTrue(r is UpdateClient.Check.Failed && !r.retryable && "signature" in r.reason, r.toString())
        srv.tamper = false
        assertTrue(UpdateClient(srv.base, "tv", listOf(Base64.getEncoder().encodeToString(other))).check(me) is UpdateClient.Check.Failed)
        assertTrue(UpdateClient(srv.base, "tv", emptyList()).check(me) is UpdateClient.Check.Failed)
        // two keys during a key change: the right one is enough
        assertTrue(UpdateClient(srv.base, "tv", listOf(Base64.getEncoder().encodeToString(other), srv.publicKey)).check(me) is UpdateClient.Check.Available)
    }

    @Test
    fun upToDateBlockedMandatoryAndErrors() = FakeUpdateServer().use { srv ->
        val c = UpdateClient(srv.base, "tv", listOf(srv.publicKey))
        assertEquals(UpdateClient.Check.UpToDate, c.check(me.copy(versionCode = 42)))
        srv.latestCode = 204
        assertEquals(UpdateClient.Check.UpToDate, c.check(me))
        srv.latestCode = 403
        assertEquals(UpdateClient.Check.Blocked, c.check(me))
        srv.latestCode = 503
        assertTrue((c.check(me) as UpdateClient.Check.Failed).retryable)
        srv.latestCode = 200
        assertTrue((c.check(me.copy(versionCode = 20)) as UpdateClient.Check.Available).mandatory, "below minSupportedVersionCode 30")
        srv.mandatory = true
        assertTrue((c.check(me) as UpdateClient.Check.Available).mandatory)
        assertTrue(c.check(me.copy(sdk = 21)) is UpdateClient.Check.Failed, "minSdk 26")
        val down = UpdateClient("http://127.0.0.1:1", "tv", listOf(srv.publicKey)).check(me)
        assertTrue(down is UpdateClient.Check.Failed && down.retryable)
    }

    @Test
    fun corruptDownloadIsRejected() = FakeUpdateServer().use { srv ->
        srv.cutFirstDownloadAt = 0
        val m = srv.manifest().copy(sha256 = "00".repeat(32))
        val dir = kotlin.io.path.createTempDirectory("upd").toFile()
        val e = assertFailsWith<java.io.IOException> { UpdateClient(srv.base, "tv", listOf(srv.publicKey), sleep = {}).download(m, dir, maxAttempts = 2) }
        assertTrue("SHA-256" in e.message!!, e.message)
        assertFalse(File(dir, "castbridge-tv-42.apk").exists())
    }
}

class UpdateScheduleTest {
    private val h = UpdateSchedule.HOUR
    private val s = UpdateSchedule(deviceSeed = 1234)

    @Test
    fun startupThenEvery12hWithJitterNeverMoreThanHourly() {
        var st = UpdateSchedule.State()
        assertTrue(s.isDue(st, 1_000, Trigger.STARTUP))
        assertTrue(s.isDue(st, 1_000, Trigger.TIMER), "never checked")
        val t0 = 10 * h
        st = s.onSuccess(st, t0)
        assertFalse(s.isDue(st, t0 + 30 * 60_000, Trigger.STARTUP), "restart loop: at most once per hour")
        assertFalse(s.isDue(st, t0 + 30 * 60_000, Trigger.FORCED))
        assertTrue(s.isDue(st, t0 + h + 1, Trigger.FORCED))
        val next = s.nextCheckAt(st)
        assertTrue(next in (t0 + 11 * h)..(t0 + 13 * h), "12 h ± 1 h")
        assertEquals(next, s.nextCheckAt(st), "stable jitter")
        assertFalse(s.isDue(st, next - 1, Trigger.TIMER))
        assertTrue(s.isDue(st, next, Trigger.TIMER))
        // devices spread out
        val others = (1..20).map { UpdateSchedule(deviceSeed = it.toLong()).nextCheckAt(st) }.toSet()
        assertTrue(others.size > 10)
    }

    @Test
    fun exponentialBackoffCappedAt12h() {
        var st = UpdateSchedule.State()
        val t0 = 100 * h
        val delays = (1..7).map { st = s.onFailure(st, t0); s.nextCheckAt(st) - t0 }
        assertTrue(delays[0] >= h, "at least one hour")
        assertTrue(delays[1] > delays[0] && delays[2] > delays[1])
        assertTrue(delays.last() <= 12 * h + h / 4)
        assertEquals(7, st.failures)
        st = s.onSuccess(st, t0 + 1)
        assertEquals(0, st.failures)
        assertEquals(st, UpdateSchedule.State.decode(st.encode()))
        assertEquals(UpdateSchedule.State(), UpdateSchedule.State.decode("garbage"))
    }
}
