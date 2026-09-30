package castbridge.core

import castbridge.core.net.JsonLite
import castbridge.core.telemetry.Consent
import castbridge.core.telemetry.EventQueue
import castbridge.core.telemetry.Telemetry
import castbridge.core.telemetry.TelemetryUploader
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.util.zip.GZIPInputStream
import kotlin.test.*

class TelemetryTest {
    private fun tmp() = File(kotlin.io.path.createTempDirectory("tel").toFile(), "events.jsonl")

    @Test
    fun consentCatalogAndForbiddenKeys() {
        val q = EventQueue(tmp())
        var consent = Consent.ESSENTIAL
        var now = 1_000_000L
        val t = Telemetry("tv", 8, q, { consent }, { now })
        assertFalse(t.featureUsed("quiz"), "usage statistics not accepted")
        assertTrue(t.error("library", "io", "x"), "errors are essential")
        consent = Consent.USAGE
        t.startSession()
        assertTrue(t.featureUsed("quiz", "remote"))
        assertFalse(t.featureUsed("teleport"), "not a TV feature")
        assertFalse(t.featureUsed("send"), "a phone feature")
        assertFalse(t.track("mystery"))
        assertFalse(t.track("playback_end", mapOf("ms" to 1000, "title" to "Mon film")), "forbidden key: dropped on the device")
        assertTrue(t.track("playback_end", mapOf("ms" to 1000, "pct" to 50.5, "codec" to "h264", "extra" to "ignored")))
        now += 60_000
        t.endSession()
        val events = q.peek().map { JsonLite.obj(it) }
        assertEquals(listOf("error", "session_start", "feature_used", "playback_end", "session_end"), events.map { it["name"] })
        @Suppress("UNCHECKED_CAST")
        val pb = events[3]["props"] as Map<String, Any?>
        assertEquals(setOf("ms", "pct", "codec"), pb.keys)
        @Suppress("UNCHECKED_CAST")
        assertEquals(60_000L, (events[4]["props"] as Map<String, Any?>)["ms"])
        assertEquals(events[1]["sessionId"], events[2]["sessionId"])
        assertNull(events[0]["sessionId"])
        assertEquals(16, Telemetry.hashForCounting("Mon film.mkv", "sel-aleatoire").length)
    }

    @Test
    fun boundedPersistentQueue() {
        val f = tmp()
        val q = EventQueue(f, maxBytes = 10_000)
        repeat(500) { q.add("""{"n":$it,"pad":"${"x".repeat(40)}"}""") }
        assertTrue(f.length() <= 10_000)
        val first = JsonLite.obj(q.peek(1).single())["n"] as Long
        assertTrue(first > 300, "the oldest are dropped first: $first")
        val again = EventQueue(f, maxBytes = 10_000)
        assertEquals(q.size(), again.size(), "survives a restart")
        again.drop(3)
        assertEquals(first + 3, JsonLite.obj(again.peek(1).single())["n"])
    }

    @Test
    fun uploadsGzipBatchesAndKeepsThemOnFailure() {
        val received = ArrayList<Map<String, Any?>>()
        var status = 200
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/v1/events/batch") { ex ->
            assertEquals("gzip", ex.requestHeaders.getFirst("Content-Encoding"))
            assertEquals("Bearer tok", ex.requestHeaders.getFirst("Authorization"))
            val body = GZIPInputStream(ex.requestBody).readBytes().toString(Charsets.UTF_8)
            val m = JsonLite.obj(body)
            val out = if (status == 200) {
                received += m
                """{"accepted":${(m["events"] as List<*>).size},"duplicates":0,"rejected":0,"errors":[]}"""
            } else """{"status":$status,"message":"non"}"""
            val b = out.toByteArray()
            ex.sendResponseHeaders(status, b.size.toLong())
            ex.responseBody.use { it.write(b) }
        }
        server.start()
        try {
            val q = EventQueue(tmp())
            val t = Telemetry("phone", 5, q, { Consent.USAGE })
            repeat(1_203) { t.featureUsed("send") }
            val up = TelemetryUploader("http://127.0.0.1:${server.address.port}")
            status = 503
            assertTrue(up.flush(q, "tok", "phone", 5) is TelemetryUploader.Result.Failed)
            assertEquals(1_203, q.size(), "kept for the next flush")
            status = 401
            assertEquals(TelemetryUploader.Result.NeedsRegistration, up.flush(q, "tok", "phone", 5))
            status = 200
            val r = up.flush(q, "tok", "phone", 5) as TelemetryUploader.Result.Sent
            assertEquals(1_203, r.accepted)
            assertEquals(3, r.batches, "500 + 500 + 203")
            assertEquals(0, q.size())
            assertEquals("phone", received[0]["app"])
            assertEquals(500, (received[0]["events"] as List<*>).size)
            assertTrue(TelemetryUploader.shouldFlush(0, TelemetryUploader.FLUSH_EVERY_MS, 1))
            assertFalse(TelemetryUploader.shouldFlush(0, 60_000, 10))
        } finally {
            server.stop(0)
        }
    }
}
