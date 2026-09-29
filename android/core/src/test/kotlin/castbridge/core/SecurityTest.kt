package castbridge.core

import castbridge.core.tv.*
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import kotlin.test.*

class SecurityTest {
    @Test fun pinFormatAndConstantTimeMatch() {
        repeat(50) { assertTrue(Pin.isValidFormat(Pin.generate()), "generated PIN has 6 digits") }
        assertFalse(Pin.isValidFormat("12345")); assertFalse(Pin.isValidFormat("12345a"))
        assertTrue(Pin.matches("123456", "123456"))
        assertFalse(Pin.matches("123456", "123457"))
        assertFalse(Pin.matches("123456", null))
        assertFalse(Pin.matches("123456", "1234567"))
    }

    @Test fun locksAfterFiveFailuresForSixtySecondsPerIp() {
        var t = 0L
        val g = PinGuard("123456", now = { t })
        repeat(4) { assertEquals(PinGuard.Result.BAD, g.check("1.1.1.1", "000000")) }
        assertEquals(PinGuard.Result.LOCKED, g.check("1.1.1.1", "000000"))
        assertEquals(PinGuard.Result.LOCKED, g.check("1.1.1.1", "123456"), "right PIN refused while locked")
        assertEquals(PinGuard.Result.OK, g.check("2.2.2.2", "123456"), "other IPs unaffected")
        t = 59_000; assertEquals(PinGuard.Result.LOCKED, g.check("1.1.1.1", "123456"))
        assertEquals(1, g.retryAfterSeconds("1.1.1.1"))
        t = 60_001; assertEquals(PinGuard.Result.OK, g.check("1.1.1.1", "123456"))
        assertEquals(PinGuard.Result.BAD, g.check("1.1.1.1", "x"))  // counter was reset
    }

    @Test fun successResetsFailureCounter() {
        val g = PinGuard("123456")
        repeat(4) { g.check("a", "0") }
        assertEquals(PinGuard.Result.OK, g.check("a", "123456"))
        repeat(4) { assertEquals(PinGuard.Result.BAD, g.check("a", "0")) }
    }

    private fun http(url: String, method: String = "GET", pin: String? = null): Int =
        (URL(url).openConnection() as HttpURLConnection).run {
            requestMethod = method; if (pin != null) setRequestProperty("X-CB-Pin", pin)
            if (method == "POST") { doOutput = true; setFixedLengthStreamingMode(0); outputStream.close() }
            responseCode
        }

    @Test fun serverRequiresPinExceptRootAndHello() {
        val dir = kotlin.io.path.createTempDirectory("tvp").toFile()
        val port = ServerSocket(0).use { it.localPort }
        val s = ReceiverServer(dir, FakePlayer(), port, profile = TvProfile(minFreeBytes = 0), pin = "246810").apply { start(5000, false) }
        val b = "http://127.0.0.1:$port"
        try {
            assertEquals(200, http("$b/"))
            assertEquals(200, http("$b/api/hello"))
            assertEquals(401, http("$b/api/info"))
            assertEquals(200, http("$b/api/info", pin = "246810"))
            assertEquals(200, http("$b/api/info?pin=246810"))
            // Client: wrong PIN -> 401, upload treats it as fatal (no endless retry)
            assertEquals(401, assertFailsWith<TvClient.HttpError> { TvClient(b, "111111").info() }.code)
            assertEquals(200, http("$b/api/info?pin=246810"), "still allowed after one failure")
            val r = ResumableUpload("v.mp4", 3, { b }, { ByteArrayInputStream(ByteArray(3)) }, sleep = {}, pin = "222222").run {}
            assertTrue(r is ResumableUpload.State.Failed, "$r")
            val ok = ResumableUpload("v.mp4", 3, { b }, { ByteArrayInputStream(ByteArray(3)) }, sleep = {}, pin = "246810").run {}
            assertEquals(ResumableUpload.State.Done, ok)
            assertTrue(File(dir, "v.mp4").isFile)
        } finally { s.stop(); dir.deleteRecursively() }
    }
}
