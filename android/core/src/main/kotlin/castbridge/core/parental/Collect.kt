package castbridge.core.parental

import castbridge.core.net.JsonLite
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/*
 * « Toute la TV » collection structures (DESIGN-W6-PARENTAL-PHONE-GATE § 2.2, § 2.3). Pure, bounded, JVM-tested. The TV glue (w6-13) feeds them from the
 * existing 15 s tick; the v2 reports (w6-07) read them. What is stored: package name, label, minutes per hour slot, launch counts, screen on/off
 * segments, connection kinds with a short sanitized label. NEVER: anything that happens inside an application, an address, a network name.
 * Writes: at most one per minute per structure ([Buffered.flushIfDue]); a structure that did not change writes nothing (screen off = no write).
 */

/** Serialization base: [dirty] is set by every change; [flushIfDue] writes at most once per [minIntervalMs]; [flushNow] on day change and at shutdown. */
abstract class Buffered(protected val store: KvStore, private val key: String) {
    @Volatile var dirty = false
        protected set
    private var lastFlushMs: Long? = null

    protected abstract fun serialize(): String

    /** Writes when something changed and the last write is old enough. Returns true when a write happened. */
    @Synchronized fun flushIfDue(nowMs: Long, minIntervalMs: Long = 60_000): Boolean {
        if (!dirty) return false
        val last = lastFlushMs
        if (last != null && nowMs - last in 0 until minIntervalMs) return false
        return flushNow(nowMs)
    }

    /** Unconditional write when dirty (day change, shutdown). */
    @Synchronized fun flushNow(nowMs: Long = System.currentTimeMillis()): Boolean {
        if (!dirty) return false
        store.put(key, serialize()); dirty = false; lastFlushMs = nowMs
        return true
    }

    protected fun touch() { dirty = true }
}

internal object CollectDays {
    fun dayOf(ms: Long, zone: ZoneId): String = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().toString()
    fun prev(day: String): String = LocalDate.parse(day).minusDays(1).toString()

    /** Drops the days older than [keep] days before the newest key. Unparsable keys are dropped. */
    fun <T> prune(m: MutableMap<String, T>, keep: Int) {
        val newest = m.keys.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.maxOrNull() ?: run { m.clear(); return }
        val limit = newest.minusDays(keep.toLong() - 1)
        m.keys.removeAll { k -> runCatching { LocalDate.parse(k) }.getOrNull().let { it == null || it.isBefore(limit) } }
    }

    @Suppress("UNCHECKED_CAST")
    fun loadMap(store: KvStore, key: String): Map<String, Any?> =
        store.get(key)?.let { runCatching { JsonLite.parse(it) as? Map<String, Any?> }.getOrNull() } ?: emptyMap()

    fun int(v: Any?): Int = (v as? Number)?.toInt() ?: 0
    fun ints(v: Any?): List<Int> = (v as? List<*>)?.map { int(it) } ?: emptyList()
}

/** One application on one day: minutes per hour [slots] (24), total [minutes], [launches]. */
data class AppSlots(val pkg: String, val label: String, val slots: IntArray, val minutes: Int, val launches: Int) {
    override fun equals(other: Any?) = other is AppSlots && pkg == other.pkg && label == other.label && slots.contentEquals(other.slots) && minutes == other.minutes && launches == other.launches
    override fun hashCode() = pkg.hashCode() * 31 + slots.contentHashCode()
}

/**
 * Minutes per application, per hour of day, per day (7 days kept), with launch counts. At most [MAX_APPS] applications per day: the next ones are
 * folded under pkg [OTHERS]. Internally seconds (a 15 s tick must not be lost); exposed in minutes (rounded). A TV without usage access simply never
 * calls [add]: the structure stays empty (the report then says « indisponible »).
 */
class UsageSlots(store: KvStore, private val now: () -> Long = System::currentTimeMillis, private val zone: ZoneId = ZoneId.systemDefault()) : Buffered(store, KEY) {
    private class Cell(var label: String, val sec: IntArray = IntArray(24), var launches: Int = 0)
    private val days = LinkedHashMap<String, LinkedHashMap<String, Cell>>()
    private var loaded = false

