package castbridge.core.parental

import castbridge.core.net.JsonLite
import castbridge.core.trust.TrustRegistry
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.IsoFields
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Reports of the TV to the parent's phone (docs/PARENTAL.md, « Rapports envoyés au téléphone »). 100 % local: a report waits in an
 * outbox on the TV and the designated phone PULLS it over the trusted Bluetooth link (CBTP, see [ParentalSync]). No server, no push
 * service. A report never holds a PIN, a token or a Bluetooth address.
 */

/** What goes into the reports of one profile. Alerts default to on: a phone only receives anything once a parent designated it. */
data class ProfileReportOptions(
    val daily: Boolean = true, val weekly: Boolean = true,
    val alertLimit: Boolean = true, val alertBlocked: Boolean = true, val alertTamper: Boolean = true, val alertNewApp: Boolean = true,
)

/** [dailyAtMin]: minutes since midnight at which the daily summary leaves; [weeklyDow]: ISO day of the weekly summary (1 = Monday ... 7 = Sunday). */
data class ReportConfig(val rev: Int = 0, val dailyAtMin: Int = 20 * 60, val weeklyDow: Int = 7, val profiles: Map<String, ProfileReportOptions> = emptyMap()) {
    fun options(profileId: String?) = (profileId?.let { profiles[it] }) ?: ProfileReportOptions()

    fun toMap(): Map<String, Any?> = linkedMapOf("rev" to rev, "dailyAtMin" to dailyAtMin, "weeklyDow" to weeklyDow,
        "profiles" to profiles.map { (id, o) -> linkedMapOf("id" to id, "daily" to o.daily, "weekly" to o.weekly, "alertLimit" to o.alertLimit,
            "alertBlocked" to o.alertBlocked, "alertTamper" to o.alertTamper, "alertNewApp" to o.alertNewApp) })

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromMap(o: Map<String, Any?>): ReportConfig {
            val at = (o["dailyAtMin"] as? Number)?.toInt() ?: 20 * 60
            val dow = (o["weeklyDow"] as? Number)?.toInt() ?: 7
            if (at !in 0..1439) throw IllegalArgumentException("Heure du rapport invalide.")
            if (dow !in 1..7) throw IllegalArgumentException("Jour du rapport hebdomadaire invalide.")
            val ps = LinkedHashMap<String, ProfileReportOptions>()
            for (e in (o["profiles"] as? List<Any?>).orEmpty()) {
                val m = e as? Map<String, Any?> ?: throw IllegalArgumentException("Options de rapport invalides.")
                val id = (m["id"] as? String)?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,16}")) } ?: throw IllegalArgumentException("Identifiant de profil invalide.")
                fun b(k: String) = m[k] as? Boolean ?: true
                ps[id] = ProfileReportOptions(b("daily"), b("weekly"), b("alertLimit"), b("alertBlocked"), b("alertTamper"), b("alertNewApp"))
            }
            if (ps.size > ParentalConfig.MAX_PROFILES) throw IllegalArgumentException("Trop de profils.")
            return ReportConfig((o["rev"] as? Number)?.toInt() ?: 0, at, dow, ps)
        }
    }
}

/** A phone the parent designated to receive reports. [key] signs the reports ([ReportMac]); it reaches the phone once, over the secure link. */
data class Recipient(val address: String, val key: String, val keyDelivered: Boolean, val at: Long)

/**
 * Who receives the reports. Rules (tested): a recipient must be a TRUSTED phone (approved on the TV, see [TrustRegistry]) AND designated by
 * a parent who proved the parental PIN ([pinVerified], checked by the caller with the lockout of [ParentalEngine.verifyPin]); removing one
 * needs the PIN too; nobody is a recipient by default (the child's phone is not); a phone that stops being trusted stops being a recipient.
 */
