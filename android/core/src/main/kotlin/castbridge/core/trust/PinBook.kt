package castbridge.core.trust

import castbridge.core.tv.BtProtocol
import castbridge.core.tv.Pin

/**
 * The phone's code book (`castbridge_pins`, private SharedPreferences on Android). [write] is SYNCHRONOUS (Android: `commit()`): a code the user typed
 * must survive the process being killed right after (R-10). Never logged, never in a backup or a device transfer (manifest rules).
 */
interface PinKv {
    fun get(key: String): String?
    /** Applies [put] and [remove] at once; false when the write failed (the caller may say so). */
    fun write(put: Map<String, String>, remove: Collection<String> = emptyList()): Boolean
    /** Every key in the store (for the one-time migration at startup). */
    fun keys(): Set<String> = emptySet()
}

class MemoryPinKv(val map: MutableMap<String, String> = LinkedHashMap()) : PinKv {
    override fun get(key: String) = map[key]
    override fun keys(): Set<String> = map.keys.toSet()
    override fun write(put: Map<String, String>, remove: Collection<String>): Boolean { remove.forEach { map.remove(it) }; map.putAll(put); return true }
}

/** What the book needs to know about the phone's TVs right now: the saved (trusted) TVs, the Bluetooth gateway's TV, which TV holds a live token. */
class PinScope(
    val saved: List<SavedTv> = emptyList(), val default: SavedTv? = null,
    val tunnelPort: Int? = null, val tunnelTv: String? = null,
    val hasToken: (SavedTv) -> Boolean = { false },
) {
    fun resolve(key: String): SavedTv? = PinKeys.resolve(key, saved, default, tunnelPort = tunnelPort, tunnelTv = tunnelTv)
}

/**
 * R-10: ONE record per TV, under a STABLE id, whatever screen key designates it (name, mDNS name, `bt:`, IP, `ip:port`, URL).
 *
 * - id of a saved (paired) TV: `bt:<ADDRESS>` ([PinKeys.resolve], the gateway's TV for the tunnel loopback): an IP change never loses its code.
 * - id of a code-only TV: its name (`name:<lowercase>`; « (Bluetooth) » is a screen decoration, « (2) » is ANOTHER TV) or its address (`host:<ip>:<port>`);
 *   [link] records that an ADDRESS designates that TV (seen with its name by the discovery, code accepted). An address alias is followed to SEND a code only
 *   when the caller saw, at that address, a name with the same base name (`seenName`); a code is never WRITTEN through an alias (a write unlinks it: the
 *   address may be another TV now); a name « … (n) » is never an alias (it may be the other TV of the same model).
 * - the tunnel loopback with no running gateway designates no TV: nothing is read or written under it (an old entry would go to whichever TV the tunnel reaches).
 * - layout of the store: `id:<id>` = code, `inst:<id>` = the TV install id at write time (a different one later = the TV was reset: the code is stale),
 *   `alias:<key form>` = id, `refused:<id>` = SHA-256 of the code the TV refused (never sent again; the code itself is never erased on a refusal, w13-08).
 *   `aname:<address form>` = base name seen at that address. The legacy entries (one per screen key) are still written and read (a downgrade keeps working);
 *   they are copied to `id:` once by [migrateAll] at startup, off the main thread: [read] never writes (a screen's decision during composition is pure).
 */
class PinBook(private val kv: PinKv) {
    companion object {
        const val ID = "id:"; const val ALIAS = "alias:"; const val INSTALL = "inst:"; const val REFUSED = "refused:"; const val ANAME = "aname:"
        private val PREFIXES = listOf(ID, ALIAS, INSTALL, REFUSED, ANAME)
        private val IPV4 = Regex("""\d{1,3}(\.\d{1,3}){3}""")
        private val BT_DECOR = Regex("""\s*\(Bluetooth\)\s*$""", RegexOption.IGNORE_CASE)
        private val SUFFIX = Regex("""\s*\(\d+\)\s*$""")
        /** « CastBridge TV M1 (2) (Bluetooth) » gives « castbridge tv m1 ». */
        fun baseName(n: String): String {
            var x = BT_DECOR.replace(n.trim(), "").trim()
            while (true) { val y = SUFFIX.replace(x, "").trim(); if (y == x) return x.lowercase(); x = y }
        }
        fun isLoopback(host: String) = host == "127.0.0.1" || host.equals("localhost", true) || host == "::1" || host == "0:0:0:0:0:0:0:1"
    }