    /**
     * Attributes [dtMs] to [pkg]. [minuteOfDay] is the minute of day at the END of the interval (the tick); the interval is [end - dt, end] and is split
     * on hour slots and on midnight (the part before midnight goes to the previous day, slot 23). [day] comes from the caller's clock.
     */
    @Synchronized fun add(day: String, pkg: String, label: String, minuteOfDay: Int, dtMs: Long) {
        load()
        if (dtMs <= 0 || pkg.isBlank()) return
        val endSec = minuteOfDay.coerceIn(0, 1439) * 60L + 30L          // middle of the minute: stable under 15 s ticks
        var remaining = (dtMs / 1000L).coerceAtMost(86_400L)
        if (remaining == 0L) remaining = 1
        var curDay = day
        var pos = endSec
        while (remaining > 0) {
            if (pos <= 0) { curDay = CollectDays.prev(curDay); pos = 86_400L }
            val slot = ((pos - 1) / 3600L).toInt().coerceIn(0, 23)
            val slotStart = slot * 3600L
            val take = minOf(remaining, pos - slotStart)
            cell(curDay, pkg, label).sec[slot] += take.toInt()
            remaining -= take; pos -= take
        }
        CollectDays.prune(days, RETENTION_DAYS); touch()
    }

    /** One launch (a package change in the foreground). */
    @Synchronized fun launch(day: String, pkg: String, label: String = pkg) {
        load()
        if (pkg.isBlank()) return
        cell(day, pkg, label).launches++
        CollectDays.prune(days, RETENTION_DAYS); touch()
    }

    @Synchronized fun forDay(day: String): List<AppSlots> {
        load()
        return days[day]?.map { (pkg, c) ->
            val slots = IntArray(24) { (c.sec[it] + 30) / 60 }
            AppSlots(pkg, c.label.ifBlank { pkg }, slots, (c.sec.sum() + 30) / 60, c.launches)
        }?.sortedByDescending { it.minutes } ?: emptyList()
    }

    @Synchronized fun days(): List<String> { load(); return days.keys.sorted() }

    private fun cell(day: String, pkg: String, label: String): Cell {
        val d = days.getOrPut(day) { LinkedHashMap() }
        val key = if (d.containsKey(pkg) || d.keys.count { it != OTHERS } < MAX_APPS) pkg else OTHERS
        return d.getOrPut(key) { Cell(if (key == OTHERS) "Autres applications" else label.take(60).ifBlank { pkg }) }
    }

    private fun load() {
        if (loaded) return
        loaded = true
        for ((day, v) in CollectDays.loadMap(store, KEY)) {
            val apps = v as? Map<*, *> ?: continue
            val d = LinkedHashMap<String, Cell>()
            for ((pkg, c) in apps) {
                val m = c as? Map<*, *> ?: continue
                val s = CollectDays.ints(m["s"])
                d[pkg.toString()] = Cell(m["l"]?.toString() ?: pkg.toString(), IntArray(24) { s.getOrElse(it) { 0 } }, CollectDays.int(m["n"]))
            }
            days[day] = d
        }
    }

    override fun serialize(): String = JsonLite.write(days.mapValues { (_, d) -> d.mapValues { (_, c) -> linkedMapOf("l" to c.label, "s" to c.sec.toList(), "n" to c.launches) } })

    companion object {
        const val KEY = "usageslots"
        const val OTHERS = "autres"
        const val MAX_APPS = 60
        const val RETENTION_DAYS = 7
    }
}

/** One on-segment, in seconds of the day. */
data class Seg(val fromSec: Int, val toSec: Int)

/** The screen of one day: total [onMin] (exact, never lowered by merging) and the (merged) [segments]. */
data class Segments(val day: String, val onMin: Int, val segments: List<Seg>)

/**
 * Screen on/off segments (PowerManager.isInteractive read by the tick). Opens when the screen turns on, closes when it turns off, cut at midnight.
 * At most [MAX_SEGMENTS] per day (the shortest are merged into their nearest neighbour; the total stays exact). Screen off and unchanged = nothing
 * written. 7 days kept.
 */
class ScreenSegments(store: KvStore, private val now: () -> Long = System::currentTimeMillis, private val zone: ZoneId = ZoneId.systemDefault()) : Buffered(store, KEY) {
    private class DayData(var onSec: Int = 0, val segs: MutableList<IntArray> = ArrayList())
    private val days = LinkedHashMap<String, DayData>()
    private var openFromMs: Long? = null
    private var loaded = false

    @Synchronized fun observe(screenOn: Boolean, nowMs: Long) {
        load()
        val open = openFromMs
        if (screenOn) {
            if (open == null) { openFromMs = nowMs; touch(); return }
            if (CollectDays.dayOf(open, zone) != CollectDays.dayOf(nowMs, zone)) { closeAt(open, midnightOf(nowMs)); openFromMs = midnightOf(nowMs); touch() }   // day change: cut
        } else if (open != null) { closeAt(open, nowMs); openFromMs = null; touch() }
    }

