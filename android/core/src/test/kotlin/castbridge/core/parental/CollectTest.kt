package castbridge.core.parental

import java.time.ZoneId
import kotlin.test.*

/** KvStore spy: counts writes. */
private class SpyKv : KvStore {
    val m = LinkedHashMap<String, String>(); var puts = 0
    override fun get(key: String) = m[key]
    override fun put(key: String, value: String?) { puts++; if (value == null) m.remove(key) else m[key] = value }
}

class CollectTest {
    private val utc = ZoneId.of("UTC")
    private fun at(day: String, h: Int, m: Int = 0) = java.time.LocalDate.parse(day).atTime(h, m).atZone(utc).toInstant().toEpochMilli()

    @Test fun dtSplitsOnHourSlotsAndMidnight() {
        val u = UsageSlots(SpyKv(), zone = utc)
        // tick at 10:00 (minuteOfDay 600, taken as 10:00:30) with 15 min: 9:45:30..10:00:30 -> slot 9 gets 14.5 min, slot 10 gets 0.5
        u.add("2026-09-30", "yt", "YouTube", 600, 15 * 60_000L)
        val a = u.forDay("2026-09-30").single()
        assertEquals(15, a.minutes); assertEquals(15, a.slots[9]); assertEquals(1, a.slots[10])      // 14.5 min and 0.5 min, rounded per slot
        // across midnight: 00:02 tick (00:02:30), 10 min -> 2.5 min on the new day slot 0, 7.5 min on the previous day slot 23
        u.add("2026-10-01", "yt", "YouTube", 2, 10 * 60_000L)
        val p = u.forDay("2026-09-30").single(); val n = u.forDay("2026-10-01").single()
        assertEquals(15 + 8, p.minutes); assertEquals(8, p.slots[23]); assertEquals(3, n.slots[0])
    }

    @Test fun capsAppsRetentionAndLaunches() {
        val u = UsageSlots(SpyKv(), zone = utc)
        for (i in 1..61) u.add("2026-09-30", "p$i", "App $i", 700, 60_000L)
        val l = u.forDay("2026-09-30")
        assertEquals(61, l.size); assertEquals(60, l.count { it.pkg != "autres" }); assertEquals(1, l.count { it.pkg == "autres" })
        u.launch("2026-09-30", "p1", "App 1"); assertEquals(1, u.forDay("2026-09-30").first { it.pkg == "p1" }.launches)
        for (d in 1..8) u.add("2026-10-0$d", "p1", "", 100, 60_000L)
        assertEquals(7, u.days().size); assertEquals("2026-10-08", u.days().last()); assertFalse(u.days().contains("2026-09-30"))
        assertEquals("p1", u.forDay("2026-10-08").single().label)         // no label: pkg
    }

    @Test fun serializationIsCompactAndRoundTrips() {
        val kv = SpyKv(); val u = UsageSlots(kv, zone = utc)
        for (i in 1..40) for (h in 0..23) u.add("2026-09-30", "com.x.app$i", "App $i", h * 60 + 30, 3 * 60_000L)
        u.flushNow(0)
        val size = kv.m[UsageSlots.KEY]!!.length
        println("COLLECT usageslots 40 apps x 24 slots = $size bytes")
        assertTrue(size < 6_000, "size=$size")
        val u2 = UsageSlots(kv, zone = utc)
        assertEquals(u.forDay("2026-09-30").map { it.minutes }, u2.forDay("2026-09-30").map { it.minutes })
    }

    @Test fun flushAtMostOncePerMinuteAndNothingWhenUnchanged() {
        val kv = SpyKv(); val u = UsageSlots(kv, zone = utc)
        assertFalse(u.flushIfDue(0)); assertEquals(0, kv.puts)                 // nothing changed: no write
        u.add("2026-09-30", "a", "A", 10, 15_000); assertTrue(u.flushIfDue(1_000)); assertEquals(1, kv.puts)
        u.add("2026-09-30", "a", "A", 10, 15_000); assertFalse(u.flushIfDue(30_000)); assertTrue(u.dirty)
        assertTrue(u.flushIfDue(61_000)); assertEquals(2, kv.puts)
        u.add("2026-09-30", "a", "A", 10, 15_000); assertTrue(u.flushNow(62_000)); assertEquals(3, kv.puts)   // day change / shutdown
    }

    @Test fun screenOffWritesNothing() {
        val kv = SpyKv(); val s = ScreenSegments(kv, zone = utc)
        repeat(20) { s.observe(false, at("2026-09-30", 3) + it * 15_000L); s.flushIfDue(at("2026-09-30", 3) + it * 15_000L) }
        assertEquals(0, kv.puts); assertFalse(s.dirty)
    }

    @Test fun screenSegmentsOnOffAndMidnightAndCap() {
        val s = ScreenSegments(SpyKv(), { at("2026-10-01", 0, 30) }, utc)
        s.observe(true, at("2026-09-30", 20)); s.observe(true, at("2026-09-30", 20, 10))
        s.observe(false, at("2026-09-30", 21))
        assertEquals(60, s.forDay("2026-09-30").onMin); assertEquals(1, s.forDay("2026-09-30").segments.size)
        s.observe(true, at("2026-09-30", 23, 30)); s.observe(true, at("2026-10-01", 0, 10)); s.observe(false, at("2026-10-01", 0, 20))
        assertEquals(60 + 30, s.forDay("2026-09-30").onMin); assertEquals(20, s.forDay("2026-10-01").onMin)
        assertEquals(86_400, s.forDay("2026-09-30").segments.last().toSec)
        // cap: 201 short segments in one day -> 200, total exact
        val c = ScreenSegments(SpyKv(), { at("2026-10-02", 23, 59) }, utc)
        for (i in 0 until 201) { c.observe(true, at("2026-10-02", 0) + i * 300_000L); c.observe(false, at("2026-10-02", 0) + i * 300_000L + 120_000L) }
        val d = c.forDay("2026-10-02")
        assertEquals(200, d.segments.size); assertEquals(402, d.onMin)
    }

