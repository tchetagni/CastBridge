package castbridge.core.policy

import castbridge.core.owner.Envelope
import castbridge.core.owner.KeyScope
import castbridge.core.owner.Order

/**
 * THE CLOSED LIST of what a deferred order may ask (docs/ORDRES.md § Actions). A whitelist: an identifier that is not here is refused (`UNKNOWN_ACTION`), whatever its signature, and
 * so is any parameter that is not listed for its action (`BAD_PARAMS`). There is deliberately NO action that runs code, reads or deletes the user's files or library, reads personal
 * data, opens a remote access or touches the signed update check: the channel is narrow by construction, not by promise. Every action is an ABSOLUTE statement ("this licence is
 * suspended", "this flag is on"), never a delta, so applying it twice gives the same state (idempotence).
 */
object PolicyActions {
    const val LICENSE_ACTIVATE = "license.activate"
    const val LICENSE_SUSPEND = "license.suspend"
    const val LICENSE_REVOKE = "license.revoke"
    const val LICENSE_EXTEND = "license.extend"
    const val REVOCATION_ADD = "revocation.add"
    const val RIGHTS_REFRESH = "rights.refresh"
    const val FLAG_SET = "flag.set"
    const val APP_MIN_VERSION = "app.min_version"
    const val UPDATE_CHANNEL = "update.channel"
    const val CATALOG_AVAILABLE = "catalog.available"
    const val CATALOG_RETIRE = "catalog.retire"
    const val BUDGET_SET = "budget.set"
    const val MESSAGE_SHOW = "message.show"
    const val MESSAGE_CLEAR = "message.clear"

    /** Feature flags an order may flip. Closed too: nothing that guards security (signed updates, pairing, activation) is a flag. */
    val FLAGS = setOf("learn.beta", "quiz.beta", "bt.tunnel", "lots.autodownload", "telemetry.verbose", "ui.new-home", "store.enabled")
    val CHANNELS = setOf("stable", "beta")
    val BUDGETS = mapOf("lots_mb" to 0L..100_000L, "starter_mb" to 0L..10_000L, "quiz_daily" to 0L..10_000L)
    val MESSAGE_LEVELS = setOf("info", "notice")
    const val MAX_MESSAGE = 280
    /** An extension can never reach further than this beyond the time the TV trusts (an order cannot grant "forever"). */
    const val MAX_EXTENSION_MS = 366L * 24 * 3600 * 1000

    /** Identifiers a hostile or buggy server might try; the tests pin that each is refused. Documentation of what is NOT possible. */
    val NEVER = listOf("exec", "shell", "run", "delete", "wipe", "library.delete", "files.read", "files.list", "remote.open", "ssh.enable", "adb.enable", "update.disable", "update.verify.off", "pii.read", "factory.reset", "app.uninstall")

    val ALL = listOf(LICENSE_ACTIVATE, LICENSE_SUSPEND, LICENSE_REVOKE, LICENSE_EXTEND, REVOCATION_ADD, RIGHTS_REFRESH, FLAG_SET, APP_MIN_VERSION, UPDATE_CHANNEL,
        CATALOG_AVAILABLE, CATALOG_RETIRE, BUDGET_SET, MESSAGE_SHOW, MESSAGE_CLEAR)

    private val LOT = Regex("^[a-z][a-z0-9]{0,15}:[a-z0-9][a-z0-9-]{0,31}$")
    private val KID = Regex("^[0-9a-f]{16}$")
    private val SEAT = Regex("^[0-9a-f]{16}$")
    private val MSG_ID = Regex("^[a-z0-9][a-z0-9-]{0,31}$")
    private val NUM = Regex("^(0|[1-9][0-9]{0,15})$")

    /** What an action does, parsed and validated. */
    sealed class Change {
        data class LicenseMode(val license: String, val mode: castbridge.core.policy.LicenseMode) : Change()
        data class Extend(val license: String, val untilMs: Long) : Change()
        data class RevokeKey(val kid: String) : Change()
        data class RevokeSeat(val license: String, val seat: String, val atMs: Long) : Change()
        data class Refresh(val reason: String) : Change()
        data class Flag(val name: String, val on: Boolean) : Change()
        data class MinVersion(val versionCode: Int) : Change()
        data class Channel(val name: String) : Change()
        data class Available(val lots: Set<String>) : Change()
        data class Retire(val lots: Set<String>) : Change()
        data class Budget(val name: String, val value: Long) : Change()
        data class Message(val id: String, val text: String, val level: String, val untilMs: Long) : Change()
        data class ClearMessage(val id: String) : Change()
    }

    sealed class Parsed {
        /** [needs] = scopes the signing key must hold IN ADDITION to POLICY (an order never grants more than its key's scopes). */
        data class Ok(val change: Change, val needs: Set<KeyScope>) : Parsed()
        data class Bad(val reason: AckReason, val message: String) : Parsed()
    }