    @Synchronized fun forDay(day: String): Segments {
        load()
        val d = days[day]
        val extra = openFromMs?.let { o -> openPartOn(day, o, now()) } ?: 0
        val segs = (d?.segs ?: emptyList<IntArray>()).map { Seg(it[0], it[1]) }.toMutableList()
        openFromMs?.let { o -> openSeg(day, o, now())?.let { segs += it } }
        return Segments(day, ((d?.onSec ?: 0) + extra + 30) / 60, segs)
    }

    private fun midnightOf(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()

    private fun secOfDay(day: String, ms: Long): Int = ((ms - LocalDate.parse(day).atStartOfDay(zone).toInstant().toEpochMilli()) / 1000L).coerceIn(0, 86_400).toInt()

    private fun openSeg(day: String, fromMs: Long, toMs: Long): Seg? {
        if (toMs <= fromMs) return null
        val start = LocalDate.parse(day).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = LocalDate.parse(day).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val a = maxOf(fromMs, start); val b = minOf(toMs, end)
        return if (b > a) Seg(secOfDay(day, a), secOfDay(day, b)) else null
    }

    private fun openPartOn(day: String, fromMs: Long, toMs: Long): Int = openSeg(day, fromMs, toMs)?.let { it.toSec - it.fromSec } ?: 0

    /** Closes [fromMs, toMs], one piece per day crossed. */
    private fun closeAt(fromMs: Long, toMs: Long) {
        if (toMs <= fromMs) return
        var day = LocalDate.parse(CollectDays.dayOf(fromMs, zone))
        val last = LocalDate.parse(CollectDays.dayOf(toMs, zone))
        var guard = 0
        while (!day.isAfter(last) && guard++ < 8) {
            openSeg(day.toString(), fromMs, toMs)?.let { s ->
                val d = days.getOrPut(day.toString()) { DayData() }
                d.onSec += s.toSec - s.fromSec
                d.segs += intArrayOf(s.fromSec, s.toSec)
                while (d.segs.size > MAX_SEGMENTS) mergeShortest(d.segs)
            }
            day = day.plusDays(1)
        }
        CollectDays.prune(days, RETENTION_DAYS)
    }

    private fun mergeShortest(l: MutableList<IntArray>) {
        val i = l.indices.minByOrNull { l[it][1] - l[it][0] } ?: return
        val gapPrev = if (i > 0) l[i][0] - l[i - 1][1] else Int.MAX_VALUE
        val gapNext = if (i < l.size - 1) l[i + 1][0] - l[i][1] else Int.MAX_VALUE
        if (gapPrev == Int.MAX_VALUE && gapNext == Int.MAX_VALUE) return
        if (gapPrev <= gapNext) { l[i - 1][1] = l[i][1]; l.removeAt(i) } else { l[i + 1][0] = l[i][0]; l.removeAt(i) }
    }

    private fun load() {
        if (loaded) return
        loaded = true
        val root = CollectDays.loadMap(store, KEY)
        openFromMs = (root["open"] as? Number)?.toLong()
        for ((day, v) in (root["d"] as? Map<*, *>) ?: emptyMap<Any, Any>()) {
            val m = v as? Map<*, *> ?: continue
            val d = DayData(CollectDays.int(m["on"]))
            val flat = CollectDays.ints(m["s"])
            for (k in 0 until flat.size / 2) d.segs += intArrayOf(flat[2 * k], flat[2 * k + 1])
            days[day.toString()] = d
        }
    }

    override fun serialize(): String = JsonLite.write(linkedMapOf("open" to openFromMs, "d" to days.mapValues { (_, d) -> linkedMapOf("on" to d.onSec, "s" to d.segs.flatMap { listOf(it[0], it[1]) }) }))

    companion object { const val KEY = "screensegs"; const val MAX_SEGMENTS = 200; const val RETENTION_DAYS = 7 }
}

/** Kinds of connection to the TV. */
object ConnectionKind { const val PHONE = "phone"; const val USB = "usb"; const val SSH = "ssh"; const val REMOTE_ASSIST = "remote_assist"; val ALL = setOf(PHONE, USB, SSH, REMOTE_ASSIST) }

/** One kind of connection of one day: [count] accepted notes between [firstMs] and [lastMs]. */
data class ConnEntry(val kind: String, val label: String, val count: Int, val firstMs: Long, val lastMs: Long)

/**
 * Connections to the TV: kind + short label + counts and times. NEVER an address: the label is sanitized (hex/MAC addresses, IPv4, long digit runs
 * removed; 40 characters max). One note per minute per (kind, label); 14 days kept; at most [MAX_PER_DAY] distinct entries per day.
 */
class ConnectionLog(store: KvStore, private val zone: ZoneId = ZoneId.systemDefault()) : Buffered(store, KEY) {
    private val days = LinkedHashMap<String, MutableList<ConnEntry>>()
    private var loaded = false

