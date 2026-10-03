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
    fun theStoreIsAFeatureOfBothApps() {
        val q = EventQueue(tmp())
        val tv = Telemetry("tv", 8, q, { Consent.USAGE }, { 1_000_000L })
        val phone = Telemetry("phone", 8, q, { Consent.USAGE }, { 1_000_000L })
        assertTrue(tv.featureUsed("store")); assertTrue(phone.featureUsed("store"))
        assertFalse(tv.featureUsed("boutique"), "only the listed id")
    }

    private val rentalProps = mapOf(
        "rental_start" to mapOf("bundle" to "pack-fr", "unit" to "hours", "amount" to 12, "maxMinutes" to 720),
        "rental_use" to mapOf("bundle" to "pack-fr", "unit" to "hours", "minutes" to 95),
        "rental_end" to mapOf("bundle" to "pack-fr", "unit" to "days", "reason" to "date", "usedMinutes" to 300, "maxMinutes" to 4320),
        "rental_extend" to mapOf("bundle" to "pack-fr", "unit" to "days", "amount" to 2),
        "rental_survey" to mapOf("unit" to "hours", "q" to "price", "answer" to "yes"),
    )

    @Test
    fun rentalEventsNeedUsageConsent() {
        var consent = Consent.ESSENTIAL
        val q = EventQueue(tmp())
        val t = Telemetry("phone", 8, q, { consent }, { 1_000_000L })
        for ((name, props) in rentalProps) assertFalse(t.track(name, props), "$name needs the usage consent")
        assertEquals(0, q.size())
        consent = Consent.USAGE
        for ((name, props) in rentalProps) assertTrue(t.track(name, props), "$name accepted with consent")
        assertEquals(rentalProps.keys.toList(), q.peek().map { JsonLite.obj(it)["name"] })
        for (n in rentalProps.keys) assertFalse(n in castbridge.core.telemetry.EventCatalog.ESSENTIAL)
    }

    @Test
    fun rentalEventsRejectForbiddenAndUnknownProps() {
        val q = EventQueue(tmp())
        val t = Telemetry("phone", 8, q, { Consent.USAGE }, { 1_000_000L })
        assertFalse(t.track("rental_start", mapOf("bundle" to "pack-fr", "email" to "a@b.c")), "forbidden key")
        assertFalse(t.track("rental_use", mapOf("bundle" to "pack-fr", "name" to "x")), "forbidden key")
        assertTrue(t.track("rental_end", mapOf("bundle" to "pack-fr", "contractId" to "C-123", "deviceId" to "d", "licenseId" to "L", "reason" to "usage")))
        @Suppress("UNCHECKED_CAST")
        val props = JsonLite.obj(q.peek().single())["props"] as Map<String, Any?>
        assertEquals(setOf("bundle", "reason"), props.keys, "no contract, device or license id survives")
        assertFalse(t.track("rental_pause", mapOf("bundle" to "pack-fr")), "unknown event")
        assertFalse(t.rentalSurvey("hours", "price", "yes", childProfile = true), "no survey under a child profile")
        assertTrue(t.rentalSurvey("hours", "price", "yes"))
        assertEquals(2, q.size())
    }

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
