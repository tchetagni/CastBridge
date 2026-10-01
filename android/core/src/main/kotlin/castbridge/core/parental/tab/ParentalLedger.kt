package castbridge.core.parental.tab

import castbridge.core.net.JsonLite
import castbridge.core.parental.InboxPersistence
import castbridge.core.parental.StoredReport
import java.time.Instant
import java.time.ZoneId

/** Retention of the phone's local copy: bounded in age AND in size (a TV that talks a lot cannot fill the phone). */
data class Retention(val days: Int = DEFAULT_DAYS, val maxEvents: Int = 20_000, val maxFacts: Int = 6_000) {
    companion object { const val DEFAULT_DAYS = 90; val CHOICES = listOf(30, 60, 90, 180, 365) }
}

/** What the phone knows about one TV. [lastReportTs]: TV clock of its newest report; [lastReceivedAt]: phone clock when it arrived. */
data class TvInfo(val name: String, val lastReportTs: Long, val lastReceivedAt: Long)

/** A profile as last seen in a report. [deleted]: the TV no longer lists it (see [ParentalLedger.markProfiles]); its history stays until purged. */
data class ProfileInfo(val id: String, val name: String, val deleted: Boolean = false)

/**
 * The phone's local copy of everything the TVs reported: the single source of every screen of the Parental tab. It absorbs the reports of
 * the inbox ([castbridge.core.parental.ReportInbox], bounded to 150 reports) into a longer-lived, deduplicated and bounded store.
 *
 * Rules (all tested):
 *  - idempotent: the same report absorbed twice, or the same event inside two different reports, counts once (dedupe by report id and event id);
 *  - order-independent: reports arriving late or out of order give the same result (for one day the newest report of the TV wins, a
 *    daily report wins over a weekly total for the detail; events are a union);
 *  - a day nobody reported stays ABSENT: it is a gap, never a zero;
 *  - a purge is remembered: what was purged is not brought back when the inbox is absorbed again;
 *  - a profile renamed on the TV shows its newest name everywhere, a deleted one keeps its history under « profil supprimé ».
 * Pure logic over [InboxPersistence] (a private file on the phone, memory in tests): no Android, no network.
 */
class ParentalLedger(private val persistence: InboxPersistence, var retention: Retention = Retention(), private val now: () -> Long = System::currentTimeMillis) {
    private val events = LinkedHashMap<String, ActivityEvent>()
    private val facts = LinkedHashMap<String, DayFact>()
    private val sup = ArrayList<SupervisionObs>()
    private val tvs = LinkedHashMap<String, TvInfo>()
    private val profiles = LinkedHashMap<String, ProfileInfo>()
    private val learn = LinkedHashMap<String, Map<String, Any?>>()          // tv|profile -> newest digest of « Apprendre » (cumulative, from the TV)
    private val learnTs = HashMap<String, Long>()
    private val seen = LinkedHashSet<String>()                              // report ids already absorbed (bounded)
    private val purged = HashMap<String, Long>()                            // profile id or "*" -> phone time of the purge
    private var dirty = false

    init { synchronized(this) { load() } }

    // ------------------------------------------------------------------ absorbing

    /** Absorbs reports (any order, any number of times). Returns how many were new. Reports older than a purge of their profile are ignored. */
    @Synchronized fun absorb(reports: Collection<StoredReport>): Int {
        var n = 0
        for (r in reports.sortedBy { it.ts }) {
            if (r.id in seen) continue
            seen += r.id; n++
            val all = purged["*"]
            if (all != null && r.at <= all) continue
            when (r.kind) { "daily" -> daily(r); "weekly" -> weekly(r); "alert" -> alert(r) }
            if (r.tv.isNotBlank()) tvs[r.tv] = TvInfo(r.tv, maxOf(r.ts, tvs[r.tv]?.lastReportTs ?: 0), maxOf(r.at, tvs[r.tv]?.lastReceivedAt ?: 0))
        }
        while (seen.size > MAX_SEEN) seen.remove(seen.first())
        if (n > 0) { prune(); dirty = true; save() }
        return n
    }