class ReportRecipients(
    private val store: KvStore,
    private val isTrusted: (String) -> Boolean,
    private val now: () -> Long = System::currentTimeMillis,
    private val random: SecureRandom = SecureRandom(),
) {
    @Synchronized fun list(): List<Recipient> = load()

    /** Recipients that are still trusted phones. A phone removed from the trusted list is forgotten here too (and its pending reports with it, see [ParentalReports]). */
    @Synchronized fun active(): List<Recipient> {
        val all = load(); val ok = all.filter { isTrusted(it.address) }
        if (ok.size != all.size) save(ok)
        return ok
    }

    @Synchronized fun isRecipient(address: String?): Boolean = address != null && active().any { it.address == TrustRegistry.norm(address) }
    @Synchronized fun get(address: String): Recipient? = active().firstOrNull { it.address == TrustRegistry.norm(address) }

    /** null = done, else the French reason. */
    @Synchronized fun designate(address: String, pinVerified: Boolean): String? {
        if (!pinVerified) return "Le code parental est nécessaire pour choisir le téléphone qui reçoit les rapports."
        val a = TrustRegistry.norm(address)
        if (!TrustRegistry.isAddress(a) || !isTrusted(a)) return "Ce téléphone n'est pas un téléphone de confiance de la TV."
        val cur = active()
        if (cur.any { it.address == a }) return null
        if (cur.size >= MAX) return "$MAX téléphones au maximum reçoivent les rapports : retirez-en un d'abord."
        // a new key at each designation: a phone removed and designated again gets a fresh key
        val key = ByteArray(32).also(random::nextBytes).joinToString("") { "%02x".format(it) }
        save(cur + Recipient(a, key, false, now()))
        return null
    }

    @Synchronized fun remove(address: String, pinVerified: Boolean): String? {
        if (!pinVerified) return "Le code parental est nécessaire pour retirer un téléphone des rapports."
        val a = TrustRegistry.norm(address)
        save(load().filterNot { it.address == a })
        return null
    }

    @Synchronized fun keyDelivered(address: String) {
        val a = TrustRegistry.norm(address)
        save(load().map { if (it.address == a) it.copy(keyDelivered = true) else it })
    }

    /** Opaque identifier of a phone for the API and the phone itself: the Bluetooth address never leaves the TV. */
    fun phoneId(address: String): String = MessageDigest.getInstance("SHA-256").digest(("cbp:" + TrustRegistry.norm(address)).toByteArray()).take(6).joinToString("") { "%02x".format(it) }

    @Synchronized fun byPhoneId(id: String, candidates: List<String>): String? = candidates.firstOrNull { phoneId(it) == id }

    private fun load(): List<Recipient> = store.get("recips")?.let { raw ->
        runCatching {
            @Suppress("UNCHECKED_CAST") (JsonLite.parse(raw) as List<Map<String, Any?>>).mapNotNull { m ->
                val a = (m["a"] as? String)?.let(TrustRegistry::norm)?.takeIf(TrustRegistry::isAddress) ?: return@mapNotNull null
                val k = (m["k"] as? String)?.takeIf { it.matches(Regex("[0-9a-f]{64}")) } ?: return@mapNotNull null
                Recipient(a, k, m["d"] as? Boolean ?: false, (m["t"] as? Number)?.toLong() ?: 0L)
            }
        }.getOrNull()
    } ?: emptyList()

    private fun save(l: List<Recipient>) = store.put("recips", JsonLite.write(l.map { linkedMapOf("a" to it.address, "k" to it.key, "d" to it.keyDelivered, "t" to it.at) }))

    companion object { const val MAX = 3 }
}

