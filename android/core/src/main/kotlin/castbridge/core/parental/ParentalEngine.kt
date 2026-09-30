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

    /** Checks the parental PIN with progressive lockout. An empty PIN is a wrong PIN (and counts). */
    @Synchronized fun verifyPin(pin: String?): PinResult {
        val stored = store.get("pin") ?: return PinResult.NoPin
        if (pinLock.isLocked()) return PinResult.Locked((pinLock.remainingMs() + 999) / 1000)
        if (pin != null && hasher.verify(pin, stored)) { pinLock.recordSuccess(); return PinResult.Ok }
        pinLock.recordFailure()
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
        if (!v.allowed) return TickResult(blockReason = v.reason)
        val left = v.minutesLeft
        if (left != null && left <= WARN_MIN) {
            val key = "${day()}:${p.id}:${if (p.window != null && left == p.window.minutesLeft(nowMin())) "w" else "l"}"
            if (warned.add(key)) return TickResult(warnMinutes = left)
        }
        return TickResult()
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
                        "downloads" to ((k["downloads"] as? Number)?.toLong() ?: 0L) / 60_000)
                })
            },
            "blocked" to blockedList().takeLast(50).reversed(),
        )
    }

    /** Erases the usage and blocked-content history (parent action). */
    @Synchronized fun clearHistory() { store.put("usage", null); store.put("blocked", null) }

    /** Status without secrets: what the screens and the phone need before asking for the PIN. */
    @Synchronized fun statusMap(): Map<String, Any?> {
        val c = config(); val p = c.active()
        return linkedMapOf(
            "pinSet" to hasPin(), "enabled" to (c.enabled && hasPin()), "rev" to c.rev,
            "active" to p?.let { linkedMapOf("id" to it.id, "name" to it.name, "age" to it.age.code, "kidMode" to it.kidMode) },
            "profiles" to c.profiles.map { linkedMapOf("id" to it.id, "name" to it.name, "age" to it.age.code) },
            "locked" to (lockedForSec() > 0), "retryAfter" to lockedForSec(),
            "sessionLeftSec" to sessionLeftSec(),
        )
    }

    companion object {
        const val WARN_MIN = 5
    }
}