    fun parse(order: Order, nowMs: Long): Parsed {
        val p = order.params
        fun bad(m: String) = Parsed.Bad(AckReason.BAD_PARAMS, m)
        fun only(vararg names: String): String? = p.keys.firstOrNull { it !in names }?.let { "paramètre inattendu : $it" }
        fun need(name: String): String? = p[name]
        fun num(name: String): Long? = p[name]?.takeIf { NUM.matches(it) }?.toLongOrNull()
        fun lots(name: String): Set<String>? = p[name]?.split(',')?.takeIf { it.isNotEmpty() && it.size <= 24 }?.takeIf { l -> l.all { LOT.matches(it) } }?.toSet()
        fun lic(): String? = p["license"]?.takeIf { Envelope.ID.matches(it) }
        return when (order.action) {
            LICENSE_ACTIVATE, LICENSE_SUSPEND, LICENSE_REVOKE -> {
                only("license")?.let { return bad(it) }
                val l = lic() ?: return bad("licence manquante ou mal formée")
                Parsed.Ok(Change.LicenseMode(l, when (order.action) { LICENSE_ACTIVATE -> LicenseMode.ACTIVE; LICENSE_SUSPEND -> LicenseMode.SUSPENDED; else -> LicenseMode.REVOKED }), emptySet())
            }
            LICENSE_EXTEND -> {
                only("license", "until")?.let { return bad(it) }
                val l = lic() ?: return bad("licence manquante ou mal formée"); val until = num("until") ?: return bad("fin manquante")
                if (until > nowMs + MAX_EXTENSION_MS) return bad("prolongation trop lointaine")
                Parsed.Ok(Change.Extend(l, until), setOf(KeyScope.ISSUE_PRODUCTION))   // extending a right = granting: needs the production-issuing scope
            }
            REVOCATION_ADD -> {
                only("kid", "seat", "license", "at")?.let { return bad(it) }
                val kid = need("kid")
                if (kid != null) {
                    if (!KID.matches(kid) || p.size != 1) return bad("clé à révoquer mal formée")
                    Parsed.Ok(Change.RevokeKey(kid), setOf(KeyScope.REVOKE))
                } else {
                    val l = lic() ?: return bad("licence manquante"); val s = need("seat")?.takeIf { SEAT.matches(it) } ?: return bad("poste manquant"); val at = num("at") ?: return bad("date manquante")
                    Parsed.Ok(Change.RevokeSeat(l, s, at), setOf(KeyScope.REVOKE))
                }
            }
            RIGHTS_REFRESH -> { only("reason")?.let { return bad(it) }; Parsed.Ok(Change.Refresh(p["reason"]?.take(64) ?: ""), emptySet()) }
            FLAG_SET -> {
                only("name", "value")?.let { return bad(it) }
                val n = need("name")?.takeIf { it in FLAGS } ?: return bad("indicateur hors liste")
                val v = when (need("value")) { "1" -> true; "0" -> false; else -> return bad("valeur : 0 ou 1") }
                Parsed.Ok(Change.Flag(n, v), emptySet())
            }
            APP_MIN_VERSION -> { only("version")?.let { return bad(it) }; val v = num("version")?.takeIf { it <= 2_000_000_000L } ?: return bad("version invalide"); Parsed.Ok(Change.MinVersion(v.toInt()), emptySet()) }
            UPDATE_CHANNEL -> { only("channel")?.let { return bad(it) }; val c = need("channel")?.takeIf { it in CHANNELS } ?: return bad("canal inconnu"); Parsed.Ok(Change.Channel(c), emptySet()) }
            CATALOG_AVAILABLE -> { only("lots")?.let { return bad(it) }; Parsed.Ok(Change.Available(lots("lots") ?: return bad("liste de lots invalide")), emptySet()) }
            CATALOG_RETIRE -> { only("lots")?.let { return bad(it) }; Parsed.Ok(Change.Retire(lots("lots") ?: return bad("liste de lots invalide")), emptySet()) }
            BUDGET_SET -> {
                only("name", "value")?.let { return bad(it) }
                val n = need("name"); val range = BUDGETS[n] ?: return bad("budget inconnu"); val v = num("value")?.takeIf { it in range } ?: return bad("valeur hors bornes")
                Parsed.Ok(Change.Budget(n!!, v), emptySet())
            }
            MESSAGE_SHOW -> {
                only("id", "text", "level", "until")?.let { return bad(it) }
                val id = need("id")?.takeIf { MSG_ID.matches(it) } ?: return bad("identifiant de message invalide")
                val t = need("text")?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_MESSAGE && it.none { c -> c.isISOControl() } && "://" !in it && "www." !in it.lowercase() } ?: return bad("texte invalide (vide, trop long, ou contenant un lien)")
                val lv = (need("level") ?: "info").takeIf { it in MESSAGE_LEVELS } ?: return bad("niveau inconnu")
                Parsed.Ok(Change.Message(id, t, lv, num("until") ?: 0L), emptySet())
            }
            MESSAGE_CLEAR -> { only("id")?.let { return bad(it) }; Parsed.Ok(Change.ClearMessage(need("id")?.takeIf { MSG_ID.matches(it) } ?: return bad("identifiant invalide")), emptySet()) }
            else -> Parsed.Bad(AckReason.UNKNOWN_ACTION, "Action « ${order.action.take(48)} » hors de la liste fermée")
        }
    }
}

enum class LicenseMode { ACTIVE, SUSPENDED, REVOKED }

/** Why an order was applied or refused: the technical code the TV reports back (and the journal shows). */
enum class AckReason {
    APPLIED,
    // generic (the common envelope checks)
    MALFORMED, UNKNOWN_TYPE, UNKNOWN_KEY, REVOKED_KEY, BAD_SIGNATURE, KEY_NOT_ALLOWED, BAD_ORDER, WRONG_TARGET, STALE_SEQUENCE, NOT_YET_VALID, WINDOW_CLOSED,
    // policy engine
    UNKNOWN_ACTION, BAD_PARAMS, SCOPE_EXCEEDED, TOO_LARGE;

    val applied get() = this == APPLIED
}