/** Integrity of a report: HMAC-SHA256 with the per-recipient key, so a device that merely shares the network cannot forge one. */
object ReportMac {
    fun sign(keyHex: String, id: String, ts: Long, kind: String, body: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(keyHex.toByteArray(Charsets.US_ASCII), "HmacSHA256"))
        return mac.doFinal("CBR1\n$id\n$ts\n$kind\n$body".toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    fun verify(keyHex: String, id: String, ts: Long, kind: String, body: String, mac: String?): Boolean =
        mac != null && MessageDigest.isEqual(sign(keyHex, id, ts, kind, body).toByteArray(), mac.toByteArray())
}

/** A report waiting for its phone. [body] is a JSON text (signed as is). */
data class OutMsg(val id: String, val seq: Long, val to: String, val ts: Long, val kind: String, val body: String, val mac: String, val attempts: Int = 0, val lastTry: Long = 0) {
    fun envelope(): Map<String, Any?> = linkedMapOf("id" to id, "ts" to ts, "kind" to kind, "body" to body, "mac" to mac)
}

/**
 * The TV's outbox: reports wait here until the phone acknowledges them. Bounded in count per phone, in total size and in age (a phone that
 * never comes back cannot make the TV's storage grow); delivered in order; still there after a broken link (retry = the phone asks again).
 */
class ReportOutbox(
    private val store: KvStore,
    private val now: () -> Long = System::currentTimeMillis,
    private val random: SecureRandom = SecureRandom(),
    val maxPerRecipient: Int = 40,
    val maxAgeMs: Long = 14 * 24 * 3600_000L,
    val maxBytes: Int = 200_000,
    val batch: Int = 15,
) {
    @Synchronized fun enqueue(to: String, kind: String, body: String, keyHex: String): OutMsg {
        val t = now()
        val seq = (store.get("obseq")?.toLongOrNull() ?: 0L) + 1
        store.put("obseq", seq.toString())
        val id = java.lang.Long.toHexString(t) + "-" + ByteArray(3).also(random::nextBytes).joinToString("") { "%02x".format(it) }
        val m = OutMsg(id, seq, TrustRegistry.norm(to), t, kind, body, ReportMac.sign(keyHex, id, t, kind, body))
        save(bound(load() + m))
        return m
    }

    /** The next reports for [to], oldest first. Each call counts as an attempt (the phone may have lost the link before storing them). */
    @Synchronized fun pending(to: String): List<OutMsg> {
        val a = TrustRegistry.norm(to)
        val all = bound(load())
        val mine = all.filter { it.to == a }.sortedBy { it.seq }.take(batch)
        if (mine.isEmpty()) { if (all.size != load().size) save(all); return emptyList() }
        val ids = mine.map { it.id }.toSet()
        val t = now()
        save(all.map { if (it.id in ids) it.copy(attempts = it.attempts + 1, lastTry = t) else it })
        return mine.map { it.copy(attempts = it.attempts + 1, lastTry = t) }
    }

    /** The phone stored these: forgotten. A phone can only acknowledge its own reports. */
    @Synchronized fun ack(to: String, ids: Collection<String>) {
        val a = TrustRegistry.norm(to); val s = ids.toSet()
        save(load().filterNot { it.to == a && it.id in s })
    }

    @Synchronized fun dropRecipient(to: String) { val a = TrustRegistry.norm(to); save(load().filterNot { it.to == a }) }
    @Synchronized fun count(to: String? = null) = load().count { to == null || it.to == TrustRegistry.norm(to) }
    @Synchronized fun all(): List<OutMsg> = load().sortedBy { it.seq }
    @Synchronized fun clear() = store.put("outbox", null)

    /** Expired reports out, then the oldest of a phone that has too many, then the oldest overall while the total is too big. */
    private fun bound(list: List<OutMsg>): List<OutMsg> {
        val t = now()
        var l = list.filter { t - it.ts <= maxAgeMs }
        for (to in l.map { it.to }.toSet()) {
            val mine = l.filter { it.to == to }.sortedBy { it.seq }
            if (mine.size > maxPerRecipient) { val drop = mine.take(mine.size - maxPerRecipient).map { it.id }.toSet(); l = l.filterNot { it.id in drop } }
        }
        l = l.sortedBy { it.seq }
        while (l.size > 1 && size(l) > maxBytes) l = l.drop(1)
        return l
    }

    private fun size(l: List<OutMsg>) = l.sumOf { it.body.length + 200 }

    @Suppress("UNCHECKED_CAST")
    private fun load(): List<OutMsg> = store.get("outbox")?.let { raw ->
        runCatching {
            (JsonLite.parse(raw) as List<Map<String, Any?>>).mapNotNull { m ->
                OutMsg(m["id"] as? String ?: return@mapNotNull null, (m["s"] as? Number)?.toLong() ?: return@mapNotNull null, m["to"] as? String ?: return@mapNotNull null,
                    (m["ts"] as? Number)?.toLong() ?: 0L, m["k"] as? String ?: return@mapNotNull null, m["b"] as? String ?: return@mapNotNull null,
                    m["mac"] as? String ?: return@mapNotNull null, (m["n"] as? Number)?.toInt() ?: 0, (m["lt"] as? Number)?.toLong() ?: 0L)
            }
        }.getOrNull()
    } ?: emptyList()

    private fun save(l: List<OutMsg>) = store.put("outbox", JsonLite.write(l.map {
        linkedMapOf("id" to it.id, "s" to it.seq, "to" to it.to, "ts" to it.ts, "k" to it.kind, "b" to it.body, "mac" to it.mac, "n" to it.attempts, "lt" to it.lastTry)
    }))
}

/** Pure report bodies: built only from typed fields of the engine's report, so nothing secret can slip in. */
object ReportBuilder {
    @Suppress("UNCHECKED_CAST")
    private fun profileDay(rep: Map<String, Any?>, day: String, id: String): Map<String, Any?>? =
        ((rep["days"] as? List<Map<String, Any?>>)?.firstOrNull { it["day"] == day }?.get("profiles") as? List<Map<String, Any?>>)?.firstOrNull { it["id"] == id }

