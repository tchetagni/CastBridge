package castbridge.core

import castbridge.core.device.DeviceClient
import castbridge.core.device.DeviceFacts
import castbridge.core.device.DeviceIdentity
import castbridge.core.device.DeviceReport
import castbridge.core.device.DeviceStore
import castbridge.core.device.Platform
import castbridge.core.net.JsonLite
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.*

class DeviceReportTest {
    private open class Tv : DeviceFacts {
        override val app = "tv"
        override val installId = "3b241101-e2bb-4255-8caf-4136c566a962"
        override val androidId: String? = "9774d56d682e549c"
        override val versionCode = 7
        override val versionName = "0.5"
        override val supportedAbis = listOf("armeabi-v7a", "armeabi")
        override val sdkInt = 34
        override val manufacturer: String? = "Hisense"
        override val model: String? = "43A4K"
        override val buildDisplay: String? = "GaiaOS 3.2 V0000.01.00"
        override val fingerprint: String? = "Hisense/HTV/43A4K:14/UP1A/1:user/release-keys"
        override val hasLeanback: Boolean? = true
        override val screenWidthPx = 1920
        override val screenHeightPx = 1080
        override val densityDpi = 320
        override val ramTotalBytes = 1_610_612_736L
        override val storageFreeBytes = 2_147_483_648L
        override val storageTotalBytes = 8_589_934_592L
        override val usbPresent = true
        override val usbFreeBytes = 31_457_280_000L
        override val btGatewayActive = true
        override val videoCount = 12
        override val lastError = "x".repeat(900)
    }

    @Test
    fun collectsGenericFactsAndHashesTheAndroidId() {
        val r = DeviceReport.collect(Tv(), salt = "castbridge-tv")
        assertEquals(DeviceIdentity.hashAndroidId("9774d56d682e549c", "castbridge-tv"), r.androidIdHash)
        assertEquals(64, r.androidIdHash!!.length)
        assertFalse(r.toJson().contains("9774d56d682e549c"), "the raw ANDROID_ID never leaves the device")
        assertEquals("android-tv", r.platform)
        assertEquals("GaiaOS", r.osName)
        assertEquals("armeabi-v7a", r.abi)
        assertEquals("1920x1080", r.screen)
        assertEquals(1536, r.ramTotalMb)
        assertEquals(30000, r.usbFreeMb)
        assertEquals(500, r.lastError!!.length)
        val json = JsonLite.obj(r.toJson())
        assertEquals(listOf("armeabi-v7a", "armeabi"), json["supportedAbis"])
        assertNull(json["sshEnabled"], "unknown values are not sent")
        assertEquals(7L, json["versionCode"])
    }

    @Test
    fun platformsOfAllKindsOfDevices() {
        fun detect(f: DeviceFacts) = Platform.detect(f).key to Platform.osName(f, Platform.detect(f))
        assertEquals("fire-os" to "Fire OS", detect(object : Tv() { override val manufacturer = "Amazon"; override val model = "AFTSSS"; override val buildDisplay = null; override val fingerprint = null }))
        assertEquals("google-tv" to "Google TV", detect(object : Tv() { override val hasGoogleTv = true; override val buildDisplay = null; override val fingerprint = null }))
        assertEquals("android-tv" to "Android TV", detect(object : Tv() { override val buildDisplay = "QP1A.191105"; override val fingerprint = "Xiaomi/mitv/x:11/user" }))
        assertEquals("android-box", detect(object : Tv() { override val hasLeanback = false; override val hasTouchscreen = false; override val buildDisplay = null }).first)
        assertEquals("phone", detect(object : Tv() { override val app = "phone"; override val hasLeanback = null; override val hasTelephony = true }).first)
        assertEquals("tablet", detect(object : Tv() { override val app = "phone"; override val hasLeanback = null; override val hasTelephony = false; override val smallestWidthDp = 720 }).first)
        assertEquals("other", detect(object : Tv() { override val hasLeanback = null }).first)
        assertEquals(36, DeviceIdentity.newInstallId().length)
    }
}