    private fun num(m: Map<*, *>?, k: String) = (m?.get(k) as? Number)?.toLong() ?: 0L
    private fun str(m: Map<*, *>?, k: String) = (m?.get(k) as? String)?.takeIf { it.isNotBlank() }

    private fun isPurged(profileId: String?, r: StoredReport): Boolean = profileId != null && (purged[profileId]?.let { r.at <= it } ?: false)

    private fun noteProfile(id: String, name: String, ts: Long) {
        val cur = profiles[id]
        // the newest report decides the name (a rename, even when a late report arrives afterwards)
        if (cur == null || ts >= (profileTs[id] ?: 0)) { profiles[id] = ProfileInfo(id, name.take(40).ifBlank { cur?.name ?: id }, false); profileTs[id] = ts }
    }
    private val profileTs = HashMap<String, Long>()

    @Suppress("UNCHECKED_CAST")
    private fun daily(r: StoredReport) {
        val b = r.body; val prof = b["profile"] as? Map<String, Any?> ?: return
        val pid = str(prof, "id") ?: return; val name = str(prof, "name") ?: pid
        if (isPurged(pid, r)) return
        val day = Clock.parseDay(b["day"] as? String)?.toString() ?: return
        noteProfile(pid, name, r.ts)
        val kinds = b["kinds"] as? Map<String, Any?>
        val sv = (b["supervision"] as? Map<String, Any?>)?.get("state") as? String
        val apps = (b["apps"] as? List<Map<String, Any?>>).orEmpty().mapNotNull { m -> str(m, "pkg")?.let { AppMin(it, str(m, "label") ?: it, num(m, "min")) } }
        val play = num(kinds, "play"); val games = num(kinds, "games"); val dl = num(kinds, "downloads"); val ap = num(kinds, "apps")
        val total = maxOf(num(b, "totalMin"), play + games + dl + ap)
        putFact(DayFact(r.tv, day, pid, name, play, games, dl, ap, total, true, apps, num(b, "limitMin").toInt(), str(b, "window"), sv, r.ts, "daily"))
        sv?.let { observeSupervision(r.tv, r.ts, it) }
        for (m in (b["blocked"] as? List<Map<String, Any?>>).orEmpty()) {
            val ts = num(m, "ts").takeIf { it > 0 } ?: continue
            val what = str(m, "what") ?: "Contenu bloqué"
            putEvent(ActivityEvent("blk:${r.tv}:$ts:${what.hashCode()}", r.tv, ts, pid, EventType.BLOCK, what, detail = str(m, "why"), severity = Severity.WARN))
        }
        for (m in (b["tamper"] as? List<Map<String, Any?>>).orEmpty()) tamper(r, m)
        for (m in (b["newApps"] as? List<Map<String, Any?>>).orEmpty()) {
            val pkg = str(m, "pkg") ?: continue
            putEvent(ActivityEvent("new:${r.tv}:$pkg", r.tv, r.ts, null, EventType.ALERT, "Nouvelle application : ${str(m, "label") ?: pkg}", detail = "Installée sur la TV : à classer dans les règles par application.", severity = Severity.INFO))
        }
        // additive: the TV's own journal (measured) and the digest of « Apprendre »
        for (m in (b["events"] as? List<Map<String, Any?>>).orEmpty()) journal(r, pid, m)
        (b["learn"] as? Map<String, Any?>)?.let { if (r.ts >= (learnTs["${r.tv}|$pid"] ?: 0)) { learn["${r.tv}|$pid"] = it; learnTs["${r.tv}|$pid"] = r.ts } }
    }

    private fun journal(r: StoredReport, pid: String, m: Map<String, Any?>) {
        val id = str(m, "id") ?: return; val ts = num(m, "ts").takeIf { it > 0 } ?: return
        val type = EventType.of(str(m, "t")) ?: return
        val q = if (type == EventType.APP) Quality.BEST_EFFORT else Quality.MEASURED
        val dur = (m["min"] as? Number)?.toInt()?.takeIf { it >= 0 }
        putEvent(ActivityEvent("j:${r.tv}:$id", r.tv, ts, pid, type, (str(m, "title") ?: type.label).take(120), dur, str(m, "score")?.take(30), str(m, "detail")?.take(160), q, if (type == EventType.BLOCK) Severity.WARN else null))
    }