    private fun n(m: Map<String, Any?>?, k: String) = (m?.get(k) as? Number)?.toLong() ?: 0L
    private fun total(m: Map<String, Any?>?) = n(m, "play") + n(m, "games") + n(m, "downloads") + n(m, "apps")

    @Suppress("UNCHECKED_CAST")
    private fun topApps(rows: List<Map<String, Any?>>, max: Int): List<Map<String, Any?>> =
        rows.groupBy { it["pkg"] as String }.map { (pkg, l) -> Triple(pkg, l.first()["label"] as? String ?: pkg, l.sumOf { n(it, "min") }) }
            .filter { it.third > 0 }.sortedByDescending { it.third }.take(max).map { linkedMapOf("pkg" to it.first, "label" to it.second, "min" to it.third) }

    @Suppress("UNCHECKED_CAST")
    fun daily(rep: Map<String, Any?>, tv: String, day: String, p: ChildProfile, sinceMs: Long, tamperSince: Long): Map<String, Any?> {
        val pd = profileDay(rep, day, p.id)
        val blocked = (rep["blocked"] as? List<Map<String, Any?>>).orEmpty().filter { (it["ts"] as? Number)?.toLong() ?: 0L >= sinceMs && (it["who"] == p.name || it["who"] == "") }
        return linkedMapOf(
            "v" to 1, "type" to "daily", "tv" to tv, "day" to day, "profile" to linkedMapOf("id" to p.id, "name" to p.name),
            "totalMin" to total(pd), "kinds" to linkedMapOf("play" to n(pd, "play"), "games" to n(pd, "games"), "downloads" to n(pd, "downloads"), "apps" to n(pd, "apps")),
            "apps" to topApps((pd?.get("byApp") as? List<Map<String, Any?>>).orEmpty(), 10),
            "limitMin" to p.dailyLimitMin, "window" to p.window?.text(),
            "blocked" to blocked.take(10).map { linkedMapOf("ts" to it["ts"], "what" to it["what"], "why" to it["why"]) },
            "newApps" to (rep["newApps"] as? List<Map<String, Any?>>).orEmpty().map { linkedMapOf("pkg" to it["pkg"], "label" to it["label"]) },
            "supervision" to ((rep["supervision"] as? Map<String, Any?>)?.let { linkedMapOf("state" to it["state"], "label" to it["label"]) }),
            "tamper" to (rep["tamper"] as? List<Map<String, Any?>>).orEmpty().filter { (it["ts"] as? Number)?.toLong() ?: 0L >= tamperSince }.map { linkedMapOf("ts" to it["ts"], "what" to it["what"]) },
        )
    }