    /** The key form of a screen key: `host:<ip>:<port>` for an IP literal (any spelling), else `name:<lowercase name>`; null for blank or the loopback. */
    fun formOf(key: String): String? {
        val raw = key.trim()
        if (raw.isBlank()) return null
        if (raw.startsWith("bt:", ignoreCase = true)) return TrustRegistry.norm(raw.substring(3)).takeIf { it.isNotBlank() }?.let { "bt:$it" }
        val (host, port) = PinKeys.hostAndPort(raw)
        if (host != null) {
            val h = host.substringBefore('%').lowercase()
            if (isLoopback(h)) return null
            if (IPV4.matches(h) || h.contains(':')) return "host:" + (if (h.contains(':')) "[$h]" else h) + ":" + (port ?: PinKeys.DEFAULT_PORT)
        }
        return "name:" + BT_DECOR.replace(raw, "").trim().lowercase()
    }

    /** The stable id [key] designates, or null (blank, or the tunnel loopback with no gateway TV). */
    fun tvId(key: String?, scope: PinScope, seenName: String? = null): String? {
        if (key.isNullOrBlank()) return null
        scope.resolve(key)?.let { return "bt:" + TrustRegistry.norm(it.address) }
        // the tunnel loopback while the gateway runs: the Bluetooth address it reaches (saved or not); with no gateway, no TV at all
        val (host, port) = PinKeys.hostAndPort(key)
        if (host != null && isLoopback(host.lowercase())) {
            val t = scope.tunnelTv
            return if (t != null && scope.tunnelPort != null && port == scope.tunnelPort) "bt:" + TrustRegistry.norm(t) else null
        }
        val f = formOf(key) ?: return null
        val a = kv.get(ALIAS + f)?.takeIf { it.isNotBlank() } ?: return f
        // an address alias leads to a TV's code only when the name seen at that address has the base name of the TV it was linked to
        return if (seenName != null && kv.get(ANAME + f) == baseName(seenName)) a else f
    }

    /** [key] is the tunnel loopback while no gateway TV is known: it designates no TV (no code is read or kept for it). */
    fun unknownRelay(key: String?, scope: PinScope): Boolean =
        key != null && tvId(key, scope) == null && PinKeys.hostAndPort(key).first?.let { isLoopback(it.lowercase()) } == true

    /** The code to present for [key], or "": the TV's record, else a legacy entry (then migrated); never a refused code, never a code older than a TV reset. */
    fun read(key: String?, scope: PinScope, seenName: String? = null): String {
        val id = tvId(key, scope, seenName) ?: return ""
        val tv = scope.resolve(key!!)
        if (stale(id, tv)) return ""
        val refused = kv.get(REFUSED + id)
        kv.get(ID + id)?.takeIf { Pin.isValidFormat(it) }?.let { return if (refused == TrustRegistry.hash(it)) "" else it }
        val own = formOf(key)
        if (tv == null && own != null && id != own) return ""                 // reached through an alias: the linked TV's record only, never a legacy entry
        val legacy = legacy(key, tv)
        if (legacy.isBlank() || refused == TrustRegistry.hash(legacy)) return ""
        return legacy                                                         // never written here: [migrateAll] does it once at startup
    }

    /**
     * Keeps [pin] for the TV [key] designates (record + the legacy keys of [PinKeys.writeKeys]); lifts a refusal. Never through an alias: the code goes under
     * the key's own form and that alias is unlinked (the address may belong to another TV now). False: not kept (loopback, write failed).
     */
    fun write(key: String, pin: String, scope: PinScope): Boolean {
        val id = tvId(key, scope) ?: return false                              // no seenName: never through an alias
        val tv = scope.resolve(key)
        val put = LinkedHashMap<String, String>()
        PinKeys.writeKeys(key, tv, tv != null && scope.hasToken(tv)).filterNot { k -> PinKeys.hostAndPort(k).first?.let { isLoopback(it.lowercase()) } == true }.forEach { put[it] = pin }
        put[ID + id] = pin
        put += installOf(id, tv)
        return kv.write(put, listOfNotNull(REFUSED + id, (INSTALL + id).takeIf { tv?.installId == null }, ALIAS + id, ANAME + id))
    }