    /** Returns false when the note was ignored (unknown kind, duplicate within a minute, day full). */
    @Synchronized fun note(kind: String, label: String, nowMs: Long): Boolean {
        load()
        if (kind !in ConnectionKind.ALL) return false
        val clean = sanitize(label).ifEmpty { kind }
        val l = days.getOrPut(CollectDays.dayOf(nowMs, zone)) { ArrayList() }
        val i = l.indexOfFirst { it.kind == kind && it.label == clean }
        if (i >= 0) {
            val e = l[i]
            if (nowMs - e.lastMs in 0 until 60_000) return false
            l[i] = e.copy(count = e.count + 1, firstMs = minOf(e.firstMs, nowMs), lastMs = maxOf(e.lastMs, nowMs))
        } else {
            if (l.size >= MAX_PER_DAY) return false
            l += ConnEntry(kind, clean, 1, nowMs, nowMs)
        }
        CollectDays.prune(days, RETENTION_DAYS); touch(); return true
    }

    @Synchronized fun forDay(day: String): List<ConnEntry> { load(); return days[day]?.toList() ?: emptyList() }

    private fun load() {
        if (loaded) return
        loaded = true
        for ((day, v) in CollectDays.loadMap(store, KEY)) {
            val l = ArrayList<ConnEntry>()
            for (e in (v as? List<*>) ?: emptyList<Any>()) {
                val m = e as? Map<*, *> ?: continue
                val k = m["k"]?.toString() ?: continue
                if (k !in ConnectionKind.ALL) continue
                l += ConnEntry(k, m["l"]?.toString() ?: k, CollectDays.int(m["c"]), (m["f"] as? Number)?.toLong() ?: 0L, (m["t"] as? Number)?.toLong() ?: 0L)
            }
            days[day] = l
        }
    }

    override fun serialize(): String = JsonLite.write(days.mapValues { (_, l) -> l.map { linkedMapOf("k" to it.kind, "l" to it.label, "c" to it.count, "f" to it.firstMs, "t" to it.lastMs) } })

    companion object {
        const val KEY = "connlog"
        const val MAX_PER_DAY = 40
        const val RETENTION_DAYS = 14
        private val HEX_ADDR = Regex("(?i)\\b[0-9a-f]{1,4}([:-][0-9a-f]{1,4}){2,}\\b")
        private val IPV4 = Regex("\\b\\d{1,3}(\\.\\d{1,3}){3}\\b")
        private val LONG_DIGITS = Regex("\\d{9,}")

        /** Removes whatever looks like an address; control characters and colons are dropped, 40 characters kept. */
        fun sanitize(label: String): String =
            label.replace(HEX_ADDR, " ").replace(IPV4, " ").replace(LONG_DIGITS, " ").filter { it >= ' ' && it != ':' }
                .replace(Regex("\\s+"), " ").trim().take(40).trim()
    }
}

/** Apps added and removed on one day. */
data class InventoryDelta(val added: List<InstalledApp>, val removed: List<InstalledApp>)

/**
 * Detects the REMOVAL of applications (the engine's syncInstalled already reports new ones) and keeps new/removed per day (14 days). The first
 * call only records the baseline: nothing is reported as new at first start.
 */
class AppInventoryDiff(store: KvStore) : Buffered(store, KEY) {
    private var known: LinkedHashMap<String, String>? = null
    private val added = LinkedHashMap<String, MutableList<InstalledApp>>()
    private val removed = LinkedHashMap<String, MutableList<InstalledApp>>()
    private var loaded = false

    @Synchronized fun apply(day: String, installed: List<InstalledApp>): InventoryDelta {
        load()
        val now = LinkedHashMap<String, String>().also { m -> installed.forEach { m[it.pkg] = it.label } }
        val prev = known
        known = now
        if (prev == null) { touch(); return InventoryDelta(emptyList(), emptyList()) }
        val a = installed.filter { it.pkg !in prev }
        val r = prev.filterKeys { it !in now }.map { (p, l) -> InstalledApp(p, l) }
        if (a.isNotEmpty()) added.getOrPut(day) { ArrayList() }.addAll(a.filter { n -> added[day]?.none { it.pkg == n.pkg } != false })
        if (r.isNotEmpty()) removed.getOrPut(day) { ArrayList() }.addAll(r.filter { n -> removed[day]?.none { it.pkg == n.pkg } != false })
        CollectDays.prune(added, RETENTION_DAYS); CollectDays.prune(removed, RETENTION_DAYS)
        if (a.isNotEmpty() || r.isNotEmpty()) touch()
        return InventoryDelta(a, r)
    }