    @Suppress("UNCHECKED_CAST")
    fun weekly(rep: Map<String, Any?>, tv: String, days: List<String>, p: ChildProfile): Map<String, Any?> {
        val perDay = days.map { d -> linkedMapOf("day" to d, "totalMin" to total(profileDay(rep, d, p.id))) }
        val apps = days.flatMap { d -> (profileDay(rep, d, p.id)?.get("byApp") as? List<Map<String, Any?>>).orEmpty() }
        return linkedMapOf(
            "v" to 1, "type" to "weekly", "tv" to tv, "from" to days.minOrNull(), "to" to days.maxOrNull(), "profile" to linkedMapOf("id" to p.id, "name" to p.name),
            "totalMin" to perDay.sumOf { it["totalMin"] as Long }, "days" to perDay.sortedBy { it["day"] as String }, "topApps" to topApps(apps, 10),
            "blockedCount" to (rep["blocked"] as? List<*>).orEmpty().size,
            "tamperCount" to (rep["tamper"] as? List<*>).orEmpty().size,
            "supervision" to ((rep["supervision"] as? Map<String, Any?>)?.let { linkedMapOf("state" to it["state"], "label" to it["label"]) }),
        )
    }

    fun alert(tv: String, type: String, profile: ChildProfile?, text: String, pkg: String? = null, label: String? = null): Map<String, Any?> = linkedMapOf(
        "v" to 1, "type" to "alert", "alert" to type, "tv" to tv, "profile" to profile?.let { linkedMapOf("id" to it.id, "name" to it.name) },
        "text" to text.take(240), "pkg" to pkg, "label" to label,
    )
}

/**
 * Turns the engine's events and the clock into reports for the designated phones.
 *
 * - Daily summary at [ReportConfig.dailyAtMin], weekly summary on [ReportConfig.weeklyDow] at the same time, each toggleable per profile.
 *   A summary missed because the TV was off leaves at the next [tick] after the time (same day only).
 * - Immediate alerts: daily limit or hours reached, blocked app tried, tampering with the supervision, new app installed (per profile toggles;
 *   tampering and new apps are TV-wide: sent when any profile wants them).
 * - Alerts are de-duplicated (same thing within 10 minutes) and capped (12 per hour) so a child hammering a blocked app cannot flood the outbox.
 */
class ParentalReports(
    private val store: KvStore,
    private val engine: ParentalEngine,
    val recipients: ReportRecipients,
    val outbox: ReportOutbox,
    private val tvName: () -> String,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {
    private val lastAlert = HashMap<String, Long>()
    private val sentAlerts = ArrayDeque<Long>()

    @Synchronized fun config(): ReportConfig = store.get("repcfg")?.let { runCatching { ReportConfig.parse(it) }.getOrNull() } ?: ReportConfig()

    /** Saves (rev + 1); null = [expectedRev] is stale. */
    @Synchronized fun saveConfig(c: ReportConfig, expectedRev: Int?): ReportConfig? {
        val cur = config()
        if (expectedRev != null && expectedRev != cur.rev) return null
        val ids = engine.config().profiles.map { it.id }.toSet()
        val fixed = c.copy(rev = cur.rev + 1, profiles = c.profiles.filterKeys { it in ids })
        store.put("repcfg", JsonLite.write(fixed.toMap()))
        return fixed
    }

    private fun enqueueAll(kind: String, body: Map<String, Any?>) {
        val text = JsonLite.write(body)
        for (r in recipients.active()) outbox.enqueue(r.address, kind, text, r.key)
    }

    /** Called by the TV glue for every event of the engine. */
    @Synchronized fun onEvent(e: ParentalEvent) {
        if (recipients.active().isEmpty()) return
        val cfg = config(); val profiles = engine.config().profiles
        val p = e.profileId?.let { id -> profiles.firstOrNull { it.id == id } }
        val (type, text, key, wanted) = when (e) {
            is ParentalEvent.LimitReached -> Quad("limit", e.text, "limit:${e.profileId}:${e.code}:${day()}", cfg.options(e.profileId).alertLimit)
            is ParentalEvent.AppBlocked -> Quad("blocked", "${p?.name ?: "Un enfant"} a essayé d'ouvrir « ${e.label} » : ${e.text}", "blocked:${e.profileId}:${e.pkg}", cfg.options(e.profileId).alertBlocked)
            is ParentalEvent.Tamper -> Quad("tamper", e.text, "tamper:${e.text}", profiles.isEmpty() || profiles.any { cfg.options(it.id).alertTamper })
            is ParentalEvent.NewAppInstalled -> Quad("newapp", "Nouvelle application installée sur la TV : « ${e.label} ».", "newapp:${e.pkg}", profiles.isEmpty() || profiles.any { cfg.options(it.id).alertNewApp })
        }
        if (!wanted) return
        val t = now()
        if (t - (lastAlert[key] ?: 0L) < ALERT_GAP_MS) return
        while (sentAlerts.isNotEmpty() && t - sentAlerts.first() > 3600_000L) sentAlerts.removeFirst()
        if (sentAlerts.size >= MAX_ALERTS_PER_HOUR) return
        lastAlert[key] = t; sentAlerts.addLast(t)
        val pkg = (e as? ParentalEvent.AppBlocked)?.pkg ?: (e as? ParentalEvent.NewAppInstalled)?.pkg
        val label = (e as? ParentalEvent.AppBlocked)?.label ?: (e as? ParentalEvent.NewAppInstalled)?.label
        enqueueAll("alert", ReportBuilder.alert(tvName(), type, p, text, pkg, label))
    }

    private data class Quad(val type: String, val text: String, val key: String, val wanted: Boolean)

    private fun day(t: Long = now()): String = Instant.ofEpochMilli(t).atZone(zone()).toLocalDate().toString()

    /** Cheap: a few comparisons. Called every few seconds by the TV glue. Returns how many reports were queued. */
    @Synchronized fun tick(): Int {
        val z = Instant.ofEpochMilli(now()).atZone(zone())
        val cfg = config()
        if (z.hour * 60 + z.minute < cfg.dailyAtMin) return 0
        if (recipients.active().isEmpty()) return 0
        var n = 0
        val today = z.toLocalDate()
        if (store.get("replastD") != today.toString()) { store.put("replastD", today.toString()); n += sendDaily(today, force = false) }
        val week = today.get(IsoFields.WEEK_BASED_YEAR).toString() + "-W" + today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        if (today.dayOfWeek == DayOfWeek.of(cfg.weeklyDow) && store.get("replastW") != week) { store.put("replastW", week); n += sendWeekly(today, force = false) }
        return n
    }

    /** « Envoyer un rapport maintenant » (parent action, also handy to test the link): today's summary of every profile, whatever the toggles. */
    @Synchronized fun sendNow(): Int = if (recipients.active().isEmpty()) 0 else sendDaily(Instant.ofEpochMilli(now()).atZone(zone()).toLocalDate(), force = true)

    private fun sendDaily(date: LocalDate, force: Boolean): Int {
        val cfg = config(); val rep = engine.report(2); val d = date.toString()
        val midnight = date.atStartOfDay(zone()).toInstant().toEpochMilli()
        var n = 0
        for (p in engine.config().profiles) {
            if (!force && !cfg.options(p.id).daily) continue
            enqueueAll("daily", ReportBuilder.daily(rep, tvName(), d, p, midnight, now() - 24 * 3600_000L)); n++
        }
        return n
    }

    private fun sendWeekly(date: LocalDate, force: Boolean): Int {
        val cfg = config(); val rep = engine.report(7)
        val days = (0 until 7).map { date.minusDays(it.toLong()).toString() }
        var n = 0
        for (p in engine.config().profiles) {
            if (!force && !cfg.options(p.id).weekly) continue
            enqueueAll("weekly", ReportBuilder.weekly(rep, tvName(), days, p)); n++
        }
        return n
    }

    /** Drops what is waiting for a phone that is no longer a recipient (call after [ReportRecipients.active] changed). */
    @Synchronized fun cleanOrphans() {
        val live = recipients.active().map { it.address }.toSet()
        for (to in outbox.all().map { it.to }.toSet()) if (to !in live) outbox.dropRecipient(to)
    }

    companion object {
        const val ALERT_GAP_MS = 10 * 60_000L
        const val MAX_ALERTS_PER_HOUR = 12
    }
}

private fun ReportConfig.Companion.parse(json: String): ReportConfig = fromMap(JsonLite.obj(json))