    private fun tamper(r: StoredReport, m: Map<String, Any?>) {
        val ts = num(m, "ts").takeIf { it > 0 } ?: return
        val what = str(m, "what") ?: "Surveillance modifiée"
        putEvent(ActivityEvent("tmp:${r.tv}:$ts", r.tv, ts, null, EventType.ALERT, what, detail = "Alerte envoyée au téléphone du parent.", severity = Severity.CRITICAL))
    }

    @Suppress("UNCHECKED_CAST")
    private fun weekly(r: StoredReport) {
        val b = r.body; val prof = b["profile"] as? Map<String, Any?> ?: return
        val pid = str(prof, "id") ?: return; val name = str(prof, "name") ?: pid
        if (isPurged(pid, r)) return
        noteProfile(pid, name, r.ts)
        val sv = (b["supervision"] as? Map<String, Any?>)?.get("state") as? String
        sv?.let { observeSupervision(r.tv, r.ts, it) }
        for (d in (b["days"] as? List<Map<String, Any?>>).orEmpty()) {
            val day = Clock.parseDay(d["day"] as? String)?.toString() ?: continue
            val total = num(d, "totalMin")
            val cur = facts["${r.tv}|$day|$pid"]
            if (cur == null) putFact(DayFact(r.tv, day, pid, name, 0, 0, 0, 0, total, false, emptyList(), 0, null, sv, r.ts, "weekly"))
            else if (total > cur.totalMin) facts[cur.key] = cur.copy(totalMin = total, name = if (r.ts >= cur.ts) name else cur.name)   // late usage the daily report did not have
        }
    }

    private fun alert(r: StoredReport) {
        val b = r.body; val prof = b["profile"] as? Map<*, *>; val pid = str(prof, "id")
        if (isPurged(pid, r)) return
        if (pid != null) noteProfile(pid, str(prof, "name") ?: pid, r.ts)
        val kind = b["alert"] as? String
        val (sev, action) = when (kind) {
            "tamper" -> Severity.CRITICAL to "Alerte immédiate envoyée à ce téléphone. À vérifier sur la TV (accès « Statistiques d'utilisation »)."
            "limit" -> Severity.INFO to "La TV a arrêté ou bloqué l'usage ; alerte envoyée à ce téléphone."
            "blocked" -> Severity.WARN to "L'application a été refusée par la TV ; alerte envoyée à ce téléphone."
            "newapp" -> Severity.INFO to "Application à classer : aucun enfant ne peut l'ouvrir tant qu'une règle n'existe pas (selon le réglage)."
            else -> Severity.INFO to "Alerte reçue."
        }
        putEvent(ActivityEvent("al:${r.id}", r.tv, r.ts, pid, EventType.ALERT, str(b, "text") ?: "Alerte", detail = action, severity = sev))
        if (kind == "blocked") putEvent(ActivityEvent("blk-al:${r.tv}:${r.ts / 60_000}:${str(b, "pkg")}", r.tv, r.ts, pid, EventType.BLOCK, "Application « ${str(b, "label") ?: str(b, "pkg") ?: "?"} » refusée", detail = str(b, "text"), severity = Severity.WARN))
    }

    private fun putEvent(e: ActivityEvent) { events[e.id] = e }

    private fun putFact(f: DayFact) {
        val cur = facts[f.key]
        // newest report of the TV wins for the day; the daily report (with its detail) wins over the total of a weekly one
        val keep = when {
            cur == null -> f
            cur.source == "daily" && f.source == "weekly" -> if (f.totalMin > cur.totalMin) cur.copy(totalMin = f.totalMin) else cur
            cur.source == "weekly" && f.source == "daily" -> f.copy(totalMin = maxOf(f.totalMin, cur.totalMin))
            f.ts >= cur.ts -> f.copy(totalMin = maxOf(f.totalMin, cur.totalMin))
            else -> cur
        }
        facts[f.key] = keep
    }