    @Synchronized fun forDay(day: String): InventoryDelta { load(); return InventoryDelta(added[day]?.toList() ?: emptyList(), removed[day]?.toList() ?: emptyList()) }

    private fun apps(v: Any?): MutableList<InstalledApp> = ((v as? List<*>) ?: emptyList<Any>()).mapNotNull { e -> (e as? Map<*, *>)?.let { InstalledApp(it["p"].toString(), it["l"]?.toString() ?: it["p"].toString()) } }.toMutableList()

    private fun load() {
        if (loaded) return
        loaded = true
        val root = CollectDays.loadMap(store, KEY)
        (root["known"] as? Map<*, *>)?.let { k -> known = LinkedHashMap<String, String>().also { m -> k.forEach { (p, l) -> m[p.toString()] = l.toString() } } }
        (root["add"] as? Map<*, *>)?.forEach { (d, v) -> added[d.toString()] = apps(v) }
        (root["rem"] as? Map<*, *>)?.forEach { (d, v) -> removed[d.toString()] = apps(v) }
    }

    private fun enc(m: Map<String, List<InstalledApp>>) = m.mapValues { (_, l) -> l.map { linkedMapOf("p" to it.pkg, "l" to it.label) } }
    override fun serialize(): String = JsonLite.write(linkedMapOf("known" to known, "add" to enc(added), "rem" to enc(removed)))

    companion object { const val KEY = "invdiff"; const val RETENTION_DAYS = 14 }
}

/** Minutes of one application over a day (aggregate). */
data class AggApp(val pkg: String, val min: Int)

/** One aggregated day: screen-on minutes (null = not measured) and minutes per application. */
data class DayAgg(val day: String, val screenOnMin: Int?, val apps: List<AggApp>) { val appsMin: Int get() = apps.sumOf { it.min } }

/** A week of aggregates: totals and the apps summed over the days present. [daysPresent] < 7 = incomplete (the report must say so). */
data class WeekAgg(val days: List<DayAgg>, val daysPresent: Int, val screenOnMin: Int, val apps: List<AggApp>)

/** 35 days of per-day aggregates (minutes per day per application, screen total) for the weekly comparison. */
class DailyAggregates(store: KvStore) : Buffered(store, KEY) {
    private val days = LinkedHashMap<String, DayAgg>()
    private var loaded = false

    /** Replaces the aggregate of [day] from that day's slots and screen segments ([screenOfDay] null = screen not measured). */
    @Synchronized fun roll(day: String, slotsOfDay: List<AppSlots>, screenOfDay: Segments?): DayAgg {
        load()
        val agg = DayAgg(day, screenOfDay?.onMin, slotsOfDay.filter { it.minutes > 0 }.map { AggApp(it.pkg, it.minutes) }.sortedByDescending { it.min })
        days[day] = agg
        CollectDays.prune(days, RETENTION_DAYS); touch()
        return agg
    }

    @Synchronized fun get(day: String): DayAgg? { load(); return days[day] }

    /** The aggregates of [wanted] days (missing days are skipped, and counted in [WeekAgg.daysPresent]). */
    @Synchronized fun week(wanted: List<String>): WeekAgg {
        load()
        val l = wanted.mapNotNull { days[it] }
        val apps = l.flatMap { it.apps }.groupBy { it.pkg }.map { (p, v) -> AggApp(p, v.sumOf { it.min }) }.sortedByDescending { it.min }
        return WeekAgg(l, l.size, l.sumOf { it.screenOnMin ?: 0 }, apps)
    }

    private fun load() {
        if (loaded) return
        loaded = true
        for ((day, v) in CollectDays.loadMap(store, KEY)) {
            val m = v as? Map<*, *> ?: continue
            val flat = (m["a"] as? List<*>) ?: emptyList<Any>()
            val apps = flat.mapNotNull { e -> (e as? List<*>)?.let { if (it.size == 2) AggApp(it[0].toString(), CollectDays.int(it[1])) else null } }
            days[day] = DayAgg(day, (m["s"] as? Number)?.toInt(), apps)
        }
    }

    override fun serialize(): String = JsonLite.write(days.mapValues { (_, d) -> linkedMapOf("s" to d.screenOnMin, "a" to d.apps.map { listOf(it.pkg, it.min) }) })

    companion object { const val KEY = "dailyagg"; const val RETENTION_DAYS = 35 }
}
