package castbridge.core.tv

import castbridge.core.FakePlayer
import castbridge.core.net.JsonLite
import castbridge.core.owner.DeviceCode
import castbridge.core.owner.FactorKind
import castbridge.core.owner.Fingerprints
import castbridge.core.owner.OwnerFrames
import castbridge.core.owner.TrialPolicy
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.test.*

/** `GET /api/tv/device-request` on the real [ReceiverServer]: authenticated like every route, five keys and nothing else, bounded. */
class TvDeviceRequestServerTest {
    private val dir = kotlin.io.path.createTempDirectory("tvdr").toFile()
    private val fp = Fingerprints(mapOf(FactorKind.FLASH to "0a1b2c3d4e5f60718293a4b5c6d7e8f9", FactorKind.WIFI to "fedcba9876543210fedcba9876543210"))
    private val pub = ByteArray(32) { (it + 3).toByte() }
    private val canaries = listOf("CANARY-WIFIMAC-77", "CANARY-TOKEN-cbk_42", "CANARY-OWNERPW-13", "CANARY-MODEL-Bravia")
    /** The TV's request plus lines of OTHER things it knows (a newer TV may add lines): none of them may leave. */
    private var text: String? = OwnerFrames.deviceInfo(DeviceCode.of(fp), fp, pub) + "\nmodel=${canaries[3]}\nwifimac=${canaries[0]}\ntoken=${canaries[1]}\nowner=${canaries[2]}"
    private val server = ReceiverServer(VolumeRegistry.single(File(dir, "tv")), FakePlayer(), 0, pin = "123456",
        extension = TvDeviceRequestApi { text }, tokenAuth = { if (it == "cbk_ok") "Mon téléphone" else null }).apply { start(5000, false) }
    private val port = server.listeningPort

    @AfterTest fun tearDown() { server.stop(); dir.deleteRecursively() }

    private fun call(method: String = "GET", headers: Map<String, String> = mapOf("X-CB-Pin" to "123456")): Pair<Int, String> {
        val c = URL("http://127.0.0.1:$port${TvDeviceRequestApi.PATH}").openConnection() as HttpURLConnection
        c.requestMethod = method; c.connectTimeout = 4000; c.readTimeout = 4000
        headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
        if (method == "POST") { c.doOutput = true; c.setFixedLengthStreamingMode(0); c.outputStream.close() }
        val code = c.responseCode
        return code to ((if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty())
    }

    @Test fun authenticationIsRequired() {
        assertEquals(401, call(headers = emptyMap()).first)
        assertEquals(401, call(headers = mapOf("X-CB-Pin" to "000000")).first)
        assertEquals(401, call(headers = mapOf("X-CB-Token" to "cbk_nope")).first)
        assertEquals(200, call(headers = mapOf("X-CB-Pin" to "123456")).first)
        assertEquals(200, call(headers = mapOf("X-CB-Token" to "cbk_ok")).first, "a trusted phone's token opens it, no PIN")
    }

    @Test fun onlyTheFiveKeysAndNoOtherFieldOfTheTv() {
        val (st, body) = call()
        assertEquals(200, st)
        val j = JsonLite.obj(body)
        assertEquals(setOf("code", "k", "factors", "install"), j.keys, "strict whitelist")
        assertEquals(DeviceCode.of(fp), j["code"]); assertEquals(2, (j["k"] as Number).toInt())
        @Suppress("UNCHECKED_CAST") val factors = j["factors"] as List<Map<String, Any?>>
        assertEquals(2, factors.size)
        assertTrue(factors.all { it.keys == setOf("type", "fingerprint") })
        assertEquals(pub.joinToString("") { "%02x".format(it) }, j["install"])
        for (c in canaries) assertFalse(c in body, "$c must not leave the TV")
        assertFalse("123456" in body)
    }

    @Test fun answerIsBounded() { assertTrue(call().second.length < 1024) }

    @Test fun unavailableOrUnreadableRequestIs503() {
        text = null; assertEquals(503, call().first)
        text = "code=pas-un-code\nk=2"; assertEquals(503, call().first)
    }

    @Test fun getOnly() { assertEquals(405, call("POST").first) }

    @Test fun theTrialTvAnswersItToo() { assertTrue(TrialPolicy.routeAllowed(TvDeviceRequestApi.PATH), "a trial TV is precisely the one that is activated") }
}
