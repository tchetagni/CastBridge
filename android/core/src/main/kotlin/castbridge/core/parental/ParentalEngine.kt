package castbridge.core.parental

import castbridge.core.net.JsonLite
import java.time.Instant
import java.time.ZoneId

/** Result of checking a PIN (the parental PIN, or the TV's administration PIN for a reset). */
sealed class PinResult {
    object Ok : PinResult()
    /** No PIN has been created yet. */
    object NoPin : PinResult()
    data class Wrong(val attemptsLeft: Int) : PinResult()
    data class Locked(val retryAfterSec: Long) : PinResult()
}

/** What a tick of the usage meter asks the TV to do. */
data class TickResult(val blockReason: String? = null, val warnMinutes: Int? = null)

/**
 * The parental brain, 100 % local: the PIN (hashed), the rules, the usage of the day and the blocked-content log.
 * Nothing in it touches the network: the parental data never leaves the TV except to a phone that knows the TV's PIN
 * AND the parental PIN. Thread-safe.
 *
 * Authentication is done by the callers: HTTP routes ([ParentalApi]) check the PIN of the request with [verifyPin] for
 * each sensitive action; the TV screens use [verifyPin] or an open parent session ([sessionActive]). The mutators here do not
 * re-check it.
 */
class ParentalEngine(
    private val store: KvStore,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    private val hasher: PinHasher = PinHasher(),
) {
    private val pinLock = PinLock(store, "parental", now)
    private val adminLock = PinLock(store, "admin", now)
    @Volatile private var sessionUntil = 0L
    private val warned = HashSet<String>()
    /** Per-app grants: the parent typed the PIN for an app set to « Code parental requis » (package -> until, ms). */
    private val grants = HashMap<String, Long>()
    private val notified = HashSet<String>()
    /** Receives what a parent wants to hear about (set by the TV glue; the reports layer turns it into messages for the designated phones). */
    @Volatile var onEvent: (ParentalEvent) -> Unit = {}
    /** The live state of the supervision (asked at each status; the TV glue knows whether usage access is really granted). */
    @Volatile var supervisionProbe: (() -> SupervisionInfo)? = null

    // ------------------------------------------------------------------ PIN

    @Synchronized fun hasPin(): Boolean = store.get("pin") != null

    /** Creates the first PIN. Refused when one exists (changing it needs the old one; forgetting it needs the TV's administrator). */
    @Synchronized fun createPin(pin: String?): String? {
        if (hasPin()) return "Un code parental existe déjà."
        ParentalPins.validateNew(pin)?.let { return it }
        store.put("pin", hasher.hash(pin!!))
        record("Code parental créé")
        return null
    }

    /** null = done, else the French reason. */
    @Synchronized fun changePin(old: String, new: String?): String? {
        when (val r = verifyPin(old)) {
            PinResult.Ok -> {}
            PinResult.NoPin -> return "Aucun code parental : créez-en un d'abord."
            is PinResult.Locked -> return "Trop d'essais : réessayez dans ${r.retryAfterSec} s."
            is PinResult.Wrong -> return "Code parental incorrect."
        }
        ParentalPins.validateNew(new)?.let { return it }
        store.put("pin", hasher.hash(new!!))
        record("Code parental changé")
        return null
    }

    /** Called after a refused PIN (the TV glue journals the attempt: « tentatives de déverrouillage »). It never receives the PIN. */
    @Volatile var onPinFailure: (() -> Unit)? = null

    /** Checks the parental PIN with progressive lockout. An empty PIN is a wrong PIN (and counts). */
    @Synchronized fun verifyPin(pin: String?): PinResult {
        val stored = store.get("pin") ?: return PinResult.NoPin
        if (pinLock.isLocked()) return PinResult.Locked((pinLock.remainingMs() + 999) / 1000)
        if (pin != null && hasher.verify(pin, stored)) { pinLock.recordSuccess(); return PinResult.Ok }
        pinLock.recordFailure()
        onPinFailure?.invoke()
        return if (pinLock.isLocked()) PinResult.Locked((pinLock.remainingMs() + 999) / 1000) else PinResult.Wrong(pinLock.attemptsLeft())
    }

    /** Seconds before the parental PIN is accepted again (0 = not locked). */
    @Synchronized fun lockedForSec(): Long = (pinLock.remainingMs() + 999) / 1000

    /**
     * The TV administrator (holder of the TV's own PIN, checked by [adminOk]) removes the parental PIN and switches the control
     * off. Profiles and rules are kept. Attempts are throttled like the parental PIN, with their own counter.
     */
    @Synchronized fun adminReset(adminPin: String?, adminOk: (String) -> Boolean): PinResult {
        if (adminLock.isLocked()) return PinResult.Locked((adminLock.remainingMs() + 999) / 1000)
        if (adminPin != null && adminPin.isNotEmpty() && adminOk(adminPin)) { adminLock.recordSuccess(); resetNow(); return PinResult.Ok }
        adminLock.recordFailure()
        return if (adminLock.isLocked()) PinResult.Locked((adminLock.remainingMs() + 999) / 1000) else PinResult.Wrong(adminLock.attemptsLeft())
    }

    /** Reset already authorised by the caller (the HTTP route sits behind the TV's PIN guard). */
    @Synchronized fun resetNow() {
        store.put("pin", null)
        pinLock.recordSuccess()
        sessionUntil = 0
        saveConfig(config().copy(enabled = false))
        record("Code parental effacé par l'administrateur de la TV : contrôle désactivé")
    }

    // ------------------------------------------------------------------ parent session (TV unlocked for a while)

    @Synchronized fun startSession(): Long { sessionUntil = now() + config().sessionMin * 60_000L; warned.clear(); return sessionUntil }
    @Synchronized fun endSession() { sessionUntil = 0 }
    fun sessionActive() = sessionUntil > now()
    fun sessionLeftSec(): Long = ((sessionUntil - now()) / 1000).coerceAtLeast(0)

    // ------------------------------------------------------------------ configuration

    @Synchronized fun config(): ParentalConfig = store.get("cfg")?.let { runCatching { ParentalConfig.parse(it) }.getOrNull() } ?: ParentalConfig()

    /** Saves [c] (rev + 1). Enabling needs a PIN. Returns the saved config. */
    @Synchronized fun saveConfig(c: ParentalConfig): ParentalConfig {
        val fixed = c.copy(rev = config().rev + 1, enabled = c.enabled && hasPin())
        store.put("cfg", fixed.toJson())
        return fixed
    }

    /** Edits the config; false = nothing changed because [expectedRev] is stale. */
    @Synchronized fun edit(expectedRev: Int? = null, f: (ParentalConfig) -> ParentalConfig): ParentalConfig? {
        val cur = config()
        if (expectedRev != null && expectedRev != cur.rev) return null
        return saveConfig(f(cur))
    }

    /** The administrator may always switch the control off (no parental PIN needed: the route is behind the TV's PIN). */
    @Synchronized fun adminDisable() { if (config().enabled) { saveConfig(config().copy(enabled = false)); record("Contrôle désactivé par l'administrateur de la TV") } }

    // ------------------------------------------------------------------ decisions

    /** True when rules apply right now: control on, a PIN exists, a profile is active, and no parent session is open. */
    @Synchronized fun active(): Boolean = activeProfile() != null

    @Synchronized fun activeProfile(): ChildProfile? {
        if (sessionActive() || !hasPin()) return null
        val c = config()
        return if (c.enabled) c.active() else null
    }

    /** Is [c] open to the active profile? Navigation and Apprendre are always open. */
    @Synchronized fun check(c: Category): Decision {
        val p = activeProfile() ?: return Decision.ALLOW
        if (ParentalRules.categoryBlocked(p, c)) return Decision.deny("category", "« ${c.label} » est désactivé pour ${p.name}.")
        val kind = when (c) { Category.GAMES -> UseKind.GAMES; Category.DOWNLOADS -> UseKind.DOWNLOADS; else -> null }
        if (kind != null) {
            val v = ParentalRules.timeVerdict(p, kind, nowMin(), usedMs(p))
            if (!v.allowed) return v
        }
        return Decision.ALLOW
    }

    /** Hours and daily time only (for a video the parent unlocked with the PIN). */
    @Synchronized fun checkTime(kind: UseKind): Decision {
        val p = activeProfile() ?: return Decision.ALLOW
        return ParentalRules.timeVerdict(p, kind, nowMin(), usedMs(p))
    }

    /** May this video be played now: rating first, then hours and daily time. */
    @Synchronized fun checkPlayback(name: String, volumeLabel: String? = null): Decision {
        val p = activeProfile() ?: return Decision.ALLOW
        val cfg = config()
        val r = ParentalRules.ratingOf(cfg, name, volumeLabel)
        if (!ParentalRules.videoAllowed(p, r)) return Decision.deny("rating", "Cette vidéo (${r.label}) n'est pas adaptée à ${p.name} (${p.age.label}).")
        return ParentalRules.timeVerdict(p, UseKind.PLAY, nowMin(), usedMs(p))
    }

    /** Library filter: null means "show everything" (no rule applies, or the profile sees it, or the parent chose to lock instead of hide). */
    @Synchronized fun libraryFilter(): ((name: String, volume: String?) -> Boolean)? {
        val p = activeProfile() ?: return null
        val cfg = config()
        if (cfg.overAge != OverAge.HIDE) return null
        return { name, vol -> ParentalRules.videoAllowed(p, ParentalRules.ratingOf(cfg, name, vol)) }
    }

    /** Does the profile have to type the PIN to play it (over-age video in "lock" mode)? */
    @Synchronized fun needsPinToPlay(name: String, volumeLabel: String? = null): Boolean {
        val p = activeProfile() ?: return false
        val cfg = config()
        return cfg.overAge == OverAge.LOCK && !ParentalRules.videoAllowed(p, ParentalRules.ratingOf(cfg, name, volumeLabel))
    }

    private fun nowMin(): Int = Instant.ofEpochMilli(now()).atZone(zone()).let { it.hour * 60 + it.minute }
    private fun day(t: Long = now()): String = Instant.ofEpochMilli(t).atZone(zone()).toLocalDate().toString()

    // ------------------------------------------------------------------ usage of the day

    @Suppress("UNCHECKED_CAST")
    private fun usage(): MutableMap<String, Any?> = store.get("usage")?.let { runCatching { JsonLite.obj(it).toMutableMap() }.getOrNull() } ?: mutableMapOf()

    /** ms spent today by the profile over the kinds its limit counts. */
    @Synchronized fun usedMs(p: ChildProfile): Long {
        @Suppress("UNCHECKED_CAST") val d = (usage()[day()] as? Map<String, Any?>)?.get(p.id) as? Map<String, Any?> ?: return 0
        return p.kinds.sumOf { (d[it.code] as? Number)?.toLong() ?: 0L }
    }

    @Synchronized fun addUsage(p: ChildProfile, kind: UseKind, ms: Long) {
        if (ms <= 0) return
        val u = usage(); val today = day()
        @Suppress("UNCHECKED_CAST") val d = (u[today] as? Map<String, Any?>)?.toMutableMap() ?: mutableMapOf()
        @Suppress("UNCHECKED_CAST") val pm = (d[p.id] as? Map<String, Any?>)?.toMutableMap() ?: mutableMapOf()
        pm[kind.code] = ((pm[kind.code] as? Number)?.toLong() ?: 0L) + ms
        d[p.id] = pm; u[today] = d
        // keep two weeks
        val keep = u.keys.sortedDescending().take(14).toSet()
        u.keys.retainAll(keep)
        store.put("usage", JsonLite.write(u))
    }

    /**
     * Called every few seconds by the TV with what the child is doing now ([kind] null = nothing counted): adds [dtMs] to the
     * day, and says when to warn (5 minutes before the end of the hours or of the daily time) or to stop.
     */
    @Synchronized fun tick(kind: UseKind?, dtMs: Long): TickResult {
        val p = activeProfile() ?: return TickResult()
        if (kind == null) return TickResult()
        if (kind in p.kinds) addUsage(p, kind, dtMs)
        val v = ParentalRules.timeVerdict(p, kind, nowMin(), usedMs(p))
        if (!v.allowed) { limitReached(p, v); return TickResult(blockReason = v.reason) }
        val left = v.minutesLeft
        if (left != null && left <= WARN_MIN) {
            val key = "${day()}:${p.id}:${if (p.window != null && left == p.window.minutesLeft(nowMin())) "w" else "l"}"
            if (warned.add(key)) return TickResult(warnMinutes = left)
        }
        return TickResult()
    }

    // ------------------------------------------------------------------ whole-TV supervision (other apps)

    @Synchronized fun appSettings(): AppSettings = store.get("apps")?.let { runCatching { AppSettings.parse(it) }.getOrNull() } ?: AppSettings()

    /** Saves [s] (rev + 1). Rules of a profile that does not exist any more are dropped; a reviewed app is no longer « nouvelle ». */
    @Synchronized fun saveAppSettings(s: AppSettings): AppSettings {
        val ids = config().profiles.map { it.id }.toSet()
        val known = s.known.take(AppSettings.MAX_KNOWN).toSet()
        val fixed = s.copy(rev = appSettings().rev + 1, rules = s.rules.filterKeys { it in ids }, known = known, news = s.news.filter { it.pkg !in known }.take(AppSettings.MAX_NEWS))
        store.put("apps", fixed.toJson())
        return fixed
    }

    /** Edits the app settings; null = nothing changed because [expectedRev] is stale. */
    @Synchronized fun editApps(expectedRev: Int? = null, f: (AppSettings) -> AppSettings): AppSettings? {
        val cur = appSettings()
        if (expectedRev != null && expectedRev != cur.rev) return null
        return saveAppSettings(f(cur))
    }

    /** Is the whole-TV supervision on AND are the rules in force now (a profile active, no parent session)? */
    @Synchronized fun supervising(): Boolean = activeProfile() != null && appSettings().supervise

    /** The parent typed the PIN for [pkg] (state « Code parental requis »): it opens for the length of a parent session. */
    @Synchronized fun grantApp(pkg: String) { grants[pkg] = now() + config().sessionMin * 60_000L }
    private fun granted(pkg: String) = (grants[pkg] ?: 0L) > now()

    /** May [pkg] be in front now? Read-only (nothing counted). Allowed when no rule is in force. */
    @Synchronized fun checkApp(pkg: String, env: AppEnv): Decision {
        val p = activeProfile() ?: return Decision.ALLOW
        val s = appSettings()
        if (!s.supervise) return Decision.ALLOW
        return AppRules.decide(p, s, pkg, env, nowMin(), usedMs(p), appUsedMs(p.id, pkg), granted(pkg))
    }

    /** An app was refused: logged for the report and passed to the phones' alerts. */
    @Synchronized fun denyApp(pkg: String, label: String, d: Decision) {
        val p = activeProfile()
        val l = AppSettings.cleanLabel(label, pkg)
        recordBlocked(l, d.reason ?: "bloquée")
        onEvent(ParentalEvent.AppBlocked(p?.id, pkg, l, d.reason ?: "Application bloquée."))
        if (p != null && (d.code == "limit" || d.code == "window")) limitReached(p, d)
    }

    /**
     * Adds [dtMs] of foreground time on [pkg] to the day (per app, and to the profile's daily quota as [UseKind.APPS]) when the app is
     * allowed, warns 5 minutes before the end, and says when to stop. CastBridge-TV itself and the launcher are never counted here
     * (CastBridge-TV has its own meter: [tick]).
     */
    @Synchronized fun appTick(pkg: String, label: String, dtMs: Long, env: AppEnv): TickResult {
        val p = activeProfile() ?: return TickResult()
        val s = appSettings()
        if (!s.supervise || env.isEssential(pkg)) return TickResult()
        rememberLabel(pkg, label)
        val before = AppRules.decide(p, s, pkg, env, nowMin(), usedMs(p), appUsedMs(p.id, pkg), granted(pkg))
        if (!before.allowed) { denyApp(pkg, label, before); return TickResult(blockReason = before.reason) }
        if (dtMs > 0) {
            if (UseKind.APPS in p.kinds) addUsage(p, UseKind.APPS, dtMs)
            addAppUsage(p.id, pkg, dtMs)
        }
        val after = AppRules.decide(p, s, pkg, env, nowMin(), usedMs(p), appUsedMs(p.id, pkg), granted(pkg))
        if (!after.allowed) { denyApp(pkg, label, after); return TickResult(blockReason = after.reason) }
        val left = after.minutesLeft
        if (left != null && left <= WARN_MIN && warned.add("${day()}:${p.id}:a:$pkg")) return TickResult(warnMinutes = left)
        return TickResult()
    }

    /**
     * The launcher-visible apps now installed. The first call is the baseline (everything is « known »: nothing is new on the day of the
     * setup); after that, an app that appears is « nouvelle » until a parent reviews it. Returns the apps that just became new.
     */
    @Synchronized fun syncInstalled(installed: List<InstalledApp>): List<NewApp> {
        val s = appSettings()
        val pkgs = installed.map { it.pkg }.filter { AppSettings.validPkg(it) }
        if (!s.baselined) {
            saveAppSettings(s.copy(baselined = true, known = (s.known + pkgs).take(AppSettings.MAX_KNOWN).toSet()))
            return emptyList()
        }
        val fresh = installed.filter { AppSettings.validPkg(it.pkg) && it.pkg !in s.known && !s.isNew(it.pkg) }.take(AppSettings.MAX_NEWS)
        if (fresh.isEmpty()) return emptyList()
        val added = fresh.map { NewApp(it.pkg, AppSettings.cleanLabel(it.label, it.pkg), now()) }
        saveAppSettings(s.copy(news = (s.news + added).takeLast(AppSettings.MAX_NEWS)))
        added.forEach { onEvent(ParentalEvent.NewAppInstalled(it.pkg, it.label)) }
        return added
    }

    private fun limitReached(p: ChildProfile, d: Decision) {
        if (!notified.add("${day()}:${p.id}:${d.code}")) return
        onEvent(ParentalEvent.LimitReached(p.id, d.code, d.reason ?: "Temps d'écran terminé."))
    }

    @Suppress("UNCHECKED_CAST")
    private fun appUsage(): MutableMap<String, Any?> = store.get("appusage")?.let { runCatching { JsonLite.obj(it).toMutableMap() }.getOrNull() } ?: mutableMapOf()

    /** ms spent today by [profileId] on [pkg]. */
    @Synchronized fun appUsedMs(profileId: String, pkg: String): Long {
        @Suppress("UNCHECKED_CAST") val d = (appUsage()[day()] as? Map<String, Any?>)?.get(profileId) as? Map<String, Any?> ?: return 0
        return (d[pkg] as? Number)?.toLong() ?: 0L
    }

    private fun addAppUsage(profileId: String, pkg: String, ms: Long) {
        val u = appUsage(); val today = day()
        @Suppress("UNCHECKED_CAST") val d = (u[today] as? Map<String, Any?>)?.toMutableMap() ?: mutableMapOf()
        @Suppress("UNCHECKED_CAST") val pm = (d[profileId] as? Map<String, Any?>)?.toMutableMap() ?: mutableMapOf()
        pm[pkg] = ((pm[pkg] as? Number)?.toLong() ?: 0L) + ms
        d[profileId] = pm.entries.sortedByDescending { (it.value as? Number)?.toLong() ?: 0L }.take(MAX_APPS_PER_DAY).associate { it.key to it.value }
        u[today] = d
        val keep = u.keys.sortedDescending().take(14).toSet()
        u.keys.retainAll(keep)
        store.put("appusage", JsonLite.write(u))
    }

    private fun rememberLabel(pkg: String, label: String) {
        val m = labels()
        val l = AppSettings.cleanLabel(label, pkg)
        if (m[pkg] == l) return
        m[pkg] = l
        while (m.size > MAX_LABELS) m.remove(m.keys.first())
        store.put("applabels", JsonLite.write(m))
    }

    @Suppress("UNCHECKED_CAST")
    private fun labels(): MutableMap<String, Any?> = store.get("applabels")?.let { runCatching { JsonLite.obj(it).toMutableMap() }.getOrNull() } ?: mutableMapOf()

    private fun labelOf(pkg: String): String = (labels()[pkg] as? String) ?: pkg

    // ---- supervision state and tampering

    /**
     * Called by the TV glue at every poll with the state it measured. A drop from « active » to « non autorisée » / « indisponible » while the
     * supervision is on (and a profile is active) is tampering (usage access revoked in Settings, accessibility service switched off):
     * it is logged and passed to the phones. The previous state is stored, so the check also works across a reboot.
     */
    @Synchronized fun reportSupervision(info: SupervisionInfo) {
        val prev = SupervisionState.of(store.get("sup"))
        if (prev == info.state) return
        store.put("sup", info.state.code)
        store.put("supSince", now().toString())
        val s = appSettings()
        if (prev == SupervisionState.ACTIVE && s.supervise && (info.state == SupervisionState.NOT_AUTHORIZED || info.state == SupervisionState.UNAVAILABLE)) {
            val text = "La surveillance de toute la TV n'est plus active" + (info.detail?.let { " ($it)" } ?: "") + " : les autres applications ne sont plus contrôlées."
            addTamper(text)
            onEvent(ParentalEvent.Tamper(text))
        } else if (prev != null && prev != SupervisionState.ACTIVE && info.state == SupervisionState.ACTIVE && s.supervise) addTamper("Surveillance de toute la TV rétablie")
    }

    @Suppress("UNCHECKED_CAST")
    private fun tamperList(): MutableList<Map<String, Any?>> =
        store.get("tamper")?.let { runCatching { (JsonLite.parse(it) as List<Map<String, Any?>>).toMutableList() }.getOrNull() } ?: mutableListOf()

    private fun addTamper(text: String) {
        val l = tamperList(); l += linkedMapOf("ts" to now(), "what" to text.take(200))
        store.put("tamper", JsonLite.write(l.takeLast(20)))
    }

    /** The state shown to the parent: the live probe of the TV when there is one, else what was last stored. Without a PIN or a profile it is « désactivée ». */
    @Synchronized fun supervision(): SupervisionInfo {
        val s = appSettings()
        if (!s.supervise) return SupervisionInfo(SupervisionState.OFF)
        supervisionProbe?.let { return runCatching { it() }.getOrElse { SupervisionInfo(SupervisionState.UNAVAILABLE) } }
        val st = SupervisionState.of(store.get("sup")) ?: SupervisionState.NOT_AUTHORIZED
        return SupervisionInfo(st, since = store.get("supSince")?.toLongOrNull() ?: 0)
    }

    // ------------------------------------------------------------------ blocked-content log and report

    @Synchronized fun recordBlocked(what: String, reason: String) {
        val p = activeProfile()
        val list = blockedList()
        val last = list.lastOrNull()
        if (last != null && last["what"] == what && now() - ((last["ts"] as? Number)?.toLong() ?: 0) < 60_000) return
        list += linkedMapOf("ts" to now(), "who" to (p?.name ?: ""), "what" to what.take(120), "why" to reason.take(160))
        store.put("blocked", JsonLite.write(list.takeLast(100)))
    }

    @Suppress("UNCHECKED_CAST")
    private fun blockedList(): MutableList<Map<String, Any?>> =
        store.get("blocked")?.let { runCatching { (JsonLite.parse(it) as List<Map<String, Any?>>).toMutableList() }.getOrNull() } ?: mutableListOf()

    @Synchronized private fun record(text: String) = recordBlocked("Réglages : $text", "réglage")

    /** The report shown on the parent's phone: minutes per day, profile and kind over [days], and the latest blocked contents. */
    @Synchronized fun report(days: Int = 7): Map<String, Any?> {
        val cfg = config(); val u = usage()
        val ds = u.keys.sortedDescending().take(days.coerceIn(1, 14))
        return linkedMapOf(
            "days" to ds.map { d ->
                @Suppress("UNCHECKED_CAST") val pm = u[d] as? Map<String, Any?> ?: emptyMap()
                linkedMapOf("day" to d, "profiles" to pm.map { (id, kinds) ->
                    @Suppress("UNCHECKED_CAST") val k = kinds as? Map<String, Any?> ?: emptyMap()
                    linkedMapOf("id" to id, "name" to (cfg.profile(id)?.name ?: id),
                        "play" to ((k["play"] as? Number)?.toLong() ?: 0L) / 60_000, "games" to ((k["games"] as? Number)?.toLong() ?: 0L) / 60_000,
                        "downloads" to ((k["downloads"] as? Number)?.toLong() ?: 0L) / 60_000,
                        // additive fields (older phones ignore them): other apps of the TV, in total and app by app
                        "apps" to ((k["apps"] as? Number)?.toLong() ?: 0L) / 60_000,
                        "byApp" to byApp(d, id))
                })
            },
            // what was refused; the parent's own setting changes are kept apart (they are not "blocked content")
            "blocked" to blockedList().filter { it["why"] != "réglage" }.takeLast(50).reversed(),
            "changes" to blockedList().filter { it["why"] == "réglage" }.takeLast(20).reversed(),
            "supervision" to supervision().toMap(),
            "newApps" to appSettings().news.map { linkedMapOf("pkg" to it.pkg, "label" to it.label, "at" to it.at) },
            "tamper" to tamperList().takeLast(10).reversed(),
        )
    }

    /** Minutes today per app for one profile and day, biggest first. */
    @Suppress("UNCHECKED_CAST")
    private fun byApp(d: String, profileId: String): List<Map<String, Any?>> {
        val m = (appUsage()[d] as? Map<String, Any?>)?.get(profileId) as? Map<String, Any?> ?: return emptyList()
        return m.entries.map { (pkg, ms) -> pkg to (((ms as? Number)?.toLong() ?: 0L) / 60_000) }.filter { it.second > 0 }
            .sortedByDescending { it.second }.map { (pkg, min) -> linkedMapOf("pkg" to pkg, "label" to labelOf(pkg), "min" to min) }
    }

    /** Erases the usage and blocked-content history (parent action). */
    @Synchronized fun clearHistory() { store.put("usage", null); store.put("blocked", null); store.put("appusage", null); store.put("tamper", null) }

    /** Status without secrets: what the screens and the phone need before asking for the PIN. */
    @Synchronized fun statusMap(): Map<String, Any?> {
        val c = config(); val p = c.active()
        return linkedMapOf(
            "pinSet" to hasPin(), "enabled" to (c.enabled && hasPin()), "rev" to c.rev,
            "active" to p?.let { linkedMapOf("id" to it.id, "name" to it.name, "age" to it.age.code, "kidMode" to it.kidMode) },
            "profiles" to c.profiles.map { linkedMapOf("id" to it.id, "name" to it.name, "age" to it.age.code) },
            "locked" to (lockedForSec() > 0), "retryAfter" to lockedForSec(),
            "sessionLeftSec" to sessionLeftSec(),
            // additive: the real state of the whole-TV supervision (older phones ignore it)
            "supervision" to supervision().toMap(),
        )
    }

    companion object {
        const val WARN_MIN = 5
        private const val MAX_APPS_PER_DAY = 60
        private const val MAX_LABELS = 400
    }
}