class DeviceClientTest {
    private class MemStore : DeviceStore {
        override var deviceId: String? = null
        override var deviceToken: String? = null
    }

    private val report = DeviceReport.collect(object : DeviceFacts {
        override val app = "tv"; override val installId = "3b241101-e2bb-4255-8caf-4136c566a962"; override val versionCode = 7
    }, "salt")

    @Test
    fun registersThenHeartbeatsAndReRegistersWhenTheTokenIsUnknown() {
        val calls = CopyOnWriteArrayList<String>()
        var validToken = "t1"
        var registrations = 0
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        fun send(ex: com.sun.net.httpserver.HttpExchange, code: Int, body: String) {
            val b = body.toByteArray()
            ex.sendResponseHeaders(code, if (b.isEmpty()) -1 else b.size.toLong())
            ex.responseBody.use { if (b.isNotEmpty()) it.write(b) }
        }
        server.createContext("/api/v1/devices/register") { ex ->
            val body = String(ex.requestBody.readBytes())
            calls += "register ${JsonLite.obj(body)["installId"]}"
            registrations++
            validToken = "t$registrations"
            send(ex, 201, """{"deviceId":"dev-1","deviceToken":"$validToken","heartbeatSeconds":900,
                "directives":{"serverTime":"2026-09-30T10:00:00+01:00","checkUpdate":false,"blocked":false,"channel":"stable","heartbeatSeconds":900}}""")
        }
        server.createContext("/api/v1/devices/heartbeat") { ex ->
            val auth = ex.requestHeaders.getFirst("Authorization")
            calls += "heartbeat $auth"
            if (auth != "Bearer $validToken") send(ex, 401, """{"status":401,"message":"Jeton d'appareil inconnu"}""")
            else send(ex, 200, """{"serverTime":"x","checkUpdate":true,"blocked":false,"channel":"beta","heartbeatSeconds":600}""")
        }
        server.createContext("/api/v1/devices/crash") { ex ->
            calls += "crash " + JsonLite.obj(String(ex.requestBody.readBytes()))["message"]
            send(ex, 204, "")
        }
        server.start()
        try {
            val store = MemStore()
            val c = DeviceClient("http://127.0.0.1:${server.address.port}/", store)
            // no token yet: registers
            assertEquals("stable", c.heartbeat(report).channel)
            assertEquals("dev-1", store.deviceId)
            assertEquals("t1", store.deviceToken)
            val d = c.heartbeat(report)
            assertTrue(d.checkUpdate)
            assertEquals("beta", d.channel)
            assertEquals(600, d.heartbeatSeconds)
            // the server forgot the token (device deleted by the admin): registers again, transparently
            validToken = "other"
            assertTrue(c.heartbeat(report).checkUpdate)
            assertEquals("t2", store.deviceToken)
            c.crash(report, "Plantage du lecteur", "at X")
            assertEquals(listOf("register 3b241101-e2bb-4255-8caf-4136c566a962", "heartbeat Bearer t1", "heartbeat Bearer t1",
                "register 3b241101-e2bb-4255-8caf-4136c566a962", "heartbeat Bearer t2", "crash Plantage du lecteur"), calls)
        } finally {
            server.stop(0)
        }
    }
}

class JsonLiteTest {
    @Test
    fun roundTrip() {
        val v = JsonLite.parse("""{"a":[1,2.5,-3e2,true,null,"é\"\\\né"],"b":{},"c":[]}""") as Map<*, *>
        assertEquals(listOf(1L, 2.5, -300.0, true, null, "é\"\\\né"), v["a"])
        assertEquals(v, JsonLite.parse(JsonLite.write(v)))
        assertFailsWith<JsonLite.ParseError> { JsonLite.parse("{\"a\":1") }
        assertFailsWith<JsonLite.ParseError> { JsonLite.parse("[1,]x") }
    }
}