    /**
     * The TV answered « bad pin » to [pin] sent for [key]. Reached through an address alias (the address now belongs to another TV), only the alias goes;
     * otherwise the code is marked refused for that TV (never sent again by itself) and is NOT erased.
     */
    fun refused(key: String?, pin: String, scope: PinScope) {
        if (key.isNullOrBlank() || pin.isBlank()) return
        val f = formOf(key)
        val h = TrustRegistry.hash(pin)
        if (f != null && scope.resolve(key) == null && kv.get(ALIAS + f) != null) { kv.write(mapOf(REFUSED + f to h), listOf(ALIAS + f, ANAME + f)); return }
        val id = tvId(key, scope) ?: return
        kv.write(mapOf(REFUSED + id to h))
    }

    /** The code kept for [key] was refused by the TV (and no new one was typed since). */
    fun isRefused(key: String?, scope: PinScope): Boolean {
        val id = tvId(key, scope) ?: return false
        val r = kv.get(REFUSED + id) ?: return false
        val p = kv.get(ID + id) ?: legacy(key!!, scope.resolve(key))
        return p.isNotBlank() && r == TrustRegistry.hash(p)
    }

    /** The TV [key] designates is another installation than the one the code was typed for (reset or reinstalled: it has a new code). */
    fun tvReset(key: String?, scope: PinScope): Boolean {
        val id = tvId(key, scope) ?: return false
        return stale(id, scope.resolve(key!!))
    }

    /**
     * [other] (an address, or the TV's new mDNS name) designates the same TV as [known]: seen together by the discovery and the code accepted.
     * A key a saved TV owns is never re-pointed. A code typed under [other] first follows the TV when its own record is empty.
     */
    fun link(known: String, other: String, scope: PinScope): Boolean {
        val id = tvId(known, scope) ?: return false
        val f = formOf(other) ?: return false
        if (f.startsWith("host:${PinKeys.WIFI_DIRECT_IP}:")) return false        // the same address on every TV: identifies none
        if (f == id) return true
        if (!f.startsWith("host:")) return false                                   // names are never aliases (« … (2) » may be the other TV of the same model)
        if (scope.resolve(other) != null) return false
        val base = scope.resolve(known)?.name?.let(::baseName) ?: formOf(known)?.takeIf { it.startsWith("name:") }?.let { baseName(known) } ?: return false
        if (kv.get(ALIAS + f) == id && kv.get(ANAME + f) == base) return true
        val put = linkedMapOf(ALIAS + f to id, ANAME + f to base)
        if (kv.get(ID + id) == null) (kv.get(ID + f) ?: kv.get(other.trim()))?.takeIf { Pin.isValidFormat(it) }?.let { put[ID + id] = it }
        return kv.write(put, listOf(REFUSED + f))
    }

    /** A legacy entry: under [key] itself (never the loopback: it designated whichever TV the tunnel reached), then under every key of [tv]. */
    private fun legacy(key: String, tv: SavedTv?): String {
        val loop = PinKeys.hostAndPort(key).first?.let { isLoopback(it.lowercase()) } == true
        return PinKeys.credential(null, if (loop) null else key, tv, kv::get).ifBlank {
            if (loop && tv != null) PinKeys.lookupKeys(tv).firstNotNullOfOrNull { k -> kv.get(k)?.takeIf { it.isNotBlank() } }.orEmpty() else ""
        }
    }

    /**
     * Once, at startup and off the main thread: each legacy entry (one per screen key) whose TV has no record yet gets one. Legacy entries of one TV that
     * disagree are left alone ([read] keeps the old search order for them). Aliases are never followed. Returns the number of records created.
     */
    fun migrateAll(scope: PinScope): Int {
        val found = LinkedHashMap<String, Pair<String, SavedTv?>?>()
        for (k in kv.keys()) {
            if (PREFIXES.any { k.startsWith(it) }) continue
            val v = kv.get(k)?.takeIf { Pin.isValidFormat(it) } ?: continue
            if (PinKeys.hostAndPort(k).first?.let { isLoopback(it.lowercase()) } == true) continue
            val id = tvId(k, scope) ?: continue
            if (kv.get(ID + id) != null) continue
            found[id] = if (!found.containsKey(id)) v to scope.resolve(k) else found[id]?.takeIf { it.first == v }
        }
        val put = LinkedHashMap<String, String>()
        found.forEach { (id, e) -> if (e != null) { put[ID + id] = e.first; put += installOf(id, e.second) } }
        if (put.isEmpty() || !kv.write(put)) return 0
        return found.values.count { it != null }
    }

    private fun stale(id: String, tv: SavedTv?): Boolean {
        val was = kv.get(INSTALL + id) ?: return false
        val now = tv?.installId ?: return false
        return was != now
    }