    @Test fun screenRoundTripKeepsOpenSegment() {
        val kv = SpyKv(); val s = ScreenSegments(kv, { at("2026-09-30", 11) }, utc)
        s.observe(true, at("2026-09-30", 10)); s.flushNow(0)
        val s2 = ScreenSegments(kv, { at("2026-09-30", 11) }, utc)
        assertEquals(60, s2.forDay("2026-09-30").onMin)
    }

    @Test fun connectionLogRefusesAddressesAndDedups() {
        val kv = SpyKv(); val c = ConnectionLog(kv, utc)
        assertTrue(c.note("phone", "Téléphone de Léa AA:BB:CC:DD:EE:FF", 1_000))
        val e = c.forDay("1970-01-01").single()
        assertEquals("Téléphone de Léa", e.label)
        assertFalse(ConnectionLog.sanitize("192.168.1.20 clé 123456789012").contains(Regex("\\d{3}")))
        assertEquals("", ConnectionLog.sanitize("AA:BB:CC:DD:EE:FF"))
        assertEquals(40, ConnectionLog.sanitize("x".repeat(100)).length)
        assertFalse(c.note("phone", "Téléphone de Léa", 30_000))                     // same minute
        assertTrue(c.note("phone", "Téléphone de Léa", 70_000)); assertEquals(2, c.forDay("1970-01-01").single().count)
        assertFalse(c.note("bluetooth_mac", "x", 5_000_000))                          // unknown kind
        assertTrue(c.note("usb", "AA:BB:CC:DD:EE:FF", 5_000_000)); assertTrue(c.forDay("1970-01-01").any { it.kind == "usb" && it.label == "usb" })
        c.flushNow(0); assertFalse(Regex("(?i)[0-9a-f]{2}:[0-9a-f]{2}:").containsMatchIn(kv.m[ConnectionLog.KEY]!!))
        assertEquals(c.forDay("1970-01-01"), ConnectionLog(kv, utc).forDay("1970-01-01"))
    }

    @Test fun connectionLogRetention14Days() {
        val c = ConnectionLog(SpyKv(), utc)
        for (d in 0 until 16) c.note("ssh", "Mac", d * 86_400_000L + 1000)
        assertTrue(c.forDay("1970-01-01").isEmpty()); assertTrue(c.forDay("1970-01-16").isNotEmpty())
        assertTrue(c.forDay("1970-01-03").isNotEmpty())
    }

    @Test fun inventoryDiffSeesRemovals() {
        val kv = SpyKv(); val i = AppInventoryDiff(kv)
        val a = InstalledApp("a", "A"); val b = InstalledApp("b", "B"); val n = InstalledApp("n", "N")
        assertTrue(i.apply("2026-09-30", listOf(a, b)).let { it.added.isEmpty() && it.removed.isEmpty() })   // baseline
        val d = i.apply("2026-09-30", listOf(a, n))
        assertEquals(listOf("n"), d.added.map { it.pkg }); assertEquals(listOf("b"), d.removed.map { it.pkg })
        i.flushNow(0)
        val j = AppInventoryDiff(kv)
        assertEquals(listOf("b"), j.forDay("2026-09-30").removed.map { it.pkg })
        assertEquals(listOf("n"), j.apply("2026-10-01", listOf(a)).removed.map { it.pkg })
    }

    @Test fun dailyAggregatesRollAndWeek() {
        val g = DailyAggregates(SpyKv()); val u = UsageSlots(SpyKv(), zone = utc); val s = ScreenSegments(SpyKv(), { at("2026-09-30", 23) }, utc)
        u.add("2026-09-30", "yt", "YouTube", 600, 30 * 60_000L); u.add("2026-09-30", "nf", "Netflix", 700, 10 * 60_000L)
        s.observe(true, at("2026-09-30", 9)); s.observe(false, at("2026-09-30", 11))
        val a = g.roll("2026-09-30", u.forDay("2026-09-30"), s.forDay("2026-09-30"))
        assertEquals(120, a.screenOnMin); assertEquals(listOf("yt", "nf"), a.apps.map { it.pkg }); assertEquals(40, a.appsMin)
        g.roll("2026-09-29", u.forDay("2026-09-30"), null)
        val w = g.week(listOf("2026-09-28", "2026-09-29", "2026-09-30"))
        assertEquals(2, w.daysPresent); assertEquals(120, w.screenOnMin); assertEquals(60, w.apps.first { it.pkg == "yt" }.min)
        val keep = DailyAggregates(SpyKv())
        for (d in 0 until 40) keep.roll(java.time.LocalDate.parse("2026-08-01").plusDays(d.toLong()).toString(), emptyList(), null)
        assertNull(keep.get("2026-08-01")); assertNotNull(keep.get("2026-09-09")); assertNull(keep.get("2026-08-05"))
    }

    @Test fun noNetworkReferenceInCollect() {
        val src = java.io.File("src/main/kotlin/castbridge/core/parental/Collect.kt").readText()
        assertFalse(Regex("TvConnect|ServerLink|HttpLite").containsMatchIn(src))
    }
}
