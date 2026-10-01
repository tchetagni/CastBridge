package castbridge.core.parental.tab

import castbridge.core.parental.StoredReport
import java.time.LocalDate
import java.time.ZoneId

internal val UTC: ZoneId = ZoneId.of("UTC")
internal fun ms(day: String, h: Int = 12, m: Int = 0, z: ZoneId = UTC) = LocalDate.parse(day).atTime(h, m).atZone(z).toInstant().toEpochMilli()

internal fun daily(id: String, day: String, ts: Long, pid: String = "p1", name: String = "Léa", play: Long = 0, games: Long = 0, dl: Long = 0, apps: Long = 0, sup: String? = "active",
                   limit: Int = 60, byApp: List<Map<String, Any?>> = emptyList(), blocked: List<Map<String, Any?>> = emptyList(), events: List<Map<String, Any?>>? = null,
                   tamper: List<Map<String, Any?>> = emptyList(), tv: String = "TV salon", at: Long = ts, window: String? = null, learn: Map<String, Any?>? = null): StoredReport {
    val b = linkedMapOf<String, Any?>("v" to 1, "type" to "daily", "tv" to tv, "day" to day, "profile" to linkedMapOf("id" to pid, "name" to name),
        "totalMin" to play + games + dl + apps, "kinds" to linkedMapOf("play" to play, "games" to games, "downloads" to dl, "apps" to apps), "apps" to byApp,
        "limitMin" to limit, "window" to window, "blocked" to blocked, "tamper" to tamper, "supervision" to sup?.let { linkedMapOf("state" to it, "label" to it) })
    if (events != null) b["events"] = events
    if (learn != null) b["learn"] = learn
    return StoredReport(id, tv, ts, "daily", b, false, at)
}

internal fun weekly(id: String, ts: Long, days: Map<String, Long>, pid: String = "p1", name: String = "Léa", tv: String = "TV salon", sup: String? = "active", at: Long = ts) =
    StoredReport(id, tv, ts, "weekly", linkedMapOf("type" to "weekly", "tv" to tv, "profile" to linkedMapOf("id" to pid, "name" to name),
        "days" to days.map { (d, t) -> linkedMapOf("day" to d, "totalMin" to t) }, "supervision" to sup?.let { linkedMapOf("state" to it, "label" to it) }), false, at)

internal fun alert(id: String, ts: Long, kind: String, text: String, pid: String? = "p1", tv: String = "TV salon", at: Long = ts) =
    StoredReport(id, tv, ts, "alert", linkedMapOf("type" to "alert", "alert" to kind, "tv" to tv, "profile" to pid?.let { linkedMapOf("id" to it, "name" to "Léa") }, "text" to text, "pkg" to "com.x", "label" to "X"), false, at)

internal fun ev(id: String, ts: Long, t: String, title: String, min: Int? = null, score: String? = null) =
    linkedMapOf<String, Any?>("id" to id, "ts" to ts, "t" to t, "title" to title, "min" to min, "score" to score)

internal fun newLedger(now: () -> Long = { ms("2026-09-30") }) = ParentalLedger(castbridge.core.parental.MemoryInboxPersistence(), Retention(), now)