    private fun installOf(id: String, tv: SavedTv?): Map<String, String> = tv?.installId?.let { mapOf(INSTALL + id to it) } ?: emptyMap()
}

/** How a TV answered 401 (the body of `TvClient.HttpError`): a wrong code is not a lockout, and neither is an expired token. */
object TvAuthReply {
    sealed class Kind { object BadPin : Kind(); data class Locked(val retryAfterSec: Long) : Kind(); object BadToken : Kind(); object Other : Kind() }
    private val RETRY = Regex(""""retryAfter"\s*:\s*(\d+)""")

    fun of(code: Int, message: String?): Kind {
        if (code != 401) return Kind.Other
        val m = message.orEmpty()
        return when {
            m.contains("\"locked\"") -> Kind.Locked(RETRY.find(m)?.groupValues?.get(1)?.toLongOrNull() ?: 60)
            m.contains("bad token") -> Kind.BadToken
            else -> Kind.BadPin
        }
    }
}

/**
 * R-10: which credential a screen presents to a TV, or why the code must be typed (one place, for every screen). The code is asked ONLY when the TV said the
 * credential is no longer valid (code changed, phone removed, TV reset) or when there is none; never while the trusted link is still renewing its token
 * (first launch), never on a lockout (« too many tries » is not « the code changed »). Keeps R-01's second half ([PinFallback.choose]).
 */
object CredentialDecision {
    enum class Cause { FIRST_TIME, PIN_REFUSED, TV_RESET, PHONE_REMOVED, TOKEN_EXPIRED, TV_OUT_OF_REACH, RELAY_UNKNOWN }
    sealed class Choice {
        data class UseToken(val token: String) : Choice()
        data class UsePin(val pin: String) : Choice()
        data class Wait(val reason: String, val retryAfterSec: Long? = null) : Choice()
        data class AskPin(val cause: Cause, val text: String) : Choice()
    }

    /**
     * @param token live token ([LinkDriver.credential]); @param tokenRefused the TV refused it, a new HELLO is under way; @param trustedTv the key designates a saved TV;
     * @param linkPending the trusted link has not concluded yet (process start, connecting, reconnecting); @param storedPin [PinBook.read];
     * @param pinRefused [PinBook.isRefused]; @param lockedSec the TV locked this phone for that long; @param relayUnknown the key is the tunnel loopback with no gateway TV.
     */
    data class Facts(
        val token: String? = null, val tokenRefused: Boolean = false, val trustedTv: Boolean = false, val linkPending: Boolean = false,
        val storedPin: String = "", val pinRefused: Boolean = false, val tvReset: Boolean = false, val phoneRemoved: Boolean = false,
        val lockedSec: Long? = null, val relayUnknown: Boolean = false, val pendingForMs: Long = 0,
    )

    data class LinkFacts(val pending: Boolean, val tokenRefused: Boolean, val tvReset: Boolean, val phoneRemoved: Boolean)

    const val PENDING = "Connexion à la TV en cours : aucun code à saisir."
    const val PIN_CHANGED = "Le code de la TV a changé : saisissez-le à nouveau."
    const val TV_WAS_RESET = "La TV a été réinitialisée : saisissez le nouveau code affiché sur la TV."
    const val REMOVED = "Ce téléphone a été retiré de la TV : saisissez le code affiché sur la TV (ou « Réassocier »)."
    const val EXPIRED = "Reconnexion automatique impossible pour l'instant : saisissez le code affiché sur la TV."
    const val RELAY = "Passerelle Bluetooth arrêtée : saisissez le code de la TV."
    const val OUT_OF_REACH = "TV hors de portée : allumez-la ou rapprochez-vous, ou saisissez le code affiché sur la TV."
    /** How long « aucun code à saisir » may be said while the trusted link reconnects with no kept code. */
    const val PENDING_MAX_MS = 20_000L
    fun locked(sec: Long) = "Trop d'essais de code : nouvel essai dans $sec s."