    private fun observeSupervision(tv: String, ts: Long, state: String) {
        if (sup.none { it.tv == tv && it.ts == ts && it.state == state }) sup += SupervisionObs(tv, ts, state)
        sup.sortBy { it.ts }
        while (sup.size > MAX_SUP) sup.removeAt(0)
    }

    // ------------------------------------------------------------------ reading

    @Synchronized fun events(): List<ActivityEvent> = events.values.sortedByDescending { it.ts }
    @Synchronized fun facts(): List<DayFact> = facts.values.sortedWith(compareBy({ it.day }, { it.profileId }))
    @Synchronized fun supervisionLog(tv: String? = null): List<SupervisionObs> = sup.filter { tv == null || it.tv == tv }
    @Synchronized fun tvList(): List<TvInfo> = tvs.values.toList()
    @Synchronized fun profileList(): List<ProfileInfo> = profiles.values.toList()
    @Synchronized fun profileName(id: String?): String = if (id == null) "Toute la TV" else profiles[id]?.let { it.name + if (it.deleted) " (profil supprimé)" else "" } ?: id
    @Synchronized fun learnDigest(tv: String, profileId: String): Map<String, Any?>? = learn["$tv|$profileId"]
    @Synchronized fun lastSupervision(tv: String? = null): SupervisionObs? = sup.lastOrNull { tv == null || it.tv == tv }
    @Synchronized fun isEmpty() = events.isEmpty() && facts.isEmpty() && tvs.isEmpty()
    @Synchronized fun sizes() = Triple(events.size, facts.size, seen.size)

    /** The TV's list of profiles (live, from its status): the ones it no longer lists are marked deleted, the others come back. */
    @Synchronized fun markProfiles(liveIds: Collection<String>) {
        var ch = false
        for ((id, p) in profiles.toList()) { val del = id !in liveIds; if (p.deleted != del) { profiles[id] = p.copy(deleted = del); ch = true } }
        if (ch) { dirty = true; save() }
    }

    // ------------------------------------------------------------------ retention and purge

    /** Drops what is older than the retention and what exceeds the bounds (oldest first). */
    @Synchronized fun prune() {
        val z = ZoneId.systemDefault()
        val cutoffDay = Instant.ofEpochMilli(now()).atZone(z).toLocalDate().minusDays(retention.days.toLong()).toString()
        val cutoffTs = now() - retention.days * 86_400_000L
        events.values.removeAll { it.ts < cutoffTs }
        facts.values.removeAll { it.day < cutoffDay }
        sup.removeAll { it.ts < cutoffTs }
        if (events.size > retention.maxEvents) events.values.sortedBy { it.ts }.take(events.size - retention.maxEvents).forEach { events.remove(it.id) }
        if (facts.size > retention.maxFacts) facts.values.sortedBy { it.day }.take(facts.size - retention.maxFacts).forEach { facts.remove(it.key) }
    }

    fun applyRetention(r: Retention) { synchronized(this) { retention = r; prune(); dirty = true; save() } }

    /** Purge of one profile (events, days, digest) or of everything ([profileId] null). The caller asked for the PIN. What was purged is not absorbed again. */
    @Synchronized fun purge(profileId: String?) {
        val t = now()
        if (profileId == null) {
            events.clear(); facts.clear(); sup.clear(); tvs.clear(); profiles.clear(); learn.clear(); learnTs.clear(); profileTs.clear(); purged["*"] = t
        } else {
            events.values.removeAll { it.profileId == profileId }; facts.values.removeAll { it.profileId == profileId }
            profiles.remove(profileId); profileTs.remove(profileId); learn.keys.removeAll { it.endsWith("|$profileId") }; purged[profileId] = t
        }
        dirty = true; save()
    }

    // ------------------------------------------------------------------ persistence