    /** What the trusted link's state says about the DEFAULT TV's credential (null = nothing observed yet: the process just started). */
    fun linkFacts(state: LinkState?): LinkFacts = when (state) {
        null, LinkState.Connecting, is LinkState.Reconnecting -> LinkFacts(pending = true, tokenRefused = false, tvReset = false, phoneRemoved = false)
        LinkState.CredentialExpired -> LinkFacts(pending = true, tokenRefused = true, tvReset = false, phoneRemoved = false)
        is LinkState.TvForgotMe -> LinkFacts(false, false, tvReset = state.hint == BtProtocol.HINT_OTHER_INSTALL, phoneRemoved = state.hint != BtProtocol.HINT_OTHER_INSTALL)
        LinkState.Denied -> LinkFacts(false, false, tvReset = false, phoneRemoved = true)
        else -> LinkFacts(false, false, false, false)
    }

    fun decide(f: Facts): Choice = when {
        !f.token.isNullOrBlank() && !f.tokenRefused -> Choice.UseToken(f.token)
        f.relayUnknown -> Choice.AskPin(Cause.RELAY_UNKNOWN, RELAY)
        f.lockedSec != null -> Choice.Wait(locked(f.lockedSec), f.lockedSec)
        f.trustedTv && f.tokenRefused && !f.tvReset && !f.phoneRemoved -> Choice.Wait(PinFallback.REFUSED)
        f.tvReset -> Choice.AskPin(Cause.TV_RESET, TV_WAS_RESET)
        Pin.isValidFormat(f.storedPin) && !f.pinRefused -> Choice.UsePin(f.storedPin)
        Pin.isValidFormat(f.storedPin) -> Choice.AskPin(Cause.PIN_REFUSED, PIN_CHANGED)
        f.trustedTv && f.phoneRemoved -> Choice.AskPin(Cause.PHONE_REMOVED, REMOVED)
        f.trustedTv && f.linkPending && f.pendingForMs < PENDING_MAX_MS -> Choice.Wait(PENDING)
        f.trustedTv && f.linkPending -> Choice.AskPin(Cause.TV_OUT_OF_REACH, OUT_OF_REACH)
        f.trustedTv -> Choice.AskPin(Cause.TOKEN_EXPIRED, EXPIRED)
        else -> Choice.AskPin(Cause.FIRST_TIME, PinFallback.NEEDS_CODE)
    }
}

/**
 * The code-path TV of the home among the discovered ones. Android renames a service « X (2) » when its own stale announcement is still cached, and two TVs of
 * the same model swap « X » / « X (2) » across restarts. With the remembered ADDRESS known: the TV at that address with the same base name, else NOTHING (the
 * user chooses: an exact name elsewhere may be the other TV). Without an address: the single exact name. A 401 marks the code refused only when name AND
 * address agree ([refuseOn401]); a 401 on a name-only or address-only match may come from another TV.
 */
object HomeTvMatch {
    data class Seen(val name: String, val host: String, val port: Int)
    enum class How { NAME_AND_HOST, NAME, HOST }
    data class Match(val tv: Seen, val how: How)

    fun pick(name: String?, host: String?, seen: List<Seen>): Match? {
        if (name.isNullOrBlank()) return null
        if (host.isNullOrBlank()) return seen.filter { it.name == name }.singleOrNull()?.let { Match(it, How.NAME) }
        val atHost = seen.filter { it.host == host && PinBook.baseName(it.name) == PinBook.baseName(name) }
        atHost.firstOrNull { it.name == name }?.let { return Match(it, How.NAME_AND_HOST) }
        return atHost.singleOrNull()?.let { Match(it, How.HOST) }
    }

    /** May a 401 « bad pin » on this match mark the kept code refused? Only when the name and the address both agree. */
    fun refuseOn401(m: Match?): Boolean = m?.how == How.NAME_AND_HOST

    /** Nothing matched but a TV with the remembered base name is visible (moved address, or the other TV of the same model): the user chooses. */
    fun ambiguous(name: String?, host: String?, seen: List<Seen>): Boolean =
        name != null && pick(name, host, seen) == null && seen.any { PinBook.baseName(it.name) == PinBook.baseName(name) }
}

/** « Too many tries » per TV, remembered for the code field of every screen (process memory only: the TV's lock lasts a minute). */
class LockMemo(private val now: () -> Long = System::currentTimeMillis) {
    private val until = java.util.concurrent.ConcurrentHashMap<String, Long>()
    fun locked(tvId: String, sec: Long) { until[tvId] = now() + sec.coerceIn(1, 3600) * 1000 }
    /** Seconds left (rounded up), or null when not locked. */
    fun left(tvId: String?): Long? {
        val u = until[tvId ?: return null] ?: return null
        val ms = u - now()
        if (ms <= 0) { until.remove(tvId, u); return null }
        return (ms + 999) / 1000
    }
}