    private fun save() {
        val o = linkedMapOf<String, Any?>(
            "v" to 1, "retention" to retention.days,
            "events" to events.values.map { e -> linkedMapOf("id" to e.id, "tv" to e.tv, "ts" to e.ts, "p" to e.profileId, "t" to e.type.code, "title" to e.title, "min" to e.durMin, "score" to e.score, "d" to e.detail, "q" to e.quality.code, "sev" to e.severity?.code) },
            "facts" to facts.values.map { f -> linkedMapOf("tv" to f.tv, "day" to f.day, "p" to f.profileId, "name" to f.name, "play" to f.play, "games" to f.games, "dl" to f.downloads, "apps" to f.apps, "total" to f.totalMin,
                "kinds" to f.kindsKnown, "byApp" to f.byApp.map { linkedMapOf("pkg" to it.pkg, "label" to it.label, "min" to it.min) }, "limit" to f.limitMin, "window" to f.window, "sup" to f.supervision, "ts" to f.ts, "src" to f.source) },
            "sup" to sup.map { linkedMapOf("tv" to it.tv, "ts" to it.ts, "s" to it.state) },
            "tvs" to tvs.values.map { linkedMapOf("name" to it.name, "rt" to it.lastReportTs, "ra" to it.lastReceivedAt) },
            "profiles" to profiles.values.map { linkedMapOf("id" to it.id, "name" to it.name, "del" to it.deleted, "ts" to (profileTs[it.id] ?: 0L)) },
            "learn" to learn.map { (k, v) -> linkedMapOf("k" to k, "ts" to (learnTs[k] ?: 0L), "d" to v) },
            "seen" to seen.toList(), "purged" to purged.map { (k, v) -> linkedMapOf("k" to k, "t" to v) },
        )
        runCatching { persistence.save(JsonLite.write(o)) }
        dirty = false
    }

    @Suppress("UNCHECKED_CAST")
    private fun load() {
        val o = runCatching { persistence.load()?.let { JsonLite.obj(it) } }.getOrNull() ?: return
        (o["retention"] as? Number)?.toInt()?.takeIf { it in 7..730 }?.let { retention = retention.copy(days = it) }
        fun l(k: String) = (o[k] as? List<Map<String, Any?>>).orEmpty()
        for (m in l("events")) runCatching {
            val e = ActivityEvent(m["id"] as String, m["tv"] as String, num(m, "ts"), m["p"] as? String, EventType.of(m["t"] as? String) ?: return@runCatching, m["title"] as? String ?: "",
                (m["min"] as? Number)?.toInt(), m["score"] as? String, m["d"] as? String, Quality.values().firstOrNull { it.code == m["q"] } ?: Quality.MEASURED, Severity.values().firstOrNull { it.code == m["sev"] })
            events[e.id] = e
        }
        for (m in l("facts")) runCatching {
            val f = DayFact(m["tv"] as String, m["day"] as String, m["p"] as String, m["name"] as? String ?: "", num(m, "play"), num(m, "games"), num(m, "dl"), num(m, "apps"), num(m, "total"), m["kinds"] != false,
                (m["byApp"] as? List<Map<String, Any?>>).orEmpty().map { AppMin(it["pkg"] as String, it["label"] as? String ?: "", num(it, "min")) }, num(m, "limit").toInt(), m["window"] as? String, m["sup"] as? String, num(m, "ts"), m["src"] as? String ?: "daily")
            facts[f.key] = f
        }
        for (m in l("sup")) runCatching { sup += SupervisionObs(m["tv"] as String, num(m, "ts"), m["s"] as String) }
        for (m in l("tvs")) runCatching { tvs[m["name"] as String] = TvInfo(m["name"] as String, num(m, "rt"), num(m, "ra")) }
        for (m in l("profiles")) runCatching { val id = m["id"] as String; profiles[id] = ProfileInfo(id, m["name"] as String, m["del"] == true); profileTs[id] = num(m, "ts") }
        for (m in l("learn")) runCatching { val k = m["k"] as String; learn[k] = m["d"] as Map<String, Any?>; learnTs[k] = num(m, "ts") }
        (o["seen"] as? List<Any?>)?.forEach { (it as? String)?.let(seen::add) }
        for (m in l("purged")) runCatching { purged[m["k"] as String] = num(m, "t") }
    }

    companion object { private const val MAX_SEEN = 1_000; private const val MAX_SUP = 500 }
}
